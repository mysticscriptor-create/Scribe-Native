package com.primaloptima.scribe.engine

import com.primaloptima.scribe.data.WorldEntry
import io.github.rosemoe.sora.widget.inlayHint.InlayHint
import io.github.rosemoe.sora.widget.inlayHint.InlayHintContainer
import java.text.NumberFormat
import java.util.Locale

object ProseInlayHintProvider {

    /**
     * Computes Inlay Hints for:
     * 1. Section / Scene break stats ([1,420 words · 5 min read]) next to "***" or "###" lines.
     * 2. POV / Location Tags next to "/..." scene markers or world entries.
     */
    fun computeInlayHints(
        text: String,
        worldEntries: List<WorldEntry>
    ): InlayHintContainer {
        val container = InlayHintContainer()
        if (text.isBlank()) return container

        val lines = text.lines()
        val numFormat = NumberFormat.getNumberInstance(Locale.US)

        // 1. Calculate section word counts between scene breaks (*** or ###)
        // Group lines into sections bounded by scene breaks
        data class SceneSection(val lineIndex: Int, val markerLength: Int, var wordCount: Int = 0)
        val breakPositions = mutableListOf<SceneSection>()

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed == "***" || trimmed == "###" || trimmed == "* * *" || trimmed == "---") {
                breakPositions.add(SceneSection(lineIndex = i, markerLength = line.length))
            }
        }

        // Compute words per section
        if (breakPositions.isNotEmpty()) {
            var currentBreakIdx = 0
            var accumulatedWords = 0

            for (i in lines.indices) {
                val line = lines[i]
                val trimmed = line.trim()
                val isBreak = trimmed == "***" || trimmed == "###" || trimmed == "* * *" || trimmed == "---"

                if (isBreak) {
                    if (currentBreakIdx < breakPositions.size) {
                        breakPositions[currentBreakIdx].wordCount = accumulatedWords
                        accumulatedWords = 0
                        currentBreakIdx++
                    }
                } else {
                    val wordsInLine = extractWordCount(line)
                    accumulatedWords += wordsInLine
                }
            }

            // For each scene break, display section word count and read time of the preceding/following section
            for (sec in breakPositions) {
                val words = if (sec.wordCount > 0) sec.wordCount else accumulatedWords
                val readTimeMin = (words / 225).coerceAtLeast(1)
                val badgeText = " [${numFormat.format(words)} words · $readTimeMin min read]"
                val hint = InlayHint(sec.lineIndex, sec.markerLength, badgeText, false)
                container.add(hint)
            }
        }

        // 2. POV / Location tags on lines starting with "/"
        val worldEntryNames = worldEntries.map { it.name.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.startsWith("/")) {
                val tagQuery = trimmed.removePrefix("/").trim()
                val matchedEntry = worldEntries.firstOrNull { it.name.equals(tagQuery, ignoreCase = true) }

                val tagBadge = when {
                    tagQuery.isEmpty() -> " [✦ Scene Tag: Type POV / Setting]"
                    matchedEntry != null -> {
                        val icon = if (matchedEntry.type == "character") "👤 POV:" else "📍 Setting:"
                        " [$icon ${matchedEntry.name}]"
                    }
                    tagQuery.startsWith("pov", ignoreCase = true) || tagQuery.startsWith("scene", ignoreCase = true) || tagQuery.startsWith("loc", ignoreCase = true) -> {
                        " [✦ $tagQuery]"
                    }
                    else -> {
                        " [✦ Tag: $tagQuery]"
                    }
                }

                val hint = InlayHint(i, line.length, tagBadge, false)
                container.add(hint)
            }
        }

        return container
    }

    private fun extractWordCount(line: CharSequence): Int {
        var count = 0
        var inWord = false
        for (i in 0 until line.length) {
            val c = line[i]
            if (c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-') {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }
}
