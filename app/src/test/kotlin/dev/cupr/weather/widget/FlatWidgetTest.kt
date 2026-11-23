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
    @Test
    fun `a widget 5 cells wide and 1 high shows now with today below it and the days that fit beside it`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(listOf("7°", "Teilweise bewölkt", "9°", "3°"), widget.shown().take(4))
        assertEquals(
            listOf(
                listOf("Sa", "10°", "1°"),
                listOf("So", "11°", "2°"),
                listOf("Mo", "12°", "3°"),
                listOf("Di", "13°", "4°"),
            ),
            WidgetHost.days(widget),
        )
        val days = widget.findViewById<ViewGroup>(R.id.days)
        assertEquals(R.drawable.weather_sunny_day, days.getChildAt(0).image(R.id.flat_day_icon))
        val icon = days.getChildAt(0).findViewById<View>(R.id.flat_day_icon).layoutParams
        assertEquals(listOf(dp(28), dp(28)), listOf(icon.width, icon.height))
        val high = days.getChildAt(0).text(R.id.flat_day_high)
        assertEquals(Typeface.BOLD, high.typeface?.style)
        // Without settings "now" animates, the days are still (D44).
        assertEquals(R.drawable.weather_partlycloudy_day_animated, widget.icon(R.id.now_icon))
        assertFlatNow(widget, icon = 66)
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
        assertEquals(dp(140), widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.divider).layoutParams.height)
        assertEquals(TapActivity::class.java.name, widget.tapped().component?.className)
    }

    @Test
    fun `a flat strip shows four days at 349 and 386 dp and five from 387 dp, at 388 and 410`() {
        signIn()
        written(SampleWeather.full())

        // 387 dp leave 210 dp for the days, five of 38 dp and their gaps of 4; 386 dp a dp less.
        val sizes = listOf(SizeF(349f, 102f), SizeF(386f, 95f), SizeF(387f, 95f), SizeF(388f, 95f), SizeF(410f, 95f))
        assertEquals(listOf(4, 4, 5, 5, 5), sizes.map { WidgetHost.days(placed(it)).size })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `a flat day is at least 38 dp wide with 4 dp before it, the first 8 after the divider, five fit 388 dp`() {
        signIn()
        written(SampleWeather.full())
        val size = SizeF(388f, 95f)

        val widget = laidOut(placed(size), size)

        val days = widget.findViewById<ViewGroup>(R.id.days)
        val divider = widget.findViewById<View>(R.id.divider)
        // As far from the divider as "now"'s column ends before it.
        assertEquals(dp(8), divider.left - widget.findViewById<View>(R.id.now_column).right)
        assertEquals(dp(8), days.left + days.getChildAt(0).left - divider.right)
        assertEquals(5, days.childCount)
        for (index in 0 until days.childCount) {
            val day = days.getChildAt(index)
            assertTrue("${day.width}", day.width >= dp(38))
            if (index > 0) assertEquals(dp(4), day.left - days.getChildAt(index - 1).right)
        }
        // The weekday's line, the 28 dp image with 1 dp above and below, and the 18 dp of the high and low, about
        // 64 dp, within the 79 dp inside a row 95 dp high.
        val first = days.getChildAt(0)
        assertEquals(first.text(R.id.flat_day_weekday).height + dp(28 + 2 + 18), first.height)
        assertTrue("${days.bottom}", days.bottom <= widget.height - widget.paddingBottom)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `below now a flat widget shows today's high, low and rain, each in its palette role`() {
        signIn()
        val today = "\"templow\":2.6,\"precipitation\":0.0"
        written(SampleWeather.full().replace(today, "\"templow\":2.6,\"precipitation\":2.1"))

        val widget = placed(SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(listOf("9°", "3°", "2,1\u00A0mm"), WidgetHost.shown(widget.findViewById(R.id.now_today_flat)))
        val palette = Palette.LIGHT
        assertEquals(palette.text, widget.text(R.id.now_today_flat_high).currentTextColor)
        assertEquals(Typeface.BOLD, widget.text(R.id.now_today_flat_high).typeface?.style)
        assertEquals(sp(11f), widget.text(R.id.now_today_flat_high).textSize, 0.5f)
        assertEquals(sp(11f), widget.text(R.id.now_today_flat_low).textSize, 0.5f)
        assertEquals(palette.textMuted, widget.text(R.id.now_today_flat_low).currentTextColor)
        assertEquals(palette.rain, widget.text(R.id.now_today_flat_rain).currentTextColor)
        // Without days, 2 cells wide, the rain does not fit beside the high and the low even at 7 sp; 3 wide it does.
        val todayAt = { size: SizeF -> WidgetHost.shown(placed(size).findViewById(R.id.now_today_flat)) }
        assertEquals(listOf("9°", "3°"), todayAt(SizeBucket.sizeOf(columns = 2, rows = 1)))
        assertEquals(listOf("9°", "3°", "2,1\u00A0mm"), todayAt(SizeF(203f, 100f)))

        // A dry today shows no rain; without today, and beside "Neu anmelden", the line stays out.
        written(SampleWeather.full())
        assertEquals(
            listOf("9°", "3°"),
            WidgetHost.shown(placed(SizeBucket.sizeOf(columns = 5, rows = 1)).findViewById(R.id.now_today_flat)),
        )
        written(SampleWeather.full().replace("2026-11-20T00:00:00+01:00", "2026-11-19T00:00:00+01:00"))
        val withoutToday = placed(SizeBucket.sizeOf(columns = 5, rows = 1))
        assertFalse(WidgetHost.visible(withoutToday.findViewById(R.id.now_today_flat), withoutToday))
        written(SampleWeather.full())
        Services.account(application).reject()
        val refused = placed(SizeBucket.sizeOf(columns = 5, rows = 1))
        assertFalse(WidgetHost.visible(refused.findViewById(R.id.now_today_flat), refused))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the flat now with its two-line form and today's line fits the 79 dp inside a 95 dp row`() {
        signIn()
        written(SampleWeather.full().replace("partlycloudy", "lightning-rainy"))
        val size = SizeF(388f, 95f)

        val widget = laidOut(placed(size), size)

        val text = widget.findViewById<View>(R.id.now_text)
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.now_line_long), widget))
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.now_today_flat), widget))
        // 30 dp of temperature, 32 of the two-line form and 16 of today's line.
        assertEquals(dp(78), text.height)
        assertTrue("${text.height}", text.height <= widget.height - widget.paddingTop - widget.paddingBottom)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the flat now centres its temperature, its line, today's line and Neu anmelden in the text area`() {
        signIn()
        val size = SizeF(388f, 95f)
        val wetToday = "\"templow\":2.6,\"precipitation\":2.1"
        written(SampleWeather.full().replace("\"templow\":2.6,\"precipitation\":0.0", wetToday))

        val widget = laidOut(placed(size), size)

        assertCentred(widget.text(R.id.now_temperature))
        assertCentred(widget.text(R.id.now_line_long))
        // Today's high, low and rain as one group, as much room before it as after it.
        val today = widget.findViewById<ViewGroup>(R.id.now_today_flat)
        val high = widget.text(R.id.now_today_flat_high)
        val rain = widget.text(R.id.now_today_flat_rain)
        assertTrue(WidgetHost.visible(rain, widget))
        assertTrue("${high.left} ${rain.right}", high.left > 1)
        assertTrue("${high.left} ${rain.right}", kotlin.math.abs(high.left - (today.width - rain.right)) <= 1)

        written(SampleWeather.full().replace("partlycloudy", "fog"))
        assertCentred(laidOut(placed(size), size).text(R.id.now_line))
        written(SampleWeather.full().replace("partlycloudy", "lightning-rainy"))
        assertCentred(laidOut(placed(size), size).text(R.id.now_line_long))
        Services.account(application).reject()
        val notice = laidOut(placed(size), size).text(R.id.widget_notice)
        assertEquals(2, notice.lineCount)
        assertCentred(notice)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `today's rain takes the largest size from 11 down to 7 sp at which it fits beside the high and the low`() {
        signIn()
        val wetToday = "\"templow\":2.6,\"precipitation\":2.1"
        written(SampleWeather.full().replace("\"templow\":2.6,\"precipitation\":0.0", wetToday))
        val rainAt = { size: SizeF -> laidOut(placed(size), size).text(R.id.now_today_flat_rain) }

        assertEquals(sp(11f), rainAt(SizeF(388f, 95f)).textSize, 0.5f)
        // Without days, 140 dp wide: a 57 dp text area, too narrow at 11 sp.
        val narrow = rainAt(SizeF(140f, 100f))
        assertEquals("2,1\u00A0mm", narrow.text.toString())
        assertTrue("${narrow.textSize}", narrow.textSize < sp(11f) && narrow.textSize >= sp(7f))
        // The group, from the high to the rain, fits the text area; with the rain a step larger it would not, with the
        // dp the app leaves free.
        // The group is measured by the drawn text, since a too-large rain gets a narrower view and is cut.
        val row = narrow.parent as ViewGroup
        val shown = narrow.text.toString()
        val group = narrow.left - row.getChildAt(0).left + narrow.paint.measureText(shown)
        assertTrue("$group ${row.width}", group <= row.width)
        val larger = TextPaint(narrow.paint).apply { textSize = narrow.textSize + sp(0.5f) }
        val growth = larger.measureText(shown) - narrow.paint.measureText(shown)
        assertTrue("$group $growth ${row.width}", group + growth > row.width - dp(1) - 1)
        assertTrue(narrow.paint.measureText(shown) <= narrow.width)
    }

    @Test
    fun `a flat widget resized from 2 to 5 cells wide gives now its column again and shows the days beside it`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, SizeBucket.sizeOf(columns = 2, rows = 1))
        val narrow = WidgetHost.view(application, id)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, narrow.findViewById<View>(R.id.now_column).layoutParams.width)

        val wide = WidgetHost.resize(application, id, SizeBucket.sizeOf(columns = 5, rows = 1))

        assertEquals(dp(140), wide.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(4, WidgetHost.days(wide).size)
        val icon = wide.iconSlot(R.id.now_icon).layoutParams
        assertEquals(listOf(dp(66), dp(66)), listOf(icon.width, icon.height))
    }

    @Test
    fun `a flat widget resized from 5 to 2 cells wide gives now the card's width and the smaller image`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, SizeBucket.sizeOf(columns = 5, rows = 1))
        assertEquals(4, WidgetHost.days(WidgetHost.view(application, id)).size)

        val narrow = WidgetHost.resize(application, id, SizeBucket.sizeOf(columns = 2, rows = 1))

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, narrow.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(emptyList<List<String>>(), WidgetHost.days(narrow))
        val icon = narrow.iconSlot(R.id.now_icon).layoutParams
        assertEquals(listOf(dp(56), dp(56)), listOf(icon.width, icon.height))
    }

    @Test
    fun `a widget 2 cells wide and 1 high shows only now and today's high and low`() {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 1))

        assertEquals(listOf("7°", "Regen in 25 min", "9°", "3°"), widget.shown())
        assertEquals(emptyList<List<String>>(), WidgetHost.days(widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.divider), widget))
        // Without days "now" takes the card's width, its image at the left as with days, smaller.
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertFlatNow(widget, icon = 56)
        // 3 cells wide on a phone with wider cells, room for a day beside "now": still no days (D11).
        assertEquals(listOf("7°", "Regen in 25 min", "9°", "3°"), placed(SizeF(272f, 100f)).shown())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `in the flat bucket a text too long for one line at 13 sp takes two lines at 11 sp, a short one stays`() {
        signIn()
        val size = SizeBucket.sizeOf(columns = 5, rows = 1)

        // With days beside it, so "now" has its column of 140 dp and beside its image a text area of 71 dp.
        written(SampleWeather.full().replace("partlycloudy", "lightning-rainy"))
        val long = placed(size)
        assertEquals(
            listOf("Gewitter, regnerisch", "9°", "3°", "<\u00A00,1\u00A0mm"),
            WidgetHost.shown(long.findViewById(R.id.now_text)).drop(1),
        )
        assertTrue(WidgetHost.visible(long.findViewById(R.id.now_line_long), long))
        assertLine(long.text(R.id.now_line_long), lines = 2, height = 32, largest = 11f, least = 9f)

        written(SampleWeather.full().replace("partlycloudy", "fog"))
        val short = placed(size)
        assertTrue(WidgetHost.visible(short.findViewById(R.id.now_line), short))
        assertFalse(WidgetHost.visible(short.findViewById(R.id.now_line_long), short))
        assertLine(short.text(R.id.now_line), lines = 1, height = 20, largest = 13f, least = 11f)

        // Measured at 13 sp against the text area, not the column: at the test's density "Regnerisch" (64.3 dp),
        // "Regen jetzt" (65 dp) and "Klare Nacht" (67 dp) fit 71 dp on one line, "Schneeregen" (75.7 dp),
        // "Regen in 25 min" and "Außergewöhnlich" do not, though they would fit the column.
        val shown = { condition: String, rain: Duration?, at: SizeF ->
            written(SampleWeather.full(rain).replace("\"condition\":\"partlycloudy\"", "\"condition\":\"$condition\""))
            val widget = placed(at)
            listOf(R.id.now_line, R.id.now_line_long).filter { WidgetHost.visible(widget.findViewById(it), widget) }
        }
        assertEquals(listOf(R.id.now_line), shown("rainy", null, size))
        assertEquals(listOf(R.id.now_line), shown("partlycloudy", Duration.ofMinutes(-5), size))
        assertEquals(listOf(R.id.now_line), shown("clear-night", null, size))
        assertEquals(listOf(R.id.now_line_long), shown("snowy-rainy", null, size))
        assertEquals(listOf(R.id.now_line_long), shown("partlycloudy", Duration.ofMinutes(25), size))
        assertEquals(listOf(R.id.now_line_long), shown("exceptional", null, size))
        // Without days, against the text area beside the image at the card's width: 47 dp at 2 x 1, where
        // "Bewölkt" (46 dp) takes one line and "Regnerisch" two; 189 dp at 272 dp wide, where it takes one.
        val narrow = SizeBucket.sizeOf(columns = 2, rows = 1)
        assertEquals(listOf(R.id.now_line), shown("cloudy", null, narrow))
        assertEquals(listOf(R.id.now_line_long), shown("rainy", null, narrow))
        assertEquals(listOf(R.id.now_line), shown("rainy", null, SizeF(272f, 100f)))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the flat text area is 71 dp, 3 dp beside the image, and holds -12 degrees at 24 sp`() {
        signIn()
        written(SampleWeather.full().replace("\"temperature\":7.4", "\"temperature\":-12.4"))
        val size = SizeBucket.sizeOf(columns = 5, rows = 1)

        val widget = laidOut(placed(size), size)

        val icon = widget.iconSlot(R.id.now_icon)
        val area = widget.findViewById<View>(R.id.now_text)
        assertTrue("${area.width}", kotlin.math.abs(area.width - 71 * density) <= 1)
        assertEquals(dp(3), area.left - icon.right)
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
    fun `a flat day's high and low stand each in its half, all at the largest size at which every one fits`() {
        signIn()
        // Every other day 0° and -4°, the rest -12° and -15°, too wide for their halves at 12 sp.
        val days =
            (1..5).joinToString(",") { index ->
                val (high, low) = if (index % 2 == 1) 0 to -4 else -12 to -15
                """{"datetime":"2026-11-${20 + index}T00:00:00+01:00","condition":"rainy",""" +
                    """"temperature":$high,"templow":$low}"""
            }
        written(SampleWeather.data(""","days":[$days]"""))

        for ((width, count) in listOf(349f to 4, 388f to 5, 410f to 5)) {
            val widget = laidOut(placed(SizeF(width, 95f)), SizeF(width, 95f))
            val cells = widget.findViewById<ViewGroup>(R.id.days)
            assertEquals("$width", count, cells.childCount)
            val texts =
                (0 until cells.childCount).flatMap { index ->
                    val cell = cells.getChildAt(index)
                    listOf(cell.text(R.id.flat_day_high), cell.text(R.id.flat_day_low))
                }
            val sizes = texts.map { it.textSize }
            assertEquals("$width $sizes", 1, sizes.distinct().size)
            assertTrue("$width $sizes", sizes[0] < sp(12f) && sizes[0] >= sp(7f))
            if (width == 388f) assertEquals("9 sp fits only without the free dp: $sizes", sp(8.5f), sizes[0], 0.5f)
            for (index in 0 until cells.childCount) {
                val high = cells.getChildAt(index).text(R.id.flat_day_high)
                val low = cells.getChildAt(index).text(R.id.flat_day_low)
                val row = high.parent as View
                // The gap between the drawn high and the drawn low, 3 dp, sits at the day's middle, within a pixel.
                val highEnd = high.left + high.totalPaddingLeft + high.layout.getLineRight(0)
                val lowStart = low.left + low.totalPaddingLeft + low.layout.getLineLeft(0)
                assertEquals("$width", dp(3).toFloat(), lowStart - highEnd, 1f)
                assertEquals("$width", row.width / 2f, (highEnd + lowStart) / 2f, 1f)
            }
            // Each text whole in its view, as drawn: no "-15" for "-15°". A step larger and one of them would not fit
            // its half with the dp the app leaves free.
            for (text in texts) {
                val drawn = text.paint.measureText(text.text.toString())
                assertTrue("$width ${text.text} $drawn ${text.width}", drawn <= text.width)
            }
            val larger = { text: TextView -> TextPaint(text.paint).apply { textSize = text.textSize + sp(0.5f) } }
            assertTrue(
                "$width",
                texts.any { larger(it).measureText(it.text.toString()) > it.width - dp(1) - 1 },
            )
        }

        // Days of 0° and -4° alone keep the largest size, 12 sp, the gap still in the middle.
        val short =
            (1..5).joinToString(",") {
                """{"datetime":"2026-11-${20 + it}T00:00:00+01:00","temperature":0,"templow":-4}"""
            }
        written(SampleWeather.data(""","days":[$short]"""))
        val day = laidOut(placed(SizeF(388f, 95f)), SizeF(388f, 95f)).findViewById<ViewGroup>(R.id.days).getChildAt(0)
        val high = day.text(R.id.flat_day_high)
        val low = day.text(R.id.flat_day_low)
        assertEquals(sp(12f), high.textSize, 0.5f)
        assertEquals(sp(12f), low.textSize, 0.5f)
        val highEnd = high.left + high.totalPaddingLeft + high.layout.getLineRight(0)
        val lowStart = low.left + low.totalPaddingLeft + low.layout.getLineLeft(0)
        assertEquals((high.parent as View).width / 2f, (highEnd + lowStart) / 2f, 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `where the lows are the widest, they decide the flat days' size`() {
        signIn()
        // Highs of 0°, narrow, and lows of -15°, too wide for their halves at 12 sp.
        val days =
            (1..5).joinToString(",") {
                """{"datetime":"2026-11-${20 + it}T00:00:00+01:00","temperature":0,"templow":-15}"""
            }
        written(SampleWeather.data(""","days":[$days]"""))

        val cells = laidOut(placed(SizeF(388f, 95f)), SizeF(388f, 95f)).findViewById<ViewGroup>(R.id.days)

        val lows = (0 until cells.childCount).map { cells.getChildAt(it).text(R.id.flat_day_low) }
        val sizes = lows.map { it.textSize }.distinct()
        assertEquals("$sizes", listOf(sp(LOWS_BIND)), sizes)
        for (low in lows) {
            val drawn = low.paint.measureText(low.text.toString())
            assertTrue("$drawn ${low.width}", drawn <= low.width)
        }
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

        assertEquals(4, before.size)
        assertEquals(before, WidgetHost.days(widget))
    }

    @Test
    fun `each part of a flat day takes its palette role`() {
        signIn()
        written(SampleWeather.full())

        val days = placed(SizeBucket.sizeOf(columns = 5, rows = 1)).findViewById<ViewGroup>(R.id.days)

        val palette = Palette.LIGHT
        val day = days.getChildAt(0)
        assertEquals(palette.textMuted, day.text(R.id.flat_day_weekday).currentTextColor)
        assertEquals(palette.text, day.text(R.id.flat_day_high).currentTextColor)
        assertEquals(palette.textMuted, day.text(R.id.flat_day_low).currentTextColor)
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

        /** The size in sp of the flat days at 388 dp when their lows of -15° are the widest texts. */
        const val LOWS_BIND = 9f
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

    /** Whether each line of [text] has as much room on its left as on its right, within a pixel. */
    private fun assertCentred(text: TextView) {
        val layout = text.layout
        val room = text.width - text.totalPaddingLeft - text.totalPaddingRight
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = room - layout.getLineRight(line)
            assertTrue("${text.text} $line $left $right", left > 1 && kotlin.math.abs(left - right) <= 1)
        }
    }

    /**
     * "Now" of a flat widget: its [icon] dp image, still or in the frame beside it animated, at the left of its column,
     * beside it temperature, line and today.
     */
    private fun assertFlatNow(
        widget: View,
        icon: Int,
    ) {
        val column = widget.findViewById<LinearLayout>(R.id.now_column)
        val text = widget.findViewById<ViewGroup>(R.id.now_text)
        assertEquals(LinearLayout.HORIZONTAL, column.orientation)
        assertEquals(
            listOf(R.id.now_icon, R.id.now_icon_animated, R.id.now_text),
            (0 until column.childCount).map { column.getChildAt(it).id },
        )
        assertEquals(dp(icon), widget.iconSlot(R.id.now_icon).layoutParams.width)
        assertEquals(dp(icon), widget.iconSlot(R.id.now_icon).layoutParams.height)
        // A warning's short form takes the line's place, "Stand 14:30" today's line's (D16, D47); a plain text stands
        // in the text area too, below the short form (D18).
        assertEquals(
            listOf(
                R.id.now_temperature,
                R.id.now_line,
                R.id.now_line_long,
                R.id.short_form,
                R.id.widget_notice,
                R.id.now_today_flat,
                R.id.stale,
                R.id.widget_plain,
            ),
            (0 until text.childCount).map { text.getChildAt(it).id },
        )
    }
}
