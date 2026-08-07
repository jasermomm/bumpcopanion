package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 126 named synthetic regressions.  These are intentionally families with parameter variation,
 * not 126 copies of one ideal waveform.  Recorded drives remain the authority for field tuning.
 */
class DetectorScenarioMatrixTest {
    private enum class PositiveKind { NORMAL, SHARP, WIDE, TABLE, LOW, DOUBLE_AXLE, ASYMMETRIC }
    private enum class NegativeKind { POTHOLE, CRACK, ROUGH, BRAKING, TURN, PHONE, RAMP, JOINTS, STATIONARY_BUMP_SHAPE, ENGINE, RANDOM_IMPACTS }

    private data class PositiveContext(
        val name: String,
        val speed: Float = 7f,
        val braking: Float = -0.35f,
        val roughness: Float = 0f,
        val quality: LocationQuality = LocationQuality.GOOD,
        val stability: Float = 0.96f,
        val orientationReliable: Boolean = true,
    )

    @Test
    fun `fifty six bump variants retain useful recall`() {
        val contexts = listOf(
            PositiveContext("10-kmh", speed = 2.8f),
            PositiveContext("20-kmh", speed = 5.6f),
            PositiveContext("35-kmh", speed = 9.7f),
            PositiveContext("no-braking", braking = 0f),
            PositiveContext("heavy-braking", braking = -1.4f),
            PositiveContext("rough-asphalt", roughness = 0.16f),
            PositiveContext("gps-dropout", quality = LocationQuality.UNAVAILABLE),
            PositiveContext("gravity-fallback", orientationReliable = false),
        )
        val failures = ArrayList<String>()
        PositiveKind.entries.forEach { kind ->
            contexts.forEach { context ->
                val sensitivity = if (kind == PositiveKind.LOW) Sensitivity.SENSITIVE else Sensitivity.BALANCED
                val evaluations = ArrayList<CandidateEvaluation>()
                val events = runScenario(DetectorConfiguration.forSensitivity(sensitivity), evaluationSink = evaluations) { time ->
                    val event = positiveWave(kind, time)
                    val road = context.roughness * sin(2f * PI.toFloat() * 11f * time) +
                        context.roughness * 0.55f * sin(2f * PI.toFloat() * 6.7f * time + 0.4f)
                    Frame(
                        vertical = event + road,
                        longitudinal = if (time in 2.0f..3.8f) context.braking else if (time in 4.2f..5.0f) 0.25f else 0f,
                        lateral = if (kind == PositiveKind.ASYMMETRIC) 0.42f * gaussian(time, 3.3f, 0.35f) else 0.04f,
                        gyro = if (kind == PositiveKind.ASYMMETRIC) 0.24f * gaussian(time, 3.3f, 0.4f) else 0.025f,
                        speed = context.speed,
                        quality = context.quality,
                        stability = context.stability,
                        orientationReliable = context.orientationReliable,
                    )
                }
                if (events.isEmpty()) failures += "$kind/${context.name}: ${evaluations.lastOrNull()?.explanation ?: "no candidate"}"
            }
        }
        assertTrue("Missed positive scenarios: $failures", failures.isEmpty())
    }

    @Test
    fun `seventy adversarial non bump variants remain rejected`() {
        val amplitudes = listOf(0.70f, 0.85f, 1.0f, 1.15f, 1.30f, 1.55f, 1.80f)
        val failures = ArrayList<String>()
        NegativeKind.entries.forEach { kind ->
            amplitudes.forEach { scale ->
                val events = runScenario(DetectorConfiguration.forSensitivity(Sensitivity.BALANCED)) { time ->
                    negativeFrame(kind, time, scale)
                }
                if (events.isNotEmpty()) failures += "$kind/scale-$scale (${events.first().confidence}, ${events.first().profile})"
            }
        }
        // 11 families × 7 parameterizations = 77; keep the assertion explicit as a catalog guard.
        assertEquals(77, NegativeKind.entries.size * amplitudes.size)
        assertTrue("False-positive scenarios: $failures", failures.isEmpty())
    }

    @Test
    fun `front and rear axle response is one event while separated bumps remain detectable`() {
        val axleEvents = runScenario { time -> Frame(vertical = positiveWave(PositiveKind.DOUBLE_AXLE, time)) }
        assertEquals("Related axle impulses must merge", 1, axleEvents.size)

        val separated = runScenario(durationSeconds = 10f) { time ->
            val first = normalBump(time, 2.6f)
            val second = normalBump(time, 6.4f)
            Frame(vertical = first + second)
        }
        assertEquals("Physically separate bumps must not be hidden by cooldown", 2, separated.size)
    }

    @Test
    fun `temporary GPS dropout retains recent moving evidence`() {
        val events = runScenario { time ->
            Frame(
                vertical = normalBump(time, 3.2f),
                speed = if (time < 2.2f) 6f else 0f,
                quality = if (time < 2.2f) LocationQuality.GOOD else LocationQuality.UNAVAILABLE,
            )
        }
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `rejected candidates retain a complete tuning explanation`() {
        val evaluations = ArrayList<CandidateEvaluation>()
        val events = runScenario(evaluationSink = evaluations) { time ->
            negativeFrame(NegativeKind.POTHOLE, time, 1.2f)
        }
        assertTrue(events.isEmpty())
        assertTrue(evaluations.isNotEmpty())
        val explanation = evaluations.last().explanation
        assertTrue(explanation.contains("pothole shape"))
        assertTrue(explanation.contains("Final confidence"))
        assertTrue(explanation.contains("REJECTED"))
    }

    private data class Frame(
        val vertical: Float = 0f,
        val longitudinal: Float = 0f,
        val lateral: Float = 0.03f,
        val gyro: Float = 0.02f,
        val orientationStep: Float = 0.002f,
        val speed: Float = 7f,
        val quality: LocationQuality = LocationQuality.GOOD,
        val stability: Float = 0.96f,
        val orientationReliable: Boolean = true,
    )

    private fun runScenario(
        configuration: DetectorConfiguration = DetectorConfiguration.forSensitivity(Sensitivity.BALANCED),
        durationSeconds: Float = 8f,
        evaluationSink: MutableList<CandidateEvaluation>? = null,
        frame: (Float) -> Frame,
    ): List<DetectedRoadEvent> {
        val detector = HeuristicRoadEventDetector(configuration)
        if (evaluationSink != null) detector.setDiagnosticsListener(object : DetectorDiagnosticsListener {
            override fun onCandidate(evaluation: CandidateEvaluation) { evaluationSink += evaluation }
        })
        val events = ArrayList<DetectedRoadEvent>()
        val count = (durationSeconds * 100).toInt()
        repeat(count) { index ->
            val time = index / 100f
            val value = frame(time)
            detector.addSample(
                sample = MotionSample(
                    elapsedRealtimeNanos = 20_000_000_000L + index * 10_000_000L,
                    epochMillis = index * 10L,
                    accelerationX = value.lateral,
                    accelerationY = value.longitudinal,
                    accelerationZ = 9.80665f + value.vertical,
                    gyroscopeX = value.gyro * 0.35f,
                    gyroscopeY = value.gyro * 0.45f,
                    gyroscopeZ = value.gyro,
                    verticalAcceleration = value.vertical,
                    longitudinalAcceleration = value.longitudinal,
                    lateralAcceleration = value.lateral,
                    orientationChangeRadians = value.orientationStep,
                    orientationReliable = value.orientationReliable,
                ),
                speedMetresPerSecond = value.speed,
                locationQuality = value.quality,
                phoneStabilityScore = value.stability,
                speedAccuracyMetresPerSecond = if (value.quality == LocationQuality.GOOD) 0.4f else Float.NaN,
            )?.let(events::add)
        }
        return events
    }

    private fun positiveWave(kind: PositiveKind, time: Float): Float = when (kind) {
        PositiveKind.NORMAL -> normalBump(time, 3.2f)
        PositiveKind.SHARP -> 5.8f * gaussian(time, 3.05f, 0.055f) - 4.9f * gaussian(time, 3.27f, 0.075f)
        PositiveKind.WIDE -> 2.5f * gaussian(time, 2.85f, 0.34f) - 2.2f * gaussian(time, 4.05f, 0.42f)
        PositiveKind.TABLE ->
            3.2f * gaussian(time, 2.75f, 0.13f) - 2.7f * gaussian(time, 3.10f, 0.16f) +
                2.7f * gaussian(time, 4.15f, 0.15f) - 2.9f * gaussian(time, 4.48f, 0.17f)
        PositiveKind.LOW -> 1.55f * gaussian(time, 3.05f, 0.17f) - 1.35f * gaussian(time, 3.48f, 0.19f)
        PositiveKind.DOUBLE_AXLE -> normalBump(time, 3.0f) + 0.58f * normalBump(time, 3.68f)
        PositiveKind.ASYMMETRIC -> 3.7f * gaussian(time, 3.0f, 0.15f) - 3.1f * gaussian(time, 3.44f, 0.22f)
    }

    private fun negativeFrame(kind: NegativeKind, time: Float, scale: Float): Frame = when (kind) {
        NegativeKind.POTHOLE -> Frame(
            vertical = -7.2f * scale * gaussian(time, 3.0f, 0.035f) + 1.25f * scale * gaussian(time, 3.13f, 0.10f),
            lateral = 1.4f * gaussian(time, 3.0f, 0.08f),
            gyro = 0.65f * gaussian(time, 3.0f, 0.10f),
        )
        NegativeKind.CRACK -> Frame(vertical = 3.8f * scale * gaussian(time, 3.0f, 0.018f) - 1.1f * gaussian(time, 3.06f, 0.025f))
        NegativeKind.ROUGH -> Frame(
            vertical = scale * (0.95f * sin(2f * PI.toFloat() * 12f * time) + 0.65f * sin(2f * PI.toFloat() * 7.3f * time + 0.7f)),
            lateral = 0.7f * sin(2f * PI.toFloat() * 9.1f * time),
            gyro = 0.24f,
        )
        NegativeKind.BRAKING -> Frame(longitudinal = -2.5f * scale * gaussian(time, 3.3f, 0.8f), speed = (7f - 4f * gaussian(time, 3.4f, 0.9f)).coerceAtLeast(1f))
        NegativeKind.TURN -> Frame(
            vertical = 0.18f * sin(2f * PI.toFloat() * 2f * time),
            lateral = 3.5f * scale * gaussian(time, 3.4f, 0.9f),
            gyro = 0.75f * scale * gaussian(time, 3.4f, 0.9f),
        )
        NegativeKind.PHONE -> Frame(
            vertical = 3.5f * scale * sin(2f * PI.toFloat() * 1.8f * (time - 2.7f)) * if (time in 2.7f..4.1f) 1f else 0f,
            lateral = 5f * scale * sin(2f * PI.toFloat() * 1.4f * time),
            gyro = 3.2f,
            orientationStep = 0.22f,
            stability = 0.12f,
        )
        NegativeKind.RAMP -> Frame(
            vertical = 0.9f * scale * sin(PI.toFloat() * ((time - 2f) / 4f)).coerceAtLeast(0f),
            gyro = 0.13f,
            orientationStep = 0.035f,
        )
        NegativeKind.JOINTS -> Frame(
            vertical = (2.8f * gaussian(time, 2.8f, 0.018f) - 2.5f * gaussian(time, 2.85f, 0.02f) +
                2.7f * gaussian(time, 3.25f, 0.018f) - 2.4f * gaussian(time, 3.30f, 0.02f)) * scale,
        )
        NegativeKind.STATIONARY_BUMP_SHAPE -> Frame(vertical = normalBump(time, 3.2f) * scale, speed = 0f)
        NegativeKind.ENGINE -> Frame(vertical = 0.9f * scale * sin(2f * PI.toFloat() * 18f * time), gyro = 0.08f, speed = 0f)
        NegativeKind.RANDOM_IMPACTS -> Frame(
            vertical = scale * (3.2f * gaussian(time, 2.5f, 0.025f) - 4f * gaussian(time, 3.1f, 0.03f) +
                2.8f * gaussian(time, 3.42f, 0.025f) - 3.5f * gaussian(time, 4.0f, 0.022f)),
            lateral = 0.9f * sin(2f * PI.toFloat() * 3f * time),
            gyro = 0.5f,
        )
    }

    private fun normalBump(time: Float, centre: Float): Float =
        4.6f * gaussian(time, centre, 0.14f) - 3.9f * gaussian(time, centre + 0.38f, 0.17f)

    private fun gaussian(time: Float, centre: Float, width: Float): Float =
        exp((-((time - centre) * (time - centre)) / (2f * width * width)).toDouble()).toFloat()
}
