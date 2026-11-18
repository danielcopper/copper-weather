package dev.cupr.weather.app

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import dev.cupr.weather.palette.Palette

// The palette turned into the platform's colours (D7): Material's colour scheme and the system bars' style. The two
// entry points below read the mode from the configuration's isNightModeActive, the only place the app's code reads
// it; the resources follow night mode through values-night.

/** The app's screens in the palette's colours, light or dark with the system. */
@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(LocalConfiguration.current.isNightModeActive), content = content)
}

/** Edge to edge, with the system bars' scrims the screen's ground, so the system draws no colour of its own there. */
fun ComponentActivity.enableEdgeToEdgeFromPalette() {
    val dark = resources.configuration.isNightModeActive
    val ground = Palette.of(dark).surfaceBackground
    val bars = if (dark) SystemBarStyle.dark(ground) else SystemBarStyle.light(ground, ground)
    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
}

/**
 * Material's colour scheme, every role from the palette. Why some roles take what they take:
 * - the low and middle containers take the card colour, and the high ones, the bright surface and the surface variant
 *   the raised colour where it differs from the ground, else the card colour, so a default card or filled field
 *   stands out from the ground as the dashboard's cards do; the lowest container and the dim surface stay the ground
 *   on purpose, as the bottom of the scale;
 * - the surface tint is the ground, so a Surface's tonal elevation mixes in no other colour;
 * - error is the warning until the palette has an error role, and the text on it is whichever text colour reaches
 *   the higher contrast;
 * - the scrim behind dialogs is the mode's darkest colour.
 */
fun colorScheme(dark: Boolean): ColorScheme {
    val palette = Palette.of(dark)
    val ground = Color(palette.surfaceBackground)
    val card = Color(palette.surfaceCard)
    val raised = if (palette.surfaceRaised != palette.surfaceBackground) Color(palette.surfaceRaised) else card
    val border = Color(palette.surfaceBorder)
    val text = Color(palette.textPrimary)
    val warning = Color(palette.warningSolid)
    val onWarning = listOf(text, Color(palette.onAccentSolid)).maxBy { contrast(it, warning) }
    return (if (dark) darkColorScheme() else lightColorScheme())
        .withAccent(Color(palette.accentSolid), Color(palette.onAccentSolid))
        .copy(
            background = ground,
            onBackground = text,
            surface = ground,
            onSurface = text,
            surfaceVariant = raised,
            onSurfaceVariant = Color(palette.textSecondary),
            surfaceTint = ground,
            inverseSurface = text,
            inverseOnSurface = card,
            error = warning,
            onError = onWarning,
            errorContainer = warning,
            onErrorContainer = onWarning,
            outline = border,
            outlineVariant = border,
            scrim = if (dark) ground else text,
            surfaceBright = raised,
            surfaceContainer = card,
            surfaceContainerHigh = raised,
            surfaceContainerHighest = raised,
            surfaceContainerLow = card,
            surfaceContainerLowest = ground,
            surfaceDim = ground,
        )
}

/** WCAG's contrast ratio of two colours. */
private fun contrast(
    a: Color,
    b: Color,
): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
}

private const val CONTRAST_OFFSET = 0.05f

private fun ColorScheme.withAccent(
    accent: Color,
    onAccent: Color,
): ColorScheme =
    copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent,
        onSecondaryContainer = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent,
        onTertiaryContainer = onAccent,
        primaryFixed = accent,
        primaryFixedDim = accent,
        onPrimaryFixed = onAccent,
        onPrimaryFixedVariant = onAccent,
        secondaryFixed = accent,
        secondaryFixedDim = accent,
        onSecondaryFixed = onAccent,
        onSecondaryFixedVariant = onAccent,
        tertiaryFixed = accent,
        tertiaryFixedDim = accent,
        onTertiaryFixed = onAccent,
        onTertiaryFixedVariant = onAccent,
    )
