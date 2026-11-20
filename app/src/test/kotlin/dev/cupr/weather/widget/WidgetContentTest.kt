package dev.cupr.weather.widget

import dev.cupr.weather.R
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.store.Account
import dev.cupr.weather.store.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** What the widget makes of the snapshot's weather at a time: its texts and which hours and days (D33, D36, D37). */
class WidgetContentTest {
    private val now = SampleWeather.NOW

    private fun weather(data: String): WidgetContent.Weather {
        val read = SensorData.read(Json.parseToJsonElement(data).jsonObject) as SensorData.Read.Readable
        return WidgetContent.of(Account.State.SIGNED_IN, Snapshot(Instant.EPOCH, read.data), SampleWeather.CLOCK)
            as WidgetContent.Weather
    }

    @Test
    fun `the rain hint counts the minutes to the rain start as the dashboard rounds them, at least 1, up to 120`() {
        val hints =
            listOf(-3600L, -1L, 0L, 1L, 89L, 90L, 1500L, 1529L, 1530L, 7199L, 7200L, 7201L, 10_000L)
                .associateWith { WidgetContent.rainHint(now.plusSeconds(it), now) }

        assertEquals(
            mapOf(
                -3600L to WidgetContent.Line.RainNow,
                -1L to WidgetContent.Line.RainNow,
                0L to WidgetContent.Line.RainNow,
                1L to WidgetContent.Line.RainIn(1),
                89L to WidgetContent.Line.RainIn(1),
                90L to WidgetContent.Line.RainIn(2),
                1500L to WidgetContent.Line.RainIn(25),
                1529L to WidgetContent.Line.RainIn(25),
                1530L to WidgetContent.Line.RainIn(26),
                7199L to WidgetContent.Line.RainIn(120),
                7200L to WidgetContent.Line.RainIn(120),
                7201L to null,
                10_000L to null,
            ),
            hints,
        )
        assertNull(WidgetContent.rainHint(null, now))
    }

    @Test
    fun `without a rain hint the line is the condition's word, and without a known condition there is none`() {
        assertEquals(
            WidgetContent.Line.Word(R.string.condition_partlycloudy),
            weather(SampleWeather.data()).now.line,
        )
        assertEquals(
            WidgetContent.Line.Word(R.string.condition_partlycloudy),
            weather(SampleWeather.data(""","rain_at":"${now.plus(Duration.ofMinutes(121))}"""")).now.line,
        )
        assertEquals(
            WidgetContent.Line.Word(R.string.condition_partlycloudy),
            weather(SampleWeather.data(""","rain_at":"soon"""")).now.line,
        )
        assertNull(weather("""{"schema_version":1,"now":{"temperature":3,"condition":"sandstorm"}}""").now.line)
    }

    @Test
    fun `the temperature is in whole degrees, a half toward positive infinity, as the dashboard rounds`() {
        val shown =
            listOf(7.4, 7.5, 2.5, -2.5, -2.6, -2.4, -0.4, -0.5, -0.0, 0.0, 12.0, 30.49)
                .associateWith(WidgetContent::degrees)

        assertEquals(
            mapOf(
                7.4 to "7°",
                7.5 to "8°",
                2.5 to "3°",
                -2.5 to "-2°",
                -2.6 to "-3°",
                -2.4 to "-2°",
                -0.4 to "0°",
                -0.5 to "0°",
                -0.0 to "0°",
                0.0 to "0°",
                12.0 to "12°",
                30.49 to "30°",
            ),
            shown,
        )
    }

    @Test
    fun `a forecast without a temperature shows an en dash`() {
        assertEquals("–", WidgetContent.forecastDegrees(null))
        assertEquals("-3°", WidgetContent.forecastDegrees(-2.6))
    }

    @Test
    fun `rain is an amount in tenths, a trace only on a wet condition, and nothing without rain`() {
        val trace = WidgetContent.Rain.Trace
        val amounts =
            listOf(
                Triple(2.5, "rainy", WidgetContent.Rain.Amount(25)),
                Triple(3.0, "rainy", WidgetContent.Rain.Amount(30)),
                Triple(12.34, "pouring", WidgetContent.Rain.Amount(123)),
                Triple(0.05, "cloudy", WidgetContent.Rain.Amount(1)),
                Triple(0.06, "sunny", WidgetContent.Rain.Amount(1)),
                Triple(0.04, "rainy", trace),
                Triple(0.0, "snowy", trace),
                Triple(0.0, "lightning-rainy", trace),
                Triple(0.0, "hail", trace),
                Triple(0.0, "snowy-rainy", trace),
                Triple(0.0, "pouring", trace),
                Triple(0.04, "lightning", null),
                Triple(0.0, "cloudy", null),
                Triple(0.0, "sandstorm", null),
                Triple(0.0, null, null),
            )

        for ((mm, condition, rain) in amounts) {
            assertEquals("$mm $condition", rain, WidgetContent.rainAmount(mm, condition))
        }
        assertNull(WidgetContent.rainAmount(null, "rainy"))
    }

    @Test
    fun `the hours start with the current one and read their time in the phone's zone`() {
        val hours = weather(SampleWeather.full()).hours

        assertEquals(23, hours.size)
        assertEquals("13", hours.first().time)
        assertEquals("11", hours.last().time)
    }

    @Test
    fun `on the hour, the hours start with that hour`() {
        val clock = Clock.fixed(OffsetDateTime.parse("2026-11-20T14:00:00+01:00").toInstant(), SampleWeather.ZONE)
        val read = SensorData.read(Json.parseToJsonElement(SampleWeather.full()).jsonObject) as SensorData.Read.Readable
        val weather = WidgetContent.of(Account.State.SIGNED_IN, Snapshot(Instant.EPOCH, read.data), clock)

        assertEquals("14", (weather as WidgetContent.Weather).hours.first().time)
    }

    @Test
    fun `the days start tomorrow, today giving now's high and low`() {
        val weather = weather(SampleWeather.full())

        assertEquals(9, weather.days.size)
        assertEquals(java.time.DayOfWeek.SATURDAY, weather.days.first().weekday)
        assertEquals(WidgetContent.HighLow("9°", "3°"), weather.now.today)
    }

    @Test
    fun `a day without a high or low shows an en dash for it`() {
        val day = """{"datetime":"2026-11-20T00:00:00+01:00","templow":1}"""
        val weather = weather(SampleWeather.data(""","days":[$day]"""))

        assertEquals(WidgetContent.HighLow("–", "1°"), weather.now.today)
    }
}
