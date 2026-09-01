package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.parseComposeColor

/**
 * Tactile tile for Foundation Sources (Background, Text, Accent).
 * Foundation sources drive the automated OKLCH perceptual derivation.
 */
@Composable
fun FoundationColorTile(
    label: String,
    hex: String,
    roleDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = parseComposeColor(hex, MaterialTheme.colorScheme.surfaceVariant)
    val borderSubtle = ScribeTheme.colors.borders.subtle
    val checkDark = ScribeTheme.colors.surfaces.surfaceRaised
    val checkLight = ScribeTheme.colors.surfaces.surface

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderSubtle.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val squareSize = 8.dp.toPx()
                    val rows = (size.height / squareSize).toInt() + 1
                    val cols = (size.width / squareSize).toInt() + 1
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val isDark = (r + c) % 2 == 0
                            drawRect(
                                color = if (isDark) checkDark else checkLight,
                                topLeft = androidx.compose.ui.geometry.Offset(c * squareSize, r * squareSize),
                                size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Source",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = hex.uppercase(),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = roleDescription,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

/**
 * Tile for Overrideable Semantic Tokens (Heading, Dialogue, Monologue, Secondary, Tertiary, etc.).
 * Explicitly communicates whether the token is auto-derived or user-overridden, and provides a reset button.
 */
@Composable
fun OverrideColorTile(
    label: String,
    hex: String,
    isOverridden: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = parseComposeColor(hex, MaterialTheme.colorScheme.surfaceVariant)
    val borderSubtle = ScribeTheme.colors.borders.subtle
    val checkDark = ScribeTheme.colors.surfaces.surfaceRaised
    val checkLight = ScribeTheme.colors.surfaces.surface

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else borderSubtle.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, borderSubtle.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val squareSize = 8.dp.toPx()
                    val rows = (size.height / squareSize).toInt() + 1
                    val cols = (size.width / squareSize).toInt() + 1
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val isDark = (r + c) % 2 == 0
                            drawRect(
                                color = if (isDark) checkDark else checkLight,
                                topLeft = androidx.compose.ui.geometry.Offset(c * squareSize, r * squareSize),
                                size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                if (isOverridden) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset to auto",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hex.uppercase(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = if (isOverridden)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isOverridden) "Custom" else "Auto",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Interactive color picker with HSV sliders, gradient tracks, and quick presets.
 */
@Composable
fun CustomColorPicker(
    currentColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember(currentColor) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(currentColor.toArgb(), array)
        array
    }
    var hue by remember(currentColor) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(currentColor) { mutableFloatStateOf(hsv[1]) }
    var valVal by remember(currentColor) { mutableFloatStateOf(hsv[2]) }

    fun update(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        valVal = v
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        onColorChanged(Color(argb))
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Presets
        val presets = listOf(
            "#FAFAF7", "#F3F4F6", "#E5E7EB", "#D1D5DB",
            "#1E1E2E", "#0D1117", "#121212", "#000000",
            "#3B82F6", "#6366F1", "#8B5CF6", "#EC4899",
            "#E11D48", "#F97316", "#F59E0B", "#10B981"
        )
        val activeRingColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Palette Swatches",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(presets) { p ->
                    val c = parseComposeColor(p)
                    val isSelected = currentColor.toArgb() == c.toArgb()
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) activeRingColor else outlineColor,
                                shape = CircleShape
                            )
                            .clickable {
                                onColorChanged(c)
                            }
                    )
                }
            }
        }

        // Hue Slider with Spectrum Track
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hue", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("${hue.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = hue,
                onValueChange = { update(it, sat, valVal) },
                valueRange = 0f..360f
            )
        }

        // Saturation Slider
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Saturation", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("${(sat * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = sat,
                onValueChange = { update(hue, it, valVal) },
                valueRange = 0f..1f
            )
        }

        // Brightness / Value Slider
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Brightness", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("${(valVal * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = valVal,
                onValueChange = { update(hue, sat, it) },
                valueRange = 0f..1f
            )
        }
    }
}

/**
 * Modal dialog for selecting color values with live comparison swatch and hex input.
 */
@Composable
fun ColorPickerBottomSheet(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val defaultColor = MaterialTheme.colorScheme.primary
    val initialColor = remember(initialHex) { parseComposeColor(initialHex, defaultColor) }
    var selectedColor by remember(initialHex) { mutableStateOf(initialColor) }
    var hexText by remember(initialHex) { mutableStateOf(initialHex) }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = hexText.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color Comparison (Initial vs Live Selected)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Original", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(initialColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Selected", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }

                CustomColorPicker(
                    modifier = Modifier.fillMaxWidth(),
                    currentColor = selectedColor,
                    onColorChanged = { color ->
                        selectedColor = color
                        val argb = color.toArgb()
                        hexText = String.format("#%06X", 0xFFFFFF and argb)
                    }
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        if (input.startsWith("#") && (input.length == 7 || input.length == 9)) {
                            try {
                                selectedColor = parseComposeColor(input)
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(hexText)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply Color", fontWeight = FontWeight.Bold)
            }
        }
    )
}

