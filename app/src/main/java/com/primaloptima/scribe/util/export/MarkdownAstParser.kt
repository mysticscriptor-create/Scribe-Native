package com.primaloptima.scribe.util.export

/**
 * Facade and backward-compatible AST adapter bridging [ScribeMarkdownAstParser]
 * with legacy AstNode consumer classes.
 */
object MarkdownAstParser {

    data class AstDocument(
        val nodes: List<AstNode>
    )

    sealed class AstNode {
        data class HeadingNode(
            val level: Int,
            val rawText: String,
            val runs: List<InlineRun>
        ) : AstNode()

        data class ParagraphNode(
            val runs: List<InlineRun>
        ) : AstNode()

        data class ListNode(
            val items: List<ListItemNode>,
            val isOrdered: Boolean
        ) : AstNode()

        data class ListItemNode(
            val runs: List<InlineRun>
        ) : AstNode()

        data class BlockquoteNode(
            val children: List<AstNode>
        ) : AstNode()

        data class TableNode(
            val headers: List<List<InlineRun>>,
            val rows: List<List<List<InlineRun>>>
        ) : AstNode()

        object DividerNode : AstNode()

        data class ImageNode(
            val alt: String,
            val uri: String,
            val caption: String
        ) : AstNode()

        data class CodeBlockNode(
            val code: String,
            val language: String
        ) : AstNode()
    }

    data class InlineRun(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isStrikethrough: Boolean = false,
        val isCode: Boolean = false,
        val linkUrl: String? = null
    )

    fun parse(markdown: String): AstDocument {
        val doc = ScribeMarkdownAstParser.parse(markdown)
        return AstDocument(doc.children.map { convertBlock(it) })
    }

    fun parseInlineRuns(text: String): List<InlineRun> {
        val runs = ScribeMarkdownAstParser.parseInlineRuns(text)
        return runs.map {
            InlineRun(
                text = it.text,
                isBold = it.isBold,
                isItalic = it.isItalic,
                isStrikethrough = it.isStrikethrough,
                isCode = it.isCode,
                linkUrl = it.linkUrl
            )
        }
    }

    private fun convertBlock(block: MarkdownNode.BlockNode): AstNode {
        return when (block) {
            is MarkdownNode.BlockNode.Heading -> {
                val runs = block.runs.map { convertInline(it) }
                AstNode.HeadingNode(
                    level = block.level,
                    rawText = runs.joinToString("") { it.text },
                    runs = runs
                )
            }
            is MarkdownNode.BlockNode.Paragraph -> {
                AstNode.ParagraphNode(block.runs.map { convertInline(it) })
            }
            is MarkdownNode.BlockNode.Blockquote -> {
                AstNode.BlockquoteNode(block.children.map { convertBlock(it) })
            }
            is MarkdownNode.BlockNode.ListBlock -> {
                AstNode.ListNode(
                    items = block.items.map { AstNode.ListItemNode(it.runs.map { r -> convertInline(r) }) },
                    isOrdered = block.isOrdered
                )
            }
            is MarkdownNode.BlockNode.ListItem -> {
                AstNode.ListItemNode(block.runs.map { convertInline(it) })
            }
            is MarkdownNode.BlockNode.Table -> {
                AstNode.TableNode(
                    headers = block.headers.map { it.runs.map { r -> convertInline(r) } },
                    rows = block.rows.map { row -> row.map { cell -> cell.runs.map { r -> convertInline(r) } } }
                )
            }
            is MarkdownNode.BlockNode.CodeBlock -> {
                AstNode.CodeBlockNode(code = block.code, language = block.language ?: "")
            }
            is MarkdownNode.BlockNode.ImageBlock -> {
                AstNode.ImageNode(alt = block.alt, uri = block.uri, caption = block.caption ?: block.alt)
            }
            is MarkdownNode.BlockNode.ThematicBreak -> {
                AstNode.DividerNode
            }
            is MarkdownNode.BlockNode.TableCell -> {
                AstNode.ParagraphNode(block.runs.map { convertInline(it) })
            }
        }
    }

    private fun convertInline(run: MarkdownNode.InlineRun): InlineRun {
        return InlineRun(
            text = run.text,
            isBold = run.isBold,
            isItalic = run.isItalic,
            isStrikethrough = run.isStrikethrough,
            isCode = run.isCode,
            linkUrl = run.linkUrl
        )
    }
}
