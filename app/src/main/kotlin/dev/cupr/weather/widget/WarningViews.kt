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
            val width = context.resources.dimen(R.dimen.widget_band_symbol) * context.aspect(warning.kind)
            views.setViewLayoutWidth(R.id.band_symbol, width, TypedValue.COMPLEX_UNIT_DIP)
        }
        views.show(R.id.band_more, context.more(warning), colour)
    }

    /**
     * A warning's short form (D47), or nothing without [warning]: its word, then the symbols of the kinds that apply,
     * one for each, and "+N" for the warnings whose symbol does not fit, at the size [fit] finds for the [width] dp the
     * line has, from [largest] down; where even the least size does not fit the word, the warning sign stands in its
     * place, unless the first symbol is the warning sign itself. All in the colour of the highest level.
     */
    private fun shortForm(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
        width: Float,
        @DimenRes largest: Int,
    ) {
        views.setViewVisibility(R.id.short_form, if (warning == null) View.GONE else View.VISIBLE)
        views.removeAllViews(R.id.short_symbols)
        if (warning == null) return
        val colour = warning.level::colour
        val word = context.word(warning)
        val aspects = warning.kinds.map { context.aspect(it.kind) }
        val alert = warning.kind != WarningKind.OTHER
        val fit = context.resources.fit(context, warning, word, width, largest, aspects, alert)
        views.ground(R.id.short_pill, warning.level)
        views.show(R.id.short_word, word.takeIf { fit.word }, colour)
        val sign = !fit.word && alert
        views.setViewVisibility(R.id.short_alert, if (sign) View.VISIBLE else View.GONE)
        if (sign) {
            views.tint(R.id.short_alert, warning.level)
            views.setViewLayoutWidth(R.id.short_alert, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutHeight(R.id.short_alert, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
        }
        val gap = context.resources.getDimension(R.dimen.widget_short_gap)
        warning.kinds.take(fit.symbols).forEachIndexed { index, kind ->
            val symbol = RemoteViews(context.packageName, R.layout.widget_short_symbol)
            symbol.setImageViewResource(R.id.short_symbol, kind.kind.symbol)
            symbol.tint(R.id.short_symbol, warning.level)
            symbol.setViewLayoutWidth(R.id.short_symbol, fit.symbol * aspects[index], TypedValue.COMPLEX_UNIT_PX)
            symbol.setViewLayoutHeight(R.id.short_symbol, fit.symbol, TypedValue.COMPLEX_UNIT_PX)
            val before = if (index == 0) 0f else gap
            symbol.setViewLayoutMargin(R.id.short_symbol, RemoteViews.MARGIN_START, before, TypedValue.COMPLEX_UNIT_PX)
            views.addView(R.id.short_symbols, symbol)
        }
        views.show(R.id.short_more, fit.more, colour)
        views.setTextViewTextSize(R.id.short_word, TypedValue.COMPLEX_UNIT_PX, fit.text)
        views.setTextViewTextSize(R.id.short_more, TypedValue.COMPLEX_UNIT_PX, fit.text)
    }

    /**
     * A short form's text size and symbol height in px, whether its word shows, how many of the kinds' symbols show,
     * and "+N" for the warnings of the kinds left out (D47).
     */
    private class Fit(
        val text: Float,
        val symbol: Float,
        val word: Boolean,
        val symbols: Int,
        val more: String?,
    )

    /**
     * The short form of [warning]: its [word], then its kinds' symbols, each [aspects] times as wide as high, and "+N"
     * for the warnings of the kinds left out, at the size it fits [width] dp at with a dp to spare, as measured in the
     * widget's font. The most symbols first, all of them where they fit, each count at the largest size from [largest]
     * down to `widget_short_text_least`; where none fits with the word, the same without it, the warning sign in its
     * place when [alert]; else the least size with the first symbol.
     */
    private fun Resources.fit(
        context: Context,
        warning: WidgetContent.Warning,
        word: String,
        width: Float,
        @DimenRes largest: Int,
        aspects: List<Float>,
        alert: Boolean,
    ): Fit {
        val sizes = sizes(largest, R.dimen.widget_short_text_least)
        val gap = dimen(R.dimen.widget_short_gap)
        val room = width - dimen(R.dimen.widget_fit_slack)
        val more = { shown: Int ->
            warning.kinds.drop(shown).sumOf { it.count }.takeIf { it > 0 }?.let {
                context.getString(R.string.warning_more, it)
            }
        }
        val widthAt = { px: Float, withWord: Boolean, shown: Int ->
            val symbol = dp(px * SYMBOL_PER_TEXT)
            val first =
                when {
                    withWord -> widthOf(word, px) + gap
                    alert -> symbol + gap
                    else -> 0f
                }
            2 * dimen(R.dimen.widget_short_padding) + first +
                aspects
                    .take(shown)
                    .sumOf { (symbol * it).toDouble() }
                    .toFloat() + (shown - 1) * gap + (more(shown)?.let { widthOf(it, px, Typeface.BOLD) + gap } ?: 0f)
        }
        for (withWord in listOf(true, false)) {
            for (shown in aspects.size downTo 1) {
                sizes.firstOrNull { widthAt(it, withWord, shown) <= room }?.let {
                    return Fit(it, it * SYMBOL_PER_TEXT, withWord, shown, more(shown))
                }
            }
        }
        return Fit(sizes.last(), sizes.last() * SYMBOL_PER_TEXT, word = false, symbols = 1, more = more(1))
    }

    /** A warning's word (D47): "Warnung" for the DWD's levels 1 and 2, "Unwetter" for 3 and 4. */
    private fun Context.word(warning: WidgetContent.Warning): String =
        getString(if (warning.level.severe) R.string.warning_word_severe else R.string.warning_word)

    /** "+N" for the N more warnings that apply, none without: the band's (D47). */
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

    /** How many times as wide as high the symbol of [kind] is, a composite wider than 1 (D48). */
    private fun Context.aspect(kind: WarningKind): Float {
        val symbol = checkNotNull(getDrawable(kind.symbol)) { "no symbol $kind" }
        return symbol.intrinsicWidth.toFloat() / symbol.intrinsicHeight
    }
}
