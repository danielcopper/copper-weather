package dev.cupr.weather.signin

import android.content.Context
import androidx.annotation.StringRes
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.Account

/** What the sign-in screen shows about the sign-in. */
data class Status(
    val state: Account.State,
    /** The stored address, the instance's root without its slash, for the field; empty until one is first entered. */
    val address: String,
) {
    /** The line under the title while signed in; none while signed out, when the address form shows instead. */
    @get:StringRes
    val line: Int?
        get() = if (state == Account.State.SIGNED_IN) R.string.sign_in_no_fetch else null

    companion object {
        /** Reads the account; off the main thread, the preferences may read a file. */
        fun read(context: Context): Status {
            val account = Services.account(context)
            return Status(
                state = account.state,
                address =
                    account.address
                        ?.url
                        ?.toString()
                        ?.removeSuffix("/")
                        .orEmpty(),
            )
        }
    }
}
