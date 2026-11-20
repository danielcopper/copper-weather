package dev.cupr.weather.signin

import android.content.Context
import androidx.annotation.StringRes
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.FailedFetch
import dev.cupr.weather.store.FetchCause
import dev.cupr.weather.store.FetchInterval
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the app's screen shows about the sign-in and the fetches. */
data class Status(
    val state: Account.State,
    /** The stored address, the instance's root without its slash, for the field; empty until one is first entered. */
    val address: String,
    /** When the snapshot was fetched; none without one. */
    val fetchedAt: Instant? = null,
    /** The last fetch, while it failed (D28). */
    val failed: FailedFetch? = null,
    /** The snapshot's attribution (D18); none without a snapshot or one without it. */
    val attribution: String? = null,
    /** How often the app fetches, set on this screen (D28). */
    val fetchInterval: FetchInterval = FetchInterval.DEFAULT,
) {
    /**
     * The line under the title: while signed in, when the last successful fetch was (the snapshot's time), or that
     * none was yet; else why signing in
     * again is needed (D32); none while signed out, when the address form shows instead.
     */
    fun line(
        context: Context,
        now: Instant,
        zone: ZoneId,
    ): String? =
        when (state) {
            Account.State.SIGNED_OUT -> {
                null
            }

            Account.State.REJECTED -> {
                context.getString(R.string.sign_in_rejected)
            }

            Account.State.UNREADABLE -> {
                context.getString(R.string.sign_in_unreadable)
            }

            Account.State.SIGNED_IN -> {
                fetchedAt?.let {
                    at(
                        context,
                        it,
                        now,
                        zone,
                        R.string.sign_in_status_today,
                        R.string.sign_in_status_day,
                    )
                }
                    ?: context.getString(R.string.sign_in_no_fetch)
            }
        }

    /** The line under it while signed in and the last fetch failed: when, and its cause (D28). */
    fun failedLine(
        context: Context,
        now: Instant,
        zone: ZoneId,
    ): String? =
        failed?.takeIf { state == Account.State.SIGNED_IN }?.let {
            val cause = context.getString(it.cause.text)
            at(context, it.at, now, zone, R.string.fetch_failed_today, R.string.fetch_failed_day, cause)
        }

    /**
     * [today]'s text with [subject], if any, and the time when [at] is today, else [day]'s with its date as well; the
     * time and the date follow the subject in the texts' arguments.
     */
    private fun at(
        context: Context,
        at: Instant,
        now: Instant,
        zone: ZoneId,
        @StringRes today: Int,
        @StringRes day: Int,
        subject: String? = null,
    ): String {
        val local = at.atZone(zone)
        val time = local.format(CLOCK_TIME)
        val isToday = local.toLocalDate() == now.atZone(zone).toLocalDate()
        val args = listOfNotNull(subject, time) + listOfNotNull(local.format(DAY).takeUnless { isToday })
        return context.getString(if (isToday) today else day, *args.toTypedArray())
    }

    /** The cause in plain words (D28, D30); the log names the technical one. */
    private val FetchCause.text: Int
        get() =
            when (this) {
                FetchCause.UNREACHABLE -> R.string.fetch_cause_unreachable
                FetchCause.NO_NETWORK -> R.string.fetch_cause_no_network
                FetchCause.TIMEOUT -> R.string.fetch_cause_timeout
                FetchCause.SENSOR_MISSING -> R.string.fetch_cause_sensor_missing
                FetchCause.REFUSED -> R.string.fetch_cause_refused
                FetchCause.UNEXPECTED -> R.string.fetch_cause_unexpected
                FetchCause.PHONE -> R.string.fetch_cause_phone
                FetchCause.NEWER_SCHEMA -> R.string.fetch_cause_newer_schema
            }

    companion object {
        private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

        /**
         * Reads the account, the snapshot, the last failure and the fetch interval; off the main thread, the snapshot
         * is a file.
         */
        fun read(context: Context): Status {
            val account = Services.account(context)
            val snapshot = Services.snapshots(context).read()
            return Status(
                state = account.state,
                address =
                    account.address
                        ?.url
                        ?.toString()
                        ?.removeSuffix("/")
                        .orEmpty(),
                fetchedAt = snapshot?.fetchedAt,
                failed = Services.failures(context).read(),
                attribution = snapshot?.data?.attribution,
                fetchInterval = Services.settings(context).fetchInterval(),
            )
        }
    }
}
