package dev.cupr.weather.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the tests run the repository's scripts ([Scripts]). */
class ScriptsTest {
    @Test
    fun `a command that does not end fails the test once its time is up`() {
        val failure =
            runCatching { Scripts.run("python3", "-I", "-B", "-c", "import time; time.sleep(60)", timeout = 1) }

        assertEquals(
            "python3 -I -B -c import time; time.sleep(60) did not end within 1 s",
            failure.exceptionOrNull()?.message,
        )
    }
}
