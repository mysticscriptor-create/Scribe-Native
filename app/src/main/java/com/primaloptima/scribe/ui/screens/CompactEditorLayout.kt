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
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.ui.theme.LocalBarBlurBitmap
import com.primaloptima.scribe.ui.theme.LocalOneShotBitmap
import com.primaloptima.scribe.ui.theme.frostedPanel
import dev.chrisbanes.haze.HazeState
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Compact layout (Phones):
 * - Fixed 100% viewport width editor pane with zero reflow
 * - Smooth horizontal touch gesture engine opening Left Drawer and Right Panel
 * - Animatable slide-over overlays with scrim backdrops and velocity springs
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
            }
        }

        // Dismiss Sora's text-action popup when a panel opens
        val isPanelOpen = abs(currentOffset.value) > 20f
        LaunchedEffect(isPanelOpen) {
            if (isPanelOpen) {
                soraEditorRef?.let { editor ->
                    if (editor.cursor.isSelected) {
                        editor.setSelection(editor.cursor.leftLine, editor.cursor.leftColumn)
                    }
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

        val gestureModifier = if (!isKeyboardVisible) {
            Modifier.pointerInput(drawerWidthPx, panelWidthPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var isDragging = false
                    var isDisallowed = false
                    var totalDx = 0f
                    var totalDy = 0f
                    val startOffset = currentOffset.value

                    // If editor is active & centered, check if text selection is active
                    val isEditorSelected = currentSoraEditorRef?.cursor?.isSelected == true
                    if (abs(startOffset) < 1f && isEditorSelected) {
                        isDisallowed = true
                    }

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (pointerChange.changedToUp()) {
                            if (isDragging) {
                                pointerChange.consume()
                                val vx = velocityTracker.calculateVelocity().x
                                val cur = currentOffset.value

                                val target = when {
                                    startOffset > drawerWidthPx * 0.5f -> {
                                        // Starting from Left Drawer: swipe left to close
                                        if (vx < -500f || cur < drawerWidthPx * 0.7f) 0f else drawerWidthPx
                                    }
                                    startOffset < -panelWidthPx * 0.5f -> {
                                        // Starting from Right Panel: swipe right to close
                                        if (vx > 500f || cur > -panelWidthPx * 0.7f) 0f else -panelWidthPx
                                    }
                                    else -> {
                                        // Starting from Editor: swipe right for Left Drawer, swipe left for Right Panel
                                        if (vx > 600f || cur > drawerWidthPx * 0.35f) drawerWidthPx
                                        else if (vx < -600f || cur < -panelWidthPx * 0.35f) -panelWidthPx
                                        else 0f
                                    }
                                }
                                scope.launch {
                                    currentOffset.animateTo(
                                        targetValue = target,
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
                                        initialVelocity = vx
                                    )
                                }
                            }
                            break
                        }

                        velocityTracker.addPosition(pointerChange.uptimeMillis, pointerChange.position)

                        if (pointerChange.isConsumed && !isDragging) {
                            isDisallowed = true
                        }
                        if (isDisallowed) continue

                        val dragAmount = pointerChange.positionChange()
                        totalDx += dragAmount.x
                        totalDy += dragAmount.y
                        val absX = abs(totalDx)
                        val absY = abs(totalDy)

                        if (!isDragging) {
                            if (absX > touchSlop || absY > touchSlop) {
                                if (absY >= absX || absX < touchSlop) {
                                    // Dominantly vertical movement -> yield to editor/list scroll
                                    isDisallowed = true
                                } else if (absX > touchSlop && absX > absY * 1.35f) {
                                    // Dominantly horizontal movement
                                    isDragging = true
                                    pointerChange.consume()
                                    if (abs(startOffset) < 1f) {
                                        focusManager.clearFocus()
                                    }
                                }
                            }
                        } else {
                            pointerChange.consume()
                            val proposed = startOffset + totalDx
                            val clamped = when {
                                startOffset > drawerWidthPx * 0.5f -> {
                                    proposed.coerceIn(0f, drawerWidthPx + (proposed - drawerWidthPx).coerceAtLeast(0f) * 0.15f)
                                }
                                startOffset < -panelWidthPx * 0.5f -> {
                                    proposed.coerceIn(-panelWidthPx - (-proposed - panelWidthPx).coerceAtLeast(0f) * 0.15f, 0f)
                                }
                                else -> {
                                    proposed.coerceIn(-panelWidthPx, drawerWidthPx)
                                }
                            }
                            scope.launch {
                                currentOffset.snapTo(clamped)
                            }
                        }
                    }
                }
            }
        } else Modifier

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
                                currentOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                            } else {
                                focusManager.clearFocus()
                                currentOffset.animateTo(drawerWidthPx, spring(dampingRatio = 0.85f, stiffness = 420f))
                            }
                        }
                    },
                    {
                        scope.launch {
                            if (currentOffset.value < -panelWidthPx * 0.5f) {
                                currentOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                            } else {
                                focusManager.clearFocus()
                                currentOffset.animateTo(-panelWidthPx, spring(dampingRatio = 0.85f, stiffness = 420f))
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
                                currentOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                            }
                        }
                )
            }

            // ── Layer 3: Left Drawer (Slides in from Left) ────────────────────
            if (currentOffset.value > 0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(drawerWidthDp)
                        .graphicsLayer {
                            translationX = -drawerWidthPx + currentOffset.value.coerceIn(0f, drawerWidthPx + 50f)
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
                                    currentOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Layer 4: Right Panel (Slides in from Right) ───────────────────
            if (currentOffset.value < -0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = screenWidthPx + currentOffset.value.coerceIn(-panelWidthPx - 50f, 0f)
                        }
                ) {
                    rightPanelContent {
                        scope.launch {
                            currentOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                        }
                    }
                }
            }
        }
    }
}
