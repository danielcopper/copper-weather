package dev.cupr.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.TextView
import dev.cupr.weather.R
import org.robolectric.Shadows.shadowOf

/** The widget "Wetter" as a launcher shows it, under Robolectric's widget host. */
internal object WidgetHost {
    /** A widget placed on the home screen, which the launcher updates through the provider at once. */
    fun placed(context: Context): View {
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        return shadow.getViewFor(shadow.createWidget(WeatherWidgetProvider::class.java, R.layout.widget_weather))
    }

    /** The texts [widget] shows, from the top. */
    fun shown(widget: View): List<String> =
        listOf(R.id.widget_temperature, R.id.widget_condition, R.id.widget_notice, R.id.widget_plain)
            .map { widget.findViewById<TextView>(it) }
            .filter { it.visibility == View.VISIBLE }
            .map { it.text.toString() }
}
