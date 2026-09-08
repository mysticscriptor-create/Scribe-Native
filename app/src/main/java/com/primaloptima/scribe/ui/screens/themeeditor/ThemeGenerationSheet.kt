package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.ThemeGenerationEngine
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.ImageUnderstanding

/**
 * Intelligent Image Theme Studio — The complete Generation Result & Comparison experience (Phase 18).
 * Enables the user to:
 * 1. Inspect measured image understanding characteristics (tonal key, temperature, chroma).
 * 2. Compare 4 coherent recipes (Balanced, Atmospheric, Ink, Expressive) with live previews.
 * 3. Switch candidate seed focus instantaneously without re-quantizing.
 * 4. Adjust image influence (Subtle, Balanced, Strong) and writing character.
 * 5. Choose artwork presentation mode (Theme Only, Theme + Image, Theme + Glass).
 * 6. Toggle Dark / Light appearance instantaneously.
 * 7. Apply the selected theme into the editor draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGenerationSheet(
    draft: ThemeEditorDraft,
    understanding: ImageUnderstanding,
    onDraftChange: (ThemeEditorDraft) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val resolvedColors = remember(draft) { draft.resolveColors() }
        val isDark = remember(draft.bgHex) { ThemeManager.isDarkColor(draft.bgHex) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Theme Generation Studio",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Intelligent multi-recipe derivation from artwork",
                                fontSize = 11.sp,
                                color = ScribeTheme.colors.content.secondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = onApply,
                            shape = ScribeTheme.shapes.button,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Image Analysis Characteristics Header ─────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.cardSmall,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Artwork Character Analysis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScribeTheme.colors.content.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AnalysisBadge(label = understanding.tonalCharacter.label, modifier = Modifier.weight(1f))
                            AnalysisBadge(label = understanding.temperatureBias.label, modifier = Modifier.weight(1f))
                            AnalysisBadge(label = understanding.chromaticCharacter.label, modifier = Modifier.weight(1f))
                            AnalysisBadge(label = understanding.paletteDiversity.label, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ── Live Scribe Preview Stage ─────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Scribe Preview",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ScribeTheme.colors.content.primary
                            )

                            // Polarity Toggle
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = isDark,
                                    onClick = { onDraftChange(draft.withPolarity(isDark = true)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Dark", fontSize = 11.sp) },
                                    shape = ScribeTheme.shapes.themeEditorControl
                                )
                                FilterChip(
                                    selected = !isDark,
                                    onClick = { onDraftChange(draft.withPolarity(isDark = false)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Light", fontSize = 11.sp) },
                                    shape = ScribeTheme.shapes.themeEditorControl
                                )
                            }
                        }

                        ThemePreviewStage(
                            colors = resolvedColors,
                            themeName = draft.name.ifBlank { "Generated Preview" },
                            fontFamily = draft.fontFamily,
                            fontSize = draft.fontSize,
                            lineHeight = draft.lineHeight,
                            textAlignment = draft.textAlignment,
                            sideMargins = draft.sideMargins,
                            bgMode = draft.bgMode,
                            bgUri = draft.bgUri,
                            bgOpacity = draft.bgOpacity,
                            blurIntensity = draft.blurIntensity,
                            modifier = Modifier.height(210.dp)
                        )
                    }
                }

                // ── 1. Candidate Focus Swatch Strip ───────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        CandidateSwatchStrip(
                            candidates = understanding.rankedCandidates,
                            selectedCandidate = draft.activeCandidate,
                            onSelectCandidate = { candidate ->
                                onDraftChange(draft.withCandidateSelection(candidate))
                            }
                        )
                    }
                }

                // ── 2. Recipe Selection Comparison ────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Theme Interpretation Recipes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScribeTheme.colors.content.primary
                        )
                        Text(
                            text = "Compare four distinct stylistic balance strategies derived deterministically from the artwork.",
                            fontSize = 12.sp,
                            color = ScribeTheme.colors.content.secondary
                        )

                        RecipeComparisonCards(
                            activeRecipe = draft.activeRecipe,
                            understanding = understanding,
                            candidateColor = draft.activeCandidate,
                            isDark = isDark,
                            influence = draft.activeInfluence,
                            writingCharacter = draft.activeWritingCharacter,
                            onSelectRecipe = { recipe ->
                                onDraftChange(draft.withRecipe(recipe))
                            }
                        )
                    }
                }

                // ── 3. Image Influence & Writing Character ────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ImageInfluenceSelector(
                            activeInfluence = draft.activeInfluence,
                            onSelectInfluence = { influence ->
                                onDraftChange(draft.withInfluence(influence))
                            }
                        )

                        HorizontalDivider(color = ScribeTheme.colors.borders.subtle)

                        WritingCharacterSelector(
                            activeCharacter = draft.activeWritingCharacter,
                            onSelectCharacter = { character ->
                                onDraftChange(draft.withWritingCharacter(character))
                            }
                        )
                    }
                }

                // ── 4. Presentation Relationship Mode ─────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        RelationshipModeSelector(
                            activeMode = draft.getRelationshipMode(),
                            onSelectMode = { mode ->
                                onDraftChange(draft.withRelationshipMode(mode))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Bottom Action Row ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = ScribeTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onApply,
                        shape = ScribeTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply to Theme", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AnalysisBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = ScribeTheme.shapes.themeEditorControl,
        border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.primary,
                maxLines = 1
            )
        }
    }
}
