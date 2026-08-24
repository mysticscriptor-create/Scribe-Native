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
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class ActiveDrawerSide {
    NONE,
    LEFT_DRAWER,
    RIGHT_PANEL
}

/**
 * Compact layout (Phones):
 * - Fixed 100% viewport width editor pane with zero reflow
 * - Smooth horizontal touch gesture engine opening Left Drawer and Right Panel
 * - Animatable slide-over overlays with scrim backdrops and critically-damped zero-overshoot physics
 * - Seamless automatic keyboard dismissal on drawer swipe
 * - Zero-gap flush screen anchoring
 */
@Composable
fun CompactEditorLayout(
    hazeState: HazeState,
    barBlurBitmap: Bitmap?,
    isKeyboardVisible: Boolean,
    soraEditorRef: CodeEditor?,
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
                currentOffset.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                )
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

        val gestureModifier = Modifier.pointerInput(drawerWidthPx, panelWidthPx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var isDragging = false
                var isDisallowed = false
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

                    if (pointerChange.changedToUp()) {
                        if (isDragging) {
                            pointerChange.consume()
                            val vx = velocityTracker.calculateVelocity().x
                            val cur = currentOffset.value

                            val target = when (activeSide) {
                                ActiveDrawerSide.LEFT_DRAWER -> {
                                    if (vx < -350f) {
                                        0f
                                    } else if (vx > 350f) {
                                        drawerWidthPx
                                    } else {
                                        if (cur >= drawerWidthPx * 0.35f) drawerWidthPx else 0f
                                    }
                                }
                                ActiveDrawerSide.RIGHT_PANEL -> {
                                    if (vx > 350f) {
                                        0f
                                    } else if (vx < -350f) {
                                        -panelWidthPx
                                    } else {
                                        if (cur <= -panelWidthPx * 0.35f) -panelWidthPx else 0f
                                    }
                                }
                                ActiveDrawerSide.NONE -> 0f
                            }

                            scope.launch {
                                currentOffset.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialVelocity = if (target == 0f) {
                                        if (activeSide == ActiveDrawerSide.LEFT_DRAWER) vx.coerceIn(-1200f, 0f)
                                        else vx.coerceIn(0f, 1200f)
                                    } else {
                                        vx.coerceIn(-2500f, 2500f)
                                    }
                                )
                                if (target == 0f) {
                                    currentOffset.snapTo(0f)
                                }
                            }
                        }
                        break
                    }

                    velocityTracker.addPosition(pointerChange.uptimeMillis, pointerChange.position)

                    if (isDisallowed) continue

                    val dragAmount = pointerChange.positionChange()
                    totalDx += dragAmount.x
                    totalDy += dragAmount.y
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)

                    if (!isDragging) {
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

                                // Close keyboard & clear focus immediately on swipe (Update 4)
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                try {
                                    currentSoraEditorRef?.hideSoftInput()
                                } catch (_: Exception) { }
                            }
                        }
                    } else {
                        pointerChange.consume()
                        val proposed = startOffset + totalDx
                        val clamped = when (activeSide) {
                            ActiveDrawerSide.LEFT_DRAWER -> {
                                if (proposed >= drawerWidthPx) {
                                    drawerWidthPx + (proposed - drawerWidthPx) * 0.15f
                                } else {
                                    proposed.coerceAtLeast(0f)
                                }
                            }
                            ActiveDrawerSide.RIGHT_PANEL -> {
                                if (proposed <= -panelWidthPx) {
                                    -panelWidthPx - (-proposed - panelWidthPx) * 0.15f
                                } else {
                                    proposed.coerceAtMost(0f)
                                }
                            }
                            ActiveDrawerSide.NONE -> 0f
                        }
                        scope.launch {
                            currentOffset.snapTo(clamped)
                        }
                    }
                }
            }
        }

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
                                currentOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                                currentOffset.snapTo(0f)
                            } else {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                try { currentSoraEditorRef?.hideSoftInput() } catch (_: Exception) { }
                                currentOffset.animateTo(
                                    drawerWidthPx,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                        }
                    },
                    {
                        scope.launch {
                            if (currentOffset.value < -panelWidthPx * 0.5f) {
                                currentOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                                currentOffset.snapTo(0f)
                            } else {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                try { currentSoraEditorRef?.hideSoftInput() } catch (_: Exception) { }
                                currentOffset.animateTo(
                                    -panelWidthPx,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
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
                                currentOffset.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                )
                                currentOffset.snapTo(0f)
                            }
                        }
                )
            }

            // ── Layer 3: Left Drawer (Slides in from Left, Zero-Gap Anchored) ────
            if (currentOffset.value > 0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidthDp)
                        .graphicsLayer {
                            translationX = (-drawerWidthPx + currentOffset.value).coerceAtMost(0f)
                        }
                ) {
                    CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
                        ModalDrawerSheet(
                            drawerContainerColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxSize()
                                .frostedPanel(hazeState)
                        ) {
                            leftDrawerContent {
                                scope.launch {
                                    currentOffset.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                    )
                                    currentOffset.snapTo(0f)
                                }
                            }
                        }
                    }
                }
            }

            // ── Layer 4: Right Panel (Slides in from Right, Zero-Gap Anchored) ───
            if (currentOffset.value < -0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (screenWidthPx + currentOffset.value).coerceAtLeast(0f)
                        }
                ) {
                    rightPanelContent {
                        scope.launch {
                            currentOffset.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            )
                            currentOffset.snapTo(0f)
                        }
                    }
                }
            }
        }
    }
}
