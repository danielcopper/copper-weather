package dev.cupr.weather.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import dev.cupr.weather.fetch.FetchWork
import dev.cupr.weather.fetch.Fetcher
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.AesGcmCipher
import dev.cupr.weather.store.AppSettingsStore
import dev.cupr.weather.store.FailedFetchStore
import dev.cupr.weather.store.KeystoreKey
import dev.cupr.weather.store.SnapshotStore
import dev.cupr.weather.store.TokenCipher
import dev.cupr.weather.store.WidgetSettingsStore
import dev.cupr.weather.widget.Widgets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * The app's one sign-in, snapshot, settings, widgets' settings and HTTP client, and what the app's screen and the
 * fetch do with them, with
 * the fetches scheduled and the widgets updated to match (D27, D28, D31, D33).
 */
object Services {
    private const val ACCOUNT = "account"
    private const val SETTINGS = "settings"
    private const val WIDGETS = "widgets"
    private const val SNAPSHOT = "snapshot.json"
    private const val CALL_TIMEOUT_SECONDS = 30L

    /**
     * The client of every HTTP call the app itself makes (the login page's WebView loads on its own); tests replace it
     * to reach their fake Home Assistant.
     */
    @set:VisibleForTesting
    var http: OkHttpClient = OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    /** The refresh token's cipher; tests replace it, since Robolectric has no Android Keystore. */
    @VisibleForTesting
    var cipher: () -> TokenCipher = { AesGcmCipher(KeystoreKey::get) }

    /** Where the app's screen and its flow do their disk and network work, off the main thread. */
    @set:VisibleForTesting
    var io: CoroutineDispatcher = Dispatchers.IO

    fun account(context: Context) = Account(prefs(context), cipher())

    fun snapshots(context: Context) = SnapshotStore(File(context.filesDir, SNAPSHOT))

    fun failures(context: Context) = FailedFetchStore(prefs(context))

    /** The settings set once for the app, which signing out keeps (D28). */
    fun settings(context: Context) = AppSettingsStore(context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE))

    /** Each widget's settings from its configuration screen (D44). */
    fun widgetSettings(context: Context) =
        WidgetSettingsStore(context.getSharedPreferences(WIDGETS, Context.MODE_PRIVATE))

    fun fetcher(context: Context) =
        Fetcher(account(context), snapshots(context), failures(context), http, Clock.systemDefaultZone()) {
            online(context)
        }

    /**
     * Whether the phone has a network meant to reach the internet, as the system sees it now (true when it cannot
     * tell).
     */
    internal fun online(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Keeps a new sign-in, updates the widgets, which have no snapshot to show until the first fetch, and fetches at
     * once (D28, D33), also when the update throws; true when signed in. A failed sign-in updates the widgets too,
     * since a delete of the earlier snapshot that failed part-way may have changed what they show (D31).
     */
    fun signIn(
        context: Context,
        request: SignInRequest,
        code: AuthorizationCode,
    ): Boolean {
        val signedIn = fetcher(context).signIn(request, code) == Fetcher.SignIn.SignedIn
        runThen({ Widgets.update(context) }) { if (signedIn) FetchWork.start(context) }
        return signedIn
    }

    /**
     * Signs out (D27) and updates the widgets, which then say that no one is signed in (D33). The fetches stop before
     * and again after it, so a fetch scheduled while it waits for a running one, when the screen opens, does not
     * outlive it (D31). The second stop and the update run also when the sign-out throws.
     */
    fun signOut(context: Context) {
        FetchWork.stop(context)
        runThen({ fetcher(context).signOut() }) {
            runThen({ FetchWork.stop(context) }) { Widgets.update(context) }
        }
    }

    /** The app's screen opened: while signed in, it fetches at once and keeps the periodic fetch (D28). */
    fun opened(context: Context) {
        if (account(context).state == Account.State.SIGNED_IN) FetchWork.start(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(ACCOUNT, Context.MODE_PRIVATE)
}
