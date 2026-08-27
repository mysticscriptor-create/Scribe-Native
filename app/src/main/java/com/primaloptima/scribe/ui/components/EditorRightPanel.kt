package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    pinnedTopNotes      : List<String>,
    pinnedTopIndex      : Int,
    pinnedBottomNotes   : List<String>,
    pinnedBottomIndex   : Int,
    workbenchState      : WorkbenchState = WorkbenchState(),
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    outline             : List<OutlineEntry>,
    activeTheme         : AppTheme?,
    activeNote          : Note? = null,
    proseAnalysis       : ProseAnalysisResult,
    soraEditorRef       : CodeEditor?,
    tabBarAtBottom      : Boolean,
    splitHorizontal     : Boolean,
    onToggleTabBarPos   : () -> Unit,
    onToggleSplitLayout : () -> Unit,
    onSwapSlots         : () -> Unit,
    onPrevTop           : () -> Unit,
    onNextTop           : () -> Unit,
    onSwitchTop         : () -> Unit,
    onEditTop           : (String) -> Unit,
    onRemoveTop         : (String) -> Unit,
    onPrevBottom        : () -> Unit,
    onNextBottom        : () -> Unit,
    onSwitchBottom      : () -> Unit,
    onEditBottom        : (String) -> Unit,
    onRemoveBottom      : (String) -> Unit,
    onPickTop           : () -> Unit,
    onPickBottom        : () -> Unit,
    onUpdatePane        : (paneId: String, transform: (PaneConfig) -> PaneConfig) -> Unit = { _, _ -> },
    onDuplicatePane     : (paneId: String) -> Unit = {},
    onMinimizePane      : (paneId: String) -> Unit = {},
    onUnpinNote         : (paneId: String, noteId: String) -> Unit = { _, _ -> },
    onReorderNote       : (paneId: String, fromIndex: Int, toIndex: Int) -> Unit = { _, _, _ -> },
    onCreateNoteForPane : (paneId: String, title: String, content: String) -> Unit = { _, _, _ -> },
    onPinNotesToPane    : (paneId: String, noteIds: List<String>) -> Unit = { _, _ -> },
    onSaveNoteContent   : (noteId: String, content: String) -> Unit = { _, _ -> },
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

    // Focus mode state
    var focusedPaneId by remember { mutableStateOf<String?>(null) }
    var focusEditMode by remember { mutableStateOf(false) }
    var focusEditorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var originalContentForFocus by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val topPane = remember(workbenchState, pinnedTopNotes, pinnedTopIndex) {
        workbenchState.panes.getOrNull(0) ?: PaneConfig(
            id = "pane_top",
            label = "SECTION",
            pinnedNoteIds = pinnedTopNotes,
            currentIndex = pinnedTopIndex
        )
    }

    val bottomPane = remember(workbenchState, pinnedBottomNotes, pinnedBottomIndex) {
        workbenchState.panes.getOrNull(1) ?: PaneConfig(
            id = "pane_bottom",
            label = "SECTION",
            pinnedNoteIds = pinnedBottomNotes,
            currentIndex = pinnedBottomIndex
        )
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

                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val totalPxFloat = with(density) {
                                    if (splitHorizontal) maxWidth.toPx() else maxHeight.toPx()
                                }

                                var dragState by remember { mutableStateOf<DetachedDragState?>(null) }
                                var activeHoveredZone by remember { mutableStateOf<SpatialDropZone?>(null) }
                                var containerPositionInRoot by remember { mutableStateOf(Offset.Zero) }
                                var pinnedContainerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

                                DisposableEffect(dragState != null, pinnedContainerBounds) {
                                    if (dragState != null && pinnedContainerBounds != null) {
                                        registerBounds?.invoke("pinned_notes_detached_drag", pinnedContainerBounds)
                                    }
                                    onDispose {
                                        registerBounds?.invoke("pinned_notes_detached_drag", null)
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
                                        val isCurrentHorizontal = splitHorizontal
                                        when (targetZone) {
                                            SpatialDropZone.TOP -> {
                                                if (isCurrentHorizontal) {
                                                    onToggleSplitLayout()
                                                    if (currentDrag.originSlot == "bottom") onSwapSlots()
                                                } else {
                                                    if (currentDrag.originSlot == "bottom") onSwapSlots()
                                                }
                                            }
                                            SpatialDropZone.BOTTOM -> {
                                                if (isCurrentHorizontal) {
                                                    onToggleSplitLayout()
                                                    if (currentDrag.originSlot == "top") onSwapSlots()
                                                } else {
                                                    if (currentDrag.originSlot == "top") onSwapSlots()
                                                }
                                            }
                                            SpatialDropZone.LEFT -> {
                                                if (!isCurrentHorizontal) {
                                                    onToggleSplitLayout()
                                                    if (currentDrag.originSlot == "bottom") onSwapSlots()
                                                } else {
                                                    if (currentDrag.originSlot == "bottom") onSwapSlots()
                                                }
                                            }
                                            SpatialDropZone.RIGHT -> {
                                                if (!isCurrentHorizontal) {
                                                    onToggleSplitLayout()
                                                    if (currentDrag.originSlot == "top") onSwapSlots()
                                                } else {
                                                    if (currentDrag.originSlot == "top") onSwapSlots()
                                                }
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
                                    if (splitHorizontal) {
                                        Row(
                                            modifier              = Modifier.fillMaxSize().padding(gapDp),
                                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                                        ) {
                                            PinnedNoteSlot(
                                                modifier            = Modifier.fillMaxHeight().weight(splitFraction),
                                                pane                = topPane,
                                                slotKey             = "top",
                                                pinnedIds           = topPane.pinnedNoteIds.ifEmpty { pinnedTopNotes },
                                                pinnedIndex         = topPane.currentIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                activeNote          = activeNote,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSelectIndex       = { idx -> onUpdatePane(topPane.id) { it.copy(currentIndex = idx) } },
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
                                                onUpdatePane        = { transform -> onUpdatePane(topPane.id, transform) },
                                                onDuplicatePane     = { handleDuplicate(topPane.id) },
                                                onMinimizePane      = { onMinimizePane(topPane.id) },
                                                onFocusPane         = { focusedPaneId = topPane.id },
                                                onUnpinNote         = { noteId -> onUnpinNote(topPane.id, noteId) },
                                                onReorderNote       = { from, to -> onReorderNote(topPane.id, from, to) },
                                                onCreateNote        = { title, content -> onCreateNoteForPane(topPane.id, title, content) },
                                                onPinNotes          = { ids -> onPinNotesToPane(topPane.id, ids) },
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "top",
                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                    onStartDetachedDrag("top", note, touchPos, size, slotPosInRoot)
                                                },
                                                onDetachedDrag      = onContinueDetachedDrag,
                                                onEndDetachedDrag   = onEndDetachedDrag,
                                                onCancelDetachedDrag= onCancelDetachedDrag,
                                            )
                                            SplitDivider(
                                                isHorizontal = true,
                                                onDrag       = { delta ->
                                                    splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                                },
                                                onSwap      = onSwapSlots,
                                                accentColor = accentColor,
                                                hazeState   = hazeState,
                                            )
                                            PinnedNoteSlot(
                                                modifier            = Modifier.fillMaxHeight().weight(1f - splitFraction),
                                                pane                = bottomPane,
                                                slotKey             = "bottom",
                                                pinnedIds           = bottomPane.pinnedNoteIds.ifEmpty { pinnedBottomNotes },
                                                pinnedIndex         = bottomPane.currentIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                activeNote          = activeNote,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSelectIndex       = { idx -> onUpdatePane(bottomPane.id) { it.copy(currentIndex = idx) } },
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
                                                onUpdatePane        = { transform -> onUpdatePane(bottomPane.id, transform) },
                                                onDuplicatePane     = { handleDuplicate(bottomPane.id) },
                                                onMinimizePane      = { onMinimizePane(bottomPane.id) },
                                                onFocusPane         = { focusedPaneId = bottomPane.id },
                                                onUnpinNote         = { noteId -> onUnpinNote(bottomPane.id, noteId) },
                                                onReorderNote       = { from, to -> onReorderNote(bottomPane.id, from, to) },
                                                onCreateNote        = { title, content -> onCreateNoteForPane(bottomPane.id, title, content) },
                                                onPinNotes          = { ids -> onPinNotesToPane(bottomPane.id, ids) },
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "bottom",
                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                    onStartDetachedDrag("bottom", note, touchPos, size, slotPosInRoot)
                                                },
                                                onDetachedDrag      = onContinueDetachedDrag,
                                                onEndDetachedDrag   = onEndDetachedDrag,
                                                onCancelDetachedDrag= onCancelDetachedDrag,
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier            = Modifier.fillMaxSize().padding(gapDp),
                                            verticalArrangement = Arrangement.spacedBy(0.dp)
                                        ) {
                                            PinnedNoteSlot(
                                                modifier            = Modifier.fillMaxWidth().weight(splitFraction),
                                                pane                = topPane,
                                                slotKey             = "top",
                                                pinnedIds           = topPane.pinnedNoteIds.ifEmpty { pinnedTopNotes },
                                                pinnedIndex         = topPane.currentIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                activeNote          = activeNote,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSelectIndex       = { idx -> onUpdatePane(topPane.id) { it.copy(currentIndex = idx) } },
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
                                                onUpdatePane        = { transform -> onUpdatePane(topPane.id, transform) },
                                                onDuplicatePane     = { handleDuplicate(topPane.id) },
                                                onMinimizePane      = { onMinimizePane(topPane.id) },
                                                onFocusPane         = { focusedPaneId = topPane.id },
                                                onUnpinNote         = { noteId -> onUnpinNote(topPane.id, noteId) },
                                                onReorderNote       = { from, to -> onReorderNote(topPane.id, from, to) },
                                                onCreateNote        = { title, content -> onCreateNoteForPane(topPane.id, title, content) },
                                                onPinNotes          = { ids -> onPinNotesToPane(topPane.id, ids) },
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "top",
                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                    onStartDetachedDrag("top", note, touchPos, size, slotPosInRoot)
                                                },
                                                onDetachedDrag      = onContinueDetachedDrag,
                                                onEndDetachedDrag   = onEndDetachedDrag,
                                                onCancelDetachedDrag= onCancelDetachedDrag,
                                            )
                                            SplitDivider(
                                                isHorizontal = false,
                                                onDrag       = { delta ->
                                                    splitFraction = (splitFraction + delta / totalPxFloat).coerceIn(0.2f, 0.8f)
                                                },
                                                onSwap      = onSwapSlots,
                                                accentColor = accentColor,
                                                hazeState   = hazeState,
                                            )
                                            PinnedNoteSlot(
                                                modifier            = Modifier.fillMaxWidth().weight(1f - splitFraction),
                                                pane                = bottomPane,
                                                slotKey             = "bottom",
                                                pinnedIds           = bottomPane.pinnedNoteIds.ifEmpty { pinnedBottomNotes },
                                                pinnedIndex         = bottomPane.currentIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                activeNote          = activeNote,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSelectIndex       = { idx -> onUpdatePane(bottomPane.id) { it.copy(currentIndex = idx) } },
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
                                                onUpdatePane        = { transform -> onUpdatePane(bottomPane.id, transform) },
                                                onDuplicatePane     = { handleDuplicate(bottomPane.id) },
                                                onMinimizePane      = { onMinimizePane(bottomPane.id) },
                                                onFocusPane         = { focusedPaneId = bottomPane.id },
                                                onUnpinNote         = { noteId -> onUnpinNote(bottomPane.id, noteId) },
                                                onReorderNote       = { from, to -> onReorderNote(bottomPane.id, from, to) },
                                                onCreateNote        = { title, content -> onCreateNoteForPane(bottomPane.id, title, content) },
                                                onPinNotes          = { ids -> onPinNotesToPane(bottomPane.id, ids) },
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "bottom",
                                                onStartDetachedDrag = { note, touchPos, size, slotPosInRoot ->
                                                    onStartDetachedDrag("bottom", note, touchPos, size, slotPosInRoot)
                                                },
                                                onDetachedDrag      = onContinueDetachedDrag,
                                                onEndDetachedDrag   = onEndDetachedDrag,
                                                onCancelDetachedDrag= onCancelDetachedDrag,
                                            )
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

                                    // ── Floating Detached Drag Proxy ──
                                    dragState?.let { drag ->
                                        val proxyWidth = (drag.initialSlotWidth * 0.94f).coerceAtLeast(140f)
                                        val proxyHeight = (drag.initialSlotHeight * 0.75f).coerceAtLeast(100f)
                                        val initialCenterX = drag.initialSlotOriginX + drag.initialSlotWidth / 2f
                                        val initialCenterY = drag.initialSlotOriginY + drag.initialSlotHeight / 2f

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
                        }
                        1 -> OutlineView(outline = outline, soraEditorRef = soraEditorRef)
                        2 -> ProseAnalysisView(proseAnalysis = proseAnalysis, soraEditorRef = soraEditorRef)
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
                val currentFocusedPane = remember(focusedPaneId, workbenchState, topPane, bottomPane) {
                    workbenchState.panes.firstOrNull { it.id == focusedPaneId }
                        ?: if (focusedPaneId == topPane.id || focusedPaneId == "top") topPane else bottomPane
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
        targetValue = if (selected) 13.5f else 13f,
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
                width = 0.8.dp,
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
                    val alpha = if (isDark) 0.06f else 0.10f
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
                                val chipBg = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer
                                val chipText = if (pane.accentColor != PaneAccentColor.NONE) paneAccentColor
                                else MaterialTheme.colorScheme.primary

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
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
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
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
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
                            IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                "${pinnedIndex + 1} / ${pinnedIds.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflow = true },
                                modifier = Modifier.size(28.dp)
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
                                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.OpenInFull, contentDescription = "Focus note", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { showAddReferenceChoiceSheet = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add reference", modifier = Modifier.size(16.dp))
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
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()

    val dividerBgColor by animateColorAsState(
        targetValue = if (isDragging) accentColor.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(120),
        label = "SplitDividerBg"
    )

    Box(
        modifier = if (isHorizontal) {
            Modifier
                .width(12.dp)
                .fillMaxHeight()
                .background(dividerBgColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
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
                .height(12.dp)
                .background(dividerBgColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
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
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = if (isDragging) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = if (isHorizontal) {
                Modifier.width(3.dp).height(32.dp)
            } else {
                Modifier.height(3.dp).width(32.dp)
            }
        ) {}
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
                            val line = entry.line.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
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
                            text = entry.title,
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
