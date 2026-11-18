package dev.cupr.weather.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.cupr.weather.palette.Palette

/** The app's screens in the palette's colours (D7), light or dark with the system. */
@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(isSystemInDarkTheme()), content = content)
}

/**
 * Material's colour scheme for night mode when [dark], else outside it, every role from the palette (D7), until the
 * shared colour scheme brings a Material 3 scheme of its own:
 * - primary, secondary and tertiary, their containers and fixed variants: the accent, with the text on it;
 * - error and its container: the warning, with the accent's text colour on it, until the palette has an error role;
 * - background and surface: the ground; the containers from the ground (lowest, dim) over the card (low, middle) to
 *   what is raised on it (high, highest, bright, variant);
 * - the surface tint is the ground, so a Surface's tonal elevation mixes in no other colour;
 * - outlines: the border; the inverse surface: the text colour with the card's colour on it;
 * - the scrim behind dialogs: the mode's darkest colour, the ground in night mode and the text colour outside it.
 */
fun colorScheme(dark: Boolean): ColorScheme {
    val palette = Palette.of(dark)
    val ground = Color(palette.surfaceBackground)
    val card = Color(palette.surfaceCard)
    val raised = Color(palette.surfaceRaised)
    val border = Color(palette.surfaceBorder)
    val text = Color(palette.textPrimary)
    val onAccent = Color(palette.onAccentSolid)
    val warning = Color(palette.warningSolid)
    return (if (dark) darkColorScheme() else lightColorScheme())
        .withAccent(Color(palette.accentSolid), onAccent)
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
            onError = onAccent,
            errorContainer = warning,
            onErrorContainer = onAccent,
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

/** Primary, secondary and tertiary, their containers and fixed variants: [accent], with [onAccent] on it. */
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
