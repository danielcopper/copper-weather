package dev.cupr.weather.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A JSON string's content, or none for any other element. */
internal fun JsonElement.stringContent(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A field that is a JSON string, or none. */
internal fun JsonObject.string(name: String): String? = this[name]?.stringContent()

/**
 * A finite JSON number as written, or none for any other element: a string, a boolean, `null`, an object or an array;
 * NaN or an infinity, which the parser keeps as unquoted values as it does any bare word; or a number too large for a
 * Double.
 */
internal fun JsonElement.numberContent(): String? =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.takeIf { it.toDoubleOrNull()?.isFinite() == true }

/** A field that is a finite JSON number, or none. */
internal fun JsonObject.number(name: String): Double? = this[name]?.numberContent()?.toDouble()

/** A field that is a JSON boolean, or none; the string "true" is not one. */
internal fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toBooleanStrictOrNull()

/** A field that is a JSON object, or none. */
internal fun JsonObject.objectField(name: String): JsonObject? = this[name] as? JsonObject

/** Text that is a JSON object, or none. */
internal fun parseObject(text: String): JsonObject? =
    try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
