package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.abs

/**
 * Testable animation driver abstraction for JUnit test drivers.
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
 * Test animation driver simulating Android's ValueAnimator lifecycle
 * where cancel() synchronously triggers onAnimationCancel followed by onAnimationEnd.
 */
class TestAnimationDriver : AnimationDriver {
    var isStarted = false
    var fromVal = 0f
    var toVal = 0f
    var updateCb: ((Float) -> Unit)? = null
    var cancelCb: (() -> Unit)? = null
    var endCb: (() -> Unit)? = null
    override var isRunning = false

    override fun start(
        from: Float,
        to: Float,
        durationMs: Long,
        onUpdate: (Float) -> Unit,
        onCancel: () -> Unit,
        onEnd: () -> Unit
    ) {
        isStarted = true
        isRunning = true
        fromVal = from
        toVal = to
        updateCb = onUpdate
        cancelCb = onCancel
        endCb = onEnd
    }

    override fun cancel() {
        if (isRunning) {
            isRunning = false
            // Android ValueAnimator lifecycle: onAnimationCancel then onAnimationEnd
            cancelCb?.invoke()
            endCb?.invoke()
        }
    }

    fun step(value: Float) {
        if (isRunning) {
            updateCb?.invoke(value)
        }
    }

    fun complete() {
        if (isRunning) {
            isRunning = false
            endCb?.invoke()
        }
    }
}

/**
 * Production-grade animation controller coordinating ScribeScrollCoordinator
 * with an AnimationDriver. Enforces monotonic operation ID tracking and race-free
 * cancellation / retargeting without stale callback interference.
 */
class ProductionScrollAnimationCoordinator(
    val scrollCoordinator: ScribeScrollCoordinator,
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
        val distance = abs(target - currentScroll)
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
 * and tick-by-tick compensation reconciliation in unit tests.
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
    val coordinator = ScribeScrollCoordinator(
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
    ): ScribeScrollRestorationPolicy.ContractionRestorationResult {
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
            return ScribeScrollRestorationPolicy.ContractionRestorationResult(0f, false, 0f, "Dismissed")
        }

        val caretScreenBottom = (headerHeight + caretDocBottom - totalScroll)
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
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
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)

        if (result.shouldScroll) {
            val candidateTarget = (totalScroll + result.deltaY).coerceAtLeast(0f)
            val validation = ScribeScrollRestorationPolicy.validateFinalTarget(candidateTarget, caretDocBottom, headerHeight, newViewportHeight, density)
            val safeTarget = if (!validation.isSafe) candidateTarget + validation.correctiveDeltaY else candidateTarget
            coordinator.requestTarget(safeTarget, ScrollAnimationReason.CONTRACTION_RESTORATION)
        }

        return result
    }

    fun applyAnimationStep(fraction: Float) {
        if (!isRestorationActive) return
        val stepTarget = totalScroll + (targetTotalScroll - totalScroll) * fraction
        val moved = coordinator.applyStep(stepTarget)
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
