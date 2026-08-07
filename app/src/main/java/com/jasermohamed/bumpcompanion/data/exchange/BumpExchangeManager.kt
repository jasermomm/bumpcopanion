package com.jasermohamed.bumpcompanion.data.exchange

import android.content.Context
import android.net.Uri
import com.jasermohamed.bumpcompanion.BuildConfig
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.data.local.AppDatabase
import com.jasermohamed.bumpcompanion.data.local.ImportBatchEntity
import com.jasermohamed.bumpcompanion.data.local.ImportBatchItemEntity
import com.jasermohamed.bumpcompanion.domain.model.BumpSource
import com.jasermohamed.bumpcompanion.domain.model.BumpStatus
import com.jasermohamed.bumpcompanion.domain.model.SpeedBump
import com.jasermohamed.bumpcompanion.domain.repository.BumpRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
private const val MAX_IMPORT_ITEMS = 20_000
private const val SCHEMA_VERSION = 1

@Serializable
data class BumpExportFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val exportId: String = UUID.randomUUID().toString(),
    val exportedAt: Long = System.currentTimeMillis(),
    val listName: String? = null,
    val sourceLabel: String? = null,
    val bumps: List<SpeedBump>,
    val checksum: String? = null,
)

data class ImportPreview(
    val fileName: String,
    val exportedAt: Long,
    val bumpCount: Int,
    val sourceLabel: String?,
    val invalidCount: Int,
    val coordinateBounds: String,
)

data class ImportResult(
    val inserted: Int,
    val merged: Int,
    val invalid: Int,
    val errors: List<String> = emptyList(),
    val batchId: String? = null,
)

interface BumpExchangeManager {
    suspend fun exportTo(uri: Uri, listName: String? = null): Result<Int>
    suspend fun preview(uri: Uri): Result<ImportPreview>
    suspend fun importFrom(uri: Uri, sourceName: String? = null): Result<ImportResult>
}

@Singleton
class BumpExchangeManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BumpRepository,
    database: AppDatabase,
) : BumpExchangeManager {
    private val importBatchDao = database.importBatchDao()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun exportTo(uri: Uri, listName: String?): Result<Int> = runCatching {
        val bumps = repository.exportableBumps()
        val safeBumps = bumps.map { bump ->
            bump.copy(
                horizontalAccuracyMetres = bump.horizontalAccuracyMetres.takeIf { it.isFinite() } ?: 999f,
                confidence = bump.confidence.coerceIn(0f, 1f),
                coordinateConfidence = bump.coordinateConfidence.coerceIn(0f, 1f),
            )
        }
        val unsigned = BumpExportFile(listName = listName, bumps = safeBumps)
        val payloadWithoutChecksum = json.encodeToString(unsigned)
        val file = unsigned.copy(checksum = sha256(payloadWithoutChecksum))
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(json.encodeToString(file))
        } ?: error(context.getString(R.string.unable_open_export))
        safeBumps.size
    }

    override suspend fun preview(uri: Uri): Result<ImportPreview> = runCatching {
        val file = readAndValidate(uri)
        val valid = file.bumps.filter(::isValidBump)
        val bounds = if (valid.isEmpty()) context.getString(R.string.import_no_valid_coordinates) else {
            val minLat = valid.minOf { it.latitude }
            val maxLat = valid.maxOf { it.latitude }
            val minLon = valid.minOf { it.longitude }
            val maxLon = valid.maxOf { it.longitude }
            "%.4f…%.4f, %.4f…%.4f".format(minLat, maxLat, minLon, maxLon)
        }
        ImportPreview(
            fileName = uri.lastPathSegment?.takeLast(80) ?: context.getString(R.string.imported_file),
            exportedAt = file.exportedAt,
            bumpCount = file.bumps.size,
            sourceLabel = file.sourceLabel,
            invalidCount = file.bumps.count { !isValidBump(it) },
            coordinateBounds = bounds,
        )
    }

    override suspend fun importFrom(uri: Uri, sourceName: String?): Result<ImportResult> = runCatching {
        val file = readAndValidate(uri)
        var inserted = 0
        var merged = 0
        var invalid = 0
        val errors = mutableListOf<String>()
        val batchId = UUID.randomUUID().toString()
        val batchItems = mutableListOf<ImportBatchItemEntity>()
        val resolvedSource = (sourceName ?: file.sourceLabel ?: context.getString(R.string.imported_list)).take(120)
        file.bumps.forEachIndexed { index, item ->
            if (!isValidBump(item)) {
                invalid++
                if (errors.size < 10) errors += context.getString(R.string.import_invalid_item, index + 1)
                return@forEachIndexed
            }
            val sanitised = item.copy(
                id = item.id.takeIf { it.length in 8..100 } ?: UUID.randomUUID().toString(),
                source = BumpSource.IMPORTED,
                status = BumpStatus.IMPORTED,
                importedSource = resolvedSource,
                notes = item.notes.take(2_000),
                roadName = item.roadName?.take(160),
                regionLabel = item.regionLabel?.take(160),
            )
            val result = repository.saveOrMergeBump(sanitised)
            batchItems += ImportBatchItemEntity(batchId, result.bump.id, result.createdNew)
            if (result.createdNew) inserted++ else merged++
        }
        importBatchDao.insert(
            ImportBatchEntity(
                id = batchId,
                importedAt = System.currentTimeMillis(),
                sourceName = resolvedSource,
                fileName = uri.lastPathSegment?.takeLast(160) ?: context.getString(R.string.imported_file),
                schemaVersion = file.schemaVersion,
                insertedCount = inserted,
                mergedCount = merged,
                invalidCount = invalid,
                undone = false,
            )
        )
        if (batchItems.isNotEmpty()) importBatchDao.insertItems(batchItems)
        ImportResult(inserted, merged, invalid, errors, batchId)
    }

    private fun readAndValidate(uri: Uri): BumpExportFile {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_IMPORT_BYTES) { context.getString(R.string.import_too_large) }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error(context.getString(R.string.unable_open_import))
        val text = bytes.toString(Charsets.UTF_8)
        val file = json.decodeFromString<BumpExportFile>(text)
        require(file.schemaVersion in 1..SCHEMA_VERSION) { context.getString(R.string.import_unsupported_schema, file.schemaVersion) }
        require(file.bumps.size <= MAX_IMPORT_ITEMS) { context.getString(R.string.import_too_many_items) }
        require(file.exportId.length in 1..100) { context.getString(R.string.import_invalid_identifier) }
        require(file.exportedAt in 0..(System.currentTimeMillis() + 86_400_000L)) { context.getString(R.string.import_invalid_timestamp) }
        require(file.listName == null || file.listName.length <= 160) { context.getString(R.string.import_list_name_too_long) }
        require(file.sourceLabel == null || file.sourceLabel.length <= 160) { context.getString(R.string.import_source_too_long) }
        require(file.bumps.map { it.id }.distinct().size == file.bumps.size) { context.getString(R.string.import_duplicate_ids) }
        file.checksum?.let { expected ->
            val unsigned = json.encodeToString(file.copy(checksum = null))
            require(expected.equals(sha256(unsigned), ignoreCase = true)) { context.getString(R.string.import_checksum_mismatch) }
        }
        return file
    }

    private fun isValidBump(bump: SpeedBump): Boolean =
        bump.latitude in -90.0..90.0 &&
            bump.longitude in -180.0..180.0 &&
            bump.confidence in 0f..1f &&
            bump.coordinateConfidence in 0f..1f &&
            bump.notes.length <= 10_000 &&
            (bump.customWarningDistanceMetres == null || bump.customWarningDistanceMetres in 10..1_000)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
