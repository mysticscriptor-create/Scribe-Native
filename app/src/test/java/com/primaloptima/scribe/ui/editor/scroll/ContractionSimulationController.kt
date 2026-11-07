package com.primaloptima.scribe.ui.editor.scroll

/**
 * Deterministic simulation controller for testing contraction restoration, retargeting,
 * and tick-by-tick compensation reconciliation in unit tests.
 */
class ContractionSimulationController(
    var totalScroll: Float,
    var temporaryDisplacement: Float,
    var caretLine: Int,
    var caretCol: Int = 0,
    var isSelected: Boolean = false,
    var caretDocBottom: Float,
    var headerHeight: Int = 200,
    var density: Float = 2.5f,
    var viewportHeight: Int = 1100
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

    var lastAppliedStep: Float = 0f
        private set

    val isRestorationActive: Boolean
        get() = coordinator.isAnimationActive && coordinator.activeReason == ScrollAnimationReason.CONTRACTION_RESTORATION

    val targetTotalScroll: Float
        get() = coordinator.activeTargetTotalScroll ?: totalScroll

    fun onViewportExpansion(newViewportHeight: Int) {
        viewportHeight = newViewportHeight
    }

    fun requestContractionRestoration(
        newViewportHeight: Int,
        isKeyboardDismissing: Boolean = false,
        isBottomSurfaceActive: Boolean = true
    ): ScribeScrollRestorationPolicy.ContractionRestorationResult {
        viewportHeight = newViewportHeight
        val caretScreenBottom = headerHeight + caretDocBottom - totalScroll
        val caretScreenTop = caretScreenBottom - (20f * density)

        val request = ScribeScrollRestorationPolicy.ContractionRestorationRequest(
            caretScreenBottom = caretScreenBottom,
            caretScreenTop = caretScreenTop,
            viewportHeight = newViewportHeight,
            density = density,
            totalScroll = totalScroll,
            temporaryDisplacement = temporaryDisplacement,
            isAtLastLine = false,
            isKeyboardDismissing = isKeyboardDismissing,
            isBottomSurfaceActive = isBottomSurfaceActive
        )

        val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(request)
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
