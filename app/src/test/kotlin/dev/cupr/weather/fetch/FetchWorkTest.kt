package dev.cupr.weather.fetch

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.weather.app.Services
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.FakeHomeAssistant
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.signin.Status
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.FetchInterval
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.TokenCipher
import dev.cupr.weather.widget.WeatherWidgetProvider
import dev.cupr.weather.widget.WidgetContent
import dev.cupr.weather.widget.WidgetHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/** When the app fetches (D28), what each fetch does to the widgets (D33), and signing in and out with them (D31). */
@RunWith(RobolectricTestRunner::class)
class FetchWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private lateinit var http: OkHttpClient
    private lateinit var content: (Context) -> WidgetContent
    private val manager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        http = Services.http
        Services.http = OkHttpClient()
        content = WeatherWidgetProvider.content
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.WARN)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @After
    fun tearDown() {
        Services.cipher = keystoreCipher
        Services.http = http
        WeatherWidgetProvider.content = content
        WorkManagerTestInitHelper.closeWorkDatabase()
        home.close()
    }

    private fun work() = TestListenableWorkerBuilder<FetchWorker>(context).build().doWork()

    private fun placed() = WidgetHost.placed(context)

    /** A snapshot with [celsius] now, written as a fetch would, just now, so the widget does not say "Stand" (D16). */
    private fun snapshot(celsius: Int) =
        Services
            .snapshots(context)
            .write(
                Snapshot(
                    Instant.now(),
                    SensorData(
                        Json.parseToJsonElement("""{"schema_version":1,"now":{"temperature":$celsius}}""").jsonObject,
                    ),
                ),
            )

    /** The test's cipher, but opening a stored token runs [open], which throws. */
    private fun throwingOpen(open: () -> Nothing) =
        object : TokenCipher {
            override fun seal(plain: ByteArray) = AesGcmCipher { key }.seal(plain)

            override fun open(sealed: ByteArray): ByteArray = open()
        }

    private fun signIn() =
        Services.account(context).signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))

    private fun states(name: String) = manager.getWorkInfosForUniqueWork(name).get().map { it.state }

    private val scheduled get() = states(FetchWork.NOW) + states(FetchWork.PERIODIC)

    private val periodicMinutes: Long?
        get() =
            manager
                .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
                .get()
                .singleOrNull { !it.state.isFinished }
                ?.periodicityInfo
                ?.repeatIntervalMillis
                ?.let(TimeUnit.MILLISECONDS::toMinutes)

    @Test
    fun `the worker fetches into the snapshot and succeeds`() {
        signIn()

        assertEquals(ListenableWorker.Result.success(), work())

        assertEquals(
            7.4,
            Services
                .snapshots(context)
                .read()!!
                .data.temperature,
        )
    }

    @Test
    fun `each fetch updates the widgets, a failed one too`() {
        val widget = placed()
        signIn()
        assertEquals(ListenableWorker.Result.success(), work())
        assertEquals(listOf("7°", "Teilweise bewölkt"), WidgetHost.shown(widget))

        snapshot(3)
        home.close()
        assertEquals(ListenableWorker.Result.failure(), work())

        assertEquals(listOf("3°"), WidgetHost.shown(widget))
    }

    @Test
    fun `a fetch that throws still updates the widgets`() {
        val widget = placed()
        signIn()
        snapshot(3)
        Services.cipher = { throwingOpen { throw IllegalStateException("broken") } }

        assertThrows(IllegalStateException::class.java) { work() }

        assertEquals(listOf("3°"), WidgetHost.shown(widget))
    }

    @Test
    fun `a fetch that throws keeps its exception when the widgets' update throws too`() {
        WeatherWidgetProvider.content = { throw IllegalArgumentException("the widgets cannot be read") }
        Services.cipher = { throw IllegalStateException("no cipher") }

        val thrown = assertThrows(IllegalStateException::class.java) { work() }

        assertEquals("no cipher", thrown.message)
        assertEquals(listOf("the widgets cannot be read"), thrown.suppressed.map { it.message })
    }

    @Test
    fun `a failed fetch fails the work and keeps the periodic fetch`() {
        signIn()
        FetchWork.start(context)
        home.close()

        assertEquals(ListenableWorker.Result.failure(), work())

        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(FetchWork.PERIODIC))
    }

    @Test
    fun `without a usable sign-in the work succeeds and the periodic fetch stops`() {
        FetchWork.start(context)
        assertEquals(ListenableWorker.Result.success(), work())
        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC))

        signIn()
        FetchWork.start(context)
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        assertEquals(ListenableWorker.Result.success(), work())
        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC).distinct())
    }

    @Test
    fun `an unreadable stored sign-in stops the periodic fetch too`() {
        signIn()
        FetchWork.start(context)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        Services.cipher = { AesGcmCipher { other } }

        assertEquals(ListenableWorker.Result.success(), work())

        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC))
    }

    @Test
    fun `the fetch at once is expedited where the quota allows`() {
        val request = FetchWork.nowRequest()

        assertTrue(request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun `starting fetches at once and every 15 minutes, each only with a network`() {
        FetchWork.start(context)

        val now = manager.getWorkInfosForUniqueWork(FetchWork.NOW).get().single()
        val periodic = manager.getWorkInfosForUniqueWork(FetchWork.PERIODIC).get().single()
        assertEquals(NetworkType.CONNECTED, now.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertNotNull(periodic.periodicityInfo)
        assertEquals(TimeUnit.MINUTES.toMillis(15), periodic.periodicityInfo!!.repeatIntervalMillis)
        assertNull(now.periodicityInfo)
    }

    @Test
    fun `the periodic fetch runs at the interval set`() {
        Services.settings(context).writeFetchInterval(FetchInterval.EVERY_60_MINUTES)

        FetchWork.start(context)

        assertEquals(60L, periodicMinutes)
    }

    @Test
    fun `a new interval while signed in enqueues the periodic fetch anew at it, with a network`() {
        signIn()
        FetchWork.start(context)

        FetchWork.setInterval(context, FetchInterval.EVERY_30_MINUTES)

        assertEquals(30L, periodicMinutes)
        val periodic = manager.getWorkInfosForUniqueWork(FetchWork.PERIODIC).get().single { !it.state.isFinished }
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertEquals(FetchInterval.EVERY_30_MINUTES, Services.settings(context).fetchInterval())
    }

    @Test
    fun `the app's screen shows the interval set`() {
        assertEquals(FetchInterval.EVERY_15_MINUTES, Status.read(context).fetchInterval)

        FetchWork.setInterval(context, FetchInterval.EVERY_60_MINUTES)

        assertEquals(FetchInterval.EVERY_60_MINUTES, Status.read(context).fetchInterval)
    }

    @Test
    fun `a new interval without a usable sign-in is kept, and fetches nothing`() {
        FetchWork.setInterval(context, FetchInterval.EVERY_60_MINUTES)
        signIn()
        Services.account(context).reject()
        FetchWork.setInterval(context, FetchInterval.EVERY_30_MINUTES)

        assertEquals(emptyList<WorkInfo.State>(), scheduled)
        assertEquals(FetchInterval.EVERY_30_MINUTES, Services.settings(context).fetchInterval())
    }

    @Test
    fun `starting again keeps the periodic fetch as it is`() {
        FetchWork.start(context)
        val first =
            manager
                .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
                .get()
                .single()
                .id

        FetchWork.start(context)

        assertEquals(
            first,
            manager
                .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
                .get()
                .single()
                .id,
        )
    }

    @Test
    fun `the screen opening fetches only while signed in`() {
        Services.opened(context)
        assertEquals(emptyList<WorkInfo.State>(), scheduled)

        for (refused in listOf<(Account) -> Unit>(Account::reject, Account::markUnreadable)) {
            signIn()
            refused(Services.account(context))
            Services.opened(context)
            assertEquals(emptyList<WorkInfo.State>(), scheduled)
        }

        signIn()
        Services.opened(context)
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.ENQUEUED), scheduled)
    }

    @Test
    fun `a sign-in updates the widgets and schedules the fetches, a failed one only updates the widgets`() {
        val widget = placed()
        // This failed sign-in changes nothing the widget shows, so whether it updates the widget is counted.
        var updates = 0
        WeatherWidgetProvider.content = {
            updates++
            content(it)
        }
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        assertFalse(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))
        assertEquals(emptyList<WorkInfo.State>(), scheduled)
        assertEquals(1, updates)
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), WidgetHost.shown(widget))

        home.token = { FakeHomeAssistant.json("""{"refresh_token":"refresh-1","token_type":"Bearer"}""") }
        assertTrue(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.ENQUEUED), scheduled)
        assertEquals(listOf("Noch keine Daten – zum Öffnen tippen"), WidgetHost.shown(widget))
    }

    @Test
    fun `a sign-in whose delete of the earlier snapshot fails part-way shows what is left on the widget`() {
        signIn()
        snapshot(3)
        Services.account(context).reject()
        val widget = placed()
        assertEquals(listOf("3°", "Neu anmelden"), WidgetHost.shown(widget))
        // The snapshot itself goes; a directory with a file in it where AtomicFile keeps its new file stays.
        File(context.filesDir, "snapshot.json.new/blocking").mkdirs()
        home.token = { FakeHomeAssistant.json("""{"refresh_token":"refresh-1","token_type":"Bearer"}""") }

        assertFalse(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))

        assertEquals(emptyList<WorkInfo.State>(), scheduled)
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), WidgetHost.shown(widget))
    }

    @Test
    fun `a sign-in whose widget update throws still schedules the fetches`() {
        home.token = { FakeHomeAssistant.json("""{"refresh_token":"refresh-1","token_type":"Bearer"}""") }
        WeatherWidgetProvider.content = { throw IllegalStateException("the widgets cannot be read") }

        assertThrows(IllegalStateException::class.java) {
            Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1"))
        }

        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.ENQUEUED), scheduled)
    }

    @Test
    fun `fetches scheduled while a sign-out waits are cancelled after it`() {
        signIn()
        // The screen opening while the sign-out asks Home Assistant to end the token schedules the fetches anew.
        home.revoke = {
            FetchWork.start(context)
            MockResponse.Builder().code(200).build()
        }

        Services.signOut(context)

        assertTrue(scheduled.toString(), scheduled.isNotEmpty() && scheduled.all { it == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `signing out stops both fetches, deletes the snapshot and updates the widgets`() {
        signIn()
        assertEquals(ListenableWorker.Result.success(), work())
        FetchWork.start(context)
        val widget = placed()
        assertEquals(listOf("7°", "Teilweise bewölkt"), WidgetHost.shown(widget))

        Services.signOut(context)

        assertTrue(scheduled.toString(), scheduled.isNotEmpty() && scheduled.all { it == WorkInfo.State.CANCELLED })
        assertNull(Services.snapshots(context).read())
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), WidgetHost.shown(widget))
    }

    @Test
    fun `a sign-out that throws still stops the fetches scheduled meanwhile and updates the widgets`() {
        signIn()
        snapshot(3)
        val widget = placed()
        assertEquals(listOf("3°"), WidgetHost.shown(widget))
        // Opening the stored sign-in throws, after the screen opening meanwhile has scheduled the fetches anew.
        Services.cipher = {
            throwingOpen {
                FetchWork.start(context)
                throw IllegalStateException("broken")
            }
        }

        assertThrows(IllegalStateException::class.java) { Services.signOut(context) }

        assertTrue(scheduled.toString(), scheduled.isNotEmpty() && scheduled.all { it == WorkInfo.State.CANCELLED })
        assertEquals(listOf("Nicht angemeldet – zum Anmelden tippen"), WidgetHost.shown(widget))
    }
}
