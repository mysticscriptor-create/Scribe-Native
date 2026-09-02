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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.components.ScribeSingleFab
import com.primaloptima.scribe.ui.components.ScribeEditorTopBar
import com.primaloptima.scribe.ui.components.ScribeBarAction
import com.primaloptima.scribe.ui.components.ScribeBarIconButton
import com.primaloptima.scribe.ui.components.FrostedBottomSheet
import com.primaloptima.scribe.ui.components.FrostedSheetDragHandle
import com.primaloptima.scribe.ui.components.EditorLeftDrawer
import com.primaloptima.scribe.ui.components.EditorRightPanel
import com.primaloptima.scribe.ui.theme.frostedFab
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.FrostedDropdownMenu
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.Help
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
    val allBooks           by bookVm.allBooks.collectAsStateWithLifecycle()

    val allNotes   by noteListVm.notes.collectAsStateWithLifecycle()
    val allFolders by noteListVm.folders.collectAsStateWithLifecycle()
    val shortcuts  by shortcutsVm.shortcuts.collectAsStateWithLifecycle()

    val floatingWindows    by editorVm.floatingWindows.collectAsStateWithLifecycle()
    val workbenchState     by editorVm.workbenchState.collectAsStateWithLifecycle()
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
    var showEditorTray       by remember { mutableStateOf(false) }

    val anyDialogOpen = showRenameDialog || showCreateNoteDialog || showEditorTray
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) { if (!anyDialogOpen) dialogOneShotBitmap = null }
    }

    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        openDialog()
    }

    // ── Sora CodeEditor state ─────────────────────────────────────────────────
    var soraEditorRef      by remember { mutableStateOf<CodeEditor?>(null) }
    var isHandleDragging   by remember { mutableStateOf(false) }
    var loadedNoteId       by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Floating Pills Scroll Animation & Dual-Title State ──────────────────────
    var floatingPillsVisible by rememberSaveable { mutableStateOf(true) }
    var accumulatedScrollUp by remember { mutableFloatStateOf(0f) }
    val hideOnScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                val dy = available.y
                if (dy < -8f) {
                    // Scrolling DOWN (content moving up) -> text scrolls, then hide pills
                    accumulatedScrollUp = 0f
                    if (floatingPillsVisible) floatingPillsVisible = false
                } else if (dy > 8f) {
                    // Scrolling UP (content moving down) -> accumulate slight threshold, then show pills
                    accumulatedScrollUp += dy
                    if (accumulatedScrollUp > 30f && !floatingPillsVisible) {
                        floatingPillsVisible = true
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // Dual-title inline editing state
    var primaryTitleText by remember(activeNote?.id, activeNote?.name) {
        mutableStateOf(activeNote?.name ?: "")
    }
    var secondaryTitleText by remember(activeNote?.id) {
        mutableStateOf("")
    }
    var showSecondaryTitle by remember(activeNote?.id) {
        mutableStateOf(false)
    }
    val primaryTitleFocusRequester = remember { FocusRequester() }
    val secondaryTitleFocusRequester = remember { FocusRequester() }

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
                bottomBar = {
                    CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                        val registerBounds = LocalInteractiveBoundsRegistry.current
                        DisposableEffect(isKeyboardVisible) {
                            onDispose { registerBounds("shortcut_bar", null) }
                        }

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
                                    .onGloballyPositioned { coords ->
                                        if (isKeyboardVisible) {
                                            registerBounds("shortcut_bar", coords.boundsInRoot())
                                        }
                                    }
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .androidx.compose.ui.input.nestedscroll.nestedScroll(hideOnScrollConnection)
                ) {
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

                        // ── Inline Dual Titles ──────────────────────────────
                        if (!zenMode && activeNote != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                // Primary Title
                                BasicTextField(
                                    value = primaryTitleText,
                                    onValueChange = {
                                        primaryTitleText = it
                                        bookVm.renameNote(activeNote!!.id, it)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            showSecondaryTitle = true
                                            secondaryTitleFocusRequester.requestFocus()
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(primaryTitleFocusRequester),
                                    decorationBox = { innerTextField ->
                                        if (primaryTitleText.isEmpty()) {
                                            Text(
                                                text = "Title",
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                // Secondary Subtitle
                                if (showSecondaryTitle || secondaryTitleText.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    BasicTextField(
                                        value = secondaryTitleText,
                                        onValueChange = { secondaryTitleText = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                        keyboardActions = KeyboardActions(
                                            onNext = {
                                                soraEditorRef?.requestFocus()
                                                soraEditorRef?.setSelection(0, 0)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(secondaryTitleFocusRequester),
                                        decorationBox = { innerTextField ->
                                            if (secondaryTitleText.isEmpty()) {
                                                Text(
                                                    text = "Subtitle (Optional)",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
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
                                        try {
                                            subscribeEvent(io.github.rosemoe.sora.event.HandleStateChangeEvent::class.java) { event, _ ->
                                                isHandleDragging = event.isHeld
                                            }
                                        } catch (_: Throwable) { }
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

                            // Word-count pill (Floating Draggable or docked)
                            CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
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

                    // ── Floating Pills Layer (Top-Left and Top-Right) ─────────
                    if (!zenMode) {
                        AnimatedVisibility(
                            visible = floatingPillsVisible,
                            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    slideInVertically(
                                        initialOffsetY = { -it },
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                    ),
                            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                   slideOutVertically(
                                       targetOffsetY = { -it },
                                       animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                   ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Top-Left: Search Floating Pill
                                FloatingPillButton(
                                    icon = Icons.Default.Search,
                                    contentDescription = "Find & Replace",
                                    hazeState = hazeState,
                                    onClick = { showFindBar = !showFindBar }
                                )

                                // Top-Right: Actions Group (Word Count, Save Checkpoint, Menu)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Word Count Floating Pill
                                    FloatingWordCountPill(
                                        wordCount = wordCount,
                                        charCount = charCount,
                                        deltaText = deltaText,
                                        isPositiveDelta = isPositiveDelta,
                                        pillMode = pillMode,
                                        onModeClick = { pillMode = (pillMode + 1) % 3 },
                                        hazeState = hazeState
                                    )

                                    // Save Checkpoint Floating Pill
                                    FloatingPillButton(
                                        icon = Icons.Default.BookmarkAdd,
                                        contentDescription = "Save Checkpoint",
                                        hazeState = hazeState,
                                        onClick = {
                                            editorVm.saveManualSnapshot(soraEditorRef?.text?.toString() ?: "")
                                            Toast.makeText(context, "Checkpoint saved", Toast.LENGTH_SHORT).show()
                                        }
                                    )

                                    // Options Menu Floating Pill
                                    FloatingPillButton(
                                        icon = Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        hazeState = hazeState,
                                        onClick = { showEditorTray = true }
                                    )
                                }
                            }
                        }
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
                workbenchState        = workbenchState,
                allNotes              = allNotes,
                worldEntries          = worldEntries,
                books                 = allBooks,
                outline               = outline,
                activeTheme           = activeTheme,
                activeNote            = activeNote,
                proseAnalysis         = proseAnalysis,
                soraEditorRef         = soraEditorRef,
                tabBarAtBottom        = companionTabBarBottom,
                onToggleTabBarPos     = { editorVm.setCompanionTabBarBottom(!companionTabBarBottom) },
                onUpdatePane          = { id, transform -> editorVm.updatePane(id, transform) },
                onUpdateWorkbench     = { transform -> editorVm.updateWorkbench(transform) },
                onAddPane             = { scope -> editorVm.addPane(scope) },
                onRemovePane          = { id -> editorVm.removePane(id) },
                onDuplicatePane       = { id -> editorVm.duplicatePane(id) },
                onMinimizePane        = { id, by -> editorVm.minimizePane(id, by) },
                onRestorePane         = { id -> editorVm.restorePane(id) },
                onPinNote             = { paneId, noteId -> editorVm.pinNoteToPane(paneId, noteId) },
                onUnpinNote           = { paneId, noteId -> editorVm.unpinNote(paneId, noteId) },
                onReorderNote         = { paneId, from, to -> editorVm.reorderPinnedNote(paneId, from, to) },
                onCreateNote          = { paneId, title, content -> editorVm.createNoteForPane(paneId, title, content, activeNote?.bookId ?: Note.DEFAULT_BOOK_ID) },
                onSaveNoteContent     = { noteId, content -> editorVm.updateNoteContent(noteId, content) },
                onLoadNote            = { id -> editorVm.loadNote(id) },
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
                isHandleDragging   = isHandleDragging,
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

        // ── Dialogs & Bottom Sheets ───────────────────────────────────────────
        if (showEditorTray) {
            EditorOptionsBottomSheet(
                noteTitle        = activeNote?.name ?: "Untitled Note",
                onDismiss        = { showEditorTray = false },
                onEnterZen       = {
                    showEditorTray = false
                    editorVm.setZen(true)
                },
                onOpenFloating   = {
                    showEditorTray = false
                    activeNote?.let { editorVm.openFloatingWindow(it.id) }
                },
                onExport         = { fmt ->
                    showEditorTray = false
                    activeNote?.let { ExportHelper.shareNote(context, it, fmt) }
                },
                onVersionHistory = {
                    showEditorTray = false
                    editorVm.flushContent(soraEditorRef?.text?.toString() ?: "")
                    onOpenHistory()
                },
                onShortcuts      = {
                    showEditorTray = false
                    onOpenShortcuts()
                },
                onGuide          = {
                    showEditorTray = false
                    onOpenGuide()
                },
                onSettings       = {
                    showEditorTray = false
                    onOpenSettings()
                },
            )
        }

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
        }
    } // end outer Box
}

// ── Floating Action Pills (Editor Top Bar Alternative) ───────────────────────

@Composable
private fun FloatingPillButton(
    icon: ImageVector,
    contentDescription: String,
    hazeState: dev.chrisbanes.haze.HazeState?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = frostedContainerColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .clip(CircleShape)
            .frostedFab(hazeState)
            .size(40.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FloatingWordCountPill(
    wordCount: Int,
    charCount: Int,
    deltaText: String?,
    isPositiveDelta: Boolean,
    pillMode: Int,
    onModeClick: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        AnimatedVisibility(
            visible = deltaText != null,
            enter = fadeIn() + slideInVertically { -10 },
            exit = fadeOut() + slideOutVertically { -10 }
        ) {
            Text(
                text = deltaText ?: "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositiveDelta) ScribeTheme.colors.semantic.success else ScribeTheme.colors.semantic.error,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Surface(
            shape = CircleShape,
            color = frostedContainerColor(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .clip(CircleShape)
                .frostedFab(hazeState)
                .height(40.dp)
                .clickable { onModeClick() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp)
            ) {
                AnimatedContent(
                    targetState = pillMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "word_count_transition"
                ) { mode ->
                    Text(
                        text = when (mode) {
                            1 -> "$wordCount w · $charCount c"
                            2 -> "$wordCount w · ${maxOf(1, wordCount / 200)}m"
                            else -> "$wordCount words"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Custom Frosted Bottom Tray for Editor ───────────────────────────────────────
@Composable
private fun EditorOptionsBottomSheet(
    noteTitle        : String,
    onDismiss        : () -> Unit,
    onEnterZen       : () -> Unit,
    onOpenFloating   : () -> Unit,
    onExport         : (String) -> Unit,
    onVersionHistory : () -> Unit,
    onShortcuts      : () -> Unit,
    onGuide          : () -> Unit,
    onSettings       : () -> Unit,
) {
    FrostedBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = noteTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Document & Editor Actions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick Mode Actions (Zen Mode & Floating Reference)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorTrayActionCard(
                    title = "Zen Mode",
                    subtitle = "Focus distraction-free",
                    icon = Icons.Default.Fullscreen,
                    modifier = Modifier.weight(1f),
                    onClick = onEnterZen
                )
                EditorTrayActionCard(
                    title = "Floating Window",
                    subtitle = "Pin as quick reference",
                    icon = Icons.Default.PictureInPicture,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFloating
                )
            }

            // Export Options Section
            Text(
                text = "EXPORT NOTE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.secondary,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "TXT" to "txt",
                    "Markdown" to "md",
                    "HTML" to "html",
                    "PDF" to "pdf"
                ).forEach { (label, format) ->
                    Surface(
                        onClick = { onExport(format) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (format) {
                                    "txt" -> Icons.Default.Description
                                    "md" -> Icons.Default.Code
                                    "html" -> Icons.Default.Language
                                    else -> Icons.Default.PictureAsPdf
                                },
                                contentDescription = null,
                                tint = ScribeTheme.colors.interaction.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Navigation & Preferences
            EditorTrayMenuItem(
                title = "Version History",
                subtitle = "Browse snapshots and restore edits",
                icon = Icons.Default.History,
                onClick = onVersionHistory
            )
            EditorTrayMenuItem(
                title = "Keyboard Shortcuts",
                subtitle = "Formatting keys and navigation helpers",
                icon = Icons.Default.Keyboard,
                onClick = onShortcuts
            )
            EditorTrayMenuItem(
                title = "User Guide",
                subtitle = "Quick manual and formatting tips",
                icon = Icons.AutoMirrored.Filled.Help,
                onClick = onGuide
            )
            EditorTrayMenuItem(
                title = "Settings & Appearance",
                subtitle = "Themes, fonts, and editor preferences",
                icon = Icons.Default.Settings,
                onClick = onSettings
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun EditorTrayActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = ScribeTheme.colors.interaction.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ScribeTheme.colors.interaction.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EditorTrayMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ScribeTheme.colors.interaction.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
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
    val registerBounds = LocalInteractiveBoundsRegistry.current
    DisposableEffect(Unit) {
        onDispose { registerBounds("word_count_pill", null) }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(pillOffsetX.roundToInt(), pillOffsetY.roundToInt()) }
            .padding(12.dp)
            .onGloballyPositioned { coords ->
                registerBounds("word_count_pill", coords.boundsInRoot())
            }
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
                    color      = if (isPositiveDelta) ScribeTheme.colors.semantic.success else ScribeTheme.colors.semantic.error,
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


@Composable
private fun FormatButton(
    label      : String,
    isSelected : Boolean = false,
    onClick    : () -> Unit
) {
    Surface(
        onClick      = onClick,
        shape        = CircleShape,
        color        = if (isSelected) ScribeTheme.colors.interaction.primary
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

