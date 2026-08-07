package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.BumpProfile
import com.jasermohamed.bumpcompanion.domain.model.EventFeatures
import com.jasermohamed.bumpcompanion.domain.model.LocationQuality
import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Event-window feature extraction is allocation-tolerant because it runs only after a candidate. */
class FeatureExtractor {
    /** Compatibility entry point used by focused unit tests and offline feature tooling. */
    fun extract(samples: List<MotionSample>, baselineNoise: Float): EventFeatures? {
        if (samples.size < 8) return null
        var previous = samples.first()
        val processed = samples.map { sample ->
            val dt = (sample.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000f
            val jerk = if (dt > 0f) (sample.verticalAcceleration - previous.verticalAcceleration) / dt else 0f
            previous = sample
            ProcessedSample(
                motion = sample,
                speedMetresPerSecond = Float.NaN,
                speedAccuracyMetresPerSecond = Float.NaN,
                locationQuality = LocationQuality.UNAVAILABLE,
                phoneStabilityScore = 1f,
                eventVertical = sample.verticalAcceleration,
                broadVertical = sample.verticalAcceleration,
                highFrequencyVertical = 0f,
                envelope = abs(sample.verticalAcceleration),
                verticalJerk = jerk,
                horizontalBand = sqrt(
                    sample.longitudinalAcceleration * sample.longitudinalAcceleration +
                        sample.lateralAcceleration * sample.lateralAcceleration
                ),
                gyroMagnitude = sqrt(
                    sample.gyroscopeX * sample.gyroscopeX + sample.gyroscopeY * sample.gyroscopeY +
                        sample.gyroscopeZ * sample.gyroscopeZ
                ),
                sampleGap = false,
            )
        }
        val baseline = RoadBaselineSnapshot(
            verticalRms = baselineNoise.coerceAtLeast(0.05f),
            horizontalRms = 0.1f,
            jerkRms = 1f,
            highFrequencyRms = 0.05f,
            roughnessScore = 0f,
            surfaceState = com.jasermohamed.bumpcompanion.domain.model.RoadSurfaceState.NORMAL,
            calibrationProgress = 1f,
        )
        return extractProcessed(processed, baseline, DetectorConfiguration())
    }

    internal fun extractProcessed(
        samples: List<ProcessedSample>,
        baseline: RoadBaselineSnapshot,
        config: DetectorConfiguration,
    ): EventFeatures? {
        if (samples.size < 8) return null
        val activityThreshold = max(
            config.minimumPhysicalPeakMetresPerSecondSquared * 0.35f,
            baseline.verticalRms * 1.55f,
        )
        val activeIndices = samples.indices.filter { abs(samples[it].eventVertical) >= activityThreshold }
        if (activeIndices.isEmpty()) return null
        val activeStart = (activeIndices.first() - 2).coerceAtLeast(0)
        val activeEnd = (activeIndices.last() + 2).coerceAtMost(samples.lastIndex)
        val event = samples.subList(activeStart, activeEnd + 1)
        val pre = samples.subList(0, activeStart)
        val post = if (activeEnd < samples.lastIndex) samples.subList(activeEnd + 1, samples.size) else emptyList()

        val positive = event.maxByOrNull { it.eventVertical } ?: return null
        val negative = event.minByOrNull { it.eventVertical } ?: return null
        val positivePeak = positive.eventVertical
        val negativePeak = negative.eventVertical
        val peakToPeak = positivePeak - negativePeak
        val eventTime = if (abs(positivePeak) >= abs(negativePeak)) {
            positive.motion.elapsedRealtimeNanos
        } else {
            negative.motion.elapsedRealtimeNanos
        }
        val peakGapMillis = abs(positive.motion.elapsedRealtimeNanos - negative.motion.elapsedRealtimeNanos) / 1_000_000L
        val durationMillis = (event.last().motion.elapsedRealtimeNanos - event.first().motion.elapsedRealtimeNanos) / 1_000_000L

        val verticalRms = rms(event) { it.eventVertical }
        // Remove the event-window horizontal mean: steady braking/centripetal acceleration is
        // context, while shocks and phone handling are dynamic multi-axis motion.
        val horizontalRms = dynamicLateralRms(event)
        val jerkRms = rms(event) { it.verticalJerk }
        val highFrequencyRms = rms(event) { it.highFrequencyVertical }
        val lowFrequencyRms = rms(event) { it.broadVertical }
        val gyroRms = rms(event) { it.gyroMagnitude }
        val jerkPeak = event.maxOf { abs(it.verticalJerk) }
        val verticalEnergy = event.sumOf { it.eventVertical.toDouble() * it.eventVertical }.toFloat()
        val lateralEnergy = event.sumOf { it.motion.lateralAcceleration.toDouble() * it.motion.lateralAcceleration }
            .div(event.size).toFloat()
        val gyroEnergy = event.sumOf { it.gyroMagnitude.toDouble() * it.gyroMagnitude }
            .div(event.size).toFloat()
        val orientationMean = event.map { it.motion.orientationChangeRadians }.average().toFloat()
        val orientationVariance = event.sumOf {
            val delta = it.motion.orientationChangeRadians - orientationMean
            delta.toDouble() * delta
        }.div(event.size).toFloat()
        val orientationMax = event.maxOf { abs(it.motion.orientationChangeRadians) }

        val peakThreshold = max(activityThreshold, max(abs(positivePeak), abs(negativePeak)) * 0.24f)
        val peaks = localPeaks(event, peakThreshold, config.minimumPeakGapMillis)
        val coherentPairs = coherentPairCount(peaks, config)
        val zeroCrossings = zeroCrossings(event, activityThreshold * 0.30f)
        val dominantWidth = dominantPeakWidthMillis(event)
        val preRms = if (pre.isEmpty()) baseline.verticalRms else rms(pre) { it.eventVertical }
        val postRms = if (post.isEmpty()) baseline.verticalRms else rms(post) { it.eventVertical }
        val contextRms = max(preRms, postRms)
        val isolation = (
            0.72f * normalized(verticalRms / contextRms.coerceAtLeast(0.06f), 1.45f, 5.5f) +
                0.28f * (1f - normalized(contextRms, baseline.verticalRms * 1.7f, baseline.verticalRms * 4.2f + 0.2f))
            ).coerceIn(0f, 1f)
        val verticalDominance = (verticalRms * verticalRms /
            (verticalRms * verticalRms + horizontalRms * horizontalRms + 0.01f)).coerceIn(0f, 1f)
        val lobeBalance = min(abs(positivePeak), abs(negativePeak)) /
            max(abs(positivePeak), abs(negativePeak)).coerceAtLeast(0.05f)
        val riseDuration = abs(eventTime - event.first().motion.elapsedRealtimeNanos).coerceAtLeast(1L)
        val fallDuration = abs(event.last().motion.elapsedRealtimeNanos - eventTime).coerceAtLeast(1L)
        val waveformSymmetry = min(riseDuration, fallDuration).toFloat() / max(riseDuration, fallDuration).toFloat()
        val highFrequencyRatio = highFrequencyRms / (highFrequencyRms + verticalRms + 0.05f)
        val postDecay = if (post.size >= 4) {
            val half = post.size / 2
            val early = rms(post.subList(0, half.coerceAtLeast(1))) { it.eventVertical }
            val late = rms(post.subList(half.coerceAtMost(post.lastIndex), post.size)) { it.eventVertical }
            normalized(early - late, -baseline.verticalRms, baseline.verticalRms * 2f + 0.15f)
        } else 0.5f
        val settling = (0.58f * (1f - normalized(postRms / baseline.verticalRms.coerceAtLeast(0.08f), 1.2f, 4f)) +
            0.42f * postDecay).coerceIn(0f, 1f)
        val gyroCoherence = 1f - normalized(gyroRms, 0.35f, 2.2f)
        val wholeVehicleCoherence = (0.66f * verticalDominance + 0.20f * gyroCoherence +
            0.14f * lobeBalance).coerceIn(0f, 1f)
        val displacement = approximateDisplacement(event)

        val preSpeed = medianFinite(pre.takeLast(max(1, pre.size / 2)).map { it.speedMetresPerSecond })
        val eventSpeeds = event.map { it.speedMetresPerSecond }.filter { it.isFinite() && it >= 0f }
        val minimumSpeed = eventSpeeds.minOrNull() ?: Float.NaN
        val postSpeed = medianFinite(post.take(max(1, post.size / 2)).map { it.speedMetresPerSecond })
        val speedReduction = if (preSpeed.isFinite() && minimumSpeed.isFinite()) (preSpeed - minimumSpeed).coerceAtLeast(0f) else 0f
        val relativeReduction = if (preSpeed.isFinite() && preSpeed > 0.8f) speedReduction / preSpeed else 0f
        val beforeLongitudinal = mean(pre.takeLast(max(1, pre.size / 2))) { it.motion.longitudinalAcceleration }
        val duringLongitudinal = mean(event) { it.motion.longitudinalAcceleration }
        val afterLongitudinal = mean(post.take(max(1, post.size / 2))) { it.motion.longitudinalAcceleration }
        val decelerationRate = min(beforeLongitudinal, duringLongitudinal)
        val accelerationRate = max(0f, afterLongitudinal)
        val brakingEvidence = max(
            normalized(-decelerationRate, 0.2f, 2.2f),
            max(normalized(speedReduction, 0.4f, 4f), normalized(relativeReduction, 0.06f, 0.38f)),
        )
        val postAccelerationEvidence = max(
            normalized(accelerationRate, 0.15f, 1.8f),
            if (postSpeed.isFinite() && minimumSpeed.isFinite()) normalized(postSpeed - minimumSpeed, 0.3f, 3f) else 0f,
        )

        val intervalsMillis = samples.zipWithNext { a, b ->
            (b.motion.elapsedRealtimeNanos - a.motion.elapsedRealtimeNanos) / 1_000_000f
        }.filter { it > 0f }
        val medianInterval = medianFinite(intervalsMillis).takeIf { it.isFinite() && it > 0f } ?: 1_000f / config.expectedSampleRateHz
        val sampleRate = 1_000f / medianInterval
        val droppedFraction = samples.count { it.sampleGap }.toFloat() / samples.size +
            intervalsMillis.count { it > medianInterval * 2.2f }.toFloat() / max(1, intervalsMillis.size)
        val orientationReliability = samples.count { it.motion.orientationReliable }.toFloat() / samples.size
        val sampleRateQuality = 1f - normalized(abs(sampleRate - config.expectedSampleRateHz), 15f, 70f)
        val signalQuality = (0.48f * sampleRateQuality + 0.32f * (1f - droppedFraction.coerceIn(0f, 1f)) +
            0.20f * (0.45f + 0.55f * orientationReliability)).coerceIn(0f, 1f)

        val peakDensity = peaks.size / (durationMillis / 1_000f).coerceAtLeast(0.15f)
        val sustainedContext = normalized(max(preRms, postRms) / baseline.verticalRms.coerceAtLeast(0.08f), 1.5f, 4f)
        val roughness = (0.32f * baseline.roughnessScore + 0.25f * highFrequencyRatio +
            0.23f * normalized(peakDensity, 4f, 14f) + 0.20f * sustainedContext).coerceIn(0f, 1f)

        val impulseSharpness = normalized(jerkPeak / peakToPeak.coerceAtLeast(0.25f), 11f, 34f)
        val narrowImpulse = 1f - normalized(dominantWidth.toFloat(), 65f, 230f)
        val singleSided = 1f - lobeBalance
        val downFirst = if (negative.motion.elapsedRealtimeNanos < positive.motion.elapsedRealtimeNanos) 1f else 0f
        val oneWheel = max(1f - verticalDominance, normalized(gyroRms, 0.35f, 1.8f))
        val potholeLikelihood = (0.29f * impulseSharpness + 0.22f * narrowImpulse + 0.17f * highFrequencyRatio +
            0.14f * singleSided + 0.12f * oneWheel + 0.06f * downFirst).coerceIn(0f, 1f)

        val minimumStability = samples.minOf { it.phoneStabilityScore }
        val phoneMovement = (0.36f * (1f - minimumStability) +
            0.25f * normalized(orientationMax, config.phoneOrientationStepRadians * 0.45f, config.phoneOrientationStepRadians * 2.5f) +
            0.20f * normalized(gyroRms, 0.55f, 3.0f) +
            0.14f * normalized(horizontalRms / verticalRms.coerceAtLeast(0.1f), 0.8f, 2.5f) +
            0.05f * if (isSaturated(samples, config)) 1f else 0f).coerceIn(0f, 1f)
        val lateralRms = rms(event) { it.motion.lateralAcceleration }
        val yawRms = rms(event) { it.motion.gyroscopeZ }
        val turnLikelihood = (0.58f * normalized(lateralRms / verticalRms.coerceAtLeast(0.1f), 0.55f, 1.8f) +
            0.42f * normalized(yawRms, 0.20f, 0.9f)).coerceIn(0f, 1f)
        val broadRatio = lowFrequencyRms / verticalRms.coerceAtLeast(0.05f)
        val hillOrRamp = (0.43f * normalized(durationMillis.toFloat(), 2_200f, 5_000f) +
            0.25f * normalized(broadRatio, 0.76f, 1.05f) +
            0.20f * normalized(orientationMax, 0.025f, 0.14f) +
            0.12f * (1f - lobeBalance)).coerceIn(0f, 1f)

        val profileScores = profileScores(
            peakToPeak = peakToPeak,
            durationMillis = durationMillis,
            peakGapMillis = peakGapMillis,
            prominentPeakCount = peaks.size,
            coherentPairs = coherentPairs,
            lobeBalance = lobeBalance,
            isolation = isolation,
            verticalDominance = verticalDominance,
            highFrequencyRatio = highFrequencyRatio,
            broadRatio = broadRatio,
            baselineRatio = verticalRms / baseline.verticalRms.coerceAtLeast(0.05f),
            gyroRms = gyroRms,
            config = config,
        )
        val bestProfile = profileScores.maxByOrNull { it.value }?.key ?: BumpProfile.UNCLASSIFIED
        val waveformScore = profileScores[bestProfile] ?: 0f

        return EventFeatures(
            eventElapsedRealtimeNanos = eventTime,
            positiveVerticalPeak = positivePeak,
            negativeVerticalPeak = negativePeak,
            peakToPeak = peakToPeak,
            positivePeakTimeNanos = positive.motion.elapsedRealtimeNanos,
            negativePeakTimeNanos = negative.motion.elapsedRealtimeNanos,
            peakGapMillis = peakGapMillis,
            durationMillis = durationMillis,
            verticalRms = verticalRms,
            verticalEnergy = verticalEnergy,
            jerkPeak = jerkPeak,
            longitudinalBefore = beforeLongitudinal,
            longitudinalDuring = duringLongitudinal,
            lateralEnergy = lateralEnergy,
            gyroscopeEnergy = gyroEnergy,
            orientationVariance = orientationVariance,
            sensorSaturated = isSaturated(samples, config),
            baselineNoiseRatio = verticalRms / baseline.verticalRms.coerceAtLeast(0.05f),
            horizontalRms = horizontalRms,
            jerkRms = jerkRms,
            highFrequencyRms = highFrequencyRms,
            highFrequencyRatio = highFrequencyRatio,
            lowFrequencyRms = lowFrequencyRms,
            zeroCrossingCount = zeroCrossings,
            prominentPeakCount = peaks.size,
            coherentPairCount = coherentPairs,
            dominantPeakWidthMillis = dominantWidth,
            eventIsolation = isolation,
            verticalDominance = verticalDominance,
            waveformSymmetry = waveformSymmetry,
            lobeBalance = lobeBalance,
            settlingScore = settling,
            wholeVehicleCoherence = wholeVehicleCoherence,
            approximateVerticalDisplacementMetres = displacement,
            preEventVerticalRms = preRms,
            postEventVerticalRms = postRms,
            preEventSpeedMetresPerSecond = preSpeed,
            minimumEventSpeedMetresPerSecond = minimumSpeed,
            postEventSpeedMetresPerSecond = postSpeed,
            absoluteSpeedReductionMetresPerSecond = speedReduction,
            relativeSpeedReduction = relativeReduction,
            decelerationRateMetresPerSecondSquared = decelerationRate,
            accelerationRateMetresPerSecondSquared = accelerationRate,
            brakingEvidence = brakingEvidence,
            postAccelerationEvidence = postAccelerationEvidence,
            gyroscopeRms = gyroRms,
            maximumOrientationStepRadians = orientationMax,
            orientationReliability = orientationReliability,
            sampleRateHz = sampleRate,
            droppedSampleFraction = droppedFraction.coerceIn(0f, 1f),
            signalQuality = signalQuality,
            roughnessScore = roughness,
            potholeLikelihood = potholeLikelihood,
            phoneMovementLikelihood = phoneMovement,
            turnLikelihood = turnLikelihood,
            hillOrRampLikelihood = hillOrRamp,
            bumpWaveformScore = waveformScore,
            profile = bestProfile,
            profileScores = profileScores,
            roadSurfaceState = baseline.surfaceState,
        )
    }

    private fun profileScores(
        peakToPeak: Float,
        durationMillis: Long,
        peakGapMillis: Long,
        prominentPeakCount: Int,
        coherentPairs: Int,
        lobeBalance: Float,
        isolation: Float,
        verticalDominance: Float,
        highFrequencyRatio: Float,
        broadRatio: Float,
        baselineRatio: Float,
        gyroRms: Float,
        config: DetectorConfiguration,
    ): Map<BumpProfile, Float> {
        val amplitude = normalized(peakToPeak, config.minimumPhysicalPeakMetresPerSecondSquared * 1.25f, 7.5f)
        val gap = when {
            peakGapMillis < config.minimumPeakGapMillis -> 0f
            peakGapMillis <= 850L -> 1f
            peakGapMillis <= config.maximumPeakGapMillis ->
                1f - 0.45f * normalized(peakGapMillis.toFloat(), 850f, config.maximumPeakGapMillis.toFloat())
            else -> 0f
        }
        val pairQuality = (0.31f * amplitude + 0.25f * lobeBalance + 0.19f * gap +
            0.15f * verticalDominance + 0.10f * isolation).coerceIn(0f, 1f)
        val shortSharp = pairQuality * gaussianScore(durationMillis.toFloat(), 620f, 520f) *
            (1f - highFrequencyRatio * 0.38f)
        val longSmooth = pairQuality * normalized(durationMillis.toFloat(), 520f, 2_300f) *
            (1f - highFrequencyRatio * 0.72f) * normalized(broadRatio, 0.25f, 0.82f)
        val flatTop = pairQuality * normalized(durationMillis.toFloat(), 650f, 2_400f) *
            (1f - normalized(durationMillis.toFloat(), 3_200f, 5_000f)) *
            (0.72f + 0.28f * normalized(prominentPeakCount.toFloat(), 2f, 5f))
        val lowProfile = pairQuality * (1f - normalized(peakToPeak, 4.5f, 9f)) *
            normalized(baselineRatio, 1.8f, 5f) * (0.65f + 0.35f * isolation)
        val doubleAxle = pairQuality * normalized(coherentPairs.toFloat(), 1f, 3f) *
            normalized(prominentPeakCount.toFloat(), 3f, 7f)
        val asymmetric = pairQuality * (0.68f + 0.32f * normalized(gyroRms, 0.12f, 0.75f)) *
            (1f - normalized(gyroRms, 1.2f, 2.8f))
        val consecutive = pairQuality * normalized(coherentPairs.toFloat(), 1.5f, 4f) *
            normalized(durationMillis.toFloat(), 1_000f, 3_600f)
        return linkedMapOf(
            BumpProfile.SHORT_SHARP to shortSharp.coerceIn(0f, 1f),
            BumpProfile.LONG_SMOOTH_HUMP to longSmooth.coerceIn(0f, 1f),
            BumpProfile.FLAT_TOP_TABLE to flatTop.coerceIn(0f, 1f),
            BumpProfile.LOW_PROFILE to lowProfile.coerceIn(0f, 1f),
            BumpProfile.DOUBLE_AXLE to doubleAxle.coerceIn(0f, 1f),
            BumpProfile.ASYMMETRIC to asymmetric.coerceIn(0f, 1f),
            BumpProfile.CONSECUTIVE to consecutive.coerceIn(0f, 1f),
        )
    }

    private data class Peak(val index: Int, val value: Float, val timestampNanos: Long)

    private fun localPeaks(samples: List<ProcessedSample>, threshold: Float, minimumSpacingMillis: Long): List<Peak> {
        val peaks = ArrayList<Peak>()
        for (index in 1 until samples.lastIndex) {
            val value = samples[index].eventVertical
            val isExtremum = (value >= samples[index - 1].eventVertical && value > samples[index + 1].eventVertical) ||
                (value <= samples[index - 1].eventVertical && value < samples[index + 1].eventVertical)
            if (!isExtremum || abs(value) < threshold) continue
            val previous = peaks.lastOrNull()
            if (previous != null && samples[index].motion.elapsedRealtimeNanos - previous.timestampNanos < minimumSpacingMillis * 1_000_000L) {
                if (abs(value) > abs(previous.value)) peaks[peaks.lastIndex] = Peak(index, value, samples[index].motion.elapsedRealtimeNanos)
            } else {
                peaks += Peak(index, value, samples[index].motion.elapsedRealtimeNanos)
            }
        }
        return peaks
    }

    private fun coherentPairCount(peaks: List<Peak>, config: DetectorConfiguration): Int {
        var pairs = 0
        for (index in 0 until peaks.lastIndex) {
            val first = peaks[index]
            val second = peaks[index + 1]
            if (first.value * second.value >= 0f) continue
            val gap = (second.timestampNanos - first.timestampNanos) / 1_000_000L
            val balance = min(abs(first.value), abs(second.value)) / max(abs(first.value), abs(second.value)).coerceAtLeast(0.05f)
            if (gap in config.minimumPeakGapMillis..config.maximumPeakGapMillis && balance >= config.minimumOpposingLobeRatio) pairs++
        }
        return pairs
    }

    private fun zeroCrossings(samples: List<ProcessedSample>, deadBand: Float): Int {
        var previousSign = 0
        var count = 0
        samples.forEach { sample ->
            val sign = when {
                sample.eventVertical > deadBand -> 1
                sample.eventVertical < -deadBand -> -1
                else -> 0
            }
            if (sign != 0) {
                if (previousSign != 0 && sign != previousSign) count++
                previousSign = sign
            }
        }
        return count
    }

    private fun dominantPeakWidthMillis(samples: List<ProcessedSample>): Long {
        val index = samples.indices.maxByOrNull { abs(samples[it].eventVertical) } ?: return 0L
        val threshold = abs(samples[index].eventVertical) * 0.35f
        var left = index
        var right = index
        while (left > 0 && abs(samples[left - 1].eventVertical) >= threshold) left--
        while (right < samples.lastIndex && abs(samples[right + 1].eventVertical) >= threshold) right++
        return (samples[right].motion.elapsedRealtimeNanos - samples[left].motion.elapsedRealtimeNanos) / 1_000_000L
    }

    private fun approximateDisplacement(samples: List<ProcessedSample>): Float {
        if (samples.size < 3) return 0f
        val meanAcceleration = samples.map { it.eventVertical }.average().toFloat()
        var velocity = 0f
        var displacement = 0f
        var maximum = 0f
        for (index in 1 until samples.size) {
            val dt = ((samples[index].motion.elapsedRealtimeNanos - samples[index - 1].motion.elapsedRealtimeNanos) /
                1_000_000_000f).coerceIn(0f, 0.1f)
            val acceleration = (samples[index].eventVertical + samples[index - 1].eventVertical) * 0.5f - meanAcceleration
            velocity += acceleration * dt
            displacement += velocity * dt
            maximum = max(maximum, abs(displacement))
        }
        return maximum.coerceAtMost(1.5f)
    }

    private fun isSaturated(samples: List<ProcessedSample>, config: DetectorConfiguration): Boolean = samples.any {
        abs(it.motion.accelerationX) > config.sensorSaturationMetresPerSecondSquared ||
            abs(it.motion.accelerationY) > config.sensorSaturationMetresPerSecondSquared ||
            abs(it.motion.accelerationZ) > config.sensorSaturationMetresPerSecondSquared
    }

    private inline fun rms(samples: List<ProcessedSample>, selector: (ProcessedSample) -> Float): Float {
        if (samples.isEmpty()) return 0f
        return sqrt(samples.sumOf { selector(it).toDouble().let { value -> value * value } } / samples.size).toFloat()
    }

    private inline fun mean(samples: List<ProcessedSample>, selector: (ProcessedSample) -> Float): Float {
        if (samples.isEmpty()) return 0f
        return samples.map(selector).average().toFloat()
    }

    private fun dynamicLateralRms(samples: List<ProcessedSample>): Float {
        if (samples.isEmpty()) return 0f
        val lateralMean = samples.map { it.motion.lateralAcceleration }.average().toFloat()
        return sqrt(samples.sumOf {
            val lateral = it.motion.lateralAcceleration - lateralMean
            (lateral * lateral).toDouble()
        } / samples.size).toFloat()
    }

    private fun medianFinite(values: List<Float>): Float {
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return Float.NaN
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) * 0.5f else sorted[middle]
    }
}
