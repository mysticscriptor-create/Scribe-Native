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
        data class DrawerOpened(val mode: String) : PresentationEvent
        data object DrawerClosed : PresentationEvent
        data class DrawerSwitched(val mode: String) : PresentationEvent
        data class DrawerToKeyboardHandoffStarted(val capturedDrawerHeightDp: Float) : PresentationEvent
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
                if (currentState == PresentationState.ACCESSORY_DRAWER_OPEN) {
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
            is PresentationEvent.ImeSettledAtTarget -> {
                if (currentState == PresentationState.KEYBOARD_ENTRANCE ||
                    currentState == PresentationState.DRAWER_TO_KEYBOARD_HANDOFF) {
                    PresentationState.KEYBOARD_SETTLED
                } else {
                    currentState
                }
            }
            is PresentationEvent.ImeDismissalStarted -> {
                if (currentState == PresentationState.KEYBOARD_SETTLED ||
                    currentState == PresentationState.KEYBOARD_ENTRANCE ||
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
                            if (event.currentBottomPx >= event.targetBottomPx) {
                                PresentationState.KEYBOARD_SETTLED
                            } else {
                                PresentationState.KEYBOARD_ENTRANCE
                            }
                        }
                        PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> {
                            if (event.currentBottomPx >= event.targetBottomPx) {
                                PresentationState.KEYBOARD_SETTLED
                            } else {
                                currentState
                            }
                        }
                        PresentationState.KEYBOARD_ENTRANCE -> {
                            if (event.currentBottomPx >= event.targetBottomPx) {
                                PresentationState.KEYBOARD_SETTLED
                            } else {
                                currentState
                            }
                        }
                        else -> currentState
                    }
                } else {
                    // targetBottomPx == 0 (dismissal requested)
                    when (currentState) {
                        PresentationState.KEYBOARD_SETTLED,
                        PresentationState.KEYBOARD_ENTRANCE -> {
                            if (event.currentBottomPx <= 0) {
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
                    PresentationState.KEYBOARD_ENTRANCE,
                    PresentationState.DRAWER_TO_KEYBOARD_HANDOFF -> {
                        if (event.targetBottomPx > 0 && event.currentBottomPx >= event.targetBottomPx) {
                            PresentationState.KEYBOARD_SETTLED
                        } else {
                            currentState
                        }
                    }
                    PresentationState.KEYBOARD_DISMISSAL -> {
                        if (event.currentBottomPx <= 0) {
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
                } else if (event.isImeVisible && event.targetImeBottomPx > 0 && event.currentImeBottomPx >= event.targetImeBottomPx) {
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
}
