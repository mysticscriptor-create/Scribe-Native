package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.image.VaultImageManager

private data class ExportFormatOption(
    val id: String,
    val label: String,
    val ext: String,
    val mimeType: String,
    val icon: ImageVector,
    val description: String
)

private val FORMAT_OPTIONS = listOf(
    ExportFormatOption("pdf", "PDF", ".pdf", "application/pdf", Icons.Default.PictureAsPdf, "Print-ready document with high-resolution illustrations"),
    ExportFormatOption("epub", "EPUB", ".epub", "application/epub+zip", Icons.Default.MenuBook, "Standard reflowable EPUB 3.0 eBook with bundled images & TOC"),
    ExportFormatOption("docx", "DOCX", ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Icons.Default.Description, "Microsoft Word document with embedded drawings & styles"),
    ExportFormatOption("html", "HTML", ".html", "text/html", Icons.Default.Language, "Standalone web page with inline self-contained illustrations"),
    ExportFormatOption("md", "Markdown", ".md", "text/markdown", Icons.Default.Code, "Clean Markdown manuscript source text (packaged with images as zip)"),
    ExportFormatOption("txt", "Plain Text", ".txt", "text/plain", Icons.Default.Article, "Clean text format with editorial illustration notes")
)

/**
 * Modern interactive Export Preview Sheet.
 * Allows authors to review their manuscript before exporting, choose between formats (PDF, EPUB, DOCX, HTML, MD, TXT),
 * view a live formatted rendering with loaded images, and either save directly to device storage or share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPreviewSheet(
    note: Note,
    bookId: String?,
    initialFormat: String = "pdf",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(initialFormat.lowercase()) }
    var previewMode by remember { mutableStateOf(0) } // 0: Formatted, 1: Raw

    val wordCount = remember(note.content) {
        note.content.split(Regex("""\s+""")).count { it.isNotBlank() }
    }
    val charCount = remember(note.content) { note.content.length }

    val scope = rememberCoroutineScope()
    val currentFormatMeta = FORMAT_OPTIONS.firstOrNull { it.id == selectedFormat } ?: FORMAT_OPTIONS.first()
    val hasVaultImages = remember(note.content) { VaultImageManager.hasImages(note.content) }
    val effectiveMimeType = if (selectedFormat == "md" && hasVaultImages) {
        "application/zip"
    } else {
        currentFormatMeta.mimeType
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(effectiveMimeType)
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                        val bytes = ExportHelper.exportBytes(context, note, selectedFormat)
                        os.write(bytes)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved ${currentFormatMeta.label} manuscript successfully", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            // ── Top Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Manuscript",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${note.name.ifBlank { "Untitled" }} · $wordCount words · $charCount chars",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Format Selector Chips ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FORMAT_OPTIONS.forEach { opt ->
                    val isSelected = opt.id == selectedFormat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = opt.id },
                        label = { Text(opt.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                imageVector = opt.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // ── Format Description Banner ──────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = currentFormatMeta.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentFormatMeta.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Preview Mode Toggle (Rendered vs Raw) ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                TabRow(
                    selectedTabIndex = previewMode,
                    modifier = Modifier.width(220.dp),
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = previewMode == 0,
                        onClick = { previewMode = 0 },
                        text = { Text("Rendered", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = previewMode == 1,
                        onClick = { previewMode = 1 },
                        text = { Text("Source", fontSize = 12.sp) }
                    )
                }
            }

            // ── Document Preview Container ─────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                val rawSourceContent = remember(selectedFormat, previewMode, note.content) {
                    if (previewMode == 1) {
                        when (selectedFormat) {
                            "html" -> ExportHelper.fullHtml(context, note)
                            "txt" -> MarkdownUtil.toPlainText(note.content)
                            "md" -> note.content
                            "epub" -> """── EPUB 3.0 Reflowable Container ──

• OEBPS/content.opf (Manifest & Spine)
• OEBPS/nav.xhtml (EPUB 3 Table of Contents)
• OEBPS/chapter1.xhtml (HTML Body)
• OEBPS/toc.ncx (Legacy TOC)
• OEBPS/images/ (Bundled Assets)

""" + MarkdownUtil.toHtmlBody(note.content)
                            "docx" -> """── Microsoft Word OpenXML (.docx) ──

• [Content_Types].xml
• _rels/.rels
• word/document.xml
• word/_rels/document.xml.rels
• word/media/ (Embedded Drawings)

""" + note.content
                            "pdf" -> """── Print-Ready PDF via PrintManager ──

""" + ExportHelper.fullHtml(context, note)
                            else -> note.content
                        }
                    } else {
                        note.content
                    }
                }

                if (previewMode == 0) {
                    RenderedDocumentPreview(note = note, bookId = bookId)
                } else {
                    RawSourcePreview(content = rawSourceContent)
                }
            }

            // ── Bottom Action Bar ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share Option
                OutlinedButton(
                    onClick = {
                        ExportHelper.shareNote(context, note, selectedFormat)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
                }

                // Primary Save to Storage / Print Option
                Button(
                    onClick = {
                        if (selectedFormat == "pdf") {
                            ExportHelper.printToPdf(context, note)
                            onDismiss()
                        } else {
                            val ext = if (selectedFormat == "md" && hasVaultImages) "zip" else currentFormatMeta.ext.removePrefix(".")
                            val filename = "${ExportHelper.sanitize(note.name)}.$ext"
                            createDocumentLauncher.launch(filename)
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (selectedFormat == "pdf") Icons.Default.Print else Icons.Default.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedFormat == "pdf") "Print / Save PDF" else "Save to Device",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderedDocumentPreview(note: Note, bookId: String?) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lines = remember(note.content) { note.content.lines() }
    val imgRegex = remember { Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Document Title
        Text(
            text = note.name.ifBlank { "Untitled Manuscript" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        lines.forEach { line ->
            val trimmed = line.trim()
            val imgMatch = imgRegex.find(trimmed)

            when {
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                imgMatch != null -> {
                    val caption = imgMatch.groupValues[1]
                    val uri = imgMatch.groupValues[2]
                    val imageModel = remember(uri, bookId, note.id) {
                        VaultImageManager.resolveModel(context, uri, bookId, note.id)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.03f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = caption,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (caption.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = trimmed.removePrefix("> ").trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                else -> {
                    Text(
                        text = trimmed,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RawSourcePreview(content: String) {
    val scrollState = rememberScrollState()
    Text(
        text = content,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    )
}
