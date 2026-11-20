package dev.cupr.weather.widget

import android.content.Context
import androidx.annotation.StringRes
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.Snapshot
import java.math.BigDecimal
import java.math.RoundingMode

/** What the widget shows (D33): the snapshot's weather, or a plain text when there is none to show. */
sealed interface WidgetContent {
    /** No weather to show: a plain text on the card. */
    data class Plain(
        @param:StringRes val text: Int,
    ) : WidgetContent

    /**
     * The snapshot's weather: its [temperature] as "7°" and its [condition] as a German word, each none when the
     * snapshot lacks it or the app does not know the condition; and whether the sign-in is no longer usable, which
     * shows "Neu anmelden" below ([signInAgain]).
     */
    data class Weather(
        val temperature: String?,
        @param:StringRes val condition: Int?,
        val signInAgain: Boolean,
    ) : WidgetContent

    companion object {
        /** What the widget shows now, from the sign-in and the snapshot the app keeps. */
        fun read(context: Context): WidgetContent =
            of(Services.account(context).state, Services.snapshots(context).read())

        /**
         * What the widget shows for [state] and [snapshot] (D33): signed in without a snapshot, "Noch keine Daten";
         * without a snapshot otherwise, or signed out, "Nicht angemeldet"; else the snapshot's weather, with
         * "Neu anmelden" while the sign-in is refused or unreadable.
         */
        fun of(
            state: Account.State,
            snapshot: Snapshot?,
        ): WidgetContent =
            when {
                snapshot == null && state == Account.State.SIGNED_IN -> {
                    Plain(R.string.widget_no_data)
                }

                snapshot == null || state == Account.State.SIGNED_OUT -> {
                    Plain(R.string.widget_signed_out)
                }

                else -> {
                    Weather(
                        temperature = snapshot.temperature?.let(::degrees),
                        condition = snapshot.condition?.let(Conditions::word),
                        signInAgain = state == Account.State.REJECTED || state == Account.State.UNREADABLE,
                    )
                }
            }

        /**
         * [celsius] in whole degrees, as the dashboard shows it (D33): a half away from zero, as the browser's number
         * format rounds, so 2.5 is "3°" and -2.5 "-3°"; what rounds to zero is "0°".
         */
        internal fun degrees(celsius: Double): String =
            BigDecimal.valueOf(celsius).setScale(0, RoundingMode.HALF_UP).toPlainString() + "°"
    }
}
