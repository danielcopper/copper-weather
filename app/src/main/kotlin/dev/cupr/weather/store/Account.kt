package dev.cupr.weather.store

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.ha.RefreshToken
import java.util.Base64

/**
 * The sign-in the app keeps, in its private preferences (D26): the Home Assistant address, entered once and kept, also
 * after signing out (D24); and while signed in the `client_id` the refresh token was issued to, in exactly that form
 * (D23), and the refresh token, sealed with [cipher]. The access token is never stored.
 */
class Account(
    private val prefs: SharedPreferences,
    private val cipher: TokenCipher,
) {
    enum class State {
        /** Never signed in, or signed out on the sign-in screen. */
        SIGNED_OUT,

        SIGNED_IN,

        /** Home Assistant refused the refresh token: sign in again (D32). */
        REJECTED,

        /** The stored sign-in cannot be read on this phone (D26): sign in again (D32). */
        UNREADABLE,
    }

    /** What a call to Home Assistant with the stored sign-in needs. */
    class Session(
        val address: HaAddress,
        val clientId: String,
        val refreshToken: RefreshToken,
    ) {
        override fun toString() = "Session(…)"
    }

    sealed interface Opened {
        data object NotSignedIn : Opened

        data class Usable(
            val session: Session,
        ) : Opened

        /**
         * Signed in, but the stored sign-in cannot be read: incomplete, not Base64, or not sealed with this key; it
         * counts as lost (D26).
         */
        data class Unreadable(
            val reason: String,
        ) : Opened

        /** The Keystore or the cipher failed; the stored sign-in may open next time (D26). */
        data class Unavailable(
            val reason: String,
        ) : Opened
    }

    val address: HaAddress?
        get() = prefs.getString(ADDRESS, null)?.let(HaAddress::stored)

    val state: State
        get() =
            when (prefs.getString(STATE, null)) {
                SIGNED_IN -> State.SIGNED_IN
                REJECTED -> State.REJECTED
                UNREADABLE -> State.UNREADABLE
                else -> State.SIGNED_OUT
            }

    fun rememberAddress(address: HaAddress) = prefs.edit { putString(ADDRESS, address.url.toString()) }

    /** Keeps a new sign-in; throws [CipherFailureException] when the token cannot be sealed, and keeps nothing then. */
    fun signIn(
        address: HaAddress,
        clientId: String,
        refreshToken: RefreshToken,
    ) {
        val sealed = Base64.getEncoder().encodeToString(cipher.seal(refreshToken.value.toByteArray(Charsets.UTF_8)))
        prefs.edit {
            putString(ADDRESS, address.url.toString())
            putString(CLIENT_ID, clientId)
            putString(REFRESH_TOKEN, sealed)
            putString(STATE, SIGNED_IN)
        }
    }

    fun open(): Opened {
        val address = address
        val clientId = prefs.getString(CLIENT_ID, null)
        val sealed = prefs.getString(REFRESH_TOKEN, null)
        val stored = address != null && clientId != null && sealed != null
        return when {
            state != State.SIGNED_IN -> Opened.NotSignedIn
            !stored -> Opened.Unreadable("the stored sign-in is incomplete")
            else -> unseal(address, clientId, sealed)
        }
    }

    private fun unseal(
        address: HaAddress,
        clientId: String,
        sealed: String,
    ): Opened {
        val bytes =
            try {
                Base64.getDecoder().decode(sealed)
            } catch (_: IllegalArgumentException) {
                return Opened.Unreadable("the stored token is not Base64")
            }
        return try {
            Opened.Usable(Session(address, clientId, RefreshToken(String(cipher.open(bytes), Charsets.UTF_8))))
        } catch (e: UnreadableTokenException) {
            Opened.Unreadable("the stored token: ${e.message}")
        } catch (e: CipherFailureException) {
            Opened.Unavailable("the Keystore failed: ${e.message}")
        }
    }

    /** Forgets the refresh token Home Assistant refused and keeps the sign-in as refused; the address stays (D32). */
    fun reject() = forget(REJECTED)

    /** Forgets a stored sign-in this phone cannot read and keeps it as unreadable; the address stays (D32). */
    fun markUnreadable() = forget(UNREADABLE)

    /** Forgets the sign-in; the address stays (D27). */
    fun signOut() = forget(null)

    private fun forget(state: String?) =
        prefs.edit {
            remove(CLIENT_ID)
            remove(REFRESH_TOKEN)
            if (state == null) remove(STATE) else putString(STATE, state)
        }

    private companion object {
        const val ADDRESS = "address"
        const val CLIENT_ID = "client_id"
        const val REFRESH_TOKEN = "refresh_token"
        const val STATE = "state"
        const val SIGNED_IN = "signed_in"
        const val REJECTED = "rejected"
        const val UNREADABLE = "unreadable"
    }
}
