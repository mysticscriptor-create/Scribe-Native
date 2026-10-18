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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import kotlin.math.roundToInt
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
import com.primaloptima.scribe.ui.components.export.EpubPreviewViewer
import com.primaloptima.scribe.ui.components.export.HtmlPreviewViewer
import com.primaloptima.scribe.ui.components.export.PdfPreviewViewer
import com.primaloptima.scribe.ui.components.export.SourcePreviewViewer
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.ScribeDataStore
import com.primaloptima.scribe.util.export.ExportArtifactRepository
import com.primaloptima.scribe.util.export.ExportCacheManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
    val dataStore = remember { ScribeDataStore(context) }
    val persistedConfigState by dataStore.exportConfigFlow.collectAsStateWithLifecycle(initialValue = null)

    var hasLoadedPersistedConfig by remember { mutableStateOf(false) }

    var selectedFormat by remember {
        mutableStateOf(ExportFormat.fromId(initialFormat))
    }

    var config by remember {
        mutableStateOf(ExportConfig(format = selectedFormat))
    }

    // Restore persisted settings from ScribeDataStore on initial load
    LaunchedEffect(persistedConfigState) {
        val persisted = persistedConfigState
        if (persisted != null && !hasLoadedPersistedConfig) {
            hasLoadedPersistedConfig = true
            val effectiveFmt = if (initialFormat != "pdf") ExportFormat.fromId(initialFormat) else persisted.format
            selectedFormat = effectiveFmt
            config = persisted.copy(format = effectiveFmt)
        }
    }

    // Keep config format synced with selectedFormat
    LaunchedEffect(selectedFormat) {
        if (config.format != selectedFormat) {
            config = config.copy(format = selectedFormat)
        }
    }

    // Debounce persistence of config changes (300ms)
    LaunchedEffect(config) {
        if (hasLoadedPersistedConfig) {
            delay(300L)
            dataStore.saveExportConfig(config)
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

    // Unified background artifact preview state across all 6 formats
    var currentArtifactFile by remember { mutableStateOf<File?>(null) }
    var isArtifactLoading by remember { mutableStateOf(false) }
    var artifactErrorMessage by remember { mutableStateOf<String?>(null) }

    // Retain and release artifact file reference to prevent background eviction while viewing
    DisposableEffect(currentArtifactFile) {
        val file = currentArtifactFile
        if (file != null) {
            ExportArtifactRepository.retainArtifact(file)
        }
        onDispose {
            if (file != null) {
                ExportArtifactRepository.releaseArtifact(file)
            }
        }
    }

    // Load artifact for active format via ExportArtifactRepository with 250ms debounce
    LaunchedEffect(selectedFormat, config, note.content, note.name) {
        isArtifactLoading = true
        artifactErrorMessage = null
        currentArtifactFile = null
        delay(250L) // 250ms debounce for rapid slider/config changes
        val result = ExportArtifactRepository.getOrCreateArtifact(context, note, selectedFormat, config)
        isArtifactLoading = false
        result.fold(
            onSuccess = { artifact ->
                currentArtifactFile = artifact.file
            },
            onFailure = { err ->
                artifactErrorMessage = err.localizedMessage ?: "Failed to generate ${selectedFormat.label} artifact"
            }
        )
    }

    // SAF CreateDocument Launcher consuming unified artifact from ExportArtifactRepository
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(effectiveMimeType)
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val fileToStream = currentArtifactFile?.takeIf { it.exists() && it.length() > 0L }
                        ?: ExportArtifactRepository.getOrCreateArtifact(
                            context, note, selectedFormat, config
                        ).getOrThrow().file

                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        fileToStream.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved ${selectedFormat.label} manuscript successfully", Toast.LENGTH_SHORT).show()
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
                    val isMdZip = meta.format == ExportFormat.MD && hasVaultImages
                    val chipLabel = if (isMdZip) "Markdown Bundle (.zip)" else meta.format.label
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = meta.format },
                        label = {
                            Text(
                                chipLabel,
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
                    val formatDesc = if (selectedFormat == ExportFormat.MD && hasVaultImages) {
                        "Markdown bundle packaged as ZIP with manuscript.md (relative image links) and images/ directory"
                    } else {
                        selectedFormat.description
                    }
                    Text(
                        text = formatDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Dual Mode Tabs: [ Document Preview ] vs [ Publication Settings ] ──
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
                        text = { Text("Publication Settings", fontSize = 12.sp) }
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
                    // Document Preview Viewport - True Artifact Previews across all 6 formats
                    when (selectedFormat) {
                        ExportFormat.PDF -> {
                            PdfPreviewViewer(
                                pdfFile = currentArtifactFile,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }

                        ExportFormat.EPUB -> {
                            EpubPreviewViewer(
                                epubFile = currentArtifactFile,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }

                        ExportFormat.HTML -> {
                            HtmlPreviewViewer(
                                htmlFile = currentArtifactFile,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }

                        ExportFormat.DOCX -> {
                            DocxLayoutViewer(
                                docxFile = currentArtifactFile,
                                note = note,
                                config = config,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }

                        ExportFormat.MD -> {
                            SourcePreviewViewer(
                                artifactFile = currentArtifactFile,
                                sourceText = note.content,
                                format = ExportFormat.MD,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }

                        ExportFormat.TXT -> {
                            SourcePreviewViewer(
                                artifactFile = currentArtifactFile,
                                sourceText = ExportHelper.buildPlainText(note, config),
                                format = ExportFormat.TXT,
                                isLoading = isArtifactLoading,
                                errorMessage = artifactErrorMessage
                            )
                        }
                    }
                } else {
                    // Publication Settings Viewport
                    StyleOverridesPanel(
                        config = config,
                        selectedFormat = selectedFormat,
                        onConfigChanged = { updated -> config = updated },
                        onResetDefaults = {
                            val reset = ExportConfig(format = selectedFormat)
                            config = reset
                            scope.launch { dataStore.resetExportConfig() }
                        }
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
                        enabled = !isArtifactLoading,
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
                        enabled = !isArtifactLoading,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isArtifactLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preparing ${selectedFormat.label}…")
                        } else {
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
}

@Composable
private fun StyleOverridesPanel(
    config: ExportConfig,
    selectedFormat: ExportFormat,
    onConfigChanged: (ExportConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Paper Size (PDF & DOCX only)
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

        // Section: Page Margins (PDF & DOCX only)
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

        // Section: Typography Preset (PDF, DOCX, EPUB, HTML)
        if (selectedFormat != ExportFormat.MD && selectedFormat != ExportFormat.TXT) {
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

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Font Size Slider (8..24 pt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${config.fontSizePt} pt",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.fontSizePt.toFloat(),
                        onValueChange = { onConfigChanged(config.copy(fontSizePt = it.roundToInt())) },
                        valueRange = 8f..24f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Line Spacing Slider (1.0..2.4x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Line Spacing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val formattedMultiplier = String.format(java.util.Locale.US, "%.1fx", config.lineSpacingMultiplier)
                        Text(
                            text = formattedMultiplier,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.lineSpacingMultiplier,
                        onValueChange = { onConfigChanged(config.copy(lineSpacingMultiplier = (Math.round(it * 10f) / 10f))) },
                        valueRange = 1.0f..2.4f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section: Image Resolution & Quality (PDF, EPUB, DOCX, HTML, MD)
        if (selectedFormat != ExportFormat.TXT) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "IMAGE QUALITY",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = config.imageQuality == quality,
                                onClick = { onConfigChanged(config.copy(imageQuality = quality)) },
                                label = { Text(quality.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section: Publication Options
        if (selectedFormat != ExportFormat.MD) {
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
                    // Author / Pen Name Input (EPUB, DOCX, PDF, HTML)
                    if (selectedFormat != ExportFormat.TXT) {
                        OutlinedTextField(
                            value = config.authorName,
                            onValueChange = { onConfigChanged(config.copy(authorName = it)) },
                            label = { Text("Author / Pen Name") },
                            placeholder = { Text("e.g. Scribe Author") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Cover Title Switch (EPUB, DOCX, PDF, HTML, TXT)
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

                    // Table of Contents Switch (for EPUB & PDF only)
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

        // Section: Reset to Defaults Action
        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Publication Settings to Defaults")
        }
    }
}
