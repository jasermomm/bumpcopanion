package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.approach.GeoMath
import com.jasermohamed.bumpcompanion.domain.model.GeoPoint
import kotlin.math.abs

class EventLocationEstimator {
    data class Estimate(
        val point: GeoPoint?,
        val coordinateConfidence: Float,
        val method: String,
        val correctionDistanceMetres: Float,
    )

    fun estimate(eventElapsedRealtimeNanos: Long, locations: List<GeoPoint>): Estimate {
        if (locations.isEmpty()) return Estimate(null, 0f, "unavailable", 0f)
        val sorted = locations.sortedBy { it.elapsedRealtimeNanos }
        val before = sorted.lastOrNull { it.elapsedRealtimeNanos <= eventElapsedRealtimeNanos }
        val after = sorted.firstOrNull { it.elapsedRealtimeNanos >= eventElapsedRealtimeNanos }
        val nearest = sorted.minByOrNull { abs(it.elapsedRealtimeNanos - eventElapsedRealtimeNanos) }
            ?: return Estimate(null, 0f, "unavailable", 0f)
        val ageMillis = abs(nearest.elapsedRealtimeNanos - eventElapsedRealtimeNanos) / 1_000_000L
        if (ageMillis > 8_000) return Estimate(null, 0f, "stale", 0f)

        if (before != null && after != null && before !== after && after.elapsedRealtimeNanos > before.elapsedRealtimeNanos) {
            val fraction = ((eventElapsedRealtimeNanos - before.elapsedRealtimeNanos).toDouble() /
                (after.elapsedRealtimeNanos - before.elapsedRealtimeNanos).toDouble()).coerceIn(0.0, 1.0)
            val lat = before.latitude + (after.latitude - before.latitude) * fraction
            val lon = before.longitude + (after.longitude - before.longitude) * fraction
            val accuracy = listOf(before.accuracyMetres, after.accuracyMetres).filter { it.isFinite() }.maxOrNull() ?: Float.NaN
            val speed = before.speedMetresPerSecond.takeIf { it.isFinite() } ?: after.speedMetresPerSecond
            val bearing = before.bearingDegrees.takeIf { it.isFinite() } ?: after.bearingDegrees
            val point = GeoPoint(lat, lon, accuracy, bearing, speed, eventElapsedRealtimeNanos, nearest.epochMillis)
            val confidence = confidenceFor(accuracy, ageMillis)
            return Estimate(point, confidence, "interpolated", 0f)
        }

        var corrected = nearest
        var correction = 0f
        val deltaSeconds = (eventElapsedRealtimeNanos - nearest.elapsedRealtimeNanos) / 1_000_000_000f
        if (abs(deltaSeconds) <= 3f && nearest.speedMetresPerSecond.isFinite() && nearest.bearingDegrees.isFinite()) {
            val distance = nearest.speedMetresPerSecond * deltaSeconds
            val projected = GeoMath.project(nearest.latitude, nearest.longitude, distance.toDouble(), nearest.bearingDegrees.toDouble())
            corrected = nearest.copy(
                latitude = projected.latitude,
                longitude = projected.longitude,
                elapsedRealtimeNanos = eventElapsedRealtimeNanos,
            )
            correction = abs(distance)
        }
        return Estimate(corrected, confidenceFor(nearest.accuracyMetres, ageMillis), if (correction > 0f) "projected" else "nearest", correction)
    }

    private fun confidenceFor(accuracy: Float, ageMillis: Long): Float {
        val accuracyScore = when {
            !accuracy.isFinite() -> 0.45f
            accuracy <= 8f -> 1f
            accuracy <= 20f -> 0.82f
            accuracy <= 40f -> 0.58f
            accuracy <= 80f -> 0.32f
            else -> 0.15f
        }
        val ageScore = (1f - ageMillis / 8_000f).coerceIn(0f, 1f)
        return (accuracyScore * 0.75f + ageScore * 0.25f).coerceIn(0f, 1f)
    }
}
