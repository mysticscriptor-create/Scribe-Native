package com.primaloptima.scribe.ui.components

import kotlin.math.roundToInt

/**
 * Unified Scroll & Viewport Positioning Policy for Scribe.
 *
 * Centralizes all dimensions, thresholds, durations, and calculation rules
 * governing cursor visibility, predictive glides, keyboard insets, and downward
 * document anchoring to prevent cross-component conflicts and oscillations.
 */
object ScribeScrollPolicy {

    // ── Unified Dimensions & Thresholds ─────────────────────────────────────────

    /**
     * Breathing margin between cursor bottom and shortcut bar / keyboard surface.
     * Guarantees the active line rests comfortably above the input accessory bar.
     */
    const val CURSOR_BOTTOM_MARGIN_DP = 24f

    /**
     * Hysteresis deadband to prevent sub-pixel animation jitter and scroll fighting.
     */
    const val CURSOR_DEADBAND_DP = 4f

    /**
     * Minimal touch scroll distance threshold to consider as manual drag.
     */
    const val DRAG_THRESHOLD_DP = 2f

    // ── Unified Animation Durations ─────────────────────────────────────────────

    /** Predictive glide duration synchronized with standard keyboard entrance curves */
    const val DURATION_PREDICTIVE_GLIDE_MS = 280L

    /** Standard cursor repositioning smooth scroll duration */
    const val DURATION_ENSURE_CURSOR_MS = 280L

    /** Rapid downward anchoring when deleting text on the final line or shrinking layout */
    const val DURATION_DELETION_ANCHOR_MS = 140L

    /** Restoring document canvas to top (title + header in view) */
    const val DURATION_RESET_TOP_MS = 240L

    // ── Unified Resolution Model ────────────────────────────────────────────────

    data class ScrollResolution(
        val deltaY: Float,
        val shouldScroll: Boolean,
        val isAnchorDownward: Boolean = false,
        val recommendedDurationMs: Long = DURATION_ENSURE_CURSOR_MS
    )

    data class CursorSafetyDecision(
        val isSafe: Boolean,
        val screenTop: Float,
        val screenBottom: Float,
        val neededUpwardDisplacement: Float,
        val targetTotalScroll: Float,
        val isTopUnderflow: Boolean = (!isSafe && neededUpwardDisplacement < 0f),
        val isBottomOccluded: Boolean = (!isSafe && neededUpwardDisplacement > 0f)
    ) {
        val requiredCorrectionDy: Float get() = neededUpwardDisplacement
    }

    /**
     * Explicit reason/origin model for cursor-visibility requests.
     */
    enum class CursorCorrectionOrigin {
        TAP_PLACEMENT,
        PROGRAMMATIC_NAVIGATION,
        KEYBOARD_ENTRANCE,
        TEMPORARY_SURFACE_CONTRACTION,
        KEYBOARD_DISMISSAL,
        TEXT_EDIT,
        SELECTION_CHANGE
    }

    /**
     * Authoritative resolver for cursor/selection safety across all visibility paths.
     * Uses canonical coordinate conversion:
     * screenTop = headerHeight + rectTop - currentTotalScroll
     * screenBottom = headerHeight + rectBottom - currentTotalScroll
     *
     * Invariants:
     * - If cursor bottom is at or above safeBottomBoundary (usableViewportHeight - breathingMarginPx + deadbandPx),
     *   it is completely safe: returns isSafe = true, neededUpwardDisplacement = 0f, targetTotalScroll = currentTotalScroll.
     *   Tapping an already-visible last line produces ZERO movement.
     * - If cursor bottom is below safeBottomBoundary, calculates the exact minimum upward displacement.
     * - Respects maxCanvasScroll and 1-DOF invariant. Never forces downward movement on keyboard transitions.
     */
    fun resolveCursorSafety(
        rectTop: Float,
        rectBottom: Float,
        headerHeight: Float,
        currentTotalScroll: Float,
        usableViewportHeight: Float,
        density: Float,
        maxCanvasScroll: Float,
        origin: CursorCorrectionOrigin,
        isDisplacedByTransaction: Boolean = false
    ): CursorSafetyDecision {
        val allowDownward = when (origin) {
            CursorCorrectionOrigin.PROGRAMMATIC_NAVIGATION -> true
            CursorCorrectionOrigin.TEXT_EDIT -> true
            CursorCorrectionOrigin.SELECTION_CHANGE -> true
            CursorCorrectionOrigin.TAP_PLACEMENT -> isDisplacedByTransaction
            CursorCorrectionOrigin.KEYBOARD_ENTRANCE -> isDisplacedByTransaction
            CursorCorrectionOrigin.TEMPORARY_SURFACE_CONTRACTION -> false
            CursorCorrectionOrigin.KEYBOARD_DISMISSAL -> false
        }
        return resolveCursorSafety(
            rectTop = rectTop,
            rectBottom = rectBottom,
            headerHeight = headerHeight,
            currentTotalScroll = currentTotalScroll,
            usableViewportHeight = usableViewportHeight,
            density = density,
            maxCanvasScroll = maxCanvasScroll,
            allowDownwardForTopUnderflow = allowDownward
        )
    }

    /**
     * Convenience overload for screen-relative coordinates and origin.
     */
    fun resolveCursorSafety(
        caretScreenBottom: Float,
        caretScreenTop: Float,
        viewportHeight: Int,
        density: Float,
        origin: CursorCorrectionOrigin,
        currentTotalScroll: Float = 500f,
        maxCanvasScroll: Float = 2000f
    ): CursorSafetyDecision {
        return resolveCursorSafety(
            rectTop = caretScreenTop + currentTotalScroll,
            rectBottom = caretScreenBottom + currentTotalScroll,
            headerHeight = 0f,
            currentTotalScroll = currentTotalScroll,
            usableViewportHeight = viewportHeight.toFloat(),
            density = density,
            maxCanvasScroll = maxCanvasScroll,
            origin = origin
        )
    }

    fun resolveCursorSafety(
        rectTop: Float,
        rectBottom: Float,
        headerHeight: Float,
        currentTotalScroll: Float,
        usableViewportHeight: Float,
        density: Float,
        maxCanvasScroll: Float,
        allowDownwardForTopUnderflow: Boolean = false
    ): CursorSafetyDecision {
        val screenTop = headerHeight + rectTop - currentTotalScroll
        val screenBottom = headerHeight + rectBottom - currentTotalScroll

        if (usableViewportHeight <= 0f) {
            return CursorSafetyDecision(
                isSafe = true,
                screenTop = screenTop,
                screenBottom = screenBottom,
                neededUpwardDisplacement = 0f,
                targetTotalScroll = currentTotalScroll
            )
        }

        val marginPx = CURSOR_BOTTOM_MARGIN_DP * density
        val deadbandPx = CURSOR_DEADBAND_DP * density
        val safeBottomBoundary = usableViewportHeight - marginPx

        if (screenBottom > (safeBottomBoundary + deadbandPx)) {
            val overflow = screenBottom - safeBottomBoundary
            val target = (currentTotalScroll + overflow).coerceIn(0f, maxCanvasScroll)
            return CursorSafetyDecision(
                isSafe = false,
                screenTop = screenTop,
                screenBottom = screenBottom,
                neededUpwardDisplacement = overflow,
                targetTotalScroll = target
            )
        }

        if (screenTop < -deadbandPx) {
            if (allowDownwardForTopUnderflow && currentTotalScroll > 0f) {
                val underflow = screenTop
                val target = (currentTotalScroll + underflow).coerceAtLeast(0f)
                return CursorSafetyDecision(
                    isSafe = false,
                    screenTop = screenTop,
                    screenBottom = screenBottom,
                    neededUpwardDisplacement = underflow,
                    targetTotalScroll = target
                )
            }
            return CursorSafetyDecision(
                isSafe = false,
                screenTop = screenTop,
                screenBottom = screenBottom,
                neededUpwardDisplacement = 0f,
                targetTotalScroll = currentTotalScroll
            )
        }

        return CursorSafetyDecision(
            isSafe = true,
            screenTop = screenTop,
            screenBottom = screenBottom,
            neededUpwardDisplacement = 0f,
            targetTotalScroll = currentTotalScroll
        )
    }

    /**
     * Authoritative calculation for cursor visibility adjustments.
     *
     * Invariant:
     * - If cursor bottom is occluded below the shortcut bar/keyboard, scroll UP to make it visible.
     * - If cursor top is scrolled above the visible canvas top, scroll DOWN to make it visible.
     * - If cursor is already within the visible safe zone, DO NOT scroll (prevents jumping while typing).
     *
     * @param screenBottom Visual bottom coordinate of cursor row in canvas space
     * @param screenTop Visual top coordinate of cursor row in canvas space
     * @param visibleHeight Usable height of the viewport above shortcut bar / keyboard
     * @param density Screen display metrics density
     * @param isLastLine Whether cursor is currently on the final line of document (optional)
     * @param isDeletionActive Whether an active text deletion is in progress (optional)
     * @param hasScrollOffset Whether either canvas header (scrollD) or editor (offsetY) has positive scroll
     */
    fun resolveCursorScrollDelta(
        screenBottom: Float,
        screenTop: Float,
        visibleHeight: Int,
        density: Float,
        isLastLine: Boolean = false,
        isDeletionActive: Boolean = false,
        hasScrollOffset: Boolean = false
    ): ScrollResolution {
        if (visibleHeight <= 0) {
            return ScrollResolution(0f, false)
        }

        val marginPx = (CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (CURSOR_DEADBAND_DP * density).roundToInt()
        val targetLineBottom = visibleHeight - marginPx

        // Case 1: Cursor is occluded below target line (overflow into shortcut bar / keyboard)
        if (screenBottom > (targetLineBottom + deadbandPx)) {
            val overflow = screenBottom - targetLineBottom
            return ScrollResolution(
                deltaY = overflow,
                shouldScroll = true,
                isAnchorDownward = false,
                recommendedDurationMs = DURATION_ENSURE_CURSOR_MS
            )
        }

        // Case 2: Cursor scrolled above top edge of visible canvas
        if (screenTop < -deadbandPx && hasScrollOffset) {
            val underflow = screenTop
            return ScrollResolution(
                deltaY = underflow,
                shouldScroll = true,
                isAnchorDownward = false,
                recommendedDurationMs = DURATION_ENSURE_CURSOR_MS
            )
        }

        // Case 3: Cursor is within visible safe zone — rock solid, no scroll needed
        return ScrollResolution(0f, false)
    }

    /**
     * Calculates the maximum vertical scroll allowed for the entire unified canvas
     * (the combination of header displacement `scrollD` and editor `offsetY`).
     *
     * @param headerHeight Measured height of the document header/title view
     * @param layoutHeight Measured height of the editor text layout
     * @param viewportHeight Measured usable viewport height of the canvas
     * @param bottomSpace Additional bottom clearance (breathing margin or persistent bottom space)
     * @param isTextEmpty Whether the editor text is completely empty (no text in body)
     * @return Total allowable vertical scroll distance across header and editor
     */
    /**
     * Calculates the shared virtual trailing extent in pixels from the stable standard keyboard baseline
     * or active physical bottom surface geometry.
     */
    fun calculateVirtualTrailingExtentPx(
        isPhysicalBottomSurfaceActive: Boolean,
        standardKeyboardHeightPx: Float,
        shortcutBarHeightPx: Float,
        breathingMarginPx: Float,
        retainedClearancePx: Float = 0f
    ): Float {
        return ScribeBottomSurfaceCoordinator.calculateVirtualBottomSpacePx(
            isPhysicalBottomSurfaceActive = isPhysicalBottomSurfaceActive,
            standardKeyboardHeightPx = standardKeyboardHeightPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            retainedClearancePx = retainedClearancePx
        )
    }

    /**
     * Calculates the complete logical content height across all layers:
     * logical content = header contribution + editor content + virtual trailing extent
     */
    fun calculateLogicalContentHeight(
        headerHeight: Int,
        editorContentHeight: Int,
        virtualTrailingExtentPx: Int
    ): Int {
        return (headerHeight.coerceAtLeast(0) +
                editorContentHeight.coerceAtLeast(0) +
                virtualTrailingExtentPx.coerceAtLeast(0))
    }

    /**
     * Calculates the logical maximum scroll range:
     * logical maximum scroll = max(0, logical content - effective viewport)
     */
    fun calculateLogicalMaxScroll(
        logicalContentHeight: Int,
        viewportHeight: Int,
        isTextEmpty: Boolean = false
    ): Int {
        if (isTextEmpty || viewportHeight <= 0) return 0
        return (logicalContentHeight - viewportHeight).coerceAtLeast(0)
    }

    /**
     * Derives the editor-local maximum scroll limit from the shared logical maximum scroll
     * by accounting for the header contribution and coordinate conversion:
     * editor-local max scroll = max(0, logical maximum scroll - headerHeight)
     */
    fun calculateEditorMaxScroll(
        logicalMaxScroll: Int,
        headerHeight: Int
    ): Int {
        return (logicalMaxScroll - headerHeight.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun calculateMaxCanvasScroll(
        headerHeight: Int,
        layoutHeight: Int,
        viewportHeight: Int,
        bottomSpace: Int = 0,
        isTextEmpty: Boolean = false
    ): Int {
        val logicalContent = calculateLogicalContentHeight(
            headerHeight = headerHeight,
            editorContentHeight = layoutHeight,
            virtualTrailingExtentPx = bottomSpace
        )
        return calculateLogicalMaxScroll(
            logicalContentHeight = logicalContent,
            viewportHeight = viewportHeight,
            isTextEmpty = isTextEmpty
        )
    }

    // ── 1-DOF Scroll Ownership & Coordination Model ────────────────────────────

    data class ScrollAllocation(
        val outerDelta: Float,
        val innerDelta: Float,
        val unconsumedDelta: Float
    )

    data class ReconciledScrollState(
        val scrollD: Int,
        val editorOffsetY: Int
    )

    /**
     * Authoritative 1 Degree of Freedom (1-DOF) scroll allocation policy.
     *
     * Invariants:
     * 1. While any portion of the title header is visible (scrollD < headerHeight),
     *    vertical scroll movement belongs EXCLUSIVELY to the outer unified canvas.
     *    The editor's internal scroll offset MUST remain at its neutral position (0).
     *    Leftover delta is NEVER passed to the editor merely because maxCanvasScroll was reached.
     * 2. Once the header is completely scrolled out of view (scrollD == headerHeight),
     *    leftover delta and subsequent upward movement may scroll the editor internally.
     * 3. Reverse movement (scrolling back towards title):
     *    Movement is consumed against the editor's internal offset until it reaches 0.
     *    Only after the editor offset reaches 0 may movement be consumed against the outer canvas
     *    to reveal the header.
     * 4. A single delta crossing the handoff boundary is split precisely at the boundary with zero
     *    gap, jump, or double consumption.
     *
     * @param deltaY Vertical delta to allocate (> 0 moves deeper into document, < 0 moves towards title)
     * @param headerHeight Measured height of the document header/title view
     * @param scrollD Current header displacement of outer canvas
     * @param editorOffsetY Current internal scroll offset of editor
     * @param maxCanvasScroll Total allowable vertical scroll distance across canvas and editor
     */
    fun allocateScrollDelta(
        deltaY: Float,
        headerHeight: Int,
        scrollD: Float,
        editorOffsetY: Int,
        maxCanvasScroll: Int
    ): ScrollAllocation {
        if (deltaY == 0f) {
            return ScrollAllocation(0f, 0f, 0f)
        }

        val safeHeaderHeight = headerHeight.coerceAtLeast(0)
        if (safeHeaderHeight <= 0) {
            val maxTotal = maxCanvasScroll.toFloat().coerceAtLeast(0f)
            val currentTotal = editorOffsetY.toFloat()
            if (deltaY > 0f) {
                val available = (maxTotal - currentTotal).coerceAtLeast(0f)
                val innerDelta = minOf(deltaY, available)
                val unconsumed = deltaY - innerDelta
                return ScrollAllocation(0f, innerDelta, unconsumed)
            } else {
                val allowedDelta = maxOf(deltaY, -currentTotal)
                val unconsumed = deltaY - allowedDelta
                return ScrollAllocation(0f, allowedDelta, unconsumed)
            }
        }

        if (deltaY > 0f) {
            // Forward scroll: content moves up, into the document
            val currentTotal = scrollD + editorOffsetY
            val maxTotal = maxCanvasScroll.toFloat().coerceAtLeast(0f)
            val availableTotal = (maxTotal - currentTotal).coerceAtLeast(0f)
            val allowedDelta = minOf(deltaY, availableTotal)

            if (allowedDelta <= 0f) {
                return ScrollAllocation(0f, 0f, deltaY)
            }

            // Phase 1: Outer canvas (scrollD) moves towards headerHeight
            val maxScrollD = minOf(headerHeight.toFloat(), maxTotal)
            val remainingToHeader = (maxScrollD - scrollD).coerceAtLeast(0f)
            val outerDelta = minOf(allowedDelta, remainingToHeader)

            // Phase 2: Inner editor (offsetY) moves ONLY if header is completely hidden
            val remainingAfterOuter = allowedDelta - outerDelta
            val newScrollD = scrollD + outerDelta

            val innerDelta = if (newScrollD >= headerHeight.toFloat() - 0.001f) {
                remainingAfterOuter
            } else {
                0f // STRICT INVARIANT: Editor CANNOT scroll while header is visible!
            }

            val unconsumed = deltaY - (outerDelta + innerDelta)
            return ScrollAllocation(outerDelta, innerDelta, unconsumed)

        } else {
            // Reverse scroll (deltaY < 0): content moves down, back towards header / top
            val currentTotal = scrollD + editorOffsetY
            if (currentTotal <= 0f) {
                return ScrollAllocation(0f, 0f, deltaY)
            }

            val allowedDelta = maxOf(deltaY, -currentTotal)

            // Phase 1: Inner editor (offsetY) returns to 0 first
            val canConsumeInner = -editorOffsetY.toFloat() // negative or 0
            val innerDelta = maxOf(allowedDelta, canConsumeInner)

            // Phase 2: Outer canvas (scrollD) moves towards 0 only after editor reaches 0
            val remainingAfterInner = allowedDelta - innerDelta // negative or 0
            val outerDelta = if (editorOffsetY + innerDelta <= 0.001f) {
                val canConsumeOuter = -scrollD // negative or 0
                maxOf(remainingAfterInner, canConsumeOuter)
            } else {
                0f // Editor must reach 0 before canvas moves
            }

            val unconsumed = deltaY - (innerDelta + outerDelta)
            return ScrollAllocation(outerDelta, innerDelta, unconsumed)
        }
    }

    /**
     * Reconciles outer and inner scroll offsets against physical bounds and the 1-DOF invariant.
     * Guarantees that if scrollD < headerHeight, editorOffsetY is strictly 0.
     */
    fun reconcileScrollState(
        headerHeight: Int,
        scrollD: Int,
        editorOffsetY: Int,
        maxCanvasScroll: Int
    ): ReconciledScrollState {
        val safeMaxCanvas = maxCanvasScroll.coerceAtLeast(0)
        val totalScroll = (scrollD + editorOffsetY).coerceIn(0, safeMaxCanvas)
        if (headerHeight <= 0) {
            return ReconciledScrollState(
                scrollD = 0,
                editorOffsetY = totalScroll
            )
        }

        return if (totalScroll < headerHeight) {
            // Within header range: all displacement belongs to outer canvas; editor MUST be 0
            ReconciledScrollState(
                scrollD = totalScroll,
                editorOffsetY = 0
            )
        } else {
            // Beyond header range: header is completely offscreen (headerHeight),
            // remainder belongs to editor
            ReconciledScrollState(
                scrollD = headerHeight,
                editorOffsetY = totalScroll - headerHeight
            )
        }
    }

    /**
     * Calculates the maximum displacement allowed for the header view (scrollD).
     * The header can only scroll as much as the total content overflows the viewport,
     * up to its own height (headerHeight).
     */
    fun calculateMaxHeaderScroll(
        headerHeight: Int,
        maxCanvasScroll: Int
    ): Int {
        if (headerHeight <= 0 || maxCanvasScroll <= 0) return 0
        return minOf(headerHeight, maxCanvasScroll)
    }

    /**
     * Calculates the downward canvas adjustment needed after text deletion so that
     * the bottom of the document text never leaves an artificial empty void above the
     * shortcut bar / keyboard surface while the canvas has positive scroll offset.
     *
     * Detects how far the bottom of the document text has drifted from the target line
     * (shortcut bar + breathing margin) and smoothly pulls the canvas down.
     *
     * @param headerHeight Measured height of the document header view
     * @param scrollD Current header displacement
     * @param layoutHeight Measured height of the editor text layout
     * @param offsetY Current editor scroll offset
     * @param viewportHeight Usable height of the canvas above shortcut bar
     * @param density Screen display metrics density
     * @param maxCanvasScroll Maximum allowable physical canvas scroll
     * @return Negative deltaY to smoothly pull the canvas down, or 0f if no adjustment needed
     */
    fun calculateDeletionAnchorDelta(
        headerHeight: Int,
        scrollD: Int,
        layoutHeight: Int,
        offsetY: Int,
        viewportHeight: Int,
        density: Float,
        maxCanvasScroll: Int
    ): Float {
        val totalScroll = scrollD + offsetY
        if (totalScroll <= 0 || viewportHeight <= 0) return 0f

        // 1. Full document fit: If remaining document content fits completely within viewport
        val totalContentHeight = headerHeight + layoutHeight
        if (totalContentHeight <= viewportHeight) {
            return -totalScroll.toFloat()
        }

        // 2. Physical bounds clamp: If total scroll exceeds maxCanvasScroll,
        // smoothly clamp excess scroll so content naturally settles at maxCanvasScroll.
        if (totalScroll > maxCanvasScroll) {
            val excess = (totalScroll - maxCanvasScroll).toFloat()
            if (excess >= 1f) {
                return -excess
            }
        }
        return 0f
    }

    // ── Zoom & Multi-Touch Gesture Arbitration Policy ────────────────────────
    data class ZoomTransaction(
        val epoch: Long,
        val isGestureActive: Boolean,
        val isAwaitingLayoutSettlement: Boolean
    ) {
        fun shouldSuppressCursorVisibility(callbackEpoch: Long): Boolean {
            return callbackEpoch != epoch || isGestureActive || isAwaitingLayoutSettlement
        }

        fun isMultiTouchActive(): Boolean {
            // Strictly represents active physical touch, freeing single-touch as soon as fingers lift
            return isGestureActive
        }
    }

    // ── Tap Target Lifecycle Model ───────────────────────────────────────────
    sealed interface TapTargetState {
        object None : TapTargetState
        object Unresolved : TapTargetState
        data class Resolved(val line: Int, val column: Int) : TapTargetState
    }

    // ── Keyboard Viewport Transaction Model ──────────────────────────────────
    data class KeyboardTransaction(
        val transactionId: Long,
        val isActive: Boolean,
        val targetScroll: Float = -1f,
        val targetLine: Int? = null,
        val targetCol: Int? = null,
        val preTransactionScroll: Float = 0f,
        val initialScreenY: Float? = null,
        val isPhysicallySettled: Boolean = false,
        val isUnresolvedTap: Boolean = false,
        val isImeInsetSettled: Boolean = false,
        val isLayoutMeasurementSettled: Boolean = false
    ) {
        val isFullySettled: Boolean get() = isImeInsetSettled && isLayoutMeasurementSettled

        fun calculateRetargetedDestination(
            caretDocBottom: Float,
            headerHeight: Float,
            usableViewportHeight: Float,
            breathingMarginPx: Float,
            maxCanvasScroll: Float
        ): Float {
            val targetLineBottom = (usableViewportHeight - breathingMarginPx).coerceAtLeast(0f)
            val desired = (headerHeight + caretDocBottom - targetLineBottom).coerceAtLeast(0f)
            return desired.coerceIn(0f, maxCanvasScroll)
        }

        fun isCaretSafe(
            currentScroll: Float,
            caretDocBottom: Float,
            headerHeight: Float,
            usableViewportHeight: Float,
            breathingMarginPx: Float,
            deadbandPx: Float,
            caretDocTop: Float = caretDocBottom - 40f
        ): Boolean {
            val targetLineBottom = (usableViewportHeight - breathingMarginPx).coerceAtLeast(0f)
            val caretScreenBottom = headerHeight + caretDocBottom - currentScroll
            val caretScreenTop = headerHeight + caretDocTop - currentScroll
            return caretScreenTop >= -deadbandPx && caretScreenBottom <= (targetLineBottom + deadbandPx)
        }

        fun shouldRetarget(
            currentScroll: Float,
            desiredTarget: Float,
            isSafe: Boolean,
            deadbandPx: Float
        ): Boolean {
            if (isSafe && kotlin.math.abs(desiredTarget - currentScroll) <= deadbandPx) {
                return false
            }
            return kotlin.math.abs(desiredTarget - currentScroll) > 1f
        }
    }

    // ── Settled Reconciliation Model ─────────────────────────────────────────
    sealed interface SettledReconciliationResult {
        object NoOp : SettledReconciliationResult
        object CancelOvershoot : SettledReconciliationResult
        data class Retarget(val targetScrollY: Int) : SettledReconciliationResult
    }

    /**
     * Reconciles settled viewport with planned target:
     * - If caret is already safe at currentScrollY, cancels any overshooting in-flight glide.
     * - If caret is unsafe at currentScrollY, computes the minimal retargeted destination.
     * - Otherwise returns NoOp.
     */
    fun evaluateSettledReconciliation(
        currentScrollY: Float,
        plannedTargetScrollY: Float,
        caretDocTop: Float,
        caretDocBottom: Float,
        headerHeight: Float,
        settledViewportHeight: Float,
        density: Float,
        maxCanvasScroll: Float
    ): SettledReconciliationResult {
        val marginPx = (CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (CURSOR_DEADBAND_DP * density).roundToInt()
        val targetLineBottom = (settledViewportHeight - marginPx).coerceAtLeast(0f)

        val screenBottom = headerHeight + caretDocBottom - currentScrollY
        val screenTop = headerHeight + caretDocTop - currentScrollY
        val isSafeAtCurrent = screenTop >= -deadbandPx && screenBottom <= (targetLineBottom + deadbandPx)

        if (isSafeAtCurrent) {
            return if (plannedTargetScrollY >= 0f && kotlin.math.abs(plannedTargetScrollY - currentScrollY) > 1f) {
                SettledReconciliationResult.CancelOvershoot
            } else {
                SettledReconciliationResult.NoOp
            }
        }

        val neededDisplacement = if (screenBottom > targetLineBottom + deadbandPx) {
            screenBottom - targetLineBottom
        } else if (screenTop < -deadbandPx) {
            screenTop
        } else {
            0f
        }

        if (kotlin.math.abs(neededDisplacement) > deadbandPx) {
            val retargeted = (currentScrollY + neededDisplacement).coerceIn(0f, maxCanvasScroll)
            return SettledReconciliationResult.Retarget(retargeted.roundToInt())
        }

        return SettledReconciliationResult.NoOp
    }

    // ── Pure Keyboard Transaction Coordinator ────────────────────────────────
    class KeyboardTransactionCoordinator {
        var currentTransaction: KeyboardTransaction? = null
            private set
        var currentEpoch: Long = 0L
            private set

        val isActive: Boolean get() = currentTransaction?.isActive == true
        val isUnresolved: Boolean get() = currentTransaction?.isUnresolvedTap == true
        val isFullySettled: Boolean get() = currentTransaction?.isFullySettled == true

        fun startTransaction(
            transactionId: Long = System.currentTimeMillis(),
            preTransactionScroll: Float,
            targetScroll: Float = -1f,
            targetLine: Int? = null,
            targetCol: Int? = null,
            initialScreenY: Float? = null,
            isUnresolvedTap: Boolean = false
        ): KeyboardTransaction {
            currentEpoch++
            val transaction = KeyboardTransaction(
                transactionId = transactionId,
                isActive = true,
                targetScroll = targetScroll,
                targetLine = targetLine,
                targetCol = targetCol,
                preTransactionScroll = preTransactionScroll,
                initialScreenY = initialScreenY,
                isPhysicallySettled = false,
                isUnresolvedTap = isUnresolvedTap,
                isImeInsetSettled = false,
                isLayoutMeasurementSettled = false
            )
            currentTransaction = transaction
            return transaction
        }

        fun onImeInsetSettled(): KeyboardTransaction? {
            val tx = currentTransaction ?: return null
            val updated = tx.copy(
                isImeInsetSettled = true,
                isPhysicallySettled = tx.isLayoutMeasurementSettled
            )
            currentTransaction = updated
            return updated
        }

        fun onLayoutMeasurementSettled(): KeyboardTransaction? {
            val tx = currentTransaction ?: return null
            val updated = tx.copy(
                isLayoutMeasurementSettled = true,
                isPhysicallySettled = tx.isImeInsetSettled
            )
            currentTransaction = updated
            return updated
        }

        fun reconcileTarget(line: Int, column: Int, targetScroll: Float = -1f): KeyboardTransaction? {
            val tx = currentTransaction ?: return null
            val updated = tx.copy(
                targetLine = line,
                targetCol = column,
                targetScroll = if (targetScroll >= 0f) targetScroll else tx.targetScroll,
                isUnresolvedTap = false
            )
            currentTransaction = updated
            return updated
        }

        fun cancel(reason: String = ""): Long {
            currentEpoch++
            currentTransaction = null
            return currentEpoch
        }

        fun finish(): KeyboardTransaction? {
            val tx = currentTransaction
            currentTransaction = null
            return tx
        }
    }

    // ── Deletion Transaction & Intent Reconciliation Model ───────────────────
    data class DeletionSnapshot(
        val transactionEpoch: Long,
        val isSelection: Boolean,
        val selectionStartLine: Int,
        val selectionEndLine: Int,
        val preEditDocLineCount: Int,
        val preEditDocHeight: Float,
        val preEditScrollY: Float,
        val preEditCaretScreenY: Float,
        val wasAtTail: Boolean,
        val affectsDocumentTail: Boolean
    )

    enum class DeletionAnchorIntent {
        TAIL_ANCHOR,   // User was editing at document end -> follow tail preserving breathing margin
        CARET_ANCHOR,  // User was editing in the middle -> keep collapsed caret screen Y stationary
        RESET_TO_TOP   // Document empty or fits entirely in view -> smoothly restore title and top
    }

    /**
     * Issue 4: Accurately classifies deletion intent using verified pre-edit snapshot.
     * Tail follow requires BOTH that the viewport was near the tail AND that the deletion affected the end.
     */
    fun classifyDeletionIntent(
        snapshot: DeletionSnapshot,
        newDocLineCount: Int,
        newDocHeight: Float,
        usableViewportHeight: Float
    ): DeletionAnchorIntent {
        if (newDocLineCount <= 0 || newDocHeight <= usableViewportHeight) {
            return DeletionAnchorIntent.RESET_TO_TOP
        }
        if (snapshot.wasAtTail && snapshot.affectsDocumentTail) {
            return DeletionAnchorIntent.TAIL_ANCHOR
        }
        return DeletionAnchorIntent.CARET_ANCHOR
    }

    /**
     * Issue 3 & 4: Calculates safe deletion destination satisfying both physical bounds and breathing margin.
     * Invariants:
     * 1. Monotonic Non-Growth: Deletion must never scroll downward beyond current scroll.
     * 2. Hard Breathing Margin: Last line bottom + breathing margin must not exceed usable viewport.
     * 3. Caret Screen Invariant: Middle deletions preserve exact collapsed caret screen Y.
     */
    fun calculateSafeDeletionDestination(
        intent: DeletionAnchorIntent,
        currentScroll: Float,
        headerHeight: Float,
        newDocHeight: Float,
        newCaretDocY: Float,
        preEditCaretScreenY: Float,
        usableViewportHeight: Float,
        breathingMarginPx: Float,
        maxCanvasScroll: Float
    ): Float {
        return when (intent) {
            DeletionAnchorIntent.RESET_TO_TOP -> 0f
            DeletionAnchorIntent.TAIL_ANCHOR -> {
                // Hard 24dp breathing margin constraint above the shortcut bar / keyboard:
                // lastLineBottom + breathingMarginPx <= usableBottom
                val minimumSafeScroll = (headerHeight + newDocHeight + breathingMarginPx - usableViewportHeight).coerceAtLeast(0f)
                minimumSafeScroll.coerceIn(0f, maxCanvasScroll)
            }
            DeletionAnchorIntent.CARET_ANCHOR -> {
                val desiredTarget = (headerHeight + newCaretDocY - preEditCaretScreenY).coerceIn(0f, maxCanvasScroll)
                val bounded = desiredTarget.coerceAtMost(currentScroll)
                val targetLineBottom = (usableViewportHeight - breathingMarginPx).coerceAtLeast(0f)
                val minSafeScroll = (headerHeight + newCaretDocY - targetLineBottom).coerceAtLeast(0f)
                bounded.coerceAtLeast(minSafeScroll).coerceIn(0f, maxCanvasScroll)
            }
        }
    }

    // ── Transition-Aware Contraction Restoration Model ─────────────────────────

    /**
     * Authoritative request model to determine whether downward contraction restoration is authorized.
     */
    /**
     * Authoritative request model to determine whether downward contraction restoration is authorized.
     */
    data class ContractionAuthorizationRequest(
        val transitionType: ScribeBottomSurfaceCoordinator.TransitionType = ScribeBottomSurfaceCoordinator.TransitionType.NONE,
        val transitionPhase: ScribeBottomSurfaceCoordinator.TransitionPhase = ScribeBottomSurfaceCoordinator.TransitionPhase.STARTING,
        val startingObstructionPx: Float = 0f,
        val targetObstructionPx: Float = 0f,
        val currentObstructionPx: Float = 0f,
        val compactBaselinePx: Float = 0f,
        val recordedExpansionDisplacement: Float = 0f,
        val remainingCompensation: Float = 0f,
        val isAnchorValid: Boolean = false,
        val hasWindowFocus: Boolean = true,
        val isScribeOwner: Boolean = true,
        val isTerminalDismissal: Boolean = false,
        val transitionGeneration: Long = 0L,
        val linkedExpansionGeneration: Long = 0L,
        val temporaryExpansionDisplacement: Float = remainingCompensation,
        val targetBottomObstructionPx: Float = targetObstructionPx,
        val currentSlackAboveBaselinePx: Float = 0f,
        val alreadyRestoredAgainstBudgetPx: Float = 0f,
        val physicalContractionReleasedPx: Float = 0f,
        val availableScrollRangePx: Float = Float.MAX_VALUE,
        val generation: Long = transitionGeneration,
        val expectedGeneration: Long = 0L
    )

    /**
     * Tri-state contraction authorization status.
     * Separates true authorization, pending physical movement, and permanent rejection.
     */
    enum class ContractionAuthorizationStatus {
        AUTHORIZED,
        PENDING,
        REJECTED
    }

    /**
     * Authoritative decision result for downward contraction restoration eligibility.
     */
    data class ContractionAuthorizationResult(
        val status: ContractionAuthorizationStatus,
        val targetBaselinePx: Float = 0f,
        val maximumPermittedRestorationPx: Float = 0f,
        val cumulativePhysicalReleasedPx: Float = 0f,
        val rejectionReason: String? = null
    ) {
        val isAuthorized: Boolean get() = status == ContractionAuthorizationStatus.AUTHORIZED
        val isPending: Boolean get() = status == ContractionAuthorizationStatus.PENDING
        val isRejected: Boolean get() = status == ContractionAuthorizationStatus.REJECTED

        constructor(
            isAuthorized: Boolean,
            targetBaselinePx: Float = 0f,
            maximumPermittedRestorationPx: Float = 0f,
            rejectionReason: String? = null
        ) : this(
            status = if (isAuthorized) ContractionAuthorizationStatus.AUTHORIZED else ContractionAuthorizationStatus.REJECTED,
            targetBaselinePx = targetBaselinePx,
            maximumPermittedRestorationPx = maximumPermittedRestorationPx,
            cumulativePhysicalReleasedPx = 0f,
            rejectionReason = rejectionReason
        )
    }

    /**
     * Authoritative policy function determining whether a downward contraction restoration is permitted.
     * Enforces the 8 strict conditions:
     * 1. Window focus and Scribe soft-input session ownership are active.
     * 2. Not a terminal dismissal (terminal dismissal is strictly stationary).
     * 3. Real upward expansion displacement was recorded (> 0) and remaining compensation > 0.
     * 4. Caret / selection anchor remains logically valid.
     * 5. Surface is returning toward a nonzero compact or standard baseline.
     * 6. Transition is a genuine contraction (KEYBOARD_CONTRACTION or DRAWER_CONTRACTION) rather than unrelated window resize or foreign IME change.
     * 7. Transition lifecycle phase is active (STARTING, IN_FLIGHT, or SETTLED).
     * 8. Generation safety: stale callbacks from older generations are rejected.
     */
    fun authorizeContractionRestoration(request: ContractionAuthorizationRequest): ContractionAuthorizationResult {
        // 1. Explicitly allow ONLY genuine contraction types (Part 5.1)
        if (request.transitionType != ScribeBottomSurfaceCoordinator.TransitionType.KEYBOARD_CONTRACTION &&
            request.transitionType != ScribeBottomSurfaceCoordinator.TransitionType.DRAWER_CONTRACTION) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Transition type ${request.transitionType} is not an authorized contraction type"
            )
        }

        // 2. Validate lifecycle phase (Part 5.2): Must be active (STARTING, IN_FLIGHT, or SETTLED with remaining compensation)
        if (request.transitionPhase != ScribeBottomSurfaceCoordinator.TransitionPhase.STARTING &&
            request.transitionPhase != ScribeBottomSurfaceCoordinator.TransitionPhase.IN_FLIGHT &&
            request.transitionPhase != ScribeBottomSurfaceCoordinator.TransitionPhase.SETTLED) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Transition phase ${request.transitionPhase} is not eligible for contraction restoration"
            )
        }

        // 3. Window focus and Scribe input ownership
        if (!request.hasWindowFocus || !request.isScribeOwner) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Window focus or input ownership not active"
            )
        }

        // 4. Terminal dismissal invariant: terminal dismissal is strictly stationary
        if (request.isTerminalDismissal) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Terminal keyboard dismissal: stationary invariant"
            )
        }

        // 5. Target baseline: must be a known nonzero compact or standard baseline (not terminal zero)
        val targetBaseline = when {
            request.compactBaselinePx > ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX -> request.compactBaselinePx
            request.targetObstructionPx > ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX -> request.targetObstructionPx
            request.targetBottomObstructionPx > ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX -> request.targetBottomObstructionPx
            else -> 0f
        }
        if (targetBaseline <= ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Target baseline is zero or unestablished (terminal dismissal)"
            )
        }

        // 6. Starting obstruction must be genuinely greater than target compact baseline
        val startingObstruction = request.startingObstructionPx
        if (startingObstruction > 0f && startingObstruction <= targetBaseline + ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Starting obstruction is not greater than target compact baseline"
            )
        }

        // 6b. Physical progress toward target baseline (Part 5.3):
        val curObstruction = request.currentObstructionPx
        val tolerance = ScribeBottomSurfaceCoordinator.IME_TARGET_TOLERANCE_PX

        if (request.transitionPhase == ScribeBottomSurfaceCoordinator.TransitionPhase.IN_FLIGHT) {
            // Zero or unknown obstruction cannot be interpreted as contraction progress during active in-flight transition
            if (curObstruction <= 0f) {
                return ContractionAuthorizationResult(
                    status = ContractionAuthorizationStatus.REJECTED,
                    rejectionReason = "Physical obstruction is zero or unmeasured during active transition"
                )
            }
            if (startingObstruction > 0f && curObstruction > startingObstruction + tolerance) {
                return ContractionAuthorizationResult(
                    status = ContractionAuthorizationStatus.REJECTED,
                    rejectionReason = "Physical obstruction is increasing, not contracting"
                )
            }
            // In flight contraction MUST have genuinely progressed downward from starting obstruction
            if (startingObstruction > 0f && (startingObstruction - curObstruction) < tolerance) {
                return ContractionAuthorizationResult(
                    status = ContractionAuthorizationStatus.PENDING,
                    rejectionReason = "Physical obstruction has not begun contracting"
                )
            }
        } else if (request.transitionPhase == ScribeBottomSurfaceCoordinator.TransitionPhase.STARTING) {
            if (startingObstruction > 0f && curObstruction > startingObstruction + tolerance) {
                return ContractionAuthorizationResult(
                    status = ContractionAuthorizationStatus.REJECTED,
                    rejectionReason = "Physical obstruction is increasing, not contracting"
                )
            }
            if (startingObstruction > 0f && (startingObstruction - curObstruction) < tolerance) {
                return ContractionAuthorizationResult(
                    status = ContractionAuthorizationStatus.PENDING,
                    rejectionReason = "Physical contraction is pending physical movement"
                )
            }
        }

        // 7. Generation safety: if expected generation is specified, must match
        if (request.expectedGeneration != 0L && request.generation != 0L && request.generation != request.expectedGeneration) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Transition generation mismatch (stale callback)"
            )
        }

        // 7b. Linked expansion generation check (must trace back to expansion or have active compensation)
        val compensation = maxOf(request.remainingCompensation, request.temporaryExpansionDisplacement)
        if (compensation <= 0.5f) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "No remaining expansion compensation to restore"
            )
        }
        if (request.transitionGeneration != 0L && request.linkedExpansionGeneration == 0L && request.recordedExpansionDisplacement <= 0.5f) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Contraction transition is not linked to an expansion generation"
            )
        }

        // 8. Anchor validity
        if (!request.isAnchorValid) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.REJECTED,
                rejectionReason = "Caret anchor or selection invalidated by structural edit or movement"
            )
        }

        // Calculate cumulative physical contraction released distance
        val releasedPhysical = when {
            request.transitionPhase == ScribeBottomSurfaceCoordinator.TransitionPhase.SETTLED -> {
                if (startingObstruction > targetBaseline) (startingObstruction - targetBaseline) else compensation
            }
            request.physicalContractionReleasedPx > 0f -> request.physicalContractionReleasedPx
            startingObstruction > curObstruction -> (startingObstruction - curObstruction)
            else -> 0f
        }

        // Calculate remaining physical contraction budget
        val remainingPhysicalBudget = if (request.transitionPhase == ScribeBottomSurfaceCoordinator.TransitionPhase.SETTLED) {
            (releasedPhysical - request.alreadyRestoredAgainstBudgetPx).coerceAtLeast(0f)
        } else {
            (releasedPhysical - request.alreadyRestoredAgainstBudgetPx).coerceAtLeast(0f)
        }

        if (remainingPhysicalBudget <= 0.05f && request.transitionPhase != ScribeBottomSurfaceCoordinator.TransitionPhase.SETTLED) {
            return ContractionAuthorizationResult(
                status = ContractionAuthorizationStatus.PENDING,
                targetBaselinePx = targetBaseline,
                cumulativePhysicalReleasedPx = releasedPhysical,
                rejectionReason = "Awaiting further physical contraction release"
            )
        }

        val slackLimit = if (request.currentSlackAboveBaselinePx > 0f) {
            request.currentSlackAboveBaselinePx
        } else {
            Float.MAX_VALUE
        }

        val maxPermitted = minOf(
            compensation,
            remainingPhysicalBudget,
            slackLimit,
            request.availableScrollRangePx
        )

        return ContractionAuthorizationResult(
            status = ContractionAuthorizationStatus.AUTHORIZED,
            targetBaselinePx = targetBaseline,
            maximumPermittedRestorationPx = maxPermitted,
            cumulativePhysicalReleasedPx = releasedPhysical
        )
    }

    /**
     * Request model for calculating automatic downward restoration when an expanded
     * keyboard (e.g. emoji picker) or accessory drawer contracts back to normal height.
     */
    data class ContractionRestorationRequest(
        val caretScreenBottom: Float,
        val caretScreenTop: Float,
        val viewportHeight: Int,
        val density: Float,
        val totalScroll: Float,
        val temporaryDisplacement: Float = 0f,
        val isAtLastLine: Boolean = false,
        val isKeyboardDismissing: Boolean = false,
        val isBottomSurfaceActive: Boolean = true
    )

    /**
     * Authoritative result model for downward contraction restoration.
     */
    data class ContractionRestorationResult(
        val deltaY: Float,
        val shouldScroll: Boolean,
        val remainingCompensation: Float,
        val reason: String
    )

    /**
     * Diagnostic logging toggle and callback for scroll & viewport coordination.
     */
    var isDebugDiagnosticsEnabled: Boolean = false
    var onScrollDiagnosticLog: ((String) -> Unit)? = null

    fun logDiagnostic(tag: String, message: String) {
        if (isDebugDiagnosticsEnabled) {
            onScrollDiagnosticLog?.invoke("[$tag] $message")
        }
    }

    /**
     * Authoritative calculation for downward canvas adjustment when an expanded keyboard
     * or accessory drawer contracts back to normal height.
     *
     * Invariants:
     * 1. Pure Writer Invariant: When the keyboard is dismissing to 0dp or no bottom surface
     *    remains active, downward scroll is STRICTLY prohibited (returns 0f). Canvas stays stationary.
     * 2. Safe Bottom Boundary: The active caret/selection row must NEVER settle behind the
     *    shortcut bar or keyboard surface. It settles comfortably with CURSOR_BOTTOM_MARGIN_DP (24dp).
     * 3. Transition-Aware Compensation: If temporaryDisplacement > 0f was recorded during expansion,
     *    it is safely restored down across ALL caret positions (first line, middle line, second-to-last, etc.).
     * 4. 1-DOF & Bounds Respect: Clamped against totalScroll (cannot scroll past top of document)
     *    and available slack above the breathing target line.
     * 5. Last Line Anchor: If isAtLastLine and no temporaryDisplacement is set, the document end
     *    smoothly anchors to the breathing margin.
     */
    fun resolveContractionRestorationDelta(
        request: ContractionRestorationRequest
    ): ContractionRestorationResult {
        if (request.viewportHeight <= 0) {
            return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "Invalid viewport height")
        }
        if (request.isKeyboardDismissing) {
            logDiagnostic("ContractionRestoration", "Rejected: keyboard is dismissing (Pure Writer stationary invariant)")
            return ContractionRestorationResult(0f, false, 0f, "Keyboard dismissing")
        }
        if (!request.isBottomSurfaceActive) {
            logDiagnostic("ContractionRestoration", "Rejected: no bottom surface active")
            return ContractionRestorationResult(0f, false, 0f, "Bottom surface inactive")
        }
        if (request.totalScroll <= 0f) {
            return ContractionRestorationResult(0f, false, 0f, "Document already at top")
        }

        val marginPx = (CURSOR_BOTTOM_MARGIN_DP * request.density).roundToInt()
        val deadbandPx = (CURSOR_DEADBAND_DP * request.density).roundToInt()
        val targetLineBottom = (request.viewportHeight - marginPx).toFloat()

        // Slack between target line bottom and caret bottom.
        // Slack between target line bottom and caret bottom.
        // Positive slack means caret is currently sitting above targetLineBottom.
        // Negative slack means caret is currently sitting below targetLineBottom (UNSAFE!).
        val slack = targetLineBottom - request.caretScreenBottom

        // Safety Invariant: If caret is currently below safe boundary (slack < 0f),
        // we must not do nothing! Calculate corrective upward delta.
        if (slack < 0f) {
            val correctiveUp = -slack // positive upward delta
            logDiagnostic("ContractionRestoration", "Corrective upward applied: slack=$slack, correctiveUp=$correctiveUp")
            return ContractionRestorationResult(
                deltaY = correctiveUp,
                shouldScroll = true,
                remainingCompensation = request.temporaryDisplacement,
                reason = "Corrective upward to safe boundary"
            )
        }

        // Caret is sitting right at the safe boundary (within deadband above targetLineBottom)
        if (slack <= deadbandPx) {
            logDiagnostic("ContractionRestoration", "Zero delta: caret already at safe boundary (slack=$slack)")
            return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "Caret already at safe boundary")
        }

        // Determine intended downward delta (negative deltaY moves canvas down)
        // STRICT INVARIANT: Downward restoration ONLY restores recorded temporary displacement
        // from a confirmed expanded bottom surface (e.g. emoji picker or accessory drawer).
        // Standard keyboards, unexpanded surfaces, and terminal dismissal must NEVER scroll
        // the document downward, preserving the Pure Writer stationary baseline.
        val intendedDown = if (request.temporaryDisplacement > 0f && !request.isKeyboardDismissing && request.isBottomSurfaceActive) {
            -request.temporaryDisplacement
        } else {
            0f
        }

        if (intendedDown >= 0f) {
            return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "No intended downward movement")
        }

        // Safe clamping:
        // 1. Must never move caret lower than targetLineBottom: maximum safe downward is -slack
        val safeDown = maxOf(intendedDown, -slack)

        // 2. Must never scroll past the document top: maximum downward is -totalScroll
        val clampedDown = maxOf(safeDown, -request.totalScroll)

        if (clampedDown < -deadbandPx) {
            val restoredAmount = -clampedDown
            val remaining = (request.temporaryDisplacement - restoredAmount).coerceAtLeast(0f)
            logDiagnostic(
                "ContractionRestoration",
                "Applied: deltaY=$clampedDown, intended=$intendedDown, slack=$slack, remainingCompensation=$remaining"
            )
            return ContractionRestorationResult(
                deltaY = clampedDown,
                shouldScroll = true,
                remainingCompensation = remaining,
                reason = "Restoration applied"
            )
        }

        return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "Delta within deadband")
    }

    /**
     * Calculates the downward canvas adjustment when the viewport expands (e.g. accessory drawer collapses,
     * emoji keyboard closes, or bottom obstruction shrinks).
     *
     * If the cursor is positioned on the last line of the document, the last line smoothly follows
     * the shortcut bar downward maintaining the breathing gap (24dp).
     * If the user is editing in the middle or upper parts of the document, no downward adjustment is made (returns 0f).
     *
     * @param lastLineDocBottom The measured bottom coordinate of the last line in document coordinates
     * @param headerHeight Measured height of the document header view
     * @param scrollD Current header displacement
     * @param offsetY Current editor scroll offset
     * @param viewportHeight The new, expanded usable viewport height
     * @param density Screen display metrics density
     * @param isAtLastLine Whether the cursor is currently on the final line of the document
     * @return Negative deltaY to smoothly pull the canvas down, or 0f if no adjustment needed
     */

    // ── Selection & Caret Coordinate Models ───────────────────────────────────

    /**
     * Unified geometry description for cursor or selection bounds in document coordinates.
     * Accurately distinguishes vertical top/bottom from horizontal left/right,
     * and accounts for multi-line selection endpoints.
     */
    data class SelectionGeometry(
        val topDocY: Float,
        val bottomDocY: Float,
        val leftDocX: Float,
        val rightDocX: Float,
        val isSelected: Boolean = true,
        val anchorLine: Int = 0,
        val focusLine: Int = 0
    )

    data class SafeTargetValidationResult(
        val isSafe: Boolean,
        val finalCaretScreenBottom: Float,
        val targetLineBottom: Float,
        val correctiveDeltaY: Float
    )

    /**
     * Validates whether a proposed total scroll position places the caret safely at or above
     * the breathing margin line. If the final position would violate the boundary (slack < 0f),
     * calculates the necessary corrective upward delta so the caret settles with at least 24dp of clearance.
     */
    fun validateFinalTarget(
        targetTotalScroll: Float,
        caretDocBottom: Float,
        headerHeight: Int,
        viewportHeight: Int,
        density: Float
    ): SafeTargetValidationResult {
        val marginPx = (CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val targetLineBottom = (viewportHeight - marginPx).toFloat()
        val finalCaretBottom = (headerHeight + caretDocBottom - targetTotalScroll)
        val slack = targetLineBottom - finalCaretBottom

        return if (slack < 0f) {
            SafeTargetValidationResult(
                isSafe = false,
                finalCaretScreenBottom = finalCaretBottom,
                targetLineBottom = targetLineBottom,
                correctiveDeltaY = -slack
            )
        } else {
            SafeTargetValidationResult(
                isSafe = true,
                finalCaretScreenBottom = finalCaretBottom,
                targetLineBottom = targetLineBottom,
                correctiveDeltaY = 0f
            )
        }
    }

    enum class ScrollAnimationReason {
        CARET_VISIBILITY,
        CONTRACTION_RESTORATION,
        PREDICTIVE_GLIDE,
        DELETION_ANCHOR,
        RESET_TOP,
        SAFE_BOUNDARY_CORRECTION,
        PROGRAMMATIC
    }

    /**
     * Interaction provenance identifying what action triggered a cursor/selection change.
     */
    enum class InteractionProvenance {
        TEXT_INPUT,
        EMOJI_INSERTION,
        PROGRAMMATIC,
        MANUAL_TAP,
        HANDLE_DRAG,
        DOCUMENT_DRAG
    }

    /**
     * Result of an authoritative animation step for simulation and validation.
     */
    data class StepResult(
        val consumedDisplacement: Float = 0f,
        val remainingDisplacement: Float = 0f
    )

    /**
     * Authoritative animation coordinator for all programmatic and automatic canvas positioning.
     * Manages a single active animation target, smooth retargeting without velocity/progress resets,
     * causal compensation bookkeeping, and safe-boundary enforcement.
     */
    class AuthoritativeScrollCoordinator(
        var totalScroll: Float = 0f,
        var temporaryDisplacement: Float = 0f,
        var caretLine: Int = -1,
        var caretCol: Int = 0,
        var isSelected: Boolean = false,
        var caretDocBottom: Float = 0f,
        var headerHeight: Int = 0,
        var density: Float = 1f
    ) {
        var currentScroll: Float
            get() = totalScroll
            set(value) { totalScroll = value }

        var targetScroll: Float = 0f
        var velocity: Float = 0f
        var selectionStartLine: Int = -1
        var selectionStartCol: Int = 0
        var selectionEndLine: Int = -1
        var selectionEndCol: Int = 0
        var isAnchorValid: Boolean = false
        var originalAnchorLine: Int = -1
        var isAnimationActive: Boolean = false
            private set
        var activeReason: ScrollAnimationReason? = null
            private set
        var activeTargetTotalScroll: Float? = null
            private set
        var lastAnimatedScroll: Float = totalScroll
            private set
        var isExpansionTransitionActive: Boolean = false

        /**
         * Authoritative generation and cumulative ledger balances.
         */
        var originatingExpansionGeneration: Long = 0L
        var recordedDisplacement: Float = 0f
        var totalConsumedDisplacement: Float = 0f

        /**
         * Monotonically increasing operation ID. Prevents obsolete animation callbacks
         * from clearing or finishing newer operations.
         */
        var activeOperationId: Long = 0L
            private set

        // Callbacks for production integration
        var onScrollBy: ((deltaY: Float) -> Float)? = null
        var onCompensationConsumed: ((Float) -> Unit)? = null
        var onAnimationFinished: (() -> Unit)? = null

        fun updateAnchorColumn(newColumn: Int = 0, newCol: Int = newColumn) {
            val col = if (newColumn != 0) newColumn else newCol
            if (isAnchorValid) {
                caretCol = col
                selectionStartCol = col
                selectionEndCol = col
            }
        }

        fun setCompensationAnchor(
            line: Int,
            col: Int = 0,
            endLine: Int = line,
            endCol: Int = col,
            selected: Boolean = false
        ) {
            val effLine = line.coerceAtLeast(0)
            val effCol = col.coerceAtLeast(0)
            caretLine = effLine
            caretCol = effCol
            selectionStartLine = effLine
            selectionStartCol = effCol
            selectionEndLine = if (endLine != -1) endLine else effLine
            selectionEndCol = endCol
            isSelected = selected
            isAnchorValid = true
            originalAnchorLine = effLine
        }

        fun recordExpansionDisplacement(
            amount: Float,
            startLine: Int = -1,
            startCol: Int = 0,
            endLine: Int = startLine,
            endCol: Int = startCol,
            selected: Boolean = false,
            anchorLine: Int = startLine,
            anchorCol: Int = startCol,
            generation: Long = 0L
        ) {
            if (amount <= 0f) return
            val effStartLine = if (anchorLine != -1) anchorLine else startLine
            val effStartCol = if (anchorLine != -1 && anchorCol != 0) anchorCol else startCol

            if (generation != 0L) {
                if (originatingExpansionGeneration == 0L || originatingExpansionGeneration == generation) {
                    originatingExpansionGeneration = generation
                    recordedDisplacement += amount
                    temporaryDisplacement += amount
                } else {
                    originatingExpansionGeneration = generation
                    recordedDisplacement = amount
                    temporaryDisplacement = amount
                    totalConsumedDisplacement = 0f
                }
            } else {
                recordedDisplacement += amount
                temporaryDisplacement += amount
            }

            caretLine = effStartLine
            caretCol = effStartCol
            selectionStartLine = effStartLine
            selectionStartCol = effStartCol
            selectionEndLine = if (endLine != -1) endLine else effStartLine
            selectionEndCol = endCol
            isSelected = selected
            isAnchorValid = true
            originalAnchorLine = effStartLine
        }

        /**
         * Initiates or retargets an automatic positioning operation.
         * Returns true if a new animation or retargeting should occur, or false if already on target.
         */
        fun requestTarget(
            targetScroll: Float,
            reason: ScrollAnimationReason,
            forceRetarget: Boolean = false,
            maxCanvasScroll: Int? = null
        ): Boolean {
            val maxBound = maxCanvasScroll?.toFloat()?.coerceAtLeast(0f)
            val clampedTarget = if (maxBound != null) targetScroll.coerceIn(0f, maxBound) else targetScroll.coerceAtLeast(0f)
            val currentScroll = totalScroll
            val distance = clampedTarget - currentScroll

            if (kotlin.math.abs(distance) < 1f && !isAnimationActive) {
                activeTargetTotalScroll = clampedTarget
                return false
            }

            if (isAnimationActive && activeReason == reason) {
                val currTarget = activeTargetTotalScroll
                if (!forceRetarget && currTarget != null && kotlin.math.abs(currTarget - clampedTarget) <= 2f) {
                    return false
                }
            }

            activeOperationId++
            activeTargetTotalScroll = clampedTarget
            lastAnimatedScroll = currentScroll
            activeReason = reason
            isAnimationActive = true
            return true
        }

        /**
         * Retargets an ongoing animation in-flight without resetting velocity,
         * advancing operation IDs, or disrupting step tracking.
         */
        fun retargetActiveOperation(
            newTarget: Float,
            reason: ScrollAnimationReason,
            maxCanvasScroll: Int? = null
        ) {
            val maxBound = maxCanvasScroll?.toFloat()?.coerceAtLeast(0f)
            val clampedTarget = if (maxBound != null) newTarget.coerceIn(0f, maxBound) else newTarget.coerceAtLeast(0f)
            activeTargetTotalScroll = clampedTarget
            activeReason = reason
        }

        /**
         * Applies a tick of animation with the new absolute animated scroll value.
         * Returns actual displacement moved.
         *
         * Strict Invariant: Authoritative remaining compensation is deducted ONLY
         * through actual applied downward movement (-actualMoved). Projected amounts
         * never decrement this counter.
         */
        fun applyStep(newAnimatedScroll: Float): Float = applyStep(newAnimatedScroll, null)

        fun applyStep(newAnimatedScroll: Float, operationId: Long?): Float {
            if (operationId != null && operationId != activeOperationId) return 0f
            if (!isAnimationActive) return 0f

            val stepDelta = newAnimatedScroll - lastAnimatedScroll
            lastAnimatedScroll = newAnimatedScroll
            if (kotlin.math.abs(stepDelta) < 0.05f) return 0f

            val before = totalScroll
            val unconsumed = onScrollBy?.invoke(stepDelta) ?: 0f
            val consumed = stepDelta - unconsumed
            totalScroll = before + consumed
            val actualMoved = consumed

            // Causal compensation accounting: only movement actually applied is deducted
            if (activeReason == ScrollAnimationReason.CONTRACTION_RESTORATION && actualMoved < 0f) {
                val appliedDown = -actualMoved
                temporaryDisplacement = (temporaryDisplacement - appliedDown).coerceAtLeast(0f)
                totalConsumedDisplacement += appliedDown
                onCompensationConsumed?.invoke(appliedDown)
                if (temporaryDisplacement <= 0.5f) {
                    temporaryDisplacement = 0f
                    recordedDisplacement = 0f
                    totalConsumedDisplacement = 0f
                    originatingExpansionGeneration = 0L
                    isAnchorValid = false
                    caretLine = -1
                    caretCol = 0
                    selectionStartLine = -1
                    selectionStartCol = 0
                    selectionEndLine = -1
                    selectionEndCol = 0
                    isSelected = false
                    originalAnchorLine = -1
                }
            } else if (isExpansionTransitionActive && actualMoved > 0f &&
                (activeReason == ScrollAnimationReason.CARET_VISIBILITY || activeReason == ScrollAnimationReason.PREDICTIVE_GLIDE)) {
                recordedDisplacement += actualMoved
                temporaryDisplacement += actualMoved
                isAnchorValid = true
            }

            // Boundary collision / unreachable target detection:
            val hitBoundary = (stepDelta > 0.05f && unconsumed > 0.05f && kotlin.math.abs(consumed) < 0.05f) ||
                    (stepDelta < -0.05f && unconsumed < -0.05f && kotlin.math.abs(consumed) < 0.05f)

            val target = activeTargetTotalScroll
            if (hitBoundary || (target != null && kotlin.math.abs(totalScroll - target) < 0.5f)) {
                finishAnimation(activeOperationId)
            }

            return actualMoved
        }

        fun applyStep(dt: Float, isPhysicsStep: Boolean = true): StepResult {
            val step = (velocity * dt).coerceIn(-150f, 150f)
            totalScroll += step
            val actualMoved = step
            val appliedDown = if (actualMoved < 0f) -actualMoved else 0f
            temporaryDisplacement = (temporaryDisplacement - appliedDown).coerceAtLeast(0f)
            totalConsumedDisplacement += appliedDown
            if (temporaryDisplacement <= 0.5f) {
                temporaryDisplacement = 0f
                recordedDisplacement = 0f
                totalConsumedDisplacement = 0f
                originatingExpansionGeneration = 0L
                isAnchorValid = false
            }
            return StepResult(consumedDisplacement = appliedDown, remainingDisplacement = temporaryDisplacement)
        }

        fun finishAnimation(operationId: Long? = null) {
            if (operationId != null && operationId != activeOperationId) return
            isAnimationActive = false
            activeReason = null
            activeTargetTotalScroll = null
            lastAnimatedScroll = totalScroll
            onAnimationFinished?.invoke()
        }

        fun cancelAnimation(reason: String? = null, operationId: Long? = null) {
            if (operationId != null && operationId != activeOperationId) return
            activeOperationId++
            isAnimationActive = false
            activeReason = null
            activeTargetTotalScroll = null
            lastAnimatedScroll = totalScroll
            if (reason != null && isDebugDiagnosticsEnabled) {
                logDiagnostic("ScrollCoordinator", "Cancelled op $operationId: $reason")
            }
        }

        fun invalidateCompensation(reason: String? = null) {
            cancelAnimation("Compensation invalidated: $reason", activeOperationId)
            temporaryDisplacement = 0f
            recordedDisplacement = 0f
            totalConsumedDisplacement = 0f
            originatingExpansionGeneration = 0L
            caretLine = -1
            caretCol = 0
            selectionStartLine = -1
            selectionStartCol = 0
            selectionEndLine = -1
            selectionEndCol = 0
            isSelected = false
            isAnchorValid = false
            originalAnchorLine = -1
        }

        fun invalidateIfCaretChanged(newLine: Int, newCol: Int, newSelected: Boolean) {
            // Invariant: Typing text or inserting emojis advances the cursor column on the same line (newLine == caretLine).
            // This MUST NOT invalidate temporary displacement. Only moving to a different line (caretLine != newLine)
            // or making an active text selection change invalidates compensation.
            if (caretLine != -1 && (caretLine != newLine || isSelected != newSelected)) {
                invalidateCompensation("Caret line or selection changed (line $caretLine -> $newLine, sel $isSelected -> $newSelected)")
            } else if (caretLine == newLine) {
                caretCol = newCol
                selectionStartCol = newCol
                selectionEndCol = newCol
            }
        }

        fun invalidateIfSelectionEndpointsChanged(
            startLine: Int,
            startCol: Int,
            endLine: Int = startLine,
            endCol: Int = startCol,
            selected: Boolean = false,
            isSelected: Boolean = selected
        ) {
            if (!isAnchorValid || temporaryDisplacement <= 0f) return
            val sel = selected || isSelected
            if (!sel && !this.isSelected) {
                if (caretLine != -1 && caretLine != startLine) {
                    invalidateCompensation("Caret moved from line $caretLine to line $startLine")
                } else if (caretLine == startLine) {
                    caretCol = startCol
                }
            } else if (sel != this.isSelected ||
                selectionStartLine != startLine || selectionStartCol != startCol ||
                selectionEndLine != endLine || selectionEndCol != endCol) {
                invalidateCompensation("Selection endpoints changed from ($selectionStartLine:$selectionStartCol..$selectionEndLine:$selectionEndCol) to ($startLine:$startCol..$endLine:$endCol)")
            }
        }

        fun notifySelectionChangedWithProvenance(
            startLine: Int,
            startCol: Int,
            endLine: Int = startLine,
            endCol: Int = startCol,
            selected: Boolean = false,
            provenance: InteractionProvenance = InteractionProvenance.TEXT_INPUT
        ) {
            if (!isAnchorValid || temporaryDisplacement <= 0f) return
            when (provenance) {
                InteractionProvenance.MANUAL_TAP -> {
                    if (caretLine != startLine || caretCol != startCol || selected != isSelected) {
                        invalidateCompensation("Manual tap placed cursor at ($startLine, $startCol)")
                    }
                }
                InteractionProvenance.HANDLE_DRAG -> {
                    invalidateCompensation("Selection handle drag changed selection")
                }
                InteractionProvenance.DOCUMENT_DRAG -> {
                    invalidateCompensation("Document drag invalidated anchor")
                }
                InteractionProvenance.TEXT_INPUT, InteractionProvenance.EMOJI_INSERTION -> {
                    if (caretLine == startLine && !selected) {
                        caretCol = startCol
                        selectionStartCol = startCol
                        selectionEndCol = startCol
                    } else {
                        invalidateCompensation("Text input moved across line boundary from $caretLine to $startLine")
                    }
                }
                InteractionProvenance.PROGRAMMATIC -> {
                    invalidateIfSelectionEndpointsChanged(startLine, startCol, endLine, endCol, selected)
                }
            }
        }

        fun rebaseRemainingCompensation(remainingSlack: Float) {
            val safeSlack = maxOf(0f, remainingSlack)
            if (temporaryDisplacement > safeSlack) {
                temporaryDisplacement = safeSlack
            }
            if (temporaryDisplacement <= 0.5f) {
                invalidateCompensation("Rebased below threshold")
            }
        }
    }

    /**
     * Testable animation driver abstraction for production ValueAnimator and JUnit test drivers.
     */
    interface AnimationDriver {
        fun start(
            from: Float,
            to: Float,
            durationMs: Long,
            onUpdate: (Float) -> Unit,
            onCancel: () -> Unit,
            onEnd: () -> Unit
        )
        fun cancel()
        val isRunning: Boolean
    }

    /**
     * Production-grade animation controller coordinating AuthoritativeScrollCoordinator
     * with an AnimationDriver. Enforces monotonic operation ID tracking and race-free
     * cancellation / retargeting without stale callback interference.
     */
    class ProductionScrollAnimationCoordinator(
        val scrollCoordinator: AuthoritativeScrollCoordinator,
        var animationDriverFactory: (() -> AnimationDriver)? = null
    ) {
        val activeOperationId: Long
            get() = scrollCoordinator.activeOperationId

        var activeDriver: AnimationDriver? = null
            private set

        fun startAuthoritativeAnimation(
            targetTotalScroll: Float,
            reason: ScrollAnimationReason,
            durationMs: Long = 280L,
            forceRetarget: Boolean = false,
            maxCanvasScroll: Int? = null,
            onEnd: (() -> Unit)? = null
        ): Boolean {
            val currentScroll = scrollCoordinator.totalScroll
            val maxBound = maxCanvasScroll?.toFloat()?.coerceAtLeast(0f)
            val clampedTarget = if (maxBound != null) targetTotalScroll.coerceIn(0f, maxBound) else targetTotalScroll.coerceAtLeast(0f)

            // Step 1: Request target in coordinator (advances scrollCoordinator.activeOperationId)
            val shouldStart = scrollCoordinator.requestTarget(clampedTarget, reason, forceRetarget, maxCanvasScroll)
            if (!shouldStart) {
                onEnd?.invoke()
                return false
            }

            val newOpId = scrollCoordinator.activeOperationId

            // Step 2: Safely cancel previous driver (its cancel/end will see oldOpId != activeOperationId)
            val oldDriver = activeDriver
            activeDriver = null
            oldDriver?.cancel()

            val target = scrollCoordinator.activeTargetTotalScroll ?: clampedTarget
            val distance = kotlin.math.abs(target - currentScroll)
            if (distance < 1f) {
                scrollCoordinator.finishAnimation(newOpId)
                onEnd?.invoke()
                return false
            }

            val scaledDuration = (distance * 1.2f).toLong().coerceIn(120L, durationMs)
            val driver = animationDriverFactory?.invoke()
            if (driver == null) {
                scrollCoordinator.applyStep(target, newOpId)
                scrollCoordinator.finishAnimation(newOpId)
                onEnd?.invoke()
                return true
            }

            activeDriver = driver
            driver.start(
                from = currentScroll,
                to = target,
                durationMs = scaledDuration,
                onUpdate = { curr ->
                    if (newOpId == scrollCoordinator.activeOperationId) {
                        scrollCoordinator.applyStep(curr, newOpId)
                    }
                },
                onCancel = {
                    if (newOpId == scrollCoordinator.activeOperationId) {
                        activeDriver = null
                        scrollCoordinator.cancelAnimation("Driver cancelled", newOpId)
                    }
                },
                onEnd = {
                    if (newOpId == scrollCoordinator.activeOperationId) {
                        activeDriver = null
                        scrollCoordinator.finishAnimation(newOpId)
                        onEnd?.invoke()
                    }
                }
            )
            return true
        }

        fun cancelActiveAnimation(reason: String? = null) {
            val oldDriver = activeDriver
            activeDriver = null
            oldDriver?.cancel()
            scrollCoordinator.cancelAnimation(reason)
        }

        fun retargetAuthoritativeAnimation(
            newTarget: Float,
            reason: ScrollAnimationReason,
            maxCanvasScroll: Int? = null
        ): Boolean {
            if (!scrollCoordinator.isAnimationActive) return false
            scrollCoordinator.retargetActiveOperation(newTarget, reason, maxCanvasScroll)
            return true
        }
    }

    /**
     * Deterministic simulation controller for testing contraction restoration, retargeting,
     * and tick-by-tick compensation reconciliation.
     */
    class ContractionSimulationController(
        var totalScroll: Float,
        var temporaryDisplacement: Float,
        var caretLine: Int,
        var caretCol: Int = 0,
        var isSelected: Boolean = false,
        var caretDocBottom: Float,
        var headerHeight: Int = 200,
        var density: Float = 2.5f
    ) {
        val coordinator = AuthoritativeScrollCoordinator(
            totalScroll = totalScroll,
            temporaryDisplacement = temporaryDisplacement,
            caretLine = caretLine,
            caretCol = caretCol,
            isSelected = isSelected,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            density = density
        )

        val isRestorationActive: Boolean
            get() = coordinator.isAnimationActive
        val targetTotalScroll: Float
            get() = coordinator.activeTargetTotalScroll ?: totalScroll
        var lastAppliedStep: Float = 0f
            private set

        init {
            coordinator.onScrollBy = { delta ->
                totalScroll += delta
                0f
            }
        }

        fun onViewportHeightChanged(
            newViewportHeight: Int,
            isAtLastLine: Boolean,
            isKeyboardDismissing: Boolean,
            isBottomSurfaceActive: Boolean
        ): ContractionRestorationResult {
            coordinator.totalScroll = totalScroll
            coordinator.temporaryDisplacement = temporaryDisplacement
            coordinator.caretLine = caretLine
            coordinator.caretCol = caretCol
            coordinator.isSelected = isSelected
            coordinator.caretDocBottom = caretDocBottom
            coordinator.headerHeight = headerHeight
            coordinator.density = density

            if (isKeyboardDismissing || !isBottomSurfaceActive) {
                coordinator.invalidateCompensation("Dismissed")
                temporaryDisplacement = 0f
                caretLine = -1
                return ContractionRestorationResult(0f, false, 0f, "Dismissed")
            }

            val caretScreenBottom = (headerHeight + caretDocBottom - totalScroll)
            val req = ContractionRestorationRequest(
                caretScreenBottom = caretScreenBottom,
                caretScreenTop = caretScreenBottom - 40f,
                viewportHeight = newViewportHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = temporaryDisplacement,
                isAtLastLine = isAtLastLine,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
            val result = resolveContractionRestorationDelta(req)
            if (result.shouldScroll) {
                val candidateTarget = (totalScroll + result.deltaY).coerceAtLeast(0f)
                val validation = validateFinalTarget(candidateTarget, caretDocBottom, headerHeight, newViewportHeight, density)
                val safeTarget = if (!validation.isSafe) candidateTarget + validation.correctiveDeltaY else candidateTarget
                coordinator.requestTarget(safeTarget, ScrollAnimationReason.CONTRACTION_RESTORATION)
            }
            return result
        }

        fun applyAnimationStep(fraction: Float) {
            if (!isRestorationActive) return
            val stepTarget = totalScroll + (targetTotalScroll - totalScroll) * fraction
            val moved = coordinator.applyStep(newAnimatedScroll = stepTarget)
            lastAppliedStep = moved
            totalScroll = coordinator.totalScroll
            temporaryDisplacement = coordinator.temporaryDisplacement
            caretLine = coordinator.caretLine
        }

        fun cancelRestoration() {
            coordinator.cancelAnimation()
        }

        fun onUserTouch() {
            coordinator.invalidateCompensation("User touch")
            temporaryDisplacement = 0f
            caretLine = -1
        }

        fun onCaretMoved(newLine: Int, newCol: Int, newSelected: Boolean) {
            coordinator.invalidateIfCaretChanged(newLine, newCol, newSelected)
            temporaryDisplacement = coordinator.temporaryDisplacement
            caretLine = coordinator.caretLine
        }

        fun onContentEdited() {
            coordinator.invalidateCompensation("Content edited")
            temporaryDisplacement = 0f
            caretLine = -1
        }
    }

    fun calculateLastLineFollowDelta(
        lastLineDocBottom: Float,
        headerHeight: Int,
        scrollD: Int,
        offsetY: Int,
        viewportHeight: Int,
        density: Float,
        isAtLastLine: Boolean,
        temporaryDisplacement: Float = 0f
    ): Float {
        if (!isAtLastLine || viewportHeight <= 0) return 0f
        val totalScroll = (scrollD + offsetY).toFloat()
        if (totalScroll <= 0f) return 0f

        val screenBottom = (headerHeight - scrollD) + lastLineDocBottom - offsetY
        val result = resolveContractionRestorationDelta(
            ContractionRestorationRequest(
                caretScreenBottom = screenBottom,
                caretScreenTop = screenBottom - (20f * density),
                viewportHeight = viewportHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = temporaryDisplacement,
                isAtLastLine = isAtLastLine,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )
        return result.deltaY
    }

    /**
     * Calculates the safe maximum vertical scroll for the editor layout, accounting for
     * persistent bottom space (keyboard + shortcut bar).
     *
     * When keyboard is visible (isKeyboardVisible = true), provides generous bottom clearance
     * (at least 45% of viewport height) so that typing or tapping at the end of the document
     * never feels choked against the shortcut bar and never snaps downward on keyboard appearance.
     * When keyboard is dismissed (isKeyboardVisible = false), persistentBottomSpacePx is used
     * to keep the text stationary behind the dismissed keyboard surface.
     */
    fun calculateSafeScrollMaxY(
        layoutHeight: Int,
        viewportHeight: Int,
        persistentBottomSpacePx: Int,
        currentOffsetY: Int = 0,
        isFocused: Boolean = false,
        baseSoraMaxY: Int = 0,
        isKeyboardVisible: Boolean = false,
        isBottomSurfaceActive: Boolean = false,
        breathingMarginPx: Int = 0,
        isTextEmpty: Boolean = false,
        activeGlideTargetY: Int = 0,
        isHeaderFullyHidden: Boolean = true,
        headerHeight: Int = 0
    ): Int {
        // Invariant A: Editor CANNOT have positive scroll bounds while title header is visible
        if (!isHeaderFullyHidden) return 0
        if (isTextEmpty || layoutHeight <= 0 || viewportHeight <= 0) return 0

        // Shared physical-obstruction extent policy:
        // When a physical bottom surface (keyboard or accessory drawer) is active,
        // it is already reducing the measured viewport. Virtual trailing extent is strictly breathingMarginPx.
        // When no physical bottom surface is active, persistentBottomSpacePx is used.
        val space = if (isKeyboardVisible || isBottomSurfaceActive) {
            breathingMarginPx
        } else if (persistentBottomSpacePx > 0) {
            persistentBottomSpacePx
        } else {
            breathingMarginPx
        }

        val logicalContent = calculateLogicalContentHeight(
            headerHeight = headerHeight,
            editorContentHeight = layoutHeight,
            virtualTrailingExtentPx = space
        )
        val logicalMax = calculateLogicalMaxScroll(
            logicalContentHeight = logicalContent,
            viewportHeight = viewportHeight,
            isTextEmpty = isTextEmpty
        )
        val editorMax = calculateEditorMaxScroll(
            logicalMaxScroll = logicalMax,
            headerHeight = headerHeight
        )

        val retainedOffset = if (currentOffsetY > 0 && isFocused) currentOffsetY else 0
        val glideTarget = if (activeGlideTargetY > 0) activeGlideTargetY else 0

        return maxOf(editorMax, maxOf(retainedOffset, glideTarget))
    }

    /**
     * Resolves the true uninterrupted scroll offset when a fling is interrupted by a user touch.
     * Prevents Android's OverScroller.abortAnimation() from jumping currY to finalY.
     *
     * @param visualOffset Current visible render offset (e.g. editor.offsetY)
     * @param scrollerCurr Scroller currY value
     * @param scrollerFinal Scroller finalY destination
     * @param isFinished Whether the scroller is marked finished
     * @return The accurate visual offset from which subsequent gestures must continue.
     */
    fun resolveInterruptedScrollOffset(
        visualOffset: Int,
        scrollerCurr: Int,
        scrollerFinal: Int,
        isFinished: Boolean
    ): Int {
        // Visual offset is always authoritative ground truth for subsequent gestures
        return visualOffset
    }

    // ── Select All Scroll Behavior ─────────────────────────────────────────────

    enum class SelectAllScrollMode {
        KEEP_CURRENT_POSITION,
        SCROLL_TO_SELECTION_END;

        companion object {
            fun fromString(value: String?): SelectAllScrollMode {
                return when (value) {
                    SCROLL_TO_SELECTION_END.name -> SCROLL_TO_SELECTION_END
                    else -> KEEP_CURRENT_POSITION
                }
            }
        }
    }

    /**
     * Checks whether an active selection spans all text in the document.
     */
    fun isSelectAll(
        isSelected: Boolean,
        leftLine: Int,
        leftCol: Int,
        rightLine: Int,
        rightCol: Int,
        lineCount: Int,
        lastLineColCount: Int
    ): Boolean {
        if (!isSelected || lineCount <= 0) return false
        return leftLine == 0 && leftCol == 0 &&
                (rightLine > lineCount - 1 || (rightLine == lineCount - 1 && rightCol >= lastLineColCount))
    }

    data class CursorTargetResolution(
        val line: Int,
        val column: Int,
        val shouldScroll: Boolean
    )

    /**
     * Resolves the coordinate target for cursor visibility positioning.
     *
     * Invariants:
     * 1. If Select All is active:
     *    - KEEP_CURRENT_POSITION mode returns null (stationary: completely suppresses scrolling).
     *    - SCROLL_TO_SELECTION_END mode targets the true active bottom handle (rightLine, rightCol).
     * 2. If explicit coordinates are passed for non-select-all operations (e.g. outline jumps, search),
     *    they are strictly preserved.
     * 3. For normal cursor typing (!isSelected), targets leftLine and leftCol.
     * 4. For non-select-all selection, targets rightLine and rightCol (active selection endpoint).
     */
    fun resolveTargetCoordinates(
        isSelected: Boolean,
        leftLine: Int,
        leftCol: Int,
        rightLine: Int,
        rightCol: Int,
        lineCount: Int,
        lastLineColCount: Int,
        mode: SelectAllScrollMode,
        explicitLine: Int? = null,
        explicitCol: Int? = null
    ): CursorTargetResolution? {
        val isAllSelected = isSelectAll(
            isSelected = isSelected,
            leftLine = leftLine,
            leftCol = leftCol,
            rightLine = rightLine,
            rightCol = rightCol,
            lineCount = lineCount,
            lastLineColCount = lastLineColCount
        )

        if (isAllSelected) {
            return when (mode) {
                SelectAllScrollMode.KEEP_CURRENT_POSITION -> null
                SelectAllScrollMode.SCROLL_TO_SELECTION_END -> CursorTargetResolution(
                    line = rightLine,
                    column = rightCol,
                    shouldScroll = true
                )
            }
        }

        if (explicitLine != null && explicitCol != null) {
            return CursorTargetResolution(
                line = explicitLine,
                column = explicitCol,
                shouldScroll = true
            )
        }

        val targetLine = if (isSelected) rightLine else leftLine
        val targetCol = if (isSelected) rightCol else leftCol
        return CursorTargetResolution(
            line = targetLine,
            column = targetCol,
            shouldScroll = true
        )
    }
}

