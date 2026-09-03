package com.primaloptima.scribe.ui.ornaments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Asterism (Celestial Three-Star Constellation) ornament — ⁂
 */
object AsterismOrnament : ManuscriptOrnament {
    override val id: String = "asterism"
    override val displayName: String = "Three Stars (Asterism)"
    override val description: String = "Traditional literary three-star asterism with delicate rules"

    private fun createFourPointStar(cx: Float, cy: Float, outerR: Float, innerR: Float): Path {
        return Path().apply {
            moveTo(cx, cy - outerR)
            cubicTo(cx, cy - innerR, cx + innerR, cy, cx + outerR, cy)
            cubicTo(cx + innerR, cy, cx, cy + innerR, cx, cy + outerR)
            cubicTo(cx, cy + innerR, cx - innerR, cy, cx - outerR, cy)
            cubicTo(cx - innerR, cy, cx, cy - innerR, cx, cy - outerR)
            close()
        }
    }

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(170.dp)
                .height(20.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val spacing = 22.dp.toPx()

            // Center primary star
            val centerStar = createFourPointStar(cx, cy, 6.5.dp.toPx(), 1.5.dp.toPx())
            drawPath(centerStar, color = tint)

            // Left secondary star
            val leftStar = createFourPointStar(cx - spacing, cy, 4.5.dp.toPx(), 1.2.dp.toPx())
            drawPath(leftStar, color = tint)

            // Right secondary star
            val rightStar = createFourPointStar(cx + spacing, cy, 4.5.dp.toPx(), 1.2.dp.toPx())
            drawPath(rightStar, color = tint)

            // Flanking hairline rules
            val lineStartOffset = spacing + 14.dp.toPx()
            val lineLength = 36.dp.toPx()
            val strokeW = 1.dp.toPx()

            drawLine(
                color = tint,
                start = Offset(cx - lineStartOffset - lineLength, cy),
                end = Offset(cx - lineStartOffset, cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(cx + lineStartOffset, cy),
                end = Offset(cx + lineStartOffset + lineLength, cy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}
