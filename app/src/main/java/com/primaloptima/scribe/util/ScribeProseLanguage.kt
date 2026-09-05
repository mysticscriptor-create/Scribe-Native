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
 *   1. Spoken Dialogue — text inside "...", “...”, «...» or em-dash lines shown in canonical DIALOGUE color.
 *   2. Thoughts / Internal Monologue — text in *asterisks* or ‘typographic single quotes’
 *      shown in canonical MONOLOGUE color (italic).
 *   3. Markdown Formatting:
 *      - Lines starting with "#" → bold HEADING color.
 *      - **bold** → bold PROSE text style.
 *      - _italic_ → italic PROSE text style.
 *   4. Scene-break lines ("***", "---", "* * *", "###") → bold HEADING accent color.
 *   5. Smart bracket and quotation pairing via SymbolPairMatch.
 */
object ScribeProseTokens {
    const val PROSE     = EditorColorScheme.TEXT_NORMAL
    const val DIALOGUE  = EditorColorScheme.LITERAL
    const val MONOLOGUE = EditorColorScheme.COMMENT
    const val HEADING   = EditorColorScheme.KEYWORD
}

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
                    spans.add(SpanFactory.obtain(0, TextStyle.makeStyle(ScribeProseTokens.PROSE)))
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
                            TextStyle.makeStyle(ScribeProseTokens.HEADING, 0, true, false, false)
                        )
                    )
                    // Scene breaks and headings always reset dialogue/thought carry state
                    return IncrementalAnalyzeManager.LineTokenizeResult(
                        ProseState(inDialogue = false, inThoughtQuote = false), spans, spans
                    )
                }

                // Em-dash dialogue line (European / literary convention): "— Line...", "– Line...", "-- Line..."
                val isEmDashDialogue = !inDialogue && !inThoughtQuote && (
                        trimmed.startsWith("— ") || trimmed.startsWith("– ") || trimmed.startsWith("-- ")
                )
                if (isEmDashDialogue) {
                    val emDashOffset = line.indexOf(trimmed[0])
                    if (emDashOffset > 0) {
                        spans.add(SpanFactory.obtain(0, TextStyle.makeStyle(ScribeProseTokens.PROSE)))
                    }
                    spans.add(SpanFactory.obtain(emDashOffset, TextStyle.makeStyle(ScribeProseTokens.DIALOGUE)))
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
                        TextStyle.makeStyle(ScribeProseTokens.DIALOGUE)
                    inThoughtQuote ->
                        TextStyle.makeStyle(ScribeProseTokens.MONOLOGUE, 0, false, true, false)
                    else ->
                        TextStyle.makeStyle(ScribeProseTokens.PROSE)
                }
                addSpan(0, initialStyle)

                while (i < len) {
                    val c = line[i]

                    // 1. Spoken dialogue quotes: " “ ” « »
                    if (c == '"' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') {
                        when (c) {
                            '"', '\u00AB', '\u00BB' -> inDialogue = !inDialogue
                            '\u201C' -> inDialogue = true
                            '\u201D' -> inDialogue = false
                        }
                        addSpan(i, TextStyle.makeStyle(ScribeProseTokens.DIALOGUE))
                        if (!inDialogue && i + 1 < len) {
                            addSpan(i + 1, TextStyle.makeStyle(ScribeProseTokens.PROSE))
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
                                TextStyle.makeStyle(ScribeProseTokens.PROSE, 0, true, false, false)
                            )
                            i = closingIndex + 2
                            if (i < len) addSpan(i, TextStyle.makeStyle(ScribeProseTokens.PROSE))
                            continue
                        }
                    }

                    // 3. Italic / Internal Monologue: *thought*
                    if (c == '*' && !inDialogue) {
                        val closingIndex = line.indexOf('*', i + 1)
                        if (closingIndex != -1 && closingIndex > i + 1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(ScribeProseTokens.MONOLOGUE, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(ScribeProseTokens.PROSE))
                            continue
                        }
                    }

                    // 4. Underscore italic: _emphasis_
                    if (c == '_' && !inDialogue) {
                        val closingIndex = line.indexOf('_', i + 1)
                        if (closingIndex != -1 && closingIndex > i + 1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(ScribeProseTokens.PROSE, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(ScribeProseTokens.PROSE))
                            continue
                        }
                    }

                    // 5. Typographical single-quote monologue: ‘thought’ with multiline carry
                    if (c == '\u2018' && !inDialogue) {
                        val closingIndex = line.indexOf('\u2019', i + 1)
                        if (closingIndex != -1) {
                            addSpan(
                                i,
                                TextStyle.makeStyle(ScribeProseTokens.MONOLOGUE, 0, false, true, false)
                            )
                            i = closingIndex + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(ScribeProseTokens.PROSE))
                            continue
                        } else {
                            inThoughtQuote = true
                            addSpan(
                                i,
                                TextStyle.makeStyle(ScribeProseTokens.MONOLOGUE, 0, false, true, false)
                            )
                            i++
                            continue
                        }
                    }

                    // 6. Closing single-quote for carry-over monologue
                    if (c == '\u2019' && inThoughtQuote) {
                        inThoughtQuote = false
                        addSpan(
                            i,
                            TextStyle.makeStyle(ScribeProseTokens.MONOLOGUE, 0, false, true, false)
                        )
                        if (i + 1 < len) {
                            addSpan(i + 1, TextStyle.makeStyle(ScribeProseTokens.PROSE))
                        }
                        i++
                        continue
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
