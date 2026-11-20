package dev.cupr.weather.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import dev.cupr.weather.app.MainActivity
import dev.cupr.weather.app.Services
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.palette.colors
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.TokenCipher
import dev.cupr.weather.tap.TapActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/**
 * The widget "Wetter" on a widget host, from the sign-in and the snapshot the app keeps, at a fixed time and size: what
 * it shows in each size bucket, in which colours, and where a tap goes (D11–D14, D33–D37, D39).
 */
@RunWith(RobolectricTestRunner::class)
class WeatherWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val address = checkNotNull(HaAddress.stored("https://home.example/"))
    private lateinit var keystoreCipher: () -> TokenCipher
    private lateinit var clock: () -> Clock

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        clock = WeatherWidgetProvider.clock
        WeatherWidgetProvider.clock = { SampleWeather.CLOCK }
    }

    @After
    fun tearDown() {
        Services.cipher = keystoreCipher
        WeatherWidgetProvider.clock = clock
    }

    private fun signIn() = Services.account(application).signIn(address, SignInRequest.CLIENT_ID, RefreshToken("r"))

    private fun snapshot(now: String = """{"condition":"partlycloudy","temperature":7.4}""") =
        written("""{"schema_version":1,"now":$now}""")

    private fun written(data: String) =
        Services
            .snapshots(application)
            .write(Snapshot(Instant.EPOCH, SensorData.read(Json.parseToJsonElement(data).jsonObject).readable()))

    private fun SensorData.Read.readable() = (this as SensorData.Read.Readable).data

    private fun placed(size: SizeF? = null): View = WidgetHost.placed(application, size)

    private fun View.text(id: Int): TextView = findViewById(id)

    private fun View.shown(): List<String> = WidgetHost.shown(this)

    /** The drawable resource an image view shows. */
    private fun View.image(id: Int): Int = shadowOf(findViewById<ImageView>(id).drawable).createdFromResId

    /** The activity a tap on the widget starts. */
    private fun View.tapped(): Intent {
        findViewById<View>(R.id.widget).performClick()
        return checkNotNull(shadowOf(application).nextStartedActivity) { "The tap started nothing" }
    }

    @Test
    fun `a small widget shows the icon, the temperature, today's high and low, and the condition`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 2, rows = 2))

        assertEquals(listOf("7°", "9°", "3°", "Teilweise bewölkt"), widget.shown())
        assertEquals(R.drawable.weather_partlycloudy_day, widget.image(R.id.now_icon))
    }

    @Test
    fun `without today among the days, now shows no high and low`() {
        signIn()
        written(SampleWeather.data(""","days":[{"datetime":"2026-11-21T00:00:00+01:00","temperature":3}]"""))

        assertEquals(listOf("7°", "Teilweise bewölkt"), placed(SizeBucket.sizeOf(columns = 2, rows = 2)).shown())
    }

    @Test
    fun `now shows the night form at night and in the morning's twilight, and the day form otherwise`() {
        signIn()
        for ((twilight, icon) in listOf(
            "night" to R.drawable.weather_partlycloudy_night,
            "sunrise" to R.drawable.weather_partlycloudy_night,
            "sunset" to R.drawable.weather_partlycloudy_day,
            "day" to R.drawable.weather_partlycloudy_day,
        )) {
            written(SampleWeather.data().replace(""""twilight":"day"""", """"twilight":"$twilight""""))
            assertEquals(twilight, icon, placed().image(R.id.now_icon))
        }
    }

    @Test
    fun `with rain starting in 25 minutes now shows Regen in 25 min, and with rain falling Regen jetzt`() {
        signIn()
        val small = SizeBucket.sizeOf(columns = 2, rows = 2)

        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))
        val due = placed(small)
        assertEquals(listOf("7°", "9°", "3°", "Regen in 25 min"), due.shown())
        assertEquals(Palette.LIGHT.rain, due.text(R.id.now_line).currentTextColor)

        written(SampleWeather.full(rainIn = Duration.ofMinutes(-5)))
        assertEquals(listOf("7°", "9°", "3°", "Regen jetzt"), placed(small).shown())
    }

    @Test
    fun `a wide widget shows as many hours as fit from the current one, each with time, icon, temperature and rain`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 4, rows = 2))

        assertEquals(
            listOf(
                listOf("13", "7°"),
                listOf("14", "6°"),
                listOf("15", "5°", "0,4 mm"),
                listOf("16", "4°", "< 0,1 mm"),
                listOf("17", "3°"),
            ),
            WidgetHost.hours(widget),
        )
        assertEquals(emptyList<List<String>>(), WidgetHost.days(widget))
        val hours = widget.findViewById<ViewGroup>(R.id.hours)
        assertEquals(
            listOf(
                R.drawable.weather_cloudy_day,
                R.drawable.weather_cloudy_day,
                R.drawable.weather_rainy_day,
                R.drawable.weather_rainy_day,
                R.drawable.weather_cloudy_night,
            ),
            (0 until hours.childCount).map { hours.getChildAt(it).image(R.id.hour_icon) },
        )
    }

    @Test
    fun `a wider widget shows more hours`() {
        signIn()
        written(SampleWeather.full())

        assertEquals(6, WidgetHost.hours(placed(SizeBucket.sizeOf(columns = 5, rows = 2))).size)
    }

    @Test
    fun `a tall widget shows as many days as fit from tomorrow, each with weekday, icon, high and low and rain`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 4, rows = 3))

        assertEquals(5, WidgetHost.hours(widget).size)
        assertEquals(
            listOf(
                listOf("Sa", "10°", "1°"),
                listOf("So", "2,5 mm", "11°", "2°"),
                listOf("Mo", "12°", "3°"),
                listOf("Di", "2,5 mm", "13°", "4°"),
                listOf("Mi", "14°", "5°"),
            ),
            WidgetHost.days(widget),
        )
        val days = widget.findViewById<ViewGroup>(R.id.days)
        assertEquals(R.drawable.weather_sunny_day, days.getChildAt(0).image(R.id.day_icon))
        assertEquals(R.drawable.weather_rainy_day, days.getChildAt(1).image(R.id.day_icon))
    }

    @Test
    fun `a taller widget shows more days, up to all the snapshot has`() {
        signIn()
        written(SampleWeather.full())

        assertEquals(9, WidgetHost.days(placed(SizeBucket.sizeOf(columns = 4, rows = 5))).size)
    }

    @Test
    fun `a widget 3 cells wide and 4 high shows only now`() {
        signIn()
        written(SampleWeather.full())

        val widget = placed(SizeBucket.sizeOf(columns = 3, rows = 4))

        assertEquals(listOf("7°", "9°", "3°", "Teilweise bewölkt"), widget.shown())
    }

    @Test
    fun `signed in, the widget shows the snapshot's temperature and condition`() {
        signIn()
        snapshot()

        assertEquals(listOf("7°", "Teilweise bewölkt"), placed().shown())
    }

    @Test
    fun `every colour the widget shows is one of the palette's outside night mode`() {
        assertColours(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `every colour the widget shows is one of the palette's in night mode`() {
        assertColours(dark = true)
    }

    private fun assertColours(dark: Boolean) {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))
        Services.account(application).reject()

        val widget = placed(SizeBucket.sizeOf(columns = 4, rows = 4))

        val palette = Palette.of(dark)
        val texts = WidgetHost.texts(widget).filter { WidgetHost.visible(it, widget) }
        assertTrue(texts.size > 20)
        val colours = palette.colors().values.toSet()
        for (text in texts) assertTrue("${text.text}", text.currentTextColor in colours)
        assertEquals(palette.text, widget.text(R.id.now_temperature).currentTextColor)
        assertEquals(palette.textMuted, widget.text(R.id.now_low).currentTextColor)
        assertEquals(palette.rain, widget.text(R.id.now_line).currentTextColor)
        assertEquals(palette.accent, widget.text(R.id.widget_notice).currentTextColor)
        assertEquals(palette.surface, application.getColor(R.color.palette_surface))
    }

    @Test
    fun `without a sign-in the widget says so, and a tap opens the sign-in`() {
        val widget = placed()

        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), widget.shown())
        assertEquals(Palette.LIGHT.text, widget.text(R.id.widget_plain).currentTextColor)
        val tap = widget.tapped()
        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertEquals(Intent.ACTION_MAIN, tap.action)
        assertEquals(setOf(Intent.CATEGORY_LAUNCHER), tap.categories)
    }

    @Test
    fun `signed in before the first snapshot, the widget says there is no data yet, and a tap opens the app`() {
        signIn()

        val widget = placed()

        assertEquals(listOf("Noch keine Daten – zum Öffnen tippen"), widget.shown())
        assertEquals(MainActivity::class.java.name, widget.tapped().component?.className)
    }

    @Test
    fun `a tap on a signed-in widget opens the weather pop-up through the tap activity`() {
        signIn()
        snapshot()

        assertEquals(TapActivity::class.java.name, placed().tapped().component?.className)
    }

    @Test
    fun `a refused or unreadable sign-in keeps the weather shown, with Neu anmelden below, and a tap opens the app`() {
        for (refuse in listOf(
            { Services.account(application).reject() },
            { Services.account(application).markUnreadable() },
        )) {
            signIn()
            snapshot()
            refuse()

            val widget = placed()
            assertEquals(listOf("7°", "Teilweise bewölkt", "Neu anmelden"), widget.shown())
            assertEquals(MainActivity::class.java.name, widget.tapped().component?.className)
        }
    }

    @Test
    fun `a refused sign-in that left no snapshot says no one is signed in`() {
        signIn()
        Services.account(application).reject()

        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), placed().shown())
    }

    @Test
    fun `an unknown condition shows no word and the warning sign, and an absent temperature no temperature`() {
        signIn()
        snapshot("""{"condition":"sandstorm","temperature":-0.4}""")
        val unknown = placed()
        assertEquals(listOf("0°"), unknown.shown())
        assertEquals(R.drawable.weather_exceptional_day, unknown.image(R.id.now_icon))

        snapshot("""{"condition":"fog"}""")
        assertEquals(listOf("Nebel"), placed().shown())
    }

    @Test
    fun `a snapshot with neither a temperature nor a condition it knows shows that there is no data yet`() {
        signIn()
        for (now in listOf("{}", """{"condition":"sandstorm"}""", """{"humidity":80}""")) {
            snapshot(now)
            assertEquals(now, listOf("Noch keine Daten – zum Öffnen tippen"), placed().shown())
        }
        written("""{"schema_version":1}""")
        assertEquals(listOf("Noch keine Daten – zum Öffnen tippen"), placed().shown())

        Services.account(application).reject()
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), placed().shown())
    }

    @Test
    fun `a temperature that is not a finite number shows nothing, and the condition still shows`() {
        signIn()
        snapshot("""{"temperature":NaN,"condition":"sunny"}""")

        assertEquals(listOf("Sonnig"), placed().shown())
    }

    @Test
    fun `signed out with a snapshot still on the phone, the widget says no one is signed in`() {
        snapshot()

        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), placed().shown())
    }

    @Test(timeout = 10_000)
    fun `an update waits for one that is reading what to show, so the later one's views come last`() {
        val widget = placed()
        val content = WeatherWidgetProvider.content
        val reading = CountDownLatch(1)
        val gate = CountDownLatch(1)
        // The first update reads the signed-out state, then waits; the sign-in and its snapshot come meanwhile.
        WeatherWidgetProvider.content = { context ->
            content(context).also {
                if (reading.count > 0) {
                    reading.countDown()
                    gate.await()
                }
            }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit { Widgets.update(application) }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            signIn()
            snapshot()
            val second = pool.submit { Widgets.update(application) }
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(second.isDone)
            gate.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
        } finally {
            gate.countDown()
            pool.shutdownNow()
            WeatherWidgetProvider.content = content
        }

        assertEquals(listOf("7°", "Teilweise bewölkt"), widget.shown())
    }

    @Test
    fun `the app's update shows a new snapshot on a widget already placed`() {
        val widget = placed()
        signIn()
        snapshot("""{"condition":"snowy","temperature":-1.6}""")

        Widgets.update(application)

        assertEquals(listOf("-2°", "Schneefall"), widget.shown())
    }

    @Test
    fun `each update hides what the one before showed`() {
        signIn()
        written(SampleWeather.full())
        Services.account(application).reject()
        val widget = placed(SizeBucket.sizeOf(columns = 4, rows = 3))
        // "Neu anmelden" takes the room of one of the five days the widget has without it.
        assertEquals(4, WidgetHost.days(widget).size)
        assertTrue(widget.shown().contains("Neu anmelden"))

        Services.account(application).signOut()
        Services.snapshots(application).clear()
        Widgets.update(application)
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), widget.shown())

        signIn()
        snapshot("""{"temperature":3}""")
        Widgets.update(application)
        assertEquals(listOf("3°"), widget.shown())
    }

    @Test
    fun `the widget is drawn for each size the launcher reports, and without any for its portrait size or 4 x 2`() {
        val portrait = SizeF(300f, 400f)
        val landscape = SizeF(600f, 200f)
        val options =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(portrait, landscape, portrait),
                )
            }
        assertEquals(listOf(portrait, landscape), WeatherWidgetProvider.sizes(options, 1))

        val legacy =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 330)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 500)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            }
        assertEquals(listOf(SizeF(250f, 330f)), WeatherWidgetProvider.sizes(legacy, 1))
        assertEquals(listOf(SizeF(276f, 220f)), WeatherWidgetProvider.sizes(Bundle(), 1))
        assertEquals(listOf(SizeF(276f, 220f)), WeatherWidgetProvider.sizes(null, 1))
    }

    @Test
    fun `the size buckets follow the cells, a widget 3 cells wide or less staying small`() {
        val buckets =
            listOf(2 to 2, 3 to 2, 3 to 4, 3 to 5, 4 to 1, 4 to 2, 5 to 2, 4 to 3, 4 to 5, 5 to 4)
                .associateWith { (columns, rows) -> SizeBucket.of(SizeBucket.sizeOf(columns, rows)) }

        assertEquals(
            mapOf(
                (2 to 2) to SizeBucket.SMALL,
                (3 to 2) to SizeBucket.SMALL,
                (3 to 4) to SizeBucket.SMALL,
                (3 to 5) to SizeBucket.SMALL,
                (4 to 1) to SizeBucket.SMALL,
                (4 to 2) to SizeBucket.WIDE,
                (5 to 2) to SizeBucket.WIDE,
                (4 to 3) to SizeBucket.TALL,
                (4 to 5) to SizeBucket.TALL,
                (5 to 4) to SizeBucket.TALL,
            ),
            buckets,
        )
        // Just below 4 cells wide or 2 and 3 high, by a dp.
        assertEquals(SizeBucket.SMALL, SizeBucket.of(SizeF(275f, 400f)))
        assertEquals(SizeBucket.SMALL, SizeBucket.of(SizeF(300f, 219f)))
        assertEquals(SizeBucket.WIDE, SizeBucket.of(SizeF(300f, 337f)))
    }

    @Test
    fun `the widget is named Wetter, added at 4 x 2 cells, down to 2 x 2, with a preview`() {
        val receiver =
            application.packageManager.getReceiverInfo(
                ComponentName(application, WeatherWidgetProvider::class.java),
                PackageManager.GET_META_DATA,
            )
        assertEquals("Wetter", receiver.loadLabel(application.packageManager).toString())
        assertEquals(R.xml.widget_weather_info, receiver.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))

        val attributes = providerAttributes()
        assertEquals("4", attributes["targetCellWidth"])
        assertEquals("2", attributes["targetCellHeight"])
        assertEquals("110.0dip", attributes["minResizeWidth"])
        assertEquals("110.0dip", attributes["minResizeHeight"])
        assertEquals("@${R.drawable.widget_preview}", attributes["previewImage"])
        assertEquals("@${R.layout.widget_weather_preview}", attributes["previewLayout"])
        assertEquals("@${R.layout.widget_weather}", attributes["initialLayout"])
    }

    @Test
    fun `the conditions' words are German, as Home Assistant shows them`() {
        val words =
            listOf(
                "clear-night" to "Klare Nacht",
                "cloudy" to "Bewölkt",
                "exceptional" to "Außergewöhnlich",
                "fog" to "Nebel",
                "hail" to "Hagel",
                "lightning" to "Gewitter",
                "lightning-rainy" to "Gewitter, regnerisch",
                "partlycloudy" to "Teilweise bewölkt",
                "pouring" to "Strömender Regen",
                "rainy" to "Regnerisch",
                "snowy" to "Schneefall",
                "snowy-rainy" to "Schneeregen",
                "sunny" to "Sonnig",
                "windy" to "Windig",
                "windy-variant" to "Windig, bewölkt",
            )

        for ((condition, word) in words) {
            assertEquals(condition, word, application.getString(checkNotNull(Conditions.word(condition))))
        }
        assertNull(Conditions.word("sandstorm"))
    }

    /** The provider info's attributes in the android namespace, as the compiled XML holds them. */
    private fun providerAttributes(): Map<String, String> {
        application.resources.getXml(R.xml.widget_weather_info).use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) continue
            return (0 until xml.attributeCount).associate { xml.getAttributeName(it) to xml.getAttributeValue(it) }
        }
    }

    private companion object {
        /** Long enough for the second update to reach the lock, were there none. */
        const val SETTLE_MILLIS = 300L
    }
}
