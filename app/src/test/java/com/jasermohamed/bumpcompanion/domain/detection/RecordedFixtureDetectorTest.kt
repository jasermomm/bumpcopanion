package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.LocationQuality
import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordedFixtureDetectorTest {
    @Test
    fun `recorded-style speed bump fixture creates a candidate`() {
        assertNotNull(runFixture("fixtures/speed_bump.csv", phoneStability = 0.95f))
    }

    @Test
    fun `smooth-road fixture creates no candidate`() {
        assertNull(runFixture("fixtures/smooth_road.csv", phoneStability = 0.95f))
    }

    @Test
    fun `phone-movement fixture is rejected`() {
        assertNull(runFixture("fixtures/phone_movement.csv", phoneStability = 0.2f))
    }

    @Test
    fun `sharp pothole fixture is rejected`() {
        assertNull(runFixture("fixtures/pothole.csv", phoneStability = 0.95f))
    }

    private fun runFixture(name: String, phoneStability: Float): Any? {
        val detector = HeuristicRoadEventDetector()
        var detected: Any? = null
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(name))
        stream.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { row ->
                val values = row.split(',')
                val elapsedMs = values[0].toLong()
                val vertical = values[1].toFloat()
                val longitudinal = values[2].toFloat()
                val lateral = values[3].toFloat()
                val gyro = values[4].toFloat()
                val event = detector.addSample(
                    MotionSample(
                        elapsedRealtimeNanos = elapsedMs * 1_000_000L,
                        epochMillis = elapsedMs,
                        accelerationX = lateral,
                        accelerationY = longitudinal,
                        accelerationZ = 9.80665f + vertical,
                        gyroscopeX = gyro,
                        verticalAcceleration = vertical,
                        longitudinalAcceleration = longitudinal,
                        lateralAcceleration = lateral,
                        orientationChangeRadians = gyro * 0.01f,
                        orientationReliable = true,
                    ),
                    speedMetresPerSecond = 8f,
                    locationQuality = LocationQuality.GOOD,
                    phoneStabilityScore = phoneStability,
                )
                if (event != null) detected = event
            }
        }
        return detected
    }
}
