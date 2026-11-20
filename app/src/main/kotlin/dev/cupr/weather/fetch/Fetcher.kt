package dev.cupr.weather.fetch

import android.util.Log
import dev.cupr.weather.ha.AuthorizationCode
import dev.cupr.weather.ha.FailureKind
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.HaClient
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.SignInRequest
import dev.cupr.weather.ha.TokenClient
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.CipherFailureException
import dev.cupr.weather.store.FailedFetch
import dev.cupr.weather.store.FailedFetchStore
import dev.cupr.weather.store.FetchCause
import dev.cupr.weather.store.Snapshot
import dev.cupr.weather.store.SnapshotStore
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Clock

/**
 * The sign-in's lifetime with Home Assistant: keeping a new sign-in (D22, D26), each fetch (D28) and signing out (D27),
 * all under one lock (D31).
 *
 * A fetch asks for a new access token with the stored refresh token, reads the app sensor's `data` and, when its schema
 * version is the one the app reads (D30), replaces the snapshot with it whole (D29). When Home Assistant refuses the
 * refresh token, the account is marked [Account.State.REJECTED]; when the stored sign-in cannot be read on this phone,
 * [Account.State.UNREADABLE]; the snapshot stays in both cases (D32). Any other failure changes nothing but the last
 * failure kept for the app's screen, and the next fetch tries again (D28).
 *
 * The log names what failed, never a token, the code or the address.
 */
class Fetcher(
    private val account: Account,
    private val snapshots: SnapshotStore,
    private val failures: FailedFetchStore,
    private val http: OkHttpClient,
    private val clock: Clock,
    /** Whether the phone has a network now; a fetch that cannot reach Home Assistant without one says so. */
    private val online: () -> Boolean,
) {
    sealed interface Outcome {
        data object NotSignedIn : Outcome

        data object Rejected : Outcome

        data object Unreadable : Outcome

        /** [reason] for the log, [cause] for the screen; neither holds a secret. */
        data class Failed(
            val reason: String,
            val cause: FetchCause,
        ) : Outcome

        data object Fetched : Outcome
    }

    sealed interface SignIn {
        data object SignedIn : SignIn

        /** The sign-in did not come about; [reason] holds no secret. */
        data class Failed(
            val reason: String,
        ) : SignIn
    }

    /**
     * Trades the code [request]'s redirect carried for a refresh token and keeps it with the `client_id` it was issued
     * to (D23, D26); the snapshot and the last failure of an earlier sign-in go (D31). When the token cannot be sealed,
     * or the earlier snapshot cannot be deleted, Home Assistant is asked to end it again, so it is not left valid there
     * unused, and nothing is kept: the account stays as it was.
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
            SignIn.SignedIn -> Log.i(ACCOUNT_TAG, "Signed in")
            is SignIn.Failed -> Log.w(ACCOUNT_TAG, "Sign-in failed: ${outcome.reason}")
        }
        return outcome
    }

    private fun keep(
        address: HaAddress,
        signed: TokenClient.Exchange.Signed,
        tokens: TokenClient,
    ): SignIn =
        try {
            // What the fetches kept goes once the token is sealed and before it is kept, so a snapshot that cannot be
            // deleted fails the sign-in with the account as it was.
            synchronized(LOCK) { account.signIn(address, signed.clientId, signed.refreshToken, ::forgetKept) }
            SignIn.SignedIn
        } catch (e: CipherFailureException) {
            val revoked = revoked(tokens.revoke(signed.refreshToken))
            SignIn.Failed("the token cannot be sealed (${e.message}); ${revoked.text}")
        } catch (e: IOException) {
            val revoked = revoked(tokens.revoke(signed.refreshToken))
            SignIn.Failed("the earlier snapshot cannot be deleted (${e.message}); ${revoked.text}")
        }

    /**
     * One fetch. Without a usable sign-in, [onNoSignIn] runs before the lock is released, so a sign-in waiting for it,
     * which schedules the fetches anew, comes after whatever [onNoSignIn] stops (D31).
     */
    fun fetch(onNoSignIn: () -> Unit = {}): Outcome =
        synchronized(LOCK) {
            val outcome = run()
            when (outcome) {
                is Outcome.Failed -> failures.write(FailedFetch(clock.instant(), outcome.cause))
                Outcome.Fetched -> failures.clear()
                Outcome.NotSignedIn, Outcome.Rejected, Outcome.Unreadable -> onNoSignIn()
            }
            log(outcome)
            outcome
        }

    /**
     * Signs out ("Abmelden", D27): asks Home Assistant to end the refresh token, then forgets it and deletes the
     * snapshot and the last failure; the address stays. Waits for a fetch that is running, so it cannot write its
     * snapshot afterwards (D31). The sign-in on the phone goes even when asking Home Assistant fails or the stored
     * token cannot be read; the log then warns that the token was not ended there. Throws [IOException] when the
     * snapshot cannot be deleted, after the sign-in on the phone has gone.
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
            try {
                synchronized(LOCK) {
                    account.signOut()
                    forgetKept()
                }
            } finally {
                if (asked.ended) {
                    Log.i(ACCOUNT_TAG, "Signed out; ${asked.text}")
                } else {
                    Log.w(ACCOUNT_TAG, "Signed out; ${asked.text}")
                }
            }
        }
    }

    /** Forgets what the fetches kept for the sign-in that ends: the snapshot and the last failure (D31). */
    private fun forgetKept() {
        snapshots.clear()
        failures.clear()
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

    private fun run(): Outcome =
        when (val opened = account.open()) {
            Account.Opened.NotSignedIn -> {
                Outcome.NotSignedIn
            }

            is Account.Opened.Unreadable -> {
                Log.w(FETCH_TAG, "The stored sign-in cannot be read: ${opened.reason}")
                account.markUnreadable()
                Outcome.Unreadable
            }

            is Account.Opened.Unavailable -> {
                Outcome.Failed(opened.reason, FetchCause.PHONE)
            }

            is Account.Opened.Usable -> {
                refresh(opened.session)
            }
        }

    private fun refresh(session: Account.Session): Outcome =
        when (val refresh = TokenClient(http, session.address).refresh(session.refreshToken, session.clientId)) {
            is TokenClient.Refresh.Access -> {
                read(HaClient(http, session.address).sensor(refresh.accessToken))
            }

            is TokenClient.Refresh.Rejected -> {
                Log.w(FETCH_TAG, "The refresh token was refused: ${refresh.reason}")
                account.reject()
                Outcome.Rejected
            }

            is TokenClient.Refresh.Failed -> {
                Outcome.Failed("refresh: ${refresh.reason}", causeOf(refresh.kind, online))
            }
        }

    private fun read(sensor: HaClient.Result): Outcome =
        when (sensor) {
            is HaClient.Result.Failed -> Outcome.Failed(sensor.reason, causeOf(sensor.kind, online))
            is HaClient.Result.Ok -> store(sensor.data)
        }

    private fun store(data: SensorData): Outcome =
        try {
            snapshots.write(Snapshot(clock.instant(), data))
            Outcome.Fetched
        } catch (e: IOException) {
            Outcome.Failed("the snapshot cannot be written: ${e.javaClass.simpleName}", FetchCause.PHONE)
        }

    private companion object {
        /** Home Assistant's refresh tokens expire after 90 days unused (`auth/const.py` at Core 2026.11.1). */
        const val UNTIL_UNUSED = "it stays valid there until it goes 90 days unused"

        /**
         * One lock for every Fetcher, since the app makes a new one for each use: a periodic fetch, one started at
         * once, a sign-in and a sign-out write the account and the snapshot one after the other (D31).
         */
        val LOCK = Any()
    }
}

/** The log's tag for keeping a sign-in and signing out. */
private const val ACCOUNT_TAG = "Account"

/** The log's tag for the fetches. */
private const val FETCH_TAG = "Fetch"

private fun log(outcome: Fetcher.Outcome) {
    when (outcome) {
        is Fetcher.Outcome.Failed -> {
            Log.w(FETCH_TAG, "Fetch failed: ${outcome.reason}")
        }

        Fetcher.Outcome.Fetched -> {
            Log.i(FETCH_TAG, "Fetched the app sensor")
        }

        Fetcher.Outcome.Rejected -> {
            Log.w(FETCH_TAG, "Home Assistant refused the refresh token; signing in again is needed")
        }

        Fetcher.Outcome.Unreadable -> {
            Log.w(FETCH_TAG, "The stored sign-in cannot be read on this phone; signing in again is needed")
        }

        Fetcher.Outcome.NotSignedIn -> {
            Log.i(FETCH_TAG, "Not signed in, nothing fetched")
        }
    }
}

/** The screen's cause for what failed (D28): Home Assistant out of reach without a network is the network's. */
private fun causeOf(
    kind: FailureKind,
    online: () -> Boolean,
): FetchCause =
    when (kind) {
        FailureKind.UNREACHABLE -> if (online()) FetchCause.UNREACHABLE else FetchCause.NO_NETWORK
        FailureKind.TIMEOUT -> if (online()) FetchCause.TIMEOUT else FetchCause.NO_NETWORK
        FailureKind.NOT_FOUND -> FetchCause.SENSOR_MISSING
        FailureKind.REFUSED -> FetchCause.REFUSED
        FailureKind.UNEXPECTED -> FetchCause.UNEXPECTED
        FailureKind.NEWER_SCHEMA -> FetchCause.NEWER_SCHEMA
    }
