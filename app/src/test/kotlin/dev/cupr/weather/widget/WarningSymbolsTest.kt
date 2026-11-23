package dev.cupr.weather.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The warnings' symbols (D48): the drawables in the repo are what `scripts/warning_symbols.py` writes from the Material
 * Symbols in `icons/symbols`, and each kind finds its own, the composites wider than the others. The script's own
 * tests run with the weather icons' (WeatherIconsTest), as one discovery of `scripts`.
 */
@RunWith(RobolectricTestRunner::class)
class WarningSymbolsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the warning symbols are what their script writes from the Material Symbols`() {
        val (code, output) = Scripts.run("python3", "-I", "-B", "scripts/warning_symbols.py", "--check")

        assertEquals(output, 0, code)
    }

    @Test
    fun `each warning kind has its own symbol 24 dp high, a composite wider and a little higher`() {
        val symbols =
            R.drawable::class.java.fields
                .filter { it.name.startsWith("symbol_") }
                .map { it.getInt(null) }

        assertEquals(symbols.toSet(), WarningKind.entries.map { it.symbol }.toSet())
        assertEquals(WarningKind.entries.size, symbols.size)
        val density = application.resources.displayMetrics.density
        val composites = setOf(WarningKind.FROST, WarningKind.HEAT, WarningKind.ICE)
        for (kind in WarningKind.entries) {
            val drawable = checkNotNull(application.getDrawable(kind.symbol)) { kind.name }
            val width = drawable.intrinsicWidth / density
            val height = drawable.intrinsicHeight / density
            if (kind in composites) {
                // The second symbol reaches up to 0.7 dp above the main one's top (D48).
                assertTrue(kind.name + " $height", height >= SYMBOL_DP && height <= SYMBOL_DP + 1)
                assertTrue(kind.name + " $width", width > SYMBOL_DP)
            } else {
                assertEquals(kind.name, SYMBOL_DP, height, 0.5f)
                assertEquals(kind.name, SYMBOL_DP, width, 0.5f)
            }
        }
    }

    private companion object {
        /** A Material Symbol's size (D48). */
        const val SYMBOL_DP = 24f
    }
}
