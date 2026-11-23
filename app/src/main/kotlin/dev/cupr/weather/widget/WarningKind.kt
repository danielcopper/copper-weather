package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.cupr.weather.R

/**
 * The kind of a warning, from its event code, EC_II, by the DWD's groups of the codes (D48), and its symbol; a code
 * outside them, or none, shows [OTHER]'s warning sign.
 */
enum class WarningKind(
    @param:DrawableRes val symbol: Int,
    /** The symbol centred on a square, for the short form's tile (D47, D48). */
    @param:DrawableRes val tile: Int,
    /** The kind's word a screen reader reads with the tile (D47), none for any other. */
    @param:StringRes val word: Int?,
    /** The event codes of the kind, as the DWD's tables list them, apart by spaces. */
    table: String,
) {
    /** THUNDERSTORM. */
    THUNDERSTORM(
        R.drawable.symbol_thunderstorm,
        R.drawable.tile_thunderstorm,
        R.string.warning_kind_thunderstorm,
        "31 33 34 36 38 40 41 42 44 45 46 48 49 90 91 92 93 95 96",
    ),

    /** WIND, the coast's and the lakes' winds among them. */
    WIND(
        R.drawable.symbol_wind,
        R.drawable.tile_wind,
        R.string.warning_kind_wind,
        "11 12 13 51 52 53 54 55 56 57 58",
    ),

    /** RAIN. */
    RAIN(
        R.drawable.symbol_rain,
        R.drawable.tile_rain,
        R.string.warning_kind_rain,
        "61 62 63 64 65 66",
    ),

    /** SNOWFALL and SNOWDRIFT. */
    SNOW(
        R.drawable.symbol_snow,
        R.drawable.tile_snow,
        R.string.warning_kind_snow,
        "70 71 72 73 74 75 76",
    ),

    /** FOG. */
    FOG(
        R.drawable.symbol_fog,
        R.drawable.tile_fog,
        R.string.warning_kind_fog,
        "59",
    ),

    /** FROST. */
    FROST(
        R.drawable.symbol_frost,
        R.drawable.tile_frost,
        R.string.warning_kind_frost,
        "22 82 83",
    ),

    /** SLIPPERINESS, BLACK_ICE and RIME_ICE: icy surfaces and glaze. */
    ICE(
        R.drawable.symbol_ice,
        R.drawable.tile_ice,
        R.string.warning_kind_ice,
        "84 85 86 87",
    ),

    /** THAW. */
    THAW(
        R.drawable.symbol_thaw,
        R.drawable.tile_thaw,
        R.string.warning_kind_thaw,
        "88 89",
    ),

    /** UV. */
    UV(
        R.drawable.symbol_uv,
        R.drawable.tile_uv,
        R.string.warning_kind_uv,
        "246",
    ),

    /** HEAT. */
    HEAT(
        R.drawable.symbol_heat,
        R.drawable.tile_heat,
        R.string.warning_kind_heat,
        "247 248",
    ),

    /** Any other code, or none. */
    OTHER(
        R.drawable.symbol_other,
        R.drawable.tile_other,
        null,
        "",
    ),
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
