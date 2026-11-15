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

    private class ControllerHarness {
        var scheduledFrameCallback: ((Long) -> Unit)? = null
        var cancelFrameCount = 0
        var invalidateCount = 0
        var refreshRateHz = 60f
        var nanoTime = 1_000_000_000L
        val mode2SettledEvents = mutableListOf<Pair<Long, Long>>()
        val externalDriftEvents = mutableListOf<Pair<Long, Long>>()

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
            onExternalDriftAbortToSafety = { gen, epoch -> externalDriftEvents.add(gen to epoch) }
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
            hasLiveSnapshot = false
            clearCount++
            retractionDebtPreserved = true
            deletionTransactionGeneration++
            controller.commitIdle()
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

        // 4. UNRESOLVED + PendingPostSafetyKeyboardEval exit 4
        run {
            val h = EventBSafetyHarness()
            // Contradictory bounds: safeMinPhysical (120) > safeMax (80)
            h.maxCanvasScroll = 80
            h.physicalScroll = 80
            val se = h.controller.transitionToFinalSafetyForFallback(10L)
            val eval = PendingPostSafetyKeyboardEval(10L, se, 540, true, true, true, false, true, ScribeDockTransition.NONE)
            assertTrue(h.controller.storePendingPostSafetyKeyboardEval(eval))

            val res = h.runEventB(10L, se)
            assertTrue(res is SafetyRevalidationResult.UNRESOLVED)
            assertEquals(GapCollapsePhase.IDLE, h.controller.phase)
            assertEquals(11L, h.deletionTransactionGeneration)
            assertTrue(h.retractionDebtPreserved)
            assertEquals(listOf(eval), h.dispatchedEvals)
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
}
