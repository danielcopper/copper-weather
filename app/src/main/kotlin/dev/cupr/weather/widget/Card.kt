package dev.cupr.weather.widget

import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette
import dev.cupr.weather.store.WidgetSettings

/** A colour's alpha when it is fully opaque. */
private const val OPAQUE = 255

/**
 * The widget's card at [opacity] percent (D45): its colour, the palette's `surface` light and dark, at that alpha
 * through the card's tint, which takes the rounded shape's place in its own colour, fully transparent at 0 %, so no
 * card shows; the divider, the texts and the icons stay opaque.
 */
internal fun RemoteViews.card(opacity: Int) {
    val alpha = Math.round(opacity * OPAQUE.toFloat() / WidgetSettings.FULL)
    setColorStateList(
        R.id.widget,
        "setBackgroundTintList",
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(Palette.LIGHT.surface, alpha)),
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(Palette.DARK.surface, alpha)),
    )
}
