package dev.cupr.weather.store

import android.util.Log
import androidx.core.util.AtomicFile
import dev.cupr.weather.ha.SensorData
import dev.cupr.weather.ha.numberContent
import dev.cupr.weather.ha.objectField
import dev.cupr.weather.ha.parseObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

/**
 * Home Assistant's last good answer (D2, D29): the app sensor's data ([SensorData]) and when it was fetched. Replaced
 * whole by each successful fetch, deleted by each new sign-in and by signing out (D31).
 */
data class Snapshot(
    val fetchedAt: Instant,
    val data: SensorData,
)

/**
 * The snapshot in one JSON file, written whole through [AtomicFile], so a reader finds the old snapshot or the new one,
 * never a part (D29): `fetched_at`, in milliseconds since the epoch, and `data`.
 */
class SnapshotStore internal constructor(
    private val path: File,
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

    /**
     * The snapshot [text] holds, or none when it holds none or its data is not in the schema version the app reads,
     * as after an update of the app that reads another (D30); logged.
     */
    private fun parse(text: String): Snapshot? {
        val json = parseObject(text)
        val fetchedAt = json?.get(FETCHED_AT)?.numberContent()?.toLongOrNull()
        return when (val read = json?.objectField(DATA)?.let(SensorData::read)) {
            is SensorData.Read.Readable if fetchedAt != null -> {
                Snapshot(Instant.ofEpochMilli(fetchedAt), read.data)
            }

            is SensorData.Read.Unreadable -> {
                Log.w(TAG, "The snapshot's data is not read: ${read.reason}")
                null
            }

            else -> {
                Log.w(TAG, "The snapshot's file holds no snapshot")
                null
            }
        }
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
                put(DATA, snapshot.data.json)
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

    /**
     * Deletes the snapshot, with the file AtomicFile keeps beside it while it writes (`.new`) and a backup an older
     * AtomicFile left (`.bak`). Throws [IOException] when one of them is still there afterwards, since AtomicFile's
     * `delete` ignores whether each file went, and a snapshot left behind must never show as a later sign-in's (D31).
     */
    fun clear() {
        file.delete()
        val left = listOf(path, File(path.path + ".new"), File(path.path + ".bak")).filter { it.exists() }
        if (left.isNotEmpty()) {
            Log.w(TAG, "The snapshot could not be deleted: ${left.joinToString { it.name }} left")
            throw IOException("the snapshot was not deleted")
        }
    }

    private companion object {
        const val TAG = "Snapshot"
        const val FETCHED_AT = "fetched_at"
        const val DATA = "data"
    }
}
