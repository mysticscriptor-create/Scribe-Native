package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.engine.ProseAnalysisResult
import com.primaloptima.scribe.ui.theme.ScribeTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProseAnalysisView(
    analysis: ProseAnalysisResult,
    modifier: Modifier = Modifier,
    onJumpToSentence: ((Int) -> Unit)? = null
) {
    val colors = ScribeTheme.colors
    val shapes = ScribeTheme.shapes

    if (analysis.wordCount == 0) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = colors.interaction.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Analytics,
                            contentDescription = null,
                            tint = colors.interaction.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "No Prose to Analyze",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.content.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Start typing your chapter or notes to view live background readability, repetition, and pacing insights.",
                    fontSize = 13.sp,
                    color = colors.content.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Readability Hero Card ──────────────────────────────────────────
        item {
            Card(
                shape = shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.85f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "READABILITY GRADE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.interaction.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                analysis.fleschKincaidGradeLabel,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.content.primary
                            )
                        }

                        val (easeBg, easeFg) = when {
                            analysis.fleschReadingEase >= 70f -> Pair(colors.semantic.successContainer, colors.semantic.success)
                            analysis.fleschReadingEase >= 50f -> Pair(colors.semantic.warningContainer, colors.semantic.warning)
                            else -> Pair(colors.semantic.errorContainer, colors.semantic.error)
                        }

                        Surface(
                            shape = shapes.medium,
                            color = easeBg
                        ) {
                            Text(
                                text = "${analysis.fleschReadingEase.roundToInt()} / 100",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = easeFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Progress indicator of reading ease
                    val progressColor = when {
                        analysis.fleschReadingEase >= 70f -> colors.semantic.success
                        analysis.fleschReadingEase >= 50f -> colors.semantic.warning
                        else -> colors.semantic.error
                    }

                    LinearProgressIndicator(
                        progress = { (analysis.fleschReadingEase / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = progressColor,
                        trackColor = colors.surfaces.surfaceLowest
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricSmallItem("Flesch Ease", "${analysis.fleschReadingEase.roundToInt()} (${analysis.fleschReadingEaseLabel})")
                        MetricSmallItem("Gunning Fog", "%.1f".format(analysis.gunningFogIndex))
                        MetricSmallItem("Coleman-Liau", "%.1f".format(analysis.colemanLiauIndex))
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricSmallItem("Avg Sentence", "%.1f words".format(analysis.avgSentenceLength))
                        MetricSmallItem("Avg Word", "%.1f chars".format(analysis.avgWordLength))
                        MetricSmallItem("Avg Syllables", "%.2f / word".format(analysis.avgSyllablesPerWord))
                    }
                }
            }
        }

        // ── 2. Time & Prose Distribution Card ────────────────────────────────
        item {
            Card(
                shape = shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "PACING & PROSE BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.interaction.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shapes.medium)
                                .background(colors.surfaces.surfaceLowest.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AutoStories, null, modifier = Modifier.size(14.dp), tint = colors.interaction.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reading Time", fontSize = 11.sp, color = colors.content.secondary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatDuration(analysis.readingTimeMinutes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.content.primary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shapes.medium)
                                .background(colors.surfaces.surfaceLowest.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.RecordVoiceOver, null, modifier = Modifier.size(14.dp), tint = colors.interaction.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Speaking Time", fontSize = 11.sp, color = colors.content.secondary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatDuration(analysis.speakingTimeMinutes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.content.primary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shapes.medium)
                                .background(colors.surfaces.surfaceLowest.copy(alpha = 0.7f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.FormatAlignLeft, null, modifier = Modifier.size(14.dp), tint = colors.interaction.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Paragraphs", fontSize = 11.sp, color = colors.content.secondary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${analysis.paragraphCount}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.content.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Dialogue vs Narrative bar
                    Text("Dialogue vs. Narrative Split", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.content.primary)
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(analysis.dialoguePercentage.coerceAtLeast(0.01f))
                                .background(colors.writing.dialogue)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(analysis.narrativePercentage.coerceAtLeast(0.01f))
                                .background(colors.interaction.primary.copy(alpha = 0.35f))
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Dialogue: ${analysis.dialoguePercentage.roundToInt()}% (${analysis.dialogueWordCount}w)",
                            fontSize = 11.sp,
                            color = colors.writing.dialogue
                        )
                        Text(
                            "Narrative: ${analysis.narrativePercentage.roundToInt()}% (${analysis.narrativeWordCount}w)",
                            fontSize = 11.sp,
                            color = colors.content.secondary
                        )
                    }
                }
            }
        }

        // ── 3. Sentence Length & Variety ─────────────────────────────────────
        item {
            Card(
                shape = shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "SENTENCE VARIETY & RHYTHM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.interaction.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PacingCategoryBox("Short (<10w)", analysis.shortSentencesCount, Modifier.weight(1f))
                        PacingCategoryBox("Med (10-20w)", analysis.mediumSentencesCount, Modifier.weight(1f))
                        PacingCategoryBox("Long (21-35w)", analysis.longSentencesCount, Modifier.weight(1f))
                        PacingCategoryBox("Very Long (>35w)", analysis.veryLongSentencesCount, Modifier.weight(1f))
                    }

                    if (analysis.monotonyWarnings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = shapes.medium,
                            color = colors.semantic.warningContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, null, tint = colors.semantic.warning, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Monotony Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.semantic.warning)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${analysis.monotonyWarnings.size} instances of repeated sentence lengths found. Varying short and long sentences improves reader engagement.",
                                    fontSize = 11.sp,
                                    color = colors.content.secondary
                                )
                            }
                        }
                    }

                    // Sentence rhythm chart
                    if (analysis.sentenceLengths.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Rhythm Visualizer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.content.primary
                        )
                        Text(
                            "Each bar = one sentence. Height = word count.",
                            fontSize = 10.sp,
                            color = colors.content.secondary
                        )
                        Spacer(Modifier.height(8.dp))

                        val maxLen = analysis.sentenceLengths.max().coerceAtLeast(1)
                        val primary = colors.interaction.primary
                        val secondary = colors.interaction.secondary
                        val tertiary = colors.interaction.tertiary
                        val warningColor = colors.semantic.warning
                        val surfaceLowest = colors.surfaces.surfaceLowest

                        // Show up to 120 sentences to keep chart compact
                        val displayLengths = if (analysis.sentenceLengths.size > 120)
                            analysis.sentenceLengths.takeLast(120) else analysis.sentenceLengths

                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(surfaceLowest.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            val barCount = displayLengths.size
                            val totalWidth = size.width
                            val totalHeight = size.height
                            val gap = (totalWidth * 0.15f / barCount).coerceAtMost(2f)
                            val barWidth = ((totalWidth - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)

                            displayLengths.forEachIndexed { i, len ->
                                val fraction = len.toFloat() / maxLen
                                val barHeight = (fraction * totalHeight).coerceAtLeast(2f)
                                val x = i * (barWidth + gap)
                                val color = when {
                                    len < 10  -> tertiary.copy(alpha = 0.75f)
                                    len <= 20 -> primary.copy(alpha = 0.75f)
                                    len <= 35 -> secondary.copy(alpha = 0.85f)
                                    else      -> warningColor.copy(alpha = 0.9f)
                                }
                                drawRoundRect(
                                    color = color,
                                    topLeft = androidx.compose.ui.geometry.Offset(x, totalHeight - barHeight),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RhythmLegendDot(tertiary.copy(alpha = 0.75f), "Short")
                            RhythmLegendDot(primary.copy(alpha = 0.75f), "Medium")
                            RhythmLegendDot(secondary.copy(alpha = 0.85f), "Long")
                            RhythmLegendDot(warningColor.copy(alpha = 0.9f), "Very long")
                        }
                    }
                }
            }
        }

        // ── 4. Overused Words & Repetitions ──────────────────────────────────
        if (analysis.overusedWords.isNotEmpty() || analysis.repeatedPhrases.isNotEmpty()) {
            item {
                Card(
                    shape = shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "WORD FREQUENCY & REPETITION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.interaction.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        if (analysis.overusedWords.isNotEmpty()) {
                            Text("Frequently Occurring Words", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.content.primary)
                            Spacer(Modifier.height(8.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                analysis.overusedWords.forEach { item ->
                                    Surface(
                                        shape = shapes.pill,
                                        color = colors.surfaces.surfaceLowest.copy(alpha = 0.85f),
                                        shadowElevation = 1.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                item.word,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.content.primary
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = colors.interaction.primaryContainer
                                            ) {
                                                Text(
                                                    "${item.count}×",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.interaction.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (analysis.repeatedPhrases.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("Repeated 3-Word Phrases", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.content.primary)
                            Spacer(Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                analysis.repeatedPhrases.forEach { phrase ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shapes.medium)
                                            .background(colors.surfaces.surfaceLowest.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "\"${phrase.phrase}\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.content.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${phrase.count} times",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.interaction.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (analysis.duplicateAdjacentWords.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("Adjacent Duplicate Words", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.semantic.error)
                            Spacer(Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                analysis.duplicateAdjacentWords.forEach { dup ->
                                    Surface(
                                        shape = shapes.medium,
                                        color = colors.semantic.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "\"${dup.word} ${dup.word}\"",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.semantic.error
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                dup.preview,
                                                fontSize = 11.sp,
                                                color = colors.content.secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 5. Stylistic & Novel Diagnostics Suite ──────────────────────────
        // 5a. Passive Voice Scanner
        if (analysis.passiveVoiceMatches.isNotEmpty()) {
            item {
                Card(
                    shape = shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "PASSIVE VOICE SCANNER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.interaction.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.passiveVoiceMatches.size} passive constructions detected",
                                    fontSize = 13.sp,
                                    color = colors.content.secondary
                                )
                            }
                            Surface(
                                shape = shapes.small,
                                color = colors.semantic.warningContainer
                            ) {
                                Text(
                                    "${analysis.passiveVoiceMatches.size} flags",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.semantic.warning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Passive voice delays the subject and slows narrative momentum. Converting to active voice creates punchier action.",
                            fontSize = 11.sp,
                            color = colors.content.secondary
                        )
                        Spacer(Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.passiveVoiceMatches.take(8).forEach { match ->
                                Surface(
                                    shape = shapes.medium,
                                    color = colors.surfaces.surfaceLowest.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToSentence?.invoke(match.sentenceIndex) }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "\"${match.text}\"",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.semantic.warning
                                            )
                                            Text(
                                                "Sentence ${match.sentenceIndex + 1} ↗",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.interaction.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            match.preview,
                                            fontSize = 11.sp,
                                            color = colors.content.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (match.suggestion != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "💡 ${match.suggestion}",
                                                fontSize = 10.sp,
                                                color = colors.interaction.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5b. Filter Words & Psychological Distance
        if (analysis.filterWordsMatches.isNotEmpty()) {
            item {
                Card(
                    shape = shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "FILTER WORDS & POV DISTANCE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.interaction.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.filterWordsMatches.size} sensory filters found",
                                    fontSize = 13.sp,
                                    color = colors.content.secondary
                                )
                            }
                            Surface(
                                shape = shapes.small,
                                color = colors.interaction.primaryContainer
                            ) {
                                Text(
                                    "${analysis.filterWordsMatches.size} filters",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.interaction.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Filter words ('she heard', 'he felt', 'noticed that') place a lens between the reader and the experience. Deleting them creates immediate immersion.",
                            fontSize = 11.sp,
                            color = colors.content.secondary
                        )
                        Spacer(Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.filterWordsMatches.take(8).forEach { match ->
                                Surface(
                                    shape = shapes.medium,
                                    color = colors.surfaces.surfaceLowest.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToSentence?.invoke(match.sentenceIndex) }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "\"${match.text}\"",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.interaction.primary
                                            )
                                            Text(
                                                "Sentence ${match.sentenceIndex + 1} ↗",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.interaction.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            match.preview,
                                            fontSize = 11.sp,
                                            color = colors.content.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (match.suggestion != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "💡 ${match.suggestion}",
                                                fontSize = 10.sp,
                                                color = colors.interaction.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5c. Weak Adverbs Scanner
        if (analysis.weakAdverbsMatches.isNotEmpty()) {
            item {
                Card(
                    shape = shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "WEAK ADVERBS (-LY) INSPECTOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.interaction.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.weakAdverbsMatches.size} adverbs flagged",
                                    fontSize = 13.sp,
                                    color = colors.content.secondary
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Adverbs often prop up weak verbs. Replace 'ran quickly' with 'sprinted', or 'said quietly' with 'whispered'.",
                            fontSize = 11.sp,
                            color = colors.content.secondary
                        )
                        Spacer(Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.weakAdverbsMatches.map { it.text }.distinct().take(16).forEach { adverb ->
                                Surface(
                                    shape = shapes.pill,
                                    color = colors.surfaces.surfaceLowest.copy(alpha = 0.85f),
                                    shadowElevation = 1.dp
                                ) {
                                    Text(
                                        adverb,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.content.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 6. Scene Inlays & Chapter Breakdown ───────────────────────────────
        if (analysis.sceneSections.isNotEmpty()) {
            item {
                Card(
                    shape = shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "SCENE INLAYS & TARGETS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.interaction.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${analysis.sceneSections.size} scene sections in chapter",
                                    fontSize = 13.sp,
                                    color = colors.content.secondary
                                )
                            }
                            Icon(
                                Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                tint = colors.interaction.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            analysis.sceneSections.forEach { scene ->
                                Surface(
                                    shape = shapes.medium,
                                    color = colors.surfaces.surfaceLowest.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colors.interaction.primaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            "${scene.sceneIndex}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.interaction.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    scene.sceneTitle ?: "Scene ${scene.sceneIndex}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.content.primary
                                                )
                                            }

                                            if (scene.povCharacter != null || scene.location != null) {
                                                Spacer(Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (scene.povCharacter != null) {
                                                        Text(
                                                            "👤 POV: ${scene.povCharacter}",
                                                            fontSize = 11.sp,
                                                            color = colors.interaction.primary
                                                        )
                                                    }
                                                    if (scene.location != null) {
                                                        Text(
                                                            "📍 ${scene.location}",
                                                            fontSize = 11.sp,
                                                            color = colors.content.secondary
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Inlay Hint Badge: [1,420 words · 5 min read]
                                        Surface(
                                            shape = shapes.small,
                                            color = colors.surfaces.surfaceRaised
                                        ) {
                                            Text(
                                                "[ ${scene.wordCount} words · ${formatDuration(scene.readingTimeMinutes)} ]",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.content.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 7. Lexical Diversity Card ─────────────────────────────────────────
        item {
            Card(
                shape = shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colors.surfaces.surfaceRaised.copy(alpha = 0.65f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "VOCABULARY DIVERSITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.interaction.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${analysis.uniqueWordCount} unique words (${(analysis.lexicalDiversity * 100f).roundToInt()}% diversity)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.content.primary
                        )
                    }

                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = colors.interaction.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricSmallItem(label: String, value: String) {
    val colors = ScribeTheme.colors
    Column {
        Text(label, fontSize = 10.sp, color = colors.content.secondary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.content.primary)
    }
}

@Composable
private fun PacingCategoryBox(label: String, count: Int, modifier: Modifier = Modifier) {
    val colors = ScribeTheme.colors
    val shapes = ScribeTheme.shapes
    Box(
        modifier = modifier
            .clip(shapes.medium)
            .background(colors.surfaces.surfaceLowest.copy(alpha = 0.7f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = colors.content.secondary, textAlign = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.content.primary)
        }
    }
}

@Composable
private fun RhythmLegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    val colors = ScribeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 9.sp, color = colors.content.secondary)
    }
}

private fun formatDuration(minutesFloat: Float): String {
    val totalSeconds = (minutesFloat * 60f).roundToInt()
    if (totalSeconds < 60) return "< 1 min"
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return if (mins >= 60) {
        val hours = mins / 60
        val remMins = mins % 60
        "${hours}h ${remMins}m"
    } else {
        "${mins}m ${secs}s"
    }
}
