package com.jasermohamed.bumpcompanion.platform.diagnostics

import android.content.Context
import com.jasermohamed.bumpcompanion.domain.detection.DetectorCsvFormat
import com.jasermohamed.bumpcompanion.domain.model.*
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID

/** Opt-in app-private recorder. Disk I/O occurs on the service's background processing dispatcher. */
class DetectorCsvRecorder private constructor(
    val file: File,
    private val writer: BufferedWriter,
) : DetectorDiagnosticsListener, Closeable {
    private var rowsSinceFlush = 0

    @Synchronized
    override fun onFrame(frame: DetectorTelemetryFrame) {
        writer.appendLine(DetectorCsvFormat.frameLine(frame))
        flushPeriodically()
    }

    @Synchronized
    override fun onCandidate(evaluation: CandidateEvaluation) {
        writer.appendLine(DetectorCsvFormat.candidateLine(evaluation))
        writer.flush()
        rowsSinceFlush = 0
    }

    @Synchronized
    fun recordLocation(point: GeoPoint, quality: LocationQuality) {
        writer.appendLine(DetectorCsvFormat.locationLine(point, quality))
        flushPeriodically()
    }

    @Synchronized
    override fun close() {
        runCatching { writer.flush() }
        runCatching { writer.close() }
    }

    private fun flushPeriodically() {
        rowsSinceFlush++
        if (rowsSinceFlush >= 100) {
            writer.flush()
            rowsSinceFlush = 0
        }
    }

    companion object {
        fun create(context: Context, driveId: String?, loggingMode: String): DetectorCsvRecorder {
            val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "diagnostics")
            check(directory.exists() || directory.mkdirs()) { "Unable to create diagnostic directory" }
            val safeDriveId = driveId?.filter { it.isLetterOrDigit() || it == '-' }?.take(48) ?: "no-drive"
            val file = File(directory, "detector-v${DetectorCsvFormat.SCHEMA_VERSION}-${safeDriveId}-${UUID.randomUUID()}.csv")
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8), 64 * 1024)
            writer.appendLine("# mode=${loggingMode.replace(',', '_')}")
            writer.appendLine(DetectorCsvFormat.headerLine())
            writer.flush()
            return DetectorCsvRecorder(file, writer)
        }
    }
}
