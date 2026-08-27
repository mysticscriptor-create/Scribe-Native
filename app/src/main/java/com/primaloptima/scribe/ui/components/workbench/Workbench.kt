package com.primaloptima.scribe.ui.components.workbench

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.primaloptima.scribe.data.Book
import com.primaloptima.scribe.data.Note
import com.primaloptima.scribe.data.WorldEntry
import com.primaloptima.scribe.ui.components.AddSectionSheet
import com.primaloptima.scribe.ui.components.OutOfScopeRestoreSheet
import com.primaloptima.scribe.ui.components.WorkbenchSettingsSheet
import com.primaloptima.scribe.ui.screens.LocalInteractiveBoundsRegistry
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.LocalSolidSurface
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.localHasBgImage
import com.primaloptima.scribe.util.model.*
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SpatialDropZone {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

data class DetachedDragState(
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
fun Workbench(
    workbenchState      : WorkbenchState = WorkbenchState(),
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    books               : List<Book> = emptyList(),
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
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
    hazeState           : dev.chrisbanes.haze.HazeState? = LocalHazeState.current,
    snackbarHostState   : SnackbarHostState,
    modifier            : Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
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

    val gapDp = 3.dp
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val registerBounds = LocalInteractiveBoundsRegistry.current

    Box(modifier = modifier.fillMaxSize()) {
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
                    val density = LocalDensity.current
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
                                WorkbenchCard(
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
                                        WorkbenchCard(
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
                                        WorkbenchSplitDivider(
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
                                        WorkbenchCard(
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
                                        WorkbenchCard(
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
                                        WorkbenchSplitDivider(
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
                                        WorkbenchCard(
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
                                if (workbenchState.splitHorizontal) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(gapDp),
                                        horizontalArrangement = Arrangement.spacedBy(gapDp)
                                    ) {
                                        WorkbenchCard(
                                            modifier = Modifier.fillMaxHeight().weight(1.1f),
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
                                            modifier = Modifier.fillMaxHeight().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(gapDp)
                                        ) {
                                            WorkbenchCard(
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
                                            WorkbenchCard(
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
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(gapDp),
                                        verticalArrangement = Arrangement.spacedBy(gapDp)
                                    ) {
                                        WorkbenchCard(
                                            modifier = Modifier.fillMaxWidth().weight(1.1f),
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(gapDp)
                                        ) {
                                            WorkbenchCard(
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
                                            WorkbenchCard(
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
                                        }
                                    }
                                }
                            }
                            else -> {
                                // 4 Panes 2x2 Grid
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
                                        WorkbenchCard(
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
                                        WorkbenchCard(
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
                                        WorkbenchCard(
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
                                        WorkbenchCard(
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
                WorkbenchContextTray(
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
                    FrostedDialog(
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
                onPickScope = { scopePick ->
                    onAddPane(scopePick)
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
            FrostedDialog(
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
            FrostedDialog(
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
            FrostedDialog(
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
            FrostedDialog(
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
    }
}

@Composable
private fun SpatialDropZoneCard(
    modifier     : Modifier = Modifier,
    label        : String,
    subLabel     : String,
    icon         : ImageVector,
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
