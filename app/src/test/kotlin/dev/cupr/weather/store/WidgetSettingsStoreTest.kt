package dev.cupr.weather.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/** Each widget's settings, kept by its id (D44). */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val store = WidgetSettingsStore(prefs)

    @Test
    fun `a widget without settings animates only now, shows the days one row high and a fully opaque card`() {
        assertEquals(WidgetSettings(Animation.NOW, FlatStrip.DAYS, 100), store.read(7))
        assertEquals(WidgetSettings.DEFAULT, store.read(7))
    }

    @Test
    fun `each widget's settings read back as written, under their own fixed names`() {
        store.write(7, WidgetSettings(Animation.ALL, FlatStrip.HOURS, 30))
        store.write(8, WidgetSettings(Animation.OFF, FlatStrip.DAYS, 0))

        assertEquals(WidgetSettings(Animation.ALL, FlatStrip.HOURS, 30), store.read(7))
        assertEquals(WidgetSettings(Animation.OFF, FlatStrip.DAYS, 0), store.read(8))
        assertEquals("all", prefs.getString("7.animation", null))
        assertEquals("hours", prefs.getString("7.flat_strip", null))
        assertEquals(30, prefs.getInt("7.opacity", -1))
        assertEquals(listOf("off", "now", "all"), Animation.entries.map { it.stored })
        assertEquals(listOf("days", "hours"), FlatStrip.entries.map { it.stored })
    }

    @Test
    fun `a removed widget's settings go, the others stay`() {
        store.write(7, WidgetSettings(Animation.ALL, FlatStrip.HOURS, 30))
        store.write(8, WidgetSettings(Animation.OFF, FlatStrip.HOURS, 50))

        store.remove(intArrayOf(7))

        assertEquals(WidgetSettings.DEFAULT, store.read(7))
        assertFalse(prefs.all.keys.any { it.startsWith("7.") })
        assertEquals(WidgetSettings(Animation.OFF, FlatStrip.HOURS, 50), store.read(8))
    }

    @Test
    fun `a stored name this app does not know and an opacity off the slider read as the defaults, logged`() {
        prefs.edit {
            putString("7.animation", "some")
            putString("7.flat_strip", "weeks")
            putInt("7.opacity", 35)
            putString("8.opacity", "full")
            putInt("9.opacity", 110)
            putInt("10.opacity", -10)
        }

        assertEquals(WidgetSettings.DEFAULT, store.read(7))
        assertEquals(WidgetSettings.DEFAULT, store.read(8))
        assertEquals(WidgetSettings.DEFAULT, store.read(9))
        assertEquals(WidgetSettings.DEFAULT, store.read(10))
        val logs = ShadowLog.getLogsForTag("WidgetSettings").joinToString { it.msg }
        assertTrue(logs, logs.contains("Widget 7: its stored animation \"some\" is unknown; it reads as now"))
        assertTrue(logs, logs.contains("Widget 7: its stored flat_strip \"weeks\" is unknown; it reads as days"))
        assertTrue(logs, logs.contains("Widget 7: its stored opacity is no step of the slider; it reads as 100"))
        assertTrue(logs, logs.contains("Widget 8: its stored opacity is no step of the slider"))
        assertTrue(logs, logs.contains("Widget 9: its stored opacity is no step of the slider"))
        assertTrue(logs, logs.contains("Widget 10: its stored opacity is no step of the slider; it reads as 100"))
    }
}
