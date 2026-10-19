package com.primaloptima.scribe.util.export

/**
 * Clean Plain Text Emitter for the Scribe export engine.
 * Converts [MarkdownNode.Document] into formatted, syntax-free prose,
 * replacing illustration elements with editorial annotations.
 */
    fun emit(
        document: MarkdownNode.Document,
        includeCoverTitle: Boolean = false,
        title: String? = null
    ): String {
        return emit(
            document = document,
            config = ExportConfig(includeCoverTitle = includeCoverTitle),
            title = title
        )
    }

    fun emit(
        document: MarkdownNode.Document,
        config: ExportConfig?,
        title: String? = null
    ): String {
        val sb = StringBuilder()
        val includeCoverTitle = config?.includeCoverTitle ?: false
        if (includeCoverTitle && !title.isNullOrBlank()) {
            val ornamentId = config?.ornamentId ?: "classic_diamond"
            val parts = title.split('
')
            val primary = parts.getOrNull(0)?.trim() ?: ""
            val secondary = if (parts.size > 1) parts.drop(1).joinToString(" ").trim() else ""

            if (primary.isNotBlank() && secondary.isNotBlank()) {
                sb.append(primary.uppercase()).append("
")
                sb.append(secondary).append("
")
            } else if (primary.isNotBlank()) {
                sb.append(primary.uppercase()).append("
")
            } else if (secondary.isNotBlank()) {
                sb.append(secondary).append("
")
            }

            if (ornamentId != "none") {
                val ornamentTxt = when (ornamentId) {
                    "fleuron" -> "─── ❦ ───"
                    "asterism" -> "─── ⁂ ───"
                    "minimal_rule" -> "────── • ──────"
                    else -> "─── ❖ ───"
                }
                sb.append(ornamentTxt).append("

")
            } else {
                sb.append("
")
            }
        }
        for (block in document.children) {
            sb.append(emitBlock(block)).append("

")
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
                    emitBlock(child).lines().joinToString("\n") { line -> "  $line" }
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
                val annotation = (block.caption ?: block.alt).trim()
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
