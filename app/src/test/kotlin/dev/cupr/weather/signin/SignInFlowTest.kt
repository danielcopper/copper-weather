package dev.cupr.weather.signin

import android.app.Application
import android.util.Log
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.app.Services
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.FakeHomeAssistant
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.TokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class SignInFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private val code = AuthorizationCode("code-1")

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
    }

    @After
    fun tearDown() {
        home.tokenGate?.countDown()
        awaitIdle()
        Services.cipher = keystoreCipher
        home.close()
        SignInFlow.clearProblem()
    }

    private fun awaitIdle() = runBlocking { withTimeout(5_000) { SignInFlow.state.first { !it.busy } } }

    @Test(timeout = 10_000)
    fun `a second sign-in while one runs is refused, and the result stays held after the run`() {
        val gate = CountDownLatch(1)
        home.tokenGate = gate
        val finished = SignInFlow.state.value.finished

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertTrue(home.tokenAsked.await(5, TimeUnit.SECONDS))
        assertTrue(SignInFlow.state.value.busy)
        assertFalse(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertFalse(SignInFlow.signOut(context))
        gate.countDown()
        awaitIdle()

        assertEquals(finished + 1, SignInFlow.state.value.finished)
        assertNull(SignInFlow.state.value.problem)
        assertEquals(Account.State.SIGNED_IN, Services.account(context).state)
        assertEquals(1, home.requests.count { it.first.url.encodedPath == "/auth/token" })
    }

    @Test(timeout = 10_000)
    fun `a sign-in Home Assistant refuses leaves its problem for the screen`() {
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        awaitIdle()

        assertEquals(Problem.FAILED, SignInFlow.state.value.problem)
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
    }

    @Test(timeout = 10_000)
    fun `a new run clears the problem of the one before, and a refused start is logged`() {
        SignInFlow.fail(Problem.UNREACHABLE)
        val gate = CountDownLatch(1)
        home.tokenGate = gate

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertNull(SignInFlow.state.value.problem)
        assertTrue(home.tokenAsked.await(5, TimeUnit.SECONDS))
        assertFalse(SignInFlow.signOut(context))
        gate.countDown()
        awaitIdle()

        val logs = ShadowLog.getLogsForTag(SIGN_IN_TAG).joinToString { it.msg }
        assertTrue(logs, logs.contains("A sign-in or sign-out is under way; another was not started"))
    }

    @Test(timeout = 10_000)
    fun `a run that throws ends, says the sign-in failed and logs the exception's class`() {
        Services.cipher = { throw IllegalStateException("broken") }
        val io = Services.io
        // The run and the handling of its exception happen at once, so the log is there when the call returns.
        Services.io = Dispatchers.Unconfined
        try {
            assertTrue(SignInFlow.signOut(context))
        } finally {
            Services.io = io
        }

        assertFalse(SignInFlow.state.value.busy)
        assertEquals(Problem.FAILED, SignInFlow.state.value.problem)
        val errors = ShadowLog.getLogsForTag(SIGN_IN_TAG).filter { it.type == Log.ERROR }.map { it.msg }
        assertEquals(listOf("A sign-in or sign-out failed: IllegalStateException"), errors)
    }

    @Test
    fun `a pending sign-in survives a configuration change`() {
        val request = SignInRequest.start(HaAddress.parse("https://home.example")!!)

        val saved = with(RequestSaver) { SaverScope { true }.save(request) }!!
        val restored = RequestSaver.restore(saved)!!

        assertEquals(request.address, restored.address)
        assertEquals(request.authorizeUrl, restored.authorizeUrl)
        assertEquals(
            "https://home.example/".toHttpUrl(),
            restored.address.url,
        )
    }
}
