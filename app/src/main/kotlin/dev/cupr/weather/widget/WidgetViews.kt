package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.tap.Taps
import kotlin.math.floor

/**
 * The views of the widget "Wetter" at one size (D12–D14, D35): "now" in every size bucket, the hours in the wide and
 * the tall one, the days in the tall one, as many of each as fit; or a plain text. Every text in its palette role for
 * light and for dark (D7); the images are the conditions' still images ([WeatherIcons]).
 */
internal object WidgetViews {
    /** The views of [content] for a widget [size] dp large. */
    fun of(
        context: Context,
        content: WidgetContent,
        size: SizeF,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        // Every part is set either way, since a launcher applies an update onto the views of the one before when their
        // layout is the same.
        views.removeAllViews(R.id.hours)
        views.removeAllViews(R.id.days)
        when (content) {
            is WidgetContent.Plain -> plain(context, views, content)
            is WidgetContent.Weather -> weather(context, views, content, size)
        }
        return views
    }

    private fun plain(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Plain,
    ) {
        for (id in intArrayOf(R.id.now, R.id.now_line, R.id.widget_notice, R.id.hours, R.id.days)) {
            views.setViewVisibility(id, View.GONE)
        }
        views.show(R.id.widget_plain, context.getString(content.text), Palette::text)
        views.setOnClickPendingIntent(R.id.widget, Taps.app(context))
    }

    private fun weather(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Weather,
        size: SizeF,
    ) {
        val now = content.now
        views.setViewVisibility(R.id.now, View.VISIBLE)
        views.setImageViewResource(R.id.now_icon, now.icon)
        views.show(R.id.now_temperature, now.temperature, Palette::text)
        views.show(R.id.now_high, now.high, Palette::text)
        views.show(R.id.now_low, now.low, Palette::textMuted)
        when (val line = now.line) {
            null -> views.show(R.id.now_line, null, Palette::text)
            WidgetContent.Line.RainNow -> views.show(R.id.now_line, context.getString(R.string.rain_now), Palette::rain)
            is WidgetContent.Line.RainIn -> views.show(R.id.now_line, context.rainIn(line.minutes), Palette::rain)
            is WidgetContent.Line.Condition -> views.show(R.id.now_line, context.getString(line.word), Palette::text)
        }
        val again = context.getString(R.string.widget_sign_in_again).takeIf { content.signInAgain }
        views.show(R.id.widget_notice, again, Palette::accent)
        views.show(R.id.widget_plain, null, Palette::text)

        val room = Room(context.resources, size, content.signInAgain)
        val bucket = SizeBucket.of(size)
        val hours = if (bucket == SizeBucket.SMALL) emptyList() else content.hours.take(room.hours())
        views.setViewVisibility(R.id.hours, if (hours.isEmpty()) View.GONE else View.VISIBLE)
        hours.forEach { views.addView(R.id.hours, hour(context, it)) }
        val days = if (bucket == SizeBucket.TALL) content.days.take(room.days(hours.isNotEmpty())) else emptyList()
        views.setViewVisibility(R.id.days, if (days.isEmpty()) View.GONE else View.VISIBLE)
        days.forEach { views.addView(R.id.days, day(context, it)) }

        views.setOnClickPendingIntent(
            R.id.widget,
            if (content.signInAgain) Taps.app(context) else Taps.weather(context),
        )
    }

    private fun Context.rainIn(minutes: Long): String = getString(R.string.rain_in, minutes)

    private fun hour(
        context: Context,
        hour: WidgetContent.Hour,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hour)
        views.show(R.id.hour_time, hour.time, Palette::textMuted)
        views.setImageViewResource(R.id.hour_icon, hour.icon)
        views.show(R.id.hour_temperature, hour.temperature, Palette::text)
        views.show(R.id.hour_rain, hour.rain, Palette::rain)
        return views
    }

    private fun day(
        context: Context,
        day: WidgetContent.Day,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        val weekdays = context.resources.getStringArray(R.array.weekdays_short)
        views.show(R.id.day_weekday, weekdays[day.weekday.value - 1], Palette::text)
        views.setImageViewResource(R.id.day_icon, day.icon)
        // The rain keeps its room without an amount, so the highs and lows of all days stand in one column.
        views.show(R.id.day_rain, day.rain, Palette::rain, hidden = View.INVISIBLE)
        views.show(R.id.day_high, day.high, Palette::text)
        views.show(R.id.day_low, day.low, Palette::textMuted)
        return views
    }

    /**
     * Shows [text] in [id], in [role]'s colour for light and for dark, or hides [id] ([hidden]) without a text.
     */
    private fun RemoteViews.show(
        id: Int,
        text: String?,
        role: (Palette) -> Int,
        hidden: Int = View.GONE,
    ) {
        if (text == null) {
            setViewVisibility(id, hidden)
            return
        }
        setTextViewText(id, text)
        setColorInt(id, "setTextColor", role(Palette.LIGHT), role(Palette.DARK))
        setViewVisibility(id, View.VISIBLE)
    }

    /**
     * The room a widget [size] dp large has for the hours and the days (D35), from the fixed sizes of its parts
     * (`dimens.xml`): below the card's padding, "now" and its line, and "Neu anmelden" while it shows ([notice]).
     */
    private class Room(
        resources: Resources,
        private val size: SizeF,
        private val notice: Boolean,
    ) {
        private val density = resources.displayMetrics.density
        private val dimen = { id: Int -> resources.getDimension(id) / density }
        private val padding = dimen(R.dimen.widget_padding)
        private val gap = dimen(R.dimen.widget_gap)

        /** The hours that fit the width, each at least its least width. */
        fun hours(): Int = fit(size.width - 2 * padding, R.dimen.widget_hour_width)

        /** The days that fit the height below "now" and, when they show ([withHours]), the hours. */
        fun days(withHours: Boolean): Int {
            var left = size.height - 2 * padding - dimen(R.dimen.widget_now_height) - dimen(R.dimen.widget_line_height)
            if (notice) left -= dimen(R.dimen.widget_notice_height)
            if (withHours) left -= gap + dimen(R.dimen.widget_hour_height)
            return fit(left - gap, R.dimen.widget_day_height)
        }

        private fun fit(
            room: Float,
            @DimenRes each: Int,
        ): Int = floor(room / dimen(each)).toInt().coerceAtLeast(0)
    }
}
