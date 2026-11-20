package dev.cupr.weather.app

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the merged manifest gives the app for the fetches (D28). */
@RunWith(RobolectricTestRunner::class)
class WeatherAppTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val packageManager = application.packageManager

    @Test
    fun `WorkManager does not start with the app, only on demand`() {
        assertTrue(application.javaClass.name, application is WeatherApp)
        val startup =
            packageManager.getProviderInfo(
                ComponentName(application, "androidx.startup.InitializationProvider"),
                PackageManager.GET_META_DATA,
            )
        val keys = startup.metaData?.keySet().orEmpty()
        assertFalse(keys.toString(), "androidx.work.WorkManagerInitializer" in keys)
    }

    @Test
    fun `the launcher opens the app's screen`() {
        val launch = packageManager.getLaunchIntentForPackage(application.packageName)

        assertEquals(MainActivity::class.java.name, launch?.component?.className)
    }

    @Test
    fun `the app may use the internet and see whether it has a network`() {
        val info = packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)

        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        // Services.online asks the ConnectivityManager, which throws without it.
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.ACCESS_NETWORK_STATE))
    }
}
