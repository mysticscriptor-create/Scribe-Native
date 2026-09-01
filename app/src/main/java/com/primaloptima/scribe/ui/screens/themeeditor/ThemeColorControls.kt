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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.parseComposeColor

/**
 * Tile for Foundation Sources (Background, Text, Accent).
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

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, borderSubtle, RoundedCornerShape(10.dp))
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
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Source",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = hex.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
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

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
            .border(1.dp, if (isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else borderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, borderSubtle, RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(14.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = if (isOverridden)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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

/**
 * Interactive color picker with HSV sliders and quick presets.
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            val presets = listOf("#FAFAF7", "#1E1E2E", "#0D1117", "#000000", "#3366FF", "#E11D48", "#10B981", "#F59E0B", "#8B5CF6")
            val activeRingColor = ScribeTheme.colors.interaction.primary
            val outlineColor = MaterialTheme.colorScheme.outline
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Column {
            Text("Hue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = hue,
                onValueChange = { update(it, sat, valVal) },
                valueRange = 0f..360f
            )
        }

        Column {
            Text("Saturation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sat,
                onValueChange = { update(hue, it, valVal) },
                valueRange = 0f..1f
            )
        }

        Column {
            Text("Brightness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = valVal,
                onValueChange = { update(hue, sat, it) },
                valueRange = 0f..1f
            )
        }
    }
}

/**
 * Modal bottom sheet / frosted dialog for selecting color values.
 */
@Composable
fun ColorPickerBottomSheet(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val defaultColor = MaterialTheme.colorScheme.primary
    var selectedColor by remember(initialHex) { mutableStateOf(parseComposeColor(initialHex, defaultColor)) }
    var hexText by remember(initialHex) { mutableStateOf(initialHex) }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )

                Text(
                    text = hexText.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

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
                    label = { Text("Hex Color") },
                    singleLine = true,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    )
}
