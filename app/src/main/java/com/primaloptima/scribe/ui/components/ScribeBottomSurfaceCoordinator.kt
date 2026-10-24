package com.primaloptima.scribe.ui.components

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

    const val MIN_STANDARD_KEYBOARD_DP = 200f
    const val MAX_STANDARD_KEYBOARD_DP = 330f
    const val DEFAULT_KEYBOARD_DP = 280f

    /**
     * Resolves the maximum upper bound for a standard text keyboard based on screen height.
     */
    fun calculateMaxStandardBaselineDp(screenHeightDp: Float): Float {
        return minOf(MAX_STANDARD_KEYBOARD_DP, maxOf(300f, screenHeightDp * 0.45f))
    }

    /**
     * Validates whether a settled keyboard height qualifies as a standard typing keyboard.
     * Rejects transient expanded pickers (emojis, stickers, GIFs, clipboard) > 330dp.
     */
    fun isValidStandardKeyboardHeight(heightDp: Float, screenHeightDp: Float): Boolean {
        val maxBaseline = calculateMaxStandardBaselineDp(screenHeightDp)
        return heightDp in MIN_STANDARD_KEYBOARD_DP..maxBaseline
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
        if (isAwaitingKeyboardOpen || isBarTransitionLatched) {
            return maxOf(compactHeightDp, imeAboveNavDp)
        }
        if (isKeyboardVisible) {
            // Standard keyboard: locks at compactHeightDp to prevent 60fps layout thrashing.
            // Emoji keyboard: expands smoothly to imeAboveNavDp when > compactHeightDp.
            // Return to standard: drops immediately to compactHeightDp with ZERO delay or floating!
            return maxOf(compactHeightDp, imeAboveNavDp)
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
}
