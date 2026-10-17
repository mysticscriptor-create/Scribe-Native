package com.primaloptima.scribe.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.components.export.DocxLayoutViewer
import com.primaloptima.scribe.ui.components.export.HtmlPreviewViewer
import com.primaloptima.scribe.ui.components.export.PdfPreviewViewer
import com.primaloptima.scribe.ui.components.export.SourcePreviewViewer
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.export.ExportCacheManager
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.HtmlPdfExporter
import com.primaloptima.scribe.util.export.ImageQuality
import com.primaloptima.scribe.util.export.MarginPreset
import com.primaloptima.scribe.util.export.PaperSize
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class FormatMeta(
    val format: ExportFormat,
    val icon: ImageVector
)

private val ALL_FORMATS = listOf(
    FormatMeta(ExportFormat.PDF, Icons.Default.PictureAsPdf),
    FormatMeta(ExportFormat.EPUB, Icons.Default.MenuBook),
    FormatMeta(ExportFormat.DOCX, Icons.Default.Description),
    FormatMeta(ExportFormat.HTML, Icons.Default.Language),
    FormatMeta(ExportFormat.MD, Icons.Default.Code),
    FormatMeta(ExportFormat.TXT, Icons.Default.Article)
)

/**
 * Modernized Export Manuscript Sheet with native PDF rendering, responsive HTML preview,
 * authentic Word layout simulator, source inspector, author style overrides, and SAF save flow.
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
    val scope = rememberCoroutineScope()

    var selectedFormat by remember {
        mutableStateOf(ExportFormat.fromId(initialFormat))
    }

    var config by remember {
        mutableStateOf(ExportConfig(format = selectedFormat))
    }

    // Keep config format synced with selectedFormat
    LaunchedEffect(selectedFormat) {
        if (config.format != selectedFormat) {
            config = config.copy(format = selectedFormat)
        }
    }

    // Active Tab: 0 = True Document Preview, 1 = Style & Layout Overrides
    var activeTab by remember { mutableIntStateOf(0) }

    // Manuscript Statistics
    val wordCount = remember(note.content) { MarkdownUtil.countWords(note.content) }
    val charCount = remember(note.content) { MarkdownUtil.countChars(note.content) }
    val readingTime = remember(note.content) { MarkdownUtil.readingTimeMinutes(note.content) }

    val hasVaultImages = remember(note.content) { VaultImageManager.hasImages(note.content) }
    val effectiveMimeType = if (selectedFormat == ExportFormat.MD && hasVaultImages) {
        "application/zip"
    } else {
        selectedFormat.mimeType
    }

    // PDF background preview state
    var pdfPreviewFile by remember { mutableStateOf<File?>(null) }
    var isPdfLoading by remember { mutableStateOf(false) }
    var pdfErrorMessage by remember { mutableStateOf<String?>(null) }

    // HTML / EPUB preview state
    val htmlPreviewContent = remember(note.content, note.name, config, selectedFormat) {
        if (selectedFormat == ExportFormat.HTML || selectedFormat == ExportFormat.EPUB) {
            HtmlPdfExporter.buildHtmlDocument(context, note, config, isForPrintPdf = false)
        } else {
            ""
        }
    }

    // Render PDF preview file on background thread whenever config changes and format == PDF
    LaunchedEffect(selectedFormat, config, note.content, note.name) {
        if (selectedFormat == ExportFormat.PDF) {
            isPdfLoading = true
            pdfErrorMessage = null
            val tempFile = ExportCacheManager.createPreviewFile(context, ".pdf")
            HtmlPdfExporter.renderPdfToFile(context, note, config, tempFile) { success, err ->
                isPdfLoading = false
                if (success) {
                    pdfPreviewFile = tempFile
                } else {
                    pdfErrorMessage = err?.localizedMessage ?: "Failed to generate PDF preview"
                }
            }
        }
    }

    // Clean preview cache on dismissal
    DisposableEffect(Unit) {
        onDispose {
            ExportCacheManager.clearPreviewCache(context)
        }
    }

    // SAF CreateDocument Launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(effectiveMimeType)
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { os ->
                        if (selectedFormat == ExportFormat.PDF) {
                            val tempPdf = ExportCacheManager.createPreviewFile(context, ".pdf")
                            HtmlPdfExporter.renderPdfToFile(context, note, config, tempPdf) { success, err ->
                                if (success && tempPdf.exists()) {
                                    scope.launch(Dispatchers.IO) {
                                        context.contentResolver.openOutputStream(destinationUri)?.use { stream ->
                                            stream.write(tempPdf.readBytes())
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Saved ${selectedFormat.label} manuscript successfully", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                } else {
                                    scope.launch(Dispatchers.Main) {
                                        Toast.makeText(context, "Export error: ${err?.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            val bytes = ExportHelper.exportBytes(context, note, selectedFormat.id, config)
                            os.write(bytes)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved ${selectedFormat.label} manuscript successfully", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val currentFormatMeta = ALL_FORMATS.firstOrNull { it.format == selectedFormat } ?: ALL_FORMATS.first()

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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${note.name.ifBlank { "Untitled" }} · $wordCount words · $readingTime min read",
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

            // ── Format Selector Carousel ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ALL_FORMATS.forEach { meta ->
                    val isSelected = meta.format == selectedFormat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = meta.format },
                        label = {
                            Text(
                                meta.format.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = meta.icon,
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
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
                        text = selectedFormat.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Dual Mode Tabs: [ Document Preview ] vs [ Overrides ] ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text("Document Preview", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text("Style & Layout Overrides", fontSize = 12.sp) }
                    )
                }
            }

            // ── Main Viewport Content ──────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            ) {
                if (activeTab == 0) {
                    // Document Preview Viewport
                    when (selectedFormat) {
                        ExportFormat.PDF -> {
                            PdfPreviewViewer(
                                pdfFile = pdfPreviewFile,
                                isLoading = isPdfLoading,
                                errorMessage = pdfErrorMessage
                            )
                        }

                        ExportFormat.EPUB, ExportFormat.HTML -> {
                            HtmlPreviewViewer(htmlContent = htmlPreviewContent)
                        }

                        ExportFormat.DOCX -> {
                            DocxLayoutViewer(note = note, config = config)
                        }

                        ExportFormat.MD -> {
                            SourcePreviewViewer(sourceText = note.content)
                        }

                        ExportFormat.TXT -> {
                            SourcePreviewViewer(sourceText = MarkdownUtil.toPlainText(note.content))
                        }
                    }
                } else {
                    // Style & Layout Overrides Viewport
                    StyleOverridesPanel(
                        config = config,
                        selectedFormat = selectedFormat,
                        onConfigChanged = { updated -> config = updated }
                    )
                }
            }

            // ── Bottom Action Bar (Share & Save) ───────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 6.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button
                    OutlinedButton(
                        onClick = {
                            ExportHelper.shareNote(context, note, selectedFormat.id, config)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }

                    // Primary Save to Storage Button
                    Button(
                        onClick = {
                            val safeName = ExportHelper.sanitize(note.name)
                            val isMdZip = selectedFormat == ExportFormat.MD && hasVaultImages
                            val finalExt = if (isMdZip) ".zip" else selectedFormat.extension
                            val defaultName = "$safeName$finalExt"
                            createDocumentLauncher.launch(defaultName)
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save ${selectedFormat.label}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleOverridesPanel(
    config: ExportConfig,
    selectedFormat: ExportFormat,
    onConfigChanged: (ExportConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Paper Size (PDF & DOCX)
        if (selectedFormat == ExportFormat.PDF || selectedFormat == ExportFormat.DOCX) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PAPER SIZE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperSize.entries.forEach { size ->
                            FilterChip(
                                selected = config.paperSize == size,
                                onClick = { onConfigChanged(config.copy(paperSize = size)) },
                                label = { Text(size.label) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Margins (PDF & DOCX)
        if (selectedFormat == ExportFormat.PDF || selectedFormat == ExportFormat.DOCX) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PAGE MARGINS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarginPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.marginPreset == preset,
                                onClick = { onConfigChanged(config.copy(marginPreset = preset)) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Typography Preset
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TYPOGRAPHY PRESET",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FontChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = config.fontChoice == choice,
                            onClick = { onConfigChanged(config.copy(fontChoice = choice)) },
                            label = { Text(choice.label) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Section: Document Options
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "PUBLICATION OPTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Cover Title Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include Cover Title", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Display manuscript title as primary header", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.includeCoverTitle,
                        onCheckedChange = { onConfigChanged(config.copy(includeCoverTitle = it)) }
                    )
                }

                // Table of Contents Switch (for EPUB / PDF)
                if (selectedFormat == ExportFormat.EPUB || selectedFormat == ExportFormat.PDF) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Table of Contents", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Generate navigation TOC from chapter headings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.includeTableOfContents,
                            onCheckedChange = { onConfigChanged(config.copy(includeTableOfContents = it)) }
                        )
                    }
                }
            }
        }
    }
}
