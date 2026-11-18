package dev.cupr.weather

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.palette.PaletteToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the launcher shows of the app: its name and its icon, a sun behind a cloud in the palette's sun on its
 * surface (D7). The icon's layers are drawn at one pixel per unit of the adaptive icon's 108-unit viewport and
 * probed inside its shapes: the sun, a ray, the cloud, and the gap between sun and cloud.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIdentityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val icon get() = application.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    @Test
    fun `the app is Copper Weather`() {
        assertEquals("Copper Weather", application.applicationInfo.loadLabel(application.packageManager).toString())
    }

    @Test
    fun `the launcher icon is the palette's sun on its surface outside night mode`() {
        assertIcon(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the launcher icon is the palette's sun on its surface in night mode`() {
        assertIcon(dark = true)
    }

    @Test
    fun `the themed icon is the sun, its rays and the cloud, with the gap between them left out`() {
        val mono = drawn(checkNotNull(icon.monochrome) { "The launcher icon has no monochrome layer" })

        assertEquals("sun", OPAQUE, Color.alpha(mono.getPixel(SUN_X, SUN_Y)))
        assertEquals("ray", OPAQUE, Color.alpha(mono.getPixel(RAY_X, RAY_Y)))
        assertEquals("cloud", OPAQUE, Color.alpha(mono.getPixel(CLOUD_X, CLOUD_Y)))
        assertEquals("gap", 0, Color.alpha(mono.getPixel(GAP_X, GAP_Y)))
    }

    private fun assertIcon(dark: Boolean) {
        val surface = Palette.color(PaletteToken.Surface, dark)
        val sun = Palette.color(PaletteToken.Sun, dark)
        val icon = icon

        assertEquals("background", surface, drawn(icon.background).getPixel(GAP_X, GAP_Y))
        val front = drawn(icon.foreground)
        assertEquals("sun", sun, front.getPixel(SUN_X, SUN_Y))
        assertEquals("ray", sun, front.getPixel(RAY_X, RAY_Y))
        assertEquals("cloud", sun, front.getPixel(CLOUD_X, CLOUD_Y))
        assertEquals("gap", 0, Color.alpha(front.getPixel(GAP_X, GAP_Y)))
    }

    private fun drawn(layer: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(VIEWPORT, VIEWPORT, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, VIEWPORT, VIEWPORT)
        layer.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val VIEWPORT = 108
        const val OPAQUE = 255

        // Pixels inside the shapes of the icon's drawables: the sun's disc is centred at (59, 49), the cloud's
        // large bump at (53, 59), and the sun stops 3 units short of that bump.
        const val SUN_X = 59
        const val SUN_Y = 42
        const val RAY_X = 58
        const val RAY_Y = 31
        const val CLOUD_X = 53
        const val CLOUD_Y = 68
        const val GAP_X = 59
        const val GAP_Y = 48
    }
}
