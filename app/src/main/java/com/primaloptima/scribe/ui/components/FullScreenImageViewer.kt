package com.primaloptima.scribe.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen pinch-to-zoom Lightbox for manuscript and document images.
 * Smoothly animates from the floating preview thumbnail geometry into full screen,
 * and smoothly reverses back into the badge on dismissal.
 */
@Composable
fun FullScreenImageViewer(
    imageUri: String,
    title: String = "",
    subtitle: String = "",
    bookId: String? = null,
    noteId: String = "",
    originOffset: Offset? = null,
    originSize: Size? = null,
    onUpdateTitle: ((newTitle: String) -> Unit)? = null,
    onDelete: ((removeFromDisk: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val navBarBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }

    val animProgress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    val requestDismiss: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                animProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
                onDismiss()
            }
        }
    }

    BackHandler(enabled = true) {
        requestDismiss()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var currentTitle by remember { mutableStateOf(title) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteFromDiskChecked by remember { mutableStateOf(true) }
    var isSavingToGallery by remember { mutableStateOf(false) }

    val resolvedModel = remember(imageUri, bookId, noteId) {
        VaultImageManager.resolveModel(context, imageUri, bookId, noteId)
    }

    val progress = animProgress.value
    val backdropAlpha = (progress * 0.95f).coerceIn(0f, 0.95f)
    val controlsAlpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

    // Accurate geometry interpolation accounting for root systemBarsPadding
    val curTransX = if (originOffset != null && originSize != null) {
        val originCenterX = originOffset.x + originSize.width / 2f
        val boxCenterX = screenWidthPx / 2f
        lerp(originCenterX - boxCenterX, 0f, progress) + offset.x
    } else {
        offset.x
    }

    val curTransY = if (originOffset != null && originSize != null) {
        val originCenterY = originOffset.y + originSize.height / 2f - statusBarTopPx
        val boxCenterY = (screenHeightPx - statusBarTopPx - navBarBottomPx) / 2f
        lerp(originCenterY - boxCenterY, 0f, progress) + offset.y
    } else {
        offset.y
    }

    val baseScale = if (originOffset != null && originSize != null) {
        val availableHeight = (screenHeightPx - statusBarTopPx - navBarBottomPx).coerceAtLeast(1f)
        val startScale = (originSize.height / availableHeight).coerceIn(0.08f, 0.75f)
        lerp(startScale, 1f, progress)
    } else {
        lerp(0.85f, 1f, progress)
    }
    val curScale = baseScale * scale
    val curCornerRadius = if (originOffset != null) {
        (14f * (1f - progress)).dp
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(150f)
            .background(Color.Black.copy(alpha = backdropAlpha))
            .systemBarsPadding()
    ) {
        // ── Main interactive image canvas ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (progress > 0.8f) {
                            scale = (scale * zoom).coerceIn(0.8f, 6f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (progress > 0.8f) {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        },
                        onTap = {
                            if (scale <= 1.05f) {
                                requestDismiss()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = resolvedModel,
                contentDescription = currentTitle,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = curScale
                        scaleY = curScale
                        translationX = curTransX
                        translationY = curTransY
                        if (curCornerRadius > 0.dp) {
                            clip = true
                            shape = RoundedCornerShape(curCornerRadius)
                        }
                    }
            )
        }

        // ── Top App Bar Overlay (Fade with controlsAlpha) ───────────
        if (controlsAlpha > 0f) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .graphicsLayer(alpha = controlsAlpha),
                color = Color.Black.copy(alpha = 0.6f * controlsAlpha)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = requestDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTitle.ifBlank { "Illustration" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Reset zoom button
                    if (scale > 1.05f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // ── Bottom Action Bar Overlay (Fade with controlsAlpha) ──────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer(alpha = controlsAlpha),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f * controlsAlpha),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    Color.White.copy(alpha = 0.20f * controlsAlpha)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rename / Caption
                    if (onUpdateTitle != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Caption",
                                tint = Color.White
                            )
                        }
                    }

                    // Share
                    IconButton(
                        onClick = {
                            VaultImageManager.shareImage(
                                context = context,
                                uriString = imageUri,
                                bookId = bookId,
                                noteId = noteId,
                                title = currentTitle
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Image",
                            tint = Color.White
                        )
                    }

                    // Save to Gallery
                    IconButton(
                        onClick = {
                            if (!isSavingToGallery) {
                                isSavingToGallery = true
                                scope.launch {
                                    val success = VaultImageManager.saveToGallery(
                                        context = context,
                                        uriString = imageUri,
                                        bookId = bookId,
                                        noteId = noteId
                                    )
                                    withContext(Dispatchers.Main) {
                                        isSavingToGallery = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Saved to device Pictures/Scribe" else "Failed to save image",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    ) {
                        if (isSavingToGallery) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.SaveAlt,
                                contentDescription = "Save to Gallery",
                                tint = Color.White
                            )
                        }
                    }

                    // Delete
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Image",
                                tint = Color(0xFFFF6B6B)
                            )
                        }
                    }
                }
            }
        }

        // ── Rename / Edit Caption Dialog ────────────────────────────
        if (showRenameDialog) {
            var editCaptionText by remember { mutableStateOf(currentTitle) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Edit Image Caption") },
                text = {
                    OutlinedTextField(
                        value = editCaptionText,
                        onValueChange = { editCaptionText = it },
                        label = { Text("Caption / Alt Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = editCaptionText.trim()
                            currentTitle = trimmed
                            onUpdateTitle?.invoke(trimmed)
                            showRenameDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Delete Confirmation Dialog ──────────────────────────────
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Image?") },
                text = {
                    Column {
                        Text("Remove this image tag from your document text.")
                        if (imageUri.startsWith("vault-img://", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = deleteFromDiskChecked,
                                    onCheckedChange = { deleteFromDiskChecked = it }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Also delete file from vault storage", fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete?.invoke(deleteFromDiskChecked)
                            requestDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
