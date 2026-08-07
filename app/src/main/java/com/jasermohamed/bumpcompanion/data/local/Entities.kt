package com.jasermohamed.bumpcompanion.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jasermohamed.bumpcompanion.domain.model.*

@Entity(
    tableName = "speed_bumps",
    indices = [
        Index("status"),
        Index("lastDetectedAt"),
        Index("importedSource"),
        Index(value = ["latitude", "longitude"]),
    ],
)
data class SpeedBumpEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val rawLatitude: Double,
    val rawLongitude: Double,
    val horizontalAccuracyMetres: Float,
    val coordinateConfidence: Float,
    val confidence: Float,
    val status: BumpStatus,
    val source: BumpSource,
    val directionality: Directionality,
    val primaryBearing: Float?,
    val oppositeBearing: Float?,
    val bearingTolerance: Float,
    val firstDetectedAt: Long,
    val lastDetectedAt: Long,
    val lastWarnedAt: Long?,
    val encounterCount: Int,
    val confirmationCount: Int,
    val rejectionCount: Int,
    val missingReports: Int,
    val importedSource: String?,
    val notes: String,
    val warningEnabled: Boolean,
    val customWarningDistanceMetres: Int?,
    val algorithmVersion: Int,
    val archived: Boolean,
    val markedRemoved: Boolean,
    val regionLabel: String?,
    val roadName: String?,
)

@Entity(
    tableName = "candidate_events",
    indices = [
        Index("driveId"),
        Index("decision"),
        Index("detectedAt"),
        Index(value = ["latitude", "longitude"]),
    ],
)
data class CandidateEventEntity(
    @PrimaryKey val id: String,
    val driveId: String?,
    val detectedAt: Long,
    val eventElapsedRealtimeNanos: Long,
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyMetres: Float?,
    val coordinateConfidence: Float,
    val speedMetresPerSecond: Float,
    val bearingDegrees: Float?,
    val confidence: Float,
    val eventType: RoadEventType,
    val decision: CandidateDecision,
    val confidenceReasons: List<ConfidenceReason>,
    val positiveVerticalPeak: Float,
    val negativeVerticalPeak: Float,
    val peakToPeak: Float,
    val peakGapMillis: Long,
    val verticalRms: Float,
    val jerkPeak: Float,
    val gyroscopeEnergy: Float,
    val orientationVariance: Float,
    val phoneStability: Float,
    val source: BumpSource,
    val note: String,
)

@Entity(
    tableName = "drive_sessions",
    indices = [Index("startedAt"), Index("incomplete")],
)
data class DriveSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMillis: Long,
    val distanceMetres: Double,
    val maximumSpeedMetresPerSecond: Float,
    val averageSpeedMetresPerSecond: Float,
    val candidateCount: Int,
    val confirmedCount: Int,
    val rejectedCount: Int,
    val knownBumpPasses: Int,
    val warningCount: Int,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val incomplete: Boolean,
    val placementProfile: String,
    val vehicleProfile: String?,
    val algorithmVersion: Int,
    val serviceInterruptions: Int,
    val detectionQuality: DetectionQuality,
)

@Entity(
    tableName = "encounters",
    indices = [Index("bumpId"), Index("candidateId"), Index("driveId"), Index("encounteredAt")],
)
data class EncounterEntity(
    @PrimaryKey val id: String,
    val bumpId: String?,
    val candidateId: String?,
    val driveId: String?,
    val encounteredAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
    val bearingDegrees: Float?,
    val speedMetresPerSecond: Float,
    val confidence: Float,
    val source: BumpSource,
)


@Entity(
    tableName = "location_track_points",
    primaryKeys = ["driveId", "elapsedRealtimeNanos"],
    indices = [Index("driveId"), Index("epochMillis")],
)
data class LocationTrackPointEntity(
    val driveId: String,
    val elapsedRealtimeNanos: Long,
    val epochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
    val bearingDegrees: Float?,
    val speedMetresPerSecond: Float?,
)

@Entity(tableName = "calibration_profiles", indices = [Index("createdAt")])
data class CalibrationProfileEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val accelerometerNoiseRms: Float,
    val gyroscopeNoiseRms: Float,
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val samplingConsistency: Float,
    val engineVibrationRms: Float,
    val sampleCount: Int,
    val algorithmVersion: Int,
)

@Entity(tableName = "import_batches", indices = [Index("importedAt")])
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val importedAt: Long,
    val sourceName: String,
    val fileName: String,
    val schemaVersion: Int,
    val insertedCount: Int,
    val mergedCount: Int,
    val invalidCount: Int,
    val undone: Boolean,
)

@Entity(tableName = "import_batch_items", primaryKeys = ["batchId", "bumpId"], indices = [Index("bumpId")])
data class ImportBatchItemEntity(
    val batchId: String,
    val bumpId: String,
    val createdNew: Boolean,
)


@Entity(
    tableName = "diagnostic_files",
    indices = [Index("driveId"), Index("createdAt"), Index("expiresAt")],
)
data class DiagnosticFileEntity(
    @PrimaryKey val id: String,
    val driveId: String?,
    val fileName: String,
    val loggingMode: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val expiresAt: Long?,
    val checksumSha256: String?,
    val corrupted: Boolean,
)
