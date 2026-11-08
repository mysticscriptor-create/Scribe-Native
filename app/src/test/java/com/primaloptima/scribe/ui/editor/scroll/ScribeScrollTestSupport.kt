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
}

/**
 * Test animation driver implementation simulating frame steps and completions in tests.
 */
class TestAnimationDriver : AnimationDriver {
    var from: Float = 0f
    var to: Float = 0f
    var durationMs: Long = 0L
    var onUpdate: ((Float) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onEnd: (() -> Unit)? = null
    var isRunning: Boolean = false
    var isCancelled: Boolean = false
    var isEnded: Boolean = false

    override fun start(
        from: Float,
        to: Float,
        durationMs: Long,
        onUpdate: (Float) -> Unit,
        onCancel: () -> Unit,
        onEnd: () -> Unit
    ) {
        this.from = from
        this.to = to
        this.durationMs = durationMs
        this.onUpdate = onUpdate
        this.onCancel = onCancel
        this.onEnd = onEnd
        this.isRunning = true
        this.isCancelled = false
        this.isEnded = false
    }

    override fun cancel() {
        if (!isRunning) return
        isRunning = false
        isCancelled = true
        onCancel?.invoke()
    }

    fun step(progress: Float) {
        if (!isRunning) return
        val current = from + (to - from) * progress.coerceIn(0f, 1f)
        onUpdate?.invoke(current)
    }

    fun complete() {
        if (!isRunning) return
        isRunning = false
        isEnded = true
        onUpdate?.invoke(to)
        onEnd?.invoke()
    }
}

/**
 * Test-support animation coordinator driving ScribeScrollCoordinator
 * with an AnimationDriver. Enforces monotonic operation ID tracking and race-free
 * multi-step simulation in unit test suites.
 */
class ProductionScrollAnimationCoordinator(
    val scrollCoordinator: ScribeScrollCoordinator,
    var animationDriverFactory: (() -> AnimationDriver)? = null
) {
    var activeDriver: AnimationDriver? = null

    fun startAuthoritativeAnimation(
        targetTotalScroll: Float,
        reason: ScrollAnimationReason,
        forceRetarget: Boolean = false,
        durationMs: Long = 300L,
        currentScroll: Float = scrollCoordinator.totalScroll,
        maxCanvasScroll: Int = 10000,
        onEnd: (() -> Unit)? = null
    ): Boolean {
        val clampedTarget = targetTotalScroll.coerceIn(0f, maxCanvasScroll.toFloat())

        // Continuous retargeting: if an animation is already active, seamlessly update target in-flight!
        if (scrollCoordinator.isAnimationActive) {
            val currTarget = scrollCoordinator.activeTargetTotalScroll
            if (!forceRetarget && currTarget != null && abs(currTarget - clampedTarget) <= 1f) {
                onEnd?.invoke()
                return false
            }
            scrollCoordinator.retargetActiveOperation(clampedTarget, reason, maxCanvasScroll)
            val driver = activeDriver
            if (driver != null) {
                driver.cancel()
                val newDriver = animationDriverFactory?.invoke()
                activeDriver = newDriver
                val opId = scrollCoordinator.activeOperationId
                val distance = abs(clampedTarget - currentScroll)
                val scaledDuration = (distance * 1.2f).toLong().coerceIn(120L, durationMs)
                newDriver?.start(
                    from = currentScroll,
                    to = clampedTarget,
                    durationMs = scaledDuration,
                    onUpdate = { curr ->
                        if (opId == scrollCoordinator.activeOperationId) {
                            scrollCoordinator.applyStep(curr, opId)
                        }
                    },
                    onCancel = {
                        if (opId == scrollCoordinator.activeOperationId) {
                            activeDriver = null
                            scrollCoordinator.cancelAnimation("Driver cancelled", opId)
                        }
                    },
                    onEnd = {
                        if (opId == scrollCoordinator.activeOperationId) {
                            activeDriver = null
                            scrollCoordinator.finishAnimation(opId)
                            onEnd?.invoke()
                        }
                    }
                )
            }
            return true
        }

        // Step 1: Secure exclusive single-owner target from authoritative coordinator
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
