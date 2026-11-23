package dev.cupr.weather.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The app sensor's data and the fields of it the app shows (D3, D30). */
class SensorDataTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private fun data(text: String) = SensorData(json(text))

    @Test
    fun `the fields the app shows read from now and the top`() {
        val data = data(FakeHomeAssistant.DATA)

        assertEquals(7.4, data.temperature)
        assertEquals("partlycloudy", data.condition)
        assertEquals("Weather data: a test source", data.attribution)
    }

    @Test
    fun `fields of the wrong kind read as absent`() {
        val odd = data("""{"attribution":3,"now":{"condition":["sunny"],"temperature":"7"}}""")

        assertNull(odd.temperature)
        assertNull(odd.condition)
        assertNull(odd.attribution)
        assertNull(data("""{"now":[]}""").temperature)
        assertNull(data("""{"now":{"temperature":true}}""").temperature)
        assertNull(data("""{"now":{"temperature":null}}""").temperature)
        assertNull(data("{}").condition)
    }

    @Test
    fun `a temperature that is not a finite number reads as absent`() {
        for (number in listOf("NaN", "Infinity", "-Infinity", "1e400", "-1e400")) {
            assertNull(number, data("""{"now":{"temperature":$number}}""").temperature)
        }
        assertEquals(-1.0e300, data("""{"now":{"temperature":-1e300}}""").temperature)
    }

    @Test
    fun `the hours read their start, condition, temperature, rain and night, and an hour without a time is left out`() {
        val data =
            data(
                """{"hours":[{"datetime":"2026-11-20T14:00:00+00:00","condition":"rainy","temperature":6.5,""" +
                    """"precipitation":0.4,"night":true},{"condition":"sunny"},{"datetime":"14:00"},""" +
                    """{"datetime":"2026-11-20T15:00:00Z","night":"true"},3]}""",
            )

        assertEquals(
            listOf(
                SensorData.Hour(Instant.parse("2026-11-20T14:00:00Z"), "rainy", 6.5, 0.4, night = true),
                SensorData.Hour(Instant.parse("2026-11-20T15:00:00Z"), null, null, null, night = false),
            ),
            data.hours,
        )
        assertEquals(emptyList<SensorData.Hour>(), data("""{"hours":{}}""").hours)
        assertEquals(emptyList<SensorData.Hour>(), data("{}").hours)
    }

    @Test
    fun `the days read their date as written, condition, high, low and rain, and a day without a date is left out`() {
        val data =
            data(
                """{"days":[{"datetime":"2026-11-21T00:00:00+01:00","condition":"sunny","temperature":10,""" +
                    """"templow":-1.5,"precipitation":0},{"datetime":"2026-11-22T00:00:00"},{"datetime":"morgen"},""" +
                    """{"temperature":3}]}""",
            )

        assertEquals(
            listOf(
                SensorData.Day(LocalDate.of(2026, 11, 21), "sunny", 10.0, -1.5, 0.0),
                SensorData.Day(LocalDate.of(2026, 11, 22), null, null, null, null),
            ),
            data.days,
        )
    }

    @Test
    fun `the warnings read their fields, each absent as sent or of another kind, and an absent list as none`() {
        val data =
            data(
                """{"warnings":[{"level":2,"type":52,"headline":"Amtliche WARNUNG vor STURMBÖEN",""" +
                    """"start":"2026-11-20T12:00:00+01:00","end":"2026-11-20T18:00:00+01:00"},{},""" +
                    """{"level":2.5,"type":"52","headline":3,"start":"2026-11-20T12:00:00","end":5},7]}""",
            )

        assertEquals(
            listOf(
                SensorData.Warning(
                    level = 2,
                    type = 52,
                    headline = "Amtliche WARNUNG vor STURMBÖEN",
                    start = Instant.parse("2026-11-20T11:00:00Z"),
                    end = Instant.parse("2026-11-20T17:00:00Z"),
                ),
                SensorData.Warning(null, null, null, null, null),
                SensorData.Warning(null, null, null, null, null),
            ),
            data.warnings,
        )
        assertEquals(emptyList<SensorData.Warning>(), data("""{"warnings":[]}""").warnings)
        // Absent or not a list: the warning sensor delivers nothing, which is not the same as no warning (D15).
        assertNull(data("{}").warnings)
        assertNull(data("""{"warnings":{}}""").warnings)
        assertNull(data("""{"warnings":null}""").warnings)
    }

    @Test
    fun `rain_at and twilight read as sent, a rain start without an offset as absent`() {
        assertEquals(
            Instant.parse("2026-11-20T12:05:00Z"),
            data("""{"rain_at":"2026-11-20T13:05:00+01:00","twilight":"sunset"}""").rainAt,
        )
        assertEquals("sunset", data("""{"twilight":"sunset"}""").twilight)
        assertNull(data("""{"rain_at":"2026-11-20T13:05:00"}""").rainAt)
        assertNull(data("""{"rain_at":5}""").rainAt)
        assertNull(data("{}").twilight)
    }

    @Test
    fun `schema version 1 is read, a newer one is named as such, and anything else is unexpected`() {
        assertEquals(
            SensorData.Read.Readable(data("""{"schema_version":1}""")),
            SensorData.read(json("""{"schema_version":1}""")),
        )
        assertEquals(
            SensorData.Read.Unreadable("schema_version 2 is newer than 1", FailureKind.NEWER_SCHEMA),
            SensorData.read(json("""{"schema_version":2}""")),
        )
        val unexpected =
            mapOf(
                """{"now":{}}""" to "the data has no schema_version",
                """{"schema_version":"1"}""" to "schema_version is not a whole number",
                """{"schema_version":1.5}""" to "schema_version is not a whole number",
                """{"schema_version":null}""" to "schema_version is not a whole number",
                """{"schema_version":true}""" to "schema_version is not a whole number",
                """{"schema_version":0}""" to "schema_version 0 is older than 1",
                """{"schema_version":NaN}""" to "schema_version is not a whole number",
            )
        for ((text, reason) in unexpected) {
            assertEquals(text, SensorData.Read.Unreadable(reason, FailureKind.UNEXPECTED), SensorData.read(json(text)))
        }
    }
}
