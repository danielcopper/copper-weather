package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every colour of the app lives in [Palette] (D7): no other file of the app's sources holds a colour literal. The
 * one exception is the launcher icon's colours, which an adaptive icon can only take from the resources; they
 * mirror tokens of the palette (AppIdentityTest keeps them equal). No built-in rule of detekt or Android Lint finds
 * colour literals in Kotlin and in the resources alike, so this test reads the sources; the test task declares them
 * as its input (build.gradle.kts).
 */
class ColorLiteralTest {
    private val main = File("src/main")

    @Test
    fun `no source outside the palette holds a colour literal`() {
        val found =
            main
                .walkTopDown()
                .filter { it.isFile && it.relativeTo(main).invariantSeparatorsPath !in exempt }
                .toList()
        assertTrue("No sources found under ${main.absolutePath}", found.any { it.extension == "kt" })

        val literals =
            found.flatMap { file ->
                val patterns = if (file.extension == "kt") kotlinLiterals else resourceLiterals
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(main)}:${index + 1}: ${line.trim()}".takeIf {
                        patterns.any { it.containsMatchIn(line) }
                    }
                }
            }

        assertEquals("Colour literals outside the palette", emptyList<String>(), literals)
    }

    @Test
    fun `the resources' only colours are the palette's surface and sun`() {
        val names =
            resourceColors.map { path ->
                COLOR_NAME
                    .findAll(File(main, path).readText())
                    .map { it.groupValues[1] }
                    .toSet()
            }

        for (colors in names) {
            assertEquals(setOf("palette_surface", "palette_sun"), colors)
        }
    }

    @Test
    fun `the patterns find the literals they are meant for`() {
        val kotlin =
            listOf(
                "val c = 0xFF1A1F25.toInt()",
                "Color(0xFF1A1F25)",
                "Color(red = 0.1f, green = 0.2f, blue = 0.3f)",
                "Color.parseColor(\"#1a1f25\")",
                "android.graphics.Color.rgb(1, 2, 3)",
                "Color.White",
                "paint.color = Color.BLACK",
            )
        val resources = listOf("android:fillColor=\"#FF000000\"", "<item>#fff</item>", "@android:color/white")

        for (line in kotlin) assertTrue(line, kotlinLiterals.any { it.containsMatchIn(line) })
        for (line in resources) assertTrue(line, resourceLiterals.any { it.containsMatchIn(line) })
        assertTrue(kotlinLiterals.none { it.containsMatchIn("Color(Palette.color(token, dark))") })
        assertTrue(resourceLiterals.none { it.containsMatchIn("android:fillColor=\"@color/palette_sun\"") })
    }

    private companion object {
        val resourceColors = listOf("res/values/colors.xml", "res/values-night/colors.xml")

        /** The palette itself, and the resources that mirror its tokens for the launcher icon. */
        val exempt =
            setOf(
                "kotlin/dev/cupr/weather/palette/Palette.kt",
            ) + resourceColors

        val COLOR_NAME = Regex("""<color\s+name="([^"]+)"""")

        val kotlinLiterals =
            listOf(
                // A colour as a hex number, RGB or ARGB.
                Regex("""\b0[xX](\p{XDigit}{6}|\p{XDigit}{8})\b"""),
                // A colour as a "#rrggbb" string.
                Regex("""#(\p{XDigit}{3,4}|\p{XDigit}{6}|\p{XDigit}{8})\b"""),
                // Compose's Color built from numbers.
                Regex("""\bColor\(\s*(\d|red\s*=)"""),
                // Android's and Compose's ready colours and colour factories.
                Regex("""\bColor\.(parseColor|rgb|argb|valueOf|pack)\b"""),
                Regex("""\bColor\.(Black|DarkGray|Gray|LightGray|White|Red|Green|Blue|Yellow|Cyan|Magenta)\b"""),
                Regex("""\bColor\.(BLACK|DKGRAY|GRAY|LTGRAY|WHITE|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA)\b"""),
            )

        val resourceLiterals =
            listOf(
                Regex("""#(\p{XDigit}{3,4}|\p{XDigit}{6}|\p{XDigit}{8})\b"""),
                // Android's own colour resources.
                Regex("""@android:color/"""),
            )
    }
}
