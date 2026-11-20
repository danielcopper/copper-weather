package dev.cupr.weather.signin

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.WorkManagerTestInitHelper.getTestDriver
import dev.cupr.weather.R
import dev.cupr.weather.app.MainActivity
import dev.cupr.weather.app.Services
import dev.cupr.weather.fetch.FetchWork
import dev.cupr.weather.ha.FakeHomeAssistant
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.FailedFetch
import dev.cupr.weather.store.FetchCause
import dev.cupr.weather.store.FetchInterval
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.TokenCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/**
 * The app's screen against the fake Home Assistant: signing in through the login page's redirect, a sign-in that does
 * not come about, signing out, and the address kept (D24, D25, D27). The screen is given the address
 * `https://home.example`, and the app's HTTP client sends what goes there to the fake, on plain http; the login page's
 * WebView loads nothing under Robolectric, so the test hands its client the redirect Home Assistant's page would make.
 */
@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val address = checkNotNull(HaAddress.parse("home.example"))
    private lateinit var keystoreCipher: () -> TokenCipher
    private lateinit var io: CoroutineDispatcher
    private lateinit var http: OkHttpClient
    private val opened = mutableListOf<ActivityController<MainActivity>>()

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        // The status's read and the sign-in's run at once, so the test sees them without waiting.
        io = Services.io
        Services.io = Dispatchers.Unconfined
        http = Services.http
        Services.http = OkHttpClient.Builder().addInterceptor(ToFake(home)).build()
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
        for (controller in opened) controller.pause().stop().destroy()
        Services.cipher = keystoreCipher
        Services.io = io
        Services.http = http
        WorkManagerTestInitHelper.closeWorkDatabase()
        home.close()
        SignInFlow.clearProblem()
    }

    @Test
    fun `signing in through the login page stores the refresh token and the screen says it is signed in`() {
        open()
        enter("home.example")
        val page = loginPage()
        val authorize = checkNotNull(shadowOf(page).lastLoadedUrl).toHttpUrl()
        // The fake trades the code only for the verifier whose challenge the login page was given.
        home.challenge = authorize.queryParameter("code_challenge")

        redirect(page, "code=code-1&state=${authorize.queryParameter("state")}")

        node(R.string.sign_in_no_fetch).assertExists()
        node(R.string.sign_out).assertExists()
        val session = (Services.account(context).open() as Account.Opened.Usable).session
        assertEquals(RefreshToken("refresh-1"), session.refreshToken)
        assertEquals(address, session.address)
        assertEquals(SignInRequest.CLIENT_ID, session.clientId)
        assertEquals("code-1", home.requests.single().second["code"])
    }

    @Test
    fun `a sign-in Home Assistant refuses leaves no token and the screen says it failed`() {
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        open()
        enter("home.example")
        val page = loginPage()
        val state = checkNotNull(shadowOf(page).lastLoadedUrl).toHttpUrl().queryParameter("state")

        redirect(page, "code=code-1&state=$state")

        node(R.string.sign_in_failed).assertExists()
        node(R.string.sign_in).assertExists()
        node(R.string.sign_in_no_fetch).assertDoesNotExist()
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
        assertFalse(prefs.all.toString(), prefs.contains("refresh_token"))
    }

    @Test
    fun `a redirect with another state leaves no token, asks Home Assistant nothing and the screen says it failed`() {
        open()
        enter("home.example")

        redirect(loginPage(), "code=code-1&state=another")

        node(R.string.sign_in_failed).assertExists()
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
        assertFalse(prefs.contains("refresh_token"))
        assertEquals(0, home.requests.size)
    }

    @Test
    fun `a login page that does not load leaves no token and the screen says Home Assistant is out of reach`() {
        open()
        enter("home.example")
        val page = loginPage()
        // What a gateway in front of Home Assistant answers while Home Assistant is down.
        val badGateway = WebResourceResponse("text/html", "utf-8", 502, "Bad Gateway", emptyMap(), null)
        val authorize = request("https://home.example/auth/authorize")

        shadowOf(page).webViewClient.onReceivedHttpError(page, authorize, badGateway)
        compose.waitForIdle()

        node(R.string.sign_in_unreachable).assertExists()
        node(R.string.sign_in).assertExists()
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
        assertFalse(prefs.contains("refresh_token"))
    }

    @Test
    fun `an address that is not https is refused on the screen`() {
        open()

        enter("http://homeassistant.local:8123")

        node(R.string.sign_in_address_invalid).assertExists()
        assertTrue(webViews().isEmpty())
    }

    @Test
    fun `signing out ends the token at Home Assistant, forgets it and keeps the address`() {
        Services.account(context).signIn(address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))
        open()

        node(R.string.sign_out).performClick()
        compose.waitForIdle()

        val (revoke, form) = home.requests.single()
        assertEquals("/auth/revoke", revoke.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
        assertEquals(setOf("address"), prefs.all.keys)
        assertEquals(address, Services.account(context).address)
        node(R.string.sign_in).assertExists()
        compose.onNodeWithText("https://home.example").assertExists()
    }

    @Test
    fun `the typed address is kept across restarts of the app`() {
        open()
        enter("home.example/lovelace/0")
        close()

        open()

        compose.onNodeWithText("https://home.example").assertExists()
        // What a restarted process reads: the preferences' file, not their copy in this process's memory.
        val file = context.dataDir.resolve("shared_prefs/account.xml")
        assertTrue(file.path, file.readText().contains("https://home.example/"))
    }

    @Test
    fun `the screen shows the attribution from the snapshot`() {
        signIn()
        snapshot("""{"schema_version":1,"attribution":"Weather data: a test source"}""")

        open()

        compose.onNodeWithText("Weather data: a test source").assertExists()
    }

    @Test
    fun `a refused sign-in keeps showing the snapshot's attribution beside the address form`() {
        signIn()
        snapshot("""{"schema_version":1,"attribution":"Weather data: a test source"}""")
        Services.account(context).reject()

        open()

        node(R.string.sign_in_rejected).assertExists()
        compose.onNodeWithText("Weather data: a test source").assertExists()
        node(R.string.sign_in).assertExists()
        node(R.string.fetch_interval).assertDoesNotExist()
    }

    @Test
    fun `a fetch that finishes while the screen is open shows its time there`() {
        signIn()
        open()
        node(R.string.sign_in_no_fetch).assertExists()
        val now =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork(FetchWork.NOW)
                .get()
                .single()

        // The fetch the screen started as it opened runs now, against the fake Home Assistant.
        checkNotNull(getTestDriver(context)).setAllConstraintsMet(now.id)

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose
                .onAllNodesWithText("Angemeldet, letzter Abruf um", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        node(R.string.sign_in_no_fetch).assertDoesNotExist()
    }

    @Test
    fun `signed in, the screen says when the last fetch failed and why`() {
        signIn()
        Services.failures(context).write(FailedFetch(Instant.now(), FetchCause.NEWER_SCHEMA))

        open()

        val text = context.getString(R.string.fetch_cause_newer_schema)
        compose.onNodeWithText(text, substring = true).assertExists()
    }

    @Test
    fun `signed in, the screen shows the stored fetch interval, and picking another sets it`() {
        signIn()
        Services.settings(context).writeFetchInterval(FetchInterval.EVERY_30_MINUTES)

        open()

        node(R.string.fetch_interval).assertExists()
        node(R.string.every_30_minutes).assertIsSelected()
        node(R.string.every_15_minutes).assertIsNotSelected()
        node(R.string.every_60_minutes).assertIsNotSelected()
        assertEquals(TimeUnit.MINUTES.toMillis(30), periodicMillis())

        node(R.string.every_60_minutes).performClick()
        compose.waitForIdle()

        node(R.string.every_60_minutes).assertIsSelected()
        node(R.string.every_30_minutes).assertIsNotSelected()
        assertEquals(FetchInterval.EVERY_60_MINUTES, Services.settings(context).fetchInterval())
        assertEquals(TimeUnit.MINUTES.toMillis(60), periodicMillis())
    }

    @Test
    fun `signed out, the screen has no fetch interval and opening it fetches nothing`() {
        open()

        node(R.string.sign_in).assertExists()
        node(R.string.fetch_interval).assertDoesNotExist()
        assertEquals(
            emptyList<WorkInfo>(),
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(FetchWork.NOW).get(),
        )
    }

    private fun signIn() = Services.account(context).signIn(address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))

    private fun snapshot(data: String) =
        Services.snapshots(context).write(Snapshot(Instant.now(), SensorData(Json.parseToJsonElement(data).jsonObject)))

    /** The periodic fetch's interval, as it is scheduled. */
    private fun periodicMillis(): Long? =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
            .get()
            .single { !it.state.isFinished }
            .periodicityInfo
            ?.repeatIntervalMillis

    /** Types [text] into the address field and taps "Mit Home Assistant anmelden". */
    private fun enter(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        node(R.string.sign_in).performClick()
        compose.waitForIdle()
    }

    /** The login page's WebView, the only one the screen shows. */
    private fun loginPage(): WebView = webViews().single()

    private fun webViews(): List<WebView> =
        opened
            .last()
            .get()
            .window.decorView
            .let(::descendants)
            .filterIsInstance<WebView>()

    private fun descendants(view: View): List<View> {
        val children = (view as? ViewGroup)?.let { group -> (0 until group.childCount).map(group::getChildAt) }
        return listOf(view) + children.orEmpty().flatMap(::descendants)
    }

    /** The navigation to the app's redirect URI with [query], as Home Assistant's login page makes it on success. */
    private fun redirect(
        page: WebView,
        query: String,
    ) {
        val caught =
            shadowOf(page).webViewClient.shouldOverrideUrlLoading(page, request("${SignInRequest.REDIRECT_URI}?$query"))

        assertTrue(caught)
        compose.waitForIdle()
    }

    private fun request(url: String) =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame() = true

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    private fun open() {
        opened += Robolectric.buildActivity(MainActivity::class.java).setup()
        compose.waitForIdle()
    }

    private fun close() {
        opened
            .removeAt(opened.lastIndex)
            .pause()
            .stop()
            .destroy()
    }

    private fun node(text: Int) = compose.onNodeWithText(context.getString(text))

    /** Sends what the app asks of `https://home.example` to the fake, on plain http. */
    private class ToFake(
        private val home: FakeHomeAssistant,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            check(url.host == "home.example") { "The app asked another host than home.example" }
            val fake =
                url
                    .newBuilder()
                    .scheme(home.url.scheme)
                    .host(home.url.host)
                    .port(home.url.port)
                    .build()
            return chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .url(fake)
                    .build(),
            )
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
