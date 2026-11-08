package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ProductionScrollAnimationCoordinator and AnimationDriver integration.
 */
class ScribeScrollAnimationTest {
    @Test
    fun testProductionCoordinator_CancellationAndReplacement_PreventsStaleCallbacks() {
        // Point 9.1: Cancel an ongoing animation with a new target. Verify the old animator's
        // cancel and completion callbacks cannot clear the new target or trigger obsolete restoration,
        // and verify the coordinator continues driving to the new destination.
        val coordinator = ScribeScrollCoordinator(totalScroll = 100f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Start operation 1: from 100f to 300f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        assertEquals(1, drivers.size)
        val driver1 = drivers[0]
        assertTrue("Driver 1 must be running", driver1.isRunning)

        // Step driver 1 to 150f
        driver1.step(150f)
        assertEquals(150f, coordinator.totalScroll, 0.001f)

        // Start operation 2: replace with target 500f
        // This cancels driver 1. In Android, cancel() invokes cancelCb followed by endCb synchronously.
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 500f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )

        // Verify driver 1 was cancelled
        assertFalse("Driver 1 must no longer be running", driver1.isRunning)
        assertEquals(2, drivers.size)
        val driver2 = drivers[1]
        assertTrue("Driver 2 must be running", driver2.isRunning)

        // Crucial invariant: Driver 1's synchronous cancel & end callbacks MUST NOT have cleared
        // the active animation state or erased driver 2's new target!
        assertTrue("Coordinator must remain active with operation 2", coordinator.isAnimationActive)
        assertEquals("Target must be 500f, not erased by old driver", 500f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)
        assertEquals(2L, productionCoordinator.activeOperationId)
        assertEquals(2L, coordinator.activeOperationId)

        // Step driver 2 to completion
        driver2.step(300f)
        assertEquals(300f, coordinator.totalScroll, 0.001f)
        driver2.step(500f)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
        driver2.complete()

        // After driver 2 completes, animation is cleanly finished at target 500f
        assertFalse("Coordinator should be finished after active driver ends", coordinator.isAnimationActive)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_InFlightRetargeting_SmoothlyUpdatesWithoutHitchOrRelativeError() {
        // Point 9.2: Retarget while moving, verify the animator smoothly updates to the final destination
        // without accumulating relative errors, and verify sub-deadband changes do not restart or hitch.
        val coordinator = ScribeScrollCoordinator(totalScroll = 100f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Initial target 300f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver1 = drivers[0]
        driver1.step(200f)
        assertEquals(200f, coordinator.totalScroll, 0.001f)

        // Sub-deadband target change (e.g. 201f vs current target 300f -> wait, let's test sub-deadband
        // against activeTarget: if target was 300f and new request is 301f, delta is 1f <= 2f)
        val ignoredRetarget = coordinator.requestTarget(
            targetScroll = 301f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        assertFalse("Sub-deadband target change (<= 2f) must not restart animation or hitch", ignoredRetarget)
        assertEquals("Target remains 300f", 300f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Material target change: 450f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 450f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        assertEquals(2, drivers.size)
        val driver2 = drivers[1]

        // Driver 2 must start from current scroll (200f) to new target (450f)
        assertEquals(200f, driver2.fromVal, 0.001f)
        assertEquals(450f, driver2.toVal, 0.001f)

        // Step and complete
        driver2.step(450f)
        driver2.complete()
        assertEquals(450f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_ClampBoundaryBehavior_StopsAtBoundaryWithoutFakingTrajectory() {
        // Point 9.3: Hit a clamped maximum while animating, verify the animator stops at the boundary
        // without faking an unreachable trajectory, and verify the coordinator reflects the true final position.
        val coordinator = ScribeScrollCoordinator(totalScroll = 300f)
        val maxCanvasScroll = 500

        // Simulate physical scroll clamping at 450 (e.g. content restricts scroll before maxCanvasScroll)
        coordinator.onScrollBy = { stepDelta ->
            val newProposed = coordinator.totalScroll + stepDelta
            if (newProposed > 450f) {
                val allowed = (450f - coordinator.totalScroll).coerceAtLeast(0f)
                val unconsumed = stepDelta - allowed
                unconsumed
            } else {
                0f
            }
        }

        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Request target 600f (exceeding maxCanvasScroll 500)
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 600f,
            reason = ScrollAnimationReason.CARET_VISIBILITY,
            maxCanvasScroll = maxCanvasScroll
        )

        val driver = drivers[0]
        // Target was clamped to maxCanvasScroll (500f)
        assertEquals(500f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        // Step 1: step to 400f (consumed)
        driver.step(400f)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
        assertTrue(coordinator.isAnimationActive)

        // Step 2: step to 450f (reaches physical boundary)
        driver.step(450f)
        assertEquals(450f, coordinator.totalScroll, 0.001f)

        // Step 3: step to 500f (blocked at 450f -> unconsumed > 0, consumed == 0)
        driver.step(500f)
        // Coordinator detects boundary collision and finishes early without faking trajectory!
        assertFalse("Coordinator must finish when hitting boundary", coordinator.isAnimationActive)
        assertEquals("Total scroll must reflect true physical boundary 450f", 450f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_Replacement_StartsMovingTowardNewTargetWithoutHitchingOrRelativeAccumulation() {
        // Point 9.2: Replacing an animation immediately starts moving toward the new target
        // without hitching or relative-delta accumulation.
        val coordinator = ScribeScrollCoordinator(totalScroll = 200f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Op 1: target 400f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 400f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver1 = drivers[0]
        driver1.step(280f)
        assertEquals(280f, coordinator.totalScroll, 0.001f)

        // Replace mid-flight with Op 2: target 650f
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 650f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        val driver2 = drivers[1]
        // Driver 2 must start precisely from current combined scroll (280f), NOT restarting at 200f or adding relative deltas
        assertEquals("New driver must start from current scroll position without hitch", 280f, driver2.fromVal, 0.001f)
        assertEquals("New driver destination must match target", 650f, driver2.toVal, 0.001f)

        driver2.step(500f)
        assertEquals(500f, coordinator.totalScroll, 0.001f)
        driver2.step(650f)
        driver2.complete()

        assertEquals("Total scroll must settle exactly at 650f", 650f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
    }

    @Test
    fun testProductionCoordinator_UserTouchDuringMovement_CancelsOperationAndDropsLateCallbacks() {
        // Point 9.7: User touch during automatic movement immediately cancels the active operation
        // and prevents late callbacks from running.
        val coordinator = ScribeScrollCoordinator(
            totalScroll = 300f,
            temporaryDisplacement = 120f,
            caretLine = 10,
            caretCol = 4
        )
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 500f,
            reason = ScrollAnimationReason.CONTRACTION_RESTORATION
        )
        val driver = drivers[0]
        driver.step(400f)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
        assertTrue(coordinator.isAnimationActive)

        // User touches screen (ACTION_DOWN): cancels animation and invalidates compensation
        productionCoordinator.cancelActiveAnimation("User touch")
        coordinator.invalidateCompensation("User touch")

        assertFalse("Coordinator must be inactive immediately upon touch", coordinator.isAnimationActive)
        assertEquals("Displacement must be zeroed immediately upon touch", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)

        // Late callbacks arrive from driver after cancel
        driver.step(450f)
        assertEquals("Late step callback must be ignored", 400f, coordinator.totalScroll, 0.001f)

        driver.complete()
        // Must not revive coordinator or trigger completion logic
        assertFalse("Late complete callback must be ignored", coordinator.isAnimationActive)
        assertEquals(400f, coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testProductionCoordinator_RapidTogglingBetweenExpandedAndCollapsed_NoDriftOrFighting() {
        // Point 9.8: Rapid toggling between expanded and collapsed states does not accumulate drift or fight itself.
        val coordinator = ScribeScrollCoordinator(totalScroll = 1000f)
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // Rapid toggle 1: expand (target 1200f)
        productionCoordinator.startAuthoritativeAnimation(1200f, ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1050f)

        // Rapid toggle 2: collapse (target 1000f)
        productionCoordinator.startAuthoritativeAnimation(1000f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        drivers.last().step(1020f)

        // Rapid toggle 3: expand again (target 1200f)
        productionCoordinator.startAuthoritativeAnimation(1200f, ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1100f)

        // Rapid toggle 4: collapse again (target 1000f)
        productionCoordinator.startAuthoritativeAnimation(1000f, ScrollAnimationReason.CONTRACTION_RESTORATION)
        drivers.last().step(1040f)

        // Rapid toggle 5: expand again (target 1200f) and let it settle
        productionCoordinator.startAuthoritativeAnimation(1200f, ScrollAnimationReason.CARET_VISIBILITY)
        drivers.last().step(1200f)
        drivers.last().complete()

        assertEquals("Coordinator must settle cleanly at 1200f with zero drift", 1200f, coordinator.totalScroll, 0.001f)
        assertFalse(coordinator.isAnimationActive)
        assertEquals("Operation ID must have advanced for each toggle without leaking animators", 5L, productionCoordinator.activeOperationId)
    }

    @Test
    fun testProductionCoordinator_CaretMoveDuringExpansion_InvalidatesCompensationAndPreventsDownwardRestoration() {
        // Point 9.9: Moving the caret during expansion invalidates compensation and prevents downward restoration on collapse.
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val headerHeight = 200
        val rowHeight = 40f

        val coordinator = ScribeScrollCoordinator(
            totalScroll = 1000f,
            temporaryDisplacement = 0f,
            headerHeight = headerHeight,
            density = density
        )
        coordinator.isExpansionTransitionActive = true

        // Expansion pushes canvas upward by 180px
        coordinator.requestTarget(1180f, ScrollAnimationReason.CARET_VISIBILITY)
        coordinator.caretLine = 25
        coordinator.caretCol = 10
        coordinator.applyStep(1180f)
        coordinator.finishAnimation()

        assertEquals(1180f, coordinator.totalScroll, 0.001f)
        assertEquals(180f, coordinator.temporaryDisplacement, 0.001f)

        // User taps/moves caret to line 12 while expanded
        coordinator.invalidateIfCaretChanged(newLine = 12, newCol = 0, newSelected = false)

        assertEquals("Displacement must be wiped to 0f on caret movement", 0f, coordinator.temporaryDisplacement, 0.001f)
        assertEquals(-1, coordinator.caretLine)

        // Surface now collapses back
        val caretDocBottom = (12 + 1) * rowHeight // line 12 bottom = 520px
        val screenBottom = headerHeight + caretDocBottom - coordinator.totalScroll // 200 + 520 - 1180 = -460px
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = screenBottom,
            caretScreenTop = screenBottom - rowHeight,
            viewportHeight = 2000,
            density = density,
            totalScroll = coordinator.totalScroll,
            temporaryDisplacement = coordinator.temporaryDisplacement, // 0f
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val restorationResult = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)

        assertFalse("Must NOT scroll downward when compensation was invalidated", restorationResult.shouldScroll)
        assertEquals(0f, restorationResult.deltaY, 0.001f)
    }

    @Test
    fun testProductionCoordinator_AfterTouchCancel_NextAnimationAppliesStepsSuccessfully() {
        // Verifies that after a user touches the screen (cancelling an in-flight animation),
        // the subsequent automatic animation (e.g. typing or keyboard/drawer expansion)
        // maintains matching operation IDs and applies update steps without being dropped.
        val coordinator = ScribeScrollCoordinator(totalScroll = 100f)
        coordinator.onScrollBy = { delta -> 0f } // all delta consumed
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        // 1. Initial animation runs partially
        productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 300f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        drivers.last().step(180f)
        assertEquals(180f, coordinator.totalScroll, 0.001f)

        // 2. User touches screen: cancels animation
        productionCoordinator.cancelActiveAnimation("User touch down")
        assertFalse("Coordinator must be inactive after touch cancel", coordinator.isAnimationActive)

        // 3. User types or accessory drawer expands: initiates new animation
        val started = productionCoordinator.startAuthoritativeAnimation(
            targetTotalScroll = 450f,
            reason = ScrollAnimationReason.CARET_VISIBILITY
        )
        assertTrue("New animation must start successfully", started)
        assertTrue("Coordinator must be active", coordinator.isAnimationActive)
        assertEquals(productionCoordinator.activeOperationId, coordinator.activeOperationId)

        // 4. Critical verification: Step ticks must NOT be rejected by operation ID mismatch!
        val activeDriver = drivers.last()
        activeDriver.step(250f)
        assertEquals("Step must be applied after touch cancel", 250f, coordinator.totalScroll, 0.001f)

        activeDriver.step(450f)
        assertEquals("Final step must reach target", 450f, coordinator.totalScroll, 0.001f)
        activeDriver.complete()
        assertFalse(coordinator.isAnimationActive)
    }

    @Test
    fun testProductionCoordinator_RetargetAuthoritativeAnimation_SmoothlyRetargetsWithoutNewDriver() {
        val coordinator = ScribeScrollCoordinator(totalScroll = 500f)
        coordinator.onScrollBy = { 0f }
        val drivers = mutableListOf<TestAnimationDriver>()
        val productionCoordinator = ProductionScrollAnimationCoordinator(
            scrollCoordinator = coordinator,
            animationDriverFactory = {
                val d = TestAnimationDriver()
                drivers.add(d)
                d
            }
        )

        productionCoordinator.startAuthoritativeAnimation(600f, ScrollAnimationReason.CARET_VISIBILITY)
        assertEquals(1, drivers.size)
        val driver = drivers[0]
        driver.step(550f)
        assertEquals(550f, coordinator.totalScroll, 0.001f)

        // Retarget in-flight to 750f
        val retargeted = productionCoordinator.retargetAuthoritativeAnimation(750f, ScrollAnimationReason.CARET_VISIBILITY)
        assertTrue(retargeted)
        assertEquals(1, drivers.size) // No extra driver instantiated!
        assertEquals(750f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)

        driver.step(750f)
        assertEquals(750f, coordinator.totalScroll, 0.001f)
        driver.complete()
        assertFalse(coordinator.isAnimationActive)
    }

}
