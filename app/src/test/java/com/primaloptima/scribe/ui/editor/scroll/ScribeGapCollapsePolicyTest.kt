package com.primaloptima.scribe.ui.editor.scroll

import androidx.compose.ui.geometry.Rect
import com.primaloptima.scribe.ui.editor.scroll.ScribeScrollDeletionPolicy.DeletionAnchorIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite A — Phase 1 Policy, Overlay Registry, Single Safety Floor & Exact Equations Tests.
 *
 * Covers Suite A cases 1–4.
 * Note: Suite A case 5 (`ScribeDeletionSnapshotCoordinator.onAfterInsert()` returning
 * `ClearTransactionAndClampOverscroll`) is Phase 2-dependent and verified upon Phase 2 completion.
 */
class ScribeGapCollapsePolicyTest {

    private fun makeValidViewportBounds(
        top: Float = 0f,
        bottom: Float = 1000f,
        hasModalBlocker: Boolean = false,
        floatingPills: List<Rect> = emptyList()
    ): UnobstructedViewportBounds = UnobstructedViewportBounds(
        headerBottomLocal = top,
        stickyBottomLocal = 0f,
        maxTopBarBottomLocal = 0f,
        minBottomBarTopLocal = bottom,
        windowVisibleBottomLocal = bottom,
        visibleEditorTop = top,
        visibleEditorBottom = bottom,
        usableHeight = (bottom - top).coerceAtLeast(0f),
        hasModalBlocker = hasModalBlocker,
        isValid = !hasModalBlocker && bottom > top,
        floatingPillLocalRects = floatingPills
    )

    private fun planWithDefaults(
        deletionIntent: DeletionAnchorIntent = DeletionAnchorIntent.CARET_ANCHOR,
        isSelection: Boolean = true,
        isWordwrap: Boolean = true,
        isWordwrapReady: Boolean = true,
        isLayoutBusy: Boolean = false,
        isComposing: Boolean = false,
        isCursorAnimationRunning: Boolean = false,
        hasRtlOrBidiAtSeam: Boolean = false,
        isMergedPartialRowSeam: Boolean = false,
        hasSurvivingUpper: Boolean = true,
        hasSurvivingLower: Boolean = true,
        deltaGapPx: Float = 120f,
        visibilityConfidence: VisibilityConfidence = VisibilityConfidence.RESOLVED,
        isUpperVisible: Boolean = true,
        isLowerVisible: Boolean = true,
        isTouchedOnScreen: Boolean = true,
        preScrollY: Int = 500,
        centeredTargetScroll: Float = 450f,
        viewportBounds: UnobstructedViewportBounds = makeValidViewportBounds(),
        safetyBounds: SafetyFloorAndBounds = SafetyFloorAndBounds(
            safeMax = 2000,
            minSafeScroll = 0f,
            safeMinPhysical = 0,
            actualPhysicalScroll = preScrollY
        )
    ): CollapsePlan = ScribeGapCollapsePolicy.planCollapse(
        deletionIntent = deletionIntent,
        isSelection = isSelection,
        isWordwrap = isWordwrap,
        isWordwrapReady = isWordwrapReady,
        isLayoutBusy = isLayoutBusy,
        isComposing = isComposing,
        isCursorAnimationRunning = isCursorAnimationRunning,
        hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam,
        isMergedPartialRowSeam = isMergedPartialRowSeam,
        hasSurvivingUpper = hasSurvivingUpper,
        hasSurvivingLower = hasSurvivingLower,
        deltaGapPx = deltaGapPx,
        visibilityConfidence = visibilityConfidence,
        isUpperVisible = isUpperVisible,
        isLowerVisible = isLowerVisible,
        isTouchedOnScreen = isTouchedOnScreen,
        preScrollY = preScrollY,
        centeredTargetScroll = centeredTargetScroll,
        viewportBounds = viewportBounds,
        safetyBounds = safetyBounds
    )

    // ── Case 1: (U, L, T) truth table, V1 Eligibility Gate & Scenario A regimes ──

    @Test
    fun testTruthTable_AllUltCombinations() {
        assertEquals(CollapseScenario.B_LOWER_ONLY, planWithDefaults(isUpperVisible = false, isLowerVisible = true, isTouchedOnScreen = false).scenario)
        assertEquals(CollapseScenario.B_LOWER_ONLY, planWithDefaults(isUpperVisible = false, isLowerVisible = true, isTouchedOnScreen = true).scenario)

        assertEquals(CollapseScenario.C_BOTH_VISIBLE, planWithDefaults(isUpperVisible = true, isLowerVisible = true, isTouchedOnScreen = false).scenario)
        assertEquals(CollapseScenario.C_BOTH_VISIBLE, planWithDefaults(isUpperVisible = true, isLowerVisible = true, isTouchedOnScreen = true).scenario)

        assertEquals(CollapseScenario.A_UPPER_ONLY, planWithDefaults(isUpperVisible = true, isLowerVisible = false, isTouchedOnScreen = false).scenario)
        assertEquals(CollapseScenario.A_UPPER_ONLY, planWithDefaults(isUpperVisible = true, isLowerVisible = false, isTouchedOnScreen = true).scenario)

        val dPlan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            deltaGapPx = 3000f,
            viewportBounds = makeValidViewportBounds(top = 0f, bottom = 1000f)
        )
        assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, dPlan.scenario)

        val dOverCap = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            deltaGapPx = 3001f,
            viewportBounds = makeValidViewportBounds(top = 0f, bottom = 1000f)
        )
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, dOverCap.scenario)

        val ePlan = planWithDefaults(isUpperVisible = false, isLowerVisible = false, isTouchedOnScreen = false)
        assertEquals(CollapseScenario.E_OFFSCREEN_CARET_FOLLOW, ePlan.scenario)
        assertTrue(ePlan.requiresOffscreenCaretFollow)
        assertFalse(ePlan.requiresMode1)
    }

    @Test
    fun testEligibilityGate_RejectsCursorAnimationAndInvalidPreconditions() {
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isCursorAnimationRunning = true).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(deletionIntent = DeletionAnchorIntent.TAIL_ANCHOR).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(deletionIntent = DeletionAnchorIntent.RESET_TO_TOP).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isSelection = false).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isWordwrap = false).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isWordwrapReady = false).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isLayoutBusy = true).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isComposing = true).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(hasRtlOrBidiAtSeam = true).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(isMergedPartialRowSeam = true).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(hasSurvivingUpper = false).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(hasSurvivingLower = false).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(deltaGapPx = 0.5f).scenario)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, planWithDefaults(visibilityConfidence = VisibilityConfidence.UNRELIABLE).scenario)
    }

    @Test
    fun testVisibilityConfidence_RequiresAllLayoutAndUnobstructedConditions() {
        val valid = makeValidViewportBounds()
        assertEquals(
            VisibilityConfidence.RESOLVED,
            ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
                isCanvasLaidOut = true,
                isEditorLaidOut = true,
                canvasWidthPx = 1080,
                canvasHeightPx = 1920,
                isWordwrapReady = true,
                isLayoutBusy = false,
                hasPreLayout = true,
                viewportBounds = valid
            )
        )
        assertEquals(
            VisibilityConfidence.UNRELIABLE,
            ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
                isCanvasLaidOut = true,
                isEditorLaidOut = true,
                canvasWidthPx = 1080,
                canvasHeightPx = 1920,
                isWordwrapReady = true,
                isLayoutBusy = false,
                hasPreLayout = true,
                viewportBounds = makeValidViewportBounds(hasModalBlocker = true)
            )
        )
        assertEquals(
            VisibilityConfidence.UNRELIABLE,
            ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
                isCanvasLaidOut = false,
                isEditorLaidOut = true,
                canvasWidthPx = 1080,
                canvasHeightPx = 1920,
                isWordwrapReady = true,
                isLayoutBusy = false,
                hasPreLayout = true,
                viewportBounds = valid
            )
        )
        assertEquals(
            VisibilityConfidence.UNRELIABLE,
            ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
                isCanvasLaidOut = true,
                isEditorLaidOut = true,
                canvasWidthPx = 1080,
                canvasHeightPx = 1920,
                isWordwrapReady = false,
                isLayoutBusy = false,
                hasPreLayout = true,
                viewportBounds = valid
            )
        )
        assertEquals(
            VisibilityConfidence.UNRELIABLE,
            ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
                isCanvasLaidOut = true,
                isEditorLaidOut = true,
                canvasWidthPx = 1080,
                canvasHeightPx = 1920,
                isWordwrapReady = true,
                isLayoutBusy = true,
                hasPreLayout = true,
                viewportBounds = valid
            )
        )
    }

    @Test
    fun testScenarioA_AllSafetyRegimes() {
        // 1. A-Stationary: S_pre inside [safeMinPhysical, safeMax] -> skip Mode 1, Mode 2 closes deltaGap
        val stationary = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 200,
            deltaGapPx = 45f,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 900,
                minSafeScroll = 150.2f,
                safeMinPhysical = 151,
                actualPhysicalScroll = 200
            )
        )
        assertEquals(CollapseScenario.A_UPPER_ONLY, stationary.scenario)
        assertFalse(stationary.requiresMode1)
        assertFalse(stationary.requiresSynchronousSnap)
        assertEquals(200f, stationary.effectiveTargetScroll, 0.001f)
        assertEquals(45f, stationary.initialTauLowerPx, 0.001f)

        // 2. A-MinSafeClamped < 1px: synchronous snap S_pre -> safeMinPhysical, no Mode 1
        val sub1pxFloor = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 100,
            deltaGapPx = 12f,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 900,
                minSafeScroll = 100.4f,
                safeMinPhysical = 101,
                actualPhysicalScroll = 100
            )
        )
        assertEquals(CollapseScenario.A_UPPER_ONLY, sub1pxFloor.scenario)
        assertFalse(sub1pxFloor.requiresMode1)
        assertTrue(sub1pxFloor.requiresSynchronousSnap)
        assertEquals(101, sub1pxFloor.synchronousSnapTarget)
        assertEquals(12f, sub1pxFloor.initialTauLowerPx, 0.001f)
        assertFalse(sub1pxFloor.isProgressCoupled)

        // 3. A-MinSafeClamped >= 1px: Mode 1 scrolls S_pre -> safeMinPhysical with progress-coupled tauLower
        val ge1pxFloor = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 100,
            deltaGapPx = 80f,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 900,
                minSafeScroll = 120.4f,
                safeMinPhysical = 121,
                actualPhysicalScroll = 100
            )
        )
        assertEquals(CollapseScenario.A_UPPER_ONLY, ge1pxFloor.scenario)
        assertTrue(ge1pxFloor.requiresMode1)
        assertFalse(ge1pxFloor.requiresSynchronousSnap)
        assertTrue(ge1pxFloor.isProgressCoupled)
        assertEquals(121f, ge1pxFloor.effectiveTargetScroll, 0.001f)
        assertEquals(80f, ScribeGapCollapsePolicy.computeMode1TauLower(ge1pxFloor, 100f), 0.001f)
        assertEquals(0f, ScribeGapCollapsePolicy.computeMode1TauLower(ge1pxFloor, 121f), 0.001f)

        // 4. A-MaxClamped (S_pre > safeMax): Mode 1 scrolls S_pre -> safeMax using lower-anchor tauLower
        val maxClamped = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 750,
            deltaGapPx = 100f,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 700,
                minSafeScroll = 100f,
                safeMinPhysical = 100,
                actualPhysicalScroll = 750
            )
        )
        assertEquals(CollapseScenario.A_UPPER_ONLY, maxClamped.scenario)
        assertTrue(maxClamped.requiresMode1)
        assertFalse(maxClamped.requiresSynchronousSnap)
        assertFalse(maxClamped.isProgressCoupled)
        assertEquals(700f, maxClamped.effectiveTargetScroll, 0.001f)
        assertEquals(50f, ScribeGapCollapsePolicy.computeMode1TauLower(maxClamped, 700f), 0.001f)
    }

    // ── Case 2: Canonical integer safeMinPhysical & transient overscroll assertions ──

    @Test
    fun testCanonicalSafeMinPhysicalTable_AndPhysicalReadbackValidation() {
        val cases = listOf(
            Triple(100.4f, 100, 101),
            Triple(100.9f, 100, 101),
            Triple(101.0f, 100, 101),
            Triple(101.3f, 100, 102),
            Triple(101.3f, 101, 102),
            Triple(101.3f, 102, 102)
        )
        val headerHeight = 100
        val usableBottom = 900f
        val breathingMargin = 20f
        val safeMax = 900

        for ((targetMinSafe, sPre, expectedSafeMin) in cases) {
            // minSafeScroll = headerHeight + anchorDocBottom + breathingMargin - usableBottom
            val anchorDocBottom = targetMinSafe - headerHeight - breathingMargin + usableBottom
            val bounds = ScribeGapCollapsePolicy.resolveSafetyFloorAndBounds(
                headerHeight = headerHeight,
                anchorDocBottom = anchorDocBottom,
                breathingMarginPx = breathingMargin,
                usableBottomForSafety = usableBottom,
                maxCanvasScrollPost = safeMax,
                scrollD = sPre,
                editorOffsetY = 0
            )
            assertEquals(targetMinSafe, bounds.minSafeScroll, 0.001f)
            assertEquals(expectedSafeMin, bounds.safeMinPhysical)
            assertEquals(sPre >= expectedSafeMin, bounds.isPhysicalScrollSafe(sPre))
            assertTrue(bounds.isPhysicalScrollSafe(expectedSafeMin))

            // validateSettledDeletionSafety must reject actualPhysicalScroll < safeMinPhysical
            val validationAtPre = ScribeScrollDeletionPolicy.validateSettledDeletionSafety(
                currentTotalScroll = sPre.toFloat(),
                headerHeight = headerHeight,
                caretDocBottom = anchorDocBottom,
                layoutHeight = 2000f,
                usableViewportHeight = usableBottom.toInt(),
                breathingMarginPx = breathingMargin,
                maxCanvasScroll = safeMax,
                isTailAnchored = false,
                usableBottomForSafety = usableBottom
            )
            if (sPre < expectedSafeMin) {
                assertFalse("S=$sPre < safeMinPhysical=$expectedSafeMin must be unsafe", validationAtPre.isSafe)
                assertEquals(expectedSafeMin.toFloat(), validationAtPre.correctiveTargetScroll, 0.001f)
            } else {
                assertTrue("S=$sPre >= safeMinPhysical=$expectedSafeMin must be safe", validationAtPre.isSafe)
            }
        }
    }

    @Test
    fun testTransientOverscroll_ClampedToSafeMaxByPlanAndSafetyValidation() {
        val safetyBounds = ScribeGapCollapsePolicy.resolveSafetyFloorAndBounds(
            headerHeight = 100,
            anchorDocBottom = 500f,
            breathingMarginPx = 20f,
            usableBottomForSafety = 900f,
            maxCanvasScrollPost = 700,
            scrollD = 700,
            editorOffsetY = 250 // transient S_actual = 950 > safeMax = 700
        )
        assertEquals(700, safetyBounds.safeMax)
        assertEquals(950, safetyBounds.actualPhysicalScroll)
        assertFalse(safetyBounds.isPhysicalScrollSafe())

        val overscrollPlan = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 950,
            deltaGapPx = 100f,
            safetyBounds = safetyBounds
        )
        assertEquals(950f, overscrollPlan.requestedTargetScroll, 0.001f)
        assertEquals(700f, overscrollPlan.effectiveTargetScroll, 0.001f)
        assertTrue(overscrollPlan.requiresMode1)

        val overscrollValidation = ScribeScrollDeletionPolicy.validateSettledDeletionSafety(
            currentTotalScroll = 950f,
            headerHeight = 100,
            caretDocBottom = 500f,
            layoutHeight = 1400f,
            usableViewportHeight = 900,
            breathingMarginPx = 20f,
            maxCanvasScroll = 700,
            isTailAnchored = false,
            usableBottomForSafety = 900f
        )
        assertFalse(overscrollValidation.isSafe)
        assertEquals(700f, overscrollValidation.correctiveTargetScroll, 0.001f)
    }

    // ── Case 3: Single safety floor (TOP_BAR + BOTTOM_BAR vs FLOATING_PILL) & registry ──

    @Test
    fun testOverlayRegistry_SingleSafetyFloorVsFloatingPill_AndLifecycleCleanup() {
        val registry = ScribeOverlayBoundsRegistry()
        // Canvas positioned at window Y in [100, 1100], X in [0, 1080]
        registry.update("find_replace_bar", OverlayOcclusionRole.TOP_BAR, Rect(0f, 100f, 1080f, 180f))
        registry.update("shortcut_bar", OverlayOcclusionRole.BOTTOM_BAR, Rect(0f, 980f, 1080f, 1100f))
        registry.update("word_count_pill", OverlayOcclusionRole.FLOATING_PILL, Rect(850f, 400f, 1040f, 460f))
        // Horizontally disjoint side panel must not occlude canvas
        registry.update("expanded_side_panel", OverlayOcclusionRole.MODAL_BLOCKER, Rect(1080f, 0f, 1800f, 1200f))

        val bounds = registry.resolveUnobstructedViewportBounds(
            canvasWindowTopPx = 100f,
            canvasHeightPx = 1000f,
            headerBottomLocal = 40f,
            stickyBottomLocal = 0f,
            windowVisibleBottomLocal = 1000f,
            canvasWindowLeftPx = 0f,
            canvasWidthPx = 1080f
        )
        assertEquals(80f, bounds.maxTopBarBottomLocal, 0.001f)
        assertEquals(80f, bounds.visibleEditorTop, 0.001f)
        assertEquals(880f, bounds.minBottomBarTopLocal, 0.001f)
        assertEquals(880f, bounds.visibleEditorBottom, 0.001f)
        assertEquals(800f, bounds.usableHeight, 0.001f)
        assertFalse(bounds.hasModalBlocker)
        assertTrue(bounds.isValid)
        // FLOATING_PILL does not shrink visibleEditorBottom, but is testable against handle rect
        assertEquals(1, bounds.floatingPillLocalRects.size)
        assertTrue(bounds.overlapsAnyFloatingPill(Rect(900f, 320f, 950f, 350f)))
        assertFalse(bounds.overlapsAnyFloatingPill(Rect(100f, 320f, 150f, 350f)))

        // Removing BOTTOM_BAR immediately restores full bottom safety bound
        registry.remove("shortcut_bar")
        val afterHide = registry.resolveUnobstructedViewportBounds(
            canvasWindowTopPx = 100f,
            canvasHeightPx = 1000f,
            headerBottomLocal = 40f,
            windowVisibleBottomLocal = 1000f,
            canvasWindowLeftPx = 0f,
            canvasWidthPx = 1080f
        )
        assertEquals(1000f, afterHide.visibleEditorBottom, 0.001f)

        // Adding overlapping MODAL_BLOCKER invalidates bounds
        registry.update("compact_drawer_scrim", OverlayOcclusionRole.MODAL_BLOCKER, Rect(0f, 0f, 1080f, 1200f))
        val modalBounds = registry.resolveUnobstructedViewportBounds(
            canvasWindowTopPx = 100f,
            canvasHeightPx = 1000f,
            headerBottomLocal = 40f,
            windowVisibleBottomLocal = 1000f,
            canvasWindowLeftPx = 0f,
            canvasWidthPx = 1080f
        )
        assertTrue(modalBounds.hasModalBlocker)
        assertFalse(modalBounds.isValid)
    }

    // ── Case 4: Mode-1 shortfall, Scenario-D / Branch handoff velocity & Mode-2 monotonicity ──

    @Test
    fun testLowerAnchorShortfall_PositiveZeroAndNegative() {
        // Positive shortfall: S_pre = 500, deltaG = 300, safeMinPhysical = 350 -> shortfall = +150
        val posBounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 349.4f, safeMinPhysical = 350, actualPhysicalScroll = 500)
        val posPlan = planWithDefaults(isUpperVisible = false, isLowerVisible = true, preScrollY = 500, deltaGapPx = 300f, safetyBounds = posBounds)
        assertEquals(350f, posPlan.effectiveTargetScroll, 0.001f)
        assertEquals(150f, ScribeGapCollapsePolicy.computeMode1TauLower(posPlan, 350f), 0.001f)
        assertEquals(150f, ScribeGapCollapsePolicy.computeShortfallPx(300f, 500, 350f), 0.001f)

        // Zero shortfall: S_pre = 500, deltaG = 300, safeMinPhysical = 100 -> S_target = 200 -> shortfall = 0
        val zeroBounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 100f, safeMinPhysical = 100, actualPhysicalScroll = 500)
        val zeroPlan = planWithDefaults(isUpperVisible = true, isLowerVisible = true, preScrollY = 500, deltaGapPx = 300f, safetyBounds = zeroBounds)
        assertEquals(200f, zeroPlan.effectiveTargetScroll, 0.001f)
        assertEquals(0f, ScribeGapCollapsePolicy.computeMode1TauLower(zeroPlan, 200f), 0.001f)
        assertEquals(0f, ScribeGapCollapsePolicy.computeShortfallPx(300f, 500, 200f), 0.001f)

        // Negative shortfall (overscroll): S_pre = 950, deltaG = 100, safeMax = 700 -> tau saturates at 0 at 850..700
        val negBounds = SafetyFloorAndBounds(safeMax = 700, minSafeScroll = 100f, safeMinPhysical = 100, actualPhysicalScroll = 950)
        val negPlan = planWithDefaults(isUpperVisible = false, isLowerVisible = true, preScrollY = 950, deltaGapPx = 100f, safetyBounds = negBounds)
        assertEquals(700f, negPlan.effectiveTargetScroll, 0.001f)
        assertEquals(50f, ScribeGapCollapsePolicy.computeMode1TauLower(negPlan, 900f), 0.001f)
        assertEquals(0f, ScribeGapCollapsePolicy.computeMode1TauLower(negPlan, 850f), 0.001f)
        assertEquals(0f, ScribeGapCollapsePolicy.computeMode1TauLower(negPlan, 700f), 0.001f)
        assertEquals(-150f, ScribeGapCollapsePolicy.computeShortfallPx(100f, 950, 700f), 0.001f)
    }

    @Test
    fun testBranches1And2_Sub1PxRegimesAndFrame0Displacements() {
        // Branch 1: S_pre in [safeMinPhysical, safeMax], 0 < abs(S_effective - S_pre) < 1f
        val branch1 = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 100,
            centeredTargetScroll = 100.4f,
            deltaGapPx = 12f,
            safetyBounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 50f, safeMinPhysical = 50, actualPhysicalScroll = 100)
        )
        assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, branch1.scenario)
        assertFalse(branch1.requiresMode1)
        assertFalse(branch1.requiresSynchronousSnap)
        assertEquals(12f, branch1.initialTauLowerPx, 0.001f)

        // Branch 2: S_pre = 100, minSafeScroll = 100.4f -> safeMinPhysical = 101, deltaGap = 12f
        val branch2Bounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 100.4f, safeMinPhysical = 101, actualPhysicalScroll = 100)
        val branch2B = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = true,
            preScrollY = 100,
            deltaGapPx = 12f,
            safetyBounds = branch2Bounds
        )
        assertTrue(branch2B.requiresSynchronousSnap)
        assertFalse(branch2B.requiresMode1)
        assertEquals(101, branch2B.synchronousSnapTarget)
        assertEquals(13f, branch2B.initialTauLowerPx, 0.001f)
        // Frame-0 Lower displacement: -deltaS + tauLower(0) - deltaG = -1 + 13 - 12 = 0f
        val deltaLower0B = -(101f - 100f) + branch2B.initialTauLowerPx - 12f
        assertEquals(0f, deltaLower0B, 0.001f)

        val branch2A = planWithDefaults(
            isUpperVisible = true,
            isLowerVisible = false,
            preScrollY = 100,
            deltaGapPx = 12f,
            safetyBounds = branch2Bounds
        )
        assertTrue(branch2A.requiresSynchronousSnap)
        assertFalse(branch2A.requiresMode1)
        assertEquals(101, branch2A.synchronousSnapTarget)
        assertEquals(12f, branch2A.initialTauLowerPx, 0.001f)
        // Frame-0 Lower displacement in A/D: -1 + 12 - 12 = -1f
        val deltaLower0A = -(101f - 100f) + branch2A.initialTauLowerPx - 12f
        assertEquals(-1f, deltaLower0A, 0.001f)
    }

    @Test
    fun testMode2HandoffVelocityGuards_AcrossAllSpanAndSnapCases() {
        val omega2 = ScribeGapCollapsePolicy.MODE2_DEFAULT_OMEGA_RAD_PER_SEC
        val safeBounds = SafetyFloorAndBounds(safeMax = 2000, minSafeScroll = 0f, safeMinPhysical = 0, actualPhysicalScroll = 500)

        // 1. Scenario D with S_effectiveTarget == S_pre
        val dZeroSpan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 500,
            centeredTargetScroll = 500f,
            deltaGapPx = 80f,
            safetyBounds = safeBounds
        )
        assertFalse(dZeroSpan.requiresMode1)
        val vZeroSpan = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dZeroSpan,
            mode1ActuallyStarted = false,
            smoothedPhysicalVelocityPxPerSec = -500f,
            tauResumePx = 80f
        )
        assertEquals(0f, vZeroSpan, 0.0001f)

        // 2. Scenario D with nonzero physical span < 1px (0.4px)
        val dSub1PxSpan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 500,
            centeredTargetScroll = 499.6f,
            deltaGapPx = 80f,
            safetyBounds = safeBounds
        )
        assertFalse(dSub1PxSpan.requiresMode1)
        val vSub1PxEvenIfStarted = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dSub1PxSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -500f,
            tauResumePx = 80f
        )
        assertEquals(0f, vSub1PxEvenIfStarted, 0.0001f)

        // 3. Scenario D with Mode-1 span of exactly 1px
        val dExact1PxSpan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 500,
            centeredTargetScroll = 499f,
            deltaGapPx = 10f,
            safetyBounds = safeBounds
        )
        assertTrue(dExact1PxSpan.requiresMode1)
        val vExact1Px = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dExact1PxSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -60f,
            tauResumePx = 8f
        )
        assertTrue(vExact1Px.isFinite())
        assertTrue(vExact1Px in 0f..omega2)

        // 4. Scenario D with normal Mode-1 span
        val dNormalSpan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 500,
            centeredTargetScroll = 300f,
            deltaGapPx = 100f,
            safetyBounds = safeBounds
        )
        assertTrue(dNormalSpan.requiresMode1)
        val vNormalD = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dNormalSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -1000f,
            tauResumePx = 50f
        )
        // -(100 / -200) * (-1000) = -500; -(-500) / 50 = 10f in [0, 13.5]
        assertEquals(10f, vNormalD, 0.001f)
        assertTrue(vNormalD in 0f..omega2)

        // 5. Synchronous safety snap followed by Mode 2 -> progressVelocity = 0f
        val snapBounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 100.4f, safeMinPhysical = 101, actualPhysicalScroll = 100)
        val snapPlan = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = false,
            isTouchedOnScreen = true,
            preScrollY = 100,
            centeredTargetScroll = 90f,
            deltaGapPx = 24f,
            safetyBounds = snapBounds
        )
        assertTrue(snapPlan.requiresSynchronousSnap)
        val vSnap = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = snapPlan,
            mode1ActuallyStarted = false,
            smoothedPhysicalVelocityPxPerSec = -500f,
            tauResumePx = snapPlan.initialTauLowerPx
        )
        assertEquals(0f, vSnap, 0.0001f)
        assertTrue(vSnap.isFinite() && vSnap in 0f..omega2)
    }

    @Test
    fun testPhysicalVelocityEma_RetainsPreBoundaryVelocityOnClampedFrame() {
        var ema: Float? = null
        ema = ScribeGapCollapsePolicy.updateObservedPhysicalVelocityEma(
            previousSmoothedVelocity = ema,
            observedDeltaPx = -10f,
            dtSec = 0.016f,
            boundaryHit = false
        )
        assertNotNull(ema)
        val preBoundary = ema!!
        // Boundary hit frame must retain pre-boundary velocity
        val afterBoundary = ScribeGapCollapsePolicy.updateObservedPhysicalVelocityEma(
            previousSmoothedVelocity = ema,
            observedDeltaPx = -1f,
            dtSec = 0.016f,
            boundaryHit = true
        )
        assertEquals(preBoundary, afterBoundary!!, 0.0001f)
        // Unclamped frame with < 0.5px quantization delta does not corrupt EMA
        val afterSubPixel = ScribeGapCollapsePolicy.updateObservedPhysicalVelocityEma(
            previousSmoothedVelocity = afterBoundary,
            observedDeltaPx = 0f,
            dtSec = 0.016f,
            boundaryHit = false
        )
        assertEquals(preBoundary, afterSubPixel!!, 0.0001f)
        assertNull(
            ScribeGapCollapsePolicy.updateObservedPhysicalVelocityEma(
                previousSmoothedVelocity = null,
                observedDeltaPx = -10f,
                dtSec = 0.016f,
                boundaryHit = true
            )
        )
    }

    @Test
    fun testMode2ClosedForm_StrictlyNonIncreasingWithZeroOvershoot() {
        val omega = ScribeGapCollapsePolicy.MODE2_DEFAULT_OMEGA_RAD_PER_SEC
        val initialTau = 150f
        val dts = floatArrayOf(0.001f, 0.008f, 0.016f, 0.032f, 0.016f, 0.025f, 0.032f, 0.032f)

        // Frame-0 dt uses refresh rate when lastFrameTimeNs == 0L
        assertEquals(1f / 120f, ScribeGapCollapsePolicy.computeFrameDtSec(1_000_000_000L, 0L, 120f), 0.0001f)
        assertEquals(0.032f, ScribeGapCollapsePolicy.computeFrameDtSec(200_000_000L, 100_000_000L, 60f), 0.0001f)

        for (v0 in listOf(0f, omega * 0.5f, omega)) {
            var prevTau = initialTau
            var elapsed = 0f
            var settled = false
            var steps = 0
            while (!settled && steps < 200) {
                val dt = dts[steps % dts.size]
                val step = ScribeGapCollapsePolicy.stepMode2ClosedForm(
                    tauAtResumePx = initialTau,
                    previousElapsedTimeSec = elapsed,
                    dtSec = dt,
                    progressVelocity = v0,
                    omega = omega
                )
                assertTrue("Mode 2 must be non-increasing (v0=$v0)", step.nextTauPx <= prevTau + 0.0001f)
                assertTrue("Mode 2 must never overshoot below 0 (v0=$v0)", step.nextTauPx >= 0f)
                prevTau = step.nextTauPx
                elapsed = step.nextElapsedTimeSec
                settled = step.isSettled
                steps++
            }
            assertTrue("Mode 2 must settle within 200 frames", settled)
            assertEquals(0f, prevTau, 0.0001f)
        }
    }

    @Test
    fun testIncrementalSeamRebase_Rules() {
        // Unchanged seam doc Y preserves phase & tau
        val preserved = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 80f,
            preEditSeamDocY = 400f,
            newSeamLine = 10,
            newSeamCol = 0,
            newSeamFirstLowerRow = 12,
            postSeamDocY = 400.2f
        )
        assertTrue(preserved is SeamMappingResult.PreservePhase)
        assertEquals(80f, (preserved as SeamMappingResult.PreservePhase).preservedTauLowerPx, 0.001f)

        // Small upward insertion reduces tau by deltaYInsert
        val rebased = ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
            isWordwrapReady = true,
            isLayoutBusy = false,
            preEditTauPx = 80f,
            preEditSeamDocY = 400f,
            newSeamLine = 11,
            newSeamCol = 0,
            newSeamFirstLowerRow = 13,
            postSeamDocY = 432f
        )
        assertTrue(rebased is SeamMappingResult.RebaseTau)
        assertEquals(48f, (rebased as SeamMappingResult.RebaseTau).residualTauLowerPx, 0.001f)

        // Exceeding tau, negative shift, non-zero col, or async layout requires fallback
        assertTrue(
            ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
                isWordwrapReady = true,
                isLayoutBusy = false,
                preEditTauPx = 80f,
                preEditSeamDocY = 400f,
                newSeamLine = 12,
                newSeamCol = 0,
                newSeamFirstLowerRow = 15,
                postSeamDocY = 490f
            ) is SeamMappingResult.FallbackRequired
        )
        assertTrue(
            ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
                isWordwrapReady = true,
                isLayoutBusy = false,
                preEditTauPx = 80f,
                preEditSeamDocY = 400f,
                newSeamLine = 10,
                newSeamCol = 4,
                newSeamFirstLowerRow = 12,
                postSeamDocY = 400f
            ) is SeamMappingResult.FallbackRequired
        )
        assertTrue(
            ScribeGapCollapsePolicy.evaluateIncrementalSeamRebase(
                isWordwrapReady = false,
                isLayoutBusy = false,
                preEditTauPx = 80f,
                preEditSeamDocY = 400f,
                newSeamLine = 10,
                newSeamCol = 0,
                newSeamFirstLowerRow = 12,
                postSeamDocY = 400f
            ) is SeamMappingResult.FallbackRequired
        )
    }
}
