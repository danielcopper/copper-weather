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
    fun `the conditions whose image draws falling rain, snow or hail are the wet ones`() {
        assertEquals(
            setOf("hail", "lightning-rainy", "pouring", "rainy", "snowy", "snowy-rainy"),
            Condition.entries
                .filter { it.wet }
                .map { it.key }
                .toSet(),
        )
        assertEquals(15, Condition.entries.size)
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

    /**
     * Runs [command] in the repository's root; its exit code and what it wrote. The output goes to a file, so a command
     * that hangs fails the test after [timeout] seconds rather than blocking a read of its output.
     */
    private fun run(
        vararg command: String,
        timeout: Long = TIMEOUT_SECONDS,
    ): Pair<Int, String> {
        val log = File.createTempFile("weather-icons", ".log")
        try {
            val process =
                ProcessBuilder(*command)
                    .directory(root)
                    .redirectErrorStream(true)
                    .redirectOutput(log)
                    .start()
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("${command.joinToString(" ")} did not end within $timeout s")
            }
            return process.exitValue() to log.readText()
        } finally {
            log.delete()
        }
    }

    @Test
    fun `a command that does not end fails the test once its time is up`() {
        val failure = runCatching { run("python3", "-I", "-B", "-c", "import time; time.sleep(60)", timeout = 1) }

        assertEquals(
            "python3 -I -B -c import time; time.sleep(60) did not end within 1 s",
            failure.exceptionOrNull()?.message,
        )
    }

    private companion object {
        const val ICON_DP = 64
        const val TIMEOUT_SECONDS = 60L
    }
}
