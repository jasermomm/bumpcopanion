package com.jasermohamed.bumpcompanion.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jasermohamed.bumpcompanion.domain.model.ThemeMode

private val LightColours = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDEA1),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF62605A),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFECE1D3),
    error = Color(0xFFBA1A1A),
)

private val DarkColours = darkColorScheme(
    primary = Color(0xFFFFB94B),
    onPrimary = Color(0xFF482900),
    primaryContainer = Color(0xFF674000),
    onPrimaryContainer = Color(0xFFFFDEA1),
    secondary = Color(0xFFCCC5BB),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF4E463D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun BumpCompanionTheme(
    themeMode: ThemeMode,
    dynamicColour: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colours = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColours
        else -> LightColours
    }
    if (context is Activity) {
        WindowCompat.getInsetsController(context.window, context.window.decorView).isAppearanceLightStatusBars = !dark
    }
    MaterialTheme(
        colorScheme = colours,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
