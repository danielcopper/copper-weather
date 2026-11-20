package dev.cupr.weather.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
