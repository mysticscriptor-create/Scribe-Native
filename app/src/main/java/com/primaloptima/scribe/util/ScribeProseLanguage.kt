package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * ── Sora API facts (verified against source) ──────────────────────────────────
 *
 * LineTokenizeResult<S, T>:
 *   Nested INSIDE IncrementalAnalyzeManager (the interface), not inside
 *   AsyncIncrementalAnalyzeManager.
 *   Full name: IncrementalAnalyzeManager.LineTokenizeResult<S, T>
 *   Fields: state: S,  tokens: List<T>?,  spans: List<Span>?
 *
 * generateSpansForLine signature (from IncrementalAnalyzeManager interface):
 *   fun generateSpansForLine(tokens: LineTokenizeResult<S, T>): List<Span>
 *   ← parameter name is "tokens" (the result object), return type is List<Span>.
 *
 * computeBlocks signature (from AsyncIncrementalAnalyzeManager abstract method):
 *   abstract fun computeBlocks(text: Content, delegate: CodeBlockAnalyzeDelegate): List<CodeBlock>
 *   ← CodeBlockAnalyzeDelegate is an INNER class of AsyncIncrementalAnalyzeManager.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Custom Sora language for rich novel and prose writing.
 *
 * Incremental tokenization features:
 *   1. Spoken Dialogue — text inside "...", “...”, «...» shown in dialogue color.
 *   2. Thoughts / Internal Monologue — text in *asterisks* or ‘typographic single quotes’
 *      shown in comment/italic color.
 *   3. Markdown Formatting:
 *      - Lines starting with "#" → bold heading color.
 *      - **bold** → bold text style.
 *      - _italic_ → italic text style.
 *   4. Scene-break lines ("***", "---", "* * *", "###") → bold accent color.
 *   5. Smart bracket and quotation pairing via SymbolPairMatch.
 */
class ScribeProseLanguage : EmptyLanguage() {

    // ── Symbol pairing ────────────────────────────────────────────────────────────────

    private val pairs = SymbolPairMatch().apply {
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        putPair('\u201C', SymbolPairMatch.SymbolPair("\u201C", "\u201D"))  // “ ”
        putPair('\u2018', SymbolPairMatch.SymbolPair("\u2018", "\u2019"))  // ‘ ’
        putPair('\u00AB', SymbolPairMatch.SymbolPair("\u00AB", "\u00BB"))  // « »
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    // ── Analyze manager ───────────────────────────────────────────────────────────────

    private val analyzeManager =
        object : AsyncIncrementalAnalyzeManager<ProseState, Span>() {

            // ── Required abstract overrides ───────────────────────────────────────────

            override fun getInitialState(): ProseState = ProseState()

            override fun stateEquals(state: ProseState?, another: ProseState?): Boolean {
                if (state === another) return true
                if (state == null || another == null) return false
                return state == another
            }

            override fun onAbandonState(state: ProseState) { /* nothing to release */ }

            override fun onAddState(state: ProseState) { /* nothing to retain */ }

            // ── Core tokenizer ────────────────────────────────────────────────────────

            override fun tokenizeLine(
                line: CharSequence,
                state: ProseState?,
                lineIndex: Int
            ): IncrementalAnalyzeManager.LineTokenizeResult<ProseState, Span> {

                val spans = ArrayList<Span>()
                var inDialogue = state?.inDialogue ?: false
                var inThoughtQuote = state?.inThoughtQuote ?: false
                val len = line.length

                // Empty line — just carry state forward
                if (len == 0) {
                    spans.add(SpanFactory.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                    return IncrementalAnalyzeManager.LineTokenizeResult(
                        ProseState(inDialogue, inThoughtQuote), spans, spans
                    )
                }

                val trimmed = line.trimStart()

                // Scene headings and scene-break markers get bold/accent treatment
                val isHeading = trimmed.startsWith("#") && (
                        trimmed.startsWith("# ") || trimmed.startsWith("## ") ||
                        trimmed.startsWith("### ") || trimmed.startsWith("#### ") ||
                        trimmed.startsWith("##### ") || trimmed.startsWith("###### ") ||
                        trimmed == "###"
                )
                val isSceneBreak = trimmed == "***" || trimmed == "---" ||
                        trimmed == "* * *" || trimmed == "###"

                if (isHeading || isSceneBreak) {
                    spans.add(
                        SpanFactory.obtain(
                            0,
                            TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                        )
                    )
                    // Scene breaks and headings always reset dialogue/thought carry state
                    return IncrementalAnalyzeManager.LineTokenizeResult(
                        ProseState(inDialogue = false, inThoughtQuote = false), spans, spans
                    )
                }

                // ── Character-by-character span generation ────────────────────────────

                var i = 0
                var lastSpanStyle = -1L

                fun addSpan(col: Int, style: Long) {
                    if (style != lastSpanStyle) {
                        spans.add(SpanFactory.obtain(col, style))
                        lastSpanStyle = style
                    }
                }

                // Apply opening carry-over state
                val initialStyle = when {
                    inDialogue ->
                        TextStyle.makeStyle(EditorColorScheme.LITERAL)
                    inThoughtQuote ->
                        TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                    else ->
                        TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                }
                addSpan(0, initialStyle)

                while (i < len) {
                    val c = line[i]

                    // 1. Spoken dialogue: " “ ” « »
                    if (c == '"' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') {
                        when (c) {
                            '"', '\u00AB', '\u00BB' -> inDialogue = !inDialogue
                            '\u201C' -> inDialogue = true
                            '\u201D' -> inDialogue = false
                        }
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        if (!inDialogue && i + 1 < len) {
                            addSpan(i + 1, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                        i++
                        continue
                    }

                    // 2. Bold markdown: **bold**  (must come before single-* check)
                    if (c == '*' && i + 1 < len && line[i + 1] == '*' && !inDialogue) {
                        val closingIndex = line.indexOf("**", i + 2)
                        if (closingIndex != -1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, true, false, false)
                            )
                            i = closingIndex + 2
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 3. Italic / Internal Monologue: *thought*
                    if (c == '*' && !inDialogue) {
                        val closingIndex = line.indexOf('*', i + 1)
                        if (closingIndex != -1 && closingIndex > i + 1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 4. Underscore italic: _emphasis_
                    if (c == '_' && !inDialogue) {
                        val closingIndex = line.indexOf('_', i + 1)
                        if (closingIndex != -1 && closingIndex > i + 1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 5. Typographical single-quote monologue: ‘thought’
                    if (c == '\u2018' && !inDialogue) {
                        val closingIndex = line.indexOf('\u2019', i + 1)
                        if (closingIndex != -1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    i++
                }

                return IncrementalAnalyzeManager.LineTokenizeResult(
                    ProseState(inDialogue = inDialogue, inThoughtQuote = inThoughtQuote),
                    spans,
                    spans
                )
            }

            override fun generateSpansForLine(
                tokens: IncrementalAnalyzeManager.LineTokenizeResult<ProseState, Span>
            ): List<Span> {
                return tokens.spans ?: tokens.tokens ?: emptyList()
            }

            override fun computeBlocks(
                text: Content,
                delegate: CodeBlockAnalyzeDelegate
            ): List<CodeBlock> {
                return emptyList()
            }
        }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    // ── State ─────────────────────────────────────────────────────────────────────────

    /**
     * Carry-over state between lines.
     * [inDialogue]     — true if we are inside an open dialogue quote span.
     * [inThoughtQuote] — true if we are inside an open thought/monologue span.
     */
    data class ProseState(
        val inDialogue: Boolean = false,
        val inThoughtQuote: Boolean = false
    )
}
