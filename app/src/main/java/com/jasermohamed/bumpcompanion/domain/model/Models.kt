package com.jasermohamed.bumpcompanion.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class BumpStatus { CONFIRMED, PENDING, IMPORTED, ARCHIVED, REMOVED }

@Serializable
enum class BumpSource { DETECTED, MANUAL, IMPORTED, SIMULATION }

@Serializable
enum class Directionality { BIDIRECTIONAL, ONE_DIRECTION, TWO_CLUSTERS, UNKNOWN }

@Serializable
enum class RoadEventType {
    LIKELY_SPEED_BUMP,
    POSSIBLE_SPEED_BUMP,
    POTHOLE_LIKE,
    ROUGH_ROAD,
    PHONE_MOVEMENT,
    HARD_BRAKING,
    UNKNOWN,
    DISCARDED
}


@Serializable
enum class ConfidenceReason {
    OPPOSING_VERTICAL_PEAKS,
    BUMP_LIKE_PEAK_TIMING,
    ORIENTATION_STABLE,
    ORIENTATION_CHANGED,
    PHONE_UNSTABLE,
    GPS_POOR,
    VEHICLE_SLOWED,
    SENSOR_CLIPPING,
    STRONG_LATERAL_MOVEMENT,
    POTHOLE_LIKE_IMPULSE,
    MANUALLY_MARKED,
    SINGLE_SHARP_IMPULSE,
    NO_PAIRED_SEQUENCE,
    SIMULATED_EVENT,
    STRUCTURED_WAVEFORM,
    ISOLATED_EVENT,
    VERTICAL_DOMINANT,
    SUSPENSION_SETTLING,
    LONG_SMOOTH_PROFILE,
    DOUBLE_AXLE_PROFILE,
    LOW_PROFILE_EVENT,
    ROUGH_ROAD_CONTEXT,
    HIGH_FREQUENCY_VIBRATION,
    PHONE_MOVEMENT_LIKELY,
    TURNING_CONTEXT,
    HILL_OR_RAMP_LIKELY,
    SPEED_CONTEXT_UNCERTAIN,
    SENSOR_QUALITY_REDUCED,
    POST_EVENT_ACCELERATION,
}

@Serializable
enum class CandidateDecision { PENDING, CONFIRMED, REJECTED, NOT_SURE, POTHOLE, ROUGH_ROAD }

@Serializable
enum class DetectionQuality { FULL, REDUCED, BASIC, UNSUPPORTED }

@Serializable
enum class LocationQuality { GOOD, FAIR, POOR, STALE, UNAVAILABLE }

@Serializable
enum class PhonePlacementState { STABLE, UNSTABLE, UNKNOWN }

@Serializable
enum class DriveServiceState {
    IDLE,
    PREPARING,
    ACTIVE,
    PAUSED,
    GPS_DEGRADED,
    SENSOR_DEGRADED,
    PERMISSION_LOST,
    STOPPING,
    FAILED
}

@Serializable
enum class Sensitivity { CONSERVATIVE, BALANCED, SENSITIVE }

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float = Float.NaN,
    val bearingDegrees: Float = Float.NaN,
    val speedMetresPerSecond: Float = Float.NaN,
    val elapsedRealtimeNanos: Long = 0L,
    val epochMillis: Long = 0L,
    val speedAccuracyMetresPerSecond: Float = Float.NaN,
)

@Serializable
data class SpeedBump(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val rawLatitude: Double = latitude,
    val rawLongitude: Double = longitude,
    val horizontalAccuracyMetres: Float = Float.NaN,
    val coordinateConfidence: Float = 0.5f,
    val confidence: Float = 0.5f,
    val status: BumpStatus = BumpStatus.CONFIRMED,
    val source: BumpSource = BumpSource.DETECTED,
    val directionality: Directionality = Directionality.UNKNOWN,
    val primaryBearing: Float? = null,
    val oppositeBearing: Float? = null,
    val bearingTolerance: Float = 45f,
    val firstDetectedAt: Long = System.currentTimeMillis(),
    val lastDetectedAt: Long = firstDetectedAt,
    val lastWarnedAt: Long? = null,
    val encounterCount: Int = 1,
    val confirmationCount: Int = 1,
    val rejectionCount: Int = 0,
    val missingReports: Int = 0,
    val importedSource: String? = null,
    val notes: String = "",
    val warningEnabled: Boolean = true,
    val customWarningDistanceMetres: Int? = null,
    val algorithmVersion: Int = 2,
    val archived: Boolean = false,
    val markedRemoved: Boolean = false,
    val regionLabel: String? = null,
    val roadName: String? = null,
)

@Serializable
data class CandidateEvent(
    val id: String = UUID.randomUUID().toString(),
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
    val decision: CandidateDecision = CandidateDecision.PENDING,
    val confidenceReasons: List<ConfidenceReason> = emptyList(),
    val positiveVerticalPeak: Float = 0f,
    val negativeVerticalPeak: Float = 0f,
    val peakToPeak: Float = 0f,
    val peakGapMillis: Long = 0L,
    val verticalRms: Float = 0f,
    val jerkPeak: Float = 0f,
    val gyroscopeEnergy: Float = 0f,
    val orientationVariance: Float = 0f,
    val phoneStability: Float = 1f,
    val source: BumpSource = BumpSource.DETECTED,
    val note: String = "",
)

@Serializable
data class DriveSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMillis: Long = 0,
    val distanceMetres: Double = 0.0,
    val maximumSpeedMetresPerSecond: Float = 0f,
    val averageSpeedMetresPerSecond: Float = 0f,
    val candidateCount: Int = 0,
    val confirmedCount: Int = 0,
    val rejectedCount: Int = 0,
    val knownBumpPasses: Int = 0,
    val warningCount: Int = 0,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val incomplete: Boolean = true,
    val placementProfile: String = "automatic",
    val vehicleProfile: String? = null,
    val algorithmVersion: Int = 2,
    val serviceInterruptions: Int = 0,
    val detectionQuality: DetectionQuality = DetectionQuality.BASIC,
)


@Serializable
data class LocationTrackPoint(
    val driveId: String,
    val elapsedRealtimeNanos: Long,
    val epochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float,
    val bearingDegrees: Float?,
    val speedMetresPerSecond: Float?,
)

@Serializable
data class CalibrationProfile(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val accelerometerNoiseRms: Float,
    val gyroscopeNoiseRms: Float,
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val samplingConsistency: Float,
    val engineVibrationRms: Float,
    val sampleCount: Int,
    val algorithmVersion: Int = 2,
)
