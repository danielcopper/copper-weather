package dev.cupr.weather.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps colours out of the app's sources outside [Palette] (D7). It reads every `.kt` and `.xml` file of every source
 * set under `app/src` but the tests' (`test…`, `androidTest…`), the palette itself and the two `colors.xml` files
 * excepted, and fails on:
 * - in Kotlin: a hex number of 6 or 8 digits, also with underscores or a `u`/`L` suffix; a string that holds only `#`
 *   and 3, 4, 6 or 8 hex digits; Compose's `Color(` with a number or a named argument first, also on the next line;
 *   `Color.` with `parseColor`, `rgb`, `argb`, `valueOf`, `pack`, `hsv`, `hsl` or `HSVToColor`; Compose's and
 *   Android's ready colours (`Color.White`, `Color.Transparent`, `Color.Unspecified`, `Color.BLACK`,
 *   `Color.TRANSPARENT` …) and anything through `Color.Companion.`; `android.R.color.` and `android.R.attr.color…`;
 *   Material's `dynamicLightColorScheme` and `dynamicDarkColorScheme`;
 * - in XML: a `#` colour as an attribute's value or an element's text, also on lines of its own; `@android:color/`,
 *   and `?android:attr/color…` or its short form `?android:color…`.
 *
 * The two `colors.xml` files may only define the palette's fields the resources need, and AppIdentityTest and
 * WindowTest keep those equal to the palette. No built-in rule of detekt or Android Lint finds colour literals in
 * Kotlin and in the resources alike, so this test reads the sources; the test task declares them as its input
 * (build.gradle.kts).
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
                val text = file.readText()
                val patterns = if (file.extension == "kt") kotlinLiterals else resourceLiterals
                patterns.flatMap { pattern ->
                    pattern.findAll(text).map { match ->
                        val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                        "${path(file)}:$line: ${match.value.trim()}"
                    }
                }
            }

        assertEquals("Colour literals outside the palette", emptyList<String>(), literals.sorted())
    }

    @Test
    fun `the resources' only colours are the palette's ground, card and sun`() {
        for (path in resourceColors) {
            val names =
                COLOR_NAME
                    .findAll(File(sources, path).readText())
                    .map { it.groupValues[1] }
                    .toSet()

            assertEquals(path, setOf("palette_surface_background", "palette_surface_card", "palette_sun"), names)
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
                "val c = 0xFF_1A1F25",
                "val c = 0xFF_1A_1F_25.toInt()",
                "Color(0xFF1A1F25)",
                "Color(0.1f, 0.2f, 0.3f)",
                "Color(.5f, .5f, .5f)",
                "Color(red = 0.1f, green = 0.2f, blue = 0.3f)",
                "Color(\n    red = 0.1f,\n    green = 0.2f,\n    blue = 0.3f,\n)",
                "Color(\n    0.1f,\n    0.2f,\n    0.3f,\n)",
                "Color(value = bits)",
                "Color.parseColor(\"#1a1f25\")",
                "val c = \"#fff\"",
                "android.graphics.Color.rgb(1, 2, 3)",
                "Color.hsv(120f, 0.5f, 0.5f)",
                "Color.hsl(120f, 0.5f, 0.5f)",
                "Color.HSVToColor(floatArrayOf(120f, 0.5f, 0.5f))",
                "Color.White",
                "Color.Transparent",
                "Color.Unspecified",
                "Color.Companion.Black",
                "paint.color = Color.BLACK",
                "paint.color = Color.TRANSPARENT",
                "getColor(android.R.color.white)",
                "attributes(intArrayOf(android.R.attr.colorPrimary))",
                "dynamicLightColorScheme(context)",
                "dynamicDarkColorScheme(context)",
            )
        val resources =
            listOf(
                "android:fillColor=\"#FF000000\"",
                "<item name=\"android:windowBackground\">#fff</item>",
                "@android:color/white",
                "<item name=\"android:windowBackground\">?android:attr/colorBackground</item>",
                "<item name=\"android:windowBackground\">?android:colorBackground</item>",
                "<item name=\"android:windowBackground\">\n    #fff\n</item>",
            )

        for (text in kotlin) assertTrue(text, kotlinLiterals.any { it.containsMatchIn(text) })
        for (text in resources) assertTrue(text, resourceLiterals.any { it.containsMatchIn(text) })
        val notColours =
            listOf(
                "Color(Palette.of(dark).surfaceBackground)",
                "Color(\n    palette.surfaceCard,\n)",
                "// see #123",
                "val issue = \"#1234 is open\"",
                "val bits = 0x1234567",
                "intArrayOf(android.R.attr.windowBackground)",
            )
        for (text in notColours) assertTrue(text, kotlinLiterals.none { it.containsMatchIn(text) })
        for (text in listOf("android:fillColor=\"@color/palette_sun\"", "<!-- see #123 -->")) {
            assertTrue(text, resourceLiterals.none { it.containsMatchIn(text) })
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

        // Each pattern runs over a whole file, so `\s` also spans line breaks; `[ \t]` keeps a match on one line.
        val kotlinLiterals =
            listOf(
                Regex("""\b0[xX]_*(\p{XDigit}_*){6}((\p{XDigit}_*){2})?[uU]?[lL]?\b"""),
                Regex(""""[ \t]*#$HEX_COLOR[ \t]*""""),
                Regex("""\bColor\(\s*(\d|\.\d|\w+\s*=)"""),
                Regex("""\bColor\.(parseColor|rgb|argb|valueOf|pack|hsv|hsl|HSVToColor)\b"""),
                Regex(
                    """\bColor\.(Black|DarkGray|Gray|LightGray|White|Red|Green|Blue|Yellow|Cyan|Magenta|""" +
                        """Transparent|Unspecified)\b""",
                ),
                Regex("""\bColor\.(BLACK|DKGRAY|GRAY|LTGRAY|WHITE|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA|TRANSPARENT)\b"""),
                Regex("""\bColor\.Companion\.\w+"""),
                Regex("""\bandroid\.R\.color\.|\bandroid\.R\.attr\.color"""),
                Regex("""\bdynamic(Light|Dark)ColorScheme\b"""),
            )

        val resourceLiterals =
            listOf(
                Regex("""["'][ \t]*#$HEX_COLOR[ \t]*["']"""),
                Regex(""">\s*#$HEX_COLOR\s*<"""),
                Regex("""@android:color/|\?android:(attr/)?color"""),
            )
    }
}
