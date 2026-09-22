import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.view.inputmethod.InputMethodManager
import android.app.Activity
import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.ripple
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.data.Folder
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.DualTitleNoteDialog
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
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.ScribeDataStore
import com.primaloptima.scribe.ui.ornaments.OrnamentRegistry
import com.primaloptima.scribe.ui.ornaments.OrnamentPickerSheet

// ── Sora Editor imports ───────────────────────────────────────────────────────
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.primaloptima.scribe.ui.components.UnifiedCanvasLayout
import com.primaloptima.scribe.ui.components.ScribeCodeEditor
import kotlinx.coroutines.suspendCancellableCoroutine
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import com.primaloptima.scribe.util.ScribeProseLanguage
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.ui.theme.FontHelper
import io.github.rosemoe.sora.event.TextSizeChangeEvent


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
    onOpenThemes: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenSheets: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    // Dismiss keyboard immediately whenever the overflow menu opens
    LaunchedEffect(showEditorTray) {
        if (showEditorTray) {
            keyboardController?.hide()
            focusManager.clearFocus()
            try { soraEditorRef?.hideSoftInput() } catch (_: Exception) {}
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val windowToken = (context as? Activity)?.window?.decorView?.windowToken
                ?: soraEditorRef?.windowToken
            windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
        }
    }
    var activeTuningCategory by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeTuningCategory != null) {
        activeTuningCategory = null
    }

    val dataStore = remember { (context.applicationContext as? ScribeApp)?.dataStore ?: ScribeDataStore(context) }
    val selectedOrnamentId by dataStore.manuscriptOrnamentIdFlow.collectAsStateWithLifecycle("classic_diamond")
    var showOrnamentPicker by remember { mutableStateOf(false) }

    val anyDialogOpen = showRenameDialog || showCreateNoteDialog || showEditorTray
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(anyDialogOpen) { if (!anyDialogOpen) dialogOneShotBitmap = null }
    }

    val captureForDialog: suspend (() -> Unit) -> Unit = { openDialog ->
        openDialog()
    }

    // ── Sora CodeEditor & Unified Canvas state ──────────────────────────────────
    var unifiedCanvasRef   by remember { mutableStateOf<UnifiedCanvasLayout?>(null) }
    var soraEditorRef      by remember { mutableStateOf<CodeEditor?>(null) }
    var isHandleDragging   by remember { mutableStateOf(false) }
    var loadedNoteId       by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Floating Pills Scroll Animation & Dual-Title State ──────────────────────
    var floatingPillsVisible by rememberSaveable { mutableStateOf(true) }
    var scrollDistanceSinceDirectionChange by remember { mutableFloatStateOf(0f) }
    var lastScrollDirection by remember { mutableIntStateOf(0) }

    val hideOnScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = available.y
                if (dy < -2f) {
                    // Scrolling DOWN / reading forward into document (dy is negative, content moves up)
                    if (lastScrollDirection != 1) {
                        lastScrollDirection = 1
                        scrollDistanceSinceDirectionChange = 0f
                    }
                    scrollDistanceSinceDirectionChange += (-dy)
                    // Delay before hiding: allow ~60px of scroll before animating away
                    if (scrollDistanceSinceDirectionChange > 60f && floatingPillsVisible) {
                        floatingPillsVisible = false
                    }
                } else if (dy > 2f) {
                    // Scrolling UP / navigating back to top (dy is positive, content moves down)
                    if (lastScrollDirection != -1) {
                        lastScrollDirection = -1
                        scrollDistanceSinceDirectionChange = 0f
                    }
                    scrollDistanceSinceDirectionChange += dy
                    // Immediate animate in when scrolling back
                    if (!floatingPillsVisible) {
                        floatingPillsVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Dual-title inline editing state & persistence
    var primaryTitleText by remember(activeNote?.id) {
        val raw = activeNote?.name ?: ""
        mutableStateOf(raw.substringBefore('\n'))
    }
    var secondaryTitleText by remember(activeNote?.id) {
        val raw = activeNote?.name ?: ""
        mutableStateOf(if (raw.contains('\n')) raw.substringAfter('\n') else "")
    }
    var showSecondaryTitle by remember(activeNote?.id) {
        val raw = activeNote?.name ?: ""
        mutableStateOf(raw.contains('\n'))
    }

    fun persistDualTitle(primary: String, secondary: String) {
        val targetNote = activeNote ?: return
        val p = primary.trim()
        val s = secondary.trim()
        val combined = if (s.isNotEmpty()) {
            if (p.isNotEmpty()) "$p\n$s" else s
        } else {
            p
        }
        if (combined.isNotEmpty()) {
            bookVm.renameNote(targetNote.id, combined)
        }
    }

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
    val preloadedNote = remember(initialNoteId, currentBookNotes) {
        if (!initialNoteId.isNullOrEmpty()) {
            currentBookNotes.firstOrNull { it.id == initialNoteId }
                ?: noteListVm.notes.value.firstOrNull { it.id == initialNoteId }
        } else if (currentBookNotes.isNotEmpty()) {
            currentBookNotes.first()
        } else null
    }

    // Seed note synchronously on first composition pass if not yet set
    remember(initialNoteId, preloadedNote) {
        if (preloadedNote != null && editorVm.activeNote.value?.id != preloadedNote.id) {
            editorVm.preloadNote(preloadedNote)
        }
        true
    }

    LaunchedEffect(initialNoteId) {
        if (!initialNoteId.isNullOrEmpty()) editorVm.loadNote(initialNoteId, preloadedNote)
        else if (currentBookNotes.isNotEmpty()) editorVm.loadNote(currentBookNotes.first().id, currentBookNotes.first())
    }

    // FIX 3: Removed activeNote?.content from the LaunchedEffect key.
    // The guard condition `editor.text.length == 0 && note.content.isNotEmpty()` already
    // handles the edge case of an editor that exists but hasn't been filled yet.
    // Keying only on id + soraEditorRef is sufficient and far cheaper.
    LaunchedEffect(activeNote?.id, soraEditorRef) {
        val note   = activeNote ?: return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        if (loadedNoteId != note.id || (editor.text.length == 0 && note.content.isNotEmpty())) {
            // Ensure editor is measured and has a valid layout width before triggering wordwrap calculations.
            // Suspends until layout pass completes on the exact frame without any artificial delay.
            if (editor.width <= 0) {
                suspendCancellableCoroutine<Unit> { cont ->
                    var listener: android.view.View.OnLayoutChangeListener? = null
                    listener = android.view.View.OnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                        if (r - l > 0) {
                            v.removeOnLayoutChangeListener(listener)
                            if (cont.isActive) cont.resume(Unit) {}
                        }
                    }
                    editor.addOnLayoutChangeListener(listener)
                    cont.invokeOnCancellation {
                        editor.removeOnLayoutChangeListener(listener)
                    }
                }
            }

            loadedNoteId = note.id
            unifiedCanvasRef?.resetScroll()
            floatingPillsVisible = true

            // 1. Compute Inlay Hints synchronously so paragraph indents & scene badges are
            // already registered when text is set. Sora Editor measures character metrics
            // with indents already present, completely eliminating post-load text reflow and shifting!
            val hints = ProseInlayHintProvider.computeInlayHints(
                note.content,
                worldEntries,
                activeTheme?.paragraphSpacing ?: 14
            )

            // Prepare for new document: suppress raw rendering and await wordwrap calculation
            if (editor is ScribeCodeEditor) {
                editor.prepareForNewDocument()
            } else {
                editor.alpha = 0f
            }

            editor.setText(note.content)
            editor.setInlayHints(hints)

            if (note.content.isEmpty()) {
                editor.alpha = 1f
            } else if (editor is ScribeCodeEditor) {
                editor.notifyContentSet()
            } else {
                editor.animate().alpha(1f).setDuration(180).start()
            }

            ProseDiagnosticProvider.attachEditor(editor)

            // 2. Heavy prose diagnostics (clichés, passive voice, filter words) run asynchronously in the background.
            // Diagnostic squiggles do NOT alter character layout, font metrics, or line wraps.
            launch(Dispatchers.Default) {
                val diagnostics = ProseDiagnosticProvider.analyzeDiagnostics(note.content)
                withContext(Dispatchers.Main) {
                    if (loadedNoteId == note.id) {
                        editor.setDiagnostics(diagnostics)
                    }
                }
            }
        }
    }

    // Debounced analysis for Inlay Hints (Scene word counts, POV tags, Paragraph Indents) and Diagnostics
    var editorCurrentText by remember { mutableStateOf("") }
    LaunchedEffect(editorCurrentText, worldEntries, activeTheme?.paragraphSpacing) {
        if (editorCurrentText.isEmpty()) return@LaunchedEffect
        val editor = soraEditorRef ?: return@LaunchedEffect
        delay(400) // Debounce 400ms to keep editing fluid
        val hints = withContext(Dispatchers.Default) {
            ProseInlayHintProvider.computeInlayHints(
                editorCurrentText,
                worldEntries,
                activeTheme?.paragraphSpacing ?: 14
            )
        }
        editor.setInlayHints(hints)
        val diagnostics = withContext(Dispatchers.Default) {
            ProseDiagnosticProvider.analyzeDiagnostics(editorCurrentText)
        }
        editor.setDiagnostics(diagnostics)
    }

    // Immediate update when paragraphSpacing toggles in options or HUD
    LaunchedEffect(activeTheme?.paragraphSpacing) {
        val editor = soraEditorRef ?: return@LaunchedEffect
        val curText = editor.text?.toString() ?: ""
        if (curText.isEmpty()) return@LaunchedEffect
        val hints = ProseInlayHintProvider.computeInlayHints(
            curText,
            worldEntries,
            activeTheme?.paragraphSpacing ?: 14
        )
        editor.setInlayHints(hints)
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
            val isOverlayActive = (activeTheme?.overlayEnabled == true || (activeTheme?.overlayColor != null && bgOpacity > 0f)) && bgOpacity > 0f
            if (isOverlayActive) {
                val overlayTint = activeTheme?.overlayColor?.let { parseComposeColor(it, themeBgColor) } ?: themeBgColor
                Box(Modifier.fillMaxSize().background(overlayTint.copy(alpha = bgOpacity)))
            }
        }

        // ── Reusable Component Renderers ──────────────────────────────────────
        val renderEditorScaffold: @Composable (
            onNavClick: () -> Unit,
            onOpenRightPanel: () -> Unit,
            isLeftDrawerOpen: Boolean
        ) -> Unit = { onNavClick, onOpenRightPanel, isLeftDrawerOpen ->
            Scaffold(
                containerColor      = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                ) {
                    // Drive Sora's searcher from find state
                    LaunchedEffect(findQuery, showFindBar) {
                        val editor = soraEditorRef ?: return@LaunchedEffect
                        if (showFindBar && findQuery.isNotEmpty()) {
                            editor.searcher.search(findQuery, EditorSearcher.SearchOptions(true, false))
                        } else {
                            editor.searcher.stopSearch()
                        }
                    }

                    // ── Sora CodeEditor & Background Theme Setup ───────────────────────
                    val hasBgImageLocal     = !activeTheme?.backgroundImageUri.isNullOrEmpty()
                    val currentThemeBg      = MaterialTheme.colorScheme.background
                    val editorTextSizeSp    = remember(activeTheme?.fontSize) {
                        (activeTheme?.fontSize ?: 18).toFloat()
                    }
                    val editorTypeface      = remember(activeTheme?.fontFamily, activeTheme?.documentFontWeight) {
                        val baseTf = activeTheme?.fontFamily?.let { ThemeManager.resolveTypeface(context, it) } ?: android.graphics.Typeface.DEFAULT
                        val weight = activeTheme?.documentFontWeight ?: 400
                        if (Build.VERSION.SDK_INT >= 28) {
                            android.graphics.Typeface.create(baseTf, weight, false)
                        } else {
                            if (weight >= 600) android.graphics.Typeface.create(baseTf, android.graphics.Typeface.BOLD)
                            else android.graphics.Typeface.create(baseTf, android.graphics.Typeface.NORMAL)
                        }
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

                    // ── Unified Continuous Document Canvas ─────────────────────────
                    val docTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val docBottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val docBottomPadding = if (isKeyboardVisible) 0.dp else docBottomNavInset

                    var lastAppliedPadding by remember { mutableFloatStateOf(-1f) }
                    var lastAppliedTextSize by remember { mutableFloatStateOf(-1f) }
                    var lastAppliedTypeface by remember { mutableStateOf<android.graphics.Typeface?>(null) }
                    var lastAppliedLineSpacing by remember { mutableFloatStateOf(-1f) }
                    var lastAppliedParaSpacing by remember { mutableFloatStateOf(-1f) }
                    var lastAppliedBgArgb by remember { mutableIntStateOf(0) }
                    var lastAppliedThemeId by remember { mutableStateOf<String?>(null) }

                    AndroidView(
                        factory = { ctx ->
                            val density = ctx.resources.displayMetrics.density
                            val initialPadH = (activeTheme?.paddingHorizontal ?: 28).toFloat()
                            val initialLineHeight = activeTheme?.lineHeight ?: 1.7f
                            val initialParaSpacing = (activeTheme?.paragraphSpacing ?: 14).toFloat()
                            val initialParaSpacingPx = initialParaSpacing * density * 0.35f

                            lastAppliedPadding = initialPadH
                            lastAppliedTextSize = editorTextSizeSp
                            lastAppliedTypeface = editorTypeface
                            lastAppliedLineSpacing = initialLineHeight
                            lastAppliedParaSpacing = initialParaSpacing
                            lastAppliedBgArgb = bgArgb
                            lastAppliedThemeId = activeTheme?.id

                            UnifiedCanvasLayout(ctx).apply {
                                horizontalPaddingDp = initialPadH
                                setBackgroundColor(bgArgb)
                                onScrollDelta = { dy ->
                                    if (dy > 2f) {
                                        if (lastScrollDirection != 1) {
                                            lastScrollDirection = 1
                                            scrollDistanceSinceDirectionChange = 0f
                                        }
                                        scrollDistanceSinceDirectionChange += dy
                                        if (scrollDistanceSinceDirectionChange > 60f && floatingPillsVisible) {
                                            floatingPillsVisible = false
                                        }
                                    } else if (dy < -2f) {
                                        if (lastScrollDirection != -1) {
                                            lastScrollDirection = -1
                                            scrollDistanceSinceDirectionChange = 0f
                                        }
                                        scrollDistanceSinceDirectionChange += (-dy)
                                        if (!floatingPillsVisible) {
                                            floatingPillsVisible = true
                                        }
                                    }
                                }
                                onUnifiedScrollChanged = { scrollD, _ ->
                                    if (scrollD <= 5 && editor.offsetY <= 10) {
                                        floatingPillsVisible = true
                                    }
                                }
                                headerView.setViewCompositionStrategy(
                                    ViewCompositionStrategy.DisposeOnDetachedFromWindow
                                )
                                editor.apply {
                                    setBackgroundColor(bgArgb)
                                    setTextSize(editorTextSizeSp)
                                    editorTypeface?.let { typefaceText = it }
                                    setLineSpacing(initialParaSpacingPx, initialLineHeight)
                                    activeTheme?.let { theme ->
                                        val scheme = ScribeColorScheme(theme)
                                        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,       bgArgb)
                                        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgArgb)
                                        scheme.setColor(EditorColorScheme.LINE_NUMBER,            bgArgb)
                                        colorScheme = scheme
                                    }
                                    isLineNumberEnabled    = false
                                    isHighlightCurrentLine = false
                                    isWordwrap             = true
                                    isScalable             = true
                                    setScaleTextSizes(12f * density, 36f * density)
                                    onPinchScaleEndListener = { finalSp ->
                                        if (finalSp != (activeTheme?.fontSize ?: 18)) {
                                            lastAppliedTextSize = finalSp.toFloat()
                                            editorVm.updateActiveTheme { it.copy(fontSize = finalSp) }
                                        }
                                    }
                                    subscribeEvent(TextSizeChangeEvent::class.java) { event, _ ->
                                        if (!isPinchScaling) {
                                            val newSp = (event.newTextSize / density).roundToInt().coerceIn(12, 36)
                                            if (newSp != (activeTheme?.fontSize ?: 18)) {
                                                lastAppliedTextSize = newSp.toFloat()
                                                editorVm.updateActiveTheme { it.copy(fontSize = newSp) }
                                            }
                                        }
                                    }
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
                                        unifiedCanvasRef?.ensureCursorVisibleAboveKeyboard()
                                    }
                                    setOnFocusChangeListener { _, hasFocus ->
                                        if (hasFocus) {
                                            unifiedCanvasRef?.ensureCursorVisibleAboveKeyboard()
                                        }
                                    }
                                    try {
                                        setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                                            val dy = scrollY - oldScrollY
                                            if (scrollY <= 10 && scrollD == 0) {
                                                floatingPillsVisible = true
                                            } else if (dy > 20 && floatingPillsVisible) {
                                                floatingPillsVisible = false
                                            } else if (dy < -10 && !floatingPillsVisible) {
                                                floatingPillsVisible = true
                                            }
                                        }
                                    } catch (_: Throwable) { }
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
                                }
                            }.also {
                                unifiedCanvasRef = it
                                soraEditorRef = it.editor
                            }
                        },
                        update = { layout ->
                            val density = layout.context.resources.displayMetrics.density
                            val padH = (activeTheme?.paddingHorizontal ?: 28).toFloat()
                            if (kotlin.math.abs(lastAppliedPadding - padH) > 0.5f) {
                                lastAppliedPadding = padH
                                layout.horizontalPaddingDp = padH
                            }
                            val editor = layout.editor
                            val scribeEditor = editor as? com.primaloptima.scribe.ui.components.ScribeCodeEditor
                            if (scribeEditor?.isPinchScaling != true && kotlin.math.abs(lastAppliedTextSize - editorTextSizeSp) > 0.1f) {
                                lastAppliedTextSize = editorTextSizeSp
                                editor.setTextSize(editorTextSizeSp)
                            }
                            if (lastAppliedTypeface !== editorTypeface && editorTypeface != null) {
                                lastAppliedTypeface = editorTypeface
                                editor.typefaceText = editorTypeface
                            }
                            val newLineHeight = activeTheme?.lineHeight ?: 1.7f
                            val newParaSpacing = (activeTheme?.paragraphSpacing ?: 14).toFloat()
                            val paraSpacingPx = newParaSpacing * density * 0.35f
                            if (kotlin.math.abs(lastAppliedLineSpacing - newLineHeight) > 0.01f ||
                                kotlin.math.abs(lastAppliedParaSpacing - newParaSpacing) > 0.5f) {
                                lastAppliedLineSpacing = newLineHeight
                                lastAppliedParaSpacing = newParaSpacing
                                editor.setLineSpacing(paraSpacingPx, newLineHeight)
                            }
                            if (lastAppliedBgArgb != bgArgb) {
                                lastAppliedBgArgb = bgArgb
                                editor.setBackgroundColor(bgArgb)
                                layout.setBackgroundColor(bgArgb)
                            }
                            activeTheme?.let { theme ->
                                if (lastAppliedThemeId != theme.id) {
                                    lastAppliedThemeId = theme.id
                                    val scheme = ScribeColorScheme(theme)
                                    scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,       bgArgb)
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bgArgb)
                                    scheme.setColor(EditorColorScheme.LINE_NUMBER,            bgArgb)
                                    editor.colorScheme = scheme
                                }
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

                            // Update Header inside ComposeView
                            layout.headerView.setContent {
                                if (!zenMode && activeNote != null) {
                                    val resolvedTitleFont = FontHelper.getFontFamily(activeTheme?.titleFontFamily ?: activeTheme?.fontFamily ?: "default")
                                    val pTitleSize = (activeTheme?.title1FontSize ?: 18).sp
                                    val sTitleSize = (activeTheme?.title2FontSize ?: 24).sp
                                    val pTitleWeight = FontWeight(activeTheme?.title1FontWeight ?: 600)
                                    val sTitleWeight = FontWeight(activeTheme?.title2FontWeight ?: 700)
                                    val pTitleLineHeight = ((activeTheme?.title1LineHeight ?: 1.35f) * (activeTheme?.title1FontSize ?: 18)).sp
                                    val sTitleLineHeight = ((activeTheme?.title2LineHeight ?: 1.30f) * (activeTheme?.title2FontSize ?: 24)).sp
                                    val pTitleColor = activeTheme?.primaryTitleColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                        ?: activeTheme?.colors?.headingText?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                    val sTitleColor = activeTheme?.secondaryTitleColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                        ?: activeTheme?.colors?.text?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

                                    ManuscriptHeader(
                                        primaryTitleText = primaryTitleText,
                                        secondaryTitleText = secondaryTitleText,
                                        selectedOrnamentId = selectedOrnamentId,
                                        showSecondaryTitle = showSecondaryTitle,
                                        titleAlignment = activeTheme?.titleAlignment ?: "center",
                                        horizontalPadding = (activeTheme?.paddingHorizontal ?: 28).dp,
                                        primaryTitleColor = pTitleColor,
                                        secondaryTitleColor = sTitleColor,
                                        titleFontFamily = resolvedTitleFont,
                                        primaryTitleFontSize = pTitleSize,
                                        secondaryTitleFontSize = sTitleSize,
                                        primaryTitleFontWeight = pTitleWeight,
                                        secondaryTitleFontWeight = sTitleWeight,
                                        primaryTitleLineHeight = pTitleLineHeight,
                                        secondaryTitleLineHeight = sTitleLineHeight,
                                        onPrimaryTitleChange = { sanitized ->
                                            primaryTitleText = sanitized
                                            persistDualTitle(sanitized, secondaryTitleText)
                                        },
                                        onSecondaryTitleChange = { sanitized ->
                                            secondaryTitleText = sanitized
                                            persistDualTitle(primaryTitleText, sanitized)
                                        },
                                        onOrnamentClick = { showOrnamentPicker = true },
                                        onMoveToSecondaryTitle = {
                                            showSecondaryTitle = true
                                        },
                                        onDone = {
                                            soraEditorRef?.let { ed ->
                                                ed.setSelection(0, 0)
                                                unifiedCanvasRef?.resetScroll()
                                                ed.requestFocus()
                                            }
                                        },
                                        onBackspaceEmptySecondary = {
                                            showSecondaryTitle = false
                                            persistDualTitle(primaryTitleText, "")
                                        },
                                        onEnterInSecondary = {
                                            soraEditorRef?.let { ed ->
                                                ed.setSelection(0, 0)
                                                unifiedCanvasRef?.resetScroll()
                                                ed.requestFocus()
                                            }
                                        },
                                        isEditable = true
                                    )
                                }
                            }
                        },
                        onRelease = { layout ->
                            soraEditorRef = null
                            unifiedCanvasRef = null
                            ProseDiagnosticProvider.attachEditor(null)
                            layout.editor.release()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = docTopInset, bottom = docBottomPadding)
                    )

                    // ── Zen Mode Exit FAB ──────────────────────────────────────────
                    if (zenMode) {
                        CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                            ScribeSingleFab(
                                icon               = Icons.Default.FullscreenExit,
                                contentDescription = "Exit Zen",
                                modifier           = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                onClick            = { editorVm.setZen(false) }
                            )
                        }
                    }

                    // ── Find/Replace bar (Fixed overlay at top) ────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    ) {
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
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
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

                                // Top-Right: Actions Group (Save Checkpoint, Menu)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            try { soraEditorRef?.hideSoftInput() } catch (_: Exception) {}
                                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                            val windowToken = (context as? Activity)?.window?.decorView?.windowToken
                                                ?: soraEditorRef?.windowToken
                                            windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
                                            showEditorTray = true
                                        }
                                    )
                                }
                            }
                        }

                        // ── Draggable Floating Word Counter Pill (Always Visible) ──
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 54.dp, end = 16.dp)
                        ) {
                            CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
                                WordCountPill(
                                    modifier        = Modifier,
                                    pillOffsetX     = pillOffsetX,
                                    pillOffsetY     = pillOffsetY,
                                    onOffsetChange  = { dx, dy ->
                                        pillOffsetX += dx
                                        pillOffsetY += dy
                                    },
                                    pillMode        = pillMode,
                                    onModeClick     = { pillMode = (pillMode + 1) % 3 },
                                    wordCount       = wordCount,
                                    charCount       = charCount,
                                    deltaText       = deltaText,
                                    isPositiveDelta = isPositiveDelta,
                                    hazeState       = hazeState,
                                )
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
                activeTheme      = activeTheme,
                onUpdateTheme    = { transform -> editorVm.updateActiveTheme(transform) },
                onDismiss        = { showEditorTray = false },
                onOpenTuning     = { category ->
                    showEditorTray = false
                    activeTuningCategory = category
                },
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
                onOpenThemes     = {
                    showEditorTray = false
                    onOpenThemes()
                },
                onSettings       = {
                    showEditorTray = false
                    onOpenSettings()
                },
            )
        }

        val curActiveTheme = activeTheme
        if (activeTuningCategory != null && curActiveTheme != null) {
            EditorLiveTuningHud(
                initialCategory = activeTuningCategory ?: "text",
                activeTheme     = curActiveTheme,
                onUpdateTheme   = { transform -> editorVm.updateActiveTheme(transform) },
                onBackToMenu    = {
                    activeTuningCategory = null
                    showEditorTray = true
                },
                onClose         = {
                    activeTuningCategory = null
                },
                modifier        = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        CompositionLocalProvider(LocalOneShotBitmap provides dialogOneShotBitmap) {
            if (showRenameDialog && activeNote != null) {
                val noteToRename = activeNote
                val p = noteToRename?.name?.substringBefore('\n') ?: ""
                val s = if (noteToRename?.name?.contains('\n') == true) noteToRename.name.substringAfter('\n') else ""
                DualTitleNoteDialog(
                    dialogTitle = "Rename Note",
                    confirmButtonText = "Rename",
                    initialPrimary = p,
                    initialSecondary = s,
                    onDismiss = { showRenameDialog = false },
                    onConfirm = { updatedName ->
                        if (noteToRename != null) bookVm.renameNote(noteToRename.id, updatedName)
                        showRenameDialog = false
                    }
                )
            }

            if (showCreateNoteDialog) {
                DualTitleNoteDialog(
                    dialogTitle = "New Note",
                    confirmButtonText = "Create",
                    onDismiss = { showCreateNoteDialog = false },
                    onConfirm = { fullName ->
                        bookVm.createNote(fullName) { id ->
                            showCreateNoteDialog = false
                            editorVm.loadNote(id)
                        }
                    }
                )
            }

            if (showOrnamentPicker) {
                OrnamentPickerSheet(
                    selectedId = selectedOrnamentId,
                    onSelect = { newId ->
                        scope.launch {
                            dataStore.setManuscriptOrnamentId(newId)
                        }
                    },
                    onDismiss = { showOrnamentPicker = false }
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
        shadowElevation = ScribeTheme.metrics.elevationLow,
        modifier = modifier
            .clip(CircleShape)
            .frostedFab(hazeState)
            .size(ScribeTheme.metrics.touchTargetCompact)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ScribeTheme.metrics.iconNormal)
            )
        }
    }
}

// ── Custom Frosted Bottom Tray for Editor ───────────────────────────────────────
@Composable
private fun EditorOptionsBottomSheet(
    noteTitle        : String,
    activeTheme      : com.primaloptima.scribe.util.model.AppTheme?,
    onUpdateTheme    : ((com.primaloptima.scribe.util.model.AppTheme) -> com.primaloptima.scribe.util.model.AppTheme) -> Unit,
    onDismiss        : () -> Unit,
    onOpenTuning     : (String) -> Unit,
    onEnterZen       : () -> Unit,
    onOpenFloating   : () -> Unit,
    onExport         : (String) -> Unit,
    onVersionHistory : () -> Unit,
    onShortcuts      : () -> Unit,
    onGuide          : () -> Unit,
    onOpenThemes     : () -> Unit,
    onSettings       : () -> Unit,
) {
    var showExportOptions by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Dismiss keyboard immediately when bottom sheet appears
    LaunchedEffect(Unit) {
        keyboardController?.hide()
        focusManager.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val windowToken = (context as? Activity)?.window?.decorView?.windowToken
        windowToken?.let { imm?.hideSoftInputFromWindow(it, 0) }
    }

    FrostedBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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

            // Row 1: 4 Compact Boxes (History, Shortcuts, Guide, Export)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorTactileBox(
                    title = "History",
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                    onClick = onVersionHistory
                )
                EditorTactileBox(
                    title = "Shortcuts",
                    icon = Icons.Default.Keyboard,
                    modifier = Modifier.weight(1f),
                    onClick = onShortcuts
                )
                EditorTactileBox(
                    title = "Guide",
                    icon = Icons.AutoMirrored.Filled.Help,
                    modifier = Modifier.weight(1f),
                    onClick = onGuide
                )
                EditorTactileBox(
                    title = "Export",
                    icon = Icons.Default.Share,
                    modifier = Modifier.weight(1f),
                    onClick = { showExportOptions = !showExportOptions }
                )
            }

            // Collapsible Export Options Section
            AnimatedVisibility(
                visible = showExportOptions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        text = "CHOOSE EXPORT FORMAT",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ScribeTheme.colors.content.secondary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "TXT" to "txt",
                            "Markdown" to "md",
                            "HTML" to "html",
                            "PDF" to "pdf"
                        ).forEach { (label, format) ->
                            EditorTactileBox(
                                title = label,
                                icon = when (format) {
                                    "txt" -> Icons.Default.Description
                                    "md" -> Icons.Default.Code
                                    "html" -> Icons.Default.Language
                                    else -> Icons.Default.PictureAsPdf
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.weight(1f),
                                onClick = { onExport(format) }
                            )
                        }
                    }
                }
            }

            // Row 2: 4 Compact Boxes (Typography, Colors, Themes, Settings)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorTactileBox(
                    title = "Typography",
                    icon = Icons.Default.TextFields,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenTuning("text") }
                )
                EditorTactileBox(
                    title = "Colors",
                    icon = Icons.Default.Palette,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenTuning("colors") }
                )
                EditorTactileBox(
                    title = "Themes",
                    icon = Icons.Default.Style,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenThemes
                )
                EditorTactileBox(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = onSettings
                )
            }

            // Row 3: Quick Mode Actions (Zen Mode & Floating)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorTactileBox(
                    title = "Zen Mode",
                    icon = Icons.Default.Fullscreen,
                    modifier = Modifier.weight(1f),
                    onClick = onEnterZen
                )
                EditorTactileBox(
                    title = "Floating",
                    icon = Icons.Default.PictureInPicture,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFloating
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Compact Live-Tuning HUD (Zero-Scrim Overlay for Direct Visual Feedback) ─────
@Composable
private fun EditorLiveTuningHud(
    initialCategory : String,
    activeTheme     : com.primaloptima.scribe.util.model.AppTheme,
    onUpdateTheme   : ((com.primaloptima.scribe.util.model.AppTheme) -> com.primaloptima.scribe.util.model.AppTheme) -> Unit,
    onBackToMenu    : () -> Unit,
    onClose         : () -> Unit,
    modifier        : Modifier = Modifier
) {
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var typographyTool by remember { mutableStateOf("size") }
    var typographyTarget by remember { mutableStateOf("document") }
    var alignmentTarget by remember { mutableStateOf("document") }
    var colorRole by remember { mutableStateOf("title1") }
    var customHexInput by remember { mutableStateOf("") }
    var isEditingHex by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val animOffsetY = remember { Animatable(0f) }
    var hudHeightPx by remember { mutableFloatStateOf(600f) }

    val dismissWithAnimation: () -> Unit = {
        coroutineScope.launch {
            animOffsetY.animateTo(
                targetValue = hudHeightPx,
                animationSpec = tween(durationMillis = 180)
            )
            onClose()
        }
    }

    BackHandler(enabled = true) {
        dismissWithAnimation()
    }

    val draggableState = rememberDraggableState { delta ->
        if (delta > 0 || animOffsetY.value > 0f) {
            coroutineScope.launch {
                animOffsetY.snapTo((animOffsetY.value + delta).coerceAtLeast(0f))
            }
        }
    }

    val onDragStoppedAction: suspend (Float) -> Unit = { velocity ->
        if (animOffsetY.value > 120f || velocity > 650f) {
            dismissWithAnimation()
        } else {
            animOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                hudHeightPx = coordinates.size.height.toFloat()
            }
            .graphicsLayer {
                translationY = animOffsetY.value
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { v -> coroutineScope.launch { onDragStoppedAction(v) } }
            )
            .navigationBarsPadding(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Drag handle with touch gesture support
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> coroutineScope.launch { onDragStoppedAction(v) } }
                    )
                    .padding(top = 2.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }

            // Top Bar: [< Back] • Title & Category Toggle • [X Close]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToMenu,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (category == "text") "Typography HUD" else "Colors HUD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Category Toggle Pills: [ Text | Colors ]
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("text" to "Text", "colors" to "Colors").forEach { (catKey, catLabel) ->
                        val isSelected = category == catKey
                        Surface(
                            onClick = { category = catKey },
                            shape = CircleShape,
                            color = if (isSelected) ScribeTheme.colors.interaction.primary else Color.Transparent,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = catLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = dismissWithAnimation,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close HUD",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Sub-Tool Strip & Controls
            if (category == "text") {
                // Typography Tools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "size" to "Size",
                        "font" to "Font",
                        "weight" to "Weight",
                        "margins" to "Margins",
                        "line" to "Line Spacing",
                        "para" to "Paragraph",
                        "align" to "Alignment"
                    ).forEach { (toolKey, toolLabel) ->
                        val isSelected = typographyTool == toolKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { typographyTool = toolKey },
                            label = { Text(toolLabel, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // Target Selector when applicable
                if (typographyTool in listOf("size", "font", "weight", "line")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Scope:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScribeTheme.colors.content.secondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "document" to "Document",
                                "title1" to "Title 1",
                                "title2" to "Title 2"
                            ).forEach { (tKey, tLabel) ->
                                val isSelected = typographyTarget == tKey
                                Surface(
                                    onClick = { typographyTarget = tKey },
                                    shape = ScribeTheme.shapes.button,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (typographyTool == "align") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Scope:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ScribeTheme.colors.content.secondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "document" to "Document Text",
                                "titles" to "Titles"
                            ).forEach { (tKey, tLabel) ->
                                val isSelected = alignmentTarget == tKey
                                Surface(
                                    onClick = { alignmentTarget = tKey },
                                    shape = ScribeTheme.shapes.button,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Tool Control UI
                when (typographyTool) {
                    "size" -> {
                        val currentVal = when (typographyTarget) {
                            "title1" -> (activeTheme.title1FontSize ?: 18).toFloat()
                            "title2" -> (activeTheme.title2FontSize ?: 24).toFloat()
                            else -> activeTheme.fontSize.toFloat()
                        }
                        val minRange = if (typographyTarget == "title2") 14f else if (typographyTarget == "title1") 12f else 10f
                        val maxRange = if (typographyTarget == "title2") 56f else if (typographyTarget == "title1") 48f else 36f

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (typographyTarget) {
                                        "title1" -> "Title 1 (Chapter) Size"
                                        "title2" -> "Title 2 (Main Title) Size"
                                        else -> "Document Prose Size"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${currentVal.roundToInt()} sp",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = currentVal.coerceIn(minRange, maxRange),
                                onValueChange = { newVal ->
                                    val rounded = newVal.roundToInt()
                                    when (typographyTarget) {
                                        "title1" -> onUpdateTheme { it.copy(title1FontSize = rounded) }
                                        "title2" -> onUpdateTheme { it.copy(title2FontSize = rounded) }
                                        else -> onUpdateTheme { it.copy(fontSize = rounded) }
                                    }
                                },
                                valueRange = minRange..maxRange
                            )
                        }
                    }
                    "font" -> {
                        val activeFont = when (typographyTarget) {
                            "title1", "title2" -> activeTheme.titleFontFamily ?: activeTheme.fontFamily
                            else -> activeTheme.fontFamily
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = when (typographyTarget) {
                                    "title1" -> "Title 1 Font Family"
                                    "title2" -> "Title 2 Font Family"
                                    else -> "Document Font Family"
                                },
                                fontSize = 11.5.sp,
                                color = ScribeTheme.colors.content.secondary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FontHelper.fontOptions.forEach { opt ->
                                    val isSelected = activeFont.equals(opt.key, ignoreCase = true) ||
                                            (opt.key == "default" && (activeFont.isEmpty() || activeFont == "default" || activeFont == "sans"))
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            when (typographyTarget) {
                                                "title1", "title2" -> onUpdateTheme { it.copy(titleFontFamily = opt.key) }
                                                else -> onUpdateTheme { it.copy(fontFamily = opt.key) }
                                            }
                                        },
                                        label = { Text(opt.name, fontSize = 11.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                    "weight" -> {
                        val currentWeight = when (typographyTarget) {
                            "title1" -> activeTheme.title1FontWeight ?: 600
                            "title2" -> activeTheme.title2FontWeight ?: 700
                            else -> activeTheme.documentFontWeight
                        }
                        val weightLabels = mapOf(
                            300 to "Light (300)",
                            400 to "Regular (400)",
                            500 to "Medium (500)",
                            600 to "SemiBold (600)",
                            700 to "Bold (700)",
                            800 to "ExtraBold (800)"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (typographyTarget) {
                                        "title1" -> "Title 1 Weight"
                                        "title2" -> "Title 2 Weight"
                                        else -> "Document Font Weight"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = weightLabels[currentWeight] ?: "$currentWeight",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(300, 400, 500, 600, 700, 800).forEach { w ->
                                    val isSelected = currentWeight == w
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            when (typographyTarget) {
                                                "title1" -> onUpdateTheme { it.copy(title1FontWeight = w) }
                                                "title2" -> onUpdateTheme { it.copy(title2FontWeight = w) }
                                                else -> onUpdateTheme { it.copy(documentFontWeight = w) }
                                            }
                                        },
                                        label = { Text(weightLabels[w] ?: "$w", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                    "margins" -> {
                        val currentPadding = activeTheme.paddingHorizontal.toFloat().coerceIn(8f, 72f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Canvas Side Margins", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${currentPadding.roundToInt()} dp", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = currentPadding,
                                onValueChange = { newVal ->
                                    val rounded = newVal.roundToInt()
                                    if (rounded != activeTheme.paddingHorizontal) {
                                        onUpdateTheme { it.copy(paddingHorizontal = rounded) }
                                    }
                                },
                                valueRange = 8f..72f
                            )
                        }
                    }
                    "line" -> {
                        val currentVal = when (typographyTarget) {
                            "title1" -> activeTheme.title1LineHeight ?: 1.35f
                            "title2" -> activeTheme.title2LineHeight ?: 1.30f
                            else -> activeTheme.lineHeight
                        }.coerceIn(1.0f, 2.4f)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (typographyTarget) {
                                        "title1" -> "Title 1 Line Height"
                                        "title2" -> "Title 2 Line Height"
                                        else -> "Prose Line Spacing"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format("%.2fx", currentVal),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = currentVal,
                                onValueChange = { newVal ->
                                    val rounded = (newVal * 20).roundToInt() / 20f
                                    if (kotlin.math.abs(rounded - currentVal) >= 0.04f) {
                                        when (typographyTarget) {
                                            "title1" -> onUpdateTheme { it.copy(title1LineHeight = rounded) }
                                            "title2" -> onUpdateTheme { it.copy(title2LineHeight = rounded) }
                                            else -> onUpdateTheme { it.copy(lineHeight = rounded) }
                                        }
                                    }
                                },
                                valueRange = 1.0f..2.4f
                            )
                        }
                    }
                    "para" -> {
                        val currentVal = (activeTheme.paragraphSpacing ?: 0).toFloat().coerceIn(0f, 40f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paragraph Block Spacing", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${currentVal.roundToInt()} dp", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = currentVal,
                                onValueChange = { newVal ->
                                    val rounded = newVal.roundToInt()
                                    if (rounded != (activeTheme.paragraphSpacing ?: 0)) {
                                        onUpdateTheme { it.copy(paragraphSpacing = rounded) }
                                    }
                                },
                                valueRange = 0f..40f
                            )
                        }
                    }
                    "align" -> {
                        if (alignmentTarget == "document") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "left" to "Left",
                                    "justified" to "Justified",
                                    "center" to "Center"
                                ).forEach { (key, label) ->
                                    val isSelected = (activeTheme.textAlignment.ifEmpty { "left" }).equals(key, ignoreCase = true)
                                    Surface(
                                        onClick = { onUpdateTheme { it.copy(textAlignment = key) } },
                                        shape = ScribeTheme.shapes.button,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "left" to "Left",
                                    "center" to "Center",
                                    "right" to "Right"
                                ).forEach { (key, label) ->
                                    val isSelected = (activeTheme.titleAlignment.ifEmpty { "center" }).equals(key, ignoreCase = true)
                                    Surface(
                                        onClick = { onUpdateTheme { it.copy(titleAlignment = key) } },
                                        shape = ScribeTheme.shapes.button,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Colors Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "title1" to "Title 1",
                        "title2" to "Title 2",
                        "prose" to "Prose",
                        "dialogue" to "Dialogue",
                        "thoughts" to "Thoughts",
                        "headings" to "Headings"
                    ).forEach { (rKey, rLabel) ->
                        val isSelected = colorRole == rKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                colorRole = rKey
                                isEditingHex = false
                            },
                            label = { Text(rLabel, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                val currentHex = when (colorRole) {
                    "title1" -> activeTheme.primaryTitleColor ?: activeTheme.colors?.headingText ?: "#0F172A"
                    "title2" -> activeTheme.secondaryTitleColor ?: activeTheme.colors?.text ?: "#0F172A"
                    "prose" -> activeTheme.colors?.text ?: "#0F172A"
                    "dialogue" -> activeTheme.overrides?.dialogueText ?: activeTheme.colors?.dialogueText ?: "#0D9488"
                    "thoughts" -> activeTheme.overrides?.monologueText ?: activeTheme.colors?.monologueText ?: "#7C3AED"
                    "headings" -> activeTheme.overrides?.headingText ?: activeTheme.colors?.headingText ?: "#B45309"
                    else -> "#0F172A"
                }

                val applyColor = { hex: String ->
                    when (colorRole) {
                        "title1" -> onUpdateTheme { it.copy(primaryTitleColor = hex) }
                        "title2" -> onUpdateTheme { it.copy(secondaryTitleColor = hex) }
                        "prose" -> onUpdateTheme { ThemeManager.updateFoundationColors(it, newText = hex) }
                        "dialogue" -> onUpdateTheme { ThemeManager.updateSemanticOverride(it, "dialogueText", hex) }
                        "thoughts" -> onUpdateTheme { ThemeManager.updateSemanticOverride(it, "monologueText", hex) }
                        "headings" -> onUpdateTheme { ThemeManager.updateSemanticOverride(it, "headingText", hex) }
                    }
                }

                val swatches = listOf(
                    "#0F172A", "#334155", "#475569", "#64748B",
                    "#F8FAFC", "#F1F5F9", "#FFFFFF",
                    "#B45309", "#78350F", "#D97706",
                    "#059669", "#047857", "#0D9488",
                    "#1D4ED8", "#4F46E5", "#3730A3",
                    "#7C3AED", "#6D28D9", "#8B5CF6",
                    "#DC2626", "#B91C1C", "#EA580C"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current: $currentHex",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            customHexInput = currentHex
                            isEditingHex = !isEditingHex
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (isEditingHex) "Hide Hex" else "Custom Hex", fontSize = 11.sp)
                    }
                }

                if (isEditingHex) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customHexInput,
                            onValueChange = { input ->
                                customHexInput = input
                                if (input.startsWith("#") && (input.length == 7 || input.length == 9)) {
                                    runCatching {
                                        android.graphics.Color.parseColor(input)
                                        applyColor(input)
                                    }
                                }
                            },
                            singleLine = true,
                            label = { Text("Hex Color (e.g. #D97706)", fontSize = 10.5.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        Button(
                            onClick = {
                                val formatted = if (customHexInput.startsWith("#")) customHexInput else "#$customHexInput"
                                runCatching {
                                    android.graphics.Color.parseColor(formatted)
                                    applyColor(formatted)
                                    isEditingHex = false
                                }
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Apply", fontSize = 11.5.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    swatches.forEach { hex ->
                        val swatchColor = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        val isSelected = currentHex.equals(hex, ignoreCase = true)
                        Surface(
                            onClick = { applyColor(hex) },
                            shape = CircleShape,
                            color = swatchColor,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (hex == "#FFFFFF" || hex.startsWith("#F")) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tactile compact option box with an icon, concise text label, and subtle directional
 * shadow underneath and to the side (bottom & right) for a modern elevated feel.
 */
@Composable
private fun EditorTactileBox(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
    contentColor: Color = ScribeTheme.colors.interaction.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val isDark = LocalAppTheme.current?.isDark == true
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0xFF0F172A).copy(alpha = 0.10f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Box(
        modifier = modifier
            .drawBehind {
                val cornerPx = 12.dp.toPx()
                // Directional tactile shadow: thin, crisp below and to the right side
                drawRoundRect(
                    color = shadowColor,
                    topLeft = Offset(1.5.dp.toPx(), 2.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    Surface(shadowElevation = ScribeTheme.metrics.elevationMedium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(ScribeTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = findQuery,
                onValueChange = onFindChange,
                placeholder   = { Text("Find") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(ScribeTheme.metrics.fieldHeightCompact)
            )
            Spacer(Modifier.width(ScribeTheme.spacing.compact))
            OutlinedTextField(
                value         = replaceQuery,
                onValueChange = onReplaceChange,
                placeholder   = { Text("Replace") },
                singleLine    = true,
                modifier      = Modifier.weight(1f).height(ScribeTheme.metrics.fieldHeightCompact)
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
@Composable
fun WordCountPill(
    modifier        : Modifier = Modifier,
    pillOffsetX     : Float = 0f,
    pillOffsetY     : Float = 0f,
    onOffsetChange  : (Float, Float) -> Unit = { _, _ -> },
    pillMode        : Int = 0,
    onModeClick     : () -> Unit = {},
    wordCount       : Int = 1420,
    charCount       : Int = 7850,
    deltaText       : String? = null,
    isPositiveDelta : Boolean = true,
    hazeState       : dev.chrisbanes.haze.HazeState? = LocalHazeState.current,
) {
    val registerBounds = LocalInteractiveBoundsRegistry.current
    DisposableEffect(Unit) {
        onDispose { registerBounds("word_count_pill", null) }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(pillOffsetX.roundToInt(), pillOffsetY.roundToInt()) }
            .onGloballyPositioned { coords ->
                registerBounds("word_count_pill", coords.boundsInRoot())
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = deltaText != null,
                enter   = fadeIn() + slideInVertically { -10 },
                exit    = fadeOut() + slideOutVertically { -10 }
            ) {
                Text(
                    text       = deltaText ?: "",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (isPositiveDelta) ScribeTheme.colors.analytics.positive else ScribeTheme.colors.analytics.negative,
                    modifier   = Modifier.padding(bottom = 2.dp)
                )
            }
            Surface(
                shape           = CircleShape,
                color           = frostedContainerColor(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                tonalElevation  = 0.dp,
                shadowElevation = ScribeTheme.metrics.elevationLow,
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(ScribeTheme.metrics.chipHeightCompact)
                        .padding(horizontal = 10.dp, vertical = ScribeTheme.spacing.hairline)
                ) {
                    AnimatedContent(
                        targetState    = pillMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label          = "word_count_transition"
                    ) { mode ->
                        Text(
                            text = when (mode) {
                                1    -> "$wordCount w · $charCount c"
                                2    -> "$wordCount w · ${maxOf(1, wordCount / 200)}m"
                                else -> "$wordCount words"
                            },
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines   = 1
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun FormatButton(
    label      : String,
    isSelected : Boolean = false,
    onClick    : () -> Unit = {}
) {
    Surface(
        onClick      = onClick,
        shape        = CircleShape,
        color        = if (isSelected) ScribeTheme.colors.interaction.primary
                       else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier     = Modifier.height(ScribeTheme.metrics.chipHeight)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.padding(horizontal = ScribeTheme.spacing.medium, vertical = ScribeTheme.spacing.micro)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Canonical Manuscript Header ───────────────────────────────────────────────

@Composable
fun ManuscriptHeader(
    primaryTitleText: String,
    secondaryTitleText: String,
    selectedOrnamentId: String = "classic_flourish",
    showSecondaryTitle: Boolean = secondaryTitleText.isNotEmpty(),
    titleAlignment: String = "center",
    horizontalPadding: androidx.compose.ui.unit.Dp = 28.dp,
    primaryTitleColor: Color? = null,
    secondaryTitleColor: Color? = null,
    titleFontFamily: FontFamily? = null,
    primaryTitleFontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    secondaryTitleFontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
    primaryTitleFontWeight: FontWeight = FontWeight.SemiBold,
    secondaryTitleFontWeight: FontWeight = FontWeight.Bold,
    primaryTitleLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    secondaryTitleLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    onPrimaryTitleChange: ((String) -> Unit)? = null,
    onSecondaryTitleChange: ((String) -> Unit)? = null,
    onOrnamentClick: (() -> Unit)? = null,
    onMoveToSecondaryTitle: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onBackspaceEmptySecondary: (() -> Unit)? = null,
    onEnterInSecondary: (() -> Unit)? = null,
    isEditable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hAlign = when (titleAlignment) {
        "left" -> Alignment.Start
        "right" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val tAlign = when (titleAlignment) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }
    val boxContentAlign = when (titleAlignment) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val primaryColor = primaryTitleColor ?: MaterialTheme.colorScheme.primary
    val secondaryColor = secondaryTitleColor ?: MaterialTheme.colorScheme.onBackground

    if (isEditable) {
        val localPrimaryFocus = remember { FocusRequester() }
        val localSecondaryFocus = remember { FocusRequester() }
        var secondaryFocusTrigger by remember { mutableIntStateOf(0) }

        val moveToSecondary: () -> Unit = {
            onMoveToSecondaryTitle?.invoke()
            secondaryFocusTrigger++
        }

        Column(
            horizontalAlignment = hAlign,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, top = 56.dp, end = horizontalPadding, bottom = 12.dp)
        ) {
            // Primary Title / Kicker (e.g., CHAPTER I)
            BasicTextField(
                value = primaryTitleText,
                onValueChange = { input ->
                    if (input.contains('\n')) {
                        val sanitized = input.replace("\n", "").trimEnd()
                        onPrimaryTitleChange?.invoke(sanitized)
                        moveToSecondary()
                    } else {
                        onPrimaryTitleChange?.invoke(input)
                    }
                },
                singleLine = false,
                maxLines = 4,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = primaryColor,
                    fontWeight = primaryTitleFontWeight,
                    fontSize = primaryTitleFontSize,
                    fontFamily = titleFontFamily,
                    lineHeight = if (primaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) primaryTitleLineHeight else MaterialTheme.typography.titleMedium.lineHeight,
                    textAlign = tAlign,
                    letterSpacing = 2.5.sp
                ),
                cursorBrush = SolidColor(primaryColor),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onNext = { moveToSecondary() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(localPrimaryFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            moveToSecondary()
                            true
                        } else false
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = boxContentAlign
                    ) {
                        if (primaryTitleText.isEmpty()) {
                            Text(
                                text = "CHAPTER / TITLE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    fontWeight = primaryTitleFontWeight,
                                    fontSize = primaryTitleFontSize,
                                    fontFamily = titleFontFamily,
                                    lineHeight = if (primaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) primaryTitleLineHeight else MaterialTheme.typography.titleMedium.lineHeight,
                                    textAlign = tAlign,
                                    letterSpacing = 2.5.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Main Title (e.g., The Starlit Archive)
            if (showSecondaryTitle || secondaryTitleText.isNotEmpty()) {
                LaunchedEffect(secondaryFocusTrigger) {
                    if (secondaryFocusTrigger > 0) {
                        withFrameNanos { }
                        try {
                            localSecondaryFocus.requestFocus()
                        } catch (_: Exception) { }
                    }
                }
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = secondaryTitleText,
                    onValueChange = { input ->
                        if (input.contains('\n')) {
                            val sanitized = input.replace("\n", "").trimEnd()
                            onSecondaryTitleChange?.invoke(sanitized)
                            onEnterInSecondary?.invoke()
                        } else {
                            onSecondaryTitleChange?.invoke(input)
                        }
                    },
                    singleLine = false,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = secondaryColor,
                        fontWeight = secondaryTitleFontWeight,
                        fontSize = secondaryTitleFontSize,
                        fontFamily = titleFontFamily,
                        lineHeight = if (secondaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) secondaryTitleLineHeight else MaterialTheme.typography.headlineMedium.lineHeight,
                        textAlign = tAlign
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onDone?.invoke() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(localSecondaryFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Backspace && secondaryTitleText.isEmpty() && event.type == KeyEventType.KeyUp) {
                                onBackspaceEmptySecondary?.invoke()
                                localPrimaryFocus.requestFocus()
                                true
                            } else if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                onEnterInSecondary?.invoke()
                                true
                            } else false
                        },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = boxContentAlign
                        ) {
                            if (secondaryTitleText.isEmpty()) {
                                Text(
                                    text = "Manuscript Title (Optional)",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        fontWeight = secondaryTitleFontWeight,
                                        fontSize = secondaryTitleFontSize,
                                        fontFamily = titleFontFamily,
                                        lineHeight = if (secondaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) secondaryTitleLineHeight else MaterialTheme.typography.headlineMedium.lineHeight,
                                        textAlign = tAlign
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Extensible Vector Manuscript Ornament Divider
            val currentOrnament = remember(selectedOrnamentId) {
                OrnamentRegistry.getById(selectedOrnamentId)
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ScribeTheme.shapes.button)
                    .clickable { onOrnamentClick?.invoke() }
                    .padding(vertical = 4.dp),
                contentAlignment = boxContentAlign
            ) {
                currentOrnament.Render(
                    tint = primaryColor.copy(alpha = 0.5f),
                    modifier = Modifier
                )
            }
        }
    } else {
        // Pure display mode for live theme preview and non-editing views
        Column(
            horizontalAlignment = hAlign,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, top = 24.dp, end = horizontalPadding, bottom = 8.dp)
        ) {
            if (primaryTitleText.isNotEmpty()) {
                Text(
                    text = primaryTitleText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = primaryColor,
                        fontWeight = primaryTitleFontWeight,
                        fontSize = primaryTitleFontSize,
                        fontFamily = titleFontFamily,
                        lineHeight = if (primaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) primaryTitleLineHeight else MaterialTheme.typography.titleMedium.lineHeight,
                        textAlign = tAlign,
                        letterSpacing = 2.sp
                    ),
                    textAlign = tAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (secondaryTitleText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = secondaryTitleText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = secondaryColor,
                        fontWeight = secondaryTitleFontWeight,
                        fontSize = secondaryTitleFontSize,
                        fontFamily = titleFontFamily,
                        lineHeight = if (secondaryTitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) secondaryTitleLineHeight else MaterialTheme.typography.headlineMedium.lineHeight,
                        textAlign = tAlign
                    ),
                    textAlign = tAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val currentOrnament = remember(selectedOrnamentId) {
                OrnamentRegistry.getById(selectedOrnamentId)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = boxContentAlign
            ) {
                currentOrnament.Render(
                    tint = primaryColor.copy(alpha = 0.5f),
                    modifier = Modifier
                )
            }
        }
    }
}

// ── Shortcut Bar Composable ───────────────────────────────────────────────────

@Composable
fun EditorShortcutBar(
    shortcuts: List<String> = listOf("B", "I", "H1", "H2", "“ ”", "—", "•"),
    onShortcutClick: (String) -> Unit = {},
    hazeState: dev.chrisbanes.haze.HazeState? = LocalHazeState.current,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .frostedBar(hazeState)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        shortcuts.forEach { label ->
            FormatButton(label = label) {
                onShortcutClick(label)
            }
        }
    }
}

// ── Canonical Prose Body Preview ──────────────────────────────────────────────

@Composable
fun EditorProsePreviewBody(
    modifier: Modifier = Modifier
) {
    val proseStyle = ScribeTheme.typography.prose
    val dialogueStyle = ScribeTheme.typography.dialogue
    val monologueStyle = ScribeTheme.typography.monologue
    val headingStyle = ScribeTheme.typography.heading
    val colors = ScribeTheme.colors.writing

    val align = when (ScribeTheme.typography.editor.textAlignment) {
        "justified" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Left
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((ScribeTheme.typography.editor.paragraphSpacing * 12).dp.coerceAtLeast(6.dp))
    ) {
        Text(
            text = "The morning mist clung to the cobblestones of Aethelgard like silver breath. Far below, the obsidian gates creaked against the rising wind.",
            style = proseStyle.copy(color = colors.prose, textAlign = align),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "“We must reach the high pass before the eclipse,” she murmured.",
            style = dialogueStyle.copy(color = colors.dialogue, textAlign = align),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "If the garrison falls, neither iron nor prayer will hold the eastern breach.",
            style = monologueStyle.copy(color = colors.monologue, textAlign = align),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "I. The Runes of the Archway",
            style = headingStyle.copy(color = colors.heading, textAlign = align),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )

        Text(
            text = "A faint amber luminescence pulsed along the ancient lintel—a warning they could no longer afford to ignore.",
            style = proseStyle.copy(color = colors.prose, textAlign = align),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Canonical Editor Content for Live Previews & Modularity ───────────────────

/**
 * Decoupled content composable representing Scribe's canonical Editor UI.
 *
 * Renders the top bar, manuscript header with extensible ornaments, typography tokens
 * (prose, dialogue, monologue, heading), floating word count pill, and shortcut bar.
 *
 * Decoupled from ViewModel or Room persistence, for direct use in live theme previews.
 */
@Composable
fun EditorContent(
    primaryTitle: String = "CHAPTER VII",
    secondaryTitle: String = "The Obsidian Gate",
    selectedOrnamentId: String = "classic_flourish",
    wordCount: Int = 1420,
    charCount: Int = 7850,
    deltaText: String? = "+340 today",
    isPositiveDelta: Boolean = true,
    showTopBar: Boolean = false,
    showWordCountPill: Boolean = true,
    showShortcutBar: Boolean = false,
    hazeState: dev.chrisbanes.haze.HazeState? = LocalHazeState.current,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showTopBar) {
                ScribeEditorTopBar(
                    title = secondaryTitle.ifEmpty { primaryTitle },
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavClick = {},
                    actions = listOf(
                        ScribeBarAction(Icons.Default.Search, "Search") {},
                        ScribeBarAction(Icons.Default.BookmarkAdd, "Bookmark") {},
                        ScribeBarAction(Icons.Default.MoreVert, "More") {}
                    )
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    ManuscriptHeader(
                        primaryTitleText = primaryTitle,
                        secondaryTitleText = secondaryTitle,
                        selectedOrnamentId = selectedOrnamentId,
                        isEditable = false
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    EditorProsePreviewBody()

                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (showWordCountPill) {
                    WordCountPill(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 12.dp),
                        pillOffsetX = 0f,
                        pillOffsetY = 0f,
                        onOffsetChange = { _, _ -> },
                        pillMode = 0,
                        onModeClick = {},
                        wordCount = wordCount,
                        charCount = charCount,
                        deltaText = deltaText,
                        isPositiveDelta = isPositiveDelta,
                        hazeState = hazeState
                    )
                }
            }

            if (showShortcutBar) {
                EditorShortcutBar(hazeState = hazeState)
            }
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

