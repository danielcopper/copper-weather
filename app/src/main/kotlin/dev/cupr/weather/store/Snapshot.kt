package dev.cupr.weather.store

import android.util.Log
import androidx.core.util.AtomicFile
import dev.cupr.weather.ha.parseObject
import dev.cupr.weather.ha.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

/**
 * Home Assistant's last good answer (D2, D29): the app sensor's attribute `data` as Home Assistant sent it, a field
 * without a value absent as it was there, and when it was fetched. Replaced whole by each successful fetch, deleted by
 * each new sign-in and by signing out (D31).
 */
data class Snapshot(
    val fetchedAt: Instant,
    val data: JsonObject,
) {
    /** `now`'s `temperature` in °C, none when it is absent or not a number. */
    val temperature: Double?
        get() = now?.number("temperature")

    /** `now`'s `condition`, Home Assistant's word (GLOSSARY.md, Condition), none when it is absent. */
    val condition: String?
        get() = now?.string("condition")

    /** The source note the app's screen shows (D18), none when it is absent. */
    val attribution: String?
        get() = data.string("attribution")

    private val now: JsonObject?
        get() = data["now"] as? JsonObject

    private fun JsonObject.number(name: String): Double? =
        (this[name] as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.content
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() }
}

/**
 * The snapshot in one JSON file, written whole through [AtomicFile], so a reader finds the old snapshot or the new one,
 * never a part (D29): `fetched_at`, in milliseconds since the epoch, and `data`.
 */
class SnapshotStore internal constructor(
    path: File,
    atomic: (File) -> AtomicFile,
) {
    constructor(path: File) : this(path, ::AtomicFile)

    private val file = atomic(path)

    /** The snapshot, or none when there is none yet or the file cannot be read (logged). */
    fun read(): Snapshot? = text()?.let(::parse)

    /** The file's text, or none when there is no file or it cannot be read (logged). */
    private fun text(): String? =
        try {
            String(file.readFully(), Charsets.UTF_8)
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            Log.w(TAG, "The snapshot cannot be read: ${e.javaClass.simpleName}")
            null
        }

    /** The snapshot [text] holds, or none when it holds none (logged). */
    private fun parse(text: String): Snapshot? {
        val json = parseObject(text)
        val fetchedAt = (json?.get(FETCHED_AT) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        val data = json?.get(DATA) as? JsonObject
        if (fetchedAt == null || data == null) Log.w(TAG, "The snapshot's file holds no snapshot")
        return if (fetchedAt == null || data == null) null else Snapshot(Instant.ofEpochMilli(fetchedAt), data)
    }

    /**
     * Throws [IOException] when the new file cannot be written, the old snapshot staying then; or when the file read
     * back after the write does not hold exactly the bytes written, since AtomicFile's `finishWrite` only logs a failed
     * sync, close or rename, which would leave the old snapshot, of any length, in place.
     */
    fun write(snapshot: Snapshot) {
        val json =
            buildJsonObject {
                put(FETCHED_AT, snapshot.fetchedAt.toEpochMilli())
                put(DATA, snapshot.data)
            }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        var written = false
        try {
            out.write(bytes)
            file.finishWrite(out)
            written = true
        } finally {
            // Whatever stopped the write, its new file goes and the old snapshot stays.
            if (!written) file.failWrite(out)
        }
        if (!file.readFully().contentEquals(bytes)) throw IOException("the snapshot was not written")
    }

    fun clear() = file.delete()

    private companion object {
        const val TAG = "Snapshot"
        const val FETCHED_AT = "fetched_at"
        const val DATA = "data"
    }
}
