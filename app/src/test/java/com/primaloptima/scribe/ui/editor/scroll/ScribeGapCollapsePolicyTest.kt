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
 * Suite A — Phase 1 & Phase 2 Policy, Overlay Registry, Single Safety Floor, Exact Equations,
 * `ScribeDeletionSnapshotCoordinator.onAfterInsert()`, and Case I / Case II Seam Geometry Tests.
 *
 * Covers Suite A cases 1–5 plus Phase 2 Case I / Case II pure geometry & RTL seam rules.
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
        assertTrue(dZeroSpan.isProgressCoupled)
        val vZeroSpan = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dZeroSpan,
            mode1ActuallyStarted = false,
            smoothedPhysicalVelocityPxPerSec = -500f,
            tauResumePx = 80f
        )
        assertEquals(0f, vZeroSpan, 0.0001f)
        val vZeroSpanEvenIfStarted = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dZeroSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -500f,
            tauResumePx = 80f
        )
        assertEquals(0f, vZeroSpanEvenIfStarted, 0.0001f)

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
        assertTrue(dSub1PxSpan.isProgressCoupled)
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
        assertTrue(dExact1PxSpan.isProgressCoupled)
        val vExact1Px = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dExact1PxSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -60f,
            tauResumePx = 8f
        )
        assertTrue(vExact1Px.isFinite())
        assertTrue(vExact1Px in 0f..omega2)
        // Verify exact progress-coupled value at 1px when unsaturated: -(10 / -1) * (-0.8) / 8 = 1.0f
        val vExact1PxUnsaturated = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = dExact1PxSpan,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -0.8f,
            tauResumePx = 8f
        )
        assertEquals(1.0f, vExact1PxUnsaturated, 0.0001f)
        assertTrue(vExact1PxUnsaturated in 0f..omega2)

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

        // 6. Lower-anchor B/C preserves direct physical velocity handoff when Mode 1 ran,
        // and blocks nonzero handoff when Mode 1 was not required
        val lowerAnchorMode1 = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = true,
            preScrollY = 500,
            deltaGapPx = 300f,
            safetyBounds = SafetyFloorAndBounds(safeMax = 900, minSafeScroll = 350f, safeMinPhysical = 350, actualPhysicalScroll = 500)
        )
        assertTrue(lowerAnchorMode1.requiresMode1)
        assertFalse(lowerAnchorMode1.isProgressCoupled)
        val vLowerAnchor = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = lowerAnchorMode1,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -600f,
            tauResumePx = 150f
        )
        assertEquals(4f, vLowerAnchor, 0.0001f)
        assertTrue(vLowerAnchor in 0f..omega2)

        val lowerAnchorSub1Px = planWithDefaults(
            isUpperVisible = false,
            isLowerVisible = true,
            preScrollY = 500,
            deltaGapPx = 0.7f,
            safetyBounds = safeBounds
        )
        assertFalse(lowerAnchorSub1Px.requiresMode1)
        val vLowerAnchorSub1Px = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = lowerAnchorSub1Px,
            mode1ActuallyStarted = true,
            smoothedPhysicalVelocityPxPerSec = -600f,
            tauResumePx = 0.7f
        )
        assertEquals(0f, vLowerAnchorSub1Px, 0.0001f)
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

    @Test
    fun testSuiteACase5_SnapshotCoordinatorOnAfterInsert_NonEmptyAndEmptyReplace() {
        val coordinator = ScribeDeletionSnapshotCoordinator()

        // 1. Non-empty replacement while NOT overscrolled -> ClearTransaction (and resets isInContentReplace)
        coordinator.onBeforeReplace()
        assertTrue(coordinator.isInContentReplace)
        coordinator.onAfterDelete(afterDeleteEndLine = 8, hasPendingDeletionSettlement = false)
        assertTrue(coordinator.deletedDuringCurrentReplace)
        val nonOverscrolled = coordinator.onAfterInsert(
            insertedNotEmpty = true,
            isTextEmpty = false,
            currentTotalScroll = 300,
            maxCanvasScroll = 500,
            hasPendingOrActiveTransaction = true
        )
        assertFalse(coordinator.isInContentReplace)
        assertEquals(ScribeDeletionSnapshotCoordinator.AfterInsertAction.ClearTransaction, nonOverscrolled)

        // 2. Non-empty replacement while OVERSCROLLED -> ClearTransactionAndClampOverscroll (never None)
        coordinator.onBeforeReplace()
        coordinator.onAfterDelete(afterDeleteEndLine = 15, hasPendingDeletionSettlement = false)
        val overscrolled = coordinator.onAfterInsert(
            insertedNotEmpty = true,
            isTextEmpty = false,
            currentTotalScroll = 900,
            maxCanvasScroll = 500,
            hasPendingOrActiveTransaction = true
        )
        assertFalse(coordinator.isInContentReplace)
        assertEquals(
            ScribeDeletionSnapshotCoordinator.AfterInsertAction.ClearTransactionAndClampOverscroll,
            overscrolled
        )

        // 3. Non-empty replacement on isTextEmpty edge -> ClearTransaction
        coordinator.onBeforeReplace()
        coordinator.onAfterDelete(afterDeleteEndLine = 0, hasPendingDeletionSettlement = false)
        val emptyDocEdge = coordinator.onAfterInsert(
            insertedNotEmpty = true,
            isTextEmpty = true,
            currentTotalScroll = 200,
            maxCanvasScroll = 0,
            hasPendingOrActiveTransaction = true
        )
        assertFalse(coordinator.isInContentReplace)
        assertEquals(ScribeDeletionSnapshotCoordinator.AfterInsertAction.ClearTransaction, emptyDocEdge)

        // 4. Empty replacement ("") -> SettleEmptyReplaceDelete (never clears prematurely)
        coordinator.onBeforeReplace()
        coordinator.onAfterDelete(afterDeleteEndLine = 10, hasPendingDeletionSettlement = false)
        val emptyReplace = coordinator.onAfterInsert(
            insertedNotEmpty = false,
            isTextEmpty = false,
            currentTotalScroll = 500,
            maxCanvasScroll = 500,
            hasPendingOrActiveTransaction = true
        )
        assertFalse(coordinator.isInContentReplace)
        assertEquals(ScribeDeletionSnapshotCoordinator.AfterInsertAction.SettleEmptyReplaceDelete, emptyReplace)
    }

    @Test
    fun testPreAndPostDeleteSeamGeometry_CaseI_And_CaseII_MatchesSpec() {
        val rowHeight = 40f

        // 1. Case I (C2 < preLineLen(L2)) with C1 == 0 -> eligible, seamLine = startLine, seamCol = 0
        val caseIC0Pre = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 3,
            c1 = 0,
            l2 = 8,
            c2 = 4,
            preLineCount = 20,
            preLineLenL2 = 12,
            preScrollY = 100,
            rowHeightPx = rowHeight,
            rowIndexAt = { line, col -> if (line == 8 && col == 4) 14 else -1 }
        )
        assertEquals(DeletionSeamCase.CASE_I_MID_LINE_OR_WHOLE_LINE, caseIC0Pre.seamCase)
        assertFalse(caseIC0Pre.isMergedPartialRowSeam)
        assertTrue(caseIC0Pre.hasSurvivingUpper)
        assertTrue(caseIC0Pre.hasSurvivingLower)
        assertEquals(14 * rowHeight, caseIC0Pre.preFirstLowerRowTopDocY, 0.001f)

        val caseIC0Post = ScribeGapCollapsePolicy.resolvePostDeleteSeamGeometry(
            preCapture = caseIC0Pre,
            postStartLine = 3,
            postLineCount = 15,
            rowHeightPx = rowHeight,
            postRowIndexAt = { line, col -> if (line == 3 && col == 0) 5 else -1 }
        )
        assertTrue(caseIC0Post.isEligibleCase)
        assertEquals(3, caseIC0Post.seamLine)
        assertEquals(0, caseIC0Post.seamCol)
        assertEquals(5, caseIC0Post.seamFirstLowerRow)
        assertEquals(5 * rowHeight, caseIC0Post.activeSeamDocY, 0.001f)
        assertEquals(9 * rowHeight, caseIC0Post.deltaGapPx, 0.001f)

        // 2. Case I (C2 < preLineLen(L2)) with C1 > 0 -> V1 partial-line seam fallback (no horizontal split)
        val caseIC1Gt0Pre = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 3,
            c1 = 2,
            l2 = 8,
            c2 = 4,
            preLineCount = 20,
            preLineLenL2 = 12,
            preScrollY = 100,
            rowHeightPx = rowHeight,
            rowIndexAt = { _, _ -> 14 }
        )
        assertTrue(caseIC1Gt0Pre.isMergedPartialRowSeam)
        val caseIC1Gt0Post = ScribeGapCollapsePolicy.resolvePostDeleteSeamGeometry(
            preCapture = caseIC1Gt0Pre,
            postStartLine = 3,
            postLineCount = 15,
            rowHeightPx = rowHeight,
            postRowIndexAt = { _, _ -> 5 }
        )
        assertFalse(caseIC1Gt0Post.isEligibleCase)
        assertTrue(caseIC1Gt0Post.isMergedPartialRowSeam)

        // 3. Case II (C2 == preLineLen(L2)) with surviving L2 + 1 (valid even when C1 > 0)
        val caseIIPre = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 4,
            c1 = 5,
            l2 = 7,
            c2 = 10,
            preLineCount = 12,
            preLineLenL2 = 10,
            preScrollY = 120,
            rowHeightPx = rowHeight,
            rowIndexAt = { line, col -> if (line == 8 && col == 0) 11 else -1 }
        )
        assertEquals(DeletionSeamCase.CASE_II_LINE_END, caseIIPre.seamCase)
        assertFalse(caseIIPre.isMergedPartialRowSeam)
        assertTrue(caseIIPre.hasSurvivingUpper)
        assertTrue(caseIIPre.hasSurvivingLower)
        assertEquals(11 * rowHeight, caseIIPre.preFirstLowerRowTopDocY, 0.001f)

        val caseIIPost = ScribeGapCollapsePolicy.resolvePostDeleteSeamGeometry(
            preCapture = caseIIPre,
            postStartLine = 4,
            postLineCount = 9,
            rowHeightPx = rowHeight,
            postRowIndexAt = { line, col -> if (line == 5 && col == 0) 6 else -1 }
        )
        assertTrue(caseIIPost.isEligibleCase)
        assertEquals(5, caseIIPost.seamLine)
        assertEquals(0, caseIIPost.seamCol)
        assertEquals(6, caseIIPost.seamFirstLowerRow)
        assertEquals(6 * rowHeight, caseIIPost.activeSeamDocY, 0.001f)
        assertEquals(5 * rowHeight, caseIIPost.deltaGapPx, 0.001f)

        // 4. Case II at EOF (L2 + 1 >= preLineCount) -> hasSurvivingLower = false fallback
        val caseIIEofPre = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 4,
            c1 = 0,
            l2 = 11,
            c2 = 10,
            preLineCount = 12,
            preLineLenL2 = 10,
            preScrollY = 120,
            rowHeightPx = rowHeight,
            rowIndexAt = { _, _ -> 15 }
        )
        assertFalse(caseIIEofPre.hasSurvivingLower)
        val caseIIEofPost = ScribeGapCollapsePolicy.resolvePostDeleteSeamGeometry(
            preCapture = caseIIEofPre,
            postStartLine = 4,
            postLineCount = 5,
            rowHeightPx = rowHeight,
            postRowIndexAt = { _, _ -> 4 }
        )
        assertFalse(caseIIEofPost.isEligibleCase)
        assertFalse(caseIIEofPost.hasSurvivingLower)

        // 5. RTL / Bidi detection at seam triggers fallback
        assertTrue(ScribeGapCollapsePolicy.containsRtlOrBidi("hello \u0645\u0631\u062d\u0628\u0627"))
        assertFalse(ScribeGapCollapsePolicy.containsRtlOrBidi("plain english prose 123"))
    }

    // ── V1 Behavioral Corrections: Handle Visibility, Case II, Span Touched & Animator Guard ──

    @Test
    fun testComputeHandleRectDoc_SideDropAndDropAlignments() {
        val leftSideDrop = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 120f,
            anchorYDoc = 400f,
            rowTopDocY = 360f,
            rowBottomDocY = 400f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_LEFT
        )
        assertEquals(76f, leftSideDrop.leftDocX, 0.001f)
        assertEquals(120f, leftSideDrop.rightDocX, 0.001f)
        assertEquals(400f, leftSideDrop.topDocY, 0.001f)
        assertEquals(444f, leftSideDrop.bottomDocY, 0.001f)

        val rightSideDrop = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 120f,
            anchorYDoc = 400f,
            rowTopDocY = 360f,
            rowBottomDocY = 400f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        assertEquals(120f, rightSideDrop.leftDocX, 0.001f)
        assertEquals(164f, rightSideDrop.rightDocX, 0.001f)

        val centeredDrop = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 120f,
            anchorYDoc = 400f,
            rowTopDocY = 360f,
            rowBottomDocY = 400f,
            handleWidthPx = 40f,
            handleHeightPx = 60f,
            alignment = HandleHorizontalAlignment.ALIGN_CENTER
        )
        assertEquals(100f, centeredDrop.leftDocX, 0.001f)
        assertEquals(140f, centeredDrop.rightDocX, 0.001f)
        assertEquals(460f, centeredDrop.bottomDocY, 0.001f)
    }

    @Test
    fun testIsHandleVisibleInViewport_ClippingDrawWindowAndPills() {
        val pillTopRight = Rect(left = 540f, top = 120f, right = 700f, bottom = 200f)
        val bounds = makeValidViewportBounds(
            top = 100f,
            bottom = 860f,
            floatingPills = listOf(pillTopRight)
        )
        val headerH = 160
        val preScrollY = 400 // preEditorOffsetY = 240, headerRemaining = -240

        // Visible mid-screen unobstructed handle: row 300..340 -> doc handle 340..384 -> local 100..144
        val visibleHandle = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 200f,
            anchorYDoc = 380f,
            rowTopDocY = 340f,
            rowBottomDocY = 380f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        assertTrue(
            ScribeGapCollapsePolicy.isHandleVisibleInViewport(
                handleDoc = visibleHandle,
                preScrollX = 0,
                preScrollY = preScrollY,
                headerHeight = headerH,
                editorHeightPx = 900,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )

        // Horizontally scrolled offscreen left
        assertFalse(
            ScribeGapCollapsePolicy.isHandleVisibleInViewport(
                handleDoc = visibleHandle,
                preScrollX = 250,
                preScrollY = preScrollY,
                headerHeight = headerH,
                editorHeightPx = 900,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )

        // Clipped behind TOP_BAR (visibleEditorTop = 100f): rowBottom = 335 -> handle 335..379 -> local 95..139;
        // Wait: at headerRemaining = -240, handle top = 95, bottom = 139 overlaps visibleEditorTop = 100.
        // Place row at 245..285 (within draw window >= 240) with handle 285..329 -> local 45..89 <= visibleEditorTop 100 -> false!
        val behindTopBar = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 200f,
            anchorYDoc = 285f,
            rowTopDocY = 245f,
            rowBottomDocY = 285f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        assertFalse(
            ScribeGapCollapsePolicy.isHandleVisibleInViewport(
                handleDoc = behindTopBar,
                preScrollX = 0,
                preScrollY = preScrollY,
                headerHeight = headerH,
                editorHeightPx = 900,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )

        // Draw-window cutoff: rowBottomDocY <= preEditorOffsetY (238 <= 240), even if handle extends into >= 240!
        val outsideDrawWindow = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 200f,
            anchorYDoc = 238f,
            rowTopDocY = 198f,
            rowBottomDocY = 238f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        assertFalse(
            ScribeGapCollapsePolicy.isHandleVisibleInViewport(
                handleDoc = outsideDrawWindow,
                preScrollX = 0,
                preScrollY = preScrollY,
                headerHeight = headerH,
                editorHeightPx = 900,
                viewportWidthPx = 720f,
                viewportBounds = makeValidViewportBounds(top = 0f, bottom = 860f)
            )
        )

        // Occluded by tight FLOATING_PILL in top-right corner, whereas mid-row handle is unobstructed
        val underRightPill = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 560f,
            anchorYDoc = 380f,
            rowTopDocY = 340f,
            rowBottomDocY = 380f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        // local rect: left = 560, right = 604, top = -240 + 380 = 140, bottom = 184 -> inside pill [540..700, 120..200]
        assertFalse(
            ScribeGapCollapsePolicy.isHandleVisibleInViewport(
                handleDoc = underRightPill,
                preScrollX = 0,
                preScrollY = preScrollY,
                headerHeight = headerH,
                editorHeightPx = 900,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )
    }

    @Test
    fun testCaseII_LowerHandleVisibleWhileNextLineOffscreenBelow_ClassifiedAsB_NotAOrE() {
        val headerH = 160
        val preScrollY = 500
        val rowHeight = 40f
        // Bounds: visible [0, 740]; editor local window has docY visible up to preScrollY - headerH + 740 = 1080
        val bounds = makeValidViewportBounds(top = 0f, bottom = 740f)

        // Case II (L1=10, C1=4, L2=27, C2=preLineLen(L2)):
        // Upper handle at L1 (docY = 400 -> local 60) -> offscreen above draw window? Wait: preEditorOffsetY = 340, local = 400 + 160 - 500 = 60 -> visible.
        // Test both B_LOWER_ONLY (upper offscreen at docY = 200) and C_BOTH_VISIBLE (upper visible).
        // L2 row ends at docY = 1060 -> local anchorY = 160 - 500 + 1060 = 720 (< 740, visible!),
        // whereas L2+1 row starts at docY = 1080 -> local = 740 (at/below bottom edge)!
        val lowerHandleAtL2 = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 260f,
            anchorYDoc = 1060f,
            rowTopDocY = 1020f,
            rowBottomDocY = 1060f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_RIGHT
        )
        val upperHandleOffscreenAbove = ScribeGapCollapsePolicy.computeHandleRectDoc(
            anchorXDoc = 80f,
            anchorYDoc = 200f,
            rowTopDocY = 160f,
            rowBottomDocY = 200f,
            handleWidthPx = 44f,
            handleHeightPx = 44f,
            alignment = HandleHorizontalAlignment.ALIGN_LEFT
        )
        val isUpperVis = ScribeGapCollapsePolicy.isHandleVisibleInViewport(
            handleDoc = upperHandleOffscreenAbove,
            preScrollX = 0,
            preScrollY = preScrollY,
            headerHeight = headerH,
            editorHeightPx = 900,
            viewportWidthPx = 720f,
            viewportBounds = bounds
        )
        val isLowerVis = ScribeGapCollapsePolicy.isHandleVisibleInViewport(
            handleDoc = lowerHandleAtL2,
            preScrollX = 0,
            preScrollY = preScrollY,
            headerHeight = headerH,
            editorHeightPx = 900,
            viewportWidthPx = 720f,
            viewportBounds = bounds
        )
        assertFalse(isUpperVis)
        assertTrue(isLowerVis)

        val plan = planWithDefaults(
            isUpperVisible = isUpperVis,
            isLowerVisible = isLowerVis,
            isTouchedOnScreen = true,
            preScrollY = preScrollY,
            deltaGapPx = rowHeight * 12f,
            viewportBounds = bounds
        )
        assertEquals(CollapseScenario.B_LOWER_ONLY, plan.scenario)
    }

    @Test
    fun testIsSelectionTouchedOnScreen_RowSpansClippingAndFloatingPills() {
        val headerH = 160
        val preScrollY = 400 // headerRemaining = -240
        val pill = Rect(left = 20f, top = 120f, right = 180f, bottom = 200f)
        val bounds = makeValidViewportBounds(top = 100f, bottom = 860f, floatingPills = listOf(pill))

        // 1. Selected span wholly outside horizontal viewport -> false
        val offscreenXCapture = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 10, c1 = 0, l2 = 20, c2 = 0,
            preLineCount = 40, preLineLenL2 = 20,
            preScrollY = preScrollY, rowHeightPx = 40f,
            rowIndexAt = { _, _ -> 20 },
            preScrollX = 500,
            preSelectedRowSpansDoc = listOf(Rect(40f, 360f, 300f, 400f)),
            hasSelectedRowSpans = true
        )
        assertFalse(
            ScribeGapCollapsePolicy.isSelectionTouchedOnScreen(
                isUpperVisible = false,
                isLowerVisible = false,
                preCapture = offscreenXCapture,
                headerHeight = headerH,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )

        // 2. Selected span wholly covered by floating pill (local y: 370-240=130 .. 400-240=160, x: 40..150) -> false
        val coveredByPillCapture = offscreenXCapture.copy(
            preScrollX = 0,
            preSelectedRowSpansDoc = listOf(Rect(40f, 370f, 150f, 400f))
        )
        assertFalse(
            ScribeGapCollapsePolicy.isSelectionTouchedOnScreen(
                isUpperVisible = false,
                isLowerVisible = false,
                preCapture = coveredByPillCapture,
                headerHeight = headerH,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )

        // 3. Partially visible row span outside pills -> true
        val visibleSpanCapture = offscreenXCapture.copy(
            preScrollX = 0,
            preSelectedRowSpansDoc = listOf(Rect(40f, 460f, 520f, 500f))
        )
        assertTrue(
            ScribeGapCollapsePolicy.isSelectionTouchedOnScreen(
                isUpperVisible = false,
                isLowerVisible = false,
                preCapture = visibleSpanCapture,
                headerHeight = headerH,
                viewportWidthPx = 720f,
                viewportBounds = bounds
            )
        )
    }

    @Test
    fun testWasCursorAnimationRunningBeforeDelete_PreservedAndTriggersFallback() {
        val preCapture = ScribeGapCollapsePolicy.classifyPreDeleteSeam(
            isSelection = true,
            l1 = 4, c1 = 0, l2 = 8, c2 = 0,
            preLineCount = 20, preLineLenL2 = 15,
            preScrollY = 300, rowHeightPx = 40f,
            rowIndexAt = { _, _ -> 8 },
            wasCursorAnimationRunningBeforeDelete = true
        )
        assertTrue(preCapture.wasCursorAnimationRunningBeforeDelete)
        val fallbackPlan = planWithDefaults(isCursorAnimationRunning = preCapture.wasCursorAnimationRunningBeforeDelete)
        assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, fallbackPlan.scenario)
    }
}
