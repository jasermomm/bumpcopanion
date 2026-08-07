package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLocationEstimatorTest {
    private val estimator = EventLocationEstimator()

    @Test
    fun `interpolates between surrounding location samples`() {
        val result = estimator.estimate(
            eventElapsedRealtimeNanos = 2_000_000_000L,
            locations = listOf(
                GeoPoint(30.0, 31.0, 8f, 90f, 10f, 1_000_000_000L, 1_000L),
                GeoPoint(30.0, 31.002, 10f, 90f, 10f, 3_000_000_000L, 3_000L),
            ),
        )

        assertEquals("interpolated", result.method)
        assertEquals(31.001, requireNotNull(result.point).longitude, 0.000001)
        assertTrue(result.coordinateConfidence > 0.7f)
    }

    @Test
    fun `rejects stale location`() {
        val result = estimator.estimate(
            eventElapsedRealtimeNanos = 20_000_000_000L,
            locations = listOf(GeoPoint(30.0, 31.0, 8f, 90f, 10f, 1_000_000_000L, 1_000L)),
        )

        assertEquals("stale", result.method)
        assertNull(result.point)
    }
}
