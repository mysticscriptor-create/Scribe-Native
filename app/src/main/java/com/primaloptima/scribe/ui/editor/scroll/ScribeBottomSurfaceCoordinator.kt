package com.primaloptima.scribe.ui.editor.scroll

/**
 * ScribeBottomSurfaceCoordinator — Unified single source of truth for keyboard,
 * accessory drawer, and bottom surface geometry.
 *
 * Architecture Principles:
 * 1. Standard Keyboard Baseline: Preserves the user's standard QWERTY keyboard height
 *    (typically 220dp-320dp). Rejects transient oversized pickers (emojis/stickers/GIFs > 320dp)
 *    so compact drawers and transition latches never get poisoned.
 * 2. Stable Bottom Surface Sizing: Clamps effective keyboard height to maxOf(baseline, imeAboveNav)
 *    during active keyboard mode. Prevents 60fps relayout vibration during keyboard entrance.
 * 3. Pure Writer Stationary Invariant: When the soft keyboard or drawer dismisses to 0dp,
 *    the document canvas MUST remain 100% rock-solid stationary at the saved margin
 *    (keyboard height + shortcut bar + breathing margin). Downward following is strictly
 *    suppressed.
 * 4. Downward Follow Tracking: ONLY permitted when a bottom surface remains actively open
 *    (e.g., expanded emoji keyboard contracts to standard keyboard, or accessory drawer
 *    collapses to compact state or keyboard).
 */
object ScribeBottomSurfaceCoordinator {

    const val MIN_STANDARD_KEYBOARD_DP = 160f
    const val MAX_STANDARD_KEYBOARD_DP = 450f
    const val DEFAULT_KEYBOARD_DP = 280f

    /**
     * Resolves the maximum upper bound for a standard text keyboard based on screen height.
     */
    fun calculateMaxStandardBaselineDp(screenHeightDp: Float): Float {
        return minOf(MAX_STANDARD_KEYBOARD_DP, maxOf(300f, screenHeightDp * 0.45f))
    }

    /**
     * Resolves the minimum lower bound for a standard text keyboard based on screen orientation/height.
     * Accommodates landscape phone keyboards (~120-180dp) while guarding against micro-toolbars.
     */
    fun calculateMinStandardBaselineDp(screenHeightDp: Float): Float {
        return if (screenHeightDp in 1f..500f) 120f else MIN_STANDARD_KEYBOARD_DP
    }

    /**
     * Validates whether a settled keyboard height qualifies as a standard typing keyboard.
     * Rejects transient expanded pickers (emojis, stickers, GIFs, clipboard) or oversized sheets.
     */
    fun isValidStandardKeyboardHeight(heightDp: Float, screenHeightDp: Float): Boolean {
        val minBaseline = calculateMinStandardBaselineDp(screenHeightDp)
        val maxBaseline = calculateMaxStandardBaselineDp(screenHeightDp)
        return heightDp in minBaseline..maxBaseline
    }

    /**
     * Determines whether the standard keyboard baseline stored in DataStore should be updated.
     */
    fun shouldUpdateSavedBaseline(
        settledHeightDp: Float,
        currentBaselineDp: Float,
        screenHeightDp: Float
    ): Boolean {
        if (!isValidStandardKeyboardHeight(settledHeightDp, screenHeightDp)) return false
        return kotlin.math.abs(settledHeightDp - currentBaselineDp) > 2f
    }

    /**
     * Resolves the effective height of the bottom surface spacer (beneath the Shortcut Bar).
     */
    fun resolveEffectiveBottomSurfaceHeight(
        isAccessoryDrawerActive: Boolean,
        drawerHeightDp: Float,
        isAwaitingKeyboardOpen: Boolean,
        isBarTransitionLatched: Boolean,
        isKeyboardVisible: Boolean,
        compactHeightDp: Float,
        imeAboveNavDp: Float
    ): Float {
        if (isAccessoryDrawerActive) {
            return drawerHeightDp
        }
        if (isKeyboardVisible) {
            // Standard keyboard: locks at compactHeightDp to prevent 60fps layout thrashing.
            // Emoji keyboard: expands smoothly to imeAboveNavDp when > compactHeightDp.
            // Return to standard: drops immediately to compactHeightDp with ZERO delay or floating!
            return maxOf(compactHeightDp, imeAboveNavDp)
        }
        if (isAwaitingKeyboardOpen || isBarTransitionLatched) {
            // In-flight transition: follow live arriving IME insets rather than prematurely bloating layout
            return imeAboveNavDp
        }
        if (imeAboveNavDp > 10f) {
            // Keyboard is dismissing: allows the visual shortcut bar to follow IME down
            return imeAboveNavDp
        }
        return 0f
    }

    /**
     * Resolves whether the soft keyboard is currently in the process of dismissing.
     */
    fun isKeyboardDismissing(
        isKeyboardVisible: Boolean,
        isAccessoryDrawerActive: Boolean,
        isAwaitingKeyboardOpen: Boolean,
        isBarTransitionLatched: Boolean,
        imeAboveNavDp: Float
    ): Boolean {
        return !isKeyboardVisible &&
                !isAccessoryDrawerActive &&
                !isAwaitingKeyboardOpen &&
                !isBarTransitionLatched &&
                imeAboveNavDp > 0f
    }

    /**
     * Determines whether the soft keyboard has fully settled into its resting position,
     * signaling that the accessory shortcut bar may now be rendered smoothly.
     */
    fun isKeyboardSettled(
        isKeyboardVisible: Boolean,
        isAwaitingKeyboardOpen: Boolean,
        imeAboveNavDp: Float
    ): Boolean {
        return isKeyboardVisible && !isAwaitingKeyboardOpen && imeAboveNavDp > 100f
    }

    /**
     * Pure Writer Invariant Decision Function:
     * Governs whether UnifiedCanvasLayout is permitted to pull the document downward on resize.
     *
     * @param isKeyboardDismissing Soft keyboard is actively dismissing to 0.
     * @param isBottomSurfaceActive Compose reports an active bottom bar or drawer.
     * @param isKeyboardVisible Soft keyboard is currently reported visible by WindowInsets.
     * @param hasPhysicalBottomObstruction Viewport has remaining obstruction >= 140dp.
     * @param isAtLastLine Cursor is on or near the last line of the document.
     * @param hasActiveScroll Document has scroll offset > 0.
     * @param temporaryDisplacement Recorded upward displacement caused by prior surface expansion.
     */
    fun shouldFollowDownward(
        isKeyboardDismissing: Boolean,
        isBottomSurfaceActive: Boolean,
        isKeyboardVisible: Boolean,
        hasPhysicalBottomObstruction: Boolean,
        isAtLastLine: Boolean,
        hasActiveScroll: Boolean,
        temporaryDisplacement: Float = 0f
    ): Boolean {
        // Pure Writer Invariant:
        // When keyboard is dismissed or actively closing, the document MUST remain
        // 100% completely stationary at the saved margin. NEVER pull down to the bottom of the screen!
        if (isKeyboardDismissing) {
            return false
        }

        // If no bottom surface remains active, document must stay stationary at saved margin.
        val isBottomActive = isBottomSurfaceActive || isKeyboardVisible || hasPhysicalBottomObstruction
        if (!isBottomActive) {
            return false
        }

        if (!hasActiveScroll) {
            return false
        }

        // Downward follow is permitted when:
        // 1. A prior expansion displaced the canvas upward (temporaryDisplacement > 0f) across ANY caret position, OR
        // 2. The cursor is at the last line of the document following an actively contracting bottom surface.
        return (temporaryDisplacement > 0f) || isAtLastLine
    }
    /**
     * Calculates the expanded drawer height in Dp.
     */
    fun calculateExpandedHeightDp(compactHeightDp: Float, screenHeightDp: Float): Float {
        return maxOf(compactHeightDp + 60f, (screenHeightDp * 0.48f).coerceIn(360f, 480f))
    }

    /**
     * Calculates the full drawer height in Dp.
     */
    fun calculateFullHeightDp(screenHeightDp: Float): Float {
        return (screenHeightDp * 0.72f).coerceIn(480f, 650f)
    }

    /**
     * Resolves the target height in Dp for a given [AccessoryDrawerState].
     */
    fun calculateDrawerTargetHeightDp(
        drawerState: AccessoryDrawerState,
        compactHeightDp: Float,
        expandedHeightDp: Float,
        fullHeightDp: Float
    ): Float {
        return when (drawerState) {
            AccessoryDrawerState.COMPACT -> compactHeightDp
            AccessoryDrawerState.EXPANDED -> expandedHeightDp
            AccessoryDrawerState.FULL -> fullHeightDp
        }
    }

    /**
     * Cycles drawer state through COMPACT -> EXPANDED -> FULL -> COMPACT.
     */
    fun calculateNextDrawerState(currentState: AccessoryDrawerState): AccessoryDrawerState {
        return when (currentState) {
            AccessoryDrawerState.COMPACT -> AccessoryDrawerState.EXPANDED
            AccessoryDrawerState.EXPANDED -> AccessoryDrawerState.FULL
            AccessoryDrawerState.FULL -> AccessoryDrawerState.COMPACT
        }
    }

    /**
     * Determines the snap target state from release velocity and live height.
     */
    fun calculateSnapTargetState(
        currentHeightDp: Float,
        velocityDpPerSec: Float,
        compactHeightDp: Float,
        expandedHeightDp: Float,
        fullHeightDp: Float
    ): AccessoryDrawerState {
        return when {
            // Fling upward: rapid upward movement (-vyDp > 300)
            velocityDpPerSec < -300f -> {
                if (currentHeightDp < expandedHeightDp * 0.95f) AccessoryDrawerState.EXPANDED
                else AccessoryDrawerState.FULL
            }
            // Fling downward: rapid downward movement (vyDp > 300)
            velocityDpPerSec > 300f -> {
                if (currentHeightDp > expandedHeightDp * 1.05f) AccessoryDrawerState.EXPANDED
                else AccessoryDrawerState.COMPACT
            }
            // Release without fast fling: snap to nearest height
            else -> {
                val dCompact = kotlin.math.abs(currentHeightDp - compactHeightDp)
                val dExpanded = kotlin.math.abs(currentHeightDp - expandedHeightDp)
                val dFull = kotlin.math.abs(currentHeightDp - fullHeightDp)
                when {
                    dCompact <= dExpanded && dCompact <= dFull -> AccessoryDrawerState.COMPACT
                    dExpanded <= dFull -> AccessoryDrawerState.EXPANDED
                    else -> AccessoryDrawerState.FULL
                }
            }
        }
    }

    /**
     * Resolves the effective drawer height in Dp accounting for dragging, state, and bounds.
     */
    fun resolveEffectiveDrawerHeightDp(
        isDrawerDragging: Boolean,
        dragLiveHeightDp: Float,
        drawerState: AccessoryDrawerState,
        animatedHeightDp: Float,
        compactHeightDp: Float,
        fullHeightDp: Float
    ): Float {
        return when {
            isDrawerDragging -> dragLiveHeightDp.coerceIn(compactHeightDp, fullHeightDp)
            drawerState == AccessoryDrawerState.COMPACT -> compactHeightDp
            else -> animatedHeightDp.coerceIn(compactHeightDp, fullHeightDp)
        }
    }
}
