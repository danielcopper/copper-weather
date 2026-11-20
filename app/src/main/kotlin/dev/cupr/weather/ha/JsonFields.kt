package dev.cupr.weather.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A JSON string's content, or none for any other element. */
internal fun JsonElement.stringContent(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A field that is a JSON string, or none. */
internal fun JsonObject.string(name: String): String? = this[name]?.stringContent()

/** Text that is a JSON object, or none. */
internal fun parseObject(text: String): JsonObject? =
    try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
