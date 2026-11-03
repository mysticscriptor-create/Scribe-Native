package com.primaloptima.scribe.ui.components

/**
 * ScribeBottomSurfaceCoordinator — Unified single source of truth for bottom surface
 * presentation state machine, shortcut bar clearance, and viewport safety geometry.
 *
 * Architecture Principles:
 * 1. Three Clear Ownership Boundaries:
 *    - Physical Viewport & Insets: Owned by Compose WindowInsets.ime and WindowInsets.navigationBars.
 *    - Presentation State: Owned by the authoritative PresentationState reducer (Hidden, Entrance, Settled, Dismissal, Drawer, Handoff).
 *    - Document Geometry & Scrolling: Owned by UnifiedCanvasLayout and ScribeScrollPolicy via actual measured viewport.
 * 2. Shared Dimensions & Clearance:
 *    - The editor must never reserve shortcut bar height while invisible.
 *    - At zero visibility, bar occupies 0dp. At full visibility, bar occupies 36dp.
 *    - Intermediate frames use matching dimensions.
 * 3. Pure Writer Stationary Invariant:
 *    - Total document scroll (scrollD + editor.offsetY) remains 100% stationary when keyboard is dismissed.
 *    - Viewport-owned safety animations are cleanly cancelled on dismissal without affecting user scrolls or deletion anchors.
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
    enum class AccessoryDrawerState {
        COMPACT,
        EXPANDED,
        FULL
    }

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
     * Authoritative event definitions driving bottom-surface presentation transitions.
     */
    sealed interface PresentationEvent {
        data class ImeTargetChanged(val targetBottomPx: Int, val currentBottomPx: Int) : PresentationEvent
        data class ImeAnimationProgress(val currentBottomPx: Int, val targetBottomPx: Int) : PresentationEvent
        data object ImeSettledAtTarget : PresentationEvent
        data object ImeDismissalStarted : PresentationEvent
        data object ImeDismissalFinished : PresentationEvent
        data object KeyboardEntranceStarted : PresentationEvent
        data class DrawerOpened(val mode: String) : PresentationEvent
        data object DrawerClosed : PresentationEvent
        data class DrawerSwitched(val mode: String) : PresentationEvent
        data class DrawerToKeyboardHandoffStarted(
            val capturedDrawerHeightDp: Float,
            val startingImeAboveNavDp: Float = 0f,
            val targetImeAboveNavDp: Float = 0f
        ) : PresentationEvent
        data object HandoffCompleted : PresentationEvent
        data object HandoffFailed : PresentationEvent
        data object ShortcutsConfigurationChanged : PresentationEvent
        data class ScreenRestored(
            val isImeVisible: Boolean,
            val currentImeBottomPx: Int,
            val targetImeBottomPx: Int,
            val hasActiveDrawer: Boolean
        ) : PresentationEvent
    }

    /**
     * Explicit tolerance for comparing physical inset measurements against animated target insets.
     */
    const val IME_TARGET_TOLERANCE_PX = 4

    fun isImeAtTarget(targetImePx: Int, curImePx: Int): Boolean {
        return kotlin.math.abs(targetImePx - curImePx) <= IME_TARGET_TOLERANCE_PX
    }

    /**
     * Authoritative pure transition reducer governing the bottom surface presentation lifecycle.
     */
    fun reduce(currentState: PresentationState, event: PresentationEvent): PresentationState {
        return when (event) {
            is PresentationEvent.DrawerOpened -> PresentationState.ACCESSORY_DRAWER_OPEN
            is PresentationEvent.DrawerClosed -> {
                if (currentState == PresentationState.ACCESSORY_DRAWER_OPEN ||
                    currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.HIDDEN
                } else {
                    currentState
                }
            }
            is PresentationEvent.DrawerSwitched -> {
                PresentationState.ACCESSORY_DRAWER_OPEN
            }
            is PresentationEvent.DrawerToKeyboardHandoffStarted -> {
                if (currentState == PresentationState.ACCESSORY_DRAWER_OPEN ||
                    currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.DRAWER_TO_KEYBOARD_HANDOFF
                } else {
                    currentState
                }
            }
            is PresentationEvent.HandoffCompleted -> {
                if (currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.KEYBOARD_SETTLED
                } else {
                    currentState
                }
            }
            is PresentationEvent.HandoffFailed -> {
                if (currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.HIDDEN
                } else {
                    currentState
                }
            }
            is PresentationEvent.KeyboardEntranceStarted -> {
                if (currentState == PresentationState.HIDDEN ||
                    currentState == PresentationState.KEYBOARD_DISMISSAL) {
                    PresentationState.KEYBOARD_ENTRANCE
                } else {
                    currentState
                }
            }
            is PresentationEvent.ImeSettledAtTarget -> {
                if (currentState == PresentationState.KEYBOARD_ENTRANCE) {
                    PresentationState.KEYBOARD_SETTLED
                } else {
                    currentState
                }
            }
            is PresentationEvent.ImeDismissalStarted -> {
                if (currentState == PresentationState.KEYBOARD_SETTLED ||
                    currentState == PresentationState.KEYBOARD_ENTRANCE ||
                    currentState == PresentationState.ACCESSORY_DRAWER_OPEN ||
                    currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.KEYBOARD_DISMISSAL
                } else {
                    currentState
                }
            }
            is PresentationEvent.ImeDismissalFinished -> {
                if (currentState == PresentationState.KEYBOARD_DISMISSAL ||
                    currentState == PresentationState.KEYBOARD_ENTRANCE ||
                    currentState == PresentationState.KEYBOARD_SETTLED) {
                    PresentationState.HIDDEN
                } else {
                    currentState
                }
            }
            is PresentationEvent.ImeTargetChanged -> {
                if (event.targetBottomPx > 0) {
                    when (currentState) {
                        PresentationState.HIDDEN,
                        PresentationState.KEYBOARD_DISMISSAL -> {
                            if (isImeAtTarget(targetImePx = event.targetBottomPx, curImePx = event.currentBottomPx)) {
                                PresentationState.KEYBOARD_SETTLED
                            } else {
                                PresentationState.KEYBOARD_ENTRANCE
                            }
                        }
                        PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> {
                            // Retain handoff state until full geometry reconciliation completes
                            currentState
                        }
                        PresentationState.KEYBOARD_ENTRANCE -> {
                            if (isImeAtTarget(targetImePx = event.targetBottomPx, curImePx = event.currentBottomPx)) {
                                PresentationState.KEYBOARD_SETTLED
                            } else {
                                PresentationState.KEYBOARD_ENTRANCE
                            }
                        }
                        else -> currentState
                    }
                } else {
                    // targetBottomPx == 0 (dismissal requested)
                    when (currentState) {
                        PresentationState.KEYBOARD_SETTLED,
                        PresentationState.KEYBOARD_ENTRANCE -> {
                            if (event.currentBottomPx <= IME_TARGET_TOLERANCE_PX) {
                                PresentationState.HIDDEN
                            } else {
                                PresentationState.KEYBOARD_DISMISSAL
                            }
                        }
                        PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> {
                            PresentationState.HIDDEN
                        }
                        else -> currentState
                    }
                }
            }
            is PresentationEvent.ImeAnimationProgress -> {
                when (currentState) {
                    PresentationState.KEYBOARD_ENTRANCE -> {
                        if (event.targetBottomPx > 0 && isImeAtTarget(targetImePx = event.targetBottomPx, curImePx = event.currentBottomPx)) {
                            PresentationState.KEYBOARD_SETTLED
                        } else {
                            currentState
                        }
                    }
                    PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> {
                        // In handoff, require explicit HandoffCompleted event after geometry verification
                        currentState
                    }
                    PresentationState.KEYBOARD_DISMISSAL -> {
                        if (event.currentBottomPx <= IME_TARGET_TOLERANCE_PX) {
                            PresentationState.HIDDEN
                        } else {
                            currentState
                        }
                    }
                    else -> currentState
                }
            }
            is PresentationEvent.ShortcutsConfigurationChanged -> {
                currentState
            }
            is PresentationEvent.ScreenRestored -> {
                if (event.hasActiveDrawer) {
                    PresentationState.ACCESSORY_DRAWER_OPEN
                } else if (event.isImeVisible && event.targetImeBottomPx > 0 &&
                    isImeAtTarget(targetImePx = event.targetImeBottomPx, curImePx = event.currentImeBottomPx)) {
                    PresentationState.KEYBOARD_SETTLED
                } else if (event.isImeVisible || event.targetImeBottomPx > 0) {
                    PresentationState.KEYBOARD_ENTRANCE
                } else {
                    PresentationState.HIDDEN
                }
            }
        }
    }

    /**
     * Determines whether the shortcut bar should be visually visible based on presentation state.
     * Note: Visual visibility controls drawing/opacity, NOT viewport layout geometry.
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
     * Authoritative check for shortcut-bar geometry reservation:
     * Determines whether the editor layout must reserve the fixed 36dp layout slot.
     * Begins when pre-IME entrance is initiated (KEYBOARD_ENTRANCE), stays stable through
     * entrance and settled phases, and remains reserved during dismissal until the transition
     * cleanly finishes into HIDDEN.
     * When hasShortcuts is false, no slot is reserved.
     */
    fun isShortcutBarGeometryReserved(state: PresentationState, hasShortcuts: Boolean = true): Boolean {
        if (!hasShortcuts) return false
        return when (state) {
            PresentationState.HIDDEN -> false
            PresentationState.KEYBOARD_ENTRANCE,
            PresentationState.KEYBOARD_SETTLED,
            PresentationState.KEYBOARD_DISMISSAL,
            PresentationState.ACCESSORY_DRAWER_OPEN,
            PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> true
        }
    }

    /**
     * Computes the reserved shortcut-bar clearance in dp based on geometry reservation state.
     */
    fun calculateReservedShortcutBarClearanceDp(state: PresentationState, hasShortcuts: Boolean = true): Float {
        return if (isShortcutBarGeometryReserved(state, hasShortcuts)) SHORTCUT_BAR_HEIGHT_DP else 0f
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
     * Authoritative calculation for the complete three-part retained bottom clearance:
     * actual IME obstruction above navigation baseline + actual shortcut bar height + breathing margin.
     * Used exclusively for virtual document scroll clearance (never as visible padding).
     */
    fun calculateRetainedBottomScrollClearancePx(
        actualObstructionAboveNavPx: Float,
        shortcutBarHeightPx: Float,
        breathingMarginPx: Float
    ): Float {
        return (actualObstructionAboveNavPx.coerceAtLeast(0f) + shortcutBarHeightPx.coerceAtLeast(0f) + breathingMarginPx.coerceAtLeast(0f))
    }

    /**
     * Computes the virtual trailing clearance in pixels for the keyboard-hidden state.
     * Captures the three-part geometry:
     * standard keyboard baseline + shortcut bar slot + breathing margin.
     * Does NOT include transient drawer or oversized IME expansions.
     */
    fun calculateStandardVirtualBottomClearancePx(
        standardKeyboardHeightPx: Float,
        shortcutBarHeightPx: Float,
        breathingMarginPx: Float
    ): Float {
        return (standardKeyboardHeightPx.coerceAtLeast(0f) +
                shortcutBarHeightPx.coerceAtLeast(0f) +
                breathingMarginPx.coerceAtLeast(0f))
    }

    /**
     * Determines the virtual bottom clearance in pixels based on whether a physical
     * bottom surface is currently active.
     *
     * Invariant:
     * - When a physical bottom surface (IME or drawer) is active, it is already reducing
     *   the measured viewport. Its height must NOT be double-counted in virtual scroll space.
     *   Therefore, only [breathingMarginPx] is virtual.
     * - When no physical bottom surface is active, the full standard clearance
     *   (standard keyboard baseline + shortcut bar + breathing margin) is returned.
     */
    fun calculateVirtualBottomSpacePx(
        isPhysicalBottomSurfaceActive: Boolean,
        standardKeyboardHeightPx: Float,
        shortcutBarHeightPx: Float,
        breathingMarginPx: Float,
        retainedClearancePx: Float = 0f
    ): Float {
        return if (isPhysicalBottomSurfaceActive) {
            breathingMarginPx.coerceAtLeast(0f)
        } else {
            calculateStandardVirtualBottomClearancePx(
                standardKeyboardHeightPx = standardKeyboardHeightPx,
                shortcutBarHeightPx = shortcutBarHeightPx,
                breathingMarginPx = breathingMarginPx
            )
        }
    }

    /**
     * Authoritative bottom geometry model capturing every component of bottom obstruction.
     */
    data class EditorBottomGeometry(
        val navBarBottomDp: Float,
        val imeBottomDp: Float,
        val imeAboveNavDp: Float,
        val displayedDrawerHeightDp: Float,
        val effectiveBottomSurfaceHeightDp: Float,
        val shortcutBarClearanceDp: Float,
        val editorBottomPaddingDp: Float
    )

    /**
     * Computes the authoritative bottom geometry across navigation bar, physical keyboard,
     * accessory drawer, drawer-to-keyboard handoff, and shortcut bar.
     * Guarantees that:
     * - Navigation inset is counted exactly once as the baseline.
     * - Physical IME and drawer obstructions are reconciled according to presentation state.
     * - Drawer replaces keyboard rather than stacking above it.
     * - Shortcut bar clearance is added without double-counting.
     */
    fun calculateEditorBottomGeometry(
        navBarBottomDp: Float,
        imeBottomDp: Float,
        presentationState: PresentationState,
        displayedDrawerHeightDp: Float = 0f,
        handoffObstructionDp: Float = 0f,
        shortcutBarClearanceDp: Float = 0f
    ): EditorBottomGeometry {
        val imeAboveNav = (imeBottomDp - navBarBottomDp).coerceAtLeast(0f)
        val effectiveBottomSurface = when (presentationState) {
            PresentationState.ACCESSORY_DRAWER_OPEN -> displayedDrawerHeightDp
            PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> handoffObstructionDp
            else -> imeAboveNav
        }
        val totalPadding = navBarBottomDp + effectiveBottomSurface + shortcutBarClearanceDp
        return EditorBottomGeometry(
            navBarBottomDp = navBarBottomDp,
            imeBottomDp = imeBottomDp,
            imeAboveNavDp = imeAboveNav,
            displayedDrawerHeightDp = displayedDrawerHeightDp,
            effectiveBottomSurfaceHeightDp = effectiveBottomSurface,
            shortcutBarClearanceDp = shortcutBarClearanceDp,
            editorBottomPaddingDp = totalPadding
        )
    }

    /**
     * Computes continuous bottom surface height in dp during drawer-to-keyboard handoff.
     * Starts from the actual captured starting obstruction and interpolates to target keyboard height
     * using normalized progress between starting and target IME insets.
     *
     * Handles:
     * - Keyboard starting at nonzero inset.
     * - Drawer taller than keyboard.
     * - Drawer shorter than keyboard.
     * - Starting and target IME equal or nearly equal (prevents divide by zero).
     * - Explicit transition progress override.
     */
    fun calculateHandoffBottomSurfaceHeightDp(
        startingObstructionDp: Float,
        startingImeAboveNavDp: Float,
        currentImeAboveNavDp: Float,
        targetImeAboveNavDp: Float,
        explicitProgress: Float? = null
    ): Float {
        if (explicitProgress != null) {
            val progress = explicitProgress.coerceIn(0f, 1f)
            return startingObstructionDp + (targetImeAboveNavDp - startingObstructionDp) * progress
        }
        if (targetImeAboveNavDp <= 0f) {
            return startingObstructionDp
        }
        val imeDelta = targetImeAboveNavDp - startingImeAboveNavDp
        val progress = if (kotlin.math.abs(imeDelta) < 0.5f) {
            if (currentImeAboveNavDp >= targetImeAboveNavDp - 1f) 1f else 0f
        } else {
            ((currentImeAboveNavDp - startingImeAboveNavDp) / imeDelta).coerceIn(0f, 1f)
        }
        return startingObstructionDp + (targetImeAboveNavDp - startingObstructionDp) * progress
    }

    /**
     * Backward-compatible overload assuming IME starts at 0dp.
     */
    fun calculateHandoffBottomSurfaceHeightDp(
        capturedDrawerHeightDp: Float,
        currentImeAboveNavDp: Float,
        targetImeAboveNavDp: Float
    ): Float = calculateHandoffBottomSurfaceHeightDp(
        startingObstructionDp = capturedDrawerHeightDp,
        startingImeAboveNavDp = 0f,
        currentImeAboveNavDp = currentImeAboveNavDp,
        targetImeAboveNavDp = targetImeAboveNavDp
    )

    /**
     * Authoritative condition for completing drawer-to-keyboard handoff.
     * Prevents clearing handoff state merely because physical IME is within tolerance;
     * ensures effective obstruction has reached the intended final keyboard geometry and
     * the switch from handoff to normal keyboard geometry is completely continuous.
     */
    fun canCompleteHandoff(
        currentHandoffObstructionDp: Float,
        finalKeyboardObstructionDp: Float,
        isImeAtTarget: Boolean,
        toleranceDp: Float = 0.5f
    ): Boolean {
        return isImeAtTarget && kotlin.math.abs(currentHandoffObstructionDp - finalKeyboardObstructionDp) <= toleranceDp
    }

    /**
     * Computes the authoritative bottom padding to apply to the editor layout.
     * In Scribe production architecture, the accessory drawer replaces the keyboard rather than stacking above it.
     * If [stacksWithKeyboard] is explicitly enabled, it stacks above maxOf(imeBottomDp, navBarBottomDp).
     * By default, [stacksWithKeyboard] is false to match production UI geometry.
     */
    fun calculateEditorBottomPaddingDp(
        imeBottomDp: Float,
        navBarBottomDp: Float,
        drawerHeightDp: Float,
        shortcutBarClearanceDp: Float,
        stacksWithKeyboard: Boolean = false
    ): Float {
        val baseObstruction = if (stacksWithKeyboard) {
            if (drawerHeightDp > 0f) {
                maxOf(imeBottomDp, navBarBottomDp) + drawerHeightDp
            } else {
                maxOf(imeBottomDp, navBarBottomDp)
            }
        } else {
            val imeAboveNav = (imeBottomDp - navBarBottomDp).coerceAtLeast(0f)
            val effectiveBottomSurface = if (drawerHeightDp > 0f) drawerHeightDp else imeAboveNav
            navBarBottomDp + effectiveBottomSurface
        }
        return baseObstruction + shortcutBarClearanceDp
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
     * Backwards-compatible Pure Writer Invariant Decision Function.
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
        if (isKeyboardDismissing) {
            return false
        }
        val isBottomActive = isBottomSurfaceActive || isKeyboardVisible || hasPhysicalBottomObstruction
        if (!isBottomActive || !hasActiveScroll) {
            return false
        }
        // Downward follow is permitted ONLY when a prior expansion displaced the canvas upward (temporaryDisplacement > 0f)
        // and returns to normal state (never during terminal dismissal, and never without recorded displacement).
        // Downward follow is permitted when:
        // 1. A prior expansion displaced the canvas upward (temporaryDisplacement > 0f) across ANY caret position, OR
        // 2. The cursor is at the last line of the document following an actively contracting bottom surface (never when keyboard is dismissing).
        return (temporaryDisplacement > 0f) || (isAtLastLine && !isKeyboardDismissing)
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
     * Resolves a valid standard keyboard baseline in dp from stored or raw inputs.
     * If the provided [rawHeightDp] passes [isValidStandardKeyboardHeight] against [screenHeightDp],
     * it is returned. Otherwise, [DEFAULT_KEYBOARD_DP] (280dp) is returned as fallback.
     * Never throws, never returns an oversized or negative height.
     */
    fun resolveStandardKeyboardBaselineDp(rawHeightDp: Float?, screenHeightDp: Float): Float {
        if (rawHeightDp == null) return DEFAULT_KEYBOARD_DP
        return if (isValidStandardKeyboardHeight(rawHeightDp, screenHeightDp)) {
            rawHeightDp
        } else {
            DEFAULT_KEYBOARD_DP
        }
    }

    /**
     * Determines whether a measured settled keyboard height qualifies to update the stored standard baseline.
     * All of the following must hold:
     * 1. Physical keyboard is actually visible and settled at target inset.
     * 2. Presentation state is KEYBOARD_SETTLED (not entrance, dismissal, drawer, or handoff).
     * 3. Accessory drawer is NOT active.
     * 4. Multi-window or split-screen mode is NOT active (prevents split-screen keyboard from poisoning full-screen baseline).
     * 5. Measured height passes the screen-aware standard keyboard validator (rejects temporary emoji/sticker/GIF/clipboard expansions).
     * 6. Height change is significant (> 2dp difference from current baseline).
     */
    fun canUpdateSavedStandardBaseline(
        presentationState: PresentationState,
        hasActiveDrawer: Boolean,
        isImeSettled: Boolean,
        measuredHeightDp: Float,
        currentBaselineDp: Float,
        screenHeightDp: Float,
        isInMultiWindowMode: Boolean = false
    ): Boolean {
        if (isInMultiWindowMode) return false
        if (hasActiveDrawer) return false
        if (presentationState != PresentationState.KEYBOARD_SETTLED) return false
        if (!isImeSettled) return false
        if (!isValidStandardKeyboardHeight(measuredHeightDp, screenHeightDp)) return false
        return shouldUpdateSavedBaseline(measuredHeightDp, currentBaselineDp, screenHeightDp)
    }

    /**
     * Determines whether Scribe owns the active soft-keyboard input session.
     * All of the following must hold:
     * 1. Scribe's window currently has window focus.
     * 2. The editor is focused or Scribe is in an active keyboard entrance, settled, or handoff lifecycle.
     */
    fun isScribeImeOwner(
        hasWindowFocus: Boolean,
        isEditorFocused: Boolean,
        presentationState: PresentationState,
        isKeyboardTransactionActive: Boolean
    ): Boolean {
        if (!hasWindowFocus) return false
        return isEditorFocused || isKeyboardTransactionActive ||
            presentationState == PresentationState.KEYBOARD_ENTRANCE ||
            presentationState == PresentationState.KEYBOARD_SETTLED ||
            presentationState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF ||
            presentationState == PresentationState.ACCESSORY_DRAWER_OPEN
    }

    /**
     * Calculates the predicted authoritative target editor viewport height in pixels.
     * Enforces single subtraction: containerHeightPx - topPaddingPx - targetBottomPaddingPx.
     * NEVER double-subtracts padding from an already constrained layout height.
     */
    fun calculateTargetEditorViewportPx(
        containerHeightPx: Int,
        topPaddingPx: Int,
        targetBottomPaddingPx: Int
    ): Int {
        return (containerHeightPx - topPaddingPx - targetBottomPaddingPx).coerceAtLeast(0)
    }

    data class ResponsiveDrawerHeights(
        val compactDp: Float,
        val expandedDp: Float,
        val fullDp: Float
    )

    /**
     * Calculates authoritative, responsive accessory drawer heights for any window or container height.
     * Enforces the strict invariant:
     * 0f <= compactDp <= expandedDp <= fullDp <= maxOf(0f, availableHeightDp)
     * Never throws IllegalArgumentException from inverted ranges.
     * Fully preserves full-screen standard dimensions on larger displays.
     */
    fun calculateResponsiveDrawerHeights(
        availableHeightDp: Float,
        preferredBaselineDp: Float = DEFAULT_KEYBOARD_DP
    ): ResponsiveDrawerHeights {
        val maxAllowed = maxOf(0f, availableHeightDp)
        if (maxAllowed <= 0f) {
            return ResponsiveDrawerHeights(0f, 0f, 0f)
        }

        // Full mode: preserves standard full-screen range [480..650dp] when space permits,
        // but gracefully scales down and clamps to available space when constrained.
        val targetFull = minOf(650f, maxAllowed)
        val full = if (maxAllowed >= 480f) {
            (maxAllowed * 0.72f).coerceIn(480f, targetFull)
        } else {
            (maxAllowed * 0.88f).coerceIn(0f, targetFull)
        }

        // Expanded mode: preserves standard range [360..480dp] bounded by full height.
        val maxExpanded = minOf(full, targetFull)
        val expanded = if (maxAllowed >= 360f && maxExpanded >= 360f) {
            (maxAllowed * 0.48f).coerceIn(360f, minOf(480f, maxExpanded))
        } else {
            (full * 0.65f).coerceIn(0f, maxExpanded)
        }

        // Compact mode: prefers stored keyboard baseline (e.g. 280dp) or 160dp min, bounded by expanded height.
        val maxCompact = minOf(expanded, maxAllowed * 0.60f)
        val compact = if (maxCompact >= 160f) {
            preferredBaselineDp.coerceIn(160f, maxCompact)
        } else {
            maxCompact
        }.coerceIn(0f, expanded)

        // Ensure strict monotonic ordering: 0 <= compact <= expanded <= full <= maxAllowed
        val safeCompact = compact.coerceIn(0f, maxAllowed)
        val safeExpanded = maxOf(safeCompact, expanded).coerceIn(safeCompact, maxAllowed)
        val safeFull = maxOf(safeExpanded, full).coerceIn(safeExpanded, maxAllowed)

        return ResponsiveDrawerHeights(
            compactDp = safeCompact,
            expandedDp = safeExpanded,
            fullDp = safeFull
        )
    }
}
