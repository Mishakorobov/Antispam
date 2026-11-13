package ru.antispam.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    secondary = AppleMintGreen,
    tertiary = AppleRed,
    background = AppleDarkBackground,
    surface = AppleDarkSurface,
    surfaceVariant = AppleDarkSurfaceSecondary,
    onPrimary = AppleDarkTextPrimary,
    onBackground = AppleDarkTextPrimary,
    onSurface = AppleDarkTextPrimary,
    outline = AppleDarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    secondary = AppleMintGreen,
    tertiary = AppleRed,
    background = AppleLightBackground,
    surface = AppleLightSurface,
    surfaceVariant = AppleLightSurfaceSecondary,
    onPrimary = AppleLightSurface,
    onBackground = AppleLightTextPrimary,
    onSurface = AppleLightTextPrimary,
    outline = AppleLightDivider
)

@Composable
fun AntiSpamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
