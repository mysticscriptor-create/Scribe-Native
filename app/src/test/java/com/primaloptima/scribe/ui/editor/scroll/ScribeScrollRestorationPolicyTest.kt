package com.primaloptima.scribe.ui.editor.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating ScribeScrollRestorationPolicy: contraction restoration delta, safe target validation, and simulation cycles.
 */
class ScribeScrollRestorationPolicyTest {
    @Test
    fun testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement() {
        // Line 50 in a 100-line document:
        // Prior expansion displaced canvas up by 200px.
        // Viewport expands from 800 to 1100 (contracting keyboard/drawer).
        val visibleHeight = 1100
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val targetLineBottom = visibleHeight - marginPx // 1040px
        val caretScreenBottom = 600f
        val totalScroll = 800f
        val tempDisplacement = 200f

        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = caretScreenBottom,
                caretScreenTop = caretScreenBottom - 50f,
                viewportHeight = visibleHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = tempDisplacement,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue("Should scroll downward to restore displacement", result.shouldScroll)
        assertEquals("Delta must restore the exact 200px displacement", -200f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation should be 0", 0f, result.remainingCompensation, 0.001f)

        // Verify settled caret position satisfies safe boundary
        val settledCaretBottom = caretScreenBottom - result.deltaY // 600 - (-200) = 800px
        assertTrue("Settled caret must rest above target line", settledCaretBottom <= targetLineBottom)
    }

    @Test
    fun testContractionRestoration_EarlyLine_ClampsToTotalScroll() {
        // Line 2 (early line): totalScroll = 80px, but temporaryDisplacement was 150px.
        // Restoration cannot scroll past top of document (totalScroll reaches 0).
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = 300f,
                caretScreenTop = 260f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 80f,
                temporaryDisplacement = 150f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals("Restoration must clamp to -totalScroll (-80px)", -80f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation must retain unapplied 70px", 70f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContractionRestoration_MiddleOfDocument_WithoutDisplacement_RemainsStationary() {
        // Line 50 with no prior displacement recorded (user was typing mid-document).
        // Must remain stationary (returns 0f).
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = 600f,
                caretScreenTop = 560f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 0f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertFalse("Mid-document editing without displacement must not scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
    }

    @Test
    fun testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta() {
        // Keyboard is completely dismissing (Pure Writer stationary invariant):
        // Even if temporary displacement exists and caret is on last line, delta must be 0f!
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = 600f,
                caretScreenTop = 560f,
                viewportHeight = 1500,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 200f,
                isAtLastLine = true,
                isKeyboardDismissing = true,
                isBottomSurfaceActive = false
            )
        )

        assertFalse("Full keyboard dismissal must strictly suppress downward scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
        assertEquals(0f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContractionRestoration_SafeBoundaryClamping_NeverSettlesBehindShortcutBar() {
        // Caret is sitting at 1000px in a 1100px viewport. Breathing margin = 60px (targetLine = 1040px).
        // Slack is only 40px (1040 - 1000 = 40px).
        // If temporaryDisplacement = 120px, full restoration would push caret to 1120px (behind shortcut bar!).
        // The policy MUST clamp downward movement to -40px.
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = 1000f,
                caretScreenTop = 960f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 120f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals("Down movement must be clamped to safe slack (-40px)", -40f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation must track unapplied 80px", 80f, result.remainingCompensation, 0.001f)

        // Assert settled caret bottom is exactly at targetLineBottom (1040px)
        val settledCaretBottom = 1000f - result.deltaY
        assertEquals(1040f, settledCaretBottom, 0.001f)
    }

    @Test
    fun testContractionRestoration_LastLine_AnchorsToBreathingMarginWhenZeroDisplacement() {
        // Caret on last line with 0 displacement: smoothly anchors document end to 24dp breathing margin
        val visibleHeight = 1100
        val density = 2.5f
        val marginPx = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density // 60px
        val targetLineBottom = visibleHeight - marginPx // 1040px
        val caretScreenBottom = 800f
        val totalScroll = 600f

        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = caretScreenBottom,
                caretScreenTop = caretScreenBottom - 40f,
                viewportHeight = visibleHeight,
                density = density,
                totalScroll = totalScroll,
                temporaryDisplacement = 0f,
                isAtLastLine = true,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )

        assertTrue(result.shouldScroll)
        assertEquals(-240f, result.deltaY, 0.001f) // 800 - 1040 = -240
    }

    @Test
    fun testContractionRestoration_DiagnosticsLogging() {
        var loggedMessage: String? = null
        ScribeScrollDiagnostics.isDebugDiagnosticsEnabled = true
        ScribeScrollDiagnostics.onScrollDiagnosticLog = { msg -> loggedMessage = msg }

        try {
            ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
                ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                    caretScreenBottom = 600f,
                    caretScreenTop = 560f,
                    viewportHeight = 1100,
                    density = 2.5f,
                    totalScroll = 500f,
                    temporaryDisplacement = 200f,
                    isAtLastLine = false,
                    isKeyboardDismissing = false,
                    isBottomSurfaceActive = true
                )
            )
            val msg = loggedMessage
            assertTrue("Diagnostic log must be called", msg != null && msg.contains("Applied"))
        } finally {
            ScribeScrollDiagnostics.isDebugDiagnosticsEnabled = false
            ScribeScrollDiagnostics.onScrollDiagnosticLog = null
        }
    }

    // ── Phase 2: Coordinated Restoration & Safe-Boundary Regression Tests ────

    @Test
    fun testContraction_CaretInitiallyBeyondSafeBoundary_CalculatesCorrectiveUpward() {
        // Viewport: 1100, density: 2.5 (margin = 60px, targetLine = 1040px).
        // Caret bottom is at 1080px (40px below breathing margin, inside unsafe zone!).
        // The policy MUST calculate an upward corrective delta (+40px) to restore safe visibility.
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
            ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                caretScreenBottom = 1080f,
                caretScreenTop = 1040f,
                viewportHeight = 1100,
                density = 2.5f,
                totalScroll = 500f,
                temporaryDisplacement = 100f,
                isAtLastLine = false,
                isKeyboardDismissing = false,
                isBottomSurfaceActive = true
            )
        )
        assertTrue("Unsafe caret position must trigger corrective scroll", result.shouldScroll)
        assertEquals("Must scroll upward by 40px to bring caret to safe breathing line", 40f, result.deltaY, 0.001f)
        assertEquals("Temporary displacement should remain unconsumed by upward correction", 100f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContraction_TargetValidation_ClampsUnsafeTarget() {
        // Header height: 200, caretDocBottom: 1500, viewport: 1100, density: 2.5 (targetLine = 1040).
        // Target total scroll = 600.
        // Final caret screen bottom = 200 + 1500 - 600 = 1100 (which is 60px below targetLine 1040!).
        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = 600f,
            caretDocBottom = 1500f,
            headerHeight = 200,
            viewportHeight = 1100,
            density = 2.5f
        )
        assertFalse("Target that places caret at 1100px must be flagged unsafe", validation.isSafe)
        assertEquals("Corrective delta must be +60px", 60f, validation.correctiveDeltaY, 0.001f)
        val safeTarget = 600f + validation.correctiveDeltaY // 660f
        val revalidation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = safeTarget,
            caretDocBottom = 1500f,
            headerHeight = 200,
            viewportHeight = 1100,
            density = 2.5f
        )
        assertTrue("Adjusted target must be safe", revalidation.isSafe)
        assertEquals(1040f, revalidation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testContractionSimulation_RepeatedViewportHeightChanges_NoOvershoot() {
        // Controller simulates drawer collapsing across intermediate layout frames:
        // h = 800 -> 850 -> 920 -> 1000 -> 1100
        val controller = ContractionSimulationController(
            totalScroll = 800f,
            temporaryDisplacement = 200f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1200f,
            headerHeight = 200,
            density = 2.5f
        )

        // Frame 1: h = 850
        controller.onViewportHeightChanged(850, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 2: h = 920
        controller.onViewportHeightChanged(920, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 3: h = 1000
        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Frame 4: h = 1100 (final settled height)
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)

        // Target scroll must be exactly 800 - 200 = 600f (not 800 - 200*4 = 0 overshoot!)
        assertEquals("Target scroll must be exactly 600f without cumulative overshoot", 600f, controller.targetTotalScroll, 0.001f)

        // Animate to completion
        controller.applyAnimationStep(1.0f)
        assertEquals(600f, controller.totalScroll, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)
    }

    @Test
    fun testContractionSimulation_NewTargetArrivingBeforeOldAnimationFinishes_RetargetsSmoothly() {
        // Animation is in flight (40% completed) when another layout frame arrives
        val controller = ContractionSimulationController(
            totalScroll = 900f,
            temporaryDisplacement = 200f,
            caretLine = 30,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1400f,
            headerHeight = 200,
            density = 2.5f
        )

        // Frame 1: Viewport 1000
        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Advance animation by 40%
        controller.applyAnimationStep(0.4f)
        val intermediateScroll = controller.totalScroll
        val intermediateDisplacement = controller.temporaryDisplacement
        assertTrue("Intermediate scroll must have advanced downward", intermediateScroll < 900f)
        assertTrue("Temporary displacement must have decremented in lockstep", intermediateDisplacement < 200f)

        // Frame 2: Viewport expands further to 1100
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)

        // Finish remaining animation
        controller.applyAnimationStep(1.0f)
        assertEquals("Settles at exact target without jumps", 700f, controller.totalScroll, 0.001f)
        assertEquals("Displacement completely restored", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_AnimationCancellation_ReconcilesActualAppliedMovement() {
        val controller = ContractionSimulationController(
            totalScroll = 800f,
            temporaryDisplacement = 150f,
            caretLine = 20,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        // Animate 33% (50px out of 150px)
        controller.applyAnimationStep(0.33333334f)
        assertEquals(750f, controller.totalScroll, 0.5f)
        assertEquals("Only the applied 50px was deducted", 100f, controller.temporaryDisplacement, 0.5f)

        // Interrupted/cancelled
        controller.cancelRestoration()
        assertFalse(controller.isRestorationActive)
        assertEquals("Unapplied displacement (100px) remains accurately tracked", 100f, controller.temporaryDisplacement, 0.5f)
    }

    @Test
    fun testContractionSimulation_TickByTickAccounting_LockstepPrecision() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 120f,
            caretLine = 15,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 900f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        val initialScroll = controller.totalScroll
        val initialDisp = controller.temporaryDisplacement

        // 3 sequential steps
        controller.applyAnimationStep(0.25f)
        val step1Applied = initialScroll - controller.totalScroll
        assertEquals(initialDisp - step1Applied, controller.temporaryDisplacement, 0.01f)

        controller.applyAnimationStep(0.5f)
        val step2Applied = initialScroll - controller.totalScroll
        assertEquals(initialDisp - step2Applied, controller.temporaryDisplacement, 0.01f)

        controller.applyAnimationStep(1.0f)
        assertEquals(480f, controller.totalScroll, 0.01f)
        assertEquals(0f, controller.temporaryDisplacement, 0.01f)
    }

    @Test
    fun testContractionSimulation_RepeatedExpandCollapseCycles_NoAccumulatedDisplacement() {
        val controller = ContractionSimulationController(
            totalScroll = 500f,
            temporaryDisplacement = 0f,
            caretLine = 10,
            caretCol = 2,
            isSelected = false,
            caretDocBottom = 800f,
            headerHeight = 200,
            density = 2.5f
        )

        // Cycle 1: Expansion records 100px
        controller.temporaryDisplacement = 100f
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        controller.applyAnimationStep(1.0f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(400f, controller.totalScroll, 0.001f)

        // Cycle 2: Another expansion records 80px
        controller.temporaryDisplacement = 80f
        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        controller.applyAnimationStep(1.0f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(320f, controller.totalScroll, 0.001f)
    }

    @Test
    fun testContractionSimulation_UserTouch_InvalidatesStaleCompensation() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1100, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        assertTrue(controller.isRestorationActive)

        // User touches screen
        controller.onUserTouch()
        assertFalse("Restoration must be immediately cancelled on user touch", controller.isRestorationActive)
        assertEquals("Displacement must be invalidated on user touch", 0f, controller.temporaryDisplacement, 0.001f)
        assertEquals(-1, controller.caretLine)
    }

    @Test
    fun testContractionSimulation_CaretMovement_InvalidatesStaleCompensation() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        // Caret moves to line 26
        controller.onCaretMoved(newLine = 26, newCol = 10, newSelected = false)
        assertEquals("Displacement must be invalidated when caret moves to another line", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_ContentEdits_InvalidatesStaleCompensation() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        // Content changed
        controller.onContentEdited()
        assertEquals("Displacement must be invalidated when document text is edited", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContractionSimulation_FullKeyboardDismissal_StrictlyStationary() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 150f,
            caretLine = 25,
            caretCol = 10,
            isSelected = false,
            caretDocBottom = 1000f,
            headerHeight = 200,
            density = 2.5f
        )

        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1500,
            isAtLastLine = true,
            isKeyboardDismissing = true,
            isBottomSurfaceActive = false
        )
        assertFalse("Full keyboard dismissal must not scroll", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)
        assertEquals(600f, controller.totalScroll, 0.001f)
    }

    @Test
    fun testContraction_Preserves1DOFInvariant_DuringRestorationSteps() {
        val headerHeight = 200
        val maxCanvasScroll = 1000
        var scrollD = 200f // Header fully hidden
        var editorOffsetY = 300 // Inner editor scrolled down 300px
        val totalScrollBefore = scrollD + editorOffsetY // 500px

        // Downward restoration delta of -250px (moves content down towards 0)
        var alloc = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -250f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()

        // 1-DOF rule: Inner editor neutralizes from 300 down to 50; outer canvas stays strictly at 200!
        assertEquals("Inner editor must consume all -250px", 50, editorOffsetY)
        assertEquals("Outer canvas must remain at headerHeight (200) while editor > 0", 200f, scrollD, 0.001f)

        // Next restoration step: delta of -100px (crosses boundary into header reveal)
        alloc = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = -100f,
            headerHeight = headerHeight,
            scrollD = scrollD,
            editorOffsetY = editorOffsetY,
            maxCanvasScroll = maxCanvasScroll
        )
        scrollD += alloc.outerDelta
        editorOffsetY += alloc.innerDelta.toInt()

        // Inner editor absorbs -50px down to 0; remaining -50px reveals header from 200 to 150!
        assertEquals("Inner editor must be strictly 0", 0, editorOffsetY)
        assertEquals("Outer canvas reveals header down to 150", 150f, scrollD, 0.001f)
        assertEquals("Zero unconsumed delta across boundary", 0f, alloc.unconsumedDelta, 0.001f)
    }

    @Test
    fun testContraction_KeyboardExpansion_FollowedByNormalHeightRestoration() {
        // Standard keyboard (viewport 1100) expands to emoji keyboard (viewport 800).
        // Document scrolls up by 150px to keep line 20 visible.
        val controller = ContractionSimulationController(
            totalScroll = 500f,
            temporaryDisplacement = 0f,
            caretLine = 20,
            caretCol = 4,
            isSelected = false,
            caretDocBottom = 800f,
            headerHeight = 200,
            density = 2.5f
        )

        // Expansion phase: upward scroll records 150px displacement
        controller.totalScroll = 650f
        controller.temporaryDisplacement = 150f

        // Keyboard contracts back to standard height (viewport 1100)
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1100,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )

        assertTrue("Contraction to standard height must trigger downward restoration", result.shouldScroll)
        assertEquals(-150f, result.deltaY, 0.001f)
        assertEquals(500f, controller.targetTotalScroll, 0.001f)

        // Animate to completion
        controller.applyAnimationStep(1.0f)
        assertEquals("Total scroll restored to exact pre-expansion position", 500f, controller.totalScroll, 0.001f)
        assertEquals("Temporary displacement cleared", 0f, controller.temporaryDisplacement, 0.001f)

        // Caret screen bottom = 200 + 800 - 500 = 500px, which has 540px clearance above safe line (1040px)
        val finalCaretBottom = 200f + 800f - controller.totalScroll
        assertTrue("Caret must settle comfortably above breathing line", finalCaretBottom <= 1040f)
    }

    @Test
    fun testContraction_AccessoryDrawerExpansion_FollowedByNormalHeightRestoration() {
        // Compact accessory (viewport 950) expands to full drawer (viewport 650).
        // Upward scroll of 220px is recorded.
        val controller = ContractionSimulationController(
            totalScroll = 400f,
            temporaryDisplacement = 0f,
            caretLine = 18,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 750f,
            headerHeight = 200,
            density = 2.5f
        )

        // Expansion phase:
        controller.totalScroll = 620f
        controller.temporaryDisplacement = 220f

        // Drawer collapses back to compact accessory (viewport 950)
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 950,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )

        assertTrue(result.shouldScroll)
        assertEquals(-220f, result.deltaY, 0.001f)
        assertEquals(400f, controller.targetTotalScroll, 0.001f)

        controller.applyAnimationStep(1.0f)
        assertEquals(400f, controller.totalScroll, 0.001f)
        assertEquals(0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContraction_SelectionChangeDuringTransition_InvalidatesStaleCompensation() {
        val controller = ContractionSimulationController(
            totalScroll = 700f,
            temporaryDisplacement = 180f,
            caretLine = 22,
            caretCol = 5,
            isSelected = false,
            caretDocBottom = 1100f,
            headerHeight = 200,
            density = 2.5f
        )

        // User extends selection while in expanded state
        controller.onCaretMoved(newLine = 22, newCol = 15, newSelected = true)

        assertEquals("Displacement must be invalidated on selection change", 0f, controller.temporaryDisplacement, 0.001f)
        assertFalse(controller.isRestorationActive)

        // Viewport collapses later: must NOT restore obsolete displacement
        val result = controller.onViewportHeightChanged(
            newViewportHeight = 1100,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        assertFalse("Must remain stationary when compensation was invalidated", result.shouldScroll)
        assertEquals(0f, result.deltaY, 0.001f)
    }

    @Test
    fun testContraction_ManualScrollDuringExpansion_InvalidatesCompensation() {
        val controller = ContractionSimulationController(
            totalScroll = 600f,
            temporaryDisplacement = 120f,
            caretLine = 15,
            caretCol = 0,
            isSelected = false,
            caretDocBottom = 950f,
            headerHeight = 200,
            density = 2.5f
        )

        controller.onViewportHeightChanged(1000, isAtLastLine = false, isKeyboardDismissing = false, isBottomSurfaceActive = true)
        assertTrue(controller.isRestorationActive)

        // User touches / drags the canvas
        controller.onUserTouch()
        assertFalse("Restoration must be cancelled immediately", controller.isRestorationActive)
        assertEquals("Displacement must be 0f", 0f, controller.temporaryDisplacement, 0.001f)
    }

    @Test
    fun testContraction_PartialRestoration_RetainsOnlySafelyAppliableCompensation() {
        // Document has caret close to bottom. Total scroll = 400.
        // Caret doc bottom = 1200. Header = 200.
        // Viewport = 1100, density = 2.5 -> targetLineBottom = 1040.
        // Current caret bottom = 200 + 1200 - 400 = 1000px.
        // Available slack to 1040 is 40px!
        // Suppose temporaryDisplacement was 150px.
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = 1000f,
            caretScreenTop = 960f,
            viewportHeight = 1100,
            density = 2.5f,
            totalScroll = 400f,
            temporaryDisplacement = 150f,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)
        assertTrue(result.shouldScroll)
        assertEquals("Downward movement clamped to available slack (-40px)", -40f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation is 110px", 110f, result.remainingCompensation, 0.001f)

        // Validate final target safety
        val target = 400f + result.deltaY // 360f
        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(target, 1200f, 200, 1100, 2.5f)
        assertTrue("Settled target at breathing margin is safe", validation.isSafe)
        assertEquals(1040f, validation.finalCaretScreenBottom, 0.001f)
    }

    @Test
    fun testContractionRestoration_ExpandedKeyboardToNormal_AuthorizesAndRestores() {
        // Taller emoji keyboard contracting to standard keyboard: destination surface active via visible keyboard
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = 700f,
            caretScreenTop = 660f,
            viewportHeight = 1100,
            density = 2.5f,
            totalScroll = 600f,
            temporaryDisplacement = 140f,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)
        assertTrue("Restoration must be authorized when destination surface remains active", result.shouldScroll)
        assertEquals("Must restore exact 140px upward displacement", -140f, result.deltaY, 0.001f)
        assertEquals("Remaining compensation must drop to 0", 0f, result.remainingCompensation, 0.001f)
    }

    @Test
    fun testContractionRestoration_DrawerToKeyboardHandoff_AuthorizesAndRestores() {
        // Expanded drawer closed via Snip/Tmpl returning to keyboard: destination surface remains active
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = 800f,
            caretScreenTop = 760f,
            viewportHeight = 1200,
            density = 2.0f,
            totalScroll = 500f,
            temporaryDisplacement = 200f,
            isAtLastLine = false,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )
        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)
        assertTrue("Restoration must be authorized during drawer-to-keyboard handoff", result.shouldScroll)
        assertEquals("Must restore 200px displacement", -200f, result.deltaY, 0.001f)
    }

}
