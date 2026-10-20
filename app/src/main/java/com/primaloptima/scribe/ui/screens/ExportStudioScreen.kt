package com.primaloptima.scribe.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.ui.components.export.DocxLayoutViewer
import com.primaloptima.scribe.ui.components.export.EpubPreviewViewer
import com.primaloptima.scribe.ui.components.export.HtmlPreviewViewer
import com.primaloptima.scribe.ui.components.export.PdfPreviewViewer
import com.primaloptima.scribe.ui.components.export.SourcePreviewViewer
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.util.MarkdownUtil
import com.primaloptima.scribe.util.ScribeDataStore
import com.primaloptima.scribe.util.export.DropCapStyle
import com.primaloptima.scribe.util.export.ExportArtifactRepository
import com.primaloptima.scribe.util.export.ExportConfig
import com.primaloptima.scribe.util.export.ExportFormat
import com.primaloptima.scribe.util.export.FontChoice
import com.primaloptima.scribe.util.export.ImageQuality
import com.primaloptima.scribe.util.export.ManuscriptTitleHeader
import com.primaloptima.scribe.util.export.MarginPreset
import com.primaloptima.scribe.util.export.OrnamentCatalog
import com.primaloptima.scribe.util.export.PaperSize
import com.primaloptima.scribe.util.image.VaultImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private data class FormatInfo(
    val format: ExportFormat,
    val title: String,
    val extension: String,
    val icon: ImageVector,
    val summary: String
)

private val EXPORT_FORMATS = listOf(
    FormatInfo(ExportFormat.PDF, "PDF Document", ".pdf", Icons.Default.PictureAsPdf, "Print-ready paginated PDF with custom typography"),
    FormatInfo(ExportFormat.EPUB, "EPUB eBook", ".epub", Icons.Default.MenuBook, "Reflowable e-reader book with cover and chapter TOC"),
    FormatInfo(ExportFormat.DOCX, "Word Document", ".docx", Icons.Default.Description, "Standard Microsoft Word layout with styled headings"),
    FormatInfo(ExportFormat.HTML, "HTML Web Page", ".html", Icons.Default.Language, "Responsive single-file publication with CSS styling"),
    FormatInfo(ExportFormat.MD, "Markdown", ".md", Icons.Default.Code, "Clean GitHub-flavored Markdown text file"),
    FormatInfo(ExportFormat.TXT, "Plain Text", ".txt", Icons.Default.Article, "Clean unformatted UTF-8 prose without styling")
)

/**
 * Dedicated, full-screen publishing workspace ("Export Studio") in Jetpack Compose.
 *
 * Provides a professional editorial workspace with:
 *  - Compact editorial header showing manuscript title, word count, and reading time.
 *  - Theme-aware outlined format dropdown replacing the horizontal chips.
 *  - Persistent two-tab toggle (Preview / Settings) on phones, and responsive dual-pane split on wide screens.
 *  - Authentic document viewers across all 6 formats with debounced artifact generation.
 *  - Storage Access Framework (SAF) save flow and system share actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportStudioScreen(
    note: Note,
    bookId: String?,
    initialFormat: String = "pdf",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 640

    val dataStore = remember { ScribeDataStore(context) }
    val persistedConfigState by dataStore.exportConfigFlow.collectAsStateWithLifecycle(initialValue = null)
    var hasLoadedPersistedConfig by remember { mutableStateOf(false) }

    var selectedFormat by rememberSaveable {
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

    // Keep config format in sync with selectedFormat
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

    // Persistent Tabs: 0 = True Document Preview, 1 = Publication Settings
    var activeTab by rememberSaveable { mutableIntStateOf(0) }

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

    // Overflow & Dialog State
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }

    // Unified background artifact preview state across all 6 formats
    var currentArtifactFile by remember { mutableStateOf<File?>(null) }
    var isArtifactLoading by remember { mutableStateOf(false) }
    var artifactErrorMessage by remember { mutableStateOf<String?>(null) }

    // Retain and release artifact file reference to prevent background cache eviction
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

    // Debounced artifact generation (250ms debounce)
    LaunchedEffect(selectedFormat, config, note.content, note.name) {
        isArtifactLoading = true
        artifactErrorMessage = null
        currentArtifactFile = null
        delay(250L)
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

    // SAF CreateDocument Launcher
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
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val manuscriptTitle = note.name.ifBlank { "Untitled Manuscript" }

    // Full screen scaffold with edge-to-edge system bar detection
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 1. Compact Editorial Header ──────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to Editor",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Export Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "$manuscriptTitle · $wordCount words · ~$readingTime min read",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Header Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reset settings to defaults") },
                                onClick = {
                                    showOverflowMenu = false
                                    val reset = ExportConfig(format = selectedFormat)
                                    config = reset
                                    scope.launch { dataStore.resetExportConfig() }
                                    Toast.makeText(context, "Export settings reset to defaults", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manuscript summary") },
                                onClick = {
                                    showOverflowMenu = false
                                    showMetadataDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ── 2. Persistent Top Control Strip (Format Selector & Tabs) ─
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isWideScreen) {
                        // Wide Screen: Format selector sits neatly across the top bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ExportFormatDropdown(
                                selectedFormat = selectedFormat,
                                hasVaultImages = hasVaultImages,
                                onSelectFormat = { selectedFormat = it },
                                modifier = Modifier.width(320.dp)
                            )
                            Text(
                                text = "Split Publishing Workspace",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Phone / Narrow Screen: Format dropdown + 2-Tab Switcher
                        ExportFormatDropdown(
                            selectedFormat = selectedFormat,
                            hasVaultImages = hasVaultImages,
                            onSelectFormat = { selectedFormat = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TabRow(
                            selectedTabIndex = activeTab,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                if (activeTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Preview",
                                        fontWeight = if (activeTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Settings",
                                        fontWeight = if (activeTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ── 3. Main Workspace Viewport ────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (isWideScreen) {
                    // Responsive Dual-Pane Layout on Tablets & Wide Screens
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Pane: Document Preview
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        ) {
                            ExportPreviewContent(
                                selectedFormat = selectedFormat,
                                currentArtifactFile = currentArtifactFile,
                                isArtifactLoading = isArtifactLoading,
                                artifactErrorMessage = artifactErrorMessage,
                                note = note,
                                config = config
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Right Pane: Style & Publication Overrides
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        ) {
                            ExportSettingsPanel(
                                note = note,
                                config = config,
                                selectedFormat = selectedFormat,
                                onConfigChanged = { config = it },
                                onResetDefaults = {
                                    val reset = ExportConfig(format = selectedFormat)
                                    config = reset
                                    scope.launch { dataStore.resetExportConfig() }
                                }
                            )
                        }
                    }
                } else {
                    // Single Column Viewport: Smooth crossfade between Preview and Settings
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    ) {
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "ExportStudioTabTransition"
                        ) { tab ->
                            if (tab == 0) {
                                ExportPreviewContent(
                                    selectedFormat = selectedFormat,
                                    currentArtifactFile = currentArtifactFile,
                                    isArtifactLoading = isArtifactLoading,
                                    artifactErrorMessage = artifactErrorMessage,
                                    note = note,
                                    config = config
                                )
                            } else {
                                ExportSettingsPanel(
                                    note = note,
                                    config = config,
                                    selectedFormat = selectedFormat,
                                    onConfigChanged = { config = it },
                                    onResetDefaults = {
                                        val reset = ExportConfig(format = selectedFormat)
                                        config = reset
                                        scope.launch { dataStore.resetExportConfig() }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── 4. Fixed Bottom Action Bar (Share & Save) ─────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 4.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary Action: Share
                    OutlinedButton(
                        onClick = {
                            ExportHelper.shareNote(context, note, selectedFormat.id, config)
                        },
                        enabled = !isArtifactLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }

                    // Primary Action: Save to Device
                    val isMdZip = selectedFormat == ExportFormat.MD && hasVaultImages
                    val primaryButtonLabel = if (isMdZip) "Save Markdown (.zip)" else "Save ${selectedFormat.label}"

                    Button(
                        onClick = {
                            val safeName = ExportHelper.sanitize(note.name)
                            val finalExt = if (isMdZip) ".zip" else selectedFormat.extension
                            val defaultName = "$safeName$finalExt"
                            createDocumentLauncher.launch(defaultName)
                        },
                        enabled = !isArtifactLoading,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
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
                            Text("Preparing…")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = primaryButtonLabel,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 5. Manuscript Metadata Summary Dialog ────────────────────────
    if (showMetadataDialog) {
        val header = ManuscriptTitleHeader.from(note, config.ornamentId)
        val tocLabel = header.toTocLabel(fallback = note.name.ifBlank { "Untitled Manuscript" })

        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            title = {
                Text(
                    text = "Manuscript Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetadataSummaryRow("Title", note.name.ifBlank { "Untitled" })
                    if (header.hasPrimary) {
                        MetadataSummaryRow("Kicker (Title 1)", header.primaryTitle)
                    }
                    if (header.hasSecondary) {
                        MetadataSummaryRow("Main Title (Title 2)", header.secondaryTitle)
                    }
                    MetadataSummaryRow("TOC Combined Label", tocLabel)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    MetadataSummaryRow("Word Count", "$wordCount words")
                    MetadataSummaryRow("Character Count", "$charCount characters")
                    MetadataSummaryRow("Estimated Reading Time", "~$readingTime min")
                    MetadataSummaryRow("Embedded Images", if (hasVaultImages) "Yes (packaged as .zip for Markdown)" else "None")
                    MetadataSummaryRow("Ornament Divider", OrnamentCatalog.getById(config.ornamentId).displayName)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMetadataDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MetadataSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Theme-aware outlined format dropdown replacing horizontal chips.
 */
@Composable
private fun ExportFormatDropdown(
    selectedFormat: ExportFormat,
    hasVaultImages: Boolean,
    onSelectFormat: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val currentMeta = EXPORT_FORMATS.firstOrNull { it.format == selectedFormat } ?: EXPORT_FORMATS.first()
    val isMdZip = selectedFormat == ExportFormat.MD && hasVaultImages
    val displayExtension = if (isMdZip) ".zip" else currentMeta.extension
    val displayLabel = if (isMdZip) "Markdown Bundle (.zip)" else currentMeta.title

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentMeta.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = displayExtension,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isMdZip) "Bundled ZIP with relative image assets" else currentMeta.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand format menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            EXPORT_FORMATS.forEach { item ->
                val isSelected = item.format == selectedFormat
                val itemIsMdZip = item.format == ExportFormat.MD && hasVaultImages
                val itemExt = if (itemIsMdZip) ".zip" else item.extension
                val itemTitle = if (itemIsMdZip) "Markdown Bundle (.zip)" else item.title

                DropdownMenuItem(
                    modifier = Modifier.background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                    ),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    },
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = itemTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = itemExt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (itemIsMdZip) "Bundled ZIP with relative image assets" else item.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        onSelectFormat(item.format)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * True document preview content rendering the active format viewer.
 */
@Composable
private fun ExportPreviewContent(
    selectedFormat: ExportFormat,
    currentArtifactFile: File?,
    isArtifactLoading: Boolean,
    artifactErrorMessage: String?,
    note: Note,
    config: ExportConfig
) {
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
}

/**
 * Comprehensive live style and publication settings panel.
 */
@Composable
private fun ExportSettingsPanel(
    note: Note,
    config: ExportConfig,
    selectedFormat: ExportFormat,
    onConfigChanged: (ExportConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Title Hierarchy & TOC
        val header = ManuscriptTitleHeader.from(note, config.ornamentId)
        val tocLabel = header.toTocLabel(fallback = note.name.ifBlank { "Untitled Manuscript" })

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TITLE HIERARCHY & TOC ENTRY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (header.hasPrimary) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Kicker (Title 1):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(header.primaryTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (header.hasSecondary) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Main Title (Title 2):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(header.secondaryTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column {
                    Text("Combined Table of Contents Label:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• $tocLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Section 2: Manuscript Ornament Divider
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MANUSCRIPT ORNAMENT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Decorative divider rendered between the title header and prose",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val ornamentOptions = OrnamentCatalog.entries.map { entry ->
                    val label = if (entry.id == "none") {
                        "None (No Divider)"
                    } else {
                        "${entry.displayName} (${entry.textDivider})"
                    }
                    entry.id to label
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ornamentOptions.forEach { (ornamentId, label) ->
                        FilterChip(
                            selected = config.ornamentId == ornamentId,
                            onClick = { onConfigChanged(config.copy(ornamentId = ornamentId)) },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Section 3: Drop Caps (Initial Letter) - PDF, EPUB, DOCX, HTML
        if (selectedFormat == ExportFormat.PDF || selectedFormat == ExportFormat.EPUB ||
            selectedFormat == ExportFormat.DOCX || selectedFormat == ExportFormat.HTML) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DROP CAPS (INITIAL LETTER)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Typeset opening letters large and dropped into paragraphs (automatically bypassed on RTL scripts)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DropCapStyle.entries.forEach { style ->
                            FilterChip(
                                selected = config.dropCapStyle == style,
                                onClick = { onConfigChanged(config.copy(dropCapStyle = style)) },
                                label = { Text(style.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    if (config.dropCapStyle != DropCapStyle.DISABLED) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Drop Cap Depth", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(2, 3).forEach { lines ->
                                    FilterChip(
                                        selected = config.dropCapLines == lines,
                                        onClick = { onConfigChanged(config.copy(dropCapLines = lines)) },
                                        label = { Text("$lines lines") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Paper Size (PDF & DOCX only)
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

        // Section 5: Page Margins (PDF & DOCX only)
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

        // Section 6: Typography Preset (PDF, DOCX, EPUB, HTML)
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

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Base Font Size",
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

        // Section 7: Image Resolution & Quality (PDF, EPUB, DOCX, HTML, MD)
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

        // Section 8: Publication Options
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
                    // Author / Pen Name Input
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
                    // First-Line Indent Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("First-Line Paragraph Indent", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Standard publication indent on prose paragraphs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.firstLineIndent,
                            onCheckedChange = { onConfigChanged(config.copy(firstLineIndent = it)) }
                        )
                    }
                    // Table of Contents Switch
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

        // Section 9: Reset to Defaults Action Button
        OutlinedButton(
            onClick = onResetDefaults,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
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
