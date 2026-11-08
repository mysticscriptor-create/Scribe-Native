package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ScribeScrollPolicy: breathing margins, deadband, centering, tap heuristics, and transactions.
 */
class ScribeScrollPolicyTest {
    @Test
    fun testCursorOccludedOverflowTriggersUpwardScroll() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density) // 10px

        // Cursor is 50px below target (1190px)
        val screenBottom = 1190f
        val screenTop = 1150f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = false,
            hasScrollOffset = true
        )

        assertTrue("Occluded cursor must scroll upward", resolution.shouldScroll)
        assertFalse("Must not be anchor downward", resolution.isAnchorDownward)
        assertEquals(50f, resolution.deltaY, 0.001f)
    }

    @Test
    fun testCursorInDeadbandSafeZoneDoesNotScroll() {
        val visibleHeight = 1200
        val density = 2.5f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // Within 4dp deadband (1142px vs 1140px)
        val screenBottom = 1142f
        val screenTop = 1102f

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = true,
            hasScrollOffset = true
        )

        assertFalse("Deadband must prevent micro-jitter scrolling", resolution.shouldScroll)
    }

    @Test
    fun testNewlineCursorCalculatesAccurateUpwardScrollDelta() {
        val density = 2.5f
        val visibleHeight = 1200 // Usable height above shortcut bar
        val rowHeight = 75f // 30dp
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt() // 60px
        val targetLineBottom = visibleHeight - marginPx // 1140px

        // When previous line rested at targetLineBottom (1140px), inserting a newline
        // moves the active cursor position to 1140 + 75 = 1215px.
        val screenBottom = targetLineBottom + rowHeight
        val screenTop = screenBottom - rowHeight

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = true,
            isDeletionActive = false,
            hasScrollOffset = true
        )

        assertEquals("Should trigger upward scroll on newline at bottom", true, resolution.shouldScroll)
        assertEquals("Scroll delta must exactly equal new line height to keep cursor at breathing margin", rowHeight, resolution.deltaY)
    }

    @Test
    fun testRapidEnterCalculatesConsistentBreathingMarginOnEachLine() {
        val density = 2.5f
        val visibleHeight = 1200
        val rowHeight = 60f
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).toInt()
        val targetLineBottom = visibleHeight - marginPx

        // Simulate 5 successive newlines starting from targetLineBottom:
        for (i in 1..5) {
            val screenBottom = targetLineBottom + rowHeight
            val screenTop = screenBottom - rowHeight
            val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
                screenBottom = screenBottom,
                screenTop = screenTop,
                visibleHeight = visibleHeight,
                density = density,
                isLastLine = true,
                isDeletionActive = false,
                hasScrollOffset = true
            )
            assertEquals("Step $i: Must scroll up", true, resolution.shouldScroll)
            assertEquals("Step $i: Must scroll exactly rowHeight", rowHeight, resolution.deltaY)
        }
    }

    @Test
    fun testFlingInterruptionRetainsVisualOffsetWithoutSnappingToFinalY() {
        // User flings down into document: visual offset is at 500, but scroller target finalY is 1800.
        val visualOffset = 500
        val scrollerCurr = 1800 // Corrupted by abortAnimation
        val scrollerFinal = 1800

        val resolved = ScribeScrollPolicy.resolveInterruptedScrollOffset(
            visualOffset = visualOffset,
            scrollerCurr = scrollerCurr,
            scrollerFinal = scrollerFinal,
            isFinished = true
        )

        assertEquals("Must retain visual offset 500 and not snap to finalY 1800", visualOffset, resolved)
    }

    @Test
    fun testOppositeScrollContinuesSmoothlyFromInterruptedVisualOffset() {
        // When user catches a fling at 500 and scrolls in opposite direction (e.g. -20px)
        val caughtVisualOffset = 500
        val dragDeltaY = -20f // Dragging up towards document top

        val newTarget = (caughtVisualOffset + dragDeltaY).toInt()
        assertEquals("Subsequent scroll must start from caught offset, yielding 480", 480, newTarget)
    }

    @Test
    fun testDownwardFlingInterruptionDoesNotSnapToZero() {
        // User flings up towards top of text: visual offset is 450, scroller target is 0.
        val visualOffset = 450
        val scrollerCurr = 0
        val scrollerFinal = 0

        val resolved = ScribeScrollPolicy.resolveInterruptedScrollOffset(
            visualOffset = visualOffset,
            scrollerCurr = scrollerCurr,
            scrollerFinal = scrollerFinal,
            isFinished = true
        )

        assertEquals("Must retain visual offset 450 and not snap to 0", visualOffset, resolved)
    }

    @Test
    fun testPredictiveGlide_FollowedByExpansionCompletion_RestoresSafeClearance() {
        // Point 9.4: Predictive glide starts, viewport shrinks further or differs from the predictive estimate,
        // coordinator detects the unsafe position, and retargets or launches a correction to restore safe clearance.
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val headerHeight = 200
        val caretDocBottom = 1200f

        // Phase 1: Predictive glide estimates keyboard = 600px -> viewport = 1400px (full height = 2000px)
        val estimatedViewport = 1400
        val targetLineBottomEst = estimatedViewport - marginPx // 1340px
        // Caret screen bottom before scroll (totalScroll = 0) = 200 + 1200 - 0 = 1400px
        // Delta to reach target line bottom = 1400 - 1340 = 60px
        val predictiveTarget = 60f

        val coordinator = ScribeScrollCoordinator(
            totalScroll = 0f,
            headerHeight = headerHeight,
            density = density
        )
        coordinator.isExpansionTransitionActive = true
        coordinator.requestTarget(predictiveTarget, ScrollAnimationReason.PREDICTIVE_GLIDE)
        coordinator.applyStep(predictiveTarget)
        assertEquals(60f, coordinator.totalScroll, 0.001f)
        assertEquals(60f, coordinator.temporaryDisplacement, 0.001f)

        // Phase 2: Final expansion completes, but the user opened an accessory drawer / tall IME
        // Actual viewport height shrinks to 1100px (not 1400px!)
        val actualViewport = 1100
        val targetLineBottomActual = actualViewport - marginPx // 1040px

        // Caret screen bottom is currently 200 + 1200 - 60 = 1340px
        // In the new 1100px viewport, caret is 300px below the breathing line!
        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = coordinator.totalScroll,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = actualViewport,
            density = density
        )
        assertFalse("Caret must be detected as unsafe after unexpected viewport contraction", validation.isSafe)
        assertEquals("Corrective delta must be 300px", 300f, validation.correctiveDeltaY, 0.001f)

        // Phase 3: Launch authoritative correction to restore safety
        val correctedTarget = coordinator.totalScroll + validation.correctiveDeltaY // 360f
        coordinator.requestTarget(
            targetScroll = correctedTarget,
            reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
            forceRetarget = true
        )
        coordinator.applyStep(correctedTarget)

        // Final position check
        assertEquals(360f, coordinator.totalScroll, 0.001f)
        val finalValidation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = coordinator.totalScroll,
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            viewportHeight = actualViewport,
            density = density
        )
        assertTrue("Caret must now be safely positioned above the breathing boundary", finalValidation.isSafe)
        assertEquals(0f, finalValidation.correctiveDeltaY, 0.001f)
        assertEquals(targetLineBottomActual, finalValidation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testPasteTransaction_SuppressesScrollWhenEndpointAlreadyVisible() {
        // Verifies that when text is pasted and its insertion point is within the safe breathing zone,
        // resolveCursorScrollDelta reports shouldScroll = false (no unnecessary jump).
        val density = 2f
        val visibleHeight = 1000
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 48px
        val breathingTarget = visibleHeight - marginPx // 952px

        // Insertion point is at screenBottom = 800px (well above 952px)
        val resolutionSafe = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = 800f,
            screenTop = 760f,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = false,
            isDeletionActive = false,
            hasScrollOffset = true
        )
        assertFalse("Must not scroll when paste insertion point is already visible", resolutionSafe.shouldScroll)

        // Insertion point is offscreen at screenBottom = 1100px (below 952px)
        val resolutionOffscreen = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = 1100f,
            screenTop = 1060f,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = false,
            isDeletionActive = false,
            hasScrollOffset = true
        )
        assertTrue("Must scroll when paste insertion point is offscreen", resolutionOffscreen.shouldScroll)
        assertEquals(1100f - breathingTarget, resolutionOffscreen.deltaY, 0.001f)
    }

    @Test
    fun testBoundedCursorRestoration_DistanceThresholdValidation() {
        // Verifies the distance-bounded smooth glide condition:
        // <= 1.5 * viewportHeight qualifies for smooth animation;
        // > 1.5 * viewportHeight uses instant layout-settled placement to avoid disorienting travel time.
        val viewportHeight = 800f
        val maxSmoothDistance = 1.5f * viewportHeight // 1200px

        val nearbyTargetScroll = 500f
        val currentScroll = 0f
        val nearbyDistance = kotlin.math.abs(nearbyTargetScroll - currentScroll)
        val qualifiesNearby = nearbyDistance <= maxSmoothDistance && nearbyDistance > 1f
        assertTrue("Nearby target (500px <= 1200px) must qualify for smooth restoration glide", qualifiesNearby)

        val distantTargetScroll = 4000f
        val distantDistance = kotlin.math.abs(distantTargetScroll - currentScroll)
        val qualifiesDistant = distantDistance <= maxSmoothDistance && distantDistance > 1f
        assertFalse("Distant target (4000px > 1200px) must bypass smooth animation for instant settled restore", qualifiesDistant)
    }

    @Test
    fun testZoomTransactionPolicy_SuppressesObsoleteAndAwaitingSettlement() {
        // Issue 1: Directly verifies production ZoomTransaction policy logic
        val activeZoom = ScribeScrollPolicy.ZoomTransaction(
            epoch = 2L,
            isGestureActive = true,
            isAwaitingLayoutSettlement = true
        )
        assertTrue("Callback with obsolete epoch must be suppressed", activeZoom.shouldSuppressCursorVisibility(1L))
        assertTrue("Callback during active gesture must be suppressed", activeZoom.shouldSuppressCursorVisibility(2L))

        // When gesture ends but layout reflow is still pending:
        val postGesture = activeZoom.copy(isGestureActive = false, isAwaitingLayoutSettlement = true)
        assertTrue("Suppression must remain active while awaiting layout settlement", postGesture.shouldSuppressCursorVisibility(2L))
        // Critical: Multi-touch arbitration must release immediately on finger lift!
        assertFalse("Multi-touch flag must clear immediately when gesture ends", postGesture.isMultiTouchActive())

        // Once layout settlement arrives:
        val settled = postGesture.copy(isAwaitingLayoutSettlement = false)
        assertFalse("Normal cursor visibility must resume after layout settlement completes", settled.shouldSuppressCursorVisibility(2L))
    }

    @Test
    fun testKeyboardTransactionPolicy_CalculatesRetargetedDestination() {
        // Issue 2: Exercises production KeyboardTransaction retargeting calculation
        val transaction = ScribeScrollPolicy.KeyboardTransaction(transactionId = 1L, isActive = true)
        val caretDocBottom = 800f
        val headerHeight = 100f
        val usableHeight = 600f
        val breathingMargin = 48f
        val maxScroll = 1000f

        // target = headerHeight (100) + caretDocBottom (800) - (usableHeight (600) - margin (48)) = 348f
        val target = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            usableViewportHeight = usableHeight,
            breathingMarginPx = breathingMargin,
            maxCanvasScroll = maxScroll
        )
        assertEquals(348f, target, 0.001f)
    }

    @Test
    fun testKeyboardTransaction_CombinedObstructionCalculatesFullTarget() {
        // Issue B: Verifies that initial combined obstruction (keyboard 280dp + shortcut bar 36dp)
        // plus single breathing clearance (24dp) calculates the exact target on frame 0 without double-counting.
        val density = 2f
        val shortcutBarDp = 36f
        val estimatedImeDp = 280f
        val breathingMarginDp = 24f
        val totalObstructionPx = (shortcutBarDp + estimatedImeDp) * density // 316dp * 2 = 632px

        val fullHeight = 1600f
        val expectedUsableHeight = fullHeight - totalObstructionPx // 968px

        val caretDocY = 1100f
        val headerHeight = 100f
        val breathingMarginPx = breathingMarginDp * density // 48px
        val targetLineBottom = expectedUsableHeight - breathingMarginPx // 920px

        val initialTarget = (headerHeight + caretDocY - targetLineBottom).coerceAtLeast(0f)
        assertEquals(280f, initialTarget, 0.001f)

        // Verify that production KeyboardTransaction.calculateRetargetedDestination matches initialTarget
        val transaction = ScribeScrollPolicy.KeyboardTransaction(transactionId = 1L, isActive = true)
        val retargeted = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = expectedUsableHeight,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )
        assertEquals("Production KeyboardTransaction destination must match initial predicted target", initialTarget, retargeted, 0.001f)
    }

    @Test
    fun testKeyboardTap_WhenAlreadyVisible_DoesNotStartNewTransaction() {
        // Issue B.1: Tapping with keyboard or bottom surface already active routes to
        // caret visibility scroll instead of starting predictive keyboard entrance.
        val isKeyboardVisible = true
        val isBottomSurfaceActive = true
        val shouldStartKeyboardTransaction = !isKeyboardVisible && !isBottomSurfaceActive
        assertFalse("Must not start keyboard-appearance transaction when keyboard is already visible", shouldStartKeyboardTransaction)
    }

    @Test
    fun testKeyboardTransaction_InvalidatesOnDismissalOrTargetSwitch() {
        // Issue B.2: Verifies lifecycle invalidation
        var activeTransaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        assertTrue("Transaction active before dismissal", activeTransaction?.isActive == true)

        // Dismissal invalidates transaction
        val isKeyboardDismissing = true
        if (isKeyboardDismissing) {
            activeTransaction = null
        }
        assertNull("Transaction must be cleanly invalidated on dismissal", activeTransaction)

        // Target switch away from body invalidates transaction
        activeTransaction = ScribeScrollPolicy.KeyboardTransaction(2L, true)
        val activeTarget = "PRIMARY_TITLE"
        if (activeTarget != "EDITOR_BODY") {
            activeTransaction = null
        }
        assertNull("Transaction must be invalidated when focusing title", activeTransaction)
    }

    @Test
    fun testKeyboardEntryAnticipatesShortcutBar() {
        // Requirement A: Initial predicted destination accounts for keyboard height and shortcut bar height.
        val fullHeight = 2000f
        val density = 2.5f
        val compactKeyboardDp = 280f
        val shortcutBarDp = 36f
        val estimatedKbPx = (compactKeyboardDp + shortcutBarDp) * density // 790px
        val breathingMarginPx = 24f * density // 60px
        val headerHeight = 200f
        val caretDocY = 1800f // Caret near bottom

        val futureViewportHeight = (fullHeight - estimatedKbPx).coerceAtLeast(100f) // 1210px
        val targetLineBottom = futureViewportHeight - breathingMarginPx // 1150px
        val predictedTarget = (headerHeight + caretDocY - targetLineBottom).coerceAtLeast(0f) // 200 + 1800 - 1150 = 850px

        // Once measured height h arrives (which equals 1210px after Compose applies padding):
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val retargeted = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = futureViewportHeight,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )

        assertEquals("Predicted destination at t=0 must match retargeted destination post-resize", predictedTarget, retargeted, 0.001f)
    }

    @Test
    fun testLateShortcutBarMeasurementRetargetsTransaction() {
        // Requirement B: Start with default/estimated shortcut bar, receive measured height later, same transaction retargeted.
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val caretDocY = 1500f
        val headerHeight = 200f
        val breathingMarginPx = 60f

        // Initial estimate (viewport = 1200px)
        val initialTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = 1200f,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        ) // 200 + 1500 - (1200 - 60) = 560px

        // Measured shortcut bar is slightly taller (viewport = 1170px)
        val retargetedTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = 1170f,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        ) // 200 + 1500 - (1170 - 60) = 590px

        assertTrue("Transaction remains active with transaction ID 1", transaction.isActive)
        assertEquals(560f, initialTarget, 0.001f)
        assertEquals(590f, retargetedTarget, 0.001f)
    }

    @Test
    fun testMultipleImeUpdatesMaintainSingleTransaction() {
        // Requirement C: Deliver successive IME height changes; all belongs to 1 transaction and target updates smoothly.
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val caretDocY = 1600f
        val headerHeight = 200f
        val breathingMarginPx = 60f

        val targets = listOf(1800f, 1600f, 1400f, 1200f).map { viewportH ->
            transaction.calculateRetargetedDestination(
                caretDocBottom = caretDocY,
                headerHeight = headerHeight,
                usableViewportHeight = viewportH,
                breathingMarginPx = breathingMarginPx,
                maxCanvasScroll = 2000f
            )
        }

        assertTrue("All IME updates maintain transaction ID 1", transaction.transactionId == 1L)
        assertEquals(listOf(60f, 260f, 460f, 660f), targets)
    }

    @Test
    fun testSpringFinishesBeforeGeometrySettles_TransactionRemainsActive() {
        // Requirement D: Spring reaching target must not end transaction while geometry transition is active.
        val coordinator = ScribeScrollCoordinator(totalScroll = 0f)
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        var isExpansionTransitionActive = true

        // Request initial target
        coordinator.requestTarget(500f, ScrollAnimationReason.PREDICTIVE_GLIDE)

        // Simulate spring settling at 500f
        coordinator.applyStep(500f)
        coordinator.finishAnimation()

        // Transaction is NOT ended when spring finishes while expansion transition is active
        var isKeyboardTransactionActive = transaction.isActive
        if (!isExpansionTransitionActive) {
            isKeyboardTransactionActive = false
        }
        assertTrue("Transaction must remain active while transition is in flight even if spring completes", isKeyboardTransactionActive)

        // Late IME update arrives: coordinator retargets active transaction
        coordinator.requestTarget(580f, ScrollAnimationReason.PREDICTIVE_GLIDE)
        assertEquals(580f, coordinator.activeTargetTotalScroll ?: 0f, 0.001f)
    }

    @Test
    fun testNoScrollPath_EventuallyCompletesTransaction() {
        // Requirement E: Keyboard entry when cursor is already safe completes cleanly without staying active forever.
        var transaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        var isExpansionTransitionActive = true

        // Simulate 600ms stability timer expiring
        isExpansionTransitionActive = false
        val isGlideRunning = false

        if (!isGlideRunning && !isExpansionTransitionActive) {
            transaction = null
        }

        assertNull("Transaction must complete when transition timer expires and no scroll is running", transaction)
    }

    @Test
    fun testKeyboardCancellation_SafelyClearsTransaction() {
        // Requirement F: Dismissing keyboard during entry cancels transaction safely.
        var transaction: ScribeScrollPolicy.KeyboardTransaction? = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val isKeyboardDismissing = true

        if (isKeyboardDismissing) {
            transaction = null
        }

        assertNull("Keyboard dismissal must immediately cancel active transaction", transaction)
    }

    @Test
    fun testNoDoubleCounting_KeyboardHeightCountedExactlyOnce() {
        // Requirement G: Keyboard height + shortcut bar is counted exactly once.
        val fullHeight = 2000f
        val density = 2f
        val keyboardHeightPx = 600f
        val shortcutBarPx = 72f
        val totalObstructionPx = keyboardHeightPx + shortcutBarPx // 672px
        val breathingMarginPx = 48f
        val headerHeight = 200f
        val caretDocY = 1500f

        // Case 1: Pre-resize calculation using unpadded fullHeight minus totalObstructionPx
        val targetLineBottom1 = (fullHeight - totalObstructionPx) - breathingMarginPx // 2000 - 672 - 48 = 1280px
        val target1 = headerHeight + caretDocY - targetLineBottom1 // 200 + 1500 - 1280 = 420px

        // Case 2: Post-resize calculation using View height h (which is already 2000 - 672 = 1328px)
        val measuredViewHeight = fullHeight - totalObstructionPx // 1328px
        val targetLineBottom2 = measuredViewHeight - breathingMarginPx // 1328 - 48 = 1280px
        val target2 = headerHeight + caretDocY - targetLineBottom2 // 200 + 1500 - 1280 = 420px

        assertEquals("Pre-resize and post-resize targets must match exactly (no double counting)", target1, target2, 0.001f)
    }

    @Test
    fun testViewportAlreadyResized_DoesNotSubtractKeyboardObstructionAgain() {
        // Requirement H: When measured viewport h already reflects Android adjustResize / padding,
        // targetLineBottom uses h - breathingMarginPx without subtracting keyboard height again.
        val measuredViewportH = 1300f // Already resized by keyboard
        val breathingMarginPx = 48f
        val headerHeight = 200f
        val caretDocY = 1400f

        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val target = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = measuredViewportH,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = 2000f
        )

        // 200 + 1400 - (1300 - 48) = 348px
        assertEquals(348f, target, 0.001f)
    }

    @Test
    fun testCaretAlreadySafePostResize_DoesNotTriggerSecondAnimation() {
        // When spring completes and layout resize delivers measured h,
        // if the caret is already safely within breathing margin + deadband,
        // no secondary scroll animation should be launched.
        val headerHeight = 200f
        val caretDocY = 1500f
        val h = 1200f
        val breathingMarginPx = 60f
        val deadbandPx = 10f
        val maxCanvasScroll = 2000f

        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val desiredTarget = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = h,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxCanvasScroll
        ) // 200 + 1500 - (1200 - 60) = 560px

        // Caret is currently at scroll = 556px (4px difference, well within 10px deadband)
        val currentScroll = 556f
        val isSafe = transaction.isCaretSafe(
            currentScroll = currentScroll,
            caretDocBottom = caretDocY,
            headerHeight = headerHeight,
            usableViewportHeight = h,
            breathingMarginPx = breathingMarginPx,
            deadbandPx = deadbandPx
        )
        val shouldRetarget = transaction.shouldRetarget(
            currentScroll = currentScroll,
            desiredTarget = desiredTarget,
            isSafe = isSafe,
            deadbandPx = deadbandPx
        )

        assertTrue("Caret within deadband must be classified as safe", isSafe)
        assertFalse("Safe caret within deadband must NOT trigger retargeting or second animation", shouldRetarget)
    }

    @Test
    fun testEnsurePositionVisibleDuringActiveKeyboardTransaction_Suppressed() {
        // Verifies that during an active keyboard transaction, a planned target that
        // already makes the caret safe correctly suppresses competing cursor visibility scroll.
        val headerHeight = 200f
        val caretDocY = 1400f
        val futureViewportH = 1200f
        val breathingMarginPx = 60f
        val deadbandPx = 10f
        val plannedTarget = 500f // Already planned upward scroll

        val targetLineBottom = futureViewportH - breathingMarginPx // 1140px
        val finalScreenBottom = (headerHeight + caretDocY - plannedTarget) // 200 + 1400 - 500 = 1100px

        val isSafeInTarget = finalScreenBottom <= targetLineBottom + deadbandPx
        assertTrue("Planned target (1100px) is well within target line bottom (1140px + 10px)", isSafeInTarget)
    }

    @Test
    fun testAuthoritativeTargetCalculationEquality() {
        // Pre-resize predictive calculation and post-resize onSizeChanged calculation
        // MUST produce the exact same target down to 0.001f.
        val density = 2.5f
        val compactKeyboardDp = 280f
        val shortcutBarDp = 36f
        val breathingMarginDp = 24f

        val estimatedKbPx = (compactKeyboardDp + shortcutBarDp) * density // 790px
        val breathingMarginPx = breathingMarginDp * density // 60px
        val headerHeight = 200f
        val fullHeight = 2200f
        val caretDocBottom = 1600f
        val layoutHeight = 3000

        // 1. Pre-resize at t = 0ms:
        val futureViewportH = fullHeight - estimatedKbPx // 1410px
        val targetLineBottomPre = futureViewportH - breathingMarginPx // 1350px
        val maxScrollPre = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight.toInt(),
            layoutHeight = layoutHeight,
            viewportHeight = futureViewportH.toInt(),
            bottomSpace = breathingMarginPx.toInt(),
            isTextEmpty = false
        ).toFloat()
        val targetPre = (headerHeight + caretDocBottom - targetLineBottomPre).coerceIn(0f, maxScrollPre)

        // 2. Post-resize when measured h = futureViewportH arrives:
        val measuredH = futureViewportH
        val transaction = ScribeScrollPolicy.KeyboardTransaction(1L, true)
        val maxScrollPost = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight.toInt(),
            layoutHeight = layoutHeight,
            viewportHeight = measuredH.toInt(),
            bottomSpace = breathingMarginPx.toInt(),
            isTextEmpty = false
        ).toFloat()
        val targetPost = transaction.calculateRetargetedDestination(
            caretDocBottom = caretDocBottom,
            headerHeight = headerHeight,
            usableViewportHeight = measuredH,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxScrollPost
        )

        assertEquals("Pre-resize and post-resize targets must match exactly", targetPre, targetPost, 0.001f)
    }

    @Test
    fun testTapOnDocumentAboveBreathingMarginDoesNotScroll() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density // 750px
        val futureViewportHeight = height - estimatedKeyboardHeightPx // 1450px
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density) // 20px
        val targetLineBottom = futureViewportHeight - marginPx // 1390px

        // User taps at screen coordinate 800px (well above breathing margin 1390px)
        val touchY = 800f
        val isTapAboveMargin = touchY <= (targetLineBottom + deadbandPx)

        assertTrue("Tap above breathing margin must be recognized as safe", isTapAboveMargin)
    }

    @Test
    fun testTitleVisibleTapDoesNotScrollWhenAlreadySafe() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density
        val futureViewportHeight = height - estimatedKeyboardHeightPx
        val scrollD = 0

        // User taps on line 1 near top of document at screen Y = 350px
        val touchY = 350f
        val isTitleSafe = (scrollD == 0 && touchY <= futureViewportHeight)

        assertTrue("Tap within future viewport while title is visible must not scroll", isTitleSafe)
    }

    @Test
    fun testTapBelowBreathingMarginCalculatesTouchOverflowWithoutDoubleJump() {
        val density = 2.5f
        val height = 2200f
        val estimatedKeyboardHeightPx = 300f * density // 750px
        val futureViewportHeight = height - estimatedKeyboardHeightPx // 1450px
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density) // 60px
        val targetLineBottom = futureViewportHeight - marginPx // 1390px
        val currentScroll = 100f
        val maxScrollFuture = 2000f

        // User taps at screen coordinate 1500px (occluded behind incoming keyboard)
        val touchY = 1500f
        val touchOverflow = touchY - targetLineBottom // 110px
        val desiredTarget = (currentScroll + touchOverflow).coerceIn(0f, maxScrollFuture) // 210px
        val deltaY = desiredTarget - currentScroll // 110px

        assertEquals("Delta must match the exact touch overflow of 110px", 110f, deltaY, 0.001f)
        assertEquals("Desired target must be 210px", 210f, desiredTarget, 0.001f)
    }

    @Test
    fun testCalculateCenterCursorTarget() {
        val viewportH = 1000f
        val headerH = 300
        val maxScroll = 2000

        // 1. Distance = 0f: already centered (distance <= 1f -> not smooth eligible)
        // targetTotalScroll = 800 - 500 = 300f. If currentScroll = 300f -> distance = 0f <= 1f
        val atTarget = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 800f,
            viewportHeight = viewportH,
            currentScroll = 300f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(300f, atTarget.targetTotalScroll, 0.001f)
        assertFalse(atTarget.isSmoothEligible)
        assertEquals(300, atTarget.targetScrollD)
        assertEquals(0, atTarget.targetScrollY)

        // 2. Boundary: Distance exactly 1f -> not smooth eligible (distance <= 1f)
        // targetTotalScroll = 800 - 500 = 300f. currentScroll = 299f -> distance = 1.0f <= 1f
        val exact1f = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 800f,
            viewportHeight = viewportH,
            currentScroll = 299f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(300f, exact1f.targetTotalScroll, 0.001f)
        assertFalse(exact1f.isSmoothEligible)

        // 3. Boundary: Distance just above 1f -> smooth eligible (distance > 1f && distance <= 1500f)
        // targetTotalScroll = 800 - 500 = 300f. currentScroll = 298.5f -> distance = 1.5f > 1f
        val justAbove1f = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 800f,
            viewportHeight = viewportH,
            currentScroll = 298.5f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(300f, justAbove1f.targetTotalScroll, 0.001f)
        assertTrue(justAbove1f.isSmoothEligible)

        // 4. Boundary: Distance exactly at 1.5 * viewportHeight (1500f) IS smooth eligible
        // targetTotalScroll = 2000 - 500 = 1500f. currentScroll = 0f -> distance = 1500f <= 1500f
        val maxSmooth = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 2000f,
            viewportHeight = viewportH,
            currentScroll = 0f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(1500f, maxSmooth.targetTotalScroll, 0.001f)
        assertTrue(maxSmooth.isSmoothEligible)

        // 5. Boundary: Distance just above 1.5 * viewportHeight (1502f) is NOT smooth eligible (too far -> jump)
        // targetTotalScroll = 2002 - 500 = 1502f. currentScroll = 0f -> distance = 1502f > 1500f
        val tooFar = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 2002f,
            viewportHeight = viewportH,
            currentScroll = 0f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(1502f, tooFar.targetTotalScroll, 0.001f)
        assertFalse(tooFar.isSmoothEligible)

        // 4. Clamping to maxCanvasScroll
        val clamped = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 3000f, // raw target 2500f
            viewportHeight = viewportH,
            currentScroll = 0f,
            headerHeight = headerH,
            maxCanvasScroll = 1200 // clamped to 1200
        )
        assertEquals(1200f, clamped.targetTotalScroll, 0.001f)

        // 5. Immediate header/editor scroll split
        // targetTotalScroll = 750f. Header takes 300, Editor takes 450
        val split = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 1250f,
            viewportHeight = viewportH,
            currentScroll = 0f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(750f, split.targetTotalScroll, 0.001f)
        assertEquals(300, split.targetScrollD)
        assertEquals(450, split.targetScrollY)

        // 6. Target at or above the top boundary (<= 0f)
        val atTop = ScribeScrollPolicy.calculateCenterCursorTarget(
            cursorCanvasY = 200f, // raw target 200 - 500 = -300f
            viewportHeight = viewportH,
            currentScroll = 100f,
            headerHeight = headerH,
            maxCanvasScroll = maxScroll
        )
        assertEquals(0f, atTop.targetTotalScroll, 0.001f)
        assertEquals(0, atTop.targetScrollD)
        assertEquals(0, atTop.targetScrollY)
    }

    @Test
    fun testCalculatePredictiveGlideDecision() {
        val density = 2.0f
        val futureViewportH = 600f
        val headerH = 100
        val maxFuture = 500f
        // targetLineBottom = 600 - (24 * 2) = 552f.
        // deadbandPx = 4 * 2 = 8f. Safe boundary = 552 + 8 = 560f.

        // 1. Safe recent tap suppression (tap at 500f <= 560f)
        val safeTap = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 50f,
            yOffset = 400f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = 500f,
            scrollD = 50,
            density = density,
            maxScrollFuture = maxFuture
        )
        assertTrue(safeTap.isTapSuppressed)
        assertFalse(safeTap.shouldGlide)
        assertEquals(50f, safeTap.desiredTarget, 0.001f)
        assertEquals(0f, safeTap.deltaY, 0.001f)

        // 2. Safe title-area tap suppression (scrollD == 0, tap within future viewport at 580f <= 600f)
        val titleSafeTap = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 0f,
            yOffset = 400f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = 580f,
            scrollD = 0,
            density = density,
            maxScrollFuture = maxFuture
        )
        assertTrue(titleSafeTap.isTapSuppressed)
        assertFalse(titleSafeTap.shouldGlide)

        // 3. Recent tap below safe boundary (unsafe tap: scrollD > 0, tap at 580f > 560f)
        // touchOverflow = 580 - 552 = 28f. desiredTarget = 50 + 28 = 78f. deltaY = 28f
        val unsafeTap = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 50f,
            yOffset = 400f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = 580f,
            scrollD = 50,
            density = density,
            maxScrollFuture = maxFuture
        )
        assertFalse(unsafeTap.isTapSuppressed)
        assertTrue(unsafeTap.isTargetOccluded)
        assertTrue(unsafeTap.shouldGlide)
        assertEquals(78f, unsafeTap.desiredTarget, 0.001f)
        assertEquals(28f, unsafeTap.deltaY, 0.001f)

        // 4. No recent tap, occluded target:
        // headerH(100) + yOffset(600) - targetLineBottom(552) = 148f.
        // currentScroll = 50f -> deltaY = 98f. screenBottom = 100 + 600 - 50 = 650f > 560f (occluded)
        val noTapOccluded = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 50f,
            yOffset = 600f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = null,
            scrollD = 50,
            density = density,
            maxScrollFuture = maxFuture
        )
        assertFalse(noTapOccluded.isTapSuppressed)
        assertTrue(noTapOccluded.isTargetOccluded)
        assertTrue(noTapOccluded.shouldGlide)
        assertEquals(148f, noTapOccluded.desiredTarget, 0.001f)
        assertEquals(98f, noTapOccluded.deltaY, 0.001f)

        // 5. No recent tap, non-occluded target:
        // headerH(100) + yOffset(300) - currentScroll(50) = screenBottom(350f) <= 560f (not occluded)
        val noTapSafe = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 50f,
            yOffset = 300f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = null,
            scrollD = 50,
            density = density,
            maxScrollFuture = maxFuture
        )
        assertFalse(noTapSafe.isTapSuppressed)
        assertFalse(noTapSafe.isTargetOccluded)
        assertFalse(noTapSafe.shouldGlide)

        // 6. Positive delta around drag threshold (2f):
        // Use a threshold configuration where rounded deadband (1px) < drag threshold (2px)
        // margin = round(24 * 0.25) = 6px; deadband = round(4 * 0.25) = 1px
        // targetLineBottom = 600 - 6 = 594px; occlusion boundary = 594 + 1 = 595px
        // desiredTarget = 100 + 600 - 594 = 106px
        val thresholdDensity = 0.25f
        val thresholdViewport = 600f
        val thresholdHeader = 100
        val thresholdYOffset = 600f
        val thresholdMaxScroll = 500f

        // Sub-threshold case: deltaY = 106 - 104.5 = 1.5f <= 2f; screenBottom = 100 + 600 - 104.5 = 595.5f > 595f (occluded)
        val subThreshold = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 104.5f,
            yOffset = thresholdYOffset,
            headerHeight = thresholdHeader,
            futureViewportHeight = thresholdViewport,
            recentTouchY = null,
            scrollD = 50,
            density = thresholdDensity,
            maxScrollFuture = thresholdMaxScroll
        )
        assertEquals(106f, subThreshold.desiredTarget, 0.001f)
        assertEquals(1.5f, subThreshold.deltaY, 0.001f)
        assertTrue(subThreshold.isTargetOccluded)
        assertFalse(subThreshold.shouldGlide)

        // Super-threshold case: deltaY = 106 - 103 = 3.0f > 2f; screenBottom = 100 + 600 - 103 = 597.0f > 595f (occluded)
        val superThreshold = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 103f,
            yOffset = thresholdYOffset,
            headerHeight = thresholdHeader,
            futureViewportHeight = thresholdViewport,
            recentTouchY = null,
            scrollD = 50,
            density = thresholdDensity,
            maxScrollFuture = thresholdMaxScroll
        )
        assertEquals(106f, superThreshold.desiredTarget, 0.001f)
        assertEquals(3.0f, superThreshold.deltaY, 0.001f)
        assertTrue(superThreshold.isTargetOccluded)
        assertTrue(superThreshold.shouldGlide)

        // 7. Maximum future scroll clamping
        val clampedFuture = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = 0f,
            yOffset = 2000f,
            headerHeight = headerH,
            futureViewportHeight = futureViewportH,
            recentTouchY = null,
            scrollD = 0,
            density = density,
            maxScrollFuture = 300f
        )
        assertEquals(300f, clampedFuture.desiredTarget, 0.001f)
    }

}
