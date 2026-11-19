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
 * Until Compose draws, the app's window and its splash screen show the palette's ground, and the system bars' scrims
 * are that ground too, on Android 15 and later and before it (D7).
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

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `before Android 15 the system bars are the palette's ground outside night mode`() {
        assertBarColors(dark = false)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], qualifiers = "+night")
    fun `before Android 15 the system bars are the palette's ground in night mode`() {
        assertBarColors(dark = true)
    }

    /** Before Android 15, edge-to-edge sets the scrims as the window's status and navigation bar colours. */
    @Suppress("DEPRECATION") // The bar colours are what Android 14 and older draw; this case runs on Android 14.
    private fun assertBarColors(dark: Boolean) {
        val window = started().window
        val ground = groundHex(dark)

        assertEquals("status bar", ground, Integer.toHexString(window.statusBarColor))
        assertEquals("navigation bar", ground, Integer.toHexString(window.navigationBarColor))
    }

    /**
     * From Android 15 on, edge-to-edge draws the bars' scrims itself, as one ColorProtection per side, held in the
     * tag of a layout it adds to the window's decor view.
     */
    private fun assertBars(dark: Boolean) {
        val api = Build.VERSION.SDK_INT
        assertTrue("Robolectric runs API $api", api >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        val activity = started()
        val ground = groundHex(dark)

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
        val activity = started()
        val ground = groundHex(dark)

        val attributes =
            activity.theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground),
            )

        attributes.use {
            assertEquals("window", ground, Integer.toHexString(it.colorAt(0)))
            assertEquals("splash screen", ground, Integer.toHexString(it.colorAt(1)))
        }
    }

    private fun started(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun groundHex(dark: Boolean): String = Integer.toHexString(Palette.of(dark).bg)

    private fun TypedArray.colorAt(index: Int): Int = getColor(index, 0)

    private companion object {
        const val SIDES = 4
    }
}
