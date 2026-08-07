package com.jasermohamed.bumpcompanion.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.GeoPoint
import com.jasermohamed.bumpcompanion.domain.model.LocationQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface LocationProvider {
    fun hasLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    fun start(listener: Listener): Boolean
    fun stop()

    interface Listener {
        fun onLocation(point: GeoPoint, quality: LocationQuality)
        fun onLocationError(message: String)
    }
}

@Singleton
class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationProvider.Listener? = null
    private var usingFused = false

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::dispatch)
        }
        override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) listener?.onLocationError(context.getString(R.string.location_temporarily_unavailable))
        }
    }

    private val frameworkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = dispatch(location)
        override fun onProviderDisabled(provider: String) { listener?.onLocationError(context.getString(R.string.location_provider_disabled)) }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Deprecated platform callback; required for older API compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isLocationEnabled(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    override fun start(listener: LocationProvider.Listener): Boolean {
        stop()
        if (!hasLocationPermission()) {
            listener.onLocationError(context.getString(R.string.location_permission_missing))
            return false
        }
        if (!isLocationEnabled()) {
            listener.onLocationError(context.getString(R.string.location_device_disabled))
            return false
        }
        this.listener = listener
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (playServicesAvailable) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateDelayMillis(2_000L)
                .setWaitForAccurateLocation(false)
                .build()
            return runCatching {
                fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                usingFused = true
                true
            }.getOrElse {
                startFramework(listener)
            }
        }
        return startFramework(listener)
    }

    @SuppressLint("MissingPermission")
    private fun startFramework(listener: LocationProvider.Listener): Boolean = runCatching {
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> throw IllegalStateException(context.getString(R.string.location_no_provider))
        }
        locationManager.requestLocationUpdates(provider, 750L, 1f, frameworkListener, Looper.getMainLooper())
        usingFused = false
        true
    }.getOrElse {
        listener.onLocationError(context.getString(R.string.location_temporarily_unavailable))
        false
    }

    override fun stop() {
        runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
        runCatching { locationManager.removeUpdates(frameworkListener) }
        listener = null
        usingFused = false
    }

    private fun dispatch(location: Location) {
        val elapsedNanos = if (location.elapsedRealtimeNanos > 0L) location.elapsedRealtimeNanos else SystemClock.elapsedRealtimeNanos()
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - elapsedNanos) / 1_000_000L
        val point = GeoPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = if (location.hasAccuracy()) location.accuracy else Float.NaN,
            bearingDegrees = if (location.hasBearing()) location.bearing else Float.NaN,
            speedMetresPerSecond = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else Float.NaN,
            elapsedRealtimeNanos = elapsedNanos,
            epochMillis = location.time,
            speedAccuracyMetresPerSecond = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                location.speedAccuracyMetersPerSecond
            } else Float.NaN,
        )
        val quality = when {
            ageMillis > 8_000 -> LocationQuality.STALE
            !point.accuracyMetres.isFinite() -> LocationQuality.FAIR
            point.accuracyMetres <= 15f -> LocationQuality.GOOD
            point.accuracyMetres <= 40f -> LocationQuality.FAIR
            else -> LocationQuality.POOR
        }
        listener?.onLocation(point, quality)
    }
}
