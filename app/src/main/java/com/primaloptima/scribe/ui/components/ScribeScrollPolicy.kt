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

        val headerRemaining = headerHeight - scrollD
        val textBottomScreenY = (headerRemaining + layoutHeight - offsetY).toFloat()

        val marginPx = (CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (CURSOR_DEADBAND_DP * density).roundToInt()
        val targetLineBottom = viewportHeight - marginPx

        // 2. Text bottom detection: If the bottom of all text is floating above targetLineBottom
        if (textBottomScreenY < (targetLineBottom - deadbandPx)) {
            val gap = targetLineBottom - textBottomScreenY
            val scrollDownBy = minOf(gap, totalScroll.toFloat())
            if (scrollDownBy >= 1f) {
                return -scrollDownBy
            }
        }

        // 3. Physical bounds check: If total scroll exceeds maxCanvasScroll
        if (totalScroll > maxCanvasScroll) {
            val excess = (totalScroll - maxCanvasScroll).toFloat()
            if (excess >= 1f) {
                return -excess
            }
        }

        return 0f
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
        isTextEmpty: Boolean = false
    ): Int {
        if (isTextEmpty || layoutHeight <= 0 || viewportHeight <= 0) return 0

        // True Pure Writer: persistentBottomSpacePx (keyboard + shortcut bar + breathing margin)
        // is the persistent physical cushion underlaying the full canvas.
        val space = if (persistentBottomSpacePx > 0) {
            persistentBottomSpacePx
        } else {
            (viewportHeight * 0.45f).toInt()
        }

        val maxScrollWithBottomSpace = (layoutHeight + space - viewportHeight).coerceAtLeast(0)
        return maxOf(baseSoraMaxY.coerceAtLeast(0), maxScrollWithBottomSpace)
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
}
