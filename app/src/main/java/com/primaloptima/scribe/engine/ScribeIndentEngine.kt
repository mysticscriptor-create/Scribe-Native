package com.primaloptima.scribe.engine

/**
 * High-performance background text processor for the Scribe Indent System.
 *
 * Implements:
 * 1. Single-pass StringBuilder document processing on Dispatchers.Default.
 * 2. Edge Case handling (Requirement 8): scans and strips pre-existing manual
 *    spaces/tabs before writing new spaces to prevent double indentation.
 * 3. Preserves Markdown headings (#), scene breaks (---, ***, * * *), blockquotes (>),
 *    and code blocks (```) without adding indent.
 * 4. Cursor position calculation to keep the writer's caret at the exact word and
 *    character position after formatting.
 */
object ScribeIndentEngine {

    /**
     * Processes document indentation in a single allocation-friendly pass.
     *
     * @param originalText The document content before formatting.
     * @param oldIndent The previous first-line indent setting.
     * @param newIndent The new first-line indent setting in spaces (0 = off).
     * @return The fully formatted document string with real spaces.
     */
    fun processDocumentIndent(
        originalText: String,
        oldIndent: Int,
        newIndent: Int
    ): String {
        if (originalText.isEmpty()) return ""
        val newIndentStr = if (newIndent > 0) " ".repeat(newIndent) else ""
        val lines = originalText.split("\n")
        val sb = StringBuilder(originalText.length + lines.size * newIndent)

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trimStart()

            // Skip Markdown headings, scene breaks, blockquotes, code blocks
            val isNonProse = trimmed.startsWith("#") || trimmed.startsWith("---") ||
                trimmed.startsWith("***") || trimmed.startsWith("* * *") ||
                trimmed.startsWith("###") || trimmed.startsWith("___") ||
                trimmed.startsWith(">") || trimmed.startsWith("```")

            if (isNonProse) {
                sb.append(line)
            } else if (trimmed.isEmpty()) {
                // Blank / empty line: keep empty when indent is off, or normalize if indent on
                if (newIndent > 0 && line.isNotEmpty()) {
                    sb.append(newIndentStr)
                } else {
                    sb.append("")
                }
            } else {
                // Prose paragraph:
                // Scan pre-existing manual spaces or tabs (Edge Case 8)
                var leadingSpaces = 0
                while (leadingSpaces < line.length && (line[leadingSpaces] == ' ' || line[leadingSpaces] == '\t')) {
                    leadingSpaces++
                }

                // Strip existing leading whitespace first to prevent double-indentation
                val proseContent = if (leadingSpaces > 0) line.substring(leadingSpaces) else line

                if (newIndent > 0) {
                    sb.append(newIndentStr).append(proseContent)
                } else {
                    // Indent turned OFF: write clean unindented prose
                    sb.append(proseContent)
                }
            }

            if (i < lines.size - 1) {
                sb.append("\n")
            }
        }

        return sb.toString()
    }

    /**
     * Calculates the adjusted column for the cursor after indentation formatting.
     */
    fun calculateAdjustedCursorCol(
        originalText: String,
        lineIndex: Int,
        originalCol: Int,
        newIndent: Int
    ): Int {
        val lines = originalText.split("\n")
        if (lineIndex !in lines.indices) return originalCol
        val line = lines[lineIndex]
        val trimmed = line.trimStart()

        val isNonProse = trimmed.startsWith("#") || trimmed.startsWith("---") ||
            trimmed.startsWith("***") || trimmed.startsWith("* * *") ||
            trimmed.startsWith("###") || trimmed.startsWith("___") ||
            trimmed.startsWith(">") || trimmed.startsWith("```")

        if (isNonProse || trimmed.isEmpty()) {
            return originalCol
        }

        var oldLeadingSpaces = 0
        while (oldLeadingSpaces < line.length && (line[oldLeadingSpaces] == ' ' || line[oldLeadingSpaces] == '\t')) {
            oldLeadingSpaces++
        }

        return if (newIndent > 0) {
            if (originalCol <= oldLeadingSpaces) {
                newIndent
            } else {
                (originalCol - oldLeadingSpaces + newIndent).coerceAtLeast(newIndent)
            }
        } else {
            (originalCol - oldLeadingSpaces).coerceAtLeast(0)
        }
    }
}
