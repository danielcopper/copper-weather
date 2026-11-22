package dev.cupr.weather.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale

/**
 * The rows the widget adds by size (D13, D14, D37, D41, D46): an hour of the hours, also in the flat bucket, a day of
 * the days, and a day of the flat bucket, each its own views.
 */
internal object WidgetRows {
    /** [rain] as the dashboard writes it (D37): "2,5 mm", "3 mm", "< 0,1 mm"; none without rain. */
    fun Context.rainText(rain: WidgetContent.Rain?): String? =
        when (rain) {
            null -> {
                null
            }

            WidgetContent.Rain.Trace -> {
                getString(R.string.rain_trace)
            }

            is WidgetContent.Rain.Amount -> {
                val millimetres = NumberFormat.getNumberInstance(Locale.GERMAN).apply { maximumFractionDigits = 1 }
                getString(R.string.rain_amount, millimetres.format(rain.millimetres))
            }
        }

    /** The short German name of [day] as the dashboard names it ("Mo" … "So", D37). */
    fun Context.weekday(day: DayOfWeek): String = resources.getStringArray(R.array.weekdays_short)[day.value - 1]

    /** An hour of the hours: its time, image, animated when [animate] (D44), temperature and rain. */
    fun hour(
        context: Context,
        hour: WidgetContent.Hour,
        animate: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hour)
        views.show(R.id.hour_time, hour.time, Palette::textMuted)
        views.icon(context, R.id.hour_icon, R.id.hour_icon_animated, hour.icon, animate)
        views.show(R.id.hour_temperature, hour.temperature, Palette::text)
        views.show(R.id.hour_rain, context.rainText(hour.rain), Palette::rain)
        return views
    }

    /** A day of the days: its weekday, image, animated when [animate] (D44), rain, high and low. */
    fun day(
        context: Context,
        day: WidgetContent.Day,
        animate: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        views.show(R.id.day_weekday, context.weekday(day.weekday), Palette::text)
        views.icon(context, R.id.day_icon, R.id.day_icon_animated, day.icon, animate)
        // The rain keeps its room without an amount, so the highs and lows of all days stand in one column.
        views.show(R.id.day_rain, context.rainText(day.rain), Palette::rain, hidden = View.INVISIBLE)
        views.show(R.id.day_high, day.highLow.high, Palette::text)
        views.show(R.id.day_low, day.highLow.low, Palette::textMuted)
        return views
    }

    /**
     * A day of the flat bucket (D41): its weekday, its image, animated when [animate] (D44), and its high and its low
     * at [degrees] px, the size [WidgetViews] picks for every day of the strip.
     */
    fun flatDay(
        context: Context,
        day: WidgetContent.Day,
        @Px degrees: Float,
        animate: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_flat_day)
        views.show(R.id.flat_day_weekday, context.weekday(day.weekday), Palette::textMuted)
        views.icon(context, R.id.flat_day_icon, R.id.flat_day_icon_animated, day.icon, animate)
        views.show(R.id.flat_day_high, day.highLow.high, Palette::text)
        views.show(R.id.flat_day_low, day.highLow.low, Palette::textMuted)
        views.setTextViewTextSize(R.id.flat_day_high, TypedValue.COMPLEX_UNIT_PX, degrees)
        views.setTextViewTextSize(R.id.flat_day_low, TypedValue.COMPLEX_UNIT_PX, degrees)
        return views
    }
}

/**
 * Shows the still image [still] in the image view [image], or, when [animate], its animated icon in the frame [frame]
 * beside it, which it fills and runs while the widget is seen (D6, D43); the other stays hidden, and the frame empty
 * without the animated icon, since a launcher applies an update onto the views of the one before.
 */
internal fun RemoteViews.icon(
    context: Context,
    image: Int,
    frame: Int,
    @DrawableRes still: Int,
    animate: Boolean,
) {
    removeAllViews(frame)
    setViewVisibility(image, if (animate) View.GONE else View.VISIBLE)
    setViewVisibility(frame, if (animate) View.VISIBLE else View.GONE)
    if (animate) {
        addView(frame, RemoteViews(context.packageName, WeatherIcons.animated(still)))
    } else {
        setImageViewResource(image, still)
    }
}

/** Shows the list [id] with a row for each of [items], made by [row], or hides it without any. */
internal fun <T> RemoteViews.rows(
    id: Int,
    items: List<T>,
    row: (T) -> RemoteViews,
) {
    setViewVisibility(id, if (items.isEmpty()) View.GONE else View.VISIBLE)
    items.forEach { addView(id, row(it)) }
}
