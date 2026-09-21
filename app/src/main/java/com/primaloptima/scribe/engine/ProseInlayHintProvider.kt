package com.primaloptima.scribe.engine

import com.primaloptima.scribe.data.WorldEntry
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import java.text.NumberFormat
import java.util.Locale

/**
 * Computes Inlay Hints for prose / novel writing.
 *
 * Features:
 *   1. Section word-count & read-time badge next to "***" / "###" / "---" scene-break lines.
 *   2. POV / Location tag badge on lines that contain only a "/" prefix tag.
 *
 * ── Sora API facts (verified against source) ──────────────────────────────────
 *
 * Correct import packages:
 *   io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer  ← note the 's'
 *   io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint         ← text subclass
 *   io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint             ← base class
 *
 * InlayHint base class constructor:
 *   InlayHint(line: Int, column: Int, type: String, displaySide: CharacterSide = CharacterSide.LEFT)
 *
 * TextInlayHint:
 *   TextInlayHint(line: Int, column: Int, text: String)
 *   ← Extends InlayHint with type = "text". This is what the renderer shows.
 *
 * InlayHintsContainer extends PointAnchoredContainer<InlayHint>.
 * Add hints with:  container.add(hint)
 * ─────────────────────────────────────────────────────────────────────────────
 */
object ProseInlayHintProvider {

    fun computeInlayHints(
        text: String,
        worldEntries: List<WorldEntry>,
        firstLineIndent: Boolean = false,
        paragraphSpacing: Int = 14
    ): InlayHintsContainer {
        val container = InlayHintsContainer()
        if (text.isBlank()) return container

        val lines = text.lines()
        val numFormat = NumberFormat.getNumberInstance(Locale.US)

        // ── 1. Section word counts between scene-break markers ────────────────────────
        data class SceneSection(val lineIndex: Int, val markerLength: Int, var wordCount: Int = 0)

        val breakPositions = mutableListOf<SceneSection>()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed == "***" || trimmed == "###" || trimmed == "* * *" || trimmed == "---") {
                breakPositions.add(SceneSection(lineIndex = i, markerLength = lines[i].length))
            }
        }

        if (breakPositions.isNotEmpty()) {
            var breakIdx = 0
            var accumulatedWords = 0

            for (i in lines.indices) {
                val trimmed = lines[i].trim()
                val isBreak = trimmed == "***" || trimmed == "###" ||
                        trimmed == "* * *" || trimmed == "---"

                if (isBreak) {
                    if (breakIdx < breakPositions.size) {
                        breakPositions[breakIdx].wordCount = accumulatedWords
                        accumulatedWords = 0
                        breakIdx++
                    }
                } else {
                    accumulatedWords += countWords(lines[i])
                }
            }

            // Emit badges per break marker
            for (sec in breakPositions) {
                val words = if (sec.wordCount > 0) sec.wordCount else accumulatedWords
                val readTimeMin = (words / 225).coerceAtLeast(1)
                val badgeText = "  [${numFormat.format(words)} words · $readTimeMin min read]"

                val hint = TextInlayHint(
                    sec.lineIndex,    // 0-based line index
                    sec.markerLength, // column after the last char of the "***" / "###"
                    badgeText         // the badge text shown inline
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

            val hint = TextInlayHint(
                i,            // 0-based line index
                line.length,  // column: right after the last character on the line
                tagBadge      // badge text
            )
            container.add(hint)
        }

        // ── 3. First-line paragraph indentation for classic typesetting ──────────────
        if (firstLineIndent) {
            for (i in lines.indices) {
                val line = lines[i]
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                // Skip headings, scene breaks, tags, blockquotes, list markers
                if (trimmed.startsWith("#") || trimmed.startsWith("***") || trimmed.startsWith("###") ||
                    trimmed.startsWith("* * *") || trimmed.startsWith("---") || trimmed.startsWith("/") ||
                    trimmed.startsWith(">") || trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                    trimmed.startsWith("+ ") || isNumberedListItem(trimmed)
                ) {
                    continue
                }
                // Only indent if line does not already begin with spaces, tabs, or ideographic full-width spaces
                if (!line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("\u3000")) {
                    val indentHint = TextInlayHint(
                        i,
                        0,
                        "    " // 4-space paragraph indent
                    )
                    container.add(indentHint)
                }
            }
        }

        return container
    }

    /** Counts words in a line by scanning character by character (zero regex allocation). */
    private fun countWords(line: CharSequence): Int {
        var count = 0
        var inWord = false
        val len = line.length
        for (i in 0 until len) {
            val c = line[i]
            if (c.isLetterOrDigit() || c == '\'' || c == '\u2019' || c == '-') {
                if (!inWord) { count++; inWord = true }
            } else {
                inWord = false
            }
        }
        return count
    }

    /** Allocation-free check for ordered list items like "1. ", "2. ", "10. " etc. */
    private fun isNumberedListItem(trimmed: String): Boolean {
        if (trimmed.isEmpty() || !trimmed[0].isDigit()) return false
        var idx = 1
        while (idx < trimmed.length && trimmed[idx].isDigit()) {
            idx++
        }
        return idx < trimmed.length && trimmed[idx] == '.'
    }
}
