package dev.cupr.weather.widget

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Sample weather for the widget's tests: the app sensor's data at [NOW], Friday 20 November 2026, 13:40 at the
 * phone's offset +01:00, written as the app sensor writes its times (hours in UTC, days at local midnight).
 */
internal object SampleWeather {
    val ZONE: ZoneOffset = ZoneOffset.ofHours(1)
    val NOW: Instant = OffsetDateTime.parse("2026-11-20T13:40:00+01:00").toInstant()
    val CLOCK: Clock = Clock.fixed(NOW, ZONE)

    /**
     * Hours from 12:00 local time, the one before the current: [count] of them, each 1 °C cooler than the one before
     * from 8.4 °C; the conditions turn cloudy, rainy with 0.4 mm at 15:00 local, a trace at 16:00 (rainy, 0.04 mm),
     * and dry otherwise; night from 17:00 local.
     */
    fun hours(count: Int = 24): String =
        (0 until count).joinToString(",", "[", "]") { index ->
            val at = OffsetDateTime.parse("2026-11-20T11:00:00Z").plusHours(index.toLong())
            val local = at.atZoneSameInstant(ZONE).hour
            val condition =
                when (local) {
                    15, 16 -> "rainy"
                    else -> "cloudy"
                }
            val rain =
                when (local) {
                    15 -> 0.4
                    16 -> 0.04
                    else -> 0.0
                }
            """{"datetime":"$at","condition":"$condition","temperature":${8.4 - index},""" +
                """"precipitation":$rain,"night":${local >= NIGHT_FROM || local < DAY_FROM}}"""
        }

    /**
     * Today and the nine days after it, each at local midnight: today 9.4 °C high and 2.6 °C low, partly cloudy; the
     * days after alternately sunny and dry and rainy with 2.5 mm, highs from 10 °C up and lows from 1 °C up.
     */
    fun days(): String =
        (0 until DAYS).joinToString(",", "[", "]") { index ->
            val date = "2026-11-${20 + index}T00:00:00+01:00"
            if (index == 0) {
                """{"datetime":"$date","condition":"partlycloudy","temperature":9.4,"templow":2.6,"precipitation":0.0}"""
            } else {
                val wet = index % 2 == 0
                """{"datetime":"$date","condition":"${if (wet) "rainy" else "sunny"}",""" +
                    """"temperature":${9 + index},"templow":$index,"precipitation":${if (wet) 2.5 else 0.0}}"""
            }
        }

    /** The app sensor's data with "now" partly cloudy at 7.4 °C, and [extra] fields after it. */
    fun data(extra: String = ""): String =
        """{"schema_version":1,"now":{"condition":"partlycloudy","temperature":7.4},"twilight":"day"$extra}"""

    /** [data] with the hours and days, rain due in [rainIn] when given. */
    fun full(rainIn: Duration? = null): String {
        val rain = rainIn?.let { ""","rain_at":"${OffsetDateTime.ofInstant(NOW.plus(it), ZoneOffset.UTC)}"""" } ?: ""
        return data(""","hours":${hours()},"days":${days()}$rain""")
    }

    private const val NIGHT_FROM = 17
    private const val DAY_FROM = 8
    private const val DAYS = 10
}
