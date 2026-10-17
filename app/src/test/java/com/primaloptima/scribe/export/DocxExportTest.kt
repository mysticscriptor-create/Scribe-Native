package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.PaperSize
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class DocxExportTest {

    @Test
    fun testDocxZipStructureAndRequiredParts() {
        val note = Note(
            id = "test-note-1",
            name = "Epic Chronicle",
            content = """
                # Chapter 1: Foundations
                This is a paragraph with **bold** text and *italic* nuance.

                > An ancient proverb echoed through the halls.

                - Point A
                - Point B

                | Item | Quantity |
                | --- | --- |
                | Swords | 5 |
                | Shields | 10 |

                ---
            """.trimIndent()
        )

        val config = ExportConfig(
            format = ExportFormat.DOCX,
            paperSize = PaperSize.A4,
            includeCoverTitle = true
        )

        val docxBytes = DocxExporter.export(null, note, config)
        assertNotNull(docxBytes)
        assertTrue(docxBytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val content = zis.readBytes().toString(Charsets.UTF_8)
                entries[entry.name] = content
                entry = zis.nextEntry
            }
        }

        // Verify required OpenXML package parts
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/_rels/document.xml.rels"))
        assertTrue(entries.containsKey("word/document.xml"))
        assertTrue(entries.containsKey("word/styles.xml"))
        assertTrue(entries.containsKey("word/numbering.xml"))

        // Verify styles content
        val stylesXml = entries["word/styles.xml"] ?: ""
        assertTrue(stylesXml.contains("Heading1"))
        assertTrue(stylesXml.contains("Heading2"))
        assertTrue(stylesXml.contains("Title"))
        assertTrue(stylesXml.contains("Normal"))

        // Verify document content
        val docXml = entries["word/document.xml"] ?: ""
        assertTrue(docXml.contains("Epic Chronicle"))
        assertTrue(docXml.contains("Chapter 1: Foundations"))
        assertTrue(docXml.contains("bold"))
        assertTrue(docXml.contains("w:tbl"))
    }
}
