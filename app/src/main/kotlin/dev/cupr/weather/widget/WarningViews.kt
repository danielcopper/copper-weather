package dev.cupr.weather.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DimenRes
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette

/**
 * A warning that applies on the widget (D47): the band across the top in the wide and tall buckets, and the short form
 * in the line's place in the small and flat ones, each in its level's colour on a tint of that colour at 16 %.
 */
internal object WarningViews {
    /** The alpha of a warning's ground: 16 % of 255, rounded (D47). */
    private const val TINT_ALPHA = 41

    /** A short form's symbol beside its text: its size for each of the text's (D47). */
    private const val SYMBOL_PER_TEXT = 1.1f

    /** Whether the size [bucket] shows a warning as the band, the wide and the tall one, else the short form (D47). */
    fun showsBand(bucket: SizeBucket): Boolean = bucket == SizeBucket.WIDE || bucket == SizeBucket.TALL

    /**
     * A warning that applies, or none, as the size [bucket] shows it (D47): the band in the wide and tall buckets, the
     * short form in the small and the flat one, [width] dp wide there.
     */
    fun show(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
        bucket: SizeBucket,
        width: Float,
    ) {
        // The flat layout has no band; its short form is smaller.
        val band = showsBand(bucket)
        if (bucket != SizeBucket.FLAT) band(context, views, warning.takeIf { band })
        val largest = if (bucket == SizeBucket.FLAT) R.dimen.widget_short_flat_text else R.dimen.widget_short_text
        shortForm(context, views, warning.takeUnless { band }, width, largest)
    }

    /**
     * The warning band (D47), or nothing without [warning]: its headline unchanged on one line, ending in "…" where it
     * must, without one the short form's word and symbol side by side, and "+N" at its right when more apply.
     */
    private fun band(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
    ) {
        views.setViewVisibility(R.id.band, if (warning == null) View.GONE else View.VISIBLE)
        if (warning == null) return
        val colour = warning.level::colour
        views.ground(R.id.band, warning.level)
        // Without a headline its text stays as the empty room before "+N".
        views.show(R.id.band_text, warning.headline, colour, hidden = View.INVISIBLE)
        views.show(R.id.band_word, context.word(warning).takeIf { warning.headline == null }, colour)
        views.setViewVisibility(R.id.band_symbol, if (warning.headline == null) View.VISIBLE else View.GONE)
        if (warning.headline == null) {
            views.symbol(R.id.band_symbol, warning)
            // A composite symbol is wider than high (D48): its width follows, so it keeps the band's height.
            val width = context.resources.dimen(R.dimen.widget_band_symbol) * context.aspect(warning)
            views.setViewLayoutWidth(R.id.band_symbol, width, TypedValue.COMPLEX_UNIT_DIP)
        }
        views.show(R.id.band_more, context.more(warning), colour)
    }

    /**
     * A warning's short form (D47), or nothing without [warning]: its word, the symbol of its kind and "+N" when more
     * apply, at the size [fit] finds for the [width] dp the line has, from [largest] down; where even the least size
     * does not fit the word, the warning sign stands in its place, unless the kind's symbol is the warning sign itself.
     */
    private fun shortForm(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
        width: Float,
        @DimenRes largest: Int,
    ) {
        views.setViewVisibility(R.id.short_form, if (warning == null) View.GONE else View.VISIBLE)
        if (warning == null) return
        val colour = warning.level::colour
        val word = context.word(warning)
        val more = context.more(warning)
        val aspect = context.aspect(warning)
        val alert = warning.kind != WarningKind.OTHER
        val fit = context.resources.fit(word, more, width, largest, aspect, alert)
        views.ground(R.id.short_pill, warning.level)
        views.show(R.id.short_word, word.takeIf { fit.word }, colour)
        val sign = !fit.word && alert
        views.setViewVisibility(R.id.short_alert, if (sign) View.VISIBLE else View.GONE)
        if (sign) {
            views.tint(R.id.short_alert, warning.level)
            views.setViewLayoutWidth(R.id.short_alert, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutHeight(R.id.short_alert, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
        }
        views.show(R.id.short_more, more, colour)
        views.setTextViewTextSize(R.id.short_word, TypedValue.COMPLEX_UNIT_PX, fit.text)
        views.setTextViewTextSize(R.id.short_more, TypedValue.COMPLEX_UNIT_PX, fit.text)
        views.symbol(R.id.short_symbol, warning)
        views.setViewLayoutWidth(R.id.short_symbol, fit.symbol * aspect, TypedValue.COMPLEX_UNIT_PX)
        views.setViewLayoutHeight(R.id.short_symbol, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
    }

    /** A short form's text size and symbol height in px, and whether its word shows (D47). */
    private class Fit(
        val text: Float,
        val symbol: Float,
        val word: Boolean,
    )

    /**
     * The short form of [word], its symbol, [aspect] times as wide as high, and [more] at the largest of the sizes from
     * [largest] down to `widget_short_text_least` at which it fits [width] dp with a dp to spare, as measured in the
     * widget's font; where none does, without the word, the warning sign in its place when [alert], at the largest
     * that fits, or the least.
     */
    private fun Resources.fit(
        word: String,
        more: String?,
        width: Float,
        @DimenRes largest: Int,
        aspect: Float,
        alert: Boolean,
    ): Fit {
        val sizes = sizes(largest, R.dimen.widget_short_text_least)
        val gap = dimen(R.dimen.widget_short_gap)
        val widthAt = { px: Float, withWord: Boolean ->
            val symbol = dp(px * SYMBOL_PER_TEXT)
            2 * dimen(R.dimen.widget_short_padding) + symbol * aspect +
                (
                    if (withWord) {
                        widthOf(word, px) + gap
                    } else if (alert) {
                        symbol + gap
                    } else {
                        0f
                    }
                ) +
                (more?.let { widthOf(it, px, Typeface.BOLD) + gap } ?: 0f)
        }
        val room = width - dimen(R.dimen.widget_fit_slack)
        val withWord = sizes.firstOrNull { widthAt(it, true) <= room }
        val text = withWord ?: sizes.firstOrNull { widthAt(it, false) <= room } ?: sizes.last()
        return Fit(text, text * SYMBOL_PER_TEXT, word = withWord != null)
    }

    /** A warning's word (D47): "Warnung" for the DWD's levels 1 and 2, "Unwetter" for 3 and 4. */
    private fun Context.word(warning: WidgetContent.Warning): String =
        getString(if (warning.level.severe) R.string.warning_word_severe else R.string.warning_word)

    /** "+N" for the N more warnings that apply, none without. */
    private fun Context.more(warning: WidgetContent.Warning): String? =
        warning.more.takeIf { it > 0 }?.let { getString(R.string.warning_more, it) }

    /** Tints the ground of [id] in the colour of a warning of [level] at 16 %, light and dark. */
    private fun RemoteViews.ground(
        id: Int,
        level: WarningLevel,
    ) = tintGround(id, level::colour, TINT_ALPHA)

    /** Shows the symbol of [warning]'s kind (D48) in the image view [id], in its level's colour, light and dark. */
    private fun RemoteViews.symbol(
        id: Int,
        warning: WidgetContent.Warning,
    ) {
        setImageViewResource(id, warning.kind.symbol)
        tint(id, warning.level)
    }

    /** Tints the image view [id] in the colour of a warning of [level], light and dark. */
    private fun RemoteViews.tint(
        id: Int,
        level: WarningLevel,
    ) = setColorInt(id, "setColorFilter", level.colour(Palette.LIGHT), level.colour(Palette.DARK))

    /** How many times as wide as high the symbol of [warning]'s kind is, a composite wider than 1 (D48). */
    private fun Context.aspect(warning: WidgetContent.Warning): Float {
        val symbol = checkNotNull(getDrawable(warning.kind.symbol)) { "no symbol ${warning.kind}" }
        return symbol.intrinsicWidth.toFloat() / symbol.intrinsicHeight
    }
}
