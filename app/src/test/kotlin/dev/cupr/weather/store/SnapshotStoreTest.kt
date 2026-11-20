package dev.cupr.weather.store

import androidx.core.util.AtomicFile
import dev.cupr.weather.ha.SensorData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SnapshotStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "snapshot.json") }
    private val store by lazy { SnapshotStore(file) }

    private fun data(text: String) = SensorData(Json.parseToJsonElement(text).jsonObject)

    private val empty = data("""{"schema_version":1}""")

    private val snapshot =
        Snapshot(
            Instant.ofEpochMilli(1_794_470_400_000),
            data("""{"schema_version":1,"attribution":"Source","now":{"condition":"sunny","temperature":7.4}}"""),
        )

    @Test
    fun `there is no snapshot before the first fetch`() {
        assertNull(store.read())
    }

    @Test
    fun `a snapshot reads back whole`() {
        store.write(snapshot)

        assertEquals(snapshot, store.read())
        assertEquals(7.4, store.read()!!.data.temperature)
    }

    @Test
    fun `the file holds when it was fetched and the data as sent`() {
        store.write(snapshot)

        val json = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(setOf("fetched_at", "data"), json.keys)
        assertEquals(snapshot.data.json, json["data"])
    }

    @Test
    fun `a new snapshot replaces the old one whole`() {
        store.write(snapshot)
        val next = Snapshot(Instant.ofEpochMilli(1_794_471_300_000), data("""{"schema_version":1}"""))

        store.write(next)

        assertEquals(next, store.read())
        assertNull(store.read()!!.data.temperature)
    }

    @Test
    fun `a write that cannot start keeps the old snapshot`() {
        store.write(snapshot)
        File(file.path + ".new", "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.write(Snapshot(Instant.ofEpochMilli(2), empty)) }

        assertEquals(snapshot, store.read())
    }

    @Test
    fun `a write whose file does not end up in place is a failure`() {
        // A directory where the snapshot belongs: AtomicFile's rename onto it fails, which it only logs.
        File(file, "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.write(snapshot) }
    }

    @Test
    fun `a rename that fails over an old snapshot of the same length is a failure`() {
        store.write(Snapshot(Instant.ofEpochMilli(1), empty))
        val renameFails = SnapshotStore(file, ::RenameFails)

        // Same length: 1 and 2 are one digit each.
        assertThrows(IOException::class.java) { renameFails.write(Snapshot(Instant.ofEpochMilli(2), empty)) }

        assertEquals(Snapshot(Instant.ofEpochMilli(1), empty), store.read())
    }

    /**
     * AtomicFile as androidx.core leaves it after a rename that fails: the new file closed, the old snapshot in place.
     * The real one logs the failure; this one does not.
     */
    private class RenameFails(
        file: File,
    ) : AtomicFile(file) {
        override fun finishWrite(str: FileOutputStream?) {
            str?.close()
        }
    }

    @Test
    fun `a file that holds no snapshot reads as none`() {
        for (text in listOf(
            """{"data":{}}""",
            """{"fetched_at":"1","data":{}}""",
            """{"fetched_at":1}""",
            "not json",
        )) {
            file.writeText(text)
            assertNull(text, store.read())
        }
    }

    @Test
    fun `clearing deletes the snapshot`() {
        store.write(snapshot)

        store.clear()

        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun `clearing deletes what a write left beside the snapshot too`() {
        store.write(snapshot)
        File(file.path + ".new").writeText("{}")
        File(file.path + ".bak").writeText("{}")

        store.clear()

        assertEquals(emptyList<String>(), folder.root.list()!!.toList())
    }

    @Test
    fun `a new file or an old backup still there after clearing is a failure, and logged`() {
        // Directories with a file in them where AtomicFile keeps its new file and an older one's backup.
        File(file.path + ".new", "blocking").mkdirs()
        File(file.path + ".bak", "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.clear() }

        val logs = ShadowLog.getLogsForTag("Snapshot").joinToString { it.msg }
        assertTrue(logs, logs.contains("The snapshot could not be deleted: snapshot.json.new, snapshot.json.bak left"))
    }

    @Test
    fun `a stored snapshot in another schema version reads as none, and is logged`() {
        file.writeText("""{"fetched_at":1,"data":{"schema_version":2,"now":{"temperature":7}}}""")

        assertNull(store.read())
        val logs = ShadowLog.getLogsForTag("Snapshot").joinToString { it.msg }
        assertTrue(logs, logs.contains("The snapshot's data is not read: schema_version 2 is newer than 1"))
    }

    @Test
    fun `a snapshot that is still there after clearing is a failure, and logged`() {
        // A directory with a file in it where the snapshot belongs: AtomicFile's delete fails and does not report it.
        File(file, "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.clear() }

        val logs = ShadowLog.getLogsForTag("Snapshot").joinToString { it.msg }
        assertTrue(logs, logs.contains("The snapshot could not be deleted: snapshot.json left"))
    }
}
