package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Inspector panel for Base Appearance & Palette Sources in the Theme Editor.
 * Houses the primary foundation driving sources (Background, Text, Accent),
 * supporting accents (Secondary, Tertiary), and system status indicators (Success, Warning, Error).
 */
@Composable
fun ThemeAppearancePanel(
    draft: ThemeEditorDraft,
    resolvedColors: ThemeColors,
    onSelectTarget: (ColorPickerTarget) -> Unit,
    onResetOverride: (ColorPickerTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. FOUNDATION PALETTE SOURCES ──────────────────────────────────────
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
                        text = "Foundation Palette",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Core Sources",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "These three core colors define your theme identity and automatically generate all elevation surfaces, borders, and harmonious tones.",
                    fontSize = 12.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 16.sp
                )

                // 3-Column Foundation Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FoundationColorTile(
                        label = "Background",
                        hex = draft.bgHex,
                        roleDescription = "Base canvas",
                        onClick = { onSelectTarget(ColorPickerTarget.BACKGROUND) },
                        modifier = Modifier.weight(1f)
                    )
                    FoundationColorTile(
                        label = "Reading Text",
                        hex = draft.textHex,
                        roleDescription = "Body prose",
                        onClick = { onSelectTarget(ColorPickerTarget.TEXT) },
                        modifier = Modifier.weight(1f)
                    )
                    FoundationColorTile(
                        label = "Theme Accent",
                        hex = draft.accentHex,
                        roleDescription = "Actions & cursor",
                        onClick = { onSelectTarget(ColorPickerTarget.ACCENT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 2. SUPPORTING ACCENTS ──────────────────────────────────────────────
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
                        text = "Supporting Accents",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Complementary Tones",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Secondary and tertiary accents for chips, badges, and secondary buttons. Derived automatically from your accent color.",
                    fontSize = 12.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OverrideColorTile(
                        label = "Secondary",
                        hex = resolvedColors.secondary,
                        isOverridden = draft.isOverridden(ColorPickerTarget.SECONDARY),
                        onClick = { onSelectTarget(ColorPickerTarget.SECONDARY) },
                        onReset = { onResetOverride(ColorPickerTarget.SECONDARY) },
                        modifier = Modifier.weight(1f)
                    )
                    OverrideColorTile(
                        label = "Tertiary",
                        hex = resolvedColors.tertiary,
                        isOverridden = draft.isOverridden(ColorPickerTarget.TERTIARY),
                        onClick = { onSelectTarget(ColorPickerTarget.TERTIARY) },
                        onReset = { onResetOverride(ColorPickerTarget.TERTIARY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 3. SYSTEM STATUS INDICATORS ────────────────────────────────────────
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
                        text = "System Indicators",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ScribeTheme.colors.content.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = ScribeTheme.shapes.themeEditorControl
                    ) {
                        Text(
                            text = "Feedback States",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ScribeTheme.colors.content.secondary
                        )
                    }
                }

                Text(
                    text = "Functional cues for alerts, confirmation badges, and destructive actions. Automatically calibrated for contrast against your canvas.",
                    fontSize = 12.sp,
                    color = ScribeTheme.colors.content.secondary,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusIndicatorTile(
                        label = "Success",
                        hex = resolvedColors.success,
                        isOverridden = draft.isOverridden(ColorPickerTarget.SUCCESS),
                        onClick = { onSelectTarget(ColorPickerTarget.SUCCESS) },
                        onReset = { onResetOverride(ColorPickerTarget.SUCCESS) },
                        modifier = Modifier.weight(1f)
                    )
                    StatusIndicatorTile(
                        label = "Warning",
                        hex = resolvedColors.warning,
                        isOverridden = draft.isOverridden(ColorPickerTarget.WARNING),
                        onClick = { onSelectTarget(ColorPickerTarget.WARNING) },
                        onReset = { onResetOverride(ColorPickerTarget.WARNING) },
                        modifier = Modifier.weight(1f)
                    )
                    StatusIndicatorTile(
                        label = "Error",
                        hex = resolvedColors.error,
                        isOverridden = draft.isOverridden(ColorPickerTarget.ERROR),
                        onClick = { onSelectTarget(ColorPickerTarget.ERROR) },
                        onReset = { onResetOverride(ColorPickerTarget.ERROR) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Compact tile for system status indicators with live color dot, hex, and auto/custom status.
 */
@Composable
private fun StatusIndicatorTile(
    label: String,
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
            .clip(ScribeTheme.shapes.cardSmall)
            .clickable { onClick() },
        shape = ScribeTheme.shapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else borderSubtle.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, borderSubtle, CircleShape)
                )

                if (isOverridden) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to auto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.primary,
                maxLines = 1
            )

            Text(
                text = hex.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
