package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.cupr.weather.R

/**
 * Home Assistant's conditions (GLOSSARY.md, Condition) and everything the widget shows of one: its German [word], as
 * Home Assistant shows it (D34); its still images by [day] and by [night] (D38); and whether its image draws falling
 * rain, snow or hail ([wet]), which shows a trace of rain as "< 0,1 mm" (D37).
 */
enum class Condition(
    /** The condition as Home Assistant states it. */
    val key: String,
    @param:StringRes val word: Int,
    @param:DrawableRes val day: Int,
    @param:DrawableRes val night: Int,
    val wet: Boolean = false,
) {
    // The dashboard draws sunny and clear-night as one picture: the sun by day, the moon and stars by night.
    CLEAR_NIGHT(
        "clear-night",
        R.string.condition_clear_night,
        R.drawable.weather_sunny_day,
        R.drawable.weather_sunny_night,
    ),
    CLOUDY("cloudy", R.string.condition_cloudy, R.drawable.weather_cloudy_day, R.drawable.weather_cloudy_night),
    EXCEPTIONAL(
        "exceptional",
        R.string.condition_exceptional,
        R.drawable.weather_exceptional_day,
        R.drawable.weather_exceptional_night,
    ),
    FOG("fog", R.string.condition_fog, R.drawable.weather_fog_day, R.drawable.weather_fog_night),
    HAIL("hail", R.string.condition_hail, R.drawable.weather_hail_day, R.drawable.weather_hail_night, wet = true),
    LIGHTNING(
        "lightning",
        R.string.condition_lightning,
        R.drawable.weather_lightning_day,
        R.drawable.weather_lightning_night,
    ),
    LIGHTNING_RAINY(
        "lightning-rainy",
        R.string.condition_lightning_rainy,
        R.drawable.weather_lightning_rainy_day,
        R.drawable.weather_lightning_rainy_night,
        wet = true,
    ),
    PARTLYCLOUDY(
        "partlycloudy",
        R.string.condition_partlycloudy,
        R.drawable.weather_partlycloudy_day,
        R.drawable.weather_partlycloudy_night,
    ),
    POURING(
        "pouring",
        R.string.condition_pouring,
        R.drawable.weather_pouring_day,
        R.drawable.weather_pouring_night,
        wet = true,
    ),
    RAINY("rainy", R.string.condition_rainy, R.drawable.weather_rainy_day, R.drawable.weather_rainy_night, wet = true),
    SNOWY("snowy", R.string.condition_snowy, R.drawable.weather_snowy_day, R.drawable.weather_snowy_night, wet = true),
    SNOWY_RAINY(
        "snowy-rainy",
        R.string.condition_snowy_rainy,
        R.drawable.weather_snowy_rainy_day,
        R.drawable.weather_snowy_rainy_night,
        wet = true,
    ),
    SUNNY("sunny", R.string.condition_sunny, R.drawable.weather_sunny_day, R.drawable.weather_sunny_night),
    WINDY("windy", R.string.condition_windy, R.drawable.weather_windy_day, R.drawable.weather_windy_night),
    WINDY_VARIANT(
        "windy-variant",
        R.string.condition_windy_variant,
        R.drawable.weather_windy_variant_day,
        R.drawable.weather_windy_variant_night,
    ),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The condition Home Assistant names [key], none for one the app does not know, which shows no word (D34). */
        fun of(key: String?): Condition? = byKey[key]
    }
}
