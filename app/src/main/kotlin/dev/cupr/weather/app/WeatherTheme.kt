package dev.cupr.weather.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.palette.PaletteToken

/**
 * The app's screens in the palette's colours (D7), light or dark with the system: its surface as the ground, its
 * text colours for text. Material's roles the palette has no colour for keep Material's own.
 */
@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(isSystemInDarkTheme()), content = content)
}

/** Material's colour scheme for night mode when [dark], else outside it, from the palette. */
fun colorScheme(dark: Boolean): ColorScheme {
    fun token(token: PaletteToken) = Color(Palette.color(token, dark))
    val surface = token(PaletteToken.Surface)
    val text = token(PaletteToken.Text)
    val muted = token(PaletteToken.TextMuted)
    return if (dark) {
        darkColorScheme(
            background = surface,
            onBackground = text,
            surface = surface,
            onSurface = text,
            onSurfaceVariant = muted,
        )
    } else {
        lightColorScheme(
            background = surface,
            onBackground = text,
            surface = surface,
            onSurface = text,
            onSurfaceVariant = muted,
        )
    }
}
