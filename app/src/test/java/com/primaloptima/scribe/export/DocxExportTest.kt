package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
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
        assertTrue(stylesXml.contains("Hyperlink"))

        // Verify document content
        val docXml = entries["word/document.xml"] ?: ""
        assertTrue(docXml.contains("Epic Chronicle"))
        assertTrue(docXml.contains("Chapter 1: Foundations"))
        assertTrue(docXml.contains("bold"))
        assertTrue(docXml.contains("w:tbl"))
    }

    @Test
    fun testExternalHyperlinksProduceValidRelationshipAndTag() {
        val note = Note(
            id = "test-link-note",
            name = "Hyperlink Test",
            content = "Visit the [Official Website](https://primaloptima.com) for details."
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val docXml = entries["word/document.xml"] ?: ""
        val relsXml = entries["word/_rels/document.xml.rels"] ?: ""

        // Document must contain <w:hyperlink r:id="...">
        assertTrue("Document must contain <w:hyperlink> element", docXml.contains("<w:hyperlink"))
        assertTrue("Hyperlink must wrap the text run", docXml.contains("Official Website"))

        // Relationships must contain external hyperlink with TargetMode="External"
        assertTrue("Relationships must declare hyperlink type", relsXml.contains("relationships/hyperlink"))
        assertTrue("Relationships must target external URL", relsXml.contains("https://primaloptima.com"))
        assertTrue("Relationships must specify TargetMode=\"External\"", relsXml.contains("TargetMode=\"External\""))
    }

    @Test
    fun testMultiLevelListProducesValidIlvlAndNumbering() {
        val note = Note(
            id = "test-list-note",
            name = "Nested List Test",
            content = """
                - Top Level Alpha
                  - Sub Level Beta
                    - Deep Level Gamma
                1. Ordered Step 1
                  2. Ordered Step 2
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val docXml = entries["word/document.xml"] ?: ""
        val numberingXml = entries["word/numbering.xml"] ?: ""

        // Document must emit different ilvl levels
        assertTrue("Document must emit level 0", docXml.contains("<w:ilvl w:val=\"0\"/>"))
        assertTrue("Document must emit level 1", docXml.contains("<w:ilvl w:val=\"1\"/>"))
        assertTrue("Document must emit level 2", docXml.contains("<w:ilvl w:val=\"2\"/>"))

        // Numbering must declare 9 levels (0..8)
        for (lvl in 0..8) {
            assertTrue("numbering.xml must declare level $lvl", numberingXml.contains("<w:lvl w:ilvl=\"$lvl\">"))
        }
    }

    @Test
    fun testLineBreaksPreservedAsWordBreaks() {
        val note = Note(
            id = "test-break-note",
            name = "Break Test",
            content = "Line one of poem  \nLine two of poem\\\nLine three"
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val docXml = entries["word/document.xml"] ?: ""
        assertTrue("Document must contain <w:br/> for hard line breaks", docXml.contains("<w:br/>"))
    }

    @Test
    fun testWordprocessingMlTableAlignmentAndGrid() {
        val note = Note(
            id = "test-tbl-note",
            name = "Table Test",
            content = """
                | Left | Center | Right |
                | :--- | :---: | ---: |
                | L1 | C1 | R1 |
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val docXml = entries["word/document.xml"] ?: ""
        assertTrue("Document must contain <w:tblGrid>", docXml.contains("<w:tblGrid>"))
        assertTrue("Document must contain <w:gridCol", docXml.contains("<w:gridCol"))
        assertTrue("Table header must have center alignment", docXml.contains("<w:jc w:val=\"center\"/>"))
        assertTrue("Table header must have right alignment", docXml.contains("<w:jc w:val=\"right\"/>"))
    }
    @Test
    fun testMissingImageAssetProducesEditorialCalloutBox() {
        val note = Note(
            id = "test-missing-img",
            name = "Missing Image Test",
            content = """
                # Chapter with Image
                ![Map of Realm](vault-img://missing_map.png)
                *Ancient Map Description*
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        val docXml = entries["word/document.xml"] ?: ""
        val relsXml = entries["word/_rels/document.xml.rels"] ?: ""

        // Should NOT corrupt package with unresolvable image relationship
        assertFalse("Relationships must not contain broken image targets", relsXml.contains("media/image_"))
        // Should contain styled editorial callout box with caption/alt
        assertTrue("Document must contain editorial illustration callout", docXml.contains("[Illustration: Ancient Map Description]"))
        assertTrue("Document callout must have single border", docXml.contains("<w:pBdr>"))
        assertTrue("Document callout must have shading", docXml.contains("<w:shd"))
    }

    @Test
    fun testSoftBreaksReflowNaturallyWithoutWordBreaks() {
        val note = Note(
            id = "test-soft-reflow",
            name = "Reflow Test",
            content = """
                The quick brown fox
                jumps over the lazy dog.
                Another line in standard paragraph.
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.DOCX)
        val docxBytes = DocxExporter.export(null, note, config)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        val docXml = entries["word/document.xml"] ?: ""
        // Soft line breaks reflow as spaces in a single paragraph, no <w:br/>
        assertFalse("Soft breaks within a paragraph must not emit <w:br/>", docXml.contains("<w:br/>"))
        assertTrue("Paragraph text must reflow with spaces", docXml.contains("The quick brown fox jumps over the lazy dog. Another line in standard paragraph."))
    }
}
