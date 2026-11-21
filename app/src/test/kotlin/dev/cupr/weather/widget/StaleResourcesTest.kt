package dev.cupr.weather.widget

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import dev.cupr.weather.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget drawn by a launcher that kept an earlier build's resources (D42): there each dimension's number names the
 * dimension after it in this build, as when this build added one before it in name
 * order (the numbers follow the names). The sizes the app sets still hold.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xxhdpi")
class StaleResourcesTest : WidgetTestBase() {
    @Test
    fun `a launcher with an earlier build's resources still draws the flat now column 130 dp wide`() {
        signIn()
        written(SampleWeather.full())
        val size = SizeF(388f, 95f)

        val widget = drawnByStaleLauncher(size)

        assertEquals(dp(130), widget.findViewById<View>(R.id.now_column).width)
        assertEquals(dp(71), widget.findViewById<View>(R.id.now_text).width)
    }

    @Test
    fun `a launcher with an earlier build's resources still draws the small now row and its image 40 dp`() {
        signIn()
        written(SampleWeather.full())
        val size = SizeBucket.sizeOf(columns = 2, rows = 2)

        val widget = drawnByStaleLauncher(size)

        assertEquals(dp(40), widget.findViewById<View>(R.id.now).height)
        assertEquals(dp(40), widget.findViewById<View>(R.id.now_icon).width)
        assertEquals(dp(40), widget.findViewById<View>(R.id.now_icon).height)
    }

    /** The views for [size] applied and laid out as a launcher with [StaleResources] does. */
    private fun drawnByStaleLauncher(size: SizeF): View {
        val views = WidgetViews.of(application, WidgetContent.read(application, WeatherWidgetProvider.clock()), size)
        val launcher =
            object : ContextWrapper(application) {
                private val stale = StaleResources(application)

                override fun getResources(): Resources = stale
            }
        return laidOut(views.apply(launcher, FrameLayout(launcher)), size)
    }

    /**
     * This build's resources, but each of the app's dimensions looked up by number gives the one numbered after it. It
     * takes Resources' deprecated constructor, its only public one: what it stands in for is a launcher's stale table.
     */
    @Suppress("DEPRECATION")
    private class StaleResources(
        context: Context,
    ) : Resources(context.resources.assets, context.resources.displayMetrics, context.resources.configuration) {
        private fun earlier(id: Int): Int =
            if (id ushr APP_SHIFT == APP &&
                getResourceTypeName(id) == "dimen"
            ) {
                id + 1
            } else {
                id
            }

        override fun getDimension(id: Int): Float = super.getDimension(earlier(id))

        override fun getDimensionPixelSize(id: Int): Int = super.getDimensionPixelSize(earlier(id))

        override fun getDimensionPixelOffset(id: Int): Int = super.getDimensionPixelOffset(earlier(id))

        private companion object {
            /** An app's own resources are numbered 0x7f…, Android's 0x01…. */
            const val APP = 0x7f
            const val APP_SHIFT = 24
        }
    }
}
