package dev.cupr.weather.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.signin.AccountKeeper
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.KeystoreKey
import dev.cupr.weather.store.TokenCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's one sign-in and HTTP client, and what the sign-in screen does with them (D22, D27). */
object Services {
    private const val ACCOUNT = "account"
    private const val CALL_TIMEOUT_SECONDS = 30L

    /** The client every call to Home Assistant goes through; tests replace it to reach their fake Home Assistant. */
    @set:VisibleForTesting
    var http: OkHttpClient = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    /** The refresh token's cipher; tests replace it, since Robolectric has no Android Keystore. */
    @VisibleForTesting
    var cipher: () -> TokenCipher = { AesGcmCipher(KeystoreKey::get) }

    /** Where the sign-in screen and its flow do their disk and network work, off the main thread. */
    @set:VisibleForTesting
    var io: CoroutineDispatcher = Dispatchers.IO

    fun account(context: Context) = Account(prefs(context), cipher())

    private fun keeper(context: Context) = AccountKeeper(account(context), http)

    /** Keeps a new sign-in; true when signed in. The first fetch at once after it (D20) comes with the fetch. */
    fun signIn(
        context: Context,
        request: SignInRequest,
        code: AuthorizationCode,
    ): Boolean = keeper(context).signIn(request, code) == AccountKeeper.SignIn.SignedIn

    /**
     * Signs out (D27). Nothing else runs on the sign-in yet: once the app fetches (D20), the fetches stop here, before
     * the sign-out and once more after it, so one scheduled meanwhile does not outlive the sign-in.
     */
    fun signOut(context: Context) {
        keeper(context).signOut()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(ACCOUNT, Context.MODE_PRIVATE)
}
