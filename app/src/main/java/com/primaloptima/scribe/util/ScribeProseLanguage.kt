package com.primaloptima.scribe.util

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
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
 * 1. Spoken Dialogue — text inside "...", "...", «...» highlighted in a distinct
 *    dialogue color ([EditorColorScheme.LITERAL]).
 * 2. Thoughts / Internal Monologue — text inside *...* or '...' rendered in
 *    italic thought color ([EditorColorScheme.COMMENT]).
 * 3. Markdown Formatting:
 *    - Headings (# ## ### etc.) and scene-break markers (*** ---) rendered bold
 *      in accent color ([EditorColorScheme.KEYWORD]).
 *    - Bold **text** rendered bold.
 *    - Italic _text_ rendered italic.
 * 4. Smart bracket and typographical quotation pairing via [SymbolPairMatch].
 *
 * State is carried across lines so that multi-line dialogue is coloured correctly.
 */
class ScribeProseLanguage : EmptyLanguage() {

    // ── Symbol pairs ──────────────────────────────────────────────────────────

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

    // ── Incremental Analyze Manager ───────────────────────────────────────────

    /**
     * Carry-over state between lines so multi-line dialogue is coloured correctly.
     */
    data class ProseState(
        val inDialogue: Boolean = false,
        val inThoughtQuote: Boolean = false
    )

    /**
     * AsyncIncrementalAnalyzeManager requires four abstract members:
     *   getInitialState()          — starting state at top of file
     *   stateEquals(a, b)          — used to short-circuit re-analysis
     *   tokenizeLine(line, state)  — returns LineTokenizeResult<S, T>
     *   generateSpansForLine(res)  — converts token list to Span list
     *   computeBlocks(content, d)  — returns code-block list (empty for prose)
     *
     * T (the token type) is Unit because we fold span generation directly into
     * tokenizeLine and return the span list as the token payload via a wrapper.
     */
    private val manager =
        object : AsyncIncrementalAnalyzeManager<ProseState, List<Span>>() {

            override fun getInitialState(): ProseState = ProseState()

            override fun stateEquals(a: ProseState?, b: ProseState?): Boolean = a == b

            /**
             * Tokenize one line. Returns a [LineTokenizeResult] whose token list
             * is a single-element list containing the pre-built [Span] list. This
             * avoids the need for a separate [generateSpansForLine] step.
             */
            override fun tokenizeLine(
                line: CharSequence,
                state: ProseState?,
                lineIndex: Int
            ): LineTokenizeResult<ProseState, List<Span>> {
                val spans = mutableListOf<Span>()
                var inDialogue    = state?.inDialogue    ?: false
                var inThoughtQuote = state?.inThoughtQuote ?: false
                val len = line.length

                if (len == 0) {
                    spans.add(Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)))
                    return LineTokenizeResult(ProseState(inDialogue, inThoughtQuote), listOf(spans))
                }

                val trimmed = line.trimStart()

                // ── Full-line treatment: headings and scene breaks ─────────────
                val isHeading = trimmed.startsWith("#") &&
                        (trimmed.length == 1 || trimmed[1] == ' ' || trimmed[1] == '#')
                val isSceneBreak = trimmed == "***" || trimmed == "---" ||
                        trimmed == "* * *" || trimmed == "- - -"

                if (isHeading || isSceneBreak) {
                    spans.add(
                        Span.obtain(
                            0,
                            TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false)
                        )
                    )
                    // Scene breaks reset dialogue carry-over
                    return LineTokenizeResult(
                        ProseState(inDialogue = false, inThoughtQuote = false),
                        listOf(spans)
                    )
                }

                // ── Character-by-character scan ────────────────────────────────
                var i = 0
                var lastStyle = -1L

                fun addSpan(col: Int, style: Long) {
                    if (style != lastStyle) {
                        spans.add(Span.obtain(col, style))
                        lastStyle = style
                    }
                }

                // Set opening style based on carry-over state
                val initialStyle = when {
                    inDialogue     -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                    inThoughtQuote -> TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false)
                    else           -> TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                }
                addSpan(0, initialStyle)

                while (i < len) {
                    val c = line[i]

                    // 1. Straight double-quote toggle
                    if (c == '"' && !inThoughtQuote) {
                        inDialogue = !inDialogue
                        addSpan(i, if (inDialogue)
                            TextStyle.makeStyle(EditorColorScheme.LITERAL)
                        else {
                            // Include the closing quote in dialogue color, then switch
                            if (i + 1 < len)
                                addSpan(i + 1, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            TextStyle.makeStyle(EditorColorScheme.LITERAL)
                        })
                        i++; continue
                    }

                    // 2. Opening curly quote "
                    if (c == '\u201C' && !inThoughtQuote) {
                        inDialogue = true
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        i++; continue
                    }

                    // 3. Closing curly quote "
                    if (c == '\u201D' && !inThoughtQuote) {
                        inDialogue = false
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        if (i + 1 < len)
                            addSpan(i + 1, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        i++; continue
                    }

                    // 4. Guillemets « open / » close (only toggle on their own char)
                    if (c == '\u00AB' && !inDialogue && !inThoughtQuote) {
                        inDialogue = true
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        i++; continue
                    }
                    if (c == '\u00BB' && inDialogue) {
                        inDialogue = false
                        addSpan(i, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                        if (i + 1 < len)
                            addSpan(i + 1, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                        i++; continue
                    }

                    // Skip inline formatting if inside dialogue
                    if (inDialogue) { i++; continue }

                    // 5. Bold **text** — must check before single-star italic
                    if (c == '*' && i + 1 < len && line[i + 1] == '*') {
                        val close = line.indexOf("**", i + 2)
                        if (close != -1) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, true, false, false))
                            i = close + 2
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 6. Italic / thought *text*
                    if (c == '*') {
                        val close = line.indexOf('*', i + 1)
                        if (close != -1 && close > i + 1) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false))
                            i = close + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 7. Underscore italic _text_
                    if (c == '_') {
                        val close = line.indexOf('_', i + 1)
                        if (close != -1 && close > i + 1) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL, 0, false, true, false))
                            i = close + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    // 8. Typographic opening single quote ' for thought
                    if (c == '\u2018' && !inThoughtQuote) {
                        val close = line.indexOf('\u2019', i + 1)
                        if (close != -1) {
                            addSpan(i, TextStyle.makeStyle(EditorColorScheme.COMMENT, 0, false, true, false))
                            i = close + 1
                            if (i < len) addSpan(i, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
                            continue
                        }
                    }

                    i++
                }

                return LineTokenizeResult(
                    ProseState(inDialogue = inDialogue, inThoughtQuote = inThoughtQuote),
                    listOf(spans)
                )
            }

            /**
             * The token payload IS the span list (wrapped in a list by tokenizeLine).
             * Just unwrap and return it.
             */
            override fun generateSpansForLine(
                result: LineTokenizeResult<ProseState, List<Span>>
            ): List<Span> = result.tokens?.firstOrNull() ?: listOf(
                Span.obtain(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
            )

            /**
             * Prose has no code-block structure (no folding regions).
             */
            override fun computeBlocks(
                content: Content?,
                delegate: AsyncIncrementalAnalyzeManager.CodeBlockAnalyzeDelegate<ProseState, List<Span>>?
            ): List<CodeBlock> = emptyList()
        }

    override fun getAnalyzeManager(): AnalyzeManager = manager
}
