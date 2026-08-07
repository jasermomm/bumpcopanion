package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Pure timestamped core: Android live sensors and CSV replay both call this exact interface. */
interface RoadEventDetector {
    fun updateConfiguration(configuration: DetectorConfiguration)
    fun addSample(
        sample: MotionSample,
        speedMetresPerSecond: Float,
        locationQuality: LocationQuality,
        phoneStabilityScore: Float,
        speedAccuracyMetresPerSecond: Float = Float.NaN,
        bearingDegrees: Float = Float.NaN,
    ): DetectedRoadEvent?
    fun setDiagnosticsListener(listener: DetectorDiagnosticsListener?)
    fun reset()
}

/**
 * Multi-stage deterministic road-event classifier.
 *
 * A trigger merely opens a candidate.  Classification happens only after the complete physical
 * waveform and suspension settling are present, so braking, cracks, isolated impacts and rough
 * road cannot become bumps from one coincident sample.
 */
class HeuristicRoadEventDetector(
    configuration: DetectorConfiguration = DetectorConfiguration.forSensitivity(Sensitivity.BALANCED),
    private val featureExtractor: FeatureExtractor = FeatureExtractor(),
) : RoadEventDetector {
    private var config = configuration
    private val capacity: Int get() = max(200, (config.expectedSampleRateHz * config.historySeconds).toInt())
    private var frames = BoundedRingBuffer<ProcessedSample>(capacity)
    private val signalProcessor = SignalProcessor(configuration)
    private val baseline = AdaptiveRoadBaseline(configuration)
    private var state = DetectorState.CALIBRATING
    private var diagnosticsListener: DetectorDiagnosticsListener? = null
    private var triggerNanos = 0L
    private var quietSinceNanos = 0L
    private var settleUntilNanos = 0L
    private var refractoryUntilNanos = 0L
    private var lastTimestampNanos = 0L
    private var lastReliableMovingNanos = 0L
    private var candidateCounter = 0L

    @Synchronized
    override fun updateConfiguration(configuration: DetectorConfiguration) {
        val capacityChanged = capacity != max(200, (configuration.expectedSampleRateHz * configuration.historySeconds).toInt())
        config = configuration
        signalProcessor.updateConfiguration(configuration)
        baseline.updateConfiguration(configuration)
        if (capacityChanged) frames = BoundedRingBuffer(max(200, (config.expectedSampleRateHz * config.historySeconds).toInt()))
        cancelCandidate()
    }

    @Synchronized
    override fun setDiagnosticsListener(listener: DetectorDiagnosticsListener?) {
        diagnosticsListener = listener
    }

    @Synchronized
    override fun addSample(
        sample: MotionSample,
        speedMetresPerSecond: Float,
        locationQuality: LocationQuality,
        phoneStabilityScore: Float,
        speedAccuracyMetresPerSecond: Float,
        bearingDegrees: Float,
    ): DetectedRoadEvent? {
        if (sample.elapsedRealtimeNanos <= lastTimestampNanos) return null
        lastTimestampNanos = sample.elapsedRealtimeNanos
        val aligned = alignToVehicle(sample, bearingDegrees)
        val safeSpeed = speedMetresPerSecond.takeIf { it.isFinite() && it >= 0f } ?: Float.NaN
        if (safeSpeed.isFinite() && safeSpeed >= config.minimumSpeedMetresPerSecond &&
            locationQuality !in setOf(LocationQuality.STALE, LocationQuality.UNAVAILABLE)
        ) {
            lastReliableMovingNanos = sample.elapsedRealtimeNanos
        }
        val frame = signalProcessor.process(
            sample = aligned,
            speedMetresPerSecond = safeSpeed,
            speedAccuracyMetresPerSecond = speedAccuracyMetresPerSecond,
            locationQuality = locationQuality,
            phoneStabilityScore = phoneStabilityScore,
        )
        frames.add(frame)
        val candidateActive = state == DetectorState.CAPTURING || state == DetectorState.SETTLING
        baseline.update(frame, candidateActive)
        val baselineNow = baseline.snapshot()
        if (state == DetectorState.CALIBRATING && baselineNow.calibrationProgress >= 1f) state = DetectorState.NORMAL

        val triggerThreshold = triggerThreshold(safeSpeed, baselineNow)
        val moving = isMoving(sample.elapsedRealtimeNanos, safeSpeed, locationQuality)
        val strongWithoutGps = locationQuality in setOf(LocationQuality.STALE, LocationQuality.UNAVAILABLE) &&
            abs(frame.eventVertical) >= triggerThreshold * config.gpsDropoutStrongSignalMultiplier
        val trigger = frame.envelope >= triggerThreshold && abs(frame.eventVertical) >= config.minimumPhysicalPeakMetresPerSecondSquared * 0.72f
        var result: DetectedRoadEvent? = null

        when (state) {
            DetectorState.CALIBRATING, DetectorState.NORMAL -> {
                if (trigger && (moving || strongWithoutGps)) beginCandidate(sample.elapsedRealtimeNanos)
            }
            DetectorState.CAPTURING -> {
                val elapsed = sample.elapsedRealtimeNanos - triggerNanos
                val releaseThreshold = triggerThreshold * config.eventReleaseRatio
                if (frame.envelope <= releaseThreshold) {
                    if (quietSinceNanos == 0L) quietSinceNanos = sample.elapsedRealtimeNanos
                } else {
                    quietSinceNanos = 0L
                }
                val quietLongEnough = quietSinceNanos != 0L &&
                    sample.elapsedRealtimeNanos - quietSinceNanos >= config.eventEndQuietMillis * 1_000_000L
                val minimumCaptured = elapsed >= config.minimumEventDurationMillis * 1_000_000L
                val maximumCaptured = elapsed >= config.maximumEventDurationMillis * 1_000_000L
                if ((quietLongEnough && minimumCaptured) || maximumCaptured) {
                    state = DetectorState.SETTLING
                    settleUntilNanos = sample.elapsedRealtimeNanos + config.postEventWindowMillis * 1_000_000L
                }
            }
            DetectorState.SETTLING -> {
                // A related axle impulse resumes the same event. A truly separate bump after a
                // quiet gap arrives after finalization and the short event-aware refractory.
                if (trigger && quietSinceNanos != 0L) {
                    if (sample.elapsedRealtimeNanos - quietSinceNanos < 700_000_000L) {
                        state = DetectorState.CAPTURING
                        quietSinceNanos = 0L
                    } else {
                        // A new coherent trigger after a real quiet gap is a second nearby bump.
                        // Emit the completed first event and immediately capture the second.
                        result = finishCandidate(sample.elapsedRealtimeNanos, baselineNow, moving)
                        if (moving || strongWithoutGps) beginCandidate(sample.elapsedRealtimeNanos)
                    }
                } else if (sample.elapsedRealtimeNanos >= settleUntilNanos) {
                    result = finishCandidate(sample.elapsedRealtimeNanos, baselineNow, moving)
                }
            }
            DetectorState.REFRACTORY -> {
                if (sample.elapsedRealtimeNanos >= refractoryUntilNanos) {
                    state = if (baselineNow.calibrationProgress < 1f) DetectorState.CALIBRATING else DetectorState.NORMAL
                    if (trigger && (moving || strongWithoutGps)) beginCandidate(sample.elapsedRealtimeNanos)
                }
            }
            DetectorState.SUPPRESSED -> {
                if (phoneStabilityScore >= 0.55f && frame.gyroMagnitude < 0.8f) {
                    state = DetectorState.NORMAL
                }
            }
        }

        diagnosticsListener?.onFrame(
            DetectorTelemetryFrame(
                sample = aligned,
                speedMetresPerSecond = safeSpeed,
                speedAccuracyMetresPerSecond = speedAccuracyMetresPerSecond,
                locationQuality = locationQuality,
                phoneStabilityScore = phoneStabilityScore,
                eventVerticalAcceleration = frame.eventVertical,
                lowFrequencyVerticalAcceleration = frame.broadVertical,
                highFrequencyVerticalAcceleration = frame.highFrequencyVertical,
                verticalEnvelope = frame.envelope,
                verticalJerk = frame.verticalJerk,
                horizontalAcceleration = frame.horizontalBand,
                baselineVerticalRms = baselineNow.verticalRms,
                baselineHighFrequencyRms = baselineNow.highFrequencyRms,
                roadSurfaceState = baselineNow.surfaceState,
                detectorState = state,
            )
        )
        return result
    }

    private fun beginCandidate(timestampNanos: Long) {
        triggerNanos = timestampNanos
        quietSinceNanos = 0L
        state = DetectorState.CAPTURING
    }

    private fun finishCandidate(
        nowNanos: Long,
        baselineSnapshot: RoadBaselineSnapshot,
        movingAtEnd: Boolean,
    ): DetectedRoadEvent? {
        candidateCounter++
        val start = triggerNanos - config.preEventWindowMillis * 1_000_000L
        val window = frames.filter { it.motion.elapsedRealtimeNanos >= start && it.motion.elapsedRealtimeNanos <= nowNanos }
        val features = featureExtractor.extractProcessed(window, baselineSnapshot, config)
        val scored = if (features == null) {
            ScoredCandidate(
                confidence = 0f,
                databaseConfidence = 0f,
                eventType = RoadEventType.UNKNOWN,
                disposition = DetectionDisposition.REJECTED,
                reasons = listOf(ConfidenceReason.NO_PAIRED_SEQUENCE),
                contributions = listOf(ScoreContribution("feature extraction", -1f, "insufficient coherent samples")),
            )
        } else {
            score(features, window, movingAtEnd)
        }
        val explanation = explanation(candidateCounter, features, scored)
        diagnosticsListener?.onCandidate(
            CandidateEvaluation(
                candidateNumber = candidateCounter,
                detectorState = state,
                eventType = scored.eventType,
                disposition = scored.disposition,
                confidence = scored.confidence,
                databaseConfidence = scored.databaseConfidence,
                features = features,
                contributions = scored.contributions,
                explanation = explanation,
            )
        )
        state = if (scored.eventType == RoadEventType.PHONE_MOVEMENT) DetectorState.SUPPRESSED else DetectorState.REFRACTORY
        refractoryUntilNanos = nowNanos + config.sameEventRefractoryMillis * 1_000_000L
        triggerNanos = 0L
        quietSinceNanos = 0L
        settleUntilNanos = 0L
        if (features == null || scored.disposition == DetectionDisposition.REJECTED) return null
        return DetectedRoadEvent(
            eventType = scored.eventType,
            confidence = scored.confidence,
            features = features,
            confidenceReasons = scored.reasons.distinct(),
            profile = features.profile,
            disposition = scored.disposition,
            databaseConfidence = scored.databaseConfidence,
            scoreContributions = scored.contributions,
            explanation = explanation,
        )
    }

    private data class ScoredCandidate(
        val confidence: Float,
        val databaseConfidence: Float,
        val eventType: RoadEventType,
        val disposition: DetectionDisposition,
        val reasons: List<ConfidenceReason>,
        val contributions: List<ScoreContribution>,
    )

    private fun score(
        features: EventFeatures,
        window: List<ProcessedSample>,
        movingAtEnd: Boolean,
    ): ScoredCandidate {
        val weights = config.weights
        val contributions = ArrayList<ScoreContribution>(18)
        val reasons = ArrayList<ConfidenceReason>(12)
        fun evidence(name: String, feature: Float, weight: Float, detail: String) {
            contributions += ScoreContribution(name, feature.coerceIn(0f, 1f) * weight, detail)
        }
        fun penalty(name: String, feature: Float, weight: Float, detail: String) {
            contributions += ScoreContribution(name, -feature.coerceIn(0f, 1f) * weight, detail)
        }

        val snrScore = normalized(features.baselineNoiseRatio, 1.6f, 6.5f)
        val durationScore = when {
            features.durationMillis < config.minimumEventDurationMillis -> 0f
            features.durationMillis < 350L -> normalized(features.durationMillis.toFloat(), config.minimumEventDurationMillis.toFloat(), 350f)
            features.durationMillis <= 2_600L -> 1f
            else -> 1f - normalized(features.durationMillis.toFloat(), 2_600f, config.maximumEventDurationMillis.toFloat())
        }
        evidence("vertical waveform", features.bumpWaveformScore, weights.waveform, "${features.profile}, ${features.prominentPeakCount} prominent peaks")
        evidence("event isolation", features.eventIsolation, weights.isolation, "pre=${format(features.preEventVerticalRms)}, post=${format(features.postEventVerticalRms)} m/s² RMS")
        evidence("vertical dominance", features.verticalDominance, weights.verticalDominance, "vertical/horizontal energy separation")
        evidence("adaptive SNR", snrScore, weights.signalToNoise, "${format(features.baselineNoiseRatio)}× current baseline")
        evidence("event duration", durationScore, weights.duration, "${features.durationMillis} ms")
        evidence("suspension settling", features.settlingScore, weights.settling, "post-event decay")
        evidence("whole-vehicle coherence", features.wholeVehicleCoherence, weights.wholeVehicleCoherence, "vertical body motion with limited rotation")
        evidence("sensor quality", features.signalQuality, weights.signalQuality, "${format(features.sampleRateHz)} Hz, dropped=${format(features.droppedSampleFraction)}")
        evidence("approach deceleration", features.brakingEvidence, weights.brakingContext, "context only; never a trigger")
        evidence("post acceleration", features.postAccelerationEvidence, weights.postAccelerationContext, "optional departure evidence")

        penalty("rough-road continuity", features.roughnessScore, weights.roughRoadPenalty, "high-frequency energy, peak density, and pre/post vibration")
        penalty("pothole shape", features.potholeLikelihood, weights.potholePenalty, "sharpness, width, lobe imbalance, and one-wheel motion")
        penalty("phone movement", features.phoneMovementLikelihood, weights.phoneMovementPenalty, "mount stability, orientation, gyro, and horizontal motion")
        penalty("turn/lane-change", features.turnLikelihood * (1f - features.verticalDominance), weights.turnPenalty, "lateral/yaw-dominated context")
        penalty("hill/ramp", features.hillOrRampLikelihood * (1f - features.bumpWaveformScore), weights.hillOrRampPenalty, "long sustained pitch-like motion")
        penalty("high-frequency vibration", normalized(features.highFrequencyRatio, 0.28f, 0.72f), weights.highFrequencyPenalty, "crack/roughness frequency band")
        if (features.sensorSaturated) penalty("sensor clipping", 1f, weights.clippingPenalty, "raw sensor exceeded physical range")

        val validSpeeds = window.map { it.speedMetresPerSecond }.filter { it.isFinite() && it >= config.minimumSpeedMetresPerSecond }
        val recentMoving = validSpeeds.isNotEmpty() || movingAtEnd
        val locationQuality = window.lastOrNull()?.locationQuality ?: LocationQuality.UNAVAILABLE
        val speedUncertain = locationQuality in setOf(LocationQuality.STALE, LocationQuality.UNAVAILABLE) ||
            window.lastOrNull()?.speedAccuracyMetresPerSecond?.let { it.isFinite() && it > config.poorSpeedAccuracyMetresPerSecond } == true
        if (!recentMoving) penalty("stationary/unknown motion", 1f, 0.34f, "no reliable recent driving-speed evidence")
        if (speedUncertain) penalty("speed uncertainty", 1f, 0.02f, "GPS is contextual, so only a small quality penalty applies")

        var confidence = (contributions.sumOf { it.value.toDouble() }.toFloat() + config.sensitivityScoreOffset).coerceIn(0f, 1f)
        val opposingLobes = features.positiveVerticalPeak > 0f && features.negativeVerticalPeak < 0f &&
            features.lobeBalance >= config.minimumOpposingLobeRatio &&
            features.peakGapMillis in config.minimumPeakGapMillis..config.maximumPeakGapMillis
        val smoothWideAlternative = features.durationMillis >= 520L && features.lowFrequencyRms > 0.28f &&
            features.bumpWaveformScore >= config.minimumWaveformScore + 0.08f && features.highFrequencyRatio < 0.46f
        var physicallyValid = (opposingLobes || smoothWideAlternative) &&
            features.bumpWaveformScore >= config.minimumWaveformScore

        if (features.roughnessScore >= config.veryRoughRoadThreshold &&
            (features.bumpWaveformScore < config.roughRoadMinimumWaveformScore || features.eventIsolation < 0.45f)
        ) physicallyValid = false
        if (features.potholeLikelihood >= config.strongPotholeLikelihood && features.bumpWaveformScore < 0.80f) physicallyValid = false
        if (features.phoneMovementLikelihood >= config.severePhoneMovementLikelihood && features.wholeVehicleCoherence < 0.88f) physicallyValid = false
        if (!recentMoving) physicallyValid = false
        if (!physicallyValid) confidence = minOf(confidence, config.localCandidateThreshold - 0.01f)

        if (opposingLobes) reasons += ConfidenceReason.OPPOSING_VERTICAL_PEAKS
        if (features.bumpWaveformScore >= 0.55f) reasons += ConfidenceReason.STRUCTURED_WAVEFORM
        if (features.eventIsolation >= 0.62f) reasons += ConfidenceReason.ISOLATED_EVENT
        if (features.verticalDominance >= 0.68f) reasons += ConfidenceReason.VERTICAL_DOMINANT
        if (features.settlingScore >= 0.55f) reasons += ConfidenceReason.SUSPENSION_SETTLING
        when (features.profile) {
            BumpProfile.LONG_SMOOTH_HUMP, BumpProfile.FLAT_TOP_TABLE -> reasons += ConfidenceReason.LONG_SMOOTH_PROFILE
            BumpProfile.DOUBLE_AXLE, BumpProfile.CONSECUTIVE -> reasons += ConfidenceReason.DOUBLE_AXLE_PROFILE
            BumpProfile.LOW_PROFILE -> reasons += ConfidenceReason.LOW_PROFILE_EVENT
            else -> Unit
        }
        if (features.brakingEvidence >= 0.35f) reasons += ConfidenceReason.VEHICLE_SLOWED
        if (features.postAccelerationEvidence >= 0.35f) reasons += ConfidenceReason.POST_EVENT_ACCELERATION
        if (features.roughnessScore >= 0.45f) reasons += ConfidenceReason.ROUGH_ROAD_CONTEXT
        if (features.highFrequencyRatio >= 0.42f) reasons += ConfidenceReason.HIGH_FREQUENCY_VIBRATION
        if (features.potholeLikelihood >= 0.50f) reasons += ConfidenceReason.POTHOLE_LIKE_IMPULSE
        if (features.phoneMovementLikelihood >= 0.50f) reasons += ConfidenceReason.PHONE_MOVEMENT_LIKELY
        if (features.turnLikelihood >= 0.55f) reasons += ConfidenceReason.TURNING_CONTEXT
        if (features.hillOrRampLikelihood >= 0.55f) reasons += ConfidenceReason.HILL_OR_RAMP_LIKELY
        if (speedUncertain) reasons += ConfidenceReason.SPEED_CONTEXT_UNCERTAIN
        if (features.signalQuality < 0.72f) reasons += ConfidenceReason.SENSOR_QUALITY_REDUCED
        if (features.sensorSaturated) reasons += ConfidenceReason.SENSOR_CLIPPING

        val databaseConfidence = (confidence * 0.82f + features.eventIsolation * 0.08f +
            features.signalQuality * 0.06f + features.wholeVehicleCoherence * 0.04f -
            if (speedUncertain) 0.035f else 0f).coerceIn(0f, 1f)
        val disposition = when {
            !physicallyValid || confidence < config.localCandidateThreshold -> DetectionDisposition.REJECTED
            databaseConfidence >= config.databaseWorthyThreshold -> DetectionDisposition.DATABASE_WORTHY
            else -> DetectionDisposition.LOCAL_CANDIDATE
        }
        val eventType = when {
            disposition != DetectionDisposition.REJECTED && confidence >= config.probableThreshold -> RoadEventType.LIKELY_SPEED_BUMP
            disposition != DetectionDisposition.REJECTED -> RoadEventType.POSSIBLE_SPEED_BUMP
            features.phoneMovementLikelihood >= max(features.potholeLikelihood, features.roughnessScore) -> RoadEventType.PHONE_MOVEMENT
            features.potholeLikelihood >= features.roughnessScore -> RoadEventType.POTHOLE_LIKE
            features.roughnessScore >= 0.45f -> RoadEventType.ROUGH_ROAD
            else -> RoadEventType.UNKNOWN
        }
        return ScoredCandidate(confidence, databaseConfidence, eventType, disposition, reasons, contributions)
    }

    private fun triggerThreshold(speed: Float, baseline: RoadBaselineSnapshot): Float {
        val speedScale = when {
            !speed.isFinite() -> 1.10f
            speed < 2f -> 0.76f
            speed < 4f -> 0.88f
            speed <= 15f -> 1f
            speed <= 25f -> 1.10f
            else -> 1.18f
        }
        val roughScale = when (baseline.surfaceState) {
            RoadSurfaceState.SMOOTH -> 0.92f
            RoadSurfaceState.NORMAL -> 1f
            RoadSurfaceState.ROUGH -> 1.16f
            RoadSurfaceState.VERY_ROUGH -> 1.30f
        }
        return max(
            config.minimumPhysicalPeakMetresPerSecondSquared,
            baseline.verticalRms * config.candidateSignalToNoiseRatio,
        ) * speedScale * roughScale
    }

    private fun isMoving(timestampNanos: Long, speed: Float, quality: LocationQuality): Boolean {
        if (speed.isFinite() && speed >= config.minimumSpeedMetresPerSecond) return true
        return quality in setOf(LocationQuality.STALE, LocationQuality.UNAVAILABLE) &&
            lastReliableMovingNanos != 0L &&
            timestampNanos - lastReliableMovingNanos <= config.recentMovingMemoryMillis * 1_000_000L
    }

    /** Earth east/north becomes true longitudinal/lateral only when GPS bearing is available. */
    private fun alignToVehicle(sample: MotionSample, bearingDegrees: Float): MotionSample {
        if (!bearingDegrees.isFinite() || !sample.worldAccelerationEast.isFinite() || !sample.worldAccelerationNorth.isFinite()) return sample
        val radians = bearingDegrees * PI.toFloat() / 180f
        val forwardEast = sin(radians)
        val forwardNorth = cos(radians)
        val longitudinal = sample.worldAccelerationEast * forwardEast + sample.worldAccelerationNorth * forwardNorth
        val lateral = sample.worldAccelerationEast * forwardNorth - sample.worldAccelerationNorth * forwardEast
        return sample.copy(longitudinalAcceleration = longitudinal, lateralAcceleration = lateral)
    }

    private fun explanation(number: Long, features: EventFeatures?, scored: ScoredCandidate): String = buildString {
        append("Candidate #").append(number).append('\n')
        if (features != null) {
            append("Profile: ").append(features.profile).append('\n')
            append("Road: ").append(features.roadSurfaceState).append('\n')
        }
        scored.contributions.forEach { contribution ->
            append(contribution.name).append(": ")
            append(if (contribution.value >= 0f) "+" else "")
            append(format(contribution.value)).append(" (").append(contribution.detail).append(")\n")
        }
        append("Final confidence: ").append(format(scored.confidence)).append('\n')
        append("Database confidence: ").append(format(scored.databaseConfidence)).append('\n')
        append("Classification: ").append(scored.eventType).append(" / ").append(scored.disposition)
    }

    private fun cancelCandidate() {
        triggerNanos = 0L
        quietSinceNanos = 0L
        settleUntilNanos = 0L
        refractoryUntilNanos = 0L
        state = DetectorState.CALIBRATING
    }

    @Synchronized
    override fun reset() {
        frames.clear()
        signalProcessor.reset()
        baseline.reset()
        lastTimestampNanos = 0L
        lastReliableMovingNanos = 0L
        candidateCounter = 0L
        cancelCandidate()
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.3f", value)
}
