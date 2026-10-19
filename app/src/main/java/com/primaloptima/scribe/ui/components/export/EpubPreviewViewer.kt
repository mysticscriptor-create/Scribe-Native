package com.primaloptima.scribe.ui.components.export

import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.primaloptima.scribe.util.image.ImageMimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

data class ExtractedEpubChapter(
    val title: String,
    val entryName: String,
    val xhtmlContent: String
)

data class EpubTocItem(
    val label: String,
    val target: String,
    val filename: String,
    val anchor: String?,
    val children: List<EpubTocItem> = emptyList()
)

/**
 * Publication-aware EPUB 3.3 package preview reader.
 * Inspects the actual compiled .epub ZIP archive, extracts the package manifest,
 * resolves OEBPS/styles.css and XHTML documents with embedded assets, and provides
 * interactive chapter and hierarchical Table of Contents outline navigation.
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
    var tocItems by remember { mutableStateOf<List<EpubTocItem>>(emptyList()) }
    var expandedTargets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTocDrawer by remember { mutableStateOf(false) }
    var selectedChapterIdx by remember { mutableIntStateOf(0) }
    var targetAnchor by remember { mutableStateOf<String?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(epubFile) {
        if (epubFile != null && epubFile.exists() && epubFile.length() > 0L) {
            withContext(Dispatchers.IO) {
                try {
                    ZipFile(epubFile).use { zip ->
                        val entries = zip.entries().asSequence().map { it.name }.toList()

                        // 1. Extract styles.css
                        val cssEntry = zip.getEntry("OEBPS/styles.css")
                        val rawCss = if (cssEntry != null) {
                            zip.getInputStream(cssEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } else ""

                        // 2. Extract inlined images
                        val imageEntries = entries.filter { it.startsWith("OEBPS/images/") }
                        val imageMap = mutableMapOf<String, String>()
                        for (imgEntryName in imageEntries) {
                            val entry = zip.getEntry(imgEntryName)
                            if (entry != null) {
                                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                val mime = ImageMimeUtil.detect(bytes, imgEntryName).mimeType
                                val relPath = imgEntryName.removePrefix("OEBPS/")
                                imageMap[relPath] = "data:$mime;base64,$base64"
                            }
                        }

                        // 3. Extract nav.xhtml for hierarchical TOC
                        val navEntry = zip.getEntry("OEBPS/nav.xhtml")
                        val navXml = if (navEntry != null) {
                            zip.getInputStream(navEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } else ""
                        val parsedToc = if (navXml.isNotBlank()) parseNavXhtml(navXml) else emptyList()

                        // 4. Find all content chapters
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

                            for ((imgRelPath, dataUri) in imageMap) {
                                xhtml = xhtml.replace("src=\"$imgRelPath\"", "src=\"$dataUri\"")
                            }

                            if (rawCss.isNotBlank()) {
                                xhtml = xhtml.replace("</head>", "<style>\n$rawCss\n</style>\n</head>")
                            }

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
                        tocItems = parsedToc
                        // Default all parent chapters to expanded in the outline
                        expandedTargets = parsedToc.map { it.target }.toSet()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // EPUB package banner with interactive TOC toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EPUB 3.3 Package · ${currentChapter?.entryName ?: "OEBPS"} (${fileKb} KB)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (tocItems.isNotEmpty()) {
                        IconButton(
                            onClick = { showTocDrawer = !showTocDrawer },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (showTocDrawer) Icons.Default.Close else Icons.Default.FormatListBulleted,
                                contentDescription = if (showTocDrawer) "Close Table of Contents" else "Open Table of Contents",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Chapter Navigation Chips
            if (chapters.size > 1 && !showTocDrawer) {
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
                                    javaScriptEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    loadsImagesAutomatically = true
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        targetAnchor?.let { anchor ->
                                            view?.evaluateJavascript(
                                                "document.getElementById('$anchor')?.scrollIntoView({behavior: 'smooth'})",
                                                null
                                            )
                                            targetAnchor = null
                                        }
                                    }
                                }
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
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

        // Expandable Hierarchical Table of Contents Drawer Overlay
        AnimatedVisibility(
            visible = showTocDrawer,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 4 }),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 42.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TABLE OF CONTENTS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { showTocDrawer = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Outline", modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        tocItems.forEach { rootItem ->
                            TocOutlineNode(
                                item = rootItem,
                                depth = 0,
                                expandedTargets = expandedTargets,
                                onToggleExpand = { target ->
                                    expandedTargets = if (expandedTargets.contains(target)) {
                                        expandedTargets - target
                                    } else {
                                        expandedTargets + target
                                    }
                                },
                                onSelectNode = { selectedItem ->
                                    val chapIdx = chapters.indexOfFirst {
                                        it.entryName.endsWith(selectedItem.filename)
                                    }
                                    if (chapIdx >= 0) {
                                        selectedChapterIdx = chapIdx
                                        targetAnchor = selectedItem.anchor
                                        if (selectedItem.anchor != null) {
                                            webViewInstance?.evaluateJavascript(
                                                "document.getElementById('${selectedItem.anchor}')?.scrollIntoView({behavior: 'smooth'})",
                                                null
                                            )
                                        }
                                    }
                                    showTocDrawer = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TocOutlineNode(
    item: EpubTocItem,
    depth: Int,
    expandedTargets: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelectNode: (EpubTocItem) -> Unit
) {
    val hasChildren = item.children.isNotEmpty()
    val isExpanded = expandedTargets.contains(item.target)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 18).dp)
                .clickable { onSelectNode(item) }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleExpand(item.target) },
                    modifier = Modifier
                        .size(24.dp)
                        .semantics {
                            role = Role.Button
                            stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                        }
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse ${item.label}" else "Expand ${item.label}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Text(
                text = item.label,
                style = when (depth) {
                    0 -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    1 -> MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    else -> MaterialTheme.typography.bodySmall
                },
                color = when (depth) {
                    0 -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (hasChildren && isExpanded) {
            item.children.forEach { child ->
                TocOutlineNode(
                    item = child,
                    depth = depth + 1,
                    expandedTargets = expandedTargets,
                    onToggleExpand = onToggleExpand,
                    onSelectNode = onSelectNode
                )
            }
        }
    }
}

/**
 * Lightweight XML Pull Parser that builds a hierarchical tree of [EpubTocItem]
 * from the EPUB 3.3 `<nav epub:type="toc">` structure.
 */
private fun parseNavXhtml(xml: String): List<EpubTocItem> {
    return try {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        val rootList = mutableListOf<EpubTocItem>()
        // Simple stack-based parser traversing nested <ol> -> <li> -> <a> -> (nested <ol>)
        var inTocNav = false
        val olStack = mutableListOf<MutableList<EpubTocItem>>()
        var currentHref: String? = null
        var currentLabel = StringBuilder()
        var capturingText = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "nav") {
                        val type = parser.getAttributeValue(null, "epub:type") ?: ""
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        if (type == "toc" || id == "toc") {
                            inTocNav = true
                        }
                    } else if (inTocNav && name == "ol") {
                        olStack.add(mutableListOf())
                    } else if (inTocNav && name == "a") {
                        currentHref = parser.getAttributeValue(null, "href")
                        currentLabel = StringBuilder()
                        capturingText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTocNav && capturingText) {
                        currentLabel.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "a") {
                        capturingText = false
                    } else if (inTocNav && name == "li") {
                        val href = currentHref ?: ""
                        val label = currentLabel.toString().trim()
                        val filename = href.substringBefore('#').substringAfterLast('/')
                        val anchor = if (href.contains('#')) href.substringAfter('#') else null
                        if (label.isNotBlank() && olStack.isNotEmpty()) {
                            val newItem = EpubTocItem(
                                label = label,
                                target = href,
                                filename = filename,
                                anchor = anchor,
                                children = emptyList()
                            )
                            olStack.last().add(newItem)
                        }
                        currentHref = null
                        currentLabel = StringBuilder()
                    } else if (inTocNav && name == "ol") {
                        if (olStack.isNotEmpty()) {
                            val finishedList = olStack.removeAt(olStack.lastIndex)
                            if (olStack.isNotEmpty() && olStack.last().isNotEmpty()) {
                                val parent = olStack.last().removeAt(olStack.last().lastIndex)
                                olStack.last().add(parent.copy(children = finishedList))
                            } else {
                                rootList.addAll(finishedList)
                            }
                        }
                    } else if (name == "nav") {
                        inTocNav = false
                    }
                }
            }
            eventType = parser.next()
        }
        rootList
    } catch (_: Exception) {
        emptyList()
    }
}
