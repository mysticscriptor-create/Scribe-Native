package com.primaloptima.scribe.ui.editor.scroll

import android.animation.ValueAnimator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.abs

/**
 * Standard FastOutSlowInInterpolator used across Scribe canvas animations.
 */
val FastOutSlowInInterpolator: Interpolator =
    PathInterpolator(0.4f, 0f, 0.2f, 1f)

/**
 * Encapsulates the critically damped spring glide physics, frame scheduling,
 * and retargeting lifecycle for UnifiedCanvasLayout.
 *
 * Delegates all completion detection and `finishAnimation()` calls strictly to [scrollCoordinator].
 */
class ScribeCanvasGlideAnimator(
    val scrollCoordinator: ScribeScrollCoordinator,
    private val postOnAnimationAction: (Runnable) -> Unit,
    private val removeCallbacksAction: (Runnable) -> Unit,
    var deletionBoundaryProvider: (() -> Float)? = null,
    var onSettledCallback: ((reason: ScrollAnimationReason?) -> Unit)? = null,
    var isDeletionSafetyActiveProvider: (() -> Boolean)? = null,
    var onCancelledCallback: ((reason: String?, wasDeletionSafetyActive: Boolean) -> Unit)? = null,
    var onFrameCommitted: ((result: ScrollStepResult, reason: ScrollAnimationReason?, physicalScroll: Float) -> Unit)? = null,
    private val monotonicTimeNanosProvider: () -> Long = { defaultMonotonicTimeNanos() }
) {
    companion object {
        private fun defaultMonotonicTimeNanos(): Long {
            val elapsed = try {
                android.os.SystemClock.elapsedRealtimeNanos()
            } catch (_: Throwable) {
                0L
            }
            return if (elapsed > 0L) elapsed else System.nanoTime()
        }
    }
    var activeCanvasAnimator: ValueAnimator? = null
    var isGlideRunning: Boolean = false
        private set
    var glidePosition: Float = 0f
        private set
    var glideVelocity: Float = 0f
        private set
    var glideTarget: Float = 0f
        private set
    private var glideLastTimeNs: Long = 0L
    var activeGlideReason: ScrollAnimationReason? = null
        private set
    private var activeGlideOnEnd: (() -> Unit)? = null
    var activeGlideOpId: Long = 0L
        private set

    val glideFrameRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isGlideRunning) return
            val nowNs = monotonicTimeNanosProvider()
            val dtSec = ((nowNs - glideLastTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            glideLastTimeNs = nowNs
            advanceFrame(dtSec)
        }
    }

    internal fun advanceFrame(dtSec: Float) {
        if (!isGlideRunning) return
        val opId = activeGlideOpId
        if (opId != scrollCoordinator.activeOperationId || !scrollCoordinator.isAnimationActive) {
            isGlideRunning = false
            return
        }

        val safeDt = dtSec.coerceIn(0.001f, 0.032f)
        // Critically damped second-order spring:
        // Natural frequency omega = 13.5 rad/s gives ~300ms ultra-smooth gentle glide
        // Damping ratio zeta = 1.0 (critically damped: zero overshoot, perfectly smooth asymptotic settling)
        val omega = 13.5f
        val displacement = glidePosition - glideTarget
        val accel = -omega * omega * displacement - 2f * omega * glideVelocity
        glideVelocity += accel * safeDt
        glidePosition += glideVelocity * safeDt

        // Hard boundary invariant: deletion safety persists across the full active deletion
        // transaction, surviving in-flight animation retargeting.
        val isDeletionSafetyActive = activeGlideReason == ScrollAnimationReason.DELETION_ANCHOR ||
            (isDeletionSafetyActiveProvider?.invoke() == true)
        if (isDeletionSafetyActive) {
            val minSafeScroll = deletionBoundaryProvider?.invoke() ?: 0f
            if (glidePosition < minSafeScroll) {
                glidePosition = minSafeScroll
                if (glideVelocity < 0f) glideVelocity = 0f
                if (glideTarget <= minSafeScroll) {
                    glideTarget = minSafeScroll
                }
            }
        }

        val distToTarget = abs(glidePosition - glideTarget)
        val isSettled = distToTarget < 0.5f && abs(glideVelocity) < 15f
        val requestedStep = if (isSettled && distToTarget < 1f) glideTarget else glidePosition

        val result = scrollCoordinator.applyStepWithResult(
            newAnimatedScroll = requestedStep,
            operationId = opId,
            isSpringSettled = isSettled
        )

        if (result.terminationReason == StepTerminationReason.STALE_OR_INACTIVE || result.operationId != opId) {
            isGlideRunning = false
            return
        }

        if (result.boundaryHit || (!result.isOperationFinished && scrollCoordinator.didLastStepClampAtBoundary)) {
            glidePosition = result.actualTotalScroll
            val clampedDelta = scrollCoordinator.lastClampedDelta
            if ((clampedDelta > 0f && glideVelocity > 0f) ||
                (clampedDelta < 0f && glideVelocity < 0f) ||
                result.boundaryHit
            ) {
                glideVelocity = 0f
            }
        }

        val frameReason = activeGlideReason
        onFrameCommitted?.invoke(result, frameReason, result.actualTotalScroll)

        if (result.isOperationFinished) {
            isGlideRunning = false
            val completedReason = activeGlideReason
            val onEnd = activeGlideOnEnd
            activeGlideReason = null
            activeGlideOnEnd = null
            onSettledCallback?.invoke(completedReason)
            if (!isGlideRunning && !scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId) {
                onEnd?.invoke()
            }
        } else if (opId == scrollCoordinator.activeOperationId && scrollCoordinator.isAnimationActive && isGlideRunning) {
            postOnAnimationAction(glideFrameRunnable)
        } else {
            isGlideRunning = false
        }
    }

    fun startAuthoritativeAnimation(
        currentScroll: Float,
        targetTotalScroll: Float,
        reason: ScrollAnimationReason,
        maxCanvasScroll: Int,
        forceRetarget: Boolean = false,
        onEnd: (() -> Unit)? = null
    ): Boolean {
        val clampedTarget = targetTotalScroll.coerceIn(0f, maxCanvasScroll.toFloat())
        val physicalDistance = clampedTarget - currentScroll

        // 1. Active glide coalescing vs retargeting
        if (isGlideRunning && scrollCoordinator.isAnimationActive) {
            val currTarget = glideTarget
            if (!forceRetarget && abs(currTarget - clampedTarget) <= 1f) {
                if (onEnd != null) {
                    val prevOnEnd = activeGlideOnEnd
                    activeGlideOnEnd = {
                        prevOnEnd?.invoke()
                        onEnd.invoke()
                    }
                }
                return false
            }
            glideTarget = clampedTarget
            activeGlideReason = reason
            if (onEnd != null) {
                val prevOnEnd = activeGlideOnEnd
                activeGlideOnEnd = {
                    prevOnEnd?.invoke()
                    onEnd.invoke()
                }
            }
            scrollCoordinator.retargetActiveOperation(clampedTarget, reason, maxCanvasScroll)
            return true
        }

        // 2. Fresh inactive sub-1px no-op (< 1f, preserving exact 1.00 px Mode-1 creation)
        if (!scrollCoordinator.isAnimationActive && abs(physicalDistance) < 1f) {
            scrollCoordinator.syncActualScroll(currentScroll)
            onEnd?.invoke()
            return false
        }

        // 3. Coordinator target request
        val shouldStart = scrollCoordinator.requestTarget(clampedTarget, reason, forceRetarget, maxCanvasScroll)
        if (!shouldStart) {
            if (scrollCoordinator.isAnimationActive) {
                if (onEnd != null) {
                    val prevOnEnd = activeGlideOnEnd
                    activeGlideOnEnd = {
                        prevOnEnd?.invoke()
                        onEnd.invoke()
                    }
                }
            } else {
                scrollCoordinator.syncActualScroll(currentScroll)
                onEnd?.invoke()
            }
            return false
        }

        val opId = scrollCoordinator.activeOperationId
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        removeCallbacksAction(glideFrameRunnable)

        val target = scrollCoordinator.activeTargetTotalScroll ?: clampedTarget
        val distance = abs(target - currentScroll)
        if (distance < 1f) {
            val result = scrollCoordinator.applyStepWithResult(
                newAnimatedScroll = target,
                operationId = opId,
                isSpringSettled = true
            )
            if (result.operationId == opId &&
                result.isOperationFinished &&
                !scrollCoordinator.isAnimationActive &&
                opId == scrollCoordinator.activeOperationId
            ) {
                onEnd?.invoke()
            }
            return false
        }

        isGlideRunning = true
        glidePosition = currentScroll
        glideVelocity = 0f
        glideTarget = target
        glideLastTimeNs = monotonicTimeNanosProvider()
        activeGlideReason = reason
        activeGlideOnEnd = onEnd
        activeGlideOpId = opId

        postOnAnimationAction(glideFrameRunnable)
        return true
    }

    fun cancel(reason: String? = null) {
        val wasActive = isGlideRunning || scrollCoordinator.isAnimationActive || activeCanvasAnimator != null
        val wasDeletionSafetyActive = activeGlideReason == ScrollAnimationReason.DELETION_ANCHOR ||
            (isDeletionSafetyActiveProvider?.invoke() == true)
        removeCallbacksAction(glideFrameRunnable)
        isGlideRunning = false
        val onEnd = activeGlideOnEnd
        activeGlideReason = null
        activeGlideOnEnd = null
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        scrollCoordinator.cancelAnimation(reason)
        onEnd?.invoke()
        if (wasActive) {
            onCancelledCallback?.invoke(reason, wasDeletionSafetyActive)
        }
    }

    /**
     * Cancels any running glide/animator and coordinator operation without invoking
     * completion (`onEnd`) or cancellation (`onCancelledCallback`) callbacks, allowing
     * callers to preserve or hand off an active deletion/gap-collapse transaction.
     */
    fun cancelPreservingTransaction(reason: String? = null) {
        removeCallbacksAction(glideFrameRunnable)
        isGlideRunning = false
        activeGlideOnEnd = null
        activeGlideReason = null
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        scrollCoordinator.cancelAnimation(reason)
    }
}
