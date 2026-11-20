package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import dev.cupr.weather.R

/**
 * The still images of the conditions (GLOSSARY.md, Condition), drawn after the dashboard's weather icons (D38): each in
 * a day and a night form, light in `drawable` and dark in `drawable-night`, so the launcher picks the mode's own.
 */
object WeatherIcons {
    private val icons: Map<String, Pair<Int, Int>> =
        mapOf(
            // The dashboard draws sunny and clear-night as one picture: the sun by day, the moon and stars by night.
            "clear-night" to (R.drawable.weather_sunny_day to R.drawable.weather_sunny_night),
            "cloudy" to (R.drawable.weather_cloudy_day to R.drawable.weather_cloudy_night),
            "exceptional" to (R.drawable.weather_exceptional_day to R.drawable.weather_exceptional_night),
            "fog" to (R.drawable.weather_fog_day to R.drawable.weather_fog_night),
            "hail" to (R.drawable.weather_hail_day to R.drawable.weather_hail_night),
            "lightning" to (R.drawable.weather_lightning_day to R.drawable.weather_lightning_night),
            "lightning-rainy" to (R.drawable.weather_lightning_rainy_day to R.drawable.weather_lightning_rainy_night),
            "partlycloudy" to (R.drawable.weather_partlycloudy_day to R.drawable.weather_partlycloudy_night),
            "pouring" to (R.drawable.weather_pouring_day to R.drawable.weather_pouring_night),
            "rainy" to (R.drawable.weather_rainy_day to R.drawable.weather_rainy_night),
            "snowy" to (R.drawable.weather_snowy_day to R.drawable.weather_snowy_night),
            "snowy-rainy" to (R.drawable.weather_snowy_rainy_day to R.drawable.weather_snowy_rainy_night),
            "sunny" to (R.drawable.weather_sunny_day to R.drawable.weather_sunny_night),
            "windy" to (R.drawable.weather_windy_day to R.drawable.weather_windy_night),
            "windy-variant" to (R.drawable.weather_windy_variant_day to R.drawable.weather_windy_variant_night),
        )

    /**
     * The still image of [condition], its night form when [night]; for a condition the app does not know, or none,
     * the warning sign of `exceptional`, as the dashboard shows an unknown condition (D38).
     */
    @DrawableRes
    fun of(
        condition: String?,
        night: Boolean,
    ): Int {
        val (dayForm, nightForm) = icons[condition] ?: icons.getValue("exceptional")
        return if (night) nightForm else dayForm
    }
}
