package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.Sensitivity

/** Positive-evidence weights are kept separate from penalties so field tuning remains auditable. */
data class ConfidenceWeights(
    val waveform: Float = 0.30f,
    val isolation: Float = 0.13f,
    val verticalDominance: Float = 0.10f,
    val signalToNoise: Float = 0.10f,
    val duration: Float = 0.07f,
    val settling: Float = 0.06f,
    val wholeVehicleCoherence: Float = 0.06f,
    val signalQuality: Float = 0.06f,
    val brakingContext: Float = 0.035f,
    val postAccelerationContext: Float = 0.025f,
    val roughRoadPenalty: Float = 0.30f,
    val potholePenalty: Float = 0.37f,
    val phoneMovementPenalty: Float = 0.52f,
    val turnPenalty: Float = 0.13f,
    val hillOrRampPenalty: Float = 0.23f,
    val highFrequencyPenalty: Float = 0.14f,
    val clippingPenalty: Float = 0.30f,
)

/**
 * All detector constants live here.  Values are SI units unless their names specify otherwise.
 * Increasing an evidence threshold makes the detector more selective; increasing a penalty makes
 * the named false-positive family less likely to be emitted.
 */
data class DetectorConfiguration(
    val version: Int = 2,

    /** Target Android accelerometer rate; actual timestamp intervals are still used by filters. */
    val expectedSampleRateHz: Float = 100f,
    /** Retained history supports baseline, pre-event, event, and post-event analysis. */
    val historySeconds: Float = 10f,
    /** Samples older than this before a trigger form context rather than the physical event. */
    val preEventWindowMillis: Long = 1_600L,
    /** Events shorter than this are normally cracks or sensor glitches. */
    val minimumEventDurationMillis: Long = 120L,
    /** Longer candidates are normally ramps, hills, or sustained roughness. */
    val maximumEventDurationMillis: Long = 4_800L,
    /** Quiet time required before front/rear axle impulses are considered complete. */
    val eventEndQuietMillis: Long = 320L,
    /** Extra settling context collected after the event envelope becomes quiet. */
    val postEventWindowMillis: Long = 430L,
    /** Short same-event guard; unlike a fixed multi-second cooldown it permits nearby bumps. */
    val sameEventRefractoryMillis: Long = 260L,

    /** Below this reliable speed, ordinary candidates do not arm. */
    val minimumSpeedMetresPerSecond: Float = 1.25f,
    /** A last reliable moving fix may bridge this much GPS dropout. */
    val recentMovingMemoryMillis: Long = 5_000L,
    /** At uncertain zero speed only exceptionally strong motion can arm. */
    val gpsDropoutStrongSignalMultiplier: Float = 2.6f,
    /** Accuracy above this makes speed contextual evidence weak, but never fabricates motion. */
    val poorSpeedAccuracyMetresPerSecond: Float = 2.5f,

    /** Removes gravity leakage, slopes, and very slow mount drift. */
    val driftLowPassCutoffHz: Float = 0.20f,
    /** Upper edge of the chassis-motion band used for event shape. */
    val eventLowPassCutoffHz: Float = 5.2f,
    /** Separates road/mount buzz from coherent chassis movement. */
    val highFrequencySplitHz: Float = 9.0f,
    /** Envelope smoothing avoids arming on one callback. */
    val envelopeLowPassCutoffHz: Float = 2.2f,
    /** Slow band used to retain wide humps and reject long ramps. */
    val broadMotionLowPassCutoffHz: Float = 1.35f,

    /** Absolute floor prevents accelerometer quantization/noise from becoming a candidate. */
    val minimumPhysicalPeakMetresPerSecondSquared: Float = 0.62f,
    /** Adaptive trigger is max(floor, baseline RMS times this ratio). */
    val candidateSignalToNoiseRatio: Float = 3.0f,
    /** Envelope must fall below this fraction of trigger before settling begins. */
    val eventReleaseRatio: Float = 0.44f,
    /** Opposite lobes weaker than this fraction of the main lobe provide little bump evidence. */
    val minimumOpposingLobeRatio: Float = 0.23f,
    /** Plausible lobe separation keeps cracks from masquerading as a chassis trajectory. */
    val minimumPeakGapMillis: Long = 55L,
    /** Wide humps remain valid up to this lobe spacing. */
    val maximumPeakGapMillis: Long = 2_900L,

    /** Initial noise floor before automatic driving calibration has enough samples. */
    val initialBaselineVerticalRms: Float = 0.22f,
    /** Baseline may fall to this value on exceptionally smooth roads. */
    val minimumBaselineVerticalRms: Float = 0.10f,
    /** Caps baseline poisoning during long rough sections. */
    val maximumBaselineVerticalRms: Float = 1.35f,
    /** Nominal time constant for benign baseline adaptation. */
    val baselineTimeConstantSeconds: Float = 24f,
    /** Upward adaptation is this fraction as fast as downward adaptation. */
    val baselineRiseRateMultiplier: Float = 0.14f,
    /** Candidate-like samples above this many baseline RMS are excluded from learning. */
    val baselineOutlierSigma: Float = 2.8f,
    /** Enough normal samples for full automatic-calibration quality. */
    val automaticCalibrationSeconds: Float = 8f,

    /** Sustained high-frequency/peak-density score entering rough-road mode. */
    val roughRoadThreshold: Float = 0.58f,
    /** Stronger score marks very rough terrain and raises structural requirements. */
    val veryRoughRoadThreshold: Float = 0.78f,
    /** Rough-road candidates need at least this isolated waveform evidence. */
    val roughRoadMinimumWaveformScore: Float = 0.66f,
    /** Likelihood above this is treated as a strong pothole warning. */
    val strongPotholeLikelihood: Float = 0.74f,
    /** Likelihood above this represents obvious independent phone manipulation. */
    val severePhoneMovementLikelihood: Float = 0.78f,
    /** Orientation steps larger than this are rarely produced by a mounted vehicle body. */
    val phoneOrientationStepRadians: Float = 0.15f,
    /** Raw accelerations near this magnitude indicate clipping/drop/violent manipulation. */
    val sensorSaturationMetresPerSecondSquared: Float = 75f,
    /** Timestamp gaps larger than this reset recursive filters instead of inventing a transition. */
    val maximumContinuousSampleGapMillis: Long = 180L,

    /** Local candidate threshold; changing sensitivity primarily changes this plus SNR/shape. */
    val localCandidateThreshold: Float = 0.59f,
    /** Above this a candidate is labeled likely rather than possible. */
    val probableThreshold: Float = 0.72f,
    /** A single-device event must exceed this before it is database-worthy evidence. */
    val databaseWorthyThreshold: Float = 0.86f,
    /** Minimum profile match even in sensitive mode; false-positive safeguards remain active. */
    val minimumWaveformScore: Float = 0.36f,
    /** Small score offset used only after all physical/rejection features are evaluated. */
    val sensitivityScoreOffset: Float = 0f,
    val weights: ConfidenceWeights = ConfidenceWeights(),
) {
    // Compatibility aliases for callers that previously displayed these coarse constants.
    val triggerAccelerationMetresPerSecondSquared: Float get() = minimumPhysicalPeakMetresPerSecondSquared
    val minimumOpposingPeakMetresPerSecondSquared: Float get() = minimumPhysicalPeakMetresPerSecondSquared * minimumOpposingLobeRatio
    val minimumPeakToPeakMetresPerSecondSquared: Float get() = minimumPhysicalPeakMetresPerSecondSquared * 1.5f
    val maximumGyroscopeEnergy: Float get() = 2.4f
    val maximumOrientationVariance: Float get() = phoneOrientationStepRadians * phoneOrientationStepRadians
    val refractoryMillis: Long get() = sameEventRefractoryMillis
    val baselineNoiseMetresPerSecondSquared: Float get() = initialBaselineVerticalRms
    val lowConfidenceThreshold: Float get() = localCandidateThreshold
    val strongThreshold: Float get() = databaseWorthyThreshold

    companion object {
        fun forSensitivity(sensitivity: Sensitivity): DetectorConfiguration {
            val balanced = DetectorConfiguration()
            return when (sensitivity) {
                Sensitivity.CONSERVATIVE -> balanced.copy(
                    minimumSpeedMetresPerSecond = 1.55f,
                    minimumPhysicalPeakMetresPerSecondSquared = 0.78f,
                    candidateSignalToNoiseRatio = 3.45f,
                    roughRoadMinimumWaveformScore = 0.72f,
                    localCandidateThreshold = 0.67f,
                    probableThreshold = 0.78f,
                    databaseWorthyThreshold = 0.90f,
                    minimumWaveformScore = 0.44f,
                    sensitivityScoreOffset = -0.02f,
                )
                Sensitivity.BALANCED -> balanced
                Sensitivity.SENSITIVE -> balanced.copy(
                    minimumSpeedMetresPerSecond = 0.90f,
                    minimumPhysicalPeakMetresPerSecondSquared = 0.48f,
                    candidateSignalToNoiseRatio = 2.55f,
                    roughRoadMinimumWaveformScore = 0.61f,
                    localCandidateThreshold = 0.53f,
                    probableThreshold = 0.67f,
                    databaseWorthyThreshold = 0.83f,
                    minimumWaveformScore = 0.31f,
                    sensitivityScoreOffset = 0.035f,
                )
            }
        }
    }
}
