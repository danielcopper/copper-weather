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

        // A tile, no word (D47).
        val strip = placed(flat)
        assertEquals(listOf("7°", "9°", "3°"), strip.shown().take(3))
        assertShortForm(strip, Palette.LIGHT.scale3, R.drawable.tile_wind)

        val square = placed(small)
        assertEquals(listOf("7°", "9°", "3°"), square.shown())
        assertShortForm(square, Palette.LIGHT.scale3, R.drawable.tile_wind)
        assertFalse(WidgetHost.visible(square.findViewById(R.id.band), square))
    }

    @Test
    fun `two warnings that apply show the higher level and +1 in the band, a tile each in the short form`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN))

        val band = placed(wide)
        assertEquals(listOf(ORKAN_HEADLINE, "+1"), band.shown().take(2))
        assertEquals(Palette.LIGHT.scale4, band.text(R.id.band_more).currentTextColor)
        assertEquals(tint(Palette.LIGHT.scale4), band.findViewById<View>(R.id.band).backgroundTintList?.defaultColor)

        // The short form shows a tile for each, each in its own level's colour, no word and no "+1" (D47).
        for (size in listOf(flat, small, SizeBucket.sizeOf(columns = 2, rows = 1))) {
            val widget = placed(size)
            assertFalse("$size", widget.shown().any { it == "+1" || it == "Unwetter" || it == "Warnung" })
            assertEquals("$size", listOf(R.drawable.tile_wind, R.drawable.tile_frost), tiles(widget))
            val (orkan, frost) = tileViews(widget)
            assertTile(orkan, Palette.LIGHT.scale4)
            assertTile(frost, Palette.LIGHT.scale2)
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
    fun `a band without a headline shows its level's word and its symbol`() {
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
        assertShortForm(placed(flat), Palette.DARK.scale4, R.drawable.tile_wind, palette = Palette.DARK)
    }

    @Test
    fun `each level takes its colour of the intensity scale`() {
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
            assertShortForm(placed(small), colour, R.drawable.tile_frost)
        }
    }

    @Test
    fun `in the flat bucket the short form takes the line's place before Neu anmelden`() {
        signIn()
        written(SampleWeather.full().warned(GUSTS))
        Services.account(application).reject()

        val widget = placed(flat)

        assertTrue(WidgetHost.visible(widget.findViewById(R.id.short_form), widget))
        assertEquals(listOf("7°", "9°", "3°"), widget.shown().take(3))
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
            assertShortForm(widget, Palette.LIGHT.scale4, R.drawable.tile_wind)
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
            // One tile, 28 dp (D47).
            assertEquals("$size", dp(28), form.height)
            assertEquals("$size", dp(16), stand.height)
            val top = column.top + text.top
            assertTrue("$size $top", top >= widget.paddingTop)
            assertTrue("$size", top + text.height <= widget.height - widget.paddingBottom)
        }
        // At 2 x 1 the text has 42 dp between them: it shrinks below its 14 sp.
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
    fun `in a wide low row the plain text leaves Stand whole though the tiles are lower than a line`() {
        signIn()
        val old = SampleWeather.NOW.minus(Duration.ofHours(2))
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN), fetchedAt = old)
        // 66 dp high the tiles fall below the line's 20 dp, which the short-form slot keeps as its least height (D47).
        val size = SizeF(388f, 66f)
        val widget = laidOut(placed(size), size)
        val text = widget.findViewById<ViewGroup>(R.id.now_text)
        val form = widget.findViewById<View>(R.id.short_form)
        val stand = widget.findViewById<View>(R.id.stale)
        assertTrue(WidgetHost.visible(stand, widget))
        assertTrue("${form.top}", form.top >= 0)
        assertTrue("${stand.bottom} ${text.height}", stand.bottom <= text.height)
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

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `in a row too low for the plain text at 9 sp it ends in an ellipsis in the lines that fit`() {
        signIn()
        written(
            """{"schema_version":1,"now":{}}""".warned(ORKAN),
            fetchedAt = SampleWeather.NOW.minus(Duration.ofHours(2)),
        )
        // 54 dp inside, 18 dp left between the short form and "Stand".
        val size = SizeF(129f, 70f)
        val id = WidgetHost.place(application, size)

        val widget = laidOut(WidgetHost.view(application, id), size)
        val plain = widget.text(R.id.widget_plain)
        assertEquals(sp(9f), plain.textSize, 0.5f)
        assertEquals(1, plain.maxLines)
        assertTrue(plain.layout.getEllipsisCount(plain.lineCount - 1) > 0)
        val text = widget.findViewById<View>(R.id.now_text)
        assertTrue(
            "${widget.findViewById<View>(R.id.stale).bottom} ${text.height}",
            widget.findViewById<View>(R.id.stale).bottom <= text.height,
        )
        assertTrue(text.height <= widget.height - widget.paddingTop - widget.paddingBottom)

        // Drawn again without a warning, the same view takes its own size and lines back.
        written("""{"schema_version":1,"now":{}}""")
        Widgets.update(application)
        val alone = WidgetHost.view(application, id).text(R.id.widget_plain)
        assertEquals(sp(14f), alone.textSize, 0.5f)
        assertEquals(Int.MAX_VALUE, alone.maxLines)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `one row high a plain text with a warning but no Stand, or with neither, keeps its 14 sp and its lines`() {
        signIn()
        written("""{"schema_version":1,"now":{}}""".warned(ORKAN))
        for (size in listOf(flat, SizeBucket.sizeOf(columns = 2, rows = 1))) {
            val plain = laidOut(placed(size), size).text(R.id.widget_plain)
            assertEquals("$size", sp(14f), plain.textSize, 0.5f)
            assertEquals("$size", Int.MAX_VALUE, plain.maxLines)
        }

        written("""{"schema_version":1,"now":{}}""")
        val plain = laidOut(placed(flat), flat).text(R.id.widget_plain)
        assertEquals(sp(14f), plain.textSize, 0.5f)
        assertEquals(Int.MAX_VALUE, plain.maxLines)
    }

    /** The short form's tiles, its kinds' in order (D47). */
    private fun tileViews(widget: View): List<ImageView> {
        val strip = widget.findViewById<ViewGroup>(R.id.short_tiles)
        return (0 until strip.childCount).map { strip.getChildAt(it) as ImageView }
    }

    private fun tiles(widget: View): List<Int> =
        tileViews(widget).map {
            org.robolectric.Shadows
                .shadowOf(it.drawable)
                .createdFromResId
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the tiles take the largest side that fits the flat text area, the small line and 2 x 1, each square`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN))
        val narrow = SizeBucket.sizeOf(columns = 2, rows = 1)
        // Two tiles: 28 dp in the flat strip's 71 dp and the small line's 106 dp, 21 dp in the 47 dp at 2 x 1.
        for ((size, side) in listOf(SizeF(388f, 95f) to 28, small to 28, narrow to 21)) {
            val widget = laidOut(placed(size), size)
            val tiles = tileViews(widget)
            assertEquals("$size", 2, tiles.size)
            for (tile in tiles) assertEquals("$size", listOf(dp(side), dp(side)), listOf(tile.width, tile.height))
            val strip = widget.findViewById<View>(R.id.short_tiles)
            assertTrue("$size ${strip.width}", strip.width <= widget.findViewById<View>(R.id.short_form).width)
            assertEquals("$size", dp(3), tiles[1].left - tiles[0].right)
        }
        // In a row lower than the guidance's, 54 dp inside, the tiles leave the temperature and today's line their
        // rows: 8 dp would be left, so they take their least side, 18 dp.
        val low = SizeF(388f, 70f)
        assertEquals(listOf(dp(18), dp(18)), tileViews(laidOut(placed(low), low)).map { it.height })
        // One warning: one tile, 28 dp.
        written(SampleWeather.full().warned(ORKAN))
        val one = tileViews(laidOut(placed(narrow), narrow))
        assertEquals(listOf(dp(28)), one.map { it.width })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `more kinds than fit show as many tiles as fit and +N in the highest level's colour`() {
        signIn()
        val ice = """{"level":1,"type":84,"start":"${at(-60)}"}"""
        val frost = """{"level":1,"type":22,"start":"${at(-60)}"}"""
        val wind = """{"level":2,"type":52,"start":"${at(-60)}"}"""
        val rain = """{"level":1,"type":61,"start":"${at(-60)}"}"""
        val narrow = SizeBucket.sizeOf(columns = 2, rows = 1)

        // The owner's case, ice and frost: both tiles everywhere, no "+N".
        written(SampleWeather.full().warned(ice, frost))
        for (size in listOf(flat, small, narrow)) {
            val widget = placed(size)
            assertEquals("$size", listOf(R.drawable.tile_ice, R.drawable.tile_frost), tiles(widget))
            assertFalse("$size", widget.shown().any { it.startsWith("+") })
        }

        // Three kinds: all three in the flat strip and the small line, the highest level's first; at 2 x 1 one tile
        // and "+2".
        written(SampleWeather.full().warned(ice, frost, wind))
        val three = listOf(R.drawable.tile_wind, R.drawable.tile_ice, R.drawable.tile_frost)
        for (size in listOf(flat, small)) assertEquals("$size", three, tiles(placed(size)))
        val two = placed(narrow)
        assertEquals(listOf(R.drawable.tile_wind), tiles(two))
        assertEquals("+2", two.text(R.id.short_more).text.toString())
        assertEquals(Palette.LIGHT.scale3, two.text(R.id.short_more).currentTextColor)

        // Four: in the flat strip two and "+2", in the small line all four.
        written(SampleWeather.full().warned(ice, frost, wind, rain))
        val strip = laidOut(placed(flat), flat)
        assertEquals(2, tiles(strip).size)
        assertEquals("+2", strip.text(R.id.short_more).text.toString())
        val more = strip.text(R.id.short_more)
        assertTrue(more.paint.measureText("+2") <= more.width)
        assertEquals(4, tiles(placed(small)).size)
    }

    @Test
    fun `two warnings of the same kind show one tile and no +1 in the short form`() {
        signIn()
        written(SampleWeather.full().warned(GUSTS, ORKAN))

        for (size in listOf(flat, small)) {
            val widget = placed(size)
            assertEquals("$size", listOf(R.drawable.tile_wind), tiles(widget))
            assertFalse("$size", widget.shown().contains("+1"))
            // The tile in the higher level's colour of the two.
            assertTile(tileViews(widget).single(), Palette.LIGHT.scale4)
        }
        // The band still counts the other warning.
        assertEquals(listOf(ORKAN_HEADLINE, "+1"), placed(wide).shown().take(2))
    }

    @Test
    fun `a composite symbol keeps its width in the band, and its tile in the short form is square`() {
        signIn()
        written(SampleWeather.full().warned("""{"level":1,"type":82}"""))
        val frost = checkNotNull(application.getDrawable(R.drawable.symbol_frost))
        val aspect = frost.intrinsicWidth.toFloat() / frost.intrinsicHeight

        val tile = tileViews(placed(small)).single().layoutParams
        assertEquals(tile.width, tile.height)
        val band = placed(wide).findViewById<View>(R.id.band_symbol).layoutParams
        assertEquals(dp(16) * aspect, band.width.toFloat(), 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+xxhdpi")
    fun `the tiles stand centred in the small widget's line as in the flat strip`() {
        signIn()
        written(SampleWeather.full().warned(FROST, ORKAN))

        for (size in listOf(small, flat)) {
            val widget = laidOut(placed(size), size)
            val slot = widget.findViewById<View>(R.id.short_form)
            val strip = widget.findViewById<View>(R.id.short_tiles)
            assertEquals("$size", (slot.width - strip.width) / 2f, strip.left.toFloat(), 1f)
        }
    }

    @Test
    fun `each tile tells a screen reader its level's word and its kind, and +N how many more`() {
        signIn()
        written(SampleWeather.full().warned(ORKAN, FROST))
        val descriptions = { size: SizeF -> tileViews(placed(size)).map { it.contentDescription.toString() } }

        assertEquals(listOf("Unwetter: Sturm", "Warnung: Frost"), descriptions(small))
        assertEquals(listOf("Unwetter: Sturm", "Warnung: Frost"), descriptions(flat))
        assertTrue(tileViews(placed(flat)).all { it.isImportantForAccessibility })
        written(SampleWeather.full().warned("""{"level":1,"type":84}""", """{"level":2,"type":98}"""))
        // The higher level's kind first; a kind without a word of its own reads as its level's word alone.
        assertEquals(listOf("Warnung", "Warnung: Glätte"), descriptions(small))

        val five =
            listOf(84, 22, 52, 61, 59).map { """{"level":1,"type":$it,"start":"${at(-60)}"}""" }.toTypedArray()
        written(SampleWeather.full().warned(*five))
        val strip = placed(flat)
        val more = strip.text(R.id.short_more).text.toString()
        assertTrue(more, more.startsWith("+") && more.drop(1).toInt() >= 2)
        assertEquals("und ${more.drop(1)} weitere", strip.text(R.id.short_more).contentDescription.toString())
        val one = application.resources.getQuantityString(R.plurals.warning_more_description, 1, 1)
        assertEquals("und eine weitere", one)
    }

    /** The short form shows in the line's place, its first tile [tile] in [colour] on its tint (D47). */
    private fun assertShortForm(
        widget: View,
        colour: Int,
        tile: Int,
        palette: Palette = Palette.LIGHT,
    ) {
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.short_form), widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line), widget))
        assertFalse(WidgetHost.visible(widget.findViewById(R.id.now_line_long), widget))
        assertEquals(tile, tiles(widget).first())
        assertTile(tileViews(widget).first(), colour)
        assertTrue(colour in listOf(palette.scale2, palette.scale3, palette.scale4, palette.scale5))
    }

    /** A tile in [colour] on [colour] at 16 %. */
    private fun assertTile(
        tile: ImageView,
        colour: Int,
    ) {
        assertEquals(PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_ATOP), tile.colorFilter)
        assertEquals(tint(colour), tile.backgroundTintList?.defaultColor)
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
