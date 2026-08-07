package com.jasermohamed.bumpcompanion.data.repository

import androidx.room.withTransaction
import com.jasermohamed.bumpcompanion.data.local.*
import com.jasermohamed.bumpcompanion.domain.approach.GeoMath
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BumpRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
) : BumpRepository {
    private val bumpDao = database.speedBumpDao()
    private val candidateDao = database.candidateDao()
    private val encounterDao = database.encounterDao()

    override fun observeBumps(status: BumpStatus?): Flow<List<SpeedBump>> =
        (status?.let(bumpDao::observeByStatus) ?: bumpDao.observeAll()).map { list -> list.map { it.toDomain() } }

    override fun observeConfirmedCount(): Flow<Int> = bumpDao.observeConfirmedCount()
    override fun observePendingCandidates(): Flow<List<CandidateEvent>> = candidateDao.observePending().map { list -> list.map { it.toDomain() } }
    override fun observePendingCount(): Flow<Int> = candidateDao.observePendingCount()

    override fun observeEncounters(bumpId: String): Flow<List<EncounterSummary>> =
        encounterDao.observeForBump(bumpId).map { list ->
            list.map {
                EncounterSummary(
                    it.id, it.bumpId, it.candidateId, it.driveId, it.encounteredAt,
                    GeoPoint(it.latitude, it.longitude, it.accuracyMetres, it.bearingDegrees ?: Float.NaN, it.speedMetresPerSecond),
                    it.confidence, it.source,
                )
            }
        }

    override suspend fun getBump(id: String): SpeedBump? = bumpDao.getById(id)?.toDomain()
    override suspend fun getCandidate(id: String): CandidateEvent? = candidateDao.getById(id)?.toDomain()
    override suspend fun saveCandidate(candidate: CandidateEvent) = candidateDao.upsert(candidate.toEntity())

    override suspend fun confirmCandidate(candidateId: String): SpeedBump? = database.withTransaction {
        val candidate = candidateDao.getById(candidateId)?.toDomain() ?: return@withTransaction null
        val lat = candidate.latitude ?: return@withTransaction null
        val lon = candidate.longitude ?: return@withTransaction null
        val proposed = SpeedBump(
            latitude = lat,
            longitude = lon,
            rawLatitude = lat,
            rawLongitude = lon,
            horizontalAccuracyMetres = candidate.horizontalAccuracyMetres ?: Float.NaN,
            coordinateConfidence = candidate.coordinateConfidence,
            confidence = candidate.confidence,
            status = BumpStatus.CONFIRMED,
            source = candidate.source,
            primaryBearing = candidate.bearingDegrees,
            firstDetectedAt = candidate.detectedAt,
            lastDetectedAt = candidate.detectedAt,
            algorithmVersion = 1,
        )
        val merged = saveOrMergeBumpInternal(proposed)
        candidateDao.setDecision(candidateId, CandidateDecision.CONFIRMED)
        encounterDao.insert(
            EncounterEntity(
                id = UUID.randomUUID().toString(),
                bumpId = merged.bump.id,
                candidateId = candidate.id,
                driveId = candidate.driveId,
                encounteredAt = candidate.detectedAt,
                latitude = lat,
                longitude = lon,
                accuracyMetres = candidate.horizontalAccuracyMetres ?: Float.NaN,
                bearingDegrees = candidate.bearingDegrees,
                speedMetresPerSecond = candidate.speedMetresPerSecond,
                confidence = candidate.confidence,
                source = candidate.source,
            )
        )
        merged.bump
    }

    override suspend fun decideCandidate(candidateId: String, decision: CandidateDecision, note: String) {
        if (decision == CandidateDecision.CONFIRMED) confirmCandidate(candidateId)
        else candidateDao.setDecision(candidateId, decision, note)
    }

    override suspend fun createManualCandidate(driveId: String?, location: GeoPoint?, source: BumpSource): CandidateEvent {
        val now = System.currentTimeMillis()
        val candidate = CandidateEvent(
            driveId = driveId,
            detectedAt = now,
            eventElapsedRealtimeNanos = location?.elapsedRealtimeNanos ?: 0L,
            latitude = location?.latitude,
            longitude = location?.longitude,
            horizontalAccuracyMetres = location?.accuracyMetres?.takeIf { it.isFinite() },
            coordinateConfidence = if (location != null && location.accuracyMetres <= 25f) 0.9f else 0.55f,
            speedMetresPerSecond = location?.speedMetresPerSecond?.takeIf { it.isFinite() } ?: 0f,
            bearingDegrees = location?.bearingDegrees?.takeIf { it.isFinite() },
            confidence = 1f,
            eventType = RoadEventType.LIKELY_SPEED_BUMP,
            confidenceReasons = listOf(ConfidenceReason.MANUALLY_MARKED),
            phoneStability = 1f,
            source = source,
        )
        saveCandidate(candidate)
        return candidate
    }

    override suspend fun saveOrMergeBump(bump: SpeedBump): MergeResult = database.withTransaction { saveOrMergeBumpInternal(bump) }

    private suspend fun saveOrMergeBumpInternal(bump: SpeedBump): MergeResult {
        val radius = if (bump.horizontalAccuracyMetres.isFinite()) max(12.0, bump.horizontalAccuracyMetres * 1.5) else 25.0
        val box = GeoMath.boundingBox(bump.latitude, bump.longitude, radius)
        val nearby = bumpDao.queryNearby(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .map { it.toDomain() }
            .filter { GeoMath.distanceMetres(it.latitude, it.longitude, bump.latitude, bump.longitude) <= radius }
        val compatible = nearby.minByOrNull { existing ->
            val bearingPenalty = if (existing.primaryBearing != null && bump.primaryBearing != null) {
                GeoMath.smallestAngleDifference(existing.primaryBearing, bump.primaryBearing).toDouble() / 10.0
            } else 0.0
            GeoMath.distanceMetres(existing.latitude, existing.longitude, bump.latitude, bump.longitude) + bearingPenalty
        }
        if (compatible == null) {
            bumpDao.upsert(bump.toEntity())
            return MergeResult(bump, createdNew = true)
        }
        val oldWeight = max(1f, compatible.coordinateConfidence * compatible.encounterCount)
        val newWeight = max(0.25f, bump.coordinateConfidence)
        val total = oldWeight + newWeight
        val merged = compatible.copy(
            latitude = (compatible.latitude * oldWeight + bump.latitude * newWeight) / total,
            longitude = (compatible.longitude * oldWeight + bump.longitude * newWeight) / total,
            horizontalAccuracyMetres = listOf(compatible.horizontalAccuracyMetres, bump.horizontalAccuracyMetres)
                .filter { it.isFinite() }.minOrNull() ?: Float.NaN,
            coordinateConfidence = max(compatible.coordinateConfidence, bump.coordinateConfidence),
            confidence = max(compatible.confidence, bump.confidence),
            lastDetectedAt = max(compatible.lastDetectedAt, bump.lastDetectedAt),
            encounterCount = compatible.encounterCount + max(1, bump.encounterCount),
            confirmationCount = compatible.confirmationCount + bump.confirmationCount,
            source = if (compatible.source == BumpSource.IMPORTED && bump.source != BumpSource.IMPORTED) bump.source else compatible.source,
            status = if (compatible.status == BumpStatus.IMPORTED && bump.status == BumpStatus.CONFIRMED) BumpStatus.CONFIRMED else compatible.status,
            warningEnabled = compatible.warningEnabled || bump.warningEnabled,
        )
        bumpDao.upsert(merged.toEntity())
        return MergeResult(merged, createdNew = false, mergedIntoId = merged.id)
    }

    override suspend fun queryWarningCandidates(location: GeoPoint, radiusMetres: Double): List<SpeedBump> {
        val box = GeoMath.boundingBox(location.latitude, location.longitude, radiusMetres)
        return bumpDao.queryWarningCandidates(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .map { it.toDomain() }
            .filter { GeoMath.distanceMetres(location.latitude, location.longitude, it.latitude, it.longitude) <= radiusMetres }
    }

    override suspend fun setWarningEnabled(id: String, enabled: Boolean) = bumpDao.setWarningEnabled(id, enabled)
    override suspend fun setArchived(id: String, archived: Boolean) = bumpDao.setArchived(id, archived)
    override suspend fun markRemoved(id: String) = bumpDao.markRemoved(id)
    override suspend fun deleteBump(id: String) {
        database.withTransaction {
            encounterDao.deleteForBump(id)

            bumpDao.getById(id)?.let { bump ->
                bumpDao.delete(bump)
            }
        }
    }
    override suspend fun updateBump(bump: SpeedBump) = bumpDao.upsert(bump.toEntity())

    override suspend fun recordEncounter(encounter: EncounterSummary) {
        encounterDao.insert(
            EncounterEntity(
                encounter.id, encounter.bumpId, encounter.candidateId, encounter.driveId,
                encounter.encounteredAt, encounter.location.latitude, encounter.location.longitude,
                encounter.location.accuracyMetres, encounter.location.bearingDegrees.takeIf { it.isFinite() },
                encounter.location.speedMetresPerSecond, encounter.confidence, encounter.source,
            )
        )
    }

    override suspend fun updateLastWarned(id: String, timestamp: Long) = bumpDao.updateLastWarned(id, timestamp)
    override suspend fun exportableBumps(): List<SpeedBump> = bumpDao.getExportable().map { it.toDomain() }

    override suspend fun deleteAll() = database.withTransaction {
        encounterDao.deleteAll()
        candidateDao.deleteAll()
        bumpDao.deleteAll()
        database.calibrationDao().deleteAll()
        database.diagnosticFileDao().deleteAll()
        database.importBatchDao().deleteAllItems()
        database.importBatchDao().deleteAllBatches()
    }
}

@Singleton
class DriveRepositoryImpl @Inject constructor(
    private val dao: DriveSessionDao,
    private val trackDao: LocationTrackPointDao,
) : DriveRepository {
    override fun observeDrives(): Flow<List<DriveSession>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observeLatestDrive(): Flow<DriveSession?> = dao.observeLatest().map { it?.toDomain() }
    override suspend fun getDrive(id: String): DriveSession? = dao.getById(id)?.toDomain()

    override suspend fun startDrive(quality: DetectionQuality, start: GeoPoint?): DriveSession {
        val session = DriveSession(
            startedAt = System.currentTimeMillis(),
            startLatitude = start?.latitude,
            startLongitude = start?.longitude,
            detectionQuality = quality,
        )
        dao.upsert(session.toEntity())
        return session
    }

    override suspend fun updateDrive(session: DriveSession) = dao.upsert(session.toEntity())
    override suspend fun finishDrive(session: DriveSession) = dao.upsert(session.copy(incomplete = false).toEntity())

    override suspend fun recoverIncompleteDrives(now: Long) {
        dao.getIncomplete().forEach { item ->
            val duration = (now - item.startedAt).coerceAtLeast(0)
            dao.upsert(item.copy(endedAt = item.endedAt ?: now, durationMillis = duration, incomplete = true))
        }
    }

    override suspend fun recordTrackPoints(points: List<LocationTrackPoint>) {
        if (points.isEmpty()) return
        trackDao.insertAll(points.map { point ->
            LocationTrackPointEntity(
                driveId = point.driveId,
                elapsedRealtimeNanos = point.elapsedRealtimeNanos,
                epochMillis = point.epochMillis,
                latitude = point.latitude,
                longitude = point.longitude,
                accuracyMetres = point.accuracyMetres,
                bearingDegrees = point.bearingDegrees,
                speedMetresPerSecond = point.speedMetresPerSecond,
            )
        })
    }

    override suspend fun getTrackPoints(driveId: String): List<LocationTrackPoint> =
        trackDao.getForDrive(driveId).map { point ->
            LocationTrackPoint(
                driveId = point.driveId,
                elapsedRealtimeNanos = point.elapsedRealtimeNanos,
                epochMillis = point.epochMillis,
                latitude = point.latitude,
                longitude = point.longitude,
                accuracyMetres = point.accuracyMetres,
                bearingDegrees = point.bearingDegrees,
                speedMetresPerSecond = point.speedMetresPerSecond,
            )
        }

    override suspend fun deleteHistory() {
        trackDao.deleteAll()
        dao.deleteAll()
    }
}
