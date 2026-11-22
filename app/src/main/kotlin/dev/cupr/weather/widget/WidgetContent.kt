package dev.cupr.weather.widget

import android.content.Context
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
     * ([signInAgain]); the [warning] that applies, if any (D15); and when the snapshot was fetched, as "14:30", while
     * that is over an hour ago ([stale], D16).
     */
    data class Weather(
        val now: Now,
        val hours: List<Hour>,
        val days: List<Day>,
        val signInAgain: Boolean,
        val warning: Warning? = null,
        val stale: String? = null,
    ) : WidgetContent

    /**
     * The warning the widget shows (D15, D47): of those that apply, the one of the highest [level], 1 to 4, its
     * [kind] and its [headline] as the DWD wrote it, none without one; and how many more apply ([more]).
     */
    data class Warning(
        val level: Int,
        val kind: WarningKind,
        val headline: String?,
        val more: Int,
    )

    /**
     * "Now" (D12, D36): its icon, the temperature as "7°", [today]'s high and low (none without today among the days),
     * and the line below them, none when there is neither a rain hint nor a condition the app knows.
     */
    data class Now(
        val icon: WeatherIcon,
        val temperature: String?,
        val today: HighLow?,
        val line: Line?,
        val todayRain: Rain?,
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

    /** An hour of the hours (D13, D37): its time "14", its icon, its temperature, and its rain when it has. */
    data class Hour(
        val time: String,
        val icon: WeatherIcon,
        val temperature: String,
        val rain: Rain?,
    )

    /**
     * A day of the days (D14, D37): its weekday, its icon, its high and low, and its rain when it has.
     */
    data class Day(
        val weekday: DayOfWeek,
        val icon: WeatherIcon,
        val highLow: HighLow,
        val rain: Rain?,
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

        /** How old the snapshot may be before the widget says when it was fetched (D16). */
        private val STALE_AFTER: Duration = Duration.ofHours(1)

        /** The DWD's warning levels, the lowest and the highest (D15). */
        private const val LOWEST_LEVEL = 1
        private const val HIGHEST_LEVEL = 4

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
                        .copy(warning = warning(data.warnings, clock.instant()), stale = stale(snapshot, clock))
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
                        todayRain = todays?.let { rainAmount(it.precipitation, it.condition) },
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
                            )
                        },
                signInAgain = signInAgain,
            )
        }

        /**
         * The warning to show at [now] of [warnings] (D15): of those that apply, their start passed or absent and
         * their end not reached or absent, the one of the highest level, the first sent among equals, with how many
         * more apply; none when none applies. A level absent, below 1 or above 4 counts as the nearest of 1 and 4.
         */
        internal fun warning(
            warnings: List<SensorData.Warning>,
            now: Instant,
        ): Warning? {
            val applying = warnings.filter { (it.start == null || it.start <= now) && (it.end == null || now < it.end) }
            val shown = applying.maxByOrNull { level(it) } ?: return null
            return Warning(level(shown), WarningKind.of(shown.type), shown.headline, applying.size - 1)
        }

        private fun level(warning: SensorData.Warning): Int =
            (warning.level ?: LOWEST_LEVEL).coerceIn(LOWEST_LEVEL, HIGHEST_LEVEL)

        /**
         * When [snapshot] was fetched, while that is over an hour before [clock]'s time (D16): its time as "14:30" in
         * [clock]'s zone, with its date before it, "21.11. 14:30", when that is not today; else none.
         */
        internal fun stale(
            snapshot: Snapshot,
            clock: Clock,
        ): String? {
            if (Duration.between(snapshot.fetchedAt, clock.instant()) <= STALE_AFTER) return null
            val at = snapshot.fetchedAt.atZone(clock.zone)
            val time = at.format(CLOCK_TIME)
            return if (at.toLocalDate() == LocalDate.now(clock)) time else "${at.format(DAY)} $time"
        }

        private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val DAY = DateTimeFormatter.ofPattern("dd.MM.")

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
