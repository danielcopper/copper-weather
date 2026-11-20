package dev.cupr.weather.widget

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.cupr.weather.R
import dev.cupr.weather.app.MainActivity
import dev.cupr.weather.app.Services
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.tap.TapActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/** The widget one row high, the flat bucket (D11, D35, D36, D41): "now" and the days beside it, texts and colours. */
@RunWith(RobolectricTestRunner::class)
class FlatWidgetTest : WidgetTestBase() {
    private val density get() = application.resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).toInt()

    @Test
    fun `a widget 5 cells wide and 1 high shows now without today's high and low, and the days that fit beside it`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(listOf("7°", "Teilweise bewölkt"), widget.shown().take(2))
        assertEquals(
            listOf(
                listOf("Sa", "10°", "1°", "sonnig"),
                listOf("So", "11°", "2°", "2,5\u00A0mm"),
                listOf("Mo", "12°", "3°", "sonnig"),
                listOf("Di", "13°", "4°", "2,5\u00A0mm"),
            ),
            WidgetHost.days(widget),
        )
        assertEquals(emptyList<List<String>>(), WidgetHost.hours(widget))
        val days = widget.findViewById<ViewGroup>(R.id.days)
        assertEquals(R.drawable.weather_sunny_day, days.getChildAt(0).image(R.id.flat_day_icon))
        val high = days.getChildAt(0).text(R.id.flat_day_high)
        assertEquals(Typeface.BOLD, high.typeface?.style)
        assertEquals(R.drawable.weather_partlycloudy_day, widget.image(R.id.now_icon))
        assertEquals(dp(40), widget.findViewById<View>(R.id.now_icon).layoutParams.width)
        // "Now" in its column, the divider and the days side by side on the card.
        assertEquals(LinearLayout.HORIZONTAL, (widget as LinearLayout).orientation)
        assertEquals(
            listOf(R.id.now_column, R.id.divider, R.id.days),
            (0 until widget.childCount)
                .map {
                    widget.getChildAt(
                        it,
                    )
                }.filter { WidgetHost.visible(it, widget) }
                .map { it.id },
        )
        assertEquals(dp(104), widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(TapActivity::class.java.name, widget.tapped().component?.className)
    }

    @Test
    fun `a flat strip shows four days at 349 and 388 dp and five at 410`() {
        signIn()
        written(SampleWeather.full())

        assertEquals(
            listOf(4, 4, 5),
            listOf(SizeF(349f, 102f), SizeF(388f, 95f), SizeF(410f, 95f)).map { WidgetHost.days(placed(it)).size },
        )
    }

    @Test
    fun `a flat widget resized from 2 to 5 cells wide gives now its column again and shows the days beside it`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, SizeBucket.sizeOf(columns = 2, rows = 1))
        val narrow = WidgetHost.view(application, id)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, narrow.findViewById<View>(R.id.now_column).layoutParams.width)

        val wide = WidgetHost.resize(application, id, SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(dp(104), wide.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(4, WidgetHost.days(wide).size)
    }

    @Test
    fun `a widget 2 cells wide and 1 high shows only now`() {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 1))

        assertEquals(listOf("7°", "Regen in 25 min"), widget.shown())
        assertEquals(emptyList<List<String>>(), WidgetHost.days(widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.divider), widget))
        // Without days "now" takes the card's width.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.now_column).layoutParams.width)
        // 3 cells wide on a phone with wider cells, room for two days beside "now": still only "now" (D11).
        assertEquals(listOf("7°", "Regen in 25 min"), placed(SizeF(272f, 100f)).shown())
    }

    @Test
    fun `the line below now takes up to two lines, shrinking from 14 to 11 sp, in the flat and the small bucket`() {
        signIn()
        written(SampleWeather.data().replace("partlycloudy", "lightning-rainy"))
        val metrics = application.resources.displayMetrics
        val sp = { value: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics).toInt() }

        for ((size, height) in listOf(
            SizeBucket.sizeOf(columns = 5, rows = 1) to 36,
            SizeBucket.sizeOf(columns = 2, rows = 2) to 36,
            SizeBucket.sizeOf(columns = 4, rows = 2) to 20,
        )) {
            val line = placed(size).text(R.id.now_line)
            assertEquals("$size", "Gewitter, regnerisch", line.text.toString())
            assertEquals("$size", dp(height), line.layoutParams.height)
            assertEquals("$size", 2, line.maxLines)
            assertEquals("$size", TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, line.autoSizeTextType)
            assertEquals("$size", sp(11f), line.autoSizeMinTextSize)
            assertEquals("$size", sp(14f), line.autoSizeMaxTextSize)
        }
    }

    @Test
    fun `a day of the flat bucket names its condition with the dashboard's short word when no rain falls`() {
        val words =
            mapOf(
                "sunny" to "sonnig",
                "clear-night" to "klar",
                "partlycloudy" to "wolkig",
                "cloudy" to "bewölkt",
                "fog" to "Nebel",
                "rainy" to "Regen",
                "pouring" to "Regen",
                "snowy" to "Schnee",
                "snowy-rainy" to "Schneeregen",
                "hail" to "Hagel",
                "lightning" to "Gewitter",
                "lightning-rainy" to "Gewitter",
                "windy" to "windig",
                "windy-variant" to "windig",
            )

        assertEquals(words, words.keys.associateWith { application.getString(checkNotNull(Condition.of(it)?.short)) })
        assertNull(Condition.EXCEPTIONAL.short)

        signIn()
        val day = """{"datetime":"2026-11-21T00:00:00+01:00","condition":"exceptional","temperature":4,"templow":1}"""
        written(SampleWeather.data(""","days":[$day]"""))
        assertEquals(
            listOf(listOf("Sa", "4°", "1°")),
            WidgetHost.days(placed(SizeBucket.sizeOf(columns = 5, rows = 1))),
        )
    }

    @Test
    fun `every colour a flat widget shows is one of the palette's outside night mode`() {
        assertColours(
            dark = false,
            SizeBucket.sizeOf(columns = 5, rows = 1),
            refuse = false,
            fewest = FLAT_TEXTS,
            more = dividerColour,
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun `every colour a flat widget shows is one of the palette's in night mode`() {
        assertColours(
            dark = true,
            SizeBucket.sizeOf(columns = 5, rows = 1),
            refuse = false,
            fewest = FLAT_TEXTS,
            more = dividerColour,
        )
    }

    /** The divider before the days: the palette's `border`. */
    private val dividerColour = { widget: View, palette: Palette ->
        assertEquals(palette.border, (widget.findViewById<View>(R.id.divider).background as ColorDrawable).color)
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
        const val FLAT_TEXTS = 10
    }
}
