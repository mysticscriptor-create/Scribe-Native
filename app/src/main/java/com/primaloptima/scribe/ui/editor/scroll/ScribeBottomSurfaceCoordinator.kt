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
     * Validates whether a keyboard height qualifies as a standard typing keyboard baseline,
     * specifically rejecting expanded picker keyboards (emojis, stickers, GIFs, clipboard)
     * which exceed standard typing keyboard bounds or significantly exceed an established baseline.
     */
    fun isStandardKeyboardBaseline(
        heightDp: Float,
        currentBaselineDp: Float,
        screenHeightDp: Float
    ): Boolean {
        if (!isValidStandardKeyboardHeight(heightDp, screenHeightDp)) return false
        val maxStandardTypingDp = when {
            screenHeightDp >= 900f -> minOf(MAX_STANDARD_KEYBOARD_DP, screenHeightDp * 0.40f)
            screenHeightDp > 500f -> 330f
            else -> calculateMaxStandardBaselineDp(screenHeightDp)
        }
        if (heightDp > maxStandardTypingDp) return false
        val minBaseline = calculateMinStandardBaselineDp(screenHeightDp)
        if (currentBaselineDp in minBaseline..maxStandardTypingDp && heightDp > currentBaselineDp + 30f) {
            return false
        }
        return true
    }

    /**
     * Determines whether the standard keyboard baseline stored in DataStore should be updated.
     */
    fun shouldUpdateSavedBaseline(
        settledHeightDp: Float,
        currentBaselineDp: Float,
        screenHeightDp: Float
    ): Boolean {
        if (!isStandardKeyboardBaseline(settledHeightDp, currentBaselineDp, screenHeightDp)) return false
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
        imeAboveNavDp: Float,
        activeTransition: ScribeDockTransition = ScribeDockTransition.NONE,
        capturedHandoffImeHeightDp: Float? = null
    ): Float {
        // 1. Hand-off priority: exchange occupancy of one stable bottom-surface slot without gaps
        if (activeTransition == ScribeDockTransition.DRAWER_TO_IME_HANDOFF ||
            activeTransition == ScribeDockTransition.IME_TO_DRAWER_HANDOFF
        ) {
            val handoffBaseline = capturedHandoffImeHeightDp ?: maxOf(compactHeightDp, imeAboveNavDp)
            val effectiveDrawer = if (capturedHandoffImeHeightDp != null && isAccessoryDrawerActive && drawerHeightDp > capturedHandoffImeHeightDp && drawerHeightDp == compactHeightDp) {
                capturedHandoffImeHeightDp
            } else {
                drawerHeightDp
            }
            return maxOf(handoffBaseline, imeAboveNavDp, effectiveDrawer)
        }

        // 2. Active accessory drawer (steady-state)
        if (isAccessoryDrawerActive) {
            return drawerHeightDp
        }

        // 3. Active keyboard typing (steady-state)
        if (isKeyboardVisible) {
            // Live measured keyboard height: reflects actual physical IME obstruction without phantom baseline inflation.
            // When standard keyboard opens, follows actual measured imeAboveNavDp (e.g. 244dp), eliminating 36dp extra padding.
            // When emoji keyboard expands, follows imeAboveNavDp smoothly.
            return imeAboveNavDp
        }

        // 4. In-flight transitions
        if (isAwaitingKeyboardOpen || isBarTransitionLatched) {
            // In-flight transition: follow live arriving IME insets rather than prematurely bloating layout
            return imeAboveNavDp
        }

        // 5. Soft keyboard dismissing
        if (imeAboveNavDp > 10f) {
            // Keyboard is dismissing: allows the visual shortcut bar to follow IME down
            return imeAboveNavDp
        }

        return 0f
    }

    /**
     * Authoritatively creates an immutable ScribeDockGeometry snapshot for unified canvas and screen layout.
     */
    fun calculateDockGeometry(
        dockMode: ScribeDockMode,
        activeTransition: ScribeDockTransition,
        transitionGeneration: Long,
        compactHeightDp: Float,
        imeAboveNavDp: Float,
        currentDrawerHeightDp: Float,
        isAccessoryDrawerActive: Boolean,
        isKeyboardVisible: Boolean,
        isAwaitingKeyboardOpen: Boolean,
        isBarTransitionLatched: Boolean,
        capturedHandoffImeHeightDp: Float?,
        hasShortcuts: Boolean,
        density: Float,
        breathingMarginPx: Float,
        targetImeAboveNavDp: Float = 0f
    ): ScribeDockGeometry {
        val standardKeyboardBaselinePx = compactHeightDp * density
        val shortcutBarHeightPx = (if (hasShortcuts) 36f else 0f) * density
        val currentImeObstructionPx = imeAboveNavDp * density
        val currentDrawerHeightPx = currentDrawerHeightDp * density
        val targetImeObstructionPx = targetImeAboveNavDp * density

        val effectiveSlotDp = resolveEffectiveBottomSurfaceHeight(
            isAccessoryDrawerActive = isAccessoryDrawerActive,
            drawerHeightDp = currentDrawerHeightDp,
            isAwaitingKeyboardOpen = isAwaitingKeyboardOpen,
            isBarTransitionLatched = isBarTransitionLatched,
            isKeyboardVisible = isKeyboardVisible,
            compactHeightDp = compactHeightDp,
            imeAboveNavDp = imeAboveNavDp,
            activeTransition = activeTransition,
            capturedHandoffImeHeightDp = capturedHandoffImeHeightDp
        )

        val physicalDockClearanceAppliedPx = (effectiveSlotDp * density) + (if (effectiveSlotDp > 0f) shortcutBarHeightPx else 0f)

        val virtualTailPx = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKeyboardBaselinePx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = physicalDockClearanceAppliedPx
        )

        return ScribeDockGeometry(
            dockMode = dockMode,
            activeTransition = activeTransition,
            transitionGeneration = transitionGeneration,
            standardKeyboardBaselinePx = standardKeyboardBaselinePx,
            currentImeObstructionPx = currentImeObstructionPx,
            currentDrawerHeightPx = currentDrawerHeightPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            isShortcutBarOccupied = hasShortcuts,
            physicalDockClearanceAppliedPx = physicalDockClearanceAppliedPx,
            virtualTailPx = virtualTailPx,
            targetImeObstructionPx = targetImeObstructionPx
        )
    }

    /**
     * Classifies IME transitions using source, current, and target geometry above navigation bar.
     *
     * Transitions:
     * - Source zero -> Target positive: IME_ENTRANCE
     * - Source positive -> Target larger positive: IME_RESIZE (expansion)
     * - Source positive -> Target smaller positive: IME_RESIZE (contraction)
     * - Source positive -> Target zero: IME_DISMISSAL
     *
     * Invariants:
     * 1. Zero target check takes precedence over visible-IME resize branch.
     * 2. Preserves active IME_TO_DRAWER_HANDOFF and DRAWER_TO_IME_HANDOFF.
     * 3. Retains IME_DISMISSAL while current IME obstruction remains positive.
     */
    fun classifyImeTransition(
        sourceAboveNavDp: Float,
        targetAboveNavDp: Float,
        currentAboveNavDp: Float,
        isImeVisible: Boolean,
        activeTransition: ScribeDockTransition = ScribeDockTransition.NONE
    ): ScribeDockTransition {
        // Exception: preserve an explicitly active drawer handoff
        if (activeTransition == ScribeDockTransition.IME_TO_DRAWER_HANDOFF ||
            activeTransition == ScribeDockTransition.DRAWER_TO_IME_HANDOFF
        ) {
            return activeTransition
        }

        val isTargetZero = targetAboveNavDp <= 0.5f
        val isCurrentPositive = currentAboveNavDp > 0.5f

        // Critical ordering: Check for a zero IME target before entering the ordinary visible-keyboard resize branch.
        // Even if isImeVisible remains true, a zero target indicates dismissal.
        if (isTargetZero) {
            if (isCurrentPositive || isImeVisible || activeTransition == ScribeDockTransition.IME_DISMISSAL) {
                return ScribeDockTransition.IME_DISMISSAL
            }
            return ScribeDockTransition.NONE
        }

        // Latch and retain IME_DISMISSAL while current IME is still positive
        if (activeTransition == ScribeDockTransition.IME_DISMISSAL && isCurrentPositive) {
            return ScribeDockTransition.IME_DISMISSAL
        }

        // Target is positive
        val isSourcePositive = sourceAboveNavDp > 0.5f
        if (!isSourcePositive || (!isImeVisible && currentAboveNavDp <= 0.5f)) {
            return ScribeDockTransition.IME_ENTRANCE
        }

        if (targetAboveNavDp > sourceAboveNavDp + 8f) {
            return ScribeDockTransition.IME_RESIZE
        }

        if (targetAboveNavDp < sourceAboveNavDp - 8f) {
            return ScribeDockTransition.IME_RESIZE
        }

        // If already in an active IME transition (e.g. entrance), retain it until converged
        if (activeTransition == ScribeDockTransition.IME_ENTRANCE) {
            return ScribeDockTransition.IME_ENTRANCE
        }
        if (activeTransition == ScribeDockTransition.IME_RESIZE) {
            return ScribeDockTransition.IME_RESIZE
        }

        return ScribeDockTransition.NONE
    }

    /**
     * Resolves whether the soft keyboard is currently in the process of dismissing.
     */
    fun isKeyboardDismissing(
        isKeyboardVisible: Boolean,
        isAccessoryDrawerActive: Boolean,
        isAwaitingKeyboardOpen: Boolean,
        isBarTransitionLatched: Boolean,
        imeAboveNavDp: Float,
        activeTransition: ScribeDockTransition = ScribeDockTransition.NONE,
        imeTargetAboveNavDp: Float = -1f
    ): Boolean {
        if (activeTransition == ScribeDockTransition.IME_DISMISSAL) {
            return true
        }
        if (isAccessoryDrawerActive ||
            isAwaitingKeyboardOpen ||
            isBarTransitionLatched ||
            activeTransition == ScribeDockTransition.DRAWER_TO_IME_HANDOFF ||
            activeTransition == ScribeDockTransition.IME_TO_DRAWER_HANDOFF
        ) {
            return false
        }
        // Authoritative detection from zero target while current IME is still positive
        if (imeTargetAboveNavDp in 0f..0.5f && imeAboveNavDp > 0.5f) {
            return true
        }
        return !isKeyboardVisible && imeAboveNavDp > 0.5f
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
     * @param activeTransition Originating dock transition provenance.
     */
    fun shouldFollowDownward(
        isKeyboardDismissing: Boolean,
        isBottomSurfaceActive: Boolean,
        isKeyboardVisible: Boolean,
        hasPhysicalBottomObstruction: Boolean,
        isAtLastLine: Boolean,
        hasActiveScroll: Boolean,
        temporaryDisplacement: Float = 0f,
        activeTransition: ScribeDockTransition = ScribeDockTransition.NONE
    ): Boolean {
        // Pure Writer Invariant:
        // When keyboard is dismissed or actively closing, or drawer is closing to 0,
        // the document MUST remain 100% completely stationary at the saved margin.
        // NEVER pull down to the bottom of the screen!
        if (isKeyboardDismissing ||
            activeTransition == ScribeDockTransition.IME_DISMISSAL ||
            activeTransition == ScribeDockTransition.DRAWER_CLOSE
        ) {
            return false
        }

        // Same-height handoffs (IME <-> Drawer) without prior expansion must NEVER pull document down
        if (activeTransition == ScribeDockTransition.IME_TO_DRAWER_HANDOFF) {
            return false
        }
        if (activeTransition == ScribeDockTransition.DRAWER_TO_IME_HANDOFF && temporaryDisplacement <= 0f) {
            return false
        }

        // If no bottom surface remains active, document must stay stationary at saved margin.
        val isBottomActive = isBottomSurfaceActive || isKeyboardVisible
        if (!isBottomActive) {
            return false
        }

        if (!hasActiveScroll) {
            return false
        }

        // Downward follow is permitted when an active bottom surface contracts:
        // 1. A prior expansion displaced the canvas upward (temporaryDisplacement > 0f) across ANY caret position, OR
        // 2. The cursor is at the last line of the document following an actively contracting drawer or active surface,
        // provided an active contraction transition is underway.
        val isContractingTransition = activeTransition == ScribeDockTransition.DRAWER_RESIZE ||
            activeTransition == ScribeDockTransition.IME_RESIZE
        if (!isContractingTransition && temporaryDisplacement <= 0f) {
            return false
        }

        return (temporaryDisplacement > 0f) || (isAtLastLine && isContractingTransition)
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
