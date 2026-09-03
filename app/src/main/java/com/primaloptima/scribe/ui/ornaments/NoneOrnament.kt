package com.primaloptima.scribe.ui.ornaments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Option to remove the manuscript separator entirely.
 */
object NoneOrnament : ManuscriptOrnament {
    override val id: String = "none"
    override val displayName: String = "None (Remove Separator)"
    override val description: String = "No decorative divider between the titles and manuscript text"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        // Renders minimal spacing with zero visible graphics
        Spacer(modifier = modifier.height(4.dp))
    }
}
