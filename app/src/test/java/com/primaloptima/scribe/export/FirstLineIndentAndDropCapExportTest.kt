package com.primaloptima.scribe.export

import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.util.export.DocxExporter
import com.primaloptima.scribe.util.export.DropCapStyle
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.ExportThemeMapper
import com.primaloptima.scribe.util.export.MarkdownNode
import com.primaloptima.scribe.util.export.OrnamentCatalog
import com.primaloptima.scribe.util.export.PlainEmitter
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class FirstLineIndentAndDropCapExportTest {

    @Test
    fun testAstParserDoesNotMergeIndentedParagraphs() {
        val markdown = """
            Once upon a time in a distant land, there was a traveler.
                The next day, the traveler arrived at the castle gate.
                A guard stood silently watching the horizon.
        """.trimIndent()

        val ast = ScribeMarkdownAstParser.parse(markdown)
        val paragraphs = ast.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>()

        // Must be 3 distinct paragraphs, NOT 1 merged paragraph!
        assertEquals("Should parse into 3 separate paragraphs", 3, paragraphs.size)
        val p1Text = paragraphs[0].runs.joinToString("") { it.text }.trim()
        val p2Text = paragraphs[1].runs.joinToString("") { it.text }.trim()
        val p3Text = paragraphs[2].runs.joinToString("") { it.text }.trim()

        assertEquals("Once upon a time in a distant land, there was a traveler.", p1Text)
        assertEquals("The next day, the traveler arrived at the castle gate.", p2Text)
        assertEquals("A guard stood silently watching the horizon.", p3Text)
    }

    @Test
    fun testOrnamentCatalogCompletenessAndSingleSourceOfTruth() {
        val expectedStyles = listOf(
            "classic_diamond",
            "fleuron",
            "asterism",
            "celtic_knot",
            "minimal_rule",
            "quatrefoil",
            "laurel_branch",
            "none"
        )

        for (id in expectedStyles) {
            val entry = OrnamentCatalog.getById(id)
            assertNotNull("Entry for $id must exist", entry)
            if (id != "none") {
                assertTrue("Text divider for $id must not be blank", entry.textDivider.isNotBlank())
                assertTrue("SVG markup for $id must not be blank", entry.svgMarkup.isNotBlank())
                assertTrue("SVG markup for $id must contain <svg", entry.svgMarkup.contains("<svg"))
            } else {
                assertEquals("", entry.textDivider)
                assertEquals("", entry.svgMarkup)
            }
        }

        // Verify fallback
        val unknown = OrnamentCatalog.getById("nonexistent_style")
        assertEquals("classic_diamond", unknown.id)
    }

    @Test
    fun testExportThemeMapperGeneratesFirstLineIndentCss() {
        val colors = ThemeColors(
            background = "#FFFFFF",
            surface = "#F8F8F8",
            text = "#1A1A1A",
            accent = "#1A5276"
        )
        val indentedConfig = ExportConfig(firstLineIndent = true)
        val normalConfig = ExportConfig(firstLineIndent = false)

        val indentedCss = ExportThemeMapper.generateExportCss(indentedConfig, colors)
        val normalCss = ExportThemeMapper.generateExportCss(normalConfig, colors)

        assertTrue("Indented CSS must include text-indent: 1.5em", indentedCss.contains("text-indent: 1.5em;"))
        assertTrue("Indented CSS must reset text-indent on h1 + p", indentedCss.contains("h1 + p"))
        assertTrue("Indented CSS must reset text-indent on p.has-dropcap", indentedCss.contains("p.has-dropcap"))
        assertFalse("Normal CSS must not have text-indent: 1.5em", normalCss.contains("text-indent: 1.5em;"))
    }

    @Test
    fun testDocxDropCapStructureAndFirstLineIndent() {
        val note = Note(
            id = "docx-test",
            name = "Test Note",
            content = """
                # Chapter 1

                Opening paragraph with a drop cap.

                Second paragraph that should have a first-line indent.

                Third paragraph that should also be indented.
            """.trimIndent()
        )

        val config = ExportConfig(
            format = ExportFormat.DOCX,
            dropCapStyle = DropCapStyle.TRADITIONAL,
            dropCapLines = 3,
            firstLineIndent = true
        )

        val bytes = DocxExporter.export(null, note, config)
        var documentXml: String? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zis.bufferedReader().readText()
                    break
                }
                entry = zis.nextEntry
            }
        }

        assertNotNull("word/document.xml must be present", documentXml)
        val xml = documentXml!!

        // Drop cap must have framePr and sz inside a dedicated drop cap paragraph
        assertTrue("Must contain dropCap framePr", xml.contains("w:framePr w:dropCap=\"drop\""))
        assertTrue("Must specify drop cap size w:sz", xml.contains("<w:sz w:val=\"96\"/>"))
        // First-line indent should be applied on subsequent paragraphs
        assertTrue("Must contain first-line indent w:firstLine=\"360\"", xml.contains("w:firstLine=\"360\""))
    }

    @Test
    fun testPlainEmitterRespectsFirstLineIndentAndOrnamentCatalog() {
        val note = Note(
            id = "txt-test",
            name = "Chapter 1\nThe Secret Path",
            content = """
                # Title

                First paragraph after heading.

                Second paragraph should be indented.
            """.trimIndent()
        )

        val config = ExportConfig(
            format = ExportFormat.TXT,
            ornamentId = "celtic_knot",
            firstLineIndent = true
        )

        val plainText = PlainEmitter.emit(
            document = ScribeMarkdownAstParser.parse(note.content),
            config = config,
            title = note.name
        )

        assertTrue("Must contain celtic knot ornament divider", plainText.contains(OrnamentCatalog.getTextDivider("celtic_knot")))
        assertTrue("Second paragraph must have 4 spaces indent", plainText.contains("    Second paragraph should be indented."))
        assertFalse("First paragraph after heading must not be indented", plainText.contains("    First paragraph after heading."))
    }

    @Test
    fun testEveryChapterDropCapInDocx() {
        val note = Note(
            id = "docx-multi-chap",
            name = "Novel",
            content = """
                # Chapter 1

                First chapter opening paragraph.

                Second paragraph of chapter 1.

                # Chapter 2

                Second chapter opening paragraph.

                Second paragraph of chapter 2.
            """.trimIndent()
        )
        val config = ExportConfig(
            format = ExportFormat.DOCX,
            dropCapStyle = DropCapStyle.EVERY_CHAPTER,
            dropCapLines = 3,
            firstLineIndent = true,
            docxCompatibilityMode = false
        )
        val bytes = DocxExporter.export(null, note, config)
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zis.bufferedReader().readText()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val dropCapCount = xml.split("w:framePr w:dropCap=\"drop\"").size - 1
        assertEquals("Both Chapter 1 and Chapter 2 should have drop caps", 2, dropCapCount)
    }

    @Test
    fun testThematicBreakSuppressesIndentInDocx() {
        val note = Note(
            id = "docx-thematic",
            name = "Scene Break Test",
            content = """
                # Chapter 1
                Paragraph one before break.

                ---

                Paragraph after scene break should not have first-line indent.

                Subsequent paragraph should have first-line indent.
            """.trimIndent()
        )
        val config = ExportConfig(
            format = ExportFormat.DOCX,
            firstLineIndent = true
        )
        val bytes = DocxExporter.export(null, note, config)
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zis.bufferedReader().readText()
                    break
                }
                entry = zis.nextEntry
            }
        }
        assertTrue("Must contain centered ornament divider or border", xml.contains("w:jc w:val=\"center\"") || xml.contains("w:pBdr"))
    }

    @Test
    fun testDocxUniversalCompatibilityMode() {
        val note = Note(
            id = "docx-compat",
            name = "Google Docs Test",
            content = """
                # Chapter 1
                Opening paragraph with universal drop cap.
            """.trimIndent()
        )
        val config = ExportConfig(
            format = ExportFormat.DOCX,
            dropCapStyle = DropCapStyle.OPENING_PARAGRAPH_ONLY,
            docxCompatibilityMode = true
        )
        val bytes = DocxExporter.export(null, note, config)
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zis.bufferedReader().readText()
                    break
                }
                entry = zis.nextEntry
            }
        }
        assertFalse("Universal compatibility mode must avoid w:framePr", xml.contains("w:framePr w:dropCap=\"drop\""))
        assertTrue("Universal mode must style drop letter with w:sz", xml.contains("<w:sz w:val=\"96\"/>"))
    }

    @Test
    fun testThematicBreakCMOSRuleInHtml() {
        val colors = ThemeColors(
            background = "#FFFFFF",
            surface = "#F8F8F8",
            text = "#1A1A1A",
            accent = "#1A5276"
        )
        val config = ExportConfig(firstLineIndent = true)
        val css = ExportThemeMapper.generateExportCss(config, colors)
        assertTrue("CSS reset must include hr + p", css.contains("hr + p"))
    }
}
