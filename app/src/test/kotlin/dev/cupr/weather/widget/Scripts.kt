package dev.cupr.weather.widget

import java.io.File
import java.util.concurrent.TimeUnit

/** The repository's own scripts, as the tests run them (D38, D48). */
internal object Scripts {
    private const val TIMEOUT_SECONDS = 60L

    /** The repository's root; the tests run in the app module's folder. */
    private val root = File("..").canonicalFile

    /**
     * Runs [command] in the repository's root; its exit code and what it wrote. The output goes to a file, so a command
     * that hangs fails the test after [timeout] seconds rather than blocking a read of its output.
     */
    fun run(
        vararg command: String,
        timeout: Long = TIMEOUT_SECONDS,
    ): Pair<Int, String> {
        val log = File.createTempFile("script", ".log")
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
}
