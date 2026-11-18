package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test
    fun `night mode takes the dark palette, else the light one`() {
        assertSame(Palette.DARK, Palette.of(dark = true))
        assertSame(Palette.LIGHT, Palette.of(dark = false))
    }

    @Test
    fun `every colour of both palettes is opaque`() {
        for ((mode, palette) in listOf("light" to Palette.LIGHT, "dark" to Palette.DARK)) {
            val colors = palette.colors()
            assertTrue("$mode has no colours", colors.isNotEmpty())
            for ((role, color) in colors) {
                assertEquals("$mode $role is ${Integer.toHexString(color)}", OPAQUE, color ushr ALPHA_SHIFT)
            }
        }
    }

    private companion object {
        const val OPAQUE = 0xFF
        const val ALPHA_SHIFT = 24
    }
}
