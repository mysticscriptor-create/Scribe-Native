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
    fun calculateMaxCanvasScroll(
        headerHeight: Int,
        layoutHeight: Int,
        viewportHeight: Int,
        bottomSpace: Int = 0,
        isTextEmpty: Boolean = false
    ): Int {
        if (isTextEmpty || viewportHeight <= 0) return 0
        val totalContentHeight = headerHeight + layoutHeight + bottomSpace
        return (totalContentHeight - viewportHeight).coerceAtLeast(0)
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
        if (deltaY == 0f || headerHeight <= 0) {
            return ScrollAllocation(0f, 0f, deltaY)
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
        if (headerHeight <= 0) {
            return ReconciledScrollState(0, 0)
        }
        val safeMaxCanvas = maxCanvasScroll.coerceAtLeast(0)
        val totalScroll = (scrollD + editorOffsetY).coerceIn(0, safeMaxCanvas)

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

    // ── Transition-Aware Contraction Restoration Model ─────────────────────────

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
        // Positive slack means caret is currently sitting above targetLineBottom.
        val slack = targetLineBottom - request.caretScreenBottom
        if (slack <= deadbandPx) {
            // Caret is already at or below target line; moving canvas downward would push caret
            // behind the shortcut bar or violate the breathing margin!
            logDiagnostic("ContractionRestoration", "Zero delta: caret has no downward slack (slack=$slack)")
            return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "Caret already at safe boundary")
        }

        // Determine intended downward delta (negative deltaY moves canvas down)
        val intendedDown = when {
            request.temporaryDisplacement > 0f -> -request.temporaryDisplacement
            request.isAtLastLine -> -slack
            else -> 0f
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
    fun calculateLastLineFollowDelta(
        lastLineDocBottom: Float,
        headerHeight: Int,
        scrollD: Int,
        offsetY: Int,
        viewportHeight: Int,
        density: Float,
        isAtLastLine: Boolean
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
                temporaryDisplacement = 0f,
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
        breathingMarginPx: Int = 0,
        isTextEmpty: Boolean = false,
        activeGlideTargetY: Int = 0,
        isHeaderFullyHidden: Boolean = true
    ): Int {
        // Invariant A: Editor CANNOT have positive scroll bounds while title header is visible
        if (!isHeaderFullyHidden) return 0
        if (isTextEmpty || layoutHeight <= 0 || viewportHeight <= 0) return 0

        // Dual-State Geometry:
        // When keyboard is visible, viewport is already padded by Compose.
        // Therefore, space is strictly breathingMarginPx (24dp) so last line sits
        // comfortably above shortcut bar without double-counting keyboard height or scrolling empty space.
        // When keyboard is dismissed, persistentBottomSpacePx is used to retain stationary document offset.
        val space = if (isKeyboardVisible) {
            breathingMarginPx
        } else if (persistentBottomSpacePx > 0) {
            persistentBottomSpacePx
        } else {
            (viewportHeight * 0.45f).toInt()
        }

        val maxScrollWithBottomSpace = (layoutHeight + space - viewportHeight).coerceAtLeast(0)
        val retainedOffset = if (currentOffsetY > 0 && isFocused) currentOffsetY else 0
        val glideTarget = if (activeGlideTargetY > 0) activeGlideTargetY else 0

        return maxOf(baseSoraMaxY.coerceAtLeast(0), maxScrollWithBottomSpace, retainedOffset, glideTarget)
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

