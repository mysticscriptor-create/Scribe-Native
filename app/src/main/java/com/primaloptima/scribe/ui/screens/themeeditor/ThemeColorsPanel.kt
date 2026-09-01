package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.autoTextColor
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Inspector panel for Color management in the Theme Editor.
 * Structures color tokens into Foundation Sources, Writing Overrides, Supporting Accents,
 * Status, 5-Tier Elevation Ramp, and Accessibility diagnostics.
 */
@Composable
fun ThemeColorsPanel(
    draft: ThemeEditorDraft,
    resolvedColors: ThemeColors,
    onSelectTarget: (ColorPickerTarget) -> Unit,
    onResetOverride: (ColorPickerTarget) -> Unit,
    onOpenAccessibilityDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. FOUNDATION SOURCES ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
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
                        text = "Foundation Sources",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Core Engine",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Authoritative base inputs. Modifying foundation colors regenerates all dependent elevations, boundaries, and secondary tones.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FoundationColorTile(
                        label = "Background",
                        hex = draft.bgHex,
                        roleDescription = "Canvas & app base",
                        onClick = { onSelectTarget(ColorPickerTarget.BACKGROUND) },
                        modifier = Modifier.weight(1f)
                    )
                    FoundationColorTile(
                        label = "Text",
                        hex = draft.textHex,
                        roleDescription = "Primary reading prose",
                        onClick = { onSelectTarget(ColorPickerTarget.TEXT) },
                        modifier = Modifier.weight(1f)
                    )
                    FoundationColorTile(
                        label = "Primary",
                        hex = draft.accentHex,
                        roleDescription = "Key actions & carets",
                        onClick = { onSelectTarget(ColorPickerTarget.ACCENT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 2. WRITING & PROSE SEMANTICS (Overrideable) ───────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
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
                        text = "Writing & Editorial Overrides",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Semantic Tokens",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Lexer highlights for prose rendering. Tap any token to customize; tap reset ↺ to restore generated default.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverrideColorTile(
                        label = "Heading",
                        hex = resolvedColors.headingText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.HEADING_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.HEADING_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.HEADING_TEXT) },
                        modifier = Modifier.weight(1f)
                    )
                    OverrideColorTile(
                        label = "Dialogue",
                        hex = resolvedColors.dialogueText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.DIALOGUE_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.DIALOGUE_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.DIALOGUE_TEXT) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverrideColorTile(
                        label = "Monologue",
                        hex = resolvedColors.monologueText,
                        isOverridden = draft.isOverridden(ColorPickerTarget.MONOLOGUE_TEXT),
                        onClick = { onSelectTarget(ColorPickerTarget.MONOLOGUE_TEXT) },
                        onReset = { onResetOverride(ColorPickerTarget.MONOLOGUE_TEXT) },
                        modifier = Modifier.weight(1f)
                    )
                    OverrideColorTile(
                        label = "Emphasis",
                        hex = if (resolvedColors.specialHighlight.isNotBlank()) resolvedColors.specialHighlight else resolvedColors.accent,
                        isOverridden = draft.isOverridden(ColorPickerTarget.SPECIAL_HIGHLIGHT),
                        onClick = { onSelectTarget(ColorPickerTarget.SPECIAL_HIGHLIGHT) },
                        onReset = { onResetOverride(ColorPickerTarget.SPECIAL_HIGHLIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 3. SUPPORTING ACCENTS (Overrideable) ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Supporting Accents",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        // ── 4. AUTO-DERIVED ELEVATION RAMP (5-Tier) ────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "5-Tier Perceptual Elevation Ramp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tiers = listOf(
                        Pair("L0 Base", resolvedColors.background),
                        Pair("L1 Recessed", resolvedColors.surfaceLowest),
                        Pair("L2 Surface", resolvedColors.surface),
                        Pair("L3 Raised", resolvedColors.surfaceRaised),
                        Pair("L4 Overlay", resolvedColors.surfaceOverlay)
                    )
                    val borderSubtle = parseComposeColor(resolvedColors.borderSubtle, MaterialTheme.colorScheme.outlineVariant)
                    tiers.forEach { item ->
                        val label = item.first
                        val hex = item.second
                        val swatchColor = parseComposeColor(hex, MaterialTheme.colorScheme.surfaceVariant)
                        val swatchText = autoTextColor(swatchColor)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(swatchColor)
                                .border(1.dp, borderSubtle, RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = swatchText, maxLines = 1)
                            Text(hex, fontSize = 8.sp, color = swatchText.copy(alpha = 0.8f), maxLines = 1)
                        }
                    }
                }
            }
        }

        // ── 5. STATUS COLORS ──────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "System Status Indicators",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statuses = listOf(
                        Triple("Success", resolvedColors.success, Color(0xFF10B981)),
                        Triple("Warning", resolvedColors.warning, Color(0xFFF59E0B)),
                        Triple("Error", resolvedColors.error, Color(0xFFEF4444))
                    )
                    statuses.forEach { (label, hex, fallback) ->
                        val c = if (hex.isNotBlank()) parseComposeColor(hex, fallback) else fallback
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                            Column {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (hex.isNotBlank()) hex.uppercase() else "Auto", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // ── 6. ACCESSIBILITY COMPACT SUMMARY ──────────────────────────────
        AccessibilitySummaryCard(
            colors = resolvedColors,
            onOpenDiagnostics = onOpenAccessibilityDiagnostics
        )
    }
}
