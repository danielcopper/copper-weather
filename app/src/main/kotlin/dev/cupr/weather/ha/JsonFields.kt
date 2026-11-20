package dev.cupr.weather.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A JSON string's content, or none for any other element. */
internal fun JsonElement.stringContent(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A field that is a JSON string, or none. */
internal fun JsonObject.string(name: String): String? = this[name]?.stringContent()

/** A JSON number as written, or none for any other element: a string, a boolean, `null`, an object or an array. */
internal fun JsonElement.numberContent(): String? =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.takeIf { it.toDoubleOrNull() != null }

/** A field that is a JSON number, or none. */
internal fun JsonObject.number(name: String): Double? = this[name]?.numberContent()?.toDouble()

/** A field that is a JSON object, or none. */
internal fun JsonObject.objectField(name: String): JsonObject? = this[name] as? JsonObject

/** Text that is a JSON object, or none. */
internal fun parseObject(text: String): JsonObject? =
    try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
