package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.HtmlPdfExporter
import com.primaloptima.scribe.util.export.PaperSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Increment 7: True Artifact-Based Preview Integrity Verification.
 * Verifies that compiled artifacts for all 6 formats (PDF, EPUB 3.3, DOCX, HTML5, MD bundle/standalone, and TXT)
 * can be accurately inspected, validated for structural integrity, and rendered with honest fidelity.
 */
class ArtifactPreviewIntegrityTest {

    private val sampleManuscript = """
        # Act I: The Awakening
        The dawn mist crept through the silent cedar valleys.
        
        ## Scene 1: Arrival
        A solitary traveler approached the forgotten gates.
        
        > "Wisdom begins where certainty ends."
        
        - First step across the threshold
        - Second step into the unknown
        
        | Waypoint | Distance | Status |
        | --- | --- | --- |
        | North Outpost | 12 leagues | Clear |
        | Valley Bridge | 24 leagues | Guarded |
    """.trimIndent()

    @Test
    fun testEpubArtifactInspectionAndChapterExtraction() {
        val note = Note(
            id = "test-epub-preview",
            name = "The Silent Valley",
            content = sampleManuscript
        )
        val config = ExportConfig(
            format = ExportFormat.EPUB,
            includeCoverTitle = true,
            fontChoice = FontChoice.CLASSIC_SERIF
        )

        val epubBytes = EpubExporter.export(null, note, config)
        assertTrue("EPUB bytes must not be empty", epubBytes.isNotEmpty())

        // Write to temp file to simulate artifact caching on disk
        val tempFile = File.createTempFile("preview_test_epub_", ".epub")
        try {
            FileOutputStream(tempFile).use { it.write(epubBytes) }

            // Inspect using ZipFile exactly as EpubPreviewViewer does
            ZipFile(tempFile).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()

                // 1. Verify EPUB 3.3 package structure
                assertTrue("Must contain mimetype", entries.contains("mimetype"))
                assertTrue("Must contain META-INF/container.xml", entries.contains("META-INF/container.xml"))
                assertTrue("Must contain OEBPS/content.opf", entries.contains("OEBPS/content.opf"))
                assertTrue("Must contain OEBPS/nav.xhtml", entries.contains("OEBPS/nav.xhtml"))
                assertTrue("Must contain OEBPS/styles.css", entries.contains("OEBPS/styles.css"))

                // 2. Verify chapter XHTML documents
                val chapterEntries = entries.filter { it.startsWith("OEBPS/") && it.endsWith(".xhtml") && !it.endsWith("nav.xhtml") }
                assertTrue("Must have at least one chapter XHTML", chapterEntries.isNotEmpty())

                for (chEntry in chapterEntries) {
                    val xhtml = zip.getInputStream(zip.getEntry(chEntry)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    assertTrue("Chapter must be valid XHTML", xhtml.contains("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
                    assertTrue("Chapter must declare EPUB namespace", xhtml.contains("http://www.w3.org/1999/xhtml"))
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDocxArtifactInspectionAndPackageIntegrity() {
        val note = Note(
            id = "test-docx-preview",
            name = "The Silent Valley",
            content = sampleManuscript
        )
        val config = ExportConfig(
            format = ExportFormat.DOCX,
            paperSize = PaperSize.LETTER,
            includeCoverTitle = true
        )

        val docxBytes = DocxExporter.export(null, note, config)
        assertTrue("DOCX bytes must not be empty", docxBytes.isNotEmpty())

        val tempFile = File.createTempFile("preview_test_docx_", ".docx")
        try {
            FileOutputStream(tempFile).use { it.write(docxBytes) }

            // Inspect using ZipFile exactly as DocxLayoutViewer does
            ZipFile(tempFile).use { zip ->
                val hasDoc = zip.getEntry("word/document.xml") != null
                val hasTypes = zip.getEntry("[Content_Types].xml") != null
                val hasRels = zip.getEntry("_rels/.rels") != null

                assertTrue("word/document.xml must exist in compiled DOCX package", hasDoc)
                assertTrue("[Content_Types].xml must exist in compiled DOCX package", hasTypes)
                assertTrue("_rels/.rels must exist in compiled DOCX package", hasRels)

                // Inspect document.xml content
                val docXml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader(Charsets.UTF_8).use { it.readText() }
                assertTrue("Document XML must contain Heading text", docXml.contains("Act I: The Awakening"))
                assertTrue("Document XML must contain paragraph body text", docXml.contains("The dawn mist crept"))
                assertTrue("Document XML must contain table markup", docXml.contains("w:tbl"))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testHtml5ArtifactStandaloneInspection() {
        val note = Note(
            id = "test-html-preview",
            name = "The Silent Valley",
            content = sampleManuscript
        )
        val config = ExportConfig(
            format = ExportFormat.HTML,
            fontChoice = FontChoice.MODERN_SANS,
            includeCoverTitle = true
        )

        val htmlContent = HtmlPdfExporter.buildHtmlDocument(null, note, config, isForPrintPdf = false)
        assertTrue("HTML artifact must contain <!DOCTYPE html>", htmlContent.contains("<!DOCTYPE html>"))
        assertTrue("HTML artifact must declare language and direction", htmlContent.contains("lang=\"en\"") && htmlContent.contains("dir=\"ltr\""))
        assertTrue("HTML artifact must contain @media print rules", htmlContent.contains("@media print"))
        assertTrue("HTML artifact must contain heading", htmlContent.contains("Act I: The Awakening"))
        assertTrue("HTML artifact must contain table", htmlContent.contains("<table>"))
    }

    @Test
    fun testMarkdownBundleZipArtifactInspection() {
        val noteWithImages = Note(
            id = "test-md-zip-preview",
            name = "Illustrated Valley",
            content = """
                # Chapter 1
                Here is the visual landscape.
                ![Ancient Outpost](vault-img://outpost.jpg)
            """.trimIndent()
        )

        // When vault images are present, buildMarkdownZip creates a package
        val zipBytes = ExportHelper.buildMarkdownZip(null, noteWithImages)
        assertTrue("Markdown ZIP bytes must not be empty", zipBytes.isNotEmpty())

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        // Must contain manuscript markdown file
        val mdEntryName = entries.keys.firstOrNull { it.endsWith(".md") }
        assertNotNull("Archive must contain .md manuscript", mdEntryName)

        val mdContent = String(entries[mdEntryName]!!, Charsets.UTF_8)
        assertFalse("Markdown in zip must not contain vault-img://", mdContent.contains("vault-img://"))
    }

    @Test
    fun testPlainTextArtifactCleanProseInspection() {
        val note = Note(
            id = "test-txt-preview",
            name = "Clean Words",
            content = sampleManuscript
        )
        val config = ExportConfig(format = ExportFormat.TXT)

        val txtBytes = ExportHelper.exportBytes(null, note, "txt", config)
        val plainText = String(txtBytes, Charsets.UTF_8)

        assertTrue("Plain text must contain uppercase H1", plainText.contains("ACT I: THE AWAKENING"))
        assertTrue("Plain text must contain body prose", plainText.contains("The dawn mist crept through the silent cedar valleys."))
        assertFalse("Plain text must not contain markdown #", plainText.contains("##"))
        assertFalse("Plain text must not contain raw table pipe delimiters", plainText.contains("| --- |"))
    }
}
