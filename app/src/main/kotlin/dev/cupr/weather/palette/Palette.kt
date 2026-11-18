package dev.cupr.weather.palette

import androidx.annotation.ColorInt

/**
 * The single home of every colour of the app and its widgets, one field per role, in a light and a dark instance
 * (D7). ColorLiteralTest finds colour literals elsewhere in the app's sources: hex numbers and strings, Android's
 * and Compose's colour constants and factories, and Android's colour resources. The launcher icon's and the window's
 * colours in the resources mirror fields of this palette, and tests keep them equal.
 *
 * The values are interim: those of the Home Assistant dashboard theme the weather card uses, until the shared colour
 * scheme "copper" exists and the values come from there. ARGB, opaque.
 */
data class Palette(
    /** The ground of the app's screen and of a widget's card; `card-background-color`. */
    @param:ColorInt val surface: Int,
    /** Text and icons; `primary-text-color`. */
    @param:ColorInt val text: Int,
    /** Text that steps back: labels, notes, how old the data is; `secondary-text-color`. */
    @param:ColorInt val textMuted: Int,
    /** Rain amounts and the rain hint. */
    @param:ColorInt val rain: Int,
    /** The first step of the intensity scale: comfortable. */
    @param:ColorInt val scale1: Int,
    /** The second step of the intensity scale: a note. */
    @param:ColorInt val scale2: Int,
    /** The third step of the intensity scale: caution. */
    @param:ColorInt val scale3: Int,
    /** The fourth step of the intensity scale: danger. */
    @param:ColorInt val scale4: Int,
    /** The fifth step of the intensity scale: extreme, where an official scale has it. */
    @param:ColorInt val scale5: Int,
    /** The lightest of three steps of water: humidity, rain and cold. */
    @param:ColorInt val water1: Int,
    /** The middle step of water. */
    @param:ColorInt val water2: Int,
    /** The darkest step of water. */
    @param:ColorInt val water3: Int,
    /** The sun: sunrise and sunset. */
    @param:ColorInt val sun: Int,
) {
    companion object {
        /** The colours outside night mode. */
        val LIGHT =
            Palette(
                surface = 0xFFFFFFFF.toInt(),
                text = 0xFF161B22.toInt(),
                textMuted = 0xFF5B6573.toInt(),
                rain = 0xFF1A66C9.toInt(),
                scale1 = 0xFF1A7040.toInt(),
                scale2 = 0xFF806000.toInt(),
                scale3 = 0xFFA84800.toInt(),
                scale4 = 0xFFB52222.toInt(),
                scale5 = 0xFF7A3FD1.toInt(),
                water1 = 0xFF5D7896.toInt(),
                water2 = 0xFF2F78D8.toInt(),
                water3 = 0xFF0B4FB3.toInt(),
                sun = 0xFFB07800.toInt(),
            )

        /** The colours in night mode. */
        val DARK =
            Palette(
                surface = 0xFF1A1F25.toInt(),
                text = 0xFFECEEF1.toInt(),
                textMuted = 0xFFA3ACB7.toInt(),
                rain = 0xFF8FC2FF.toInt(),
                scale1 = 0xFF7ED6A0.toInt(),
                scale2 = 0xFFF5C451.toInt(),
                scale3 = 0xFFFF9D5C.toInt(),
                scale4 = 0xFFFF7A7A.toInt(),
                scale5 = 0xFFB48CFF.toInt(),
                water1 = 0xFF9FB4CC.toInt(),
                water2 = 0xFF7FB6FF.toInt(),
                water3 = 0xFF4D97FF.toInt(),
                sun = 0xFFF5C451.toInt(),
            )

        /** The palette of night mode when [dark], else the one outside it. */
        fun of(dark: Boolean): Palette = if (dark) DARK else LIGHT
    }
}
