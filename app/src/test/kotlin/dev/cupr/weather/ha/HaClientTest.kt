package dev.cupr.weather.ha

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The states call for the app sensor against the fake Home Assistant (D28). */
class HaClientTest {
    private val home = FakeHomeAssistant()
    private val client = HaClient(OkHttpClient(), home.address)

    @After
    fun stop() = home.close()

    @Test
    fun `Home Assistant refusing the access token is a refusal, 401 or 403`() {
        assertEquals(
            HaClient.Result.Failed("HTTP 401 for sensor.copper_weather", FailureKind.REFUSED),
            client.sensor(AccessToken("another")),
        )

        home.states = { FakeHomeAssistant.json("""{"message":"Forbidden"}""", 403) }

        assertEquals(
            HaClient.Result.Failed("HTTP 403 for sensor.copper_weather", FailureKind.REFUSED),
            client.sensor(AccessToken(home.accessToken)),
        )
    }

    @Test
    fun `a gateway's status is no connection, another one unexpected`() {
        home.states = { FakeHomeAssistant.json("Bad Gateway", 502) }
        assertEquals(
            FailureKind.UNREACHABLE,
            (client.sensor(AccessToken(home.accessToken)) as HaClient.Result.Failed).kind,
        )

        home.states = { FakeHomeAssistant.json("""{"message":"Server error"}""", 500) }
        assertEquals(
            FailureKind.UNEXPECTED,
            (client.sensor(AccessToken(home.accessToken)) as HaClient.Result.Failed).kind,
        )
    }
}
