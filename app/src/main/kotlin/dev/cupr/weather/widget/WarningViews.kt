package dev.cupr.weather.widget

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import dev.cupr.weather.R
import dev.cupr.weather.palette.Palette

/**
 * A warning that applies on the widget (D47): the band across the top in the wide and tall buckets, and the short form
 * in the line's place in the small and flat ones, each in its level's colour on a tint of that colour at 16 %.
 */
internal object WarningViews {
    /** The alpha of a warning's ground: 16 % of 255, rounded (D47). */
    private const val TINT_ALPHA = 41

    /** Whether the size [bucket] shows a warning as the band, the wide and the tall one, else the short form (D47). */
    fun showsBand(bucket: SizeBucket): Boolean = bucket == SizeBucket.WIDE || bucket == SizeBucket.TALL

    /**
     * A warning that applies, or none, as the size [bucket] shows it (D47): the band in the wide and tall buckets, the
     * short form in the small and the flat one, [width] dp wide there, its tiles no higher than [height] dp, where
     * one row high leaves less than their largest side.
     */
    fun show(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
        bucket: SizeBucket,
        width: Float,
        height: Float? = null,
    ) {
        // The flat layout has no band.
        val band = showsBand(bucket)
        if (bucket != SizeBucket.FLAT) band(context, views, warning.takeIf { band })
        shortForm(context, views, warning.takeUnless { band }, width, height)
    }

    /**
     * The warning band (D47), or nothing without [warning]: its headline unchanged on one line, ending in "…" where it
     * must, without one its level's word and its symbol side by side, and "+N" at its right when more apply.
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
     * A warning's short form (D47), or nothing without [warning]: a tile for each kind that applies, its symbol centred
     * on a ground of the colour of the highest level among that kind's warnings at 16 %, in that colour, and "+N" for
     * the warnings of the kinds whose tiles do not fit, in the highest level's colour; at the side [tileSide] finds for
     * the [width] dp the line has.
     */
    private fun shortForm(
        context: Context,
        views: RemoteViews,
        warning: WidgetContent.Warning?,
        width: Float,
        height: Float?,
    ) {
        views.setViewVisibility(R.id.short_form, if (warning == null) View.GONE else View.VISIBLE)
        views.removeAllViews(R.id.short_tiles)
        if (warning == null) return
        val fit = context.tiles(warning, width, height)
        val side = fit.side * context.resources.displayMetrics.density
        val gap = context.resources.getDimension(R.dimen.widget_tile_gap)
        warning.kinds.take(fit.tiles).forEachIndexed { index, kind ->
            val tile = RemoteViews(context.packageName, R.layout.widget_short_tile)
            tile.setImageViewResource(R.id.short_tile, kind.kind.tile)
            tile.tint(R.id.short_tile, kind.level)
            tile.ground(R.id.short_tile, kind.level)
            tile.setViewLayoutWidth(R.id.short_tile, side, TypedValue.COMPLEX_UNIT_PX)
            tile.setViewLayoutHeight(R.id.short_tile, side, TypedValue.COMPLEX_UNIT_PX)
            val before = if (index == 0) 0f else gap
            tile.setViewLayoutMargin(R.id.short_tile, RemoteViews.MARGIN_START, before, TypedValue.COMPLEX_UNIT_PX)
            views.addView(R.id.short_tiles, tile)
        }
        views.show(R.id.short_more, fit.more, warning.level::colour)
    }

    /** How many of a short form's tiles show, at what side in dp, and "+N" for the warnings of the others (D47). */
    private class Tiles(
        val side: Float,
        val tiles: Int,
        val more: String?,
    )

    /**
     * The short form's tiles for [warning] in [width] dp with a dp to spare: as many as fit, all where they do, each
     * count at the largest side from `widget_tile_largest`, or [height] where that is less, down to `widget_tile_least`
     * at which they and "+N" for the rest fit; where not even one does, one at the least side.
     */
    private fun Context.tiles(
        warning: WidgetContent.Warning,
        width: Float,
        height: Float?,
    ): Tiles {
        val res = resources
        val gap = res.dimen(R.dimen.widget_tile_gap)
        val room = width - res.dimen(R.dimen.widget_fit_slack)
        val step = res.dimen(R.dimen.widget_tile_step)
        val least = res.dimen(R.dimen.widget_tile_least)
        val largest = minOf(res.dimen(R.dimen.widget_tile_largest), height ?: Float.MAX_VALUE).coerceAtLeast(least)
        val sides = generateSequence(largest) { it - step }.takeWhile { it >= least }
        val moreText = res.getDimension(R.dimen.widget_line_flat_text)
        val more = { shown: Int ->
            warning.kinds
                .drop(shown)
                .sumOf { it.count }
                .takeIf { it > 0 }
                ?.let { getString(R.string.warning_more, it) }
        }
        val widthAt = { side: Float, shown: Int ->
            shown * side + (shown - 1) * gap +
                (more(shown)?.let { gap + res.widthOf(it, moreText, Typeface.BOLD) } ?: 0f)
        }
        for (shown in warning.kinds.size downTo 1) {
            sides.firstOrNull { widthAt(it, shown) <= room }?.let { return Tiles(it, shown, more(shown)) }
        }
        return Tiles(least, 1, more(1))
    }

    /** The side in dp of the short form's tiles for [warning] in [width] dp and [height], the height it takes (D47). */
    fun tileSide(
        context: Context,
        warning: WidgetContent.Warning,
        width: Float,
        height: Float?,
    ): Float = context.tiles(warning, width, height).side

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
}

/** How many times as wide as high the symbol of [kind] is, a composite wider than 1 (D48). */
private fun Context.aspect(kind: WarningKind): Float {
    val symbol = checkNotNull(getDrawable(kind.symbol)) { "no symbol $kind" }
    return symbol.intrinsicWidth.toFloat() / symbol.intrinsicHeight
}
