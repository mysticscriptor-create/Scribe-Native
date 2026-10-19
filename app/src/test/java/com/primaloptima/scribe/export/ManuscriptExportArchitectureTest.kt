package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.primaryTitle
import com.primaloptima.scribe.data.secondaryTitle
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.DropCapStyle
import com.primaloptima.scribe.util.export.EpubExporter
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.ExportThemeMapper
import com.primaloptima.scribe.util.export.HtmlPdfExporter
import com.primaloptima.scribe.util.export.ManuscriptTitleHeader
import com.primaloptima.scribe.util.export.PlainEmitter
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Rigorous automated validation suite for the Scribe Publication Architecture:
 * - Two-title hierarchy & combined TOC entries
 * - Manuscript ornaments across formats
 * - Drop caps with RTL and punctuation safeguards
 * - EPUB 3.3 chapter splitting & hierarchical TOC trees
 */
class ManuscriptExportArchitectureTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Two-Title Hierarchy & Combined TOC Label Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testTwoTitleHierarchyWithBothTitlesPresent() {
        val note = Note(
            id = "note-two-titles",
            name = "Chapter 1\nThe Beginning Of Everything",
            content = "In the beginning, all was calm."
        )
        val header = ManuscriptTitleHeader.from(note, "classic_diamond")
        assertEquals("Chapter 1", header.primaryTitle)
        assertEquals("The Beginning Of Everything", header.secondaryTitle)
        assertTrue(header.hasPrimary)
        assertTrue(header.hasSecondary)
        assertEquals("Chapter 1: The Beginning Of Everything", header.toTocLabel())

        // In HTML: Primary must be kicker, Secondary must be h1
        val config = ExportConfig(format = ExportFormat.HTML, includeCoverTitle = true)
        val html = HtmlPdfExporter.buildHtmlDocument(null, note, config)
        assertTrue("Must contain kicker element", html.contains("<p class=\"title-kicker\">Chapter 1</p>"))
        assertTrue("Must contain main h1 title", html.contains("<h1 class=\"document-title\">The Beginning Of Everything</h1>"))
    }

    @Test
    fun testPunctuationAndTrailingColonTrimmingInTocLabel() {
        val note = Note(
            id = "note-colons",
            name = "Chapter 1:\nThe Beginning Of Everything",
            content = "Prose text here."
        )
        val header = ManuscriptTitleHeader.from(note)
        // Must avoid double colons "Chapter 1:: The Beginning Of Everything"
        assertEquals("Chapter 1: The Beginning Of Everything", header.toTocLabel())
    }

    @Test
    fun testSingleTitleRendersAsMainDocumentTitleNotKicker() {
        val note = Note(
            id = "note-single-title",
            name = "The Great Adventure",
            content = "Once upon a time in the northern woods."
        )
        val header = ManuscriptTitleHeader.from(note)
        assertEquals("The Great Adventure", header.primaryTitle)
        assertEquals("", header.secondaryTitle)
        assertTrue(header.hasPrimary)
        assertFalse(header.hasSecondary)
        assertEquals("The Great Adventure", header.toTocLabel())

        // HTML export MUST render single title as h1.document-title, NOT as a small kicker without title
        val config = ExportConfig(format = ExportFormat.HTML, includeCoverTitle = true)
        val html = HtmlPdfExporter.buildHtmlDocument(null, note, config)
        assertTrue("Single title must render as h1", html.contains("<h1 class=\"document-title\">The Great Adventure</h1>"))
        assertFalse("Single title must NOT render as kicker", html.contains("<p class=\"title-kicker\">The Great Adventure</p>"))
    }

    @Test
    fun testDuplicateTocSuppressionInEpub() {
        val note = Note(
            id = "note-dup-toc",
            name = "Chapter 1\nThe Odyssey",
            content = """
                # The Odyssey
                Prose narrative text begins here.
                
                ## Section 1: The Shore
                Details of the shore.
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = extractZipEntries(epubBytes)
        val nav = entries["OEBPS/nav.xhtml"] ?: ""

        // Combined title should appear as root
        assertTrue(nav.contains("Chapter 1: The Odyssey"))
        // Subheading Section 1 should appear
        assertTrue(nav.contains("Section 1: The Shore"))
        // But redundant '# The Odyssey' in body should NOT produce a duplicate second root entry
        val occurrences = Regex("""The Odyssey""").findAll(nav).count()
        // Appears once in the combined label "Chapter 1: The Odyssey"
        assertEquals(1, occurrences)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Manuscript Ornaments Across Formats
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testAllRegisteredOrnamentsProduceValidSvgOrnaments() {
        val ornamentIds = listOf(
            "classic_diamond",
            "fleuron",
            "asterism",
            "celtic_knot",
            "minimal_rule",
            "quatrefoil",
            "laurel_branch"
        )
        for (id in ornamentIds) {
            val svg = ExportThemeMapper.renderOrnamentSvg(id)
            assertTrue("SVG for $id must not be blank", svg.isNotBlank())
            assertTrue("SVG for $id must contain <svg tag", svg.contains("<svg"))
            assertTrue("SVG for $id must contain </svg>", svg.contains("</svg>"))
        }

        // None must produce empty string
        val noneSvg = ExportThemeMapper.renderOrnamentSvg("none")
        assertTrue("None ornament must produce blank string", noneSvg.isBlank())
    }

    @Test
    fun testOrnamentIncludedInHtmlAndEpub() {
        val note = Note(
            id = "note-ornament",
            name = "Chapter 1\nTitle",
            content = "Text"
        )
        val config = ExportConfig(format = ExportFormat.HTML, ornamentId = "fleuron")
        val html = HtmlPdfExporter.buildHtmlDocument(null, note, config)
        assertTrue("HTML must contain manuscript-ornament div", html.contains("class=\"manuscript-ornament\""))
        assertTrue("HTML must contain fleuron SVG path", html.contains("<svg width=\"140\""))

        val epubConfig = ExportConfig(format = ExportFormat.EPUB, ornamentId = "asterism")
        val epubBytes = EpubExporter.export(null, note, epubConfig)
        val entries = extractZipEntries(epubBytes)
        val chap1 = entries["OEBPS/chapter_1.xhtml"] ?: ""
        assertTrue("EPUB must contain manuscript-ornament div", chap1.contains("class=\"manuscript-ornament\""))
        assertTrue("EPUB must contain asterism glyph", chap1.contains("⁂"))
    }

    @Test
    fun testPlainTextOrnamentDividers() {
        val ast = ScribeMarkdownAstParser.parse("Prose paragraph.")
        val config = ExportConfig(format = ExportFormat.TXT, ornamentId = "fleuron", includeCoverTitle = true)
        val plain = PlainEmitter.emit(ast, config, "Chapter 1\nThe Journey")
        assertTrue("Plain text must contain fleuron divider", plain.contains("─── ❦ ───"))
        assertTrue("Plain text must contain upper kicker", plain.contains("CHAPTER 1"))
        assertTrue("Plain text must contain secondary title", plain.contains("The Journey"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Drop Caps Typography & Safeguards
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testDropCapInjectedOnFirstParagraphInHtml() {
        val note = Note(
            id = "note-dropcap",
            name = "Drop Cap Test",
            content = """
                # Title
                Once upon a time in a faraway forest.
                
                The next day was bright and sunny.
            """.trimIndent()
        )
        val config = ExportConfig(
            format = ExportFormat.HTML,
            dropCapStyle = DropCapStyle.OPENING_PARAGRAPH_ONLY
        )
        val html = HtmlPdfExporter.buildHtmlDocument(null, note, config)
        assertTrue("First paragraph must have has-dropcap class", html.contains("<p class=\"has-dropcap\">Once upon a time"))
        assertFalse("Second paragraph must NOT have has-dropcap", html.contains("<p class=\"has-dropcap\">The next day"))
    }

    @Test
    fun testDropCapStrictlyBypassedOnRtlScripts() {
        val note = Note(
            id = "note-rtl-dropcap",
            name = "دیوان غالب",
            content = "دل ناداں تجھے ہوا کیا ہے آخر اس درد کی دوا کیا ہے"
        )
        val config = ExportConfig(
            format = ExportFormat.HTML,
            dropCapStyle = DropCapStyle.OPENING_PARAGRAPH_ONLY
        )
        val html = HtmlPdfExporter.buildHtmlDocument(null, note, config)
        assertFalse("RTL paragraph must never receive has-dropcap class", html.contains("has-dropcap"))
    }

    @Test
    fun testDocxDropCapWordprocessingMlGeneration() {
        val note = Note(
            id = "note-docx-dropcap",
            name = "Docx Drop Cap",
            content = "Once upon a time in an ancient library."
        )
        val config = ExportConfig(
            format = ExportFormat.DOCX,
            dropCapStyle = DropCapStyle.OPENING_PARAGRAPH_ONLY,
            dropCapLines = 3
        )
        val docxBytes = DocxExporter.export(null, note, config)
        val entries = extractZipEntries(docxBytes)
        val documentXml = entries["word/document.xml"] ?: ""
        assertTrue("Must contain w:framePr dropCap attribute", documentXml.contains("<w:framePr w:dropCap=\"drop\" w:lines=\"3\""))
        assertTrue("Must extract initial glyph O", documentXml.contains("<w:t xml:space=\"preserve\">O</w:t>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. EPUB 3.3 Chapter Splitting & Hierarchical TOC Trees
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testChapterSplittingOnH2WhenSingleH1Present() {
        val note = Note(
            id = "note-h2-split",
            name = "Anthology",
            content = """
                # Anthology Volume I
                Introductory front matter prose.
                
                ## Chapter 1: Dawn
                Prose for chapter 1.
                
                ## Chapter 2: Noon
                Prose for chapter 2.
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = extractZipEntries(epubBytes)

        assertTrue("Must split into chapter_1.xhtml", entries.containsKey("OEBPS/chapter_1.xhtml"))
        assertTrue("Must split into chapter_2.xhtml", entries.containsKey("OEBPS/chapter_2.xhtml"))
    }

    @Test
    fun testHierarchicalNavXhtmlProducesNestedLists() {
        val note = Note(
            id = "note-nested-nav",
            name = "Technical Manual",
            content = """
                # Chapter 1: Architecture
                Intro prose.
                
                ## Section 1.1: Core Engine
                Details on core engine.
                
                ### Subsection 1.1.1: Memory Model
                Memory allocation details.
                
                ## Section 1.2: Concurrency
                Thread safety.
            """.trimIndent()
        )
        val config = ExportConfig(format = ExportFormat.EPUB)
        val epubBytes = EpubExporter.export(null, note, config)
        val entries = extractZipEntries(epubBytes)
        val nav = entries["OEBPS/nav.xhtml"] ?: ""

        assertTrue("Nav must contain nested <ol>", nav.contains("<ol>"))
        assertTrue("Nav must link to section anchor", nav.contains("href=\"chapter_1.xhtml#sec-1-1-core-engine\"") || nav.contains("chapter_1.xhtml#sec-"))
        val tocNcx = entries["OEBPS/toc.ncx"] ?: ""
        assertTrue("NCX must contain nested navPoint", tocNcx.contains("<navPoint id=\"navPoint-"))
    }

    private fun extractZipEntries(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                result[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        return result
    }
}
