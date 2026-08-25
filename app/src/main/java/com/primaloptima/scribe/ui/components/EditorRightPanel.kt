package com.primaloptima.scribe.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.ScribeColorScheme
import com.primaloptima.scribe.ui.theme.frostedBar
import com.primaloptima.scribe.ui.theme.frostedCard
import com.primaloptima.scribe.ui.theme.frostedPanel
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
    val initialTouchX: Float,
    val initialTouchY: Float,
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
        Surface(
            shape    = RoundedCornerShape(50),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(3.dp)
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                PillTab(label = "Pinned",  selected = rightPanelTab == 0, onClick = { onTabChange(0) })
                PillTab(label = "Outline", selected = rightPanelTab == 1, onClick = { onTabChange(1) })
                PillTab(label = "Prose",   selected = rightPanelTab == 2, onClick = { onTabChange(2) })
            }
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
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val totalPxFloat = with(density) {
                                    if (splitHorizontal) maxWidth.toPx() else maxHeight.toPx()
                                }

                                var dragState by remember { mutableStateOf<DetachedDragState?>(null) }
                                var activeHoveredZone by remember { mutableStateOf<SpatialDropZone?>(null) }

                                val containerWidthPx = with(density) { maxWidth.toPx() }
                                val containerHeightPx = with(density) { maxHeight.toPx() }

                                fun computeHoveredZone(touchX: Float, touchY: Float): SpatialDropZone? {
                                    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null
                                    val relX = (touchX / containerWidthPx).coerceIn(0f, 1f)
                                    val relY = (touchY / containerHeightPx).coerceIn(0f, 1f)

                                    val distTop = relY
                                    val distBottom = 1f - relY
                                    val distLeft = relX
                                    val distRight = 1f - relX

                                    val minDist = minOf(distTop, distBottom, distLeft, distRight)
                                    return when (minDist) {
                                        distTop -> SpatialDropZone.TOP
                                        distBottom -> SpatialDropZone.BOTTOM
                                        distLeft -> SpatialDropZone.LEFT
                                        else -> SpatialDropZone.RIGHT
                                    }
                                }

                                val onStartDetachedDrag: (slot: String, note: Note, touchPos: Offset, size: androidx.compose.ui.geometry.Size) -> Unit = { slot, note, touchPos, size ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragState = DetachedDragState(
                                        originSlot = slot,
                                        note = note,
                                        initialTouchX = touchPos.x,
                                        initialTouchY = touchPos.y,
                                        currentDragX = 0f,
                                        currentDragY = 0f,
                                        initialSlotWidth = size.width,
                                        initialSlotHeight = size.height,
                                    )
                                    activeHoveredZone = computeHoveredZone(touchPos.x, touchPos.y)
                                }

                                val onContinueDetachedDrag: (dx: Float, dy: Float) -> Unit = { dx, dy ->
                                    dragState?.let { current ->
                                        val newDragX = current.currentDragX + dx
                                        val newDragY = current.currentDragY + dy
                                        dragState = current.copy(
                                            currentDragX = newDragX,
                                            currentDragY = newDragY
                                        )
                                        val currentTouchX = current.initialTouchX + newDragX
                                        val currentTouchY = current.initialTouchY + newDragY
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

                                Box(Modifier.fillMaxSize()) {
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
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "top",
                                                onStartDetachedDrag = { note, touchPos, size ->
                                                    onStartDetachedDrag("top", note, touchPos, size)
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
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "bottom",
                                                onStartDetachedDrag = { note, touchPos, size ->
                                                    onStartDetachedDrag("bottom", note, touchPos, size)
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
                                                onPrev              = onPrevTop,
                                                onNext              = onNextTop,
                                                onSwitch            = onSwitchTop,
                                                onEdit              = onEditTop,
                                                onRemove            = onRemoveTop,
                                                onPick              = onPickTop,
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "top",
                                                onStartDetachedDrag = { note, touchPos, size ->
                                                    onStartDetachedDrag("top", note, touchPos, size)
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
                                                onPrev              = onPrevBottom,
                                                onNext              = onNextBottom,
                                                onSwitch            = onSwitchBottom,
                                                onEdit              = onEditBottom,
                                                onRemove            = onRemoveBottom,
                                                onPick              = onPickBottom,
                                                hazeState           = hazeState,
                                                isDetached          = dragState?.originSlot == "bottom",
                                                onStartDetachedDrag = { note, touchPos, size ->
                                                    onStartDetachedDrag("bottom", note, touchPos, size)
                                                },
                                                onDetachedDrag      = onContinueDetachedDrag,
                                                onEndDetachedDrag   = onEndDetachedDrag,
                                                onCancelDetachedDrag= onCancelDetachedDrag,
                                            )
                                        }
                                    }

                                    // ── Spatial 4-Zone Glass Blueprint Dock Overlay ──
                                    AnimatedVisibility(
                                        visible = dragState != null,
                                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                                        exit = fadeOut(tween(160, easing = FastOutSlowInEasing)),
                                        modifier = Modifier.fillMaxSize().zIndex(10f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.28f))
                                                .padding(6.dp)
                                        ) {
                                            // Vertical Pair (Top / Bottom) - outer bounds
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                SpatialDropZoneCard(
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    label = "Top Slot",
                                                    icon = Icons.Outlined.Splitscreen,
                                                    isHighlighted = activeHoveredZone == SpatialDropZone.TOP,
                                                    accentColor = accentColor,
                                                )
                                                SpatialDropZoneCard(
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    label = "Bottom Slot",
                                                    icon = Icons.Outlined.Splitscreen,
                                                    isHighlighted = activeHoveredZone == SpatialDropZone.BOTTOM,
                                                    accentColor = accentColor,
                                                )
                                            }

                                            // Horizontal Pair (Left / Right) - nested inner glass targets
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                SpatialDropZoneCard(
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    label = "Left Slot",
                                                    icon = Icons.Outlined.VerticalSplit,
                                                    isHighlighted = activeHoveredZone == SpatialDropZone.LEFT,
                                                    accentColor = accentColor,
                                                )
                                                SpatialDropZoneCard(
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    label = "Right Slot",
                                                    icon = Icons.Outlined.VerticalSplit,
                                                    isHighlighted = activeHoveredZone == SpatialDropZone.RIGHT,
                                                    accentColor = accentColor,
                                                )
                                            }
                                        }
                                    }

                                    // ── Detached Dragging Card Floating Layer ──────
                                    dragState?.let { activeDrag ->
                                        val cardWidthDp = with(density) { (activeDrag.initialSlotWidth.coerceAtLeast(180f)).toDp() }
                                        val cardHeightDp = with(density) { (activeDrag.initialSlotHeight.coerceIn(120f, 320f)).toDp() }

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
                                                            x = activeDrag.currentDragX.roundToInt(),
                                                            y = activeDrag.currentDragY.roundToInt()
                                                        )
                                                    }
                                                    .graphicsLayer {
                                                        scaleX = 1.04f
                                                        scaleY = 1.04f
                                                        alpha = 0.94f
                                                        shadowElevation = 18.dp.toPx()
                                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                                    }
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = accentColor.copy(alpha = 0.85f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                                tonalElevation = 8.dp
                                            ) {
                                                Column(Modifier.fillMaxSize()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(accentColor.copy(alpha = 0.16f))
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
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                    detectDragGestures { change, dragAmount ->
                        change.consume(); onDrag(dragAmount.x)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(26.dp).pointerInput(Unit) {
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
                    detectDragGestures { change, dragAmount ->
                        change.consume(); onDrag(dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(
                shape    = RoundedCornerShape(50),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(26.dp).width(44.dp).pointerInput(Unit) {
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
    Surface(
        shape           = RoundedCornerShape(50),
        color           = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier        = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SpatialDropZoneCard(
    modifier     : Modifier = Modifier,
    label        : String,
    icon         : androidx.compose.ui.graphics.vector.ImageVector,
    isHighlighted: Boolean,
    accentColor  : Color,
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = tween(150),
        label = "SpatialDropZoneBorder"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
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
                width = if (isHighlighted) 2.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        color = animatedBgColor,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isHighlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    onPrev              : () -> Unit,
    onNext              : () -> Unit,
    onSwitch            : () -> Unit,
    onEdit              : (String) -> Unit,
    onRemove            : (String) -> Unit,
    onPick              : () -> Unit,
    hazeState           : dev.chrisbanes.haze.HazeState,
    isDetached          : Boolean = false,
    onStartDetachedDrag : ((note: Note, touchPos: Offset, size: androidx.compose.ui.geometry.Size) -> Unit)? = null,
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

    var slotSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val boundsRegistry = LocalInteractiveBoundsRegistry.current
    val boundsKey = remember(slotKey) { "pinned_note_slot_$slotKey" }

    DisposableEffect(boundsRegistry, boundsKey) {
        onDispose {
            boundsRegistry?.unregisterBounds(boundsKey)
        }
    }

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
            .frostedCard(hazeState, RoundedCornerShape(12.dp), applyFallbackBackground = true)
            .onGloballyPositioned { layoutCoordinates ->
                slotSize = androidx.compose.ui.geometry.Size(
                    layoutCoordinates.size.width.toFloat(),
                    layoutCoordinates.size.height.toFloat()
                )
                boundsRegistry?.registerBounds(boundsKey, layoutCoordinates.boundsInRoot())
            }
    ) {
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
            Column(Modifier.fillMaxSize()) {
                var headerDragging by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (headerDragging || isDetached) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
                        .pointerInput(currentNote, slotSize, onStartDetachedDrag, onDetachedDrag, onEndDetachedDrag, onCancelDetachedDrag) {
                            if (onStartDetachedDrag == null) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    headerDragging = true
                                    onStartDetachedDrag(currentNote, offset, slotSize)
                                },
                                onDragEnd = {
                                    headerDragging = false
                                    onEndDetachedDrag?.invoke()
                                },
                                onDragCancel = {
                                    headerDragging = false
                                    onCancelDetachedDrag?.invoke()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDetachedDrag?.invoke(dragAmount.x, dragAmount.y)
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape    = RoundedCornerShape(50),
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "Long press and drag to reposition",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp).padding(end = 2.dp)
                            )
                            Text(
                                text       = currentNote.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 12.sp,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pinnedIds.size > 1) {
                        Text("${pinnedIndex + 1}/${pinnedIds.size}", fontSize = 10.sp,
                             color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 2.dp))
                        IconButton(onClick = onPrev, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(14.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp))
                        }
                    }
                    IconButton(onClick = onSwitch, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.SwapHoriz, "Switch note", Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onEdit(currentNote.id) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, "Edit in main editor", Modifier.size(14.dp))
                    }
                    IconButton(onClick = { onRemove(currentNote.id) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Close, "Unpin", Modifier.size(14.dp))
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
            }
        }
    }
}
