package dev.cupr.weather.widget

import androidx.annotation.DrawableRes

/**
 * The still images of the conditions ([Condition]), drawn after the dashboard's weather icons (D38): each in a day and
 * a night form, light in `drawable` and dark in `drawable-night`, so the launcher picks the mode's own.
 */
object WeatherIcons {
    /**
     * The still image of [condition], its night form when [night]; for a condition the app does not know, or none,
     * the warning sign of `exceptional`, as the dashboard shows an unknown condition (D38).
     */
    @DrawableRes
    fun of(
        condition: String?,
        night: Boolean,
    ): Int {
        val known = Condition.of(condition) ?: Condition.EXCEPTIONAL
        return if (night) known.night else known.day
    }
}
