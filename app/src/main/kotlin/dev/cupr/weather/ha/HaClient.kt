package dev.cupr.weather.ha

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * What the app reads from Home Assistant with an access token: the app sensor [SENSOR] over REST
 * (`/api/states/<entity_id>`, D3, D28), its attribute `data`.
 *
 * A failure has its [FailureKind] and a reason that names the HTTP status or the exception's class, never a token or
 * the address.
 */
class HaClient(
    private val http: OkHttpClient,
    private val address: HaAddress,
) {
    sealed interface Result {
        /** The app sensor's attribute `data`, in the schema version the app reads ([SensorData.read]). */
        data class Ok(
            val data: SensorData,
        ) : Result

        data class Failed(
            val reason: String,
            val kind: FailureKind,
        ) : Result
    }

    /** The app sensor's attribute `data`, read by [SensorData.read]. */
    fun sensor(accessToken: AccessToken): Result {
        val request =
            Request
                .Builder()
                .url(address.resolve("api/states/$SENSOR"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .build()
        return try {
            http.newCall(request).execute().use(::sensorOf)
        } catch (e: IOException) {
            Result.Failed(e.javaClass.simpleName, FailureKind.of(e))
        }
    }

    private fun sensorOf(response: Response): Result {
        val state = if (response.isSuccessful) parseObject(response.body.string()) else null
        val data = state?.objectField("attributes")?.objectField("data")
        return when {
            !response.isSuccessful -> {
                Result.Failed("HTTP ${response.code} for $SENSOR", kindOf(response.code))
            }

            state == null -> {
                Result.Failed(
                    "HTTP ${response.code} for $SENSOR, but no JSON object",
                    FailureKind.UNEXPECTED,
                )
            }

            data == null -> {
                Result.Failed("$SENSOR has no data", FailureKind.NOT_FOUND)
            }

            else -> {
                when (val read = SensorData.read(data)) {
                    is SensorData.Read.Readable -> Result.Ok(read.data)
                    is SensorData.Read.Unreadable -> Result.Failed(read.reason, read.kind)
                }
            }
        }
    }

    companion object {
        /** The app sensor (D3). */
        const val SENSOR = "sensor.copper_weather"

        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404

        /** What an HTTP status other than success says about the states call, which carries the access token. */
        private fun kindOf(status: Int) =
            when (status) {
                UNAUTHORIZED, FORBIDDEN -> FailureKind.REFUSED
                NOT_FOUND -> FailureKind.NOT_FOUND
                else -> FailureKind.ofStatus(status)
            }
    }
}
