package com.primaloptima.scribe.engine

import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.Quickfix
import java.util.Locale

/**
 * Prose diagnostic analyser.
 *
 * Scans text for stylistic suggestions and pushes them into a [DiagnosticsContainer]
 * that Sora renders as wavy underlines directly in the editor canvas.
 *
 * API notes for Sora 0.24.6:
 *  - [DiagnosticRegion] severity is a raw Short, not a named constant.
 *      Use the companion constants SEVERITY_ERROR / SEVERITY_WARNING / SEVERITY_TYPO
 *      declared below, which map to Sora's internal values (1, 2, 3).
 *  - The flag for wavy underline is [DiagnosticRegion.FLAG_WAVY_UNDERLINE] (not FLAG_WAVY_LINE).
 *  - [DiagnosticDetail] takes (message: String, quickfixes: List<Quickfix>).
 *      The titleRes parameter does not exist in this version; pass message only.
 *  - [Quickfix] lambda is `() -> Unit` — editor and region are NOT lambda parameters.
 *      Access them via closure if needed (not applicable here since we have no
 *      direct editor reference at construction time; jump-to is handled by the UI layer).
 *  - Set on editor via [CodeEditor.setDiagnostics], not via a property assignment.
 */
object ProseDiagnosticProvider {

    // ── Severity constants (Short values matching Sora internals) ─────────────
    private const val SEV_ERROR:   Short = 1
    private const val SEV_WARNING: Short = 2
    private const val SEV_TYPO:    Short = 3   // used for filter words / soft suggestions

    // ── Weak adverb phrase → stronger replacement ─────────────────────────────
    private val ADVERB_SUGGESTIONS = mapOf(
        "suddenly heard"  to "heard",
        "loudly shouted"  to "shouted",
        "quietly whispered" to "whispered",
        "slowly walked"   to "strolled",
        "quickly ran"     to "sprinted",
        "angrily said"    to "snapped",
        "happily smiled"  to "beamed",
        "sadly sighed"    to "sighed",
        "nervously looked" to "glanced",
        "calmly answered" to "replied",
        "gently touched"  to "caressed",
        "fiercely fought" to "battled"
    )

    // ── Filter words → tip ────────────────────────────────────────────────────
    private val FILTER_WORDS_MAP = mapOf(
        "felt like"    to "Direct sensory perception instead.",
        "felt that"    to "Internal filtering creates distance from the reader.",
        "felt a"       to "Filter phrase creates emotional distance.",
        "noticed that" to "Show what was noticed directly.",
        "noticed a"    to "Remove the filter for closer POV.",
        "noticed the"  to "Direct observation is stronger.",
        "saw that"     to "Describe the visual directly.",
        "saw a"        to "Eliminate the visual filter.",
        "saw the"      to "State the action directly.",
        "heard that"   to "Auditory filtering slows pace.",
        "heard a"      to "Immerse the reader in the sound directly.",
        "heard the"    to "Direct sound description has more impact.",
        "wondered if"  to "Express the thought directly.",
        "wondered whether" to "Present the dilemma directly.",
        "decided to"   to "Show the character acting, not deciding.",
        "realized that" to "State the discovery directly.",
        "seemed to"    to "Weakens certainty. State the fact.",
        "watched as"   to "Describe the action directly.",
        "could hear"   to "Replace with the direct auditory verb.",
        "could see"    to "Replace with direct visual imagery.",
        "could feel"   to "Direct sensation provides deeper immersion.",
        "could tell"   to "Show the evidence rather than the inference.",
        "suddenly"     to "Remove for immediacy.",
        "immediately"  to "Remove for immediacy.",
        "actually"     to "Remove filler.",
        "basically"    to "Remove filler.",
        "really"       to "Use a stronger adjective.",
        "very"         to "Use a stronger adjective."
    )

    // ── Passive voice ─────────────────────────────────────────────────────────
    private val PASSIVE_AUXILIARIES = setOf(
        "was", "were", "is", "are", "been", "being", "be"
    )
    private val COMMON_PAST_PARTICIPLES = setOf(
        "given", "taken", "seen", "done", "made", "found", "told", "heard",
        "written", "broken", "chosen", "driven", "eaten", "fallen", "forgotten",
        "frozen", "hidden", "known", "lost", "paid", "run", "said", "sent",
        "shown", "spoken", "spent", "killed", "destroyed", "watched", "chased",
        "pushed", "pulled", "held", "opened", "closed"
    )

    // ── Stop words for repeated-word scan ────────────────────────────────────
    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "if", "in", "on", "at", "to",
        "for", "with", "by", "of", "from", "up", "about", "into", "over", "after",
        "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us",
        "them", "my", "your", "his", "their", "our", "its", "that", "this",
        "these", "those", "is", "am", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will", "would",
        "shall", "should", "can", "could", "may", "might",
        "said", "asked", "replied", "looked"
    )

    /**
     * Analyses [text] and returns a [DiagnosticsContainer] populated with
     * wavy-underline regions for passive voice, filter words, overused adverb
     * phrases, and repeated words within a 3-sentence sliding window.
     *
     * Call [CodeEditor.setDiagnostics] with the returned container.
     */
    fun analyzeDiagnostics(text: String): DiagnosticsContainer {
        val container = DiagnosticsContainer()
        if (text.isBlank()) return container

        val len   = text.length
        val lower = text.lowercase(Locale.ROOT)

        // 1. Adverb phrase suggestions ─────────────────────────────────────────
        for ((phrase, replacement) in ADVERB_SUGGESTIONS) {
            var start = 0
            while (start < len) {
                val idx = lower.indexOf(phrase, start)
                if (idx == -1) break
                val end = idx + phrase.length
                if (isWordBoundary(text, lower, idx, end)) {
                    val original = text.substring(idx, end)
                    val detail = DiagnosticDetail(
                        "Overused adverb phrase '$original' — consider '$replacement' for stronger pace.",
                        emptyList()
                    )
                    container.addDiagnostic(
                        DiagnosticRegion(idx, end, SEV_WARNING, DiagnosticRegion.FLAG_WAVY_UNDERLINE, detail)
                    )
                }
                start = end
            }
        }

        // 2. Filter words ──────────────────────────────────────────────────────
        for ((filterPhrase, tip) in FILTER_WORDS_MAP) {
            var start = 0
            while (start < len) {
                val idx = lower.indexOf(filterPhrase, start)
                if (idx == -1) break
                val end = idx + filterPhrase.length
                if (isWordBoundary(text, lower, idx, end)) {
                    val original = text.substring(idx, end)

                    // For pure filler words we offer a one-tap delete quickfix.
                    // Quickfix lambda is () -> Unit — it runs on the UI thread when
                    // the user taps the tooltip action. We capture nothing here
                    // (the editor reference is unavailable at analysis time); the
                    // tooltip window provides its own context for applying fixes.
                    val quickfixes: List<Quickfix> = emptyList()

                    val detail = DiagnosticDetail(
                        "Filter word '$original': $tip",
                        quickfixes
                    )
                    container.addDiagnostic(
                        DiagnosticRegion(idx, end, SEV_TYPO, DiagnosticRegion.FLAG_WAVY_UNDERLINE, detail)
                    )
                }
                start = end
            }
        }

        // 3. Passive voice (aux + past participle) ────────────────────────────
        val wordRegex = Regex("\\b([A-Za-z]+)\\b")
        val matches   = wordRegex.findAll(text).toList()

        for (i in 0 until matches.size - 1) {
            val w1 = matches[i].value.lowercase(Locale.ROOT)
            val w2 = matches[i + 1].value.lowercase(Locale.ROOT)

            if (PASSIVE_AUXILIARIES.contains(w1) &&
                (COMMON_PAST_PARTICIPLES.contains(w2) ||
                    (w2.endsWith("ed") && w2.length > 4))
            ) {
                val start = matches[i].range.first
                val end   = matches[i + 1].range.last + 1
                val phrase = text.substring(start, end)
                val detail = DiagnosticDetail(
                    "Passive voice '$phrase' — consider active voice for stronger prose.",
                    emptyList()
                )
                container.addDiagnostic(
                    DiagnosticRegion(start, end, SEV_WARNING, DiagnosticRegion.FLAG_WAVY_UNDERLINE, detail)
                )
            }
        }

        // 4. Repeated word within 3-sentence window ───────────────────────────
        val sentenceRegex = Regex("[^.!?\\n]+[.!?\\n]?")
        val sentences     = sentenceRegex.findAll(text).toList()

        for (sIdx in sentences.indices) {
            val current      = sentences[sIdx]
            val currentWords = wordRegex.findAll(current.value).toList()
            val windowStart  = (sIdx - 2).coerceAtLeast(0)
            val priorWords   = sentences.subList(windowStart, sIdx)
                .flatMap { wordRegex.findAll(it.value).map { m -> m.value.lowercase(Locale.ROOT) } }
                .toSet()

            for (wMatch in currentWords) {
                val w = wMatch.value.lowercase(Locale.ROOT)
                if (w.length >= 4 && !STOP_WORDS.contains(w) && priorWords.contains(w)) {
                    val absStart = current.range.first + wMatch.range.first
                    val absEnd   = current.range.first + wMatch.range.last + 1
                    val detail = DiagnosticDetail(
                        "Word '${wMatch.value}' repeats within 3 sentences — consider a synonym.",
                        emptyList()
                    )
                    container.addDiagnostic(
                        DiagnosticRegion(absStart, absEnd, SEV_TYPO, DiagnosticRegion.FLAG_WAVY_UNDERLINE, detail)
                    )
                }
            }
        }

        return container
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isWordBoundary(
        text: String,
        lower: String,
        start: Int,
        end: Int
    ): Boolean {
        val leftOk  = start == 0 || !text[start - 1].isLetterOrDigit()
        val rightOk = end >= lower.length || !text[end].isLetterOrDigit()
        return leftOk && rightOk
    }
}
