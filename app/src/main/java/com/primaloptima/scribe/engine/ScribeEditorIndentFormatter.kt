package com.primaloptima.scribe.engine

/**
 * Architectural Result Actions calculated by [ScribeEditorIndentFormatter].
 *
 * Designed to separate pure indentation rules and text edit calculations
 * from the Android / Sora Editor UI mutation layer.
 */
sealed class FormatterNewlineAction {
    /**
     * Standard insertion of a single newline followed by calculated indentation.
     * Invariant: Inserts strictly ONE newline ('\n').
     */
    data class InsertNewline(
        val insertCol: Int,
        val textToInsert: String,
        val targetLine: Int,
        val targetCol: Int
    ) : FormatterNewlineAction()

    /**
     * Normalizes an empty or whitespace-only line to the auto-indent floor
     * and inserts the next line with auto-indent.
     * Invariant: Inserts strictly ONE newline ('\n').
     */
    data class ReplaceAndInsertNewline(
        val lineToReplace: Int,
        val oldLineLength: Int,
        val normalizedLine: String,
        val textToInsert: String,
        val targetLine: Int,
        val targetCol: Int
    ) : FormatterNewlineAction()
}

sealed class FormatterDeleteAction {
    /**
     * Not intercepted by the formatter; delegate to standard character-by-character backspace.
     */
    object DelegateToStandardDelete : FormatterDeleteAction()

    /**
     * Line 0 clamp: Cursor sits at or before the auto-indent floor on the first line of the document.
     * Invariant: Zero deletion, preserves all Line 0 text.
     */
    data class ClampCursorLineZero(val targetCol: Int) : FormatterDeleteAction()

    /**
     * Merge current line with previous line.
     * Deletes from the end of the previous line across the newline and up to [deleteToCol]
     * on the current line, preserving all subsequent manual spaces and text.
     */
    data class MergeWithPreviousLine(
        val prevLine: Int,
        val prevCol: Int,
        val currentLine: Int,
        val deleteToCol: Int
    ) : FormatterDeleteAction()
}

/**
 * Pure Kotlin Indentation Policy & Formatter for Scribe.
 *
 * Implements:
 * 1. Single-newline invariant: Enter ALWAYS inserts strictly 1 newline ('\n', never '\n\n').
 * 2. Automatic-prefix invariant: First N spaces = auto-indent; additional spaces = manual indent.
 * 3. Prevention of data loss during deletion, newline splitting, and setting adjustments.
 * 4. Empty-line preservation: Empty lines in auto-indent mode retain the auto-indent floor
 *    as their logical "column 0", eliminating teleporting text and IME composition glitches.
 * 5. Safe handling of non-prose markdown elements (headings, scene breaks, code fences, quotes, tables)
 *    and manual tab characters ('\t').
 */
object ScribeEditorIndentFormatter {

    /**
     * Counts consecutive leading ASCII spaces (' ').
     * Tabs ('\t') and any other characters immediately terminate the space count.
     */
    fun countLeadingSpaces(lineStr: CharSequence): Int {
        var count = 0
        while (count < lineStr.length && lineStr[count] == ' ') {
            count++
        }
        return count
    }

    /**
     * Returns true if the line is empty or composed entirely of ASCII spaces or tabs.
     */
    fun isLineWhitespaceOnly(lineStr: CharSequence): Boolean {
        for (i in 0 until lineStr.length) {
            val c = lineStr[i]
            if (c != ' ' && c != '\t') return false
        }
        return true
    }

    /**
     * Identifies non-prose lines in Markdown (headings, thematic breaks / scene breaks,
     * blockquotes, code fences, tables, XML/HTML tags).
     */
    fun isNonProseLine(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        return trimmed.startsWith("#") ||
            trimmed.startsWith("---") ||
            trimmed.startsWith("***") ||
            trimmed.startsWith("* * *") ||
            trimmed.startsWith("###") ||
            trimmed.startsWith("___") ||
            trimmed.startsWith(">") ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("|") ||
            trimmed.startsWith("<")
    }

    /**
     * Calculates the exact Newline edit for the given cursor position and document state.
     *
     * Invariants:
     * - Inserts strictly ONE newline ('\n').
     * - In auto-indent mode (autoIndentSpaces > 0), the cursor lands at or after the indent floor,
     *   never dropping to column 0.
     * - Retains manual spaces beyond the auto-indent floor.
     * - When skipping a line (Enter on whitespace-only line), normalizes the current line to
     *   autoIndentSpaces and supplies autoIndentSpaces to the new line.
     */
    fun computeNewline(
        lineStr: String,
        line: Int,
        col: Int,
        autoIndentSpaces: Int
    ): FormatterNewlineAction {
        val isWhitespaceOnly = isLineWhitespaceOnly(lineStr)

        if (autoIndentSpaces > 0) {
            val baseIndent = " ".repeat(autoIndentSpaces)
            if (isWhitespaceOnly) {
                // Scene break / blank line:
                // Current line retains baseIndent (acting as visual Col 0); new line gets baseIndent.
                return FormatterNewlineAction.ReplaceAndInsertNewline(
                    lineToReplace = line,
                    oldLineLength = lineStr.length,
                    normalizedLine = baseIndent,
                    textToInsert = "\n$baseIndent",
                    targetLine = line + 1,
                    targetCol = autoIndentSpaces
                )
            } else {
                // Line has text content:
                val leadingSpaces = countLeadingSpaces(lineStr)
                val manualSpaces = (leadingSpaces - autoIndentSpaces).coerceAtLeast(0)
                val totalIndent = baseIndent + " ".repeat(manualSpaces)

                // Cursor splitting: clamp insert col so cursor cannot split the auto-indent prefix itself
                val effectiveCol = col.coerceAtLeast(autoIndentSpaces).coerceAtMost(lineStr.length)

                return FormatterNewlineAction.InsertNewline(
                    insertCol = effectiveCol,
                    textToInsert = "\n$totalIndent",
                    targetLine = line + 1,
                    targetCol = totalIndent.length
                )
            }
        }

        // Auto Indent is OFF (Pure Manual Mode):
        if (isWhitespaceOnly) {
            // Consecutive Enter on empty line clears manual spaces to ""
            return FormatterNewlineAction.ReplaceAndInsertNewline(
                lineToReplace = line,
                oldLineLength = lineStr.length,
                normalizedLine = "",
                textToInsert = "\n",
                targetLine = line + 1,
                targetCol = 0
            )
        }

        val leadingSpaces = countLeadingSpaces(lineStr)
        val indentToInsert = " ".repeat(leadingSpaces.coerceAtMost(col))
        return FormatterNewlineAction.InsertNewline(
            insertCol = col,
            textToInsert = "\n$indentToInsert",
            targetLine = line + 1,
            targetCol = indentToInsert.length
        )
    }

    /**
     * Calculates the exact Backspace action.
     *
     * Invariants:
     * - Manual spaces (> autoIndentSpaces) delete ONE-BY-ONE via standard delete.
     * - Backspace at the auto-indent boundary (col == autoIndentSpaces) merges with the previous line.
     * - Line 0 boundary delete NEVER destroys line 0 text or manual indentation; it safely clamps cursor.
     * - Zero data loss: line text is never truncated or overwritten with empty spaces.
     */
    fun computeDelete(
        lineStr: String,
        line: Int,
        col: Int,
        autoIndentSpaces: Int,
        prevLineLength: Int
    ): FormatterDeleteAction {
        if (autoIndentSpaces <= 0) {
            return FormatterDeleteAction.DelegateToStandardDelete
        }

        val isWhitespaceOnly = isLineWhitespaceOnly(lineStr)

        if (isWhitespaceOnly) {
            // If the empty line has manual spaces (> autoIndentSpaces), delete one space at a time
            if (lineStr.length > autoIndentSpaces && col > autoIndentSpaces) {
                return FormatterDeleteAction.DelegateToStandardDelete
            }

            // Line is at base indent or empty:
            if (line > 0) {
                // Merge this empty line into previous line
                return FormatterDeleteAction.MergeWithPreviousLine(
                    prevLine = line - 1,
                    prevCol = prevLineLength,
                    currentLine = line,
                    deleteToCol = lineStr.length
                )
            } else {
                // Line 0 empty: clamp cursor to auto-indent floor
                return FormatterDeleteAction.ClampCursorLineZero(targetCol = autoIndentSpaces)
            }
        }

        // Line has text content:
        if (col <= autoIndentSpaces) {
            if (line > 0) {
                // Merge line into previous line: strip auto prefix (col 0..autoIndentSpaces)
                // and append the remaining text to the previous line.
                val deleteTo = if (lineStr.length >= autoIndentSpaces) autoIndentSpaces else countLeadingSpaces(lineStr)
                return FormatterDeleteAction.MergeWithPreviousLine(
                    prevLine = line - 1,
                    prevCol = prevLineLength,
                    currentLine = line,
                    deleteToCol = deleteTo
                )
            } else {
                // Line 0 guard: NEVER delete or replace Line 0 text! Clamp cursor to floor.
                return FormatterDeleteAction.ClampCursorLineZero(targetCol = autoIndentSpaces)
            }
        }

        // col > autoIndentSpaces: cursor is in manual spaces or body text.
        // Delegate to standard delete so spaces and characters delete one at a time.
        return FormatterDeleteAction.DelegateToStandardDelete
    }

    /**
     * Determines whether an empty or whitespace-only line requires its indent floor to be established.
     * Returns the replacement string if an update is required, or null if the line already meets the floor.
     *
     * Invariant: Never alters lines containing text or manual spaces exceeding autoIndentSpaces.
     */
    fun computeEmptyLineFloor(lineStr: String, autoIndentSpaces: Int): String? {
        if (autoIndentSpaces <= 0) return null
        if (!isLineWhitespaceOnly(lineStr)) return null

        // Only expand if line has fewer spaces than autoIndentSpaces.
        // If it already has >= autoIndentSpaces (e.g. manual spaces on blank line), preserve them.
        if (lineStr.length < autoIndentSpaces) {
            return " ".repeat(autoIndentSpaces)
        }
        return null
    }

    /**
     * Non-destructive Document Transformation when the user adjusts the typography indent setting.
     *
     * Invariants:
     * - The first N spaces represent the automatic indentation prefix.
     * - Any additional spaces are manual spaces and MUST be preserved.
     * - If a line had fewer than oldIndent spaces (legacy/inconsistent content), do NOT sacrifice manual spaces.
     * - Non-prose lines (headings, code blocks, scene breaks, quotes, tables) are preserved as-is.
     * - Tab characters ('\t') are preserved as-is.
     * - Empty lines in auto-indent mode receive the new auto-indent floor.
     */
    fun transformDocument(
        originalText: String,
        oldIndent: Int,
        newIndent: Int
    ): String {
        if (originalText.isEmpty()) return ""
        val newIndentStr = if (newIndent > 0) " ".repeat(newIndent) else ""
        val isCrlf = originalText.contains("\r\n")
        val newline = if (isCrlf) "\r\n" else "\n"
        val lines = originalText.split("\n")
        val sb = StringBuilder(originalText.length + lines.size * (newIndent + 2))

        var inCodeBlock = false
        for (i in lines.indices) {
            val rawLine = lines[i]
            val line = if (rawLine.endsWith("\r")) rawLine.dropLast(1) else rawLine
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                sb.append(line)
            } else if (inCodeBlock || line.startsWith("\t") || isNonProseLine(trimmed)) {
                // Preserve non-prose and tab-indented lines untouched
                sb.append(line)
            } else if (trimmed.isEmpty()) {
                // Empty / whitespace-only line:
                if (newIndent > 0) {
                    sb.append(newIndentStr)
                } else {
                    sb.append("")
                }
            } else {
                // Prose line with content:
                val leadingSpaces = countLeadingSpaces(line)
                // Legitimate old auto-prefix detection:
                val legitimateOldAuto = if (oldIndent > 0 && leadingSpaces >= oldIndent) oldIndent else 0
                val manualSpaces = leadingSpaces - legitimateOldAuto
                val proseContent = line.substring(leadingSpaces)
                val newLeading = newIndentStr + " ".repeat(manualSpaces)
                sb.append(newLeading).append(proseContent)
            }

            if (i < lines.size - 1) {
                sb.append(newline)
            }
        }

        return sb.toString()
    }

    /**
     * Calculates the new cursor column after a document indent transformation.
     */
    fun calculateAdjustedCol(
        originalText: String,
        lineIndex: Int,
        originalCol: Int,
        oldIndent: Int,
        newIndent: Int
    ): Int {
        val lines = originalText.split("\n")
        if (lineIndex !in lines.indices) return originalCol

        var inCodeBlock = false
        for (i in 0 until lineIndex) {
            val l = lines[i].trimEnd('\r').trimStart()
            if (l.startsWith("```")) inCodeBlock = !inCodeBlock
        }

        val rawLine = lines[lineIndex]
        val line = if (rawLine.endsWith("\r")) rawLine.dropLast(1) else rawLine
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```")) inCodeBlock = !inCodeBlock

        if (inCodeBlock || line.startsWith("\t") || isNonProseLine(trimmed)) {
            return originalCol
        }

        if (trimmed.isEmpty()) {
            return if (newIndent > 0) newIndent else 0
        }

        val leadingSpaces = countLeadingSpaces(line)
        val legitimateOldAuto = if (oldIndent > 0 && leadingSpaces >= oldIndent) oldIndent else 0

        return if (originalCol <= legitimateOldAuto) {
            if (newIndent > 0) newIndent else 0
        } else {
            val offsetAfterAuto = originalCol - legitimateOldAuto
            val target = (if (newIndent > 0) newIndent else 0) + offsetAfterAuto
            target.coerceAtLeast(if (newIndent > 0) newIndent else 0)
        }
    }

    /**
     * Formats pasted text to respect the active auto-indent floor while preserving
     * markdown structures and manual formatting.
     */
    fun formatPastedText(
        rawText: CharSequence,
        autoIndentSpaces: Int,
        lineStr: String,
        col: Int
    ): String {
        if (autoIndentSpaces <= 0) return rawText.toString()
        val indent = " ".repeat(autoIndentSpaces)
        val isCursorAtStart = col == 0 || (col <= autoIndentSpaces && isLineWhitespaceOnly(lineStr))
        val lineAlreadyHasIndent = lineStr.startsWith(indent) && col >= indent.length

        val splitLines = rawText.toString().lines()
        val result = StringBuilder()

        for (i in splitLines.indices) {
            val rawLine = splitLines[i]
            val trimmed = rawLine.trimStart()
            val isMarkdownNonProse = isNonProseLine(trimmed)

            val indentedLine = when {
                isMarkdownNonProse || rawLine.startsWith("\t") -> rawLine
                trimmed.isEmpty() -> indent
                else -> {
                    val leadingSpaces = countLeadingSpaces(rawLine)
                    val content = rawLine.substring(leadingSpaces)
                    // If pasted text had existing spaces, preserve manual spaces beyond 4:
                    val manualSpaces = (leadingSpaces - autoIndentSpaces).coerceAtLeast(0)
                    indent + " ".repeat(manualSpaces) + content
                }
            }

            if (i == 0) {
                if (lineAlreadyHasIndent) {
                    result.append(trimmed)
                } else if (!isCursorAtStart && !isLineWhitespaceOnly(lineStr)) {
                    result.append(rawLine)
                } else {
                    result.append(indentedLine)
                }
            } else {
                result.append("\n").append(indentedLine)
            }
        }
        return result.toString()
    }
}
