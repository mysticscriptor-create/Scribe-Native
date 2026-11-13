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

    @Test
    fun testCoordinator_SubPixelAccumulation_DoesNotAbortPrematurelyOrDoubleCount() {
        // Scenario 8a: Verifies that when rendered integer scroll defers sub-pixel motion into a separate residual
        // (so physical totalScroll changes by 0px on a -0.4px step and -1px once residual crosses 0.5px),
        // ScribeScrollCoordinator neither prematurely aborts on hitBoundary nor double-counts residual.
        val coordinator = ScribeScrollCoordinator(totalScroll = 500f)
        var renderedScrollD = 200
        var renderedOffsetY = 300
        var editorResidual = 0f
        var lastClamped = false

        coordinator.wasLastStepClampedByBoundary = { lastClamped }
        coordinator.onScrollBy = { dy ->
            val physicalBefore = renderedScrollD + renderedOffsetY
            val step = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
                currentOffsetY = renderedOffsetY,
                currentResidualPx = editorResidual,
                innerDelta = dy,
                maxAllowedY = 240,
                isDeletionTransactionActive = true
            )
            renderedOffsetY = step.nextIntOffset
            editorResidual = step.nextResidualPx
            lastClamped = step.hitBoundary
            val physicalAfter = renderedScrollD + renderedOffsetY
            dy - (physicalAfter - physicalBefore).toFloat()
        }

        assertTrue(coordinator.requestTarget(440f, ScrollAnimationReason.DELETION_ANCHOR, maxCanvasScroll = 500))

        // Step 1: -0.4px -> rounded integer stays 300, residual becomes -0.4px, NOT a boundary hit
        coordinator.applyStep(499.6f)
        assertEquals("Rendered position stays integer 500", 500f, coordinator.totalScroll, 0.001f)
        assertTrue("Sub-pixel deferral must NOT falsely abort active animation", coordinator.isAnimationActive)
        assertFalse(coordinator.didLastStepClampAtBoundary)

        // Step 2: another -0.4px -> proposed 299.2 -> integer moves 299 (-1px physical), residual +0.2px
        coordinator.applyStep(499.2f)
        assertEquals("Coordinator totalScroll matches rendered integer 499", 499f, coordinator.totalScroll, 0.001f)
        assertEquals(299, renderedOffsetY)
        assertEquals(0.2f, editorResidual, 0.001f)
        assertTrue(coordinator.isAnimationActive)
        assertFalse(coordinator.didLastStepClampAtBoundary)

        // Verify zero residual double-counting over 10 small steps (-0.3f each -> total -3.0f)
        for (i in 1..10) {
            coordinator.applyStep(499.2f - i * 0.3f)
        }
        assertEquals((renderedScrollD + renderedOffsetY).toFloat(), coordinator.totalScroll, 0.001f)
        assertEquals(496f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testCoordinator_GenuineBoundaryClamp_ReconcilesLastAnimatedScrollAndSignalsClamped() {
        // Scenario 8b: Genuine bound clamp reconciles lastAnimatedScroll = totalScroll and records clamped delta
        val coordinator = ScribeScrollCoordinator(totalScroll = 100f)
        var renderedScroll = 100
        var lastClamped = false

        coordinator.wasLastStepClampedByBoundary = { lastClamped }
        coordinator.onScrollBy = { dy ->
            val before = renderedScroll
            val step = ScribeScrollDeletionPolicy.resolveOuterScrollStep(
                currentScrollD = renderedScroll,
                currentResidualPx = 0f,
                outerDelta = dy,
                headerHeight = 200,
                maxCanvasScroll = 80,
                isDeletionTransactionActive = true
            )
            renderedScroll = step.nextIntOffset
            lastClamped = step.hitBoundary
            dy - (renderedScroll - before).toFloat()
        }

        assertTrue(coordinator.requestTarget(60f, ScrollAnimationReason.DELETION_ANCHOR, maxCanvasScroll = 100))
        coordinator.applyStep(70f)
        assertEquals(80, renderedScroll)
        assertEquals(80f, coordinator.totalScroll, 0.001f)
        assertTrue(coordinator.didLastStepClampAtBoundary)
        assertEquals(-10f, coordinator.lastClampedDelta, 0.001f)
    }

    @Test
    fun testScopedResolvers_StrictBoundsOutsideDeletion_TransitionalBoundsDuringDeletion() {
        // Outside deletion transaction: strict [0, maxAllowedY] even if offset > max
        val normalStep = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
            currentOffsetY = 500,
            currentResidualPx = 0f,
            innerDelta = -3f,
            maxAllowedY = 440,
            isDeletionTransactionActive = false
        )
        assertEquals(440, normalStep.nextIntOffset)
        assertTrue(normalStep.hitBoundary)

        // Inside deletion transaction: -3f moves cleanly 500 -> 497
        val deletionStep = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
            currentOffsetY = 500,
            currentResidualPx = 0f,
            innerDelta = -3f,
            maxAllowedY = 440,
            isDeletionTransactionActive = true
        )
        assertEquals(497, deletionStep.nextIntOffset)
        assertFalse(deletionStep.hitBoundary)

        // Inside deletion transaction with large negative step: stops at [440, 500], zero overshoot below 440
        val deletionLargeStep = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
            currentOffsetY = 500,
            currentResidualPx = 0f,
            innerDelta = -120f,
            maxAllowedY = 440,
            isDeletionTransactionActive = true
        )
        assertEquals(440, deletionLargeStep.nextIntOffset)
        assertTrue(deletionLargeStep.hitBoundary)
        assertEquals(0f, deletionLargeStep.nextResidualPx, 0.001f)

        // Outer layer equivalent protection when maxCanvasScroll_new < headerHeight:
        val outerRetractStep = ScribeScrollDeletionPolicy.resolveOuterScrollStep(
            currentScrollD = 200,
            currentResidualPx = 0f,
            outerDelta = -5f,
            headerHeight = 200,
            maxCanvasScroll = 120,
            isDeletionTransactionActive = true
        )
        assertEquals(195, outerRetractStep.nextIntOffset)
        assertFalse(outerRetractStep.hitBoundary)

        val outerOvershootStop = ScribeScrollDeletionPolicy.resolveOuterScrollStep(
            currentScrollD = 200,
            currentResidualPx = 0f,
            outerDelta = -150f,
            headerHeight = 200,
            maxCanvasScroll = 120,
            isDeletionTransactionActive = true
        )
        assertEquals(120, outerOvershootStop.nextIntOffset)
        assertTrue(outerOvershootStop.hitBoundary)
    }

}
