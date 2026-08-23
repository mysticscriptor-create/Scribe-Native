package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.primaloptima.scribe.util.WorldImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ImageCropperDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onCropFinished: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedAspect by remember { mutableStateOf(WorldImageUtil.AspectRatio.PORTRAIT_3_4) }
    var rotationDegrees by remember { mutableStateOf(0f) }

    // Transform states
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Canvas / Frame layout dimensions
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        loadedBitmap = WorldImageUtil.loadBitmapFromUri(context, sourceUri)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121316)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // ── Top Bar ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        text = "Crop & Frame Image",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = {
                            val bitmap = loadedBitmap ?: return@Button
                            // Calculate crop in bitmap coordinates
                            val cropped = cropBitmap(
                                source = bitmap,
                                selectedAspect = selectedAspect,
                                scale = scale,
                                offset = offset,
                                rotation = rotationDegrees,
                                viewportSize = viewportSize
                            )
                            if (cropped != null) {
                                onCropFinished(cropped)
                            }
                        },
                        enabled = loadedBitmap != null && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }

                // ── Main Crop Viewport ───────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E2024))
                        .onGloballyPositioned { coordinates ->
                            viewportSize = coordinates.size
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading || loadedBitmap == null) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        val bitmap = loadedBitmap!!
                        val imageBitmap = remember(bitmap, rotationDegrees) {
                            if (rotationDegrees == 0f) bitmap.asImageBitmap()
                            else {
                                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                                val rotated = Bitmap.createBitmap(
                                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                )
                                rotated.asImageBitmap()
                            }
                        }

                        // Calculate crop framing rect inside viewport
                        val frameAspect = if (selectedAspect == WorldImageUtil.AspectRatio.FREE) {
                            bitmap.width.toFloat() / bitmap.height.toFloat()
                        } else {
                            selectedAspect.ratio
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            val viewW = size.width
                            val viewH = size.height

                            // Draw image with transform
                            val bmpW = imageBitmap.width.toFloat()
                            val bmpH = imageBitmap.height.toFloat()
                            val fitScale = min(viewW / bmpW, viewH / bmpH)

                            val drawW = bmpW * fitScale * scale
                            val drawH = bmpH * fitScale * scale

                            val left = (viewW - drawW) / 2f + offset.x
                            val top = (viewH - drawH) / 2f + offset.y

                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                            )

                            // Compute Crop Frame Box (centered in viewport)
                            val maxFrameW = viewW * 0.90f
                            val maxFrameH = viewH * 0.90f

                            val cropW: Float
                            val cropH: Float
                            if (maxFrameW / frameAspect <= maxFrameH) {
                                cropW = maxFrameW
                                cropH = maxFrameW / frameAspect
                            } else {
                                cropH = maxFrameH
                                cropW = maxFrameH * frameAspect
                            }

                            val cropLeft = (viewW - cropW) / 2f
                            val cropTop = (viewH - cropH) / 2f

                            // Dark overlay outside crop box
                            // Top scrim
                            drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset.Zero, size = Size(viewW, cropTop))
                            // Bottom scrim
                            drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, cropTop + cropH), size = Size(viewW, viewH - (cropTop + cropH)))
                            // Left scrim
                            drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropH))
                            // Right scrim
                            drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(cropLeft + cropW, cropTop), size = Size(viewW - (cropLeft + cropW), cropH))

                            // Crop frame border
                            drawRect(
                                color = Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Rule of thirds grid lines
                            val oneThirdW = cropW / 3f
                            val oneThirdH = cropH / 3f
                            // Vertical lines
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft + oneThirdW, cropTop), Offset(cropLeft + oneThirdW, cropTop + cropH), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft + oneThirdW * 2, cropTop), Offset(cropLeft + oneThirdW * 2, cropTop + cropH), strokeWidth = 1.dp.toPx())
                            // Horizontal lines
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft, cropTop + oneThirdH), Offset(cropLeft + cropW, cropTop + oneThirdH), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft, cropTop + oneThirdH * 2), Offset(cropLeft + cropW, cropTop + oneThirdH * 2), strokeWidth = 1.dp.toPx())
                        }
                    }
                }

                // ── Aspect Ratio & Quick Transform Controls ──────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16181C))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Aspect ratio chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val aspectRatios = listOf(
                            WorldImageUtil.AspectRatio.PORTRAIT_3_4,
                            WorldImageUtil.AspectRatio.LANDSCAPE_16_9,
                            WorldImageUtil.AspectRatio.SQUARE_1_1,
                            WorldImageUtil.AspectRatio.FREE
                        )

                        aspectRatios.forEach { aspect ->
                            val isSelected = selectedAspect == aspect
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedAspect = aspect
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                label = {
                                    Text(
                                        text = when (aspect) {
                                            WorldImageUtil.AspectRatio.PORTRAIT_3_4 -> "Portrait (3:4)"
                                            WorldImageUtil.AspectRatio.LANDSCAPE_16_9 -> "Landscape (16:9)"
                                            WorldImageUtil.AspectRatio.SQUARE_1_1 -> "Square (1:1)"
                                            WorldImageUtil.AspectRatio.FREE -> "Original"
                                            else -> aspect.label
                                        },
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = Color(0xFF24272D),
                                    labelColor = Color.LightGray
                                )
                            )
                        }
                    }

                    // Secondary Tools: Rotate, Reset, Zoom In/Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90f) % 360f
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF2A2D34),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                    rotationDegrees = 0f
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF2A2D34),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom & Pan")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Zoom: ${(scale * 100).roundToInt()}%",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { scale = (scale - 0.25f).coerceAtLeast(0.5f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.LightGray)
                            }
                            IconButton(
                                onClick = { scale = (scale + 0.25f).coerceAtMost(5f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Accurately extracts and renders the cropped region of the Bitmap based on viewport transform math.
 */
private fun cropBitmap(
    source: Bitmap,
    selectedAspect: WorldImageUtil.AspectRatio,
    scale: Float,
    offset: Offset,
    rotation: Float,
    viewportSize: IntSize
): Bitmap? {
    try {
        // Step 1: Rotate if needed
        val rotatedSource = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val viewW = viewportSize.width.toFloat()
        val viewH = viewportSize.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return rotatedSource

        val bmpW = rotatedSource.width.toFloat()
        val bmpH = rotatedSource.height.toFloat()
        val fitScale = min(viewW / bmpW, viewH / bmpH)

        val drawW = bmpW * fitScale * scale
        val drawH = bmpH * fitScale * scale

        val imgLeft = (viewW - drawW) / 2f + offset.x
        val imgTop = (viewH - drawH) / 2f + offset.y

        val frameAspect = if (selectedAspect == WorldImageUtil.AspectRatio.FREE) {
            bmpW / bmpH
        } else {
            selectedAspect.ratio
        }

        val maxFrameW = viewW * 0.90f
        val maxFrameH = viewH * 0.90f
        val cropW: Float
        val cropH: Float
        if (maxFrameW / frameAspect <= maxFrameH) {
            cropW = maxFrameW
            cropH = maxFrameW / frameAspect
        } else {
            cropH = maxFrameH
            cropW = maxFrameH * frameAspect
        }

        val cropLeft = (viewW - cropW) / 2f
        val cropTop = (viewH - cropH) / 2f

        // Map crop window coordinates back to rotatedSource bitmap pixels
        val pixelScale = bmpW / drawW

        val srcCropX = ((cropLeft - imgLeft) * pixelScale).roundToInt()
        val srcCropY = ((cropTop - imgTop) * pixelScale).roundToInt()
        val srcCropW = (cropW * pixelScale).roundToInt()
        val srcCropH = (cropH * pixelScale).roundToInt()

        // Clamp to bitmap boundaries
        val safeX = srcCropX.coerceIn(0, rotatedSource.width - 1)
        val safeY = srcCropY.coerceIn(0, rotatedSource.height - 1)
        val safeW = min(srcCropW, rotatedSource.width - safeX).coerceAtLeast(1)
        val safeH = min(srcCropH, rotatedSource.height - safeY).coerceAtLeast(1)

        return Bitmap.createBitmap(rotatedSource, safeX, safeY, safeW, safeH)
    } catch (e: Exception) {
        e.printStackTrace()
        return source
    }
}
