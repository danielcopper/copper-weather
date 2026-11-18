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
    fun `a card or filled field stands out from the ground in both modes`() {
        for (dark in listOf(false, true)) {
            val scheme = colorScheme(dark)

            assertNotEquals("highest container, dark=$dark", scheme.background, scheme.surfaceContainerHighest)
            assertNotEquals("container, dark=$dark", scheme.background, scheme.surfaceContainer)
        }
    }

    @Test
    fun `text on the accent and on the error reaches WCAG's 4,5 to 1 in both modes`() {
        for (dark in listOf(false, true)) {
            val scheme = colorScheme(dark)

            val onPrimary = contrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb())
            val onError = contrast(scheme.onError.toArgb(), scheme.error.toArgb())

            assertTrue("onPrimary $onPrimary:1, dark=$dark", onPrimary >= MINIMUM_CONTRAST)
            assertTrue("onError $onError:1, dark=$dark", onError >= MINIMUM_CONTRAST)
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
