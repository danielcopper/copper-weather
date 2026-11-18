package dev.cupr.weather.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.palette.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Material's colour scheme from the palette (D7): every colour is one of the palette of its mode, the main roles are
 * the palette's roles of that name, the containers differ from the ground, and text on the accent and on the warning
 * is readable.
 */
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
    fun `the scheme's ground, text, accent and border are the palette's roles of that name`() {
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

    @Test
    fun `the containers, the bright surface and the surface variant differ from the ground in both modes`() {
        for (dark in listOf(false, true)) {
            val scheme = colorScheme(dark)
            val containers =
                mapOf(
                    "surfaceContainerLow" to scheme.surfaceContainerLow,
                    "surfaceContainer" to scheme.surfaceContainer,
                    "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                    "surfaceContainerHighest" to scheme.surfaceContainerHighest,
                    "surfaceBright" to scheme.surfaceBright,
                    "surfaceVariant" to scheme.surfaceVariant,
                )

            for ((role, color) in containers) assertNotEquals("$role, dark=$dark", scheme.background, color)
        }
    }

    @Test
    fun `text on the accent, the error and the error container reaches WCAG's 4,5 to 1 in both modes`() {
        for (dark in listOf(false, true)) {
            val scheme = colorScheme(dark)
            val pairs =
                mapOf(
                    "onPrimary" to (scheme.onPrimary to scheme.primary),
                    "onError" to (scheme.onError to scheme.error),
                    "onErrorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                )

            for ((role, colors) in pairs) {
                val ratio = contrast(colors.first.toArgb(), colors.second.toArgb())
                assertTrue("$role $ratio:1, dark=$dark", ratio >= MINIMUM_CONTRAST)
            }
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
        /** WCAG 2's minimum contrast for normal text (AA). */
        const val MINIMUM_CONTRAST = 4.5

        /** WCAG 2's contrast ratio of two opaque sRGB colours, computed here rather than through Compose. */
        fun contrast(
            a: Int,
            b: Int,
        ): Double {
            val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
            return (light + 0.05) / (dark + 0.05)
        }

        /** WCAG 2's relative luminance. */
        fun luminance(argb: Int): Double {
            fun linear(channel: Int): Double {
                val c = channel / 255.0
                return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) +
                0.0722 * linear(argb and 0xFF)
        }

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
