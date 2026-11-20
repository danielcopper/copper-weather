package dev.cupr.weather.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Whether the phone has a network, as a failed fetch names its cause by it (D28). */
@RunWith(RobolectricTestRunner::class)
class ServicesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    /** The active network with [capabilities]. */
    private fun network(vararg capabilities: Int) {
        val network = checkNotNull(connectivity.activeNetwork) { "Robolectric starts with an active network" }
        val shadow = ShadowNetworkCapabilities.newInstance()
        capabilities.forEach { shadowOf(shadow).addCapability(it) }
        shadowOf(connectivity).setNetworkCapabilities(network, shadow)
    }

    @Test
    fun `a network meant to reach the internet counts as online`() {
        network(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        assertTrue(Services.online(application))
    }

    @Test
    fun `a network not meant to reach the internet does not`() {
        network(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        assertFalse(Services.online(application))
    }

    @Test
    fun `no network does not`() {
        shadowOf(connectivity).setActiveNetworkInfo(null)

        assertFalse(Services.online(application))
    }
}
