package com.primaloptima.scribe.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedPanel
import dev.chrisbanes.haze.HazeState
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

private fun isTouchOnSelectionHandle(editor: CodeEditor?, touchX: Float, touchY: Float, density: Float): Boolean {
    if (editor == null) return false
    return try {
        val cursor = editor.cursor ?: return false
        val layout = editor.layout ?: return false
        val textRegionOffset = editor.measureTextRegionOffset()
        val scrollX = editor.offsetX.toFloat()
        val scrollY = editor.offsetY.toFloat()
        val rowHeight = editor.rowHeight.toFloat()

        /**
         * Checks if (touchX, touchY) hits a handle anchored at (anchorX, anchorY).
         * @param handleType -1 for Left/Start handle (teardrop hangs left),
         *                    1 for Right/End handle (teardrop hangs right),
         *                    0 for Insertion handle (teardrop centered below cursor)
         */
        fun checkHandleHit(anchorX: Float, anchorY: Float, handleType: Int): Boolean {
            // Teardrop bulb center offset relative to the anchor tip
            val bulbOffsetX = when (handleType) {
                -1 -> -16f * density // Bulb hangs down-left
                1 -> 16f * density  // Bulb hangs down-right
                else -> 0f          // Bulb hangs directly down
            }
            val bulbCenterY = anchorY + (18f * density)
            val bulbCenterX = anchorX + bulbOffsetX

            // 1. Check Euclidean distance from teardrop bulb center (covers full circular bulb + finger pad)
            val bulbRadius = 42f * density
            val dxBulb = touchX - bulbCenterX
            val dyBulb = touchY - bulbCenterY
            if ((dxBulb * dxBulb + dyBulb * dyBulb) <= (bulbRadius * bulbRadius)) {
                return true
            }

            // 2. Check Euclidean distance from anchor tip and cursor baseline
            val anchorRadius = 32f * density
            val dxAnchor = touchX - anchorX
            val dyAnchor = touchY - anchorY
            if ((dxAnchor * dxAnchor + dyAnchor * dyAnchor) <= (anchorRadius * anchorRadius)) {
                return true
            }

            // 3. Check composite rectangular bounding box with generous touch padding
            val minX = when (handleType) {
                -1 -> anchorX - (52f * density)
                1 -> anchorX - (24f * density)
                else -> anchorX - (38f * density)
            }
            val maxX = when (handleType) {
                -1 -> anchorX + (24f * density)
                1 -> anchorX + (52f * density)
                else -> anchorX + (38f * density)
            }
            val minY = anchorY - rowHeight - (8f * density)
            val maxY = anchorY + (56f * density)

            return touchX in minX..maxX && touchY in minY..maxY
        }

        if (cursor.isSelected) {
            // Left Handle (Start Selection Handle)
            val leftOffset = layout.getCharLayoutOffset(cursor.leftLine, cursor.leftColumn)
            if (leftOffset != null && leftOffset.size >= 2) {
                val leftCharX = leftOffset[1] + textRegionOffset - scrollX
                val leftCharY = leftOffset[0] - scrollY
                val anchorY = leftCharY + rowHeight
                if (checkHandleHit(leftCharX, anchorY, handleType = -1)) {
                    return true
                }
            }

            // Right Handle (End Selection Handle)
            val rightOffset = layout.getCharLayoutOffset(cursor.rightLine, cursor.rightColumn)
            if (rightOffset != null && rightOffset.size >= 2) {
                val rightCharX = rightOffset[1] + textRegionOffset - scrollX
                val rightCharY = rightOffset[0] - scrollY
                val anchorY = rightCharY + rowHeight
                if (checkHandleHit(rightCharX, anchorY, handleType = 1)) {
                    return true
                }
            }
        } else {
            // Insertion Handle (Single Cursor)
            val curOffset = layout.getCharLayoutOffset(cursor.leftLine, cursor.leftColumn)
            if (curOffset != null && curOffset.size >= 2) {
                val curCharX = curOffset[1] + textRegionOffset - scrollX
                val curCharY = curOffset[0] - scrollY
                val anchorY = curCharY + rowHeight
                if (checkHandleHit(curCharX, anchorY, handleType = 0)) {
                    return true
                }
            }
        }

        false
    } catch (_: Exception) {
        false
    }
}

val LocalInteractiveBoundsRegistry = androidx.compose.runtime.compositionLocalOf<(key: String, bounds: Rect?) -> Unit> {
    { _, _ -> }
}

private enum class ActiveDrawerSide {
    NONE,
    LEFT_DRAWER,
    RIGHT_PANEL
}

private fun calculateClampedOffset(
    proposed: Float,
    activeSide: ActiveDrawerSide,
    drawerWidthPx: Float,
    panelWidthPx: Float
): Float {
    return when (activeSide) {
        ActiveDrawerSide.LEFT_DRAWER -> {
            if (proposed > drawerWidthPx) {
                val over = proposed - drawerWidthPx
                drawerWidthPx + 140f * (1f - exp(-over / 220f))
            } else {
                proposed.coerceAtLeast(0f)
            }
        }
        ActiveDrawerSide.RIGHT_PANEL -> {
            if (proposed < -panelWidthPx) {
                val over = -proposed - panelWidthPx
                -panelWidthPx - 140f * (1f - exp(-over / 220f))
            } else {
                proposed.coerceAtMost(0f)
            }
        }
        ActiveDrawerSide.NONE -> 0f
    }
}

/**
 * Compact layout (Phones):
 * - Fixed 100% viewport width editor pane with zero reflow
 * - Smooth horizontal touch gesture engine opening Left Drawer and Right Panel
 * - Touch-slop subtraction for continuous, pop-free drag initiation
 * - Energy & momentum-aware flick detection riding finger throw velocity
 * - Asymmetric spring physics (fluid settle bounce on open, critically-damped on close)
 * - Tactile edge-pinned elastic content rubber-band stretching on overdrag
 * - Seamless automatic keyboard dismissal on drawer swipe
 * - Zero-gap flush screen anchoring
 */
@Composable
fun CompactEditorLayout(
    hazeState: HazeState,
    barBlurBitmap: Bitmap?,
    isKeyboardVisible: Boolean,
    soraEditorRef: CodeEditor?,
    isHandleDragging: Boolean = false,
    focusManager: FocusManager,
    editorContent: @Composable (
        onNavClick: () -> Unit,
        onOpenRightPanel: () -> Unit,
        isLeftDrawerOpen: Boolean
    ) -> Unit,
    leftDrawerContent: @Composable (onClose: () -> Unit) -> Unit,
    rightPanelContent: @Composable (onClose: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val currentOffset = remember { Animatable(0f) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Spring specs: Asymmetric physics
    val openSpringSpec = remember {
        spring<Float>(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessLow
        )
    }
    val closeSpringSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = constraints.maxWidth.toFloat()
        val drawerWidthPx = with(density) { 320.dp.toPx() }.coerceAtMost(screenWidthPx * 0.82f)
        val drawerWidthDp = with(density) { drawerWidthPx.toDp() }
        val panelWidthPx  = screenWidthPx

        val isLeftDrawerOpen = currentOffset.value > drawerWidthPx * 0.5f
        val isRightPanelOpen = currentOffset.value < -panelWidthPx * 0.5f

        // Integrated Android Hardware / Predictive Back Handler
        BackHandler(enabled = isLeftDrawerOpen || isRightPanelOpen) {
            scope.launch {
                currentOffset.animateTo(0f, closeSpringSpec)
                currentOffset.snapTo(0f)
            }
        }

        // Dismiss Sora's text-action popup when a panel is significantly opening/open
        val isPanelOpen = abs(currentOffset.value) > 20f
        LaunchedEffect(isPanelOpen) {
            if (isPanelOpen) {
                soraEditorRef?.let { editor ->
                    try {
                        editor.getComponent(
                            io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
                        ).dismiss()
                    } catch (_: Exception) { }
                }
            }
        }

        // ── Unified 3-Pane Gesture Engine ─────────────────────────────────────
        val currentSoraEditorRef by rememberUpdatedState(soraEditorRef)
        val currentIsHandleDragging by rememberUpdatedState(isHandleDragging)
        val interactiveBoundsMap = remember { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
        val registerBounds: (String, Rect?) -> Unit = remember {
            { key, bounds ->
                if (bounds != null) {
                    interactiveBoundsMap[key] = bounds
                } else {
                    interactiveBoundsMap.remove(key)
                }
            }
        }

        val gestureModifier = Modifier.pointerInput(drawerWidthPx, panelWidthPx) {
            val touchSlop = viewConfiguration.touchSlop
            var runningAnimationJob: Job? = null

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                
                // Stop active animation when a new pointer goes down to allow seamless mid-flight touch catch
                runningAnimationJob?.cancel()
                scope.launch { currentOffset.stop() }

                val velocityTracker = VelocityTracker()
                velocityTracker.resetTracking()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var isDragging = false
                var isDisallowed = false

                val isInsideInteractiveArea = interactiveBoundsMap.values.any { rect ->
                    rect.contains(down.position)
                }

                val isHittingSelectionHandle = isTouchOnSelectionHandle(
                    editor = currentSoraEditorRef,
                    touchX = down.position.x,
                    touchY = down.position.y,
                    density = density.density
                )

                if (isInsideInteractiveArea || currentIsHandleDragging || isHittingSelectionHandle) {
                    isDisallowed = true
                }

                var totalDx = 0f
                var totalDy = 0f
                val startOffset = currentOffset.value

                var activeSide = when {
                    startOffset > 10f -> ActiveDrawerSide.LEFT_DRAWER
                    startOffset < -10f -> ActiveDrawerSide.RIGHT_PANEL
                    else -> ActiveDrawerSide.NONE
                }

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break

                    // 2a: Continuous position tracking without stationary zero-delta UP dampening
                    if (pointerChange.positionChange() != Offset.Zero) {
                        velocityTracker.addPosition(pointerChange.uptimeMillis, pointerChange.position)
                    }

                    if (pointerChange.changedToUp()) {
                        if (isDragging) {
                            pointerChange.consume()
                            val vx = velocityTracker.calculateVelocity().x
                            val cur = currentOffset.value

                            // 2b: Directional momentum & velocity threshold with energy projection
                            val target = when (activeSide) {
                                ActiveDrawerSide.LEFT_DRAWER -> {
                                    if (startOffset < drawerWidthPx * 0.5f) {
                                        // Opening Left Drawer
                                        val projected = cur + (vx * 0.16f)
                                        val isFlickOpen = vx > 100f || (totalDx > touchSlop * 2f && vx > -150f)
                                        if (isFlickOpen || projected >= drawerWidthPx * 0.30f || cur >= drawerWidthPx * 0.35f) {
                                            drawerWidthPx
                                        } else {
                                            0f
                                        }
                                    } else {
                                        // Closing Left Drawer
                                        val projected = cur + (vx * 0.16f)
                                        val isFlickClose = vx < -100f || (totalDx < -touchSlop * 2f && vx < 150f)
                                        if (isFlickClose || projected < drawerWidthPx * 0.70f || cur < drawerWidthPx * 0.65f) {
                                            0f
                                        } else {
                                            drawerWidthPx
                                        }
                                    }
                                }
                                ActiveDrawerSide.RIGHT_PANEL -> {
                                    if (startOffset > -panelWidthPx * 0.5f) {
                                        // Opening Right Panel
                                        val projected = cur + (vx * 0.16f)
                                        val isFlickOpen = vx < -100f || (totalDx < -touchSlop * 2f && vx < 150f)
                                        if (isFlickOpen || projected <= -panelWidthPx * 0.30f || cur <= -panelWidthPx * 0.35f) {
                                            -panelWidthPx
                                        } else {
                                            0f
                                        }
                                    } else {
                                        // Closing Right Panel
                                        val projected = cur + (vx * 0.16f)
                                        val isFlickClose = vx > 100f || (totalDx > touchSlop * 2f && vx > -150f)
                                        if (isFlickClose || projected > -panelWidthPx * 0.70f || cur > -panelWidthPx * 0.65f) {
                                            0f
                                        } else {
                                            -panelWidthPx
                                        }
                                    }
                                }
                                ActiveDrawerSide.NONE -> 0f
                            }

                            val isOpening = target != 0f
                            val spec = if (isOpening) openSpringSpec else closeSpringSpec

                            // 2c: Pass finger release velocity directly into the spring
                            val initVel = when {
                                target == drawerWidthPx -> vx.coerceIn(0f, 4000f)
                                target == -panelWidthPx -> vx.coerceIn(-4000f, 0f)
                                activeSide == ActiveDrawerSide.LEFT_DRAWER -> vx.coerceIn(-3000f, 0f)
                                activeSide == ActiveDrawerSide.RIGHT_PANEL -> vx.coerceIn(0f, 3000f)
                                else -> 0f
                            }

                            runningAnimationJob = scope.launch {
                                currentOffset.animateTo(
                                    targetValue = target,
                                    animationSpec = spec,
                                    initialVelocity = initVel
                                )
                                if (target == 0f) {
                                    currentOffset.snapTo(0f)
                                }
                            }
                        }
                        break
                    }

                    if (isDisallowed) continue

                    val dragAmount = pointerChange.positionChange()
                    totalDx += dragAmount.x
                    totalDy += dragAmount.y
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)

                    if (!isDragging) {
                        if (currentIsHandleDragging) {
                            isDisallowed = true
                            continue
                        }
                        if (absX > touchSlop || absY > touchSlop) {
                            if (absY > absX * 1.15f || absX < touchSlop) {
                                // Dominantly vertical movement -> yield to editor or list vertical scrolling
                                isDisallowed = true
                            } else if (absX > touchSlop && absX > absY * 1.15f) {
                                // Dominantly horizontal movement -> engage drawer drag
                                isDragging = true
                                pointerChange.consume()

                                if (activeSide == ActiveDrawerSide.NONE) {
                                    activeSide = if (totalDx > 0f) ActiveDrawerSide.LEFT_DRAWER else ActiveDrawerSide.RIGHT_PANEL
                                }

                                // Close soft keyboard on swipe while preserving text selection in Sora Editor
                                keyboardController?.hide()
                                try {
                                    currentSoraEditorRef?.hideSoftInput()
                                } catch (_: Exception) { }

                                // 1: Subtract touch slop and immediately update offset on the first frame (zero latency)
                                val adjustedDx = totalDx - sign(totalDx) * touchSlop
                                val proposed = startOffset + adjustedDx
                                val clamped = calculateClampedOffset(proposed, activeSide, drawerWidthPx, panelWidthPx)
                                scope.launch { currentOffset.snapTo(clamped) }
                            }
                        }
                    } else {
                        pointerChange.consume()
                        // 1: Subtract touch slop so drawer moves continuously from 0px without pop/jump
                        val adjustedDx = totalDx - sign(totalDx) * touchSlop
                        val proposed = startOffset + adjustedDx
                        val clamped = calculateClampedOffset(proposed, activeSide, drawerWidthPx, panelWidthPx)
                        scope.launch { currentOffset.snapTo(clamped) }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalInteractiveBoundsRegistry provides registerBounds
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(gestureModifier)
            ) {
                // ── Layer 1: Editor Main Pane (Fixed Width = 100% stable, zero reflow) ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = currentOffset.value * 0.18f
                        }
                ) {
                    editorContent(
                        {
                            scope.launch {
                                if (currentOffset.value > drawerWidthPx * 0.5f) {
                                    currentOffset.animateTo(0f, closeSpringSpec)
                                    currentOffset.snapTo(0f)
                                } else {
                                    keyboardController?.hide()
                                    try { currentSoraEditorRef?.hideSoftInput() } catch (_: Exception) { }
                                    currentOffset.animateTo(drawerWidthPx, openSpringSpec)
                                }
                            }
                        },
                        {
                            scope.launch {
                                if (currentOffset.value < -panelWidthPx * 0.5f) {
                                    currentOffset.animateTo(0f, closeSpringSpec)
                                    currentOffset.snapTo(0f)
                                } else {
                                    keyboardController?.hide()
                                    try { currentSoraEditorRef?.hideSoftInput() } catch (_: Exception) { }
                                    currentOffset.animateTo(-panelWidthPx, openSpringSpec)
                                }
                            }
                        },
                        isLeftDrawerOpen
                    )
                }

                // ── Layer 2: Dim / Scrim Backdrop Overlay ─────────────────────────
                val scrimAlpha = when {
                    currentOffset.value > 0f -> (currentOffset.value / drawerWidthPx).coerceIn(0f, 1f) * 0.45f
                    currentOffset.value < 0f -> (abs(currentOffset.value) / panelWidthPx).coerceIn(0f, 1f) * 0.45f
                    else -> 0f
                }
                if (scrimAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = scrimAlpha }
                            .background(Color.Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    currentOffset.animateTo(0f, closeSpringSpec)
                                    currentOffset.snapTo(0f)
                                }
                            }
                    )
                }

                // ── Layer 3: Left Drawer (Slides in from Left, Zero-Gap Anchored with Full-Height Elastic Stretch) ────
                if (currentOffset.value > 0.5f) {
                    val overdragPx = (currentOffset.value - drawerWidthPx).coerceAtLeast(0f)
                    val stretchScaleX = 1f + (overdragPx / drawerWidthPx) * 0.12f

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(drawerWidthDp)
                            .graphicsLayer {
                                // Background/container strictly anchored to left edge (Zero gaps)
                                translationX = (-drawerWidthPx + currentOffset.value).coerceAtMost(0f)
                                // Full-height (including status bar) tactile rubber-band elastic stretch
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                scaleX = stretchScaleX
                            }
                    ) {
                        leftDrawerContent {
                            scope.launch {
                                currentOffset.animateTo(0f, closeSpringSpec)
                                currentOffset.snapTo(0f)
                            }
                        }
                    }
                }

                // ── Layer 4: Right Panel (Slides in from Right, Zero-Gap Anchored with Full-Height Elastic Stretch) ───
                if (currentOffset.value < -0.5f) {
                    val overdragPx = (-panelWidthPx - currentOffset.value).coerceAtLeast(0f)
                    val stretchScaleX = 1f + (overdragPx / panelWidthPx) * 0.12f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Background/container strictly anchored to right edge (Zero gaps)
                                translationX = (screenWidthPx + currentOffset.value).coerceAtLeast(0f)
                                // Full-height (including status bar) tactile rubber-band elastic stretch
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                scaleX = stretchScaleX
                            }
                    ) {
                        rightPanelContent {
                            scope.launch {
                                currentOffset.animateTo(0f, closeSpringSpec)
                                currentOffset.snapTo(0f)
                            }
                        }
                    }
                }
            }
        }
    }
}