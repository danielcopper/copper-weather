package dev.cupr.weather.signin

import android.util.Log
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.RefreshToken
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.ha.TokenClient
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.CipherFailureException
import okhttp3.OkHttpClient

/**
 * The sign-in's start and end with Home Assistant: keeping a new sign-in (D22, D26) and signing out (D27).
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
     * Trades the code [request]'s redirect carried for a refresh token and keeps it (D26). When the token cannot be
     * sealed, Home Assistant is asked to end it again, so it is not left valid there unused, and nothing is kept.
     */
    fun signIn(
        request: SignInRequest,
        code: AuthorizationCode,
    ): SignIn {
        val tokens = TokenClient(http, request.address)
        val outcome =
            when (val exchange = tokens.exchange(code, request)) {
                is TokenClient.Exchange.Failed -> SignIn.Failed("Home Assistant gave no tokens: ${exchange.reason}")
                is TokenClient.Exchange.Signed -> keep(request.address, exchange.refreshToken, tokens)
            }
        when (outcome) {
            SignIn.SignedIn -> Log.i(TAG, "Signed in")
            is SignIn.Failed -> Log.w(TAG, "Sign-in failed: ${outcome.reason}")
        }
        return outcome
    }

    private fun keep(
        address: HaAddress,
        refreshToken: RefreshToken,
        tokens: TokenClient,
    ): SignIn =
        try {
            account.signIn(address, SignInRequest.CLIENT_ID, refreshToken)
            SignIn.SignedIn
        } catch (e: CipherFailureException) {
            SignIn.Failed("the token cannot be sealed (${e.message}); ${revoked(tokens.revoke(refreshToken))}")
        }

    /**
     * Signs out ("Abmelden", D27): asks Home Assistant to end the refresh token, then forgets it; the address stays.
     * The sign-in on the phone goes even when asking Home Assistant fails or the stored token cannot be read.
     */
    fun signOut() {
        var asked = "Home Assistant was not asked to end the refresh token"
        try {
            asked =
                when (val opened = account.open()) {
                    Account.Opened.NotSignedIn -> {
                        "nothing was stored"
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
            Log.i(TAG, "Signed out; $asked")
        }
    }

    private fun revoked(revoke: TokenClient.Revoke): String =
        when (revoke) {
            TokenClient.Revoke.Ended -> {
                "Home Assistant ended the refresh token"
            }

            is TokenClient.Revoke.Failed -> {
                "Home Assistant could not be asked to end the refresh token (${revoke.reason}); $UNTIL_UNUSED"
            }
        }

    private fun notAsked(reason: String) =
        "the stored token cannot be read ($reason), so Home Assistant was not asked to end it; $UNTIL_UNUSED"

    private companion object {
        const val TAG = "Account"

        /** Home Assistant's refresh tokens expire after 90 days unused (`auth/const.py` at Core 2026.11.1). */
        const val UNTIL_UNUSED = "it stays valid there until it goes 90 days unused"
    }
}
