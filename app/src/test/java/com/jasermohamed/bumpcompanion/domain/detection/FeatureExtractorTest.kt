package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {
    @Test
    fun `extracts opposing peaks and timing`() {
        val values = listOf(0f, 0.2f, 1f, 4.8f, 1.2f, -0.5f, -4.1f, -1f, 0.1f, 0f)
        val samples = values.mapIndexed { index, vertical ->
            MotionSample(
                elapsedRealtimeNanos = index * 100_000_000L,
                epochMillis = index * 100L,
                accelerationX = 0f,
                accelerationY = 0f,
                accelerationZ = 9.80665f + vertical,
                verticalAcceleration = vertical,
                orientationReliable = true,
            )
        }

        val features = FeatureExtractor().extract(samples, baselineNoise = 0.5f)

        assertNotNull(features)
        requireNotNull(features)
        assertEquals(4.8f, features.positiveVerticalPeak, 0.001f)
        assertEquals(-4.1f, features.negativeVerticalPeak, 0.001f)
        assertEquals(300L, features.peakGapMillis)
        assertTrue(features.peakToPeak > 8f)
        assertTrue(features.jerkPeak > 0f)
    }
}
