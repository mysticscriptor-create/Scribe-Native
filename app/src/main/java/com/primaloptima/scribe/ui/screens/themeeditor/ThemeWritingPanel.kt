package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Editorial Writing & Typesetting inspector panel for the Theme Editor.
 * Unifies editorial color tokens (Heading, Dialogue, Monologue, Highlight, Annotation)
 * with typeface selection and core typesetting metrics (size, line height, paragraph spacing).
 *
 * Designed around authorial intent: "How should different kinds of writing look?"
 */
@Composable
fun ThemeWritingPanel(
    draft: ThemeEditorDraft,
    resolvedColors: ThemeColors,
    onSelectTarget: (ColorPickerTarget) -> Unit,
    onResetOverride: (ColorPickerTarget) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. EDITORIAL PROSE STYLES (Color & Highlights) ─────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ScribeTheme.shapes.themeEditorSection,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Editorial Styles",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Writing Elements",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Tailor distinct visual tones for dialogue, internal thought, and structure. Tap any style to customize, or tap the reset icon to return to the automatic theme color.",
                    fontSize = 12.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 16.sp
                )

                // Editorial Style Rows
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditorialStyleRow(
                        title = "Chapter & Section Headings",
                        subtitle = "Chapter titles, scene dividers, and section titles",
                        hex = resolvedColors.headingText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.HEADING_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.HEADING_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.HEADING_TEXT) }
                    )

                    EditorialStyleRow(
                        title = "Character Dialogue",
                        subtitle = "Spoken speech and conversational text inside quotes",
                        hex = resolvedColors.dialogueText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.DIALOGUE_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.DIALOGUE_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.DIALOGUE_TEXT) }
                    )

                    EditorialStyleRow(
                        title = "Internal Monologue & Thoughts",
                        subtitle = "Unspoken thoughts, introspective musings, and italicized prose",
                        hex = resolvedColors.monologueText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.MONOLOGUE_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.MONOLOGUE_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.MONOLOGUE_TEXT) }
                    )

                    EditorialStyleRow(
                        title = "Emphasis & Highlights",
                        subtitle = "Search matches, text selections, and keyword emphasis",
                        hex = if (resolvedColors.specialHighlight.isNotBlank()) resolvedColors.specialHighlight else resolvedColors.accent,
                        isOverridden = draft.isOverridden(ColorPickerTarget.SPECIAL_HIGHLIGHT),
                        onClick = { onSelectTarget(ColorPickerTarget.SPECIAL_HIGHLIGHT) },
                        onReset = { onResetOverride(ColorPickerTarget.SPECIAL_HIGHLIGHT) }
                    )

                    EditorialStyleRow(
                        title = "Editorial Margin Notes",
                        subtitle = "Marginalia, footnote references, and research commentary",
                        hex = if (resolvedColors.annotation.isNotBlank()) resolvedColors.annotation else resolvedColors.accent,
                        isOverridden = draft.isOverridden(ColorPickerTarget.ANNOTATION),
                        onClick = { onSelectTarget(ColorPickerTarget.ANNOTATION) },
                        onReset = { onResetOverride(ColorPickerTarget.ANNOTATION) }
                    )
                }
            }
        }

        // ── 2. TYPEFACE & SPECIMEN ─────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ScribeTheme.shapes.themeEditorSection,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Typeface & Specimen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Reading Font",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Select your primary reading typeface. Live specimen displays the natural glyph rhythm and optical balance.",
                    fontSize = 12.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 16.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontHelper.fontOptions.forEach { option ->
                        val isSelected = draft.fontFamily.equals(option.key, ignoreCase = true) ||
                                (option.key == "default" && (draft.fontFamily.isEmpty() || draft.fontFamily.equals("default", ignoreCase = true)))
                        val optionFont = FontHelper.getFontFamily(option.key)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ScribeTheme.shapes.cardSmall)
                                .clickable { onFontFamilyChange(option.key) },
                            shape = ScribeTheme.shapes.cardSmall,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else ScribeTheme.colors.borders.subtle
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.name,
                                            fontFamily = optionFont,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) ScribeTheme.colors.interaction.primary else ScribeTheme.colors.content.primary
                                        )
                                        Text(
                                            text = option.subtitle,
                                            fontSize = 11.sp,
                                            color = ScribeTheme.colors.content.secondary
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onFontFamilyChange(option.key) }
                                    )
                                }

                                Text(
                                    text = "Sphinx of black quartz, judge my vow • 12345",
                                    fontFamily = optionFont,
                                    fontSize = 12.sp,
                                    color = if (isSelected) ScribeTheme.colors.content.primary else ScribeTheme.colors.content.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 3. TYPESETTING & METRICS ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ScribeTheme.shapes.themeEditorSection,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Typesetting & Rhythm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Geometry",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Base Font Size
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Base Font Size", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ScribeTheme.colors.content.primary)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ScribeTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "${draft.fontSize.toInt()} sp",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = draft.fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..28f,
                        steps = 15
                    )
                }

                // Line Height
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Line Height", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ScribeTheme.colors.content.primary)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ScribeTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = String.format("%.2fx", draft.lineHeight),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = draft.lineHeight,
                        onValueChange = onLineHeightChange,
                        valueRange = 1.2f..2.4f,
                        steps = 11
                    )
                }

                // Paragraph Spacing
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Paragraph Spacing", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ScribeTheme.colors.content.primary)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = ScribeTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "${draft.paragraphSpacing.toInt()} dp",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Slider(
                        value = draft.paragraphSpacing,
                        onValueChange = onParagraphSpacingChange,
                        valueRange = 0f..32f,
                        steps = 15
                    )
                }
            }
        }
    }
}

/**
 * An accessible editorial styling row with a large touch target swatch,
 * descriptive subtext, Auto/Custom badge, and a dedicated 48dp reset button.
 */
@Composable
private fun EditorialStyleRow(
    title: String,
    subtitle: String,
    hex: String,
    isOverridden: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = parseComposeColor(hex, MaterialTheme.colorScheme.surfaceVariant)
    val borderSubtle = ScribeTheme.colors.borders.subtle

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScribeTheme.shapes.cardSmall)
            .clickable { onClick() },
        shape = ScribeTheme.shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else borderSubtle.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color Swatch
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ScribeTheme.shapes.themeEditorControl)
                    .background(color)
                    .border(1.dp, borderSubtle, ScribeTheme.shapes.themeEditorControl)
            )

            // Text Titles & Role
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = ScribeTheme.colors.content.primary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hex.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = if (isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = ScribeTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (isOverridden) "Custom" else "Auto",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOverridden) ScribeTheme.colors.interaction.primary else ScribeTheme.colors.content.secondary
                        )
                    }
                }
            }

            // Accessible Reset Button (48dp touch target)
            if (isOverridden) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset to auto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
