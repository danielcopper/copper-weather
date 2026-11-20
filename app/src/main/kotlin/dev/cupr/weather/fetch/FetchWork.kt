package dev.cupr.weather.fetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.cupr.weather.app.Services
import dev.cupr.weather.app.runThen
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.FetchInterval
import dev.cupr.weather.widget.Widgets
import java.util.concurrent.TimeUnit

/**
 * Runs one fetch ([Fetcher]) and then updates the widgets, whatever its outcome, also when it throws, so they show a
 * new snapshot or that signing in again is needed (D33). A failed fetch is not retried on its own: the next periodic
 * fetch tries again (D28). Without a usable sign-in the periodic fetch stops, decided under the fetch's lock, until
 * the next sign-in starts it again (D31).
 */
class FetchWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val outcome =
            runThen({ Services.fetcher(applicationContext).fetch { FetchWork.stopPeriodic(applicationContext) } }) {
                Widgets.update(applicationContext)
            }
        return if (outcome is Fetcher.Outcome.Failed) Result.failure() else Result.success()
    }
}

/**
 * When the app fetches (D28): at the interval set on the app's screen, and at once after signing in and whenever the
 * app's screen opens; each only with a network connection.
 */
object FetchWork {
    /** The periodic fetch's unique name. */
    const val PERIODIC = "fetch"

    /** The fetch started at once; the app's screen watches it to show the new fetch time. */
    const val NOW = "fetch-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Fetches now, as expedited work where the system's quota allows, and keeps the periodic fetch scheduled. */
    fun start(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, nowRequest())
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodicRequest(context))
    }

    /** Keeps a new fetch interval and, while signed in, enqueues the periodic fetch anew at it (D28). */
    fun setInterval(
        context: Context,
        interval: FetchInterval,
    ) {
        Services.settings(context).writeFetchInterval(interval)
        if (Services.account(context).state == Account.State.SIGNED_IN) {
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodicRequest(context))
        }
    }

    /** The periodic fetch at the interval set on the app's screen, with a network. */
    internal fun periodicRequest(context: Context) =
        PeriodicWorkRequestBuilder<FetchWorker>(Services.settings(context).fetchInterval().minutes, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()

    /** The fetch at once: expedited where the system's quota allows, else as soon as it may run, with a network. */
    internal fun nowRequest() =
        OneTimeWorkRequestBuilder<FetchWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    /** Stops both fetches, on signing out. */
    fun stop(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(NOW)
        manager.cancelUniqueWork(PERIODIC)
    }

    /** Stops the periodic fetch, when there is no usable sign-in to fetch with. */
    fun stopPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
