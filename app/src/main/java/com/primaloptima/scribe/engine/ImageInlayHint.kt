package com.primaloptima.scribe.engine

import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint

/**
 * Click data dispatched when an image inlay hint is tapped.
 */
data class ImageHintClickData(
    val line: Int,
    val column: Int,
    val description: String,
    val imageUri: String,
    val rawTag: String,
    val viewX: Float,
    val viewY: Float
)

/**
 * Inlay hint representing an inline Markdown image.
 * Extends [TextInlayHint] to utilize Sora's built-in styled badge pill rendering.
 */
class ImageInlayHint(
    line: Int,
    column: Int,
    val description: String,
    val imageUri: String,
    val rawTag: String
) : TextInlayHint(
    line,
    column,
    "  🖼 ${if (description.isNotBlank()) description else "Image"}"
)
