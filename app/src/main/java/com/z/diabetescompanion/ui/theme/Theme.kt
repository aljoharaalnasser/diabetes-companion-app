package com.z.diabetescompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = NeutralSurfaceLight,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue20,
    secondary = Navy40,
    onSecondary = NeutralSurfaceLight,
    secondaryContainer = Navy90,
    onSecondaryContainer = Navy30,
    tertiary = Red40,
    onTertiary = NeutralSurfaceLight,
    tertiaryContainer = Red90,
    onTertiaryContainer = Red10,
    error = ErrorLight,
    onError = NeutralSurfaceLight,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = NeutralBackgroundLight,
    onBackground = NeutralOnSurfaceLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceVariant = NeutralSurfaceVariantLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    surfaceContainerLowest = NeutralSurfaceLight,
    surfaceContainerLow = NeutralBackgroundLight,
    surfaceContainer = Blue95,
    surfaceContainerHigh = NeutralSurfaceVariantLight,
    surfaceContainerHighest = NeutralSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight,
    inverseSurface = Blue10,
    inverseOnSurface = Blue95,
    inversePrimary = Blue80,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Navy80,
    onSecondary = Blue20,
    secondaryContainer = Navy30,
    onSecondaryContainer = Navy90,
    tertiary = Red80,
    onTertiary = Red10,
    tertiaryContainer = Red30,
    onTertiaryContainer = Red90,
    error = ErrorDark,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = NeutralBackgroundDark,
    onBackground = NeutralOnSurfaceDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    surfaceContainerLowest = NeutralBackgroundDark,
    surfaceContainerLow = NeutralSurfaceDark,
    surfaceContainer = NeutralSurfaceVariantDark,
    surfaceContainerHigh = NeutralSurfaceVariantDark,
    surfaceContainerHighest = NeutralOutlineVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark,
    inverseSurface = NeutralOnSurfaceDark,
    inverseOnSurface = NeutralBackgroundDark,
    inversePrimary = Blue40,
)

/**
 * App theme. Dynamic (Material You) colour is intentionally disabled so the
 * brand palette from the logo stays consistent across devices.
 */
@Composable
fun DiabetesCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val clinicalColors = if (darkTheme) DarkClinicalColors else LightClinicalColors

    CompositionLocalProvider(LocalClinicalColors provides clinicalColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

/** Convenience accessor, e.g. AppTheme.clinical.inRange */
object AppTheme {
    val clinical: ClinicalColors
        @Composable
        @ReadOnlyComposable
        get() = LocalClinicalColors.current
}
