package dev.cupr.weather.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

/** The app's screen until signing in exists: its name and that it is not set up yet, on the palette's surface. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the screen names the app and says it is not set up yet`() {
        compose.onNodeWithText("Copper Weather").assertExists()
        compose.onNodeWithText("Die App ist noch nicht eingerichtet.").assertExists()
    }

    @Test
    fun `the screen's ground is the palette's surface outside night mode`() {
        assertGround(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the screen's ground is the palette's surface in night mode`() {
        assertGround(dark = true)
    }

    private fun assertGround(dark: Boolean) {
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()

        // The bottom-right corner, away from the text.
        val ground = screen.getPixel(screen.width - 1, screen.height - 1)

        assertEquals(Integer.toHexString(Palette.of(dark).surface), Integer.toHexString(ground))
    }
}
