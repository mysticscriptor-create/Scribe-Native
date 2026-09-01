package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FontHelper

/**
 * Inspector panel for Typography & Typesetting controls in the Theme Editor.
 */
@Composable
fun ThemeTypographyPanel(
    fontFamily: String,
    fontSize: Float,
    lineHeight: Float,
    paragraphSpacing: Float,
    sideMargins: Float,
    onFontFamilyChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onSideMarginsChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Font Family Selection
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
                    text = "Typeface & Family",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Text(
                    text = "Select the primary editorial typeface for reading and writing prose.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontHelper.fontOptions.forEach { option ->
                        val isSelected = fontFamily.equals(option.key, ignoreCase = true) ||
                                (option.key == "default" && (fontFamily.isEmpty() || fontFamily.equals("default", ignoreCase = true)))
                        val optionFont = FontHelper.getFontFamily(option.key)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onFontFamilyChange(option.key) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = option.name,
                                    fontFamily = optionFont,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onFontFamilyChange(option.key) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Metrics & Spacing
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Editorial Metrics & Metrics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Font Size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base Font Size", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${fontSize.toInt()} sp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..32f,
                        steps = 19
                    )
                }

                // Line Height
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Line Spacing Multiplier", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(String.format(java.util.Locale.US, "%.2fx", lineHeight), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = lineHeight,
                        onValueChange = onLineHeightChange,
                        valueRange = 1.1f..2.2f
                    )
                }

                // Paragraph Spacing
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paragraph Spacing", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${paragraphSpacing.toInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = paragraphSpacing,
                        onValueChange = onParagraphSpacingChange,
                        valueRange = 0f..28f,
                        steps = 13
                    )
                }

                // Side Margins
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Horizontal Margins", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${sideMargins.toInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sideMargins,
                        onValueChange = onSideMarginsChange,
                        valueRange = 8f..48f,
                        steps = 19
                    )
                }
            }
        }
    }
}
