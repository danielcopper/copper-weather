package dev.cupr.weather.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The weather icons (D38): the drawables in the repo are what `scripts/weather_icons.py` writes from the SVGs in
 * `icons/weather`, the script converts what it should and refuses the rest, and each condition finds its image.
 */
@RunWith(RobolectricTestRunner::class)
class WeatherIconsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    /** The repository's root; the tests run in the app module's folder. */
    private val root = File("..").canonicalFile

    @Test
    fun `the weather drawables are what the script writes from the SVGs`() {
        val (code, output) = run("python3", "-I", "-B", "scripts/weather_icons.py", "--check")

        assertEquals(output, 0, code)
    }

    @Test
    fun `the script converts what the SVGs use and refuses anything else`() {
        val (code, output) = run("python3", "-I", "-B", "-m", "unittest", "discover", "-s", "scripts", "-t", "scripts")

        assertEquals(output, 0, code)
        assertTrue(output, output.contains("OK"))
    }

    @Test
    fun `each condition shows its image, by day and by night, sunny and clear-night the same, others the warning`() {
        val conditions =
            listOf(
                "cloudy",
                "exceptional",
                "fog",
                "hail",
                "lightning",
                "lightning-rainy",
                "partlycloudy",
                "pouring",
                "rainy",
                "snowy",
                "snowy-rainy",
                "sunny",
                "windy",
                "windy-variant",
            )
        for (condition in conditions) {
            for (night in listOf(false, true)) {
                val name = "weather_${condition.replace('-', '_')}_${if (night) "night" else "day"}"
                assertEquals(name, name, application.resources.getResourceEntryName(WeatherIcons.of(condition, night)))
            }
        }
        for (night in listOf(false, true)) {
            assertEquals(WeatherIcons.of("sunny", night), WeatherIcons.of("clear-night", night))
            assertEquals(WeatherIcons.of("exceptional", night), WeatherIcons.of("sandstorm", night))
            assertEquals(WeatherIcons.of("exceptional", night), WeatherIcons.of(null, night))
        }
        assertEquals(R.drawable.weather_sunny_night, WeatherIcons.of("clear-night", night = true))
    }

    @Test
    fun `every weather drawable loads outside night mode`() {
        assertLoads()
    }

    @Test
    @Config(qualifiers = "+night")
    fun `every weather drawable loads in night mode`() {
        assertLoads()
    }

    private fun assertLoads() {
        val icons = R.drawable::class.java.fields.filter { it.name.startsWith("weather_") }
        assertEquals(28, icons.size)
        val density = application.resources.displayMetrics.density
        for (icon in icons) {
            val drawable = checkNotNull(application.getDrawable(icon.getInt(null))) { icon.name }
            assertEquals(icon.name, (ICON_DP * density).toInt(), drawable.intrinsicWidth)
        }
    }

    /** Runs [command] in the repository's root; its exit code and what it wrote. */
    private fun run(vararg command: String): Pair<Int, String> {
        val process =
            ProcessBuilder(*command)
                .directory(root)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "${command.joinToString(" ")} did not end" }
        return process.exitValue() to output
    }

    private companion object {
        const val ICON_DP = 64
        const val TIMEOUT_SECONDS = 60L
    }
}
