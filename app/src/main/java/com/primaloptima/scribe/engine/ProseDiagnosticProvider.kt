package com.primaloptima.scribe.engine

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import java.util.Locale

/**
 * Prose-writing stylistic diagnostic model.
 *
 * ── Sora API facts (verified against source) ──────────────────────────────────
 *
 * DiagnosticRegion constants:
 *   SEVERITY_NONE    = 0  (Short)
 *   SEVERITY_TYPO    = 1  (Short)
 *   SEVERITY_WARNING = 2  (Short)
 *   SEVERITY_ERROR   = 3  (Short)
 *   ← There is NO SEVERITY_INFO and NO FLAG_WAVY_LINE in this version.
 *
 * DiagnosticRegion constructors:
 *   DiagnosticRegion(startIndex: Int, endIndex: Int, severity: Short)
 *   DiagnosticRegion(startIndex: Int, endIndex: Int, severity: Short, id: Long)
 *   DiagnosticRegion(startIndex: Int, endIndex: Int, severity: Short, id: Long, detail: DiagnosticDetail?)
 *   ← severity must be Short. Cast with .toShort() in Kotlin.
 *   ← There is NO "flags" parameter.
 *
 * DiagnosticDetail constructor:
 *   DiagnosticDetail(briefMessage: CharSequence, detailedMessage: CharSequence? = null,
 *                    quickfixes: List<Quickfix>? = null, extraData: Any? = null)
 *
 * Quickfix constructors:
 *   Quickfix(title: CharSequence?, documentVersion: Long = 0, fixAction: Runnable? = null)
 *   Quickfix(titleRes: Int, documentVersion: Long = 0, fixAction: Runnable)
 *   ← fixAction is a plain Runnable (no parameters). The editor/region reference
 *     must be captured from the outer scope — NOT passed as lambda parameters.
 * ─────────────────────────────────────────────────────────────────────────────
 */
data class ProseDiagnostic(
    val startIndex: Int,
    val endIndex: Int,
    val category: String,
    val message: String,
    val suggestion: String?,
    val replacement: String?,
    val severity: Short = DiagnosticRegion.SEVERITY_WARNING
)

object ProseDiagnosticProvider {

    // Filter words (weak narrative distance / tell-don't-show)
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

    // Common overused adverb+verb pairs
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

    private val PASSIVE_AUXILIARIES = setOf("was", "were", "is", "are", "been", "being", "be")
    private val COMMON_PAST_PARTICIPLES = setOf(
        "given", "taken", "seen", "done", "made", "found", "told", "heard", "written",
        "broken", "chosen", "driven", "eaten", "fallen", "forgotten", "frozen", "hidden",
        "known", "lost", "paid", "run", "said", "sent", "shown", "spoken", "spent",
        "killed", "destroyed", "watched", "chased", "pushed", "pulled", "held", "opened", "closed"
    )

    /**
     * Scans [text] for stylistic diagnostics:
     *   1. Overused adverb phrases (e.g. "suddenly heard")
     *   2. Filter words / tell-don't-show phrases
     *   3. Passive voice constructions
     *   4. Repeated words within a 3-sentence window
     *
     * Returns a populated [DiagnosticsContainer] ready to hand to Sora.
     */
    fun analyzeDiagnostics(text: String): DiagnosticsContainer {
        val container = DiagnosticsContainer()
        if (text.isBlank()) return container

        val len = text.length
        val lower = text.lowercase(Locale.ROOT)

        // ── 1. Overused adverb phrases ────────────────────────────────────────────────
        for ((phrase, replacement) in ADVERB_SUGGESTIONS) {
            var searchFrom = 0
            while (searchFrom < len) {
                val idx = lower.indexOf(phrase, searchFrom)
                if (idx == -1) break

                val endIdx = idx + phrase.length
                val boundLeft = idx == 0 || !text[idx - 1].isLetterOrDigit()
                val boundRight = endIdx >= len || !text[endIdx].isLetterOrDigit()

                if (boundLeft && boundRight) {
                    val original = text.substring(idx, endIdx)

                    // Quickfix: fixAction is a plain Runnable — capture indices in the closure
                    val capturedStart = idx
                    val capturedEnd = endIdx
                    val quickfix = Quickfix(
                        title = "Replace with '$replacement'",
                        fixAction = Runnable {
                            // NOTE: The editor reference must be obtained externally (e.g. via
                            // the Language's DiagnosticProvider callback) and passed through
                            // your own mechanism. Sora's Quickfix.executeQuickfix() calls this
                            // Runnable; use your editor reference captured in that outer scope.
                            // The indices capturedStart/capturedEnd are available here.
                        }
                    )
                    val detail = DiagnosticDetail(
                        briefMessage = "Overused Adverb Phrase",
                        detailedMessage = "Consider simplifying '$original' → '$replacement' for stronger pace.",
                        quickfixes = listOf(quickfix)
                    )
                    // severity is Short — cast with .toShort()
                    val region = DiagnosticRegion(
                        idx,
                        endIdx,
                        DiagnosticRegion.SEVERITY_WARNING,   // already Short
                        0L,
                        detail
                    )
                    container.addDiagnostic(region)
                }
                searchFrom = endIdx
            }
        }

        // ── 2. Filter words / Tell-don't-show ────────────────────────────────────────
        for ((filterWord, tip) in FILTER_WORDS_MAP) {
            var searchFrom = 0
            while (searchFrom < len) {
                val idx = lower.indexOf(filterWord, searchFrom)
                if (idx == -1) break

                val endIdx = idx + filterWord.length
                val boundLeft = idx == 0 || !text[idx - 1].isLetterOrDigit()
                val boundRight = endIdx >= len || !text[endIdx].isLetterOrDigit()

                if (boundLeft && boundRight) {
                    val original = text.substring(idx, endIdx)

                    val deletable = filterWord in setOf(
                        "suddenly", "immediately", "actually", "basically", "really", "very"
                    )
                    val quickfixes: List<Quickfix> = if (deletable) {
                        val capturedStart = idx
                        val capturedEnd = endIdx
                        listOf(
                            Quickfix(
                                title = "Delete '$original'",
                                fixAction = Runnable {
                                    // Deletion logic to be executed with your editor reference.
                                    // capturedStart and capturedEnd are in scope here.
                                }
                            )
                        )
                    } else {
                        emptyList()
                    }

                    val detail = DiagnosticDetail(
                        briefMessage = "Filter Word / Pacing",
                        detailedMessage = "Filter word '$original': $tip",
                        quickfixes = quickfixes.ifEmpty { null }
                    )
                    // Use SEVERITY_TYPO (the closest to "info" in this version)
                    val region = DiagnosticRegion(
                        idx,
                        endIdx,
                        DiagnosticRegion.SEVERITY_TYPO,   // Short constant — no cast needed
                        0L,
                        detail
                    )
                    container.addDiagnostic(region)
                }
                searchFrom = endIdx
            }
        }

        // ── 3. Passive Voice Detection (auxiliary + past participle) ─────────────────
        val wordRegex = Regex("\\b([A-Za-z]+)\\b")
        val matches = wordRegex.findAll(text).toList()

        for (i in 0 until matches.size - 1) {
            val w1 = matches[i].value.lowercase(Locale.ROOT)
            val w2 = matches[i + 1].value.lowercase(Locale.ROOT)

            if (w1 in PASSIVE_AUXILIARIES &&
                (w2 in COMMON_PAST_PARTICIPLES || (w2.endsWith("ed") && w2.length > 4))
            ) {
                val start = matches[i].range.first
                val end = matches[i + 1].range.last + 1
                val passivePhrase = text.substring(start, end)

                val detail = DiagnosticDetail(
                    briefMessage = "Passive Voice",
                    detailedMessage = "Passive construction '$passivePhrase'. Consider active voice for stronger prose.",
                    quickfixes = null
                )
                val region = DiagnosticRegion(
                    start,
                    end,
                    DiagnosticRegion.SEVERITY_WARNING,
                    0L,
                    detail
                )
                container.addDiagnostic(region)
            }
        }

        // ── 4. Repeated Word Alert (3-sentence sliding window) ───────────────────────
        val sentenceRegex = Regex("[^.!?\\n]+[.!?\\n]?")
        val sentences = sentenceRegex.findAll(text).toList()

        val stopWords = hashSetOf(
            "a", "an", "the", "and", "or", "but", "if", "in", "on", "at", "to", "for",
            "with", "by", "of", "from", "up", "about", "into", "over", "after",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
            "my", "your", "his", "their", "our", "its", "that", "this", "these", "those",
            "is", "am", "are", "was", "were", "be", "been", "being", "have", "has", "had",
            "do", "does", "did", "will", "would", "shall", "should", "can", "could",
            "may", "might", "said", "asked", "replied", "looked"
        )

        for (sIdx in sentences.indices) {
            val currentSentence = sentences[sIdx]
            val currentWords = wordRegex.findAll(currentSentence.value).toList()
            val windowStart = (sIdx - 2).coerceAtLeast(0)
            val priorWords = sentences
                .subList(windowStart, sIdx)
                .flatMap { s -> wordRegex.findAll(s.value).map { m -> m.value.lowercase(Locale.ROOT) } }
                .toSet()

            for (wMatch in currentWords) {
                val w = wMatch.value.lowercase(Locale.ROOT)
                if (w.length >= 4 && w !in stopWords && w in priorWords) {
                    val absStart = currentSentence.range.first + wMatch.range.first
                    val absEnd = currentSentence.range.first + wMatch.range.last + 1

                    val detail = DiagnosticDetail(
                        briefMessage = "Repeated Word",
                        detailedMessage = "'${wMatch.value}' appears repeatedly within 3 sentences. Consider a synonym.",
                        quickfixes = null
                    )
                    val region = DiagnosticRegion(
                        absStart,
                        absEnd,
                        DiagnosticRegion.SEVERITY_ERROR,
                        0L,
                        detail
                    )
                    container.addDiagnostic(region)
                }
            }
        }

        return container
    }
}
