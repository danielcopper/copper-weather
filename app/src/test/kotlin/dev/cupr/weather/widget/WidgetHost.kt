package dev.cupr.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.cupr.weather.R
import org.robolectric.Shadows.shadowOf

/** The widget "Wetter" as a launcher shows it, under Robolectric's widget host. */
internal object WidgetHost {
    /**
     * A widget placed on the home screen, which the launcher updates through the provider at once; with a [size], the
     * launcher then reports that one size (`OPTION_APPWIDGET_SIZES`), and the widget is shown at it.
     */
    fun placed(
        context: Context,
        size: SizeF? = null,
    ): View = view(context, place(context, size))

    /** Places a widget as [placed] does; its id. */
    fun place(
        context: Context,
        size: SizeF? = null,
    ): Int {
        val id =
            shadowOf(
                AppWidgetManager.getInstance(context),
            ).createWidget(WeatherWidgetProvider::class.java, R.layout.widget_weather)
        if (size != null) resize(context, id, size)
        return id
    }

    /** The widget [id] resized to [size]: the launcher reports that one size, and the provider draws it anew. */
    fun resize(
        context: Context,
        id: Int,
        size: SizeF,
    ): View {
        val options =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(size),
                )
            }
        AppWidgetManager.getInstance(context).updateAppWidgetOptions(id, options)
        return view(context, id)
    }

    /** The widget [id] as the launcher shows it now. */
    fun view(
        context: Context,
        id: Int,
    ): View = shadowOf(AppWidgetManager.getInstance(context)).getViewFor(id)

    /** The texts [widget] shows, from the top, each part's in order: "now", the hours, the days, a plain text. */
    fun shown(widget: View): List<String> = texts(widget).filter { visible(it, widget) }.map { it.text.toString() }

    /** Whether [view] and each view above it up to [root] are visible; a widget's root has no window to ask. */
    fun visible(
        view: View,
        root: View,
    ): Boolean =
        generateSequence(view) {
            if (it ===
                root
            ) {
                null
            } else {
                it.parent as? View
            }
        }.all { it.visibility == View.VISIBLE }

    /** Every text view under [view], shown or not, depth first. */
    fun texts(view: View): List<TextView> =
        when (view) {
            is TextView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    /** The hours [widget] shows, each as the texts of its cell. */
    fun hours(widget: View): List<List<String>> = rows(widget, R.id.hours)

    /** The days [widget] shows, each as the texts of its row. */
    fun days(widget: View): List<List<String>> = rows(widget, R.id.days)

    private fun rows(
        widget: View,
        id: Int,
    ): List<List<String>> {
        val parent = widget.findViewById<ViewGroup>(id)?.takeIf { visible(it, widget) } ?: return emptyList()
        return (0 until parent.childCount).map { index -> shown(parent.getChildAt(index)) }
    }
}
