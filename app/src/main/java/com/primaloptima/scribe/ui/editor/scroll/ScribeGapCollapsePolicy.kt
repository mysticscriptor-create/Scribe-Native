package com.primaloptima.scribe.ui.editor.scroll

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

enum class VisibilityConfidence {
    RESOLVED,
    UNRELIABLE
}

enum class CollapseScenario {
    INELIGIBLE_FALLBACK,
    A_UPPER_ONLY,
    B_LOWER_ONLY,
    C_BOTH_VISIBLE,
    D_SELECTION_ONLY_CENTERED,
    E_OFFSCREEN_CARET_FOLLOW
}

data class UnobstructedViewportBounds(
    val headerBottomLocal: Float,
    val stickyBottomLocal: Float,
    val maxTopBarBottomLocal: Float,
    val minBottomBarTopLocal: Float,
    val windowVisibleBottomLocal: Float,
    val visibleEditorTop: Float,
    val visibleEditorBottom: Float,
    val usableHeight: Float,
    val hasModalBlocker: Boolean,
    val isValid: Boolean,
    val floatingPillLocalRects: List<Rect> = emptyList()
) {
    fun overlapsAnyFloatingPill(rect: Rect): Boolean =
        floatingPillLocalRects.any { it.overlaps(rect) }

    fun isRectUnobstructedByFloatingPills(rect: Rect): Boolean =
        !overlapsAnyFloatingPill(rect)
}

data class SafetyFloorAndBounds(
    val safeMax: Int,
    val minSafeScroll: Float,
    val safeMinPhysical: Int,
    val actualPhysicalScroll: Int
) {
    fun clampPhysicalSafetyTarget(requestedTarget: Float): Float =
        requestedTarget.coerceIn(safeMinPhysical.toFloat(), safeMax.toFloat())

    fun isPhysicalScrollSafe(physicalScroll: Int = actualPhysicalScroll): Boolean =
        physicalScroll in safeMinPhysical..safeMax
}

data class CollapsePlan(
    val scenario: CollapseScenario,
    val requestedTargetScroll: Float,
    val effectiveTargetScroll: Float,
    val safetyBounds: SafetyFloorAndBounds,
    val deltaGapPx: Float,
    val preScrollY: Int,
    val requiresMode1: Boolean,
    val requiresSynchronousSnap: Boolean,
    val synchronousSnapTarget: Int,
    val initialTauLowerPx: Float,
    val isProgressCoupled: Boolean,
    val requiresOffscreenCaretFollow: Boolean
) {
    val isFallback: Boolean
        get() = scenario == CollapseScenario.INELIGIBLE_FALLBACK
}

data class Mode2StepResult(
    val nextTauPx: Float,
    val nextElapsedTimeSec: Float,
    val isSettled: Boolean
)

enum class DeletionSeamCase {
    NONE,
    CASE_I_MID_LINE_OR_WHOLE_LINE,
    CASE_II_LINE_END
}

data class PreDeleteSeamCapture(
    val isSelection: Boolean,
    val l1: Int,
    val c1: Int,
    val l2: Int,
    val c2: Int,
    val preLineCount: Int,
    val preLineLenL2: Int,
    val preScrollY: Int,
    val seamCase: DeletionSeamCase,
    val isMergedPartialRowSeam: Boolean,
    val hasSurvivingUpper: Boolean,
    val hasSurvivingLower: Boolean,
    val preFirstLowerRowTopDocY: Float,
    val preSelectionTopDocY: Float,
    val preSelectionBottomDocY: Float,
    val hasRtlOrBidiAtSeam: Boolean = false
)

data class ResolvedSeamGeometry(
    val isEligibleCase: Boolean,
    val isMergedPartialRowSeam: Boolean,
    val hasSurvivingUpper: Boolean,
    val hasSurvivingLower: Boolean,
    val hasRtlOrBidiAtSeam: Boolean,
    val seamLine: Int,
    val seamCol: Int,
    val seamFirstLowerRow: Int,
    val activeSeamDocY: Float,
    val preFirstLowerRowTopDocY: Float,
    val deltaGapPx: Float,
    val preSelectionTopDocY: Float,
    val preSelectionBottomDocY: Float
)

sealed interface SeamMappingResult {
    data class PreservePhase(
        val newSeamLine: Int,
        val newSeamFirstLowerRow: Int,
        val newSeamDocY: Float,
        val preservedTauLowerPx: Float
    ) : SeamMappingResult

    data class RebaseTau(
        val newSeamLine: Int,
        val newSeamFirstLowerRow: Int,
        val newSeamDocY: Float,
        val residualTauLowerPx: Float
    ) : SeamMappingResult

    data class FallbackRequired(
        val reason: String
    ) : SeamMappingResult
}

/**
 * Pure, stateless math and classification policy for V1 selection-deletion gap collapse.
 *
 * Strictly decoupled from Android View lifecycle state and shared identically across
 * planning, Mode-1 boundary safety, Mode-2 handoff & closed-form settling, and Event-B
 * settled physical-safety validation.
 */
object ScribeGapCollapsePolicy {

    const val MODE2_DEFAULT_OMEGA_RAD_PER_SEC = 13.5f
    const val MODE2_SETTLE_THRESHOLD_PX = 0.5f
    const val PHYSICAL_VELOCITY_EMA_ALPHA = 0.65f
    const val MIN_GAP_COLLAPSE_PX = 0.5f
    const val MAX_SCENARIO_D_GAP_VIEWPORT_MULTIPLIER = 3f

    fun evaluateVisibilityConfidence(
        isCanvasLaidOut: Boolean,
        isEditorLaidOut: Boolean,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        isWordwrapReady: Boolean,
        isLayoutBusy: Boolean,
        hasPreLayout: Boolean,
        viewportBounds: UnobstructedViewportBounds
    ): VisibilityConfidence {
        val resolved = isCanvasLaidOut &&
            isEditorLaidOut &&
            canvasWidthPx > 0 &&
            canvasHeightPx > 0 &&
            isWordwrapReady &&
            !isLayoutBusy &&
            hasPreLayout &&
            !viewportBounds.hasModalBlocker &&
            viewportBounds.isValid
        return if (resolved) VisibilityConfidence.RESOLVED else VisibilityConfidence.UNRELIABLE
    }

    fun resolveSafetyFloorAndBounds(
        headerHeight: Int,
        anchorDocBottom: Float,
        breathingMarginPx: Float,
        usableBottomForSafety: Float,
        maxCanvasScrollPost: Int,
        scrollD: Int,
        editorOffsetY: Int
    ): SafetyFloorAndBounds {
        val safeMax: Int = maxCanvasScrollPost.coerceAtLeast(0)
        val minSafeScroll: Float =
            (headerHeight.toFloat() + anchorDocBottom + breathingMarginPx - usableBottomForSafety)
                .coerceAtLeast(0f)
                .coerceIn(0f, safeMax.toFloat())
        val safeMinPhysical: Int =
            ceil(minSafeScroll).toInt().coerceIn(0, safeMax)
        val actualPhysicalScroll: Int = scrollD + editorOffsetY
        return SafetyFloorAndBounds(
            safeMax = safeMax,
            minSafeScroll = minSafeScroll,
            safeMinPhysical = safeMinPhysical,
            actualPhysicalScroll = actualPhysicalScroll
        )
    }

    fun planCollapse(
        deletionIntent: ScribeScrollDeletionPolicy.DeletionAnchorIntent,
        isSelection: Boolean,
        isWordwrap: Boolean,
        isWordwrapReady: Boolean,
        isLayoutBusy: Boolean,
        isComposing: Boolean,
        isCursorAnimationRunning: Boolean,
        hasRtlOrBidiAtSeam: Boolean,
        isMergedPartialRowSeam: Boolean,
        hasSurvivingUpper: Boolean,
        hasSurvivingLower: Boolean,
        deltaGapPx: Float,
        visibilityConfidence: VisibilityConfidence,
        isUpperVisible: Boolean,
        isLowerVisible: Boolean,
        isTouchedOnScreen: Boolean,
        preScrollY: Int,
        centeredTargetScroll: Float,
        viewportBounds: UnobstructedViewportBounds,
        safetyBounds: SafetyFloorAndBounds
    ): CollapsePlan {
        fun fallbackPlan(
            requested: Float = preScrollY.toFloat(),
            scenario: CollapseScenario = CollapseScenario.INELIGIBLE_FALLBACK,
            requiresOffscreenFollow: Boolean = false
        ): CollapsePlan = CollapsePlan(
            scenario = scenario,
            requestedTargetScroll = requested,
            effectiveTargetScroll = safetyBounds.clampPhysicalSafetyTarget(requested),
            safetyBounds = safetyBounds,
            deltaGapPx = deltaGapPx.coerceAtLeast(0f),
            preScrollY = preScrollY,
            requiresMode1 = false,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = preScrollY,
            initialTauLowerPx = 0f,
            isProgressCoupled = false,
            requiresOffscreenCaretFollow = requiresOffscreenFollow
        )

        val isEligible =
            deletionIntent == ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR &&
                isSelection &&
                isWordwrap &&
                isWordwrapReady &&
                !isLayoutBusy &&
                !isComposing &&
                !isCursorAnimationRunning &&
                !hasRtlOrBidiAtSeam &&
                !isMergedPartialRowSeam &&
                hasSurvivingUpper &&
                hasSurvivingLower &&
                deltaGapPx.isFinite() &&
                deltaGapPx > MIN_GAP_COLLAPSE_PX &&
                visibilityConfidence == VisibilityConfidence.RESOLVED

        if (!isEligible) {
            return fallbackPlan()
        }

        val scenario = when {
            !isUpperVisible && isLowerVisible -> CollapseScenario.B_LOWER_ONLY
            isUpperVisible && isLowerVisible -> CollapseScenario.C_BOTH_VISIBLE
            isUpperVisible && !isLowerVisible -> CollapseScenario.A_UPPER_ONLY
            !isUpperVisible && !isLowerVisible && isTouchedOnScreen -> {
                if (deltaGapPx > MAX_SCENARIO_D_GAP_VIEWPORT_MULTIPLIER * viewportBounds.usableHeight) {
                    return fallbackPlan()
                }
                CollapseScenario.D_SELECTION_ONLY_CENTERED
            }
            else -> return fallbackPlan(
                scenario = CollapseScenario.E_OFFSCREEN_CARET_FOLLOW,
                requiresOffscreenFollow = true
            )
        }

        val preScrollFloat = preScrollY.toFloat()
        val requestedTarget = when (scenario) {
            CollapseScenario.B_LOWER_ONLY,
            CollapseScenario.C_BOTH_VISIBLE -> preScrollFloat - deltaGapPx
            CollapseScenario.A_UPPER_ONLY -> preScrollFloat
            CollapseScenario.D_SELECTION_ONLY_CENTERED -> centeredTargetScroll
            CollapseScenario.INELIGIBLE_FALLBACK,
            CollapseScenario.E_OFFSCREEN_CARET_FOLLOW -> preScrollFloat
        }
        val effectiveTarget = safetyBounds.clampPhysicalSafetyTarget(requestedTarget)

        // Branch 2: Reachable sub-1px fractional floor crossing just above integer S_pre.
        val floorGapAbovePre = safetyBounds.minSafeScroll - preScrollFloat
        val isSub1PxFloorSnap = preScrollY < safetyBounds.safeMinPhysical &&
            floorGapAbovePre > 0f &&
            floorGapAbovePre < 1f &&
            effectiveTarget <= safetyBounds.safeMinPhysical.toFloat()

        if (isSub1PxFloorSnap) {
            val snapTarget = safetyBounds.safeMinPhysical
            return CollapsePlan(
                scenario = scenario,
                requestedTargetScroll = requestedTarget,
                effectiveTargetScroll = snapTarget.toFloat(),
                safetyBounds = safetyBounds,
                deltaGapPx = deltaGapPx,
                preScrollY = preScrollY,
                requiresMode1 = false,
                requiresSynchronousSnap = true,
                synchronousSnapTarget = snapTarget,
                initialTauLowerPx = computeSynchronousSnapTauLower(
                    scenario = scenario,
                    deltaGapPx = deltaGapPx,
                    preScrollY = preScrollY,
                    actualInitialScroll = snapTarget
                ),
                isProgressCoupled = false,
                requiresOffscreenCaretFollow = false
            )
        }

        val distanceToEffective = abs(effectiveTarget - preScrollFloat)
        val requiresMode1 = distanceToEffective >= 1f
        val isProgressCoupled = when (scenario) {
            CollapseScenario.A_UPPER_ONLY ->
                preScrollY < safetyBounds.safeMinPhysical
            CollapseScenario.D_SELECTION_ONLY_CENTERED ->
                true
            CollapseScenario.B_LOWER_ONLY,
            CollapseScenario.C_BOTH_VISIBLE,
            CollapseScenario.INELIGIBLE_FALLBACK,
            CollapseScenario.E_OFFSCREEN_CARET_FOLLOW ->
                false
        }

        return CollapsePlan(
            scenario = scenario,
            requestedTargetScroll = requestedTarget,
            effectiveTargetScroll = effectiveTarget,
            safetyBounds = safetyBounds,
            deltaGapPx = deltaGapPx,
            preScrollY = preScrollY,
            requiresMode1 = requiresMode1,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = preScrollY,
            initialTauLowerPx = deltaGapPx,
            isProgressCoupled = isProgressCoupled,
            requiresOffscreenCaretFollow = false
        )
    }

    fun computeSynchronousSnapTauLower(
        scenario: CollapseScenario,
        deltaGapPx: Float,
        preScrollY: Int,
        actualInitialScroll: Int
    ): Float = when (scenario) {
        CollapseScenario.B_LOWER_ONLY,
        CollapseScenario.C_BOTH_VISIBLE ->
            maxOf(0f, deltaGapPx + (actualInitialScroll.toFloat() - preScrollY.toFloat()))
        CollapseScenario.A_UPPER_ONLY,
        CollapseScenario.D_SELECTION_ONLY_CENTERED ->
            deltaGapPx.coerceAtLeast(0f)
        CollapseScenario.INELIGIBLE_FALLBACK,
        CollapseScenario.E_OFFSCREEN_CARET_FOLLOW ->
            0f
    }

    fun computeMode1TauLower(
        plan: CollapsePlan,
        actualScroll: Float
    ): Float {
        if (plan.isFallback || plan.scenario == CollapseScenario.E_OFFSCREEN_CARET_FOLLOW) {
            return 0f
        }
        if (!plan.requiresMode1) {
            return plan.initialTauLowerPx
        }
        val deltaGap = plan.deltaGapPx.coerceAtLeast(0f)
        val preScroll = plan.preScrollY.toFloat()
        return if (plan.isProgressCoupled) {
            val span = plan.effectiveTargetScroll - preScroll
            if (!span.isFinite() || abs(span) < 1f) {
                deltaGap
            } else {
                val progress = (actualScroll - preScroll) / span
                (deltaGap * (1f - progress)).coerceIn(0f, deltaGap)
            }
        } else {
            maxOf(0f, deltaGap - (preScroll - actualScroll))
        }
    }

    fun computeShortfallPx(
        deltaGapPx: Float,
        preScrollY: Int,
        actualFinalScroll: Float
    ): Float = deltaGapPx - (preScrollY.toFloat() - actualFinalScroll)

    fun updateObservedPhysicalVelocityEma(
        previousSmoothedVelocity: Float?,
        observedDeltaPx: Float,
        dtSec: Float,
        boundaryHit: Boolean
    ): Float? {
        if (boundaryHit ||
            !observedDeltaPx.isFinite() ||
            !dtSec.isFinite() ||
            dtSec <= 0f ||
            abs(observedDeltaPx) < 0.5f
        ) {
            return previousSmoothedVelocity
        }
        val instantVelocity = observedDeltaPx / dtSec
        if (!instantVelocity.isFinite()) {
            return previousSmoothedVelocity
        }
        return if (previousSmoothedVelocity == null || !previousSmoothedVelocity.isFinite()) {
            instantVelocity
        } else {
            PHYSICAL_VELOCITY_EMA_ALPHA * instantVelocity +
                (1f - PHYSICAL_VELOCITY_EMA_ALPHA) * previousSmoothedVelocity
        }
    }

    fun computeMode2HandoffProgressVelocity(
        plan: CollapsePlan,
        mode1ActuallyStarted: Boolean,
        smoothedPhysicalVelocityPxPerSec: Float?,
        tauResumePx: Float,
        omega2: Float = MODE2_DEFAULT_OMEGA_RAD_PER_SEC
    ): Float {
        if (!tauResumePx.isFinite() || tauResumePx <= MODE2_SETTLE_THRESHOLD_PX) {
            return 0f
        }
        if (!plan.requiresMode1 ||
            !mode1ActuallyStarted ||
            plan.isFallback ||
            plan.scenario == CollapseScenario.E_OFFSCREEN_CARET_FOLLOW ||
            smoothedPhysicalVelocityPxPerSec == null ||
            !smoothedPhysicalVelocityPxPerSec.isFinite()
        ) {
            return 0f
        }
        val tauDotHandoff = if (plan.isProgressCoupled) {
            val physicalSpan = plan.effectiveTargetScroll - plan.preScrollY.toFloat()
            if (!plan.deltaGapPx.isFinite() || !physicalSpan.isFinite() || abs(physicalSpan) < 1f) {
                0f
            } else {
                -(plan.deltaGapPx / physicalSpan) * smoothedPhysicalVelocityPxPerSec
            }
        } else {
            smoothedPhysicalVelocityPxPerSec
        }
        if (!tauDotHandoff.isFinite() || tauDotHandoff >= 0f) {
            return 0f
        }
        val raw = -tauDotHandoff / tauResumePx
        return if (raw.isFinite()) raw.coerceIn(0f, omega2) else 0f
    }

    fun computeFrameDtSec(
        frameTimeNanos: Long,
        lastFrameTimeNs: Long,
        refreshRateHz: Float = 60f
    ): Float {
        val rawDt = if (lastFrameTimeNs == 0L || frameTimeNanos <= lastFrameTimeNs) {
            val safeHz = if (refreshRateHz.isFinite() && refreshRateHz > 0f) refreshRateHz else 60f
            1f / safeHz
        } else {
            (frameTimeNanos - lastFrameTimeNs) / 1_000_000_000f
        }
        return rawDt.coerceIn(0.001f, 0.032f)
    }

    fun stepMode2ClosedForm(
        tauAtResumePx: Float,
        previousElapsedTimeSec: Float,
        dtSec: Float,
        progressVelocity: Float,
        omega: Float = MODE2_DEFAULT_OMEGA_RAD_PER_SEC
    ): Mode2StepResult {
        val safeDt = if (dtSec.isFinite()) dtSec.coerceIn(0.001f, 0.032f) else 0.016f
        val elapsed = (previousElapsedTimeSec.coerceAtLeast(0f) + safeDt)
        if (!tauAtResumePx.isFinite() || tauAtResumePx <= MODE2_SETTLE_THRESHOLD_PX) {
            return Mode2StepResult(
                nextTauPx = 0f,
                nextElapsedTimeSec = elapsed,
                isSettled = true
            )
        }
        val safeV0 = if (progressVelocity.isFinite()) progressVelocity.coerceIn(0f, omega) else 0f
        val wt = omega * elapsed
        val residualFraction = exp(-wt) * (1f + (omega - safeV0) * elapsed)
        val nextTau = (tauAtResumePx * residualFraction).coerceIn(0f, tauAtResumePx)
        val settled = !nextTau.isFinite() || nextTau <= MODE2_SETTLE_THRESHOLD_PX
        return Mode2StepResult(
            nextTauPx = if (settled) 0f else nextTau,
            nextElapsedTimeSec = elapsed,
            isSettled = settled
        )
    }

    fun evaluateIncrementalSeamRebase(
        isWordwrapReady: Boolean,
        isLayoutBusy: Boolean,
        preEditTauPx: Float,
        preEditSeamDocY: Float,
        newSeamLine: Int,
        newSeamCol: Int,
        newSeamFirstLowerRow: Int,
        postSeamDocY: Float
    ): SeamMappingResult {
        if (!isWordwrapReady || isLayoutBusy) {
            return SeamMappingResult.FallbackRequired("layout_not_ready")
        }
        if (newSeamCol != 0 ||
            newSeamLine < 0 ||
            newSeamFirstLowerRow < 0 ||
            !postSeamDocY.isFinite() ||
            !preEditSeamDocY.isFinite()
        ) {
            return SeamMappingResult.FallbackRequired("invalid_seam_column_or_position")
        }
        val deltaYInsert = postSeamDocY - preEditSeamDocY
        return when {
            deltaYInsert in -0.5f..0.5f -> SeamMappingResult.PreservePhase(
                newSeamLine = newSeamLine,
                newSeamFirstLowerRow = newSeamFirstLowerRow,
                newSeamDocY = postSeamDocY,
                preservedTauLowerPx = preEditTauPx.coerceAtLeast(0f)
            )
            deltaYInsert > 0.5f && deltaYInsert <= preEditTauPx -> SeamMappingResult.RebaseTau(
                newSeamLine = newSeamLine,
                newSeamFirstLowerRow = newSeamFirstLowerRow,
                newSeamDocY = postSeamDocY,
                residualTauLowerPx = (preEditTauPx - deltaYInsert).coerceAtLeast(0f)
            )
            else -> SeamMappingResult.FallbackRequired(
                if (deltaYInsert < -0.5f) "negative_seam_shift" else "insertion_exceeded_tau"
            )
        }
    }

    fun containsRtlOrBidi(text: CharSequence): Boolean {
        for (i in 0 until text.length) {
            when (Character.getDirectionality(text[i])) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE,
                Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT,
                Character.DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE -> return true
            }
        }
        return false
    }

    fun classifyPreDeleteSeam(
        isSelection: Boolean,
        l1: Int,
        c1: Int,
        l2: Int,
        c2: Int,
        preLineCount: Int,
        preLineLenL2: Int,
        preScrollY: Int,
        rowHeightPx: Float,
        rowIndexAt: (line: Int, col: Int) -> Int,
        preSelectionTopDocY: Float = 0f,
        preSelectionBottomDocY: Float = 0f,
        hasRtlOrBidiAtSeam: Boolean = false
    ): PreDeleteSeamCapture {
        if (!isSelection || preLineCount <= 0 || rowHeightPx <= 0f || !rowHeightPx.isFinite()) {
            return PreDeleteSeamCapture(
                isSelection = isSelection,
                l1 = l1,
                c1 = c1,
                l2 = l2,
                c2 = c2,
                preLineCount = preLineCount,
                preLineLenL2 = preLineLenL2,
                preScrollY = preScrollY,
                seamCase = DeletionSeamCase.NONE,
                isMergedPartialRowSeam = false,
                hasSurvivingUpper = false,
                hasSurvivingLower = false,
                preFirstLowerRowTopDocY = Float.NaN,
                preSelectionTopDocY = preSelectionTopDocY,
                preSelectionBottomDocY = preSelectionBottomDocY,
                hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam
            )
        }
        val hasSurvivingUpper = l1 > 0 || c1 > 0
        if (c2 < preLineLenL2) {
            // Case I (C2 < preLineLen(L2))
            if (c1 > 0) {
                return PreDeleteSeamCapture(
                    isSelection = true,
                    l1 = l1,
                    c1 = c1,
                    l2 = l2,
                    c2 = c2,
                    preLineCount = preLineCount,
                    preLineLenL2 = preLineLenL2,
                    preScrollY = preScrollY,
                    seamCase = DeletionSeamCase.CASE_I_MID_LINE_OR_WHOLE_LINE,
                    isMergedPartialRowSeam = true,
                    hasSurvivingUpper = hasSurvivingUpper,
                    hasSurvivingLower = true,
                    preFirstLowerRowTopDocY = Float.NaN,
                    preSelectionTopDocY = preSelectionTopDocY,
                    preSelectionBottomDocY = preSelectionBottomDocY,
                    hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam
                )
            }
            val preRow = rowIndexAt(l2, c2)
            val preTopDocY = if (preRow >= 0) preRow * rowHeightPx else Float.NaN
            return PreDeleteSeamCapture(
                isSelection = true,
                l1 = l1,
                c1 = c1,
                l2 = l2,
                c2 = c2,
                preLineCount = preLineCount,
                preLineLenL2 = preLineLenL2,
                preScrollY = preScrollY,
                seamCase = DeletionSeamCase.CASE_I_MID_LINE_OR_WHOLE_LINE,
                isMergedPartialRowSeam = false,
                hasSurvivingUpper = hasSurvivingUpper,
                hasSurvivingLower = true,
                preFirstLowerRowTopDocY = preTopDocY,
                preSelectionTopDocY = preSelectionTopDocY,
                preSelectionBottomDocY = preSelectionBottomDocY,
                hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam
            )
        }
        // Case II (C2 == preLineLen(L2))
        if (l2 + 1 >= preLineCount) {
            return PreDeleteSeamCapture(
                isSelection = true,
                l1 = l1,
                c1 = c1,
                l2 = l2,
                c2 = c2,
                preLineCount = preLineCount,
                preLineLenL2 = preLineLenL2,
                preScrollY = preScrollY,
                seamCase = DeletionSeamCase.CASE_II_LINE_END,
                isMergedPartialRowSeam = false,
                hasSurvivingUpper = hasSurvivingUpper,
                hasSurvivingLower = false,
                preFirstLowerRowTopDocY = Float.NaN,
                preSelectionTopDocY = preSelectionTopDocY,
                preSelectionBottomDocY = preSelectionBottomDocY,
                hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam
            )
        }
        val preRow = rowIndexAt(l2 + 1, 0)
        val preTopDocY = if (preRow >= 0) preRow * rowHeightPx else Float.NaN
        return PreDeleteSeamCapture(
            isSelection = true,
            l1 = l1,
            c1 = c1,
            l2 = l2,
            c2 = c2,
            preLineCount = preLineCount,
            preLineLenL2 = preLineLenL2,
            preScrollY = preScrollY,
            seamCase = DeletionSeamCase.CASE_II_LINE_END,
            isMergedPartialRowSeam = false,
            hasSurvivingUpper = hasSurvivingUpper,
            hasSurvivingLower = true,
            preFirstLowerRowTopDocY = preTopDocY,
            preSelectionTopDocY = preSelectionTopDocY,
            preSelectionBottomDocY = preSelectionBottomDocY,
            hasRtlOrBidiAtSeam = hasRtlOrBidiAtSeam
        )
    }

    fun resolvePostDeleteSeamGeometry(
        preCapture: PreDeleteSeamCapture,
        postStartLine: Int,
        postLineCount: Int,
        rowHeightPx: Float,
        postRowIndexAt: (line: Int, col: Int) -> Int
    ): ResolvedSeamGeometry {
        fun ineligible(hasLower: Boolean = preCapture.hasSurvivingLower): ResolvedSeamGeometry =
            ResolvedSeamGeometry(
                isEligibleCase = false,
                isMergedPartialRowSeam = preCapture.isMergedPartialRowSeam,
                hasSurvivingUpper = preCapture.hasSurvivingUpper,
                hasSurvivingLower = hasLower,
                hasRtlOrBidiAtSeam = preCapture.hasRtlOrBidiAtSeam,
                seamLine = -1,
                seamCol = 0,
                seamFirstLowerRow = -1,
                activeSeamDocY = 0f,
                preFirstLowerRowTopDocY = preCapture.preFirstLowerRowTopDocY,
                deltaGapPx = 0f,
                preSelectionTopDocY = preCapture.preSelectionTopDocY,
                preSelectionBottomDocY = preCapture.preSelectionBottomDocY
            )

        if (!preCapture.isSelection ||
            preCapture.isMergedPartialRowSeam ||
            !preCapture.hasSurvivingUpper ||
            !preCapture.hasSurvivingLower ||
            preCapture.hasRtlOrBidiAtSeam ||
            !preCapture.preFirstLowerRowTopDocY.isFinite() ||
            rowHeightPx <= 0f ||
            !rowHeightPx.isFinite()
        ) {
            return ineligible()
        }

        val seamLine = when (preCapture.seamCase) {
            DeletionSeamCase.CASE_I_MID_LINE_OR_WHOLE_LINE -> postStartLine
            DeletionSeamCase.CASE_II_LINE_END -> postStartLine + 1
            DeletionSeamCase.NONE -> return ineligible()
        }
        if (seamLine < 0 || seamLine >= postLineCount) {
            return ineligible(hasLower = false)
        }
        val seamFirstLowerRow = postRowIndexAt(seamLine, 0)
        if (seamFirstLowerRow < 0) {
            return ineligible()
        }
        val activeSeamDocY = seamFirstLowerRow * rowHeightPx
        val deltaGapPx = preCapture.preFirstLowerRowTopDocY - activeSeamDocY
        return ResolvedSeamGeometry(
            isEligibleCase = true,
            isMergedPartialRowSeam = false,
            hasSurvivingUpper = true,
            hasSurvivingLower = true,
            hasRtlOrBidiAtSeam = false,
            seamLine = seamLine,
            seamCol = 0,
            seamFirstLowerRow = seamFirstLowerRow,
            activeSeamDocY = activeSeamDocY,
            preFirstLowerRowTopDocY = preCapture.preFirstLowerRowTopDocY,
            deltaGapPx = deltaGapPx,
            preSelectionTopDocY = preCapture.preSelectionTopDocY,
            preSelectionBottomDocY = preCapture.preSelectionBottomDocY
        )
    }
}
