package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite B1: State machine, safety revalidation (all 6 SafetyRevalidationResult variants),
 * atomic teardown-before-IDLE, PendingPostSafetyKeyboardEval, physical velocity read-back,
 * PhysicalScrollOrigin drift vs user origins, Dual ACTION_DOWN, and Mode-2 0L first-frame clock.
 *
 * Scope boundary note:
 * - Option-B mid-collapse cursor-animator renderer & EditorTextActionWindow gating complete in Phase 4.
 * - Incremental `(seamLine, 0)` insertion rebasing & Policy A/B internal mutation hooks complete in Phase 5.
 */
class ScribeGapCollapseControllerTest {

    private class ControllerHarness(
        private val quantizeScrollToInt: Boolean = false,
        initialScrollPx: Float = 0f
    ) {
        var scheduledFrameCallback: ((Long) -> Unit)? = null
        var cancelFrameCount = 0
        var invalidateCount = 0
        var refreshRateHz = 60f
        var nanoTime = 1_000_000_000L
        var simulatedScrollPx = initialScrollPx
        val mode2SettledEvents = mutableListOf<Pair<Long, Long>>()
        val externalDriftEvents = mutableListOf<Pair<Long, Long>>()
        val appliedUnifiedScrolls = mutableListOf<Float>()

        val controller: ScribeGapCollapseController = ScribeGapCollapseController(
            scheduleFrame = { cb -> scheduledFrameCallback = cb },
            cancelFrame = {
                cancelFrameCount++
                scheduledFrameCallback = null
            },
            refreshRateHzProvider = { refreshRateHz },
            nanoTimeProvider = { nanoTime },
            onInvalidateEditor = { invalidateCount++ },
            onMode2SettledToSafety = { gen, epoch -> mode2SettledEvents.add(gen to epoch) },
            onExternalDriftAbortToSafety = { gen, epoch -> externalDriftEvents.add(gen to epoch) },
            onApplyUnifiedScrollTo = { desired ->
                appliedUnifiedScrolls.add(desired)
                simulatedScrollPx = if (quantizeScrollToInt) {
                    kotlin.math.round(desired)
                } else {
                    desired
                }
            },
            currentActualScrollProvider = { simulatedScrollPx }
        )

        fun pumpMode2Frame(frameTimeNs: Long) {
            val cb = scheduledFrameCallback
            scheduledFrameCallback = null
            cb?.invoke(frameTimeNs)
        }
    }

    private fun makeViewportBounds(
        top: Float = 0f,
        bottom: Float = 900f
    ): UnobstructedViewportBounds = UnobstructedViewportBounds(
        headerBottomLocal = 0f,
        stickyBottomLocal = 0f,
        maxTopBarBottomLocal = top,
        minBottomBarTopLocal = bottom,
        windowVisibleBottomLocal = bottom,
        visibleEditorTop = top,
        visibleEditorBottom = bottom,
        usableHeight = (bottom - top).coerceAtLeast(0f),
        hasModalBlocker = false,
        isValid = bottom > top,
        floatingPillLocalRects = emptyList()
    )

    private fun makeLowerOnlyPlan(
        preScrollY: Int = 500,
        deltaGapPx: Float = 300f,
        minSafeScroll: Float = 350f,
        safeMax: Int = 900
    ): CollapsePlan {
        val bounds = makeViewportBounds()
        val safety = SafetyFloorAndBounds(
            safeMax = safeMax,
            minSafeScroll = minSafeScroll,
            safeMinPhysical = kotlin.math.ceil(minSafeScroll.toDouble()).toInt().coerceAtMost(safeMax),
            actualPhysicalScroll = preScrollY
        )
        return ScribeGapCollapsePolicy.planCollapse(
            deletionIntent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
            isSelection = true,
            isWordwrap = true,
            isWordwrapReady = true,
            isLayoutBusy = false,
            isComposing = false,
            isCursorAnimationRunning = false,
            hasRtlOrBidiAtSeam = false,
            isMergedPartialRowSeam = false,
            hasSurvivingUpper = true,
            hasSurvivingLower = true,
            deltaGapPx = deltaGapPx,
            visibilityConfidence = VisibilityConfidence.RESOLVED,
            isUpperVisible = false,
            isLowerVisible = true,
            isTouchedOnScreen = true,
            preScrollY = preScrollY,
            centeredTargetScroll = preScrollY.toFloat(),
            viewportBounds = bounds,
            safetyBounds = safety
        )
    }

    @Test
    fun testAllFiveGapCollapsePhases_Mode1Mode2PointerFreezeSafetyAndIdle() {
        val h = ControllerHarness()
        assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
        assertFalse(h.controller.isTransactionOwned)
        assertFalse(h.controller.shouldApplySplitRender)

        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 300f, minSafeScroll = 350f)
        val epoch = h.controller.beginCollapse(
            owningGen = 7L,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 12,
            seamDocY = 480f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, h.controller.phase)
        assertTrue(h.controller.isTransactionOwned)
        assertTrue(h.controller.shouldApplySplitRender)
        assertEquals(300f, h.controller.tauLower, 0.001f)

        h.controller.markMode1Started(7L, epoch, 500f)
        h.nanoTime = 1_016_000_000L
        h.controller.onMode1FrameCommitted(7L, epoch, 450f, boundaryHit = false)
        assertEquals(250f, h.controller.tauLower, 0.001f)

        // Freeze for pointer mid-Mode-1
        assertTrue(h.controller.freezeForPointer(7L))
        assertEquals(GapCollapsePhase.POINTER_FROZEN, h.controller.phase)
        assertTrue(h.controller.isResumableCollapse)
        assertTrue(h.controller.shouldApplySplitRender)

        // Resume from POINTER_FROZEN -> MODE_2_VISUAL_SETTLING
        val resumed = h.controller.resumeFromPointerFrozen(7L, 450f)
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, resumed)
        assertNotNull(h.scheduledFrameCallback)
        assertTrue(h.controller.shouldApplySplitRender)

        // Pump Mode 2 until settled -> FINAL_SAFETY_PENDING
        var tNs = 2_000_000_000L
        var guard = 0
        while (h.controller.phase == GapCollapsePhase.MODE_2_VISUAL_SETTLING && guard < 200) {
            tNs += 16_000_000L
            h.pumpMode2Frame(tNs)
            guard++
        }
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertFalse("Split rendering MUST disable immediately upon FINAL_SAFETY_PENDING", h.controller.shouldApplySplitRender)
        assertTrue("Controller still owns transaction in FINAL_SAFETY_PENDING", h.controller.isTransactionOwned)
        assertEquals(1, h.mode2SettledEvents.size)

        val claim = h.controller.beginTeardownLockAndConsumeEval(
            expectedGen = 7L,
            expectedSafetyEpoch = h.controller.safetyEpoch
        )
        assertNotNull(claim)
        assertTrue(h.controller.isTeardownLocked)
        assertFalse(h.controller.shouldApplySplitRender)

        h.controller.commitIdle()
        assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
        assertFalse(h.controller.isTransactionOwned)
        assertEquals(-1L, h.controller.owningDeletionGen)
    }

    @Test
    fun testMode2FirstFrameZeroClockHandling_UsesRefreshRateAndInvalidatesSynchronously() {
        val h = ControllerHarness()
        h.refreshRateHz = 120f
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 40f, minSafeScroll = 500f)
        // preScroll == safeMinPhysical == 500, requested == 460 -> effective == 500 (distance < 1f -> direct Mode 2)
        h.controller.beginCollapse(
            owningGen = 1L,
            plan = plan,
            seamLine = 5,
            seamFirstLowerRow = 5,
            seamDocY = 200f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)
        assertEquals(0L, h.controller.mode2State?.lastFrameTimeNs)
        val invBefore = h.invalidateCount

        // First frame arrives with arbitrary Choreographer timestamp: 0L sentinel must yield 1/120f (~8.33ms), NOT huge jump
        val firstChoreographerNs = 98_765_432_100L
        h.pumpMode2Frame(firstChoreographerNs)
        val stateAfterFirst = h.controller.mode2State!!
        assertEquals(firstChoreographerNs, stateAfterFirst.lastFrameTimeNs)
        assertEquals(1f / 120f, stateAfterFirst.elapsedTimeSec, 0.0005f)
        assertTrue("tauLower must shrink smoothly without instant collapse", h.controller.tauLower in 35f..39.99f)
        assertTrue("Synchronous invalidate must fire on Mode-2 visual tick", h.invalidateCount > invBefore)

        // Advance until settle and verify synchronous invalidate fires on final 0f tick too
        var t = firstChoreographerNs
        var ticks = 0
        while (h.controller.phase == GapCollapsePhase.MODE_2_VISUAL_SETTLING && ticks < 240) {
            t += 8_333_333L
            val beforeStepInv = h.invalidateCount
            h.pumpMode2Frame(t)
            assertTrue("Every tau-changing/settling frame must call editor.invalidate()", h.invalidateCount > beforeStepInv)
            ticks++
        }
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertEquals(0f, h.controller.tauLower, 0.001f)
    }

    @Test
    fun testObservedPhysicalVelocityReadBack_IgnoresUnconsumedSpringVelocityOnBoundaryClamp() {
        val h = ControllerHarness()
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 300f, minSafeScroll = 350f)
        val epoch = h.controller.beginCollapse(
            owningGen = 3L,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        h.controller.markMode1Started(3L, epoch, 500f)

        // Two smooth physical frames: 500 -> 470 -> 440 at ~16ms intervals (-1875 px/s)
        h.nanoTime = 1_016_000_000L
        h.controller.onMode1FrameCommitted(3L, epoch, 470f, boundaryHit = false)
        h.nanoTime = 1_032_000_000L
        h.controller.onMode1FrameCommitted(3L, epoch, 440f, boundaryHit = false)
        val validEma = h.controller.observedPhysicalVelocityEmaPxPerSec
        assertNotNull(validEma)
        assertTrue(validEma!! < 0f)

        // Boundary frame: physical scroll hits boundary at 440f (zero physical delta, boundaryHit = true)
        h.nanoTime = 1_048_000_000L
        h.controller.onMode1FrameCommitted(3L, epoch, 440f, boundaryHit = true)
        assertEquals(
            "Boundary-clamped frame must retain last valid pre-boundary physical velocity EMA",
            validEma,
            h.controller.observedPhysicalVelocityEmaPxPerSec!!,
            0.001f
        )

        val nextPhase = h.controller.onMode1SettledOrHandoff(3L, epoch, 440f, mode1WasStarted = true)
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, nextPhase)
        val v0 = h.controller.mode2State!!.progressVelocity
        assertTrue(v0.isFinite())
        assertTrue(v0 in 0f..ScribeGapCollapsePolicy.MODE2_DEFAULT_OMEGA_RAD_PER_SEC)
        assertTrue("Valid pre-boundary negative physical velocity must seed positive progressVelocity", v0 > 0f)
    }

    @Test
    fun testPhysicalScrollOrigin_UserOriginsTrackCleanly_ExternalMakeVisibleAbortsMode2() {
        val h = ControllerHarness()
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 80f, minSafeScroll = 500f)
        h.controller.beginCollapse(
            owningGen = 9L,
            plan = plan,
            seamLine = 8,
            seamFirstLowerRow = 8,
            seamDocY = 320f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)

        // All authorized user scroll origins update lastMode2KnownPhysicalScroll without aborting Mode 2
        assertFalse(h.controller.onViewportPhysicalScrollChanged(512f, PhysicalScrollOrigin.USER_CANVAS_DRAG))
        assertFalse(h.controller.onViewportPhysicalScrollChanged(524f, PhysicalScrollOrigin.USER_CANVAS_FLING))
        assertFalse(h.controller.onViewportPhysicalScrollChanged(530f, PhysicalScrollOrigin.USER_EDITOR_DRAG_OR_FLING))
        assertFalse(h.controller.onViewportPhysicalScrollChanged(540f, PhysicalScrollOrigin.USER_HANDLE_AUTOSCROLL))
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)
        assertEquals(540f, h.controller.lastMode2KnownPhysicalScroll, 0.001f)
        assertTrue(h.externalDriftEvents.isEmpty())

        // Sub-0.5px EXTERNAL_PROGRAMMATIC noise is tolerated
        assertFalse(h.controller.onViewportPhysicalScrollChanged(540.4f, PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC))
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)

        // CAUSE_MAKE_POSITION_VISIBLE / EXTERNAL_PROGRAMMATIC drift > 0.5px aborts Mode 2 to FINAL_SAFETY_PENDING
        assertTrue(h.controller.onViewportPhysicalScrollChanged(560f, PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC))
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertFalse(h.controller.shouldApplySplitRender)
        assertEquals(1, h.externalDriftEvents.size)
        assertEquals(9L, h.externalDriftEvents[0].first)
    }

    /**
     * Harness mirroring UnifiedCanvasLayout's Event-B state machine (`completeDeletionVisualTransaction`),
     * atomic teardown-before-IDLE, PendingPostSafetyKeyboardEval, and Dual ACTION_DOWN semantics.
     */
    private class EventBSafetyHarness {
        val controllerHarness = ControllerHarness()
        val controller = controllerHarness.controller
        var deletionTransactionGeneration = 10L
        var hasLiveSnapshot = true
        var isLayoutReady = true
        var physicalScroll = 200
        var headerHeight = 100
        var anchorDocBottom = 500f
        var maxCanvasScroll = 900
        var visibleBottom = 540f
        var breathingMargin = 60f
        val dispatchedEvals = mutableListOf<PendingPostSafetyKeyboardEval>()
        val eventOrderLog = mutableListOf<String>()
        var pendingCorrectionTarget: Int? = null
        var clearCount = 0
        var retractionDebtPreserved = false

        fun makeBounds(): SafetyFloorAndBounds {
            val vb = UnobstructedViewportBounds(
                headerBottomLocal = 0f,
                stickyBottomLocal = 0f,
                maxTopBarBottomLocal = 0f,
                minBottomBarTopLocal = visibleBottom,
                windowVisibleBottomLocal = visibleBottom,
                visibleEditorTop = 0f,
                visibleEditorBottom = visibleBottom,
                usableHeight = visibleBottom,
                hasModalBlocker = false,
                isValid = visibleBottom > 0f,
                floatingPillLocalRects = emptyList()
            )
            return ScribeGapCollapsePolicy.resolveSafetyFloorAndBounds(
                headerHeight = headerHeight,
                anchorDocBottom = anchorDocBottom,
                breathingMarginPx = breathingMargin,
                usableBottomForSafety = vb.visibleEditorBottom,
                maxCanvasScrollPost = maxCanvasScroll,
                scrollD = physicalScroll,
                editorOffsetY = 0
            )
        }

        fun runEventB(capturedGen: Long, capturedSafetyEpoch: Long): SafetyRevalidationResult {
            if (!controller.matchesSafetyEpoch(capturedGen, capturedSafetyEpoch) ||
                deletionTransactionGeneration != capturedGen ||
                !hasLiveSnapshot
            ) {
                return SafetyRevalidationResult.STALE_TRANSACTION
            }
            if (!isLayoutReady) {
                return if (controller.markSafetyRetryAttempted(capturedGen, capturedSafetyEpoch)) {
                    SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY("layout_not_ready")
                } else {
                    finalizeUnresolved(capturedGen, capturedSafetyEpoch, "retry_exhausted")
                    SafetyRevalidationResult.UNRESOLVED("retry_exhausted")
                }
            }
            val bounds = makeBounds()
            if (bounds.safeMinPhysical > bounds.safeMax) {
                finalizeUnresolved(capturedGen, capturedSafetyEpoch, "contradictory_bounds")
                return SafetyRevalidationResult.UNRESOLVED("contradictory_bounds")
            }
            if (bounds.isPhysicalScrollSafe(physicalScroll)) {
                assertTrue(physicalScroll in bounds.safeMinPhysical..bounds.safeMax)
                val wasCorrected = controller.safetyCorrectionAttempted
                finalizeComplete(capturedGen, capturedSafetyEpoch)
                return if (wasCorrected) {
                    SafetyRevalidationResult.CORRECTED_AND_VALIDATED
                } else {
                    SafetyRevalidationResult.VALIDATED_SAFE
                }
            }
            val correctiveTarget = bounds.clampPhysicalSafetyTarget(physicalScroll.toFloat())
            if (!controller.markSafetyCorrectionAttempted(capturedGen, capturedSafetyEpoch)) {
                finalizeUnresolved(capturedGen, capturedSafetyEpoch, "did_not_converge")
                return SafetyRevalidationResult.UNRESOLVED("did_not_converge")
            }
            pendingCorrectionTarget = kotlin.math.round(correctiveTarget).toInt()
            return SafetyRevalidationResult.CORRECTION_STARTED
        }

        fun finishCorrectionAndReenter(capturedGen: Long, capturedSafetyEpoch: Long, actualAfter: Int): SafetyRevalidationResult {
            if (!controller.matchesSafetyEpoch(capturedGen, capturedSafetyEpoch) ||
                deletionTransactionGeneration != capturedGen
            ) {
                return SafetyRevalidationResult.STALE_TRANSACTION
            }
            physicalScroll = actualAfter
            val nextEpoch = controller.advanceSafetyEpoch(capturedGen)!!
            return runEventB(capturedGen, nextEpoch)
        }

        private fun finalizeComplete(capturedGen: Long, capturedSafetyEpoch: Long) {
            val claim = controller.beginTeardownLockAndConsumeEval(
                expectedGen = capturedGen,
                expectedSafetyEpoch = capturedSafetyEpoch
            )!!
            eventOrderLog.add("teardown_locked:${controller.isTeardownLocked}")
            // Before commitIdle, IDLE must NOT be reached yet while live tx exists
            assertNotNull(controller.phase)
            assertTrue(controller.phase != GapCollapsePhase.IDLE)
            hasLiveSnapshot = false
            clearCount++
            deletionTransactionGeneration++
            eventOrderLog.add("snapshot_cleared_gen=$deletionTransactionGeneration")
            controller.commitIdle()
            eventOrderLog.add("idle_committed:${controller.phase}")
            claim.pendingEval?.let { dispatchedEvals.add(it) }
        }

        private fun finalizeUnresolved(capturedGen: Long, capturedSafetyEpoch: Long, reason: String) {
            val claim = controller.beginTeardownLockAndConsumeEval(
                expectedGen = capturedGen,
                expectedSafetyEpoch = capturedSafetyEpoch
            )!!
            eventOrderLog.add("unresolved_locked:$reason")
            assertTrue(controller.phase != GapCollapsePhase.IDLE)
            hasLiveSnapshot = false
            clearCount++
            retractionDebtPreserved = true
            deletionTransactionGeneration++
            controller.commitIdle()
            eventOrderLog.add("idle_committed:${controller.phase}")
            claim.pendingEval?.let { dispatchedEvals.add(it) }
        }

        fun onUserTouchDown() {
            val gen = deletionTransactionGeneration
            if (controller.tryClaimCancellation(gen)) {
                if (hasLiveSnapshot) {
                    hasLiveSnapshot = false
                    clearCount++
                    retractionDebtPreserved = true
                    deletionTransactionGeneration++
                }
                controller.commitIdle()
            } else if (!controller.isTransactionOwned) {
                // Legacy path when already IDLE: must NOT bump gen or clear a second time
                if (hasLiveSnapshot) {
                    hasLiveSnapshot = false
                    clearCount++
                    deletionTransactionGeneration++
                }
            }
        }
    }

    @Test
    fun testEventB_AllSixSafetyRevalidationResults_AndAtomicTeardownBeforeIdle() {
        // 1. VALIDATED_SAFE + PendingPostSafetyKeyboardEval exit 1
        run {
            val h = EventBSafetyHarness()
            // anchor=500, header=100, breathing=60, visibleBottom=540 -> minSafe=100+500-(540-60)=120
            h.physicalScroll = 150
            val se = h.controller.transitionToFinalSafetyForFallback(h.deletionTransactionGeneration)
            val eval = PendingPostSafetyKeyboardEval(10L, se, 540, true, true, true, false, true, ScribeDockTransition.NONE)
            assertTrue(h.controller.storePendingPostSafetyKeyboardEval(eval))

            val res = h.runEventB(10L, se)
            assertEquals(SafetyRevalidationResult.VALIDATED_SAFE, res)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(11L, h.deletionTransactionGeneration)
            assertEquals(listOf(eval), h.dispatchedEvals)
            // Verify strict teardown ordering before IDLE
            assertEquals("teardown_locked:true", h.eventOrderLog[0])
            assertEquals("snapshot_cleared_gen=11", h.eventOrderLog[1])
            assertEquals("idle_committed:IDLE", h.eventOrderLog[2])
            // Stale epoch re-entry rejected
            assertEquals(SafetyRevalidationResult.STALE_TRANSACTION, h.runEventB(10L, se))
        }

        // 2. CORRECTION_STARTED -> CORRECTED_AND_VALIDATED + PendingPostSafetyKeyboardEval exit 2
        run {
            val h = EventBSafetyHarness()
            // minSafe = ceil(120.4) = 121; actual = 100 (unsafe)
            h.anchorDocBottom = 500.4f
            h.physicalScroll = 100
            val se = h.controller.transitionToFinalSafetyForFallback(10L)
            val res1 = h.runEventB(10L, se)
            assertEquals(SafetyRevalidationResult.CORRECTION_STARTED, res1)
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
            assertEquals(121, h.pendingCorrectionTarget)
            assertEquals(10L, h.deletionTransactionGeneration)

            // Store pending eval for current safetyEpoch before correction completes
            val eval = PendingPostSafetyKeyboardEval(10L, h.controller.safetyEpoch, 540, true, true, true, false, true, ScribeDockTransition.NONE)
            assertTrue(h.controller.storePendingPostSafetyKeyboardEval(eval))

            // Stale safetyEpoch=999 rejected
            assertEquals(SafetyRevalidationResult.STALE_TRANSACTION, h.finishCorrectionAndReenter(10L, 999L, 121))

            val res2 = h.finishCorrectionAndReenter(10L, se, actualAfter = 121)
            assertEquals(SafetyRevalidationResult.CORRECTED_AND_VALIDATED, res2)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(11L, h.deletionTransactionGeneration)
            assertEquals(listOf(eval), h.dispatchedEvals)
        }

        // 3. DEFERRED_UNTIL_LAYOUT_READY -> retry -> VALIDATED_SAFE + PendingPostSafetyKeyboardEval exit 3
        run {
            val h = EventBSafetyHarness()
            h.isLayoutReady = false
            h.physicalScroll = 150
            val se = h.controller.transitionToFinalSafetyForFallback(10L)
            val eval = PendingPostSafetyKeyboardEval(10L, se, 540, true, true, true, false, true, ScribeDockTransition.NONE)
            assertTrue(h.controller.storePendingPostSafetyKeyboardEval(eval))

            val res1 = h.runEventB(10L, se)
            assertTrue(res1 is SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY)
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
            assertEquals(10L, h.deletionTransactionGeneration)

            // Layout becomes ready and retries with same (gen, se)
            h.isLayoutReady = true
            val res2 = h.runEventB(10L, se)
            assertEquals(SafetyRevalidationResult.VALIDATED_SAFE, res2)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(listOf(eval), h.dispatchedEvals)
        }

        // 4. UNRESOLVED (layout retry exhaustion) + PendingPostSafetyKeyboardEval exit 4
        run {
            val h = EventBSafetyHarness()
            h.isLayoutReady = false
            val se = h.controller.transitionToFinalSafetyForFallback(10L)
            val eval = PendingPostSafetyKeyboardEval(10L, se, 540, true, true, true, false, true, ScribeDockTransition.NONE)
            assertTrue(h.controller.storePendingPostSafetyKeyboardEval(eval))

            val res1 = h.runEventB(10L, se)
            assertTrue(res1 is SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY)
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
            assertEquals(10L, h.deletionTransactionGeneration)
            assertTrue(h.hasLiveSnapshot)
            assertEquals(0, h.clearCount)
            assertTrue(h.dispatchedEvals.isEmpty())

            // Keep layout unready and retry with same (gen, se) -> UNRESOLVED via retry exhaustion
            val res2 = h.runEventB(10L, se)
            assertTrue(res2 is SafetyRevalidationResult.UNRESOLVED)
            assertEquals("retry_exhausted", (res2 as SafetyRevalidationResult.UNRESOLVED).reason)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(11L, h.deletionTransactionGeneration)
            assertFalse(h.hasLiveSnapshot)
            assertEquals(1, h.clearCount)
            assertTrue(h.retractionDebtPreserved)
            assertEquals(listOf(eval), h.dispatchedEvals)
            assertEquals("unresolved_locked:retry_exhausted", h.eventOrderLog[0])
            assertEquals("idle_committed:IDLE", h.eventOrderLog[1])
        }
    }

    @Test
    fun testDualActionDownAfterIdle_ClearsSnapshotAndBumpsGenerationExactlyOnce() {
        val h = EventBSafetyHarness()
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 200f, minSafeScroll = 350f)
        h.controller.beginCollapse(
            owningGen = 10L,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, h.controller.phase)

        // ACTION_DOWN #1 (e.g. UnifiedCanvasLayout.onUserTouchDown)
        h.onUserTouchDown()
        assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
        assertEquals(1, h.clearCount)
        assertEquals(11L, h.deletionTransactionGeneration)
        assertTrue(h.retractionDebtPreserved)

        // ACTION_DOWN #2 arriving right after IDLE is committed (e.g. ScribeCodeEditor.onTouchEvent)
        h.onUserTouchDown()
        assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
        assertEquals("Snapshot clear must execute exactly once", 1, h.clearCount)
        assertEquals("Generation must advance exactly once", 11L, h.deletionTransactionGeneration)
    }

    @Test
    fun testCancelPreservingTransaction_SuppressesCallbacksAndClearsCoordinator() {
        val coord = ScribeScrollCoordinator(totalScroll = 500f)
        coord.onScrollBy = { 0f }
        var onEndCalls = 0
        var onSettledCalls = 0
        var onCancelledCalls = 0
        val animator = ScribeCanvasGlideAnimator(
            scrollCoordinator = coord,
            postOnAnimationAction = {},
            removeCallbacksAction = {},
            onSettledCallback = { onSettledCalls++ },
            onCancelledCallback = { _, _ -> onCancelledCalls++ }
        )
        assertTrue(
            animator.startAuthoritativeAnimation(
                currentScroll = 500f,
                targetTotalScroll = 350f,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                maxCanvasScroll = 900,
                forceRetarget = true,
                onEnd = { onEndCalls++ }
            )
        )
        assertTrue(animator.isGlideRunning)
        assertTrue(coord.isAnimationActive)

        animator.cancelPreservingTransaction("handoff_to_teardown")
        assertFalse(animator.isGlideRunning)
        assertFalse(coord.isAnimationActive)
        assertEquals("cancelPreservingTransaction must NOT fire onEnd", 0, onEndCalls)
        assertEquals("cancelPreservingTransaction must NOT fire onSettledCallback", 0, onSettledCalls)
        assertEquals("cancelPreservingTransaction must NOT fire onCancelledCallback", 0, onCancelledCalls)
    }

    @Test
    fun testFreezeForPointerDuringMode1_CancelsMode1AnimatorWithoutBumpingGenAndResumesToMode2Cleanly() {
        var settledCalls = 0
        var endCalls = 0
        var cancelledCalls = 0
        var currentScroll = 500f
        val postedRunnables = mutableListOf<Runnable>()
        val coord = ScribeScrollCoordinator(totalScroll = currentScroll).apply {
            onScrollBy = { delta ->
                currentScroll += delta
                0f
            }
        }
        val animator = ScribeCanvasGlideAnimator(
            scrollCoordinator = coord,
            postOnAnimationAction = { postedRunnables.add(it) },
            removeCallbacksAction = { postedRunnables.remove(it) },
            onSettledCallback = { settledCalls++ },
            onCancelledCallback = { _, _ -> cancelledCalls++ }
        )

        var scheduledFrame: ((Long) -> Unit)? = null
        var freezeAnimatorCalls = 0
        lateinit var controller: ScribeGapCollapseController
        controller = ScribeGapCollapseController(
            scheduleFrame = { cb -> scheduledFrame = cb },
            cancelFrame = { scheduledFrame = null },
            onFreezeMode1Animator = {
                freezeAnimatorCalls++
                animator.cancelPreservingTransaction("pointer_freeze")
                coord.syncActualScroll(currentScroll)
            }
        )

        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 300f, minSafeScroll = 350f)
        val gen = 11L
        val epoch = controller.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        controller.markMode1Started(gen, epoch, 500f)
        assertTrue(
            animator.startAuthoritativeAnimation(
                currentScroll = 500f,
                targetTotalScroll = plan.effectiveTargetScroll,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                maxCanvasScroll = 900,
                forceRetarget = true,
                onEnd = { endCalls++ }
            )
        )
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, controller.phase)
        assertTrue(animator.isGlideRunning)
        assertTrue(coord.isAnimationActive)

        // Advance one Mode-1 physical frame before pointer touch-down
        animator.advanceFrame(0.016f)
        controller.onMode1FrameCommitted(gen, epoch, currentScroll, boundaryHit = false, frameTimeNanos = 1_016_000_000L)
        val scrollAtTouchDown = currentScroll
        val tauAtTouchDown = controller.tauLower

        // Freeze mid-Mode-1: must stop Mode-1 glide/coordinator cleanly without firing onEnd/onCancelled
        assertTrue(controller.freezeForPointer(gen))
        assertEquals(1, freezeAnimatorCalls)
        assertEquals(GapCollapsePhase.POINTER_FROZEN, controller.phase)
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, controller.phaseBeforeFreeze)
        assertTrue(controller.isPointerFrozen)
        assertFalse("Mode-1 glide must be stopped immediately on pointer freeze", animator.isGlideRunning)
        assertFalse("Scroll coordinator must be inactive after pointer freeze", coord.isAnimationActive)
        assertEquals(0, endCalls)
        assertEquals(0, settledCalls)
        assertEquals(0, cancelledCalls)

        // Any stale queued frame callback from the cancelled Mode-1 glide must be a strict no-op
        animator.advanceFrame(0.016f)
        assertEquals(scrollAtTouchDown, currentScroll, 0.001f)
        assertEquals(tauAtTouchDown, controller.tauLower, 0.001f)
        assertEquals(GapCollapsePhase.POINTER_FROZEN, controller.phase)

        // Freezing a second time (dual ACTION_DOWN) is idempotent and does not re-trigger Mode-1 callback
        assertTrue(controller.freezeForPointer(gen))
        assertEquals(1, freezeAnimatorCalls)

        // Resume on ACTION_UP -> enters Mode 2 cleanly with zero concurrent Mode-1 animator
        val nextPhase = controller.resumeFromPointerFrozen(gen, currentScroll)
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, nextPhase)
        assertNotNull(scheduledFrame)
        assertFalse(animator.isGlideRunning)
        assertFalse(coord.isAnimationActive)
    }

    @Test
    fun testFreezeForPointerDuringMode1WithZeroTauLower_IsPointerFrozenAndResumesToFinalSafety() {
        var settledCalls = 0
        var endCalls = 0
        var cancelledCalls = 0
        var currentScroll = 850f
        val postedRunnables = mutableListOf<Runnable>()
        val coord = ScribeScrollCoordinator(totalScroll = currentScroll).apply {
            onScrollBy = { delta ->
                currentScroll += delta
                0f
            }
        }
        val animator = ScribeCanvasGlideAnimator(
            scrollCoordinator = coord,
            postOnAnimationAction = { postedRunnables.add(it) },
            removeCallbacksAction = { postedRunnables.remove(it) },
            onSettledCallback = { settledCalls++ },
            onCancelledCallback = { _, _ -> cancelledCalls++ }
        )

        var freezeAnimatorCalls = 0
        lateinit var controller: ScribeGapCollapseController
        controller = ScribeGapCollapseController(
            scheduleFrame = { },
            cancelFrame = { },
            onFreezeMode1Animator = {
                freezeAnimatorCalls++
                animator.cancelPreservingTransaction("pointer_freeze_zero_tau")
                coord.syncActualScroll(currentScroll)
            }
        )

        val overscrollPlan = makeLowerOnlyPlan(
            preScrollY = 850,
            deltaGapPx = 100f,
            minSafeScroll = 300f,
            safeMax = 700
        )
        assertTrue(overscrollPlan.requiresMode1)
        assertEquals(700f, overscrollPlan.effectiveTargetScroll, 0.001f)
        assertEquals(100f, overscrollPlan.initialTauLowerPx, 0.001f)
        assertFalse(overscrollPlan.isProgressCoupled)

        val gen = 15L
        val epoch = controller.beginCollapse(
            owningGen = gen,
            plan = overscrollPlan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 850f
        )
        controller.markMode1Started(gen, epoch, 850f)
        assertEquals(100f, controller.tauLower, 0.001f)

        assertTrue(
            animator.startAuthoritativeAnimation(
                currentScroll = 850f,
                targetTotalScroll = overscrollPlan.effectiveTargetScroll,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                maxCanvasScroll = 900,
                forceRetarget = true,
                onEnd = { endCalls++ }
            )
        )

        // Advance Mode-1 physical glide past the gap delta (850 -> <=750) into Phase 1b where tauLower genuinely reaches 0f
        var steps = 0
        while (currentScroll > 745f && steps < 30) {
            steps++
            animator.advanceFrame(0.016f)
            controller.onMode1FrameCommitted(
                expectedGen = gen,
                expectedCollapseEpoch = epoch,
                actualPhysicalScrollPx = currentScroll,
                boundaryHit = false,
                frameTimeNanos = 1_000_000_000L + steps * 16_000_000L
            )
        }
        assertTrue("Mode-1 glide must still be running in Phase 1b above safeMax target", animator.isGlideRunning)
        assertTrue(currentScroll in 701f..750f)
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, controller.phase)
        assertEquals(0f, controller.tauLower, 0.001f)

        assertTrue(controller.freezeForPointer(gen))
        assertEquals(1, freezeAnimatorCalls)
        assertEquals(GapCollapsePhase.POINTER_FROZEN, controller.phase)
        assertEquals(gen, controller.owningDeletionGen)
        assertTrue("Zero-tau Mode-1 pointer freeze must report isPointerFrozen=true so ACTION_UP never strands", controller.isPointerFrozen)
        assertFalse(controller.isResumableCollapse)
        assertFalse("Mode-1 animator must be stopped on zero-tau pointer freeze", animator.isGlideRunning)
        assertFalse("Coordinator operation must be stopped on zero-tau pointer freeze", coord.isAnimationActive)
        assertEquals(0, endCalls)
        assertEquals(0, settledCalls)
        assertEquals(0, cancelledCalls)

        val resumed = controller.resumeFromPointerFrozen(gen, currentScroll)
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, resumed)
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, controller.phase)
        assertEquals(gen, controller.owningDeletionGen)

        val claim = controller.beginTeardownLockAndConsumeEval(
            expectedGen = gen,
            expectedSafetyEpoch = controller.safetyEpoch
        )
        assertNotNull("Event B must be able to claim and settle after zero-tau ACTION_UP", claim)
        controller.commitIdle()
        assertEquals(GapCollapsePhase.IDLE, controller.phase)
        assertFalse(controller.isTransactionOwned)
        assertFalse(controller.isPointerFrozen)
    }

    @Test
    fun testPhysicalScrollOriginNone_AbortsMode2OnDriftOverHalfPx_WhileAuthorizedOriginsTrack() {
        val h = ControllerHarness()
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 80f, minSafeScroll = 500f)
        h.controller.beginCollapse(
            owningGen = 12L,
            plan = plan,
            seamLine = 8,
            seamFirstLowerRow = 8,
            seamDocY = 320f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)

        // Sub-0.5px untagged NONE noise is tolerated
        assertFalse(h.controller.onViewportPhysicalScrollChanged(500.4f, PhysicalScrollOrigin.NONE))
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)
        assertTrue(h.externalDriftEvents.isEmpty())

        // Mode-1 and Event-B internal tags plus authorized user tags continue tracking cleanly
        assertFalse(h.controller.onViewportPhysicalScrollChanged(510f, PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1))
        assertFalse(h.controller.onViewportPhysicalScrollChanged(515f, PhysicalScrollOrigin.EVENT_B_SAFETY_CORRECTION))
        assertEquals(515f, h.controller.lastMode2KnownPhysicalScroll, 0.001f)

        // Untagged NONE movement exceeding 0.5px must NOT bypass Mode-2 drift detection
        assertTrue(h.controller.onViewportPhysicalScrollChanged(515.6f, PhysicalScrollOrigin.NONE))
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertFalse(h.controller.shouldApplySplitRender)
        assertEquals(1, h.externalDriftEvents.size)
        assertEquals(12L, h.externalDriftEvents[0].first)
    }

    /**
     * Event-B safety harness exercising ScribeGapCollapseController.restartFinalSafetyClaim(),
     * routing every outcome through EventBSafetyHarness.runEventB() and tracking
     * EditorTextActionWindow eligibility across FINAL_SAFETY_PENDING -> IDLE -> unsplit draw.
     */
    private class OptionBCursorAbortHarness {
        val safety = EventBSafetyHarness()
        val controller = safety.controller
        var splitEnteredCount = 0
        var textActionWindowEnabled = true
        var savedTextActionWindowEnabled: Boolean? = null
        var renderedDrawPassSeq = 0L
        var pendingTextActionRefreshTargetSeq = -1L
        var pendingTextActionRefreshGen = -1L
        var cursorAnimatorRunning = false
        var animatorCancelCount = 0
        var restorationCancelCount = 0

        fun beginMode1OrMode2Collapse(
            requiresMode1: Boolean,
            preScrollY: Int = 500,
            deltaGapPx: Float = 240f,
            minSafeScroll: Float = if (requiresMode1) 350f else 500f
        ): Long {
            val bounds = UnobstructedViewportBounds(
                headerBottomLocal = 0f,
                stickyBottomLocal = 0f,
                maxTopBarBottomLocal = 0f,
                minBottomBarTopLocal = safety.visibleBottom,
                windowVisibleBottomLocal = safety.visibleBottom,
                visibleEditorTop = 0f,
                visibleEditorBottom = safety.visibleBottom,
                usableHeight = safety.visibleBottom,
                hasModalBlocker = false,
                isValid = true,
                floatingPillLocalRects = emptyList()
            )
            val safetyBounds = SafetyFloorAndBounds(
                safeMax = safety.maxCanvasScroll,
                minSafeScroll = minSafeScroll,
                safeMinPhysical = kotlin.math.ceil(minSafeScroll.toDouble()).toInt().coerceAtMost(safety.maxCanvasScroll),
                actualPhysicalScroll = preScrollY
            )
            val plan = ScribeGapCollapsePolicy.planCollapse(
                deletionIntent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
                isSelection = true,
                isWordwrap = true,
                isWordwrapReady = true,
                isLayoutBusy = false,
                isComposing = false,
                isCursorAnimationRunning = false,
                hasRtlOrBidiAtSeam = false,
                isMergedPartialRowSeam = false,
                hasSurvivingUpper = true,
                hasSurvivingLower = true,
                deltaGapPx = deltaGapPx,
                visibilityConfidence = VisibilityConfidence.RESOLVED,
                isUpperVisible = false,
                isLowerVisible = true,
                isTouchedOnScreen = true,
                preScrollY = preScrollY,
                centeredTargetScroll = preScrollY.toFloat(),
                viewportBounds = bounds,
                safetyBounds = safetyBounds
            )
            safety.physicalScroll = preScrollY
            val epoch = controller.beginCollapse(
                owningGen = safety.deletionTransactionGeneration,
                plan = plan,
                seamLine = 10,
                seamFirstLowerRow = 10,
                seamDocY = 400f,
                rowHeightPx = 40f,
                initialPhysicalScrollPx = preScrollY.toFloat()
            )
            if (requiresMode1) {
                controller.markMode1Started(safety.deletionTransactionGeneration, epoch, preScrollY.toFloat())
            }
            if (controller.shouldApplySplitRender) {
                splitEnteredCount++
                if (savedTextActionWindowEnabled == null) {
                    savedTextActionWindowEnabled = textActionWindowEnabled
                }
                textActionWindowEnabled = false
            }
            return epoch
        }

        fun onCursorAnimatorStartedMidCollapse(): SafetyRevalidationResult? {
            cursorAnimatorRunning = true
            val gen = controller.owningDeletionGen
            if (!controller.isTransactionOwned || gen != safety.deletionTransactionGeneration || controller.isTeardownLocked) {
                return null
            }
            if (controller.phase == GapCollapsePhase.FINAL_SAFETY_PENDING) {
                return null
            }
            animatorCancelCount++
            restorationCancelCount++
            val se = controller.restartFinalSafetyClaim(gen) ?: return null
            val result = safety.runEventB(gen, se)
            if (controller.phase == GapCollapsePhase.IDLE && savedTextActionWindowEnabled == true) {
                pendingTextActionRefreshTargetSeq = renderedDrawPassSeq + 1L
                pendingTextActionRefreshGen = safety.deletionTransactionGeneration
            }
            return result
        }

        fun completeAsyncSettlement(block: () -> SafetyRevalidationResult): SafetyRevalidationResult {
            val result = block()
            if (controller.phase == GapCollapsePhase.IDLE && savedTextActionWindowEnabled == true) {
                pendingTextActionRefreshTargetSeq = renderedDrawPassSeq + 1L
                pendingTextActionRefreshGen = safety.deletionTransactionGeneration
            }
            return result
        }

        fun simulateRendererDrawPass(): Boolean {
            renderedDrawPassSeq++
            val didRefreshCursorDescriptors = !controller.shouldApplySplitRender && !cursorAnimatorRunning
            if (pendingTextActionRefreshTargetSeq >= 0L &&
                renderedDrawPassSeq >= pendingTextActionRefreshTargetSeq &&
                didRefreshCursorDescriptors &&
                !controller.shouldApplySplitRender &&
                controller.phase == GapCollapsePhase.IDLE &&
                safety.deletionTransactionGeneration == pendingTextActionRefreshGen &&
                savedTextActionWindowEnabled == true
            ) {
                pendingTextActionRefreshTargetSeq = -1L
                pendingTextActionRefreshGen = -1L
                savedTextActionWindowEnabled = null
                textActionWindowEnabled = true
            }
            return didRefreshCursorDescriptors
        }
    }

    @Test
    fun testOptionB_MidCollapseCursorAnimatorAbort_AllSixEventBOutcomesAndTextActionGate() {
        // 1. Mode 1 -> cursorAnimator starts -> VALIDATED_SAFE -> stays disabled until unsplit !isRunning draw
        run {
            val h = OptionBCursorAbortHarness()
            h.safety.physicalScroll = 500
            h.beginMode1OrMode2Collapse(requiresMode1 = true, preScrollY = 500, deltaGapPx = 300f, minSafeScroll = 350f)
            assertTrue(h.controller.shouldApplySplitRender)
            assertFalse(h.textActionWindowEnabled)

            val res = h.onCursorAnimatorStartedMidCollapse()
            assertEquals(SafetyRevalidationResult.VALIDATED_SAFE, res)
            assertEquals(0f, h.controller.tauLower, 0.001f)
            assertFalse(h.controller.shouldApplySplitRender)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(1, h.animatorCancelCount)
            assertEquals(1, h.restorationCancelCount)
            // Even in IDLE, EditorTextActionWindow remains disabled while cursorAnimator is still running
            assertFalse(h.textActionWindowEnabled)
            assertFalse(h.simulateRendererDrawPass())
            assertFalse(h.textActionWindowEnabled)
            // Once cursorAnimator finishes, next unsplit draw refreshes descriptors and re-enables window
            h.cursorAnimatorRunning = false
            assertTrue(h.simulateRendererDrawPass())
            assertTrue(h.textActionWindowEnabled)
        }

        // 2 & 3. Mode 2 -> cursorAnimator starts -> CORRECTION_STARTED keeps FINAL_SAFETY_PENDING + ownership ->
        // split render disabled immediately, EditorTextActionWindow stays disabled -> CORRECTED_AND_VALIDATED
        run {
            val h = OptionBCursorAbortHarness()
            h.beginMode1OrMode2Collapse(requiresMode1 = false, preScrollY = 500, deltaGapPx = 80f, minSafeScroll = 500f)
            assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)
            assertNotNull(h.safety.controllerHarness.scheduledFrameCallback)
            assertFalse(h.textActionWindowEnabled)

            // Make scroll unsafe so Event B enters CORRECTION_STARTED
            h.safety.anchorDocBottom = 700.4f
            h.safety.physicalScroll = 100
            val capturedGen = h.safety.deletionTransactionGeneration
            val cancelBefore = h.safety.controllerHarness.cancelFrameCount

            val res1 = h.onCursorAnimatorStartedMidCollapse()
            assertEquals(SafetyRevalidationResult.CORRECTION_STARTED, res1)
            assertTrue(h.safety.controllerHarness.cancelFrameCount > cancelBefore)
            assertNull(h.safety.controllerHarness.scheduledFrameCallback)
            // CRITICAL: split rendering disabled immediately EVEN THOUGH controller stays FINAL_SAFETY_PENDING
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
            assertEquals(0f, h.controller.tauLower, 0.001f)
            assertFalse(h.controller.shouldApplySplitRender)
            assertTrue(h.controller.isTransactionOwned)
            assertEquals(capturedGen, h.controller.owningDeletionGen)
            assertEquals(capturedGen, h.safety.deletionTransactionGeneration)
            assertFalse(h.textActionWindowEnabled)

            // Draw during FINAL_SAFETY_PENDING must NOT re-enable EditorTextActionWindow
            h.cursorAnimatorRunning = false
            h.simulateRendererDrawPass()
            assertFalse(h.textActionWindowEnabled)

            // Stale epoch callback rejected
            assertEquals(
                SafetyRevalidationResult.STALE_TRANSACTION,
                h.safety.finishCorrectionAndReenter(capturedGen, capturedSafetyEpoch = 999L, actualAfter = 300)
            )
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)

            val se = h.controller.safetyEpoch
            val res2 = h.completeAsyncSettlement {
                h.safety.finishCorrectionAndReenter(capturedGen, se, actualAfter = h.safety.pendingCorrectionTarget!!)
            }
            assertEquals(SafetyRevalidationResult.CORRECTED_AND_VALIDATED, res2)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(capturedGen + 1L, h.safety.deletionTransactionGeneration)
            // Still disabled in IDLE prior to post-IDLE unsplit draw pass
            assertFalse(h.textActionWindowEnabled)
            assertTrue(h.simulateRendererDrawPass())
            assertTrue(h.textActionWindowEnabled)
        }

        // 4. DEFERRED_UNTIL_LAYOUT_READY keeps FINAL_SAFETY_PENDING + ownership, split render false -> retry settles
        run {
            val h = OptionBCursorAbortHarness()
            h.beginMode1OrMode2Collapse(requiresMode1 = true, preScrollY = 500, deltaGapPx = 240f, minSafeScroll = 350f)
            val capturedGen = h.safety.deletionTransactionGeneration
            h.safety.isLayoutReady = false

            val res1 = h.onCursorAnimatorStartedMidCollapse()
            assertTrue(res1 is SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY)
            assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
            assertFalse(h.controller.shouldApplySplitRender)
            assertEquals(0f, h.controller.tauLower, 0.001f)
            assertTrue(h.controller.isTransactionOwned)
            assertEquals(capturedGen, h.controller.owningDeletionGen)
            assertFalse(h.textActionWindowEnabled)

            val retryEpoch = h.controller.safetyEpoch
            h.safety.isLayoutReady = true
            h.cursorAnimatorRunning = false
            val res2 = h.completeAsyncSettlement {
                h.safety.runEventB(capturedGen, retryEpoch)
            }
            assertEquals(SafetyRevalidationResult.VALIDATED_SAFE, res2)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertTrue(h.simulateRendererDrawPass())
            assertTrue(h.textActionWindowEnabled)
        }

        // 5 & 6. UNRESOLVED + STALE_TRANSACTION rejection when newer generation replaces aborted tx
        run {
            val h = OptionBCursorAbortHarness()
            h.beginMode1OrMode2Collapse(requiresMode1 = false, preScrollY = 500, deltaGapPx = 80f, minSafeScroll = 500f)
            val oldGen = h.safety.deletionTransactionGeneration
            h.safety.isLayoutReady = false

            val deferred = h.onCursorAnimatorStartedMidCollapse()
            assertTrue(deferred is SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY)
            val oldEpoch = h.controller.safetyEpoch

            // Second retry still not ready -> UNRESOLVED via common finalizer
            val unresolved = h.completeAsyncSettlement {
                h.safety.runEventB(oldGen, oldEpoch)
            }
            assertTrue(unresolved is SafetyRevalidationResult.UNRESOLVED)
            assertTrue(h.safety.retractionDebtPreserved)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(oldGen + 1L, h.safety.deletionTransactionGeneration)

            // Stale callbacks from oldGen / oldEpoch rejected without touching newer transaction
            assertEquals(SafetyRevalidationResult.STALE_TRANSACTION, h.safety.runEventB(oldGen, oldEpoch))
            assertEquals(
                SafetyRevalidationResult.STALE_TRANSACTION,
                h.safety.finishCorrectionAndReenter(oldGen, oldEpoch, actualAfter = 300)
            )
        }
    }

    @Test
    fun testIncrementalSeamRebase_PreserveAndRebaseAcrossPhasesAndFallbackBoundaries() {
        var freezeCalls = 0
        var settledToSafetyCount = 0
        var scheduledFrame: ((Long) -> Unit)? = null
        val controller = ScribeGapCollapseController(
            scheduleFrame = { scheduledFrame = it },
            cancelFrame = { scheduledFrame = null },
            onFreezeMode1Animator = { freezeCalls++ },
            onMode2SettledToSafety = { _, _ -> settledToSafetyCount++ }
        )

        // Pure policy bounds for (seamLine, 0) rebase
        val preserveRes = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 10,
            newSeamCol = 0,
            newSeamFirstLowerRow = 10,
            postSeamDocY = 400.4f
        )
        assertTrue(preserveRes is SeamMappingResult.PreservePhase)

        val rebaseRes = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 11,
            newSeamCol = 0,
            newSeamFirstLowerRow = 11,
            postSeamDocY = 440f
        ) as SeamMappingResult.RebaseTau
        assertEquals(80f, rebaseRes.residualTauLowerPx, 0.001f)

        val colNonZeroFallback = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 10,
            newSeamCol = 1,
            newSeamFirstLowerRow = 10,
            postSeamDocY = 400f
        )
        assertTrue(colNonZeroFallback is SeamMappingResult.FallbackRequired)

        val overTauFallback = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 15,
            newSeamCol = 0,
            newSeamFirstLowerRow = 15,
            postSeamDocY = 600f
        )
        assertTrue(overTauFallback is SeamMappingResult.FallbackRequired)

        val negShiftFallback = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 9,
            newSeamCol = 0,
            newSeamFirstLowerRow = 9,
            postSeamDocY = 360f
        )
        assertTrue(negShiftFallback is SeamMappingResult.FallbackRequired)

        val busyFallback = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = false,
            isLayoutBusy = true,
            preEditTauPx = 120f,
            preEditSeamDocY = 400f,
            newSeamLine = 10,
            newSeamCol = 0,
            newSeamFirstLowerRow = 10,
            postSeamDocY = 400f
        )
        assertTrue(busyFallback is SeamMappingResult.FallbackRequired)

        // Controller: Mode 1 preserve preserves phase, tauLower, G, E
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 160f, minSafeScroll = 380f)
        val gen = 21L
        val epoch0 = controller.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        controller.markMode1Started(gen, epoch0, 500f)
        assertTrue(controller.hasActiveRebasableSeam)
        assertTrue(controller.applyIncrementalSeamPreserve(gen, 11, 12, 480f))
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, controller.phase)
        assertEquals(gen, controller.owningDeletionGen)
        assertEquals(epoch0, controller.collapseAnimEpoch)
        assertEquals(11, controller.activeSeamLine)
        assertEquals(12, controller.activeSeamFirstLowerRow)
        assertEquals(480f, controller.activeSeamDocY, 0.001f)
        assertEquals(0, freezeCalls)

        // Controller: Mode 1 rebase freezes Mode-1 animator and transitions to Mode 2 preserving G & bumping E
        val phaseAfterRebase1 = controller.applyIncrementalSeamRebase(
            expectedGen = gen,
            newSeamLine = 12,
            newSeamFirstLowerRow = 13,
            newSeamDocY = 520f,
            residualTauLowerPx = 80f,
            actualPhysicalScrollPx = 460f
        )
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, phaseAfterRebase1)
        assertEquals(1, freezeCalls)
        assertEquals(gen, controller.owningDeletionGen)
        assertEquals(epoch0 + 1L, controller.collapseAnimEpoch)
        assertEquals(80f, controller.tauLower, 0.001f)
        assertNotNull(scheduledFrame)

        // POINTER_FROZEN rebase preserves POINTER_FROZEN with residual tauLower
        assertTrue(controller.freezeForPointer(gen))
        val frozenEpoch = controller.collapseAnimEpoch
        val phaseFrozenRebase = controller.applyIncrementalSeamRebase(
            expectedGen = gen,
            newSeamLine = 13,
            newSeamFirstLowerRow = 14,
            newSeamDocY = 560f,
            residualTauLowerPx = 40f,
            actualPhysicalScrollPx = 460f
        )
        assertEquals(GapCollapsePhase.POINTER_FROZEN, phaseFrozenRebase)
        assertEquals(frozenEpoch + 1L, controller.collapseAnimEpoch)
        assertEquals(40f, controller.tauLower, 0.001f)

        // Resume into Mode 2, then rebase to <= MODE2_SETTLE_THRESHOLD_PX -> FINAL_SAFETY_PENDING
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, controller.resumeFromPointerFrozen(gen, 460f))
        val settledPhase = controller.applyIncrementalSeamRebase(
            expectedGen = gen,
            newSeamLine = 14,
            newSeamFirstLowerRow = 15,
            newSeamDocY = 600f,
            residualTauLowerPx = 0.25f,
            actualPhysicalScrollPx = 460f
        )
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, settledPhase)
        assertEquals(0f, controller.tauLower, 0.001f)
        assertFalse(controller.shouldApplySplitRender)
        assertEquals(1, settledToSafetyCount)
    }

    @Test
    fun testPendingPostSafetyKeyboardEval_ExpansionAndDismissalTransitionsAcrossSafetyExits() {
        // Verify dh > 0 authorized follow AND dh < 0 contraction vs dismissal payloads survive
        // Event B exits cleanly and are consumed strictly inside beginTeardownLockAndConsumeEval.
        val h = OptionBCursorAbortHarness()
        h.beginMode1OrMode2Collapse(requiresMode1 = true, preScrollY = 500, deltaGapPx = 220f, minSafeScroll = 350f)
        val gen = h.safety.deletionTransactionGeneration
        val newSafetyEpoch = h.controller.restartFinalSafetyClaim(gen)!!
        assertEquals(-1, h.controller.activeSeamLine)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertFalse(h.controller.shouldApplySplitRender)

        val contractionEval = PendingPostSafetyKeyboardEval(
            owningGen = gen,
            expectedSafetyEpoch = newSafetyEpoch,
            targetHeightPx = 780,
            isHeightShrink = false,
            isAuthorizedTransition = false,
            shouldFollowExpansion = true,
            isKeyboardDismissing = false,
            isDestinationSurfaceActive = true,
            dockTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF
        )
        assertTrue(h.controller.storePendingPostSafetyKeyboardEval(contractionEval))
        val res = h.completeAsyncSettlement {
            h.safety.runEventB(gen, newSafetyEpoch)
        }
        assertEquals(SafetyRevalidationResult.VALIDATED_SAFE, res)
        assertEquals(1, h.safety.dispatchedEvals.size)
        assertEquals(contractionEval, h.safety.dispatchedEvals.single())
        assertNull(h.controller.pendingPostSafetyKeyboardEval)

        // Second scenario: CORRECTION_STARTED on dh < 0 IME_DISMISSAL preserves eval until fresh epoch finalizes
        val h2 = OptionBCursorAbortHarness()
        h2.beginMode1OrMode2Collapse(requiresMode1 = true, preScrollY = 300, deltaGapPx = 240f, minSafeScroll = 420f)
        h2.safety.anchorDocBottom = 800f
        val gen2 = h2.safety.deletionTransactionGeneration
        val se2 = h2.controller.restartFinalSafetyClaim(gen2)!!
        val dismissalEval = PendingPostSafetyKeyboardEval(
            owningGen = gen2,
            expectedSafetyEpoch = se2,
            targetHeightPx = 1100,
            isHeightShrink = false,
            isAuthorizedTransition = false,
            shouldFollowExpansion = false,
            isKeyboardDismissing = true,
            isDestinationSurfaceActive = false,
            dockTransition = ScribeDockTransition.IME_DISMISSAL
        )
        assertTrue(h2.controller.storePendingPostSafetyKeyboardEval(dismissalEval))
        assertEquals(SafetyRevalidationResult.CORRECTION_STARTED, h2.safety.runEventB(gen2, se2))
        assertTrue("Eval must remain pending while correction is running", h2.safety.dispatchedEvals.isEmpty())
        assertTrue(h2.controller.isTransactionOwned)
        assertEquals(se2, h2.controller.safetyEpoch)
        val res2 = h2.completeAsyncSettlement {
            h2.safety.finishCorrectionAndReenter(gen2, se2, actualAfter = h2.safety.pendingCorrectionTarget!!)
        }
        assertEquals(SafetyRevalidationResult.CORRECTED_AND_VALIDATED, res2)
        assertTrue(h2.controller.safetyEpoch > se2)
        assertEquals(1, h2.safety.dispatchedEvals.size)
        assertTrue(h2.safety.dispatchedEvals.single().isKeyboardDismissing)
        assertEquals(ScribeDockTransition.IME_DISMISSAL, h2.safety.dispatchedEvals.single().dockTransition)
    }

    @Test
    fun testActiveSeamColAndUpdateSeamRowBaseline() {
        val h = ControllerHarness()
        val plan = makeLowerOnlyPlan(preScrollY = 400, deltaGapPx = 120f, minSafeScroll = 0f)
        h.controller.beginCollapse(
            owningGen = 11L,
            plan = plan,
            seamLine = 7,
            seamFirstLowerRow = 12,
            seamDocY = 480f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 400f,
            seamCol = 14
        )
        assertEquals(14, h.controller.activeSeamCol)
        assertEquals(0f, h.controller.getTranslationForPosition(7, 13), 0.001f)
        assertEquals(120f, h.controller.getTranslationForPosition(7, 14), 0.001f)
        assertEquals(120f, h.controller.getTranslationForPosition(8, 0), 0.001f)

        assertTrue(h.controller.updateSeamRowBaseline(11L, newSeamFirstLowerRow = 13, newSeamDocY = 520f))
        assertEquals(13, h.controller.activeSeamFirstLowerRow)
        assertEquals(520f, h.controller.activeSeamDocY, 0.001f)
        assertFalse(h.controller.updateSeamRowBaseline(99L, newSeamFirstLowerRow = 14, newSeamDocY = 560f))
    }

    @Test
    fun testUnifiedCollapse_ScenarioBC_DrivesPhysicalScrollAndTauLowerToFinalSafety() {
        val h = ControllerHarness(quantizeScrollToInt = true, initialScrollPx = 500f)
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 180.5f, minSafeScroll = 0f)
        val seamDocY = 640f
        val headerH = 200f
        val expectedLowerScreenY = headerH + (seamDocY + plan.deltaGapPx) - 500f

        h.controller.beginUnifiedCollapse(
            owningGen = 21L,
            plan = plan,
            seamLine = 14,
            seamFirstLowerRow = 16,
            seamDocY = seamDocY,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        assertTrue(h.controller.isUnifiedCollapseActive)
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, h.controller.phase)
        assertNotNull(h.scheduledFrameCallback)

        var frameNs = 2_000_000_000L
        var frames = 0
        while (h.scheduledFrameCallback != null && frames < 40) {
            h.pumpMode2Frame(frameNs)
            frameNs += 16_000_000L
            frames++
            if (h.controller.phase != GapCollapsePhase.FINAL_SAFETY_PENDING) {
                val screenLower = headerH - h.simulatedScrollPx + seamDocY + h.controller.tauLower
                assertEquals(
                    "Frame $frames lower screen Y must stay stationary under Int scroll rounding",
                    expectedLowerScreenY,
                    screenLower,
                    0.05f
                )
            }
        }
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertFalse(h.controller.isUnifiedCollapseActive)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertEquals(plan.effectiveTargetScroll, h.simulatedScrollPx, 0.51f)
        assertEquals(listOf(21L), h.mode2SettledEvents.map { it.first })
    }

    @Test
    fun testUnifiedCollapse_ScenarioD_SubPixelScrollStillAnimatesSmoothly() {
        val h = ControllerHarness(quantizeScrollToInt = false, initialScrollPx = 600f)
        val bounds = makeViewportBounds(top = 0f, bottom = 900f)
        val safety = SafetyFloorAndBounds(
            safeMax = 2000,
            minSafeScroll = 0f,
            safeMinPhysical = 0,
            actualPhysicalScroll = 600
        )
        val plan = ScribeGapCollapsePolicy.planCollapse(
            deletionIntent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
            isSelection = true,
            isWordwrap = true,
            isWordwrapReady = true,
            isLayoutBusy = false,
            isComposing = false,
            isCursorAnimationRunning = false,
            hasRtlOrBidiAtSeam = false,
            isMergedPartialRowSeam = false,
            hasSurvivingUpper = true,
            hasSurvivingLower = true,
            deltaGapPx = 360f,
            visibilityConfidence = VisibilityConfidence.RESOLVED,
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 600,
            centeredTargetScroll = 600f, // exact zero physical delta; previously risked no-op or kink
            viewportBounds = bounds,
            safetyBounds = safety
        )
        assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, plan.scenario)

        h.controller.beginUnifiedCollapse(
            owningGen = 31L,
            plan = plan,
            seamLine = 18,
            seamFirstLowerRow = 20,
            seamDocY = 800f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 600f
        )
        assertTrue(h.controller.isUnifiedCollapseActive)
        assertTrue(h.controller.shouldApplySplitRender)
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, h.controller.phase)

        var frameNs = 3_000_000_000L
        var frames = 0
        var lastTau = h.controller.tauLower
        while (h.scheduledFrameCallback != null && frames < 40) {
            h.pumpMode2Frame(frameNs)
            frameNs += 16_000_000L
            frames++
            assertTrue(h.controller.tauLower <= lastTau + 0.001f)
            lastTau = h.controller.tauLower
        }
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h.controller.phase)
        assertEquals(0f, h.controller.tauLower, 0.001f)
        assertEquals(1, h.mode2SettledEvents.size)
    }

    @Test
    fun testUnifiedCollapse_PointerFreezeAndExternalDriftAbort() {
        // 1. Pointer freeze mid-unified-collapse preserves remaining tauLower & resumes smoothly
        val h1 = ControllerHarness(quantizeScrollToInt = false, initialScrollPx = 500f)
        val plan = makeLowerOnlyPlan(preScrollY = 500, deltaGapPx = 200f, minSafeScroll = 0f)
        h1.controller.beginUnifiedCollapse(
            owningGen = 41L,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 12,
            seamDocY = 480f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        h1.pumpMode2Frame(4_000_000_000L)
        h1.pumpMode2Frame(4_064_000_000L)
        val frozenTau = h1.controller.tauLower
        assertTrue(frozenTau > 1f && frozenTau < 200f)

        assertTrue(h1.controller.freezeForPointer(41L))
        assertFalse(h1.controller.isUnifiedCollapseActive)
        assertEquals(GapCollapsePhase.POINTER_FROZEN, h1.controller.phase)
        assertNull(h1.scheduledFrameCallback)
        assertEquals(frozenTau, h1.controller.tauLower, 0.001f)

        assertEquals(
            GapCollapsePhase.MODE_2_VISUAL_SETTLING,
            h1.controller.resumeFromPointerFrozen(41L, h1.simulatedScrollPx)
        )
        assertNotNull(h1.scheduledFrameCallback)

        // 2. External physical drift during unified collapse aborts safely into FINAL_SAFETY_PENDING
        val h2 = ControllerHarness(quantizeScrollToInt = false, initialScrollPx = 500f)
        h2.controller.beginUnifiedCollapse(
            owningGen = 42L,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 12,
            seamDocY = 480f,
            rowHeightPx = 40f,
            initialPhysicalScrollPx = 500f
        )
        h2.pumpMode2Frame(5_000_000_000L)
        val aborted = h2.controller.onViewportPhysicalScrollChanged(
            origin = PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC,
            actualPhysicalScroll = h2.simulatedScrollPx + 12f
        )
        assertTrue(aborted)
        assertFalse(h2.controller.isUnifiedCollapseActive)
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, h2.controller.phase)
        assertEquals(0f, h2.controller.tauLower, 0.001f)
        assertEquals(listOf(42L), h2.externalDriftEvents.map { it.first })
    }
}
