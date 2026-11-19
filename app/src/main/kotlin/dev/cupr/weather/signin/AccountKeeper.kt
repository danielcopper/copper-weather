package dev.cupr.weather.signin

import android.util.Log
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.ha.TokenClient
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.CipherFailureException
import okhttp3.OkHttpClient

/**
 * The sign-in's start and end with Home Assistant: keeping a new sign-in (D22, D26) and signing out (D27).
 *
 * Once the app fetches (D20), signing out stops the fetches too (D27). copper-calendar also runs its sign-in and
 * sign-out under its fetch's lock and clears what the fetches kept; whether this app does the same is the fetch's to
 * decide.
 *
 * The log names what failed, never a token, the code or the address.
 */
class AccountKeeper(
    private val account: Account,
    private val http: OkHttpClient,
) {
    sealed interface SignIn {
        data object SignedIn : SignIn

        /** The sign-in did not come about; [reason] holds no secret. */
        data class Failed(
            val reason: String,
        ) : SignIn
    }

    /**
     * Trades the code [request]'s redirect carried for a refresh token and keeps it with the `client_id` it was issued
     * to (D23, D26). When the token cannot be sealed, Home Assistant is asked to end it again, so it is not left valid
     * there unused, and nothing is kept.
     */
    fun signIn(
        request: SignInRequest,
        code: AuthorizationCode,
    ): SignIn {
        val tokens = TokenClient(http, request.address)
        val outcome =
            when (val exchange = tokens.exchange(code, request)) {
                is TokenClient.Exchange.Failed -> SignIn.Failed("Home Assistant gave no tokens: ${exchange.reason}")
                is TokenClient.Exchange.Signed -> keep(request.address, exchange, tokens)
            }
        when (outcome) {
            SignIn.SignedIn -> Log.i(TAG, "Signed in")
            is SignIn.Failed -> Log.w(TAG, "Sign-in failed: ${outcome.reason}")
        }
        return outcome
    }

    private fun keep(
        address: HaAddress,
        signed: TokenClient.Exchange.Signed,
        tokens: TokenClient,
    ): SignIn =
        try {
            account.signIn(address, signed.clientId, signed.refreshToken)
            SignIn.SignedIn
        } catch (e: CipherFailureException) {
            val revoked = revoked(tokens.revoke(signed.refreshToken))
            SignIn.Failed("the token cannot be sealed (${e.message}); ${revoked.text}")
        }

    /**
     * Signs out ("Abmelden", D27): asks Home Assistant to end the refresh token, then forgets it; the address stays.
     * The sign-in on the phone goes even when asking Home Assistant fails or the stored token cannot be read; the log
     * then warns that the token was not ended there.
     */
    fun signOut() {
        var asked = Asked("Home Assistant was not asked to end the refresh token", ended = false)
        try {
            asked =
                when (val opened = account.open()) {
                    Account.Opened.NotSignedIn -> {
                        Asked("nothing was stored", ended = true)
                    }

                    is Account.Opened.Usable -> {
                        revoked(TokenClient(http, opened.session.address).revoke(opened.session.refreshToken))
                    }

                    is Account.Opened.Unreadable -> {
                        notAsked(opened.reason)
                    }

                    is Account.Opened.Unavailable -> {
                        notAsked(opened.reason)
                    }
                }
        } finally {
            account.signOut()
            if (asked.ended) Log.i(TAG, "Signed out; ${asked.text}") else Log.w(TAG, "Signed out; ${asked.text}")
        }
    }

    /** What became of the refresh token at Home Assistant; [ended] when it ended there or nothing was stored. */
    private class Asked(
        val text: String,
        val ended: Boolean,
    )

    private fun revoked(revoke: TokenClient.Revoke): Asked =
        when (revoke) {
            TokenClient.Revoke.Ended -> {
                Asked("Home Assistant ended the refresh token", ended = true)
            }

            is TokenClient.Revoke.Failed -> {
                Asked(
                    "Home Assistant could not be asked to end the refresh token (${revoke.reason}); $UNTIL_UNUSED",
                    ended = false,
                )
            }
        }

    private fun notAsked(reason: String) =
        Asked(
            "the stored token cannot be read ($reason), so Home Assistant was not asked to end it; $UNTIL_UNUSED",
            ended = false,
        )

    private companion object {
        const val TAG = "Account"

        /** Home Assistant's refresh tokens expire after 90 days unused (`auth/const.py` at Core 2026.11.1). */
        const val UNTIL_UNUSED = "it stays valid there until it goes 90 days unused"
    }
}
