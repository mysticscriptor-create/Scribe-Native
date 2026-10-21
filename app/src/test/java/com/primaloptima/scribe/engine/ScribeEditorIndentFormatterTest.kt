package com.primaloptima.scribe.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rigorous test suite for [ScribeEditorIndentFormatter].
 *
 * Validates:
 * 1. Single-newline invariant on Enter (strictly 1 newline, never double newline \n\n).
 * 2. Automatic indentation on Enter at start, middle, and end of text.
 * 3. Moving cursor to an empty line before typing (automatic floor calculation).
 * 4. Automatic indentation combined with additional manual spaces.
 * 5. Disabling (N -> 0) or changing (N -> M) indentation without losing manual spaces.
 * 6. Manual Backspace deleting spaces one by one.
 * 7. Boundary Backspace merging lines without text destruction or line-0 data loss.
 * 8. Consecutive Enter presses and blank lines (retaining auto floor as Col 0).
 * 9. Handling of tabs ('\t'), Markdown non-prose elements (#, ---, >, ```), and pasted text.
 */
class ScribeEditorIndentFormatterTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Enter Key & Newline Calculations
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testNewline_atEndOfIndentedText() {
        val lineStr = "    Once upon a time"
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 0,
            col = lineStr.length,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        assertEquals("\n    ", insert.textToInsert)
        assertEquals(lineStr.length, insert.insertCol)
        assertEquals(1, insert.targetLine)
        assertEquals(4, insert.targetCol)
    }

    @Test
    fun testNewline_inMiddleOfIndentedText() {
        val lineStr = "    Hello World"
        val col = 9 // right between "Hello" and " World"
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 2,
            col = col,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        assertEquals("\n    ", insert.textToInsert)
        assertEquals(9, insert.insertCol)
        assertEquals(3, insert.targetLine)
        assertEquals(4, insert.targetCol)
    }

    @Test
    fun testNewline_atStartOfIndentedText() {
        val lineStr = "    Hello"
        val col = 4 // cursor right in front of "H"
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 0,
            col = col,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        assertEquals("\n    ", insert.textToInsert)
        assertEquals(4, insert.insertCol)
        assertEquals(1, insert.targetLine)
        assertEquals(4, insert.targetCol)
    }

    @Test
    fun testNewline_withManualSpacesBeyondAutoIndent() {
        // 4 auto spaces + 2 manual spaces = 6 leading spaces
        val lineStr = "      Indented item"
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 1,
            col = lineStr.length,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        // Invariant: Carries 4 auto spaces + 2 manual spaces = 6 spaces!
        assertEquals("\n      ", insert.textToInsert)
        assertEquals(6, insert.targetCol)
    }

    @Test
    fun testNewline_onEmptyLineWithAutoIndent() {
        // User presses Enter on an empty line (or line with spaces) for scene break
        val lineStr = "    "
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 3,
            col = 4,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.ReplaceAndInsertNewline)
        val replace = action as FormatterNewlineAction.ReplaceAndInsertNewline
        assertEquals("    ", replace.normalizedLine)
        assertEquals("\n    ", replace.textToInsert)
        assertEquals(4, replace.targetLine)
        assertEquals(4, replace.targetCol)
    }

    @Test
    fun testNewline_inManualMode() {
        // Auto indent OFF (0)
        val lineStr = "  Manual line"
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 0,
            col = lineStr.length,
            autoIndentSpaces = 0
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        assertEquals("\n  ", insert.textToInsert)
        assertEquals(2, insert.targetCol)

        // Empty line in manual mode clears spaces and drops to col 0
        val emptyAction = ScribeEditorIndentFormatter.computeNewline(
            lineStr = "  ",
            line = 1,
            col = 2,
            autoIndentSpaces = 0
        )
        assertTrue(emptyAction is FormatterNewlineAction.ReplaceAndInsertNewline)
        val rep = emptyAction as FormatterNewlineAction.ReplaceAndInsertNewline
        assertEquals("", rep.normalizedLine)
        assertEquals("\n", rep.textToInsert)
        assertEquals(0, rep.targetCol)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Empty-Line Floor Enforcer (No Teleporting Text)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testEmptyLineFloorEnforcer() {
        // Completely empty line needs 4 spaces
        assertEquals("    ", ScribeEditorIndentFormatter.computeEmptyLineFloor("", 4))

        // Line with only 2 spaces needs expansion to 4 spaces
        assertEquals("    ", ScribeEditorIndentFormatter.computeEmptyLineFloor("  ", 4))

        // Line already at 4 spaces: null (no modification needed)
        assertEquals(null, ScribeEditorIndentFormatter.computeEmptyLineFloor("    ", 4))

        // Blank line with 6 spaces (4 auto + 2 manual): null (preserve manual spaces!)
        assertEquals(null, ScribeEditorIndentFormatter.computeEmptyLineFloor("      ", 4))

        // Line with text: null (never alter text lines during cursor move)
        assertEquals(null, ScribeEditorIndentFormatter.computeEmptyLineFloor("Hello", 4))

        // Auto indent OFF: null
        assertEquals(null, ScribeEditorIndentFormatter.computeEmptyLineFloor("", 0))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Backspace & Deletion Calculations
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testDelete_manualSpaceDeletesOneByOne() {
        // 4 auto spaces + 2 manual spaces = 6 leading spaces. Cursor at col 6.
        val lineStr = "      Text"
        val action = ScribeEditorIndentFormatter.computeDelete(
            lineStr = lineStr,
            line = 1,
            col = 6,
            autoIndentSpaces = 4,
            prevLineLength = 20
        )
        // Must delegate to standard delete to remove only 1 space!
        assertEquals(FormatterDeleteAction.DelegateToStandardDelete, action)
    }

    @Test
    fun testDelete_atAutoIndentBoundary_mergesWithPreviousLine() {
        val lineStr = "    Second paragraph"
        val action = ScribeEditorIndentFormatter.computeDelete(
            lineStr = lineStr,
            line = 1,
            col = 4,
            autoIndentSpaces = 4,
            prevLineLength = 25
        )
        assertTrue(action is FormatterDeleteAction.MergeWithPreviousLine)
        val merge = action as FormatterDeleteAction.MergeWithPreviousLine
        assertEquals(0, merge.prevLine)
        assertEquals(25, merge.prevCol)
        assertEquals(1, merge.currentLine)
        assertEquals(4, merge.deleteToCol) // Strips this line's auto prefix and merges text
    }

    @Test
    fun testDelete_onLineZero_neverDestroysText() {
        val lineStr = "    First paragraph of book"
        val action = ScribeEditorIndentFormatter.computeDelete(
            lineStr = lineStr,
            line = 0,
            col = 4,
            autoIndentSpaces = 4,
            prevLineLength = 0
        )
        // Invariant: ZERO data loss! Clamps cursor to floor without buffer mutation.
        assertTrue(action is FormatterDeleteAction.ClampCursorLineZero)
        val clamp = action as FormatterDeleteAction.ClampCursorLineZero
        assertEquals(4, clamp.targetCol)
    }

    @Test
    fun testDelete_onEmptyLine_mergesWithPreviousLine() {
        val lineStr = "    "
        val action = ScribeEditorIndentFormatter.computeDelete(
            lineStr = lineStr,
            line = 2,
            col = 4,
            autoIndentSpaces = 4,
            prevLineLength = 18
        )
        assertTrue(action is FormatterDeleteAction.MergeWithPreviousLine)
        val merge = action as FormatterDeleteAction.MergeWithPreviousLine
        assertEquals(1, merge.prevLine)
        assertEquals(18, merge.prevCol)
        assertEquals(4, merge.deleteToCol)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Document Transformation & Invariants (Slider Changes)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testDocumentTransform_turnOff_preservesManualSpaces() {
        // Document has line 1 with 4 auto spaces, and line 2 with 4 auto + 2 manual spaces
        val doc = "    Line 1\n      Line 2 with manual indent"
        val result = ScribeEditorIndentFormatter.transformDocument(doc, oldIndent = 4, newIndent = 0)
        val expected = "Line 1\n  Line 2 with manual indent"
        assertEquals(expected, result)
    }

    @Test
    fun testDocumentTransform_turnOn_addsAutoIndentAndPreservesManual() {
        val doc = "Line 1\n  Line 2 manual"
        val result = ScribeEditorIndentFormatter.transformDocument(doc, oldIndent = 0, newIndent = 4)
        val expected = "    Line 1\n      Line 2 manual"
        assertEquals(expected, result)
    }

    @Test
    fun testDocumentTransform_changeIndent_preservesManualSpaces() {
        // 4 -> 2 spaces
        val doc = "    Line 1\n      Line 2"
        val result = ScribeEditorIndentFormatter.transformDocument(doc, oldIndent = 4, newIndent = 2)
        val expected = "  Line 1\n    Line 2"
        assertEquals(expected, result)
    }

    @Test
    fun testDocumentTransform_preservesMarkdownNonProseAndTabs() {
        val doc = "# Title\n---\n> Quote\n```\ncode\n```\n\tTabbed\n    Prose"
        val result = ScribeEditorIndentFormatter.transformDocument(doc, oldIndent = 4, newIndent = 2)
        val expected = "# Title\n---\n> Quote\n```\ncode\n```\n\tTabbed\n  Prose"
        assertEquals(expected, result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Cursor Column Adjustment Calculation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testCursorAdjustment() {
        val doc = "    Hello World"
        // Cursor at col 4 (start of text) when changing 4 -> 2
        val colAtStart = ScribeEditorIndentFormatter.calculateAdjustedCol(doc, 0, 4, oldIndent = 4, newIndent = 2)
        assertEquals(2, colAtStart)

        // Cursor at col 10 (inside "World") when changing 4 -> 2
        val colInText = ScribeEditorIndentFormatter.calculateAdjustedCol(doc, 0, 10, oldIndent = 4, newIndent = 2)
        assertEquals(8, colInText)

        // Turning off 4 -> 0
        val colTurnOff = ScribeEditorIndentFormatter.calculateAdjustedCol(doc, 0, 10, oldIndent = 4, newIndent = 0)
        assertEquals(6, colTurnOff)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Pasted Text Formatting
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun testFormatPastedText() {
        val pasted = "First paragraph\nSecond paragraph\n# Heading"
        val formatted = ScribeEditorIndentFormatter.formatPastedText(
            rawText = pasted,
            autoIndentSpaces = 4,
            lineStr = "",
            col = 0
        )
        val expected = "    First paragraph\n    Second paragraph\n# Heading"
        assertEquals(expected, formatted)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. Mid-Sentence Splitting & Smart List Continuation Invariants
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun testNewline_inMiddleOfManualIndent_splitsSentenceCorrectly() {
        val lineStr = "    The quick brown fox jumps"
        val col = lineStr.indexOf("fox") // Exactly right before "fox" (col = 20)
        val action = ScribeEditorIndentFormatter.computeNewline(
            lineStr = lineStr,
            line = 0,
            col = col,
            autoIndentSpaces = 4
        )
        assertTrue(action is FormatterNewlineAction.InsertNewline)
        val insert = action as FormatterNewlineAction.InsertNewline
        assertEquals("\n    ", insert.textToInsert)
        assertEquals(col, insert.insertCol)
        assertEquals(1, insert.targetLine)
        assertEquals(4, insert.targetCol)

        // Verify simulated line splitting:
        val firstPart = lineStr.substring(0, insert.insertCol)
        val secondPart = insert.textToInsert.trimStart('\n') + lineStr.substring(insert.insertCol)
        assertEquals("    The quick brown ", firstPart)
        assertEquals("    fox jumps", secondPart)
    }

    @Test
    fun testNewline_inMiddleOfSmartNumericList_splitsSentenceAndIncrementsPrefix() {
        val lineStr = "1. The quick brown fox jumps"
        val listMatch = ScribeListEngine.parseListLine(lineStr)
        assertTrue(listMatch != null)
        val match = listMatch!!
        assertTrue(match.isOrdered)
        assertEquals(1L, match.orderNumber)
        assertEquals("1. ", match.fullPrefix)

        val nextPrefix = ScribeListEngine.nextSequencePrefix(match)
        assertEquals("2. ", nextPrefix)

        val col = lineStr.indexOf("fox") // Exactly right before "fox" (col = 19)
        assertTrue(col >= match.fullPrefix.length)

        // Simulating the insertion at col:
        val firstPart = lineStr.substring(0, col)
        val secondPart = nextPrefix + lineStr.substring(col)
        assertEquals("1. The quick brown ", firstPart)
        assertEquals("2. fox jumps", secondPart)
        assertEquals(3, nextPrefix.length) // Target col on next line
    }
}
