package dev.cupr.weather.palette

import androidx.annotation.ColorInt

/**
 * The single home of every colour of the app and its widgets, light and dark, by [PaletteToken] (D7). Nothing else
 * in the app holds a colour literal (ColorLiteralTest); the launcher icon's colours in the resources mirror tokens of
 * this palette, and a test keeps them equal.
 *
 * The values are interim: those of the Home Assistant dashboard theme the weather card uses, until the shared colour
 * scheme "copper" exists and the values come from there. ARGB, opaque.
 */
object Palette {
    /** The colours outside night mode. */
    val light: Map<PaletteToken, Int> =
        mapOf(
            // card-background-color, primary-text-color, secondary-text-color
            PaletteToken.Surface to 0xFFFFFFFF.toInt(),
            PaletteToken.Text to 0xFF161B22.toInt(),
            PaletteToken.TextMuted to 0xFF5B6573.toInt(),
            // The theme's own weather colours: rain, the intensity scale, water, sun.
            PaletteToken.Rain to 0xFF1A66C9.toInt(),
            PaletteToken.Scale1 to 0xFF1A7040.toInt(),
            PaletteToken.Scale2 to 0xFF806000.toInt(),
            PaletteToken.Scale3 to 0xFFA84800.toInt(),
            PaletteToken.Scale4 to 0xFFB52222.toInt(),
            PaletteToken.Scale5 to 0xFF7A3FD1.toInt(),
            PaletteToken.Water1 to 0xFF5D7896.toInt(),
            PaletteToken.Water2 to 0xFF2F78D8.toInt(),
            PaletteToken.Water3 to 0xFF0B4FB3.toInt(),
            PaletteToken.Sun to 0xFFB07800.toInt(),
        )

    /** The colours in night mode. */
    val dark: Map<PaletteToken, Int> =
        mapOf(
            PaletteToken.Surface to 0xFF1A1F25.toInt(),
            PaletteToken.Text to 0xFFECEEF1.toInt(),
            PaletteToken.TextMuted to 0xFFA3ACB7.toInt(),
            PaletteToken.Rain to 0xFF8FC2FF.toInt(),
            PaletteToken.Scale1 to 0xFF7ED6A0.toInt(),
            PaletteToken.Scale2 to 0xFFF5C451.toInt(),
            PaletteToken.Scale3 to 0xFFFF9D5C.toInt(),
            PaletteToken.Scale4 to 0xFFFF7A7A.toInt(),
            PaletteToken.Scale5 to 0xFFB48CFF.toInt(),
            PaletteToken.Water1 to 0xFF9FB4CC.toInt(),
            PaletteToken.Water2 to 0xFF7FB6FF.toInt(),
            PaletteToken.Water3 to 0xFF4D97FF.toInt(),
            PaletteToken.Sun to 0xFFF5C451.toInt(),
        )

    /** The colour of [token] in night mode when [dark], else outside it. */
    @ColorInt
    fun color(
        token: PaletteToken,
        dark: Boolean,
    ): Int = (if (dark) this.dark else light).getValue(token)
}
