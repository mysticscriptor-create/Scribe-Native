package com.primaloptima.scribe.ui.editor.scroll

/**
 * Reusable, pure geometry models and data structures for cursor, selection,
 * and character bounds across the document canvas.
 *
 * Free of Android View or widget framework dependencies.
 */

/**
 * Unified geometry description for cursor or selection bounds in document coordinates.
 * Accurately distinguishes vertical top/bottom from horizontal left/right,
 * and accounts for multi-line selection endpoints.
 */
data class SelectionGeometry(
    val topDocY: Float,
    val bottomDocY: Float,
    val leftDocX: Float,
    val rightDocX: Float,
    val isSelected: Boolean,
    val anchorLine: Int,
    val focusLine: Int
)

/**
 * Clean, strongly typed abstraction for character and layout geometry from Sora Editor.
 * Eliminates confusion between vertical doc Y and horizontal doc X.
 */
data class SoraCharGeometry(
    val bottomDocY: Float,
    val topDocY: Float,
    val leftDocX: Float
)

/**
 * Outcome model for safe target boundary validation.
 */
data class SafeTargetValidationResult(
    val isSafe: Boolean,
    val finalCaretScreenBottom: Float,
    val targetLineBottom: Float,
    val correctiveDeltaY: Float
)

/**
 * Target line and column resolution for cursor visibility positioning.
 */
data class CursorTargetResolution(
    val line: Int,
    val column: Int,
    val shouldScroll: Boolean
)

/**
 * High-level mode of the unified bottom dock.
 */
enum class ScribeDockMode {
    HIDDEN,
    IME,
    ACCESSORY_DRAWER
}

/**
 * Explicit transition provenance for the unified bottom dock.
 */
enum class ScribeDockTransition {
    NONE,
    IME_ENTRANCE,
    IME_DISMISSAL,
    IME_RESIZE,
    DRAWER_OPEN,
    DRAWER_CLOSE,
    DRAWER_RESIZE,
    IME_TO_DRAWER_HANDOFF,
    DRAWER_TO_IME_HANDOFF
}

/**
 * Immutable snapshot of unified bottom dock geometry and clearance state.
 */
data class ScribeDockGeometry(
    val dockMode: ScribeDockMode,
    val activeTransition: ScribeDockTransition,
    val transitionGeneration: Long,
    val standardKeyboardBaselinePx: Float,
    val currentImeObstructionPx: Float,
    val currentDrawerHeightPx: Float,
    val shortcutBarHeightPx: Float,
    val isShortcutBarOccupied: Boolean,
    val physicalDockClearanceAppliedPx: Float,
    val virtualTailPx: Float
)

