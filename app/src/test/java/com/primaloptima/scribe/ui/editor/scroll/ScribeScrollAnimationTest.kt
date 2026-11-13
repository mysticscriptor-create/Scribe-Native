package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.abs
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

    /**
     * Deterministic two-layer physics harness driving the real production ScribeCanvasGlideAnimator,
     * ScribeScrollCoordinator, ScribeScrollPolicy.allocateScrollDelta, and scoped transitional
     * step resolvers (resolveOuterScrollStep / resolveInnerScrollStep).
     */
    private class DeletionPhysicsHarness(
        val headerHeight: Int = 200,
        val usableViewportHeight: Int = 900,
        val breathingMarginPx: Float = 60f,
        initialLayoutHeight: Int,
        initialScrollD: Int,
        initialOffsetY: Int
    ) {
        var layoutHeight: Int = initialLayoutHeight
        var scrollD: Int = initialScrollD
        var offsetY: Int = initialOffsetY
        var outerResidualPx: Float = 0f
        var editorResidualPx: Float = 0f
        var lastClamped: Boolean = false
        var isDeletionActive: Boolean = false
        var isDeletionResetRetractionActive: Boolean = false
        var innerRetractionDebtActive: Boolean = false
        var outerRetractionDebtActive: Boolean = false
        var isTailAnchored: Boolean = false

        fun updateRetractionDebtFromCurrentPosition(armIfOverscrolled: Boolean) {
            val maxD = ScribeScrollPolicy.calculateMaxHeaderScroll(headerHeight, maxCanvasScroll)
            val safeMaxY = (maxCanvasScroll - headerHeight).coerceAtLeast(0)
            val editorOverscrolled = scrollD >= headerHeight && offsetY > safeMaxY
            val outerOverscrolled = scrollD > maxD
            if (armIfOverscrolled) {
                innerRetractionDebtActive = editorOverscrolled
                outerRetractionDebtActive = outerOverscrolled
            } else {
                if (innerRetractionDebtActive && !editorOverscrolled) {
                    innerRetractionDebtActive = false
                }
                if (outerRetractionDebtActive && !outerOverscrolled) {
                    outerRetractionDebtActive = false
                }
            }
        }
        var newCaretDocY: Float = initialLayoutHeight.toFloat()
        var settledReasonHistory = mutableListOf<ScrollAnimationReason?>()
        var pendingFrameRunnable: Runnable? = null

        val maxCanvasScroll: Int
            get() = ScribeScrollPolicy.calculateMaxCanvasScroll(
                headerHeight = headerHeight,
                layoutHeight = layoutHeight,
                viewportHeight = usableViewportHeight,
                bottomSpace = breathingMarginPx.toInt(),
                isTextEmpty = layoutHeight <= 0
            )

        val currentTotalScroll: Int
            get() = scrollD + offsetY

        val coordinator = ScribeScrollCoordinator(
            totalScroll = (initialScrollD + initialOffsetY).toFloat(),
            headerHeight = headerHeight,
            density = 2.5f
        )

        var animator: ScribeCanvasGlideAnimator = ScribeCanvasGlideAnimator(
            scrollCoordinator = coordinator,
            postOnAnimationAction = { r -> pendingFrameRunnable = r },
            removeCallbacksAction = { r -> if (pendingFrameRunnable === r) pendingFrameRunnable = null },
            deletionBoundaryProvider = {
                val maxBound = maxCanvasScroll.toFloat()
                val tailOrCaret = if (isTailAnchored) maxOf(layoutHeight.toFloat(), newCaretDocY) else newCaretDocY
                (headerHeight + tailOrCaret + breathingMarginPx - usableViewportHeight).coerceIn(0f, maxBound)
            },
            onSettledCallback = { reason ->
                settledReasonHistory.add(reason)
                if (isDeletionActive) {
                    val validation = ScribeScrollDeletionPolicy.validateSettledDeletionSafety(
                        currentTotalScroll = currentTotalScroll.toFloat(),
                        headerHeight = headerHeight,
                        caretDocBottom = newCaretDocY,
                        layoutHeight = layoutHeight.toFloat(),
                        usableViewportHeight = usableViewportHeight,
                        breathingMarginPx = breathingMarginPx,
                        maxCanvasScroll = maxCanvasScroll,
                        isTailAnchored = isTailAnchored
                    )
                    if (!validation.isSafe && reason != ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION &&
                        abs(validation.correctiveTargetScroll - currentTotalScroll.toFloat()) >= 1f
                    ) {
                        animator.startAuthoritativeAnimation(
                            currentScroll = currentTotalScroll.toFloat(),
                            targetTotalScroll = validation.correctiveTargetScroll,
                            reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
                            maxCanvasScroll = maxCanvasScroll,
                            forceRetarget = true,
                            onEnd = { isDeletionActive = false }
                        )
                    }
                }
            },
            isDeletionSafetyActiveProvider = { isDeletionActive },
            onCancelledCallback = { reason, wasDeletionSafetyActive ->
                if (wasDeletionSafetyActive && reason == "User touch") {
                    updateRetractionDebtFromCurrentPosition(armIfOverscrolled = true)
                }
            }
        )

        init {
            coordinator.wasLastStepClampedByBoundary = { lastClamped }
            coordinator.onScrollBy = { dy -> scrollBy(dy) }
        }

        fun scrollBy(dy: Float): Float {
            lastClamped = false
            val editorOffsetBefore = offsetY
            val editorMaxBefore = if (scrollD < headerHeight) 0 else (maxCanvasScroll - headerHeight).coerceAtLeast(0)
            val editorResidualBefore = editorResidualPx
            val physicalBefore = (scrollD + offsetY).toFloat()
            val maxBound = maxCanvasScroll
            val allowInnerRetract = isDeletionActive || isDeletionResetRetractionActive || innerRetractionDebtActive
            val allowOuterRetract = isDeletionActive || isDeletionResetRetractionActive || outerRetractionDebtActive
            val clampInnerLowerToNewMax = isDeletionActive || isDeletionResetRetractionActive
            val clampOuterLowerToNewMax = isDeletionActive || isDeletionResetRetractionActive

            val allocation = ScribeScrollPolicy.allocateScrollDelta(
                deltaY = dy,
                headerHeight = headerHeight,
                scrollD = (scrollD.toFloat() + outerResidualPx).coerceAtLeast(0f),
                editorOffsetY = offsetY,
                maxCanvasScroll = maxBound
            )

            var outerHit = false
            var innerHit = false

            fun applyInner(delta: Float): Boolean {
                if (scrollD < headerHeight) {
                    editorResidualPx = 0f
                    return delta != 0f
                }
                val safeMaxY = (maxBound - headerHeight).coerceAtLeast(0)
                val step = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
                    currentOffsetY = offsetY,
                    currentResidualPx = editorResidualPx,
                    innerDelta = delta,
                    maxAllowedY = safeMaxY,
                    isDeletionTransactionActive = allowInnerRetract,
                    clampLowerToNewMax = clampInnerLowerToNewMax
                )
                offsetY = step.nextIntOffset
                editorResidualPx = step.nextResidualPx
                return step.hitBoundary
            }

            fun applyOuter(delta: Float) {
                if (delta < 0f && offsetY > 0) {
                    outerHit = true
                    return
                }
                val step = ScribeScrollDeletionPolicy.resolveOuterScrollStep(
                    currentScrollD = scrollD,
                    currentResidualPx = outerResidualPx,
                    outerDelta = delta,
                    headerHeight = headerHeight,
                    maxCanvasScroll = maxBound,
                    isDeletionTransactionActive = allowOuterRetract,
                    clampLowerToNewMax = clampOuterLowerToNewMax
                )
                scrollD = step.nextIntOffset
                outerResidualPx = step.nextResidualPx
                outerHit = step.hitBoundary
                if (scrollD < headerHeight) {
                    editorResidualPx = 0f
                }
            }

            if (dy < 0f) {
                var effectiveOuterDelta = allocation.outerDelta
                if (allocation.innerDelta != 0f) {
                    val innerProjectedAfter = editorOffsetBefore.toFloat() + editorResidualBefore + allocation.innerDelta
                    innerHit = applyInner(allocation.innerDelta)
                    val innerReachedZeroHandoff = offsetY == 0 && scrollD > 0 &&
                        (!allowInnerRetract || editorMaxBefore == 0 || !clampInnerLowerToNewMax)
                    if (innerReachedZeroHandoff) {
                        if (innerProjectedAfter < 0f) {
                            effectiveOuterDelta += innerProjectedAfter
                        } else if (editorResidualPx > 0f && effectiveOuterDelta < 0f) {
                            val combinedOuter = effectiveOuterDelta + editorResidualPx
                            if (combinedOuter <= 0f) {
                                editorResidualPx = 0f
                                effectiveOuterDelta = combinedOuter
                            } else {
                                editorResidualPx = combinedOuter
                                effectiveOuterDelta = 0f
                            }
                        }
                    }
                } else if (editorOffsetBefore == 0 && editorResidualPx > 0f && scrollD > 0 && effectiveOuterDelta < 0f) {
                    val combinedOuter = effectiveOuterDelta + editorResidualPx
                    if (combinedOuter <= 0f) {
                        editorResidualPx = 0f
                        effectiveOuterDelta = combinedOuter
                    } else {
                        editorResidualPx = combinedOuter
                        effectiveOuterDelta = 0f
                    }
                }
                if (effectiveOuterDelta != 0f) applyOuter(effectiveOuterDelta)
            } else {
                if (allocation.outerDelta != 0f) applyOuter(allocation.outerDelta)
                if (allocation.innerDelta != 0f) innerHit = applyInner(allocation.innerDelta)
            }

            updateRetractionDebtFromCurrentPosition(armIfOverscrolled = false)

            if (scrollD < headerHeight) {
                assertEquals("1-DOF violated: editor offsetY must be 0 whenever scrollD < headerHeight", 0, offsetY)
            }

            val physicalAfter = (scrollD + offsetY).toFloat()
            val actualMoved = physicalAfter - physicalBefore
            val allocationBlocked = (dy > 0f && allocation.unconsumedDelta > 0.05f) ||
                (dy < 0f && allocation.unconsumedDelta < -0.05f)
            val innerHandoffToOuter = dy < 0f &&
                scrollD > 0 &&
                (editorMaxBefore == 0 || !(allowInnerRetract && clampInnerLowerToNewMax && editorOffsetBefore > editorMaxBefore))
            val outerHandoffToInner = dy > 0f && maxBound > headerHeight && allocation.innerDelta != 0f
            lastClamped = (outerHit && !outerHandoffToInner) || (innerHit && !innerHandoffToOuter) || allocationBlocked

            return dy - actualMoved
        }

        fun runUntilSettled(maxFrames: Int = 180, dtSec: Float = 0.016f): List<Int> {
            val trace = mutableListOf<Int>()
            var frames = 0
            while (animator.isGlideRunning && frames < maxFrames) {
                animator.advanceFrame(dtSec)
                trace.add(currentTotalScroll)
                frames++
            }
            return trace
        }
    }

    @Test
    fun testPhysicsDeletion_MultipleTrailingEmptyLines_KeepsCaretAboveBreathingMargin() {
        // Scenario 1: Deleting multiple trailing empty lines when scrolled to bottom
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1720,
            initialScrollD = 200,
            initialOffsetY = 960 // pre-delete maxCanvasScroll = 1160
        )
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 1720f

        val snapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 1L,
            isSelection = false,
            selectionStartLine = 42,
            selectionEndLine = 42,
            preEditDocLineCount = 43,
            preEditDocHeight = 2000f,
            preEditScrollY = 1160f,
            preEditCaretScreenY = 840f,
            wasAtTail = true,
            affectsDocumentTail = true,
            preEditLastNonBlankLine = 40
        )
        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = 41,
            newDocHeight = 1920f,
            usableViewportHeight = 900f,
            breathingMarginPx = 60f,
            isTextEmpty = false,
            isPostEditCaretAtLogicalTail = true
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR, intent)

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = 1160f,
            headerHeight = 200f,
            newDocHeight = 1720f,
            newCaretDocY = 1720f,
            preEditCaretScreenY = 840f,
            usableViewportHeight = 900f,
            breathingMarginPx = 60f,
            maxCanvasScroll = harness.maxCanvasScroll.toFloat()
        )
        assertEquals(1080f, target, 0.001f)

        val started = harness.animator.startAuthoritativeAnimation(
            currentScroll = 1160f,
            targetTotalScroll = target,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = {
                if (!harness.animator.isGlideRunning && !harness.coordinator.isAnimationActive) {
                    harness.isDeletionActive = false
                }
            }
        )
        assertTrue(started)
        val trace = harness.runUntilSettled()
        assertTrue("First frame must retract gradually without snapping straight to 1080", trace.first() > 1100)
        assertFalse(harness.animator.isGlideRunning)
        assertFalse(harness.isDeletionActive)
        assertEquals("Final totalScroll must land at 1080 without 2*deltaH double-subtraction", 1080, harness.currentTotalScroll)

        val caretScreenBottom = harness.headerHeight + harness.newCaretDocY - harness.currentTotalScroll
        assertTrue(
            "Caret must stay strictly at or above breathing margin ($caretScreenBottom <= 840)",
            caretScreenBottom <= 900f - 60f + 0.5f
        )
    }

    @Test
    fun testPhysicsDeletion_EntireLastParagraph_PreventsScrollBehindBreathingMarginAndDock() {
        // Scenario 2: Deleting whole last paragraph shrinks doc by 320px (> breathingMarginPx=60px).
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1600,
            initialScrollD = 200,
            initialOffsetY = 1080 // pre-delete totalScroll = 1280
        )
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 1600f

        harness.animator.startAuthoritativeAnimation(
            currentScroll = 1280f,
            targetTotalScroll = 960f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = {
                if (!harness.animator.isGlideRunning && !harness.coordinator.isAnimationActive) {
                    harness.isDeletionActive = false
                }
            }
        )
        val trace = harness.runUntilSettled()
        assertTrue("Frame 1 must not snap all 320px at once", trace.first() > 1150)
        assertEquals(960, harness.currentTotalScroll)
        val docBottomScreen = harness.headerHeight + harness.layoutHeight - harness.currentTotalScroll
        assertEquals("Remaining last line must settle right at top of breathing margin (840px)", 840, docBottomScreen)
        assertFalse(harness.isDeletionActive)
    }

    @Test
    fun testPhysicsDeletion_SelectionInFinalParagraph_SettlesAboveBreathingMargin() {
        // Scenario 3: Partial text selection deletion inside the final paragraph
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1500,
            initialScrollD = 200,
            initialOffsetY = 780 // pre-delete totalScroll = 980
        )
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 1480f

        harness.animator.startAuthoritativeAnimation(
            currentScroll = 980f,
            targetTotalScroll = 860f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { harness.isDeletionActive = false }
        )
        harness.runUntilSettled()
        assertEquals(860, harness.currentTotalScroll)
        val caretScreenBottom = harness.headerHeight + harness.newCaretDocY - harness.currentTotalScroll
        assertTrue(caretScreenBottom <= 840f + 0.5f)
    }

    @Test
    fun testPhysicsDeletion_MultiCharAndComposingReplaceBatch_PreservesEarliestSnapshotAndSettlesSafely() {
        // Scenario 6: Multi-character (deleteSurroundingText) / composing-text batch deletion
        val firstBatchSnapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = 10L,
            isSelection = false,
            selectionStartLine = 19,
            selectionEndLine = 19,
            preEditDocLineCount = 22,
            preEditDocHeight = 1400f,
            preEditScrollY = 560f,
            preEditCaretScreenY = 700f,
            wasAtTail = true,
            affectsDocumentTail = ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(19, 21),
            preEditLastNonBlankLine = 21
        )
        val mergedSnapshot = firstBatchSnapshot.copy(
            affectsDocumentTail = firstBatchSnapshot.affectsDocumentTail ||
                ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(21, firstBatchSnapshot.preEditLastNonBlankLine)
        )
        assertTrue(mergedSnapshot.affectsDocumentTail)
        assertEquals(560f, mergedSnapshot.preEditScrollY, 0.001f)

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = mergedSnapshot,
            newDocLineCount = 20,
            newDocHeight = 1280f,
            usableViewportHeight = 900f,
            breathingMarginPx = 60f,
            isTextEmpty = false,
            isPostEditCaretAtLogicalTail = true
        )
        assertEquals(ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR, intent)

        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1080,
            initialScrollD = 200,
            initialOffsetY = 360
        )
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 1080f

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = 560f,
            headerHeight = 200f,
            newDocHeight = 1080f,
            newCaretDocY = 1080f,
            preEditCaretScreenY = 700f,
            usableViewportHeight = 900f,
            breathingMarginPx = 60f,
            maxCanvasScroll = harness.maxCanvasScroll.toFloat()
        )
        harness.animator.startAuthoritativeAnimation(
            currentScroll = 560f,
            targetTotalScroll = target,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { harness.isDeletionActive = false }
        )
        harness.runUntilSettled()
        assertEquals(440, harness.currentTotalScroll)
        assertEquals(440f, harness.coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testPhysicsDeletion_StepByStepRetraction_NoFrame1SnapAndExactPhysicalAccounting() {
        // Scenario 8c: Maximum scroll drops from 700 to 640 (scrollD=200, offsetY=500 -> new safeMaxY=440).
        // Requesting -3px moves 500 -> 497 (NOT -60px -> 440!), coordinator tracks rendered scroll exactly.
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1340,
            initialScrollD = 200,
            initialOffsetY = 500
        )
        harness.isDeletionActive = true
        assertTrue(harness.coordinator.requestTarget(640f, ScrollAnimationReason.DELETION_ANCHOR, maxCanvasScroll = 640))

        harness.coordinator.applyStep(697f)
        assertEquals("Frame 1 must move by -3px (500 -> 497), NOT snap 60px to 440!", 497, harness.offsetY)
        assertEquals(697, harness.currentTotalScroll)
        assertEquals(697f, harness.coordinator.totalScroll, 0.001f)
        assertFalse(harness.coordinator.didLastStepClampAtBoundary)
    }

    @Test
    fun testPhysicsDeletion_MaxCanvasScrollBelowHeaderHeight_TwoLayerSmoothRetractionAndStrict1DOF() {
        // Scenario 9: maxCanvasScroll_new < headerHeight: drains offsetY 60 -> 0 FIRST, then scrollD 200 -> 120.
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 760,
            initialScrollD = 200,
            initialOffsetY = 60
        )
        assertEquals(120, harness.maxCanvasScroll)
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 760f

        harness.animator.startAuthoritativeAnimation(
            currentScroll = 260f,
            targetTotalScroll = 120f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { harness.isDeletionActive = false }
        )

        val offsetHistory = mutableListOf<Int>()
        val scrollDHistory = mutableListOf<Int>()
        var frames = 0
        while (harness.animator.isGlideRunning && frames < 180) {
            harness.animator.advanceFrame(0.016f)
            offsetHistory.add(harness.offsetY)
            scrollDHistory.add(harness.scrollD)
            frames++
        }

        assertTrue("First frame must keep offsetY > 0 while retracting smoothly", offsetHistory.first() > 40)
        assertEquals("First frame must keep scrollD at 200 while offsetY drains", 200, scrollDHistory.first())
        assertFalse(harness.animator.isGlideRunning)
        assertEquals(0, harness.offsetY)
        assertEquals(120, harness.scrollD)
        assertEquals(120, harness.currentTotalScroll)
        assertTrue("All scrollD steps must remain within [120, 200]", scrollDHistory.all { it in 120..200 })
    }

    @Test
    fun testGlideAnimator_RetargetedSafetyAndCorrectiveCompletionLifecycle() {
        // Scenario 10a: In-flight retarget keeps deletion boundary enforced via isDeletionSafetyActiveProvider
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400,
            initialScrollD = 200,
            initialOffsetY = 700
        )
        harness.isDeletionActive = true
        harness.isTailAnchored = true
        harness.newCaretDocY = 1400f

        var clearCount = 0
        harness.animator.startAuthoritativeAnimation(
            currentScroll = 900f,
            targetTotalScroll = 760f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = {
                if (!harness.animator.isGlideRunning && !harness.coordinator.isAnimationActive) {
                    clearCount++
                    harness.isDeletionActive = false
                }
            }
        )
        harness.animator.advanceFrame(0.016f)
        harness.animator.startAuthoritativeAnimation(
            currentScroll = harness.currentTotalScroll.toFloat(),
            targetTotalScroll = 500f,
            reason = ScrollAnimationReason.CARET_VISIBILITY,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true
        )
        harness.runUntilSettled()
        assertEquals("Retargeted glide must still protect 24dp breathing margin (minSafe=760)", 760, harness.currentTotalScroll)
        assertEquals(1, clearCount)
        assertFalse(harness.isDeletionActive)

        // Scenario 10b: onSettledCallback triggers SAFE_BOUNDARY_CORRECTION.
        // Superseded DELETION_ANCHOR onEnd MUST NOT wipe deletionSafety until correction completes!
        var deletionSafety = true
        var activeInSettledWhenLaunched = false
        var finalClearedAfterCorrection = false
        val coord2 = ScribeScrollCoordinator(totalScroll = 680f)
        coord2.onScrollBy = { 0f }
        lateinit var anim2: ScribeCanvasGlideAnimator
        anim2 = ScribeCanvasGlideAnimator(
            scrollCoordinator = coord2,
            postOnAnimationAction = {},
            removeCallbacksAction = {},
            deletionBoundaryProvider = { 650f },
            onSettledCallback = { reason ->
                if (reason == ScrollAnimationReason.DELETION_ANCHOR) {
                    anim2.startAuthoritativeAnimation(
                        currentScroll = coord2.totalScroll,
                        targetTotalScroll = 710f,
                        reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
                        maxCanvasScroll = 800,
                        forceRetarget = true,
                        onEnd = {
                            finalClearedAfterCorrection = true
                            deletionSafety = false
                        }
                    )
                    activeInSettledWhenLaunched = deletionSafety
                }
            },
            isDeletionSafetyActiveProvider = { deletionSafety }
        )
        anim2.startAuthoritativeAnimation(
            currentScroll = 680f,
            targetTotalScroll = 650f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = 800,
            forceRetarget = true,
            onEnd = {
                deletionSafety = false
            }
        )
        var guard = 0
        while (anim2.isGlideRunning && anim2.activeGlideReason == ScrollAnimationReason.DELETION_ANCHOR && guard < 180) {
            anim2.advanceFrame(0.016f)
            guard++
        }
        assertTrue("Deletion safety must remain TRUE immediately after corrective animation launches", activeInSettledWhenLaunched)
        assertTrue("Deletion safety must still be TRUE while SAFE_BOUNDARY_CORRECTION runs", deletionSafety)
        assertFalse(finalClearedAfterCorrection)

        while (anim2.isGlideRunning && guard < 360) {
            anim2.advanceFrame(0.016f)
            guard++
        }
        assertTrue("Deletion safety cleared cleanly once SAFE_BOUNDARY_CORRECTION settled", finalClearedAfterCorrection)
        assertFalse(deletionSafety)
        assertEquals(710f, coord2.totalScroll, 0.5f)
    }

    @Test
    fun testGlideAnimator_DeleteAllContent_IdempotentResetToTopReachesZeroCleanly() {
        // Scenario 12: Empty document RESET_TOP retracts both layers to 0 and skips duplicate RESET_TOP request
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 0,
            initialScrollD = 200,
            initialOffsetY = 300
        )
        harness.isDeletionActive = false
        assertTrue(
            harness.animator.startAuthoritativeAnimation(
                currentScroll = 500f,
                targetTotalScroll = 0f,
                reason = ScrollAnimationReason.RESET_TOP,
                maxCanvasScroll = harness.maxCanvasScroll,
                forceRetarget = true
            )
        )
        assertFalse(
            harness.animator.startAuthoritativeAnimation(
                currentScroll = 500f,
                targetTotalScroll = 0f,
                reason = ScrollAnimationReason.RESET_TOP,
                maxCanvasScroll = harness.maxCanvasScroll,
                forceRetarget = false
            )
        )
        harness.runUntilSettled()
        assertEquals(0, harness.offsetY)
        assertEquals(0, harness.scrollD)
        assertEquals(0f, harness.coordinator.totalScroll, 0.001f)
    }

    @Test
    fun testGlideAnimator_UserTouchCancelsDeletionGlide_DropsLateFramesAndPreservesUserPosition() {
        // Scenario 13: User touch cancels active deletion glide mid-flight; late ticks preserve user scroll.
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400,
            initialScrollD = 200,
            initialOffsetY = 640
        )
        harness.isDeletionActive = true
        harness.animator.startAuthoritativeAnimation(
            currentScroll = 840f,
            targetTotalScroll = 760f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { harness.isDeletionActive = false }
        )

        repeat(4) { harness.animator.advanceFrame(0.016f) }
        val midScroll = harness.currentTotalScroll
        assertTrue(midScroll in 761..839)
        harness.settledReasonHistory.clear()

        harness.animator.cancel("User touch")
        assertFalse(harness.animator.isGlideRunning)
        assertFalse(harness.coordinator.isAnimationActive)
        assertFalse(harness.isDeletionActive)

        val unconsumed = harness.scrollBy(-50f)
        assertEquals(0f, unconsumed, 0.5f)
        val userSettledPos = harness.currentTotalScroll
        assertEquals(midScroll - 50, userSettledPos)

        harness.animator.glideFrameRunnable.run()
        harness.animator.advanceFrame(0.016f)
        assertEquals("User scroll position must remain completely untouched", userSettledPos, harness.currentTotalScroll)
        assertTrue("onSettledCallback must not fire for cancelled animation", harness.settledReasonHistory.isEmpty())
    }

    @Test
    fun testGlideAnimator_DeletionOriginatedResetToTop_RetractsSmoothlyWithoutFrame1Snap() {
        // Deletion-originated RESET_TO_TOP keeps transitional retraction active across both layers
        // so Frame 1 moves gradually from 500 rather than snapping straight to 0.
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 0,
            initialScrollD = 200,
            initialOffsetY = 300
        )
        harness.isDeletionResetRetractionActive = true
        harness.animator.startAuthoritativeAnimation(
            currentScroll = 500f,
            targetTotalScroll = 0f,
            reason = ScrollAnimationReason.RESET_TOP,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = {
                if (!harness.animator.isGlideRunning && !harness.coordinator.isAnimationActive) {
                    harness.isDeletionResetRetractionActive = false
                }
            }
        )
        val trace = harness.runUntilSettled()
        assertTrue("Frame 1 of deletion RESET_TOP must retract smoothly without snapping to 0", trace.first() > 420)
        assertEquals(0, harness.offsetY)
        assertEquals(0, harness.scrollD)
        assertEquals(0, harness.currentTotalScroll)
        assertFalse(harness.isDeletionResetRetractionActive)
    }

    @Test
    fun testPhysicsDeletion_TouchCancelRetractionDebt_BlocksOverscrollAndClearsInsideBounds() {
        // Sub-case A: Inner retraction debt drains past new max smoothly and clears once <= safeMaxY,
        // while forward (+dy) overscroll is strictly blocked.
        val harness = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400, // maxCanvasScroll = 760, safeMaxY = 560
            initialScrollD = 200,
            initialOffsetY = 640
        )
        harness.isDeletionActive = true
        harness.animator.startAuthoritativeAnimation(
            currentScroll = 840f,
            targetTotalScroll = 760f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = harness.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { harness.isDeletionActive = false }
        )
        repeat(4) { harness.animator.advanceFrame(0.016f) }
        harness.animator.cancel("User touch")
        assertFalse(harness.isDeletionActive)
        assertTrue(harness.innerRetractionDebtActive)

        val beforeForward = harness.currentTotalScroll
        val forwardUnconsumed = harness.scrollBy(+20f)
        assertEquals("Forward drag into overscrolled space must be blocked", 20f, forwardUnconsumed, 0.001f)
        assertEquals(beforeForward, harness.currentTotalScroll)

        assertEquals(0f, harness.scrollBy(-50f), 0.001f)
        assertTrue("Debt stays active while still > safeMaxY", harness.innerRetractionDebtActive)

        val expectedAfterSecondDrag = harness.currentTotalScroll - 50
        assertEquals(0f, harness.scrollBy(-50f), 0.001f)
        assertEquals(expectedAfterSecondDrag, harness.currentTotalScroll)
        assertFalse("Debt clears automatically once offsetY <= safeMaxY", harness.innerRetractionDebtActive)

        // Sub-case B: Two-layer debt when maxCanvasScroll < headerHeight (120 < 200)
        val twoLayer = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 760, // maxCanvasScroll = 120
            initialScrollD = 200,
            initialOffsetY = 20
        )
        twoLayer.isDeletionActive = true
        twoLayer.animator.startAuthoritativeAnimation(
            currentScroll = 220f,
            targetTotalScroll = 120f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            maxCanvasScroll = twoLayer.maxCanvasScroll,
            forceRetarget = true,
            onEnd = { twoLayer.isDeletionActive = false }
        )
        twoLayer.animator.advanceFrame(0.016f)
        twoLayer.animator.cancel("User touch")
        assertTrue(twoLayer.innerRetractionDebtActive)
        assertTrue(twoLayer.outerRetractionDebtActive)
        val midPos = twoLayer.currentTotalScroll
        assertEquals(0f, twoLayer.scrollBy(-50f), 0.001f)
        assertEquals(midPos - 50, twoLayer.currentTotalScroll)
        assertEquals(0, twoLayer.offsetY)
        assertFalse(twoLayer.innerRetractionDebtActive)
        assertTrue(twoLayer.outerRetractionDebtActive)
    }

    @Test
    fun testPhysicsDeletion_FractionalResidualHandoff_ExactAcrossInnerOuterBoundaryBothDirections() {
        // Direction 1a: inner -> outer with negative inner residual (offsetY = 1, residual = -0.3f -> virtual 200.7)
        val innerToOuterNeg = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400,
            initialScrollD = 200,
            initialOffsetY = 1
        )
        innerToOuterNeg.editorResidualPx = -0.3f
        val unconsumed1 = innerToOuterNeg.scrollBy(-0.8f)
        assertEquals(0, innerToOuterNeg.offsetY)
        assertEquals(0f, innerToOuterNeg.editorResidualPx, 0.001f)
        assertEquals(200, innerToOuterNeg.scrollD)
        assertEquals(-0.1f, innerToOuterNeg.outerResidualPx, 0.001f)
        assertFalse("Handoff with negative inner residual must not report false boundary clamp", innerToOuterNeg.lastClamped)
        assertEquals(0.2f, unconsumed1, 0.001f) // moved integer -1px for -0.8f step

        innerToOuterNeg.scrollBy(-0.5f)
        assertEquals(199, innerToOuterNeg.scrollD)
        assertEquals(0.4f, innerToOuterNeg.outerResidualPx, 0.001f) // 199 + 0.4 = 199.4 == 200.7 - 0.8 - 0.5

        // Direction 1b: inner -> outer with positive residual left at offsetY = 0 (virtual 201.3 -> 200.3 -> 199.5)
        val innerToOuterPos = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400,
            initialScrollD = 200,
            initialOffsetY = 1
        )
        innerToOuterPos.editorResidualPx = 0.3f
        innerToOuterPos.scrollBy(-1.0f)
        assertEquals(0, innerToOuterPos.offsetY)
        assertEquals(0.3f, innerToOuterPos.editorResidualPx, 0.001f)
        innerToOuterPos.scrollBy(-0.8f)
        assertEquals(0f, innerToOuterPos.editorResidualPx, 0.001f)
        assertEquals(200, innerToOuterPos.scrollD)
        assertEquals(-0.5f, innerToOuterPos.outerResidualPx, 0.001f) // 200 - 0.5 = 199.5 == 201.3 - 1.8

        // Direction 2: outer -> inner crossing headerHeight = 200 with positive outer residual (199.3 -> 200.3 -> 200.8)
        val outerToInnerPos = DeletionPhysicsHarness(
            headerHeight = 200,
            usableViewportHeight = 900,
            breathingMarginPx = 60f,
            initialLayoutHeight = 1400,
            initialScrollD = 199,
            initialOffsetY = 0
        )
        outerToInnerPos.outerResidualPx = 0.3f
        outerToInnerPos.scrollBy(+1.0f)
        assertEquals(200, outerToInnerPos.scrollD)
        assertEquals(0f, outerToInnerPos.outerResidualPx, 0.001f)
        assertEquals(0, outerToInnerPos.offsetY)
        assertEquals(0.3f, outerToInnerPos.editorResidualPx, 0.001f)
        assertFalse(outerToInnerPos.lastClamped)

        outerToInnerPos.scrollBy(+0.5f)
        assertEquals(200, outerToInnerPos.scrollD)
        assertEquals(1, outerToInnerPos.offsetY)
        assertEquals(-0.2f, outerToInnerPos.editorResidualPx, 0.001f) // 200 + 1 - 0.2 = 200.8 == 199.3 + 1.5
    }
}
