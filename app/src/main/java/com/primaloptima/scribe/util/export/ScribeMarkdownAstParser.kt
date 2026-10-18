package com.primaloptima.scribe.util.export

/**
 * High-performance, standards-compliant CommonMark AST parser for the Scribe export engine.
 * Generates an immutable, structured document tree supporting multi-level lists (ilvl 0..8),
 * tables (with or without outer pipes, with uneven row padding), code fences, blockquotes,
 * illustrations, and rich inline runs with character escaping, soft breaks, and hard line-break semantics.
 */
object ScribeMarkdownAstParser {

    /**
     * Parses markdown text into a unified [MarkdownNode.Document].
     */
    fun parse(markdown: String): MarkdownNode.Document {
        if (markdown.isBlank()) {
            return MarkdownNode.Document(emptyList())
        }
        val lines = markdown.lines()
        val blocks = parseBlocks(lines)
        return MarkdownNode.Document(blocks)
    }

    /**
     * Parses a list of lines into top-level [MarkdownNode.BlockNode] elements.
     */
    fun parseBlocks(lines: List<String>): List<MarkdownNode.BlockNode> {
        val blocks = mutableListOf<MarkdownNode.BlockNode>()
        var i = 0
        val count = lines.size

        while (i < count) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Skip completely empty lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 2. Fenced Code Blocks (``` or ~~~)
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                val language = trimmed.removePrefix(fence).trim().ifBlank { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < count && !lines[i].trim().startsWith(fence)) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < count && lines[i].trim().startsWith(fence)) {
                    i++ // consume closing fence
                }
                blocks.add(
                    MarkdownNode.BlockNode.CodeBlock(
                        code = codeLines.joinToString("\n"),
                        language = language
                    )
                )
                continue
            }

            // 3. Headings (# H1 to ###### H6)
            val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                blocks.add(
                    MarkdownNode.BlockNode.Heading(
                        level = level,
                        runs = parseInlineRuns(text)
                    )
                )
                i++
                continue
            }

            // 4. Thematic Break (---, ***, ___)
            if (trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                trimmed.matches(Regex("""^[-*_](\s*[-*_]){2,}$"""))
            ) {
                blocks.add(MarkdownNode.BlockNode.ThematicBreak)
                i++
                continue
            }

            // 5. Blockquotes (> ...)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < count) {
                    val qLine = lines[i].trim()
                    if (qLine.startsWith(">")) {
                        val stripped = qLine.substring(1).let {
                            if (it.startsWith(" ")) it.substring(1) else it
                        }
                        quoteLines.add(stripped)
                        i++
                    } else if (qLine.isEmpty()) {
                        // Check if following line continues blockquote
                        if (i + 1 < count && lines[i + 1].trim().startsWith(">")) {
                            quoteLines.add("")
                            i++
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
                val childBlocks = parseBlocks(quoteLines)
                blocks.add(MarkdownNode.BlockNode.Blockquote(children = childBlocks))
                continue
            }

            // 6. Tables (with or without outer pipes, e.g. `| A | B |` or `A | B`)
            if (isTableRow(trimmed) && i + 1 < count && isTableSeparatorRow(lines[i + 1].trim())) {
                val headerCells = parseTableCells(trimmed)
                val sepLine = lines[i + 1].trim()
                val alignments = parseTableAlignments(sepLine, headerCells.size)
                i += 2 // consume header and separator

                val rows = mutableListOf<List<MarkdownNode.BlockNode.TableCell>>()
                while (i < count && isTableRow(lines[i].trim()) && !isTableSeparatorRow(lines[i].trim())) {
                    val rawCells = parseTableCells(lines[i].trim())
                    // Pad or trim cells to match column count
                    val paddedCells = (0 until headerCells.size).map { colIdx ->
                        if (colIdx < rawCells.size) rawCells[colIdx]
                        else MarkdownNode.BlockNode.TableCell(emptyList())
                    }
                    rows.add(paddedCells)
                    i++
                }
                blocks.add(
                    MarkdownNode.BlockNode.Table(
                        headers = headerCells,
                        rows = rows,
                        alignments = alignments
                    )
                )
                continue
            }

            // 7. Lists (Ordered and Unordered with Nesting)
            if (isListLine(line)) {
                val listItems = mutableListOf<MarkdownNode.BlockNode.ListItem>()
                val isOrderedList = isOrderedListLine(line)
                val startNumber = if (isOrderedList) extractOrderedNumber(line) else 1

                while (i < count && isListLine(lines[i])) {
                    val curLine = lines[i]
                    if (isOrderedListLine(curLine) != isOrderedList) {
                        break
                    }
                    val indent = countLeadingSpaces(curLine)
                    val level = (indent / 2).coerceIn(0, 8)
                    val itemText = stripListMarker(curLine)
                    val runs = parseInlineRuns(itemText)
                    listItems.add(
                        MarkdownNode.BlockNode.ListItem(
                            level = level,
                            runs = runs,
                            children = emptyList()
                        )
                    )
                    i++
                }
                blocks.add(
                    MarkdownNode.BlockNode.ListBlock(
                        items = listItems,
                        isOrdered = isOrderedList,
                        startNumber = startNumber
                    )
                )
                continue
            }

            // 8. Standalone Image with optional caption
            val imageMatch = standaloneImageRegex.find(trimmed)
            if (imageMatch != null) {
                val alt = imageMatch.groupValues[1]
                val uri = imageMatch.groupValues[2]
                var caption: String? = null
                if (i + 1 < count) {
                    val nextTrim = lines[i + 1].trim()
                    if (isItalicCaptionLine(nextTrim)) {
                        caption = nextTrim.substring(1, nextTrim.length - 1).trim()
                        i++ // consume caption
                    }
                }
                blocks.add(
                    MarkdownNode.BlockNode.ImageBlock(
                        alt = alt,
                        uri = uri,
                        caption = caption?.ifBlank { null }
                    )
                )
                i++
                continue
            }

            // 9. Standard Paragraph with soft & hard line breaks
            val paragraphRuns = mutableListOf<MarkdownNode.InlineRun>()
            while (i < count) {
                val pLine = lines[i]
                val pTrim = pLine.trim()

                if (pTrim.isEmpty() ||
                    pTrim.startsWith("#") ||
                    pTrim.startsWith(">") ||
                    pTrim.startsWith("```") ||
                    pTrim.startsWith("~~~") ||
                    pTrim == "---" || pTrim == "***" || pTrim == "___" ||
                    isListLine(pLine) ||
                    (isTableRow(pTrim) && i + 1 < count && isTableSeparatorRow(lines[i + 1].trim())) ||
                    standaloneImageRegex.matches(pTrim)
                ) {
                    break
                }

                val isHardBreak = pLine.endsWith("  ") || pLine.endsWith("\\")
                val cleanLine = if (pLine.endsWith("\\")) {
                    pLine.dropLast(1).trim()
                } else {
                    pTrim
                }

                val lineRuns = parseInlineRuns(cleanLine)
                paragraphRuns.addAll(lineRuns)

                if (isHardBreak) {
                    paragraphRuns.add(MarkdownNode.InlineRun(text = "", isHardBreak = true))
                } else {
                    // Soft break: single space between joined lines if not last line of paragraph
                    if (i + 1 < count && lines[i + 1].trim().isNotBlank() && !isBlockStarter(lines[i + 1])) {
                        paragraphRuns.add(MarkdownNode.InlineRun(text = " "))
                    }
                }
                i++
            }

            if (paragraphRuns.isNotEmpty()) {
                blocks.add(MarkdownNode.BlockNode.Paragraph(runs = mergeRuns(paragraphRuns)))
            }
        }

        return blocks
    }

    private val standaloneImageRegex = Regex("""^!\[(.*?)\]\((.*?)\)$""")

    private fun isBlockStarter(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("#") ||
                trimmed.startsWith(">") ||
                trimmed.startsWith("```") ||
                trimmed.startsWith("~~~") ||
                trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                isListLine(line) ||
                (isTableRow(trimmed)) ||
                standaloneImageRegex.matches(trimmed)
    }

    private fun countLeadingSpaces(line: String): Int {
        var count = 0
        for (ch in line) {
            when (ch) {
                ' ' -> count++
                '\t' -> count += 4
                else -> break
            }
        }
        return count
    }

    private fun isListLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) && trimmed.length > 2 ||
                trimmed.matches(Regex("""^\d+\.\s+.*"""))
    }

    private fun isOrderedListLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.matches(Regex("""^\d+\.\s+.*"""))
    }

    private fun extractOrderedNumber(line: String): Int {
        val trimmed = line.trimStart()
        val numStr = trimmed.substringBefore('.')
        return numStr.toIntOrNull() ?: 1
    }

    private fun stripListMarker(line: String): String {
        val trimmed = line.trimStart()
        return if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            trimmed.substring(2).trim()
        } else {
            trimmed.replaceFirst(Regex("""^\d+\.\s+"""), "").trim()
        }
    }

    private fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.contains("|")) return false
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~") || trimmed.startsWith("#") || trimmed.startsWith(">")) return false
        return true
    }

    private fun isTableSeparatorRow(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("|")) return false
        val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        return parts.all { it.matches(Regex("""^:?-+:?$""")) }
    }

    private fun parseTableAlignments(sepLine: String, columnCount: Int): List<TableAlignment> {
        val parts = sepLine.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val alignments = mutableListOf<TableAlignment>()
        for (part in parts) {
            val align = when {
                part.startsWith(":") && part.endsWith(":") -> TableAlignment.CENTER
                part.endsWith(":") -> TableAlignment.RIGHT
                part.startsWith(":") -> TableAlignment.LEFT
                else -> TableAlignment.DEFAULT
            }
            alignments.add(align)
        }
        while (alignments.size < columnCount) {
            alignments.add(TableAlignment.DEFAULT)
        }
        return alignments
    }

    private fun parseTableCells(rowLine: String): List<MarkdownNode.BlockNode.TableCell> {
        var trimmed = rowLine.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.dropLast(1)
        val cells = trimmed.split("|")
        return cells.map { cellText ->
            MarkdownNode.BlockNode.TableCell(runs = parseInlineRuns(cellText.trim()))
        }
    }

    private fun isItalicCaptionLine(line: String): Boolean {
        return ((line.startsWith("*") && line.endsWith("*") && line.length > 2 && !line.startsWith("**")) ||
                (line.startsWith("_") && line.endsWith("_") && line.length > 2 && !line.startsWith("__")))
    }

    /**
     * Parses inline formatted runs with support for:
     * - Escaping (`\*`, `\_`, `\[`, `\]`, `\~`, `\``, `\\`)
     * - Inline code (`` `code` ``)
     * - Strikethrough (`~~text~~`)
     * - Links with nested inlines (`[**Bold Link**](url)`)
     * - Bold italic (`***text***` or `___text___`)
     * - Bold (`**text**` or `__text__`)
     * - Italic (`*text*` or `_text_`)
     */
    fun parseInlineRuns(text: String): List<MarkdownNode.InlineRun> {
        if (text.isEmpty()) return emptyList()
        val runs = mutableListOf<MarkdownNode.InlineRun>()
        val len = text.length
        var i = 0
        val buffer = StringBuilder()

        fun flush(
            bold: Boolean = false,
            italic: Boolean = false,
            strike: Boolean = false,
            code: Boolean = false,
            link: String? = null
        ) {
            if (buffer.isNotEmpty()) {
                runs.add(
                    MarkdownNode.InlineRun(
                        text = buffer.toString(),
                        isBold = bold,
                        isItalic = italic,
                        isStrikethrough = strike,
                        isCode = code,
                        linkUrl = link
                    )
                )
                buffer.clear()
            }
        }

        while (i < len) {
            val ch = text[i]

            // 1. Escaped Formatting (\* \_ \[ \] \~ \` \\)
            if (ch == '\\' && i + 1 < len) {
                val next = text[i + 1]
                if (next in "\\*_~`[]()#-+|") {
                    buffer.append(next)
                    i += 2
                    continue
                }
            }

            // 2. Inline Code: `code`
            if (ch == '`') {
                val closeIdx = text.indexOf('`', i + 1)
                if (closeIdx != -1) {
                    flush()
                    val codeContent = text.substring(i + 1, closeIdx)
                    runs.add(MarkdownNode.InlineRun(text = codeContent, isCode = true))
                    i = closeIdx + 1
                    continue
                }
            }

            // 3. Strikethrough: ~~text~~
            if (ch == '~' && i + 1 < len && text[i + 1] == '~') {
                val closeIdx = text.indexOf("~~", i + 2)
                if (closeIdx != -1) {
                    flush()
                    val strikeContent = text.substring(i + 2, closeIdx)
                    val innerRuns = parseInlineRuns(strikeContent)
                    for (ir in innerRuns) {
                        runs.add(ir.copy(isStrikethrough = true))
                    }
                    i = closeIdx + 2
                    continue
                }
            }

            // 4. Links with nested inlines: [label](url)
            if (ch == '[') {
                val closeBracket = findMatchingBracket(text, i)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        flush()
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        val labelRuns = parseInlineRuns(label)
                        if (labelRuns.isEmpty()) {
                            runs.add(MarkdownNode.InlineRun(text = label, linkUrl = url))
                        } else {
                            for (lr in labelRuns) {
                                runs.add(lr.copy(linkUrl = url))
                            }
                        }
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 5. Bold-Italic: ***text*** or ___text___
            if ((ch == '*' || ch == '_') && i + 2 < len && text[i + 1] == ch && text[i + 2] == ch) {
                val delim = "$ch$ch$ch"
                val closeIdx = text.indexOf(delim, i + 3)
                if (closeIdx != -1) {
                    flush()
                    val content = text.substring(i + 3, closeIdx)
                    val innerRuns = parseInlineRuns(content)
                    for (ir in innerRuns) {
                        runs.add(ir.copy(isBold = true, isItalic = true))
                    }
                    i = closeIdx + 3
                    continue
                }
            }

            // 6. Bold: **text** or __text__
            if ((ch == '*' || ch == '_') && i + 1 < len && text[i + 1] == ch) {
                val delim = "$ch$ch"
                val closeIdx = text.indexOf(delim, i + 2)
                if (closeIdx != -1) {
                    flush()
                    val content = text.substring(i + 2, closeIdx)
                    val innerRuns = parseInlineRuns(content)
                    for (ir in innerRuns) {
                        runs.add(ir.copy(isBold = true))
                    }
                    i = closeIdx + 2
                    continue
                }
            }

            // 7. Italic: *text* or _text_
            if (ch == '*' || ch == '_') {
                val closeIdx = findClosingItalic(text, ch, i + 1)
                if (closeIdx != -1) {
                    flush()
                    val content = text.substring(i + 1, closeIdx)
                    val innerRuns = parseInlineRuns(content)
                    for (ir in innerRuns) {
                        runs.add(ir.copy(isItalic = true))
                    }
                    i = closeIdx + 1
                    continue
                }
            }

            // Regular literal character
            buffer.append(ch)
            i++
        }

        flush()
        return mergeRuns(runs)
    }

    private fun findMatchingBracket(text: String, startIdx: Int): Int {
        var depth = 0
        for (i in startIdx until text.length) {
            if (text[i] == '\\') continue
            if (text[i] == '[') depth++
            if (text[i] == ']') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }

    private fun findClosingItalic(text: String, delim: Char, start: Int): Int {
        for (i in start until text.length) {
            if (text[i] == '\\') continue
            if (text[i] == delim && text[i - 1] != ' ') {
                return i
            }
        }
        return -1
    }

    private fun mergeRuns(runs: List<MarkdownNode.InlineRun>): List<MarkdownNode.InlineRun> {
        if (runs.size <= 1) return runs
        val merged = mutableListOf<MarkdownNode.InlineRun>()
        var current = runs[0]

        for (k in 1 until runs.size) {
            val next = runs[k]
            if (!current.isHardBreak && !next.isHardBreak &&
                current.isBold == next.isBold &&
                current.isItalic == next.isItalic &&
                current.isStrikethrough == next.isStrikethrough &&
                current.isCode == next.isCode &&
                current.linkUrl == next.linkUrl
            ) {
                current = current.copy(text = current.text + next.text)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
