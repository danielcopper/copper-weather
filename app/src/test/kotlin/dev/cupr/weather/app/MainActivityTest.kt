package dev.cupr.weather.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app's screen: its name and, signed out, the sign-in, on the palette's `bg`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the screen names the app and, signed out, offers the sign-in with Home Assistant`() {
        compose.onNodeWithText("Copper Weather").assertExists()
        // The status is read off the main thread.
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Mit Home Assistant anmelden").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Home-Assistant-Adresse").assertExists()
    }

    @Test
    fun `the screen's background is the palette's bg outside night mode`() {
        assertBg(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the screen's background is the palette's bg in night mode`() {
        assertBg(dark = true)
    }

    private fun assertBg(dark: Boolean) {
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()

        // The bottom-right corner, away from the text.
        val corner = screen.getPixel(screen.width - 1, screen.height - 1)

        assertEquals(Integer.toHexString(Palette.of(dark).bg), Integer.toHexString(corner))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
