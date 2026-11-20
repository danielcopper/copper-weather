package dev.cupr.weather.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
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
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.TokenCipher
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
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/**
 * The widget "Wetter" on a widget host, from the sign-in and the snapshot the app keeps: what it shows, in which
 * colours, and where a tap goes (D33, D34).
 */
@RunWith(RobolectricTestRunner::class)
class WeatherWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val address = checkNotNull(HaAddress.stored("https://home.example/"))
    private lateinit var keystoreCipher: () -> TokenCipher

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
    }

    @After
    fun tearDown() {
        Services.cipher = keystoreCipher
    }

    private fun signIn() = Services.account(application).signIn(address, SignInRequest.CLIENT_ID, RefreshToken("r"))

    private fun snapshot(now: String = """{"condition":"partlycloudy","temperature":7.4}""") =
        Services
            .snapshots(application)
            .write(
                Snapshot(
                    Instant.EPOCH,
                    SensorData(Json.parseToJsonElement("""{"schema_version":1,"now":$now}""").jsonObject),
                ),
            )

    private fun placed(): View = WidgetHost.placed(application)

    private fun View.text(id: Int): TextView = findViewById(id)

    private fun View.shown(): List<String> = WidgetHost.shown(this)

    /** The activity a tap on the widget starts. */
    private fun View.tapped(): Intent {
        findViewById<View>(R.id.widget).performClick()
        return checkNotNull(shadowOf(application).nextStartedActivity) { "The tap started nothing" }
    }

    @Test
    fun `signed in, the widget shows the snapshot's temperature and condition`() {
        signIn()
        snapshot()

        val widget = placed()

        assertEquals(listOf("7°", "Teilweise bewölkt"), widget.shown())
    }

    @Test
    fun `the texts are the palette's text on the card outside night mode`() {
        assertColours(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the texts are the dark palette's text on the card in night mode`() {
        assertColours(dark = true)
    }

    private fun assertColours(dark: Boolean) {
        signIn()
        snapshot()
        Services.account(application).reject()

        val widget = placed()

        val palette = Palette.of(dark)
        assertEquals(palette.text, widget.text(R.id.widget_temperature).currentTextColor)
        assertEquals(palette.text, widget.text(R.id.widget_condition).currentTextColor)
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
    fun `with the weather shown, a tap opens the app's screen too`() {
        signIn()
        snapshot()

        assertEquals(MainActivity::class.java.name, placed().tapped().component?.className)
    }

    @Test
    fun `a refused or unreadable sign-in keeps the weather shown, with Neu anmelden below`() {
        for (refuse in listOf(
            { Services.account(application).reject() },
            { Services.account(application).markUnreadable() },
        )) {
            signIn()
            snapshot()
            refuse()

            assertEquals(listOf("7°", "Teilweise bewölkt", "Neu anmelden"), placed().shown())
        }
    }

    @Test
    fun `a refused sign-in that left no snapshot says no one is signed in`() {
        signIn()
        Services.account(application).reject()

        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), placed().shown())
    }

    @Test
    fun `an unknown condition shows nothing, and an absent temperature neither`() {
        signIn()
        snapshot("""{"condition":"sandstorm","temperature":-0.4}""")
        assertEquals(listOf("0°"), placed().shown())

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
        Services
            .snapshots(
                application,
            ).write(Snapshot(Instant.EPOCH, SensorData(Json.parseToJsonElement("""{"schema_version":1}""").jsonObject)))
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

        assertEquals(listOf("-2°", "Schnee"), widget.shown())
    }

    @Test
    fun `each update hides what the one before showed`() {
        signIn()
        snapshot()
        Services.account(application).reject()
        val widget = placed()
        assertEquals(listOf("7°", "Teilweise bewölkt", "Neu anmelden"), widget.shown())

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
    fun `the temperature is in whole degrees, a half toward positive infinity, as the dashboard rounds`() {
        val shown =
            listOf(7.4, 7.5, 2.5, -2.5, -2.6, -2.4, -0.4, -0.5, -0.0, 0.0, 12.0, 30.49)
                .associateWith(WidgetContent::degrees)

        assertEquals(
            mapOf(
                7.4 to "7°",
                7.5 to "8°",
                2.5 to "3°",
                -2.5 to "-2°",
                -2.6 to "-3°",
                -2.4 to "-2°",
                -0.4 to "0°",
                -0.5 to "0°",
                -0.0 to "0°",
                0.0 to "0°",
                12.0 to "12°",
                30.49 to "30°",
            ),
            shown,
        )
    }

    @Test
    fun `each condition is a German word, as Home Assistant shows it`() {
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
                "pouring" to "Strömend",
                "rainy" to "Regnerisch",
                "snowy" to "Schnee",
                "snowy-rainy" to "Schnee, regnerisch",
                "sunny" to "Sonnig",
                "windy" to "Windig",
                "windy-variant" to "Windig, bewölkt",
            )

        for ((condition, word) in words) {
            assertEquals(condition, word, application.getString(checkNotNull(Conditions.word(condition))))
        }
        assertNull(Conditions.word("sandstorm"))
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
        assertEquals("@${R.layout.widget_weather}", attributes["initialLayout"])
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
