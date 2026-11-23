package dev.cupr.weather.widget

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.OffsetDateTime

/**
 * A warning that applies on the widget (D15, D47): the band across the top in the wide and tall buckets, the short form
 * in the line's place in the small and flat ones; and "Stand 14:30" while the snapshot is old (D16).
 */
@RunWith(RobolectricTestRunner::class)
class WarningWidgetTest : WidgetTestBase() {
    private val wide = SizeBucket.sizeOf(columns = 4, rows = 2)
    private val tall = SizeBucket.sizeOf(columns = 4, rows = 3)
    private val small = SizeBucket.sizeOf(columns = 2, rows = 2)
    private val flat = SizeBucket.sizeOf(columns = 5, rows = 1)

    @Test
    fun `a warning that applies shows as the band with its headline in its level's colour on its tint`() {
        signIn()
        written(SampleWeather.full().warned(GUSTS))

        for (size in listOf(wide, tall)) {
            val widget = placed(size)

            assertEquals(GUSTS_HEADLINE, widget.shown().first())
            val band = widget.findViewById<ViewGroup>(R.id.band)
            assertTrue(WidgetHost.visible(band, widget))
            assertEquals(R.id.band, (widget as ViewGroup).getChildAt(0).id)
            assertEquals(Palette.LIGHT.scale3, widget.text(R.id.band_text).currentTextColor)
            assertEquals(tint(Palette.LIGHT.scale3), band.backgroundTintList?.defaultColor)
            assertFalse(WidgetHost.visible(widget.findViewById(R.id.band_more), widget))
            assertFalse(WidgetHost.visible(widget.findViewById(R.id.band_symbol), widget))
            assertFalse(WidgetHost.visible(widget.findViewById(R.id.short_form), widget))
            assertEquals(1, widget.text(R.id.band_text).maxLines)
            assertEquals(TextUtils.TruncateAt.END, widget.text(R.id.band_text).ellipsize)
        }
    }

    @Test
    fun `a warning that applies shows as the short form in the line's place in the flat and the small bucket`() {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)).warned(GUSTS))

        val strip = placed(flat)
        assertEquals(listOf("7°", "Warnung", "9°", "3°"), strip.shown().take(4))
        assertShortForm(strip, Palette.LIGHT.scale3, R.drawable.symbol_wind)

        val square = placed(small)
        assertEquals(listOf("7°", "9°", "3°", "Warnung"), square.shown())
        assertShortForm(square, Palette.LIGHT.scale3, R.drawable.symbol_wind)
        assertFalse(WidgetHost.visible(square.findViewById(R.id.band), square))
    }

    @Test
    fun `two warnings that apply show the higher level and +1`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN))

        val band = placed(wide)
        assertEquals(listOf(ORKAN_HEADLINE, "+1"), band.shown().take(2))
        assertEquals(Palette.LIGHT.scale4, band.text(R.id.band_more).currentTextColor)
        assertEquals(tint(Palette.LIGHT.scale4), band.findViewById<View>(R.id.band).backgroundTintList?.defaultColor)

        for (size in listOf(flat, small)) {
            val widget = placed(size)
            assertTrue(widget.shown().containsAll(listOf("Unwetter", "+1")))
            assertShortForm(widget, Palette.LIGHT.scale4, R.drawable.symbol_wind)
            assertEquals(Palette.LIGHT.scale4, widget.text(R.id.short_more).currentTextColor)
        }
    }

    @Test
    fun `a warning that begins later does not show, one without a start shows at once, one without an end shows`() {
        signIn()
        val later = """{"level":2,"type":52,"headline":"$GUSTS_HEADLINE","start":"${at(30)}","end":"${at(240)}"}"""
        written(SampleWeather.full().warned(later))
        assertEquals(listOf(R.id.now), shownParts(placed(wide)).take(1))

        written(SampleWeather.full().warned("""{"level":2,"type":52,"headline":"$GUSTS_HEADLINE","end":"${at(60)}"}"""))
        assertEquals(GUSTS_HEADLINE, placed(wide).shown().first())

        written(
            SampleWeather.full().warned("""{"level":2,"type":52,"headline":"$GUSTS_HEADLINE","start":"${at(-600)}"}"""),
        )
        assertEquals(GUSTS_HEADLINE, placed(wide).shown().first())

        written(SampleWeather.full().warned(GUSTS.replace(at(120), at(-1))))
        assertEquals(listOf(R.id.now), shownParts(placed(wide)).take(1))
    }

    @Test
    fun `a band without a headline shows the short form's word and symbol`() {
        signIn()
        written(SampleWeather.full().warned("""{"level":4,"type":62}"""))

        val widget = placed(wide)

        assertEquals("Unwetter", widget.shown().first())
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.band_symbol), widget))
        // The word and the symbol side by side at the band's start, its text's empty room after them.
        val laid = laidOut(widget, wide)
        val word = laid.findViewById<View>(R.id.band_word)
        val symbol = laid.findViewById<View>(R.id.band_symbol)
        assertEquals(dp(2), symbol.left - word.right)
        assertEquals(View.INVISIBLE, laid.findViewById<View>(R.id.band_text).visibility)
        assertEquals(R.drawable.symbol_rain, widget.image(R.id.band_symbol))
        assertEquals(
            PorterDuffColorFilter(Palette.LIGHT.scale5, PorterDuff.Mode.SRC_ATOP),
            widget.findViewById<ImageView>(R.id.band_symbol).colorFilter,
        )
    }

    @Test
    fun `the band takes room from the days, and a widget redrawn without a warning hides it`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, tall)
        assertEquals(5, WidgetHost.days(WidgetHost.view(application, id)).size)

        written(SampleWeather.full().warned(GUSTS))
        Widgets.update(application)
        val warned = WidgetHost.view(application, id)
        assertEquals(4, WidgetHost.days(warned).size)
        assertTrue(WidgetHost.visible(warned.findViewById(R.id.band), warned))

        written(SampleWeather.full())
        Widgets.update(application)
        val clear = WidgetHost.view(application, id)
        assertFalse(WidgetHost.visible(clear.findViewById(R.id.band), clear))
        assertEquals(5, WidgetHost.days(clear).size)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the band and the short form take the dark palette's level colours`() {
        signIn()
        written(SampleWeather.full().warned(ORKAN))

        val band = placed(wide)
        assertEquals(Palette.DARK.scale4, band.text(R.id.band_text).currentTextColor)
        assertEquals(tint(Palette.DARK.scale4), band.findViewById<View>(R.id.band).backgroundTintList?.defaultColor)
        assertShortForm(placed(flat), Palette.DARK.scale4, R.drawable.symbol_wind, palette = Palette.DARK)
    }

    @Test
    fun `each level takes its colour of the intensity scale and its word`() {
        signIn()
        val levels =
            listOf(
                1 to Palette.LIGHT.scale2,
                2 to Palette.LIGHT.scale3,
                3 to Palette.LIGHT.scale4,
                4 to Palette.LIGHT.scale5,
            )

        for ((level, colour) in levels) {
            written(SampleWeather.full().warned("""{"level":$level,"type":22}"""))
            val widget = placed(small)
            val word = if (level >= 3) "Unwetter" else "Warnung"
            assertEquals("$level", word, widget.text(R.id.short_word).text.toString())
            assertShortForm(widget, colour, R.drawable.symbol_frost)
        }
    }

    @Test
    fun `in the flat bucket the short form takes the line's place before Neu anmelden`() {
        signIn()
        written(SampleWeather.full().warned(GUSTS))
        Services.account(application).reject()

        val widget = placed(flat)

        assertEquals(listOf("7°", "Warnung", "9°", "3°"), widget.shown().take(4))
        assertFalse(widget.shown().contains("Neu anmelden"))
        // Wide, "Neu anmelden" keeps its notice line below "now".
        assertTrue(placed(wide).shown().contains("Neu anmelden"))
    }

    @Test
    fun `two hours after the last successful fetch the widget shows Stand and that time, after a fetch no longer`() {
        signIn()
        written(SampleWeather.full(), fetchedAt = SampleWeather.NOW.minus(Duration.ofHours(2)))
        val ids = listOf(wide, tall, small, flat).associateWith { WidgetHost.place(application, it) }

        for ((size, id) in ids) {
            val widget = WidgetHost.view(application, id)
            assertTrue("$size ${widget.shown()}", widget.shown().contains("Stand 11:40"))
            val note = widget.text(if (size == wide || size == tall) R.id.notice_stale else R.id.stale)
            assertEquals("$size", "Stand 11:40", note.text.toString())
            assertTrue("$size", WidgetHost.visible(note, widget))
            assertEquals("$size", Palette.LIGHT.textMuted, note.currentTextColor)
        }
        // In the small and the flat bucket in the place of today's high and low.
        assertEquals(
            listOf("7°", "Stand 11:40", "Teilweise bewölkt"),
            WidgetHost.view(application, ids.getValue(small)).shown(),
        )
        assertEquals(
            listOf("7°", "Teilweise bewölkt", "Stand 11:40"),
            WidgetHost.view(application, ids.getValue(flat)).shown().take(3),
        )
        // In the wide and tall buckets in the notice line below "now".
        assertEquals(
            listOf("7°", "9°", "3°", "Teilweise bewölkt", "Stand 11:40"),
            WidgetHost.view(application, ids.getValue(wide)).shown().take(5),
        )

        written(SampleWeather.full())
        Widgets.update(application)

        for ((size, id) in ids) {
            val widget = WidgetHost.view(application, id)
            assertFalse("$size ${widget.shown()}", widget.shown().any { it.startsWith("Stand") })
        }
        assertEquals(
            listOf("7°", "9°", "3°", "Teilweise bewölkt"),
            WidgetHost.view(application, ids.getValue(small)).shown(),
        )
    }

    @Test
    fun `with Neu anmelden the notice line says Stand after it, and the flat bucket says it below it`() {
        signIn()
        written(SampleWeather.full(), fetchedAt = SampleWeather.NOW.minus(Duration.ofHours(2)))
        Services.account(application).reject()

        val widget = placed(wide)

        val row = widget.findViewById<ViewGroup>(R.id.notice_row)
        assertEquals(listOf("Neu anmelden", "Stand 11:40"), WidgetHost.shown(row))
        assertEquals(Palette.LIGHT.accent, widget.text(R.id.widget_notice).currentTextColor)
        assertEquals(Palette.LIGHT.textMuted, widget.text(R.id.notice_stale).currentTextColor)
        // In the flat bucket "Neu anmelden" takes the line's place and today's line gives way; Stand keeps its place
        // (D16).
        assertEquals(
            listOf("7°", "Neu anmelden", "Stand 11:40"),
            placed(SizeBucket.sizeOf(columns = 2, rows = 1)).shown(),
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `a wide widget 4 x 2 with the band and the notice line still holds now and the hours`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN), fetchedAt = SampleWeather.NOW.minus(Duration.ofHours(2)))
        Services.account(application).reject()

        val widget = laidOut(placed(wide), wide)

        val hours = widget.findViewById<View>(R.id.hours)
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.band), widget))
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.notice_row), widget))
        assertEquals(5, WidgetHost.hours(widget).size)
        assertTrue("${hours.bottom} ${widget.height}", hours.bottom <= widget.height - widget.paddingBottom)
        assertEquals(dp(22), widget.findViewById<View>(R.id.band).height)
        // The headline on one line, "+1" whole at its right.
        val more = widget.text(R.id.band_more)
        assertTrue(more.paint.measureText("+1") <= more.width)
        assertEquals(1, widget.text(R.id.band_text).lineCount)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the short form fits the flat text area and the small line, its word left out where even 9 sp is too wide`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN))
        val fits = { size: SizeF ->
            val widget = laidOut(placed(size), size)
            val pill = widget.findViewById<ViewGroup>(R.id.short_pill)
            val slot = widget.findViewById<View>(R.id.short_form)
            assertTrue("$size ${pill.width} ${slot.width}", pill.width <= slot.width)
            for (id in listOf(R.id.short_word, R.id.short_more)) {
                val text = widget.text(id)
                if (WidgetHost.visible(text, widget)) {
                    assertTrue("$size $id", text.paint.measureText(text.text.toString()) <= text.width)
                }
            }
            widget
        }

        // The flat text area, 71 dp beside the days: "Unwetter", its symbol and "+1" at 9.5 sp, 69.2 dp.
        val strip = fits(SizeF(388f, 95f))
        val word = strip.text(R.id.short_word)
        assertTrue(WidgetHost.visible(word, strip))
        assertEquals(sp(9.5f), word.textSize, 0.5f)
        // The small line, 106 dp: at 14 sp.
        val square = fits(small)
        assertEquals(sp(14f), square.text(R.id.short_word).textSize, 0.5f)
        // At 2 x 1 the text area is 47 dp: the warning sign in the word's place, the kind's symbol and "+1" (D47).
        val narrow = fits(SizeBucket.sizeOf(columns = 2, rows = 1))
        assertFalse(WidgetHost.visible(narrow.findViewById(R.id.short_word), narrow))
        assertTrue(WidgetHost.visible(narrow.findViewById(R.id.short_alert), narrow))
        assertTrue(WidgetHost.visible(narrow.findViewById(R.id.short_more), narrow))
        assertEquals(sp(10.5f), narrow.text(R.id.short_more).textSize, 0.5f)
        val pill = narrow.findViewById<ViewGroup>(R.id.short_pill)
        assertEquals(
            listOf(R.id.short_alert, R.id.short_symbol, R.id.short_more),
            (0 until pill.childCount)
                .map {
                    pill.getChildAt(
                        it,
                    )
                }.filter { WidgetHost.visible(it, narrow) }
                .map { it.id },
        )
        // One warning alone: "Unwetter" and its symbol at 12 sp in the flat text area, 69.2 dp.
        written(SampleWeather.full().warned(ORKAN))
        assertEquals(sp(12f), fits(SizeF(388f, 95f)).text(R.id.short_word).textSize, 0.5f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `where the word does not fit, the warning sign stands in its place before the kind's symbol`() {
        signIn()
        written(SampleWeather.full().warned(GUSTS, FROST))

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 1))

        assertFalse(WidgetHost.visible(widget.findViewById(R.id.short_word), widget))
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.short_alert), widget))
        assertEquals(R.drawable.symbol_wind, widget.image(R.id.short_symbol))
        assertEquals(
            PorterDuffColorFilter(Palette.LIGHT.scale3, PorterDuff.Mode.SRC_ATOP),
            widget.findViewById<ImageView>(R.id.short_alert).colorFilter,
        )
        assertEquals(listOf("7°", "+1"), widget.shown().take(2))
        // With the word, no sign; and a kind whose symbol is the warning sign shows it once.
        val strip = placed(flat)
        assertFalse(WidgetHost.visible(strip.findViewById(R.id.short_alert), strip))
        written(SampleWeather.full().warned("""{"level":2,"type":98}"""))
        val other = placed(SizeBucket.sizeOf(columns = 2, rows = 1))
        assertFalse(WidgetHost.visible(other.findViewById(R.id.short_alert), other))
        assertEquals(R.drawable.symbol_other, other.image(R.id.short_symbol))
    }

    @Test
    fun `a composite symbol is as much wider than high as its drawable, in the short form and the band`() {
        signIn()
        written(SampleWeather.full().warned("""{"level":1,"type":82}"""))
        val frost = checkNotNull(application.getDrawable(R.drawable.symbol_frost))
        val aspect = frost.intrinsicWidth.toFloat() / frost.intrinsicHeight

        val square = placed(small)
        val symbol = square.findViewById<View>(R.id.short_symbol).layoutParams
        assertEquals(R.drawable.symbol_frost, square.image(R.id.short_symbol))
        assertEquals(symbol.height * aspect, symbol.width.toFloat(), 1f)
        assertTrue(symbol.width > symbol.height)
        val band = placed(wide).findViewById<View>(R.id.band_symbol).layoutParams
        assertEquals(dp(16) * aspect, band.width.toFloat(), 1f)
    }

    @Test
    fun `a tall widget whose only notice is Stand shows a day fewer, and the fresh one all`() {
        signIn()
        written(SampleWeather.full(), fetchedAt = SampleWeather.NOW.minus(Duration.ofHours(2)))
        val id = WidgetHost.place(application, tall)
        val old = WidgetHost.view(application, id)
        assertEquals(listOf("Stand 11:40"), WidgetHost.shown(old.findViewById(R.id.notice_row)))
        assertEquals(4, WidgetHost.days(old).size)

        written(SampleWeather.full())
        Widgets.update(application)
        assertEquals(5, WidgetHost.days(WidgetHost.view(application, id)).size)
    }

    @Test
    fun `a warning that applies shows above the plain text of a snapshot without a usable now, in each bucket`() {
        signIn()
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN))
        val text = "Noch keine Daten – zum Öffnen tippen"
        val narrow = SizeBucket.sizeOf(columns = 2, rows = 1)
        val ids = listOf(wide, tall, small, flat, narrow).associateWith { WidgetHost.place(application, it) }

        for (size in listOf(wide, tall)) {
            val widget = WidgetHost.view(application, ids.getValue(size))
            assertEquals("$size", listOf(ORKAN_HEADLINE, text), widget.shown())
            assertEquals(listOf(R.id.band, R.id.plain_area), shownParts(widget))
        }
        for (size in listOf(small, flat, narrow)) {
            val widget = WidgetHost.view(application, ids.getValue(size))
            assertEquals("$size", text, widget.shown().last())
            assertShortForm(widget, Palette.LIGHT.scale4, R.drawable.symbol_wind)
            val order = drawn(widget)
            assertTrue("$size", order.indexOf(R.id.short_form) < order.indexOf(R.id.widget_plain))
        }

        // The same widgets, the warning gone: the plain text alone.
        written("""{"schema_version":1,"now":{}}""")
        Widgets.update(application)
        for ((size, id) in ids) {
            val widget = WidgetHost.view(application, id)
            assertEquals("$size", listOf(text), widget.shown())
            for (part in listOf(R.id.band, R.id.short_form)) {
                assertFalse("$size", widget.findViewById<View>(part)?.let { WidgetHost.visible(it, widget) } == true)
            }
        }
    }

    @Test
    fun `beside a warning above the plain text an old snapshot says Stand, without one the text stands alone`() {
        signIn()
        val old = SampleWeather.NOW.minus(Duration.ofHours(2))
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN), fetchedAt = old)
        val text = "Noch keine Daten – zum Öffnen tippen"
        val narrow = SizeBucket.sizeOf(columns = 2, rows = 1)
        val ids = listOf(wide, tall, small, flat, narrow).associateWith { WidgetHost.place(application, it) }

        for (size in listOf(wide, tall)) {
            val widget = WidgetHost.view(application, ids.getValue(size))
            assertEquals("$size", listOf(ORKAN_HEADLINE, "Stand 11:40", text), widget.shown())
            assertEquals(listOf("Stand 11:40"), WidgetHost.shown(widget.findViewById(R.id.notice_row)))
        }
        for (size in listOf(small, flat, narrow)) {
            val widget = WidgetHost.view(application, ids.getValue(size))
            assertEquals("$size", listOf(text, "Stand 11:40"), widget.shown().takeLast(2))
            val stand = if (size == small) R.id.plain_stale else R.id.stale
            assertEquals(Palette.LIGHT.textMuted, widget.text(stand).currentTextColor)
        }

        // The same widgets with the weather again: no plain text and no "Stand" left over.
        written(SampleWeather.full())
        Widgets.update(application)
        assertEquals(
            listOf("7°", "9°", "3°", "Teilweise bewölkt"),
            WidgetHost.view(application, ids.getValue(small)).shown(),
        )

        written("""{"schema_version":1,"now":{}}""", fetchedAt = old)
        Widgets.update(application)
        for ((size, id) in ids) assertEquals("$size", listOf(text), WidgetHost.view(application, id).shown())
    }

    @Test
    fun `a wide widget drawn from a refused sign-in's weather to a plain text says Stand alone in its notice line`() {
        signIn()
        written(SampleWeather.full())
        Services.account(application).reject()
        val id = WidgetHost.place(application, wide)
        assertEquals(
            listOf("Neu anmelden"),
            WidgetHost.shown(WidgetHost.view(application, id).findViewById(R.id.notice_row)),
        )

        val old = SampleWeather.NOW.minus(Duration.ofHours(2))
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN), fetchedAt = old)
        Widgets.update(application)

        val widget = WidgetHost.view(application, id)
        assertEquals(listOf("Stand 11:40"), WidgetHost.shown(widget.findViewById(R.id.notice_row)))
        assertEquals(listOf(ORKAN_HEADLINE, "Stand 11:40", "Nicht angemeldet – zum Anmelden tippen"), widget.shown())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `one row high the plain text shrinks so the short form and Stand stay whole`() {
        signIn()
        val old = SampleWeather.NOW.minus(Duration.ofHours(2))
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN), fetchedAt = old)

        for (size in listOf(SizeBucket.sizeOf(columns = 2, rows = 1), SizeF(129f, 95f), flat)) {
            val widget = laidOut(placed(size), size)
            val text = widget.findViewById<ViewGroup>(R.id.now_text)
            val form = widget.findViewById<View>(R.id.short_form)
            val stand = widget.findViewById<View>(R.id.stale)
            val column = widget.findViewById<View>(R.id.now_column)
            assertTrue("$size", WidgetHost.visible(stand, widget))
            // The short form's top and "Stand"'s bottom inside the text area, and the text area inside the card.
            assertTrue("$size ${form.top}", form.top >= 0)
            assertTrue("$size ${stand.bottom} ${text.height}", stand.bottom <= text.height)
            assertEquals("$size", form.height, dp(20))
            assertEquals("$size", stand.height, dp(16))
            val top = column.top + text.top
            assertTrue("$size $top", top >= widget.paddingTop)
            assertTrue("$size", top + text.height <= widget.height - widget.paddingBottom)
        }
        // At 2 x 1 the text has 50 dp between them: it shrinks below its 14 sp.
        val narrow = laidOut(placed(SizeBucket.sizeOf(columns = 2, rows = 1)), SizeBucket.sizeOf(columns = 2, rows = 1))
        assertTrue(narrow.text(R.id.widget_plain).textSize < sp(14f))
        // Without a warning the plain text keeps its size.
        written("""{"schema_version":1,"now":{}}""", fetchedAt = old)
        val alone = laidOut(placed(SizeBucket.sizeOf(columns = 2, rows = 1)), SizeBucket.sizeOf(columns = 2, rows = 1))
        assertEquals(sp(14f), alone.text(R.id.widget_plain).textSize, 0.5f)
        assertEquals(Int.MAX_VALUE, alone.text(R.id.widget_plain).maxLines)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `a flat widget drawn from a plain text to the weather gives its text area back its 71 dp`() {
        signIn()
        val size = SizeF(388f, 95f)
        val id = WidgetHost.place(application, size)
        assertEquals(listOf("Noch keine Daten – zum Öffnen tippen"), WidgetHost.view(application, id).shown())

        written(SampleWeather.full())
        Widgets.update(application)

        val widget = laidOut(WidgetHost.view(application, id), size)
        assertEquals(dp(71), widget.findViewById<View>(R.id.now_text).width)
    }

    private fun assertShortForm(
        widget: View,
        colour: Int,
        symbol: Int,
        palette: Palette = Palette.LIGHT,
    ) {
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.short_form), widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line), widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line_long), widget))
        assertEquals(colour, widget.text(R.id.short_word).currentTextColor)
        assertEquals(tint(colour), widget.findViewById<View>(R.id.short_pill).backgroundTintList?.defaultColor)
        assertEquals(symbol, widget.image(R.id.short_symbol))
        assertEquals(
            PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_ATOP),
            widget.findViewById<ImageView>(R.id.short_symbol).colorFilter,
        )
        assertTrue(colour in listOf(palette.scale2, palette.scale3, palette.scale4, palette.scale5))
    }

    /** The visible children of the card, by id, from the top. */
    private fun shownParts(widget: View): List<Int> {
        val card = widget as ViewGroup
        return (0 until card.childCount)
            .map {
                card.getChildAt(
                    it,
                )
            }.filter { WidgetHost.visible(it, widget) }
            .map { it.id }
    }

    /** The ids of [view] and every view below it, depth first, as they are drawn from the top. */
    private fun drawn(view: View): List<Int> =
        listOf(view.id) +
            (
                (view as? ViewGroup)?.let { group ->
                    (0 until group.childCount).flatMap {
                        drawn(
                            group.getChildAt(it),
                        )
                    }
                }
                    ?: emptyList()
            )

    /** [colour] at 16 %, a warning's ground. */
    private fun tint(colour: Int) = ColorUtils.setAlphaComponent(colour, Math.round(0.16f * 255))

    /** This data with [warnings] added. */
    private fun String.warned(vararg warnings: String) =
        removeSuffix("}") + warnings.joinToString(",", ""","warnings":[""", "]}")

    private companion object {
        const val GUSTS_HEADLINE = "Amtliche WARNUNG vor STURMBÖEN"
        const val ORKAN_HEADLINE = "Amtliche UNWETTERWARNUNG vor ORKANARTIGEN BÖEN"

        /** The sample clock's time [minutes] from now, as the app sensor writes it. */
        fun at(minutes: Long): String =
            OffsetDateTime.ofInstant(SampleWeather.NOW.plusSeconds(minutes * 60), SampleWeather.ZONE).toString()

        val GUSTS = """{"level":2,"type":52,"headline":"$GUSTS_HEADLINE","start":"${at(-60)}","end":"${at(120)}"}"""
        val FROST = """{"level":1,"type":22,"headline":"Amtliche WARNUNG vor FROST","start":"${at(-60)}"}"""
        val ORKAN = """{"level":3,"type":54,"headline":"$ORKAN_HEADLINE","start":"${at(-30)}","end":"${at(90)}"}"""
    }
}
