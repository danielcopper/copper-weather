package dev.cupr.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.core.os.BundleCompat
import java.time.Clock

/**
 * The widget "Wetter" (D19): the snapshot's weather by its size bucket (D11–D14), or why there is none, on a card in
 * the palette's colours, light and dark with the system (D7). Its views are built anew on each update and whenever the
 * launcher reports new sizes, one set of views for each size the launcher shows it at (D35); the launcher's own
 * updates read what to show as the app's do ([Widgets]).
 */
class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        Widgets.withContent(context) { content -> update(context, appWidgetManager, appWidgetIds, content) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        Widgets.withContent(context) { content -> update(context, appWidgetManager, intArrayOf(appWidgetId), content) }
    }

    companion object {
        private const val TAG = "WeatherWidget"

        /** The most views a RemoteViews takes for its sizes (RemoteViews' `MAX_INIT_VIEW_COUNT`). */
        private const val MOST_SIZES = 16

        /**
         * What the widget shows now ([WidgetContent.read]), read once per update; tests replace it to make the update
         * fail.
         */
        @VisibleForTesting
        internal var content: (Context) -> WidgetContent = { WidgetContent.read(it, clock()) }

        /** The clock and zone the widget's times are read in; tests set their own. */
        @VisibleForTesting
        internal var clock: () -> Clock = { Clock.systemDefaultZone() }

        /** Hands each of the widgets [ids] the views of [content] for the sizes the launcher shows it at. */
        internal fun update(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            content: WidgetContent,
        ) {
            ids.forEach { id ->
                val sizes = sizes(manager.getAppWidgetOptions(id), id)
                manager.updateAppWidget(id, RemoteViews(sizes.associateWith { WidgetViews.of(context, content, it) }))
            }
        }

        /** This app's widgets on the home screen. */
        internal fun ids(
            context: Context,
            manager: AppWidgetManager,
        ): IntArray = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))

        /**
         * The sizes in dp a widget is shown at (D35): those the launcher reports (`OPTION_APPWIDGET_SIZES`, one for
         * portrait and one for landscape on a phone); without them its width in portrait and its height in portrait
         * (its least width and greatest height); without those either, 4 × 2 cells, the size it is added at (D11), and
         * the log says so.
         */
        internal fun sizes(
            options: Bundle?,
            id: Int,
        ): List<SizeF> {
            val key = AppWidgetManager.OPTION_APPWIDGET_SIZES
            val reported =
                options
                    ?.let { BundleCompat.getParcelableArrayList(it, key, SizeF::class.java) }
                    .orEmpty()
                    .filter { it.width > 0 && it.height > 0 }
                    .distinct()
                    .take(MOST_SIZES)
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
            return when {
                reported.isNotEmpty() -> {
                    reported
                }

                width > 0 && height > 0 -> {
                    listOf(SizeF(width.toFloat(), height.toFloat()))
                }

                else -> {
                    Log.w(TAG, "Widget $id: the launcher reports no size, so it is drawn for 4 x 2 cells")
                    listOf(SizeBucket.sizeOf(columns = 4, rows = 2))
                }
            }
        }
    }
}
