package dev.cupr.weather.setup

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.Animation
import dev.cupr.weather.store.FlatStrip
import dev.cupr.weather.store.WidgetSettings
import dev.cupr.weather.widget.SampleWeather
import dev.cupr.weather.widget.SizeBucket
import dev.cupr.weather.widget.WidgetHost
import dev.cupr.weather.widget.WidgetTestBase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLog

/**
 * A widget's configuration screen (D44, D45): it shows the widget's stored settings, saves new ones and draws that
 * widget with them, or keeps them on going back; it shows nothing for an id that is none of this app's widgets.
 */
@RunWith(RobolectricTestRunner::class)
class SetupTest : WidgetTestBase() {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var io: CoroutineDispatcher
    private val opened = mutableListOf<ActivityController<SetupActivity>>()
    private val tall = SizeBucket.sizeOf(columns = 4, rows = 3)

    @Before
    fun setUpIo() {
        // The settings' read and the save run at once, so the test sees them without waiting.
        io = Services.io
        Services.io = Dispatchers.Unconfined
    }

    @After
    fun tearDownIo() {
        for (controller in opened) controller.pause().stop().destroy()
        Services.io = io
    }

    @Test
    fun `the screen shows the stored settings, the opacity in percent on a slider of steps of 10`() {
        val id = WidgetHost.place(application, tall)
        Services.widgetSettings(application).write(id, WidgetSettings(Animation.ALL, FlatStrip.HOURS, 70))

        open(id)

        node(R.string.setup_animation_all).assertIsSelected()
        node(R.string.setup_flat_strip_hours).assertIsSelected()
        compose.onNodeWithText("70 %").assertExists()
        val range = slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(ProgressBarRangeInfo(70f, 0f..100f, steps = 9), range)
        compose.onNodeWithText(application.getString(R.string.setup_title, "Wetter")).assertExists()
        // The flat strip's choice under the owner's heading (D44).
        compose.onNodeWithText("Vorschau").assertExists()
    }

    @Test
    fun `a widget without settings shows the defaults on its screen`() {
        open(WidgetHost.place(application, tall))

        node(R.string.setup_animation_now).assertIsSelected()
        node(R.string.setup_flat_strip_days).assertIsSelected()
        compose.onNodeWithText("100 %").assertExists()
    }

    @Test
    fun `saving stores the settings shown, draws the widget with them and finishes for the launcher`() {
        signIn()
        written(SampleWeather.full())
        val id = WidgetHost.place(application, tall)
        val activity = open(id)

        node(R.string.setup_animation_off).performClick()
        node(R.string.setup_flat_strip_hours).performClick()
        // The slider stops at the step nearest to where it is let go.
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(34f) }
        compose.onNodeWithText("30 %").assertExists()
        node(R.string.setup_save).performClick()
        compose.waitForIdle()

        assertEquals(WidgetSettings(Animation.OFF, FlatStrip.HOURS, 30), Services.widgetSettings(application).read(id))
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(id, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        val widget = WidgetHost.view(application, id)
        assertEquals(R.drawable.weather_partlycloudy_day, widget.icon(R.id.now_icon))
        assertEquals(77, Color.alpha(checkNotNull(widget.backgroundTintList).defaultColor))
    }

    @Test
    fun `the settings shown, changed but not saved, outlive the screen made anew, as on turning the phone`() {
        val id = WidgetHost.place(application, tall)
        open(id)

        node(R.string.setup_animation_off).performClick()
        node(R.string.setup_flat_strip_hours).performClick()
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        opened.last().recreate()
        compose.waitForIdle()

        node(R.string.setup_animation_off).assertIsSelected()
        node(R.string.setup_flat_strip_hours).assertIsSelected()
        compose.onNodeWithText("40\u00A0%").assertExists()
        assertEquals(WidgetSettings.DEFAULT, Services.widgetSettings(application).read(id))
    }

    @Test
    fun `going back keeps the stored settings`() {
        val id = WidgetHost.place(application, tall)
        Services.widgetSettings(application).write(id, WidgetSettings(opacity = 50))
        val activity = open(id)

        node(R.string.setup_animation_off).performClick()
        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(WidgetSettings(opacity = 50), Services.widgetSettings(application).read(id))
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
    }

    @Test
    fun `opened for an id that is none of this app's widgets, it shows nothing and finishes, logged`() {
        val activity = open(4711, waitForIdle = false)

        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        val logs = ShadowLog.getLogsForTag(TAG).joinToString { it.msg }
        assertTrue(logs, logs.contains("Configuration opened for widget 4711, which is none of this app's widgets"))
    }

    @Test
    fun `a widget removed while its screen was open keeps nothing and draws nothing`() {
        assertFalse(save(application, 4711, WidgetSettings(animation = Animation.ALL)))

        assertEquals(WidgetSettings.DEFAULT, Services.widgetSettings(application).read(4711))
        val logs = ShadowLog.getLogsForTag(TAG).joinToString { it.msg }
        assertTrue(logs, logs.contains("Widget 4711: saved, but no longer one of the app's widgets"))
    }

    private fun open(
        id: Int,
        waitForIdle: Boolean = true,
    ): SetupActivity {
        val intent = Intent(application, SetupActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        val controller = Robolectric.buildActivity(SetupActivity::class.java, intent).setup()
        opened += controller
        if (waitForIdle) compose.waitForIdle()
        return controller.get()
    }

    private fun node(text: Int) = compose.onNodeWithText(application.getString(text))

    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
}
