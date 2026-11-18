package dev.cupr.weather.app

import android.content.res.TypedArray
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Until Compose draws, the app's window and its splash screen show the palette's surface (D7). */
@RunWith(RobolectricTestRunner::class)
class WindowTest {
    @Test
    fun `the window and the splash screen are the palette's surface outside night mode`() {
        assertWindow(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the window and the splash screen are the palette's surface in night mode`() {
        assertWindow(dark = true)
    }

    private fun assertWindow(dark: Boolean) {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val surface = Integer.toHexString(Palette.of(dark).surface)

        val attributes =
            activity.theme.obtainStyledAttributes(
                intArrayOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground),
            )

        attributes.use {
            assertEquals("window", surface, Integer.toHexString(it.colorAt(0)))
            assertEquals("splash screen", surface, Integer.toHexString(it.colorAt(1)))
        }
    }

    private fun TypedArray.colorAt(index: Int): Int = getColor(index, 0)
}
