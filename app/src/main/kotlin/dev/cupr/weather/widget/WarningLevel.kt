package dev.cupr.weather.widget

import androidx.annotation.ColorInt
import dev.cupr.weather.palette.Palette

/**
 * A warning's DWD level, 1 to 4 (D15, D47): its colour on the palette's intensity scale, and whether its short form's
 * word is "Unwetter" rather than "Warnung".
 */
enum class WarningLevel(
    private val colour: (Palette) -> Int,
    val severe: Boolean,
) {
    /** Level 1, a weather warning: `scale2`. */
    MINOR(Palette::scale2, severe = false),

    /** Level 2, marked weather: `scale3`. */
    MODERATE(Palette::scale3, severe = false),

    /** Level 3, severe weather: `scale4`, "Unwetter". */
    SEVERE(Palette::scale4, severe = true),

    /** Level 4, extreme weather: `scale5`, "Unwetter". */
    EXTREME(Palette::scale5, severe = true),
    ;

    /** The level's colour in [palette]. */
    @ColorInt
    fun colour(palette: Palette): Int = colour.invoke(palette)

    companion object {
        /** The DWD's [level]: absent or below 1 counts as 1, above 4 as 4 (D15). */
        fun of(level: Int?): WarningLevel = entries[(level ?: 1).coerceIn(1, entries.size) - 1]
    }
}
