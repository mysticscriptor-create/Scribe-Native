package com.primaloptima.scribe.engine

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
