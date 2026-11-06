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
     * Authoritative classification of bottom-surface transitions.
     * Keeps keyboard entrance, settled, expanded, contraction, terminal dismissal,
     * accessory drawer modes, and foreign window resizing strictly distinct.
     */
    enum class TransitionType {
        NONE,
        KEYBOARD_ENTRANCE,
        KEYBOARD_SETTLED,
        KEYBOARD_EXPANDED,
        KEYBOARD_CONTRACTION,
        TERMINAL_KEYBOARD_DISMISSAL,
        DRAWER_OPENING,
        DRAWER_EXPANSION,
        DRAWER_CONTRACTION,
        DRAWER_DISMISSAL,
        DRAWER_TO_KEYBOARD_HANDOFF,
        VIEWPORT_RESIZE,
        FOREIGN_WINDOW_CHANGE
    }

    /**
     * Authoritative transition lifecycle phases.
     */
    enum class TransitionPhase {
        IDLE,
        STARTING,
        IN_FLIGHT,
        SETTLED,
        COMPLETED,
        CANCELLED
    }

    /**
     * Selection range tracking for transition anchor validation.
     */
    data class SelectionRange(
        val startLine: Int = -1,
        val startColumn: Int = 0,
        val endLine: Int = -1,
        val endColumn: Int = 0,
        val hasSelection: Boolean = false
    ) {
        fun matches(curLine: Int, curCol: Int, isSelected: Boolean): Boolean {
            if (!hasSelection && !isSelected) {
                return startLine == curLine
            }
            return hasSelection == isSelected && startLine == curLine && startColumn == curCol
        }
    }

    /**
     * Active bottom surface ownership classification.
     */
    enum class SurfaceOwner {
        NONE,
        KEYBOARD,
        DRAWER,
        HANDOFF
    }

    /**
     * Authoritative bottom-surface geometry snapshot.
     * Keeps IME, drawer, shortcut bar, and navigation insets separate, providing
     * explicit projections per transition type.
     */
    data class BottomSurfaceGeometrySnapshot(
        val imeObstructionPx: Float = 0f,
        val drawerHeightPx: Float = 0f,
        val shortcutBarReservationPx: Float = 0f,
        val navigationBarInsetPx: Float = 0f,
        val effectiveBottomObstructionPx: Float = 0f,
        val activeOwner: SurfaceOwner = SurfaceOwner.NONE,
        val sequenceNumber: Long = 0L,
        val timestampMs: Long = 0L
    ) {
        fun projectionFor(type: TransitionType): Float = when (type) {
            TransitionType.KEYBOARD_ENTRANCE,
            TransitionType.KEYBOARD_SETTLED,
            TransitionType.KEYBOARD_EXPANDED,
            TransitionType.KEYBOARD_CONTRACTION,
            TransitionType.TERMINAL_KEYBOARD_DISMISSAL -> imeObstructionPx

            TransitionType.DRAWER_OPENING,
            TransitionType.DRAWER_EXPANSION,
            TransitionType.DRAWER_CONTRACTION,
            TransitionType.DRAWER_DISMISSAL -> drawerHeightPx

            TransitionType.DRAWER_TO_KEYBOARD_HANDOFF -> effectiveBottomObstructionPx
            else -> effectiveBottomObstructionPx
        }
    }

    /**
     * Bottom surface physical geometry.
     */
    data class BottomSurfaceGeometry(
        val startingObstructionPx: Float = 0f,
        val targetObstructionPx: Float = 0f,
        val currentObstructionPx: Float = 0f,
        val compactBaselinePx: Float = 0f,
        val physicalViewportHeightPx: Int = 0,
        val activeOwner: SurfaceOwner = SurfaceOwner.NONE
    )

    /**
     * Expansion compensation tracking tied strictly to an active transition.
     */
    data class ExpansionCompensation(
        val recordedDisplacement: Float = 0f,
        val remainingDisplacement: Float = 0f,
        val anchorLine: Int = -1,
        val anchorColumn: Int = 0,
        val selectionRange: SelectionRange = SelectionRange(),
        val isAnchorValid: Boolean = false
    ) {
        val temporaryDisplacementPx: Float get() = remainingDisplacement
    }

    /**
     * Authoritative bottom-surface transition representation.
     */
    data class BottomSurfaceTransition(
        val transitionId: Long = 0L,
        val generation: Long = 0L,
        val linkedExpansionGeneration: Long = 0L,
        val type: TransitionType = TransitionType.NONE,
        val phase: TransitionPhase = TransitionPhase.IDLE,
        val geometry: BottomSurfaceGeometry = BottomSurfaceGeometry(),
        val compensation: ExpansionCompensation = ExpansionCompensation(),
        val preservedScroll: Int? = null
    ) {
        val transitionType: TransitionType get() = type
        val targetObstructionPx: Float get() = geometry.targetObstructionPx
        val initialObstructionPx: Float get() = geometry.startingObstructionPx
        val activeOwner: SurfaceOwner get() = geometry.activeOwner
        val isValid: Boolean get() = phase != TransitionPhase.CANCELLED && phase != TransitionPhase.COMPLETED
        val isTerminalDismissal: Boolean get() = type == TransitionType.TERMINAL_KEYBOARD_DISMISSAL
        val isContraction: Boolean get() = type == TransitionType.KEYBOARD_CONTRACTION || type == TransitionType.DRAWER_CONTRACTION
        val isExpansion: Boolean get() = type == TransitionType.KEYBOARD_EXPANDED || type == TransitionType.DRAWER_EXPANSION || type == TransitionType.DRAWER_OPENING || type == TransitionType.KEYBOARD_ENTRANCE
        val isHandoff: Boolean get() = type == TransitionType.DRAWER_TO_KEYBOARD_HANDOFF
    }

    /**
     * Per-editor authoritative transition coordinator.
     * Manages transition identity, bottom-surface geometry, and expansion compensation
     * strictly per editor instance without global singleton sharing.
     */
    class BottomSurfaceTransitionCoordinator {
        private var generationCounter: Long = 0L
        var currentTransition: BottomSurfaceTransition = BottomSurfaceTransition()
            private set

        val activeType: TransitionType get() = currentTransition.type
        val activePhase: TransitionPhase get() = currentTransition.phase

        fun isTerminalDismissalInFlight(): Boolean = currentTransition.isTerminalDismissal && (currentTransition.phase == TransitionPhase.STARTING || currentTransition.phase == TransitionPhase.IN_FLIGHT)
        val isTerminalDismissalSettled: Boolean get() = currentTransition.isTerminalDismissal && currentTransition.phase == TransitionPhase.SETTLED
        val isTerminalDismissalLockActive: Boolean get() = isTerminalDismissalInFlight()
        val isTerminalDismissalActive: Boolean get() = currentTransition.isTerminalDismissal && currentTransition.isValid
        val isContractionActive: Boolean get() = currentTransition.isContraction && (currentTransition.phase == TransitionPhase.STARTING || currentTransition.phase == TransitionPhase.IN_FLIGHT || currentTransition.phase == TransitionPhase.SETTLED)
        val preservedDismissalScroll: Int? get() = if (currentTransition.isTerminalDismissal) currentTransition.preservedScroll else null

        fun startTransition(
            type: TransitionType,
            startingObstructionPx: Float = 0f,
            targetObstructionPx: Float = 0f,
            compactBaselinePx: Float = targetObstructionPx,
            viewportHeightPx: Int = 0,
            preservedScroll: Int? = null,
            initialObstructionPx: Float = startingObstructionPx,
            linkedGeneration: Long = 0L
        ): Long {
            val effectiveStartingPx = if (startingObstructionPx != 0f) startingObstructionPx else initialObstructionPx

            // Hard invariant: Any keyboard contraction or expansion targeting zero / sub-tolerance
            // is strictly a terminal keyboard dismissal.
            if ((type == TransitionType.KEYBOARD_CONTRACTION || type == TransitionType.KEYBOARD_EXPANDED) &&
                targetObstructionPx <= IME_TARGET_TOLERANCE_PX
            ) {
                return startTerminalDismissal(
                    currentScroll = preservedScroll,
                    initialObstructionPx = effectiveStartingPx
                )
            }

            // Idempotency: If transition of the exact same type is already in progress (STARTING or IN_FLIGHT),
            // do NOT increment generationCounter and do NOT overwrite startingObstructionPx.
            // Retarget within the existing generation if the target obstruction changed.
            if (currentTransition.type == type &&
                (currentTransition.phase == TransitionPhase.STARTING || currentTransition.phase == TransitionPhase.IN_FLIGHT)
            ) {
                if (targetObstructionPx > 0f && kotlin.math.abs(currentTransition.geometry.targetObstructionPx - targetObstructionPx) > 0.5f) {
                    currentTransition = currentTransition.copy(
                        geometry = currentTransition.geometry.copy(
                            targetObstructionPx = targetObstructionPx,
                            compactBaselinePx = if (compactBaselinePx > 0f) compactBaselinePx else currentTransition.geometry.compactBaselinePx,
                            physicalViewportHeightPx = if (viewportHeightPx > 0) viewportHeightPx else currentTransition.geometry.physicalViewportHeightPx
                        )
                    )
                }
                return currentTransition.generation
            }

            generationCounter++

            val linkedGen = if (linkedGeneration != 0L) {
                linkedGeneration
            } else if (type == TransitionType.KEYBOARD_CONTRACTION) {
                if (currentTransition.type == TransitionType.KEYBOARD_EXPANDED) {
                    currentTransition.generation
                } else if (currentTransition.compensation.recordedDisplacement > 0f || currentTransition.compensation.remainingDisplacement > 0f) {
                    if (currentTransition.linkedExpansionGeneration != 0L) currentTransition.linkedExpansionGeneration else currentTransition.generation
                } else {
                    0L
                }
            } else if (type == TransitionType.DRAWER_CONTRACTION) {
                if (currentTransition.type == TransitionType.DRAWER_EXPANSION || currentTransition.type == TransitionType.DRAWER_OPENING) {
                    currentTransition.generation
                } else if (currentTransition.compensation.recordedDisplacement > 0f || currentTransition.compensation.remainingDisplacement > 0f) {
                    if (currentTransition.linkedExpansionGeneration != 0L) currentTransition.linkedExpansionGeneration else currentTransition.generation
                } else {
                    0L
                }
            } else {
                0L
            }

            val inheritedCompensation = if (linkedGen != 0L && (currentTransition.compensation.recordedDisplacement > 0f || currentTransition.compensation.remainingDisplacement > 0f)) {
                currentTransition.compensation
            } else if (type.isContraction && (currentTransition.compensation.recordedDisplacement > 0f || currentTransition.compensation.remainingDisplacement > 0f)) {
                currentTransition.compensation
            } else {
                ExpansionCompensation()
            }

            val owner = when (type) {
                TransitionType.KEYBOARD_ENTRANCE,
                TransitionType.KEYBOARD_SETTLED,
                TransitionType.KEYBOARD_EXPANDED,
                TransitionType.KEYBOARD_CONTRACTION,
                TransitionType.TERMINAL_KEYBOARD_DISMISSAL -> SurfaceOwner.KEYBOARD

                TransitionType.DRAWER_OPENING,
                TransitionType.DRAWER_EXPANSION,
                TransitionType.DRAWER_CONTRACTION,
                TransitionType.DRAWER_DISMISSAL -> SurfaceOwner.DRAWER

                TransitionType.DRAWER_TO_KEYBOARD_HANDOFF -> SurfaceOwner.HANDOFF
                else -> SurfaceOwner.NONE
            }

            val tx = BottomSurfaceTransition(
                transitionId = System.nanoTime(),
                generation = generationCounter,
                linkedExpansionGeneration = linkedGen,
                type = type,
                phase = TransitionPhase.STARTING,
                geometry = BottomSurfaceGeometry(
                    startingObstructionPx = effectiveStartingPx,
                    targetObstructionPx = targetObstructionPx,
                    currentObstructionPx = effectiveStartingPx,
                    compactBaselinePx = compactBaselinePx,
                    physicalViewportHeightPx = viewportHeightPx,
                    activeOwner = owner
                ),
                compensation = inheritedCompensation,
                preservedScroll = preservedScroll
            )
            currentTransition = tx
            return generationCounter
        }

        fun markInFlight(generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val tx = currentTransition.copy(phase = TransitionPhase.IN_FLIGHT)
            currentTransition = tx
            return tx
        }

        fun updatePhysicalObstruction(currentPx: Float, generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val startPx = currentTransition.geometry.startingObstructionPx
            val targetPx = currentTransition.geometry.targetObstructionPx

            val newPhase = if (currentTransition.phase == TransitionPhase.STARTING) {
                val hasProgressed = when {
                    currentTransition.isContraction && startPx > 0f -> (startPx - currentPx) >= IME_TARGET_TOLERANCE_PX
                    currentTransition.isExpansion && startPx >= 0f -> (currentPx - startPx) >= IME_TARGET_TOLERANCE_PX
                    else -> false
                }
                if (hasProgressed) TransitionPhase.IN_FLIGHT else TransitionPhase.STARTING
            } else {
                currentTransition.phase
            }

            val tx = currentTransition.copy(
                geometry = currentTransition.geometry.copy(currentObstructionPx = currentPx),
                phase = newPhase
            )
            currentTransition = tx
            return tx
        }

        fun updateTargetObstruction(targetPx: Float, generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val tx = currentTransition.copy(
                geometry = currentTransition.geometry.copy(targetObstructionPx = targetPx)
            )
            currentTransition = tx
            return tx
        }

        fun recordExpansionDisplacement(
            generation: Long? = null,
            displacementPx: Float = 0f,
            anchorLine: Int = -1,
            anchorCol: Int = 0,
            amountPx: Float = displacementPx,
            selectionRange: SelectionRange = SelectionRange(anchorLine, anchorCol, anchorLine, anchorCol)
        ): Long {
            if (generation != null && generation != currentTransition.generation) return currentTransition.generation
            val effectiveAmount = if (amountPx > 0f) amountPx else displacementPx
            val prevDisp = currentTransition.compensation.recordedDisplacement
            val newDisp = prevDisp + effectiveAmount.coerceAtLeast(0f)
            val prevRemaining = currentTransition.compensation.remainingDisplacement
            val newRemaining = prevRemaining + effectiveAmount.coerceAtLeast(0f)
            val tx = currentTransition.copy(
                compensation = ExpansionCompensation(
                    recordedDisplacement = newDisp,
                    remainingDisplacement = newRemaining,
                    anchorLine = anchorLine,
                    anchorColumn = anchorCol,
                    selectionRange = selectionRange,
                    isAnchorValid = true
                )
            )
            currentTransition = tx
            return currentTransition.generation
        }

        fun recordExpansionDisplacement(
            amountPx: Float,
            anchorLine: Int,
            anchorCol: Int,
            selectionRange: SelectionRange
        ): Long {
            return recordExpansionDisplacement(
                generation = currentTransition.generation,
                displacementPx = amountPx,
                anchorLine = anchorLine,
                anchorCol = anchorCol,
                amountPx = amountPx,
                selectionRange = selectionRange
            )
        }

        fun consumeAppliedMovement(appliedDownPx: Float, generation: Long? = null): Float {
            if (generation != null && generation != currentTransition.generation) return currentTransition.compensation.remainingDisplacement
            val comp = currentTransition.compensation
            val remaining = (comp.remainingDisplacement - appliedDownPx.coerceAtLeast(0f)).coerceAtLeast(0f)
            currentTransition = currentTransition.copy(
                compensation = comp.copy(
                    remainingDisplacement = remaining,
                    isAnchorValid = remaining > 0.5f && comp.isAnchorValid
                )
            )
            return remaining
        }

        fun rebaseCompensation(safeSlackPx: Float, generation: Long? = null) {
            if (generation != null && generation != currentTransition.generation) return
            val comp = currentTransition.compensation
            val bounded = minOf(comp.remainingDisplacement, maxOf(0f, safeSlackPx))
            currentTransition = currentTransition.copy(
                compensation = comp.copy(
                    remainingDisplacement = bounded,
                    isAnchorValid = bounded > 0.5f && comp.isAnchorValid
                )
            )
        }

        fun updateAnchorColumn(newColumn: Int = 0, newCol: Int = newColumn, generation: Long? = null) {
            if (generation != null && generation != currentTransition.generation) return
            val effectiveCol = if (newColumn != 0) newColumn else newCol
            val comp = currentTransition.compensation
            if (comp.isAnchorValid) {
                currentTransition = currentTransition.copy(
                    compensation = comp.copy(anchorColumn = effectiveCol)
                )
            }
        }

        fun notifySelectionChanged(
            startLine: Int,
            startCol: Int,
            endLine: Int = startLine,
            endCol: Int = startCol,
            isSelected: Boolean = false,
            generation: Long? = null
        ) {
            if (generation != null && generation != currentTransition.generation) return
            val comp = currentTransition.compensation
            if (!comp.isAnchorValid) return
            if (isSelected) {
                if (startLine != endLine || comp.anchorLine != startLine) {
                    invalidateCompensation("Selection changed", generation)
                }
            } else {
                if (comp.anchorLine != startLine) {
                    invalidateCompensation("Caret line changed from ${comp.anchorLine} to $startLine", generation)
                }
            }
        }

        fun invalidateCompensation(reason: String? = null, generation: Long? = null) {
            if (generation != null && generation != currentTransition.generation) return
            currentTransition = currentTransition.copy(
                compensation = ExpansionCompensation(isAnchorValid = false)
            )
        }

        fun startTerminalDismissal(
            scrollPos: Int = 0,
            generation: Long? = null,
            currentScroll: Int? = null,
            initialObstructionPx: Float = 0f
        ): Long {
            if (generation != null && generation != currentTransition.generation) return currentTransition.generation
            if (isTerminalDismissalInFlight() && currentTransition.preservedScroll != null) {
                return currentTransition.generation // Idempotent: don't overwrite saved scroll
            }
            generationCounter++
            val effectiveScroll = currentScroll ?: scrollPos
            val startingObstruction = if (initialObstructionPx > 0f) initialObstructionPx else currentTransition.geometry.currentObstructionPx
            val tx = BottomSurfaceTransition(
                transitionId = System.nanoTime(),
                generation = generationCounter,
                type = TransitionType.TERMINAL_KEYBOARD_DISMISSAL,
                phase = TransitionPhase.IN_FLIGHT,
                geometry = currentTransition.geometry.copy(
                    startingObstructionPx = startingObstruction,
                    targetObstructionPx = 0f
                ),
                compensation = ExpansionCompensation(isAnchorValid = false),
                preservedScroll = effectiveScroll
            )
            currentTransition = tx
            return generationCounter
        }

        fun startDrawerDismissal(
            currentScroll: Int? = null,
            initialObstructionPx: Float = 0f,
            scrollPos: Int? = currentScroll
        ): Long {
            generationCounter++
            val effectiveScroll = scrollPos ?: currentScroll
            val startingObstruction = if (initialObstructionPx > 0f) initialObstructionPx else currentTransition.geometry.currentObstructionPx
            val tx = BottomSurfaceTransition(
                transitionId = System.nanoTime(),
                generation = generationCounter,
                type = TransitionType.DRAWER_DISMISSAL,
                phase = TransitionPhase.IN_FLIGHT,
                geometry = currentTransition.geometry.copy(
                    startingObstructionPx = startingObstruction,
                    targetObstructionPx = 0f
                ),
                compensation = ExpansionCompensation(isAnchorValid = false),
                preservedScroll = null
            )
            currentTransition = tx
            return generationCounter
        }

        fun startHandoff(startingObstructionPx: Float, targetObstructionPx: Float): Long {
            generationCounter++
            val tx = BottomSurfaceTransition(
                transitionId = System.nanoTime(),
                generation = generationCounter,
                type = TransitionType.DRAWER_TO_KEYBOARD_HANDOFF,
                phase = TransitionPhase.IN_FLIGHT,
                geometry = BottomSurfaceGeometry(
                    startingObstructionPx = startingObstructionPx,
                    targetObstructionPx = targetObstructionPx,
                    currentObstructionPx = startingObstructionPx,
                    compactBaselinePx = targetObstructionPx
                ),
                compensation = ExpansionCompensation(isAnchorValid = false)
            )
            currentTransition = tx
            return generationCounter
        }

        fun onSurfacePhysicallySettled(finalObstructionPx: Float): BottomSurfaceTransition {
            val tx = currentTransition.copy(
                phase = TransitionPhase.SETTLED,
                geometry = currentTransition.geometry.copy(currentObstructionPx = finalObstructionPx)
            )
            currentTransition = tx
            return tx
        }

        fun onSurfacePhysicallySettled(finalObstructionPx: Float, generation: Long?): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val tx = currentTransition.copy(
                phase = TransitionPhase.SETTLED,
                geometry = currentTransition.geometry.copy(currentObstructionPx = finalObstructionPx)
            )
            currentTransition = tx
            return tx
        }

        fun onSurfacePhysicallySettled(generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val tx = currentTransition.copy(phase = TransitionPhase.SETTLED)
            currentTransition = tx
            return tx
        }

        fun completeTransition(generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            val tx = currentTransition.copy(
                phase = TransitionPhase.COMPLETED,
                preservedScroll = null
            )
            currentTransition = tx
            return tx
        }

        fun cancelTransition(reason: String? = null, generation: Long? = null): BottomSurfaceTransition {
            if (generation != null && generation != currentTransition.generation) return currentTransition
            generationCounter++
            val tx = currentTransition.copy(
                phase = TransitionPhase.CANCELLED,
                preservedScroll = null
            )
            currentTransition = tx
            return tx
        }
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
    fun isShortcutBarGeometryReserved(
        state: PresentationState,
        hasShortcuts: Boolean = true,
        isImeVisibleOrEntering: Boolean = false,
        isDrawerActive: Boolean = false
    ): Boolean {
        if (!hasShortcuts) return false
        if (isImeVisibleOrEntering || isDrawerActive) return true
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
    fun calculateReservedShortcutBarClearanceDp(
        state: PresentationState,
        hasShortcuts: Boolean = true,
        isImeVisibleOrEntering: Boolean = false,
        isDrawerActive: Boolean = false
    ): Float {
        return if (isShortcutBarGeometryReserved(state, hasShortcuts, isImeVisibleOrEntering, isDrawerActive)) SHORTCUT_BAR_HEIGHT_DP else 0f
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
        // Downward follow is permitted ONLY when a prior expansion displaced the canvas upward (temporaryDisplacement > 0f)
        // and returns from an expanded state (never during terminal dismissal, and never without recorded displacement).
        return temporaryDisplacement > 0f
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
