package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class PaletteTest {
    @Test
    fun `night mode takes the dark palette, else the light one`() {
        assertSame(Palette.DARK, Palette.of(dark = true))
        assertSame(Palette.LIGHT, Palette.of(dark = false))
    }

    @Test
    fun `every colour of both palettes is opaque`() {
        for ((mode, palette) in listOf("light" to Palette.LIGHT, "dark" to Palette.DARK)) {
            val colors = colorsOf(palette)
            assertTrue("$mode has no colours", colors.isNotEmpty())
            for ((role, color) in colors) {
                assertEquals("$mode $role is ${Integer.toHexString(color)}", OPAQUE, color ushr ALPHA_SHIFT)
            }
        }
    }

    private companion object {
        const val OPAQUE = 0xFF
        const val ALPHA_SHIFT = 24

        /** Every role's colour, by its field's name; Java reflection, as the app has no kotlin-reflect. */
        fun colorsOf(palette: Palette): Map<String, Int> =
            Palette::class.java.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
                .associate { field ->
                    field.isAccessible = true
                    field.name to field.getInt(palette)
                }
    }
}
