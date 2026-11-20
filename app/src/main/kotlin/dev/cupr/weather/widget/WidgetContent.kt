package dev.cupr.weather.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.cupr.weather.R
import dev.cupr.weather.app.Services
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.Snapshot
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * What the widget shows (D33): the snapshot's weather, "now" with the hours and the days after it (D12–D14), or a
 * plain text when there is none to show. Which of it fits is the size bucket's matter ([WidgetViews]).
 */
sealed interface WidgetContent {
    /** No weather to show: a plain text on the card. */
    data class Plain(
        @param:StringRes val text: Int,
    ) : WidgetContent

    /**
     * The snapshot's weather: [now], the [hours] from the current one on and the [days] from tomorrow on, as many as
     * the snapshot has; and whether the sign-in is no longer usable, which shows "Neu anmelden" below "now"
     * ([signInAgain]).
     */
    data class Weather(
        val now: Now,
        val hours: List<Hour>,
        val days: List<Day>,
        val signInAgain: Boolean,
    ) : WidgetContent

    /**
     * "Now" (D12, D36): its still image, the temperature as "7°", [today]'s high and low (none without today among the
     * days), and the line below them, none when there is neither a rain hint nor a condition the app knows.
     */
    data class Now(
        @param:DrawableRes val icon: Int,
        val temperature: String?,
        val today: HighLow?,
        val line: Line?,
    )

    /** A day's high and low as "9°", "–" for one the forecast lacks (D37); shown or left out together. */
    data class HighLow(
        val high: String,
        val low: String,
    )

    /** The one line below "now" (D12): the rain hint, else the condition as a German word. */
    sealed interface Line {
        /** "Regen jetzt": rain falls. */
        data object RainNow : Line

        /** "Regen in 25 min": rain is due in [minutes], 1 to 120. */
        data class RainIn(
            val minutes: Long,
        ) : Line

        /** The condition's German word (D34). */
        data class Word(
            @param:StringRes val word: Int,
        ) : Line
    }

    /** An hour of the hours (D13, D37): its time "14", its still image, its temperature, and its rain when it has. */
    data class Hour(
        val time: String,
        @param:DrawableRes val icon: Int,
        val temperature: String,
        val rain: Rain?,
    )

    /**
     * A day of the days (D14, D37): its weekday, its still image, its high and low, its rain when it has, and the short
     * word of its condition, which the flat bucket shows without rain (D41).
     */
    data class Day(
        val weekday: DayOfWeek,
        @param:DrawableRes val icon: Int,
        val highLow: HighLow,
        val rain: Rain?,
        @param:StringRes val short: Int?,
    )

    /** Rain that falls in an hour or on a day (D37), as the rows write it ([WidgetRows]). */
    sealed interface Rain {
        /** An amount in tenths of a millimetre, never 0: "2,5 mm", "3 mm". */
        data class Amount(
            val tenths: Long,
        ) : Rain {
            val millimetres: Double
                get() = tenths / TENTHS
        }

        /** An amount that rounds to 0 under an image of falling rain, snow or hail: "< 0,1 mm". */
        data object Trace : Rain
    }

    companion object {
        /** How far ahead a rain start makes the rain hint (D12). */
        private val RAIN_AHEAD: Duration = Duration.ofMinutes(120)

        private val MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis().toDouble()

        /** What the widget shows now, from the sign-in and the snapshot the app keeps, at [clock]'s time and zone. */
        fun read(
            context: Context,
            clock: Clock,
        ): WidgetContent = of(Services.account(context).state, Services.snapshots(context).read(), clock)

        /**
         * What the widget shows for [state] and [snapshot] at [clock]'s time, in its zone (D33). A snapshot with
         * neither a temperature nor a condition the app knows counts as none. Signed in without one, "Noch keine
         * Daten"; without one otherwise, or signed out, "Nicht angemeldet"; else the snapshot's weather, with "Neu
         * anmelden" while the sign-in is refused or unreadable.
         */
        fun of(
            state: Account.State,
            snapshot: Snapshot?,
            clock: Clock,
        ): WidgetContent {
            val data = snapshot?.data?.takeIf { it.temperature != null || Condition.of(it.condition)?.word != null }
            return when {
                data == null && state == Account.State.SIGNED_IN -> {
                    Plain(R.string.widget_no_data)
                }

                data == null || state == Account.State.SIGNED_OUT -> {
                    Plain(R.string.widget_signed_out)
                }

                else -> {
                    weather(data, clock, state == Account.State.REJECTED || state == Account.State.UNREADABLE)
                }
            }
        }

        private fun weather(
            data: SensorData,
            clock: Clock,
            signInAgain: Boolean,
        ): Weather {
            val now = clock.instant()
            val today = LocalDate.now(clock)
            val todays = data.days.firstOrNull { it.date == today }
            val time = DateTimeFormatter.ofPattern("HH").withZone(clock.zone)
            return Weather(
                now =
                    Now(
                        icon = WeatherIcons.of(data.condition, night = nightNow(data.twilight)),
                        temperature = data.temperature?.let(::degrees),
                        today = todays?.let { HighLow(forecastDegrees(it.high), forecastDegrees(it.low)) },
                        line = rainHint(data.rainAt, now) ?: Condition.of(data.condition)?.word?.let(Line::Word),
                    ),
                hours =
                    data.hours
                        .filter { it.at.plus(Duration.ofHours(1)) > now }
                        .map {
                            Hour(
                                time = time.format(it.at),
                                icon = WeatherIcons.of(it.condition, it.night),
                                temperature = forecastDegrees(it.temperature),
                                rain = rainAmount(it.precipitation, it.condition),
                            )
                        },
                days =
                    data.days
                        .filter { it.date > today }
                        .map {
                            Day(
                                weekday = it.date.dayOfWeek,
                                icon = WeatherIcons.of(it.condition, night = false),
                                highLow = HighLow(forecastDegrees(it.high), forecastDegrees(it.low)),
                                rain = rainAmount(it.precipitation, it.condition),
                                short = Condition.of(it.condition)?.short,
                            )
                        },
                signInAgain = signInAgain,
            )
        }

        /**
         * Whether "now" shows the night form (D36): at night; in the morning's twilight too and not in the evening's,
         * the picture each twilight starts from, as the dashboard shows it without the sun's times; by day, and
         * without a twilight, the day form.
         */
        internal fun nightNow(twilight: String?): Boolean = twilight == "night" || twilight == "sunrise"

        /**
         * The rain hint (D36) for rain starting at [rainAt], at [now]: "Regen jetzt" once it has started, "Regen in N
         * min" while it is due within two hours, N the minutes rounded as the dashboard rounds them and at least 1;
         * none without a start or with one further ahead.
         */
        internal fun rainHint(
            rainAt: Instant?,
            now: Instant,
        ): Line? {
            if (rainAt == null) return null
            val ahead = Duration.between(now, rainAt)
            return when {
                ahead <= Duration.ZERO -> Line.RainNow
                ahead > RAIN_AHEAD -> null
                else -> Line.RainIn(maxOf(1L, (ahead.toMillis() / MILLIS_PER_MINUTE).roundToLong()))
            }
        }

        /**
         * [celsius] in whole degrees, as the dashboard shows it (D33): JavaScript's `Math.round`, a half toward
         * positive infinity, so 2.5 is "3°" and -2.5 "-2°"; what rounds to zero is "0°", never "-0°". Java's
         * `Math.round`, which `roundToLong` calls, rounds the same way, and a Long has no negative zero.
         */
        internal fun degrees(celsius: Double): String = "${celsius.roundToLong()}°"

        /** A forecast's temperature (D37): [degrees], or an en dash where the forecast has none, as the dashboard. */
        internal fun forecastDegrees(celsius: Double?): String = celsius?.let(::degrees) ?: "–"

        /**
         * The rain of an hour or a day as the dashboard shows it (D37), or none when nothing falls: none without an
         * amount; for an amount that rounds to 0 at one decimal, a trace where [condition]'s image draws falling rain,
         * snow or hail ([Condition.wet]) and none otherwise; else the amount in tenths.
         */
        internal fun rainAmount(
            millimetres: Double?,
            condition: String?,
        ): Rain? {
            val tenths = millimetres?.let { (it * TENTHS).roundToLong() }
            return when {
                tenths == null -> null
                tenths == 0L -> Rain.Trace.takeIf { Condition.of(condition)?.wet == true }
                else -> Rain.Amount(tenths)
            }
        }
    }
}

/** Tenths in a millimetre: the steps rain is shown in (D37). */
private const val TENTHS = 10.0
