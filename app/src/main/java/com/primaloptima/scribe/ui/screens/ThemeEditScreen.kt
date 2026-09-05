package com.primaloptima.scribe.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.screens.themeeditor.*
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.AppJson
import com.primaloptima.scribe.util.BitmapBlur
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.util.SAFHelper
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.viewmodel.ThemeViewModel
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditScreen(
    themeId: String,
    vm: ThemeViewModel,
    onBack: UnitCallback
) {
    val context = LocalContext.current
    val themes by vm.themes.collectAsStateWithLifecycle()

    val originalTheme = remember(themes, themeId) {
        themes.firstOrNull { it.id == themeId } ?: DefaultThemes.all.first()
    }

    // ── Centralized Draft State Holder ────────────────────────────────────────
    var draft by remember(originalTheme) {
        mutableStateOf(ThemeEditorDraft.fromAppTheme(originalTheme))
    }

    // Canonical Resolved Theme Colors (authoritative resolution layering overrides onto defaults)
    val resolvedColors = remember(draft) {
        draft.resolveColors()
    }

    // ── Active Category Navigation Tab ────────────────────────────────────────
    var selectedCategory by remember { mutableStateOf(ThemeEditorCategory.COLORS) }

    // ── Modal Dialog & Sheet States ───────────────────────────────────────────
    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    var showAccessibilityDiagnostics by remember { mutableStateOf(false) }
    var showCropScreen by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<String?>(null) }
    var isLuminancePending by remember { mutableStateOf(false) }

    val view = LocalView.current
    val hazeState = LocalHazeState.current
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dialogCaptured by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val anyDialogOpen = showEmojiDialog || activeColorPickerTarget != null || showAccessibilityDiagnostics
        LaunchedEffect(anyDialogOpen) {
            if (anyDialogOpen && !dialogCaptured) {
                dialogCaptured = true
                val raw = BitmapBlur.captureOnly(view)
                dialogOneShotBitmap = withContext(Dispatchers.IO) {
                    raw?.let { BitmapBlur.blurBitmap(it, radius = draft.frostedBlurRadius.toInt().coerceIn(1, 25)) }
                }
            } else if (!anyDialogOpen) {
                dialogCaptured = false
                dialogOneShotBitmap = null
            }
        }
    }

    val scope = rememberCoroutineScope()

    // ── Image Picker Launcher ─────────────────────────────────────────────────
    val bgImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val localUri = SAFHelper.copyBgImageToInternalStorage(context, uri, themeId)
                val stableUri = (localUri ?: uri).toString()
                pendingCropUri = stableUri
                draft = draft.copy(
                    bgOriginalUri = stableUri,
                    bgUri = stableUri,
                    bgMode = if (draft.bgMode == "color") "image" else draft.bgMode
                )
                showCropScreen = true
            }
        }
    }

    // ── Save Theme Action ─────────────────────────────────────────────────────
    val saveAction = {
        val updated = draft.toAppTheme(originalTheme)
        vm.save(updated)
        Toast.makeText(context, "Theme saved", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
            topBar = {
                ScribeTopBar(
                    title = if (originalTheme.builtIn) "View Theme" else "Edit Theme",
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavigationClick = onBack,
                    actions = if (originalTheme.builtIn) emptyList() else listOf(
                        ScribeBarAction(Icons.Default.Check, "Save") {
                            if (!isLuminancePending) {
                                saveAction()
                            }
                        }
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // ── 1. THEME DETAILS (Name & Emoji) ───────────────────────────
                if (!originalTheme.builtIn) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = { showEmojiDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(draft.emoji, fontSize = 22.sp)
                                    }
                                }

                                OutlinedTextField(
                                    value = draft.name,
                                    onValueChange = { draft = draft.copy(name = it) },
                                    label = { Text("Theme Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ── 2. LIVE ANCHORED PREVIEW STAGE ────────────────────────────
                item {
                    ThemePreviewStage(
                        colors = resolvedColors,
                        themeName = draft.name,
                        fontFamily = draft.fontFamily,
                        fontSize = draft.fontSize,
                        lineHeight = draft.lineHeight,
                        textAlignment = draft.textAlignment,
                        sideMargins = draft.sideMargins,
                        bgMode = draft.bgMode,
                        bgUri = draft.bgUri,
                        bgOpacity = draft.bgOpacity,
                        blurIntensity = draft.blurIntensity
                    )
                }

                // ── 3. CATEGORY SELECTOR (Segmented Navigation) ───────────────
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ScribeTheme.colors.borders.subtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemeEditorCategory.entries.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    border = if (isSelected) BorderStroke(1.dp, ScribeTheme.colors.borders.subtle) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = when (cat) {
                                                ThemeEditorCategory.COLORS -> "Colors"
                                                ThemeEditorCategory.TYPOGRAPHY -> "Type"
                                                ThemeEditorCategory.LAYOUT -> "Layout"
                                                ThemeEditorCategory.ATMOSPHERE -> "Atmosphere"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. ACTIVE INSPECTOR PANEL ─────────────────────────────────
                item {
                    when (selectedCategory) {
                        ThemeEditorCategory.COLORS -> {
                            ThemeColorsPanel(
                                draft = draft,
                                resolvedColors = resolvedColors,
                                onSelectTarget = { target ->
                                    activeColorPickerTarget = target
                                },
                                onResetOverride = { target ->
                                    draft = draft.withClearedOverride(target)
                                },
                                onOpenAccessibilityDiagnostics = {
                                    showAccessibilityDiagnostics = true
                                }
                            )
                        }
                        ThemeEditorCategory.TYPOGRAPHY -> {
                            ThemeTypographyPanel(
                                fontFamily = draft.fontFamily,
                                fontSize = draft.fontSize,
                                lineHeight = draft.lineHeight,
                                paragraphSpacing = draft.paragraphSpacing,
                                sideMargins = draft.sideMargins,
                                onFontFamilyChange = { draft = draft.copy(fontFamily = it) },
                                onFontSizeChange = { draft = draft.copy(fontSize = it) },
                                onLineHeightChange = { draft = draft.copy(lineHeight = it) },
                                onParagraphSpacingChange = { draft = draft.copy(paragraphSpacing = it) },
                                onSideMarginsChange = { draft = draft.copy(sideMargins = it) }
                            )
                        }
                        ThemeEditorCategory.LAYOUT -> {
                            ThemeLayoutPanel(
                                textAlignment = draft.textAlignment,
                                themeScope = draft.themeScope,
                                onTextAlignmentChange = { draft = draft.copy(textAlignment = it) },
                                onThemeScopeChange = { draft = draft.copy(themeScope = it) }
                            )
                        }
                        ThemeEditorCategory.ATMOSPHERE -> {
                            ThemeAtmospherePanel(
                                bgMode = draft.bgMode,
                                bgUri = draft.bgUri,
                                bgOriginalUri = draft.bgOriginalUri,
                                bgOpacity = draft.bgOpacity,
                                blurIntensity = draft.blurIntensity,
                                frostedGlassEnabled = draft.frostedGlassEnabled,
                                frostedTintEnabled = draft.frostedTintEnabled,
                                frostedBlurRadius = draft.frostedBlurRadius,
                                onPickImage = { bgImagePicker.launch("image/*") },
                                onCropImage = {
                                    val uriToCrop = draft.bgOriginalUri ?: draft.bgUri
                                    if (uriToCrop != null) {
                                        pendingCropUri = uriToCrop
                                        showCropScreen = true
                                    }
                                },
                                onRemoveImage = {
                                    draft = draft.copy(
                                        bgUri = null,
                                        bgOriginalUri = null,
                                        bgMode = "color",
                                        bgLuminance = -1f,
                                        zonalLuminanceMatrix = emptyList(),
                                        zonalVarianceMatrix = emptyList(),
                                        bgDominantColor = null,
                                        zonalColorsMatrix = emptyList(),
                                        luminanceFieldMatrix = emptyList()
                                    )
                                },
                                onBgModeChange = { draft = draft.copy(bgMode = it) },
                                onBgOpacityChange = { draft = draft.copy(bgOpacity = it) },
                                onBlurIntensityChange = { draft = draft.copy(blurIntensity = it) },
                                onFrostedGlassEnabledChange = { draft = draft.copy(frostedGlassEnabled = it) },
                                onFrostedTintEnabledChange = { draft = draft.copy(frostedTintEnabled = it) },
                                onFrostedBlurRadiusChange = { draft = draft.copy(frostedBlurRadius = it) }
                            )
                        }
                    }
                }

                // ── 5. SAVE & EXPORT ACTION BUTTONS ───────────────────────────
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = saveAction,
                        enabled = !isLuminancePending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLuminancePending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analysing image…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                if (!originalTheme.builtIn) {
                    item {
                        OutlinedButton(
                            onClick = {
                                val currentThemeToExport = draft.toAppTheme(originalTheme)
                                exportThemeJson(context, currentThemeToExport)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Theme JSON")
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
            // Modal Color Picker Bottom Sheet
            activeColorPickerTarget?.let { target ->
                val title = when (target) {
                    ColorPickerTarget.BACKGROUND -> "Background Color"
                    ColorPickerTarget.TEXT -> "Text Color"
                    ColorPickerTarget.ACCENT -> "Primary Accent Color"
                    ColorPickerTarget.HEADING_TEXT -> "Heading Color"
                    ColorPickerTarget.DIALOGUE_TEXT -> "Dialogue Color"
                    ColorPickerTarget.MONOLOGUE_TEXT -> "Monologue Color"
                    ColorPickerTarget.SPECIAL_HIGHLIGHT -> "Emphasis Highlight Color"
                    ColorPickerTarget.ANNOTATION -> "Annotation Color"
                    ColorPickerTarget.SECONDARY -> "Secondary Accent Color"
                    ColorPickerTarget.TERTIARY -> "Tertiary Accent Color"
                    ColorPickerTarget.SURFACE -> "Surface Color"
                }

                val currentHex = when (target) {
                    ColorPickerTarget.BACKGROUND -> draft.bgHex
                    ColorPickerTarget.TEXT -> draft.textHex
                    ColorPickerTarget.ACCENT -> draft.accentHex
                    ColorPickerTarget.HEADING_TEXT -> resolvedColors.headingText
                    ColorPickerTarget.DIALOGUE_TEXT -> resolvedColors.dialogueText
                    ColorPickerTarget.MONOLOGUE_TEXT -> resolvedColors.monologueText
                    ColorPickerTarget.SPECIAL_HIGHLIGHT -> if (resolvedColors.specialHighlight.isNotBlank()) resolvedColors.specialHighlight else resolvedColors.accent
                    ColorPickerTarget.ANNOTATION -> if (resolvedColors.annotation.isNotBlank()) resolvedColors.annotation else resolvedColors.accent
                    ColorPickerTarget.SECONDARY -> resolvedColors.secondary
                    ColorPickerTarget.TERTIARY -> resolvedColors.tertiary
                    ColorPickerTarget.SURFACE -> resolvedColors.surface
                }

                ColorPickerBottomSheet(
                    title = title,
                    initialHex = currentHex,
                    onDismiss = { activeColorPickerTarget = null },
                    onColorSelected = { newHex ->
                        when (target) {
                            ColorPickerTarget.BACKGROUND -> draft = draft.copy(bgHex = newHex)
                            ColorPickerTarget.TEXT -> draft = draft.copy(textHex = newHex)
                            ColorPickerTarget.ACCENT -> draft = draft.copy(accentHex = newHex)
                            else -> draft = draft.withOverride(target, newHex)
                        }
                    }
                )
            }

            // Accessibility Diagnostics Full Dialog
            if (showAccessibilityDiagnostics) {
                AccessibilityDiagnosticsDialog(
                    colors = resolvedColors,
                    onDismiss = { showAccessibilityDiagnostics = false }
                )
            }

            // Emoji Picker Dialog
            if (showEmojiDialog) {
                val emojis = listOf(
                    "🖊️", "📖", "🌙", "⭐", "🌿", "🔥", "🌊", "🌸", "🏔️", "🌌",
                    "📜", "✨", "🎭", "🌅", "🍂", "❄️", "🪶", "🕯️", "🌺", "☕"
                )
                FrostedDialog(
                    onDismissRequest = { showEmojiDialog = false },
                    title = { Text("Theme Emoji Badge") },
                    text = {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(emojis) { em ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            draft = draft.copy(emoji = em)
                                            showEmojiDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(em, fontSize = 22.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showEmojiDialog = false }) { Text("Close") }
                    }
                )
            }
        }

        // ── Fullscreen Image Crop Screen Overlay ──────────────────────────────
        if (showCropScreen && pendingCropUri != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                ImageCropScreen(
                    imageUri = pendingCropUri!!,
                    themeId = themeId,
                    onConfirm = { croppedUri ->
                        draft = draft.copy(
                            bgOriginalUri = pendingCropUri,
                            bgUri = croppedUri,
                            bgMode = if (draft.bgMode == "color") "image" else draft.bgMode
                        )
                        showCropScreen = false
                        pendingCropUri = null

                        isLuminancePending = true
                        scope.launch {
                            val analysis = computeBgAnalysis(context, croppedUri)
                            draft = draft.copy(
                                bgLuminance = analysis.avgLightness,
                                zonalLuminanceMatrix = analysis.zonalLuminance,
                                zonalVarianceMatrix = analysis.zonalVariance,
                                bgDominantColor = analysis.dominantColor,
                                zonalColorsMatrix = analysis.zonalColors,
                                luminanceFieldMatrix = analysis.bgLuminanceField
                            )
                            isLuminancePending = false
                        }
                    },
                    onCancel = {
                        showCropScreen = false
                        pendingCropUri = null
                    }
                )
            }
        }
    }
}

private typealias UnitCallback = () -> Unit

private fun exportThemeJson(context: Context, theme: AppTheme) {
    try {
        val json = AppJson.encodeToString(theme)
        val fileName = "${theme.name.lowercase().replace(Regex("[^a-z0-9]"), "_")}_theme.json"
        val dir = File(context.cacheDir, "exported_themes").also { it.mkdirs() }
        val file = File(dir, fileName).also { it.writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Theme: ${theme.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
