package com.primaloptima.scribe.ui.ornaments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Minimalist Tapered Hairline rule with a central accent pip.
 */
object MinimalRuleOrnament : ManuscriptOrnament {
    override val id: String = "minimal_rule"
    override val displayName: String = "Minimalist Rule"
    override val description: String = "Understated modern tapered hairline with a delicate center pip"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(180.dp)
                .height(16.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val totalHalfWidth = 75.dp.toPx()
            val centerGap = 8.dp.toPx()

            // Left tapered line (fades in from transparent to tint)
            val leftBrush = Brush.linearGradient(
                colors = listOf(tint.copy(alpha = 0f), tint),
                start = Offset(cx - totalHalfWidth, cy),
                end = Offset(cx - centerGap, cy)
            )
            drawLine(
                brush = leftBrush,
                start = Offset(cx - totalHalfWidth, cy),
                end = Offset(cx - centerGap, cy),
                strokeWidth = 1.1.dp.toPx()
            )

            // Right tapered line (fades out from tint to transparent)
            val rightBrush = Brush.linearGradient(
                colors = listOf(tint, tint.copy(alpha = 0f)),
                start = Offset(cx + centerGap, cy),
                end = Offset(cx + totalHalfWidth, cy)
            )
            drawLine(
                brush = rightBrush,
                start = Offset(cx + centerGap, cy),
                end = Offset(cx + totalHalfWidth, cy),
                strokeWidth = 1.1.dp.toPx()
            )

            // Center subtle pip
            drawCircle(
                color = tint,
                radius = 2.2.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}
