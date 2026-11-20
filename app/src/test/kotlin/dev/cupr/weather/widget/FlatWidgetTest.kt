package dev.cupr.weather.widget

import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.cupr.weather.R
import dev.cupr.weather.app.MainActivity
import dev.cupr.weather.app.Services
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.tap.TapActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/** The widget one row high, the flat bucket (D11, D35, D36): "now" and the hours beside it, its texts and colours. */
@RunWith(RobolectricTestRunner::class)
class FlatWidgetTest : WidgetTestBase() {
    @Test
    fun `a widget 5 cells wide and 1 high shows now without today's high and low, and the hours that fit beside it`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(
            listOf("7°", "Teilweise bewölkt", "13", "7°", "14", "6°", "15", "5°", "0,4\u00A0mm"),
            widget.shown(),
        )
        assertEquals(3, WidgetHost.hours(widget).size)
        assertEquals(R.drawable.weather_partlycloudy_day, widget.image(R.id.now_icon))
        val density = application.resources.displayMetrics.density
        assertEquals((40 * density).toInt(), widget.findViewById<View>(R.id.now_icon).layoutParams.width)
        // "Now" in its column and the hours side by side on the card.
        assertEquals(LinearLayout.HORIZONTAL, (widget as LinearLayout).orientation)
        assertEquals(widget, widget.findViewById<View>(R.id.now_column).parent)
        assertEquals(widget, widget.findViewById<View>(R.id.hours).parent)
        assertEquals((128 * density).toInt(), widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(TapActivity::class.java.name, widget.tapped().component?.className)
        // A strip across a phone's five columns, a little lower than the guidance's row.
        assertEquals(5, WidgetHost.hours(placed(SizeF(410f, 95f))).size)
    }

    @Test
    fun `a flat widget resized from 2 to 5 cells wide gives now its column again and shows the hours beside it`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, SizeBucket.sizeOf(columns = 2, rows = 1))
        val narrow = WidgetHost.view(application, id)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, narrow.findViewById<View>(R.id.now_column).layoutParams.width)

        val wide = WidgetHost.resize(application, id, SizeBucket.sizeOf(columns = 5, rows = 1))

        val density = application.resources.displayMetrics.density
        assertEquals((128 * density).toInt(), wide.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(3, WidgetHost.hours(wide).size)
    }

    @Test
    fun `a widget 2 cells wide and 1 high shows only now`() {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 1))

        assertEquals(listOf("7°", "Regen in 25 min"), widget.shown())
        assertEquals(emptyList<List<String>>(), WidgetHost.hours(widget))
        // Without hours "now" takes the card's width.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.now_column).layoutParams.width)
        // 3 cells wide on a phone with wider cells, room for two hours beside "now": still only "now" (D11).
        assertEquals(listOf("7°", "Regen in 25 min"), placed(SizeF(272f, 100f)).shown())
    }

    @Test
    fun `every colour a flat widget shows is one of the palette's outside night mode`() {
        assertColours(dark = false, SizeBucket.sizeOf(columns = 5, rows = 1), refuse = false, fewest = FLAT_TEXTS)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `every colour a flat widget shows is one of the palette's in night mode`() {
        assertColours(dark = true, SizeBucket.sizeOf(columns = 5, rows = 1), refuse = false, fewest = FLAT_TEXTS)
    }

    @Test
    fun `on a redraw of a flat widget, Neu anmelden takes the line's place`() {
        signIn()
        written(SampleWeather.full())
        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))
        assertEquals(listOf("7°", "Teilweise bewölkt"), widget.shown().take(2))

        Services.account(application).reject()
        Widgets.update(application)

        assertEquals(listOf("7°", "Neu anmelden"), widget.shown().take(2))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line), widget))
    }

    @Test
    fun `a flat widget shows the plain texts, and Neu anmelden in the line's place`() {
        for (size in listOf(SizeBucket.sizeOf(columns = 5, rows = 1), SizeBucket.sizeOf(columns = 2, rows = 1))) {
            Services.account(application).signOut()
            Services.snapshots(application).clear()
            val signedOut = placed(size)
            assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), signedOut.shown())
            assertEquals(Palette.LIGHT.text, signedOut.text(R.id.widget_plain).currentTextColor)
            assertEquals(MainActivity::class.java.name, signedOut.tapped().component?.className)

            signIn()
            assertEquals(listOf("Noch keine Daten – zum Öffnen tippen"), placed(size).shown())

            written(SampleWeather.full())
            Services.account(application).reject()
            val refused = placed(size)
            assertEquals(listOf("7°", "Neu anmelden"), refused.shown().take(2))
            assertEquals(Palette.LIGHT.accent, refused.text(R.id.widget_notice).currentTextColor)
            assertEquals(MainActivity::class.java.name, refused.tapped().component?.className)

            Services.account(application).signOut()
            Services.snapshots(application).clear()
            Widgets.update(application)
            assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), refused.shown())
        }
    }

    private companion object {
        /** Fewer texts than a flat widget shows in the colour tests. */
        const val FLAT_TEXTS = 5
    }
}
