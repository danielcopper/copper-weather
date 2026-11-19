package dev.cupr.weather.signin

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.ha.HaAddress
import dev.cupr.weather.store.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `signed in, the line says so, and signed out there is none`() {
        assertEquals(
            "Angemeldet, noch kein Abruf",
            context.getString(checkNotNull(Status(Account.State.SIGNED_IN, "").line)),
        )
        assertEquals(R.string.sign_in_no_fetch, Status(Account.State.SIGNED_IN, "").line)
        assertNull(Status(Account.State.SIGNED_OUT, "https://home.example").line)
    }

    @Test
    fun `the status reads the state and the address without its slash, or none before one is entered`() {
        assertEquals(Status(Account.State.SIGNED_OUT, ""), Status.read(context))

        Services.account(context).rememberAddress(checkNotNull(HaAddress.stored("https://home.example/")))

        assertEquals(Status(Account.State.SIGNED_OUT, "https://home.example"), Status.read(context))
    }
}
