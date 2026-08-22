package com.primaloptima.scribe.engine

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import java.util.Locale

/**
 * Diagnostic analysis model representing a stylistic or flow diagnostic in text.
 */
data class ProseDiagnostic(
    val startIndex: Int,
    val endIndex: Int,
    val category: String,
    val message: String,
    val suggestion: String?,
    val replacement: String?,
    val severity: Int = DiagnosticRegion.SEVERITY_WARNING,
    val flags: Int = DiagnosticRegion.FLAG_WAVY_LINE
)

object ProseDiagnosticProvider {

    // Filter words (weak narrative distance)
    private val FILTER_WORDS_MAP = mapOf(
        "felt" to "experienced / perceived directly",
        "feel" to "experience / perceive directly",
        "feeling" to "experiencing directly",
        "noticed" to "saw / observed",
        "notice" to "see / observe",
        "decided to" to "simply act",
        "started to" to "acted immediately",
        "began to" to "acted immediately",
        "wondered if" to "questioned internally",
        "thought that" to "concluded directly",
        "seemed to" to "was / appeared",
        "appeared to" to "was",
        "was able to" to "could / did",
        "heard" to "heard directly",
        "suddenly" to "remove for immediacy",
        "immediately" to "remove for immediacy",
        "actually" to "remove filler",
        "basically" to "remove filler",
        "really" to "remove filler",
        "very" to "use stronger adjective"
    )

    // Common overused adverbs with high impact suggestions
    private val ADVERB_SUGGESTIONS = mapOf(
        "suddenly heard" to "heard",
        "loudly shouted" to "shouted / roared",
        "quietly whispered" to "whispered",
        "slowly walked" to "strolled / ambled",
        "quickly ran" to "sprinted / dashed",
        "angrily said" to "snapped / fumed",
        "happily smiled" to "beamed / grinned",
        "sadly sighed" to "sighed / despaired",
        "nervously looked" to "glanced / darted eyes",
        "calmly answered" to "replied / answered",
        "gently touched" to "caressed / grazed",
        "fiercely fought" to "battled / clashed"
    )

    // Passive voice auxiliary patterns (e.g. "was written", "were destroyed", "is given", "been seen")
    private val PASSIVE_AUXILIARIES = setOf("was", "were", "is", "are", "been", "being", "be")
    private val COMMON_PAST_PARTICIPLES = setOf(
        "given", "taken", "seen", "done", "made", "found", "told", "heard", "written",
        "broken", "chosen", "driven", "eaten", "fallen", "forgotten", "frozen", "hidden",
        "known", "lost", "paid", "run", "said", "sent", "shown", "spoken", "spent",
        "killed", "destroyed", "watched", "chased", "pushed", "pulled", "held", "opened", "closed"
    )

    /**
     * Scans [text] for stylistic suggestions, passive voice, overused adverbs, filter words,
     * and repeated words within a 3-sentence sliding window.
     */
    fun analyzeDiagnostics(text: String): DiagnosticsContainer {
        val container = DiagnosticsContainer()
        if (text.isBlank()) return container

        val len = text.length
        val lower = text.lowercase(Locale.ROOT)

        // 1. Phrasal Adverb Suggestions (e.g. "suddenly heard" -> "heard")
        for ((phrase, replacement) in ADVERB_SUGGESTIONS) {
            var startIndex = 0
            while (startIndex < len) {
                val idx = lower.indexOf(phrase, startIndex)
                if (idx == -1) break

                val endIdx = idx + phrase.length
                val isWordBoundLeft = (idx == 0 || !text[idx - 1].isLetterOrDigit())
                val isWordBoundRight = (endIdx >= len || !text[endIdx].isLetterOrDigit())

                if (isWordBoundLeft && isWordBoundRight) {
                    val originalSub = text.substring(idx, endIdx)
                    val qf = Quickfix { editor, reg ->
                        editor.text.replace(reg.startIndex, reg.endIndex, replacement)
                    }
                    val detail = DiagnosticDetail(
                        "Overused Adverb Phrase",
                        "Consider simplifying '$originalSub' to '$replacement' for stronger pace.",
                        listOf(qf)
                    )
                    val region = DiagnosticRegion(
                        idx,
                        endIdx,
                        DiagnosticRegion.SEVERITY_WARNING,
                        DiagnosticRegion.FLAG_WAVY_LINE,
                        detail
                    )
                    container.addDiagnostic(region)
                }
                startIndex = endIdx
            }
        }

        // 2. Filter words & Tell vs Show checks
        for ((filterWord, tip) in FILTER_WORDS_MAP) {
            var startIndex = 0
            while (startIndex < len) {
                val idx = lower.indexOf(filterWord, startIndex)
                if (idx == -1) break

                val endIdx = idx + filterWord.length
                val isWordBoundLeft = (idx == 0 || !text[idx - 1].isLetterOrDigit())
                val isWordBoundRight = (endIdx >= len || !text[endIdx].isLetterOrDigit())

                if (isWordBoundLeft && isWordBoundRight) {
                    val originalSub = text.substring(idx, endIdx)
                    val qf = if (filterWord == "suddenly" || filterWord == "actually" || filterWord == "basically" || filterWord == "really") {
                        Quickfix { editor, reg ->
                            // Remove word and adjacent trailing space if present
                            val eraseEnd = if (reg.endIndex < editor.text.length && editor.text[reg.endIndex] == ' ') {
                                reg.endIndex + 1
                            } else reg.endIndex
                            editor.text.delete(reg.startIndex, eraseEnd)
                        }
                    } else null

                    val quickfixes = if (qf != null) listOf(qf) else emptyList()
                    val detail = DiagnosticDetail(
                        "Filter Word / Pacing",
                        "Filter word '$originalSub': $tip",
                        quickfixes
                    )
                    val region = DiagnosticRegion(
                        idx,
                        endIdx,
                        DiagnosticRegion.SEVERITY_INFO,
                        DiagnosticRegion.FLAG_WAVY_LINE,
                        detail
                    )
                    container.addDiagnostic(region)
                }
                startIndex = endIdx
            }
        }

        // 3. Passive Voice Detection (Auxiliary + Past Participle)
        val wordRegex = Regex("\\b([A-Za-z]+)\\b")
        val matches = wordRegex.findAll(text).toList()

        for (i in 0 until matches.size - 1) {
            val w1 = matches[i].value.lowercase(Locale.ROOT)
            val w2 = matches[i + 1].value.lowercase(Locale.ROOT)

            if (PASSIVE_AUXILIARIES.contains(w1) && (COMMON_PAST_PARTICIPLES.contains(w2) || (w2.endsWith("ed") && w2.length > 4))) {
                val start = matches[i].range.first
                val end = matches[i + 1].range.last + 1
                val passivePhrase = text.substring(start, end)

                val detail = DiagnosticDetail(
                    "Passive Voice",
                    "Passive construction '$passivePhrase'. Consider converting to active voice for stronger prose.",
                    emptyList()
                )
                val region = DiagnosticRegion(
                    start,
                    end,
                    DiagnosticRegion.SEVERITY_WARNING,
                    DiagnosticRegion.FLAG_WAVY_LINE,
                    detail
                )
                container.addDiagnostic(region)
            }
        }

        // 4. Repeated Word Alert within a 3-Sentence Window
        // Finds significant non-stopwords repeated close together
        val sentenceRegex = Regex("[^.!?\\n]+[.!?\\n]?")
        val sentences = sentenceRegex.findAll(text).toList()

        val stopWords = hashSetOf(
            "a", "an", "the", "and", "or", "but", "if", "in", "on", "at", "to", "for",
            "with", "by", "of", "from", "up", "about", "into", "over", "after",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "their", "our", "its", "that", "this", "these", "those",
            "is", "am", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "will", "would", "shall", "should", "can", "could", "may", "might",
            "said", "asked", "replied", "looked"
        )

        for (sIdx in sentences.indices) {
            val currentSentence = sentences[sIdx]
            val currentWords = wordRegex.findAll(currentSentence.value).toList()

            // Look back up to 2 preceding sentences (total 3-sentence window)
            val windowStart = (sIdx - 2).coerceAtLeast(0)
            val priorSentences = sentences.subList(windowStart, sIdx)
            val priorWords = priorSentences.flatMap { wordRegex.findAll(it.value).map { m -> m.value.lowercase(Locale.ROOT) } }.toSet()

            for (wMatch in currentWords) {
                val w = wMatch.value.lowercase(Locale.ROOT)
                if (w.length >= 4 && !stopWords.contains(w) && priorWords.contains(w)) {
                    val absStart = currentSentence.range.first + wMatch.range.first
                    val absEnd = currentSentence.range.first + wMatch.range.last + 1

                    val detail = DiagnosticDetail(
                        "Repeated Word Alert",
                        "The word '${wMatch.value}' appears repeatedly within 3 sentences. Consider a synonym to vary prose rhythm.",
                        emptyList()
                    )
                    val region = DiagnosticRegion(
                        absStart,
                        absEnd,
                        DiagnosticRegion.SEVERITY_ERROR,
                        DiagnosticRegion.FLAG_WAVY_LINE,
                        detail
                    )
                    container.addDiagnostic(region)
                }
            }
        }

        return container
    }
}
