package com.primaloptima.scribe.ui.ornaments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Interface representing an extensible manuscript divider ornament style.
 *
 * Adding a new ornament:
 * 1. Create an object in this folder implementing [ManuscriptOrnament].
 * 2. Add it to [OrnamentRegistry.all].
 * It will instantly appear in the picker sheet, persist across sessions, and render on screen.
 */
interface ManuscriptOrnament {
    val id: String
    val displayName: String
    val description: String

    @Composable
    fun Render(
        tint: Color,
        modifier: Modifier
    )
}
