package com.primaloptima.scribe.ui.components

/**
 * ScribeBottomSurfaceCoordinator — Unified single source of truth for bottom surface
 * presentation state machine, shortcut bar clearance, and viewport safety geometry.
 *
 * Architecture Principles:
 * 1. Three Clear Ownership Boundaries:
 *    - Physical Viewport & Insets: Owned by Compose WindowInsets.ime and WindowInsets.navigationBars.
 *    - Presentation State: Owned by PresentationState state machine (Hidden, Entrance, Settled, Dismissal, Drawer, Handoff).
 *    - Document Geometry & Scrolling: Owned by UnifiedCanvasLayout and ScribeScrollPolicy via actual measured viewport.
 * 2. Shared Dimensions & Clearance:
 *    - The editor must never reserve shortcut bar height while invisible.
 *    - At zero visibility, bar occupies 0dp. At full visibility, bar occupies 36dp.
 *    - Intermediate frames use matching dimensions.
 * 3. Pure Writer Stationary Invariant:
 *    - Total document scroll (scrollD + editor.offsetY) remains 100% stationary when keyboard is dismissed.
 *    - Downward follow is strictly suppressed on dismissal.
 */
object ScribeBottomSurfaceCoordinator {

    const val SHORTCUT_BAR_HEIGHT_DP = 36f
    const val MIN_STANDARD_KEYBOARD_DP = 200f
    const val MAX_STANDARD_KEYBOARD_DP = 330f
    const val DEFAULT_KEYBOARD_DP = 280f

    /**
     * Authoritative presentation state machine for keyboard, shortcut bar, and accessory drawers.
     * Controls presentation and visual coordination, completely decoupled from physical keyboard insets.
     */
    enum class PresentationState {
        HIDDEN,
        KEYBOARD_ENTRANCE,
        KEYBOARD_SETTLED,
        KEYBOARD_DISMISSAL,
        ACCESSORY_DRAWER_OPEN,
        DRAWER_TO_KEYBOARD_HANDOFF
    }

    /**
     * Determines whether the shortcut bar should be visible based on presentation state.
     */
    fun isShortcutBarVisible(state: PresentationState, hasShortcuts: Boolean = true): Boolean {
        if (!hasShortcuts) return false
        return when (state) {
            PresentationState.HIDDEN,
            PresentationState.KEYBOARD_ENTRANCE -> false
            PresentationState.KEYBOARD_SETTLED,
            PresentationState.KEYBOARD_DISMISSAL,
            PresentationState.ACCESSORY_DRAWER_OPEN,
            PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> true
        }
    }

    /**
     * Computes the shared animated bottom clearance in dp for the shortcut bar.
     * Guarantees the strict invariant: at 0 progress, bar occupies 0dp.
     * At 1.0 progress, bar occupies full 36dp.
     * The editor clearance and shortcut bar height use the exact same dimension.
     */
    fun calculateShortcutBarClearanceDp(progress: Float): Float {
        return (SHORTCUT_BAR_HEIGHT_DP * progress.coerceIn(0f, 1f))
    }

    /**
     * Computes the authoritative bottom padding to apply to the editor layout.
     * Combines physical IME insets and accessory drawer height, accounting for
     * the shared shortcut bar height.
     */
    fun calculateEditorBottomPaddingDp(
        imeBottomDp: Float,
        navBarBottomDp: Float,
        drawerHeightDp: Float,
        shortcutBarClearanceDp: Float
    ): Float {
        val baseObstruction = maxOf(imeBottomDp, navBarBottomDp + drawerHeightDp)
        return baseObstruction + shortcutBarClearanceDp
    }

    /**
     * Viewport cursor safety check according to actual measured geometry.
     * Returns true if the cursor's screen-space bottom coordinate is within the safe viewport area.
     */
    fun isCursorSafeInViewport(
        screenBottom: Float,
        viewportHeight: Float,
        breathingMarginPx: Float,
        deadbandPx: Float = 0f
    ): Boolean {
        val safeBottomBoundary = (viewportHeight - breathingMarginPx).coerceAtLeast(0f)
        return screenBottom <= (safeBottomBoundary + deadbandPx)
    }

    /**
     * Computes the minimum necessary upward displacement to bring an unsafe cursor
     * into the safe breathing zone. Returns 0f if cursor is already safe.
     */
    fun calculateRequiredUpwardDisplacement(
        screenBottom: Float,
        viewportHeight: Float,
        breathingMarginPx: Float,
        deadbandPx: Float = 0f
    ): Float {
        val safeBottomBoundary = (viewportHeight - breathingMarginPx).coerceAtLeast(0f)
        if (screenBottom <= safeBottomBoundary + deadbandPx) {
            return 0f
        }
        return (screenBottom - safeBottomBoundary).coerceAtLeast(0f)
    }

    /**
     * Computes the retained scroll extent needed upon keyboard dismissal to preserve
     * the stationary document position without downward bounds clamping.
     */
    fun calculateRetainedScrollExtent(
        totalScroll: Int,
        headerHeight: Int,
        layoutHeight: Int,
        expandedViewportHeight: Int
    ): Int {
        val naturalContentHeight = headerHeight + layoutHeight
        val requiredBottomExtent = (totalScroll + expandedViewportHeight - naturalContentHeight).coerceAtLeast(0)
        return requiredBottomExtent
    }

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
     * Backwards-compatible resolver for bottom surface height during transitions.
     */
    fun resolveEffectiveBottomSurfaceHeight(
        isAccessoryDrawerActive: Boolean,
        drawerHeightDp: Float,
        isAwaitingKeyboardOpen: Boolean = false,
        isBarTransitionLatched: Boolean = false,
        isKeyboardVisible: Boolean = false,
        compactHeightDp: Float = DEFAULT_KEYBOARD_DP,
        imeAboveNavDp: Float = 0f
    ): Float {
        if (isAccessoryDrawerActive) {
            return drawerHeightDp
        }
        if (isAwaitingKeyboardOpen || isBarTransitionLatched) {
            return maxOf(compactHeightDp, imeAboveNavDp)
        }
        if (isKeyboardVisible) {
            return maxOf(compactHeightDp, imeAboveNavDp)
        }
        if (imeAboveNavDp > 10f) {
            return imeAboveNavDp
        }
        return 0f
    }

    /**
     * Backwards-compatible helper for keyboard dismissal state check.
     */
    fun isKeyboardDismissing(
        isKeyboardVisible: Boolean,
        isAccessoryDrawerActive: Boolean = false,
        isAwaitingKeyboardOpen: Boolean = false,
        isBarTransitionLatched: Boolean = false,
        imeAboveNavDp: Float = 0f
    ): Boolean {
        return !isKeyboardVisible &&
                !isAccessoryDrawerActive &&
                !isAwaitingKeyboardOpen &&
                !isBarTransitionLatched &&
                imeAboveNavDp > 0f
    }

    /**
     * Backwards-compatible helper for keyboard settled state check.
     */
    fun isKeyboardSettled(
        isKeyboardVisible: Boolean,
        isAwaitingKeyboardOpen: Boolean = false,
        imeAboveNavDp: Float = 0f
    ): Boolean {
        return isKeyboardVisible && !isAwaitingKeyboardOpen && imeAboveNavDp > 100f
    }

    /**
     * Pure Writer Invariant Decision Function:
     * Governs whether downward follow is permitted on layout resize.
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
        // When keyboard is dismissed or actively closing, document MUST remain 100% stationary!
        if (isKeyboardDismissing) {
            return false
        }
        val isBottomActive = isBottomSurfaceActive || isKeyboardVisible || hasPhysicalBottomObstruction
        if (!isBottomActive || !hasActiveScroll) {
            return false
        }
        return (temporaryDisplacement > 0f) || isAtLastLine
    }
}
