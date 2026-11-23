package dev.cupr.weather.widget

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.DimenRes
import androidx.annotation.Px
import dev.cupr.weather.R

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

/** A plain text's size in px and the most lines it takes (D47). */
internal class PlainFit(
    @param:Px val size: Float,
    val lines: Int,
)

/**
 * The size and the most lines for the plain [text], [width] dp wide, in a [height] dp high room, or its own size and
 * lines without one: the largest size from `widget_plain_text` down to `widget_plain_text_least`, in the steps of
 * [sizes], at which it fits, as laid out in the widget's font; where none does, the least, in the lines that fit, the
 * last ending in "…" (D47).
 */
internal fun Resources.plainFit(
    text: String,
    width: Float,
    height: Float?,
): PlainFit {
    val sizes = sizes(R.dimen.widget_plain_text, R.dimen.widget_plain_text_least)
    if (height == null) return PlainFit(sizes.first(), Int.MAX_VALUE)
    val layout = { px: Float ->
        val paint =
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = px
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
        val inPx = Math.round(width * displayMetrics.density)
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, inPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
    }
    val fits = sizes.firstOrNull { dp(layout(it).height.toFloat()) <= height }
    val least = layout(sizes.last())
    val lines = maxOf(1, (height / (dp(least.height.toFloat()) / least.lineCount)).toInt())
    return if (fits != null) PlainFit(fits, Int.MAX_VALUE) else PlainFit(sizes.last(), lines)
}
