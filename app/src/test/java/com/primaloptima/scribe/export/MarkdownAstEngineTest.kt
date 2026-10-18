package com.primaloptima.scribe.export

import com.primaloptima.scribe.util.export.HtmlEmitter
import com.primaloptima.scribe.util.export.MarkdownNode
import com.primaloptima.scribe.util.export.PlainEmitter
import com.primaloptima.scribe.util.export.ScribeMarkdownAstParser
import com.primaloptima.scribe.util.export.ScribeMarkdownEngine
import com.primaloptima.scribe.util.export.TableAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAstEngineTest {

    @Test
    fun testHeadingsAndInlines() {
        val markdown = """
            # Epic Chronicle: The First Age
            ## Chapter 1: The Gathering Storm
            ### Section A: Shadows in the East
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val headings = doc.children.filterIsInstance<MarkdownNode.BlockNode.Heading>()

        assertEquals(3, headings.size)
        assertEquals(1, headings[0].level)
        assertEquals("Epic Chronicle: The First Age", headings[0].runs.joinToString("") { it.text })
        assertEquals(2, headings[1].level)
        assertEquals(3, headings[2].level)
    }

    @Test
    fun testEscapedCharactersDoNotFormat() {
        val markdown = """
            This has \*not italic\* and \**not bold\** and \[not a link\] and \~not strike\~.
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val para = doc.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>().first()
        val text = para.runs.joinToString("") { it.text }

        assertTrue(text.contains("*not italic*"))
        assertTrue(text.contains("[not a link]"))
        assertTrue(text.contains("~not strike~"))

        // None of the escaped runs should have bold/italic/strike flags
        for (run in para.runs) {
            if (run.text.contains("not italic")) {
                assertFalse("Escaped asterisk must not trigger italic", run.isItalic)
            }
            if (run.text.contains("not a link")) {
                assertEquals("Escaped brackets must not trigger link", null, run.linkUrl)
            }
        }
    }

    @Test
    fun testNestedListsWithTwoAndFourSpaceIndentation() {
        val markdown = """
            - Level Zero Item A
              - Level One Child A1
              - Level One Child A2
                - Level Two Grandchild
            - Level Zero Item B
              - Level One Child B1
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val listBlocks = doc.children.filterIsInstance<MarkdownNode.BlockNode.ListBlock>()

        assertEquals(1, listBlocks.size)
        val items = listBlocks[0].items
        assertEquals(6, items.size)
        assertEquals(0, items[0].level)
        assertEquals("Level Zero Item A", items[0].runs.joinToString("") { it.text })
        assertEquals(1, items[1].level)
        assertEquals("Level One Child A1", items[1].runs.joinToString("") { it.text })
        assertEquals(1, items[2].level)
        assertEquals(2, items[3].level)
        assertEquals("Level Two Grandchild", items[3].runs.joinToString("") { it.text })
        assertEquals(0, items[4].level)
        assertEquals(1, items[5].level)
    }

    @Test
    fun testOrderedListWithStartNumber() {
        val markdown = """
            3. Third Item
            4. Fourth Item
            5. Fifth Item
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val list = doc.children.filterIsInstance<MarkdownNode.BlockNode.ListBlock>().first()

        assertTrue(list.isOrdered)
        assertEquals(3, list.startNumber)
        assertEquals(3, list.items.size)
    }

    @Test
    fun testTableWithAlignmentsAndPadding() {
        val markdown = """
            | Left Header | Center Header | Right Header |
            | :--- | :---: | ---: |
            | Alpha | Beta | Gamma |
            | One | Two |
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val tables = doc.children.filterIsInstance<MarkdownNode.BlockNode.Table>()

        assertEquals(1, tables.size)
        val table = tables[0]
        assertEquals(3, table.headers.size)
        assertEquals(TableAlignment.LEFT, table.alignments[0])
        assertEquals(TableAlignment.CENTER, table.alignments[1])
        assertEquals(TableAlignment.RIGHT, table.alignments[2])
        assertEquals(2, table.rows.size)
        // Row 2 only had 2 items in markdown, must be padded to 3
        assertEquals(3, table.rows[1].size)
    }

    @Test
    fun testTableWithoutOuterPipes() {
        val markdown = """
            Column A | Column B | Column C
            :--- | :---: | ---:
            Val 1 | Val 2 | Val 3
            Alpha | Beta | Gamma
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val tables = doc.children.filterIsInstance<MarkdownNode.BlockNode.Table>()

        assertEquals("Table without outer pipes must be successfully parsed", 1, tables.size)
        val table = tables[0]
        assertEquals(3, table.headers.size)
        assertEquals("Column A", table.headers[0].runs.joinToString("") { it.text })
        assertEquals("Column B", table.headers[1].runs.joinToString("") { it.text })
        assertEquals("Column C", table.headers[2].runs.joinToString("") { it.text })
        assertEquals(2, table.rows.size)
        assertEquals("Val 1", table.rows[0][0].runs.joinToString("") { it.text })
    }

    @Test
    fun testSoftAndHardLineBreaks() {
        val markdown = "Line one with hard break  \nLine two with backslash\\\nLine three soft\nLine four"
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val para = doc.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>().first()
        val hardBreaks = para.runs.filter { it.isHardBreak }
        assertEquals("Should contain 2 hard breaks", 2, hardBreaks.size)

        val html = HtmlEmitter.emit(doc)
        assertTrue("HTML must contain <br/> for hard break", html.contains("<br/>"))
    }

    @Test
    fun testParagraphSoftBreaksReflowAsSpaces() {
        val markdown = """
            The quick brown fox
            jumps over the lazy dog.
            Another sentence on a new line.
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val paragraphs = doc.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>()
        assertEquals(1, paragraphs.size)
        val text = paragraphs[0].runs.joinToString("") { it.text }
        assertEquals("The quick brown fox jumps over the lazy dog. Another sentence on a new line.", text)
    }

    @Test
    fun testUrduAndArabicUnicodePreservationInAst() {
        val markdown = """
            # ایک قدیم داستان
            یہ ایک معلوماتی پیراگراف ہے جس میں اردو اور عربی الفاظ شامل ہیں۔
            - پہلا نکتہ
            - دوسرا نکتہ
        """.trimIndent()
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val headings = doc.children.filterIsInstance<MarkdownNode.BlockNode.Heading>()
        val paragraphs = doc.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>()
        val lists = doc.children.filterIsInstance<MarkdownNode.BlockNode.ListBlock>()

        assertEquals(1, headings.size)
        assertEquals("ایک قدیم داستان", headings[0].runs.joinToString("") { it.text })
        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs[0].runs.joinToString("") { it.text }.contains("اردو اور عربی"))
        assertEquals(1, lists.size)
        assertEquals(2, lists[0].items.size)
    }

    @Test
    fun testNestedInlinesInsideLinks() {
        val markdown = "Check out [**Bold Official Site**](https://primaloptima.com) for updates."
        val doc = ScribeMarkdownAstParser.parse(markdown)
        val para = doc.children.filterIsInstance<MarkdownNode.BlockNode.Paragraph>().first()
        val linkRun = para.runs.firstOrNull { it.linkUrl == "https://primaloptima.com" }

        assertNotNull("Link run must be parsed", linkRun)
        assertEquals("https://primaloptima.com", linkRun?.linkUrl)
        assertTrue("Nested bold flag must be preserved on link run", linkRun?.isBold == true)

        val html = HtmlEmitter.emit(doc)
        assertTrue(html.contains("<a href=\"https://primaloptima.com\"><strong>Bold Official Site</strong></a>"))
    }

    @Test
    fun testHtmlEmitterProducesStrictXhtml() {
        val markdown = """
            # Test Document

            This is a paragraph with **bold**, *italic*, ~~strikethrough~~, and `inline code`.

            ---

            | Feature | Status |
            | :--- | :---: |
            | AST | Ready |

            ![Vault Cover](vault-img://cover.jpg)
            *Figure 1: Scribe Architecture*
        """.trimIndent()

        val html = ScribeMarkdownEngine.toHtml(markdown) { "images/" + it.removePrefix("vault-img://") }
        assertTrue(html.contains("<h1>Test Document</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<del>strikethrough</del>"))
        assertTrue(html.contains("<code>inline code</code>"))
        assertTrue(html.contains("<hr/>"))
        assertTrue(html.contains("<th style=\"text-align: left;\">Feature</th>"))
        assertTrue(html.contains("<th style=\"text-align: center;\">Status</th>"))
        assertTrue(html.contains("<figure style=\"text-align:center;"))
        assertTrue(html.contains("<figcaption"))
    }

    @Test
    fun testPlainEmitterEditorialAnnotations() {
        val markdown = """
            # Chapter 1

            First paragraph of manuscript.

            ![Map of Valinor](vault-img://valinor.png)
            *Detailed Realm Map*

            - Note item 1
            - Note item 2
        """.trimIndent()

        val plain = ScribeMarkdownEngine.toPlainText(markdown)
        assertTrue(plain.contains("CHAPTER 1"))
        assertTrue(plain.contains("First paragraph of manuscript."))
        assertTrue(plain.contains("[Illustration: Detailed Realm Map]"))
        assertTrue(plain.contains("- Note item 1"))
        assertFalse(plain.contains("![Map"))
    }
}
