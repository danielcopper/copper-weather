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
 * The widget's card at the opacity [settings] set (D45): its colour, the palette's `surface` light and dark, at that
 * alpha through the card's tint, fully transparent at 0 %, so no card shows; the divider, the texts and the icons stay
 * opaque.
 */
internal fun RemoteViews.card(settings: WidgetSettings) =
    tintGround(R.id.widget, Palette::surface, Math.round(settings.opacity * OPAQUE.toFloat() / WidgetSettings.FULL))

/**
 * Tints the ground of [id], a rounded shape, in [role]'s colour at [alpha], light and dark: the tint takes the shape's
 * place in its own colour (`src_in`, the default), so no drawable's number goes through RemoteViews (D42); the card
 * (D45) and a warning's ground (D47).
 */
internal fun RemoteViews.tintGround(
    id: Int,
    role: (Palette) -> Int,
    alpha: Int,
) = setColorStateList(
    id,
    "setBackgroundTintList",
    ColorStateList.valueOf(ColorUtils.setAlphaComponent(role(Palette.LIGHT), alpha)),
    ColorStateList.valueOf(ColorUtils.setAlphaComponent(role(Palette.DARK), alpha)),
)
