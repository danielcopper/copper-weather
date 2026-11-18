package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finds colour literals in the app's Kotlin and XML sources outside [Palette] (D7), in every source set but the
 * tests': hex numbers and `"#…"` strings, Android's and Compose's colour constants and factories, Compose's `Color`
 * built from numbers or named arguments, and Android's colour resources. The resources' own colours may only mirror
 * fields of the palette (AppIdentityTest and WindowTest keep them equal). No built-in rule of detekt or Android Lint
 * finds colour literals in Kotlin and in the resources alike, so this test reads the sources; the test task declares
 * them as its input (build.gradle.kts).
 */
class ColorLiteralTest {
    private val sources = File("src")

    @Test
    fun `no source outside the palette holds a colour literal`() {
        val found =
            sources
                .walkTopDown()
                .onEnter { it == sources || it.parentFile != sources || !isTestSourceSet(it.name) }
                .filter { it.isFile && it.extension in setOf("kt", "xml") && path(it) !in exempt }
                .toList()
        assertTrue("No main sources found under ${sources.absolutePath}", found.any { path(it).startsWith("main/") })

        val literals =
            found.flatMap { file ->
                val patterns = if (file.extension == "kt") kotlinLiterals else resourceLiterals
                file.readLines().mapIndexedNotNull { index, line ->
                    "${path(file)}:${index + 1}: ${line.trim()}".takeIf { patterns.any { it.containsMatchIn(line) } }
                }
            }

        assertEquals("Colour literals outside the palette", emptyList<String>(), literals)
    }

    @Test
    fun `the resources' only colours are the palette's surface and sun`() {
        for (path in resourceColors) {
            val names =
                COLOR_NAME
                    .findAll(File(sources, path).readText())
                    .map { it.groupValues[1] }
                    .toSet()

            assertEquals(path, setOf("palette_surface", "palette_sun"), names)
        }
    }

    @Test
    fun `the patterns find the literals they are meant for`() {
        val kotlin =
            listOf(
                "val c = 0xFF1A1F25.toInt()",
                "val c = 0x1A1F25",
                "val c = 0xFF1A1F25L",
                "val c = 0xFF1A1F25u",
                "val c = 0xFF1A1F25uL",
                "Color(0xFF1A1F25)",
                "Color(0.1f, 0.2f, 0.3f)",
                "Color(.5f, .5f, .5f)",
                "Color(red = 0.1f, green = 0.2f, blue = 0.3f)",
                "Color(value = bits)",
                "Color.parseColor(\"#1a1f25\")",
                "val c = \"#fff\"",
                "android.graphics.Color.rgb(1, 2, 3)",
                "Color.hsv(120f, 0.5f, 0.5f)",
                "Color.hsl(120f, 0.5f, 0.5f)",
                "Color.White",
                "Color.Transparent",
                "Color.Unspecified",
                "Color.Companion.Black",
                "paint.color = Color.BLACK",
                "paint.color = Color.TRANSPARENT",
                "getColor(android.R.color.white)",
            )
        val resources =
            listOf(
                "android:fillColor=\"#FF000000\"",
                "<item name=\"android:windowBackground\">#fff</item>",
                "@android:color/white",
            )

        for (line in kotlin) assertTrue(line, kotlinLiterals.any { it.containsMatchIn(line) })
        for (line in resources) assertTrue(line, resourceLiterals.any { it.containsMatchIn(line) })
        for (line in listOf("Color(Palette.of(dark).surface)", "// see #123", "val issue = \"#1234 is open\"")) {
            assertTrue(line, kotlinLiterals.none { it.containsMatchIn(line) })
        }
        for (line in listOf("android:fillColor=\"@color/palette_sun\"", "<!-- see #123 -->")) {
            assertTrue(line, resourceLiterals.none { it.containsMatchIn(line) })
        }
    }

    private fun path(file: File) = file.relativeTo(sources).invariantSeparatorsPath

    private companion object {
        val resourceColors = listOf("main/res/values/colors.xml", "main/res/values-night/colors.xml")

        /** The palette itself, and the resources that mirror its fields. */
        val exempt = setOf("main/kotlin/dev/cupr/weather/palette/Palette.kt") + resourceColors

        val COLOR_NAME = Regex("""<color\s+name="([^"]+)"""")

        private const val HEX_COLOR = """(\p{XDigit}{3,4}|\p{XDigit}{6}|\p{XDigit}{8})"""

        /** Unit tests (`test`, `testDebug`, `testFixtures` …) and instrumented tests (`androidTest…`). */
        fun isTestSourceSet(name: String) = name.startsWith("test") || name.startsWith("androidTest")

        val kotlinLiterals =
            listOf(
                // A colour as a hex number, RGB or ARGB, with or without an unsigned or Long suffix.
                Regex("""\b0[xX](\p{XDigit}{6}|\p{XDigit}{8})[uU]?[lL]?\b"""),
                // A colour as a string that holds only "#" and the hex digits.
                Regex(""""\s*#$HEX_COLOR\s*""""),
                // Compose's Color built from numbers or from named arguments.
                Regex("""\bColor\(\s*(\d|\.\d|\w+\s*=)"""),
                // Android's and Compose's colour factories and ready colours.
                Regex("""\bColor\.(parseColor|rgb|argb|valueOf|pack|hsv|hsl)\b"""),
                Regex(
                    """\bColor\.(Black|DarkGray|Gray|LightGray|White|Red|Green|Blue|Yellow|Cyan|Magenta|""" +
                        """Transparent|Unspecified)\b""",
                ),
                Regex("""\bColor\.(BLACK|DKGRAY|GRAY|LTGRAY|WHITE|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA|TRANSPARENT)\b"""),
                Regex("""\bColor\.Companion\.\w+"""),
                // Android's own colour resources.
                Regex("""\bandroid\.R\.color\."""),
            )

        val resourceLiterals =
            listOf(
                // A colour as an attribute's value or an element's text.
                Regex("""["'>]\s*#$HEX_COLOR\s*["'<]"""),
                // Android's own colour resources.
                Regex("""@android:color/"""),
            )
    }
}
