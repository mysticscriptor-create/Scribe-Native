package com.primaloptima.scribe.util.export

/**
 * Standards-compliant XHTML5 HTML Emitter for the Scribe export engine.
 * Converts [MarkdownNode.Document] into well-formed XML/HTML with semantic typography,
 * nested list structures, table alignment styles, RTL directionality markers, and self-closing void elements.
 */
object HtmlEmitter {

    fun generateHeadingSlug(rawText: String, index: Int): String {
        val slug = rawText.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (slug.isNotBlank()) slug else "sec-$index"
    }

    fun emit(
        document: MarkdownNode.Document,
        imageResolver: ((String) -> String)? = null,
        docDirection: String = "ltr",
        enableDropCaps: Boolean = false,
        addHeadingAnchors: Boolean = false
    ): String {
        val sb = StringBuilder()
        var dropCapApplied = false
        for ((idx, block) in document.children.withIndex()) {
            val shouldApplyDropCap = enableDropCaps && !dropCapApplied &&
                    block is MarkdownNode.BlockNode.Paragraph &&
                    !isRtlRuns(block.runs) &&
                    block.runs.any { it.text.isNotBlank() }
            if (shouldApplyDropCap) {
                dropCapApplied = true
            }
            sb.append(emitBlock(block, imageResolver, docDirection, shouldApplyDropCap, addHeadingAnchors, idx)).append("\n")
        }
        return sb.toString().trimEnd()
    }

    fun emitBlock(
        block: MarkdownNode.BlockNode,
        imageResolver: ((String) -> String)? = null,
        docDirection: String = "ltr",
        isDropCap: Boolean = false,
        addHeadingAnchors: Boolean = false,
        blockIndex: Int = 0
    ): String {
        return when (block) {
            is MarkdownNode.BlockNode.Heading -> {
                val tag = "h${block.level.coerceIn(1, 6)}"
                val dirAttr = resolveBlockDir(block.runs, docDirection)
                val idAttr = if (addHeadingAnchors) {
                    val rawText = block.runs.joinToString("") { it.text }
                    val slug = generateHeadingSlug(rawText, blockIndex)
                    " id=\"$slug\""
                } else ""
                "<$tag$idAttr$dirAttr>${emitRuns(block.runs)}</$tag>"
            }
            is MarkdownNode.BlockNode.Paragraph -> {
                val dirAttr = resolveBlockDir(block.runs, docDirection)
                val classAttr = if (isDropCap) " class=\"has-dropcap\"" else ""
                "<p$classAttr$dirAttr>${emitRuns(block.runs)}</p>"
            }
            is MarkdownNode.BlockNode.Blockquote -> {
                val inner = block.children.joinToString("\n") { emitBlock(it, imageResolver, docDirection) }
                val isRtl = block.children.any { child ->
                    when (child) {
                        is MarkdownNode.BlockNode.Paragraph -> isRtlRuns(child.runs)
                        is MarkdownNode.BlockNode.Heading -> isRtlRuns(child.runs)
                        else -> false
                    }
                }
                val isLtr = block.children.any { child ->
                    when (child) {
                        is MarkdownNode.BlockNode.Paragraph -> isLtrRuns(child.runs)
                        is MarkdownNode.BlockNode.Heading -> isLtrRuns(child.runs)
                        else -> false
                    }
                }
                val dirAttr = if (isRtl) {
                    " dir=\"rtl\""
                } else if (docDirection == "rtl" && isLtr) {
                    " dir=\"ltr\""
                } else {
                    ""
                }
                "<blockquote$dirAttr>\n$inner\n</blockquote>"
            }
            is MarkdownNode.BlockNode.ListBlock -> {
                emitListBlock(block, docDirection)
            }
            is MarkdownNode.BlockNode.ListItem -> {
                val dirAttr = resolveBlockDir(block.runs, docDirection)
                "<li$dirAttr>${emitRuns(block.runs)}</li>"
            }
            is MarkdownNode.BlockNode.Table -> {
                emitTable(block, docDirection)
            }
            is MarkdownNode.BlockNode.CodeBlock -> {
                val langClass = if (!block.language.isNullOrBlank()) {
                    " class=\"language-${escapeHtml(block.language)}\""
                } else ""
                val dirAttr = if (docDirection == "rtl") " dir=\"ltr\"" else ""
                "<pre><code$langClass$dirAttr>${escapeHtml(block.code)}</code></pre>"
            }
            is MarkdownNode.BlockNode.ImageBlock -> {
                val resolvedSrc = imageResolver?.invoke(block.uri) ?: block.uri
                val altEscaped = escapeHtml(block.alt)
                val captionEscaped = if (!block.caption.isNullOrBlank()) escapeHtml(block.caption) else ""
                if (resolvedSrc.isBlank() || resolvedSrc == "MISSING_ASSET" || resolvedSrc.startsWith("vault-img://")) {
                    val label = if (altEscaped.isNotBlank()) altEscaped else if (captionEscaped.isNotBlank()) captionEscaped else "Image"
                    "<figure class=\"editorial-callout\"><p style=\"font-style:italic; margin:0;\">[Illustration: $label]</p></figure>"
                } else {
                    val captionHtml = if (captionEscaped.isNotBlank()) {
                        "<figcaption style=\"font-size:0.85em; color:#777; margin-top:6px; font-style:italic;\">$captionEscaped</figcaption>"
                    } else ""
                    "<figure style=\"text-align:center; margin:24px 0;\"><img src=\"$resolvedSrc\" alt=\"$altEscaped\" style=\"max-width:100%; height:auto; border-radius:8px; display:inline-block;\"/>$captionHtml</figure>"
                }
            }
            is MarkdownNode.BlockNode.ThematicBreak -> {
                "<hr/>"
            }
            is MarkdownNode.BlockNode.TableCell -> {
                val dirAttr = resolveBlockDir(block.runs, docDirection)
                "<td$dirAttr>${emitRuns(block.runs)}</td>"
            }
        }
    }

    private fun emitListBlock(listBlock: MarkdownNode.BlockNode.ListBlock, docDirection: String = "ltr"): String {
        val sb = StringBuilder()
        val isOrdered = listBlock.isOrdered
        val tag = if (isOrdered) "ol" else "ul"
        val startAttr = if (isOrdered && listBlock.startNumber > 1) " start=\"${listBlock.startNumber}\"" else ""
        var currentLevel = 0
        val openTags = mutableListOf<String>()

        fun openList(tagType: String) {
            sb.append("<$tagType$startAttr>\n")
            openTags.add(tagType)
        }

        fun closeList() {
            if (openTags.isNotEmpty()) {
                val lastTag = openTags.removeAt(openTags.size - 1)
                sb.append("</$lastTag>\n")
            }
        }

        openList(tag)
        currentLevel = 0

        for (item in listBlock.items) {
            while (item.level > currentLevel) {
                openList(tag)
                currentLevel++
            }
            while (item.level < currentLevel && openTags.size > 1) {
                closeList()
                currentLevel--
            }
            val dirAttr = resolveBlockDir(item.runs, docDirection)
            sb.append("  <li$dirAttr>${emitRuns(item.runs)}</li>\n")
        }

        while (openTags.isNotEmpty()) {
            closeList()
        }

        return sb.toString().trimEnd()
    }

    private fun emitTable(table: MarkdownNode.BlockNode.Table, docDirection: String = "ltr"): String {
        val sb = StringBuilder()
        sb.append("<table>\n")

        // Header Row
        if (table.headers.isNotEmpty()) {
            sb.append("  <thead>\n    <tr>\n")
            table.headers.forEachIndexed { colIdx, cell ->
                val align = table.alignments.getOrElse(colIdx) { TableAlignment.DEFAULT }
                val alignStyle = alignToStyle(align)
                val styleAttr = if (alignStyle.isNotEmpty()) " style=\"$alignStyle\"" else ""
                val dirAttr = resolveBlockDir(cell.runs, docDirection)
                sb.append("      <th$styleAttr$dirAttr>${emitRuns(cell.runs)}</th>\n")
            }
            sb.append("    </tr>\n  </thead>\n")
        }

        // Body Rows
        if (table.rows.isNotEmpty()) {
            sb.append("  <tbody>\n")
            for (row in table.rows) {
                sb.append("    <tr>\n")
                row.forEachIndexed { colIdx, cell ->
                    val align = table.alignments.getOrElse(colIdx) { TableAlignment.DEFAULT }
                    val alignStyle = alignToStyle(align)
                    val styleAttr = if (alignStyle.isNotEmpty()) " style=\"$alignStyle\"" else ""
                    val dirAttr = resolveBlockDir(cell.runs, docDirection)
                    sb.append("      <td$styleAttr$dirAttr>${emitRuns(cell.runs)}</td>\n")
                }
                sb.append("    </tr>\n")
            }
            sb.append("  </tbody>\n")
        }

        sb.append("</table>")
        return sb.toString()
    }

    private fun alignToStyle(align: TableAlignment): String {
        return when (align) {
            TableAlignment.LEFT -> "text-align: left;"
            TableAlignment.CENTER -> "text-align: center;"
            TableAlignment.RIGHT -> "text-align: right;"
            TableAlignment.DEFAULT -> ""
        }
    }

    fun emitRuns(runs: List<MarkdownNode.InlineRun>): String {
        val sb = StringBuilder()
        for (run in runs) {
            if (run.isHardBreak) {
                sb.append("<br/>")
                continue
            }
            var text = escapeHtml(run.text)
            if (run.isCode) {
                text = "<code>$text</code>"
            }
            if (run.isStrikethrough) {
                text = "<del>$text</del>"
            }
            if (run.isItalic) {
                text = "<em>$text</em>"
            }
            if (run.isBold) {
                text = "<strong>$text</strong>"
            }
            if (run.linkUrl != null) {
                val escapedUrl = escapeHtml(run.linkUrl)
                text = "<a href=\"$escapedUrl\">$text</a>"
            }
            sb.append(text)
        }
        return sb.toString()
    }

    fun isRtlText(text: String): Boolean {
        var rtl = 0
        var ltr = 0
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
            }
        }
        return rtl > 0 && rtl >= ltr
    }

    fun isRtlRuns(runs: List<MarkdownNode.InlineRun>): Boolean {
        var rtl = 0
        var ltr = 0
        for (run in runs) {
            for (ch in run.text) {
                when (Character.getDirectionality(ch)) {
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
                }
            }
        }
        return rtl > 0 && rtl >= ltr
    }

    fun isLtrRuns(runs: List<MarkdownNode.InlineRun>): Boolean {
        var rtl = 0
        var ltr = 0
        for (run in runs) {
            for (ch in run.text) {
                when (Character.getDirectionality(ch)) {
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
                }
            }
        }
        return ltr > 0 && ltr > rtl
    }

    fun resolveBlockDir(runs: List<MarkdownNode.InlineRun>, docDirection: String = "ltr"): String {
        return if (isRtlRuns(runs)) {
            " dir=\"rtl\""
        } else if (docDirection == "rtl" && isLtrRuns(runs)) {
            " dir=\"ltr\""
        } else {
            ""
        }
    }

    fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
