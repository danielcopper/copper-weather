package dev.cupr.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Nothing the app keeps leaves the phone by backup or device transfer, from either storage. */
class BackupRulesTest {
    @Test
    fun `the manifest turns backup off and names the rules`() {
        val application = parsed("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.getAttribute("android:allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun `cloud backup and device transfer exclude every domain`() {
        val rules = parsed("src/main/res/xml/data_extraction_rules.xml")

        for (section in listOf("cloud-backup", "device-transfer")) {
            val excludes = (rules.getElementsByTagName(section).item(0) as Element).getElementsByTagName("exclude")
            val excluded =
                (0 until excludes.length)
                    .map { excludes.item(it) as Element }
                    .filter { it.getAttribute("path") == "." }
                    .map { it.getAttribute("domain") }
                    .toSet()

            assertEquals(section, DOMAINS, excluded)
        }
    }

    private fun parsed(path: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(File(path))
            .documentElement

    private companion object {
        val DOMAINS =
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref",
            )
    }
}
