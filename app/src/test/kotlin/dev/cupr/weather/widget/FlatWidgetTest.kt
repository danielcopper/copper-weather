package dev.cupr.weather.widget

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Layout
import android.text.TextPaint
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
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The widget one row high, the flat bucket (D11, D35, D36, D41): "now" and the days beside it, texts and colours. */
@RunWith(RobolectricTestRunner::class)
class FlatWidgetTest : WidgetTestBase() {
    private val density get() = application.resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).toInt()

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, application.resources.displayMetrics)

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
            ),
            WidgetHost.days(widget),
        )
        val days = widget.findViewById<ViewGroup>(R.id.days)
        assertEquals(R.drawable.weather_sunny_day, days.getChildAt(0).image(R.id.flat_day_icon))
        val high = days.getChildAt(0).text(R.id.flat_day_high)
        assertEquals(Typeface.BOLD, high.typeface?.style)
        assertEquals(R.drawable.weather_partlycloudy_day, widget.image(R.id.now_icon))
        assertFlatNow(widget, icon = 72)
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
        assertEquals(dp(144), widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.divider).layoutParams.height)
        assertEquals(TapActivity::class.java.name, widget.tapped().component?.className)
    }

    @Test
    fun `a flat strip shows three days at 349 dp and four at 388 and 410`() {
        signIn()
        written(SampleWeather.full())

        assertEquals(
            listOf(3, 4, 4),
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

        assertEquals(dp(144), wide.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(3, WidgetHost.days(wide).size)
    }

    @Test
    fun `a widget 2 cells wide and 1 high shows only now`() {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 1))

        assertEquals(listOf("7°", "Regen in 25 min"), widget.shown())
        assertEquals(emptyList<List<String>>(), WidgetHost.days(widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.divider), widget))
        // Without days "now" takes the card's width, its image at the left as with days, smaller.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertFlatNow(widget, icon = 56)
        // 3 cells wide on a phone with wider cells, room for a day beside "now": still only "now" (D11).
        assertEquals(listOf("7°", "Regen in 25 min"), placed(SizeF(272f, 100f)).shown())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `in the flat bucket a text too long for one line at 13 sp takes two lines at 11 sp, a short one stays`() {
        signIn()
        val size = SizeBucket.sizeOf(columns = 5, rows = 1)

        // With days beside it, so "now" has its column of 144 dp and beside its image a text area of 66 dp.
        written(SampleWeather.full().replace("partlycloudy", "lightning-rainy"))
        val long = placed(size)
        assertEquals(listOf("Gewitter, regnerisch"), WidgetHost.shown(long.findViewById(R.id.now_text)).drop(1))
        assertTrue(WidgetHost.visible(long.findViewById(R.id.now_line_long), long))
        assertLine(long.text(R.id.now_line_long), lines = 2, height = 32, largest = 11f, least = 9f)

        written(SampleWeather.full().replace("partlycloudy", "fog"))
        val short = placed(size)
        assertTrue(WidgetHost.visible(short.findViewById(R.id.now_line), short))
        assertFalse(WidgetHost.visible(short.findViewById(R.id.now_line_long), short))
        assertLine(short.text(R.id.now_line), lines = 1, height = 20, largest = 13f, least = 11f)

        // Measured at 13 sp against the text area, not the column: at the test's density "Regnerisch" (64.3 dp) fits
        // 66 dp on one line, "Regen in 25 min", "Außergewöhnlich" and "Klare Nacht" (67 dp) do not, though they would
        // fit the column.
        val shown = { condition: String, rain: Duration?, at: SizeF ->
            written(SampleWeather.full(rain).replace("\"condition\":\"partlycloudy\"", "\"condition\":\"$condition\""))
            val widget = placed(at)
            listOf(R.id.now_line, R.id.now_line_long).filter { WidgetHost.visible(widget.findViewById(it), widget) }
        }
        assertEquals(listOf(R.id.now_line), shown("rainy", null, size))
        assertEquals(listOf(R.id.now_line_long), shown("partlycloudy", Duration.ofMinutes(25), size))
        assertEquals(listOf(R.id.now_line_long), shown("exceptional", null, size))
        assertEquals(listOf(R.id.now_line_long), shown("clear-night", null, size))
        // Without days, against the text area beside the smaller image at the card's width: 44 dp at 2 x 1, where
        // "Bewölkt" (46 dp) takes two lines; 186 dp at 272 dp wide, where it takes one.
        assertEquals(listOf(R.id.now_line_long), shown("cloudy", null, SizeBucket.sizeOf(columns = 2, rows = 1)))
        assertEquals(listOf(R.id.now_line), shown("cloudy", null, SizeF(272f, 100f)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the flat text area is 66 dp beside the image, 6 dp from it, and holds -12 degrees at 24 sp`() {
        signIn()
        written(SampleWeather.full().replace("\"temperature\":7.4", "\"temperature\":-12.4"))
        val size = SizeBucket.sizeOf(columns = 5, rows = 1)

        val widget = laidOut(placed(size), size)

        val icon = widget.findViewById<View>(R.id.now_icon)
        val area = widget.findViewById<View>(R.id.now_text)
        assertTrue("${area.width}", kotlin.math.abs(area.width - 66 * density) <= 1)
        assertTrue("${area.left - icon.right}", kotlin.math.abs(area.left - icon.right - 6 * density) <= 1)
        val temperature = widget.text(R.id.now_temperature)
        assertEquals("-12°", temperature.text.toString())
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, temperature.autoSizeTextType)
        assertEquals(Math.round(sp(24f)), temperature.autoSizeMaxTextSize)
        assertEquals(Math.round(sp(18f)), temperature.autoSizeMinTextSize)
        assertEquals(dp(30), temperature.layoutParams.height)
        val largest = TextPaint(temperature.paint).apply { textSize = sp(24f) }
        assertTrue(largest.measureText("-12°") <= area.width)
        // TextView's own sizing, run with the layout's values, keeps the largest size: "-12°" fits the area's width
        // and, without the font's padding, its 30 dp height.
        resize(temperature)
        assertEquals(sp(24f), temperature.textSize, 0.5f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `in the flat bucket Neu anmelden shows whole, on two lines and smaller where the text area is narrow`() {
        signIn()
        written(SampleWeather.full())
        Services.account(application).reject()

        for (size in listOf(SizeBucket.sizeOf(columns = 5, rows = 1), SizeBucket.sizeOf(columns = 2, rows = 1))) {
            val notice = laidOut(placed(size), size).text(R.id.widget_notice)
            assertEquals(2, notice.maxLines)
            assertEquals(dp(36), notice.layoutParams.height)
            assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, notice.autoSizeTextType)
            resize(notice)
            val text = notice.text.toString()
            val layout = notice.layout
            assertEquals("$size", "Neu anmelden", text)
            assertTrue("$size ${layout.lineCount}", layout.lineCount <= 2)
            assertEquals("$size", text.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount - 1) {
                assertEquals("$size breaks a word", ' ', text[layout.getLineEnd(line) - 1])
            }
        }
    }

    @Test
    fun `only the flat bucket's two-line form hyphenates`() {
        signIn()
        written(SampleWeather.full())

        val flat = placed(SizeBucket.sizeOf(columns = 5, rows = 1)).text(R.id.now_line_long)
        val small = placed(SizeBucket.sizeOf(columns = 2, rows = 2)).text(R.id.now_line_long)

        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, flat.hyphenationFrequency)
        assertEquals(Layout.HYPHENATION_FREQUENCY_NONE, small.hyphenationFrequency)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `a flat day's texts shrink to fit its cell instead of being cut`() {
        signIn()
        // Five days of -12° and -15°, the rain a trace, "< 0,1 mm", on every other one, sleet without an amount, so its
        // short word, on the rest.
        val days =
            (1..5).joinToString(",") { index ->
                val wet = index % 2 == 1
                val condition = if (wet) "rainy" else "snowy-rainy"
                val rain = if (wet) ",\"precipitation\":0.04" else ""
                """{"datetime":"2026-11-${20 + index}T00:00:00+01:00","condition":"$condition",""" +
                    """"temperature":-12,"templow":-15$rain}"""
            }
        written(SampleWeather.data(""","days":[$days]"""))
        var tooWide = 0

        for ((width, count) in listOf(349f to 3, 388f to 4, 410f to 4)) {
            val widget = laidOut(placed(SizeF(width, 102f)), SizeF(width, 102f))
            val cells = widget.findViewById<ViewGroup>(R.id.days)
            assertEquals("$width", count, cells.childCount)
            for (index in 0 until cells.childCount) {
                val cell = cells.getChildAt(index)
                val note = if (index % 2 == 0) "<\u00A00,1\u00A0mm" else "Schneeregen"
                assertEquals(
                    listOf(
                        "-12°",
                        "-15°",
                        note,
                    ),
                    listOf(R.id.flat_day_high, R.id.flat_day_low, R.id.flat_day_note).map {
                        cell.text(it).text.toString()
                    },
                )
                // The high and the low each take half the day, less the low's 3 dp before it.
                val high = cell.text(R.id.flat_day_high).width
                val low = cell.text(R.id.flat_day_low).width
                assertTrue("$width $high $low", kotlin.math.abs(high - low) <= 1)
                assertTrue("$width $high", kotlin.math.abs(high - (cell.width - dp(3)) / 2f) <= 1)
                for (id in listOf(R.id.flat_day_high, R.id.flat_day_low, R.id.flat_day_note)) {
                    val text = cell.text(id)
                    val shown = text.text.toString()
                    assertEquals("$width $shown", TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, text.autoSizeTextType)
                    assertEquals("$width $shown", Math.round(sp(7.5f)), text.autoSizeMinTextSize)
                    assertEquals("$width $shown", 1, text.maxLines)
                    assertTrue(
                        "$width $shown",
                        TextPaint(text.paint).apply { textSize = sp(7.5f) }.measureText(shown) <= text.width,
                    )
                    val largest = TextPaint(text.paint).apply { textSize = text.autoSizeMaxTextSize.toFloat() }
                    if (largest.measureText(shown) > text.width) tooWide++
                }
            }
        }
        // Some of them need the shrink: at their largest size they would not fit.
        assertTrue("$tooWide", tooWide > 0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a redraw of a flat widget whose line took two lines puts Neu anmelden in its place`() {
        signIn()
        written(SampleWeather.full().replace("partlycloudy", "lightning-rainy"))
        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.now_line_long), widget))

        Services.account(application).reject()
        Widgets.update(application)

        assertEquals(listOf("7°", "Neu anmelden"), widget.shown().take(2))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line_long), widget))
    }

    @Test
    fun `a redraw of a flat widget does not add its days a second time`() {
        signIn()
        written(SampleWeather.full())
        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))
        val before = WidgetHost.days(widget)

        Widgets.update(application)

        assertEquals(3, before.size)
        assertEquals(before, WidgetHost.days(widget))
    }

    @Test
    fun `each part of a flat day takes its palette role`() {
        signIn()
        written(SampleWeather.full())

        val days = placed(SizeBucket.sizeOf(columns = 5, rows = 1)).findViewById<ViewGroup>(R.id.days)

        val palette = Palette.LIGHT
        val dry = days.getChildAt(0)
        val wet = days.getChildAt(1)
        assertEquals(palette.textMuted, dry.text(R.id.flat_day_weekday).currentTextColor)
        assertEquals(palette.text, dry.text(R.id.flat_day_high).currentTextColor)
        assertEquals(palette.textMuted, dry.text(R.id.flat_day_low).currentTextColor)
        assertEquals("sonnig", dry.text(R.id.flat_day_note).text.toString())
        assertEquals(palette.textMuted, dry.text(R.id.flat_day_note).currentTextColor)
        assertEquals("2,5\u00A0mm", wet.text(R.id.flat_day_note).text.toString())
        assertEquals(palette.rain, wet.text(R.id.flat_day_note).currentTextColor)
    }

    /** [widget] measured and laid out at [size], as a launcher does before it draws it. */
    private fun laidOut(
        widget: View,
        size: SizeF,
    ): View {
        val density = application.resources.displayMetrics.density
        val width = (size.width * density).toInt()
        val height = (size.height * density).toInt()
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, width, height)
        return widget
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
            assertFalse(WidgetHost.visible(refused.findViewById(R.id.divider), refused))
            assertFalse(WidgetHost.visible(refused.findViewById(R.id.days), refused))
        }
    }

    private companion object {
        /** Fewer texts than a flat widget shows in the colour tests. */
        const val FLAT_TEXTS = 10
    }

    /** [line] takes up to [lines] lines in [height] dp, at most [largest] sp, shrinking down to 11 sp. */
    private fun assertLine(
        line: TextView,
        lines: Int,
        height: Int,
        largest: Float,
        least: Float,
    ) {
        assertEquals(lines, line.maxLines)
        assertEquals(dp(height), line.layoutParams.height)
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM, line.autoSizeTextType)
        assertEquals(Math.round(sp(least)), line.autoSizeMinTextSize)
        assertEquals(Math.round(sp(largest)), line.autoSizeMaxTextSize)
    }

    /**
     * Runs TextView's own sizing of [text] once more, with the values its layout gave it, on the size it is laid out
     * at; it throws when the layout lost its autosize, whose values it reads.
     */
    private fun resize(text: TextView) {
        text.setAutoSizeTextTypeUniformWithConfiguration(
            text.autoSizeMinTextSize,
            text.autoSizeMaxTextSize,
            text.autoSizeStepGranularity,
            TypedValue.COMPLEX_UNIT_PX,
        )
    }

    /** "Now" of a flat widget: its image, [icon] dp, at the left of its column, beside it temperature over line. */
    private fun assertFlatNow(
        widget: View,
        icon: Int,
    ) {
        val column = widget.findViewById<LinearLayout>(R.id.now_column)
        val text = widget.findViewById<ViewGroup>(R.id.now_text)
        assertEquals(LinearLayout.HORIZONTAL, column.orientation)
        assertEquals(listOf(R.id.now_icon, R.id.now_text), (0 until column.childCount).map { column.getChildAt(it).id })
        assertEquals(dp(icon), widget.findViewById<View>(R.id.now_icon).layoutParams.width)
        assertEquals(dp(icon), widget.findViewById<View>(R.id.now_icon).layoutParams.height)
        assertEquals(
            listOf(R.id.now_temperature, R.id.now_line, R.id.now_line_long, R.id.widget_notice),
            (0 until text.childCount).map { text.getChildAt(it).id },
        )
    }
}
