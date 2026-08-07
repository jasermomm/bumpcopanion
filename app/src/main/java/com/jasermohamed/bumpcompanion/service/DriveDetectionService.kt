package com.jasermohamed.bumpcompanion.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.jasermohamed.bumpcompanion.BuildConfig
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.approach.*
import com.jasermohamed.bumpcompanion.domain.detection.*
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.*
import com.jasermohamed.bumpcompanion.platform.location.LocationProvider
import com.jasermohamed.bumpcompanion.platform.diagnostics.DetectorCsvRecorder
import com.jasermohamed.bumpcompanion.platform.sensors.MotionSensorProvider
import com.jasermohamed.bumpcompanion.platform.warnings.WarningOutput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class DriveDetectionService : Service(), MotionSensorProvider.Listener, LocationProvider.Listener {
    @Inject lateinit var runtimeStore: DriveRuntimeStore
    @Inject lateinit var motionSensors: MotionSensorProvider
    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var detector: RoadEventDetector
    @Inject lateinit var locationEstimator: EventLocationEstimator
    @Inject lateinit var approachPredictor: ApproachPredictor
    @Inject lateinit var bumpRepository: BumpRepository
    @Inject lateinit var driveRepository: DriveRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var warningOutput: WarningOutput
    @Inject lateinit var notificationFactory: DriveNotificationFactory

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private val processingMutex = Mutex()
    private data class QueuedMotion(val sample: MotionSample, val stabilityScore: Float)
    private val motionQueue = Channel<QueuedMotion>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val locationHistory = BoundedRingBuffer<GeoPoint>(60)
    private var settings = AppSettings()
    private var currentSession: DriveSession? = null
    private var isStarted = false
    private var previousLocation: GeoPoint? = null
    private var speedSum = 0.0
    private var speedSamples = 0
    private var maximumSpeed = 0f
    private val pendingTrackPoints = ArrayList<LocationTrackPoint>(12)
    private var lastTrackPointNanos = 0L
    private var stoppingCleanly = false
    private var diagnosticRecorder: DetectorCsvRecorder? = null
    private var lastNotificationUpdateElapsedMillis = 0L
    private val recoveryPreferences by lazy { getSharedPreferences("drive_service_recovery", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        notificationFactory.createChannels()
        serviceScope.launch {
            settingsRepository.settings.collectLatest {
                settings = it
                detector.updateConfiguration(DetectorConfiguration.forSensitivity(it.sensitivity))
            }
        }
        serviceScope.launch { driveRepository.recoverIncompleteDrives(System.currentTimeMillis()) }
        serviceScope.launch {
            for (queued in motionQueue) processMotionSample(queued.sample, queued.stabilityScore)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null && recoveryPreferences.getBoolean(KEY_WAS_ACTIVE, false)) {
            ensureForeground(DriveServiceState.PREPARING)
            serviceScope.launch { startDrive(recovered = true) }
            return START_STICKY
        }
        when (action) {
            DriveServiceActions.START -> {
                if (isStarted) {
                    updateNotification()
                } else {
                    ensureForeground(DriveServiceState.PREPARING)
                    serviceScope.launch { startDrive(recovered = false) }
                }
            }
            DriveServiceActions.PAUSE -> serviceScope.launch { pauseDrive() }
            DriveServiceActions.RESUME -> {
                ensureForeground(DriveServiceState.PREPARING)
                serviceScope.launch { resumeDrive() }
            }
            DriveServiceActions.MARK -> serviceScope.launch { markBump() }
            DriveServiceActions.MUTE -> serviceScope.launch { toggleMute() }
            DriveServiceActions.STOP -> serviceScope.launch { stopDrive(clean = true) }
        }
        return START_STICKY
    }

    private fun ensureForeground(state: DriveServiceState) {
        runtimeStore.update { it.copy(serviceState = state) }
        val notification = notificationFactory.activeNotification(runtimeStore.state.value, settings)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(
            this,
            DriveNotificationFactory.ACTIVE_NOTIFICATION_ID,
            notification,
            type,
        )
    }

    private suspend fun startDrive(recovered: Boolean) = processingMutex.withLock {
        startDriveLocked(recovered)
    }

    private suspend fun startDriveLocked(recovered: Boolean) {
        if (isStarted) return
        if (!motionSensors.capabilities.hasAccelerometer) {
            failAndStopLocked(getString(R.string.accelerometer_unavailable))
            return
        }
        if (!locationProvider.hasLocationPermission()) {
            failAndStopLocked(getString(R.string.precise_location_required))
            return
        }
        if (!locationProvider.isLocationEnabled()) {
            failAndStopLocked(getString(R.string.location_disabled))
            return
        }

        detector.reset()
        approachPredictor.reset()
        locationHistory.clear()
        previousLocation = null
        speedSum = 0.0
        speedSamples = 0
        maximumSpeed = 0f
        pendingTrackPoints.clear()
        lastTrackPointNanos = 0L
        stoppingCleanly = false

        currentSession = driveRepository.startDrive(motionSensors.capabilities.quality, null).let { session ->
            if (recovered) session.copy(serviceInterruptions = session.serviceInterruptions + 1) else session
        }
        if (recovered) currentSession?.let { driveRepository.updateDrive(it) }
        isStarted = true
        diagnosticRecorder?.close()
        diagnosticRecorder = if (BuildConfig.DEBUG && settings.diagnosticLoggingMode != "off") {
            runCatching { DetectorCsvRecorder.create(this, currentSession?.id, settings.diagnosticLoggingMode) }
                .getOrNull()
        } else null
        detector.setDiagnosticsListener(diagnosticRecorder)
        recoveryPreferences.edit().putBoolean(KEY_WAS_ACTIVE, true).apply()

        val sensorStarted = motionSensors.start(this)
        val locationStarted = locationProvider.start(this)
        if (!sensorStarted || !locationStarted) {
            failAndStopLocked(getString(R.string.service_error_start))
            return
        }

        runtimeStore.update {
            it.copy(
                serviceState = DriveServiceState.ACTIVE,
                driveId = currentSession?.id,
                startedAt = currentSession?.startedAt,
                detectionQuality = motionSensors.capabilities.quality,
                phonePlacementState = PhonePlacementState.UNKNOWN,
                errorMessage = null,
            )
        }
        updateNotification()
    }

    private suspend fun pauseDrive() = processingMutex.withLock {
        if (!isStarted || runtimeStore.state.value.serviceState == DriveServiceState.PAUSED) return@withLock
        motionSensors.stop()
        locationProvider.stop()
        runtimeStore.update { it.copy(serviceState = DriveServiceState.PAUSED, pausedAt = System.currentTimeMillis()) }
        updateNotification()
    }

    private suspend fun resumeDrive() = processingMutex.withLock {
        if (!isStarted) {
            startDriveLocked(recovered = false)
            return@withLock
        }
        if (!locationProvider.hasLocationPermission() || !locationProvider.isLocationEnabled()) {
            failAndStopLocked(getString(R.string.service_error_location))
            return@withLock
        }
        val sensorsOk = motionSensors.start(this)
        val locationOk = locationProvider.start(this)
        if (!sensorsOk || !locationOk) {
            failAndStopLocked(getString(R.string.service_error_start))
            return@withLock
        }
        runtimeStore.update { it.copy(serviceState = DriveServiceState.ACTIVE, pausedAt = null, errorMessage = null) }
        updateNotification()
    }

    private suspend fun markBump() = processingMutex.withLock {
        if (!isStarted) return@withLock
        val state = runtimeStore.state.value
        val candidate = bumpRepository.createManualCandidate(state.driveId, state.lastLocation)
        runtimeStore.update { it.copy(candidateCount = it.candidateCount + 1) }
        currentSession = currentSession?.copy(candidateCount = (currentSession?.candidateCount ?: 0) + 1)
        warningOutput.announce(getString(R.string.warning_marked), settings, "manual-${candidate.id}")
        updateNotification()
    }

    private suspend fun toggleMute() = processingMutex.withLock {
        runtimeStore.update { it.copy(warningsMuted = !it.warningsMuted) }
        updateNotification()
    }

    private suspend fun stopDrive(clean: Boolean) = processingMutex.withLock {
        stopDriveLocked(clean)
    }

    private suspend fun stopDriveLocked(clean: Boolean) {
        if (!isStarted) {
            recoveryPreferences.edit().putBoolean(KEY_WAS_ACTIVE, false).apply()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        stoppingCleanly = clean
        runtimeStore.update { it.copy(serviceState = DriveServiceState.STOPPING) }
        updateNotification()
        motionSensors.stop()
        locationProvider.stop()
        detector.setDiagnosticsListener(null)
        diagnosticRecorder?.close()
        diagnosticRecorder = null
        flushTrackPoints()
        val state = runtimeStore.state.value
        val now = System.currentTimeMillis()
        currentSession?.let { session ->
            val completed = session.copy(
                endedAt = now,
                durationMillis = (now - session.startedAt).coerceAtLeast(0),
                distanceMetres = state.distanceMetres,
                maximumSpeedMetresPerSecond = maximumSpeed,
                averageSpeedMetresPerSecond = if (speedSamples > 0) (speedSum / speedSamples).toFloat() else 0f,
                candidateCount = state.candidateCount,
                knownBumpPasses = state.knownBumpsPassed,
                warningCount = state.warningCount,
                endLatitude = state.lastLocation?.latitude,
                endLongitude = state.lastLocation?.longitude,
                incomplete = !clean,
                detectionQuality = state.detectionQuality,
            )
            if (clean) driveRepository.finishDrive(completed) else driveRepository.updateDrive(completed)
        }
        currentSession = null
        isStarted = false
        recoveryPreferences.edit().putBoolean(KEY_WAS_ACTIVE, false).apply()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runtimeStore.reset()
        stopSelf()
    }

    override fun onMotionSample(sample: MotionSample, stabilityScore: Float) {
        motionQueue.trySend(QueuedMotion(sample, stabilityScore))
    }

    private suspend fun processMotionSample(sample: MotionSample, stabilityScore: Float) {
        processingMutex.withLock {
            if (!isStarted || runtimeStore.state.value.serviceState !in setOf(
                    DriveServiceState.ACTIVE,
                    DriveServiceState.GPS_DEGRADED,
                    DriveServiceState.SENSOR_DEGRADED,
                )
            ) return@withLock
            val state = runtimeStore.state.value
            val point = state.lastLocation
            val event = detector.addSample(
                sample = sample,
                speedMetresPerSecond = state.currentSpeedMetresPerSecond ?: 0f,
                locationQuality = state.locationQuality,
                phoneStabilityScore = stabilityScore,
                speedAccuracyMetresPerSecond = point?.speedAccuracyMetresPerSecond ?: Float.NaN,
                bearingDegrees = point?.bearingDegrees ?: Float.NaN,
            )
            runtimeStore.update {
                it.copy(
                    phoneStabilityScore = stabilityScore,
                    phonePlacementState = when {
                        stabilityScore >= 0.72f -> PhonePlacementState.STABLE
                        stabilityScore < 0.42f -> PhonePlacementState.UNSTABLE
                        else -> PhonePlacementState.UNKNOWN
                    },
                )
            }
            if (event != null) saveDetectedEvent(event, stabilityScore)
        }
    }

    private suspend fun saveDetectedEvent(event: DetectedRoadEvent, stabilityScore: Float) {
        val estimate = locationEstimator.estimate(event.features.eventElapsedRealtimeNanos, locationHistory.toList())
        val point = estimate.point
        val candidate = CandidateEvent(
            driveId = runtimeStore.state.value.driveId,
            detectedAt = point?.epochMillis?.takeIf { it > 0 } ?: System.currentTimeMillis(),
            eventElapsedRealtimeNanos = event.features.eventElapsedRealtimeNanos,
            latitude = point?.latitude,
            longitude = point?.longitude,
            horizontalAccuracyMetres = point?.accuracyMetres?.takeIf { it.isFinite() },
            coordinateConfidence = estimate.coordinateConfidence,
            speedMetresPerSecond = point?.speedMetresPerSecond?.takeIf { it.isFinite() } ?: runtimeStore.state.value.currentSpeedMetresPerSecond ?: 0f,
            bearingDegrees = point?.bearingDegrees?.takeIf { it.isFinite() },
            confidence = event.confidence,
            eventType = event.eventType,
            confidenceReasons = event.confidenceReasons,
            positiveVerticalPeak = event.features.positiveVerticalPeak,
            negativeVerticalPeak = event.features.negativeVerticalPeak,
            peakToPeak = event.features.peakToPeak,
            peakGapMillis = event.features.peakGapMillis,
            verticalRms = event.features.verticalRms,
            jerkPeak = event.features.jerkPeak,
            gyroscopeEnergy = event.features.gyroscopeEnergy,
            orientationVariance = event.features.orientationVariance,
            phoneStability = stabilityScore,
            source = BumpSource.DETECTED,
        )
        bumpRepository.saveCandidate(candidate)
        runtimeStore.update { it.copy(candidateCount = it.candidateCount + 1) }
        currentSession = currentSession?.copy(candidateCount = (currentSession?.candidateCount ?: 0) + 1)
        if (settings.candidateNotificationEnabled) {
            warningOutput.announce(getString(R.string.warning_possible_detected), settings.copy(voiceEnabled = false), "candidate-${candidate.id}")
        }
        updateNotification()
    }

    override fun onSensorError(message: String) {
        serviceScope.launch { failAndStop(message.ifBlank { getString(R.string.service_error_sensor) }) }
    }

    override fun onLocation(point: GeoPoint, quality: LocationQuality) {
        serviceScope.launch {
            processingMutex.withLock {
                if (!isStarted || runtimeStore.state.value.serviceState == DriveServiceState.PAUSED) return@withLock
                locationHistory.add(point)
                diagnosticRecorder?.recordLocation(point, quality)
                currentSession?.takeIf { it.startLatitude == null || it.startLongitude == null }?.let { session ->
                    val updated = session.copy(startLatitude = point.latitude, startLongitude = point.longitude)
                    currentSession = updated
                    driveRepository.updateDrive(updated)
                }
                val previous = previousLocation
                var distanceIncrement = 0.0
                if (previous != null) {
                    val delta = GeoMath.distanceMetres(previous.latitude, previous.longitude, point.latitude, point.longitude)
                    val elapsedSeconds = (point.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
                    val plausibleLimit = max(80.0, elapsedSeconds * 65.0)
                    if (delta in 0.5..plausibleLimit) distanceIncrement = delta
                }
                previousLocation = point
                val rawSpeed = point.speedMetresPerSecond.takeIf { it.isFinite() && it >= 0f }
                val previousSpeed = runtimeStore.state.value.currentSpeedMetresPerSecond
                val smoothedSpeed = rawSpeed?.let { if (previousSpeed == null) it else previousSpeed * 0.65f + it * 0.35f }
                if (smoothedSpeed != null) {
                    speedSum += smoothedSpeed
                    speedSamples++
                    maximumSpeed = max(maximumSpeed, smoothedSpeed)
                }
                runtimeStore.update {
                    it.copy(
                        serviceState = when (quality) {
                            LocationQuality.POOR, LocationQuality.STALE -> DriveServiceState.GPS_DEGRADED
                            LocationQuality.UNAVAILABLE -> DriveServiceState.GPS_DEGRADED
                            else -> if (it.serviceState != DriveServiceState.SENSOR_DEGRADED) DriveServiceState.ACTIVE else it.serviceState
                        },
                        currentSpeedMetresPerSecond = smoothedSpeed,
                        distanceMetres = it.distanceMetres + distanceIncrement,
                        gpsAccuracyMetres = point.accuracyMetres.takeIf { value -> value.isFinite() },
                        locationQuality = quality,
                        lastLocation = point,
                    )
                }
                collectRouteHistory(point)
                processApproach(point)
                updateNotification(force = false)
            }
        }
    }


    private suspend fun collectRouteHistory(point: GeoPoint) {
        val driveId = currentSession?.id ?: return
        if (!settings.routeHistoryEnabled) return
        if (lastTrackPointNanos != 0L && point.elapsedRealtimeNanos - lastTrackPointNanos < 2_000_000_000L) return
        lastTrackPointNanos = point.elapsedRealtimeNanos
        pendingTrackPoints += LocationTrackPoint(
            driveId = driveId,
            elapsedRealtimeNanos = point.elapsedRealtimeNanos,
            epochMillis = point.epochMillis,
            latitude = point.latitude,
            longitude = point.longitude,
            accuracyMetres = point.accuracyMetres.takeIf { it.isFinite() } ?: 999f,
            bearingDegrees = point.bearingDegrees.takeIf { it.isFinite() },
            speedMetresPerSecond = point.speedMetresPerSecond.takeIf { it.isFinite() },
        )
        if (pendingTrackPoints.size >= 10) flushTrackPoints()
    }

    private suspend fun flushTrackPoints() {
        if (pendingTrackPoints.isEmpty()) return
        val batch = pendingTrackPoints.toList()
        pendingTrackPoints.clear()
        runCatching { driveRepository.recordTrackPoints(batch) }
            .onFailure {
                pendingTrackPoints.addAll(0, batch.takeLast((12 - pendingTrackPoints.size).coerceAtLeast(0)))
                runtimeStore.update { it.copy(errorMessage = getString(R.string.route_history_save_failed)) }
            }
    }

    private suspend fun processApproach(point: GeoPoint) {
        val nearby = bumpRepository.queryWarningCandidates(point, 350.0)
        val state = runtimeStore.state.value
        val decision = approachPredictor.update(
            location = point,
            candidates = nearby,
            nowMillis = System.currentTimeMillis(),
            warningsEnabled = settings.warningsEnabled && !state.warningsMuted,
            adaptiveDistance = settings.adaptiveWarningDistance,
            fixedWarningDistanceMetres = settings.fixedWarningDistanceMetres.toFloat(),
            cooldownMillis = settings.warningCooldownSeconds * 1_000L,
        )
        when (decision) {
            ApproachDecision.None -> runtimeStore.update { it.copy(nextBumpDistanceMetres = null) }
            is ApproachDecision.Tracking -> runtimeStore.update { it.copy(nextBumpDistanceMetres = decision.distanceMetres) }
            is ApproachDecision.Warn -> {
                warningOutput.warnSpeedBump(decision.distanceMetres, decision.phase, settings)
                notificationFactory.showWarning(decision.distanceMetres, settings)
                bumpRepository.updateLastWarned(decision.bump.id, System.currentTimeMillis())
                runtimeStore.update {
                    it.copy(
                        nextBumpDistanceMetres = decision.distanceMetres,
                        warningCount = it.warningCount + 1,
                    )
                }
            }
            is ApproachDecision.Passed -> {
                runtimeStore.update { it.copy(knownBumpsPassed = it.knownBumpsPassed + 1, nextBumpDistanceMetres = null) }
                bumpRepository.recordEncounter(
                    EncounterSummary(
                        id = UUID.randomUUID().toString(),
                        bumpId = decision.bump.id,
                        candidateId = null,
                        driveId = state.driveId,
                        encounteredAt = System.currentTimeMillis(),
                        location = point,
                        confidence = decision.bump.confidence,
                        source = decision.bump.source,
                    )
                )
            }
        }
    }

    override fun onLocationError(message: String) {
        serviceScope.launch {
            processingMutex.withLock {
                runtimeStore.update {
                    it.copy(
                        serviceState = DriveServiceState.GPS_DEGRADED,
                        locationQuality = LocationQuality.UNAVAILABLE,
                        errorMessage = message,
                    )
                }
                updateNotification(force = false)
                if (!locationProvider.hasLocationPermission() || !locationProvider.isLocationEnabled()) {
                    failAndStopLocked(getString(R.string.service_error_location))
                }
            }
        }
    }

    private suspend fun failAndStop(message: String) = processingMutex.withLock {
        failAndStopLocked(message)
    }

    private suspend fun failAndStopLocked(message: String) {
        runtimeStore.update { it.copy(serviceState = DriveServiceState.FAILED, errorMessage = message) }
        notificationFactory.showError(message)
        if (isStarted) {
            stopDriveLocked(clean = false)
        } else {
            recoveryPreferences.edit().putBoolean(KEY_WAS_ACTIVE, false).apply()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification(force: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotificationUpdateElapsedMillis < 5_000L) return
        lastNotificationUpdateElapsedMillis = now
        val notification = notificationFactory.activeNotification(runtimeStore.state.value, settings)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(DriveNotificationFactory.ACTIVE_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        motionSensors.stop()
        locationProvider.stop()
        detector.setDiagnosticsListener(null)
        diagnosticRecorder?.close()
        diagnosticRecorder = null
        warningOutput.release()
        if (!stoppingCleanly && isStarted) recoveryPreferences.edit().putBoolean(KEY_WAS_ACTIVE, true).apply()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KEY_WAS_ACTIVE = "was_active"
    }
}
