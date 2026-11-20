package dev.cupr.weather.widget

import android.app.Application
import android.view.LayoutInflater
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The widget picker's preview (D40): sample weather, wide, in the palette's colours, light and dark. */
@RunWith(RobolectricTestRunner::class)
class WidgetPreviewTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the preview shows now and five hours of sample weather in the palette's colours outside night mode`() {
        assertPreview(Palette.LIGHT)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the preview shows now and five hours of sample weather in the palette's colours in night mode`() {
        assertPreview(Palette.DARK)
    }

    private fun assertPreview(palette: Palette) {
        val preview = LayoutInflater.from(application).inflate(R.layout.widget_weather_preview, null)

        val texts = WidgetHost.texts(preview)
        assertEquals(
            listOf(
                "7°",
                "9°",
                "3°",
                "Teilweise bewölkt",
                "14",
                "8°",
                "15",
                "8°",
                "16",
                "7°",
                "17",
                "6°",
                "0,4\u00A0mm",
                "18",
                "6°",
            ),
            texts.map { it.text.toString() },
        )
        val roles = mapOf(palette.text to "text", palette.textMuted to "textMuted", palette.rain to "rain")
        assertEquals(
            listOf(
                "text",
                "text",
                "textMuted",
                "text",
                "textMuted",
                "text",
                "textMuted",
                "text",
                "textMuted",
                "text",
                "textMuted",
                "text",
                "rain",
                "textMuted",
                "text",
            ),
            texts
                .map(
                    TextView::getCurrentTextColor,
                ).map { roles[it] ?: "not the palette's: ${Integer.toHexString(it)}" },
        )
    }

    @Test
    fun `every part of the preview takes its looks from a style the widget's own layouts use, and adds none`() {
        val preview = elements("widget_weather_preview.xml")
        val widget = listOf("widget_weather.xml", "widget_weather_flat.xml", "widget_hour.xml").flatMap(::elements)
        val shared = preview.map { it.getAttribute("style") }.toSet()

        assertTrue(preview.isNotEmpty())
        assertEquals(emptyList<String>(), preview.filter { it.getAttribute("style").isEmpty() }.map { it.tagName })
        assertEquals(emptyList<String>(), (shared - widget.map { it.getAttribute("style") }.toSet()).toList())
        assertEquals(emptyList<String>(), extras(preview, PREVIEW_OWN))
        assertEquals(emptyList<String>(), extras(widget.filter { it.getAttribute("style") in shared }, WIDGET_OWN))
    }

    /** Every element of the layout [name], from the source file. */
    private fun elements(name: String): List<Element> {
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                File("src/main/res/layout/$name"),
            )
        val elements = document.getElementsByTagName("*")
        return (0 until elements.length).map { elements.item(it) as Element }
    }

    /** The attributes of [elements] beyond their style, the namespace and [own], as "style: attribute". */
    private fun extras(
        elements: List<Element>,
        own: Set<String>,
    ): List<String> =
        elements.flatMap { element ->
            (0 until element.attributes.length)
                .map { element.attributes.item(it).nodeName }
                .filter { it != "style" && it != "xmlns:android" && it !in own }
                .map { "${element.getAttribute("style")}: $it" }
        }

    @Test
    fun `the preview is a layout a widget can show`() {
        // The picker inflates it as a widget's views, which take only the views RemoteViews allows.
        RemoteViews(application.packageName, R.layout.widget_weather_preview).apply(application, null)
    }

    private companion object {
        /**
         * What Android Lint reads only on the element, not from its style: that an image says nothing to
         * accessibility, and "now"'s image's scale type, without which Lint takes its row for a compound drawable.
         */
        val LINT_OWN = setOf("android:importantForAccessibility", "android:scaleType")

        /** A preview part's own attributes: its sample, and [LINT_OWN]. */
        val PREVIEW_OWN = setOf("android:text", "android:textColor", "android:src") + LINT_OWN

        /** A widget part's own attributes: its id, its visibility before WidgetViews shows it, and [LINT_OWN]. */
        val WIDGET_OWN = setOf("android:id", "android:visibility") + LINT_OWN
    }
}
