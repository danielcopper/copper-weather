package dev.cupr.weather.palette

/** The roles a colour has in the app and its widgets; each mode of [Palette] gives every one of them a colour (D7). */
enum class PaletteToken {
    /** The ground of the app's screen and of a widget's card. */
    Surface,

    /** Text and icons. */
    Text,

    /** Text that steps back: labels, notes, how old the data is. */
    TextMuted,

    /** Rain amounts and the rain hint. */
    Rain,

    /** The first step of the intensity scale: comfortable. */
    Scale1,

    /** The second step of the intensity scale: a note. */
    Scale2,

    /** The third step of the intensity scale: caution. */
    Scale3,

    /** The fourth step of the intensity scale: danger. */
    Scale4,

    /** The fifth step of the intensity scale: extreme, where an official scale has it. */
    Scale5,

    /** The lightest of three steps of water: humidity, rain and cold. */
    Water1,

    /** The middle step of water. */
    Water2,

    /** The darkest step of water. */
    Water3,

    /** The sun: sunrise and sunset. */
    Sun,
}
