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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** What the widget makes of the snapshot's weather at a time: its texts and which hours and days (D33, D36, D37). */
@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val now = SampleWeather.NOW

    private fun weather(data: String): WidgetContent.Weather {
        val read = SensorData.read(Json.parseToJsonElement(data).jsonObject) as SensorData.Read.Readable
        return WidgetContent.of(Account.State.SIGNED_IN, Snapshot(now, read.data), SampleWeather.CLOCK)
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
        val weather = WidgetContent.of(Account.State.SIGNED_IN, Snapshot(now, read.data), clock)

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

    private fun warning(
        level: Int? = 2,
        type: Int? = 52,
        headline: String? = "Amtliche WARNUNG vor STURMBÖEN",
        start: Duration? = Duration.ofHours(-1),
        end: Duration? = Duration.ofHours(1),
    ) = SensorData.Warning(level, type, headline, start?.let(now::plus), end?.let(now::plus))

    @Test
    fun `a warning that applies shows with its level, kind and headline, and one that begins later does not`() {
        assertEquals(
            WidgetContent.Warning(WarningLevel.MODERATE, WarningKind.WIND, "Amtliche WARNUNG vor STURMBÖEN", more = 0),
            WidgetContent.warning(listOf(warning()), now),
        )
        assertNull(WidgetContent.warning(listOf(warning(start = Duration.ofMinutes(1))), now))
        assertNull(WidgetContent.warning(listOf(warning(end = Duration.ZERO)), now))
        assertNull(WidgetContent.warning(listOf(warning(end = Duration.ofMinutes(-1))), now))
        assertEquals(0, WidgetContent.warning(listOf(warning(start = Duration.ZERO)), now)?.more)
        assertNull(WidgetContent.warning(emptyList(), now))
    }

    @Test
    fun `a warning without a start shows at once, and one without an end while the snapshot carries it`() {
        assertEquals(
            WidgetContent.Warning(WarningLevel.MODERATE, WarningKind.WIND, "Amtliche WARNUNG vor STURMBÖEN", more = 0),
            WidgetContent.warning(listOf(warning(start = null)), now),
        )
        assertEquals(
            WidgetContent.Warning(WarningLevel.MODERATE, WarningKind.WIND, "Amtliche WARNUNG vor STURMBÖEN", more = 0),
            WidgetContent.warning(listOf(warning(start = Duration.ofDays(-3), end = null)), now),
        )
        assertEquals(
            WidgetContent.Warning(WarningLevel.MODERATE, WarningKind.WIND, "Amtliche WARNUNG vor STURMBÖEN", more = 0),
            WidgetContent.warning(listOf(warning(start = null, end = null)), now),
        )
    }

    @Test
    fun `of several that apply the highest level shows, the first sent among equals, with how many more apply`() {
        val frost = warning(level = 1, type = 22, headline = "Amtliche WARNUNG vor FROST")
        val gusts = warning(level = 3, type = 54, headline = "Amtliche UNWETTERWARNUNG vor ORKANARTIGEN BÖEN")
        val rain = warning(level = 3, type = 62, headline = "Amtliche UNWETTERWARNUNG vor HEFTIGEM STARKREGEN")
        val later = warning(level = 4, start = Duration.ofHours(2))

        assertEquals(
            WidgetContent.Warning(WarningLevel.SEVERE, WarningKind.WIND, gusts.headline, more = 2),
            WidgetContent.warning(listOf(frost, gusts, rain, later), now),
        )
        assertEquals(
            WidgetContent.Warning(WarningLevel.SEVERE, WarningKind.WIND, gusts.headline, more = 1),
            WidgetContent.warning(listOf(frost, gusts), now),
        )
    }

    @Test
    fun `a level absent or outside 1 to 4 counts as the nearest of them, a headline and a type may be absent`() {
        val shown =
            listOf(null, 0, -2, 1, 2, 3, 4, 5, 20).associateWith {
                WidgetContent.warning(listOf(warning(level = it)), now)?.level
            }

        assertEquals(
            mapOf(
                null to WarningLevel.MINOR,
                0 to WarningLevel.MINOR,
                -2 to WarningLevel.MINOR,
                1 to WarningLevel.MINOR,
                2 to WarningLevel.MODERATE,
                3 to WarningLevel.SEVERE,
                4 to WarningLevel.EXTREME,
                5 to WarningLevel.EXTREME,
                20 to WarningLevel.EXTREME,
            ),
            shown,
        )
        assertEquals(
            WidgetContent.Warning(WarningLevel.MINOR, WarningKind.OTHER, null, more = 0),
            WidgetContent.warning(listOf(warning(level = null, type = null, headline = null)), now),
        )
    }

    @Test
    fun `the event codes take their kinds by the DWD's groups, a code outside them the warning sign`() {
        val kinds =
            mapOf(
                WarningKind.THUNDERSTORM to
                    listOf(31, 33, 34, 36, 38, 40, 41, 42, 44, 45, 46, 48, 49, 90, 91, 92, 93, 95, 96),
                WarningKind.WIND to listOf(11, 12, 13, 51, 52, 53, 54, 55, 56, 57, 58),
                WarningKind.RAIN to listOf(61, 62, 63, 64, 65, 66),
                WarningKind.SNOW to listOf(70, 71, 72, 73, 74, 75, 76),
                WarningKind.FOG to listOf(59),
                WarningKind.FROST to listOf(22, 82, 83),
                WarningKind.ICE to listOf(84, 85, 86, 87),
                WarningKind.THAW to listOf(88, 89),
                WarningKind.UV to listOf(246),
                WarningKind.HEAT to listOf(247, 248),
                WarningKind.OTHER to listOf(14, 79, 98, 99, 0, 100),
            )

        for ((kind, codes) in kinds) {
            for (code in codes) assertEquals("$code", kind, WarningKind.of(code))
        }
        assertEquals(WarningKind.OTHER, WarningKind.of(null))
    }

    @Test
    fun `the weather carries the warning that applies at the clock's time`() {
        val warnings =
            ""","warnings":[{"level":1,"type":22,"headline":"Amtliche WARNUNG vor FROST",""" +
                """"start":"2026-11-20T12:00:00+01:00","end":"2026-11-20T18:00:00+01:00"}]"""

        assertEquals(
            WidgetContent.Warning(WarningLevel.MINOR, WarningKind.FROST, "Amtliche WARNUNG vor FROST", more = 0),
            weather(SampleWeather.data(warnings)).warning,
        )
        assertNull(weather(SampleWeather.data()).warning)
    }

    @Test
    fun `while the snapshot is over an hour old the weather says when it was fetched, on another day with the date`() {
        val read = SensorData.read(Json.parseToJsonElement(SampleWeather.data()).jsonObject) as SensorData.Read.Readable
        val stale = { fetchedAt: Instant ->
            (
                WidgetContent.of(Account.State.SIGNED_IN, Snapshot(fetchedAt, read.data), SampleWeather.CLOCK)
                    as WidgetContent.Weather
            ).stale
        }

        assertNull(stale(now))
        assertNull(stale(now.minus(Duration.ofHours(1))))
        assertEquals("12:39", stale(now.minus(Duration.ofMinutes(61))))
        assertEquals("11:40", stale(now.minus(Duration.ofHours(2))))
        assertEquals("19.11. 23:40", stale(now.minus(Duration.ofHours(14))))
        assertNull(stale(now.plus(Duration.ofHours(2))))
    }

    @Test
    fun `a blank headline reads as none`() {
        for (headline in listOf("", "  ")) {
            assertNull(WidgetContent.warning(listOf(warning(headline = headline)), now)?.headline)
        }
    }

    private fun content(
        data: String,
        state: Account.State = Account.State.SIGNED_IN,
    ): WidgetContent {
        val read = SensorData.read(Json.parseToJsonElement(data).jsonObject) as SensorData.Read.Readable
        return WidgetContent.of(state, Snapshot(now, read.data), SampleWeather.CLOCK)
    }

    private val frostWarning =
        ""","warnings":[{"level":1,"type":22,"headline":"Amtliche WARNUNG vor FROST"}]"""

    @Test
    fun `without a warnings field the content logs it and shows no warning, as with none`() {
        ShadowLog.clear()
        assertNull((content(SampleWeather.data()) as WidgetContent.Weather).warning)
        assertEquals(
            listOf("The snapshot has no warnings: the warning sensor delivers nothing"),
            ShadowLog.getLogsForTag("WidgetContent").map { it.msg },
        )

        ShadowLog.clear()
        assertNull((content(SampleWeather.data(""","warnings":[]""")) as WidgetContent.Weather).warning)
        assertNull((content(SampleWeather.data(""","warnings":{}""")) as WidgetContent.Weather).warning)
        assertEquals(1, ShadowLog.getLogsForTag("WidgetContent").size)
    }

    @Test
    fun `a warning that applies shows also with the plain text of a snapshot without a usable now`() {
        val empty = """{"schema_version":1$frostWarning}"""
        val frost = WidgetContent.Warning(WarningLevel.MINOR, WarningKind.FROST, "Amtliche WARNUNG vor FROST", 0)

        assertEquals(WidgetContent.Plain(R.string.widget_no_data, frost), content(empty))
        assertEquals(
            WidgetContent.Plain(R.string.widget_signed_out, frost),
            content(empty, Account.State.REJECTED),
        )
        // Signed out, the snapshot left behind is no one's (D31).
        assertEquals(
            WidgetContent.Plain(R.string.widget_signed_out, null),
            content(SampleWeather.data(frostWarning), Account.State.SIGNED_OUT),
        )
        assertEquals(WidgetContent.Plain(R.string.widget_no_data, null), content("""{"schema_version":1}"""))
    }
}
