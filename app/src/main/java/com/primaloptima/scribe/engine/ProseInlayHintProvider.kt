package com.primaloptima.scribe.engine

import com.primaloptima.scribe.data.WorldEntry
import io.github.rosemoe.sora.widget.inlayHint.InlayHint
import io.github.rosemoe.sora.widget.inlayHint.InlayHintContainer
import java.text.NumberFormat
import java.util.Locale

/**
 * Computes Inlay Hints for prose / novel writing in Sora 0.24.x.
 *
 * Features:
 *   1. Section word-count & read-time badge next to "***" / "###" scene-break lines.
 *   2. POV / Location tag badge on lines that start with "/".
 *
 * ── API notes for Sora 0.24.x ──────────────────────────────────────────────────────────
 *
 *  InlayHint constructor (the only public constructor in 0.24.x):
 *    InlayHint(line: Int, column: Int, label: CharSequence, hasBackground: Boolean)
 *
 *    • line    — 0-based line index in the document.
 *    • column  — column offset where the hint is rendered (after that column).
 *    • label   — the text shown inline (String is fine; it implements CharSequence).
 *    • hasBackground — whether to draw a pill/background behind the text.
 *
 *  There is NO "addHint" method on InlayHint or InlayHintContainer.
 *  There is NO "CharacterSide" enum in 0.24.x.
 *  The correct pattern is:
 *    val hint = InlayHint(line, column, label, hasBackground)
 *    container.add(hint)
 *
 * ───────────────────────────────────────────────────────────────────────────────────────
 */
object ProseInlayHintProvider {

    fun computeInlayHints(
        text: String,
        worldEntries: List<WorldEntry>
    ): InlayHintContainer {
        val container = InlayHintContainer()
        if (text.isBlank()) return container

        val lines = text.lines()
        val numFormat = NumberFormat.getNumberInstance(Locale.US)

        // ── 1. Section word counts between scene-break markers (*** or ###) ───────────

        data class SceneSection(val lineIndex: Int, val markerLength: Int, var wordCount: Int = 0)

        val breakPositions = mutableListOf<SceneSection>()

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed == "***" || trimmed == "###" || trimmed == "* * *" || trimmed == "---") {
                breakPositions.add(SceneSection(lineIndex = i, markerLength = lines[i].length))
            }
        }

        if (breakPositions.isNotEmpty()) {
            var currentBreakIdx = 0
            var accumulatedWords = 0

            for (i in lines.indices) {
                val trimmed = lines[i].trim()
                val isBreak = trimmed == "***" || trimmed == "###" ||
                        trimmed == "* * *" || trimmed == "---"

                if (isBreak) {
                    if (currentBreakIdx < breakPositions.size) {
                        breakPositions[currentBreakIdx].wordCount = accumulatedWords
                        accumulatedWords = 0
                        currentBreakIdx++
                    }
                } else {
                    accumulatedWords += extractWordCount(lines[i])
                }
            }

            for (sec in breakPositions) {
                val words = if (sec.wordCount > 0) sec.wordCount else accumulatedWords
                val readTimeMin = (words / 225).coerceAtLeast(1)
                val badgeText = "  [${numFormat.format(words)} words · $readTimeMin min read]"

                // ── Correct InlayHint API ─────────────────────────────────────────────
                // InlayHint(line, column, label, hasBackground)
                // No "addHint", no "CharacterSide" — just construct and container.add()
                val hint = InlayHint(
                    sec.lineIndex,       // line (0-based)
                    sec.markerLength,    // column: placed after the last char of the marker
                    badgeText,           // label text (String implements CharSequence)
                    true                 // hasBackground: pill-style badge
                )
                container.add(hint)
            }
        }

        // ── 2. POV / Location tags on lines starting with "/" ─────────────────────────

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            if (!trimmed.startsWith("/")) continue

            val tagQuery = trimmed.removePrefix("/").trim()
            val matchedEntry = worldEntries.firstOrNull {
                it.name.equals(tagQuery, ignoreCase = true)
            }

            val tagBadge: String = when {
                tagQuery.isEmpty() -> "  [✦ Scene Tag: Type POV / Setting]"
                matchedEntry != null -> {
                    val icon = if (matchedEntry.type == "character") "👤 POV:" else "📍 Setting:"
                    "  [$icon ${matchedEntry.name}]"
                }
                tagQuery.startsWith("pov", ignoreCase = true) ||
                        tagQuery.startsWith("scene", ignoreCase = true) ||
                        tagQuery.startsWith("loc", ignoreCase = true) -> "  [✦ $tagQuery]"
                else -> "  [✦ Tag: $tagQuery]"
            }

            val hint = InlayHint(
                i,              // line index
                line.length,    // column: after the full line (end of text)
                tagBadge,
                true
            )
            container.add(hint)
        }

        return container
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private fun extractWordCount(line: CharSequence): Int {
        var count = 0
        var inWord = false
        for (i in 0 until line.length) {
            val c = line[i]
            if (c.isLetterOrDigit() || c == '\'' || c == '\u2019' || c == '-') {
                if (!inWord) { count++; inWord = true }
            } else {
                inWord = false
            }
        }
        return count
    }
}
