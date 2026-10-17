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

    @Test
    fun testFrontMatterPreservationWithoutDataLoss() {
        val note = Note(
            id = "test-frontmatter-note",
            name = "Epic of the Forgotten",
            content = """
                Dedicated to dreamers and poets across all eras.
                
                May your ink never fade and your words find harbor.
                
                # Chapter 1: The First Step
                The protagonist opened the ancient manuscript.
                
                # Chapter 2: The Crossroads
                Two paths diverged in an obsidian forest.
            """.trimIndent()
        )
        val config = ExportConfig(
            format = ExportFormat.EPUB,
            authorName = "Archivist Vance"
        )
        val epubBytes = EpubExporter.export(null, note, config)
        assertNotNull(epubBytes)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        // Front matter must exist and contain the pre-chapter dedication
        assertTrue("Front matter must be preserved as frontmatter.xhtml", entries.containsKey("OEBPS/frontmatter.xhtml"))
        val frontmatterContent = entries["OEBPS/frontmatter.xhtml"] ?: ""
        assertTrue(frontmatterContent.contains("Dedicated to dreamers and poets"))
        assertTrue(frontmatterContent.contains("epub:type=\"frontmatter\""))
        assertTrue(frontmatterContent.contains("epub:type=\"prologue\""))

        // Chapters 1 and 2 must follow
        assertTrue(entries.containsKey("OEBPS/chapter_1.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter_2.xhtml"))
        val chap1Content = entries["OEBPS/chapter_1.xhtml"] ?: ""
        assertTrue(chap1Content.contains("The First Step"))
        assertTrue(chap1Content.contains("ancient manuscript"))
        assertTrue(chap1Content.contains("epub:type=\"bodymatter\""))

        // Navigation must index both front matter and body chapters
        val navXhtml = entries["OEBPS/nav.xhtml"] ?: ""
        assertTrue("TOC must list frontmatter", navXhtml.contains("frontmatter.xhtml"))
        assertTrue("TOC must list chapter 1", navXhtml.contains("chapter_1.xhtml"))
        assertTrue("TOC must list chapter 2", navXhtml.contains("chapter_2.xhtml"))

        // Semantic landmarks must point bodymatter to chapter_1.xhtml, bypassing front matter
        assertTrue(navXhtml.contains("epub:type=\"landmarks\""))
        assertTrue("Landmark bodymatter must point to first body chapter", navXhtml.contains("epub:type=\"bodymatter\" href=\"chapter_1.xhtml\""))
    }

    @Test
    fun testDynamicAuthorAndModifiedMetadata() {
        val note = Note(
            id = "test-author-note",
            name = "Chronicles of Eldoria",
            content = "# Chapter 1\nSolemn winds howled through the ruined spires."
        )
        val config = ExportConfig(
            format = ExportFormat.EPUB,
            authorName = "Lady Genevieve"
        )
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val opf = entries["OEBPS/content.opf"] ?: ""
        assertTrue("Package metadata must include dynamic author name", opf.contains("<dc:creator id=\"author\">Lady Genevieve</dc:creator>"))
        assertTrue("Package metadata must include manuscript title", opf.contains("<dc:title>Chronicles of Eldoria</dc:title>"))
        assertTrue("Package metadata must include urn:uuid identifier", opf.contains("<dc:identifier id=\"pub-id\">urn:uuid:"))
        assertTrue("Package metadata must include UTC ISO-8601 modified timestamp", opf.contains("<meta property=\"dcterms:modified\">"))
    }

    @Test
    fun testStrictXhtmlAndSelfClosingVoidElements() {
        val note = Note(
            id = "test-strict-xhtml",
            name = "Strict XML Conformance",
            content = """
                # Chapter 1: Elements
                Line one with a hard break  
                Line two following break.
                
                ---
                
                > The mind is everything. What you think you become.
                
                | Metric | Status |
                | :--- | ---: |
                | Build | Verified |
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        val chapContent = entries["OEBPS/chapter_1.xhtml"] ?: ""
        assertTrue("Must include XML declaration", chapContent.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue("Must declare XHTML namespace", chapContent.contains("xmlns=\"http://www.w3.org/1999/xhtml\""))
        assertTrue("Must declare EPUB namespace", chapContent.contains("xmlns:epub=\"http://www.idpf.org/2007/ops\""))
        assertTrue("Void tag hr must be self-closing", chapContent.contains("<hr/>"))
        assertTrue("Void tag br must be self-closing", chapContent.contains("<br/>"))
        assertTrue("Blockquote must be correctly emitted", chapContent.contains("<blockquote>"))
        assertTrue("Table must be correctly emitted", chapContent.contains("<table>"))
    }

    @Test
    fun testSingleChapterWithoutH1Headings() {
        val note = Note(
            id = "test-no-h1-note",
            name = "Prose Without Headers",
            content = """
                This is a reflective essay written entirely in prose.
                
                It contains multiple paragraphs and deep thoughts, but no H1 headers.
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(epubBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        assertTrue("Single chapter must be generated as chapter_1.xhtml", entries.containsKey("OEBPS/chapter_1.xhtml"))
        val chapContent = entries["OEBPS/chapter_1.xhtml"] ?: ""
        assertTrue(chapContent.contains("reflective essay written entirely in prose"))
        assertTrue(chapContent.contains("multiple paragraphs and deep thoughts"))
    }
}
