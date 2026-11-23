package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import dev.cupr.weather.R

/**
 * Sets [id]'s width to [size] as a value in dp read here, not as a resource's number the launcher looks up: a launcher
 * can keep an earlier build's resources, where that number names another one (D42).
 */
internal fun RemoteViews.setWidth(
    context: Context,
    id: Int,
    @DimenRes size: Int,
) = setViewLayoutWidth(id, context.resources.dimen(size), TypedValue.COMPLEX_UNIT_DIP)

/** Sets [id]'s height to [size] as a value in dp read here, as [setWidth] does (D42). */
internal fun RemoteViews.setHeight(
    context: Context,
    id: Int,
    @DimenRes size: Int,
) = setViewLayoutHeight(id, context.resources.dimen(size), TypedValue.COMPLEX_UNIT_DIP)

/** [px] in dp on this display. */
internal fun Resources.dp(px: Float): Float = px / displayMetrics.density

/** The dimension [size] in dp. */
internal fun Resources.dimen(
    @DimenRes size: Int,
): Float = dp(getDimension(size))

/**
 * The sizes in px from [largest] down to [least], a step of `widget_text_step` apart, largest first: those the app
 * picks from for a text it sizes to fit (D36, D41).
 */
internal fun Resources.sizes(
    @DimenRes largest: Int,
    @DimenRes least: Int,
): List<Float> {
    val from = getDimension(largest)
    val step = getDimension(R.dimen.widget_text_step)
    return (0..Math.round((from - getDimension(least)) / step)).map { from - it * step }
}
