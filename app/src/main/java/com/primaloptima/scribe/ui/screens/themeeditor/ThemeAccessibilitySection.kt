package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.calculateApcaContrast
import com.primaloptima.scribe.ui.theme.calculateWcagContrastRatio
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.ThemeColors

/**
 * Compact high-impact accessibility summary card placed inside the Colors inspector panel.
 */
@Composable
fun AccessibilitySummaryCard(
    colors: ThemeColors,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contrastReport = remember(colors) {
        ThemeManager.validateSemanticContrast(
            bgHex = colors.background,
            textHex = colors.text,
            accentHex = colors.accent,
            dialogueHex = colors.dialogueText,
            monologueHex = colors.monologueText,
            headingHex = colors.headingText
        )
    }

    val isExcellent = contrastReport.overallPassRate >= 0.9f
    val statusColor = if (isExcellent) Color(0xFF059669) else Color(0xFFD97706)
    val containerColor = if (isExcellent) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Readability & Contrast",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${(contrastReport.overallPassRate * 100).toInt()}% Passed",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                text = if (isExcellent)
                    "Optimal editorial contrast: ${contrastReport.passedPairsCount}/${contrastReport.totalPairsChecked} color pairs comfortably satisfy WCAG 2.1 AA / APCA standards."
                else
                    "Moderate contrast (${contrastReport.passedPairsCount}/${contrastReport.totalPairsChecked} pairs pass): Some subtle elements or specialized accents may have reduced legibility in bright environments.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "View Contrast Breakdown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Detailed accessibility diagnostic dialog.
 */
@Composable
fun AccessibilityDiagnosticsDialog(
    colors: ThemeColors,
    onDismiss: () -> Unit
) {
    val contrastReport = remember(colors) {
        ThemeManager.validateSemanticContrast(
            bgHex = colors.background,
            textHex = colors.text,
            accentHex = colors.accent,
            dialogueHex = colors.dialogueText,
            monologueHex = colors.monologueText,
            headingHex = colors.headingText
        )
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Accessibility & Contrast Engine",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "High-Impact Semantic Pairs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        contrastReport.results.forEach { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res.pair.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lc ${res.apcaLc.toInt().let { if (it > 0) "+$it" else "$it" }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (res.passesApca) Color(0xFF10B981) else Color(0xFFEF4444)
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f:1", res.wcagRatio),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.passesWcag) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                item {
                    AccessibilitySampleCard(colors = colors)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Phase 12 Live Accessibility Preview Component
 * Self-contained composable rendering actual real-time UI component samples with live contrast verification.
 */
@Composable
fun AccessibilitySampleCard(
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val scribeThemeColors = ScribeTheme.colors
    val appSurfaces = scribeThemeColors.surfaces
    val appContent = scribeThemeColors.content
    val appInteraction = scribeThemeColors.interaction
    val appSemantic = scribeThemeColors.semantic
    val appBorders = scribeThemeColors.borders

    val surface = parseComposeColor(colors.surface, appSurfaces.surface)
    val primaryText = parseComposeColor(colors.text, appContent.primary)
    val secondaryText = parseComposeColor(colors.mutedText, appContent.secondary)
    val tertiaryText = parseComposeColor(colors.subtleText, appContent.tertiary)
    val accent = parseComposeColor(colors.accent, appInteraction.primary)
    val onAccent = if (ThemeManager.isDarkColor(colors.accent)) Color.White else Color.Black
    val borderSubtle = parseComposeColor(colors.borderSubtle, appBorders.subtle)
    val dialogueText = parseComposeColor(colors.dialogueText, accent)
    val warningColor = if (colors.warning.isNotEmpty()) parseComposeColor(colors.warning, appSemantic.warning) else if (ThemeManager.isDarkColor(colors.background)) Color(0xFFFBBF24) else Color(0xFFD97706)
    val errorColor = if (colors.error.isNotEmpty()) parseComposeColor(colors.error, appSemantic.error) else if (ThemeManager.isDarkColor(colors.background)) Color(0xFFF87171) else Color(0xFFDC2626)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Live Verification Samples",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surface)
                .border(1.dp, borderSubtle, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Primary Text
            AccessibilitySampleRow(
                label = "Primary Text",
                foreground = primaryText,
                background = surface
            ) {
                Text(
                    text = "The quick brown fox jumps over the lazy dog.",
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 2. Secondary Text
            AccessibilitySampleRow(
                label = "Secondary Text",
                foreground = secondaryText,
                background = surface
            ) {
                Text(
                    text = "Chapter 3 • 2,450 words • 8 min read",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 3. Tertiary Text
            AccessibilitySampleRow(
                label = "Tertiary Text",
                foreground = tertiaryText,
                background = surface
            ) {
                Text(
                    text = "Saved 2 minutes ago • Plain text mode",
                    color = tertiaryText,
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 4. Primary Button
            AccessibilitySampleRow(
                label = "Button Action",
                foreground = onAccent,
                background = accent
            ) {
                Surface(
                    color = accent,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Save Chapter",
                        color = onAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 5. Dialogue
            AccessibilitySampleRow(
                label = "Dialogue",
                foreground = dialogueText,
                background = surface
            ) {
                Text(
                    text = "“We cross the mountains at first light,” she said.",
                    color = dialogueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 6. Warning
            AccessibilitySampleRow(
                label = "Warning",
                foreground = warningColor,
                background = surface
            ) {
                Surface(
                    color = warningColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, warningColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚠️", fontSize = 10.sp)
                        Text(
                            text = "Passive Voice Detected",
                            color = warningColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = borderSubtle.copy(alpha = 0.5f))

            // 7. Error
            AccessibilitySampleRow(
                label = "Error",
                foreground = errorColor,
                background = surface
            ) {
                Surface(
                    color = errorColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, errorColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("❌", fontSize = 10.sp)
                        Text(
                            text = "Repeated Word Conflict",
                            color = errorColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilitySampleRow(
    label: String,
    foreground: Color,
    background: Color,
    content: @Composable () -> Unit
) {
    val wcagRatio = remember(foreground, background) {
        calculateWcagContrastRatio(foreground, background)
    }
    val apcaLc = remember(foreground, background) {
        calculateApcaContrast(foreground, background)
    }
    val passes = wcagRatio >= 3.0 || Math.abs(apcaLc) >= 45.0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = if (passes) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (wcagRatio >= 4.5) "AA Pass (${String.format(java.util.Locale.US, "%.1f:1", wcagRatio)})"
                    else if (passes) "UI Pass (${String.format(java.util.Locale.US, "%.1f:1", wcagRatio)})"
                    else "Low (${String.format(java.util.Locale.US, "%.1f:1", wcagRatio)})",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (passes) Color(0xFF059669) else Color(0xFFDC2626)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            content()
        }
    }
}
