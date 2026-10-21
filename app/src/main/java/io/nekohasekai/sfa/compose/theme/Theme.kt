package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = XeroDarkPrimary,
        onPrimary = XeroDarkOnPrimary,
        primaryContainer = XeroDarkPrimaryContainer,
        onPrimaryContainer = XeroDarkOnPrimaryContainer,
        secondary = XeroDarkSecondary,
        onSecondary = XeroDarkOnSecondary,
        secondaryContainer = XeroDarkSecondaryContainer,
        onSecondaryContainer = XeroDarkOnSecondaryContainer,
        tertiary = XeroDarkTertiary,
        onTertiary = XeroDarkOnTertiary,
        tertiaryContainer = XeroDarkTertiaryContainer,
        onTertiaryContainer = XeroDarkOnTertiaryContainer,
        error = XeroDarkError,
        onError = XeroDarkOnError,
        errorContainer = XeroDarkErrorContainer,
        onErrorContainer = XeroDarkOnErrorContainer,
        background = XeroDarkBackground,
        onBackground = XeroDarkOnSurface,
        surface = XeroDarkSurface,
        onSurface = XeroDarkOnSurface,
        surfaceVariant = XeroDarkSurfaceVariant,
        onSurfaceVariant = XeroDarkOnSurfaceVariant,
        surfaceTint = XeroDarkPrimary,
        outline = XeroDarkOutline,
        outlineVariant = XeroDarkOutlineVariant,
        surfaceContainerLowest = XeroDarkSurfaceContainerLowest,
        surfaceContainerLow = XeroDarkSurfaceContainerLow,
        surfaceContainer = XeroDarkSurfaceContainer,
        surfaceContainerHigh = XeroDarkSurfaceContainerHigh,
        surfaceContainerHighest = XeroDarkSurfaceContainerHighest,
        surfaceBright = XeroDarkSurfaceContainerHighest,
        surfaceDim = XeroDarkBackground,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = XeroLightPrimary,
        onPrimary = XeroLightOnPrimary,
        primaryContainer = XeroLightPrimaryContainer,
        onPrimaryContainer = XeroLightOnPrimaryContainer,
        secondary = XeroLightSecondary,
        onSecondary = XeroLightOnSecondary,
        secondaryContainer = XeroLightSecondaryContainer,
        onSecondaryContainer = XeroLightOnSecondaryContainer,
        tertiary = XeroLightTertiary,
        onTertiary = XeroLightOnTertiary,
        tertiaryContainer = XeroLightTertiaryContainer,
        onTertiaryContainer = XeroLightOnTertiaryContainer,
        error = XeroLightError,
        onError = XeroLightOnError,
        errorContainer = XeroLightErrorContainer,
        onErrorContainer = XeroLightOnErrorContainer,
        background = XeroLightBackground,
        onBackground = XeroLightOnSurface,
        surface = XeroLightSurface,
        onSurface = XeroLightOnSurface,
        surfaceVariant = XeroLightSurfaceVariant,
        onSurfaceVariant = XeroLightOnSurfaceVariant,
        surfaceTint = XeroLightPrimary,
        outline = XeroLightOutline,
        outlineVariant = XeroLightOutlineVariant,
        surfaceContainerLowest = XeroLightSurfaceContainerLowest,
        surfaceContainerLow = XeroLightSurfaceContainerLow,
        surfaceContainer = XeroLightSurfaceContainer,
        surfaceContainerHigh = XeroLightSurfaceContainerHigh,
        surfaceContainerHighest = XeroLightSurfaceContainerHighest,
        surfaceBright = XeroLightSurfaceContainerLowest,
        surfaceDim = XeroLightSurfaceContainerHighest,
    )

/** Protection-state colours, which sit outside the Material roles. */
data class ProtectionColors(val protected: Color, val unprotected: Color)

val LocalProtectionColors =
    staticCompositionLocalOf { ProtectionColors(protected = ProtectedDark, unprotected = UnprotectedDark) }

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the XeroBox brand palette always shows. Wallpaper-based
    // Material You colours (Android 12+) can be turned back on by passing true.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val protectionColors =
        if (darkTheme) {
            ProtectionColors(protected = ProtectedDark, unprotected = UnprotectedDark)
        } else {
            ProtectionColors(protected = ProtectedLight, unprotected = UnprotectedLight)
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalProtectionColors provides protectionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
