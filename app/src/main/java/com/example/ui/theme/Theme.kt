package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MT5Blue,
    secondary = MT5Red,
    tertiary = MT5BlueLight,
    background = MT5Background,
    surface = MT5Surface,
    onPrimary = MT5Surface,
    onSecondary = MT5Surface,
    onBackground = MT5TextPrimary,
    onSurface = MT5TextPrimary,
    outline = MT5Separator,
    surfaceVariant = MT5Background,
    onSurfaceVariant = MT5TextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
