package dev.cupr.weather.widget

import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.DimenRes

/**
 * [text]'s width in dp at [px] in the system's sans-serif, the font every text of the widget is set in (`styles.xml`,
 * D36), [style] as `Typeface`'s styles.
 */
internal fun Resources.widthOf(
    text: String,
    px: Float,
    style: Int = Typeface.NORMAL,
): Float {
    val paint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = px
            typeface = Typeface.create("sans-serif", style)
        }
    return dp(paint.measureText(text))
}

/** The width in dp of [pair] side by side at [px]: the high in bold, [gap] and the low (D36). */
internal fun Resources.widthOf(
    pair: WidgetContent.HighLow,
    px: Float,
    @DimenRes gap: Int,
): Float = widthOf(pair.high, px, Typeface.BOLD) + dimen(gap) + widthOf(pair.low, px)
