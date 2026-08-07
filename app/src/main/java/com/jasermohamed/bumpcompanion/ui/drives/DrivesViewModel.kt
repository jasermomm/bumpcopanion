package com.jasermohamed.bumpcompanion.ui.drives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.domain.model.AppSettings
import com.jasermohamed.bumpcompanion.domain.model.DriveSession
import com.jasermohamed.bumpcompanion.domain.repository.DriveRepository
import com.jasermohamed.bumpcompanion.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DrivesUiState(
    val drives: List<DriveSession> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class DrivesViewModel @Inject constructor(
    repository: DriveRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<DrivesUiState> = combine(
        repository.observeDrives(),
        settingsRepository.settings,
    ) { drives, settings -> DrivesUiState(drives, settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrivesUiState())
}
