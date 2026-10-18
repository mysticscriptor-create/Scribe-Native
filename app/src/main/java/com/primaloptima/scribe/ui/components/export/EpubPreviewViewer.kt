package com.primaloptima.scribe.ui.components.export

import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class ExtractedEpubChapter(
    val title: String,
    val entryName: String,
    val xhtmlContent: String
)

/**
 * Publication-aware EPUB 3.3 package preview reader.
 * Inspects the actual compiled .epub ZIP archive, extracts the package manifest,
 * resolves OEBPS/styles.css and XHTML documents with embedded assets, and provides chapter navigation.
 */
@Composable
fun EpubPreviewViewer(
    epubFile: File?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.surface.toArgb()

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
                Text(
                    text = "Compiling EPUB 3.3 publication package…",
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

    var chapters by remember { mutableStateOf<List<ExtractedEpubChapter>>(emptyList()) }
    var selectedChapterIdx by remember { mutableIntStateOf(0) }
    var parseError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(epubFile) {
        if (epubFile != null && epubFile.exists() && epubFile.length() > 0L) {
            withContext(Dispatchers.IO) {
                try {
                    ZipFile(epubFile).use { zip ->
                        val entries = zip.entries().asSequence().map { it.name }.toList()

                        // Extract CSS if present
                        val cssEntry = zip.getEntry("OEBPS/styles.css")
                        val rawCss = if (cssEntry != null) {
                            zip.getInputStream(cssEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } else ""

                        // Extract image byte cache for inlining into WebView
                        val imageEntries = entries.filter { it.startsWith("OEBPS/images/") }
                        val imageMap = mutableMapOf<String, String>()
                        for (imgEntryName in imageEntries) {
                            val entry = zip.getEntry(imgEntryName)
                            if (entry != null) {
                                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                val mime = when {
                                    imgEntryName.endsWith(".png", true) -> "image/png"
                                    imgEntryName.endsWith(".webp", true) -> "image/webp"
                                    imgEntryName.endsWith(".gif", true) -> "image/gif"
                                    imgEntryName.endsWith(".svg", true) -> "image/svg+xml"
                                    else -> "image/jpeg"
                                }
                                val relPath = imgEntryName.removePrefix("OEBPS/")
                                imageMap[relPath] = "data:$mime;base64,$base64"
                            }
                        }

                        // Find all content chapters (exclude nav.xhtml)
                        val chapterEntryNames = entries
                            .filter { it.startsWith("OEBPS/") && it.endsWith(".xhtml") && !it.endsWith("nav.xhtml") }
                            .sortedWith(Comparator { a, b ->
                                if (a.contains("frontmatter")) -1
                                else if (b.contains("frontmatter")) 1
                                else a.compareTo(b)
                            })

                        val extracted = mutableListOf<ExtractedEpubChapter>()
                        for (entryName in chapterEntryNames) {
                            val entry = zip.getEntry(entryName) ?: continue
                            var xhtml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                            // Replace relative image references with inlined data URIs
                            for ((imgRelPath, dataUri) in imageMap) {
                                xhtml = xhtml.replace("src=\"$imgRelPath\"", "src=\"$dataUri\"")
                            }

                            // Inline extracted OEBPS/styles.css into the HTML head so WebView styles properly
                            if (rawCss.isNotBlank()) {
                                xhtml = xhtml.replace("</head>", "<style>\n$rawCss\n</style>\n</head>")
                            }

                            // Extract clean chapter title
                            val titleMatch = Regex("""<h[1-2][^>]*>(.*?)</h[1-2]>""").find(xhtml)
                            val title = titleMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]*>"""), "")?.trim()
                                ?: entryName.substringAfterLast("/").substringBeforeLast(".")

                            extracted.add(
                                ExtractedEpubChapter(
                                    title = title,
                                    entryName = entryName,
                                    xhtmlContent = xhtml
                                )
                            )
                        }

                        chapters = extracted
                        selectedChapterIdx = 0
                        parseError = null
                    }
                } catch (e: Exception) {
                    parseError = "Failed to inspect EPUB package: ${e.localizedMessage}"
                }
            }
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

    val currentChapter = chapters.getOrNull(selectedChapterIdx)
    val fileKb = remember(epubFile) { epubFile?.let { it.length() / 1024L } ?: 0L }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Honest EPUB package banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EPUB 3.3 Package Reader · ${currentChapter?.entryName ?: "OEBPS"} (${fileKb} KB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Chapter Navigation Chips (if manuscript has multiple chapters)
        if (chapters.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chapters.forEachIndexed { idx, ch ->
                    val isSelected = idx == selectedChapterIdx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedChapterIdx = idx },
                        label = {
                            Text(
                                text = ch.title.take(24),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // WebView rendering selected chapter document
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentChapter != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(bgColor)
                            settings.apply {
                                javaScriptEnabled = false
                                allowFileAccess = false
                                allowContentAccess = false
                                loadsImagesAutomatically = true
                            }
                            webViewClient = object : WebViewClient() {}
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL("file:///android_asset/", currentChapter.xhtmlContent, "text/html", "UTF-8", null)
                    }
                )
            } else if (epubFile != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No readable chapters found in EPUB package",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
