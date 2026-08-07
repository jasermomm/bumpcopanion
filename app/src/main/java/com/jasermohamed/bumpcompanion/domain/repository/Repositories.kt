package com.jasermohamed.bumpcompanion.domain.repository

import com.jasermohamed.bumpcompanion.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BumpRepository {
    fun observeBumps(status: BumpStatus? = null): Flow<List<SpeedBump>>
    fun observeConfirmedCount(): Flow<Int>
    fun observePendingCandidates(): Flow<List<CandidateEvent>>
    fun observePendingCount(): Flow<Int>
    fun observeEncounters(bumpId: String): Flow<List<EncounterSummary>>
    suspend fun getBump(id: String): SpeedBump?
    suspend fun getCandidate(id: String): CandidateEvent?
    suspend fun saveCandidate(candidate: CandidateEvent)
    suspend fun confirmCandidate(candidateId: String): SpeedBump?
    suspend fun decideCandidate(candidateId: String, decision: CandidateDecision, note: String = "")
    suspend fun createManualCandidate(driveId: String?, location: GeoPoint?, source: BumpSource = BumpSource.MANUAL): CandidateEvent
    suspend fun saveOrMergeBump(bump: SpeedBump): MergeResult
    suspend fun queryWarningCandidates(location: GeoPoint, radiusMetres: Double): List<SpeedBump>
    suspend fun setWarningEnabled(id: String, enabled: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun markRemoved(id: String)
    suspend fun deleteBump(id: String)
    suspend fun updateBump(bump: SpeedBump)
    suspend fun recordEncounter(encounter: EncounterSummary)
    suspend fun updateLastWarned(id: String, timestamp: Long)
    suspend fun exportableBumps(): List<SpeedBump>
    suspend fun deleteAll()
}

interface DriveRepository {
    fun observeDrives(): Flow<List<DriveSession>>
    fun observeLatestDrive(): Flow<DriveSession?>
    suspend fun getDrive(id: String): DriveSession?
    suspend fun startDrive(quality: DetectionQuality, start: GeoPoint?): DriveSession
    suspend fun updateDrive(session: DriveSession)
    suspend fun finishDrive(session: DriveSession)
    suspend fun recoverIncompleteDrives(now: Long)
    suspend fun recordTrackPoints(points: List<LocationTrackPoint>)
    suspend fun getTrackPoints(driveId: String): List<LocationTrackPoint>
    suspend fun deleteHistory()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setSensitivity(value: Sensitivity)
    suspend fun setWarningsEnabled(value: Boolean)
    suspend fun setVoiceEnabled(value: Boolean)
    suspend fun setToneEnabled(value: Boolean)
    suspend fun setVibrationEnabled(value: Boolean)
    suspend fun setAdaptiveDistance(value: Boolean)
    suspend fun setRouteHistoryEnabled(value: Boolean)
    suspend fun setMetricUnits(value: Boolean)
    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setDynamicColour(value: Boolean)
    suspend fun setDiagnosticLoggingEnabled(value: Boolean)
    suspend fun setPreferredNavigationPackage(value: String?)
    suspend fun reset()
}

data class EncounterSummary(
    val id: String,
    val bumpId: String?,
    val candidateId: String?,
    val driveId: String?,
    val encounteredAt: Long,
    val location: GeoPoint,
    val confidence: Float,
    val source: BumpSource,
)

data class MergeResult(
    val bump: SpeedBump,
    val createdNew: Boolean,
    val mergedIntoId: String? = null,
)
