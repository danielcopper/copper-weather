package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.DimenRes

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
