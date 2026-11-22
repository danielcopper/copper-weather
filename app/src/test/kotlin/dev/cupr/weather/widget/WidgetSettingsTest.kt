package dev.cupr.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.setup.SetupActivity
import dev.cupr.weather.store.Animation
import dev.cupr.weather.store.FlatStrip
import dev.cupr.weather.store.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget as its settings draw it (D44–D46): which icons move, the days or the hours one row high, the card's
 * opacity, each widget by its own settings, kept until it is removed.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsTest : WidgetTestBase() {
    private val tall = SizeBucket.sizeOf(columns = 4, rows = 3)
    private val flat = SizeF(388f, 95f)

    @Test
    fun `a widget without settings animates only now, shows the days one row high and a fully opaque card`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(tall)

        assertEquals(R.drawable.weather_partlycloudy_day_animated, widget.icon(R.id.now_icon))
        assertEquals(R.drawable.weather_cloudy_day, icons(widget, R.id.hours, R.id.hour_icon).first())
        assertEquals(R.drawable.weather_sunny_day, icons(widget, R.id.days, R.id.day_icon).first())
        assertEquals(listOf(false), (hours(widget) + days(widget)).map(::animated).distinct())
        assertEquals(Palette.LIGHT.surface, card(widget))
        val strip = placed(flat)
        assertEquals(5, WidgetHost.days(strip).size)
        assertEquals(emptyList<List<String>>(), WidgetHost.hours(strip))
        assertEquals(Palette.LIGHT.surface, card(strip))
    }

    @Test
    fun `off shows every icon still, only now animates now alone, all animates now, the hours and the days`() {
        signIn()
        written(SampleWeather.full())
        val still = R.drawable.weather_partlycloudy_day
        val moving = R.drawable.weather_partlycloudy_day_animated
        val hour = R.drawable.weather_cloudy_day
        val day = R.drawable.weather_sunny_day
        val movingHour = R.drawable.weather_cloudy_day_animated
        val movingDay = R.drawable.weather_sunny_day_animated
        for ((animation, expected) in listOf(
            Animation.OFF to listOf(still, hour, day),
            Animation.NOW to listOf(moving, hour, day),
            Animation.ALL to listOf(moving, movingHour, movingDay),
        )) {
            val widget = placed(WidgetSettings(animation = animation), tall)

            val hours = hours(widget)
            val days = days(widget)
            assertEquals("$animation", expected, listOf(widget.icon(R.id.now_icon), hours.first(), days.first()))
            assertEquals("$animation", listOf(5, 5), listOf(hours.size, days.size))
            val all = animation == Animation.ALL
            assertEquals("$animation", listOf(all), (hours + days).map(::animated).distinct())
        }
        // One row high, the days and the hours move with all, and stay still otherwise.
        for (strip in FlatStrip.entries) {
            val (list, image) =
                if (strip == FlatStrip.DAYS) R.id.days to R.id.flat_day_icon else R.id.hours to R.id.hour_icon
            for (animation in Animation.entries) {
                val widget = placed(WidgetSettings(animation = animation, flatStrip = strip), flat)
                val icons = icons(widget, list, image)
                assertTrue("$strip $animation", icons.isNotEmpty())
                assertEquals("$strip $animation", listOf(animation == Animation.ALL), icons.map(::animated).distinct())
                assertEquals(animation != Animation.OFF, widget.icon(R.id.now_icon) == moving)
            }
        }
    }

    @Test
    fun `changing a widget's settings draws that widget anew, and no other`() {
        signIn()
        written(SampleWeather.full())
        val first = WidgetHost.place(application, tall)
        val second = WidgetHost.place(application, tall)
        // Both set to still images, but stored without drawing: a widget shows them once it is drawn anew.
        val store = Services.widgetSettings(application)
        store.write(first, WidgetSettings(animation = Animation.OFF))
        store.write(second, WidgetSettings(animation = Animation.OFF))

        assertTrue(Widgets.saved(application, first))

        val redrawn = WidgetHost.view(application, first)
        assertEquals(R.drawable.weather_partlycloudy_day, redrawn.icon(R.id.now_icon))
        val kept = WidgetHost.view(application, second)
        assertEquals(R.drawable.weather_partlycloudy_day_animated, kept.icon(R.id.now_icon))
    }

    @Test
    fun `one row high a widget set to the hours shows the hours that fit beside now instead of the days`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(WidgetSettings(flatStrip = FlatStrip.HOURS), flat)

        assertEquals(emptyList<List<String>>(), WidgetHost.days(widget))
        assertEquals(
            listOf(
                listOf("13", "7°"),
                listOf("14", "6°"),
                listOf("15", "5°", "0,4 mm"),
                listOf("16", "4°", "< 0,1 mm"),
            ),
            WidgetHost.hours(widget),
        )
        assertTrue(WidgetHost.visible(widget.findViewById(R.id.divider), widget))
        assertEquals(dp(140), widget.findViewById<View>(R.id.now_column).layoutParams.width)
        assertEquals(dp(66), widget.iconSlot(R.id.now_icon).layoutParams.width)
        // Each hour at least 48 dp wide in the room the days have: 172, 209, 210, 211 and 233 dp.
        val sizes = listOf(SizeF(349f, 102f), SizeF(386f, 95f), SizeF(387f, 95f), flat, SizeF(410f, 95f))
        val hours = WidgetSettings(flatStrip = FlatStrip.HOURS)
        assertEquals(listOf(3, 4, 4, 4, 4), sizes.map { WidgetHost.hours(placed(hours, it)).size })
        // Below 4 cells wide neither shows, also 3 cells wide on a phone with wider cells, where an hour would fit
        // (D11), and "now" takes the card's width.
        for (size in listOf(SizeBucket.sizeOf(columns = 2, rows = 1), SizeF(272f, 100f))) {
            val narrow = placed(hours, size)
            assertEquals("$size", emptyList<List<String>>(), WidgetHost.hours(narrow))
            val column = narrow.findViewById<View>(R.id.now_column).layoutParams.width
            assertEquals("$size", ViewGroup.LayoutParams.MATCH_PARENT, column)
        }
    }

    @Test
    fun `the flat hours are drawn anew on each update, and give way to a plain text`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application)
        Services.widgetSettings(application).write(id, WidgetSettings(flatStrip = FlatStrip.HOURS))
        WidgetHost.resize(application, id, flat)

        // A launcher applies an update onto the views of the one before: the hours are not added twice.
        assertTrue(Widgets.saved(application, id))
        assertEquals(4, WidgetHost.hours(WidgetHost.view(application, id)).size)
        Widgets.update(application)
        assertEquals(4, WidgetHost.hours(WidgetHost.view(application, id)).size)

        Services.account(application).signOut()
        Widgets.update(application)
        val plain = WidgetHost.view(application, id)
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), plain.shown())
        assertEquals(emptyList<List<String>>(), WidgetHost.hours(plain))
        assertFalse(plain.findViewById<View>(R.id.hours).visibility == View.VISIBLE)
    }

    @Test
    fun `the card takes the set opacity, none at 0 percent, while the divider, texts and icons stay opaque`() {
        assertOpacity(Palette.LIGHT)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the card takes the set opacity of the dark surface`() {
        assertOpacity(Palette.DARK)
    }

    @Test
    fun `a widget removed from the home screen takes its settings with it, the others keep theirs`() {
        val store = Services.widgetSettings(application)
        store.write(7, WidgetSettings(animation = Animation.ALL))
        store.write(8, WidgetSettings(animation = Animation.OFF))

        WeatherWidgetProvider().onDeleted(application, intArrayOf(7))

        assertEquals(WidgetSettings.DEFAULT, store.read(7))
        assertEquals(WidgetSettings(animation = Animation.OFF), store.read(8))
    }

    @Test
    fun `a placed widget draws at once, its configuration screen opens only from the launcher's settings`() {
        val parser = application.resources.getXml(R.xml.widget_weather_info)
        while (parser.name != "appwidget-provider") parser.next()
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals(SetupActivity::class.java.name, parser.getAttributeValue(android, "configure"))
        val features = parser.getAttributeIntValue(android, "widgetFeatures", 0)
        val optional = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
        assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE or optional, features)
        val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setPackage(application.packageName)
        val found = application.packageManager.queryIntentActivities(configure, 0).map { it.activityInfo.name }
        assertEquals(listOf(SetupActivity::class.java.name), found)
    }

    /**
     * The card is [palette]'s surface at each opacity, while every shown text, the divider's colour and every icon,
     * "now"'s, the hours' and the days', stay fully opaque.
     */
    private fun assertOpacity(palette: Palette) {
        // A plain text stands on the card at its opacity too.
        val plain = placed(WidgetSettings(opacity = 30), tall)
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), plain.shown())
        assertEquals(ColorUtils.setAlphaComponent(palette.surface, 77), card(plain))
        signIn()
        written(SampleWeather.full())
        for (opacity in listOf(0, 30, 70, 100)) {
            val settings = WidgetSettings(opacity = opacity)
            val alpha = Math.round(opacity * 255f / 100)
            for (size in listOf(tall, flat)) {
                val widget = placed(settings, size)

                assertEquals("$opacity", ColorUtils.setAlphaComponent(palette.surface, alpha), card(widget))
                assertEquals(1f, widget.alpha)
                for (text in WidgetHost.texts(widget).filter { WidgetHost.visible(it, widget) }) {
                    assertEquals("${text.text}", 255, Color.alpha(text.currentTextColor))
                }
                assertEquals(1f, widget.iconSlot(R.id.now_icon).alpha)
                val day = if (size == flat) R.id.flat_day_icon else R.id.day_icon
                for ((list, image) in listOf(R.id.hours to R.id.hour_icon, R.id.days to day)) {
                    val rows = widget.findViewById<ViewGroup>(list)
                    for (row in (0 until rows.childCount).map { rows.getChildAt(it) }) {
                        assertEquals(1f, row.iconSlot(image).alpha)
                    }
                }
            }
            val divider = placed(settings, flat).findViewById<View>(R.id.divider)
            assertEquals(palette.border, (divider.background as ColorDrawable).color)
        }
    }

    /** A widget placed at [size] with [settings] stored for it before it is drawn. */
    private fun placed(
        settings: WidgetSettings,
        size: SizeF,
    ): View {
        val id = WidgetHost.place(application)
        Services.widgetSettings(application).write(id, settings)
        return WidgetHost.resize(application, id, size)
    }

    /** The card's colour: the palette's surface as the widget's settings tint it. */
    private fun card(widget: View): Int =
        checkNotNull(widget.findViewById<View>(R.id.widget).backgroundTintList).defaultColor

    private fun hours(widget: View) = icons(widget, R.id.hours, R.id.hour_icon)

    private fun days(widget: View) = icons(widget, R.id.days, R.id.day_icon)

    /** Whether [icon] is an animated icon (D43). */
    private fun animated(icon: Int) = application.resources.getResourceEntryName(icon).endsWith("_animated")

    /** The drawable each row of the list [list] shows in the icon of [image]. */
    private fun icons(
        widget: View,
        list: Int,
        image: Int,
    ): List<Int> {
        val rows = widget.findViewById<ViewGroup>(list)
        return (0 until rows.childCount).map { rows.getChildAt(it).icon(image) }
    }
}
