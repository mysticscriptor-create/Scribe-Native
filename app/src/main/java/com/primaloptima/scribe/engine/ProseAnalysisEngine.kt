package com.primaloptima.scribe.engine

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

@Immutable
data class WordFrequency(
    val word: String,
    val count: Int,
    val percentage: Float
)

@Immutable
data class PhraseFrequency(
    val phrase: String,
    val count: Int
)

@Immutable
data class DuplicateWordAlert(
    val word: String,
    val sentenceIndex: Int,
    val preview: String
)

@Immutable
data class MonotonyAlert(
    val startSentenceIndex: Int,
    val sentenceCount: Int,
    val avgLength: Int,
    val preview: String
)

enum class DiagnosticCategory(val displayName: String) {
    PASSIVE_VOICE("Passive Voice"),
    WEAK_ADVERB("Weak Adverb"),
    FILTER_WORD("Filter Word"),
    REPEATED_WORD("Repeated Word"),
    MONOTONY("Sentence Monotony")
}

@Immutable
data class DiagnosticMatch(
    val category: DiagnosticCategory,
    val text: String,
    val preview: String,
    val explanation: String,
    val suggestion: String? = null,
    val sentenceIndex: Int = 0,
    val charOffset: Int = 0,
    val length: Int = 0
)

@Immutable
data class SceneSectionInfo(
    val sceneIndex: Int,
    val lineIndex: Int,
    val marker: String,
    val wordCount: Int,
    val readingTimeMinutes: Float,
    val povCharacter: String? = null,
    val location: String? = null,
    val sceneTitle: String? = null
)

@Immutable
data class ProseAnalysisResult(
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val sentenceCount: Int = 0,
    val paragraphCount: Int = 0,
    val syllableCount: Int = 0,
    val readingTimeMinutes: Float = 0f,
    val speakingTimeMinutes: Float = 0f,
    val avgSentenceLength: Float = 0f,
    val avgWordLength: Float = 0f,
    val avgSyllablesPerWord: Float = 0f,

    // Readability
    val fleschReadingEase: Float = 0f,
    val fleschReadingEaseLabel: String = "N/A",
    val fleschKincaidGrade: Float = 0f,
    val fleschKincaidGradeLabel: String = "N/A",
    val gunningFogIndex: Float = 0f,
    val colemanLiauIndex: Float = 0f,

    // Repetition & Vocabulary
    val overusedWords: List<WordFrequency> = emptyList(),
    val repeatedPhrases: List<PhraseFrequency> = emptyList(),
    val duplicateAdjacentWords: List<DuplicateWordAlert> = emptyList(),
    val uniqueWordCount: Int = 0,
    val lexicalDiversity: Float = 0f, // Type-Token Ratio (0.0 - 1.0)

    // Pacing & Sentence Variety
    val shortSentencesCount: Int = 0,      // < 10 words
    val mediumSentencesCount: Int = 0,     // 10 - 20 words
    val longSentencesCount: Int = 0,       // 21 - 35 words
    val veryLongSentencesCount: Int = 0,   // > 35 words
    val sentenceLengths: List<Int> = emptyList(),
    val monotonyWarnings: List<MonotonyAlert> = emptyList(),

    // Dialogue & Prose Balance
    val dialogueWordCount: Int = 0,
    val narrativeWordCount: Int = 0,
    val dialoguePercentage: Float = 0f,
    val narrativePercentage: Float = 0f,

    // Novel Stylistic Diagnostics
    val passiveVoiceMatches: List<DiagnosticMatch> = emptyList(),
    val weakAdverbsMatches: List<DiagnosticMatch> = emptyList(),
    val filterWordsMatches: List<DiagnosticMatch> = emptyList(),
    val repeatedWordMatches: List<DiagnosticMatch> = emptyList(),

    // Novel Inlay Hints (Scene Sections & Word Targets)
    val sceneSections: List<SceneSectionInfo> = emptyList()
)

object ProseAnalysisEngine {

    private val STOP_WORDS = hashSetOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
        "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
        "but", "by", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't",
        "doing", "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't",
        "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
        "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i", "i'd", "i'll",
        "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself", "let's",
        "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on",
        "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
        "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some",
        "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this",
        "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we",
        "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's",
        "where", "where's", "which", "while", "who", "who's", "whom", "why", "why's", "with",
        "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your",
        "yours", "yourself", "yourselves", "said", "asked", "replied", "looked"
    )

    private val PASSIVE_AUXILIARIES = hashSetOf(
        "is", "are", "was", "were", "be", "been", "being", "am"
    )

    private val COMMON_PAST_PARTICIPLES = hashSetOf(
        "seen", "taken", "heard", "felt", "made", "given", "known", "told", "found", "thought",
        "left", "kept", "written", "brought", "lost", "broken", "chosen", "driven", "forgotten",
        "drawn", "built", "caught", "sent", "struck", "held", "shot", "killed", "attacked",
        "pushed", "pulled", "watched", "opened", "closed", "moved", "touched", "dropped", "lifted",
        "placed", "followed", "chased", "cornered", "surrounded", "destroyed", "damaged", "fixed",
        "discovered", "abandoned", "hidden", "silenced", "noticed", "warned", "revealed", "summoned",
        "greeted", "guided", "captured", "forced", "carried", "hurled", "locked", "unlocked",
        "swallowed", "dragged", "struck", "struck", "shaken", "thrown", "cast", "drawn"
    )

    private val WEAK_ADVERBS = hashSetOf(
        "suddenly", "very", "really", "quietly", "quickly", "slowly", "softly", "angrily", "happily",
        "sadly", "nervously", "desperately", "violently", "barely", "almost", "completely", "totally",
        "absolutely", "actually", "certainly", "clearly", "eventually", "instantly", "slightly",
        "somewhat", "terribly", "truly", "utterly", "wildly", "calmly", "carefully", "hastily",
        "loudly", "peacefully", "sharply", "firmly", "gently", "heavily", "lightly", "tightly"
    )

    private val FILTER_PHRASES = listOf(
        "felt like" to "Direct sensory perception (e.g. replace 'She felt like the room grew cold' with 'The room grew cold').",
        "felt that" to "Internal filtering puts a barrier between reader and sensation.",
        "felt a" to "Filter phrase creates emotional distance.",
        "noticed that" to "Show what was noticed directly instead of filtering through the character.",
        "noticed a" to "Remove the filter word for closer POV immediacy.",
        "noticed the" to "Direct observation is stronger than filtered awareness.",
        "saw that" to "Describe the visual detail directly instead of saying the character saw it.",
        "saw a" to "Eliminate the visual filter for vivid pacing.",
        "saw the" to "State the action or scenery directly.",
        "heard that" to "Auditory filtering slows scene velocity.",
        "heard a" to "Immerse reader directly in the sound (e.g. 'A floorboard creaked' instead of 'He heard a floorboard creak').",
        "heard the" to "Direct sound description carries more impact.",
        "wondered if" to "Express the thought or question directly in free indirect discourse.",
        "wondered whether" to "Present the dilemma directly in prose.",
        "decided to" to "Show the character performing the action rather than deciding it.",
        "realized that" to "State the truth or discovery directly to maintain narrative momentum.",
        "seemed to" to "Weakens certainty. Declare the event directly.",
        "watched as" to "Describe the action unfolding directly without the camera-lens effect.",
        "could hear" to "Replace 'could hear' with the direct auditory verb or sound.",
        "could see" to "Replace 'could see' with the direct visual imagery.",
        "could feel" to "Direct sensation provides deeper reader immersion.",
        "could tell" to "Show the evidence or emotion rather than telling the inference."
    )

    suspend fun analyze(text: String): ProseAnalysisResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext ProseAnalysisResult()
        }

        val totalLength = text.length
        val paragraphs = text.split(Regex("\n+")).filter { it.isNotBlank() }
        val paragraphCount = paragraphs.size.coerceAtLeast(1)

        // 1. Sentence splitting and tokenization
        val sentences = splitSentences(text)
        val sentenceCount = sentences.size.coerceAtLeast(1)

        val words = mutableListOf<String>()
        val wordsLower = mutableListOf<String>()
        val sentenceLengths = mutableListOf<Int>()
        var totalSyllables = 0
        var complexWordCount = 0 // for Gunning Fog (3+ syllables)
        var totalLetters = 0

        // Dialogue extraction: words enclosed in quotes (", ', “, ”, «, »)
        var dialogueWords = 0
        var inQuotes = false

        val duplicateAdjacent = mutableListOf<DuplicateWordAlert>()

        for (sIdx in sentences.indices) {
            coroutineContext.ensureActive()
            val sentence = sentences[sIdx]
            val sWords = extractWords(sentence)
            sentenceLengths.add(sWords.size)

            var prevWordLower = ""

            for (w in sWords) {
                words.add(w)
                val lower = w.lowercase(Locale.ROOT)
                wordsLower.add(lower)
                totalLetters += w.count { it.isLetter() }

                val syl = countSyllables(lower)
                totalSyllables += syl
                if (syl >= 3 && !isCompoundOrProperNoun(w)) {
                    complexWordCount++
                }

                // Check for immediate repetition ("the the", "had had")
                if (lower == prevWordLower && lower.length > 1 && !isAllowedDoubleWord(lower)) {
                    duplicateAdjacent.add(
                        DuplicateWordAlert(
                            word = w,
                            sentenceIndex = sIdx,
                            preview = sentence.take(60).trim()
                        )
                    )
                }
                prevWordLower = lower
            }
        }

        // Dialogue vs Narrative word counter
        for (i in 0 until text.length) {
            val c = text[i]
            if (c == '"' || c == '“' || c == '”' || c == '«' || c == '»') {
                inQuotes = !inQuotes
            } else if (c.isLetterOrDigit()) {
                if (inQuotes) {
                    val prevIsSpace = (i == 0 || !text[i - 1].isLetterOrDigit())
                    if (prevIsSpace) dialogueWords++
                }
            }
        }

        val totalWords = words.size.coerceAtLeast(1)
        val narrativeWords = (totalWords - dialogueWords).coerceAtLeast(0)
        val dialoguePercent = (dialogueWords.toFloat() / totalWords * 100f).coerceIn(0f, 100f)
        val narrativePercent = (100f - dialoguePercent).coerceIn(0f, 100f)

        // Reading & Speaking times
        val readingTime = totalWords / 225f
        val speakingTime = totalWords / 150f

        // Averages
        val avgSentenceLen = totalWords.toFloat() / sentenceCount
        val avgWordLen = if (totalWords > 0) totalLetters.toFloat() / totalWords else 0f
        val avgSyllablesPerWord = if (totalWords > 0) totalSyllables.toFloat() / totalWords else 0f

        // Readability formulas
        // Flesch Reading Ease: 206.835 - 1.015 * (words/sentence) - 84.6 * (syllables/word)
        val fleschEase = (206.835f - (1.015f * avgSentenceLen) - (84.6f * avgSyllablesPerWord)).coerceIn(0f, 100f)
        val fleschEaseLabel = when {
            fleschEase >= 90f -> "Very Easy"
            fleschEase >= 80f -> "Easy"
            fleschEase >= 70f -> "Fairly Easy"
            fleschEase >= 60f -> "Standard"
            fleschEase >= 50f -> "Fairly Difficult"
            fleschEase >= 30f -> "Difficult"
            else -> "Very Confusing"
        }

        // Flesch-Kincaid Grade Level: 0.39 * (words/sentence) + 11.8 * (syllables/word) - 15.59
        val fkGrade = (0.39f * avgSentenceLen + 11.8f * avgSyllablesPerWord - 15.59f).coerceAtLeast(0f)
        val fkGradeLabel = when {
            fkGrade <= 5f -> "Elementary"
            fkGrade <= 8f -> "Middle School (${fkGrade.roundToInt()}th Grade)"
            fkGrade <= 12f -> "High School (${fkGrade.roundToInt()}th Grade)"
            fkGrade <= 16f -> "College Level"
            else -> "Graduate / Academic"
        }

        // Gunning Fog Index: 0.4 * ((words/sentence) + 100 * (complex words / words))
        val gunningFog = (0.4f * (avgSentenceLen + (100f * complexWordCount.toFloat() / totalWords))).coerceAtLeast(0f)

        // Coleman-Liau: 0.0588 * L - 0.296 * S - 15.8 (L = avg letters per 100 words, S = avg sentences per 100 words)
        val l = (totalLetters.toFloat() / totalWords) * 100f
        val s = (sentenceCount.toFloat() / totalWords) * 100f
        val colemanLiau = (0.0588f * l - 0.296f * s - 15.8f).coerceAtLeast(0f)

        // Word frequencies & repetition (excluding stopwords)
        val freqMap = mutableMapOf<String, Int>()
        for (w in wordsLower) {
            if (w.length > 2 && !STOP_WORDS.contains(w)) {
                freqMap[w] = (freqMap[w] ?: 0) + 1
            }
        }
        val overusedWords = freqMap.entries
            .filter { it.value >= 3 && (it.value.toFloat() / totalWords) > 0.005f }
            .sortedByDescending { it.value }
            .take(10)
            .map {
                WordFrequency(
                    word = it.key,
                    count = it.value,
                    percentage = (it.value.toFloat() / totalWords) * 100f
                )
            }

        // Repeated 2-word and 3-word n-gram phrases
        val repeatedPhrases = findRepeatedPhrases(wordsLower, totalWords)

        // Lexical Diversity (Type-Token Ratio)
        val uniqueWords = wordsLower.toHashSet().size
        val typeTokenRatio = if (totalWords > 0) uniqueWords.toFloat() / totalWords else 0f

        // Pacing & Sentence distribution
        var shortCount = 0
        var mediumCount = 0
        var longCount = 0
        var veryLongCount = 0

        for (len in sentenceLengths) {
            when {
                len < 10 -> shortCount++
                len <= 20 -> mediumCount++
                len <= 35 -> longCount++
                else -> veryLongCount++
            }
        }

        // Monotony detection: 4 or more consecutive sentences with nearly identical length (within 2 words)
        val monotonyAlerts = mutableListOf<MonotonyAlert>()
        var streakStart = 0
        var streakLen = 1

        for (i in 1 until sentenceLengths.size) {
            val diff = Math.abs(sentenceLengths[i] - sentenceLengths[i - 1])
            if (diff <= 2 && sentenceLengths[i] > 3) {
                streakLen++
                if (streakLen == 4) {
                    val sub = sentences.subList(streakStart, (streakStart + 4).coerceAtMost(sentences.size))
                    val preview = sub.joinToString(" ") { it.trim() }.take(90)
                    monotonyAlerts.add(
                        MonotonyAlert(
                            startSentenceIndex = streakStart,
                            sentenceCount = 4,
                            avgLength = sentenceLengths[i],
                            preview = "$preview..."
                        )
                    )
                }
            } else {
                streakStart = i
                streakLen = 1
            }
        }

        // ── 5. Stylistic Prose Diagnostics ────────────────────────────────────
        val passiveMatches = mutableListOf<DiagnosticMatch>()
        val adverbMatches = mutableListOf<DiagnosticMatch>()
        val filterMatches = mutableListOf<DiagnosticMatch>()
        val repeatedMatches = mutableListOf<DiagnosticMatch>()

        // 5a. Passive Voice & Filter Words & Weak Adverbs per sentence
        for (sIdx in sentences.indices) {
            val sentence = sentences[sIdx]
            val sLower = sentence.lowercase(Locale.ROOT)
            val sWords = extractWords(sentence)

            // Passive voice scan: aux + (optional adverb) + participle
            for (wIdx in 0 until sWords.size - 1) {
                val w1 = sWords[wIdx].lowercase(Locale.ROOT)
                val w2 = sWords[wIdx + 1].lowercase(Locale.ROOT)
                val isAux = PASSIVE_AUXILIARIES.contains(w1)

                if (isAux) {
                    val isParticiple = COMMON_PAST_PARTICIPLES.contains(w2) ||
                            (w2.endsWith("ed") && w2.length > 4 && !STOP_WORDS.contains(w2)) ||
                            (w2.endsWith("en") && w2.length > 4 && !STOP_WORDS.contains(w2))

                    if (isParticiple) {
                        val phrase = "${sWords[wIdx]} ${sWords[wIdx + 1]}"
                        passiveMatches.add(
                            DiagnosticMatch(
                                category = DiagnosticCategory.PASSIVE_VOICE,
                                text = phrase,
                                preview = sentence.take(75).trim(),
                                explanation = "Passive construction: '$phrase' conceals or delays the actor.",
                                suggestion = "Reframe with direct subject action (e.g., replace 'the door was opened by her' with 'she opened the door').",
                                sentenceIndex = sIdx
                            )
                        )
                    } else if (wIdx + 2 < sWords.size) {
                        // Check aux + adverb + participle: e.g. "was quietly opened"
                        val w3 = sWords[wIdx + 2].lowercase(Locale.ROOT)
                        if (WEAK_ADVERBS.contains(w2) || w2.endsWith("ly")) {
                            val isParticiple3 = COMMON_PAST_PARTICIPLES.contains(w3) ||
                                    (w3.endsWith("ed") && w3.length > 4) ||
                                    (w3.endsWith("en") && w3.length > 4)
                            if (isParticiple3) {
                                val phrase = "${sWords[wIdx]} ${sWords[wIdx + 1]} ${sWords[wIdx + 2]}"
                                passiveMatches.add(
                                    DiagnosticMatch(
                                        category = DiagnosticCategory.PASSIVE_VOICE,
                                        text = phrase,
                                        preview = sentence.take(75).trim(),
                                        explanation = "Passive voice with modifier: '$phrase'.",
                                        suggestion = "Convert to active voice with a vivid verb.",
                                        sentenceIndex = sIdx
                                    )
                                )
                            }
                        }
                    }
                }

                // Weak Adverbs scan
                if (WEAK_ADVERBS.contains(w1) || (w1.endsWith("ly") && w1.length > 4 && !isAllowedLyWord(w1))) {
                    adverbMatches.add(
                        DiagnosticMatch(
                            category = DiagnosticCategory.WEAK_ADVERB,
                            text = sWords[wIdx],
                            preview = sentence.take(75).trim(),
                            explanation = "Adverb '${sWords[wIdx]}' may weaken verb impact.",
                            suggestion = "Consider replacing with a stronger, more descriptive action verb.",
                            sentenceIndex = sIdx
                        )
                    )
                }
            }

            // Filter phrase scan
            for ((filterPhrase, explanation) in FILTER_PHRASES) {
                if (sLower.contains(filterPhrase)) {
                    filterMatches.add(
                        DiagnosticMatch(
                            category = DiagnosticCategory.FILTER_WORD,
                            text = filterPhrase,
                            preview = sentence.take(75).trim(),
                            explanation = explanation,
                            suggestion = "Eliminate '$filterPhrase' to place the reader directly inside the scene.",
                            sentenceIndex = sIdx
                        )
                    )
                }
            }
        }

        // 5b. Repeated word scanner (3-sentence rolling window)
        for (i in 0 until sentences.size) {
            val windowEnd = (i + 3).coerceAtMost(sentences.size)
            val windowWords = mutableMapOf<String, MutableList<Int>>()
            for (j in i until windowEnd) {
                val wordsInS = extractWords(sentences[j])
                for (w in wordsInS) {
                    val lower = w.lowercase(Locale.ROOT)
                    if (lower.length > 3 && !STOP_WORDS.contains(lower)) {
                        windowWords.getOrPut(lower) { mutableListOf() }.add(j)
                    }
                }
            }
            for ((word, sentIndices) in windowWords) {
                if (sentIndices.size >= 3 && sentIndices.distinct().size >= 2) {
                    val firstSentence = sentences[sentIndices.first()]
                    repeatedMatches.add(
                        DiagnosticMatch(
                            category = DiagnosticCategory.REPEATED_WORD,
                            text = word,
                            preview = firstSentence.take(75).trim(),
                            explanation = "Word '$word' appears ${sentIndices.size} times in adjacent sentences.",
                            suggestion = "Vary your vocabulary or use pronouns/synonyms to avoid reader fatigue.",
                            sentenceIndex = sentIndices.first()
                        )
                    )
                }
            }
        }

        // ── 6. Scene Sections Inlay Parsing ──────────────────────────────────
        val sceneSections = parseSceneSections(text)

        ProseAnalysisResult(
            wordCount = totalWords,
            charCount = totalLength,
            sentenceCount = sentenceCount,
            paragraphCount = paragraphCount,
            syllableCount = totalSyllables,
            readingTimeMinutes = readingTime,
            speakingTimeMinutes = speakingTime,
            avgSentenceLength = avgSentenceLen,
            avgWordLength = avgWordLen,
            avgSyllablesPerWord = avgSyllablesPerWord,
            fleschReadingEase = fleschEase,
            fleschReadingEaseLabel = fleschEaseLabel,
            fleschKincaidGrade = fkGrade,
            fleschKincaidGradeLabel = fkGradeLabel,
            gunningFogIndex = gunningFog,
            colemanLiauIndex = colemanLiau,
            overusedWords = overusedWords,
            repeatedPhrases = repeatedPhrases,
            duplicateAdjacentWords = duplicateAdjacent.take(6),
            uniqueWordCount = uniqueWords,
            lexicalDiversity = typeTokenRatio,
            shortSentencesCount = shortCount,
            mediumSentencesCount = mediumCount,
            longSentencesCount = longCount,
            veryLongSentencesCount = veryLongCount,
            sentenceLengths = sentenceLengths.take(50),
            monotonyWarnings = monotonyAlerts.take(4),
            dialogueWordCount = dialogueWords,
            narrativeWordCount = narrativeWords,
            dialoguePercentage = dialoguePercent,
            narrativePercentage = narrativePercent,
            passiveVoiceMatches = passiveMatches.distinctBy { "${it.sentenceIndex}_${it.text}" }.take(25),
            weakAdverbsMatches = adverbMatches.distinctBy { "${it.sentenceIndex}_${it.text}" }.take(25),
            filterWordsMatches = filterMatches.distinctBy { "${it.sentenceIndex}_${it.text}" }.take(25),
            repeatedWordMatches = repeatedMatches.distinctBy { it.text }.take(15),
            sceneSections = sceneSections
        )
    }

    private fun isAllowedLyWord(word: String): Boolean {
        return word == "only" || word == "early" || word == "holy" || word == "ugly" ||
                word == "family" || word == "daily" || word == "weekly" || word == "monthly" ||
                word == "friendly" || word == "lovely" || word == "lonely" || word == "silly"
    }

    private fun parseSceneSections(text: String): List<SceneSectionInfo> {
        val lines = text.split("\n")
        val sections = mutableListOf<SceneSectionInfo>()
        var currentSceneStartLine = 0
        var currentMarker = "Start of Chapter"
        var currentPov: String? = null
        var currentLocation: String? = null
        var currentTitle: String? = null
        var sceneCounter = 1

        val sceneAccumulator = StringBuilder()

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx].trim()
            val isSceneBreak = line == "***" || line == "* * *" || line == "---" || line == "- - -" ||
                    line.startsWith("###") || line.startsWith("<!-- scene:") || line.startsWith("[Scene") ||
                    line.startsWith("/scene")

            if (isSceneBreak && lineIdx > 0) {
                // Finish preceding section
                val prevWords = extractWords(sceneAccumulator.toString()).size
                val prevRt = prevWords / 225f
                sections.add(
                    SceneSectionInfo(
                        sceneIndex = sceneCounter++,
                        lineIndex = currentSceneStartLine,
                        marker = currentMarker,
                        wordCount = prevWords,
                        readingTimeMinutes = prevRt,
                        povCharacter = currentPov,
                        location = currentLocation,
                        sceneTitle = currentTitle
                    )
                )

                // Start new section
                sceneAccumulator.clear()
                currentSceneStartLine = lineIdx
                currentMarker = line
                currentPov = extractTagValue(line, "POV") ?: extractTagValue(line, "pov")
                currentLocation = extractTagValue(line, "Loc") ?: extractTagValue(line, "loc") ?: extractTagValue(line, "Location")
                currentTitle = if (line.startsWith("###")) line.removePrefix("###").trim() else "Scene $sceneCounter"
            } else {
                sceneAccumulator.append(lines[lineIdx]).append("\n")
                if (line.startsWith("<!-- scene:") || line.startsWith("/scene")) {
                    currentPov = extractTagValue(line, "POV") ?: extractTagValue(line, "pov") ?: currentPov
                    currentLocation = extractTagValue(line, "Loc") ?: extractTagValue(line, "loc") ?: currentLocation
                }
            }
        }

        // Add the last or only section
        val lastWords = extractWords(sceneAccumulator.toString()).size
        val lastRt = lastWords / 225f
        sections.add(
            SceneSectionInfo(
                sceneIndex = sceneCounter,
                lineIndex = currentSceneStartLine,
                marker = currentMarker,
                wordCount = lastWords,
                readingTimeMinutes = lastRt,
                povCharacter = currentPov,
                location = currentLocation,
                sceneTitle = currentTitle ?: if (sections.isEmpty()) "Full Chapter" else "Scene $sceneCounter"
            )
        )

        return sections
    }

    private fun extractTagValue(text: String, tag: String): String? {
        val regex = Regex("""$tag\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Check if it's an abbreviation (e.g. "Mr.", "Dr.", "e.g.", "1.5")
                val isAbbrev = (c == '.' && i > 0 && i + 1 < text.length && text[i + 1].isLetterOrDigit())
                if (!isAbbrev) {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty()) {
                        result.add(s)
                    }
                    sb.clear()
                }
            }
            i++
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun extractWords(sentence: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (i in 0 until sentence.length) {
            val c = sentence[i]
            if (c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-') {
                sb.append(c)
            } else {
                if (sb.isNotEmpty()) {
                    val w = sb.toString().trim('\'', '’', '-')
                    if (w.isNotEmpty()) result.add(w)
                    sb.clear()
                }
            }
        }
        if (sb.isNotEmpty()) {
            val w = sb.toString().trim('\'', '’', '-')
            if (w.isNotEmpty()) result.add(w)
        }
        return result
    }

    fun countSyllables(word: String): Int {
        val clean = word.lowercase(Locale.ROOT).filter { it.isLetter() }
        if (clean.length <= 3) return 1

        var count = 0
        var prevIsVowel = false
        val vowels = "aeiouy"

        for (i in clean.indices) {
            val isVowel = vowels.contains(clean[i])
            if (isVowel && !prevIsVowel) {
                count++
            }
            prevIsVowel = isVowel
        }

        // Silent 'e' at the end
        if (clean.endsWith("e") && !clean.endsWith("le") && count > 1) {
            count--
        }
        // '-ed' ending usually not a syllable unless preceded by 't' or 'd'
        if (clean.endsWith("ed") && !clean.endsWith("ted") && !clean.endsWith("ded") && count > 1) {
            count--
        }

        return count.coerceAtLeast(1)
    }

    private fun isCompoundOrProperNoun(word: String): Boolean {
        return word.isNotEmpty() && word[0].isUpperCase()
    }

    private fun isAllowedDoubleWord(word: String): Boolean {
        return word == "that" || word == "had" // e.g. "He knew that that was true", "He had had enough"
    }

    private fun findRepeatedPhrases(words: List<String>, totalWords: Int): List<PhraseFrequency> {
        if (words.size < 6) return emptyList()
        val phraseCounts = mutableMapOf<String, Int>()

        // 3-word n-grams
        for (i in 0 until words.size - 2) {
            val w1 = words[i]
            val w2 = words[i + 1]
            val w3 = words[i + 2]
            // Require at least one non-stop word
            if (!STOP_WORDS.contains(w1) || !STOP_WORDS.contains(w2) || !STOP_WORDS.contains(w3)) {
                val phrase = "$w1 $w2 $w3"
                phraseCounts[phrase] = (phraseCounts[phrase] ?: 0) + 1
            }
        }

        return phraseCounts.entries
            .filter { it.value >= 3 }
            .sortedByDescending { it.value }
            .take(6)
            .map { PhraseFrequency(it.key, it.value) }
    }
}
