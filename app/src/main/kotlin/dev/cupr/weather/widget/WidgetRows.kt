package dev.cupr.weather.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.Px
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale

/**
 * The rows the widget adds by size (D13, D14, D37, D41): an hour of the hours, a day of the days, and a day of the flat
 * bucket, each its own views.
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

    /** An hour of the hours: its time, still image, temperature and rain. */
    fun hour(
        context: Context,
        hour: WidgetContent.Hour,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hour)
        views.show(R.id.hour_time, hour.time, Palette::textMuted)
        views.setImageViewResource(R.id.hour_icon, hour.icon)
        views.show(R.id.hour_temperature, hour.temperature, Palette::text)
        views.show(R.id.hour_rain, context.rainText(hour.rain), Palette::rain)
        return views
    }

    /** A day of the days: its weekday, still image, rain, high and low. */
    fun day(
        context: Context,
        day: WidgetContent.Day,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        views.show(R.id.day_weekday, context.weekday(day.weekday), Palette::text)
        views.setImageViewResource(R.id.day_icon, day.icon)
        // The rain keeps its room without an amount, so the highs and lows of all days stand in one column.
        views.show(R.id.day_rain, context.rainText(day.rain), Palette::rain, hidden = View.INVISIBLE)
        views.show(R.id.day_high, day.highLow.high, Palette::text)
        views.show(R.id.day_low, day.highLow.low, Palette::textMuted)
        return views
    }

    /**
     * A day of the flat bucket, as the dashboard's day cell (D41): its weekday, its still image, and its high and its
     * low at [degrees] px, the size [WidgetViews] picks for every day of the strip.
     */
    fun flatDay(
        context: Context,
        day: WidgetContent.Day,
        @Px degrees: Float,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_flat_day)
        views.show(R.id.flat_day_weekday, context.weekday(day.weekday), Palette::textMuted)
        views.setImageViewResource(R.id.flat_day_icon, day.icon)
        views.show(R.id.flat_day_high, day.highLow.high, Palette::text)
        views.show(R.id.flat_day_low, day.highLow.low, Palette::textMuted)
        views.setTextViewTextSize(R.id.flat_day_high, TypedValue.COMPLEX_UNIT_PX, degrees)
        views.setTextViewTextSize(R.id.flat_day_low, TypedValue.COMPLEX_UNIT_PX, degrees)
        return views
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
