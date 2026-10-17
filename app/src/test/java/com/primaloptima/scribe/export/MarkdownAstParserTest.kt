package com.primaloptima.scribe.export

import com.primaloptima.scribe.util.export.MarkdownAstParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAstParserTest {

    @Test
    fun testHeadingsParsing() {
        val markdown = """
            # Chapter 1: The Beginning
            Some text.
            ## Section 1.1: Early Days
            More text.
            ### Subsection 1.1.1
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val headings = doc.nodes.filterIsInstance<MarkdownAstParser.AstNode.HeadingNode>()

        assertEquals(3, headings.size)
        assertEquals(1, headings[0].level)
        assertEquals("Chapter 1: The Beginning", headings[0].rawText)
        assertEquals(2, headings[1].level)
        assertEquals("Section 1.1: Early Days", headings[1].rawText)
        assertEquals(3, headings[2].level)
        assertEquals("Subsection 1.1.1", headings[2].rawText)
    }

    @Test
    fun testInlineFormatting() {
        val line = "This has **bold text**, *italic text*, `inline code`, and ~~strike~~ with [a link](https://example.com)."
        val runs = MarkdownAstParser.parseInlineRuns(line)

        assertTrue(runs.any { it.text == "bold text" && it.isBold })
        assertTrue(runs.any { it.text == "italic text" && it.isItalic })
        assertTrue(runs.any { it.text == "inline code" && it.isCode })
        assertTrue(runs.any { it.text == "strike" && it.isStrikethrough })
        assertTrue(runs.any { it.text == "a link" && it.linkUrl == "https://example.com" })
    }

    @Test
    fun testListParsing() {
        val markdown = """
            - Item Alpha
            - Item Beta
            - Item Gamma

            1. First Step
            2. Second Step
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val lists = doc.nodes.filterIsInstance<MarkdownAstParser.AstNode.ListNode>()

        assertEquals(2, lists.size)
        val unordered = lists[0]
        val ordered = lists[1]

        assertEquals(false, unordered.isOrdered)
        assertEquals(3, unordered.items.size)

        assertEquals(true, ordered.isOrdered)
        assertEquals(2, ordered.items.size)
    }

    @Test
    fun testBlockquoteParsing() {
        val markdown = """
            > To be or not to be, that is the question.
            > Whether 'tis nobler in the mind to suffer.
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val quotes = doc.nodes.filterIsInstance<MarkdownAstParser.AstNode.BlockquoteNode>()

        assertEquals(1, quotes.size)
        assertTrue(quotes[0].children.isNotEmpty())
    }

    @Test
    fun testTableParsing() {
        val markdown = """
            | Character | Role | Faction |
            | --- | --- | --- |
            | Elen | Protagonist | Rebellious Guild |
            | Vane | Antagonist | Imperial Guard |
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val tables = doc.nodes.filterIsInstance<MarkdownAstParser.AstNode.TableNode>()

        assertEquals(1, tables.size)
        val table = tables[0]
        assertEquals(3, table.headers.size)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun testDividerParsing() {
        val markdown = """
            Section Before
            ---
            Section After
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val dividerCount = doc.nodes.count { it is MarkdownAstParser.AstNode.DividerNode }
        assertEquals(1, dividerCount)
    }

    @Test
    fun testStandaloneImageWithCaption() {
        val markdown = """
            ![Ancient Map](vault-img://map.jpg)
            *Figure 1: The Lost Realm*
        """.trimIndent()

        val doc = MarkdownAstParser.parse(markdown)
        val images = doc.nodes.filterIsInstance<MarkdownAstParser.AstNode.ImageNode>()

        assertEquals(1, images.size)
        val img = images[0]
        assertEquals("Ancient Map", img.alt)
        assertEquals("vault-img://map.jpg", img.uri)
        assertEquals("Figure 1: The Lost Realm", img.caption)
    }
}
