package dev.cupr.weather.widget

import androidx.annotation.StringRes
import dev.cupr.weather.R

/** Home Assistant's conditions (GLOSSARY.md, Condition) as German words, as Home Assistant shows them (D34). */
object Conditions {
    private val words =
        mapOf(
            "clear-night" to R.string.condition_clear_night,
            "cloudy" to R.string.condition_cloudy,
            "exceptional" to R.string.condition_exceptional,
            "fog" to R.string.condition_fog,
            "hail" to R.string.condition_hail,
            "lightning" to R.string.condition_lightning,
            "lightning-rainy" to R.string.condition_lightning_rainy,
            "partlycloudy" to R.string.condition_partlycloudy,
            "pouring" to R.string.condition_pouring,
            "rainy" to R.string.condition_rainy,
            "snowy" to R.string.condition_snowy,
            "snowy-rainy" to R.string.condition_snowy_rainy,
            "sunny" to R.string.condition_sunny,
            "windy" to R.string.condition_windy,
            "windy-variant" to R.string.condition_windy_variant,
        )

    /** The German word for [condition]; none for a condition the app does not know, which shows nothing (D34). */
    @StringRes
    fun word(condition: String): Int? = words[condition]
}
