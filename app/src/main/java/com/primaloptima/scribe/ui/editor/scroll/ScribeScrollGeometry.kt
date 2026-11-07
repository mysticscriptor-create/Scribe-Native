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
