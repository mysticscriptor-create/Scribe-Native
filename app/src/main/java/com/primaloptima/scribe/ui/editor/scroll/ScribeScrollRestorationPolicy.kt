package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.roundToInt

/**
 * Pure policy rules and calculations for downward contraction restoration and safe boundary target validation.
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
object ScribeScrollRestorationPolicy {

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
     * Authoritative calculation for downward canvas adjustment when an expanded keyboard
     * or accessory drawer contracts back to normal height.
     */
    fun resolveContractionRestorationDelta(
        request: ContractionRestorationRequest
    ): ContractionRestorationResult {
        if (request.viewportHeight <= 0) {
            return ContractionRestorationResult(0f, false, request.temporaryDisplacement, "Invalid viewport height")
        }
        if (request.isKeyboardDismissing) {
            ScribeScrollDiagnostics.log("ContractionRestoration", "Rejected: keyboard is dismissing (Pure Writer stationary invariant)")
            return ContractionRestorationResult(0f, false, 0f, "Keyboard dismissing")
        }
        if (!request.isBottomSurfaceActive) {
            ScribeScrollDiagnostics.log("ContractionRestoration", "Rejected: no bottom surface active")
            return ContractionRestorationResult(0f, false, 0f, "Bottom surface inactive")
        }
        if (request.totalScroll <= 0f) {
            return ContractionRestorationResult(0f, false, 0f, "Document already at top")
        }

        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * request.density).roundToInt()
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * request.density).roundToInt()
        val targetLineBottom = (request.viewportHeight - marginPx).toFloat()

        // Slack between target line bottom and caret bottom.
        // Positive slack means caret is currently sitting above targetLineBottom.
        // Negative slack means caret is currently sitting below targetLineBottom (UNSAFE!).
        val slack = targetLineBottom - request.caretScreenBottom

        // Safety Invariant: If caret is currently below safe boundary (slack < 0f),
        // calculate corrective upward delta.
        if (slack < 0f) {
            val correctiveUp = -slack // positive upward delta
            ScribeScrollDiagnostics.log("ContractionRestoration", "Corrective upward applied: slack=$slack, correctiveUp=$correctiveUp")
            return ContractionRestorationResult(
                deltaY = correctiveUp,
                shouldScroll = true,
                remainingCompensation = request.temporaryDisplacement,
                reason = "Corrective upward to safe boundary"
            )
        }

        // Caret is sitting right at the safe boundary (within deadband above targetLineBottom)
        if (slack <= deadbandPx) {
            ScribeScrollDiagnostics.log("ContractionRestoration", "Zero delta: caret already at safe boundary (slack=$slack)")
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
            ScribeScrollDiagnostics.log(
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
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
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
}
