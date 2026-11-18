package dev.cupr.weather

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The app's version is the one release-please writes into version.txt, and its version code follows from it by the
 * rule in build.gradle.kts: major × 1,000,000 + minor × 1,000 + patch.
 */
@RunWith(RobolectricTestRunner::class)
class VersionTest {
    @Test
    fun `the version is version txt's and the version code follows from it`() {
        val version = File("../version.txt").readText().trim()
        val (major, minor, patch) = version.split(".").map(String::toInt)
        val application = ApplicationProvider.getApplicationContext<Application>()

        val info = application.packageManager.getPackageInfo(application.packageName, 0)

        assertEquals(version, info.versionName)
        assertEquals(major * 1_000_000L + minor * 1_000L + patch, info.longVersionCode)
    }
}
