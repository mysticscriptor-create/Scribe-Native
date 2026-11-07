package com.primaloptima.scribe.ui.editor.scroll

/**
 * Pure policy rules and calculations for document deletions and downward tail anchoring.
 *
 * Preserves breathing margins, monotonic non-growth bounds, and caret screen position invariants.
 */
object ScribeScrollDeletionPolicy {

    /** Rapid downward anchoring when deleting text on the final line or shrinking layout */
    const val DURATION_DELETION_ANCHOR_MS = 140L

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

    /**
     * Calculates the downward canvas adjustment when deleting lines or shrinking text.
     *
     * @param headerHeight Measured height of the document header view
     * @param scrollD Current header displacement
     * @param layoutHeight Measured height of the Sora editor text layout
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
}
