package com.primaloptima.scribe.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen pinch-to-zoom Lightbox for manuscript and document images.
 * Provides multi-touch zoom (up to 6x), double-tap reset, image renaming/captioning,
 * delete from document/disk, sharing via FileProvider, and saving to device gallery.
 */
@Composable
fun FullScreenImageViewer(
    imageUri: String,
    title: String = "",
    subtitle: String = "",
    bookId: String? = null,
    noteId: String = "",
    onUpdateTitle: ((newTitle: String) -> Unit)? = null,
    onDelete: ((removeFromDisk: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .systemBarsPadding()
        ) {
            // ── Main interactive image canvas ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 6f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
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
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // ── Top Bar Overlay ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = currentTitle.ifBlank { "Illustration" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (scale > 1.05f || offset != Offset.Zero) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f)
                        )
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Reset Zoom",
                            tint = Color.White
                        )
                    }
                }
            }

            // ── Bottom Action Bar Overlay ───────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    Color.White.copy(alpha = 0.20f)
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
                                onDismiss()
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
}
