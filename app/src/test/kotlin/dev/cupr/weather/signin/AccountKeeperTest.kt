package dev.cupr.weather.signin

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.FakeHomeAssistant
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.TokenCipher
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.security.ProviderException
import javax.crypto.KeyGenerator

/** Keeping a new sign-in and signing out, against the fake Home Assistant (D26, D27). */
@RunWith(RobolectricTestRunner::class)
class AccountKeeperTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val account = Account(prefs, AesGcmCipher { key })
    private val keeper = keeper(account)
    private val code = AuthorizationCode("code-1")

    @After
    fun stop() = home.close()

    /** Another keeper on the same preferences, as the app makes one for each use. */
    private fun keeper(account: Account) = AccountKeeper(account, OkHttpClient())

    private fun signIn() = account.signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))

    private fun cipher(open: () -> Nothing) =
        object : TokenCipher {
            override fun seal(plain: ByteArray) = AesGcmCipher { key }.seal(plain)

            override fun open(sealed: ByteArray): ByteArray = open()
        }

    private val logs: String
        get() = ShadowLog.getLogsForTag("Account").joinToString("\n") { it.msg }

    /** The level of the sign-out's log line: info when the token ended at Home Assistant or none was stored. */
    private fun signedOutLevel() = ShadowLog.getLogsForTag("Account").single { it.msg.startsWith("Signed out") }.type

    @Test
    fun `a sign-in trades the code with the verifier whose challenge the login page had and keeps the token`() {
        val request = SignInRequest.start(home.address)
        home.challenge = request.authorizeUrl.queryParameter("code_challenge")

        assertEquals(AccountKeeper.SignIn.SignedIn, keeper.signIn(request, code))

        val session = (account.open() as Account.Opened.Usable).session
        assertEquals(RefreshToken("refresh-1"), session.refreshToken)
        assertEquals(SignInRequest.CLIENT_ID, session.clientId)
        assertEquals(home.address, session.address)
        assertEquals("code-1", home.requests.single().second["code"])
    }

    @Test
    fun `a sign-in with another verifier than the challenge's keeps nothing`() {
        val request = SignInRequest.start(home.address)
        home.challenge = SignInRequest.challenge("another verifier, long enough to be one: 0123456789")

        assertEquals(
            AccountKeeper.SignIn.Failed("Home Assistant gave no tokens: HTTP 400 invalid_grant"),
            keeper.signIn(request, code),
        )

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertFalse(prefs.contains("refresh_token"))
    }

    @Test
    fun `a token that cannot be sealed is ended in Home Assistant again and nothing is kept`() {
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })

        val outcome = keeper(failing).signIn(SignInRequest.start(home.address), code)

        assertEquals(
            AccountKeeper.SignIn.Failed(
                "the token cannot be sealed (ProviderException); Home Assistant ended the refresh token",
            ),
            outcome,
        )
        val (revoke, form) = home.requests.last()
        assertEquals("/auth/revoke", revoke.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(emptySet<String>(), prefs.all.keys)
    }

    @Test
    fun `signing out ends the refresh token, forgets it and keeps the address`() {
        signIn()

        keeper.signOut()

        val (request, form) = home.requests.single()
        assertEquals("/auth/revoke", request.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(Account.Opened.NotSignedIn, account.open())
        assertEquals(home.address, account.address)
        assertTrue(logs, logs.contains("Signed out; Home Assistant ended the refresh token"))
        assertEquals(Log.INFO, signedOutLevel())
    }

    @Test
    fun `signing out with nothing stored asks Home Assistant nothing and logs it as info`() {
        keeper.signOut()

        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("Signed out; nothing was stored"))
        assertEquals(Log.INFO, signedOutLevel())
    }

    @Test
    fun `signing out that Home Assistant answers with an error warns that the token was not ended`() {
        signIn()
        home.revoke = { FakeHomeAssistant.json("Server error", 500) }

        keeper.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(logs, logs.contains("could not be asked to end the refresh token (HTTP 500)"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out without an answer from Home Assistant still signs out`() {
        signIn()
        home.close()

        keeper.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(logs, logs.contains("could not be asked to end the refresh token (ConnectException)"))
        assertTrue(logs, logs.contains("it stays valid there until it goes 90 days unused"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out with a stored token that cannot be read signs out and says Home Assistant was not asked`() {
        signIn()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        keeper(Account(prefs, AesGcmCipher { other })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("so Home Assistant was not asked to end it"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out with a Keystore that fails signs out and says Home Assistant was not asked`() {
        signIn()

        keeper(Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("the stored token cannot be read (the Keystore failed: ProviderException)"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `signing out signs out even when opening the stored sign-in throws`() {
        signIn()
        val throwing = Account(prefs, cipher { throw IllegalStateException("broken") })

        runCatching { keeper(throwing).signOut() }

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(logs, logs.contains("Signed out; Home Assistant was not asked to end the refresh token"))
        assertEquals(Log.WARN, signedOutLevel())
    }

    @Test
    fun `no token, code or address shows in the log, on success or failure`() {
        keeper.signIn(SignInRequest.start(home.address), code)
        keeper.signOut()
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        keeper.signIn(SignInRequest.start(home.address), code)
        signIn()
        home.close()
        keeper.signIn(SignInRequest.start(home.address), code)
        keeper.signOut()

        val all = ShadowLog.getLogs().joinToString("\n") { it.msg + it.throwable }
        // An exception's message names the host and its port ("localhost/127.0.0.1:41234"); the port alone is enough.
        for (secret in listOf("refresh-1", "access-1", "code-1", ":${home.url.port}")) {
            assertFalse(secret, all.contains(secret))
        }
    }
}
