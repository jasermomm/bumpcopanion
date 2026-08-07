package com.jasermohamed.bumpcompanion.ui.calibration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.data.calibration.CalibrationManager
import com.jasermohamed.bumpcompanion.domain.model.CalibrationProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalibrationUiState(
    val latest: CalibrationProfile? = null,
    val running: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: CalibrationManager,
) : ViewModel() {
    private val running = MutableStateFlow(false)
    private val completed = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<CalibrationUiState> = combine(manager.latest, running, completed, error) { latest, active, done, message ->
        CalibrationUiState(latest, active, done, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationUiState())

    fun start() = viewModelScope.launch {
        if (running.value) return@launch
        running.value = true
        completed.value = false
        error.value = null
        manager.calibrate()
            .onSuccess { completed.value = true }
            .onFailure { error.value = context.getString(R.string.calibration_failed) }
        running.value = false
    }

    fun reset() = viewModelScope.launch {
        manager.reset()
        completed.value = false
        error.value = null
    }
}
