package dev.cupr.weather.tap

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A tap on the widget: the tap activity opens the dashboard's weather pop-up in the Home Assistant app (D17, D39). */
@RunWith(RobolectricTestRunner::class)
class TapTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val weather = "homeassistant://navigate/dashboard-zuhause/start#wetter"

    /** The Home Assistant app as its minimal build, so the tests see it found by the link's scheme, not by name. */
    private val homeAssistant = "io.homeassistant.companion.android.minimal"
    private val launchActivity =
        ComponentName(homeAssistant, "io.homeassistant.companion.android.launch.LaunchActivity")

    @Test
    fun `the widget's pending intent starts the tap activity`() {
        val intent = shadowOf(Taps.weather(application)).savedIntent

        assertEquals(TapActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `the tap activity opens the weather pop-up and finishes`() {
        val activity = launch()

        val opened = started().last()
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(weather, opened.dataString)
        assertTrue(activity.isFinishing)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun `the weather pop-up opens on top of the Home Assistant app started as its icon starts it, in a cleared task`() {
        installLinkApp()
        launch()

        val opened = started()
        assertEquals(2, opened.size)
        val (app, target) = opened
        assertEquals(launchActivity, app.component)
        assertEquals(Intent.ACTION_MAIN, app.action)
        assertTrue(app.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, app.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, app.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertEquals(weather, target.dataString)
        assertEquals(0, target.flags)
    }

    @Test
    fun `without an app for Home Assistant links, the target is started alone in a new task and the log says so`() {
        launch()

        val opened = started().single()
        assertEquals(weather, opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("No app is seen to open $weather; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when two apps open Home Assistant links and none is the default, the target opens alone in a new task`() {
        installLinkApp()
        installLinkApp(app = "io.homeassistant.companion.android")
        shadowOf(application.packageManager).setShouldShowActivityChooser(true)
        launch()

        val opened = started().single()
        assertEquals(weather, opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("2 apps open $weather and none is the default; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when the app for Home Assistant links has no launcher entry, the target opens alone and the log says so`() {
        installLinkApp(launcher = false)
        launch()

        val opened = started().single()
        assertEquals(weather, opened.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.flags)
        assertEquals(
            listOf("$homeAssistant opens $weather but has no launcher entry; opening the link alone"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `when starting the Home Assistant app is not allowed, the target is not started and the toast says so`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, weather) {
            if (it.component == launchActivity) throw SecurityException("denied")
            opened += it
        }

        assertEquals(emptyList<Intent>(), opened)
        assertEquals(
            listOf("Not allowed to open ${launchActivity.flattenToShortString()} (tapped $weather)"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `when the link finds no app after the Home Assistant app started, the toast says the target does not open`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, weather) {
            if (it.component != launchActivity) throw ActivityNotFoundException("none")
            opened += it
        }

        assertEquals(listOf(launchActivity), opened.map { it.component })
        assertEquals(listOf("No app opens $weather"), ShadowLog.getLogsForTag("TapActivity").map { it.msg })
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `when the Home Assistant app's launcher start finds no app, the toast says it is missing`() {
        installLinkApp()
        val opened = mutableListOf<Intent>()
        openTarget(application, weather) {
            if (it.component == launchActivity) throw ActivityNotFoundException("none")
            opened += it
        }

        assertEquals(emptyList<Intent>(), opened)
        assertEquals(
            listOf("No app opens ${launchActivity.flattenToShortString()} (tapped $weather)"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Home-Assistant-App nicht gefunden", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a target no app handles is logged and shown, not thrown`() {
        openTarget(application, weather) { throw ActivityNotFoundException("none") }

        assertEquals(
            listOf("No app is seen to open $weather; opening the link alone", "No app opens $weather"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Home-Assistant-App nicht gefunden", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a Home Assistant target this app may not start says it does not open, not that the app is missing`() {
        openTarget(application, weather) { throw SecurityException("denied") }

        assertEquals(
            listOf("No app is seen to open $weather; opening the link alone", "Not allowed to open $weather"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a target outside the Home Assistant app opens alone, in a new task it does not clear`() {
        // An app that opens the target and has a launcher entry: it is still not started as its icon does.
        installLinkApp(
            app = "x.other.app",
            links =
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("other")
                },
        )
        val opened = mutableListOf<Intent>()
        openTarget(application, "other://x/y") { opened += it }

        assertEquals(listOf("other://x/y"), opened.map { it.dataString })
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, opened.single().flags)
        openTarget(application, "other://x/y") { throw ActivityNotFoundException("none") }
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `nothing crashes when no app handles the target`() {
        shadowOf(application).checkActivities(true)
        val activity = launch()

        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the manifest's query matches the Home Assistant app's filter for navigate links`() {
        // Android 11 and later show this app another app only when a query matches one of its filters; without that,
        // the Home Assistant app stays hidden and every tap opens its link alone. Android turns a query's <intent>
        // into an intent with the data scheme://host/* (ParsingPackageUtils.parseQueries) and matches it against the
        // filters (AppsFilterUtils), as built here.
        val manifest =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val queries = manifest.getElementsByTagName("queries").item(0) as Element
        val intent = queries.getElementsByTagName("intent").item(0) as Element
        val action = (intent.getElementsByTagName("action").item(0) as Element).getAttribute("android:name")
        val data = intent.getElementsByTagName("data").item(0) as Element
        val uri =
            Uri
                .Builder()
                .scheme(data.getAttribute("android:scheme"))
                .authority(data.getAttribute("android:host").ifEmpty { null })
                .path("*")
                .build()
        val match = linkFilter().match(action, null, uri.scheme, uri, null, "TapTest")
        assertTrue("the query $action $uri matches no filter of the Home Assistant app", match > 0)
    }

    /** Every intent started since the last call, oldest first (Robolectric hands out the newest first). */
    private fun started(): List<Intent> =
        generateSequence { shadowOf(application).nextStartedActivity }.toList().asReversed()

    /**
     * Installs an app as far as a tap sees it: an activity for its links, by default the Home Assistant app's navigate
     * links, and, unless told otherwise, its launcher entry.
     */
    private fun installLinkApp(
        app: String = homeAssistant,
        launcher: Boolean = true,
        links: IntentFilter = linkFilter(),
    ) {
        val packageManager = shadowOf(application.packageManager)
        val linkActivity = ComponentName(app, "io.homeassistant.companion.android.launch.link.LinkActivity")
        packageManager.addActivityIfNotPresent(linkActivity)
        packageManager.addIntentFilterForActivity(linkActivity, links)
        if (launcher) {
            val launch = ComponentName(app, launchActivity.className)
            packageManager.addActivityIfNotPresent(launch)
            packageManager.addIntentFilterForActivity(
                launch,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    /** The Home Assistant app's filter for navigate links, as its manifest has it (home-assistant/android c6eb57c). */
    private fun linkFilter() =
        IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme(Taps.HOME_ASSISTANT_SCHEME)
            addDataAuthority("navigate", null)
        }

    private fun launch(): TapActivity =
        Robolectric
            .buildActivity(TapActivity::class.java, Intent(application, TapActivity::class.java))
            .create()
            .get()
}
