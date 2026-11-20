package dev.cupr.weather.widget

import android.view.View
import android.widget.RemoteViews
import dev.cupr.weather.palette.Palette

/**
 * Shows [text] in [id], in [role]'s colour for light and for dark, or hides [id] ([hidden]) without a text.
 */
internal fun RemoteViews.show(
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
