package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.line.LineTokenizeResult
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Custom Sora [EmptyLanguage] subclass for rich novel and prose writing.
 *
 * Implements incremental tokenization and styling for:
 * 1. Spoken Dialogue: Text inside "...", “...”, «...» highlighted in distinct dialogue color ([EditorColorScheme.LITERAL]).
 * 2. Thoughts / Internal Monologue: Text inside single quotes or asterisks '*thought*' in italics and subtle thought color ([EditorColorScheme.COMMENT]).
 * 3. Markdown Formatting:
 *    - Headings (#, ##, ###, ####, etc.) rendered in bold accent ([EditorColorScheme.KEYWORD]).
 *    - Bold formatting **bold** rendered in bold style ([TextStyle.makeStyle(..., bold = true)]).
 *    - Italic emphasis _italic_ rendered in italic style ([TextStyle.makeStyle(..., italic = true)]).
 * 4. Smart bracket and typographical quotation pairing via [SymbolPairMatch].
 */
class ScribeProseLanguage : EmptyLanguage() {

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

    private val analyzeManager = object : AsyncIncrementalAnalyzeManager<ProseState, Any>() {
        override fun tokenizeLine(line: CharSequence, state: ProseState?, lineIndex: Int): LineTokenizeResult<ProseState, Any> {
            val spans = mutableListOf<Span>()
            var inDialogue = state?.inDialogue ?: false
            var inThoughtQuote = state?.inThoughtQuote ?: false
            val len = line.length

            if (len == 0) {
                spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                return LineTokenizeResult(ProseState(inDialogue, inThoughtQuote), spans)
            }

            val trimmed = line.trimStart()
            val isHeading = trimmed.startsWith("#") && (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") || trimmed.startsWith("#### ") || trimmed.startsWith("##### ") || trimmed.startsWith("###### ") || trimmed == "###")
            val isSceneBreak = trimmed == "***" || trimmed == "---" || trimmed == "* * *" || trimmed == "###"

            if (isHeading || isSceneBreak) {
                // Style entire line with Keyword / Accent styling and bold
                spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)))
                return LineTokenizeResult(ProseState(inDialogue = false, inThoughtQuote = false), spans)
            }

            var i = 0
            var lastSpanStyle = -1L

            fun addSpan(col: Int, style: Long) {
                if (style != lastSpanStyle) {
                    spans.add(Span.obtain(col, style))
                    lastSpanStyle = style
                }
            }

            // Default initial style based on carry-over state
            val initialStyle = when {
                inDialogue -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                inThoughtQuote -> TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                else -> TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
            }
            addSpan(0, initialStyle)

            while (i < len) {
                val c = line[i]

                // 1. Spoken Dialogue matching: "...", “...”, «...»
                if (c == '"' || c == '\u201C' || c == '\u201D' || c == '\u00AB' || c == '\u00BB') {
                    if (c == '"' || c == '\u00AB' || c == '\u00BB') {
                        inDialogue = !inDialogue
                    } else if (c == '\u201C') {
                        inDialogue = true
                    } else if (c == '\u201D') {
                        inDialogue = false
                    }

                    if (inDialogue) {
                        // Open quote + dialogue content
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                    } else {
                        // Close quote included in dialogue style, next character normal
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        if (i + 1 < len) {
                            addSpan(i + 1, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                    }
                    i++
                    continue
                }

                // 2. Bold markdown: **bold text**
                if (c == '*' && i + 1 < len && line[i + 1] == '*' && !inDialogue) {
                    val closingIndex = line.indexOf("**", i + 2)
                    if (closingIndex != -1) {
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, true, false, false))
                        i = closingIndex + 2
                        if (i < len) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                        continue
                    }
                }

                // 3. Asterisk Italic / Internal Monologue: *thought*
                if (c == '*' && !inDialogue) {
                    val closingIndex = line.indexOf('*', i + 1)
                    if (closingIndex != -1 && closingIndex > i + 1) {
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false))
                        i = closingIndex + 1
                        if (i < len) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                        continue
                    }
                }

                // 4. Underscore Italic: _emphasis_
                if (c == '_' && !inDialogue) {
                    val closingIndex = line.indexOf('_', i + 1)
                    if (closingIndex != -1 && closingIndex > i + 1) {
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, false, true, false))
                        i = closingIndex + 1
                        if (i < len) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                        continue
                    }
                }

                // 5. Typographical single quote monologue: ‘thought’
                if (c == '\u2018' && !inDialogue) {
                    val closingIndex = line.indexOf('\u2019', i + 1)
                    if (closingIndex != -1) {
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false))
                        i = closingIndex + 1
                        if (i < len) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        }
                        continue
                    }
                }

                i++
            }

            return LineTokenizeResult(ProseState(inDialogue = inDialogue, inThoughtQuote = inThoughtQuote), spans)
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager {
        return analyzeManager
    }

    data class ProseState(
        val inDialogue: Boolean = false,
        val inThoughtQuote: Boolean = false
    )
}
