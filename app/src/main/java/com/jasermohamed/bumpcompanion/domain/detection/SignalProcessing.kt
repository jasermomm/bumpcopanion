package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.LocationQuality
import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Internal frame shared by live collection and CSV replay after timestamp-aware filtering. */
internal data class ProcessedSample(
    val motion: MotionSample,
    val speedMetresPerSecond: Float,
    val speedAccuracyMetresPerSecond: Float,
    val locationQuality: LocationQuality,
    val phoneStabilityScore: Float,
    val eventVertical: Float,
    val broadVertical: Float,
    val highFrequencyVertical: Float,
    val envelope: Float,
    val verticalJerk: Float,
    val horizontalBand: Float,
    val gyroMagnitude: Float,
    val sampleGap: Boolean,
)

/** A one-pole low pass whose coefficient follows the real Android sensor timestamp interval. */
private class TimedLowPass(private var cutoffHz: Float) {
    private var value = 0f
    private var lastTimestampNanos = 0L
    private var initialized = false

    fun update(input: Float, timestampNanos: Long): Float {
        if (!initialized || timestampNanos <= lastTimestampNanos) {
            value = input
            initialized = true
            lastTimestampNanos = timestampNanos
            return value
        }
        val dt = ((timestampNanos - lastTimestampNanos) / 1_000_000_000.0).coerceIn(0.0005, 0.25)
        lastTimestampNanos = timestampNanos
        val rc = 1.0 / (2.0 * PI * cutoffHz.coerceAtLeast(0.01f))
        val alpha = (dt / (rc + dt)).toFloat()
        value += alpha * (input - value)
        return value
    }

    fun reset() {
        value = 0f
        lastTimestampNanos = 0L
        initialized = false
    }
}

/** Three-point median removes isolated callback glitches without smearing an automotive impulse. */
private class MedianOfThree {
    private var first = 0f
    private var second = 0f
    private var count = 0

    fun update(value: Float): Float {
        if (count == 0) {
            first = value
            second = value
            count = 1
            return value
        }
        if (count == 1) {
            second = value
            count = 2
            return (first + value) * 0.5f
        }
        val result = median(first, second, value)
        first = second
        second = value
        return result
    }

    fun reset() { count = 0 }

    private fun median(a: Float, b: Float, c: Float): Float = when {
        a > b -> if (b > c) b else if (a > c) c else a
        else -> if (a > c) a else if (b > c) c else b
    }
}

/**
 * Produces complementary motion bands:
 * - 0.2-5.2 Hz: coherent chassis event;
 * - below 1.35 Hz: wide hump / ramp evidence;
 * - above 9 Hz: cracks, gravel, mount buzz and rough-road evidence.
 */
internal class SignalProcessor(private var config: DetectorConfiguration) {
    private val median = MedianOfThree()
    private var drift = TimedLowPass(config.driftLowPassCutoffHz)
    private var eventLow = TimedLowPass(config.eventLowPassCutoffHz)
    private var broadLow = TimedLowPass(config.broadMotionLowPassCutoffHz)
    private var highSplitLow = TimedLowPass(config.highFrequencySplitHz)
    private var envelopeLow = TimedLowPass(config.envelopeLowPassCutoffHz)
    private var horizontalLow = TimedLowPass(config.eventLowPassCutoffHz)
    private var lastEventVertical = 0f
    private var lastTimestampNanos = 0L

    fun updateConfiguration(configuration: DetectorConfiguration) {
        if (configuration == config) return
        config = configuration
        reset()
        drift = TimedLowPass(config.driftLowPassCutoffHz)
        eventLow = TimedLowPass(config.eventLowPassCutoffHz)
        broadLow = TimedLowPass(config.broadMotionLowPassCutoffHz)
        highSplitLow = TimedLowPass(config.highFrequencySplitHz)
        envelopeLow = TimedLowPass(config.envelopeLowPassCutoffHz)
        horizontalLow = TimedLowPass(config.eventLowPassCutoffHz)
    }

    fun process(
        sample: MotionSample,
        speedMetresPerSecond: Float,
        speedAccuracyMetresPerSecond: Float,
        locationQuality: LocationQuality,
        phoneStabilityScore: Float,
    ): ProcessedSample {
        val gap = lastTimestampNanos != 0L &&
            sample.elapsedRealtimeNanos - lastTimestampNanos > config.maximumContinuousSampleGapMillis * 1_000_000L
        if (gap) resetFiltersOnly()

        val cleanVertical = median.update(sample.verticalAcceleration.finiteOrZero())
        val eventLowValue = eventLow.update(cleanVertical, sample.elapsedRealtimeNanos)
        val driftValue = drift.update(cleanVertical, sample.elapsedRealtimeNanos)
        val eventVertical = eventLowValue - driftValue
        val broad = broadLow.update(cleanVertical, sample.elapsedRealtimeNanos) - driftValue
        val highFrequency = cleanVertical - highSplitLow.update(cleanVertical, sample.elapsedRealtimeNanos)
        val envelope = envelopeLow.update(abs(eventVertical), sample.elapsedRealtimeNanos)
        val dt = if (lastTimestampNanos == 0L) 0f else
            (sample.elapsedRealtimeNanos - lastTimestampNanos) / 1_000_000_000f
        val jerk = if (dt in 0.001f..0.2f) ((eventVertical - lastEventVertical) / dt).coerceIn(-300f, 300f) else 0f
        lastTimestampNanos = sample.elapsedRealtimeNanos
        lastEventVertical = eventVertical

        val horizontalMagnitude = sqrt(
            sample.longitudinalAcceleration * sample.longitudinalAcceleration +
                sample.lateralAcceleration * sample.lateralAcceleration
        )
        val horizontalBand = horizontalLow.update(horizontalMagnitude, sample.elapsedRealtimeNanos)
        val gyroMagnitude = sqrt(
            sample.gyroscopeX * sample.gyroscopeX +
                sample.gyroscopeY * sample.gyroscopeY +
                sample.gyroscopeZ * sample.gyroscopeZ
        )
        return ProcessedSample(
            motion = sample,
            speedMetresPerSecond = speedMetresPerSecond,
            speedAccuracyMetresPerSecond = speedAccuracyMetresPerSecond,
            locationQuality = locationQuality,
            phoneStabilityScore = phoneStabilityScore.coerceIn(0f, 1f),
            eventVertical = eventVertical,
            broadVertical = broad,
            highFrequencyVertical = highFrequency,
            envelope = envelope,
            verticalJerk = jerk,
            horizontalBand = horizontalBand,
            gyroMagnitude = gyroMagnitude,
            sampleGap = gap,
        )
    }

    fun reset() {
        resetFiltersOnly()
        lastTimestampNanos = 0L
    }

    private fun resetFiltersOnly() {
        median.reset()
        drift.reset()
        eventLow.reset()
        broadLow.reset()
        highSplitLow.reset()
        envelopeLow.reset()
        horizontalLow.reset()
        lastEventVertical = 0f
    }
}

internal fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

internal fun normalized(value: Float, low: Float, high: Float): Float {
    if (high <= low) return if (value >= high) 1f else 0f
    return ((value - low) / (high - low)).coerceIn(0f, 1f)
}

internal fun gaussianScore(value: Float, centre: Float, spread: Float): Float {
    if (spread <= 0f) return 0f
    val z = (value - centre) / spread
    return exp((-0.5f * z * z).toDouble()).toFloat().coerceIn(0f, 1f)
}
