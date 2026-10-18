package com.primaloptima.scribe.ui.components.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.util.export.ExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private const val CHUNK_SIZE = 50

private data class SourceChunk(
    val chunkIndex: Int,
    val startLineNumber: Int,
    val lineNumbersText: String,
    val contentText: String
)

private data class ZipBundleManifest(
    val mdFileName: String,
    val mdContent: String,
    val bundledImages: List<String>,
    val fileKb: Long
)

/**
 * Virtualized monospace document source inspector with synchronized line numbers.
 * Supports direct text, standalone .md / .txt artifacts, and .zip asset bundle archive inspection
 * with honest packaging metadata.
 */
@Composable
fun SourcePreviewViewer(
    artifactFile: File? = null,
    sourceText: String? = null,
    format: ExportFormat = ExportFormat.MD,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
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
                Spacer(modifier = Modifier.height(12.dp))
                val label = if (format == ExportFormat.MD) "Markdown" else "Plain text"
                Text(
                    text = "Compiling $label manuscript…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (errorMessage != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    // Inspect artifact off-thread
    var zipManifest by remember { mutableStateOf<ZipBundleManifest?>(null) }
    var loadedArtifactText by remember { mutableStateOf<String?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artifactFile, format) {
        if (artifactFile != null && artifactFile.exists() && artifactFile.length() > 0L) {
            withContext(Dispatchers.IO) {
                try {
                    val isZip = artifactFile.name.endsWith(".zip", ignoreCase = true)
                    if (isZip) {
                        ZipFile(artifactFile).use { zip ->
                            val entries = zip.entries().asSequence().map { it.name }.toList()
                            val mdEntry = entries.firstOrNull { it.endsWith(".md", ignoreCase = true) }
                            val mdContent = if (mdEntry != null) {
                                zip.getInputStream(zip.getEntry(mdEntry)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                            } else {
                                "# No markdown document found in ZIP bundle"
                            }
                            val imageEntries = entries.filter { it.startsWith("images/") }
                            zipManifest = ZipBundleManifest(
                                mdFileName = mdEntry ?: "manuscript.md",
                                mdContent = mdContent,
                                bundledImages = imageEntries,
                                fileKb = artifactFile.length() / 1024L
                            )
                            loadedArtifactText = mdContent
                            parseError = null
                        }
                    } else {
                        zipManifest = null
                        loadedArtifactText = artifactFile.readText(Charsets.UTF_8)
                        parseError = null
                    }
                } catch (e: Exception) {
                    parseError = "Failed to inspect artifact: ${e.localizedMessage}"
                }
            }
        } else {
            zipManifest = null
            loadedArtifactText = null
        }
    }

    if (parseError != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = parseError ?: "Error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    val activeText = loadedArtifactText ?: sourceText ?: ""
    val fileKb = remember(artifactFile) { artifactFile?.let { it.length() / 1024L } ?: (activeText.toByteArray(Charsets.UTF_8).size / 1024L) }

    // Active sub-tab for zip bundles: 0 = Manuscript, 1 = Archive Manifest
    var bundleViewMode by remember { mutableIntStateOf(0) }

    val chunks = remember(activeText) {
        val lines = activeText.lines()
        val totalLines = lines.size
        val chunkList = ArrayList<SourceChunk>((totalLines / CHUNK_SIZE) + 1)
        var start = 0
        var chunkIdx = 0
        while (start < totalLines) {
            val end = (start + CHUNK_SIZE).coerceAtMost(totalLines)
            val chunkLines = lines.subList(start, end)
            val lineNumbers = (start + 1..end).joinToString("\n")
            val chunkContent = chunkLines.joinToString("\n")
            chunkList.add(
                SourceChunk(
                    chunkIndex = chunkIdx++,
                    startLineNumber = start + 1,
                    lineNumbersText = lineNumbers,
                    contentText = chunkContent
                )
            )
            start = end
        }
        chunkList
    }

    val lazyListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Honest artifact preview banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    zipManifest != null -> Icons.Default.FolderZip
                    format == ExportFormat.TXT -> Icons.Default.Article
                    else -> Icons.Default.Code
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val bannerTitle = when {
                    zipManifest != null -> "Markdown Asset Bundle (.zip) · Cached Artifact (${fileKb} KB) · ${zipManifest?.bundledImages?.size ?: 0} images"
                    format == ExportFormat.TXT -> "Plain Text Document (.txt) · Cached Artifact (${fileKb} KB) · Clean Prose"
                    else -> "Markdown Document (.md) · Cached Artifact (${fileKb} KB)"
                }
                Text(
                    text = bannerTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Sub-tabs for zip bundle (Manuscript vs File tree)
        if (zipManifest != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = bundleViewMode == 0,
                    onClick = { bundleViewMode = 0 },
                    label = { Text("Manuscript (${zipManifest?.mdFileName})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = bundleViewMode == 1,
                    onClick = { bundleViewMode = 1 },
                    label = { Text("Archive Files (${1 + (zipManifest?.bundledImages?.size ?: 0)})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (zipManifest != null && bundleViewMode == 1) {
            // Display Bundle Archive File Tree
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Archive Contents",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Text(
                        text = "📄 ${zipManifest?.mdFileName} (Markdown manuscript with normalized relative image links)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                zipManifest?.bundledImages?.let { images ->
                    items(images) { img ->
                        Text(
                            text = "🖼️ $img",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Virtualized source text viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    items(
                        items = chunks,
                        key = { it.chunkIndex }
                    ) { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Line Numbers Gutter
                            Text(
                                text = chunk.lineNumbersText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .width(42.dp)
                                    .padding(end = 12.dp)
                            )

                            // Source Content
                            Text(
                                text = chunk.contentText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
