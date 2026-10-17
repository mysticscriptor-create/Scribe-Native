package com.primaloptima.scribe.ui.components.export

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pixel-perfect native PDF Preview component utilizing [PdfRenderer].
 * Renders pages asynchronously into an LRU bitmap memory cache, ensuring zero OOM crashes
 * on large book manuscripts. Features pinch-to-zoom, page shadows, and floating page pill.
 */
@Composable
fun PdfPreviewViewer(
    pdfFile: File?,
    isLoading: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Rendering print pages…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (errorMessage != null || pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0L) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = errorMessage ?: "Unable to render PDF preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    var renderer by remember(pdfFile) { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember(pdfFile) { mutableIntStateOf(0) }
    var fileDescriptor by remember(pdfFile) { mutableStateOf<ParcelFileDescriptor?>(null) }

    // LRU Bitmap cache (max 6 page bitmaps cached in memory at once)
    val bitmapCache = remember(pdfFile) {
        object : LruCache<Int, Bitmap>(6) {
            override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
                if (evicted && oldValue != null && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }
    }

    val pageBitmaps = remember(pdfFile) { mutableStateMapOf<Int, Bitmap>() }
    val scope = rememberCoroutineScope()

    DisposableEffect(pdfFile) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(pfd)
            fileDescriptor = pfd
            renderer = pdfRenderer
            pageCount = pdfRenderer.pageCount
        } catch (_: Throwable) {
            pageCount = 0
        }

        onDispose {
            try {
                renderer?.close()
                fileDescriptor?.close()
                bitmapCache.evictAll()
                pageBitmaps.clear()
            } catch (_: Throwable) {}
        }
    }

    // Zoom & pan transformations
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 3.5f)
        offset = if (scale > 1f) {
            Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
        } else {
            Offset.Zero
        }
    }

    val listState = rememberLazyListState()
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF202124)) // Dark neutral print inspection backdrop
            .transformable(state = transformState)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(count = pageCount) { pageIndex ->
                PdfPageItem(
                    pageIndex = pageIndex,
                    renderer = renderer,
                    cachedBitmap = pageBitmaps[pageIndex],
                    onRenderPage = { idx ->
                        scope.launch(Dispatchers.IO) {
                            val cached = bitmapCache.get(idx)
                            if (cached != null && !cached.isRecycled) {
                                withContext(Dispatchers.Main) {
                                    pageBitmaps[idx] = cached
                                }
                                return@launch
                            }

                            val ren = renderer ?: return@launch
                            synchronized(ren) {
                                try {
                                    val page = ren.openPage(idx)
                                    val targetWidth = 1080
                                    val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(AndroidColor.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()

                                    bitmapCache.put(idx, bmp)
                                    scope.launch(Dispatchers.Main) {
                                        pageBitmaps[idx] = bmp
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                )
            }
        }

        // Floating Page Indicator Pill ("Page 1 of 8")
        if (pageCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "Page ${firstVisibleItemIndex + 1} of $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    renderer: PdfRenderer?,
    cachedBitmap: Bitmap?,
    onRenderPage: (Int) -> Unit
) {
    LaunchedEffect(pageIndex, renderer) {
        if (cachedBitmap == null) {
            onRenderPage(pageIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(4.dp), clip = false)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(0.5.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Placeholder while rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.414f) // Standard ISO A4 aspect ratio placeholder
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
    }
}
