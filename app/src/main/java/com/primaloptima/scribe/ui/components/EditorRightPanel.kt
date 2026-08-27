package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.OutlineEntry
import io.github.rosemoe.sora.widget.CodeEditor
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
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    outline             : List<OutlineEntry>,
    activeTheme         : AppTheme?,
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
    onClose             : () -> Unit,
    barBlurBitmap       : Bitmap?,
    hazeState           : dev.chrisbanes.haze.HazeState,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

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

                                // Lock swipe navigation ONLY when a pinned note is actively detached and dragging
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
                                                slotKey             = "top",
                                                pinnedIds           = pinnedTopNotes,
                                                pinnedIndex         = pinnedTopIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
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
                                                slotKey             = "bottom",
                                                pinnedIds           = pinnedBottomNotes,
                                                pinnedIndex         = pinnedBottomIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
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
                                                slotKey             = "top",
                                                pinnedIds           = pinnedTopNotes,
                                                pinnedIndex         = pinnedTopIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
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
                                                slotKey             = "bottom",
                                                pinnedIds           = pinnedBottomNotes,
                                                pinnedIndex         = pinnedBottomIndex,
                                                allNotes            = allNotes,
                                                worldEntries        = worldEntries,
                                                activeTheme         = activeTheme,
                                                accentColor         = accentColor,
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
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
                                                // 1. Top Slot (Horizontal Split · Top)
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

                                                // 2. Middle Row: Left & Right Slots (Vertical Split Side-by-Side)
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

                                                // 3. Bottom Slot (Horizontal Split · Bottom)
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

                                    // ── Detached Dragging Card Floating Layer ──────
                                    dragState?.let { activeDrag ->
                                        val cardWidthDp = with(density) { (activeDrag.initialSlotWidth.coerceAtLeast(180f)).toDp() }
                                        val cardHeightDp = with(density) { (activeDrag.initialSlotHeight.coerceIn(100f, 600f)).toDp() }
                                        val currentCardX = activeDrag.initialSlotOriginX + activeDrag.currentDragX
                                        val currentCardY = activeDrag.initialSlotOriginY + activeDrag.currentDragY

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .zIndex(20f)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .size(width = cardWidthDp, height = cardHeightDp)
                                                    .offset {
                                                        IntOffset(
                                                            x = currentCardX.roundToInt(),
                                                            y = currentCardY.roundToInt()
                                                        )
                                                    }
                                                    .graphicsLayer {
                                                        scaleX = 1.03f
                                                        scaleY = 1.03f
                                                        shadowElevation = 18.dp.toPx()
                                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                                    }
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = accentColor.copy(alpha = 0.85f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .then(
                                                        if (!hasBgImage) {
                                                            Modifier.background(solidSurface, RoundedCornerShape(12.dp))
                                                        } else {
                                                            Modifier.frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
                                                        }
                                                    ),
                                                color = if (!hasBgImage) solidSurface else Color.Transparent,
                                                tonalElevation = 8.dp
                                            ) {
                                                Column(Modifier.fillMaxSize()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(accentColor.copy(alpha = 0.18f))
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DragIndicator,
                                                            contentDescription = null,
                                                            tint = accentColor,
                                                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                        )
                                                        Text(
                                                            text = activeDrag.note.name,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = activeDrag.note.content.ifBlank { "(Empty note content)" },
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                        maxLines = 8,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (outline.isEmpty()) {
                                Box(
                                    modifier         = Modifier.fillMaxSize().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint     = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Text(
                                            "No headings yet",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 16.sp,
                                            color      = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Use # Heading to structure\nyour writing",
                                            fontSize  = 13.sp,
                                            color     = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(outline) { entry ->
                                        val indentDp   = ((entry.level - 1) * 16).dp
                                        val isTopLevel = entry.level == 1
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = indentDp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isTopLevel) MaterialTheme.colorScheme.surfaceVariant
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    soraEditorRef?.let { editor ->
                                                        val pos = editor.text.toString().indexOf(entry.text)
                                                        if (pos >= 0) {
                                                            val line = editor.text.indexer.getCharLine(pos)
                                                            val col  = editor.text.indexer.getCharColumn(pos)
                                                            editor.cursor.set(line, col)
                                                            editor.ensurePositionVisible(line, col)
                                                        }
                                                    }
                                                    onClose()
                                                }
                                                .padding(
                                                    horizontal = if (isTopLevel) 14.dp else 10.dp,
                                                    vertical   = if (isTopLevel) 12.dp else 8.dp
                                                ),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = if (isTopLevel) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    "H${entry.level}",
                                                    fontSize   = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = if (isTopLevel) MaterialTheme.colorScheme.onPrimary
                                                                 else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text       = entry.text,
                                                fontSize   = if (isTopLevel) 15.sp else 13.sp,
                                                fontWeight = if (isTopLevel) FontWeight.SemiBold else FontWeight.Normal,
                                                color      = MaterialTheme.colorScheme.onSurface,
                                                maxLines   = 2,
                                                overflow   = TextOverflow.Ellipsis,
                                                modifier   = Modifier.weight(1f)
                                            )
                                            if (isTopLevel) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint     = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            ProseAnalysisView(
                                analysis         = proseAnalysis,
                                modifier         = Modifier.fillMaxSize(),
                                onJumpToSentence = { sentenceIndex ->
                                    soraEditorRef?.let { editor ->
                                        // Build a flat list of sentence start offsets by scanning
                                        // the editor content for sentence-ending punctuation,
                                        // then jump to the character offset of the target sentence.
                                        val content = editor.text.toString()
                                        val offsets = mutableListOf<Int>()
                                        offsets.add(0)
                                        var i = 0
                                        while (i < content.length) {
                                            val c = content[i]
                                            if (c == '.' || c == '!' || c == '?') {
                                                // Skip abbreviations: "Mr.", "e.g.", digits
                                                val nextIsWordChar = i + 1 < content.length &&
                                                        content[i + 1].isLetterOrDigit()
                                                if (!nextIsWordChar) {
                                                    // Advance past trailing whitespace to the
                                                    // first char of the next sentence
                                                    var next = i + 1
                                                    while (next < content.length && content[next].isWhitespace()) next++
                                                    if (next < content.length) offsets.add(next)
                                                }
                                            }
                                            i++
                                        }
                                        val targetOffset = offsets.getOrElse(sentenceIndex) { 0 }
                                        try {
                                            val line = editor.text.indexer.getCharLine(targetOffset)
                                            val col  = editor.text.indexer.getCharColumn(targetOffset)
                                            editor.cursor.set(line, col)
                                            editor.ensurePositionVisible(line, col)
                                        } catch (_: Exception) { }
                                    }
                                }
                            )
                        }
                    }
                }

                if (tabBarAtBottom) {
                    Surface(
                        color    = Color.Transparent,
                        modifier = Modifier.fillMaxWidth().frostedBar(hazeState)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Back to Editor")
                            }
                            tabBarContent()
                            IconButton(onClick = onToggleTabBarPos) {
                                Icon(Icons.Default.VerticalAlignTop, "Move tabs to top",
                                     modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDivider(
    isHorizontal : Boolean,
    onDrag       : (Float) -> Unit,
    onSwap       : () -> Unit,
    accentColor  : Color,
    hazeState    : dev.chrisbanes.haze.HazeState,
) {
    val haptic = LocalHapticFeedback.current

    if (isHorizontal) {
        Box(
            modifier         = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxHeight().width(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(28.dp).height(36.dp).pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwap()
                    })
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint               = accentColor,
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(48.dp).height(28.dp).pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSwap()
                    })
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_scribe_s),
                        contentDescription = "Tap to swap slots",
                        tint               = accentColor,
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var textWidth by remember { mutableFloatStateOf(0f) }
    val animatedFontSize by animateFloatAsState(
        targetValue = if (selected) 15f else 13f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "PillTabFontSize"
    )
    val animatedUnderlineWidth by animateFloatAsState(
        targetValue = if (selected) textWidth else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "PillTabUnderlineWidth"
    )
    val primaryColor = MaterialTheme.colorScheme.primary

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

@Composable
private fun PinnedNoteSlot(
    modifier            : Modifier = Modifier,
    slotKey             : String,
    pinnedIds           : List<String>,
    pinnedIndex         : Int,
    allNotes            : List<Note>,
    worldEntries        : List<WorldEntry>,
    activeTheme         : AppTheme?,
    accentColor         : Color = Color.Unspecified,
    onPrev              : () -> Unit,
    onNext              : () -> Unit,
    onSwitch            : () -> Unit,
    onEdit              : (String) -> Unit,
    onRemove            : (String) -> Unit,
    onPick              : () -> Unit,
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
                Note(id = w.id, name = w.name, content = "${w.type.uppercase()}: ${w.summary}")
            }
    }

    val isDark = isSystemInDarkTheme()
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()

    var slotSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var slotPositionInRoot by remember { mutableStateOf(Offset.Zero) }

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
                modifier            = Modifier.fillMaxSize().clickable(onClick = onPick),
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
                Text("Tap to browse your vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            val currentNoteState by rememberUpdatedState(currentNote)
            val currentSlotSize by rememberUpdatedState(slotSize)
            val currentSlotPosition by rememberUpdatedState(slotPositionInRoot)
            val currentOnStartDetachedDrag by rememberUpdatedState(onStartDetachedDrag)
            val currentOnDetachedDrag by rememberUpdatedState(onDetachedDrag)
            val currentOnEndDetachedDrag by rememberUpdatedState(onEndDetachedDrag)
            val currentOnCancelDetachedDrag by rememberUpdatedState(onCancelDetachedDrag)

            Column(Modifier.fillMaxSize()) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val chipBg = if (accentColor != Color.Unspecified) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                            val chipText = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = chipBg,
                            ) {
                                Text(
                                    "SECTION",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = chipText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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
                        IconButton(onClick = { /* TODO Phase 3 */ }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

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
                        IconButton(onClick = { /* TODO Phase 3: Expand/Focus */ }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.OpenInFull, contentDescription = "Expand note", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { /* TODO Phase 3: Add Reference */ }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add reference", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
