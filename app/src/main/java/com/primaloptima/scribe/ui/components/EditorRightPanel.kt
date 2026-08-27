package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.primaloptima.scribe.R
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.engine.ProseAnalysisResult
import com.primaloptima.scribe.ui.components.ProseAnalysisView
import com.primaloptima.scribe.ui.screens.LocalInteractiveBoundsRegistry
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.frostedPanel
import com.primaloptima.scribe.ui.theme.localHasBgImage
import com.primaloptima.scribe.util.model.*
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SpatialDropZone {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

private data class DetachedDragState(
    val originSlot: String,
    val note: Note,
    val initialSlotOriginX: Float,
    val initialSlotOriginY: Float,
    val initialTouchInContainerX: Float,
    val initialTouchInContainerY: Float,
    val currentDragX: Float,
    val currentDragY: Float,
    val initialSlotWidth: Float,
    val initialSlotHeight: Float,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorRightPanel(
    rightPanelTab       : Int,
    onTabChange         : (Int) -> Unit,
    workbenchState      : WorkbenchState = WorkbenchState(),
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    outline             : List<OutlineEntry>,
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
    proseAnalysis       : ProseAnalysisResult,
    soraEditorRef       : CodeEditor?,
    tabBarAtBottom      : Boolean,
    onToggleTabBarPos   : () -> Unit,
    onUpdatePane        : (paneId: String, transform: (PaneConfig) -> PaneConfig) -> Unit = { _, _ -> },
    onUpdateWorkbench   : (transform: (WorkbenchState) -> WorkbenchState) -> Unit = {},
    onAddPane           : (scope: PaneScope) -> Unit = {},
    onRemovePane        : (paneId: String) -> Unit = {},
    onDuplicatePane     : (paneId: String) -> Unit = {},
    onMinimizePane      : (paneId: String, by: MinimizedBy) -> Unit = { _, _ -> },
    onRestorePane       : (paneId: String) -> Unit = {},
    onPinNote           : (paneId: String, noteId: String) -> Unit = { _, _ -> },
    onUnpinNote         : (paneId: String, noteId: String) -> Unit = { _, _ -> },
    onReorderNote       : (paneId: String, fromIndex: Int, toIndex: Int) -> Unit = { _, _, _ -> },
    onCreateNote        : (paneId: String, title: String, content: String) -> Unit = { _, _, _ -> },
    onSaveNoteContent   : (noteId: String, content: String) -> Unit = { _, _ -> },
    onLoadNote          : (noteId: String) -> Unit = {},
    onClose             : () -> Unit,
    barBlurBitmap       : Bitmap?,
    hazeState           : dev.chrisbanes.haze.HazeState,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    // Session scope overrides for out-of-scope panes restored temporarily
    var sessionScopeOverrides by remember { mutableStateOf(setOf<String>()) }

    // Tray and Mode states
    var contextTrayExpanded by remember { mutableStateOf(false) }
    var removeMode by remember { mutableStateOf(false) }

    // Modals & Dialogs
    var showAddSectionSheet by remember { mutableStateOf(false) }
    var showWorkbenchSettingsSheet by remember { mutableStateOf(false) }
    var restoreTargetPane by remember { mutableStateOf<PaneConfig?>(null) }
    var slotSwapReplaceTarget by remember { mutableStateOf<PaneConfig?>(null) }
    var removeCandidatePane by remember { mutableStateOf<PaneConfig?>(null) }
    var edgeTabLongPressPane by remember { mutableStateOf<PaneConfig?>(null) }
    var showEdgeTabGroupPopup by remember { mutableStateOf<List<PaneConfig>?>(null) }

    // Focus mode state
    var focusedPaneId by remember { mutableStateOf<String?>(null) }
    var focusEditMode by remember { mutableStateOf(false) }
    var focusEditorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var originalContentForFocus by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val visiblePanes = remember(workbenchState.panes) { workbenchState.panes.filter { !it.isMinimized } }
    val userMinimizedPanes = remember(workbenchState.panes) { workbenchState.panes.filter { it.isMinimized && it.minimizedBy == MinimizedBy.USER } }
    val systemMinimizedPanes = remember(workbenchState.panes) { workbenchState.panes.filter { it.isMinimized && it.minimizedBy == MinimizedBy.SYSTEM } }

    // System-minimized notification snackbar (show only once per pane)
    LaunchedEffect(workbenchState.panes) {
        val unnotified = workbenchState.panes.firstOrNull { it.isMinimized && it.minimizedBy == MinimizedBy.SYSTEM && !it.systemMinimizedNoticeShown }
        if (unnotified != null) {
            onUpdatePane(unnotified.id) { it.copy(systemMinimizedNoticeShown = true) }
            snackbarHostState.showSnackbar("'${unnotified.label}' is available. Tap the edge tab to restore it.")
        }
    }

    val isPaneInCurrentScope: (PaneConfig) -> Boolean = { pane ->
        if (sessionScopeOverrides.contains(pane.id)) true
        else {
            val active = activeNote
            if (active == null) true
            else {
                val matches: (PaneScope) -> Boolean = { s ->
                    when (s) {
                        is PaneScope.Global -> true
                        is PaneScope.Book -> s.id == (active.bookId.ifBlank { "default" })
                        is PaneScope.Folder -> s.id == (active.folderPath.ifBlank { "/" })
                        is PaneScope.File -> s.id == active.id
                    }
                }
                matches(pane.primaryScope) || pane.secondaryScopes.any { matches(it) }
            }
        }
    }

    val handleRestorePane: (PaneConfig) -> Unit = { pane ->
        val inScope = isPaneInCurrentScope(pane)
        fun executeRestore() {
            if (visiblePanes.size >= workbenchState.maxSlots) {
                slotSwapReplaceTarget = pane
            } else {
                onRestorePane(pane.id)
            }
        }

        if (inScope) {
            executeRestore()
        } else {
            when (workbenchState.outOfScopeDefault) {
                OutOfScopeDefault.SESSION_ONLY -> {
                    sessionScopeOverrides = sessionScopeOverrides + pane.id
                    executeRestore()
                }
                OutOfScopeDefault.ALWAYS_ADD -> {
                    val fScope = PaneScope.File(id = activeNote?.id ?: "note", title = activeNote?.name ?: "Note")
                    onUpdatePane(pane.id) { it.copy(secondaryScopes = it.secondaryScopes + fScope) }
                    executeRestore()
                }
                OutOfScopeDefault.ALWAYS_ASK -> {
                    restoreTargetPane = pane
                }
            }
        }
    }

    val handleDuplicate: (String) -> Unit = { paneId ->
        val activePanesCount = workbenchState.panes.count { !it.isMinimized }
        if (activePanesCount < workbenchState.maxSlots) {
            onDuplicatePane(paneId)
            scope.launch { snackbarHostState.showSnackbar("Section duplicated") }
        } else {
            scope.launch { snackbarHostState.showSnackbar("No empty slot. Minimize a section first.") }
        }
    }

    val tabBarContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            PillTab(label = "Shelf",   selected = rightPanelTab == 0, onClick = { onTabChange(0) })
            PillTab(label = "Outline", selected = rightPanelTab == 1, onClick = { onTabChange(1) })
            PillTab(label = "Prose",   selected = rightPanelTab == 2, onClick = { onTabChange(2) })
        }
    }

    CompositionLocalProvider(LocalOneShotBitmap provides barBlurBitmap) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .frostedPanel(hazeState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                if (!tabBarAtBottom) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor             = Color.Transparent,
                            titleContentColor          = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary,
                            actionIconContentColor     = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.frostedBar(hazeState),
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                            }
                        },
                        title = { tabBarContent() },
                        actions = {
                            IconButton(onClick = onToggleTabBarPos) {
                                Icon(Icons.Default.VerticalAlignBottom, "Move tabs to bottom",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (rightPanelTab) {
                        0 -> {
                            val gapDp = 3.dp
                            val solidSurface = LocalSolidSurface.current
                            val hasBgImage = localHasBgImage()
                            val registerBounds = LocalInteractiveBoundsRegistry.current

                            Row(modifier = Modifier.fillMaxSize()) {
                                // ── Left Edge Rail (User Minimized) ─────────────────────
                                EdgeTabRail(
                                    minimizedPanes = userMinimizedPanes,
                                    side = EdgeSide.LEFT,
                                    isDark = isDark,
                                    onTap = { handleRestorePane(it) },
                                    onLongPress = { edgeTabLongPressPane = it },
                                    onOpenGroup = { showEdgeTabGroupPopup = it }
                                )

                                // ── Center Workbench Surface ───────────────────────────
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                                    ) {
                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                        val totalPxFloat = with(density) {
                                            if (workbenchState.splitHorizontal) maxWidth.toPx() else maxHeight.toPx()
                                        }

                                        var dragState by remember { mutableStateOf<DetachedDragState?>(null) }
                                        var activeHoveredZone by remember { mutableStateOf<SpatialDropZone?>(null) }
                                        var containerPositionInRoot by remember { mutableStateOf(Offset.Zero) }
                                        var pinnedContainerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

                                        DisposableEffect(dragState != null, pinnedContainerBounds) {
                                            if (dragState != null && pinnedContainerBounds != null) {
                                                registerBounds("pinned_notes_detached_drag", pinnedContainerBounds)
                                            }
                                            onDispose {
                                                registerBounds("pinned_notes_detached_drag", null)
                                            }
                                        }

                                        val containerWidthPx = with(density) { maxWidth.toPx() }
                                        val containerHeightPx = with(density) { maxHeight.toPx() }

                                        fun computeHoveredZone(touchX: Float, touchY: Float): SpatialDropZone? {
                                            if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null
                                            val relX = (touchX / containerWidthPx).coerceIn(0f, 1f)
                                            val relY = (touchY / containerHeightPx).coerceIn(0f, 1f)

                                            return when {
                                                relY < 0.28f -> SpatialDropZone.TOP
                                                relY > 0.72f -> SpatialDropZone.BOTTOM
                                                relX < 0.50f -> SpatialDropZone.LEFT
                                                else -> SpatialDropZone.RIGHT
                                            }
                                        }

                                        val onStartDetachedDrag: (slot: String, note: Note, touchPosInHeader: Offset, size: androidx.compose.ui.geometry.Size, slotPosInRoot: Offset) -> Unit = { slot, note, touchPosInHeader, size, slotPosInRoot ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val slotOriginInContainer = slotPosInRoot - containerPositionInRoot
                                            val touchInContainer = slotOriginInContainer + touchPosInHeader

                                            dragState = DetachedDragState(
                                                originSlot = slot,
                                                note = note,
                                                initialSlotOriginX = slotOriginInContainer.x,
                                                initialSlotOriginY = slotOriginInContainer.y,
                                                initialTouchInContainerX = touchInContainer.x,
                                                initialTouchInContainerY = touchInContainer.y,
                                                currentDragX = 0f,
                                                currentDragY = 0f,
                                                initialSlotWidth = size.width,
                                                initialSlotHeight = size.height,
                                            )
                                            activeHoveredZone = computeHoveredZone(touchInContainer.x, touchInContainer.y)
                                        }

                                        val onContinueDetachedDrag: (dx: Float, dy: Float) -> Unit = { dx, dy ->
                                            dragState?.let { current ->
                                                val newDragX = current.currentDragX + dx
                                                val newDragY = current.currentDragY + dy
                                                dragState = current.copy(
                                                    currentDragX = newDragX,
                                                    currentDragY = newDragY
                                                )
                                                val currentTouchX = current.initialTouchInContainerX + newDragX
                                                val currentTouchY = current.initialTouchInContainerY + newDragY
                                                val newZone = computeHoveredZone(currentTouchX, currentTouchY)
                                                if (newZone != activeHoveredZone) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    activeHoveredZone = newZone
                                                }
                                            }
                                        }

                                        val onEndDetachedDrag: () -> Unit = {
                                            val currentDrag = dragState
                                            val targetZone = activeHoveredZone
                                            if (currentDrag != null && targetZone != null) {
                                                when (targetZone) {
                                                    SpatialDropZone.TOP, SpatialDropZone.BOTTOM -> {
                                                        onUpdateWorkbench { it.copy(splitHorizontal = false) }
                                                    }
                                                    SpatialDropZone.LEFT, SpatialDropZone.RIGHT -> {
                                                        onUpdateWorkbench { it.copy(splitHorizontal = true) }
                                                    }
                                                }
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            dragState = null
                                            activeHoveredZone = null
                                        }

                                        val onCancelDetachedDrag: () -> Unit = {
                                            dragState = null
                                            activeHoveredZone = null
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .onGloballyPositioned { layoutCoordinates ->
                                                    pinnedContainerBounds = layoutCoordinates.boundsInRoot()
                                                    containerPositionInRoot = layoutCoordinates.positionInRoot()
                                                }
                                        ) {
                                            // ── 1-4 Pane Layout Engine ────────────────────────
                                            when {
                                                visiblePanes.isEmpty() -> {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Icon(
                                                                Icons.Default.VerticalSplit,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                                modifier = Modifier.size(40.dp)
                                                            )
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                "All sections minimized",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                "Tap an edge tab to restore a section",
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                        }
                                                    }
                                                }
                                                visiblePanes.size == 1 -> {
                                                    val pane = visiblePanes[0]
                                                    PinnedNoteSlot(
                                                        modifier = Modifier.fillMaxSize().padding(gapDp),
                                                        pane = pane,
                                                        slotKey = pane.id,
                                                        pinnedIds = pane.pinnedNoteIds,
                                                        pinnedIndex = pane.currentIndex,
                                                        allNotes = allNotes,
                                                        worldEntries = worldEntries,
                                                        activeTheme = activeTheme,
                                                        activeNote = activeNote,
                                                        accentColor = accentColor,
                                                        removeMode = removeMode,
                                                        onRemoveClick = { removeCandidatePane = pane },
                                                        onPrev = { onUpdatePane(pane.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                        onNext = { onUpdatePane(pane.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                        onSelectIndex = { idx -> onUpdatePane(pane.id) { it.copy(currentIndex = idx) } },
                                                        onSwitch = {},
                                                        onEdit = { id -> onLoadNote(id) },
                                                        onRemove = { id -> onUnpinNote(pane.id, id) },
                                                        onPick = {},
                                                        onUpdatePane = { transform -> onUpdatePane(pane.id, transform) },
                                                        onDuplicatePane = { handleDuplicate(pane.id) },
                                                        onMinimizePane = { onMinimizePane(pane.id, MinimizedBy.USER) },
                                                        onFocusPane = { focusedPaneId = pane.id },
                                                        onUnpinNote = { noteId -> onUnpinNote(pane.id, noteId) },
                                                        onReorderNote = { from, to -> onReorderNote(pane.id, from, to) },
                                                        onCreateNote = { title, content -> onCreateNote(pane.id, title, content) },
                                                        onPinNotes = { ids -> ids.forEach { onPinNote(pane.id, it) } },
                                                        hazeState = hazeState,
                                                        isDetached = dragState?.originSlot == pane.id,
                                                        onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                            onStartDetachedDrag(pane.id, note, touchPos, size, slotPosInRoot)
                                                        },
                                                        onDetachedDrag = onContinueDetachedDrag,
                                                        onEndDetachedDrag = onEndDetachedDrag,
                                                        onCancelDetachedDrag = onCancelDetachedDrag,
                                                    )
                                                }
                                                visiblePanes.size == 2 -> {
                                                    val pane1 = visiblePanes[0]
                                                    val pane2 = visiblePanes[1]
                                                    if (workbenchState.splitHorizontal) {
                                                        Row(
                                                            modifier = Modifier.fillMaxSize().padding(gapDp),
                                                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                                                        ) {
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(splitFraction),
                                                                pane = pane1,
                                                                slotKey = pane1.id,
                                                                pinnedIds = pane1.pinnedNoteIds,
                                                                pinnedIndex = pane1.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = pane1 },
                                                                onPrev = { onUpdatePane(pane1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(pane1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(pane1.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(pane1.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(pane1.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(pane1.id) },
                                                                onMinimizePane = { onMinimizePane(pane1.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = pane1.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(pane1.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(pane1.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(pane1.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(pane1.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == pane1.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(pane1.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                            SplitDivider(
                                                                isHorizontal = true,
                                                                onDrag = { delta ->
                                                                    splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                                                },
                                                                onSwap = {
                                                                    onUpdateWorkbench { wb ->
                                                                        val p = wb.panes.toMutableList()
                                                                        val idx1 = p.indexOfFirst { it.id == pane1.id }
                                                                        val idx2 = p.indexOfFirst { it.id == pane2.id }
                                                                        if (idx1 != -1 && idx2 != -1) {
                                                                            val tmp = p[idx1]
                                                                            p[idx1] = p[idx2]
                                                                            p[idx2] = tmp
                                                                        }
                                                                        wb.copy(panes = p)
                                                                    }
                                                                },
                                                                accentColor = accentColor,
                                                                hazeState = hazeState,
                                                            )
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(1f - splitFraction),
                                                                pane = pane2,
                                                                slotKey = pane2.id,
                                                                pinnedIds = pane2.pinnedNoteIds,
                                                                pinnedIndex = pane2.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = pane2 },
                                                                onPrev = { onUpdatePane(pane2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(pane2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(pane2.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(pane2.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(pane2.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(pane2.id) },
                                                                onMinimizePane = { onMinimizePane(pane2.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = pane2.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(pane2.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(pane2.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(pane2.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(pane2.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == pane2.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(pane2.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                        }
                                                    } else {
                                                        Column(
                                                            modifier = Modifier.fillMaxSize().padding(gapDp),
                                                            verticalArrangement = Arrangement.spacedBy(0.dp)
                                                        ) {
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxWidth().weight(splitFraction),
                                                                pane = pane1,
                                                                slotKey = pane1.id,
                                                                pinnedIds = pane1.pinnedNoteIds,
                                                                pinnedIndex = pane1.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = pane1 },
                                                                onPrev = { onUpdatePane(pane1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(pane1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(pane1.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(pane1.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(pane1.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(pane1.id) },
                                                                onMinimizePane = { onMinimizePane(pane1.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = pane1.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(pane1.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(pane1.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(pane1.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(pane1.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == pane1.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(pane1.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                            SplitDivider(
                                                                isHorizontal = false,
                                                                onDrag = { delta ->
                                                                    splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                                                },
                                                                onSwap = {
                                                                    onUpdateWorkbench { wb ->
                                                                        val p = wb.panes.toMutableList()
                                                                        val idx1 = p.indexOfFirst { it.id == pane1.id }
                                                                        val idx2 = p.indexOfFirst { it.id == pane2.id }
                                                                        if (idx1 != -1 && idx2 != -1) {
                                                                            val tmp = p[idx1]
                                                                            p[idx1] = p[idx2]
                                                                            p[idx2] = tmp
                                                                        }
                                                                        wb.copy(panes = p)
                                                                    }
                                                                },
                                                                accentColor = accentColor,
                                                                hazeState = hazeState,
                                                            )
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxWidth().weight(1f - splitFraction),
                                                                pane = pane2,
                                                                slotKey = pane2.id,
                                                                pinnedIds = pane2.pinnedNoteIds,
                                                                pinnedIndex = pane2.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = pane2 },
                                                                onPrev = { onUpdatePane(pane2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(pane2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(pane2.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(pane2.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(pane2.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(pane2.id) },
                                                                onMinimizePane = { onMinimizePane(pane2.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = pane2.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(pane2.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(pane2.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(pane2.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(pane2.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == pane2.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(pane2.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                        }
                                                    }
                                                }
                                                visiblePanes.size == 3 -> {
                                                    val p0 = visiblePanes[0]
                                                    val p1 = visiblePanes[1]
                                                    val p2 = visiblePanes[2]
                                                    Row(
                                                        modifier = Modifier.fillMaxSize().padding(gapDp),
                                                        horizontalArrangement = Arrangement.spacedBy(gapDp)
                                                    ) {
                                                        PinnedNoteSlot(
                                                            modifier = Modifier.fillMaxHeight().weight(0.5f),
                                                            pane = p0,
                                                            slotKey = p0.id,
                                                            pinnedIds = p0.pinnedNoteIds,
                                                            pinnedIndex = p0.currentIndex,
                                                            allNotes = allNotes,
                                                            worldEntries = worldEntries,
                                                            activeTheme = activeTheme,
                                                            activeNote = activeNote,
                                                            accentColor = accentColor,
                                                            removeMode = removeMode,
                                                            onRemoveClick = { removeCandidatePane = p0 },
                                                            onPrev = { onUpdatePane(p0.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                            onNext = { onUpdatePane(p0.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                            onSelectIndex = { idx -> onUpdatePane(p0.id) { it.copy(currentIndex = idx) } },
                                                            onSwitch = {},
                                                            onEdit = { id -> onLoadNote(id) },
                                                            onRemove = { id -> onUnpinNote(p0.id, id) },
                                                            onPick = {},
                                                            onUpdatePane = { transform -> onUpdatePane(p0.id, transform) },
                                                            onDuplicatePane = { handleDuplicate(p0.id) },
                                                            onMinimizePane = { onMinimizePane(p0.id, MinimizedBy.USER) },
                                                            onFocusPane = { focusedPaneId = p0.id },
                                                            onUnpinNote = { noteId -> onUnpinNote(p0.id, noteId) },
                                                            onReorderNote = { from, to -> onReorderNote(p0.id, from, to) },
                                                            onCreateNote = { title, content -> onCreateNote(p0.id, title, content) },
                                                            onPinNotes = { ids -> ids.forEach { onPinNote(p0.id, it) } },
                                                            hazeState = hazeState,
                                                            isDetached = dragState?.originSlot == p0.id,
                                                            onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                onStartDetachedDrag(p0.id, note, touchPos, size, slotPosInRoot)
                                                            },
                                                            onDetachedDrag = onContinueDetachedDrag,
                                                            onEndDetachedDrag = onEndDetachedDrag,
                                                            onCancelDetachedDrag = onCancelDetachedDrag,
                                                        )
                                                        Column(
                                                            modifier = Modifier.fillMaxHeight().weight(0.5f),
                                                            verticalArrangement = Arrangement.spacedBy(gapDp)
                                                        ) {
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                                pane = p1,
                                                                slotKey = p1.id,
                                                                pinnedIds = p1.pinnedNoteIds,
                                                                pinnedIndex = p1.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p1 },
                                                                onPrev = { onUpdatePane(p1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p1.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p1.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p1.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p1.id) },
                                                                onMinimizePane = { onMinimizePane(p1.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p1.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p1.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p1.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p1.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p1.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p1.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(p1.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                                pane = p2,
                                                                slotKey = p2.id,
                                                                pinnedIds = p2.pinnedNoteIds,
                                                                pinnedIndex = p2.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p2 },
                                                                onPrev = { onUpdatePane(p2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p2.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p2.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p2.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p2.id) },
                                                                onMinimizePane = { onMinimizePane(p2.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p2.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p2.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p2.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p2.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p2.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p2.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(p2.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    val p0 = visiblePanes[0]
                                                    val p1 = visiblePanes[1]
                                                    val p2 = visiblePanes[2]
                                                    val p3 = visiblePanes[3]
                                                    Column(
                                                        modifier = Modifier.fillMaxSize().padding(gapDp),
                                                        verticalArrangement = Arrangement.spacedBy(gapDp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                                            horizontalArrangement = Arrangement.spacedBy(gapDp)
                                                        ) {
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(1f),
                                                                pane = p0,
                                                                slotKey = p0.id,
                                                                pinnedIds = p0.pinnedNoteIds,
                                                                pinnedIndex = p0.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p0 },
                                                                onPrev = { onUpdatePane(p0.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p0.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p0.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p0.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p0.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p0.id) },
                                                                onMinimizePane = { onMinimizePane(p0.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p0.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p0.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p0.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p0.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p0.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p0.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(p0.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(1f),
                                                                pane = p1,
                                                                slotKey = p1.id,
                                                                pinnedIds = p1.pinnedNoteIds,
                                                                pinnedIndex = p1.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p1 },
                                                                onPrev = { onUpdatePane(p1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p1.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p1.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p1.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p1.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p1.id) },
                                                                onMinimizePane = { onMinimizePane(p1.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p1.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p1.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p1.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p1.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p1.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p1.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(p1.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                                            horizontalArrangement = Arrangement.spacedBy(gapDp)
                                                        ) {
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(1f),
                                                                pane = p2,
                                                                slotKey = p2.id,
                                                                pinnedIds = p2.pinnedNoteIds,
                                                                pinnedIndex = p2.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p2 },
                                                                onPrev = { onUpdatePane(p2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p2.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p2.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p2.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p2.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p2.id) },
                                                                onMinimizePane = { onMinimizePane(p2.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p2.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p2.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p2.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p2.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p2.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p2.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                    onStartDetachedDrag(p2.id, note, touchPos, size, slotPosInRoot)
                                                                },
                                                                onDetachedDrag = onContinueDetachedDrag,
                                                                onEndDetachedDrag = onEndDetachedDrag,
                                                                onCancelDetachedDrag = onCancelDetachedDrag,
                                                            )
                                                            PinnedNoteSlot(
                                                                modifier = Modifier.fillMaxHeight().weight(1f),
                                                                pane = p3,
                                                                slotKey = p3.id,
                                                                pinnedIds = p3.pinnedNoteIds,
                                                                pinnedIndex = p3.currentIndex,
                                                                allNotes = allNotes,
                                                                worldEntries = worldEntries,
                                                                activeTheme = activeTheme,
                                                                activeNote = activeNote,
                                                                accentColor = accentColor,
                                                                removeMode = removeMode,
                                                                onRemoveClick = { removeCandidatePane = p3 },
                                                                onPrev = { onUpdatePane(p3.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex > 0) p.currentIndex - 1 else l.size - 1) } },
                                                                onNext = { onUpdatePane(p3.id) { p -> val l = p.pinnedNoteIds; if (l.size <= 1) p else p.copy(currentIndex = if (p.currentIndex < l.size - 1) p.currentIndex + 1 else 0) } },
                                                                onSelectIndex = { idx -> onUpdatePane(p3.id) { it.copy(currentIndex = idx) } },
                                                                onSwitch = {},
                                                                onEdit = { id -> onLoadNote(id) },
                                                                onRemove = { id -> onUnpinNote(p3.id, id) },
                                                                onPick = {},
                                                                onUpdatePane = { transform -> onUpdatePane(p3.id, transform) },
                                                                onDuplicatePane = { handleDuplicate(p3.id) },
                                                                onMinimizePane = { onMinimizePane(p3.id, MinimizedBy.USER) },
                                                                onFocusPane = { focusedPaneId = p3.id },
                                                                onUnpinNote = { noteId -> onUnpinNote(p3.id, noteId) },
                                                                onReorderNote = { from, to -> onReorderNote(p3.id, from, to) },
                                                                onCreateNote = { title, content -> onCreateNote(p3.id, title, content) },
                                                                onPinNotes = { ids -> ids.forEach { onPinNote(p3.id, it) } },
                                                                hazeState = hazeState,
                                                                isDetached = dragState?.originSlot == p3.id,
                                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                                onStartDetachedDrag(p3.id, note, touchPos, size, slotPosInRoot)
                                                            },
                                                            onDetachedDrag = onContinueDetachedDrag,
                                                            onEndDetachedDrag = onEndDetachedDrag,
                                                            onCancelDetachedDrag = onCancelDetachedDrag,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // ── Spatial 4-Zone Glass Blueprint Dock Overlay ──
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = dragState != null,
                                            enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                                            exit = fadeOut(tween(160, easing = FastOutSlowInEasing)),
                                            modifier = Modifier.fillMaxSize().zIndex(10f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (hasBgImage) Color.Black.copy(alpha = 0.40f)
                                                        else solidSurface.copy(alpha = 0.65f)
                                                    )
                                                    .padding(10.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    SpatialDropZoneCard(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(1f),
                                                        label = "Top Slot",
                                                        subLabel = "Horizontal split · Top",
                                                        icon = Icons.Outlined.Splitscreen,
                                                        isHighlighted = activeHoveredZone == SpatialDropZone.TOP,
                                                        accentColor = accentColor,
                                                    )

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(1.2f),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        SpatialDropZoneCard(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            label = "Left Slot",
                                                            subLabel = "Vertical split · Left",
                                                            icon = Icons.Outlined.VerticalSplit,
                                                            isHighlighted = activeHoveredZone == SpatialDropZone.LEFT,
                                                            accentColor = accentColor,
                                                        )
                                                        SpatialDropZoneCard(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            label = "Right Slot",
                                                            subLabel = "Vertical split · Right",
                                                            icon = Icons.Outlined.VerticalSplit,
                                                            isHighlighted = activeHoveredZone == SpatialDropZone.RIGHT,
                                                            accentColor = accentColor,
                                                        )
                                                    }

                                                    SpatialDropZoneCard(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(1f),
                                                        label = "Bottom Slot",
                                                        subLabel = "Horizontal split · Bottom",
                                                        icon = Icons.Outlined.Splitscreen,
                                                        isHighlighted = activeHoveredZone == SpatialDropZone.BOTTOM,
                                                        accentColor = accentColor,
                                                    )
                                                }
                                            }
                                        }

                                        // ── Floating Detached Drag Proxy ─────────────────
                                        dragState?.let { drag ->
                                            val proxyWidth = (drag.initialSlotWidth * 0.94f).coerceAtLeast(140f)
                                            val proxyHeight = (drag.initialSlotHeight * 0.75f).coerceAtLeast(100f)

                                            val currentTouchX = drag.initialTouchInContainerX + drag.currentDragX
                                            val currentTouchY = drag.initialTouchInContainerY + drag.currentDragY
                                            val targetCenterX = currentTouchX
                                            val targetCenterY = currentTouchY

                                            val springSpec = spring<Float>(
                                                dampingRatio = 0.78f,
                                                stiffness = 500f
                                            )

                                            val animCenterX by animateFloatAsState(
                                                targetValue = targetCenterX,
                                                animationSpec = springSpec,
                                                label = "DetachedDragProxyCenterX"
                                            )
                                            val animCenterY by animateFloatAsState(
                                                targetValue = targetCenterY,
                                                animationSpec = springSpec,
                                                label = "DetachedDragProxyCenterY"
                                            )

                                            val proxyLeft = animCenterX - proxyWidth / 2f
                                            val proxyTop = animCenterY - proxyHeight / 2f

                                            val isHighlighted = activeHoveredZone != null
                                            val proxyBorderColor by animateColorAsState(
                                                targetValue = if (isHighlighted) accentColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                animationSpec = tween(150),
                                                label = "ProxyBorder"
                                            )

                                            Surface(
                                                modifier = Modifier
                                                    .size(
                                                        width = with(density) { proxyWidth.toDp() },
                                                        height = with(density) { proxyHeight.toDp() }
                                                    )
                                                    .offset {
                                                        IntOffset(
                                                            proxyLeft.roundToInt(),
                                                            proxyTop.roundToInt()
                                                        )
                                                    }
                                                    .zIndex(20f)
                                                    .graphicsLayer {
                                                        scaleX = 1.05f
                                                        scaleY = 1.05f
                                                        shadowElevation = 24.dp.toPx()
                                                        shape = RoundedCornerShape(14.dp)
                                                        clip = true
                                                        alpha = 0.94f
                                                    }
                                                    .border(
                                                        width = if (isHighlighted) 2.dp else 1.2.dp,
                                                        color = proxyBorderColor,
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .then(
                                                        if (hasBgImage) Modifier.frostedCard(hazeState, RoundedCornerShape(14.dp), applyFallbackBackground = true)
                                                        else Modifier
                                                    ),
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (hasBgImage) Color.Transparent else solidSurface.copy(alpha = 0.96f),
                                                tonalElevation = 12.dp,
                                                shadowElevation = 20.dp
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = accentColor.copy(alpha = 0.15f),
                                                        ) {
                                                            Text(
                                                                "REPOSITIONING",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 0.8.sp,
                                                                color = accentColor,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Spacer(Modifier.weight(1f))
                                                        Icon(
                                                            imageVector = Icons.Default.DragHandle,
                                                            contentDescription = null,
                                                            tint = accentColor,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = drag.note.name,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = drag.note.content.take(140).ifBlank { "(Empty note)" },
                                                        fontSize = 11.sp,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // ── Context Tray (4d) ──────────────────────────────
                                ContextTray(
                                    trayExpanded = contextTrayExpanded,
                                    onToggleExpand = { contextTrayExpanded = !contextTrayExpanded },
                                    waitingCount = systemMinimizedPanes.size,
                                    removeMode = removeMode,
                                    onToggleRemove = { removeMode = !removeMode },
                                    onAddSection = { showAddSectionSheet = true },
                                    onOpenSettings = { showWorkbenchSettingsSheet = true },
                                )
                            }

                            // ── Right Edge Rail (System Minimized) ───────────────────
                            EdgeTabRail(
                                minimizedPanes = systemMinimizedPanes,
                                side = EdgeSide.RIGHT,
                                isDark = isDark,
                                onTap = { handleRestorePane(it) },
                                onLongPress = { edgeTabLongPressPane = it },
                                onOpenGroup = { showEdgeTabGroupPopup = it }
                            )
                        }
                    }
                        1 -> OutlineView(outline = outline, soraEditorRef = soraEditorRef)
                        2 -> ProseAnalysisView(
                            analysis = proseAnalysis,
                            onJumpToSentence = { line ->
                                soraEditorRef?.let { editor ->
                                    val l = line.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                                    editor.setSelection(l, 0)
                                }
                            }
                        )
                    }
                }

                if (tabBarAtBottom) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            tabBarContent()
                        }
                        IconButton(onClick = onToggleTabBarPos) {
                            Icon(Icons.Default.VerticalAlignTop, "Move tabs to top", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Focus Mode Full-Screen Overlay (3f) ───────────────────────────
            if (focusedPaneId != null) {
                val currentFocusedPane = remember(focusedPaneId, workbenchState) {
                    workbenchState.panes.firstOrNull { it.id == focusedPaneId }
                        ?: workbenchState.panes.firstOrNull()
                        ?: PaneConfig(id = "pane_default")
                }

                val currentFocusedNoteId = currentFocusedPane.pinnedNoteIds.getOrNull(currentFocusedPane.currentIndex)
                val focusedNote = remember(currentFocusedNoteId, allNotes, worldEntries) {
                    allNotes.firstOrNull { it.id == currentFocusedNoteId }
                        ?: worldEntries.firstOrNull { it.id == currentFocusedNoteId }?.let { w ->
                            Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}")
                        }
                }

                LaunchedEffect(focusedNote?.id) {
                    originalContentForFocus = focusedNote?.content ?: ""
                }

                BackHandler(enabled = true) {
                    val currentText = focusEditorRef?.text?.toString() ?: ""
                    if (focusEditMode && currentText != originalContentForFocus) {
                        showDiscardDialog = true
                    } else {
                        focusedPaneId = null
                        focusEditMode = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(30f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = focusedNote?.name ?: "Focus View",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (focusEditMode) "Editing mode" else "Focus view · Read only",
                                        fontSize = 11.sp,
                                        color = if (focusEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    val currentText = focusEditorRef?.text?.toString() ?: ""
                                    if (focusEditMode && currentText != originalContentForFocus) {
                                        showDiscardDialog = true
                                    } else {
                                        focusedPaneId = null
                                        focusEditMode = false
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit Focus")
                                }
                            },
                            actions = {
                                if (focusedNote != null) {
                                    if (!focusEditMode) {
                                        TextButton(onClick = {
                                            originalContentForFocus = focusedNote.content
                                            focusEditMode = true
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val newText = focusEditorRef?.text?.toString() ?: ""
                                                onSaveNoteContent(focusedNote.id, newText)
                                                originalContentForFocus = newText
                                                focusEditMode = false
                                                scope.launch { snackbarHostState.showSnackbar("Saved") }
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Save")
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        if (focusedNote != null) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                factory = { ctx ->
                                    CodeEditor(ctx).apply {
                                        focusEditorRef = this
                                        isEditable = focusEditMode
                                        isLineNumberEnabled = true
                                        isHighlightCurrentLine = focusEditMode
                                        isWordwrap = true
                                        setText(focusedNote.content)
                                        activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }
                                },
                                update = { editor ->
                                    focusEditorRef = editor
                                    editor.isEditable = focusEditMode
                                    editor.isHighlightCurrentLine = focusEditMode
                                    if (!focusEditMode && editor.text.toString() != focusedNote.content) {
                                        editor.setText(focusedNote.content)
                                    }
                                    activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                                }
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No note selected", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    if (showDiscardDialog) {
                        AlertDialog(
                            onDismissRequest = { showDiscardDialog = false },
                            title = { Text("Discard changes?") },
                            text = { Text("You have unsaved changes in focus mode.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDiscardDialog = false
                                        focusedPaneId = null
                                        focusEditMode = false
                                    }
                                ) {
                                    Text("Discard", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDiscardDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            // ── Modal Sheets & Dialogs (Phase 4) ──────────────────────────────
            if (showAddSectionSheet) {
                AddSectionSheet(
                    activeNote = activeNote,
                    onDismiss = { showAddSectionSheet = false },
                    onPickScope = { scope ->
                        onAddPane(scope)
                        showAddSectionSheet = false
                    }
                )
            }

            if (showWorkbenchSettingsSheet) {
                WorkbenchSettingsSheet(
                    workbenchState = workbenchState,
                    onDismiss = { showWorkbenchSettingsSheet = false },
                    onUpdateWorkbench = onUpdateWorkbench
                )
            }

            if (restoreTargetPane != null) {
                val target = restoreTargetPane!!
                OutOfScopeRestoreSheet(
                    pane = target,
                    activeNote = activeNote,
                    onDismiss = { restoreTargetPane = null },
                    onJustSession = {
                        sessionScopeOverrides = sessionScopeOverrides + target.id
                        if (visiblePanes.size >= workbenchState.maxSlots) {
                            slotSwapReplaceTarget = target
                        } else {
                            onRestorePane(target.id)
                        }
                        restoreTargetPane = null
                    },
                    onAlwaysAdd = {
                        val fScope = PaneScope.File(id = activeNote?.id ?: "note", title = activeNote?.name ?: "Note")
                        onUpdatePane(target.id) { it.copy(secondaryScopes = it.secondaryScopes + fScope) }
                        if (visiblePanes.size >= workbenchState.maxSlots) {
                            slotSwapReplaceTarget = target
                        } else {
                            onRestorePane(target.id)
                        }
                        restoreTargetPane = null
                    }
                )
            }

            if (slotSwapReplaceTarget != null) {
                val incoming = slotSwapReplaceTarget!!
                AlertDialog(
                    onDismissRequest = { slotSwapReplaceTarget = null },
                    title = { Text("Workbench Full (${workbenchState.maxSlots}/${workbenchState.maxSlots})") },
                    text = {
                        Column {
                            Text(
                                text = "Select an active section to minimize and replace with \"${incoming.label}\":",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            visiblePanes.forEach { activePane ->
                                Surface(
                                    onClick = {
                                        onMinimizePane(activePane.id, MinimizedBy.SYSTEM)
                                        onRestorePane(incoming.id)
                                        slotSwapReplaceTarget = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = activePane.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Minimize",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { slotSwapReplaceTarget = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (removeCandidatePane != null) {
                val paneToRemove = removeCandidatePane!!
                AlertDialog(
                    onDismissRequest = { removeCandidatePane = null },
                    icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Remove Section?") },
                    text = {
                        Text("Are you sure you want to remove \"${paneToRemove.label}\" from the workbench? Pinned notes will not be deleted.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onRemovePane(paneToRemove.id)
                                removeCandidatePane = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { removeCandidatePane = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (edgeTabLongPressPane != null) {
                val p = edgeTabLongPressPane!!
                AlertDialog(
                    onDismissRequest = { edgeTabLongPressPane = null },
                    title = { Text(p.label) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                onClick = {
                                    handleRestorePane(p)
                                    edgeTabLongPressPane = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Restore Section", fontSize = 14.sp)
                                }
                            }
                            Surface(
                                onClick = {
                                    removeCandidatePane = p
                                    edgeTabLongPressPane = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Remove Section", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { edgeTabLongPressPane = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showEdgeTabGroupPopup != null) {
                val groupPanes = showEdgeTabGroupPopup!!
                AlertDialog(
                    onDismissRequest = { showEdgeTabGroupPopup = null },
                    title = { Text("Minimized Sections (${groupPanes.size})") },
                    text = {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(groupPanes, key = { it.id }) { pane ->
                                Surface(
                                    onClick = {
                                        handleRestorePane(pane)
                                        showEdgeTabGroupPopup = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(pane.accentColor.toComposeColor(isDark).takeOrElse { MaterialTheme.colorScheme.primary })
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(pane.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showEdgeTabGroupPopup = null }) {
                            Text("Close")
                        }
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .zIndex(40f)
            )
        }
    }
}

// ── PillTab ───────────────────────────────────────────────────────────────────

@Composable
private fun PillTab(
    label   : String,
    selected: Boolean,
    onClick : () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var textWidth by remember { mutableFloatStateOf(0f) }

    val animatedUnderlineWidth by animateFloatAsState(
        targetValue = if (selected) textWidth else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "PillTabUnderlineWidth"
    )

    val animatedFontSize by animateFloatAsState(
        targetValue = if (selected) 15f else 13f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "PillTabFontSize"
    )

    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .drawBehind {
                if (animatedUnderlineWidth > 0f) {
                    val strokeWidth = 2.dp.toPx()
                    val lineY = size.height - strokeWidth / 2
                    val startX = (size.width - animatedUnderlineWidth) / 2
                    val endX = startX + animatedUnderlineWidth
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, lineY),
                        end = Offset(endX, lineY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = animatedFontSize.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.onGloballyPositioned { layoutCoordinates ->
                textWidth = layoutCoordinates.size.width.toFloat()
            }
        )
    }
}

@Composable
private fun SpatialDropZoneCard(
    modifier     : Modifier = Modifier,
    label        : String,
    subLabel     : String,
    icon         : androidx.compose.ui.graphics.vector.ImageVector,
    isHighlighted: Boolean,
    accentColor  : Color,
) {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val hazeState = LocalHazeState.current

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
        animationSpec = tween(150),
        label = "SpatialDropZoneBorder"
    )
    val zoneBgColor = when {
        isHighlighted && !hasBgImage -> accentColor.copy(alpha = 0.20f)
        isHighlighted && hasBgImage -> accentColor.copy(alpha = 0.32f)
        !hasBgImage -> solidSurface.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.24f)
    }
    val animatedBgColor by animateColorAsState(
        targetValue = zoneBgColor,
        animationSpec = tween(150),
        label = "SpatialDropZoneBg"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.02f else 1.0f,
        animationSpec = tween(150),
        label = "SpatialDropZoneScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .then(
                if (hasBgImage) Modifier.frostedCard(hazeState, RoundedCornerShape(14.dp), applyFallbackBackground = false)
                else Modifier
            ),
        color = animatedBgColor,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isHighlighted) accentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isHighlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isHighlighted) accentColor else MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── PinnedNoteSlot ────────────────────────────────────────────────────────────

@Composable
private fun PinnedNoteSlot(
    modifier            : Modifier = Modifier,
    pane                : PaneConfig,
    slotKey             : String,
    pinnedIds           : List<String>,
    pinnedIndex         : Int,
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
    accentColor         : Color = Color.Unspecified,
    removeMode          : Boolean = false,
    onRemoveClick       : (() -> Unit)? = null,
    onPrev              : () -> Unit,
    onNext              : () -> Unit,
    onSelectIndex       : (Int) -> Unit,
    onSwitch            : () -> Unit,
    onEdit              : (String) -> Unit,
    onRemove            : (String) -> Unit,
    onPick              : () -> Unit,
    onUpdatePane        : (transform: (PaneConfig) -> PaneConfig) -> Unit,
    onDuplicatePane     : () -> Unit,
    onMinimizePane      : () -> Unit,
    onFocusPane         : () -> Unit,
    onUnpinNote         : (noteId: String) -> Unit,
    onReorderNote       : (fromIndex: Int, toIndex: Int) -> Unit,
    onCreateNote        : (title: String, content: String) -> Unit,
    onPinNotes          : (selectedIds: List<String>) -> Unit,
    hazeState           : dev.chrisbanes.haze.HazeState,
    isDetached          : Boolean = false,
    onStartDetachedDrag : ((note: Note, touchPos: Offset, size: androidx.compose.ui.geometry.Size, slotPosInRoot: Offset) -> Unit)? = null,
    onDetachedDrag      : ((dx: Float, dy: Float) -> Unit)? = null,
    onEndDetachedDrag   : (() -> Unit)? = null,
    onCancelDetachedDrag: (() -> Unit)? = null,
) {
    val currentId = pinnedIds.getOrNull(pinnedIndex)
    val currentNote = remember(currentId, allNotes, worldEntries) {
        allNotes.firstOrNull { it.id == currentId }
            ?: worldEntries.firstOrNull { it.id == currentId }?.let { w ->
                Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}\n\n${w.fieldsJson}")
            }
    }

    val pinnedNotesList = remember(pinnedIds, allNotes, worldEntries) {
        pinnedIds.mapNotNull { id ->
            allNotes.firstOrNull { it.id == id }
                ?: worldEntries.firstOrNull { it.id == id }?.let { w ->
                    Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
                }
        }
    }

    val isDark = isSystemInDarkTheme()
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()

    var slotSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var slotPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    // Section feature states
    var showOverflow by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf(false) }
    var labelInputText by remember(pane.label) { mutableStateOf(pane.label) }
    var showReferences by remember { mutableStateOf(false) }
    var unpinCandidateNoteId by remember { mutableStateOf<String?>(null) }

    // Modals
    var showAddReferenceChoiceSheet by remember { mutableStateOf(false) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showAddFileSheet by remember { mutableStateOf(false) }
    var showAddWorldSheetModal by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showScopeSheet by remember { mutableStateOf(false) }

    val paneAccentColor = pane.accentColor.toComposeColor(isDark)
    val effectiveAccent = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor else accentColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .graphicsLayer {
                alpha = if (isDetached) 0.35f else 1f
                scaleX = if (isDetached) 0.98f else 1f
                scaleY = if (isDetached) 0.98f else 1f
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!hasBgImage) Modifier.background(solidSurface, RoundedCornerShape(12.dp))
                else Modifier.frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .onGloballyPositioned { layoutCoordinates ->
                slotSize = androidx.compose.ui.geometry.Size(
                    layoutCoordinates.size.width.toFloat(),
                    layoutCoordinates.size.height.toFloat()
                )
                slotPositionInRoot = layoutCoordinates.positionInRoot()
            }
    ) {
        // Vertical accent bar on leading edge if pane accent color is set (3g)
        if (pane.accentColor != PaneAccentColor.NONE) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(paneAccentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .drawBehind {
                    val alpha = if (isDark) 0.06f else 0.12f
                    drawRoundRect(
                        color = Color.White.copy(alpha = alpha),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
                    )
                }
        )

        if (currentNote == null) {
            Column(
                modifier            = Modifier.fillMaxSize().clickable(onClick = { showAddReferenceChoiceSheet = true }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Pin a reference note",
                             tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Pin a reference note", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text("Tap to add notes or world sheets", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            val currentNoteState by rememberUpdatedState(currentNote)
            val currentSlotSize by rememberUpdatedState(slotSize)
            val currentSlotPosition by rememberUpdatedState(slotPositionInRoot)
            val currentOnStartDetachedDrag by rememberUpdatedState(onStartDetachedDrag)
            val currentOnDetachedDrag by rememberUpdatedState(onDetachedDrag)
            val currentOnEndDetachedDrag by rememberUpdatedState(onEndDetachedDrag)
            val currentOnCancelDetachedDrag by rememberUpdatedState(onCancelDetachedDrag)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(animationSpec = spring())
            ) {
                var headerDragging by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (headerDragging || isDetached) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val note = currentNoteState ?: return@detectDragGesturesAfterLongPress
                                    val onStart = currentOnStartDetachedDrag ?: return@detectDragGesturesAfterLongPress
                                    headerDragging = true
                                    onStart(note, offset, currentSlotSize, currentSlotPosition)
                                },
                                onDragEnd = {
                                    headerDragging = false
                                    currentOnEndDetachedDrag?.invoke()
                                },
                                onDragCancel = {
                                    headerDragging = false
                                    currentOnCancelDetachedDrag?.invoke()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDetachedDrag?.invoke(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Section Label Chip with inline edit (3b)
                            AnimatedVisibility(visible = pane.showLabel) {
                                val chipBg = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor.copy(alpha = 0.20f)
                                else MaterialTheme.colorScheme.primaryContainer
                                val chipText = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor
                                else MaterialTheme.colorScheme.onPrimaryContainer

                                if (editingLabel) {
                                    val focusRequester = remember { FocusRequester() }
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                    }
                                    BasicTextField(
                                        value = labelInputText,
                                        onValueChange = { labelInputText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            color = chipText
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            editingLabel = false
                                            onUpdatePane { it.copy(label = labelInputText.ifBlank { "SECTION" }) }
                                        }),
                                        modifier = Modifier
                                            .focusRequester(focusRequester)
                                            .background(chipBg, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused && editingLabel) {
                                                    editingLabel = false
                                                    onUpdatePane { it.copy(label = labelInputText.ifBlank { "SECTION" }) }
                                                }
                                            }
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = chipBg,
                                        onClick = {
                                            labelInputText = pane.label
                                            editingLabel = true
                                        }
                                    ) {
                                        Text(
                                            text = pane.label.ifBlank { "SECTION" }.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            color = chipText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = currentNote.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (pinnedIds.size > 1) {
                            IconButton(onClick = onPrev, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous note", modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "${pinnedIndex + 1} / ${pinnedIds.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(2.dp))
                            IconButton(onClick = onNext, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next note", modifier = Modifier.size(14.dp))
                            }
                        }

                        if (removeMode) {
                            IconButton(
                                onClick = { onRemoveClick?.invoke() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove Section",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(16.dp))
                            }

                            if (showOverflow) {
                                SectionOverflowMenu(
                                    pane = pane,
                                    isDark = isDark,
                                    onDismiss = { showOverflow = false },
                                    onEditLabel = {
                                        showOverflow = false
                                        labelInputText = pane.label
                                        editingLabel = true
                                    },
                                    onAddRef = {
                                        showOverflow = false
                                        showAddReferenceChoiceSheet = true
                                    },
                                    onReferences = {
                                        showOverflow = false
                                        showReferences = !showReferences
                                    },
                                    onDuplicate = {
                                        showOverflow = false
                                        onDuplicatePane()
                                    },
                                    onFocus = {
                                        showOverflow = false
                                        onFocusPane()
                                    },
                                    onAppearance = {
                                        showOverflow = false
                                        showAppearanceSheet = true
                                    },
                                    onScope = {
                                        showOverflow = false
                                        showScopeSheet = true
                                    },
                                    onMinimize = {
                                        showOverflow = false
                                        onMinimizePane()
                                    },
                                    hazeState = hazeState
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // ── References Expand Panel (3c) ──────────────────────────────
                AnimatedVisibility(visible = showReferences) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "REFERENCES (${pinnedNotesList.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            IconButton(
                                onClick = { showReferences = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ExpandLess, contentDescription = "Collapse", modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                        ) {
                            itemsIndexed(pinnedNotesList, key = { _, n -> n.id }) { idx, noteItem ->
                                val isCurrent = idx == pinnedIndex
                                Surface(
                                    onClick = { onSelectIndex(idx) },
                                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(20.dp)
                                        ) {
                                            if (idx > 0) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Move Up",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onReorderNote(idx, idx - 1) }
                                                )
                                            }
                                            if (idx < pinnedNotesList.size - 1) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Move Down",
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onReorderNote(idx, idx + 1) }
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        Text(
                                            text = noteItem.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = { unpinCandidateNoteId = noteItem.id },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Unpin",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.5.dp
                        )
                    }
                }

                // ── Note Content (Read-Only Sora CodeEditor) ───────────────────
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 2.dp),
                    factory  = { ctx ->
                        CodeEditor(ctx).apply {
                            isEditable             = false
                            isLineNumberEnabled    = false
                            isHighlightCurrentLine = false
                            isWordwrap             = true
                            setText(currentNote.content.ifBlank { "(Empty note content)" })
                            activeTheme?.let { colorScheme = ScribeColorScheme(it) }
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { editor ->
                        val incoming = currentNote.content.ifBlank { "(Empty note content)" }
                        if (editor.text.toString() != incoming) editor.setText(incoming)
                        activeTheme?.let { editor.colorScheme = ScribeColorScheme(it) }
                    }
                )

                // ── Footer Row with Pills Visibility (3g) ─────────────────────
                AnimatedVisibility(visible = pane.showFooterPills) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Edited just now",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = onFocusPane,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.OpenInFull, contentDescription = "Focus note", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { showAddReferenceChoiceSheet = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add reference", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Unpin Confirmation Dialog (3c) ────────────────────────────────────────
    if (unpinCandidateNoteId != null) {
        val noteIdToUnpin = unpinCandidateNoteId!!
        AlertDialog(
            onDismissRequest = { unpinCandidateNoteId = null },
            title = { Text("Unpin this reference?") },
            text = { Text("It will be removed from this section. The original file won't be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnpinNote(noteIdToUnpin)
                        unpinCandidateNoteId = null
                    }
                ) {
                    Text("Unpin", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { unpinCandidateNoteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Add Reference Choice Sheet (3d) ───────────────────────────────────────
    if (showAddReferenceChoiceSheet) {
        AddReferenceChoiceSheet(
            onDismiss = { showAddReferenceChoiceSheet = false },
            onPickNewNote = { showNewNoteDialog = true },
            onPickAddFile = { showAddFileSheet = true },
            onPickAddWorldSheet = { showAddWorldSheetModal = true }
        )
    }

    // ── New Note Dialog (3d) ──────────────────────────────────────────────────
    if (showNewNoteDialog) {
        NewNoteDialog(
            onDismiss = { showNewNoteDialog = false },
            onSave = { title, content -> onCreateNote(title, content) }
        )
    }

    // ── Add File Sheet (3d) ───────────────────────────────────────────────────
    if (showAddFileSheet) {
        AddFileSheet(
            allNotes = allNotes,
            currentBookId = activeNote?.bookId,
            onDismiss = { showAddFileSheet = false },
            onConfirm = { ids -> onPinNotes(ids) }
        )
    }

    // ── Add WorldSheet Modal (3d) ─────────────────────────────────────────────
    if (showAddWorldSheetModal) {
        AddWorldSheetModal(
            worldEntries = worldEntries,
            onDismiss = { showAddWorldSheetModal = false },
            onConfirm = { ids -> onPinNotes(ids) }
        )
    }

    // ── Section Appearance Sheet (3g) ─────────────────────────────────────────
    if (showAppearanceSheet) {
        SectionAppearanceSheet(
            pane = pane,
            isDark = isDark,
            onDismiss = { showAppearanceSheet = false },
            onUpdatePane = onUpdatePane
        )
    }

    // ── Section Scope Sheet (3h) ──────────────────────────────────────────────
    if (showScopeSheet) {
        SectionScopeSheet(
            pane = pane,
            activeNote = activeNote,
            onDismiss = { showScopeSheet = false },
            onUpdatePane = onUpdatePane
        )
    }
}

// ── SplitDivider ──────────────────────────────────────────────────────────────

@Composable
private fun SplitDivider(
    isHorizontal: Boolean,
    onDrag      : (Float) -> Unit,
    onSwap      : () -> Unit,
    accentColor : Color,
    hazeState   : dev.chrisbanes.haze.HazeState,
) {
    var isDragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val dividerBgColor by animateColorAsState(
        targetValue = if (isDragging) accentColor.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(120),
        label = "SplitDividerBg"
    )

    Box(
        modifier = if (isHorizontal) {
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .background(dividerBgColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd   = { isDragging = false },
                        onDragCancel= { isDragging = false },
                        onDrag      = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onSwap() })
                }
        } else {
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(dividerBgColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd   = { isDragging = false },
                        onDragCancel= { isDragging = false },
                        onDrag      = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onSwap() })
                }
        },
        contentAlignment = Alignment.Center
    ) {
        // 0.5dp Hairline running through the divider center
        if (isHorizontal) {
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            )
        }

        // Center pill with RoundedCornerShape(50)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = if (isHorizontal) {
                Modifier.width(28.dp).height(36.dp)
            } else {
                Modifier.width(48.dp).height(28.dp)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Swap split",
                    modifier = Modifier.size(16.dp),
                    tint = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── OutlineView ───────────────────────────────────────────────────────────────

@Composable
private fun OutlineView(
    outline      : List<OutlineEntry>,
    soraEditorRef: CodeEditor?,
) {
    if (outline.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Default.FormatListNumbered,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp),
                    tint               = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "No headings found",
                    fontSize  = 13.sp,
                    color     = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = "Add # Heading 1 or ## Heading 2 in your text",
                    fontSize  = 11.sp,
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(outline) { entry ->
                val indent = ((entry.level - 1) * 14).dp
                Surface(
                    onClick = {
                        soraEditorRef?.let { editor ->
                            val line = entry.lineIndex.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                            editor.setSelection(line, 0)
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "H${entry.level}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = entry.text,
                            fontSize = 13.sp,
                            fontWeight = if (entry.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── EdgeTabRail (Phase 4) ───────────────────────────────────────────────────

enum class EdgeSide { LEFT, RIGHT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeTabRail(
    minimizedPanes: List<PaneConfig>,
    side: EdgeSide,
    isDark: Boolean,
    onTap: (PaneConfig) -> Unit,
    onLongPress: (PaneConfig) -> Unit,
    onOpenGroup: (List<PaneConfig>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (minimizedPanes.isEmpty()) return

    val maxIndividualTabs = 3
    val showGroup = minimizedPanes.size > maxIndividualTabs
    val individualTabs = if (showGroup) minimizedPanes.take(maxIndividualTabs - 1) else minimizedPanes
    val groupedTabs = if (showGroup) minimizedPanes.drop(maxIndividualTabs - 1) else emptyList()

    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = if (side == EdgeSide.LEFT) Alignment.Start else Alignment.End
    ) {
        individualTabs.forEach { pane ->
            EdgeTabItem(
                pane = pane,
                side = side,
                isDark = isDark,
                onTap = { onTap(pane) },
                onLongPress = { onLongPress(pane) }
            )
        }

        if (showGroup && groupedTabs.isNotEmpty()) {
            val groupAccent = groupedTabs.firstOrNull()?.accentColor?.toComposeColor(isDark)
                ?.takeOrElse { MaterialTheme.colorScheme.primary } ?: MaterialTheme.colorScheme.primary

            val shape = if (side == EdgeSide.LEFT) {
                RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
            }

            Surface(
                onClick = { onOpenGroup(groupedTabs) },
                shape = shape,
                color = groupAccent.copy(alpha = if (isDark) 0.22f else 0.15f),
                border = BorderStroke(1.dp, groupAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .width(26.dp)
                    .height(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${groupedTabs.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = groupAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EdgeTabItem(
    pane: PaneConfig,
    side: EdgeSide,
    isDark: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = pane.accentColor.toComposeColor(isDark).takeOrElse { MaterialTheme.colorScheme.primary }
    val shape = if (side == EdgeSide.LEFT) {
        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    }

    Surface(
        modifier = modifier
            .width(26.dp)
            .height(84.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = shape,
        color = accent.copy(alpha = if (isDark) 0.25f else 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pip / dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.height(4.dp))
                // Vertical text rotation
                Text(
                    text = pane.label.take(8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (side == EdgeSide.LEFT) -90f else 90f
                    }
                )
            }
        }
    }
}

// ── ContextTray (Phase 4) ───────────────────────────────────────────────────

@Composable
fun ContextTray(
    trayExpanded: Boolean,
    onToggleExpand: () -> Unit,
    waitingCount: Int,
    removeMode: Boolean,
    onToggleRemove: () -> Unit,
    onAddSection: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand / collapse handle
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (trayExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (trayExpanded) "Collapse Context Tray" else "Expand Context Tray",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                // Waiting badge indicator if any waiting panes
                if (waitingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "$waitingCount waiting",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Remove Mode Toggle
                IconButton(
                    onClick = onToggleRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Toggle Remove Mode",
                        tint = if (removeMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Add Section Button
                IconButton(
                    onClick = onAddSection,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Section",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Workbench Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Workbench Settings",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded Tray Content
            AnimatedVisibility(visible = trayExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Workbench Controls",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddSection,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Section", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Settings", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
