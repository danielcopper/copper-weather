package dev.cupr.weather.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The app sensor's attribute `data` (D3), as Home Assistant sent it, a field without a value absent as it is there;
 * and the fields of it the app shows. The one place that knows the app sensor's format: only [read] makes one, so each
 * has the schema version the app reads.
 */
@ConsistentCopyVisibility
data class SensorData internal constructor(
    val json: JsonObject,
) {
    /** `now`'s `temperature` in °C, none when it is absent or not a finite number. */
    val temperature: Double?
        get() = now?.number("temperature")

    /** `now`'s `condition`, Home Assistant's word (GLOSSARY.md, Condition), none when it is absent. */
    val condition: String?
        get() = now?.string("condition")

    /** The source note the app's screen shows (D18), none when it is absent. */
    val attribution: String?
        get() = json.string("attribution")

    /** `twilight`: `day`, `sunset`, `night` or `sunrise`, as the app sensor writes it; none when it is absent. */
    val twilight: String?
        get() = json.string("twilight")

    /**
     * `rain_at`, when rain starts or, while it falls, since when; none when it is absent or not a time with an
     * offset.
     */
    val rainAt: Instant?
        get() = json.string("rain_at")?.let(::instant)

    /** `hours`, in the order sent; an hour without a time with an offset is left out. */
    val hours: List<Hour>
        get() =
            entries("hours").mapNotNull { hour ->
                hour.string("datetime")?.let(::instant)?.let { at ->
                    Hour(
                        at = at,
                        condition = hour.string("condition"),
                        temperature = hour.number("temperature"),
                        precipitation = hour.number("precipitation"),
                        night = hour.boolean("night") == true,
                    )
                }
            }

    /** `days`, in the order sent; a day without a date is left out. */
    val days: List<Day>
        get() =
            entries("days").mapNotNull { day ->
                day.string("datetime")?.let(::date)?.let { date ->
                    Day(
                        date = date,
                        condition = day.string("condition"),
                        high = day.number("temperature"),
                        low = day.number("templow"),
                        precipitation = day.number("precipitation"),
                    )
                }
            }

    private val now: JsonObject?
        get() = json.objectField("now")

    /** The objects of the list [name], none of its other elements; empty when it is absent or not a list. */
    private fun entries(name: String): List<JsonObject> =
        (json[name] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    /** An hour of `hours`: when it starts, its condition, °C, mm, and whether the sun is down at its start. */
    data class Hour(
        val at: Instant,
        val condition: String?,
        val temperature: Double?,
        val precipitation: Double?,
        val night: Boolean,
    )

    /** A day of `days`: its date where the weather is, its condition, its high and low in °C, and mm. */
    data class Day(
        val date: LocalDate,
        val condition: String?,
        val high: Double?,
        val low: Double?,
        val precipitation: Double?,
    )

    /** What [read] makes of the app sensor's data. */
    sealed interface Read {
        data class Readable(
            val data: SensorData,
        ) : Read

        /** The app does not read it; [reason] for the log, [kind] for the screen's cause. */
        data class Unreadable(
            val reason: String,
            val kind: FailureKind,
        ) : Read
    }

    companion object {
        /** A time with an offset, ISO 8601, as the app sensor writes its times; none for anything else. */
        private fun instant(text: String): Instant? =
            try {
                OffsetDateTime.parse(text).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }

        /**
         * A day's date as its time states it, the local midnight of the place the weather is for: the date of a time
         * with an offset, or of one without, as the forecast may send it; none for anything else.
         */
        private fun date(text: String): LocalDate? =
            try {
                OffsetDateTime.parse(text).toLocalDate()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(text).toLocalDate()
                } catch (_: DateTimeParseException) {
                    null
                }
            }

        /** The schema version the app reads (D30). */
        const val SCHEMA_VERSION = 1

        /**
         * [json] as the app sensor's data, when its schema version is [SCHEMA_VERSION] (D30). A newer version is
         * [FailureKind.NEWER_SCHEMA]; none, one that is not a whole number, or an older one is
         * [FailureKind.UNEXPECTED].
         */
        fun read(json: JsonObject): Read {
            val field = json["schema_version"]
            val version = field?.numberContent()?.toIntOrNull()
            return when {
                version == SCHEMA_VERSION -> {
                    Read.Readable(SensorData(json))
                }

                version != null && version > SCHEMA_VERSION -> {
                    Read.Unreadable("schema_version $version is newer than $SCHEMA_VERSION", FailureKind.NEWER_SCHEMA)
                }

                field == null -> {
                    Read.Unreadable("the data has no schema_version", FailureKind.UNEXPECTED)
                }

                version == null -> {
                    Read.Unreadable("schema_version is not a whole number", FailureKind.UNEXPECTED)
                }

                else -> {
                    Read.Unreadable("schema_version $version is older than $SCHEMA_VERSION", FailureKind.UNEXPECTED)
                }
            }
        }
    }
}
