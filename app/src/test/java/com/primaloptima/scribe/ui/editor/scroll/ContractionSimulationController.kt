package com.primaloptima.scribe.ui.editor.scroll

/**
 * Deterministic simulation controller for testing contraction restoration, retargeting,
 * and tick-by-tick compensation reconciliation in unit tests.
 *
 * Restores the exact original API and behavior from d0d39b5, updated only to reference
 * the extracted production types directly.
 */
class ContractionSimulationController(
    var totalScroll: Float,
    var temporaryDisplacement: Float,
    var caretLine: Int,
    var caretCol: Int = 0,
    var isSelected: Boolean = false,
    var caretDocBottom: Float,
    var headerHeight: Int = 200,
    var density: Float = 2.5f
) {
    val coordinator = ScribeScrollCoordinator(
        totalScroll = totalScroll,
        temporaryDisplacement = temporaryDisplacement,
        caretLine = caretLine,
        caretCol = caretCol,
        isSelected = isSelected,
        caretDocBottom = caretDocBottom,
        headerHeight = headerHeight,
        density = density
    )

    val isRestorationActive: Boolean
        get() = coordinator.isAnimationActive

    val targetTotalScroll: Float
        get() = coordinator.activeTargetTotalScroll ?: totalScroll

    var lastAppliedStep: Float = 0f
        private set

    init {
        coordinator.onScrollBy = { delta ->
            totalScroll += delta
            0f
        }
    }

    fun onViewportHeightChanged(
        newViewportHeight: Int,
        isAtLastLine: Boolean,
        isKeyboardDismissing: Boolean,
        isBottomSurfaceActive: Boolean
    ): ScribeScrollRestorationPolicy.ContractionRestorationResult {
        coordinator.totalScroll = totalScroll
        coordinator.temporaryDisplacement = temporaryDisplacement
        coordinator.caretLine = caretLine
        coordinator.caretCol = caretCol
        coordinator.isSelected = isSelected
        coordinator.caretDocBottom = caretDocBottom
        coordinator.headerHeight = headerHeight
        coordinator.density = density

        if (isKeyboardDismissing || !isBottomSurfaceActive) {
            coordinator.invalidateCompensation("Dismissed")
            temporaryDisplacement = 0f
            caretLine = -1
            return ScribeScrollRestorationPolicy.ContractionRestorationResult(0f, false, 0f, "Dismissed")
        }

        val caretScreenBottom = (headerHeight + caretDocBottom - totalScroll)
        val req = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = caretScreenBottom,
            caretScreenTop = caretScreenBottom - 40f,
            viewportHeight = newViewportHeight,
            density = density,
            totalScroll = totalScroll,
            temporaryDisplacement = temporaryDisplacement,
            isAtLastLine = isAtLastLine,
            isKeyboardDismissing = false,
            isBottomSurfaceActive = true
        )

        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(req)
        if (result.shouldScroll) {
            val candidateTarget = (totalScroll + result.deltaY).coerceAtLeast(0f)
            val validation = ScribeScrollRestorationPolicy.validateFinalTarget(candidateTarget, caretDocBottom, headerHeight, newViewportHeight, density)
            val safeTarget = if (!validation.isSafe) candidateTarget + validation.correctiveDeltaY else candidateTarget
            coordinator.requestTarget(safeTarget, ScrollAnimationReason.CONTRACTION_RESTORATION)
        }
        return result
    }

    fun applyAnimationStep(fraction: Float) {
        if (!isRestorationActive) return
        val stepTarget = totalScroll + (targetTotalScroll - totalScroll) * fraction
        val moved = coordinator.applyStep(stepTarget)
        lastAppliedStep = moved
        totalScroll = coordinator.totalScroll
        temporaryDisplacement = coordinator.temporaryDisplacement
        caretLine = coordinator.caretLine
    }

    fun cancelRestoration() {
        coordinator.cancelAnimation()
    }

    fun onUserTouch() {
        coordinator.invalidateCompensation("User touch")
        temporaryDisplacement = 0f
        caretLine = -1
    }

    fun onCaretMoved(newLine: Int, newCol: Int, newSelected: Boolean) {
        coordinator.invalidateIfCaretChanged(newLine, newCol, newSelected)
        temporaryDisplacement = coordinator.temporaryDisplacement
        caretLine = coordinator.caretLine
    }

    fun onContentEdited() {
        coordinator.invalidateCompensation("Content edited")
        temporaryDisplacement = 0f
        caretLine = -1
    }
}
