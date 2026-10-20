package com.primaloptima.scribe.engine

import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.yield

/**
 * High-performance background text processor for the Scribe Indent System.
 *
 * Implements:
 * 1. Single-pass StringBuilder document processing on Dispatchers.Default with cooperative yield().
 * 2. Automatic-Prefix Invariant: Distinguishes legitimate automatic indentation from manual
 *    indentation, preventing loss of manual spaces when changing or disabling settings.
 * 3. Preserves Markdown headings (#), scene breaks (---, ***, * * *), blockquotes (>),
 *    code blocks (```), tables (|), HTML tags (<), and manual tab characters ('\t').
 * 4. Empty-line floor: Empty lines retain the auto-indent floor as column 0 when indent is ON,
 *    and clean empty strings when indent is OFF.
 * 5. Accurate cursor column calculation relative to pre-formatted text.
 * 6. Flawless line-ending handling across LF, CRLF, and mixed files.
 * 7. Zero-allocation fast checks for line-level visual hints directly on Content buffer.
 */
object ScribeIndentEngine {

    /**
     * Checks if a trimmed line is non-prose (Markdown formatting, headers, breaks, code, tables).
     * Lists are treated as prose so they participate in document indentation.
     */
    fun isNonProseLine(trimmed: String): Boolean {
        return ScribeEditorIndentFormatter.isNonProseLine(trimmed)
    }

    /**
     * High-speed zero-allocation check directly on Sora Editor's Content buffer.
     * Evaluates leading whitespace and whether the line is non-prose without allocating Strings.
     *
     * Returns:
     *   -1 if the line is blank or non-prose (headings, scene breaks, code fences, etc.) -> No indent hint.
     *   >= 0 the count of existing leading whitespace characters (spaces or tabs).
     */
    fun getProseLeadingSpacesFast(content: Content, lineIndex: Int): Int {
        val len = content.getColumnCount(lineIndex)
        if (len == 0) return -1
        var idx = 0
        while (idx < len) {
            val c = content.charAt(lineIndex, idx)
            if (c != ' ' && c != '\t') break
            idx++
        }
        if (idx == len) return -1 // Whitespace only line -> no indent
        val firstChar = content.charAt(lineIndex, idx)

        // Quick non-prose checks: headings, blockquotes, code fences, tables, XML tags
        if (firstChar == '#' || firstChar == '>' || firstChar == '`' || firstChar == '|' || firstChar == '<') {
            return -1
        }
        // Scene breaks (horizontal rules) starting with '---', '***', '___'
        if ((firstChar == '-' || firstChar == '*' || firstChar == '_') && idx + 2 < len) {
            val c2 = content.charAt(lineIndex, idx + 1)
            val c3 = content.charAt(lineIndex, idx + 2)
            if (c2 == firstChar && c3 == firstChar) {
                return -1
            }
        }
        return idx
    }

    /**
     * Processes document indentation in a single allocation-friendly pass with cooperative yielding.
     *
     * @param originalText The document content before formatting.
     * @param oldIndent The previous first-line indent setting.
     * @param newIndent The new first-line indent setting in spaces (0 = off).
     * @return The fully formatted document string with real spaces.
     */
    suspend fun processDocumentIndent(
        originalText: String,
        oldIndent: Int,
        newIndent: Int
    ): String {
        if (originalText.isEmpty()) return ""
        val newIndentStr = if (newIndent > 0) " ".repeat(newIndent) else ""
        val isCrlf = originalText.contains("\r\n")
        val newline = if (isCrlf) "\r\n" else "\n"
        val rawLines = originalText.split("\n")
        val sb = StringBuilder(originalText.length + rawLines.size * (newIndent + 2))

        var inCodeBlock = false
        for (i in rawLines.indices) {
            if (i % 100 == 0 && i > 0) {
                yield()
            }
            val rawLine = rawLines[i]
            val line = if (rawLine.endsWith("\r")) rawLine.substring(0, rawLine.length - 1) else rawLine
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                sb.append(line)
            } else if (inCodeBlock || line.startsWith("\t") || isNonProseLine(trimmed)) {
                // Non-prose elements and tab stops retain original text
                sb.append(line)
            } else if (trimmed.isEmpty()) {
                // Empty / blank lines: retain auto-indent floor when ON, clean string when OFF
                if (newIndent > 0) {
                    sb.append(newIndentStr)
                } else {
                    sb.append("")
                }
            } else {
                // Prose paragraph (including lists):
                val leadingSpaces = ScribeEditorIndentFormatter.countLeadingSpaces(line)
                val legitimateOldAuto = if (oldIndent > 0 && leadingSpaces >= oldIndent) oldIndent else 0
                val manualSpaces = leadingSpaces - legitimateOldAuto
                val proseContent = line.substring(leadingSpaces)
                val newLeading = newIndentStr + " ".repeat(manualSpaces)
                sb.append(newLeading).append(proseContent)
            }

            if (i < rawLines.size - 1) {
                sb.append(newline)
            }
        }
        return sb.toString()
    }

    /**
     * Calculates the adjusted column for the cursor after indentation formatting.
     * MUST be passed the ORIGINAL (pre-formatted) text and pre-formatted cursor coordinates.
     */
    fun calculateAdjustedCursorCol(
        originalText: String,
        lineIndex: Int,
        originalCol: Int,
        newIndent: Int,
        oldIndent: Int = 0
    ): Int {
        return ScribeEditorIndentFormatter.calculateAdjustedCol(
            originalText = originalText,
            lineIndex = lineIndex,
            originalCol = originalCol,
            oldIndent = oldIndent,
            newIndent = newIndent
        )
    }
}
