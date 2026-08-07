package com.jasermohamed.bumpcompanion.domain.approach

import com.jasermohamed.bumpcompanion.domain.model.BumpSource
import com.jasermohamed.bumpcompanion.domain.model.Directionality
import com.jasermohamed.bumpcompanion.domain.model.GeoPoint
import com.jasermohamed.bumpcompanion.domain.model.SpeedBump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachPredictorTest {
    @Test
    fun `warning distance increases with speed`() {
        val predictor = ApproachPredictor()
        val slow = predictor.adaptiveWarningDistance(3f, 8f)
        val fast = predictor.adaptiveWarningDistance(20f, 8f)
        assertTrue(fast > slow)
    }

    @Test
    fun `direct approach warns once and detects pass`() {
        val predictor = ApproachPredictor(warningCooldownMillis = 60_000L)
        val bump = SpeedBump(
            id = "direct",
            latitude = 30.0444,
            longitude = 31.2380,
            confidence = 0.95f,
            coordinateConfidence = 0.95f,
            source = BumpSource.DETECTED,
            directionality = Directionality.BIDIRECTIONAL,
        )
        var warningCount = 0
        var passed = false
        for (index in 0..50) {
            val point = GeoPoint(
                latitude = 30.0444,
                longitude = 31.2340 + index * 0.0001,
                accuracyMetres = 7f,
                bearingDegrees = 90f,
                speedMetresPerSecond = 10f,
                elapsedRealtimeNanos = index * 1_000_000_000L,
                epochMillis = index * 1_000L,
            )
            when (predictor.update(point, listOf(bump), index * 1_000L)) {
                is ApproachDecision.Warn -> warningCount++
                is ApproachDecision.Passed -> passed = true
                else -> Unit
            }
        }
        assertEquals(1, warningCount)
        assertTrue(passed)
    }

    @Test
    fun `parallel road does not warn`() {
        val predictor = ApproachPredictor()
        val bump = SpeedBump(
            id = "parallel",
            latitude = 30.0453,
            longitude = 31.2380,
            confidence = 0.95f,
            source = BumpSource.DETECTED,
        )
        var warningCount = 0
        for (index in 0..45) {
            val point = GeoPoint(
                latitude = 30.0444,
                longitude = 31.2340 + index * 0.0001,
                accuracyMetres = 6f,
                bearingDegrees = 90f,
                speedMetresPerSecond = 11f,
                elapsedRealtimeNanos = index * 1_000_000_000L,
                epochMillis = index * 1_000L,
            )
            if (predictor.update(point, listOf(bump), index * 1_000L) is ApproachDecision.Warn) warningCount++
        }
        assertEquals(0, warningCount)
    }
}
