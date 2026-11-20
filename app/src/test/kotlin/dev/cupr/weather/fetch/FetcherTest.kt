package dev.cupr.weather.fetch

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.FakeHomeAssistant
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.FailedFetch
import dev.cupr.weather.store.FailedFetchStore
import dev.cupr.weather.store.FetchCause
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.SnapshotStore
import dev.cupr.weather.store.TokenCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.io.IOException
import java.security.ProviderException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/**
 * The sign-in's lifetime against the fake Home Assistant: keeping a new sign-in, each fetch into the snapshot, and
 * signing out (D26–D32).
 */
@RunWith(RobolectricTestRunner::class)
class FetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val account = Account(prefs, AesGcmCipher { key })
    private val snapshotFile = File(context.filesDir, "snapshot.json")
    private val snapshots = SnapshotStore(snapshotFile)
    private val failures = FailedFetchStore(prefs)
    private val now = Instant.parse("2026-11-20T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))
    private var online = true
    private val fetcher = fetcher(account)
    private val code = AuthorizationCode("code-1")
    private val old =
        Snapshot(Instant.ofEpochMilli(1), SensorData(json("""{"schema_version":1,"now":{"temperature":-3}}""")))

    @After
    fun stop() = home.close()

    /** Another Fetcher on the same files, as the app makes one for each use. */
    private fun fetcher(account: Account) = Fetcher(account, snapshots, failures, OkHttpClient(), clock) { online }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun signIn() = account.signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))

    private fun cipher(open: () -> Nothing) =
        object : TokenCipher {
            override fun seal(plain: ByteArray) = AesGcmCipher { key }.seal(plain)

            override fun open(sealed: ByteArray): ByteArray = open()
        }

    private fun logs(tag: String): String = ShadowLog.getLogsForTag(tag).joinToString("\n") { it.msg }

    private val fetchLogs: String get() = logs("Fetch")

    private val accountLogs: String get() = logs("Account")

    /** The level of the sign-out's log line: info when the token ended at Home Assistant or none was stored. */
    private fun signedOutLevel() = ShadowLog.getLogsForTag("Account").single { it.msg.startsWith("Signed out") }.type

    // The fetch (D28, D29).

    @Test
    fun `a fetch writes the app sensor's data to the snapshot whole, with absent fields left absent`() {
        signIn()

        assertEquals(Fetcher.Outcome.Fetched, fetcher.fetch())

        val snapshot = checkNotNull(snapshots.read())
        assertEquals(now, snapshot.fetchedAt)
        assertEquals(json(FakeHomeAssistant.DATA), snapshot.data.json)
        assertFalse(snapshot.data.toString(), "rain_at" in snapshot.data.json)
        assertFalse(snapshot.data.toString(), "storm" in snapshot.data.json)
        assertFalse(snapshot.toString(), "null" in snapshot.data.json.toString())
        assertEquals(7.4, snapshot.data.temperature)
        assertEquals("partlycloudy", snapshot.data.condition)
        assertEquals("Weather data: a test source", snapshot.data.attribution)
        val (refresh, form) = home.requests.first()
        assertEquals("/auth/token", refresh.url.encodedPath)
        assertEquals("refresh-1", form["refresh_token"])
        assertEquals(SignInRequest.CLIENT_ID, form["client_id"])
        val states = home.requests.last().first
        assertEquals("/api/states/sensor.copper_weather", states.url.encodedPath)
        assertEquals("Bearer access-1", states.headers["Authorization"])
    }

    @Test
    fun `a field the next fetch no longer carries is gone from the snapshot`() {
        signIn()
        fetcher.fetch()
        home.data = """{"schema_version":1,"now":{"condition":"rainy"}}"""

        fetcher.fetch()

        val snapshot = checkNotNull(snapshots.read())
        assertEquals(json(home.data), snapshot.data.json)
        assertNull(snapshot.data.temperature)
        assertNull(snapshot.data.attribution)
    }

    @Test
    fun `no token lands in the preferences or the snapshot`() {
        signIn()

        fetcher.fetch()

        val stored = prefs.all.values.joinToString() + snapshotFile.readText()
        assertFalse(stored, stored.contains("refresh-1"))
        assertFalse(stored, stored.contains("access-1"))
    }

    @Test
    fun `a refused refresh token marks the sign-in as refused and keeps the snapshot`() {
        signIn()
        snapshots.write(old)
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }

        assertEquals(Fetcher.Outcome.Rejected, fetcher.fetch())

        assertEquals(Account.State.REJECTED, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(1, home.requests.size)
        assertTrue(fetchLogs, fetchLogs.contains("Home Assistant refused the refresh token"))
    }

    @Test
    fun `a failed fetch keeps the last snapshot and records the failure`() {
        signIn()
        snapshots.write(old)
        home.states = { FakeHomeAssistant.json("""{"message":"Server error"}""", 500) }

        val reason = "HTTP 500 for sensor.copper_weather"
        assertEquals(Fetcher.Outcome.Failed(reason, FetchCause.UNEXPECTED), fetcher.fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(FailedFetch(now, FetchCause.UNEXPECTED), failures.read())
    }

    @Test
    fun `a fetch that succeeds forgets the last failure`() {
        signIn()
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        fetcher.fetch()

        assertNull(failures.read())
    }

    @Test
    fun `a refresh that gets no answer is a failure, not a refusal`() {
        signIn()
        home.token = { FakeHomeAssistant.json("Bad Gateway", 502) }

        assertEquals(Fetcher.Outcome.Failed("refresh: HTTP 502", FetchCause.UNREACHABLE), fetcher.fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `a snapshot that cannot be written is a failure of the phone`() {
        signIn()
        // A directory where AtomicFile writes the new snapshot first: it cannot open its file there.
        File(snapshotFile.path + ".new", "blocking").mkdirs()

        val outcome = fetcher.fetch()

        assertTrue(outcome.toString(), outcome.toString().startsWith("Failed(reason=the snapshot cannot be written: "))
        assertEquals(FetchCause.PHONE, (outcome as Fetcher.Outcome.Failed).cause)
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `a snapshot that does not end up in place is a failure of the phone`() {
        signIn()
        File(snapshotFile, "blocking").mkdirs()

        assertEquals(FetchCause.PHONE, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
    }

    @Test
    fun `without a sign-in nothing is fetched`() {
        assertEquals(Fetcher.Outcome.NotSignedIn, fetcher.fetch())

        assertEquals(0, home.requests.size)
    }

    @Test
    fun `a stored token that cannot be read on this phone marks the sign-in unreadable, not refused`() {
        signIn()
        snapshots.write(old)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertEquals(Fetcher.Outcome.Unreadable, fetcher(Account(prefs, AesGcmCipher { other })).fetch())

        assertEquals(Account.State.UNREADABLE, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(0, home.requests.size)
        assertTrue(fetchLogs, fetchLogs.contains("The stored sign-in cannot be read on this phone"))
        assertFalse(fetchLogs, fetchLogs.contains("refused"))
    }

    @Test
    fun `a Keystore that fails is a failed fetch, and the sign-in stays`() {
        signIn()
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })

        val reason = "the Keystore failed: ProviderException"
        assertEquals(Fetcher.Outcome.Failed(reason, FetchCause.PHONE), fetcher(failing).fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
        assertEquals(0, home.requests.size)
        assertEquals(FailedFetch(now, FetchCause.PHONE), failures.read())
    }

    @Test
    fun `what failed is named for the screen in plain causes`() {
        signIn()
        home.states = { FakeHomeAssistant.json("""{"message":"Entity not found."}""", 404) }
        assertEquals(FetchCause.SENSOR_MISSING, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)

        home.states = { FakeHomeAssistant.json("""{"entity_id":"sensor.copper_weather","attributes":{}}""") }
        assertEquals(
            Fetcher.Outcome.Failed("sensor.copper_weather has no data", FetchCause.SENSOR_MISSING),
            fetcher.fetch(),
        )

        home.states = { FakeHomeAssistant.json("[]") }
        assertEquals(FetchCause.UNEXPECTED, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)

        home.accessToken = "another"
        home.token = { FakeHomeAssistant.json("""{"access_token":"access-1","token_type":"Bearer"}""") }
        assertEquals(FetchCause.REFUSED, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)

        home.close()
        assertEquals(FetchCause.UNREACHABLE, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
        online = false
        assertEquals(FetchCause.NO_NETWORK, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
    }

    @Test(timeout = 10_000)
    fun `a timeout is named as such, or as no network while the phone has none`() {
        signIn()
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val fetcher = Fetcher(account, snapshots, failures, impatient, clock) { online }
        try {
            assertEquals(FetchCause.TIMEOUT, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
            online = false
            assertEquals(FetchCause.NO_NETWORK, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
        } finally {
            gate.countDown()
        }
    }

    // The schema version (D30).

    @Test
    fun `a newer schema version keeps the last snapshot and asks for an update of the app`() {
        signIn()
        snapshots.write(old)
        home.data = """{"schema_version":2,"now":{"temperature":9}}"""

        assertEquals(
            Fetcher.Outcome.Failed("schema_version 2 is newer than 1", FetchCause.NEWER_SCHEMA),
            fetcher.fetch(),
        )

        assertEquals(old, snapshots.read())
        assertEquals(FailedFetch(now, FetchCause.NEWER_SCHEMA), failures.read())
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `data without a schema version is an unexpected answer and keeps the last snapshot`() {
        signIn()
        snapshots.write(old)
        home.data = """{"now":{"temperature":9}}"""

        assertEquals(
            Fetcher.Outcome.Failed("the data has no schema_version", FetchCause.UNEXPECTED),
            fetcher.fetch(),
        )

        assertEquals(old, snapshots.read())
        assertEquals(FailedFetch(now, FetchCause.UNEXPECTED), failures.read())
    }

    // Keeping a new sign-in (D22, D26, D31).

    @Test
    fun `a sign-in trades the code with the verifier whose challenge the login page had, and fetches nothing yet`() {
        val request = SignInRequest.start(home.address)
        home.challenge = request.authorizeUrl.queryParameter("code_challenge")
        snapshots.write(old)
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        assertEquals(Fetcher.SignIn.SignedIn, fetcher.signIn(request, code))

        val session = (account.open() as Account.Opened.Usable).session
        assertEquals(RefreshToken("refresh-1"), session.refreshToken)
        assertEquals(SignInRequest.CLIENT_ID, session.clientId)
        assertEquals(home.address, session.address)
        assertNull(snapshots.read())
        assertNull(failures.read())
        assertEquals("code-1", home.requests.single().second["code"])
    }

    @Test
    fun `a sign-in with another verifier than the challenge's keeps nothing`() {
        val request = SignInRequest.start(home.address)
        home.challenge = SignInRequest.challenge("another verifier, long enough to be one: 0123456789")
        snapshots.write(old)

        assertEquals(
            Fetcher.SignIn.Failed("Home Assistant gave no tokens: HTTP 400 invalid_grant"),
            fetcher.signIn(request, code),
        )

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertFalse(prefs.contains("refresh_token"))
        assertEquals(old, snapshots.read())
    }

    @Test
    fun `a token that cannot be sealed is ended in Home Assistant again and nothing is kept`() {
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })
        snapshots.write(old)

        val outcome = fetcher(failing).signIn(SignInRequest.start(home.address), code)

        assertEquals(
            Fetcher.SignIn.Failed(
                "the token cannot be sealed (ProviderException); Home Assistant ended the refresh token",
            ),
            outcome,
        )
        val (revoke, form) = home.requests.last()
        assertEquals("/auth/revoke", revoke.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(emptySet<String>(), prefs.all.keys)
        assertEquals(old, snapshots.read())
    }

    // Signing out (D27, D31).

    @Test
    fun `signing out ends the refresh token, forgets it and deletes the snapshot and the last failure`() {
        signIn()
        snapshots.write(old)
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        fetcher.signOut()

        val (request, form) = home.requests.single()
        assertEquals("/auth/revoke", request.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(Account.Opened.NotSignedIn, account.open())
        assertEquals(home.address, account.address)
        assertNull(snapshots.read())
        assertFalse(snapshotFile.exists())
        assertNull(failures.read())
        assertTrue(accountLogs, accountLogs.contains("Signed out; Home Assistant ended the refresh token"))
        assertEquals(Log.INFO, signedOutLevel())
    }

    @Test
    fun `signing out after a refused sign-in deletes the snapshot it kept`() {
        signIn()
        snapshots.write(old)
        account.reject()

        fetcher.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertNull(snapshots.read())
        assertEquals(0, home.requests.size)
    }

    @Test
    fun `signing out with nothing stored asks Home Assistant nothing and logs it as info`() {
        fetcher.signOut()

        assertEquals(0, home.requests.size)
        assertTrue(accountLogs, accountLogs.contains("Signed out; nothing was stored"))
        assertEquals(Log.INFO, signedOutLevel())
    }

    @Test
    fun `signing out that Home Assistant answers with an error warns that the token was not ended`() {
        signIn()
        home.revoke = { FakeHomeAssistant.json("Server error", 500) }

        fetcher.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(accountLogs, accountLogs.contains("could not be asked to end the refresh token (HTTP 500)"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out without an answer from Home Assistant still signs out`() {
        signIn()
        home.close()

        fetcher.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(accountLogs, accountLogs.contains("could not be asked to end the refresh token (ConnectException)"))
        assertTrue(accountLogs, accountLogs.contains("it stays valid there until it goes 90 days unused"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out with a stored token that cannot be read signs out and says Home Assistant was not asked`() {
        signIn()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        fetcher(Account(prefs, AesGcmCipher { other })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(accountLogs, accountLogs.contains("so Home Assistant was not asked to end it"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out with a Keystore that fails signs out and says Home Assistant was not asked`() {
        signIn()

        fetcher(Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(
            accountLogs,
            accountLogs.contains("the stored token cannot be read (the Keystore failed: ProviderException)"),
        )
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out signs out even when opening the stored sign-in throws`() {
        signIn()
        snapshots.write(old)
        val throwing = Account(prefs, cipher { throw IllegalStateException("broken") })

        runCatching { fetcher(throwing).signOut() }

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertNull(snapshots.read())
        assertTrue(
            accountLogs,
            accountLogs.contains("Signed out; Home Assistant was not asked to end the refresh token"),
        )
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `a sign-in after a refused one whose snapshot cannot be deleted leaves the account refused`() {
        signIn()
        account.reject()
        File(snapshotFile, "blocking").mkdirs()

        val outcome = fetcher.signIn(SignInRequest.start(home.address), code)

        assertTrue(outcome.toString(), outcome is Fetcher.SignIn.Failed)
        assertEquals(Account.State.REJECTED, account.state)
        assertFalse(prefs.contains("refresh_token"))
        assertEquals(
            "/auth/revoke",
            home.requests
                .last()
                .first.url.encodedPath,
        )
    }

    @Test
    fun `a new sign-in whose earlier snapshot cannot be deleted keeps nothing and ends its token again`() {
        val request = SignInRequest.start(home.address)
        // A directory with a file in it where the snapshot belongs: it cannot be deleted.
        File(snapshotFile, "blocking").mkdirs()

        val outcome = fetcher.signIn(request, code)

        assertEquals(
            Fetcher.SignIn.Failed(
                "the earlier snapshot cannot be deleted (the snapshot was not deleted); " +
                    "Home Assistant ended the refresh token",
            ),
            outcome,
        )
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertFalse(prefs.contains("refresh_token"))
        assertEquals(
            "/auth/revoke",
            home.requests
                .last()
                .first.url.encodedPath,
        )
    }

    @Test
    fun `signing out with a snapshot that cannot be deleted signs out, logs it and throws`() {
        signIn()
        File(snapshotFile, "blocking").mkdirs()

        assertThrows(IOException::class.java) { fetcher.signOut() }

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(accountLogs, accountLogs.contains("Signed out; Home Assistant ended the refresh token"))
    }

    // The lock (D31).

    @Test(timeout = 10_000)
    fun `signing out waits for a running fetch, so its snapshot does not outlive the sign-in`() {
        signIn()
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val pool = Executors.newFixedThreadPool(2)
        try {
            val fetch = pool.submit<Fetcher.Outcome> { fetcher(account).fetch() }
            assertTrue(home.statesAsked.await(5, TimeUnit.SECONDS))
            val signOut = pool.submit { fetcher(account).signOut() }
            // The sign-out has asked Home Assistant to end the token and now waits for the fetch.
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(signOut.isDone)
            gate.countDown()

            assertEquals(Fetcher.Outcome.Fetched, fetch.get(5, TimeUnit.SECONDS))
            signOut.get(5, TimeUnit.SECONDS)
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }

        assertNull(snapshots.read())
        assertEquals(Account.State.SIGNED_OUT, account.state)
    }

    @Test(timeout = 10_000)
    fun `a new sign-in waits for a running fetch, so its snapshot does not outlive the sign-in`() {
        signIn()
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val pool = Executors.newFixedThreadPool(2)
        try {
            val fetch = pool.submit<Fetcher.Outcome> { fetcher(account).fetch() }
            assertTrue(home.statesAsked.await(5, TimeUnit.SECONDS))
            val signIn =
                pool.submit<Fetcher.SignIn> {
                    fetcher(
                        account,
                    ).signIn(SignInRequest.start(home.address), code)
                }
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(signIn.isDone)
            gate.countDown()

            assertEquals(Fetcher.Outcome.Fetched, fetch.get(5, TimeUnit.SECONDS))
            assertEquals(Fetcher.SignIn.SignedIn, signIn.get(5, TimeUnit.SECONDS))
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }

        assertNull(snapshots.read())
    }

    @Test(timeout = 10_000)
    fun `without a sign-in, what stops the fetches runs before a waiting sign-in is kept`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            var stateWhileStopping: Account.State? = null
            val outcome =
                fetcher.fetch {
                    val signIn = pool.submit { fetcher(account).signIn(SignInRequest.start(home.address), code) }
                    Thread.sleep(SETTLE_MILLIS)
                    stateWhileStopping = account.state
                    assertFalse(signIn.isDone)
                }

            assertEquals(Fetcher.Outcome.NotSignedIn, outcome)
            assertEquals(Account.State.SIGNED_OUT, stateWhileStopping)
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `no token, code or address shows in the log, on success or failure`() {
        fetcher.signIn(SignInRequest.start(home.address), code)
        fetcher.fetch()
        fetcher.signOut()
        signIn()
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        fetcher.fetch()
        fetcher.signIn(SignInRequest.start(home.address), code)
        signIn()
        home.close()
        fetcher.fetch()
        fetcher.signIn(SignInRequest.start(home.address), code)
        fetcher.signOut()

        val all = ShadowLog.getLogs().joinToString("\n") { it.msg + it.throwable }
        // An exception's message names the host and its port ("localhost/127.0.0.1:41234"); the port alone is enough.
        for (secret in listOf("refresh-1", "access-1", "code-1", ":${home.url.port}")) {
            assertFalse(secret, all.contains(secret))
        }
    }

    private companion object {
        /** Long enough for the sign-out to reach the lock, were there none. */
        const val SETTLE_MILLIS = 300L
    }
}
