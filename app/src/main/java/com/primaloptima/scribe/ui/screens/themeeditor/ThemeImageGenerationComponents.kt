package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.autoTextColor
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.ThemeGenerationEngine
import com.primaloptima.scribe.util.model.ImageInfluence
import com.primaloptima.scribe.util.model.ImageUnderstanding
import com.primaloptima.scribe.util.model.ThemeCanvasMode
import com.primaloptima.scribe.util.model.ThemeGenerationRecipe
import com.primaloptima.scribe.util.model.ThemeRelationshipMode
import com.primaloptima.scribe.util.model.VisualThemePalette
import com.primaloptima.scribe.util.model.WritingCharacter

/**
 * Visual candidate color swatch strip displaying ranked source colors.
 * Allows switching the active seed focus instantaneously without re-quantizing.
 */
@Composable
fun CandidateSwatchStrip(
    candidates: List<Int>,
    selectedCandidate: Int?,
    onSelectCandidate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Artwork Color Candidates",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.primary
            )
            Text(
                text = "Tap to switch seed focus",
                fontSize = 11.sp,
                color = ScribeTheme.colors.content.secondary
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(candidates) { candidate ->
                val isSelected = selectedCandidate == candidate || (selectedCandidate == null && candidate == candidates.firstOrNull())
                val swatchColor = Color(candidate)
                val descriptor = ThemeGenerationEngine.getColorDescriptor(candidate)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(ScribeTheme.shapes.themeEditorControl)
                        .clickable { onSelectCandidate(candidate) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, ScribeTheme.colors.borders.subtle, CircleShape)
                                }
                            )
                            .padding(if (isSelected) 3.5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(swatchColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active focus",
                                tint = autoTextColor(swatchColor),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = descriptor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else ScribeTheme.colors.content.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Collapsible menu for recipe selection.
 * Shows only the currently selected recipe by default. Clicking it expands the full recipe list.
 * Selecting a recipe collapses the menu again.
 */
@Composable
fun CollapsibleRecipeSelector(
    activeRecipe: ThemeGenerationRecipe,
    understanding: ImageUnderstanding?,
    candidateColor: Int?,
    isDark: Boolean,
    canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
    influence: ImageInfluence = ImageInfluence.BALANCED,
    writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
    onSelectRecipe: (ThemeGenerationRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ScribeTheme.shapes.themeEditorControl)
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Interpretation Recipe",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScribeTheme.colors.content.primary
                )
                Text(
                    text = if (isExpanded) "Tap recipe to select & collapse" else "Tap card or icon to expand options",
                    fontSize = 11.sp,
                    color = ScribeTheme.colors.content.secondary
                )
            }

            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse recipes" else "Expand recipes",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (!isExpanded) {
            // Collapsed view: Only shows currently active recipe
            RecipeCardItem(
                recipe = activeRecipe,
                isSelected = true,
                understanding = understanding,
                candidateColor = candidateColor,
                isDark = isDark,
                canvasMode = canvasMode,
                influence = influence,
                writingCharacter = writingCharacter,
                onClick = { isExpanded = true }
            )
        } else {
            // Expanded view: Shows all 4 recipes
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeGenerationRecipe.values().forEach { recipe ->
                    RecipeCardItem(
                        recipe = recipe,
                        isSelected = activeRecipe == recipe,
                        understanding = understanding,
                        candidateColor = candidateColor,
                        isDark = isDark,
                        canvasMode = canvasMode,
                        influence = influence,
                        writingCharacter = writingCharacter,
                        onClick = {
                            onSelectRecipe(recipe)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 4-Recipe comparison cards presenting BALANCED, ATMOSPHERIC, INK, and EXPRESSIVE interpretations.
 */
@Composable
fun RecipeComparisonCards(
    activeRecipe: ThemeGenerationRecipe,
    understanding: ImageUnderstanding?,
    candidateColor: Int?,
    isDark: Boolean,
    canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
    influence: ImageInfluence = ImageInfluence.BALANCED,
    writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
    onSelectRecipe: (ThemeGenerationRecipe) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemeGenerationRecipe.values().forEach { recipe ->
            RecipeCardItem(
                recipe = recipe,
                isSelected = activeRecipe == recipe,
                understanding = understanding,
                candidateColor = candidateColor,
                isDark = isDark,
                canvasMode = canvasMode,
                influence = influence,
                writingCharacter = writingCharacter,
                onClick = { onSelectRecipe(recipe) }
            )
        }
    }
}

@Composable
fun RecipeCardItem(
    recipe: ThemeGenerationRecipe,
    isSelected: Boolean,
    understanding: ImageUnderstanding?,
    candidateColor: Int?,
    isDark: Boolean,
    canvasMode: ThemeCanvasMode = if (isDark) ThemeCanvasMode.DARK else ThemeCanvasMode.LIGHT,
    influence: ImageInfluence = ImageInfluence.BALANCED,
    writingCharacter: WritingCharacter = WritingCharacter.NEUTRAL,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = if (understanding != null) {
        ThemeGenerationEngine.generateSourcePalette(
            understanding = understanding,
            recipe = recipe,
            candidateColor = candidateColor,
            isDark = isDark,
            canvasMode = canvasMode,
            influence = influence,
            writingCharacter = writingCharacter
        )
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ScribeTheme.shapes.cardSmall,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recipe.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = getRecipeBadge(recipe),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else ScribeTheme.colors.content.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = recipe.description,
                fontSize = 12.sp,
                color = ScribeTheme.colors.content.secondary,
                lineHeight = 16.sp
            )

            // Visual Palette Distribution Preview
            if (palette != null) {
                val vp = palette.visualPalette
                if (vp != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MiniPaletteTile(
                            label = "Canvas",
                            hex = vp.visualCanvas,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Editor",
                            hex = vp.visualEditorSurface,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Chrome",
                            hex = vp.visualChrome,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Accent",
                            hex = vp.visualPrimaryAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Sec",
                            hex = vp.visualSecondaryAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "High",
                            hex = vp.visualHighlight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniPaletteTile(
                            label = "Base",
                            hex = palette.background,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Prose",
                            hex = palette.text,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPaletteTile(
                            label = "Accent",
                            hex = palette.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPaletteTile(
    label: String,
    hex: String,
    modifier: Modifier = Modifier
) {
    val color = parseComposeColor(hex, Color.Gray)
    Surface(
        shape = ScribeTheme.shapes.themeEditorControl,
        color = color,
        border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle),
        modifier = modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = autoTextColor(color)
            )
        }
    }
}

private fun getRecipeBadge(recipe: ThemeGenerationRecipe): String {
    return when (recipe) {
        ThemeGenerationRecipe.BALANCED -> "Natural"
        ThemeGenerationRecipe.ATMOSPHERIC -> "Evocative"
        ThemeGenerationRecipe.INK -> "Writing-First"
        ThemeGenerationRecipe.EXPRESSIVE -> "Vibrant"
    }
}

/**
 * Segmented control for Image Influence (Subtle, Balanced, Strong).
 * Modulates semantic token tinting depth (orthogonal to canvas image opacity).
 */
@Composable
fun ImageInfluenceSelector(
    activeInfluence: ImageInfluence,
    onSelectInfluence: (ImageInfluence) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Image Influence",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.primary
            )
            Text(
                text = when (activeInfluence) {
                    ImageInfluence.SUBTLE -> "Accent only • Restrained"
                    ImageInfluence.BALANCED -> "Harmonized surfaces"
                    ImageInfluence.STRONG -> "Deep environmental tone"
                },
                fontSize = 11.sp,
                color = ScribeTheme.colors.content.secondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImageInfluence.values().forEach { influence ->
                val isSelected = activeInfluence == influence
                OutlinedButton(
                    onClick = { onSelectInfluence(influence) },
                    modifier = Modifier.weight(1f),
                    shape = ScribeTheme.shapes.themeEditorControl,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else ScribeTheme.colors.content.secondary
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                    }
                ) {
                    Text(
                        text = influence.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Segmented control for Writing Character (Neutral, Warm, Cool, Dramatic).
 */
@Composable
fun WritingCharacterSelector(
    activeCharacter: WritingCharacter,
    onSelectCharacter: (WritingCharacter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Writing Character Undertone",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ScribeTheme.colors.content.primary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WritingCharacter.values().forEach { character ->
                val isSelected = activeCharacter == character
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCharacter(character) },
                    label = {
                        Text(
                            text = character.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = ScribeTheme.shapes.themeEditorControl
                )
            }
        }
    }
}

/**
 * High-level presentation selector for Theme Only, Theme + Image, and Theme + Glass.
 * Decoupled from color derivation.
 */
@Composable
fun RelationshipModeSelector(
    activeMode: ThemeRelationshipMode,
    onSelectMode: (ThemeRelationshipMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Artwork Presentation",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.primary
            )
            Text(
                text = activeMode.description,
                fontSize = 11.sp,
                color = ScribeTheme.colors.content.secondary,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeRelationshipMode.values().forEach { mode ->
                val isSelected = activeMode == mode
                OutlinedButton(
                    onClick = { onSelectMode(mode) },
                    modifier = Modifier.weight(1f),
                    shape = ScribeTheme.shapes.themeEditorControl,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.secondary else ScribeTheme.colors.content.secondary
                    ),
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                    } else {
                        BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                    }
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Visual Palette Distribution Display
 * Renders the 10-role distribution of colors across Canvas, Editor Surface, Chrome,
 * Accents, Highlight, and Neutral.
 */
@Composable
fun VisualPaletteDistributionDisplay(
    visualPalette: VisualThemePalette?,
    modifier: Modifier = Modifier
) {
    if (visualPalette == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Visual Palette Distribution",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ScribeTheme.colors.content.primary
            )
            Text(
                text = "10 Architectural Roles",
                fontSize = 11.sp,
                color = ScribeTheme.colors.content.secondary
            )
        }

        // Structural Surfaces
        Text(
            text = "SURFACES & CHROME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = ScribeTheme.colors.content.secondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisualRoleTile("Canvas", visualPalette.visualCanvas, Modifier.weight(1f))
            VisualRoleTile("Editor", visualPalette.visualEditorSurface, Modifier.weight(1f))
            VisualRoleTile("Chrome", visualPalette.visualChrome, Modifier.weight(1f))
            VisualRoleTile("Sec Chrome", visualPalette.visualSecondaryChrome, Modifier.weight(1f))
            VisualRoleTile("Elevated", visualPalette.visualElevatedSurface, Modifier.weight(1f))
        }

        // Accents, Highlights & Neutrals
        Text(
            text = "ACCENTS & HARMONICS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = ScribeTheme.colors.content.secondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisualRoleTile("Primary", visualPalette.visualPrimaryAccent, Modifier.weight(1f))
            VisualRoleTile("Secondary", visualPalette.visualSecondaryAccent, Modifier.weight(1f))
            VisualRoleTile("Tertiary", visualPalette.visualTertiaryAccent, Modifier.weight(1f))
            VisualRoleTile("Highlight", visualPalette.visualHighlight, Modifier.weight(1f))
            VisualRoleTile("Neutral", visualPalette.visualNeutral, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VisualRoleTile(
    label: String,
    hex: String,
    modifier: Modifier = Modifier
) {
    val color = parseComposeColor(hex, Color.Gray)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = ScribeTheme.shapes.themeEditorControl,
            color = color,
            border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hex.takeLast(6),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = autoTextColor(color)
                )
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = ScribeTheme.colors.content.secondary,
            maxLines = 1
        )
    }
}
