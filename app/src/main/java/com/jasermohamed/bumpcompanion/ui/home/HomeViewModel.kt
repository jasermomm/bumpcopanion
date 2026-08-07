package com.jasermohamed.bumpcompanion.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.BumpRepository
import com.jasermohamed.bumpcompanion.domain.repository.DriveRepository
import com.jasermohamed.bumpcompanion.domain.repository.SettingsRepository
import com.jasermohamed.bumpcompanion.platform.location.LocationProvider
import com.jasermohamed.bumpcompanion.platform.navigation.NavigationAppLauncher
import com.jasermohamed.bumpcompanion.platform.sensors.MotionSensorProvider
import com.jasermohamed.bumpcompanion.service.DriveRuntimeStore
import com.jasermohamed.bumpcompanion.service.DriveServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val runtime: DriveRuntimeState = DriveRuntimeState(),
    val confirmedBumps: Int = 0,
    val pendingCandidates: Int = 0,
    val latestDrive: DriveSession? = null,
    val sensorCapabilities: SensorCapabilities = SensorCapabilities(),
    val locationEnabled: Boolean = false,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    runtimeStore: DriveRuntimeStore,
    bumpRepository: BumpRepository,
    driveRepository: DriveRepository,
    settingsRepository: SettingsRepository,
    motionSensorProvider: MotionSensorProvider,
    private val locationProvider: LocationProvider,
    private val controller: DriveServiceController,
    private val navigationAppLauncher: NavigationAppLauncher,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val readiness = MutableStateFlow(locationProvider.isLocationEnabled())

    private val baseState = combine(
        runtimeStore.state,
        bumpRepository.observeConfirmedCount(),
        bumpRepository.observePendingCount(),
        driveRepository.observeLatestDrive(),
        readiness,
    ) { runtime, confirmed, pending, latest, locationEnabled ->
        HomeUiState(
            runtime = runtime,
            confirmedBumps = confirmed,
            pendingCandidates = pending,
            latestDrive = latest,
            sensorCapabilities = motionSensorProvider.capabilities,
            locationEnabled = locationEnabled,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        baseState,
        message,
        settingsRepository.settings,
    ) { base, text, settings ->
        base.copy(message = text, settings = settings)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(sensorCapabilities = motionSensorProvider.capabilities),
    )

    fun refreshReadiness() { readiness.value = locationProvider.isLocationEnabled() }
    fun startDrive() = controller.start()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
    fun markBump() = controller.markBump()
    fun toggleMute() = controller.toggleMute()

    fun openNavigation() = viewModelScope.launch {
        navigationAppLauncher.openChooser().onFailure {
            message.value = context.getString(R.string.navigation_app_unavailable)
        }
    }

    fun consumeMessage() { message.value = null }
}
