package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class EpubExportTest {

    @Test
    fun testEpub3PackageComplianceAndMimetypePosition() {
        val note = Note(
            id = "test-epub-note",
            name = "The Odyssey of Code",
            content = """
                # Chapter 1: The Call
                Once upon a time in a terminal far away.

                # Chapter 2: The Departure
                The ship sailed into uncharted git branches.
            """.trimIndent()
        )

        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)

        assertNotNull(epubBytes)
        assertTrue(epubBytes.isNotEmpty())

        val entryNames = mutableListOf<String>()
        val entries = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entries[entry.name] = zis.readBytes()
                entry = zis.nextEntry
            }
        }

        // EPUB standard: mimetype MUST be the very first entry
        assertEquals("mimetype", entryNames.first())
        val mimetypeStr = entries["mimetype"]?.toString(Charsets.US_ASCII)
        assertEquals("application/epub+zip", mimetypeStr)

        // Mandatory EPUB 3 files
        assertTrue(entries.containsKey("META-INF/container.xml"))
        assertTrue(entries.containsKey("OEBPS/content.opf"))
        assertTrue(entries.containsKey("OEBPS/nav.xhtml"))
        assertTrue(entries.containsKey("OEBPS/toc.ncx"))
        assertTrue(entries.containsKey("OEBPS/styles.css"))

        // Multiple chapters were present -> chapter_1.xhtml and chapter_2.xhtml
        assertTrue(entries.containsKey("OEBPS/chapter_1.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter_2.xhtml"))

        val navXhtml = entries["OEBPS/nav.xhtml"]?.toString(Charsets.UTF_8) ?: ""
        assertTrue(navXhtml.contains("epub:type=\"toc\""))
        assertTrue(navXhtml.contains("chapter_1.xhtml"))
        assertTrue(navXhtml.contains("chapter_2.xhtml"))

        val contentOpf = entries["OEBPS/content.opf"]?.toString(Charsets.UTF_8) ?: ""
        assertTrue(contentOpf.contains("The Odyssey of Code"))
        assertTrue(contentOpf.contains("properties=\"nav\""))
    }
}
