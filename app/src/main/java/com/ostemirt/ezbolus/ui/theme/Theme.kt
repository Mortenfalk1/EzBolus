package com.ostemirt.ezbolus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Dynamic (wallpaper) color is deliberately OFF — the design specifies an
 * exact hunter-green/vanilla palette that must render the same on every
 * device. If we ever want per-device tinting, add a Settings toggle.
 */

private val LightScheme = lightColorScheme(
    primary = L_Primary,
    onPrimary = L_OnPrimary,
    primaryContainer = L_PrimaryContainer,
    onPrimaryContainer = L_OnPrimaryContainer,

    secondary = L_KindGlucose,
    onSecondary = L_OnPrimary,
    secondaryContainer = L_PrimaryContainer,
    onSecondaryContainer = L_OnPrimaryContainer,

    tertiary = L_KindGlucose,
    onTertiary = L_OnPrimary,

    background = L_Bg,
    onBackground = L_OnSurface,

    surface = L_Surface,
    onSurface = L_OnSurface,
    surfaceVariant = L_SurfaceContainer,
    onSurfaceVariant = L_OnSurfaceVariant,
    surfaceContainer = L_SurfaceContainer,
    surfaceContainerHigh = L_SurfaceContainerHigh,

    outline = L_Outline,
    outlineVariant = L_OutlineVariant,

    error = L_Error,
    onError = L_OnError,
    errorContainer = L_ErrorContainer,
    onErrorContainer = L_OnErrorContainer,

    inverseSurface = L_InverseSurface,
    inverseOnSurface = L_InverseOnSurface,
    inversePrimary = L_InversePrimary,
)

private val DarkScheme = darkColorScheme(
    primary = D_Primary,
    onPrimary = D_OnPrimary,
    primaryContainer = D_PrimaryContainer,
    onPrimaryContainer = D_OnPrimaryContainer,

    secondary = D_KindGlucose,
    onSecondary = D_OnPrimary,
    secondaryContainer = D_PrimaryContainer,
    onSecondaryContainer = D_OnPrimaryContainer,

    tertiary = D_KindGlucose,
    onTertiary = D_OnPrimary,

    background = D_Bg,
    onBackground = D_OnSurface,

    surface = D_Surface,
    onSurface = D_OnSurface,
    surfaceVariant = D_SurfaceContainer,
    onSurfaceVariant = D_OnSurfaceVariant,
    surfaceContainer = D_SurfaceContainer,
    surfaceContainerHigh = D_SurfaceContainerHigh,

    outline = D_Outline,
    outlineVariant = D_OutlineVariant,

    error = D_Error,
    onError = D_OnError,
    errorContainer = D_ErrorContainer,
    onErrorContainer = D_OnErrorContainer,

    inverseSurface = D_InverseSurface,
    inverseOnSurface = D_InverseOnSurface,
    inversePrimary = D_InversePrimary,
)

/** Colors that don't have a Material3 slot — kind indicators for the IOB
 *  ring, the history dots, etc. Consumed via [LocalKindColors]. */
data class KindColors(
    val insulin: Color,
    val glucose: Color,
    val carbs: Color,
)

private val LightKindColors = KindColors(L_KindInsulin, L_KindGlucose, L_KindCarbs)
private val DarkKindColors = KindColors(D_KindInsulin, D_KindGlucose, D_KindCarbs)

val LocalKindColors = staticCompositionLocalOf { LightKindColors }

@Composable
fun EzBolusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val kinds = if (darkTheme) DarkKindColors else LightKindColors
    CompositionLocalProvider(LocalKindColors provides kinds) {
        MaterialTheme(
            colorScheme = scheme,
            typography = EzBolusTypography,
            content = content,
        )
    }
}
