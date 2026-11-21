package dev.cupr.weather.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.DimenRes

/**
 * Sets [id]'s width to [size] as a value in dp read here, not as a resource's number the launcher looks up: a launcher
 * can keep an earlier build's resources, where that number names another one (D42).
 */
internal fun RemoteViews.width(
    context: Context,
    id: Int,
    @DimenRes size: Int,
) = setViewLayoutWidth(id, context.dp(size), TypedValue.COMPLEX_UNIT_DIP)

/** Sets [id]'s height to [size] as a value in dp read here, as [width] does (D42). */
internal fun RemoteViews.height(
    context: Context,
    id: Int,
    @DimenRes size: Int,
) = setViewLayoutHeight(id, context.dp(size), TypedValue.COMPLEX_UNIT_DIP)

/** [size] in dp. */
private fun Context.dp(
    @DimenRes size: Int,
): Float = resources.getDimension(size) / resources.displayMetrics.density
