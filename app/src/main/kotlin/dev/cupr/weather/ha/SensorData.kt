package dev.cupr.weather.ha

import kotlinx.serialization.json.JsonObject

/**
 * The app sensor's attribute `data` (D3), as Home Assistant sent it, a field without a value absent as it is there;
 * and the fields of it the app shows. The one place that knows the app sensor's format.
 */
data class SensorData(
    val json: JsonObject,
) {
    /** `now`'s `temperature` in °C, none when it is absent or not a number. */
    val temperature: Double?
        get() = now?.number("temperature")

    /** `now`'s `condition`, Home Assistant's word (GLOSSARY.md, Condition), none when it is absent. */
    val condition: String?
        get() = now?.string("condition")

    /** The source note the app's screen shows (D18), none when it is absent. */
    val attribution: String?
        get() = json.string("attribution")

    private val now: JsonObject?
        get() = json.objectField("now")

    companion object {
        /** The schema version the app reads (D30). */
        const val SCHEMA_VERSION = 1

        /**
         * [json] as the app sensor's data, when its schema version is [SCHEMA_VERSION] (D30). A newer version is
         * [FailureKind.NEWER_SCHEMA]; none, one that is not a whole number, or an older one is
         * [FailureKind.UNEXPECTED].
         */
        fun read(json: JsonObject): HaClient.Result {
            val field = json["schema_version"]
            val version = field?.numberContent()?.toIntOrNull()
            return when {
                version == SCHEMA_VERSION -> {
                    HaClient.Result.Ok(SensorData(json))
                }

                version != null && version > SCHEMA_VERSION -> {
                    HaClient.Result.Failed(
                        "schema_version $version is newer than $SCHEMA_VERSION",
                        FailureKind.NEWER_SCHEMA,
                    )
                }

                field == null -> {
                    HaClient.Result.Failed("the data has no schema_version", FailureKind.UNEXPECTED)
                }

                version == null -> {
                    HaClient.Result.Failed("schema_version is not a whole number", FailureKind.UNEXPECTED)
                }

                else -> {
                    HaClient.Result.Failed(
                        "schema_version $version is older than $SCHEMA_VERSION",
                        FailureKind.UNEXPECTED,
                    )
                }
            }
        }
    }
}
