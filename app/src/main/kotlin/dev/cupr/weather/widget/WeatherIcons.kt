package dev.cupr.weather.widget

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes

/**
 * The weather icons of the conditions ([Condition]), drawn after the dashboard's weather icons: each in a day and a
 * night form, light in `drawable` and dark in `drawable-night`, so the launcher picks the mode's own; each as a still
 * image (D38) and as an animated icon, the layout of a ProgressBar that runs it (D43).
 */
object WeatherIcons {
    /** The animated icon's layout of each still image. */
    private val animated =
        Condition.entries
            .flatMap { listOf(it.day to it.dayAnimated, it.night to it.nightAnimated) }
            .toMap()

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

    /** The layout of the animated icon of the still image [still], one of [of]'s (D43). */
    @LayoutRes
    fun animated(
        @DrawableRes still: Int,
    ): Int = animated.getValue(still)
}
