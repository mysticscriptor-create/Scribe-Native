package com.primaloptima.scribe.util.export

/**
 * High-performance, AST-driven Markdown tokenizer and parser.
 * Provides clean structural document nodes and formatted inline text runs for
 * DOCX OpenXML generation, EPUB 3 chapter assembly, and HTML styling.
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
    ) {
        val hasFormatting: Boolean
            get() = isBold || isItalic || isStrikethrough || isCode || linkUrl != null
    }

    /**
     * Parses the markdown content into a list of structured [AstNode]s.
     */
    fun parse(markdown: String): AstDocument {
        val nodes = mutableListOf<AstNode>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Empty line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 2. Fenced Code Block
            if (trimmed.startsWith("```")) {
                val language = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // Skip closing ```
                nodes.add(AstNode.CodeBlockNode(codeLines.joinToString("\n"), language))
                continue
            }

            // 3. Horizontal Divider
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                nodes.add(AstNode.DividerNode)
                i++
                continue
            }

            // 4. Heading (# to ######)
            if (trimmed.startsWith("#")) {
                val hashCount = trimmed.takeWhile { it == '#' }.length
                if (hashCount in 1..6 && trimmed.length > hashCount && trimmed[hashCount] == ' ') {
                    val headingText = trimmed.substring(hashCount).trim()
                    nodes.add(AstNode.HeadingNode(hashCount, headingText, parseInlineRuns(headingText)))
                    i++
                    continue
                }
            }

            // 5. Blockquote (> ...)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith(">") || (lines[i].isNotBlank() && quoteLines.isNotEmpty() && !lines[i].trim().startsWith("#")))) {
                    val qLine = lines[i].trim()
                    if (qLine.startsWith(">")) {
                        quoteLines.add(qLine.removePrefix(">").trimStart())
                    } else {
                        quoteLines.add(qLine)
                    }
                    i++
                }
                val innerDoc = parse(quoteLines.joinToString("\n"))
                nodes.add(AstNode.BlockquoteNode(innerDoc.nodes))
                continue
            }

            // 6. Unordered List (- , * , +)
            if (isUnorderedListLine(trimmed)) {
                val items = mutableListOf<AstNode.ListItemNode>()
                while (i < lines.size && isUnorderedListLine(lines[i].trim())) {
                    val itemText = stripUnorderedMarker(lines[i].trim())
                    items.add(AstNode.ListItemNode(parseInlineRuns(itemText)))
                    i++
                }
                nodes.add(AstNode.ListNode(items, isOrdered = false))
                continue
            }

            // 7. Ordered List (1. , 2. )
            if (isOrderedListLine(trimmed)) {
                val items = mutableListOf<AstNode.ListItemNode>()
                while (i < lines.size && isOrderedListLine(lines[i].trim())) {
                    val itemText = stripOrderedMarker(lines[i].trim())
                    items.add(AstNode.ListItemNode(parseInlineRuns(itemText)))
                    i++
                }
                nodes.add(AstNode.ListNode(items, isOrdered = true))
                continue
            }

            // 8. Markdown Table (| header | header |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && isTableSeparatorLine(lines[i + 1].trim())) {
                val headerRow = parseTableRow(trimmed)
                i += 2 // skip header and separator
                val dataRows = mutableListOf<List<List<InlineRun>>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    dataRows.add(parseTableRow(lines[i].trim()))
                    i++
                }
                nodes.add(AstNode.TableNode(headerRow, dataRows))
                continue
            }

            // 9. Standalone Image (![alt](url))
            val imageMatch = standaloneImageRegex.matchEntire(trimmed)
            if (imageMatch != null) {
                val alt = imageMatch.groupValues[1]
                val uri = imageMatch.groupValues[2]
                var caption = ""
                // Check if following line is an italic caption like *Caption* or _Caption_
                if (i + 1 < lines.size) {
                    val nextTrim = lines[i + 1].trim()
                    if (isItalicCaptionLine(nextTrim)) {
                        caption = nextTrim.substring(1, nextTrim.length - 1).trim()
                        i++ // consume caption line
                    }
                }
                nodes.add(AstNode.ImageNode(alt = alt, uri = uri, caption = caption.ifBlank { alt }))
                i++
                continue
            }

            // 10. Standard Paragraph (can span consecutive non-empty lines)
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.isEmpty() ||
                    cur.startsWith("#") ||
                    cur.startsWith(">") ||
                    cur.startsWith("```") ||
                    cur == "---" || cur == "***" || cur == "___" ||
                    isUnorderedListLine(cur) ||
                    isOrderedListLine(cur) ||
                    (cur.startsWith("|") && cur.endsWith("|")) ||
                    standaloneImageRegex.matches(cur)
                ) {
                    break
                }
                paraLines.add(cur)
                i++
            }
            if (paraLines.isNotEmpty()) {
                val fullParaText = paraLines.joinToString(" ")
                nodes.add(AstNode.ParagraphNode(parseInlineRuns(fullParaText)))
            }
        }

        return AstDocument(nodes)
    }

    private val standaloneImageRegex = Regex("""^!\[(.*?)\]\((.*?)\)$""")

    private fun isUnorderedListLine(line: String): Boolean {
        return (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) && line.length > 2
    }

    private fun stripUnorderedMarker(line: String): String {
        return line.substring(2).trim()
    }

    private fun isOrderedListLine(line: String): Boolean {
        return line.matches(Regex("""^\d+\.\s+.*"""))
    }

    private fun stripOrderedMarker(line: String): String {
        return line.replaceFirst(Regex("""^\d+\.\s+"""), "").trim()
    }

    private fun isTableSeparatorLine(line: String): Boolean {
        if (!line.startsWith("|") || !line.endsWith("|")) return false
        val segments = line.substring(1, line.length - 1).split("|")
        return segments.all { seg ->
            val s = seg.trim()
            s.matches(Regex("""^:?-+:?$"""))
        }
    }

    private fun parseTableRow(rowLine: String): List<List<InlineRun>> {
        val cells = rowLine.substring(1, rowLine.length - 1).split("|")
        return cells.map { parseInlineRuns(it.trim()) }
    }

    private fun isItalicCaptionLine(line: String): Boolean {
        return (line.startsWith("*") && line.endsWith("*") && line.length > 2 && !line.startsWith("**")) ||
                (line.startsWith("_") && line.endsWith("_") && line.length > 2 && !line.startsWith("__"))
    }

    /**
     * Parses inline runs within a paragraph, heading, or list item.
     * Evaluates `***bold italic***`, `**bold**`, `*italic*`, `~~strike~~`, `` `code` ``, `[label](url)`.
     */
    fun parseInlineRuns(text: String): List<InlineRun> {
        if (text.isEmpty()) return emptyList()

        val runs = mutableListOf<InlineRun>()
        val len = text.length
        var index = 0
        val currentText = StringBuilder()

        fun flushText(bold: Boolean = false, italic: Boolean = false, strike: Boolean = false, code: Boolean = false, link: String? = null) {
            if (currentText.isNotEmpty()) {
                runs.add(InlineRun(currentText.toString(), isBold = bold, isItalic = italic, isStrikethrough = strike, isCode = code, linkUrl = link))
                currentText.clear()
            }
        }

        while (index < len) {
            // Check for Inline Code: `code`
            if (text[index] == '`') {
                val endCode = text.indexOf('`', index + 1)
                if (endCode != -1) {
                    flushText()
                    val codeContent = text.substring(index + 1, endCode)
                    runs.add(InlineRun(codeContent, isCode = true))
                    index = endCode + 1
                    continue
                }
            }

            // Check for Strikethrough: ~~text~~
            if (index + 1 < len && text[index] == '~' && text[index + 1] == '~') {
                val endStrike = text.indexOf("~~", index + 2)
                if (endStrike != -1) {
                    flushText()
                    val strikeContent = text.substring(index + 2, endStrike)
                    // Nested inline parsing for italic/bold inside strikethrough if needed
                    val nested = parseInlineRuns(strikeContent)
                    for (n in nested) {
                        runs.add(n.copy(isStrikethrough = true))
                    }
                    index = endStrike + 2
                    continue
                }
            }

            // Check for Links: [label](url)
            if (text[index] == '[') {
                val closeBracket = text.indexOf(']', index + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        flushText()
                        val label = text.substring(index + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        runs.add(InlineRun(label, linkUrl = url))
                        index = closeParen + 1
                        continue
                    }
                }
            }

            // Check for Bold-Italic: ***text***
            if (index + 2 < len && text[index] == '*' && text[index + 1] == '*' && text[index + 2] == '*') {
                val endBi = text.indexOf("***", index + 3)
                if (endBi != -1) {
                    flushText()
                    val biContent = text.substring(index + 3, endBi)
                    runs.add(InlineRun(biContent, isBold = true, isItalic = true))
                    index = endBi + 3
                    continue
                }
            }

            // Check for Bold: **text** or __text__
            if ((index + 1 < len && text[index] == '*' && text[index + 1] == '*') ||
                (index + 1 < len && text[index] == '_' && text[index + 1] == '_')
            ) {
                val delim = text.substring(index, index + 2)
                val endBold = text.indexOf(delim, index + 2)
                if (endBold != -1) {
                    flushText()
                    val boldContent = text.substring(index + 2, endBold)
                    // Check if inner content has italic: *italic*
                    val innerRuns = parseInlineRuns(boldContent)
                    for (ir in innerRuns) {
                        runs.add(ir.copy(isBold = true))
                    }
                    index = endBold + 2
                    continue
                }
            }

            // Check for Italic: *text* or _text_
            if ((text[index] == '*' || text[index] == '_') && (index + 1 < len && text[index + 1] != ' ')) {
                val delim = text[index]
                val endItalic = findClosingItalic(text, delim, index + 1)
                if (endItalic != -1) {
                    flushText()
                    val italicContent = text.substring(index + 1, endItalic)
                    runs.add(InlineRun(italicContent, isItalic = true))
                    index = endItalic + 1
                    continue
                }
            }

            currentText.append(text[index])
            index++
        }

        flushText()
        return runs
    }

    private fun findClosingItalic(text: String, delim: Char, start: Int): Int {
        for (i in start until text.length) {
            if (text[i] == delim && text[i - 1] != ' ') {
                return i
            }
        }
        return -1
    }
}
