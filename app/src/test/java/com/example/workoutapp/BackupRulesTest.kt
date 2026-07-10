package com.example.workoutapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {

    @Test
    fun fullBackupRules_includeDatabasePreferencesAndOwnedMediaOnly() {
        val xml = File("src/main/res/xml/backup_rules.xml").readText()

        assertTrue(xml.contains("""<include domain="database" path="."/>"""))
        assertTrue(xml.contains("""<include domain="sharedpref" path="."/>"""))
        assertTrue(xml.contains("""<include domain="file" path="."/>"""))
        assertTrue(xml.contains("""<exclude domain="file" path="tmp/"/>"""))

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/backup_rules.xml"))
    }

    @Test
    fun dataExtractionRules_defineCloudAndDeviceTransferMatrices() {
        val xml = File("src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(xml.contains("<cloud-backup"))
        assertTrue(xml.contains("<device-transfer>"))
        assertTrue(xml.contains("""<include domain="database" path="."/>"""))
        assertTrue(xml.contains("""<include domain="sharedpref" path="."/>"""))
        assertTrue(xml.contains("""<include domain="file" path="."/>"""))
        assertTrue(xml.contains("""<exclude domain="file" path="tmp/"/>"""))

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/data_extraction_rules.xml"))
    }
}
