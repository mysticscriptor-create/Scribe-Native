package com.primaloptima.scribe.ui.components.export

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.util.export.ExportArtifactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Crash-Proof Native PDF Document Viewer for Scribe.
 * Uses Android's native [PdfRenderer] with serialized access, strict finally page closure,
 * byte-bounded LRU bitmap caching, active artifact retention, and smooth zoom/pan gestures.
 */
@Composable
fun PdfPreviewViewer(
    pdfFile: File?,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF202124)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Rendering PDF vector preview...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
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

    // Mutex to strictly serialize native PdfRenderer access (only 1 page open at a time globally)
    val renderMutex = remember(pdfFile) { Mutex() }

    // Byte-bounded LRU Bitmap cache (Max 12.5% of total JVM heap)
    val bitmapCache = remember(pdfFile) {
        val maxMemoryBytes = Runtime.getRuntime().maxMemory()
        val cacheSizeBytes = (maxMemoryBytes / 8).coerceIn(16L * 1024 * 1024, 80L * 1024 * 1024).toInt()
        object : LruCache<String, Bitmap>(cacheSizeBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    DisposableEffect(pdfFile) {
        if (pdfFile.exists() && pdfFile.length() > 0L) {
            ExportArtifactRepository.retainArtifact(pdfFile)
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
            } catch (_: Throwable) {
                pageCount = 0
            }
        }

        onDispose {
            val ren = renderer
            val pfd = fileDescriptor
            renderer = null
            fileDescriptor = null

            // Close renderer asynchronously under mutex so active page renders complete first
            scope.launch(Dispatchers.IO) {
                renderMutex.withLock {
                    try { ren?.close() } catch (_: Throwable) {}
                    try { pfd?.close() } catch (_: Throwable) {}
                }
            }
            bitmapCache.evictAll()
            ExportArtifactRepository.releaseArtifact(pdfFile)
        }
    }

    // Zoom & pan transformations
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.85f, 3.0f)
        offset = if (scale > 1f) {
            Offset(offset.x + offsetChange.x, offset.y + offsetChange.y)
        } else {
            Offset.Zero
        }
    }

    val listState = rememberLazyListState()
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) { screenWidthDp.dp.roundToPx() }
    val effectiveTargetWidth = remember(screenWidthPx, scale) {
        ((screenWidthPx * 0.92f * scale.coerceAtLeast(1f)).toInt()).coerceIn(720, 1800)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF202124)) // Dark neutral desk mat
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
                    targetWidth = effectiveTargetWidth,
                    fileId = pdfFile.name,
                    bitmapCache = bitmapCache,
                    onRenderPage = { idx, targetW ->
                        withContext(Dispatchers.IO) {
                            renderMutex.withLock {
                                val cacheKey = "${pdfFile.name}_p${idx}_w${targetW}"
                                val existing = bitmapCache.get(cacheKey)
                                if (existing != null && !existing.isRecycled) {
                                    return@withLock existing
                                }
                                val ren = renderer ?: return@withLock null
                                val page = try {
                                    ren.openPage(idx)
                                } catch (_: Throwable) {
                                    return@withLock null
                                }
                                try {
                                    val targetHeight = (targetW * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(100)
                                    val bmp = Bitmap.createBitmap(targetW, targetHeight, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(AndroidColor.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmapCache.put(cacheKey, bmp)
                                    bmp
                                } catch (_: Throwable) {
                                    null
                                } finally {
                                    try {
                                        page.close()
                                    } catch (_: Throwable) {}
                                }
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
    targetWidth: Int,
    fileId: String,
    bitmapCache: LruCache<String, Bitmap>,
    onRenderPage: suspend (Int, Int) -> Bitmap?
) {
    val cacheKey = "${fileId}_p${pageIndex}_w${targetWidth}"
    var bitmap by remember(cacheKey) { mutableStateOf(bitmapCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            bitmap = cached
        } else {
            val rendered = onRenderPage(pageIndex, targetWidth)
            if (rendered != null && !rendered.isRecycled) {
                bitmap = rendered
            }
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
        val currentBitmap = bitmap
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Placeholder while rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / 1.414f) // ISO A4 aspect ratio placeholder
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
