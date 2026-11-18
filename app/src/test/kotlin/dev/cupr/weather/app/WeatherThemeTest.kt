package dev.cupr.weather.app

import androidx.compose.ui.graphics.toArgb
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Test

/** Material's colour roles the app's screens draw with come from the palette, in both modes. */
class WeatherThemeTest {
    @Test
    fun `the colour scheme takes the palette's surface and text colours outside night mode`() {
        assertScheme(dark = false)
    }

    @Test
    fun `the colour scheme takes the palette's surface and text colours in night mode`() {
        assertScheme(dark = true)
    }

    private fun assertScheme(dark: Boolean) {
        val palette = Palette.of(dark)

        val scheme = colorScheme(dark)

        assertEquals("background", palette.surface, scheme.background.toArgb())
        assertEquals("onBackground", palette.text, scheme.onBackground.toArgb())
        assertEquals("surface", palette.surface, scheme.surface.toArgb())
        assertEquals("onSurface", palette.text, scheme.onSurface.toArgb())
        assertEquals("onSurfaceVariant", palette.textMuted, scheme.onSurfaceVariant.toArgb())
    }
}
