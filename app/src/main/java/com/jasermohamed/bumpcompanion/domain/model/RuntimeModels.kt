package com.jasermohamed.bumpcompanion.domain.model

data class SensorCapabilities(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasGravity: Boolean = false,
    val hasLinearAcceleration: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasGameRotationVector: Boolean = false,
    val accelerometerMinDelayMicros: Int = 0,
    val accelerometerFifoMax: Int = 0,
) {
    val quality: DetectionQuality
        get() = when {
            !hasAccelerometer -> DetectionQuality.UNSUPPORTED
            hasGyroscope && (hasRotationVector || hasGameRotationVector) -> DetectionQuality.FULL
            hasRotationVector || hasGravity -> DetectionQuality.REDUCED
            else -> DetectionQuality.BASIC
        }
}

data class DriveRuntimeState(
    val serviceState: DriveServiceState = DriveServiceState.IDLE,
    val driveId: String? = null,
    val startedAt: Long? = null,
    val pausedAt: Long? = null,
    val currentSpeedMetresPerSecond: Float? = null,
    val distanceMetres: Double = 0.0,
    val gpsAccuracyMetres: Float? = null,
    val locationQuality: LocationQuality = LocationQuality.UNAVAILABLE,
    val detectionQuality: DetectionQuality = DetectionQuality.UNSUPPORTED,
    val phonePlacementState: PhonePlacementState = PhonePlacementState.UNKNOWN,
    val phoneStabilityScore: Float = 0f,
    val candidateCount: Int = 0,
    val knownBumpsPassed: Int = 0,
    val warningCount: Int = 0,
    val nextBumpDistanceMetres: Float? = null,
    val warningsMuted: Boolean = false,
    val lastLocation: GeoPoint? = null,
    val errorMessage: String? = null,
) {
    val isRunning: Boolean
        get() = serviceState in setOf(
            DriveServiceState.PREPARING,
            DriveServiceState.ACTIVE,
            DriveServiceState.PAUSED,
            DriveServiceState.GPS_DEGRADED,
            DriveServiceState.SENSOR_DEGRADED,
        )
}
