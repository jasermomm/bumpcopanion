package com.jasermohamed.bumpcompanion.domain.approach

import com.jasermohamed.bumpcompanion.domain.model.GeoPoint
import kotlin.math.*

object GeoMath {
    private const val EARTH_RADIUS_METRES = 6_371_000.0

    data class BoundingBox(
        val minLatitude: Double,
        val maxLatitude: Double,
        val minLongitude: Double,
        val maxLongitude: Double,
    )

    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METRES * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    fun smallestAngleDifference(a: Float, b: Float): Float {
        val delta = ((a - b + 540f) % 360f) - 180f
        return abs(delta)
    }

    fun project(latitude: Double, longitude: Double, distanceMetres: Double, bearingDegrees: Double): GeoPoint {
        val angular = distanceMetres / EARTH_RADIUS_METRES
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    fun boundingBox(latitude: Double, longitude: Double, radiusMetres: Double): BoundingBox {
        val latDelta = Math.toDegrees(radiusMetres / EARTH_RADIUS_METRES)
        val lonDelta = Math.toDegrees(radiusMetres / (EARTH_RADIUS_METRES * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)))
        return BoundingBox(latitude - latDelta, latitude + latDelta, longitude - lonDelta, longitude + lonDelta)
    }

    fun alongTrackMetres(start: GeoPoint, end: GeoPoint, target: GeoPoint): Double {
        val pathDistance = distanceMetres(start.latitude, start.longitude, end.latitude, end.longitude)
        if (pathDistance < 1.0) return 0.0
        val pathBearing = initialBearingDegrees(start.latitude, start.longitude, end.latitude, end.longitude)
        val targetBearing = initialBearingDegrees(start.latitude, start.longitude, target.latitude, target.longitude)
        val targetDistance = distanceMetres(start.latitude, start.longitude, target.latitude, target.longitude)
        return targetDistance * cos(Math.toRadians(smallestSignedAngle(targetBearing, pathBearing).toDouble()))
    }

    private fun smallestSignedAngle(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f
}
