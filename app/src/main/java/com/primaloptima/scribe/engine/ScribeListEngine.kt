package com.primaloptima.scribe.engine

/**
 * High-performance list detection and continuation engine for Scribe.
 * Supports:
 * - Unordered bullet lists: `- `, `* `, `+ `, `• `
 * - Ordered numeric lists: `1. `, `1) `, `42. `, `99) `
 * - Task / Checklists: `- [ ] `, `- [x] `, `* [ ] `, `+ [ ] `
 * - Blockquotes: `> `
 *
 * Handles:
 * - Sequence incrementation (e.g. 1. -> 2., 9. -> 10.)
 * - Sub-list indentation preservation (e.g. "   - " -> "   - ")
 * - List termination on empty marker (double Enter)
 * - Single-stroke marker deletion on Backspace
 */
object ScribeListEngine {

    data class ListMatch(
        val leadingWhitespace: String,
        val marker: String,
        val fullPrefix: String,
        val isOrdered: Boolean = false,
        val orderNumber: Long = 0L,
        val orderDelimiter: Char = '.',
        val isTask: Boolean = false,
        val isBlockquote: Boolean = false
    )

    private val TASK_REGEX = Regex("""^([ \t]*)([-*+]\s+\[[ xX]\]\s+)""")
    private val ORDERED_REGEX = Regex("""^([ \t]*)(\d+)([.)]\s+)""")
    private val UNORDERED_REGEX = Regex("""^([ \t]*)([-*+•]\s+)""")
    private val BLOCKQUOTE_REGEX = Regex("""^([ \t]*)(>\s*)""")

    /**
     * Inspects line text and determines if it starts with an active list marker or quote.
     */
    fun parseListLine(lineStr: String): ListMatch? {
        if (lineStr.isEmpty()) return null

        // 1. Task lists: e.g. "- [ ] ", "* [x] "
        TASK_REGEX.find(lineStr)?.let { match ->
            val ws = match.groupValues[1]
            val marker = match.groupValues[2]
            return ListMatch(
                leadingWhitespace = ws,
                marker = marker,
                fullPrefix = ws + marker,
                isTask = true
            )
        }

        // 2. Ordered lists: e.g. "1. ", "12) "
        ORDERED_REGEX.find(lineStr)?.let { match ->
            val ws = match.groupValues[1]
            val numStr = match.groupValues[2]
            val delimWithSpace = match.groupValues[3]
            val num = numStr.toLongOrNull() ?: 1L
            val delim = delimWithSpace.firstOrNull() ?: '.'
            val marker = "$numStr$delimWithSpace"
            return ListMatch(
                leadingWhitespace = ws,
                marker = marker,
                fullPrefix = ws + marker,
                isOrdered = true,
                orderNumber = num,
                orderDelimiter = delim
            )
        }

        // 3. Unordered lists: e.g. "- ", "* ", "+ ", "• "
        UNORDERED_REGEX.find(lineStr)?.let { match ->
            val ws = match.groupValues[1]
            val marker = match.groupValues[2]
            return ListMatch(
                leadingWhitespace = ws,
                marker = marker,
                fullPrefix = ws + marker
            )
        }

        // 4. Blockquotes: e.g. "> ", ">"
        BLOCKQUOTE_REGEX.find(lineStr)?.let { match ->
            val ws = match.groupValues[1]
            val marker = match.groupValues[2]
            return ListMatch(
                leadingWhitespace = ws,
                marker = marker,
                fullPrefix = ws + marker,
                isBlockquote = true
            )
        }

        return null
    }

    /**
     * Given an existing match, generates the next sequence prefix.
     */
    fun nextSequencePrefix(match: ListMatch): String {
        return when {
            match.isOrdered -> {
                val nextNum = match.orderNumber + 1
                "${match.leadingWhitespace}$nextNum${match.orderDelimiter} "
            }
            match.isTask -> {
                // Next item in task list is always an unchecked box
                "${match.leadingWhitespace}- [ ] "
            }
            match.isBlockquote -> {
                "${match.leadingWhitespace}> "
            }
            else -> {
                // Unordered bullet
                "${match.leadingWhitespace}${match.marker}"
            }
        }
    }
}
