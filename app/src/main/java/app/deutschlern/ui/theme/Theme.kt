package app.deutschlern.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = LightError,
    onError = LightSurface,
    // Without these Material falls back to its baseline lilac in dialogs, sheets and chips
    primaryContainer = LightAccentTint,
    onPrimaryContainer = LightOnAccentTint,
    secondary = LightAccent,
    onSecondary = LightSurface,
    secondaryContainer = LightAccentTint,
    onSecondaryContainer = LightOnAccentTint,
    tertiary = LightOnWarningTint,
    onTertiary = LightSurface,
    tertiaryContainer = LightWarningTint,
    onTertiaryContainer = LightOnWarningTint,
    errorContainer = LightErrorTint,
    onErrorContainer = LightOnErrorTint,
    surfaceTint = LightSurface,
    surfaceBright = LightSurface,
    surfaceDim = LightMutedSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightMutedSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = DarkError,
    onError = DarkBackground,
    primaryContainer = DarkAccentTint,
    onPrimaryContainer = DarkOnAccentTint,
    secondary = DarkAccent,
    onSecondary = DarkBackground,
    secondaryContainer = DarkAccentTint,
    onSecondaryContainer = DarkOnAccentTint,
    tertiary = DarkOnWarningTint,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkWarningTint,
    onTertiaryContainer = DarkOnWarningTint,
    errorContainer = DarkErrorTint,
    onErrorContainer = DarkOnErrorTint,
    surfaceTint = DarkSurface,
    surfaceBright = DarkMutedSurface,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkMutedSurface,
    surfaceContainerHighest = DarkMutedSurface
)

@Composable
fun DeutschLernTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
