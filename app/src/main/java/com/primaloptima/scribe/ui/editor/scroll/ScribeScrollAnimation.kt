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
                    if (glideTarget <= minSafeScroll) {
                        glideTarget = minSafeScroll
                    }
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
        val onEnd = activeGlideOnEnd
        activeGlideReason = null
        activeGlideOnEnd = null
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        scrollCoordinator.cancelAnimation(reason)
        onEnd?.invoke()
    }
}
