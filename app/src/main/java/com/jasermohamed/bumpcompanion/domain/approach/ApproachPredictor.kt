package com.jasermohamed.bumpcompanion.domain.approach

import com.jasermohamed.bumpcompanion.domain.model.*

sealed interface ApproachDecision {
    data object None : ApproachDecision
    data class Tracking(val bump: SpeedBump, val distanceMetres: Float, val score: Float) : ApproachDecision
    data class Warn(val bump: SpeedBump, val distanceMetres: Float, val phase: WarningPhase, val score: Float) : ApproachDecision
    data class Passed(val bump: SpeedBump) : ApproachDecision
}

enum class WarningPhase { EARLY, MAIN, IMMEDIATE }

class ApproachPredictor(
    private val warningCooldownMillis: Long = 45_000L,
) {
    private data class Track(
        val distances: ArrayDeque<Float> = ArrayDeque(),
        var warnedAt: Long? = null,
        var closestDistance: Float = Float.MAX_VALUE,
        var wasInsidePassRadius: Boolean = false,
        var passed: Boolean = false,
    )

    private val tracks = mutableMapOf<String, Track>()
    private val recentPath = ArrayDeque<GeoPoint>()

    fun reset() {
        tracks.clear()
        recentPath.clear()
    }

    fun update(
        location: GeoPoint,
        candidates: List<SpeedBump>,
        nowMillis: Long,
        warningsEnabled: Boolean = true,
        adaptiveDistance: Boolean = true,
        fixedWarningDistanceMetres: Float = 100f,
        cooldownMillis: Long = warningCooldownMillis,
    ): ApproachDecision {
        updatePath(location)
        val speed = location.speedMetresPerSecond.takeIf { it.isFinite() } ?: 0f
        if (speed < 1.2f) return ApproachDecision.None
        val travelBearing = stableTravelBearing(location) ?: return ApproachDecision.None

        var best: ApproachDecision.Tracking? = null
        for (bump in candidates) {
            if (!bump.warningEnabled || bump.archived || bump.markedRemoved) continue
            val distance = GeoMath.distanceMetres(location.latitude, location.longitude, bump.latitude, bump.longitude).toFloat()
            val track = tracks.getOrPut(bump.id) { Track() }
            track.distances.add(distance)
            while (track.distances.size > 5) track.distances.removeFirst()
            track.closestDistance = minOf(track.closestDistance, distance)

            if (distance <= passRadius(location.accuracyMetres)) track.wasInsidePassRadius = true
            val increasing = track.distances.size >= 3 && track.distances.zipWithNext().takeLast(2).all { (a, b) -> b > a + 1.5f }
            if (track.wasInsidePassRadius && increasing && distance > track.closestDistance + 8f) {
                if (!track.passed) {
                    track.passed = true
                    return ApproachDecision.Passed(bump)
                }
                continue
            }
            if (track.passed) continue

            val toBumpBearing = GeoMath.initialBearingDegrees(location.latitude, location.longitude, bump.latitude, bump.longitude)
            val relative = GeoMath.smallestAngleDifference(travelBearing, toBumpBearing)
            val headingTolerance = headingConeDegrees(speed, location.accuracyMetres)
            val ahead = relative <= headingTolerance
            val decreasing = track.distances.size >= 3 && track.distances.zipWithNext().takeLast(2).all { (a, b) -> b < a - 0.5f }
            val directionCompatible = directionCompatible(bump, travelBearing)
            val pathCompatible = pathCorridorScore(bump, location, travelBearing) > 0.35f
            if (!ahead || !decreasing || !directionCompatible || !pathCompatible) continue

            val bearingScore = (1f - relative / headingTolerance).coerceIn(0f, 1f)
            val gpsScore = when {
                !location.accuracyMetres.isFinite() -> 0.45f
                location.accuracyMetres <= 12f -> 1f
                location.accuracyMetres <= 30f -> 0.72f
                location.accuracyMetres <= 60f -> 0.42f
                else -> 0.18f
            }
            val decreaseScore = decreasingStrength(track.distances)
            val confidenceScore = bump.confidence.coerceIn(0.25f, 1f)
            val score = bearingScore * 0.30f + gpsScore * 0.18f + decreaseScore * 0.25f + confidenceScore * 0.17f + pathCorridorScore(bump, location, travelBearing) * 0.10f
            if (score < 0.58f) continue
            val tracking = ApproachDecision.Tracking(bump, distance, score)
            if (best == null || tracking.score > best.score) best = tracking
        }

        val selected = best ?: return ApproachDecision.None
        val warningDistance = selected.bump.customWarningDistanceMetres?.toFloat()
            ?: if (adaptiveDistance) adaptiveWarningDistance(speed, location.accuracyMetres)
            else fixedWarningDistanceMetres.coerceIn(20f, 1_000f)
        if (!warningsEnabled || selected.distanceMetres > warningDistance) return selected
        val track = tracks.getValue(selected.bump.id)
        val lastWarning = listOfNotNull(track.warnedAt, selected.bump.lastWarnedAt).maxOrNull()
        if (lastWarning != null && nowMillis - lastWarning < cooldownMillis.coerceAtLeast(0L)) return selected
        val phase = when {
            selected.distanceMetres <= 35f -> WarningPhase.IMMEDIATE
            selected.distanceMetres <= warningDistance * 0.65f -> WarningPhase.MAIN
            else -> WarningPhase.EARLY
        }
        track.warnedAt = nowMillis
        return ApproachDecision.Warn(selected.bump, selected.distanceMetres, phase, selected.score)
    }

    fun adaptiveWarningDistance(speedMetresPerSecond: Float, gpsAccuracyMetres: Float): Float {
        val kmh = speedMetresPerSecond * 3.6f
        val base = when {
            kmh < 15f -> 45f
            kmh < 30f -> 75f
            kmh < 50f -> 125f
            kmh < 80f -> 205f
            else -> 260f
        }
        val gpsAdjustment = when {
            !gpsAccuracyMetres.isFinite() -> 15f
            gpsAccuracyMetres <= 15f -> 0f
            gpsAccuracyMetres <= 35f -> 15f
            else -> 30f
        }
        return (base + gpsAdjustment).coerceAtMost(300f)
    }

    private fun updatePath(location: GeoPoint) {
        if (recentPath.lastOrNull()?.let { GeoMath.distanceMetres(it.latitude, it.longitude, location.latitude, location.longitude) < 1.5 } == true) return
        recentPath.add(location)
        while (recentPath.size > 8) recentPath.removeFirst()
    }

    private fun stableTravelBearing(location: GeoPoint): Float? {
        if (recentPath.size >= 3) {
            val first = recentPath.first()
            val last = recentPath.last()
            if (GeoMath.distanceMetres(first.latitude, first.longitude, last.latitude, last.longitude) >= 8.0) {
                return GeoMath.initialBearingDegrees(first.latitude, first.longitude, last.latitude, last.longitude)
            }
        }
        return location.bearingDegrees.takeIf { it.isFinite() }
    }

    private fun headingConeDegrees(speed: Float, accuracy: Float): Float {
        val kmh = speed * 3.6f
        val base = when {
            kmh < 15f -> 58f
            kmh < 40f -> 42f
            else -> 32f
        }
        return base + if (accuracy.isFinite() && accuracy > 35f) 8f else 0f
    }

    private fun directionCompatible(bump: SpeedBump, travelBearing: Float): Boolean = when (bump.directionality) {
        Directionality.UNKNOWN, Directionality.BIDIRECTIONAL -> true
        Directionality.ONE_DIRECTION -> bump.primaryBearing?.let { GeoMath.smallestAngleDifference(it, travelBearing) <= bump.bearingTolerance } ?: true
        Directionality.TWO_CLUSTERS -> listOfNotNull(bump.primaryBearing, bump.oppositeBearing).any {
            GeoMath.smallestAngleDifference(it, travelBearing) <= bump.bearingTolerance
        }
    }

    private fun decreasingStrength(distances: ArrayDeque<Float>): Float {
        if (distances.size < 3) return 0f
        val list = distances.toList()
        val decrease = list.first() - list.last()
        return (decrease / 18f).coerceIn(0f, 1f)
    }

    private fun pathCorridorScore(bump: SpeedBump, location: GeoPoint, bearing: Float): Float {
        val projectionDistance = 220.0
        val projected = GeoMath.project(location.latitude, location.longitude, projectionDistance, bearing.toDouble())
        val bumpPoint = GeoPoint(bump.latitude, bump.longitude)
        val along = GeoMath.alongTrackMetres(location, projected, bumpPoint)
        if (along < -5.0 || along > projectionDistance * 1.25) return 0f
        val direct = GeoMath.distanceMetres(location.latitude, location.longitude, bump.latitude, bump.longitude)
        val projectedDistance = GeoMath.distanceMetres(projected.latitude, projected.longitude, bump.latitude, bump.longitude)
        val corridorWidth = 25.0 + (location.accuracyMetres.takeIf { it.isFinite() } ?: 30f)
        val perpendicularApprox = kotlin.math.sqrt((direct * direct - along * along).coerceAtLeast(0.0))
        val corridorScore = (1.0 - perpendicularApprox / corridorWidth).coerceIn(0.0, 1.0).toFloat()
        val endpointScore = (1.0 - projectedDistance / 320.0).coerceIn(0.0, 1.0).toFloat()
        return maxOf(corridorScore, endpointScore * 0.5f)
    }

    private fun passRadius(accuracy: Float): Float = when {
        !accuracy.isFinite() -> 22f
        accuracy <= 10f -> 14f
        accuracy <= 30f -> 20f
        else -> 28f
    }
}
