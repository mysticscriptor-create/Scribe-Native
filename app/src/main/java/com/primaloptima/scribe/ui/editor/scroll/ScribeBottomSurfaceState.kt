package com.primaloptima.scribe.ui.editor.scroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.primaloptima.scribe.ui.components.UnifiedCanvasLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Visual expansion states for the accessory drawer.
 */
enum class AccessoryDrawerState {
    COMPACT,
    EXPANDED,
    FULL
}

/**
 * Active content mode for the accessory drawer.
 */
enum class AccessoryDrawerMode {
    SNIPPETS,
    TEMPLATES
}

/**
 * ScribeBottomSurfaceState — State container and Compose lifecycle coordinator for
 * soft keyboard, IME viewport insets, accessory drawer, and bottom surface transitions.
 *
 * Responsibilities:
 * 1. IME Inset Observation: Tracks `WindowInsets.isImeVisible`, `ime`, and `navigationBars`.
 * 2. Standard Baseline Persistence: Observes and debounces DataStore keyboard baseline updates,
 *    filtering out transient oversized pickers (emojis/stickers/GIFs > 330dp).
 * 3. Keyboard Entrance & Settling Lifecycle: Detects arrival and calibration of keyboard rest
 *    height using snapshotFlow sampling.
 * 4. Surface Transition Latching: Coordinates `isAwaitingKeyboardOpen` and `isBarTransitionLatched`
 *    so the shortcut bar and document canvas avoid floating or vibrating during drawer/keyboard handoffs.
 * 5. Accessory Drawer Viewport State: Coordinates `activeAccessoryDrawer`, `drawerState`, live dragging,
 *    velocity tracking, snapping, and Animatable spring transitions (Phase 7).
 * 6. Layout & Sizing Delegation: Evaluates effective bottom surface heights and dismissal states
 *    via [ScribeBottomSurfaceCoordinator].
 */
class ScribeBottomSurfaceState(
    initialKeyboardHeightDp: Dp = 280.dp
) {
    /**
     * Whether the UI is currently awaiting soft keyboard entrance after a drawer collapse or touch-down.
     */
    var isAwaitingKeyboardOpen: Boolean by mutableStateOf(false)

    /**
     * Latched transition flag preventing bottom surface jitter while the keyboard or drawer animates.
     */
    var isBarTransitionLatched: Boolean by mutableStateOf(false)

    /**
     * Current validated standard keyboard height baseline.
     */
    var lastKeyboardHeightDp: Dp by mutableStateOf(initialKeyboardHeightDp)

    /**
     * Current soft keyboard visibility reported by [WindowInsets.isImeVisible].
     */
    var isKeyboardVisible: Boolean by mutableStateOf(false)
        internal set

    /**
     * Total bottom padding from IME insets.
     */
    var imeBottomDp: Dp by mutableStateOf(0.dp)
        internal set

    /**
     * System navigation bar bottom inset padding.
     */
    var docBottomNavInset: Dp by mutableStateOf(0.dp)
        internal set

    /**
     * Soft keyboard height strictly above system navigation bar.
     */
    var imeAboveNavDp: Dp by mutableStateOf(0.dp)
        internal set

    /**
     * Screen height in Dp for clamping and baseline threshold calculations.
     */
    var screenHeightDp: Dp by mutableStateOf(0.dp)
        internal set

    /**
     * Screen height as Float for policy boundary calculations.
     */
    var screenHeightDpFloat: Float by mutableFloatStateOf(0f)
        internal set

    /**
     * Current screen density value.
     */
    var densityValue: Float by mutableFloatStateOf(1f)
        internal set

    // ── Accessory Drawer State (Phase 7) ──────────────────────────────────

    /**
     * Currently active accessory drawer content mode (snippets or templates), or null if closed.
     */
    var activeAccessoryDrawer: AccessoryDrawerMode? by mutableStateOf(null)

    /**
     * Visual sizing state of the accessory drawer: COMPACT, EXPANDED, or FULL.
     */
    var drawerState: AccessoryDrawerState by mutableStateOf(AccessoryDrawerState.COMPACT)

    /**
     * True while the user is actively dragging the drawer handle.
     */
    var isDrawerDragging: Boolean by mutableStateOf(false)

    /**
     * Current uncommitted height in Dp during an active touch drag gesture.
     */
    var dragLiveHeightDp: Float by mutableFloatStateOf(initialKeyboardHeightDp.value)

    /**
     * Monotonic timestamp of last drawer toggle to debounce rapid taps.
     */
    var lastDrawerToggleTimestamp: Long by mutableLongStateOf(0L)

    /**
     * Animatable height in Dp driving smooth transitions between drawer states.
     */
    val drawerHeightDpAnim: Animatable<Float, AnimationVector1D> = Animatable(initialKeyboardHeightDp.value)

    /**
     * Whether an accessory drawer is currently open.
     */
    val isAccessoryDrawerActive: Boolean
        get() = activeAccessoryDrawer != null

    /**
     * Standard compact keyboard height clamped between 160dp and 60% of screen height.
     */
    val compactHeightDp: Dp
        get() = lastKeyboardHeightDp.coerceIn(160.dp, screenHeightDp * 0.60f)

    /**
     * Intermediate expanded drawer height (~48% screen height or compact + 60dp).
     */
    val expandedHeightDp: Dp
        get() = ScribeBottomSurfaceCoordinator.calculateExpandedHeightDp(
            compactHeightDp = compactHeightDp.value,
            screenHeightDp = screenHeightDpFloat
        ).dp

    /**
     * Full drawer height (~72% screen height clamped between 480dp and 650dp).
     */
    val fullHeightDp: Dp
        get() = ScribeBottomSurfaceCoordinator.calculateFullHeightDp(
            screenHeightDp = screenHeightDpFloat
        ).dp

    /**
     * Effective current height of the accessory drawer in Dp.
     */
    val currentDrawerHeightDp: Dp
        get() = ScribeBottomSurfaceCoordinator.resolveEffectiveDrawerHeightDp(
            isDrawerDragging = isDrawerDragging,
            dragLiveHeightDp = dragLiveHeightDp,
            drawerState = drawerState,
            animatedHeightDp = drawerHeightDpAnim.value,
            compactHeightDp = compactHeightDp.value,
            fullHeightDp = fullHeightDp.value
        ).dp

    /**
     * Maximum allowed baseline for standard keyboards on this display.
     */
    val maxStandardKbBaselineDp: Dp
        get() = ScribeBottomSurfaceCoordinator.calculateMaxStandardBaselineDp(screenHeightDpFloat).dp

    /**
     * Resolves the effective spacer height beneath the shortcut bar.
     */
    fun resolveEffectiveBottomSurfaceHeight(
        isAccessoryDrawerActive: Boolean = this.isAccessoryDrawerActive,
        drawerHeightDp: Float = currentDrawerHeightDp.value
    ): Dp {
        return ScribeBottomSurfaceCoordinator.resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = isAccessoryDrawerActive,
            drawerHeightDp = drawerHeightDp,
            isAwaitingKeyboardOpen = isAwaitingKeyboardOpen,
            isBarTransitionLatched = isBarTransitionLatched,
            isKeyboardVisible = isKeyboardVisible,
            compactHeightDp = compactHeightDp.value,
            imeAboveNavDp = imeAboveNavDp.value
        ).dp
    }

    /**
     * Direct property accessor for current effective bottom surface height in Dp.
     */
    val effectiveBottomSurfaceHeight: Dp
        get() = resolveEffectiveBottomSurfaceHeight()

    /**
     * Resolves whether the soft keyboard is currently in the process of dismissing.
     */
    fun isKeyboardDismissing(
        isAccessoryDrawerActive: Boolean = this.isAccessoryDrawerActive
    ): Boolean {
        return ScribeBottomSurfaceCoordinator.isKeyboardDismissing(
            isKeyboardVisible = isKeyboardVisible,
            isAccessoryDrawerActive = isAccessoryDrawerActive,
            isAwaitingKeyboardOpen = isAwaitingKeyboardOpen,
            isBarTransitionLatched = isBarTransitionLatched,
            imeAboveNavDp = imeAboveNavDp.value
        )
    }

    /**
     * Determines whether the soft keyboard has settled into resting position.
     */
    fun isKeyboardSettled(): Boolean {
        return ScribeBottomSurfaceCoordinator.isKeyboardSettled(
            isKeyboardVisible = isKeyboardVisible,
            isAwaitingKeyboardOpen = isAwaitingKeyboardOpen,
            imeAboveNavDp = imeAboveNavDp.value
        )
    }

    /**
     * Checks if an accessory drawer or keyboard transition latch is active.
     */
    fun isDrawerOrTransitionActive(
        isAccessoryDrawerActive: Boolean = this.isAccessoryDrawerActive
    ): Boolean {
        return isAccessoryDrawerActive || isAwaitingKeyboardOpen || isBarTransitionLatched
    }

    /**
     * Effective keyboard visibility accounting for in-flight transitions.
     */
    fun curIsKeyboardVisible(
        isAccessoryDrawerActive: Boolean = this.isAccessoryDrawerActive
    ): Boolean {
        return isKeyboardVisible || isDrawerOrTransitionActive(isAccessoryDrawerActive)
    }

    /**
     * Effective bottom surface active state for canvas layout reservation.
     */
    fun curIsBottomSurfaceActive(
        isAccessoryDrawerActive: Boolean = this.isAccessoryDrawerActive,
        hasShortcuts: Boolean = true
    ): Boolean {
        return (isKeyboardVisible || isDrawerOrTransitionActive(isAccessoryDrawerActive)) && hasShortcuts
    }

    /**
     * Calculates estimated keyboard height in pixels for [UnifiedCanvasLayout].
     */
    fun calculateEstimatedKeyboardHeightPx(
        bottomSurfaceHeightDp: Float,
        hasShortcuts: Boolean,
        densityVal: Float = densityValue
    ): Float {
        val shortcutBarDp = if (hasShortcuts) 36f else 0f
        return (bottomSurfaceHeightDp + shortcutBarDp) * densityVal
    }

    /**
     * Initiates awaiting keyboard open transition, latching bottom surface state.
     */
    fun startAwaitingKeyboard(
        unifiedCanvas: UnifiedCanvasLayout? = null,
        onLatched: (() -> Unit)? = null
    ) {
        isAwaitingKeyboardOpen = true
        isBarTransitionLatched = true
        unifiedCanvas?.isBottomSurfaceActive = true
        unifiedCanvas?.isKeyboardVisible = true
        onLatched?.invoke()
    }

    /**
     * Cancels awaiting keyboard state and unlatches transition locks.
     */
    fun cancelAwaitingKeyboard() {
        isAwaitingKeyboardOpen = false
        isBarTransitionLatched = false
    }

    /**
     * Invoked when [UnifiedCanvasLayout] signals soft keyboard entrance has begun.
     */
    fun onKeyboardEntranceStarted(unifiedCanvas: UnifiedCanvasLayout? = null) {
        if (!isAwaitingKeyboardOpen && !isKeyboardVisible) {
            startAwaitingKeyboard(unifiedCanvas)
        }
    }

    // ── Drawer Lifecycle & Drag Management ────────────────────────────────

    /**
     * Invoked when a user begins dragging the drawer handle.
     */
    fun onDrawerDragStart() {
        isDrawerDragging = true
        dragLiveHeightDp = drawerHeightDpAnim.value
    }

    /**
     * Invoked on vertical drag increments to track live drawer height.
     */
    fun onDrawerDragDelta(deltaPx: Float, densityVal: Float = densityValue) {
        isDrawerDragging = true
        val deltaDp = deltaPx / densityVal
        dragLiveHeightDp = (dragLiveHeightDp - deltaDp).coerceIn(compactHeightDp.value, fullHeightDp.value)
    }

    /**
     * Invoked on drag release to calculate fling/snap target and animate settling.
     */
    fun onDrawerDragEndWithVelocity(
        vyPx: Float,
        scope: CoroutineScope,
        densityVal: Float = densityValue
    ) {
        val vyDp = vyPx / densityVal
        val currentH = dragLiveHeightDp
        val targetState = ScribeBottomSurfaceCoordinator.calculateSnapTargetState(
            currentHeightDp = currentH,
            velocityDpPerSec = vyDp,
            compactHeightDp = compactHeightDp.value,
            expandedHeightDp = expandedHeightDp.value,
            fullHeightDp = fullHeightDp.value
        )
        drawerState = targetState
        val targetHeight = ScribeBottomSurfaceCoordinator.calculateDrawerTargetHeightDp(
            drawerState = targetState,
            compactHeightDp = compactHeightDp.value,
            expandedHeightDp = expandedHeightDp.value,
            fullHeightDp = fullHeightDp.value
        )
        scope.launch {
            drawerHeightDpAnim.snapTo(currentH)
            isDrawerDragging = false
            drawerHeightDpAnim.animateTo(
                targetValue = targetHeight,
                initialVelocity = -vyDp,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
        }
    }

    /**
     * Explicitly sets the drawer sizing state and notifies the canvas.
     */
    fun setDrawerState(newState: AccessoryDrawerState, unifiedCanvas: UnifiedCanvasLayout? = null) {
        drawerState = newState
        unifiedCanvas?.isBottomSurfaceActive = true
        unifiedCanvas?.isKeyboardVisible = true
    }

    /**
     * Cycles drawer state through COMPACT -> EXPANDED -> FULL -> COMPACT.
     */
    fun cycleDrawerState(unifiedCanvas: UnifiedCanvasLayout? = null): AccessoryDrawerState {
        val next = ScribeBottomSurfaceCoordinator.calculateNextDrawerState(drawerState)
        setDrawerState(next, unifiedCanvas)
        return next
    }

    /**
     * Closes the active accessory drawer without restoring the soft keyboard.
     */
    fun closeDrawer() {
        activeAccessoryDrawer = null
        drawerState = AccessoryDrawerState.COMPACT
        cancelAwaitingKeyboard()
    }

    /**
     * Closes the active accessory drawer and initiates keyboard entrance handoff.
     */
    fun closeDrawerAndRestoreKeyboard(
        unifiedCanvas: UnifiedCanvasLayout? = null,
        scope: CoroutineScope? = null,
        onRestore: (() -> Unit)? = null
    ) {
        activeAccessoryDrawer = null
        drawerState = AccessoryDrawerState.COMPACT
        startAwaitingKeyboard(unifiedCanvas)
        scope?.launch {
            drawerHeightDpAnim.snapTo(compactHeightDp.value)
        }
        onRestore?.invoke()
    }

    /**
     * Opens an accessory drawer in COMPACT state from keyboard or idle state.
     */
    fun openDrawer(
        targetMode: AccessoryDrawerMode,
        scope: CoroutineScope? = null,
        onOpen: (() -> Unit)? = null
    ) {
        cancelAwaitingKeyboard()
        activeAccessoryDrawer = targetMode
        drawerState = AccessoryDrawerState.COMPACT
        scope?.launch {
            drawerHeightDpAnim.snapTo(compactHeightDp.value)
        }
        onOpen?.invoke()
    }

    /**
     * Seamless instantaneous switch between open drawers with zero cursor or keyboard flicker.
     */
    fun switchDrawer(
        targetMode: AccessoryDrawerMode,
        onSwitch: (() -> Unit)? = null
    ) {
        activeAccessoryDrawer = targetMode
        onSwitch?.invoke()
    }

    /**
     * Coordinates the complete drawer toggle lifecycle: closing, switching, or opening.
     */
    fun toggleAccessoryDrawer(
        targetMode: AccessoryDrawerMode,
        unifiedCanvas: UnifiedCanvasLayout? = null,
        scope: CoroutineScope? = null,
        onClosing: (() -> Unit)? = null,
        onOpening: (() -> Unit)? = null,
        onSwitching: (() -> Unit)? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val isSwitchingBetweenOpenDrawers = activeAccessoryDrawer != null && targetMode != activeAccessoryDrawer
        if (!isSwitchingBetweenOpenDrawers && now - lastDrawerToggleTimestamp < 200L) {
            return false
        }
        lastDrawerToggleTimestamp = now
        if (activeAccessoryDrawer == targetMode) {
            closeDrawerAndRestoreKeyboard(unifiedCanvas, scope, onClosing)
        } else if (activeAccessoryDrawer != null) {
            switchDrawer(targetMode, onSwitching)
        } else {
            openDrawer(targetMode, scope, onOpening)
        }
        return true
    }
}

/**
 * Creates and remembers a [ScribeBottomSurfaceState] instance, coordinating IME insets,
 * DataStore baseline persistence, drawer lifecycle transitions, and transition latching across recompositions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberScribeBottomSurfaceState(
    savedKeyboardHeightFlow: Flow<Float>,
    onSaveKeyboardHeight: suspend (Float) -> Unit,
    isAccessoryDrawerActive: Boolean = false,
): ScribeBottomSurfaceState {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightDpFloat = configuration.screenHeightDp.toFloat()
    val isKeyboardVisible = WindowInsets.isImeVisible
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val docBottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeAboveNavDp = (imeBottomDp - docBottomNavInset).coerceAtLeast(0.dp)

    val savedKeyboardHeightFloat by savedKeyboardHeightFlow.collectAsState(initial = 280f)

    val state = remember {
        ScribeBottomSurfaceState(
            initialKeyboardHeightDp = if (savedKeyboardHeightFloat >= 220f) savedKeyboardHeightFloat.dp else 280.dp
        )
    }

    // Keep observable snapshot state properties in sync with composition
    state.isKeyboardVisible = isKeyboardVisible
    state.imeBottomDp = imeBottomDp
    state.docBottomNavInset = docBottomNavInset
    state.imeAboveNavDp = imeAboveNavDp
    state.screenHeightDp = screenHeightDp
    state.screenHeightDpFloat = screenHeightDpFloat
    state.densityValue = density.density

    // Update baseline when DataStore emits a validated standard keyboard height
    LaunchedEffect(savedKeyboardHeightFloat) {
        if (ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(savedKeyboardHeightFloat, screenHeightDpFloat)) {
            state.lastKeyboardHeightDp = savedKeyboardHeightFloat.dp
        }
    }

    // Animate drawer height on state change, and restore compact height on close
    LaunchedEffect(
        state.drawerState,
        state.compactHeightDp,
        state.expandedHeightDp,
        state.fullHeightDp,
        state.activeAccessoryDrawer
    ) {
        if (state.activeAccessoryDrawer != null && !state.isDrawerDragging) {
            val target = ScribeBottomSurfaceCoordinator.calculateDrawerTargetHeightDp(
                drawerState = state.drawerState,
                compactHeightDp = state.compactHeightDp.value,
                expandedHeightDp = state.expandedHeightDp.value,
                fullHeightDp = state.fullHeightDp.value
            )
            if (state.drawerHeightDpAnim.targetValue != target) {
                state.drawerHeightDpAnim.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            }
        } else if (state.activeAccessoryDrawer == null) {
            if (state.drawerHeightDpAnim.value != state.compactHeightDp.value) {
                state.drawerHeightDpAnim.animateTo(
                    targetValue = state.compactHeightDp.value,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
            }
            state.dragLiveHeightDp = state.compactHeightDp.value
        }
    }

    // Capture steady-state standard keyboard height once settled, saving to DataStore only on confirmed rest.
    // Filter out transient oversized keyboard pickers (emojis, stickers, GIFs, clipboard) which exceed 330dp,
    // ensuring compact drawers and transition latches reflect the baseline standard keyboard.
    val effectiveDrawerActive = isAccessoryDrawerActive || state.isAccessoryDrawerActive
    LaunchedEffect(imeBottomDp, isKeyboardVisible, docBottomNavInset, effectiveDrawerActive) {
        if (isKeyboardVisible && !effectiveDrawerActive && !state.isAwaitingKeyboardOpen && imeBottomDp > 150.dp) {
            val aboveNav = (imeBottomDp - docBottomNavInset).coerceAtLeast(0.dp)
            if (ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(aboveNav.value, screenHeightDpFloat)) {
                // Debounce 350ms to ensure the keyboard has finished 100% of its entrance animation and settled
                delay(350)
                val settledAboveNav = (imeBottomDp - docBottomNavInset).coerceAtLeast(0.dp)
                if (isKeyboardVisible && !effectiveDrawerActive && !state.isAwaitingKeyboardOpen &&
                    ScribeBottomSurfaceCoordinator.shouldUpdateSavedBaseline(settledAboveNav.value, state.lastKeyboardHeightDp.value, screenHeightDpFloat) &&
                    kotlin.math.abs(settledAboveNav.value - aboveNav.value) < 1f) {
                    state.lastKeyboardHeightDp = settledAboveNav
                    if (kotlin.math.abs(settledAboveNav.value - savedKeyboardHeightFloat) >= 1f) {
                        onSaveKeyboardHeight(settledAboveNav.value)
                    }
                }
            }
        }
    }

    // Keep bottom spacer pinned at compactHeightDp when returning from drawer to keyboard
    // so the shortcut bar stays rock-solid while the IME animates in
    LaunchedEffect(state.isAwaitingKeyboardOpen) {
        if (state.isAwaitingKeyboardOpen) {
            var settled = false
            try {
                // 1. Give keyboard at least 150ms to begin its animation from 0
                delay(150)
                // 2. Sample imeAboveNavDp until it stops changing (settled at rest)
                var lastHeight = imeAboveNavDp.value
                var stableCount = 0
                val result = withTimeoutOrNull(750) {
                    snapshotFlow { Pair(imeAboveNavDp.value, isKeyboardVisible) }
                        .filter { (currentHeight, visible) ->
                            if (!visible) return@filter false
                            // If keyboard is substantial and height delta is < 0.5dp for 3 consecutive checks
                            if (currentHeight >= 140f) {
                                if (kotlin.math.abs(currentHeight - lastHeight) < 0.5f) {
                                    stableCount++
                                } else {
                                    stableCount = 0
                                    lastHeight = currentHeight
                                }
                                stableCount >= 3
                            } else {
                                false
                            }
                        }
                        .first()
                }
                settled = result != null
            } finally {
                // 3. Calibrate compactHeightDp to the exact settled height so future toggles match 1:1
                if (settled && ScribeBottomSurfaceCoordinator.isValidStandardKeyboardHeight(imeAboveNavDp.value, screenHeightDpFloat)) {
                    state.lastKeyboardHeightDp = imeAboveNavDp
                }
                state.isAwaitingKeyboardOpen = false
                state.isBarTransitionLatched = false
            }
        }
    }

    return state
}
