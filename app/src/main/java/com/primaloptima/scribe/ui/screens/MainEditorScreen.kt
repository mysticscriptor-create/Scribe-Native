package com.primaloptima.scribe.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeEditorTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.EditorLeftDrawer
import com.primaloptima.scribe.ui.components.EditorRightPanel
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.frostedContainerColor
import com.primaloptima.scribe.ui.theme.LocalAppTheme
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.engine.ProseDiagnosticProvider
import com.primaloptima.scribe.engine.ProseInlayHintProvider
import com.primaloptima.scribe.util.BitmapBlur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.FloatingWindowOverlay
import com.primaloptima.scribe.util.ExportHelper
import com.primaloptima.scribe.viewmodel.BookViewModel
import com.primaloptima.scribe.viewmodel.EditorViewModel
import com.primaloptima.scribe.viewmodel.NoteListViewModel
import com.primaloptima.scribe.viewmodel.ShortcutsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.window.core.layout.WindowWidthSizeClass
import dev.chrisbanes.haze.HazeState

// ── Sora Editor imports ───────────────────────────────────────────────────────
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import com.primaloptima.scribe.util.ScribeProseLanguage
import com.primaloptima.scribe.util.ThemeManager


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun MainEditorScreen(
    editorVm: EditorViewModel,
    bookVm: BookViewModel,
    noteListVm: NoteListViewModel,
    shortcutsVm: ShortcutsViewModel,
    initialNoteId: String?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSheets: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ── Adaptive Window Size Class ────────────────────────────────────────────
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompact = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    // ── Frosted-glass blur bitmaps (pre-API-31 fallback) ─────────────────────
    val view         = LocalView.current
    val blurRadiusPx = com.primaloptima.scribe.ui.theme.LocalFrostedBlurRadius.current
        .toInt().coerceIn(1, 25)
    var dialogOneShotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val barBlurBitmap = LocalBarBlurBitmap.current

    val editorTheme  = LocalAppTheme.current
    val editorBgUri  = editorTheme?.backgroundImageUri

    // ── ViewModel state ───────────────────────────────────────────────────────
    val activeNote     by editorVm.activeNote.collectAsStateWithLifecycle()
    val wordCount      by editorVm.wordCount.collectAsStateWithLifecycle()
    val charCount      by editorVm.charCount.collectAsStateWithLifecycle()
    val outline        by editorVm.outline.collectAsStateWithLifecycle()
    val proseAnalysis  by editorVm.proseAnalysis.collectAsStateWithLifecycle()
    val zenMode        by editorVm.zenMode.collectAsStateWithLifecycle()
    val activeTheme    by editorVm.theme.collectAsStateWithLifecycle()
    val goalProgress   by editorVm.goalProgress.collectAsStateWithLifecycle()

    val bgUri        = activeTheme?.backgroundImageUri
    val bgMode       = activeTheme?.bgMode ?: "color"
    val themeScope   = activeTheme?.themeScope ?: "editor_only"
    val bgOpacity    = activeTheme?.backgroundImageOpacity ?: 0.35f
    val blurIntensity = activeTheme?.blurIntensity ?: 0f
    val hasBgImage   = !bgUri.isNullOrEmpty() && bgMode != "color"
    val isEditorOnlyBg = hasBgImage && themeScope == "editor_only"

    val currentBookNotes   by bookVm.notes.collectAsStateWithLifecycle()
    val currentBookFolders by bookVm.folders.collectAsStateWithLifecycle()
    val worldEntries       by bookVm.worldEntries.collectAsStateWithLifecycle()

    val allNotes   by noteListVm.notes.collectAsStateWithLifecycle()
    val allFolders by noteListVm.folders.collectAsStateWithLifecycle()
    val shortcuts  by shortcutsVm.shortcuts.collectAsStateWithLifecycle()

    val floatingWindows    by editorVm.floatingWindows.collectAsStateWithLifecycle()
    val pinnedTopNotes     by editorVm.pinnedTopNotes.collectAsStateWithLifecycle()
    val pinnedTopIndex     by editorVm.pinnedTopIndex.collectAsStateWithLifecycle()
    val pinnedBottomNotes  by editorVm.pinnedBottomNotes.collectAsStateWithLifecycle()
    val pinnedBottomIndex  by editorVm.pinnedBottomIndex.collectAsStateWithLifecycle()
    val companionTabBarBottom   by editorVm.companionTabBarBottom.collectAsStateWithLifecycle()
    val companionSplitHorizontal by editorVm.companionSplitHorizontal.collectAsStateWithLifecycle()

    // ── Local UI state ────────────────────────────────────────────────────────
    var rightPanelTab   by remember { mutableIntStateOf(0) }
    var leftDrawerMode  by remember { mutableStateOf("Current") }

    var showFindBar    by remember { mutableStateOf(false) }
    var findQuery      by remember { mutableStateOf("") }
    var replaceQuery   by remember { mutableStateOf("") }

    var showRenameDialog     by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var filePickerTargetSlot by remember { mutableStateOf<String?>(null) }

    val anyDialogOpen = showRenameDialog || showCreateNoteDialog || filePickerTargetSlot != null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) { if (!anyDialogOpen) dialogOneShotBitmap = null }
    }

    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val raw = BitmapBlur.captureOnly(view)
            dialogOneShotBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                raw?.let { BitmapBlur.blurBitmap(it, radius = blurRadiusPx) }
            }
        }
        openDialog()
    }

    // ── Sora CodeEditor state ─────────────────────────────────────────────────
    var soraEditorRef  by remember { mutableStateOf<CodeEditor?>(null) }
    var loadedNoteId   by rememberSaveable { mutableStateOf<String?>(null) }

    var pillMode     by remember { mutableIntStateOf(0) }
    var pillOffsetX  by remember { mutableFloatStateOf(0f) }
    var pillOffsetY  by remember { mutableFloatStateOf(0f) }

    // FIX 2: prevWordCount now uses rememberSaveable so it survives rotation.
    // Previously a plain remember meant the delta indicator reset to 0 after config change.
    var prevWordCount   by rememberSaveable { mutableIntStateOf(wordCount) }
    var deltaText       by remember { mutableStateOf<String?>(null) }
    var isPositiveDelta by remember { mutableStateOf(true) }
    var goalNotified    by remember { mutableStateOf(false) }

    LaunchedEffect(wordCount) {
        val diff = wordCount - prevWordCount
        if (diff != 0) {
            deltaText       = if (diff > 0) "+$diff" else "$diff"
            isPositiveDelta = diff > 0
            prevWordCount   = wordCount
            delay(800)
            deltaText = null
        }
    }
    LaunchedEffect(goalProgress) {
        if (goalProgress >= 1f && !goalNotified && wordCount > 0) {
            goalNotified = true
            Toast.makeText(context, "Daily writing goal reached!", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(initialNoteId) {
        if (!initialNoteId.isNullOrEmpty()) editorVm.loadNote(initialNoteId)
        else if (currentBookNotes.isNotEmpty()) editorVm.loadNote(currentBookNotes.first().id)
    }

    // FIX 3: Removed activeNote?.content from the LaunchedEffect key.
    // Previously keying on content meant this effect was cancelled and re-launched on
    // every keystroke (content changes → ViewModel emits → new content value → effect restarts).
    // The guard condition `editor.text.length == 0 && note.content.isNotEmpty()` already
    // handles the edge case of an editor that exists but hasn't been filled yet.
    // Keying only on id + soraEditorRef is sufficient and far cheaper.
    LaunchedEffect(activeNote?.id, soraEditorRef) {
        val note   = activeNote ?: return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        if (loadedNoteId != note.id || (editor.text.length == 0 && note.content.isNotEmpty())) {
            loadedNoteId = note.id
            editor.setText(note.content)
            ProseDiagnosticProvider.attachEditor(editor)
            val (hints, diagnostics) = withContext(Dispatchers.Default) {
                val h = ProseInlayHintProvider.computeInlayHints(note.content, worldEntries)
                val d = ProseDiagnosticProvider.analyzeDiagnostics(note.content)
                h to d
            }
            editor.setInlayHints(hints)
            editor.setDiagnostics(diagnostics)
        }
    }

    // Debounced analysis for Inlay Hints (Scene word counts & POV tags) and Diagnostics (Passives, Adverbs, Repetitions)
    var editorCurrentText by remember { mutableStateOf("") }
    LaunchedEffect(editorCurrentText, worldEntries) {
        if (editorCurrentText.isEmpty()) return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        delay(400) // Debounce 400ms to keep editing fluid
        val (hints, diagnostics) = withContext(Dispatchers.Default) {
            val h = ProseInlayHintProvider.computeInlayHints(editorCurrentText, worldEntries)
            val d = ProseDiagnosticProvider.analyzeDiagnostics(editorCurrentText)
            h to d
        }
        editor.setInlayHints(hints)
        editor.setDiagnostics(diagnostics)
    }

    val soraEditorForDispose = soraEditorRef
    DisposableEffect(activeNote?.id) {
        onDispose {
            activeNote?.let {
                editorVm.saveVersionSnapshotOnLeave(soraEditorForDispose?.text?.toString() ?: "")
            }
        }
    }

    // FIX 4: The launcher result was being discarded (variable not stored, never launched).
    // Now stored so it can be called. If you have a UI entry point for connecting an external
    // folder, call externalFolderLauncher.launch(null) from that button/menu item.
    val externalFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "External Folder"
        noteListVm.connectExternalFolder(uri, name)
    }

    val isKeyboardVisible = WindowInsets.isImeVisible
    val hazeState = LocalHazeState.current ?: dev.chrisbanes.haze.HazeState()
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Editor-only background image ──────────────────────────────────────
        if (isEditorOnlyBg) {
            AsyncImage(
                model              = bgUri,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgMode == "blurred" &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            blurIntensity > 0f
                        ) Modifier.graphicsLayer {
                            val r = blurIntensity * density.density
                            if (r > 0f) renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else Modifier
                    )
            )
            val themeBgColor = parseComposeColor(
                activeTheme?.colors?.background ?: "#FAFAF7", Color(0xFFFAFAF7)
            )
            Box(Modifier.fillMaxSize().background(themeBgColor.copy(alpha = bgOpacity)))
        }

        // ── Reusable Component Renderers ──────────────────────────────────────
        val renderEditorScaffold: @Composable (
            onNavClick: () -> Unit,
            onOpenRightPanel: () -> Unit,
            isLeftDrawerOpen: Boolean
        ) -> Unit = { onNavClick, onOpenRightPanel, isLeftDrawerOpen ->
            Scaffold(
                containerColor      = Color.Transparent,
                contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
                topBar = {
                    EditorTopBarWithMenu(
                        activeNote        = activeNote,
                        zenMode           = zenMode,
                        goalProgress      = goalProgress,
                        isLeftDrawerOpen  = isLeftDrawerOpen,
                        soraEditorRef     = soraEditorRef,
                        onNavClick        = onNavClick,
                        onTitleClick      = { if (activeNote != null) scope.launch { captureForDialog { showRenameDialog = true } } },
                        onOpenRightPanel  = onOpenRightPanel,
                        onToggleFind      = { showFindBar = !showFindBar },
                        onSaveCheckpoint  = {
                            editorVm.saveManualSnapshot(soraEditorRef?.text?.toString() ?: "")
                            Toast.makeText(context, "Checkpoint saved", Toast.LENGTH_SHORT).show()
                        },
                        onEnterZen        = { editorVm.setZen(true) },
                        onOpenFloating    = { activeNote?.let { editorVm.openFloatingWindow(it.id) } },
                        onExport          = { fmt -> activeNote?.let { ExportHelper.shareNote(context, it, fmt) } },
                        onVersionHistory  = { editorVm.flushContent(soraEditorRef?.text?.toString() ?: ""); onOpenHistory() },
                        onShortcuts       = onOpenShortcuts,
                        onGuide           = onOpenGuide,
                        onSettings        = onOpenSettings,
                    )
                },
                bottomBar = {
                                CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                    AnimatedVisibility(
                                        visible = isKeyboardVisible,
                                        enter   = slideInVertically(initialOffsetY = { it }),
                                        exit    = slideOutVertically(targetOffsetY = { it })
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .frostedBar(hazeState)
                                                .imePadding()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment     = Alignment.CenterVertically
                                        ) {
                                            shortcuts.forEach { shortcut ->
                                                FormatButton(label = shortcut.label) {
                                                    when (shortcut.kind) {
                                                        "wrap" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: shortcut.payload)
                                                        "pair" -> soraEditorRef?.applyFormat(shortcut.payload, shortcut.closing ?: "")
                                                        else   -> soraEditorRef?.insertAtCursor(shortcut.payload)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) {
                                Column(Modifier.fillMaxSize()) {

                                    // ── Find/Replace bar ──────────────────────────────
                                    FindReplaceBar(
                                        visible       = showFindBar,
                                        findQuery     = findQuery,
                                        replaceQuery  = replaceQuery,
                                        onFindChange  = { findQuery = it },
                                        onReplaceChange = { replaceQuery = it },
                                        onPrevious    = { soraEditorRef?.searcher?.gotoPrevious() },
                                        onNext        = { soraEditorRef?.searcher?.gotoNext() },
                                        onReplaceAll  = {
                                            val editor = soraEditorRef ?: return@FindReplaceBar
                                            if (findQuery.isNotEmpty()) {
                                                editor.searcher.replaceAll(replaceQuery)
                                                editorVm.onContentChanged(editor.text.toString())
                                            }
                                        },
                                        onClose       = { showFindBar = false }
                                    )

                                    // Drive Sora's searcher from find state
                                    LaunchedEffect(findQuery, showFindBar) {
                                        val editor = soraEditorRef ?: return@LaunchedEffect
                                        if (showFindBar && findQuery.isNotEmpty()) {
                                            editor.searcher.search(findQuery, EditorSearcher.SearchOptions(true, false))
                                        } else {
                                            editor.searcher.stopSearch()
                                        }
                                    }

                                    // ── Sora CodeEditor ───────────────────────────────
                                    val hasBgImageLocal     = !activeTheme?.backgroundImageUri.isNullOrEmpty()
                                    val currentThemeBg      = MaterialTheme.colorScheme.background
                                    val editorTextSizeSp    = remember(activeTheme?.fontSize) {
                                        (activeTheme?.fontSize ?: 18).toFloat()
                                    }
                                    val editorTypeface      = remember(activeTheme?.fontFamily) {
                                        activeTheme?.fontFamily?.let { ThemeManager.resolveTypeface(context, it) }
                                    }
                                    val bgArgb              = remember(hasBgImageLocal, currentThemeBg) {
                                        if (hasBgImageLocal) android.graphics.Color.TRANSPARENT
                                        else currentThemeBg.toArgb()
                                    }
                                    val popupBgDrawable = remember(activeTheme?.colors?.accent, activeTheme?.colors?.surface) {
                                        val density    = context.resources.displayMetrics.density
                                        val cornerPx   = 24f * density
                                        val accentHex  = activeTheme?.colors?.accent ?: "#000000"
                                        val surfaceHex = activeTheme?.colors?.surface ?: "#FFFFFF"
                                        val accentArgb  = runCatching { android.graphics.Color.parseColor(accentHex) }.getOrDefault(android.graphics.Color.BLACK)
                                        val surfaceArgb = runCatching { android.graphics.Color.parseColor(surfaceHex) }.getOrDefault(android.graphics.Color.WHITE)
                                        val fill = android.graphics.drawable.GradientDrawable().apply {
                                            setColor(surfaceArgb); cornerRadius = cornerPx
                                        }
                                        val overlay = android.graphics.drawable.GradientDrawable(
                                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                                            intArrayOf(
                                                android.graphics.Color.argb(
                                                    71,
                                                    android.graphics.Color.red(accentArgb),
                                                    android.graphics.Color.green(accentArgb),
                                                    android.graphics.Color.blue(accentArgb)
                                                ),
                                                android.graphics.Color.TRANSPARENT
                                            )
                                        ).apply { cornerRadius = cornerPx }
                                        android.graphics.drawable.LayerDrawable(arrayOf(fill, overlay))
                                    }

                                    Box(Modifier.fillMaxSize()) {
                                        AndroidView(
                                            factory = { ctx ->
                                                CodeEditor(ctx).apply {
                                                    isLineNumberEnabled    = false
                                                    isHighlightCurrentLine = false
                                                    isWordwrap             = true
                                                    registerInlayHintRenderer(
                                                        io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer()
                                                    )
                                                    setEditorLanguage(ScribeProseLanguage())
                                                    isNestedScrollingEnabled = true
                                                    try {
                                                        getComponent(
                                                            io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
                                                        ).isEnabled = true
                                                    } catch (_: Exception) { }

                                                    try {
                                                        getComponent(
                                                            io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow::class.java
                                                        ).isEnabled = true
                                                    } catch (_: Exception) { }

                                                    subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                                        val current = text.toString()
                                                        editorCurrentText = current
                                                        if (loadedNoteId != null)
                                                            editorVm.onContentChanged(current)
                                                    }
                                                    subscribeEvent(EditorKeyEvent::class.java) { event, _ ->
                                                        if (event.action != android.view.KeyEvent.ACTION_DOWN) return@subscribeEvent
                                                        if (event.keyCode != android.view.KeyEvent.KEYCODE_ENTER) return@subscribeEvent
                                                        val cur = this.cursor
                                                        if (cur.isSelected) return@subscribeEvent
                                                        val line = this.text.getLine(cur.leftLine)
                                                        val col  = cur.leftColumn
                                                        val closeChars = setOf(')', ']', '}', '`', '"', '\'', '\u201D', '\u2019', '\u00BB')
                                                        if (col < line.length && line[col] in closeChars) {
                                                            setSelection(cur.leftLine, col + 1)
                                                            event.intercept()
                                                        }
                                                    }
                                                }.also { soraEditorRef = it }
                                            },
                                            update = { editor ->
                                                editor.setTextSize(editorTextSizeSp)
                                                editorTypeface?.let { editor.typefaceText = it }
                                                editor.setBackgroundColor(bgArgb)
                                                activeTheme?.let { theme ->
                                                    val scheme = ScribeColorScheme(theme)
                                                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,       bgArgb)
                                                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgArgb)
                                                    scheme.setColor(EditorColorScheme.LINE_NUMBER,            bgArgb)
                                                    editor.colorScheme = scheme
                                                    try {
                                                        val aw = editor.getComponent(
                                                            io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
                                                        )
                                                        var popup: android.widget.PopupWindow? = null
                                                        var cls: Class<*>? = aw.javaClass
                                                        outer@ while (cls != null && cls != Any::class.java) {
                                                            for (f in cls.declaredFields) {
                                                                if (android.widget.PopupWindow::class.java.isAssignableFrom(f.type)) {
                                                                    f.isAccessible = true
                                                                    popup = f.get(aw) as? android.widget.PopupWindow
                                                                    break@outer
                                                                }
                                                            }
                                                            cls = cls.superclass
                                                        }
                                                        popup?.setBackgroundDrawable(popupBgDrawable)
                                                    } catch (_: Exception) { }
                                                }
                                            },
                                            onRelease = { editor ->
                                                soraEditorRef = null
                                                ProseDiagnosticProvider.attachEditor(null)
                                                editor.release()
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Word-count pill
                                        CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                            WordCountPill(
                                                modifier        = Modifier.align(Alignment.TopEnd),
                                                pillOffsetX     = pillOffsetX,
                                                pillOffsetY     = pillOffsetY,
                                                onOffsetChange  = { dx, dy -> pillOffsetX += dx; pillOffsetY += dy },
                                                pillMode        = pillMode,
                                                onModeClick     = { pillMode = (pillMode + 1) % 3 },
                                                wordCount       = wordCount,
                                                charCount       = charCount,
                                                deltaText       = deltaText,
                                                isPositiveDelta = isPositiveDelta,
                                                hazeState       = LocalHazeState.current,
                                            )

                                            if (zenMode) {
                                                ScribeSingleFab(
                                                    icon               = Icons.Default.FullscreenExit,
                                                    contentDescription = "Exit Zen",
                                                    modifier           = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                                    onClick            = { editorVm.setZen(false) }
                                                )
                                            }
                                        }
                                    } // end editor Box
                                }
                            }
                        } // end Scaffold
        }

        val renderLeftDrawer: @Composable (onClose: () -> Unit) -> Unit = { onClose ->
            EditorLeftDrawer(
                leftDrawerMode   = leftDrawerMode,
                onModeChange     = { leftDrawerMode = it },
                currentBookNotes = currentBookNotes,
                allNotes         = allNotes,
                activeNoteId     = activeNote?.id,
                onNoteClick      = { id ->
                    editorVm.loadNote(id)
                    onClose()
                },
                onAddNote        = { scope.launch { captureForDialog { showCreateNoteDialog = true } } },
                hazeState        = hazeState,
                barBlurBitmap    = barBlurBitmap,
            )
        }

        val renderRightPanel: @Composable (onClose: () -> Unit) -> Unit = { onClose ->
            EditorRightPanel(
                rightPanelTab         = rightPanelTab,
                onTabChange           = { rightPanelTab = it },
                pinnedTopNotes        = pinnedTopNotes,
                pinnedTopIndex        = pinnedTopIndex,
                pinnedBottomNotes     = pinnedBottomNotes,
                pinnedBottomIndex     = pinnedBottomIndex,
                allNotes              = allNotes,
                worldEntries          = worldEntries,
                outline               = outline,
                activeTheme           = activeTheme,
                proseAnalysis         = proseAnalysis,
                soraEditorRef         = soraEditorRef,
                tabBarAtBottom        = companionTabBarBottom,
                splitHorizontal       = companionSplitHorizontal,
                onToggleTabBarPos     = { editorVm.setCompanionTabBarBottom(!companionTabBarBottom) },
                onToggleSplitLayout   = { editorVm.setCompanionSplitHorizontal(!companionSplitHorizontal) },
                onSwapSlots           = { editorVm.swapPinnedSlots() },
                onPrevTop             = { editorVm.prevPinnedTop() },
                onNextTop             = { editorVm.nextPinnedTop() },
                onSwitchTop           = { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } },
                onEditTop             = { id -> editorVm.loadNote(id) },
                onRemoveTop           = { id -> editorVm.removePinnedTop(id) },
                onPrevBottom          = { editorVm.prevPinnedBottom() },
                onNextBottom          = { editorVm.nextPinnedBottom() },
                onSwitchBottom        = { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } },
                onEditBottom          = { id -> editorVm.loadNote(id) },
                onRemoveBottom        = { id -> editorVm.removePinnedBottom(id) },
                onPickTop             = { scope.launch { captureForDialog { filePickerTargetSlot = "top" } } },
                onPickBottom          = { scope.launch { captureForDialog { filePickerTargetSlot = "bottom" } } },
                onClose               = onClose,
                barBlurBitmap         = barBlurBitmap,
                hazeState             = hazeState,
            )
        }

        // ── Adaptive Layout Branching ─────────────────────────────────────────
        if (isCompact) {
            CompactEditorLayout(
                hazeState          = hazeState,
                barBlurBitmap      = barBlurBitmap,
                isKeyboardVisible  = isKeyboardVisible,
                soraEditorRef      = soraEditorRef,
                focusManager       = focusManager,
                editorContent      = renderEditorScaffold,
                leftDrawerContent  = renderLeftDrawer,
                rightPanelContent  = renderRightPanel,
            )
        } else {
            ExpandedEditorLayout(
                hazeState          = hazeState,
                barBlurBitmap      = barBlurBitmap,
                soraEditorRef      = soraEditorRef,
                editorContent      = renderEditorScaffold,
                leftDrawerContent  = renderLeftDrawer,
                rightPanelContent  = renderRightPanel,
            )
        }

        // ── Floating Windows Overlay ──────────────────────────────────────────
        val mappedNotes = remember(currentBookNotes, worldEntries) {
            buildList {
                addAll(currentBookNotes)
                worldEntries.forEach { w ->
                    if (none { it.id == w.id }) add(
                        Note(id = w.id, name = w.name,
                             content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}")
                    )
                }
            }
        }
        FloatingWindowOverlay(
            floatingWindows  = floatingWindows,
            notes            = mappedNotes,
            activeTheme      = activeTheme,
            onCloseWindow    = { id -> editorVm.closeFloatingWindow(id) },
            onToggleCollapse = { id -> editorVm.toggleCollapseFloatingWindow(id) },
            onMoveWindow     = { id, x, y -> editorVm.moveFloatingWindow(id, x, y) }
        )

        // ── Dialogs ───────────────────────────────────────────────────────────
        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
            if (showRenameDialog && activeNote != null) {
                val noteToRename = activeNote
                var renameText by remember { mutableStateOf(noteToRename?.name ?: "") }
                FrostedDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title            = { Text("Rename Note") },
                    text             = {
                        OutlinedTextField(
                            value         = renameText,
                            onValueChange = { renameText = it },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = renameText.trim()
                            if (t.isNotEmpty() && noteToRename != null) bookVm.renameNote(noteToRename.id, t)
                            showRenameDialog = false
                        }) { Text("Rename") }
                    },
                    dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
                )
            }

            if (showCreateNoteDialog) {
                var noteTitle by remember { mutableStateOf("") }
                FrostedDialog(
                    onDismissRequest = { showCreateNoteDialog = false },
                    title            = { Text("New Note") },
                    text             = {
                        OutlinedTextField(
                            value         = noteTitle,
                            onValueChange = { noteTitle = it },
                            label         = { Text("Title") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = noteTitle.trim()
                            if (t.isNotEmpty()) bookVm.createNote(t) { id ->
                                showCreateNoteDialog = false
                                editorVm.loadNote(id)
                            }
                        }) { Text("Create") }
                    },
                    dismissButton = { TextButton(onClick = { showCreateNoteDialog = false }) { Text("Cancel") } }
                )
            }

            filePickerTargetSlot?.let { targetSlot ->
                FileExplorerOverlayDialog(
                    allNotes     = if (leftDrawerMode == "Current") currentBookNotes else allNotes,
                    allFolders   = if (leftDrawerMode == "Current") currentBookFolders else allFolders,
                    onSelectNote = { note ->
                        if (targetSlot == "top") editorVm.addPinnedTop(note.id)
                        else editorVm.addPinnedBottom(note.id)
                        filePickerTargetSlot = null
                    },
                    onDismiss = { filePickerTargetSlot = null }
                )
            }
        }
    } // end outer Box
}

// ── Extracted: Top bar + overflow menu ───────────────────────────────────────
// FIX 9 (decomposition): Moved the topBar content into its own composable so
// the Scaffold's topBar slot isn't holding 60+ lines of inline logic. This
// also means showMenu recomposition is scoped here instead of touching the parent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBarWithMenu(
    activeNote       : Note?,
    zenMode          : Boolean,
    goalProgress     : Float,
    isLeftDrawerOpen : Boolean,
    soraEditorRef    : CodeEditor?,
    onNavClick       : () -> Unit,
    onTitleClick     : () -> Unit,
    onOpenRightPanel : () -> Unit,
    onToggleFind     : () -> Unit,
    onSaveCheckpoint : () -> Unit,
    onEnterZen       : () -> Unit,
    onOpenFloating   : () -> Unit,
    onExport         : (String) -> Unit,
    onVersionHistory : () -> Unit,
    onShortcuts      : () -> Unit,
    onGuide          : () -> Unit,
    onSettings       : () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        ScribeEditorTopBar(
            title          = activeNote?.name,
            onNavClick     = onNavClick,
            onTitleClick   = onTitleClick,
            navigationIcon = Icons.Default.Menu,
            visible        = !zenMode,
            actions        = listOf(
                ScribeBarAction(Icons.Default.Dock,        "Outline & Pinned Notes") { onOpenRightPanel() },
                ScribeBarAction(Icons.Default.Search,      "Find")                   { onToggleFind() },
                ScribeBarAction(Icons.Default.BookmarkAdd, "Save Checkpoint")        { onSaveCheckpoint() },
                ScribeBarAction(Icons.Default.MoreVert,    "Menu")                   { showMenu = true },
            ),
            extraContent = {
                if (!zenMode) {
                    LinearProgressIndicator(
                        progress   = { goalProgress },
                        modifier   = Modifier.fillMaxWidth().height(3.dp),
                        color      = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        )
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor   = LocalSolidSurface.current
        ) {
            DropdownMenuItem(text = { Text("Enter Zen Mode") },                  onClick = { showMenu = false; onEnterZen() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Open as Floating Reference Window") }, onClick = { showMenu = false; onOpenFloating() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Export as TXT") },      onClick = { showMenu = false; onExport("txt") })
            DropdownMenuItem(text = { Text("Export as Markdown") }, onClick = { showMenu = false; onExport("md") })
            DropdownMenuItem(text = { Text("Export as HTML") },     onClick = { showMenu = false; onExport("html") })
            DropdownMenuItem(text = { Text("Export as PDF") },      onClick = { showMenu = false; onExport("pdf") })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Version History") }, onClick = { showMenu = false; onVersionHistory() })
            DropdownMenuItem(text = { Text("Shortcuts") },       onClick = { showMenu = false; onShortcuts() })
            DropdownMenuItem(text = { Text("User Guide") },      onClick = { showMenu = false; onGuide() })
            DropdownMenuItem(text = { Text("Settings") },        onClick = { showMenu = false; onSettings() })
        }
    }
}

// ── Extracted: Find/Replace bar ───────────────────────────────────────────────
// FIX 9 (decomposition): Pulled out so the Column inside the Scaffold content
// doesn't inline 40+ lines of find/replace UI.
@Composable
private fun FindReplaceBar(
    visible         : Boolean,
    findQuery       : String,
    replaceQuery    : String,
    onFindChange    : (String) -> Unit,
    onReplaceChange : (String) -> Unit,
    onPrevious      : () -> Unit,
    onNext          : () -> Unit,
    onReplaceAll    : () -> Unit,
    onClose         : () -> Unit,
) {
    if (!visible) return
    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = findQuery,
                onValueChange = onFindChange,
                placeholder   = { Text("Find") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value         = replaceQuery,
                onValueChange = onReplaceChange,
                placeholder   = { Text("Replace") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(48.dp)
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
            IconButton(onClick = onReplaceAll) {
                Icon(Icons.Default.FindReplace, contentDescription = "Replace All")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }
}

// ── Extracted: Word-count pill ────────────────────────────────────────────────
// FIX 9 (decomposition): Separated the draggable pill from the editor Box so
// drag state and animated content are scoped here and don't invalidate the parent.
@Composable
private fun WordCountPill(
    modifier        : Modifier,
    pillOffsetX     : Float,
    pillOffsetY     : Float,
    onOffsetChange  : (Float, Float) -> Unit,
    pillMode        : Int,
    onModeClick     : () -> Unit,
    wordCount       : Int,
    charCount       : Int,
    deltaText       : String?,
    isPositiveDelta : Boolean,
    hazeState       : dev.chrisbanes.haze.HazeState?,
) {
    Box(
        modifier = modifier
            .offset { IntOffset(pillOffsetX.roundToInt(), pillOffsetY.roundToInt()) }
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = deltaText != null,
                enter   = fadeIn() + slideInVertically { -20 },
                exit    = fadeOut() + slideOutVertically { -20 }
            ) {
                Text(
                    text       = deltaText ?: "",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (isPositiveDelta) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier   = Modifier.padding(bottom = 2.dp)
                )
            }
            Surface(
                shape           = CircleShape,
                color           = frostedContainerColor(MaterialTheme.colorScheme.primaryContainer),
                tonalElevation  = 0.dp,
                shadowElevation = 0.dp,
                modifier        = Modifier
                    .clip(CircleShape)
                    .frostedFab(hazeState)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onOffsetChange(dragAmount.x, dragAmount.y)
                        }
                    }
                    .clickable { onModeClick() }
            ) {
                AnimatedContent(
                    targetState    = pillMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { mode ->
                    Text(
                        text = when (mode) {
                            1    -> "$wordCount words · $charCount chars"
                            2    -> "$wordCount words · ${maxOf(1, wordCount / 200)}m"
                            else -> "$wordCount words"
                        },
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ── File explorer overlay ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileExplorerOverlayDialog(
    allNotes     : List<Note>,
    allFolders   : List<Folder>,
    onSelectNote : (Note) -> Unit,
    onDismiss    : () -> Unit
) {
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }
    val folderGrouped = remember(allNotes, allFolders) {
        buildMap<String, MutableList<Note>> {
            allNotes.forEach { n -> getOrPut(n.folderPath.ifBlank { "/" }) { mutableListOf() }.add(n) }
        }
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Pick a note to pin", fontWeight = FontWeight.Bold) },
        text             = {
            LazyColumn(
                modifier            = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                folderGrouped.forEach { (folderPath, notesInFolder) ->
                    val isExpanded = expandedPaths[folderPath] ?: true
                    item(key = "f_$folderPath") {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expandedPaths[folderPath] = !isExpanded }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                folderPath.substringAfterLast('/'),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp,
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }
                    if (isExpanded) {
                        items(notesInFolder, key = { "n_${it.id}" }) { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectNote(note) }
                                    .padding(start = 24.dp),
                                shape  = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    note.name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormatButton(
    label      : String,
    isSelected : Boolean = false,
    onClick    : () -> Unit
) {
    Surface(
        onClick      = onClick,
        shape        = CircleShape,
        color        = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier     = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── CodeEditor extension helpers ──────────────────────────────────────────────

private fun CodeEditor.applyFormat(open: String, close: String) {
    val cur = cursor
    if (cur.isSelected) {
        val indexer  = text.indexer
        val startIdx = indexer.getCharIndex(cur.leftLine,  cur.leftColumn)
        val endIdx   = indexer.getCharIndex(cur.rightLine, cur.rightColumn)
        val selected = text.subSequence(startIdx, endIdx).toString()
        text.replace(
            cur.leftLine,  cur.leftColumn,
            cur.rightLine, cur.rightColumn,
            "$open$selected$close"
        )
    } else {
        val line = cur.leftLine
        val col  = cur.leftColumn
        text.insert(line, col, "$open$close")
        this.cursor.set(line, col + open.length)
    }
}

private fun CodeEditor.applyLinePrefix(prefix: String) {
    val line = cursor.leftLine
    text.insert(line, 0, prefix)
    cursor.set(line, cursor.leftColumn + prefix.length)
}

private fun CodeEditor.insertAtCursor(str: String) {
    commitText(str)
}

private fun parseComposeColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { fallback }

