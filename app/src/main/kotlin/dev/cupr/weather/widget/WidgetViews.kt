package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.tap.Taps
import kotlin.math.floor

/**
 * The views of the widget "Wetter" at one size (D11–D14, D35, D36, D41): "now" in every size bucket, the hours in the
 * wide and the tall one, the days in the tall one and in the flat one from 4 cells wide, as many of each as fit; or a
 * plain text. The flat bucket has a layout of its own, "now" and the days side by side. Every text in its palette role
 * for light and for dark (D7); the images are the conditions' still images ([WeatherIcons]).
 */
internal object WidgetViews {
    /** The views of [content] for a widget [size] dp large. */
    fun of(
        context: Context,
        content: WidgetContent,
        size: SizeF,
    ): RemoteViews {
        val bucket = SizeBucket.of(size)
        val flat = bucket == SizeBucket.FLAT
        val views =
            RemoteViews(context.packageName, if (flat) R.layout.widget_weather_flat else R.layout.widget_weather)
        // Every part is set either way, since a launcher applies an update onto the views of the one before when their
        // layout is the same.
        if (!flat) views.removeAllViews(R.id.hours)
        views.removeAllViews(R.id.days)
        when (content) {
            is WidgetContent.Plain -> plain(context, views, content, flat)
            is WidgetContent.Weather if flat -> flatWeather(context, views, content, size)
            is WidgetContent.Weather -> weather(context, views, content, size, bucket)
        }
        return views
    }

    /** The parts of each layout a plain text hides: all of the flat one's, or all of the other one's. */
    private val flatParts = intArrayOf(R.id.now_column, R.id.divider, R.id.days)
    private val parts =
        intArrayOf(R.id.now, R.id.now_today_below, R.id.now_line, R.id.widget_notice, R.id.hours, R.id.days)

    private fun plain(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Plain,
        flat: Boolean,
    ) {
        for (id in if (flat) flatParts else parts) views.setViewVisibility(id, View.GONE)
        views.show(R.id.widget_plain, context.getString(content.text), Palette::text)
        views.setOnClickPendingIntent(R.id.widget, Taps.app(context))
    }

    private fun weather(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Weather,
        size: SizeF,
        bucket: SizeBucket,
    ) {
        val small = bucket == SizeBucket.SMALL
        now(views, content.now, compact = small)
        views.today(R.id.now_today, R.id.now_high, R.id.now_low, content.now.today.takeUnless { small })
        views.today(R.id.now_today_below, R.id.now_high_below, R.id.now_low_below, content.now.today.takeIf { small })
        line(context, views, content.now.line, compact = small)

        val room = Room(context.resources, size, content.signInAgain)
        val hours = if (small) emptyList() else content.hours.take(room.hours())
        views.setViewVisibility(R.id.hours, if (hours.isEmpty()) View.GONE else View.VISIBLE)
        hours.forEach { views.addView(R.id.hours, WidgetRows.hour(context, it)) }
        rest(context, views, content)
        val days = if (bucket == SizeBucket.TALL) content.days.take(room.days(hours.isNotEmpty())) else emptyList()
        views.setViewVisibility(R.id.days, if (days.isEmpty()) View.GONE else View.VISIBLE)
        days.forEach { views.addView(R.id.days, WidgetRows.day(context, it)) }
    }

    /**
     * The flat bucket (D36): "now" as in the small bucket without today's high and low, "Neu anmelden" in its line's
     * place while it shows, and beside it, after a divider in the palette's `border`, as many of the next days as fit,
     * from 4 cells wide (D41); without days "now" takes the card's width.
     */
    private fun flatWeather(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Weather,
        size: SizeF,
    ) {
        views.setViewVisibility(R.id.now_column, View.VISIBLE)
        now(views, content.now, compact = true)
        if (content.signInAgain) {
            views.setViewVisibility(R.id.now_line, View.GONE)
        } else {
            line(context, views, content.now.line, compact = true)
        }

        val room = Room(context.resources, size, content.signInAgain)
        val days = if (SizeBucket.wide(size)) content.days.take(room.flatDays()) else emptyList()
        if (days.isEmpty()) {
            views.setViewLayoutWidth(
                R.id.now_column,
                ViewGroup.LayoutParams.MATCH_PARENT.toFloat(),
                TypedValue.COMPLEX_UNIT_PX,
            )
        } else {
            views.setViewLayoutWidthDimen(R.id.now_column, R.dimen.widget_flat_now_width)
        }
        val shown = if (days.isEmpty()) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.divider, shown)
        views.setColorInt(R.id.divider, "setBackgroundColor", Palette.LIGHT.border, Palette.DARK.border)
        views.setViewVisibility(R.id.days, shown)
        days.forEach { views.addView(R.id.days, WidgetRows.flatDay(context, it)) }
        rest(context, views, content)
    }

    /** What both layouts show after "now" and its parts: "Neu anmelden" while it applies, no plain text, the tap. */
    private fun rest(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Weather,
    ) {
        val again = context.getString(R.string.widget_sign_in_again).takeIf { content.signInAgain }
        views.show(R.id.widget_notice, again, Palette::accent)
        views.show(R.id.widget_plain, null, Palette::text)
        views.setOnClickPendingIntent(R.id.widget, tap(context, content))
    }

    /** The app's screen while the sign-in asks to be done again, else the weather pop-up (D39). */
    private fun tap(
        context: Context,
        content: WidgetContent.Weather,
    ) = if (content.signInAgain) Taps.app(context) else Taps.weather(context)

    /** "Now"'s row (D36): [compact] in the small and the flat bucket, lower and with a smaller image. */
    private fun now(
        views: RemoteViews,
        now: WidgetContent.Now,
        compact: Boolean,
    ) {
        views.setViewVisibility(R.id.now, View.VISIBLE)
        views.setViewLayoutHeightDimen(
            R.id.now,
            if (compact) R.dimen.widget_now_height_compact else R.dimen.widget_now_height,
        )
        views.setImageViewResource(R.id.now_icon, now.icon)
        val icon = if (compact) R.dimen.widget_now_icon_compact else R.dimen.widget_now_icon
        views.setViewLayoutWidthDimen(R.id.now_icon, icon)
        views.setViewLayoutHeightDimen(R.id.now_icon, icon)
        views.show(R.id.now_temperature, now.temperature, Palette::text)
    }

    /**
     * The line below "now": the rain hint in the palette's `rain`, else the condition's word, else nothing; [compact]
     * in the small and the flat bucket, room for two lines (D36).
     */
    private fun line(
        context: Context,
        views: RemoteViews,
        line: WidgetContent.Line?,
        compact: Boolean,
    ) {
        views.setViewLayoutHeightDimen(
            R.id.now_line,
            if (compact) R.dimen.widget_line_height_compact else R.dimen.widget_line_height,
        )
        when (line) {
            null -> views.show(R.id.now_line, null, Palette::text)
            WidgetContent.Line.RainNow -> views.show(R.id.now_line, context.getString(R.string.rain_now), Palette::rain)
            is WidgetContent.Line.RainIn -> views.show(R.id.now_line, context.rainIn(line.minutes), Palette::rain)
            is WidgetContent.Line.Word -> views.show(R.id.now_line, context.getString(line.word), Palette::text)
        }
    }

    /** Shows [today]'s high and low in [high] and [low] and their [group], or hides the group without them. */
    private fun RemoteViews.today(
        group: Int,
        high: Int,
        low: Int,
        today: WidgetContent.HighLow?,
    ) {
        setViewVisibility(group, if (today == null) View.GONE else View.VISIBLE)
        if (today == null) return
        show(high, today.high, Palette::text)
        show(low, today.low, Palette::textMuted)
    }

    private fun Context.rainIn(minutes: Long): String = getString(R.string.rain_in, minutes)

    /**
     * The room a widget [size] dp large has for the hours and the days (D35), from the fixed sizes of its parts
     * (`dimens.xml`): within the card's padding, beside "now" in the flat bucket; below "now" and its line, and "Neu
     * anmelden" while it shows ([notice]), in the others.
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

        /**
         * The days that fit beside "now" in the flat bucket: after its column, the gap and the divider with its
         * padding, each day at least its least width with the gap before it.
         */
        fun flatDays(): Int {
            val left =
                size.width - 2 * padding - dimen(R.dimen.widget_flat_now_width) - gap - dimen(R.dimen.widget_divider) -
                    dimen(R.dimen.widget_flat_days_padding)
            return floor(left / (dimen(R.dimen.widget_flat_day_width) + dimen(R.dimen.widget_flat_day_gap)))
                .toInt()
                .coerceAtLeast(0)
        }

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
