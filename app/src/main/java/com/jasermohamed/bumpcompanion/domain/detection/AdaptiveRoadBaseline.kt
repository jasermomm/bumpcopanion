package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.RoadSurfaceState
import kotlin.math.abs
import kotlin.math.sqrt

internal data class RoadBaselineSnapshot(
    val verticalRms: Float,
    val horizontalRms: Float,
    val jerkRms: Float,
    val highFrequencyRms: Float,
    val roughnessScore: Float,
    val surfaceState: RoadSurfaceState,
    val calibrationProgress: Float,
)

/**
 * Bounded, asymmetric baseline learning.  Quiet-road decreases are learned normally; rising noise
 * is learned slowly and candidate-sized outliers are excluded, preventing a long bad road from
 * making genuine bumps impossible to detect.
 */
internal class AdaptiveRoadBaseline(private var config: DetectorConfiguration) {
    private var verticalSquare = config.initialBaselineVerticalRms * config.initialBaselineVerticalRms
    private var horizontalSquare = 0.12f * 0.12f
    private var jerkSquare = 2f * 2f
    private var highFrequencySquare = 0.08f * 0.08f
    private var shortVerticalSquare = verticalSquare
    private var shortHighFrequencySquare = highFrequencySquare
    private var shortHorizontalSquare = horizontalSquare
    private var peakActivity = 0f
    private var learnedSeconds = 0f
    private var lastTimestampNanos = 0L

    fun updateConfiguration(configuration: DetectorConfiguration) {
        config = configuration
        verticalSquare = verticalSquare.coerceIn(
            config.minimumBaselineVerticalRms * config.minimumBaselineVerticalRms,
            config.maximumBaselineVerticalRms * config.maximumBaselineVerticalRms,
        )
    }

    fun update(sample: ProcessedSample, candidateActive: Boolean) {
        val dt = if (lastTimestampNanos == 0L) 1f / config.expectedSampleRateHz else
            ((sample.motion.elapsedRealtimeNanos - lastTimestampNanos) / 1_000_000_000f).coerceIn(0f, 0.2f)
        lastTimestampNanos = sample.motion.elapsedRealtimeNanos
        if (dt <= 0f) return

        // Two-second context follows the road quickly enough to mark sustained vibration.
        val shortAlpha = 1f - kotlin.math.exp((-dt / 2.0f).toDouble()).toFloat()
        shortVerticalSquare += shortAlpha * (sample.eventVertical.sq() - shortVerticalSquare)
        shortHighFrequencySquare += shortAlpha * (sample.highFrequencyVertical.sq() - shortHighFrequencySquare)
        shortHorizontalSquare += shortAlpha * (sample.horizontalBand.sq() - shortHorizontalSquare)
        val currentVerticalRms = sqrt(verticalSquare).coerceAtLeast(config.minimumBaselineVerticalRms)
        val isPeak = if (abs(sample.eventVertical) > currentVerticalRms * 2.2f + 0.25f) 1f else 0f
        peakActivity += shortAlpha * (isPeak - peakActivity)

        val isOutlier = abs(sample.eventVertical) > currentVerticalRms * config.baselineOutlierSigma + 0.35f
        if (!candidateActive && !isOutlier && !sample.sampleGap) {
            val normalAlpha = 1f - kotlin.math.exp((-dt / config.baselineTimeConstantSeconds).toDouble()).toFloat()
            verticalSquare = adaptSquare(verticalSquare, sample.eventVertical.sq(), normalAlpha)
            horizontalSquare = adaptSquare(horizontalSquare, sample.horizontalBand.sq(), normalAlpha)
            jerkSquare = adaptSquare(jerkSquare, sample.verticalJerk.sq(), normalAlpha)
            highFrequencySquare = adaptSquare(highFrequencySquare, sample.highFrequencyVertical.sq(), normalAlpha)
            val minSq = config.minimumBaselineVerticalRms.sq()
            val maxSq = config.maximumBaselineVerticalRms.sq()
            verticalSquare = verticalSquare.coerceIn(minSq, maxSq)
            learnedSeconds = (learnedSeconds + dt).coerceAtMost(config.automaticCalibrationSeconds)
        }
    }

    fun snapshot(): RoadBaselineSnapshot {
        val vertical = sqrt(verticalSquare).coerceIn(config.minimumBaselineVerticalRms, config.maximumBaselineVerticalRms)
        val shortVertical = sqrt(shortVerticalSquare.coerceAtLeast(0f))
        val shortHigh = sqrt(shortHighFrequencySquare.coerceAtLeast(0f))
        val shortHorizontal = sqrt(shortHorizontalSquare.coerceAtLeast(0f))
        val elevatedBroadband = normalized(shortVertical / vertical.coerceAtLeast(0.08f), 1.35f, 3.6f)
        val highFrequency = normalized(shortHigh / (sqrt(highFrequencySquare).coerceAtLeast(0.05f)), 1.4f, 4.5f)
        val multiAxis = normalized(shortHorizontal / (shortVertical + 0.08f), 0.55f, 1.8f)
        val density = normalized(peakActivity, 0.025f, 0.20f)
        val roughness = (0.34f * elevatedBroadband + 0.31f * highFrequency + 0.17f * multiAxis + 0.18f * density)
            .coerceIn(0f, 1f)
        val surface = when {
            roughness >= config.veryRoughRoadThreshold -> RoadSurfaceState.VERY_ROUGH
            roughness >= config.roughRoadThreshold -> RoadSurfaceState.ROUGH
            roughness < 0.22f -> RoadSurfaceState.SMOOTH
            else -> RoadSurfaceState.NORMAL
        }
        return RoadBaselineSnapshot(
            verticalRms = vertical,
            horizontalRms = sqrt(horizontalSquare.coerceAtLeast(0f)),
            jerkRms = sqrt(jerkSquare.coerceAtLeast(0f)),
            highFrequencyRms = sqrt(highFrequencySquare.coerceAtLeast(0f)),
            roughnessScore = roughness,
            surfaceState = surface,
            calibrationProgress = (learnedSeconds / config.automaticCalibrationSeconds).coerceIn(0f, 1f),
        )
    }

    fun reset() {
        verticalSquare = config.initialBaselineVerticalRms.sq()
        horizontalSquare = 0.12f.sq()
        jerkSquare = 2f.sq()
        highFrequencySquare = 0.08f.sq()
        shortVerticalSquare = verticalSquare
        shortHighFrequencySquare = highFrequencySquare
        shortHorizontalSquare = horizontalSquare
        peakActivity = 0f
        learnedSeconds = 0f
        lastTimestampNanos = 0L
    }

    private fun adaptSquare(current: Float, observed: Float, alpha: Float): Float {
        val boundedObserved = observed.coerceAtMost(current * 9f + 0.04f)
        val rate = if (boundedObserved > current) alpha * config.baselineRiseRateMultiplier else alpha
        return current + rate * (boundedObserved - current)
    }
}

private fun Float.sq(): Float = this * this
