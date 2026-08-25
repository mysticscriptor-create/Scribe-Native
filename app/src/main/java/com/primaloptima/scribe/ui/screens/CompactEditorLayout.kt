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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

private enum class ActiveDrawerSide {
    NONE,
    LEFT_DRAWER,
    RIGHT_PANEL
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

                    // 2a: Capture all pointer events (including the up event) into VelocityTracker FIRST
                    velocityTracker.addPosition(pointerChange.uptimeMillis, pointerChange.position)

                    if (pointerChange.changedToUp()) {
                        if (isDragging) {
                            pointerChange.consume()
                            val vx = velocityTracker.calculateVelocity().x
                            val cur = currentOffset.value

                            // 2b: Directional momentum & velocity threshold
                            val target = when (activeSide) {
                                ActiveDrawerSide.LEFT_DRAWER -> {
                                    if (vx > 180f) {
                                        drawerWidthPx
                                    } else if (vx < -180f) {
                                        0f
                                    } else {
                                        if (cur >= drawerWidthPx * 0.35f) drawerWidthPx else 0f
                                    }
                                }
                                ActiveDrawerSide.RIGHT_PANEL -> {
                                    if (vx < -180f) {
                                        -panelWidthPx
                                    } else if (vx > 180f) {
                                        0f
                                    } else {
                                        if (cur <= -panelWidthPx * 0.35f) -panelWidthPx else 0f
                                    }
                                }
                                ActiveDrawerSide.NONE -> 0f
                            }

                            scope.launch {
                                val isOpening = target != 0f
                                val spec = if (isOpening) openSpringSpec else closeSpringSpec
                                // 2c: Pass finger release velocity into spring
                                val initVel = if (isOpening) {
                                    vx.coerceIn(-3500f, 3500f)
                                } else {
                                    if (activeSide == ActiveDrawerSide.LEFT_DRAWER) vx.coerceIn(-2000f, 0f)
                                    else vx.coerceIn(0f, 2000f)
                                }

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
                        // 1: Subtract touch slop so drawer moves continuously from 0px without pop/jump
                        val adjustedDx = totalDx - sign(totalDx) * touchSlop
                        val proposed = startOffset + adjustedDx

                        // Apply exponential resistance for overdrag past boundaries
                        val clamped = when (activeSide) {
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
                                currentOffset.animateTo(0f, closeSpringSpec)
                                currentOffset.snapTo(0f)
                            } else {
                                focusManager.clearFocus(force = true)
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
                                focusManager.clearFocus(force = true)
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

            // ── Layer 3: Left Drawer (Slides in from Left, Zero-Gap Anchored with Elastic Stretch) ────
            if (currentOffset.value > 0.5f) {
                val overdragPx = (currentOffset.value - drawerWidthPx).coerceAtLeast(0f)
                val stretchScaleX = 1f + (overdragPx / drawerWidthPx) * 0.12f
                val stretchTranslationX = overdragPx * 0.18f

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidthDp)
                        .graphicsLayer {
                            // Background/container strictly anchored to left edge (Zero gaps)
                            translationX = (-drawerWidthPx + currentOffset.value).coerceAtMost(0f)
                        }
                ) {
                    CompositionLocalProvider(LocalOneShotBitmap provides LocalBarBlurBitmap.current) {
                        ModalDrawerSheet(
                            drawerContainerColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxSize()
                                .frostedPanel(hazeState)
                                .graphicsLayer {
                                    // 3: Tactile rubber-band content stretch anchored to left edge
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = stretchScaleX
                                    translationX = stretchTranslationX
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
                }
            }

            // ── Layer 4: Right Panel (Slides in from Right, Zero-Gap Anchored with Elastic Stretch) ───
            if (currentOffset.value < -0.5f) {
                val overdragPx = (-panelWidthPx - currentOffset.value).coerceAtLeast(0f)
                val stretchScaleX = 1f + (overdragPx / panelWidthPx) * 0.12f
                val stretchTranslationX = -overdragPx * 0.18f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Background/container strictly anchored to right edge (Zero gaps)
                            translationX = (screenWidthPx + currentOffset.value).coerceAtLeast(0f)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 3: Tactile rubber-band content stretch anchored to right edge
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                scaleX = stretchScaleX
                                translationX = stretchTranslationX
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
