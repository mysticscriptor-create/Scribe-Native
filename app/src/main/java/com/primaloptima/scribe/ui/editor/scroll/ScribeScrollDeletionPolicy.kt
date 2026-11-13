package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.roundToInt

/**
 * Pure policy rules and calculations for document deletions and downward tail anchoring.
 *
 * Preserves breathing margins, monotonic non-growth bounds, 1-DOF transitional retraction,
 * and caret screen position invariants.
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
        val affectsDocumentTail: Boolean,
        val preEditLastNonBlankLine: Int = (preEditDocLineCount - 1).coerceAtLeast(0)
    )

    enum class DeletionAnchorIntent {
        TAIL_ANCHOR,   // User was editing at logical document end -> follow tail preserving breathing margin
        CARET_ANCHOR,  // User was editing in the middle -> keep collapsed caret screen Y stationary
        RESET_TO_TOP   // Document empty or fits entirely in view -> smoothly restore title and top
    }

    data class ScopedScrollStepResult(
        val nextIntOffset: Int,
        val nextResidualPx: Float,
        val hitBoundary: Boolean
    )

    data class PreModificationSnapshot(
        val isSelection: Boolean,
        val selectionStartLine: Int,
        val selectionEndLine: Int,
        val preEditDocLineCount: Int,
        val preEditDocHeight: Float,
        val preEditScrollY: Float,
        val preEditCaretScreenY: Float,
        val wasAtTail: Boolean,
        val preEditLastNonBlankLine: Int
    )

    data class DeletionSettlementValidation(
        val isSafe: Boolean,
        val correctiveTargetScroll: Float
    )

    /**
     * Monotonically merges a subsequent deletion event within the same edit batch into the earliest
     * active pre-edit snapshot without overwriting pre-edit scroll/document height metrics.
     */
    fun mergeDeletionBatchSnapshot(
        existing: DeletionSnapshot,
        batchEndLine: Int,
        currentLastNonBlankLine: Int = existing.preEditLastNonBlankLine
    ): DeletionSnapshot {
        val mergedEndLine = maxOf(existing.selectionEndLine, batchEndLine)
        val mergedAffectsTail = existing.affectsDocumentTail ||
            isLineRangeAffectingLogicalTail(mergedEndLine, existing.preEditLastNonBlankLine) ||
            isLineRangeAffectingLogicalTail(batchEndLine, currentLastNonBlankLine)
        return if (mergedEndLine == existing.selectionEndLine && mergedAffectsTail == existing.affectsDocumentTail) {
            existing
        } else {
            existing.copy(
                selectionEndLine = mergedEndLine,
                affectsDocumentTail = mergedAffectsTail
            )
        }
    }

    /**
     * Finds the last logical non-blank line in the document so trailing empty or
     * whitespace-only lines do not conceal deletions affecting the real final paragraph.
     */
    fun findLastNonBlankLine(
        lineCount: Int,
        lineAt: (Int) -> CharSequence
    ): Int {
        for (line in (lineCount - 1) downTo 0) {
            if (lineAt(line).isNotBlank()) {
                return line
            }
        }
        return 0
    }

    /**
     * Returns true if the deleted range touches or extends beyond the last meaningful non-blank line.
     */
    fun isLineRangeAffectingLogicalTail(
        endLine: Int,
        lastNonBlankLine: Int
    ): Boolean {
        return endLine >= lastNonBlankLine
    }

    /**
     * Checks whether the resulting collapsed post-edit caret sits at the logical document tail
     * (no meaningful non-blank lines or non-whitespace wrapped rows below it).
     */
    fun isCaretAtLogicalTail(
        lineCount: Int,
        caretLine: Int,
        caretCol: Int,
        lineAt: (Int) -> CharSequence,
        newCaretDocY: Float,
        layoutHeight: Float,
        rowHeight: Float
    ): Boolean {
        if (lineCount <= 0) return true
        val safeLine = caretLine.coerceIn(0, lineCount - 1)
        for (i in (safeLine + 1) until lineCount) {
            if (lineAt(i).isNotBlank()) {
                return false
            }
        }
        val lineSeq = lineAt(safeLine)
        val safeCol = caretCol.coerceIn(0, lineSeq.length)
        var hasNonWhitespaceAfter = false
        for (c in safeCol until lineSeq.length) {
            if (!lineSeq[c].isWhitespace()) {
                hasNonWhitespaceAfter = true
                break
            }
        }
        if (hasNonWhitespaceAfter && (layoutHeight - newCaretDocY) > rowHeight * 0.5f) {
            return false
        }
        return true
    }

    /**
     * Issue 4: Accurately classifies deletion intent using verified pre-edit snapshot and
     * post-edit caret placement.
     *
     * RESET_TO_TOP requires content PLUS breathing margin to fit inside usable viewport.
     * TAIL_ANCHOR requires viewport tail proximity, deletion touching logical tail, AND
     * resulting collapsed post-edit caret remaining at logical tail.
     */
    fun classifyDeletionIntent(
        snapshot: DeletionSnapshot,
        newDocLineCount: Int,
        newDocHeight: Float,
        usableViewportHeight: Float,
        breathingMarginPx: Float = 0f,
        isTextEmpty: Boolean = false,
        isPostEditCaretAtLogicalTail: Boolean = true
    ): DeletionAnchorIntent {
        if (isTextEmpty || newDocLineCount <= 0 || (newDocHeight + breathingMarginPx) <= usableViewportHeight) {
            return DeletionAnchorIntent.RESET_TO_TOP
        }
        if (snapshot.wasAtTail && snapshot.affectsDocumentTail && isPostEditCaretAtLogicalTail) {
            return DeletionAnchorIntent.TAIL_ANCHOR
        }
        return DeletionAnchorIntent.CARET_ANCHOR
    }

    /**
     * Issue 3 & 4: Calculates safe deletion destination satisfying both physical bounds and breathing margin.
     * Invariants:
     * 1. Monotonic Non-Growth: Deletion must never scroll downward beyond current scroll.
     * 2. Hard Breathing Margin: Last line / caret bottom + breathing margin must not exceed usable viewport.
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
                // effectiveTailBottom + breathingMarginPx <= usableBottom
                val effectiveTailDocY = maxOf(newDocHeight, newCaretDocY)
                val minimumSafeScroll = (headerHeight + effectiveTailDocY + breathingMarginPx - usableViewportHeight)
                    .coerceAtLeast(0f)
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
     * @param breathingMarginPx Minimum clearance below bottom line before resetting to top
     * @return Negative deltaY to smoothly pull the canvas down, or 0f if no adjustment needed
     */
    fun calculateDeletionAnchorDelta(
        headerHeight: Int,
        scrollD: Int,
        layoutHeight: Int,
        offsetY: Int,
        viewportHeight: Int,
        density: Float,
        maxCanvasScroll: Int,
        breathingMarginPx: Float = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density
    ): Float {
        val totalScroll = scrollD + offsetY
        if (totalScroll <= 0 || viewportHeight <= 0) return 0f

        // 1. Full document fit: Only reset scroll to 0 when remaining content PLUS breathing margin fits in viewport
        val totalContentHeightWithMargin = headerHeight + layoutHeight + breathingMarginPx
        if (totalContentHeightWithMargin <= viewportHeight.toFloat()) {
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

    /**
     * Scoped outer canvas step resolution (`scrollD`) with sub-pixel residual preservation
     * and deletion-only transitional retraction bounds `[maxD, currentScrollD]`.
     * Outside active shrinking deletion retraction, enforces strict `[0, maxD]` bounds.
     */
    fun resolveOuterScrollStep(
        currentScrollD: Int,
        currentResidualPx: Float,
        outerDelta: Float,
        headerHeight: Int,
        maxCanvasScroll: Int,
        isDeletionTransactionActive: Boolean,
        clampLowerToNewMax: Boolean = true
    ): ScopedScrollStepResult {
        val maxD = minOf(headerHeight, maxCanvasScroll).coerceAtLeast(0)
        val inDeletionRetraction = isDeletionTransactionActive && currentScrollD > maxD && outerDelta < 0f
        val minAllowed = if (inDeletionRetraction) (if (clampLowerToNewMax) maxD else 0) else 0
        val maxAllowed = if (inDeletionRetraction) currentScrollD else maxD
        val proposed = currentScrollD.toFloat() + currentResidualPx + outerDelta
        val hitLower = proposed < minAllowed.toFloat()
        // During transitional downward retraction, positive fractional carry from a prior rounding step
        // plus a smaller negative step can leave proposed slightly > currentScrollD; coerceIn preserves
        // monotonic non-growth without prematurely triggering hitBoundary or wiping the residual.
        val hitUpper = !inDeletionRetraction && proposed > maxAllowed.toFloat()
        val nextScrollD = when {
            hitLower -> minAllowed
            hitUpper -> maxAllowed
            else -> proposed.roundToInt().coerceIn(minAllowed, maxAllowed)
        }
        val nextResidual = if (hitLower || hitUpper) {
            0f
        } else {
            proposed - nextScrollD.toFloat()
        }
        return ScopedScrollStepResult(
            nextIntOffset = nextScrollD,
            nextResidualPx = nextResidual,
            hitBoundary = hitLower || hitUpper
        )
    }

    /**
     * Scoped inner editor step resolution (`editor.offsetY`) with sub-pixel residual preservation
     * and deletion-only transitional retraction bounds `[maxAllowedY, currentOffsetY]`.
     * Outside active shrinking deletion retraction, enforces strict `[0, maxAllowedY]` bounds.
     */
    fun resolveInnerScrollStep(
        currentOffsetY: Int,
        currentResidualPx: Float,
        innerDelta: Float,
        maxAllowedY: Int,
        isDeletionTransactionActive: Boolean,
        clampLowerToNewMax: Boolean = true
    ): ScopedScrollStepResult {
        val safeMaxY = maxAllowedY.coerceAtLeast(0)
        val inDeletionRetraction = isDeletionTransactionActive && currentOffsetY > safeMaxY && innerDelta < 0f
        val minAllowed = if (inDeletionRetraction) (if (clampLowerToNewMax) safeMaxY else 0) else 0
        val maxAllowed = if (inDeletionRetraction) currentOffsetY else safeMaxY
        val proposed = currentOffsetY.toFloat() + currentResidualPx + innerDelta
        val hitLower = proposed < minAllowed.toFloat()
        // See resolveOuterScrollStep: keep sub-pixel carry intact when retreating downward from an
        // already-overscrolled offset so small frames do not abort early.
        val hitUpper = !inDeletionRetraction && proposed > maxAllowed.toFloat()
        val nextOffsetY = when {
            hitLower -> minAllowed
            hitUpper -> maxAllowed
            else -> proposed.roundToInt().coerceIn(minAllowed, maxAllowed)
        }
        val nextResidual = if (hitLower || hitUpper) {
            0f
        } else {
            proposed - nextOffsetY.toFloat()
        }
        return ScopedScrollStepResult(
            nextIntOffset = nextOffsetY,
            nextResidualPx = nextResidual,
            hitBoundary = hitLower || hitUpper
        )
    }

    /**
     * Final post-settlement physical safety validation.
     * Ensures settled scroll satisfies both `totalScroll <= maxCanvasScroll` and
     * `caretScreenBottom <= usableViewportHeight - breathingMarginPx` without ever
     * forcing a downward scroll when the caret is already safe.
     */
    fun validateSettledDeletionSafety(
        currentTotalScroll: Float,
        headerHeight: Int,
        caretDocBottom: Float,
        layoutHeight: Float,
        usableViewportHeight: Int,
        breathingMarginPx: Float,
        maxCanvasScroll: Int,
        isTailAnchored: Boolean
    ): DeletionSettlementValidation {
        if (usableViewportHeight <= 0) {
            return DeletionSettlementValidation(isSafe = true, correctiveTargetScroll = currentTotalScroll)
        }
        val safeMax = maxCanvasScroll.toFloat().coerceAtLeast(0f)
        val targetLineBottom = (usableViewportHeight.toFloat() - breathingMarginPx).coerceAtLeast(0f)
        val effectiveDocBottom = if (isTailAnchored) maxOf(layoutHeight, caretDocBottom) else caretDocBottom
        val minSafeForCaret = (headerHeight.toFloat() + caretDocBottom - targetLineBottom).coerceAtLeast(0f)
        val minSafeForEffective = (headerHeight.toFloat() + effectiveDocBottom - targetLineBottom).coerceAtLeast(0f)
        val minRequiredSafe = maxOf(minSafeForCaret, if (isTailAnchored) minSafeForEffective else 0f)
            .coerceIn(0f, safeMax)

        if (currentTotalScroll > safeMax + 0.5f) {
            return DeletionSettlementValidation(
                isSafe = false,
                correctiveTargetScroll = maxOf(safeMax, minRequiredSafe).coerceIn(0f, safeMax)
            )
        }
        if (currentTotalScroll + 0.5f < minRequiredSafe) {
            return DeletionSettlementValidation(
                isSafe = false,
                correctiveTargetScroll = minRequiredSafe
            )
        }
        return DeletionSettlementValidation(isSafe = true, correctiveTargetScroll = currentTotalScroll)
    }
}


/**
 * Pure state machine coordinating pre-modification deletion snapshot staging,
 * multi-event batch tail merging, and replace-with-delete settlement.
 */
class ScribeDeletionSnapshotCoordinator {

    data class BatchTailProbe(
        val endLine: Int,
        val lastNonBlankLine: Int
    )

    sealed class AfterDeleteAction {
        data class BeginTransaction(
            val snapshot: ScribeScrollDeletionPolicy.DeletionSnapshot,
            val affectsDocumentTail: Boolean
        ) : AfterDeleteAction()

        data class MergeExistingBatch(
            val batchEndLine: Int,
            val batchLastNonBlankLine: Int?
        ) : AfterDeleteAction()

        data object MarkPendingOnly : AfterDeleteAction()
    }

    sealed class AfterInsertAction {
        data object None : AfterInsertAction()
        data object ClearTransaction : AfterInsertAction()
        data object SettleEmptyReplaceDelete : AfterInsertAction()
    }

    var stagedInitialSnapshot: ScribeScrollDeletionPolicy.PreModificationSnapshot? = null
        private set
    var stagedBatchProbe: BatchTailProbe? = null
        private set
    var isInContentReplace: Boolean = false
        private set
    var deletedDuringCurrentReplace: Boolean = false
        private set

    fun reset() {
        stagedInitialSnapshot = null
        stagedBatchProbe = null
        isInContentReplace = false
        deletedDuringCurrentReplace = false
    }

    fun onBeforeReplace() {
        isInContentReplace = true
        deletedDuringCurrentReplace = false
    }

    fun onBeforeModification(
        hasPendingDeletionSettlement: Boolean,
        captureInitialSnapshot: () -> ScribeScrollDeletionPolicy.PreModificationSnapshot?,
        captureBatchProbe: () -> BatchTailProbe?
    ) {
        if (!hasPendingDeletionSettlement && stagedInitialSnapshot == null) {
            stagedInitialSnapshot = captureInitialSnapshot()
            stagedBatchProbe = null
        } else if (hasPendingDeletionSettlement && stagedBatchProbe == null) {
            stagedBatchProbe = captureBatchProbe()
        }
    }

    fun onAfterDelete(
        afterDeleteEndLine: Int,
        hasPendingDeletionSettlement: Boolean
    ): AfterDeleteAction {
        val initial = stagedInitialSnapshot
        val probe = stagedBatchProbe
        stagedInitialSnapshot = null
        stagedBatchProbe = null
        if (isInContentReplace) {
            deletedDuringCurrentReplace = true
        }
        return if (initial != null) {
            val effectiveEndLine = maxOf(afterDeleteEndLine, initial.selectionEndLine)
            val affectsTail = ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(
                endLine = effectiveEndLine,
                lastNonBlankLine = initial.preEditLastNonBlankLine
            )
            if (!hasPendingDeletionSettlement) {
                AfterDeleteAction.BeginTransaction(
                    snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
                        transactionEpoch = 0L,
                        isSelection = initial.isSelection,
                        selectionStartLine = initial.selectionStartLine,
                        selectionEndLine = effectiveEndLine,
                        preEditDocLineCount = initial.preEditDocLineCount,
                        preEditDocHeight = initial.preEditDocHeight,
                        preEditScrollY = initial.preEditScrollY,
                        preEditCaretScreenY = initial.preEditCaretScreenY,
                        wasAtTail = initial.wasAtTail,
                        affectsDocumentTail = affectsTail,
                        preEditLastNonBlankLine = initial.preEditLastNonBlankLine
                    ),
                    affectsDocumentTail = affectsTail
                )
            } else {
                AfterDeleteAction.MergeExistingBatch(
                    batchEndLine = effectiveEndLine,
                    batchLastNonBlankLine = initial.preEditLastNonBlankLine
                )
            }
        } else if (hasPendingDeletionSettlement) {
            val effectiveEndLine = maxOf(afterDeleteEndLine, probe?.endLine ?: afterDeleteEndLine)
            AfterDeleteAction.MergeExistingBatch(
                batchEndLine = effectiveEndLine,
                batchLastNonBlankLine = probe?.lastNonBlankLine
            )
        } else {
            AfterDeleteAction.MarkPendingOnly
        }
    }

    fun onAfterInsert(
        insertedNotEmpty: Boolean,
        isTextEmpty: Boolean,
        currentTotalScroll: Int,
        maxCanvasScroll: Int,
        hasPendingOrActiveTransaction: Boolean
    ): AfterInsertAction {
        val wasReplaceWithDelete = isInContentReplace && deletedDuringCurrentReplace
        isInContentReplace = false
        deletedDuringCurrentReplace = false
        stagedInitialSnapshot = null
        stagedBatchProbe = null

        if (wasReplaceWithDelete) {
            if (insertedNotEmpty && !isTextEmpty && currentTotalScroll <= maxCanvasScroll) {
                return AfterInsertAction.ClearTransaction
            }
            if (!insertedNotEmpty) {
                return AfterInsertAction.SettleEmptyReplaceDelete
            }
            return AfterInsertAction.None
        }
        if (insertedNotEmpty && hasPendingOrActiveTransaction) {
            return AfterInsertAction.ClearTransaction
        }
        return AfterInsertAction.None
    }
}
