package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import dev.cupr.weather.R

/**
 * The kind of a warning, from its event code, EC_II, by the DWD's groups of the codes, and its symbol, one of Google's
 * Material Symbols (D48). The groups are those of the "CAP DWD Profile" 2.1.14 (17 October 2023), section 3.3, without
 * the codes it marks as optional members; where a code belongs to several groups, its kind is the group its event's
 * name names (section 3.1): a thunderstorm with gusts, rain or hail is a thunderstorm, a snow drift is snow, a thaw is
 * a thaw. A code outside them, also the test warnings and the powerline vibrations, which have no symbol of their own,
 * shows [OTHER]'s warning sign.
 */
enum class WarningKind(
    @param:DrawableRes val symbol: Int,
    /** The event codes of the kind, as the DWD's tables list them, apart by spaces. */
    table: String,
) {
    /** THUNDERSTORM. */
    THUNDERSTORM(
        R.drawable.symbol_thunderstorm,
        "31 33 34 36 38 40 41 42 44 45 46 48 49 90 91 92 93 95 96",
    ),

    /** WIND, the coast's and the lakes' winds among them. */
    WIND(R.drawable.symbol_air, "11 12 13 51 52 53 54 55 56 57 58"),

    /** RAIN. */
    RAIN(R.drawable.symbol_rainy, "61 62 63 64 65 66"),

    /** SNOWFALL and SNOWDRIFT. */
    SNOW(R.drawable.symbol_weather_snowy, "70 71 72 73 74 75 76"),

    /** FOG. */
    FOG(R.drawable.symbol_foggy, "59"),

    /** FROST. */
    FROST(R.drawable.symbol_severe_cold, "22 82 83"),

    /** SLIPPERINESS, BLACK_ICE and RIME_ICE: icy surfaces and glaze. */
    ICE(R.drawable.symbol_ac_unit, "84 85 86 87"),

    /** THAW. */
    THAW(R.drawable.symbol_water_drop, "88 89"),

    /** UV. */
    UV(R.drawable.symbol_sunny, "246"),

    /**
     * HEAT; the profile's group lists only 247, while its events (section 3.1) add 248, EXTREME HITZE, which is heat
     * too.
     */
    HEAT(R.drawable.symbol_heat, "247 248"),

    /** Any other code, or none. */
    OTHER(R.drawable.symbol_warning, ""),
    ;

    private val codes =
        table
            .split(' ')
            .filter(String::isNotEmpty)
            .map(String::toInt)
            .toSet()

    companion object {
        /** The kind of the event code [type], [OTHER] for one outside the groups or none. */
        fun of(type: Int?): WarningKind = entries.firstOrNull { type in it.codes } ?: OTHER
    }
}
