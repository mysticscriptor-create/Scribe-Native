package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.engine.ImageHintClickData
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.image.VaultImageManager

/**
 * Non-modal floating preview card anchored above/near a tapped image inlay hint in Sora editor.
 */
@Composable
fun EditorImagePreviewCard(
    data: ImageHintClickData,
    bookId: String?,
    noteId: String,
    modifier: Modifier = Modifier,
    onExpandFullscreen: () -> Unit,
    onEditDescription: () -> Unit,
    onDeleteImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = remember(data.imageUri, bookId, noteId) {
        VaultImageManager.resolveModel(context, data.imageUri, bookId, noteId)
    }
    val metadata = remember(data.imageUri, bookId, noteId) {
        VaultImageManager.getImageMetadata(context, data.imageUri, bookId, noteId)
    }

    Surface(
        modifier = modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandFullscreen() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = data.description,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.description.ifBlank { "Illustration" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (metadata.dimensionsFormatted.isNotBlank()) {
                                append(metadata.dimensionsFormatted)
                            }
                            if (metadata.sizeFormatted.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(metadata.sizeFormatted)
                            }
                            if (isEmpty()) {
                                append(metadata.filename)
                            }
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap thumbnail to enlarge",
                        fontSize = 10.sp,
                        color = ScribeTheme.colors.interaction.primary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onExpandFullscreen,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ScribeTheme.colors.interaction.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fullscreen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScribeTheme.colors.interaction.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEditDescription,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Caption",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteImage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Image",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
