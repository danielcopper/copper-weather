package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.store.Animation
import dev.cupr.weather.store.FlatStrip
import dev.cupr.weather.store.WidgetSettings
import dev.cupr.weather.tap.Taps
import kotlin.math.floor

/**
 * The views of the widget "Wetter" at one size (D11–D14, D35, D36, D41): "now" in every size bucket, the hours in the
 * wide and the tall one, the days in the tall one and, from 4 cells wide, the days or the hours in the flat one (D46),
 * as many of each as fit; or a plain text. The flat bucket has a layout of its own, "now" and the days or hours side by
 * side. Every text in its palette role for light and for dark (D7); the images are the conditions' weather icons
 * ([WeatherIcons]), animated where the widget's settings say (D44), on its card at the opacity they set (D45).
 */
internal object WidgetViews {
    /** The views of [content] for a widget [size] dp large with [settings]. */
    fun of(
        context: Context,
        content: WidgetContent,
        size: SizeF,
        settings: WidgetSettings,
    ): RemoteViews {
        val bucket = SizeBucket.of(size)
        val flat = bucket == SizeBucket.FLAT
        val views =
            RemoteViews(context.packageName, if (flat) R.layout.widget_weather_flat else R.layout.widget_weather)
        // Every part is set either way, since a launcher applies an update onto the views of the one before when their
        // layout is the same.
        views.removeAllViews(R.id.hours)
        views.removeAllViews(R.id.days)
        views.card(settings.opacity)
        when (content) {
            is WidgetContent.Plain -> plain(context, views, content, flat)
            is WidgetContent.Weather if flat -> flatWeather(context, views, content, size, settings)
            is WidgetContent.Weather -> weather(context, views, content, size, bucket, settings.animation)
        }
        return views
    }

    /** The parts of each layout a plain text hides: all of the flat one's, or all of the other one's. */
    private val flatParts = intArrayOf(R.id.now_column, R.id.divider, R.id.days, R.id.hours)
    private val parts =
        intArrayOf(
            R.id.now,
            R.id.now_today_below,
            R.id.now_line,
            R.id.now_line_long,
            R.id.widget_notice,
            R.id.hours,
            R.id.days,
        )

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
        animation: Animation,
    ) {
        val small = bucket == SizeBucket.SMALL
        now(context, views, content.now, compact = small, animate = animation != Animation.OFF)
        views.today(R.id.now_today, R.id.now_high, R.id.now_low, content.now.today.takeUnless { small })
        views.today(R.id.now_today_below, R.id.now_high_below, R.id.now_low_below, content.now.today.takeIf { small })
        val room = Room(context.resources, size, content.signInAgain)
        line(
            context,
            views,
            room,
            content.now.line,
            twoLineRoom =
                room.inside().takeIf {
                    small
                },
            lineText = R.dimen.widget_line_text,
        )

        val rows = animation == Animation.ALL
        val hours = if (small) emptyList() else content.hours.take(room.hours())
        views.rows(R.id.hours, hours) { WidgetRows.hour(context, it, rows) }
        rest(context, views, content)
        val days = if (bucket == SizeBucket.TALL) content.days.take(room.days(hours.isNotEmpty())) else emptyList()
        views.rows(R.id.days, days) { WidgetRows.day(context, it, rows) }
    }

    /**
     * The flat bucket (D36): "now" as the dashboard's today, its image at the left and beside it its temperature over
     * its line and today's high, low and rain, "Neu anmelden" in its line's place while it shows; and beside it, after
     * a divider in the palette's `border`, as many of the next days (D41) or, as [settings] say, hours (D46) as fit,
     * from 4 cells wide; without them "now" takes the card's width.
     */
    private fun flatWeather(
        context: Context,
        views: RemoteViews,
        content: WidgetContent.Weather,
        size: SizeF,
        settings: WidgetSettings,
    ) {
        views.setViewVisibility(R.id.now_column, View.VISIBLE)
        views.show(R.id.now_temperature, content.now.temperature, Palette::text)
        val room = Room(context.resources, size, content.signInAgain)
        val wide = SizeBucket.wide(size)
        val days = if (wide && settings.flatStrip == FlatStrip.DAYS) content.days.take(room.flatDays()) else emptyList()
        val hours =
            if (wide && settings.flatStrip == FlatStrip.HOURS) content.hours.take(room.flatHours()) else emptyList()
        val strip = days.isNotEmpty() || hours.isNotEmpty()
        // With days or hours "now" has its column, without them the card's width; the line is measured against the
        // text area beside the image.
        val column =
            if (!strip) {
                views.setViewLayoutWidth(
                    R.id.now_column,
                    ViewGroup.LayoutParams.MATCH_PARENT.toFloat(),
                    TypedValue.COMPLEX_UNIT_PX,
                )
                room.inside()
            } else {
                views.setWidth(context, R.id.now_column, R.dimen.widget_flat_now_width)
                room.flatNow()
            }
        // The image's size by whether days or hours show (D36).
        val icon = if (strip) R.dimen.widget_flat_now_icon else R.dimen.widget_flat_now_icon_narrow
        views.nowIcon(context, content.now.icon, icon, settings.animation != Animation.OFF)
        val text = room.flatText(column, icon)
        if (content.signInAgain) {
            views.setViewVisibility(R.id.now_line, View.GONE)
            views.setViewVisibility(R.id.now_line_long, View.GONE)
        } else {
            line(context, views, room, content.now.line, twoLineRoom = text, lineText = R.dimen.widget_line_flat_text)
        }
        // Today's high, low and rain below; with "Neu anmelden", 36 dp high, today's line gives way (D36).
        flatToday(context, views, content.now.takeUnless { content.signInAgain }, room, text)

        views.setViewVisibility(R.id.divider, if (strip) View.VISIBLE else View.GONE)
        views.setColorInt(R.id.divider, "setBackgroundColor", Palette.LIGHT.border, Palette.DARK.border)
        val rows = settings.animation == Animation.ALL
        val degrees = room.flatDayDegrees(days.map { it.highLow })
        views.rows(R.id.days, days) { WidgetRows.flatDay(context, it, degrees, rows) }
        views.rows(R.id.hours, hours) { WidgetRows.hour(context, it, rows) }
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

    /**
     * The flat bucket's third line below "now" (D36): today's high and low as [today] shows them, and today's rain in
     * `rain` at the size [Room.flatTodayRain] finds for the [text] dp wide area, or without it where none fits; nothing
     * without today or without [now].
     */
    private fun flatToday(
        context: Context,
        views: RemoteViews,
        now: WidgetContent.Now?,
        room: Room,
        text: Float,
    ) {
        views.today(R.id.now_today_flat, R.id.now_today_flat_high, R.id.now_today_flat_low, now?.today)
        val today = now?.today ?: return
        val rain = with(WidgetRows) { context.rainText(now.todayRain) }
        val size = rain?.let { room.flatTodayRain(today, it, text) }
        views.show(R.id.now_today_flat_rain, rain?.takeIf { size != null }, Palette::rain)
        if (size != null) views.setTextViewTextSize(R.id.now_today_flat_rain, TypedValue.COMPLEX_UNIT_PX, size)
    }

    /** The app's screen while the sign-in asks to be done again, else the weather pop-up (D39). */
    private fun tap(
        context: Context,
        content: WidgetContent.Weather,
    ) = if (content.signInAgain) Taps.app(context) else Taps.weather(context)

    /**
     * "Now"'s row outside the flat bucket (D36): [compact] in the small bucket, lower and with a smaller image; its
     * image animated when [animate] (D44).
     */
    private fun now(
        context: Context,
        views: RemoteViews,
        now: WidgetContent.Now,
        compact: Boolean,
        animate: Boolean,
    ) {
        views.setViewVisibility(R.id.now, View.VISIBLE)
        views.setHeight(
            context,
            R.id.now,
            if (compact) R.dimen.widget_now_height_compact else R.dimen.widget_now_height,
        )
        val icon = if (compact) R.dimen.widget_now_icon_compact else R.dimen.widget_now_icon
        views.nowIcon(context, now.icon, icon, animate)
        views.show(R.id.now_temperature, now.temperature, Palette::text)
    }

    /**
     * The line below "now": the rain hint in the palette's `rain`, else the condition's word, else nothing (D36). In
     * the small and the flat bucket, given [twoLineRoom], the dp the line has there, a text that does not fit that on
     * one line at the line's size, [lineText], takes the line's two-line form (`now_line_long`) instead, at a smaller
     * size; in the other buckets, without it, the line keeps its one-line form.
     */
    private fun line(
        context: Context,
        views: RemoteViews,
        room: Room,
        line: WidgetContent.Line?,
        twoLineRoom: Float?,
        @DimenRes lineText: Int,
    ) {
        val (text, role) =
            when (line) {
                null -> null to Palette::text
                WidgetContent.Line.RainNow -> context.getString(R.string.rain_now) to Palette::rain
                is WidgetContent.Line.RainIn -> context.rainIn(line.minutes) to Palette::rain
                is WidgetContent.Line.Word -> context.getString(line.word) to Palette::text
            }
        val long = text != null && twoLineRoom != null && !room.fitsOneLine(text, twoLineRoom, lineText)
        views.show(R.id.now_line, text.takeUnless { long }, role)
        views.show(R.id.now_line_long, text.takeIf { long }, role)
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
        private val resources: Resources,
        private val size: SizeF,
        private val notice: Boolean,
    ) {
        private val dimen = resources::dimen

        /**
         * Whether [text] fits [width] dp on one line at [size], the size of the line below "now" in its bucket, in the
         * system's sans-serif, the font every text of the widget is set in (`styles.xml`, D36).
         */
        fun fitsOneLine(
            text: String,
            width: Float,
            @DimenRes size: Int,
        ): Boolean = resources.widthOf(text, resources.getDimension(size)) <= width

        /**
         * The size in px at which today's [rain] fits beside [today]'s high and low on the flat bucket's third line,
         * [width] dp wide: the largest of the rain's sizes in `dimens.xml`, or none (D36).
         */
        fun flatTodayRain(
            today: WidgetContent.HighLow,
            rain: String,
            width: Float,
        ): Float? {
            val text = resources.getDimension(R.dimen.widget_flat_today_text)
            val left =
                width - resources.widthOf(today, text, R.dimen.widget_flat_today_low_gap) -
                    dimen(R.dimen.widget_flat_today_rain_gap) - dimen(R.dimen.widget_flat_fit_slack)
            return resources
                .sizes(R.dimen.widget_flat_today_text, R.dimen.widget_flat_today_text_least)
                .firstOrNull { resources.widthOf(rain, it) <= left }
        }

        /**
         * The size in px for the highs and lows of the flat bucket's [days], each in its half of its day: the largest
         * of the sizes in `dimens.xml` at which every high and every low fits its half, else the least (D41).
         */
        fun flatDayDegrees(days: List<WidgetContent.HighLow>): Float {
            val day = flatDaysRoom / days.size - dimen(R.dimen.widget_flat_day_gap)
            val half = (day - dimen(R.dimen.widget_flat_day_low_gap)) / 2 - dimen(R.dimen.widget_flat_fit_slack)
            val sizes = resources.sizes(R.dimen.widget_flat_day_text, R.dimen.widget_flat_day_text_least)
            return sizes.firstOrNull { size ->
                days.all {
                    resources.widthOf(it.high, size, Typeface.BOLD) <= half && resources.widthOf(it.low, size) <= half
                }
            } ?: sizes.last()
        }

        private val padding = dimen(R.dimen.widget_padding)
        private val gap = dimen(R.dimen.widget_gap)

        /** The width inside the card's padding. */
        fun inside(): Float = size.width - 2 * padding

        /** The width of "now"'s column in the flat bucket beside its days. */
        fun flatNow(): Float = dimen(R.dimen.widget_flat_now_width)

        /** The text area beside the image, [icon] large, in a flat "now" column [column] dp wide (D36). */
        fun flatText(
            column: Float,
            @DimenRes icon: Int,
        ): Float = column - dimen(icon) - dimen(R.dimen.widget_flat_now_gap)

        /** The hours that fit the width, each at least its least width. */
        fun hours(): Int = fit(inside(), dimen(R.dimen.widget_hour_width))

        /**
         * The days that fit beside "now" in the flat bucket: after its column, the gap and the divider with its
         * padding, each day at least its least width with the gap before it (D41).
         */
        fun flatDays(): Int =
            fit(
                flatDaysRoom,
                dimen(R.dimen.widget_flat_day_width) + dimen(R.dimen.widget_flat_day_gap),
            )

        /**
         * The hours that fit beside "now" in the flat bucket, in the days' room: each at least its least width, as in
         * the wide bucket's strip, without a gap (D46).
         */
        fun flatHours(): Int = fit(flatDaysRoom, dimen(R.dimen.widget_hour_width))

        /** The width the flat bucket's days or hours share, each day with the gap before it. */
        private val flatDaysRoom
            get() =
                inside() - flatNow() - gap - dimen(R.dimen.widget_divider) - dimen(R.dimen.widget_flat_days_padding)

        /** The days that fit the height below "now" and, when they show ([withHours]), the hours. */
        fun days(withHours: Boolean): Int {
            var left = size.height - 2 * padding - dimen(R.dimen.widget_now_height) - dimen(R.dimen.widget_line_height)
            if (notice) left -= dimen(R.dimen.widget_notice_height)
            if (withHours) left -= gap + dimen(R.dimen.widget_hour_height)
            return fit(left - gap, dimen(R.dimen.widget_day_height))
        }

        /** How many parts [each] dp large fit into [room] dp. */
        private fun fit(
            room: Float,
            each: Float,
        ): Int = floor(room / each).toInt().coerceAtLeast(0)
    }
}

/**
 * "Now"'s image, [size] large: its still image, or, when [animate], its animated icon in the frame beside it, both
 * sized as values (D42, D43).
 */
private fun RemoteViews.nowIcon(
    context: Context,
    @DrawableRes still: Int,
    @DimenRes size: Int,
    animate: Boolean,
) {
    for (id in intArrayOf(R.id.now_icon, R.id.now_icon_animated)) {
        setWidth(context, id, size)
        setHeight(context, id, size)
    }
    icon(context, R.id.now_icon, R.id.now_icon_animated, still, animate)
}
