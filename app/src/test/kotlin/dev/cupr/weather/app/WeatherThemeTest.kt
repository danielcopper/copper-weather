package dev.cupr.weather.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.palette.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every colour of Material's colour scheme comes from the palette of its mode (D7). */
class WeatherThemeTest {
    @Test
    fun `every colour of the scheme is a palette colour outside night mode`() {
        assertEveryColorFromPalette(dark = false)
    }

    @Test
    fun `every colour of the scheme is a palette colour in night mode`() {
        assertEveryColorFromPalette(dark = true)
    }

    @Test
    fun `the screen's ground and text come from the palette's ground and text roles`() {
        for (dark in listOf(false, true)) {
            val palette = Palette.of(dark)

            val scheme = colorScheme(dark)

            assertEquals("background", palette.surfaceBackground, scheme.background.toArgb())
            assertEquals("surface", palette.surfaceBackground, scheme.surface.toArgb())
            assertEquals("onBackground", palette.textPrimary, scheme.onBackground.toArgb())
            assertEquals("onSurface", palette.textPrimary, scheme.onSurface.toArgb())
            assertEquals("onSurfaceVariant", palette.textSecondary, scheme.onSurfaceVariant.toArgb())
            assertEquals("primary", palette.accentSolid, scheme.primary.toArgb())
            assertEquals("outline", palette.surfaceBorder, scheme.outline.toArgb())
        }
    }

    private fun assertEveryColorFromPalette(dark: Boolean) {
        val palette =
            Palette
                .of(dark)
                .colors()
                .values
                .toSet()

        val scheme = colorsOf(colorScheme(dark))

        assertTrue("No colours read from the scheme", scheme.size > MATERIAL_BASELINE_ROLES)
        val foreign = scheme.filterValues { it !in palette }.mapValues { Integer.toHexString(it.value) }
        assertEquals("Colours not in the palette", emptyMap<String, String>(), foreign)
    }

    private companion object {
        /** Material 3 has had more colour roles than this since its first release. */
        const val MATERIAL_BASELINE_ROLES = 25

        /**
         * Every colour role of [scheme] by name, read through its getters: Compose's Color is an inline class, so a
         * getter returns its bits as a long and carries a mangled name ("getPrimary-0d7_KjU").
         */
        fun colorsOf(scheme: ColorScheme): Map<String, Int> =
            ColorScheme::class.java.methods
                .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
                .filter { it.name.startsWith("get") }
                .associate { getter ->
                    val name = getter.name.removePrefix("get").substringBefore('-')
                    name to Color((getter.invoke(scheme) as Long).toULong()).toArgb()
                }
    }
}
