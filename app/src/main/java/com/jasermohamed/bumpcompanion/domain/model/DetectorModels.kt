package com.jasermohamed.bumpcompanion.domain.model

/**
 * Timestamped, orientation-normalized input accepted by the detector core.
 *
 * [worldAccelerationEast] and [worldAccelerationNorth] are optional.  Live Android collection
 * supplies them when a rotation vector is available; replay files may omit them and provide the
 * already vehicle-aligned longitudinal/lateral components instead.
 */
data class MotionSample(
    val elapsedRealtimeNanos: Long,
    val epochMillis: Long,
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val gyroscopeX: Float = 0f,
    val gyroscopeY: Float = 0f,
    val gyroscopeZ: Float = 0f,
    val verticalAcceleration: Float,
    val longitudinalAcceleration: Float = 0f,
    val lateralAcceleration: Float = 0f,
    val orientationChangeRadians: Float = 0f,
    val orientationReliable: Boolean = false,
    val worldAccelerationEast: Float = Float.NaN,
    val worldAccelerationNorth: Float = Float.NaN,
    val linearAccelerationAvailable: Boolean = false,
    val linearAccelerationX: Float = Float.NaN,
    val linearAccelerationY: Float = Float.NaN,
    val linearAccelerationZ: Float = Float.NaN,
    val gravityX: Float = Float.NaN,
    val gravityY: Float = Float.NaN,
    val gravityZ: Float = Float.NaN,
    val rotationVectorX: Float = Float.NaN,
    val rotationVectorY: Float = Float.NaN,
    val rotationVectorZ: Float = Float.NaN,
    val rotationVectorW: Float = Float.NaN,
)

enum class BumpProfile {
    SHORT_SHARP,
    LONG_SMOOTH_HUMP,
    FLAT_TOP_TABLE,
    LOW_PROFILE,
    DOUBLE_AXLE,
    ASYMMETRIC,
    CONSECUTIVE,
    UNCLASSIFIED,
}

enum class RoadSurfaceState { SMOOTH, NORMAL, ROUGH, VERY_ROUGH }

enum class DetectorState { CALIBRATING, NORMAL, CAPTURING, SETTLING, REFRACTORY, SUPPRESSED }

enum class DetectionDisposition { REJECTED, LOCAL_CANDIDATE, DATABASE_WORTHY }

/**
 * A stable, ML-ready feature vector.  Existing persisted candidate columns remain a deliberately
 * small subset of this object, while diagnostic CSV captures the full vector for later training.
 */
data class EventFeatures(
    val eventElapsedRealtimeNanos: Long,
    val positiveVerticalPeak: Float,
    val negativeVerticalPeak: Float,
    val peakToPeak: Float,
    val positivePeakTimeNanos: Long,
    val negativePeakTimeNanos: Long,
    val peakGapMillis: Long,
    val durationMillis: Long,
    val verticalRms: Float,
    val verticalEnergy: Float,
    val jerkPeak: Float,
    val longitudinalBefore: Float,
    val longitudinalDuring: Float,
    val lateralEnergy: Float,
    val gyroscopeEnergy: Float,
    val orientationVariance: Float,
    val sensorSaturated: Boolean,
    val baselineNoiseRatio: Float,
    val horizontalRms: Float = 0f,
    val jerkRms: Float = 0f,
    val highFrequencyRms: Float = 0f,
    val highFrequencyRatio: Float = 0f,
    val lowFrequencyRms: Float = 0f,
    val zeroCrossingCount: Int = 0,
    val prominentPeakCount: Int = 0,
    val coherentPairCount: Int = 0,
    val dominantPeakWidthMillis: Long = 0L,
    val eventIsolation: Float = 0f,
    val verticalDominance: Float = 0f,
    val waveformSymmetry: Float = 0f,
    val lobeBalance: Float = 0f,
    val settlingScore: Float = 0f,
    val wholeVehicleCoherence: Float = 0f,
    val approximateVerticalDisplacementMetres: Float = 0f,
    val preEventVerticalRms: Float = 0f,
    val postEventVerticalRms: Float = 0f,
    val preEventSpeedMetresPerSecond: Float = Float.NaN,
    val minimumEventSpeedMetresPerSecond: Float = Float.NaN,
    val postEventSpeedMetresPerSecond: Float = Float.NaN,
    val absoluteSpeedReductionMetresPerSecond: Float = 0f,
    val relativeSpeedReduction: Float = 0f,
    val decelerationRateMetresPerSecondSquared: Float = 0f,
    val accelerationRateMetresPerSecondSquared: Float = 0f,
    val brakingEvidence: Float = 0f,
    val postAccelerationEvidence: Float = 0f,
    val gyroscopeRms: Float = 0f,
    val maximumOrientationStepRadians: Float = 0f,
    val orientationReliability: Float = 0f,
    val sampleRateHz: Float = 0f,
    val droppedSampleFraction: Float = 0f,
    val signalQuality: Float = 0f,
    val roughnessScore: Float = 0f,
    val potholeLikelihood: Float = 0f,
    val phoneMovementLikelihood: Float = 0f,
    val turnLikelihood: Float = 0f,
    val hillOrRampLikelihood: Float = 0f,
    val bumpWaveformScore: Float = 0f,
    val profile: BumpProfile = BumpProfile.UNCLASSIFIED,
    val profileScores: Map<BumpProfile, Float> = emptyMap(),
    val roadSurfaceState: RoadSurfaceState = RoadSurfaceState.NORMAL,
) {
    /** Fixed-order numeric vector for offline model experiments. */
    fun toMlFeatureVector(): FloatArray = floatArrayOf(
        positiveVerticalPeak,
        negativeVerticalPeak,
        peakToPeak,
        peakGapMillis.toFloat(),
        durationMillis.toFloat(),
        verticalRms,
        horizontalRms,
        jerkPeak,
        jerkRms,
        highFrequencyRms,
        highFrequencyRatio,
        lowFrequencyRms,
        zeroCrossingCount.toFloat(),
        prominentPeakCount.toFloat(),
        coherentPairCount.toFloat(),
        dominantPeakWidthMillis.toFloat(),
        eventIsolation,
        verticalDominance,
        waveformSymmetry,
        lobeBalance,
        settlingScore,
        wholeVehicleCoherence,
        approximateVerticalDisplacementMetres,
        preEventVerticalRms,
        postEventVerticalRms,
        preEventSpeedMetresPerSecond.finiteOrZero(),
        minimumEventSpeedMetresPerSecond.finiteOrZero(),
        postEventSpeedMetresPerSecond.finiteOrZero(),
        absoluteSpeedReductionMetresPerSecond,
        relativeSpeedReduction,
        decelerationRateMetresPerSecondSquared,
        accelerationRateMetresPerSecondSquared,
        brakingEvidence,
        postAccelerationEvidence,
        gyroscopeRms,
        maximumOrientationStepRadians,
        orientationReliability,
        sampleRateHz,
        droppedSampleFraction,
        signalQuality,
        roughnessScore,
        potholeLikelihood,
        phoneMovementLikelihood,
        turnLikelihood,
        hillOrRampLikelihood,
        bumpWaveformScore,
    )
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

data class ScoreContribution(
    val name: String,
    /** Positive values add evidence; negative values are explicit false-positive penalties. */
    val value: Float,
    val detail: String,
)

data class CandidateEvaluation(
    val candidateNumber: Long,
    val detectorState: DetectorState,
    val eventType: RoadEventType,
    val disposition: DetectionDisposition,
    val confidence: Float,
    val databaseConfidence: Float,
    val features: EventFeatures?,
    val contributions: List<ScoreContribution>,
    val explanation: String,
)

data class DetectorTelemetryFrame(
    val sample: MotionSample,
    val speedMetresPerSecond: Float,
    val speedAccuracyMetresPerSecond: Float,
    val locationQuality: LocationQuality,
    val phoneStabilityScore: Float,
    val eventVerticalAcceleration: Float,
    val lowFrequencyVerticalAcceleration: Float,
    val highFrequencyVerticalAcceleration: Float,
    val verticalEnvelope: Float,
    val verticalJerk: Float,
    val horizontalAcceleration: Float,
    val baselineVerticalRms: Float,
    val baselineHighFrequencyRms: Float,
    val roadSurfaceState: RoadSurfaceState,
    val detectorState: DetectorState,
)

interface DetectorDiagnosticsListener {
    fun onFrame(frame: DetectorTelemetryFrame) = Unit
    fun onCandidate(evaluation: CandidateEvaluation) = Unit
}

data class DetectedRoadEvent(
    val eventType: RoadEventType,
    val confidence: Float,
    val features: EventFeatures,
    val confidenceReasons: List<ConfidenceReason>,
    val profile: BumpProfile = features.profile,
    val disposition: DetectionDisposition = DetectionDisposition.LOCAL_CANDIDATE,
    val databaseConfidence: Float = confidence,
    val scoreContributions: List<ScoreContribution> = emptyList(),
    val explanation: String = "",
)
