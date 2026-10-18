package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testLanguageDetectionAndRtlDirectionalitySeparation() {
        val note = Note(
            id = "test-bidi-note",
            name = "Bilingual Poetry Collection",
            content = """
                # Chapter 1: Introduction
                This volume contains reflections in both English and Urdu.
                
                ستاروں سے آگے جہاں اور بھی ہیں
                
                The journey of thought continues beyond the horizon.
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

        val opf = entries["OEBPS/content.opf"] ?: ""
        // Both primary English and secondary Urdu must be declared in metadata
        assertTrue("Primary language must be en", opf.contains("<dc:language>en</dc:language>"))
        assertTrue("Secondary language must declare ur", opf.contains("<dc:language>ur</dc:language>"))

        val chapContent = entries["OEBPS/chapter_1.xhtml"] ?: ""
        // Chapter document remains LTR since English is primary
        assertTrue("Document root must be ltr", chapContent.contains("dir=\"ltr\""))
        // But the Urdu paragraph must have explicit dir="rtl"
        assertTrue("Urdu paragraph must be decorated with dir=rtl", chapContent.contains("<p dir=\"rtl\">ستاروں سے آگے جہاں اور بھی ہیں</p>"))
        // English paragraph must not have forced rtl
        assertFalse("English paragraph must not be rtl", chapContent.contains("<p dir=\"rtl\">This volume"))
    }

    @Test
    fun testPredominantlyUrduManuscriptSetsGlobalRtl() {
        val note = Note(
            id = "test-urdu-note",
            name = "دیوان غالب",
            content = """
                # دیباچہ
                یہ کتاب مکمل طور پر اردو زبان میں مرتب کی گئی ہے۔
                
                دل ناداں تجھے ہوا کیا ہے
                آخر اس درد کی دوا کیا ہے
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

        val opf = entries["OEBPS/content.opf"] ?: ""
        assertTrue("Primary language must be ur", opf.contains("<dc:language>ur</dc:language>"))

        val chapContent = entries["OEBPS/chapter_1.xhtml"] ?: ""
        assertTrue("Root HTML must have dir=rtl", chapContent.contains("dir=\"rtl\""))
        assertTrue("Root HTML must have lang=ur", chapContent.contains("lang=\"ur\""))
    }

    @Test
    fun testMissingImageEmitsEditorialCalloutWithoutManifestCorruption() {
        val note = Note(
            id = "test-missing-img",
            name = "Illustrated Tale",
            content = """
                # Chapter 1: The Expedition
                ![Lost Map of Eldoria](vault-img://lost_map.png)
                *Found in the archives of antiquity.*
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

        val opf = entries["OEBPS/content.opf"] ?: ""
        // Manifest must NOT contain broken missing_map reference
        assertFalse("Manifest must not contain unresolved vault images", opf.contains("lost_map"))

        val chapContent = entries["OEBPS/chapter_1.xhtml"] ?: ""
        // Content must contain editorial illustration callout box
        assertTrue("Must emit editorial illustration callout", chapContent.contains("[Illustration: Lost Map of Eldoria]"))
        assertTrue("Callout box must have editorial-callout class", chapContent.contains("class=\"editorial-callout\""))
        assertFalse("Must not contain dangling vault-img src", chapContent.contains("vault-img://"))
    }

    @Test
    fun testEpubVersionIsStrictly3Point0() {
        val note = Note(
            id = "test-version-note",
            name = "Spec Test",
            content = "# Chapter 1\nContent here."
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

        val opf = entries["OEBPS/content.opf"] ?: ""
        // Strict W3C EPUB 3.3 Recommendation requirement: package version attribute MUST be "3.0"
        assertTrue("Package declaration must specify version 3.0", opf.contains("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\""))
        assertFalse("Package version must not be set to 3.3", opf.contains("version=\"3.3\""))
    }

    @Test
    fun testChapterSplittingOnH2WhenNoH1Present() {
        val note = Note(
            id = "test-h2-split",
            name = "Segmented Essay",
            content = """
                ## Part I: Foundations
                The early principles of the craft.
                
                ## Part II: Innovations
                Modern techniques for high-reliability systems.
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

        assertTrue("Part I must be chapter_1.xhtml", entries.containsKey("OEBPS/chapter_1.xhtml"))
        assertTrue("Part II must be chapter_2.xhtml", entries.containsKey("OEBPS/chapter_2.xhtml"))

        val nav = entries["OEBPS/nav.xhtml"] ?: ""
        assertTrue("Nav must link chapter_1.xhtml", nav.contains("chapter_1.xhtml"))
        assertTrue("Nav must link chapter_2.xhtml", nav.contains("chapter_2.xhtml"))
    }

    @Test
    fun testAuthorExtractionFromManuscriptContent() {
        val note = Note(
            id = "test-author-sniff",
            name = "A Tale of Two Cities",
            content = """
                Author: Charles Dickens
                
                # Book the First: Recalled to Life
                It was the best of times, it was the worst of times.
            """.trimIndent()
        )
        // With blank authorName in config
        val config = ExportConfig(format = ExportFormat.EPUB, authorName = "")
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
        assertTrue("Must extract author from manuscript front matter", opf.contains("<dc:creator id=\"author\">Charles Dickens</dc:creator>"))
    }
}
