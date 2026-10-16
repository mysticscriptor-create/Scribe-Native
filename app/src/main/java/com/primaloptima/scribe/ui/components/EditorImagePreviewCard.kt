package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.engine.ImageHintClickData
import com.primaloptima.scribe.util.image.VaultImageManager

/**
 * Pure minimalist floating preview thumbnail anchored near a tapped image inlay hint in Sora editor.
 * Features a compact fixed height of 110dp with dynamic width adjusting to the natural aspect ratio.
 * Displays ONLY the clean image preview without text clutter. Tapping expands into fullscreen lightbox.
 */
@Composable
fun EditorImagePreviewCard(
    data: ImageHintClickData,
    bookId: String?,
    noteId: String,
    modifier: Modifier = Modifier,
    onExpandFullscreen: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageModel = remember(data.imageUri, bookId, noteId) {
        VaultImageManager.resolveModel(context, data.imageUri, bookId, noteId)
    }
    val metadata = remember(data.imageUri, bookId, noteId) {
        VaultImageManager.getImageMetadata(context, data.imageUri, bookId, noteId)
    }

    // Dynamic width calculation based on natural aspect ratio with 110dp fixed height
    val aspect = remember(metadata.width, metadata.height) {
        if (metadata.width > 0 && metadata.height > 0) {
            (metadata.width.toFloat() / metadata.height.toFloat()).coerceIn(0.6f, 2.2f)
        } else {
            1.33f
        }
    }
    val targetWidth = (110.dp * aspect).coerceIn(80.dp, 180.dp)

    Surface(
        modifier = modifier
            .height(110.dp)
            .width(targetWidth)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onExpandFullscreen() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = data.description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
