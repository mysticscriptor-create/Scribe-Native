package com.primaloptima.scribe.ui.screens.themeeditor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Inspector panel for Atmosphere, Background Images, Blur, and Glassmorphic effects.
 */
@Composable
fun ThemeAtmospherePanel(
    bgMode: String,
    bgUri: String?,
    bgOriginalUri: String?,
    bgOpacity: Float,
    blurIntensity: Float,
    frostedGlassEnabled: Boolean,
    frostedTintEnabled: Boolean,
    frostedBlurRadius: Float,
    onPickImage: () -> Unit,
    onCropImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onBgModeChange: (String) -> Unit,
    onBgOpacityChange: (Float) -> Unit,
    onBlurIntensityChange: (Float) -> Unit,
    onFrostedGlassEnabledChange: (Boolean) -> Unit,
    onFrostedTintEnabledChange: (Boolean) -> Unit,
    onFrostedBlurRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Background Image Card
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
                    text = "Background Artwork & Canvas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (bgUri.isNullOrEmpty()) {
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Background Image")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPickImage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Change Image")
                        }

                        if (!bgOriginalUri.isNullOrEmpty() || !bgUri.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = onCropImage,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = "Crop", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Crop")
                            }
                        }

                        IconButton(
                            onClick = onRemoveImage,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }

                    // Background Mode (Clear Image vs Blurred Image)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blur Image Canvas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = bgMode == "blurred",
                            onCheckedChange = { isBlurred ->
                                onBgModeChange(if (isBlurred) "blurred" else "image")
                            }
                        )
                    }

                    if (bgMode == "blurred") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Artwork Blur Radius", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("${blurIntensity.toInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = blurIntensity,
                                onValueChange = onBlurIntensityChange,
                                valueRange = 0f..30f
                            )
                        }
                    }

                    // Overlay Opacity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Darkening Overlay Opacity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${(bgOpacity * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = bgOpacity,
                            onValueChange = onBgOpacityChange,
                            valueRange = 0f..0.90f
                        )
                    }
                }
            }
        }

        // Frosted Glass Card
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
                    text = "Frosted Glass & Translucency",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Glassmorphism", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = frostedGlassEnabled,
                        onCheckedChange = onFrostedGlassEnabledChange
                    )
                }

                if (frostedGlassEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tint with Theme Surface", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = frostedTintEnabled,
                            onCheckedChange = onFrostedTintEnabledChange
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Glass Blur Strength", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${frostedBlurRadius.toInt()} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = frostedBlurRadius,
                            onValueChange = onFrostedBlurRadiusChange,
                            valueRange = 0f..40f
                        )
                    }
                }
            }
        }
    }
}
