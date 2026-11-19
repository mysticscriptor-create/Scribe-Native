package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.abs

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

enum class StepTerminationReason {
    NONE,
    TARGET_REACHED,
    BOUNDARY_HIT,
    SPRING_SETTLED,
    STALE_OR_INACTIVE
}

data class ScrollStepResult(
    val operationId: Long,
    val actualTotalScroll: Float,
    val consumedDelta: Float,
    val boundaryHit: Boolean,
    val terminationReason: StepTerminationReason,
    val isOperationFinished: Boolean
)

/**
 * Authoritative single-owner scroll and compensation coordinator.
 *
 * Eliminates dual state ownership across View layout listeners, touch events, and animation ticks by tracking
 * the active scroll animation target, operation IDs, interaction generations, and causal expansion displacement.
 *
 * Literal sole owner and production caller of [finishAnimation] via [applyStepWithResult].
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

    /**
     * Monotonically increasing interaction generation counter.
     * Advanced on confirmed manual drag gestures to invalidate stale asynchronous callbacks.
     */
    var interactionGeneration: Long = 0L
        private set

    fun onManualDragConfirmed() {
        interactionGeneration++
        invalidateCompensation("Confirmed manual drag")
    }

    // Callbacks for production integration
    var onScrollBy: ((deltaY: Float) -> Float)? = null
    var onAnimationFinished: (() -> Unit)? = null

    /**
     * Optional predicate supplied by host (e.g. UnifiedCanvasLayout) indicating whether the
     * most recent `onScrollBy` call hit or was clamped by a physical bound rather than deferred
     * into sub-pixel carry. Defaults to true when absent so existing mock tests preserve behavior.
     */
    var wasLastStepClampedByBoundary: (() -> Boolean)? = null

    var didLastStepClampAtBoundary: Boolean = false
        private set

    var lastClampedDelta: Float = 0f
        private set

    /**
     * Synchronizes coordinator scroll tracking with actual physical viewport scroll when idle.
     * No-op while an authoritative operation is active.
     */
    fun syncActualScroll(actualTotalScroll: Float) {
        if (isAnimationActive) return
        totalScroll = actualTotalScroll
        lastAnimatedScroll = actualTotalScroll
    }

    /**
     * Initiates or retargets an automatic positioning operation.
     *
     * Explicit return contract (callers MUST NOT treat `false` as a single ambiguous signal):
     * 1. Returns `false` when idle and within `< 1f` of `clampedTarget` (`!isAnimationActive && abs(distance) < 1f`):
     *    inactive sub-1px no-op; updates [activeTargetTotalScroll] without starting an operation.
     * 2. Returns `false` when already active with the same [reason] and `!forceRetarget` within `<= 2f` of
     *    [activeTargetTotalScroll]: same-reason active-target coalescing; the existing operation is STILL RUNNING
     *    and callers must NOT invoke completion callbacks prematurely.
     * 3. Returns `true` when a fresh operation was started (`activeOperationId++`, `isAnimationActive = true`).
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
        if (abs(distance) < 1f && !isAnimationActive) {
            activeTargetTotalScroll = clampedTarget
            return false
        }

        if (isAnimationActive && activeReason == reason) {
            val currTarget = activeTargetTotalScroll
            if (!forceRetarget && currTarget != null && abs(currTarget - clampedTarget) <= 2f) {
                return false
            }
        }

        activeOperationId++
        activeTargetTotalScroll = clampedTarget
        lastAnimatedScroll = currentScroll
        didLastStepClampAtBoundary = false
        lastClampedDelta = 0f
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
        didLastStepClampAtBoundary = false
        lastClampedDelta = 0f
    }

    /**
     * Applies a tick of animation with the new absolute animated scroll value, returning
     * a structured [ScrollStepResult] and owning completion detection and [finishAnimation].
     */
    fun applyStepWithResult(
        newAnimatedScroll: Float,
        operationId: Long? = null,
        isSpringSettled: Boolean = false
    ): ScrollStepResult {
        val requestedOpId = operationId ?: activeOperationId
        if ((operationId != null && operationId != activeOperationId) || !isAnimationActive) {
            return ScrollStepResult(
                operationId = requestedOpId,
                actualTotalScroll = totalScroll,
                consumedDelta = 0f,
                boundaryHit = false,
                terminationReason = StepTerminationReason.STALE_OR_INACTIVE,
                isOperationFinished = false
            )
        }

        val opId = activeOperationId
        val stepDelta = newAnimatedScroll - lastAnimatedScroll
        lastAnimatedScroll = newAnimatedScroll
        didLastStepClampAtBoundary = false
        lastClampedDelta = 0f

        if (abs(stepDelta) < 0.05f) {
            val target = activeTargetTotalScroll
            val targetReached = target != null && abs(totalScroll - target) < 0.5f
            val terminationReason = when {
                targetReached -> StepTerminationReason.TARGET_REACHED
                isSpringSettled -> StepTerminationReason.SPRING_SETTLED
                else -> StepTerminationReason.NONE
            }
            if (terminationReason != StepTerminationReason.NONE) {
                finishAnimation(opId)
                return ScrollStepResult(
                    operationId = opId,
                    actualTotalScroll = totalScroll,
                    consumedDelta = 0f,
                    boundaryHit = false,
                    terminationReason = terminationReason,
                    isOperationFinished = true
                )
            }
            return ScrollStepResult(
                operationId = opId,
                actualTotalScroll = totalScroll,
                consumedDelta = 0f,
                boundaryHit = false,
                terminationReason = StepTerminationReason.NONE,
                isOperationFinished = false
            )
        }

        val before = totalScroll
        val unconsumed = onScrollBy?.invoke(stepDelta) ?: 0f
        if (!isAnimationActive || activeOperationId != opId) {
            return ScrollStepResult(
                operationId = opId,
                actualTotalScroll = totalScroll,
                consumedDelta = 0f,
                boundaryHit = false,
                terminationReason = StepTerminationReason.STALE_OR_INACTIVE,
                isOperationFinished = false
            )
        }

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
            (activeReason == ScrollAnimationReason.CARET_VISIBILITY || activeReason == ScrollAnimationReason.PREDICTIVE_GLIDE)
        ) {
            temporaryDisplacement += actualMoved
        }

        val clampedByBoundary = wasLastStepClampedByBoundary?.invoke() ?: true
        val hasSignificantUnconsumed = (stepDelta > 0.05f && unconsumed > 0.05f) ||
            (stepDelta < -0.05f && unconsumed < -0.05f)
        val hasPhysicalOvershootDivergence = abs(totalScroll - lastAnimatedScroll) > 1.5f &&
            ((stepDelta < -0.05f && unconsumed > 0.05f) || (stepDelta > 0.05f && unconsumed < -0.05f))
        if ((clampedByBoundary && hasSignificantUnconsumed) || hasPhysicalOvershootDivergence) {
            didLastStepClampAtBoundary = true
            lastClampedDelta = unconsumed
            lastAnimatedScroll = totalScroll
        }

        // Boundary collision / unreachable target detection:
        // If movement is blocked by physical bounds (and not merely deferred in sub-pixel carry),
        // finish early without continuing an unreachable trajectory.
        val hitBoundary = clampedByBoundary && (
            (stepDelta > 0.05f && unconsumed > 0.05f && abs(consumed) < 0.05f) ||
                (stepDelta < -0.05f && unconsumed < -0.05f && abs(consumed) < 0.05f)
            )
        val target = activeTargetTotalScroll
        val targetReached = target != null && abs(totalScroll - target) < 0.5f

        val terminationReason = when {
            targetReached -> StepTerminationReason.TARGET_REACHED
            hitBoundary -> StepTerminationReason.BOUNDARY_HIT
            isSpringSettled -> StepTerminationReason.SPRING_SETTLED
            else -> StepTerminationReason.NONE
        }

        if (terminationReason != StepTerminationReason.NONE) {
            finishAnimation(opId)
            return ScrollStepResult(
                operationId = opId,
                actualTotalScroll = totalScroll,
                consumedDelta = actualMoved,
                boundaryHit = hitBoundary,
                terminationReason = terminationReason,
                isOperationFinished = true
            )
        }

        return ScrollStepResult(
            operationId = opId,
            actualTotalScroll = totalScroll,
            consumedDelta = actualMoved,
            boundaryHit = hitBoundary,
            terminationReason = StepTerminationReason.NONE,
            isOperationFinished = false
        )
    }

    /**
     * Applies a tick of animation with the new absolute animated scroll value.
     * Returns actual physical displacement moved.
     */
    fun applyStep(newAnimatedScroll: Float, operationId: Long? = null): Float {
        return applyStepWithResult(
            newAnimatedScroll = newAnimatedScroll,
            operationId = operationId,
            isSpringSettled = false
        ).consumedDelta
    }

    fun finishAnimation(operationId: Long? = null) {
        if (operationId != null && operationId != activeOperationId) return
        isAnimationActive = false
        activeReason = null
        activeTargetTotalScroll = null
        lastAnimatedScroll = totalScroll
        didLastStepClampAtBoundary = false
        lastClampedDelta = 0f
        onAnimationFinished?.invoke()
    }

    fun cancelAnimation(reason: String? = null, operationId: Long? = null) {
        if (operationId != null && operationId != activeOperationId) return
        activeOperationId++
        isAnimationActive = false
        activeReason = null
        activeTargetTotalScroll = null
        lastAnimatedScroll = totalScroll
        didLastStepClampAtBoundary = false
        lastClampedDelta = 0f
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
