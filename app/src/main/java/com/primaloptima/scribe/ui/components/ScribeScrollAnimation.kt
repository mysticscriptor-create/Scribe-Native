package com.primaloptima.scribe.ui.components

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
 * Testable animation driver abstraction for production ValueAnimator and JUnit test drivers.
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
 * Production-grade animation controller coordinating AuthoritativeScrollCoordinator
 * with an AnimationDriver. Enforces monotonic operation ID tracking and race-free
 * cancellation / retargeting without stale callback interference.
 */
open class ProductionScrollAnimationCoordinator(
    val scrollCoordinator: AuthoritativeScrollCoordinator,
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
 * Encapsulates the critically damped spring glide physics, frame scheduling,
 * and retargeting lifecycle for UnifiedCanvasLayout.
 */
class ScribeCanvasGlideAnimator(
    val scrollCoordinator: ScribeScrollCoordinator,
    private val postOnAnimationAction: (Runnable) -> Unit,
    private val removeCallbacksAction: (Runnable) -> Unit,
    var deletionBoundaryProvider: (() -> Float)? = null,
    var onSettledCallback: ((reason: ScrollAnimationReason?) -> Unit)? = null
) {
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
            val opId = activeGlideOpId
            if (opId != scrollCoordinator.activeOperationId || !scrollCoordinator.isAnimationActive) {
                isGlideRunning = false
                return
            }

            val nowNs = System.nanoTime()
            val dtSec = ((nowNs - glideLastTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            glideLastTimeNs = nowNs

            // Critically damped second-order spring:
            // Natural frequency omega = 13.5 rad/s gives ~300ms ultra-smooth gentle glide
            // Damping ratio zeta = 1.0 (critically damped: zero overshoot, perfectly smooth asymptotic settling)
            val omega = 13.5f
            val displacement = glidePosition - glideTarget
            val accel = -omega * omega * displacement - 2f * omega * glideVelocity
            glideVelocity += accel * dtSec
            glidePosition += glideVelocity * dtSec

            // Hard boundary invariant: deletion glide must never cross the 24dp breathing margin
            if (activeGlideReason == ScrollAnimationReason.DELETION_ANCHOR) {
                val minSafeScroll = deletionBoundaryProvider?.invoke() ?: 0f
                if (glidePosition < minSafeScroll) {
                    glidePosition = minSafeScroll
                    if (glideVelocity < 0f) glideVelocity = 0f
                }
            }

            scrollCoordinator.applyStep(glidePosition, opId)

            val distToTarget = abs(glidePosition - glideTarget)
            val isSettled = distToTarget < 0.5f && abs(glideVelocity) < 15f
            val isStoppedByCoord = !scrollCoordinator.isAnimationActive

            if (isSettled || isStoppedByCoord) {
                isGlideRunning = false
                if (scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId) {
                    if (distToTarget < 1f) {
                        scrollCoordinator.applyStep(glideTarget, opId)
                    }
                    scrollCoordinator.finishAnimation(opId)
                }
                val reason = activeGlideReason
                val onEnd = activeGlideOnEnd
                activeGlideOnEnd = null
                activeGlideReason = null

                onSettledCallback?.invoke(reason)
                onEnd?.invoke()
            } else {
                postOnAnimationAction(this)
            }
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

        // Continuous retargeting: if a glide is already running, seamlessly retarget in-flight!
        if (isGlideRunning && scrollCoordinator.isAnimationActive) {
            val currTarget = glideTarget
            if (!forceRetarget && abs(currTarget - clampedTarget) <= 1f) {
                onEnd?.invoke()
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

        val shouldStart = scrollCoordinator.requestTarget(clampedTarget, reason, forceRetarget, maxCanvasScroll)
        if (!shouldStart) {
            onEnd?.invoke()
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
            scrollCoordinator.finishAnimation(opId)
            onEnd?.invoke()
            return false
        }

        isGlideRunning = true
        glidePosition = currentScroll
        glideVelocity = 0f
        glideTarget = target
        glideLastTimeNs = System.nanoTime()
        activeGlideReason = reason
        activeGlideOnEnd = onEnd
        activeGlideOpId = opId

        postOnAnimationAction(glideFrameRunnable)
        return true
    }

    fun cancel(reason: String? = null) {
        removeCallbacksAction(glideFrameRunnable)
        isGlideRunning = false
        activeGlideReason = null
        activeGlideOnEnd = null
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        scrollCoordinator.cancelAnimation(reason)
    }
}
