package dev.cupr.weather.palette

import androidx.annotation.ColorInt

/**
 * The palette of D7: one field per role, in a light and a dark instance. The general roles, [bg] to [warning], carry
 * the general role names of the shared colour scheme "copper"; the domain colours, [rain], the intensity scale
 * ([scale1] to [scale5]), the water steps ([water1] to [water3]) and [sun], are the app's own names. ARGB, opaque.
 *
 * Only [LIGHT] and [DARK] exist: the constructor is private, and so is `copy()`, which Kotlin 2.4 makes public unless
 * the class says otherwise. What keeps colours out of the app's other sources: ColorLiteralTest.
 */
@ConsistentCopyVisibility
data class Palette private constructor(
    /** The ground of a screen and of the window; `primary-background-color`. */
    @param:ColorInt val bg: Int,
    /** A card on the ground, and a widget's card; `card-background-color`. */
    @param:ColorInt val surface: Int,
    /** Something raised on a card, such as a button. */
    @param:ColorInt val surfaceRaised: Int,
    /** The border of a card and lines between parts; `ha-card-border-color`. */
    @param:ColorInt val border: Int,
    /** Text and icons; `primary-text-color`. */
    @param:ColorInt val text: Int,
    /** Text that steps back: labels, notes, how old the data is; `secondary-text-color`. */
    @param:ColorInt val textMuted: Int,
    /** The accent, filled: what is active or chosen; `primary-color`. */
    @param:ColorInt val accent: Int,
    /** Text and icons on [accent]; `text-primary-color`. */
    @param:ColorInt val onAccent: Int,
    /** A warning, filled; `warning-color`. */
    @param:ColorInt val warning: Int,
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
                bg = 0xFFEEF1F5.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                surfaceRaised = 0xFFEEF1F5.toInt(),
                border = 0xFFDDE2E9.toInt(),
                text = 0xFF161B22.toInt(),
                textMuted = 0xFF5B6573.toInt(),
                accent = 0xFF1F6FD6.toInt(),
                onAccent = 0xFFFFFFFF.toInt(),
                warning = 0xFFD4612C.toInt(),
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
                bg = 0xFF101317.toInt(),
                surface = 0xFF1A1F25.toInt(),
                surfaceRaised = 0xFF262C34.toInt(),
                border = 0xFF262C34.toInt(),
                text = 0xFFECEEF1.toInt(),
                textMuted = 0xFFA3ACB7.toInt(),
                accent = 0xFF6FB1FF.toInt(),
                onAccent = 0xFF0D1A2A.toInt(),
                warning = 0xFFFF9A6B.toInt(),
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
