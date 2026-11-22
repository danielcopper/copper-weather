package dev.cupr.weather.widget

import android.app.Application
import android.content.Intent
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.KeyGenerator

/**
 * What the tests of the widget on Robolectric's host share: a sign-in with a cipher Robolectric can run, the sample
 * clock, the snapshot written as the app keeps it, a widget placed on that host, and the check of its colours.
 */
abstract class WidgetTestBase {
    protected val application = ApplicationProvider.getApplicationContext<Application>()
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

    protected val density get() = application.resources.displayMetrics.density

    private companion object {
        /** Each image view of an icon and the frame beside it for its animated icon (D43). */
        val ANIMATED =
            mapOf(
                R.id.now_icon to R.id.now_icon_animated,
                R.id.hour_icon to R.id.hour_icon_animated,
                R.id.day_icon to R.id.day_icon_animated,
                R.id.flat_day_icon to R.id.flat_day_icon_animated,
            )
    }

    /** [value] dp in px on the test's display. */
    protected fun dp(value: Int): Int = Math.round(value * density)

    /** [value] sp in px on the test's display. */
    protected fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, application.resources.displayMetrics)

    /** [widget] measured and laid out at [size], as a launcher does before it draws it. */
    protected fun laidOut(
        widget: View,
        size: SizeF,
    ): View {
        val width = Math.round(size.width * density)
        val height = Math.round(size.height * density)
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, width, height)
        return widget
    }

    protected fun signIn() = Services.account(application).signIn(address, SignInRequest.CLIENT_ID, RefreshToken("r"))

    protected fun snapshot(now: String = """{"condition":"partlycloudy","temperature":7.4}""") =
        written("""{"schema_version":1,"now":$now}""")

    protected fun written(data: String) =
        Services
            .snapshots(application)
            .write(Snapshot(Instant.EPOCH, SensorData.read(Json.parseToJsonElement(data).jsonObject).readable()))

    private fun SensorData.Read.readable() = (this as SensorData.Read.Readable).data

    protected fun placed(size: SizeF? = null): View = WidgetHost.placed(application, size)

    protected fun View.text(id: Int): TextView = findViewById(id)

    protected fun View.shown(): List<String> = WidgetHost.shown(this)

    /** The drawable resource an image view shows. */
    protected fun View.image(id: Int): Int = shadowOf(findViewById<ImageView>(id).drawable).createdFromResId

    /**
     * Where the icon of the image view [still] shows: that view, or the frame beside it that holds its animated icon
     * (D43), whichever is visible; the other is hidden.
     */
    protected fun View.iconSlot(still: Int): View =
        listOf(still, ANIMATED.getValue(still))
            .map { findViewById<View>(it) }
            .filter { it.visibility == View.VISIBLE }
            .single()

    /**
     * The drawable resource the icon of the image view [still] shows: its still image, or the animated icon a
     * ProgressBar in the frame beside it runs (D43).
     */
    protected fun View.icon(still: Int): Int =
        when (val slot = iconSlot(still)) {
            is ImageView -> {
                shadowOf(slot.drawable).createdFromResId
            }

            else -> {
                val bar = (slot as ViewGroup).getChildAt(0) as ProgressBar
                assertEquals(1, slot.childCount)
                shadowOf(bar.indeterminateDrawable).createdFromResId
            }
        }

    /** The activity a tap on the widget starts. */
    protected fun View.tapped(): Intent {
        findViewById<View>(R.id.widget).performClick()
        return checkNotNull(shadowOf(application).nextStartedActivity) { "The tap started nothing" }
    }

    /**
     * Every text a widget [size] dp large shows with rain due, and the sign-in refused when [refuse], more than
     * [fewest] of them, is a colour of the palette of night mode when [dark], else of the other: the temperature
     * `text`, the rain hint `rain`, and [more].
     */
    protected fun assertColours(
        dark: Boolean,
        size: SizeF,
        refuse: Boolean,
        fewest: Int,
        more: (View, Palette) -> Unit = { _, _ -> },
    ) {
        signIn()
        written(SampleWeather.full(rainIn = Duration.ofMinutes(25)))
        if (refuse) Services.account(application).reject()

        val widget = placed(size)

        val palette = Palette.of(dark)
        val texts = WidgetHost.texts(widget).filter { WidgetHost.visible(it, widget) }
        assertTrue("${texts.size} texts", texts.size > fewest)
        val colours = palette.colors().values.toSet()
        for (text in texts) assertTrue("${text.text}", text.currentTextColor in colours)
        assertEquals(palette.text, widget.text(R.id.now_temperature).currentTextColor)
        assertEquals(palette.rain, widget.text(R.id.now_line).currentTextColor)
        assertEquals(palette.surface, application.getColor(R.color.palette_surface))
        more(widget, palette)
    }
}
