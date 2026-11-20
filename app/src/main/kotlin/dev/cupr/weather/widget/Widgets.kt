package dev.cupr.weather.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * The app's own updates of all its widgets (D33): after each fetch, whatever its outcome, after a new sign-in and after
 * signing out. The launcher's own updates (a new widget) go through the provider and leave WorkManager alone, so it
 * does not start with every process they start (D28).
 */
object Widgets {
    private val lock = Any()

    /** Shows what the app keeps now on every widget. */
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = WeatherWidgetProvider.ids(context, manager)
        withContent(context) { WeatherWidgetProvider.update(context, manager, ids, it) }
    }

    /**
     * Runs [update] with what the widgets show now ([WeatherWidgetProvider.content]) under one lock, from reading it
     * until [update] has handed its views to the launcher. The app's updates and the launcher's all take it, so an
     * update that read before a sign-out cannot hand its weather over after the sign-out's update.
     */
    internal fun withContent(
        context: Context,
        update: (WidgetContent) -> Unit,
    ) {
        synchronized(lock) { update(WeatherWidgetProvider.content(context)) }
    }
}
