package com.primaloptima.scribe.util.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Last active target in the editor environment.
 */
@Immutable
@Serializable
enum class EditingTarget {
    NONE,
    PRIMARY_TITLE,
    SECONDARY_TITLE,
    EDITOR_BODY
}

/**
 * Snapshot of the user's cursor position, selection range, viewport offset,
 * and active editing target when leaving a document.
 */
@Immutable
@Serializable
data class DocumentCursorState(
    val target: EditingTarget = EditingTarget.NONE,
    val bodyStartLine: Int = 0,
    val bodyStartCol: Int = 0,
    val bodyEndLine: Int = 0,
    val bodyEndCol: Int = 0,
    val scrollD: Int = 0,
    val scrollY: Int = 0,
    val primarySelStart: Int = 0,
    val primarySelEnd: Int = 0,
    val secondarySelStart: Int = 0,
    val secondarySelEnd: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
