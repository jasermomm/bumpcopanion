package com.jasermohamed.bumpcompanion.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "bump_companion_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val sensitivity = stringPreferencesKey("sensitivity")
        val warnings = booleanPreferencesKey("warnings_enabled")
        val voice = booleanPreferencesKey("voice_enabled")
        val tone = booleanPreferencesKey("tone_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val adaptiveDistance = booleanPreferencesKey("adaptive_distance")
        val routeHistory = booleanPreferencesKey("route_history")
        val metric = booleanPreferencesKey("metric_units")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColour = booleanPreferencesKey("dynamic_colour")
        val navigationPackage = stringPreferencesKey("navigation_package")
        val diagnosticLogging = stringPreferencesKey("diagnostic_logging")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingComplete = p[Keys.onboarding] ?: false,
            sensitivity = p[Keys.sensitivity]?.let { runCatching { Sensitivity.valueOf(it) }.getOrNull() } ?: Sensitivity.BALANCED,
            warningsEnabled = p[Keys.warnings] ?: true,
            voiceEnabled = p[Keys.voice] ?: true,
            toneEnabled = p[Keys.tone] ?: true,
            vibrationEnabled = p[Keys.vibration] ?: true,
            adaptiveWarningDistance = p[Keys.adaptiveDistance] ?: true,
            routeHistoryEnabled = p[Keys.routeHistory] ?: false,
            metricUnits = p[Keys.metric] ?: true,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColour = p[Keys.dynamicColour] ?: true,
            preferredNavigationPackage = p[Keys.navigationPackage],
            diagnosticLoggingMode = p[Keys.diagnosticLogging] ?: "off",
        )
    }

    private suspend fun edit(block: MutablePreferences.() -> Unit) {
        context.dataStore.edit { it.block() }
    }

    override suspend fun setOnboardingComplete(value: Boolean) = edit { this[Keys.onboarding] = value }
    override suspend fun setSensitivity(value: Sensitivity) = edit { this[Keys.sensitivity] = value.name }
    override suspend fun setWarningsEnabled(value: Boolean) = edit { this[Keys.warnings] = value }
    override suspend fun setVoiceEnabled(value: Boolean) = edit { this[Keys.voice] = value }
    override suspend fun setToneEnabled(value: Boolean) = edit { this[Keys.tone] = value }
    override suspend fun setVibrationEnabled(value: Boolean) = edit { this[Keys.vibration] = value }
    override suspend fun setAdaptiveDistance(value: Boolean) = edit { this[Keys.adaptiveDistance] = value }
    override suspend fun setRouteHistoryEnabled(value: Boolean) = edit { this[Keys.routeHistory] = value }
    override suspend fun setMetricUnits(value: Boolean) = edit { this[Keys.metric] = value }
    override suspend fun setThemeMode(value: ThemeMode) = edit { this[Keys.theme] = value.name }
    override suspend fun setDynamicColour(value: Boolean) = edit { this[Keys.dynamicColour] = value }
    override suspend fun setDiagnosticLoggingEnabled(value: Boolean) = edit {
        this[Keys.diagnosticLogging] = if (value) "full" else "off"
    }
    override suspend fun setPreferredNavigationPackage(value: String?) = edit {
        if (value == null) remove(Keys.navigationPackage) else this[Keys.navigationPackage] = value
    }
    override suspend fun reset() { context.dataStore.edit { it.clear() } }
}
