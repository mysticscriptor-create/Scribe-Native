package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ScribeScrollCoordinator: authoritative state ownership, in-flight retargeting, and compensation lifecycle.
 */
class ScribeScrollCoordinatorTest {
    @Test
    fun testAuthoritativeCoordinator_SmoothRetargetingInFlight_NoVelocityReset() {
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 700f,
            temporaryDisplacement = 250f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1200f,
            headerHeight = 200,
            density = 2.5f
        )
        coordinator.onScrollBy = { 0f }

        // 1. Initial contraction restoration towards 500f (-200px)
        val shouldStart = coordinator.requestTarget(500f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(shouldStart)
        assertTrue(coordinator.isAnimationActive)
        assertEquals(500f, coordinator.activeTargetTotalScroll)

        // 2. Animate halfway: step to 600f (-100px applied)
        val moved1 = coordinator.applyStep(600f)
        assertEquals(-100f, moved1, 0.001f)
        assertEquals(600f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement decremented by 100px", 150f, coordinator.temporaryDisplacement, 0.001f)

        // 3. New target arrives while in flight (e.g. drawer collapsed further, new target 450f)
        val retargeted = coordinator.requestTarget(450f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue("Coordinator must accept retarget", retargeted)
        assertEquals("Target updated to 450f", 450f, coordinator.activeTargetTotalScroll)
        assertEquals("Current position remains at 600f", 600f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement remains at 150f", 150f, coordinator.temporaryDisplacement, 0.001f)

        // 4. Animate remaining distance to 450f (-150px)
        val moved2 = coordinator.applyStep(450f)
        assertEquals(-150f, moved2, 0.001f)
        assertEquals(450f, coordinator.totalScroll, 0.001f)
        assertEquals("Displacement fully consumed down to 0f", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals("Expansion caret reset", -1, coordinator.caretLine)
        assertFalse("Animation marked finished on reaching target", coordinator.isAnimationActive)
    }

    @Test
    fun testAuthoritativeCoordinator_CoalescesIntermediateCloseTargets() {
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 600f,
            temporaryDisplacement = 100f
        )
        coordinator.requestTarget(500f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(coordinator.isAnimationActive)

        // Sub-2px intermediate changes must NOT trigger retargeting or reset
        val ignoredRetarget = coordinator.requestTarget(501f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertFalse("Close target within deadband must be coalesced without restarting", ignoredRetarget)
        assertEquals(500f, coordinator.activeTargetTotalScroll)
    }

    @Test
    fun testAuthoritativeCoordinator_CausalExpansionTracking() {
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 300f,
            temporaryDisplacement = 0f,
            caretLine = 12,
            caretCol = 2
        )
        coordinator.onScrollBy = { 0f }

        // When transition is NOT active, upward movement is NOT recorded as expansion
        coordinator.isExpansionTransitionActive = false
        coordinator.requestTarget(400f, ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.applyStep(400f)
        assertEquals("No displacement recorded outside expansion transition", 0f, coordinator.temporaryDisplacement, 0.001f)

        // When transition IS active, upward movement from CARET_VISIBILITY is recorded
        coordinator.isExpansionTransitionActive = true
        coordinator.requestTarget(550f, ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.applyStep(550f)
        assertEquals("Expansion displacement recorded", 150f, coordinator.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testAuthoritativeCoordinator_StrictSafeBoundary_SlackNegativeIsAlwaysUnsafe() {
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val viewportHeight = 1000
        val targetLineBottom = viewportHeight - marginPx // 940px
        val headerHeight = 200
        val caretDocBottom = 1000f

        // targetTotalScroll = 259f -> finalCaretBottom = 200 + 1000 - 259 = 941px
        // slack = 940 - 941 = -1px (1px into the breathing margin)
        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = 259f,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = viewportHeight,
            density = density
        )
        assertFalse("Even 1px below breathing margin MUST be marked unsafe", validation.isSafe)
        assertEquals(1f, validation.correctiveDeltaY, 0.001f)

        // With targetTotalScroll = 260f -> finalCaretBottom = 940px -> slack = 0px
        val safeValidation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = 260f,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = viewportHeight,
            density = density
        )
        assertTrue("Exact 24dp breathing clearance must be marked safe", safeValidation.isSafe)
        assertEquals(0f, safeValidation.correctiveDeltaY, 0.001f)
    }

    @Test
    fun testAuthoritativeCoordinator_UserTouch_InvalidatesAllCompensation() {
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 500f,
            temporaryDisplacement = 180f,
            caretLine = 15,
            caretCol = 3,
            isSelected = false
        )
        coordinator.requestTarget(400f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        assertTrue(coordinator.isAnimationActive)

        // User touch down
        coordinator.invalidateCompensation("User touch")
        assertFalse(coordinator.isAnimationActive)
        assertEquals(0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    @Test
    fun testAuthoritativeCoordinator_CaretChange_InvalidatesCompensation() {
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 500f,
            temporaryDisplacement = 180f,
            caretLine = 15,
            caretCol = 3,
            isSelected = false
        )

        // Caret moved to different column on same line
        coordinator.invalidateIfCaretChanged(newLine = 15, newCol = 10, newSelected = false)
        assertEquals("Displacement must be invalidated on column change", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    /**
     * Test animation driver simulating Android's ValueAnimator lifecycle
     * where cancel() synchronously triggers onAnimationCancel followed by onAnimationEnd.
     */
    private class TestAnimationDriver : AnimationDriver {
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

    // --- Production Scroll Architecture Regression Tests (Point 9) ---

    @Test
    fun testCorrectionOrdering_SafetyCorrectionSettlesBeforeCompensationRebase() {
        // Point 9.5: A safe boundary correction is required, the corrective animation starts,
        // and compensation is rebased only after the correction finishes.
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 200f,
            temporaryDisplacement = 150f,
            caretLine = 20,
            caretCol = 5
        )
        coordinator.isExpansionTransitionActive = true

        // During a corrective animation, temporary displacement is NOT prematurely rebased
        coordinator.requestTarget(
            targetScroll = 250f,
            reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION
        )
        assertTrue(coordinator.isAnimationActive)
        assertEquals("Displacement must not be wiped out or rebased before correction settles", 150f, coordinator.temporaryDisplacement, 0.001f)

        // Apply step to completion
        coordinator.applyStep(250f)
        coordinator.finishAnimation()

        // After completion, rebaseRemainingCompensation is called with the settled slack
        coordinator.rebaseRemainingCompensation(remainingSlack = 120f)
        assertEquals("Displacement rebased to safe slack", 120f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(20, coordinator.caretLine)

        // If user touches screen during active state, compensation is immediately invalidated
        coordinator.invalidateCompensation("User touch")
        assertEquals(0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)
    }

    @Test
    fun testAuthoritativeScrollCoordinator_RetargetActiveOperation_ContinuousTargetUpdate() {
        // Verifies that retargetActiveOperation updates the active target and reason in-flight
        // without advancing operationId or disrupting step progression, ensuring continuous smooth glide.
        val coordinator = ScribeScrollCoordinator(totalScroll = 100f)
        coordinator.onScrollBy = { 0f } // all delta consumed

        val started = coordinator.requestTarget(200f, ScrollAnimationReason.CARET_VISIBILITY)
        assertTrue(started)
        assertTrue(coordinator.isAnimationActive)
        val initialOpId = coordinator.activeOperationId
        assertEquals(200f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Mid-flight: step toward 200f
        coordinator.applyStep(140f)
        assertEquals(140f, coordinator.totalScroll, 0.001f)

        // User pressed Enter again: new target 280f arrives while in flight
        coordinator.retargetActiveOperation(280f, ScrollAnimationReason.CARET_VISIBILITY)

        // Invariant: operationId must remain unchanged so running loop is not invalidated
        assertEquals(initialOpId, coordinator.activeOperationId)
        assertTrue(coordinator.isAnimationActive)
        assertEquals(280f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Subsequent steps seamlessly continue toward 280f
        coordinator.applyStep(210f)
        assertEquals(210f, coordinator.totalScroll, 0.001f)
        coordinator.applyStep(280f)
        assertEquals(280f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
    }

}
