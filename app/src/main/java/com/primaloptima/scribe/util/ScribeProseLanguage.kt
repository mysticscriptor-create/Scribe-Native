package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom Sora [EmptyLanguage] subclass for rich novel and prose writing.
 *
 * Implements incremental tokenization and styling for:
 * 1. Spoken Dialogue: Text inside "...", "...", «...» highlighted in distinct dialogue color.
 * 2. Thoughts / Internal Monologue: Text inside single quotes or asterisks '*thought*' in italics.
 * 3. Markdown Formatting:
 *    - Headings (#, ##, ###, ####, etc.) rendered in bold accent.
 *    - Bold formatting **bold** rendered in bold style.
 *    - Italic emphasis _italic_ rendered in italic style.
 * 4. Smart bracket and typographical quotation pairing via [SymbolPairMatch].
 *
 * NOTE: LineTokenizeResult lives inside AsyncIncrementalAnalyzeManager as a nested class.
 * The type alias below makes the code readable without the long qualified name.
 */
class ScribeProseLanguage : EmptyLanguage() {

    // Type alias for the nested result class — avoids long qualified names throughout
    private typealias TokenResult = AsyncIncrementalAnalyzeManager.LineTokenizeResult<ProseState, List<Span>>

    private val pairs = SymbolPairMatch().apply {
        putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        putPair('`', SymbolPairMatch.SymbolPair("`", "`"))
        putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        putPair('\u201C', SymbolPairMatch.SymbolPair("\u201C", "\u201D"))  // " "
        putPair('\u2018', SymbolPairMatch.SymbolPair("\u2018", "\u2019"))  // ' '
        putPair('\u00AB', SymbolPairMatch.SymbolPair("\u00AB", "\u00BB"))  // « »
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    private val analyzeManager =
        object : AsyncIncrementalAnalyzeManager<ProseState, List<Span>>() {

            // ── Required abstract overrides ──────────────────────────────────

            override fun getInitialState(): ProseState = ProseState()

            override fun stateEquals(state: ProseState?, another: ProseState?): Boolean {
                if (state === another) return true
                if (state == null || another == null) return false
                return state == another
            }

            /**
             * Tokenizes one line of prose text into spans.
             * Returns a [LineTokenizeResult] with the updated carry-over state and the
             * list of styled [Span] objects — passed directly as the token payload so
             * [generateSpansForLine] can return them unchanged.
             */
            override fun tokenizeLine(
                line: CharSequence,
                state: ProseState?,
                lineIndex: Int
            ): AsyncIncrementalAnalyzeManager.LineTokenizeResult<ProseState, List<Span>> {
                val spans = mutableListOf<Span>()
                var inDialogue = state?.inDialogue ?: false
                var inThoughtQuote = state?.inThoughtQuote ?: false
                val len = line.length

                if (len == 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                    return AsyncIncrementalAnalyzeManager.LineTokenizeResult(
                        ProseState(inDialogue, inThoughtQuote), spans
                    )
                }

                val trimmed = line.trimStart()
                val isHeading = trimmed.startsWith("#") &&
                        (trimmed.startsWith("# ") || trimmed.startsWith("## ") ||
                                trimmed.startsWith("### ") || trimmed.startsWith("#### ") ||
                                trimmed.startsWith("##### ") || trimmed.startsWith("###### ") ||
                                trimmed == "###")
                val isSceneBreak = trimmed == "***" || trimmed == "---" ||
                        trimmed == "* * *" || trimmed == "###"

                if (isHeading || isSceneBreak) {
                    spans.add(
                        Span.obtain(
                            0,
                            TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                        )
                    )
                    return AsyncIncrementalAnalyzeManager.LineTokenizeResult(
                        ProseState(inDialogue = false, inThoughtQuote = false), spans
                    )
                }

                var i = 0
                var lastSpanStyle = -1L

                fun addSpan(col: Int, style: Long) {
                    if (style != lastSpanStyle) {
                        spans.add(Span.obtain(col, style))
                        lastSpanStyle = style
                    }
                }

                val initialStyle = when {
                    inDialogue -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                    inThoughtQuote ->
                        TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                    else -> TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                }
                addSpan(0, initialStyle)

                while (i < len) {
                    val c = line[i]

                    // 1. Spoken dialogue: "...", "...", «...»
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

                    // 2. Bold markdown: **bold**
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

                    // 4. Underscore Italic: _emphasis_
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

                    // 5. Typographical single-quote monologue: 'thought'
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

                return AsyncIncrementalAnalyzeManager.LineTokenizeResult(
                    ProseState(inDialogue = inDialogue, inThoughtQuote = inThoughtQuote),
                    spans
                )
            }

            /**
             * Our token payload IS already a [List<Span>], so we return it directly.
             * This is the correct pattern for the 0.24.x API where you can use
             * the token list as the T type parameter and skip a second conversion step.
             */
            override fun generateSpansForLine(
                lineResult: AsyncIncrementalAnalyzeManager.LineTokenizeResult<ProseState, List<Span>>
            ): MutableList<Span> {
                // tokens holds the List<Span> we built in tokenizeLine — just return it
                return (lineResult.tokens ?: emptyList<Span>()).toMutableList()
            }

            /**
             * Prose has no code-block structure (no braces, brackets to fold).
             * Return an empty list — the delegate is never used.
             */
            override fun computeBlocks(
                text: Content,
                delegate: AsyncIncrementalAnalyzeManager.CodeBlockAnalyzeDelegate<ProseState, List<Span>>
            ): MutableList<CodeBlock> {
                return mutableListOf()
            }
        }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    data class ProseState(
        val inDialogue: Boolean = false,
        val inThoughtQuote: Boolean = false
    )
}
