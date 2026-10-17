package com.primaloptima.scribe.util.export

/**
 * Clean Plain Text Emitter for the Scribe export engine.
 * Converts [MarkdownNode.Document] into formatted, syntax-free prose,
 * replacing illustration elements with editorial annotations.
 */
object PlainEmitter {

    fun emit(document: MarkdownNode.Document): String {
        val sb = StringBuilder()
        for (block in document.children) {
            sb.append(emitBlock(block)).append("\n\n")
        }
        return sb.toString().trim()
    }

    fun emitBlock(block: MarkdownNode.BlockNode): String {
        return when (block) {
            is MarkdownNode.BlockNode.Heading -> {
                val text = emitRuns(block.runs)
                if (block.level == 1) text.uppercase() else text
            }
            is MarkdownNode.BlockNode.Paragraph -> {
                emitRuns(block.runs)
            }
            is MarkdownNode.BlockNode.Blockquote -> {
                block.children.joinToString("\n") { child ->
                    emitBlock(child).lines().joinToString("\n") { line -> "> $line" }
                }
            }
            is MarkdownNode.BlockNode.ListBlock -> {
                val sb = StringBuilder()
                var currentNumber = block.startNumber
                for (item in block.items) {
                    val indent = "  ".repeat(item.level)
                    val prefix = if (block.isOrdered) "${currentNumber++}. " else "- "
                    sb.append(indent).append(prefix).append(emitRuns(item.runs)).append("\n")
                }
                sb.toString().trimEnd()
            }
            is MarkdownNode.BlockNode.ListItem -> {
                val indent = "  ".repeat(block.level)
                "$indent- ${emitRuns(block.runs)}"
            }
            is MarkdownNode.BlockNode.Table -> {
                val sb = StringBuilder()
                if (tableHasContent(block)) {
                    val headers = block.headers.map { emitRuns(it.runs) }
                    sb.append(headers.joinToString(" | ")).append("\n")
                    sb.append(headers.joinToString(" | ") { "-".repeat(it.length.coerceAtLeast(3)) }).append("\n")
                    for (row in block.rows) {
                        val cells = row.map { emitRuns(it.runs) }
                        sb.append(cells.joinToString(" | ")).append("\n")
                    }
                }
                sb.toString().trimEnd()
            }
            is MarkdownNode.BlockNode.CodeBlock -> {
                block.code
            }
            is MarkdownNode.BlockNode.ImageBlock -> {
                val annotation = block.caption ?: block.alt
                if (annotation.isNotBlank()) "[Illustration: $annotation]" else "[Illustration]"
            }
            is MarkdownNode.BlockNode.ThematicBreak -> {
                "---"
            }
            is MarkdownNode.BlockNode.TableCell -> {
                emitRuns(block.runs)
            }
        }
    }

    private fun tableHasContent(table: MarkdownNode.BlockNode.Table): Boolean {
        return table.headers.isNotEmpty() || table.rows.isNotEmpty()
    }

    fun emitRuns(runs: List<MarkdownNode.InlineRun>): String {
        val sb = StringBuilder()
        for (run in runs) {
            if (run.isHardBreak) {
                sb.append("\n")
            } else {
                sb.append(run.text)
            }
        }
        return sb.toString()
    }
}
