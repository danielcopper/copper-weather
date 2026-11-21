package dev.cupr.weather.setup

import android.content.Context
import android.util.Log
import dev.cupr.weather.app.Services
import dev.cupr.weather.store.WidgetSettings
import dev.cupr.weather.widget.Widgets

/** The log's tag for a widget's configuration. */
internal const val TAG = "Setup"

/**
 * Stores [settings] for the widget [id] and draws that widget anew at once, and only it ([Widgets.saved]). When [id] is
 * no longer one of the app's widgets, removed while its configuration screen was open, nothing is drawn, the settings
 * are not kept and the log says so; false then.
 */
internal fun save(
    context: Context,
    id: Int,
    settings: WidgetSettings,
): Boolean {
    val store = Services.widgetSettings(context)
    // Written before the check: a widget removed after it loses them with its removal (onDeleted), one removed before
    // it here.
    store.write(id, settings)
    if (Widgets.saved(context, id)) return true
    store.remove(intArrayOf(id))
    Log.w(TAG, "Widget $id: saved, but no longer one of the app's widgets, so nothing is drawn or kept")
    return false
}
