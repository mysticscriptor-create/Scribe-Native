package com.primaloptima.scribe.util.export

/**
 * Unified Markdown AST hierarchy for the Scribe export engine.
 * Forms the Single Source of Truth (SSoT) pipeline consumed by
 * HTML5, Headless PDF, DOCX OpenXML, EPUB 3.3, and PlainText emitters.
 */
sealed class MarkdownNode {
    data class Document(val children: List<BlockNode>) : MarkdownNode()

    sealed class BlockNode : MarkdownNode() {
        data class Heading(
            val level: Int,
            val runs: List<InlineRun>
        ) : BlockNode()

        data class Paragraph(
            val runs: List<InlineRun>
        ) : BlockNode()

        data class Blockquote(
            val children: List<BlockNode>
        ) : BlockNode()

        data class ListBlock(
            val items: List<ListItem>,
            val isOrdered: Boolean,
            val startNumber: Int = 1
        ) : BlockNode()

        data class ListItem(
            val level: Int,
            val runs: List<InlineRun>,
            val children: List<BlockNode> = emptyList()
        ) : BlockNode()

        data class Table(
            val headers: List<TableCell>,
            val rows: List<List<TableCell>>,
            val alignments: List<TableAlignment>
        ) : BlockNode()

        data class TableCell(
            val runs: List<InlineRun>
        ) : BlockNode()

        data class CodeBlock(
            val code: String,
            val language: String? = null
        ) : BlockNode()

        data class ImageBlock(
            val alt: String,
            val uri: String,
            val caption: String? = null
        ) : BlockNode()

        object ThematicBreak : BlockNode()
    }

    data class InlineRun(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isStrikethrough: Boolean = false,
        val isCode: Boolean = false,
        val linkUrl: String? = null,
        val isHardBreak: Boolean = false
    )
}

enum class TableAlignment {
    LEFT,
    CENTER,
    RIGHT,
    DEFAULT
}
