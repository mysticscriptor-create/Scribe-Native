package com.primaloptima.scribe.ui.editor.scroll

/**
 * Reasons for automatic or programmatic canvas positioning operations.
 */
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
 * Authoritative single-owner scroll and animation coordinator for all programmatic
 * and automatic canvas positioning.
 *
 * Manages a single active animation target, smooth retargeting without velocity/progress resets,
 * causal compensation bookkeeping, and safe-boundary enforcement.
 */
class ScribeScrollCoordinator(
    var totalScroll: Float = 0f,
    var temporaryDisplacement: Float = 0f,
    var caretLine: Int = -1,
    var caretCol: Int = 0,
    var isSelected: Boolean = false,
    var caretDocBottom: Float = 0f,
    var headerHeight: Int = 0,
    var density: Float = 1f
) {
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
     * Monotonically increasing operation ID. Prevents obsolete animation callbacks
     * from clearing or finishing newer operations.
     */
    var activeOperationId: Long = 0L
        private set

    // Callbacks for production integration
    var onScrollBy: ((deltaY: Float) -> Float)? = null
    var onAnimationFinished: (() -> Unit)? = null

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
     */
    fun applyStep(newAnimatedScroll: Float, operationId: Long? = null): Float {
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

        // Causal compensation accounting: only movement actually applied is recorded
        if (activeReason == ScrollAnimationReason.CONTRACTION_RESTORATION && actualMoved < 0f) {
            val appliedDown = -actualMoved
            temporaryDisplacement = (temporaryDisplacement - appliedDown).coerceAtLeast(0f)
            if (temporaryDisplacement <= 0f) {
                caretLine = -1
                caretCol = 0
                isSelected = false
            }
        } else if (isExpansionTransitionActive && actualMoved > 0f &&
            (activeReason == ScrollAnimationReason.CARET_VISIBILITY || activeReason == ScrollAnimationReason.PREDICTIVE_GLIDE)) {
            temporaryDisplacement += actualMoved
        }

        // Boundary collision / unreachable target detection:
        // If movement is completely blocked by bounds (consumed == 0 while stepDelta is non-zero),
        // do not continue applying steps as if the ideal trajectory was followed.
        val hitBoundary = (stepDelta > 0.05f && unconsumed > 0.05f && kotlin.math.abs(consumed) < 0.05f) ||
                (stepDelta < -0.05f && unconsumed < -0.05f && kotlin.math.abs(consumed) < 0.05f)

        val target = activeTargetTotalScroll
        if (hitBoundary || (target != null && kotlin.math.abs(totalScroll - target) < 0.5f)) {
            finishAnimation(activeOperationId)
        }

        return actualMoved
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
        if (reason != null && ScribeScrollDiagnostics.isDebugDiagnosticsEnabled) {
            ScribeScrollDiagnostics.log("ScrollCoordinator", "Cancelled op $operationId: $reason")
        }
    }

    fun invalidateCompensation(reason: String? = null) {
        cancelAnimation("Compensation invalidated: $reason", activeOperationId)
        temporaryDisplacement = 0f
        caretLine = -1
        caretCol = 0
        isSelected = false
    }

    fun invalidateIfCaretChanged(newLine: Int, newCol: Int, newSelected: Boolean) {
        if (caretLine != -1 && (caretLine != newLine || caretCol != newCol || isSelected != newSelected)) {
            invalidateCompensation("Caret changed to $newLine:$newCol")
        }
    }

    fun rebaseRemainingCompensation(remainingSlack: Float) {
        val safeSlack = maxOf(0f, remainingSlack)
        if (temporaryDisplacement > safeSlack) {
            temporaryDisplacement = safeSlack
        }
        if (temporaryDisplacement <= 0.5f) {
            temporaryDisplacement = 0f
            caretLine = -1
            caretCol = 0
            isSelected = false
        }
    }
}

/**
 * Backward compatibility alias for the AuthoritativeScrollCoordinator.
 */
typealias AuthoritativeScrollCoordinator = ScribeScrollCoordinator
