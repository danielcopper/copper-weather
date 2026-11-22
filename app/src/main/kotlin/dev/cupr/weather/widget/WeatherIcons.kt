package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes

/** A weather icon in its two forms: its still image (D38) and the layout of its animated icon (D43). */
data class WeatherIcon(
    @param:DrawableRes val still: Int,
    @param:LayoutRes val animated: Int,
)

/**
 * The weather icons of the conditions ([Condition]), drawn after the dashboard's weather icons: each in a day and a
 * night form, light in `drawable` and dark in `drawable-night`, so the launcher picks the mode's own; each as a still
 * image (D38) and as an animated icon, the layout of a ProgressBar that runs it (D43).
 */
object WeatherIcons {
    /**
     * The icon of [condition], its night form when [night]; for a condition the app does not know, or none, the warning
     * sign of `exceptional`, as the dashboard shows an unknown condition (D38).
     */
    fun of(
        condition: String?,
        night: Boolean,
    ): WeatherIcon {
        val known = Condition.of(condition) ?: Condition.EXCEPTIONAL
        return if (night) WeatherIcon(known.night, known.nightAnimated) else WeatherIcon(known.day, known.dayAnimated)
    }
}
