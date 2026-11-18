package dev.cupr.weather.app

import android.content.res.TypedArray
import android.os.Build
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.insets.ColorProtection
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Until Compose draws, the app's window and its splash screen show the palette's ground, and the scrims behind the
 * system bars are that ground too (D7).
 */
@RunWith(RobolectricTestRunner::class)
class WindowTest {
    @Test
    fun `the window and the splash screen are the palette's ground outside night mode`() {
        assertWindow(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the window and the splash screen are the palette's ground in night mode`() {
        assertWindow(dark = true)
    }

    @Test
    fun `the system bars' scrims are the palette's ground outside night mode`() {
        assertBars(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the system bars' scrims are the palette's ground in night mode`() {
        assertBars(dark = true)
    }

    /**
     * From Android 15 on, edge-to-edge draws the bars' scrims itself, as one ColorProtection per side, held in the
     * tag of a layout it adds to the window's decor view.
     */
    private fun assertBars(dark: Boolean) {
        val api = Build.VERSION.SDK_INT
        assertTrue("Robolectric runs API $api", api >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val ground = Integer.toHexString(Palette.of(dark).surfaceBackground)

        val scrims =
            (activity.window.decorView as ViewGroup)
                .children
                .mapNotNull { it.tag as? List<*> }
                .flatten()
                .filterIsInstance<ColorProtection>()
                .toList()

        assertEquals("one scrim per side", SIDES, scrims.size)
        for (scrim in scrims) assertEquals("side ${scrim.side}", ground, Integer.toHexString(scrim.color))
    }

    private fun assertWindow(dark: Boolean) {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val ground = Integer.toHexString(Palette.of(dark).surfaceBackground)

        val attributes =
            activity.theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground),
            )

        attributes.use {
            assertEquals("window", ground, Integer.toHexString(it.colorAt(0)))
            assertEquals("splash screen", ground, Integer.toHexString(it.colorAt(1)))
        }
    }

    private fun TypedArray.colorAt(index: Int): Int = getColor(index, 0)

    private companion object {
        const val SIDES = 4
    }
}
