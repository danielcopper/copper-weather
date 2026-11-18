package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test
    fun `light and dark define the same tokens, every one of them`() {
        val all = PaletteToken.entries.toSet()

        assertEquals("light", all, Palette.light.keys)
        assertEquals("dark", all, Palette.dark.keys)
    }

    @Test
    fun `a token's colour is the mode's`() {
        assertEquals(0xFFFFFFFF.toInt(), Palette.color(PaletteToken.Surface, dark = false))
        assertEquals(0xFF1A1F25.toInt(), Palette.color(PaletteToken.Surface, dark = true))
    }
}
