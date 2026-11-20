package dev.cupr.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import dev.cupr.weather.R
import dev.cupr.weather.app.MainActivity
import dev.cupr.weather.palette.Palette

/**
 * The widget "Wetter" (D19), a plain text stand-in (D33): the snapshot's temperature and condition, or why there is
 * none, on a card in the palette's colours, light and dark with the system (D7). A tap opens the app's screen. Its
 * views are built anew on each update; the launcher's own updates read what to show as the app's do ([Widgets]).
 */
class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        Widgets.withContent(context) { content -> update(context, appWidgetManager, appWidgetIds, content) }
    }

    companion object {
        /**
         * What the widget shows now ([WidgetContent.read]), read once per update; tests replace it to make the update
         * fail.
         */
        @VisibleForTesting
        internal var content: (Context) -> WidgetContent = { WidgetContent.read(it) }

        /** Hands the widgets [ids] the views of [content]. */
        internal fun update(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            content: WidgetContent,
        ) {
            if (ids.isEmpty()) return
            val views = views(context, content)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /** This app's widgets on the home screen. */
        internal fun ids(
            context: Context,
            manager: AppWidgetManager,
        ): IntArray = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))

        /** The views of [content], each text in its palette role for light and for dark, the whole widget a tap. */
        internal fun views(
            context: Context,
            content: WidgetContent,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            val weather = content as? WidgetContent.Weather
            val plain = (content as? WidgetContent.Plain)?.let { context.getString(it.text) }
            val again = context.getString(R.string.widget_sign_in_again).takeIf { weather?.signInAgain == true }
            views.show(R.id.widget_temperature, weather?.temperature, Palette::text)
            views.show(R.id.widget_condition, weather?.condition?.let(context::getString), Palette::text)
            views.show(R.id.widget_notice, again, Palette::accent)
            views.show(R.id.widget_plain, plain, Palette::text)
            views.setOnClickPendingIntent(R.id.widget, open(context))
            return views
        }

        /**
         * Shows [text] in [id], in [role]'s colour, or hides [id] without a text. Each view is set either way, since a
         * launcher applies an update onto the views of the one before when their layout is the same.
         */
        private fun RemoteViews.show(
            id: Int,
            text: String?,
            role: (Palette) -> Int,
        ) {
            if (text == null) {
                setViewVisibility(id, View.GONE)
                return
            }
            setTextViewText(id, text)
            setColorInt(id, "setTextColor", role(Palette.LIGHT), role(Palette.DARK))
            setViewVisibility(id, View.VISIBLE)
        }

        /**
         * The app's screen, started as the app's icon starts it, so a screen already open comes to the front instead
         * of a second one.
         */
        private fun open(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
