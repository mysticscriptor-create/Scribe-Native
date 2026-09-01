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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Background Artwork & Canvas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (bgUri.isNullOrEmpty()) "Solid Base" else "Artwork Active",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Apply immersive editorial cover photography or textured patterns under the writing canvas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                if (bgUri.isNullOrEmpty()) {
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Artwork Image", fontWeight = FontWeight.SemiBold)
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
                            Text("Change Image", fontWeight = FontWeight.SemiBold)
                        }

                        if (!bgOriginalUri.isNullOrEmpty() || !bgUri.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = onCropImage,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = "Crop", modifier = Modifier.size(16.dp))
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
                        Text("Soft Blur Artwork Canvas", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = bgMode == "blurred",
                            onCheckedChange = { isBlurred ->
                                onBgModeChange(if (isBlurred) "blurred" else "image")
                            }
                        )
                    }

                    if (bgMode == "blurred") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Artwork Blur Radius", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${blurIntensity.toInt()} dp",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Slider(
                                value = blurIntensity,
                                onValueChange = onBlurIntensityChange,
                                valueRange = 0f..30f
                            )
                        }
                    }

                    // Overlay Opacity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Contrast Darkening Overlay", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${(bgOpacity * 100).toInt()}%",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frosted Glass & Translucency",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (frostedGlassEnabled) "Glass On" else "Opaque Surface",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = "Refined hardware-accelerated frosted glass diffusion across toolbars and elevated cards.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Glassmorphic Diffusion", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                        Text("Tint with Theme Surface Base", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = frostedTintEnabled,
                            onCheckedChange = onFrostedTintEnabledChange
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Glass Blur Strength", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${frostedBlurRadius.toInt()} dp",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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

