package com.primaloptima.scribe.engine

import com.primaloptima.scribe.data.WorldEntry
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import java.text.NumberFormat
import java.util.Locale

/**
 * Computes [InlayHint] entries for the Sora editor canvas.
 *
 * Two categories of hints are produced:
 *
 * 1. Scene-section word-count badges — appended inline after every scene-break
 *    marker (`***`, `###`, `---`, `* * *`) showing how many words the preceding
 *    section contains and its estimated reading time.
 *    Example: `*** [ 1,420 words · 6 min read ]`
 *
 * 2. World-entry / POV tags — on lines that start with `/` the hint shows
 *    the matched world entry type and name (character or location), or a generic
 *    tag badge if no match is found.
 *    Example: `/kael` → `[ 👤 POV: Kael ]`
 *
 * API notes for Sora 0.24.6:
 *  - Package: `io.github.rosemoe.sora.lang.styling.inlayHint`
 *    (NOT `io.github.rosemoe.sora.widget.inlayHint`)
 *  - [InlayHint] constructor: `InlayHint(line: Int, column: Int, label: String,
 *    showBefore: Boolean)` — line and column are 0-based.
 *  - [InlayHintsContainer.addHint] is the method to populate the container.
 *  - Set on editor via `editor.setInlayHints(container)` (method, not property).
 */
object ProseInlayHintProvider {

    fun computeInlayHints(
        text: String,
        worldEntries: List<WorldEntry>
    ): InlayHintsContainer {
        val container = InlayHintsContainer()
        if (text.isBlank()) return container

        val lines     = text.lines()
        val numFormat = NumberFormat.getNumberInstance(Locale.US)

        // ── 1. Scene-section word counts ──────────────────────────────────────

        data class BreakInfo(val lineIndex: Int, val columnAfterMarker: Int)

        val breakLines   = mutableListOf<BreakInfo>()
        val sectionWords = mutableListOf<Int>()   // words in section BEFORE each break
        var accumulated  = 0

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            val isBreak = trimmed == "***"   || trimmed == "* * *" ||
                          trimmed == "---"   || trimmed == "- - -" ||
                          trimmed.startsWith("###")

            if (isBreak && i > 0) {
                sectionWords.add(accumulated)
                breakLines.add(BreakInfo(i, lines[i].length))
                accumulated = 0
            } else {
                accumulated += countWords(lines[i])
            }
        }

        // Attach a hint to each break marker
        breakLines.forEachIndexed { idx, info ->
            val words       = sectionWords[idx]
            val readTimeMins = (words / 225.0).coerceAtLeast(1.0).toInt()
            val badge       = "  [ ${numFormat.format(words)} words · $readTimeMins min read ]"
            // showBefore = false  →  hint appears AFTER the column
            container.addHint(InlayHint(info.lineIndex, info.columnAfterMarker, badge, false))
        }

        // ── 2. World-entry POV / location tags ────────────────────────────────

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (!trimmed.startsWith("/")) continue

            val query        = trimmed.removePrefix("/").trim()
            val matchedEntry = worldEntries.firstOrNull {
                it.name.equals(query, ignoreCase = true)
            }

            val badge = when {
                query.isEmpty()       -> "  [ ✦ Scene Tag: type POV or location ]"
                matchedEntry != null  -> {
                    val icon = if (matchedEntry.type.equals("character", ignoreCase = true))
                        "👤 POV:" else "📍 Setting:"
                    "  [ $icon ${matchedEntry.name} ]"
                }
                query.startsWith("pov", ignoreCase = true) ||
                query.startsWith("scene", ignoreCase = true) ||
                query.startsWith("loc", ignoreCase = true) ->
                    "  [ ✦ $query ]"
                else ->
                    "  [ ✦ Tag: $query ]"
            }

            container.addHint(InlayHint(i, lines[i].length, badge, false))
        }

        return container
    }

    // ── Word counter ──────────────────────────────────────────────────────────

    private fun countWords(line: CharSequence): Int {
        var count  = 0
        var inWord = false
        for (c in line) {
            if (c.isLetterOrDigit() || c == '\'' || c == '\u2019' || c == '-') {
                if (!inWord) { count++; inWord = true }
            } else {
                inWord = false
            }
        }
        return count
    }
}
