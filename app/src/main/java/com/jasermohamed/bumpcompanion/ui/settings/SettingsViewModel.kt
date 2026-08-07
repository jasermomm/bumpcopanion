package com.jasermohamed.bumpcompanion.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.data.exchange.BumpExchangeManager
import com.jasermohamed.bumpcompanion.data.exchange.ImportPreview
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import javax.inject.Inject

data class PendingImport(
    val uri: Uri,
    val preview: ImportPreview,
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val busy: Boolean = false,
    val message: String? = null,
    val pendingImport: PendingImport? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bumpRepository: BumpRepository,
    private val driveRepository: DriveRepository,
    private val exchangeManager: BumpExchangeManager,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingImport = MutableStateFlow<PendingImport?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        busy,
        message,
        pendingImport,
    ) { settings, working, text, import ->
        SettingsUiState(settings, working, text, import)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setSensitivity(value: Sensitivity) = launch { settingsRepository.setSensitivity(value) }
    fun setWarnings(value: Boolean) = launch { settingsRepository.setWarningsEnabled(value) }
    fun setVoice(value: Boolean) = launch { settingsRepository.setVoiceEnabled(value) }
    fun setTone(value: Boolean) = launch { settingsRepository.setToneEnabled(value) }
    fun setVibration(value: Boolean) = launch { settingsRepository.setVibrationEnabled(value) }
    fun setAdaptiveDistance(value: Boolean) = launch { settingsRepository.setAdaptiveDistance(value) }
    fun setRouteHistory(value: Boolean) = launch { settingsRepository.setRouteHistoryEnabled(value) }
    fun setMetric(value: Boolean) = launch { settingsRepository.setMetricUnits(value) }
    fun setTheme(value: ThemeMode) = launch { settingsRepository.setThemeMode(value) }
    fun setDynamicColour(value: Boolean) = launch { settingsRepository.setDynamicColour(value) }
    fun export(uri: Uri) = viewModelScope.launch {
        busy.value = true
        exchangeManager.exportTo(uri, context.getString(R.string.export_list_name))
            .onSuccess { message.value = context.getString(R.string.export_result_format, it) }
            .onFailure { message.value = exchangeError(it, R.string.export_failed) }
        busy.value = false
    }

    fun previewImport(uri: Uri) = viewModelScope.launch {
        busy.value = true
        exchangeManager.preview(uri)
            .onSuccess { pendingImport.value = PendingImport(uri, it) }
            .onFailure { message.value = exchangeError(it, R.string.import_failed) }
        busy.value = false
    }

    fun confirmImport() = viewModelScope.launch {
        val selected = pendingImport.value ?: return@launch
        pendingImport.value = null
        busy.value = true
        exchangeManager.importFrom(selected.uri, selected.preview.sourceLabel)
            .onSuccess { message.value = context.getString(R.string.import_result_format, it.inserted, it.merged, it.invalid) }
            .onFailure { message.value = exchangeError(it, R.string.import_failed) }
        busy.value = false
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    fun deleteHistory() = viewModelScope.launch {
        runCatching { driveRepository.deleteHistory() }
            .onSuccess { message.value = context.getString(R.string.drive_history_deleted) }
            .onFailure { message.value = context.getString(R.string.action_failed) }
    }

    fun deleteAll() = viewModelScope.launch {
        busy.value = true
        runCatching {
            bumpRepository.deleteAll()
            driveRepository.deleteHistory()
            settingsRepository.reset()
        }.onSuccess {
            message.value = context.getString(R.string.all_local_data_deleted)
        }.onFailure {
            message.value = context.getString(R.string.action_failed)
        }
        busy.value = false
    }

    fun consumeMessage() { message.value = null }
    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    private fun exchangeError(error: Throwable, fallback: Int): String = when (error) {
        is SerializationException -> context.getString(fallback)
        is IllegalArgumentException, is IllegalStateException -> error.message ?: context.getString(fallback)
        else -> context.getString(fallback)
    }
}
