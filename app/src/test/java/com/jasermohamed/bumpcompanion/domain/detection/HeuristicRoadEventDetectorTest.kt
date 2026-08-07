package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.LocationQuality
import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import com.jasermohamed.bumpcompanion.domain.model.RoadEventType
import com.jasermohamed.bumpcompanion.domain.model.Sensitivity
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicRoadEventDetectorTest {
    @Test
    fun `paired synthetic bump creates candidate`() {
        val detector = HeuristicRoadEventDetector(DetectorConfiguration.forSensitivity(Sensitivity.BALANCED))
        var event: com.jasermohamed.bumpcompanion.domain.model.DetectedRoadEvent? = null
        val base = 10_000_000_000L

        repeat(500) { index ->
            val seconds = index / 100f
            val vertical = bumpWave(seconds)
            val sample = MotionSample(
                elapsedRealtimeNanos = base + index * 10_000_000L,
                epochMillis = index * 10L,
                accelerationX = 0f,
                accelerationY = -0.25f,
                accelerationZ = 9.80665f + vertical,
                gyroscopeX = 0.01f,
                gyroscopeY = 0.01f,
                gyroscopeZ = 0.01f,
                verticalAcceleration = vertical,
                longitudinalAcceleration = if (seconds in 1.2f..2.1f) -0.5f else 0f,
                lateralAcceleration = 0.03f,
                orientationChangeRadians = 0.01f,
                orientationReliable = true,
            )
            event = detector.addSample(sample, 8f, LocationQuality.GOOD, 0.95f) ?: event
        }

        assertNotNull(event)
        requireNotNull(event)
        assertTrue(event!!.eventType == RoadEventType.LIKELY_SPEED_BUMP || event!!.eventType == RoadEventType.POSSIBLE_SPEED_BUMP)
        assertTrue(event!!.confidence >= 0.5f)
    }

    @Test
    fun `stationary samples never arm detector`() {
        val detector = HeuristicRoadEventDetector()
        var event: com.jasermohamed.bumpcompanion.domain.model.DetectedRoadEvent? = null
        repeat(200) { index ->
            val vertical = if (index == 50) 7f else if (index == 80) -6f else 0f
            event = detector.addSample(
                MotionSample(
                    elapsedRealtimeNanos = index * 10_000_000L,
                    epochMillis = index * 10L,
                    accelerationX = 0f,
                    accelerationY = 0f,
                    accelerationZ = 9.80665f + vertical,
                    verticalAcceleration = vertical,
                ),
                speedMetresPerSecond = 0f,
                locationQuality = LocationQuality.GOOD,
                phoneStabilityScore = 1f,
            ) ?: event
        }
        assertTrue(event == null)
    }

    private fun bumpWave(timeSeconds: Float): Float {
        val rise = 5.0f * exp(-((timeSeconds - 1.7f) * (timeSeconds - 1.7f)) / 0.018f)
        val fall = -4.3f * exp(-((timeSeconds - 2.05f) * (timeSeconds - 2.05f)) / 0.025f)
        return rise + fall + 0.08f * sin(2f * PI.toFloat() * 7f * timeSeconds)
    }
}
