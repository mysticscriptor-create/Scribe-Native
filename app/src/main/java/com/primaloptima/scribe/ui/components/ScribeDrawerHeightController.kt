package com.primaloptima.scribe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ScribeDrawerHeightController — Authoritative single-owner controller for accessory drawer
 * height animation, interactive dragging, velocity-based settling, and handoff geometry capture.
 *
 * Architecture Principles:
 * 1. Single Writer: Only ONE animation job / coroutine owns the Animatable at any moment.
 *    LaunchedEffects and drag handlers never compete or animate independently.
 * 2. Unambiguous States:
 *    - logicalState: target drawer state (COMPACT, EXPANDED, FULL).
 *    - isDragging: active touch drag by the user.
 *    - isAnimating: active spring animation or programmatic transition.
 * 3. Actual Displayed Height:
 *    - currentDisplayedHeightDp always returns the actual live height (from live drag or Animatable).
 *    - Handoff logic, layout padding, and visible drawer container always observe this identical value.
 */
class ScribeDrawerHeightController(
    val compactHeightDp: Float,
    val expandedHeightDp: Float,
    val fullHeightDp: Float,
    private val scope: CoroutineScope
) {
    val animatable = Animatable(compactHeightDp)

    var logicalState by mutableStateOf(ScribeBottomSurfaceCoordinator.AccessoryDrawerState.COMPACT)
        private set

    var isDragging by mutableStateOf(false)
        private set

    var isAnimating by mutableStateOf(false)
        private set

    private var liveDragHeightDp by mutableFloatStateOf(compactHeightDp)
    private var activeAnimationJob: Job? = null

    /**
     * Authoritative currently displayed height in dp.
     * When dragging, returns the live drag height coerced in [compactHeightDp, fullHeightDp].
     * When animating or settled, returns animatable.value coerced in [compactHeightDp, fullHeightDp].
     */
    val currentDisplayedHeightDp: Float
        get() = if (isDragging) {
            liveDragHeightDp.coerceIn(compactHeightDp, fullHeightDp)
        } else {
            animatable.value.coerceIn(compactHeightDp, fullHeightDp)
        }

    fun targetHeightForState(state: ScribeBottomSurfaceCoordinator.AccessoryDrawerState): Float = when (state) {
        ScribeBottomSurfaceCoordinator.AccessoryDrawerState.COMPACT -> compactHeightDp
        ScribeBottomSurfaceCoordinator.AccessoryDrawerState.EXPANDED -> expandedHeightDp
        ScribeBottomSurfaceCoordinator.AccessoryDrawerState.FULL -> fullHeightDp
    }

    /**
     * Programmatic state transition (e.g. user toggles compact/expanded/full via UI).
     */
    fun animateToState(
        targetState: ScribeBottomSurfaceCoordinator.AccessoryDrawerState,
        forceSnap: Boolean = false
    ) {
        activeAnimationJob?.cancel()
        isDragging = false
        logicalState = targetState
        val targetHeight = targetHeightForState(targetState)

        if (forceSnap) {
            isAnimating = false
            activeAnimationJob = scope.launch {
                animatable.snapTo(targetHeight)
                liveDragHeightDp = targetHeight
            }
            return
        }

        isAnimating = true
        activeAnimationJob = scope.launch {
            try {
                animatable.animateTo(
                    targetValue = targetHeight,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            } finally {
                isAnimating = false
                liveDragHeightDp = animatable.value
            }
        }
    }

    /**
     * User begins dragging drawer handle: cancels active animation immediately and captures start height.
     */
    fun onDragStart() {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        isAnimating = false
        isDragging = true
        liveDragHeightDp = animatable.value.coerceIn(compactHeightDp, fullHeightDp)
    }

    /**
     * Live drag updates from user gesture.
     */
    fun onDragDelta(deltaDp: Float) {
        if (!isDragging) {
            onDragStart()
        }
        liveDragHeightDp = (liveDragHeightDp - deltaDp).coerceIn(compactHeightDp, fullHeightDp)
    }

    /**
     * Drag release: computes target state based on velocity and displacement,
     * updates logical state, and launches authoritative settling animation.
     */
    fun onDragEndWithVelocity(
        vyDp: Float,
        onStateChanged: ((ScribeBottomSurfaceCoordinator.AccessoryDrawerState) -> Unit)? = null
    ) {
        activeAnimationJob?.cancel()
        isDragging = false
        val currentH = liveDragHeightDp.coerceIn(compactHeightDp, fullHeightDp)

        val targetState = when {
            // Rapid upward fling (vyDp < -300)
            vyDp < -300f -> {
                if (currentH < expandedHeightDp * 0.95f) ScribeBottomSurfaceCoordinator.AccessoryDrawerState.EXPANDED
                else ScribeBottomSurfaceCoordinator.AccessoryDrawerState.FULL
            }
            // Rapid downward fling (vyDp > 300)
            vyDp > 300f -> {
                if (currentH > expandedHeightDp * 1.05f) ScribeBottomSurfaceCoordinator.AccessoryDrawerState.EXPANDED
                else ScribeBottomSurfaceCoordinator.AccessoryDrawerState.COMPACT
            }
            // Release without fast fling: snap to nearest height
            else -> {
                val dCompact = kotlin.math.abs(currentH - compactHeightDp)
                val dExpanded = kotlin.math.abs(currentH - expandedHeightDp)
                val dFull = kotlin.math.abs(currentH - fullHeightDp)
                when {
                    dCompact <= dExpanded && dCompact <= dFull -> ScribeBottomSurfaceCoordinator.AccessoryDrawerState.COMPACT
                    dExpanded <= dFull -> ScribeBottomSurfaceCoordinator.AccessoryDrawerState.EXPANDED
                    else -> ScribeBottomSurfaceCoordinator.AccessoryDrawerState.FULL
                }
            }
        }

        logicalState = targetState
        onStateChanged?.invoke(targetState)
        val targetHeight = targetHeightForState(targetState)

        isAnimating = true
        activeAnimationJob = scope.launch {
            try {
                animatable.snapTo(currentH)
                animatable.animateTo(
                    targetValue = targetHeight,
                    initialVelocity = -vyDp,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            } finally {
                isAnimating = false
                liveDragHeightDp = animatable.value
            }
        }
    }

    /**
     * Drawer dismissal: resets state cleanly to COMPACT without competing animations.
     */
    fun dismiss(forceSnap: Boolean = false) {
        animateToState(ScribeBottomSurfaceCoordinator.AccessoryDrawerState.COMPACT, forceSnap = forceSnap)
    }
}
