package com.jasermohamed.bumpcompanion.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val sensitivity: Sensitivity = Sensitivity.BALANCED,
    val warningsEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val toneEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val adaptiveWarningDistance: Boolean = true,
    val fixedWarningDistanceMetres: Int = 100,
    val warningCooldownSeconds: Int = 45,
    val routeHistoryEnabled: Boolean = false,
    val candidateNotificationEnabled: Boolean = false,
    val metricUnits: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColour: Boolean = true,
    val reducedMotion: Boolean = false,
    val preferredNavigationPackage: String? = null,
    val repeatEvidenceAutoConfirmation: Boolean = false,
    val diagnosticLoggingMode: String = "off",
)
