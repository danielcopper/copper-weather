package dev.cupr.weather.palette

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Each colour resource the app defines, `palette_<role>`, is that role of the palette (D7), light outside night mode
 * and dark in it: `palette_text_muted` is [Palette.textMuted].
 */
@RunWith(RobolectricTestRunner::class)
class ColorResourcesTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the colour resources are the light palette's outside night mode`() {
        assertColours(Palette.LIGHT)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the colour resources are the dark palette's in night mode`() {
        assertColours(Palette.DARK)
    }

    private fun assertColours(palette: Palette) {
        val roles = palette.colors()
        val resources = R.color::class.java.fields.filter { it.name.startsWith(PREFIX) }
        assertEquals(6, resources.size)
        for (resource in resources) {
            val role = resource.name.removePrefix(PREFIX).replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
            val colour = checkNotNull(roles[role]) { "${resource.name} names no role of the palette" }
            assertEquals(resource.name, colour, application.getColor(resource.getInt(null)))
        }
    }

    private companion object {
        const val PREFIX = "palette_"
    }
}
