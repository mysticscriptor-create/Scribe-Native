package com.primaloptima.scribe.ui.editor.scroll

import kotlin.math.abs

enum class GapCollapsePhase {
    IDLE,
    MODE_1_PHYSICAL_COUPLED,
    MODE_2_VISUAL_SETTLING,
    POINTER_FROZEN,
    FINAL_SAFETY_PENDING
}

enum class PhysicalScrollOrigin {
    NONE,
    USER_CANVAS_DRAG,
    USER_CANVAS_FLING,
    USER_EDITOR_DRAG_OR_FLING,
    USER_HANDLE_AUTOSCROLL,
    GAP_COLLAPSE_MODE_1,
    EVENT_B_SAFETY_CORRECTION,
    EXTERNAL_PROGRAMMATIC
}

data class Mode2VisualState(
    val tauAtResumePx: Float,
    val elapsedTimeSec: Float = 0f,
    val progressVelocity: Float,
    val omega: Float = ScribeGapCollapsePolicy.MODE2_DEFAULT_OMEGA_RAD_PER_SEC,
    val lastFrameTimeNs: Long = 0L
)

sealed interface SafetyRevalidationResult {
    data object VALIDATED_SAFE : SafetyRevalidationResult
    data object CORRECTION_STARTED : SafetyRevalidationResult
    data object CORRECTED_AND_VALIDATED : SafetyRevalidationResult
    data class DEFERRED_UNTIL_LAYOUT_READY(val reason: String) : SafetyRevalidationResult
    data class UNRESOLVED(val reason: String) : SafetyRevalidationResult
    data object STALE_TRANSACTION : SafetyRevalidationResult
}

data class PendingPostSafetyKeyboardEval(
    val owningGen: Long,
    val expectedSafetyEpoch: Long,
    val targetHeightPx: Int,
    val isHeightShrink: Boolean,
    val isAuthorizedTransition: Boolean,
    val shouldFollowExpansion: Boolean,
    val isKeyboardDismissing: Boolean,
    val isDestinationSurfaceActive: Boolean,
    val dockTransition: ScribeDockTransition
)

data class TeardownClaimResult(
    val pendingEval: PendingPostSafetyKeyboardEval?
)

/**
 * Dedicated transaction-scoped state machine owning Gap Collapse (Modes 1 & 2),
 * pointer-freeze/resume, physical scroll origin drift monitoring, and sealed Event-B
 * finalization locks across UnifiedCanvasLayout and ScribeCodeEditor.
 */
class ScribeGapCollapseController(
    private val scheduleFrame: ((Long) -> Unit) -> Unit = {},
    private val cancelFrame: () -> Unit = {},
    private val refreshRateHzProvider: () -> Float = { 60f },
    private val nanoTimeProvider: () -> Long = { System.nanoTime() },
    private val onInvalidateEditor: () -> Unit = {},
    private val onMode2SettledToSafety: (owningGen: Long, collapseEpoch: Long) -> Unit = { _, _ -> },
    private val onExternalDriftAbortToSafety: (owningGen: Long, collapseEpoch: Long) -> Unit = { _, _ -> },
    private val onFreezeMode1Animator: () -> Unit = {}
) {
    var phase: GapCollapsePhase = GapCollapsePhase.IDLE
        private set

    var owningDeletionGen: Long = -1L
        private set

    var collapseAnimEpoch: Long = 0L
        private set

    var safetyEpoch: Long = 0L
        private set

    var isTeardownLocked: Boolean = false
        private set

    var safetyRetryAttempted: Boolean = false
        private set

    var safetyCorrectionAttempted: Boolean = false
        private set

    var pendingPostSafetyKeyboardEval: PendingPostSafetyKeyboardEval? = null
        private set

    var activeSeamLine: Int = -1
        private set

    var activeSeamCol: Int = 0
        private set

    var activeSeamFirstLowerRow: Int = -1
        private set

    var activeSeamDocY: Float = 0f
        private set

    var rowHeightPx: Float = 0f
        private set

    var currentPlan: CollapsePlan? = null
        private set

    var tauLower: Float = 0f
        private set

    var observedPhysicalVelocityEmaPxPerSec: Float? = null
        private set

    var lastPhysicalScrollPx: Float = 0f
        private set

    var lastMode1FrameTimeNs: Long = 0L
        private set

    var mode1EverStarted: Boolean = false
        private set

    var mode2State: Mode2VisualState? = null
        private set

    var lastMode2KnownPhysicalScroll: Float = 0f
        private set

    var phaseBeforeFreeze: GapCollapsePhase = GapCollapsePhase.IDLE
        private set

    private var mode2FramePending: Boolean = false

    val isTransactionOwned: Boolean
        get() = phase != GapCollapsePhase.IDLE

    val shouldApplySplitRender: Boolean
        get() = !isTeardownLocked &&
            (phase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED ||
                phase == GapCollapsePhase.MODE_2_VISUAL_SETTLING ||
                phase == GapCollapsePhase.POINTER_FROZEN) &&
            activeSeamLine >= 0 &&
            tauLower > ScribeGapCollapsePolicy.MODE2_SETTLE_THRESHOLD_PX

    val isResumableCollapse: Boolean
        get() = !isTeardownLocked &&
            phase == GapCollapsePhase.POINTER_FROZEN &&
            activeSeamLine >= 0 &&
            tauLower > 0f

    val hasActiveRebasableSeam: Boolean
        get() = !isTeardownLocked &&
            (phase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED ||
                phase == GapCollapsePhase.MODE_2_VISUAL_SETTLING ||
                phase == GapCollapsePhase.POINTER_FROZEN) &&
            activeSeamLine >= 0 &&
            tauLower > 0f

    val isPointerFrozen: Boolean
        get() = !isTeardownLocked && phase == GapCollapsePhase.POINTER_FROZEN

    fun getTranslationForPosition(line: Int, col: Int): Float {
        if (!shouldApplySplitRender || line < 0 || col < 0) return 0f
        return if (line > activeSeamLine || (line == activeSeamLine && col >= activeSeamCol)) tauLower else 0f
    }

    fun getTranslationForRow(rowIndex: Int): Float {
        if (!shouldApplySplitRender || activeSeamFirstLowerRow < 0) return 0f
        return if (rowIndex >= activeSeamFirstLowerRow) tauLower else 0f
    }

    fun matchesCollapseEpoch(expectedGen: Long, expectedCollapseEpoch: Long): Boolean {
        return owningDeletionGen == expectedGen &&
            collapseAnimEpoch == expectedCollapseEpoch &&
            !isTeardownLocked
    }

    fun matchesSafetyEpoch(expectedGen: Long, expectedSafetyEpoch: Long): Boolean {
        return phase == GapCollapsePhase.FINAL_SAFETY_PENDING &&
            owningDeletionGen == expectedGen &&
            safetyEpoch == expectedSafetyEpoch &&
            !isTeardownLocked
    }

    fun advanceSafetyEpoch(expectedGen: Long): Long? {
        if (owningDeletionGen != expectedGen || isTeardownLocked) return null
        safetyEpoch++
        return safetyEpoch
    }

    fun markSafetyRetryAttempted(expectedGen: Long, expectedSafetyEpoch: Long): Boolean {
        if (!matchesSafetyEpoch(expectedGen, expectedSafetyEpoch) || safetyRetryAttempted) {
            return false
        }
        safetyRetryAttempted = true
        return true
    }

    fun markSafetyCorrectionAttempted(expectedGen: Long, expectedSafetyEpoch: Long): Boolean {
        if (!matchesSafetyEpoch(expectedGen, expectedSafetyEpoch) || safetyCorrectionAttempted) {
            return false
        }
        safetyCorrectionAttempted = true
        return true
    }

    fun storePendingPostSafetyKeyboardEval(eval: PendingPostSafetyKeyboardEval): Boolean {
        if (!isTransactionOwned ||
            isTeardownLocked ||
            eval.owningGen != owningDeletionGen ||
            eval.expectedSafetyEpoch != safetyEpoch
        ) {
            return false
        }
        pendingPostSafetyKeyboardEval = eval
        return true
    }

    fun transitionToFinalSafetyForFallback(owningGen: Long): Long {
        cancelMode2Frame()
        owningDeletionGen = owningGen
        collapseAnimEpoch++
        safetyEpoch++
        isTeardownLocked = false
        safetyRetryAttempted = false
        safetyCorrectionAttempted = false
        pendingPostSafetyKeyboardEval = null
        phase = GapCollapsePhase.FINAL_SAFETY_PENDING
        tauLower = 0f
        mode2State = null
        currentPlan = null
        activeSeamLine = -1
        activeSeamCol = 0
        activeSeamFirstLowerRow = -1
        activeSeamDocY = 0f
        return safetyEpoch
    }

    fun restartFinalSafetyClaim(owningGen: Long): Long? {
        if (owningDeletionGen != owningGen || isTeardownLocked || !isTransactionOwned) return null
        cancelMode2Frame()
        collapseAnimEpoch++
        safetyEpoch++
        safetyRetryAttempted = false
        safetyCorrectionAttempted = false
        phase = GapCollapsePhase.FINAL_SAFETY_PENDING
        tauLower = 0f
        mode2State = null
        activeSeamLine = -1
        activeSeamCol = 0
        activeSeamFirstLowerRow = -1
        activeSeamDocY = 0f
        onInvalidateEditor()
        return safetyEpoch
    }

    fun updateSeamRowBaseline(
        expectedGen: Long,
        newSeamFirstLowerRow: Int,
        newSeamDocY: Float
    ): Boolean {
        if (!isTransactionOwned || isTeardownLocked || owningDeletionGen != expectedGen) {
            return false
        }
        if (newSeamFirstLowerRow < 0 || !newSeamDocY.isFinite()) {
            return false
        }
        activeSeamFirstLowerRow = newSeamFirstLowerRow
        activeSeamDocY = newSeamDocY
        return true
    }

    fun applyIncrementalSeamPreserve(
        expectedGen: Long,
        newSeamLine: Int,
        newSeamFirstLowerRow: Int,
        newSeamDocY: Float,
        newSeamCol: Int = activeSeamCol
    ): Boolean {
        if (isTeardownLocked || owningDeletionGen != expectedGen || !hasActiveRebasableSeam) {
            return false
        }
        activeSeamLine = newSeamLine
        activeSeamCol = newSeamCol.coerceAtLeast(0)
        activeSeamFirstLowerRow = newSeamFirstLowerRow
        activeSeamDocY = newSeamDocY
        onInvalidateEditor()
        return true
    }

    fun applyIncrementalSeamRebase(
        expectedGen: Long,
        newSeamLine: Int,
        newSeamFirstLowerRow: Int,
        newSeamDocY: Float,
        residualTauLowerPx: Float,
        actualPhysicalScrollPx: Float,
        newSeamCol: Int = activeSeamCol
    ): GapCollapsePhase {
        if (isTeardownLocked || owningDeletionGen != expectedGen || !hasActiveRebasableSeam) {
            return phase
        }
        val previousPhase = phase
        val clampedResidual = residualTauLowerPx.coerceAtLeast(0f)
        activeSeamLine = newSeamLine
        activeSeamCol = newSeamCol.coerceAtLeast(0)
        activeSeamFirstLowerRow = newSeamFirstLowerRow
        activeSeamDocY = newSeamDocY
        collapseAnimEpoch++

        if (previousPhase == GapCollapsePhase.POINTER_FROZEN) {
            tauLower = clampedResidual
            onInvalidateEditor()
            return GapCollapsePhase.POINTER_FROZEN
        }

        cancelMode2Frame()
        if (previousPhase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED) {
            onFreezeMode1Animator()
        }

        return if (clampedResidual > ScribeGapCollapsePolicy.MODE2_SETTLE_THRESHOLD_PX) {
            tauLower = clampedResidual
            startMode2Internal(actualPhysicalScrollPx, progressVelocity = 0f)
            onInvalidateEditor()
            GapCollapsePhase.MODE_2_VISUAL_SETTLING
        } else {
            val settledCollapseEpoch = collapseAnimEpoch
            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
            tauLower = 0f
            mode2State = null
            activeSeamLine = -1
            activeSeamFirstLowerRow = -1
            activeSeamDocY = 0f
            safetyEpoch++
            safetyRetryAttempted = false
            safetyCorrectionAttempted = false
            onInvalidateEditor()
            onMode2SettledToSafety(owningDeletionGen, settledCollapseEpoch)
            phase
        }
    }

    fun beginCollapse(
        owningGen: Long,
        plan: CollapsePlan,
        seamLine: Int,
        seamFirstLowerRow: Int,
        seamDocY: Float,
        rowHeightPx: Float,
        initialPhysicalScrollPx: Float,
        seamCol: Int = 0
    ): Long {
        cancelMode2Frame()
        pendingPostSafetyKeyboardEval = null
        isTeardownLocked = false
        safetyRetryAttempted = false
        safetyCorrectionAttempted = false
        owningDeletionGen = owningGen
        collapseAnimEpoch++
        currentPlan = plan
        activeSeamLine = seamLine
        activeSeamCol = seamCol.coerceAtLeast(0)
        activeSeamFirstLowerRow = seamFirstLowerRow
        activeSeamDocY = seamDocY
        this.rowHeightPx = rowHeightPx
        tauLower = plan.initialTauLowerPx.coerceAtLeast(0f)
        observedPhysicalVelocityEmaPxPerSec = null
        lastPhysicalScrollPx = initialPhysicalScrollPx
        lastMode2KnownPhysicalScroll = initialPhysicalScrollPx
        lastMode1FrameTimeNs = 0L
        mode1EverStarted = false
        mode2State = null
        phaseBeforeFreeze = GapCollapsePhase.IDLE

        if (plan.requiresMode1) {
            phase = GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
        } else if (tauLower > ScribeGapCollapsePolicy.MODE2_SETTLE_THRESHOLD_PX && seamLine >= 0) {
            startMode2Internal(initialPhysicalScrollPx, progressVelocity = 0f)
            onInvalidateEditor()
        } else {
            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
            tauLower = 0f
            safetyEpoch++
            onInvalidateEditor()
        }
        return collapseAnimEpoch
    }

    fun markMode1Started(
        expectedGen: Long,
        expectedCollapseEpoch: Long,
        actualStartPhysicalScrollPx: Float
    ) {
        if (!matchesCollapseEpoch(expectedGen, expectedCollapseEpoch) ||
            phase != GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
        ) {
            return
        }
        val plan = currentPlan ?: return
        mode1EverStarted = true
        lastPhysicalScrollPx = actualStartPhysicalScrollPx
        lastMode1FrameTimeNs = 0L
        tauLower = ScribeGapCollapsePolicy.computeMode1TauLower(plan, actualStartPhysicalScrollPx)
        onInvalidateEditor()
    }

    fun onMode1FrameCommitted(
        expectedGen: Long,
        expectedCollapseEpoch: Long,
        actualPhysicalScrollPx: Float,
        boundaryHit: Boolean,
        frameTimeNanos: Long = nanoTimeProvider()
    ) {
        if (!matchesCollapseEpoch(expectedGen, expectedCollapseEpoch) ||
            phase != GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
        ) {
            return
        }
        val plan = currentPlan ?: return
        val dtSec = ScribeGapCollapsePolicy.computeFrameDtSec(
            frameTimeNanos = frameTimeNanos,
            lastFrameTimeNs = lastMode1FrameTimeNs,
            refreshRateHz = refreshRateHzProvider()
        )
        val observedDelta = actualPhysicalScrollPx - lastPhysicalScrollPx
        observedPhysicalVelocityEmaPxPerSec = ScribeGapCollapsePolicy.updateObservedPhysicalVelocityEma(
            previousSmoothedVelocity = observedPhysicalVelocityEmaPxPerSec,
            observedDeltaPx = observedDelta,
            dtSec = dtSec,
            boundaryHit = boundaryHit
        )
        lastPhysicalScrollPx = actualPhysicalScrollPx
        lastMode2KnownPhysicalScroll = actualPhysicalScrollPx
        lastMode1FrameTimeNs = frameTimeNanos
        tauLower = ScribeGapCollapsePolicy.computeMode1TauLower(plan, actualPhysicalScrollPx)
        onInvalidateEditor()
    }

    fun onMode1SettledOrHandoff(
        expectedGen: Long,
        expectedCollapseEpoch: Long,
        actualFinalPhysicalScrollPx: Float,
        mode1WasStarted: Boolean = mode1EverStarted
    ): GapCollapsePhase {
        if (!matchesCollapseEpoch(expectedGen, expectedCollapseEpoch) ||
            phase != GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
        ) {
            return phase
        }
        val plan = currentPlan ?: return phase
        tauLower = ScribeGapCollapsePolicy.computeMode1TauLower(plan, actualFinalPhysicalScrollPx)
        val progressVelocity = ScribeGapCollapsePolicy.computeMode2HandoffProgressVelocity(
            plan = plan,
            mode1ActuallyStarted = mode1WasStarted,
            smoothedPhysicalVelocityPxPerSec = observedPhysicalVelocityEmaPxPerSec,
            tauResumePx = tauLower
        )
        return if (tauLower > ScribeGapCollapsePolicy.MODE2_SETTLE_THRESHOLD_PX && activeSeamLine >= 0) {
            startMode2Internal(actualFinalPhysicalScrollPx, progressVelocity)
            onInvalidateEditor()
            GapCollapsePhase.MODE_2_VISUAL_SETTLING
        } else {
            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
            tauLower = 0f
            mode2State = null
            safetyEpoch++
            safetyRetryAttempted = false
        safetyCorrectionAttempted = false
            onInvalidateEditor()
            GapCollapsePhase.FINAL_SAFETY_PENDING
        }
    }

    private fun startMode2Internal(actualPhysicalScrollPx: Float, progressVelocity: Float) {
        val omega = ScribeGapCollapsePolicy.MODE2_DEFAULT_OMEGA_RAD_PER_SEC
        val clampedV = if (progressVelocity.isFinite()) progressVelocity.coerceIn(0f, omega) else 0f
        phase = GapCollapsePhase.MODE_2_VISUAL_SETTLING
        lastMode2KnownPhysicalScroll = actualPhysicalScrollPx
        mode2State = Mode2VisualState(
            tauAtResumePx = tauLower,
            elapsedTimeSec = 0f,
            progressVelocity = clampedV,
            omega = omega,
            lastFrameTimeNs = 0L
        )
        scheduleMode2NextFrame()
    }

    private fun scheduleMode2NextFrame() {
        if (mode2FramePending) return
        val capturedGen = owningDeletionGen
        val capturedEpoch = collapseAnimEpoch
        mode2FramePending = true
        scheduleFrame { frameTimeNs ->
            onMode2Frame(capturedGen, capturedEpoch, frameTimeNs)
        }
    }

    private fun cancelMode2Frame() {
        if (mode2FramePending) {
            mode2FramePending = false
        }
        cancelFrame()
    }

    fun onMode2Frame(expectedGen: Long, expectedCollapseEpoch: Long, frameTimeNanos: Long) {
        mode2FramePending = false
        if (!matchesCollapseEpoch(expectedGen, expectedCollapseEpoch) ||
            phase != GapCollapsePhase.MODE_2_VISUAL_SETTLING
        ) {
            return
        }
        val state = mode2State ?: return
        val dtSec = ScribeGapCollapsePolicy.computeFrameDtSec(
            frameTimeNanos = frameTimeNanos,
            lastFrameTimeNs = state.lastFrameTimeNs,
            refreshRateHz = refreshRateHzProvider()
        )
        val step = ScribeGapCollapsePolicy.stepMode2ClosedForm(
            tauAtResumePx = state.tauAtResumePx,
            previousElapsedTimeSec = state.elapsedTimeSec,
            dtSec = dtSec,
            progressVelocity = state.progressVelocity,
            omega = state.omega
        )
        val prevTau = tauLower
        tauLower = step.nextTauPx
        mode2State = state.copy(
            elapsedTimeSec = step.nextElapsedTimeSec,
            lastFrameTimeNs = frameTimeNanos
        )
        if (step.isSettled) {
            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
            tauLower = 0f
            mode2State = null
            safetyEpoch++
            safetyRetryAttempted = false
        safetyCorrectionAttempted = false
            onInvalidateEditor()
            onMode2SettledToSafety(expectedGen, expectedCollapseEpoch)
        } else {
            if (tauLower != prevTau) {
                onInvalidateEditor()
            }
            scheduleMode2NextFrame()
        }
    }

    fun freezeForPointer(expectedGen: Long = owningDeletionGen): Boolean {
        if (isTeardownLocked || owningDeletionGen != expectedGen) return false
        if (phase == GapCollapsePhase.POINTER_FROZEN) return true
        if (phase != GapCollapsePhase.MODE_1_PHYSICAL_COUPLED &&
            phase != GapCollapsePhase.MODE_2_VISUAL_SETTLING
        ) {
            return false
        }
        val previousPhase = phase
        cancelMode2Frame()
        phaseBeforeFreeze = previousPhase
        phase = GapCollapsePhase.POINTER_FROZEN
        mode2State = null
        collapseAnimEpoch++
        if (previousPhase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED) {
            onFreezeMode1Animator()
        }
        return true
    }

    fun resumeFromPointerFrozen(
        expectedGen: Long = owningDeletionGen,
        actualPhysicalScrollPx: Float
    ): GapCollapsePhase {
        if (isTeardownLocked ||
            owningDeletionGen != expectedGen ||
            phase != GapCollapsePhase.POINTER_FROZEN
        ) {
            return phase
        }
        collapseAnimEpoch++
        return if (tauLower > ScribeGapCollapsePolicy.MODE2_SETTLE_THRESHOLD_PX && activeSeamLine >= 0) {
            startMode2Internal(actualPhysicalScrollPx, progressVelocity = 0f)
            onInvalidateEditor()
            GapCollapsePhase.MODE_2_VISUAL_SETTLING
        } else {
            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
            tauLower = 0f
            mode2State = null
            safetyEpoch++
            safetyRetryAttempted = false
        safetyCorrectionAttempted = false
            onInvalidateEditor()
            GapCollapsePhase.FINAL_SAFETY_PENDING
        }
    }

    fun onViewportPhysicalScrollChanged(
        actualPhysicalScroll: Float,
        origin: PhysicalScrollOrigin
    ): Boolean {
        if (isTeardownLocked) return false
        when (phase) {
            GapCollapsePhase.MODE_2_VISUAL_SETTLING -> {
                when (origin) {
                    PhysicalScrollOrigin.USER_CANVAS_DRAG,
                    PhysicalScrollOrigin.USER_CANVAS_FLING,
                    PhysicalScrollOrigin.USER_EDITOR_DRAG_OR_FLING,
                    PhysicalScrollOrigin.USER_HANDLE_AUTOSCROLL,
                    PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1,
                    PhysicalScrollOrigin.EVENT_B_SAFETY_CORRECTION -> {
                        lastMode2KnownPhysicalScroll = actualPhysicalScroll
                        return false
                    }
                    PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC,
                    PhysicalScrollOrigin.NONE -> {
                        if (abs(actualPhysicalScroll - lastMode2KnownPhysicalScroll) > 0.5f) {
                            cancelMode2Frame()
                            phase = GapCollapsePhase.FINAL_SAFETY_PENDING
                            tauLower = 0f
                            mode2State = null
                            collapseAnimEpoch++
                            safetyEpoch++
                            safetyRetryAttempted = false
                            safetyCorrectionAttempted = false
                            val g = owningDeletionGen
                            val ce = collapseAnimEpoch
                            onInvalidateEditor()
                            onExternalDriftAbortToSafety(g, ce)
                            return true
                        }
                        return false
                    }
                }
            }
            GapCollapsePhase.POINTER_FROZEN -> {
                if (origin != PhysicalScrollOrigin.NONE &&
                    origin != PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC
                ) {
                    lastMode2KnownPhysicalScroll = actualPhysicalScroll
                }
                return false
            }
            else -> return false
        }
    }

    fun tryClaimCancellation(expectedGen: Long): Boolean {
        if (isTeardownLocked ||
            phase == GapCollapsePhase.IDLE ||
            owningDeletionGen != expectedGen
        ) {
            return false
        }
        isTeardownLocked = true
        cancelMode2Frame()
        collapseAnimEpoch++
        safetyEpoch++
        pendingPostSafetyKeyboardEval = null
        return true
    }

    fun beginTeardownLockAndConsumeEval(
        expectedGen: Long,
        expectedCollapseEpoch: Long? = null,
        expectedSafetyEpoch: Long? = null
    ): TeardownClaimResult? {
        if (isTeardownLocked ||
            phase == GapCollapsePhase.IDLE ||
            owningDeletionGen != expectedGen
        ) {
            return null
        }
        if (expectedCollapseEpoch != null && collapseAnimEpoch != expectedCollapseEpoch) {
            return null
        }
        if (expectedSafetyEpoch != null && safetyEpoch != expectedSafetyEpoch) {
            return null
        }
        isTeardownLocked = true
        cancelMode2Frame()
        collapseAnimEpoch++
        safetyEpoch++
        val eval = pendingPostSafetyKeyboardEval
        pendingPostSafetyKeyboardEval = null
        return TeardownClaimResult(eval)
    }

    fun commitIdle() {
        cancelMode2Frame()
        phase = GapCollapsePhase.IDLE
        owningDeletionGen = -1L
        isTeardownLocked = false
        safetyRetryAttempted = false
        safetyCorrectionAttempted = false
        pendingPostSafetyKeyboardEval = null
        activeSeamLine = -1
        activeSeamCol = 0
        activeSeamFirstLowerRow = -1
        activeSeamDocY = 0f
        rowHeightPx = 0f
        currentPlan = null
        tauLower = 0f
        observedPhysicalVelocityEmaPxPerSec = null
        lastPhysicalScrollPx = 0f
        lastMode1FrameTimeNs = 0L
        mode1EverStarted = false
        mode2State = null
        lastMode2KnownPhysicalScroll = 0f
        phaseBeforeFreeze = GapCollapsePhase.IDLE
    }
}
