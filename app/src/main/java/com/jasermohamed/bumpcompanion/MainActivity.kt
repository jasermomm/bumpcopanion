package com.jasermohamed.bumpcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasermohamed.bumpcompanion.ui.navigation.BumpCompanionRoot
import com.jasermohamed.bumpcompanion.ui.navigation.RootViewModel
import com.jasermohamed.bumpcompanion.ui.onboarding.OnboardingRoute
import com.jasermohamed.bumpcompanion.ui.theme.BumpCompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val viewModel: RootViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            BumpCompanionTheme(settings.themeMode, settings.dynamicColour) {
                if (settings.onboardingComplete) BumpCompanionRoot() else OnboardingRoute()
            }
        }
    }
}
