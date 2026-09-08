package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import java.util.UUID

/**
 * Phase 18.1: Dedicated Creation Workflow Screen for Generating a Theme from an Image.
 *
 * ARCHITECTURAL INTEGRITY:
 * - This screen represents a dedicated CREATION WORKFLOW, completely distinct from normal Theme Editing.
 * - It owns the temporary [ImageThemeGenerationSession] and all image-generation controls.
 * - When the user cancels, no theme is created or mutated.
 * - When the user clicks "Create Theme", the session is committed into a standard, persistent [AppTheme].
 * - After creation, the resulting theme behaves like any ordinary custom theme without an active generator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateThemeFromImageScreen(
    imageUri: String,
    onThemeCreated: (AppTheme) -> Unit,
    onCancel: () -> Unit,
    baseTheme: AppTheme = DefaultThemes.all.first(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<ImageThemeGenerationSession?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }

    // Initial Image Understanding Analysis
    LaunchedEffect(imageUri) {
        isAnalyzing = true
        val understanding = computeImageUnderstanding(context, imageUri)
        session = ImageThemeGenerationSession(
            imageUri = imageUri,
            understanding = understanding
        )
        isAnalyzing = false
    }

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
                                text = "New Theme from Picture",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Intelligent palette derivation and tonal atmosphere",
                            fontSize = 11.sp,
                            color = ScribeTheme.colors.content.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            session?.let { activeSession ->
                                val newId = UUID.randomUUID().toString()
                                val theme = activeSession.toAppTheme(newId, baseTheme)
                                onThemeCreated(theme)
                            }
                        },
                        enabled = session != null && !isAnalyzing,
                        shape = ScribeTheme.shapes.button,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Theme", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (isAnalyzing || session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Analyzing artwork chromatic structure...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ScribeTheme.colors.content.secondary
                    )
                }
            }
        } else {
            val currentSession = session!!
            val understanding = currentSession.understanding
            val resolvedColors = currentSession.resolvedColors

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Artwork Character Analysis Badges ─────────────────────────
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
                                    selected = currentSession.isDark,
                                    onClick = { session = currentSession.withPolarity(isDark = true) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Dark", fontSize = 11.sp) },
                                    shape = ScribeTheme.shapes.themeEditorControl
                                )
                                FilterChip(
                                    selected = !currentSession.isDark,
                                    onClick = { session = currentSession.withPolarity(isDark = false) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text("Light", fontSize = 11.sp) },
                                    shape = ScribeTheme.shapes.themeEditorControl
                                )
                            }
                        }

                        val previewBgUri = when (currentSession.relationshipMode) {
                            ThemeRelationshipMode.THEME_ONLY -> null
                            else -> currentSession.croppedUri ?: currentSession.imageUri
                        }

                        ThemePreviewStage(
                            colors = resolvedColors,
                            themeName = currentSession.effectiveThemeName,
                            fontFamily = baseTheme.fontFamily,
                            fontSize = baseTheme.fontSize.toFloat(),
                            lineHeight = baseTheme.lineHeight,
                            textAlignment = baseTheme.textAlignment,
                            sideMargins = baseTheme.paddingHorizontal.toFloat(),
                            bgMode = if (currentSession.relationshipMode == ThemeRelationshipMode.THEME_ONLY) "color" else "image",
                            bgUri = previewBgUri,
                            bgOpacity = baseTheme.backgroundImageOpacity ?: 0.35f,
                            blurIntensity = baseTheme.blurIntensity,
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
                            selectedCandidate = currentSession.selectedCandidate,
                            onSelectCandidate = { candidate ->
                                session = currentSession.withCandidate(candidate)
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
                            activeRecipe = currentSession.activeRecipe,
                            understanding = understanding,
                            candidateColor = currentSession.selectedCandidate,
                            isDark = currentSession.isDark,
                            influence = currentSession.activeInfluence,
                            writingCharacter = currentSession.activeWritingCharacter,
                            onSelectRecipe = { recipe ->
                                session = currentSession.withRecipe(recipe)
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
                            activeInfluence = currentSession.activeInfluence,
                            onSelectInfluence = { influence ->
                                session = currentSession.withInfluence(influence)
                            }
                        )

                        HorizontalDivider(color = ScribeTheme.colors.borders.subtle)

                        WritingCharacterSelector(
                            activeCharacter = currentSession.activeWritingCharacter,
                            onSelectCharacter = { character ->
                                session = currentSession.withWritingCharacter(character)
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
                            activeMode = currentSession.relationshipMode,
                            onSelectMode = { mode ->
                                session = currentSession.withRelationshipMode(mode)
                            }
                        )
                    }
                }

                // ── 5. Theme Name & Metadata ──────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScribeTheme.shapes.themeEditorSection,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Theme Title",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScribeTheme.colors.content.primary
                        )
                        OutlinedTextField(
                            value = currentSession.effectiveThemeName,
                            onValueChange = { session = currentSession.withThemeName(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = ScribeTheme.shapes.field,
                            placeholder = { Text("Enter theme name") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Bottom Action Buttons ─────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = ScribeTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val newId = UUID.randomUUID().toString()
                            val theme = currentSession.toAppTheme(newId, baseTheme)
                            onThemeCreated(theme)
                        },
                        shape = ScribeTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Theme", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
