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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Celtic Knotwork (Interlaced Ribbon Loop) manuscript divider.
 */
object CelticKnotOrnament : ManuscriptOrnament {
    override val id: String = "celtic_knot"
    override val displayName: String = "Celtic Knot"
    override val description: String = "Intricate interlaced infinity loop ribbon flanked by hairlines"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(170.dp)
                .height(20.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeW = 1.3.dp.toPx()

            // Outer infinity loop ribbon
            val knotPath = Path().apply {
                moveTo(cx, cy)
                cubicTo(cx - 10.dp.toPx(), cy - 7.dp.toPx(), cx - 18.dp.toPx(), cy - 7.dp.toPx(), cx - 18.dp.toPx(), cy)
                cubicTo(cx - 18.dp.toPx(), cy + 7.dp.toPx(), cx - 10.dp.toPx(), cy + 7.dp.toPx(), cx, cy)
                cubicTo(cx + 10.dp.toPx(), cy - 7.dp.toPx(), cx + 18.dp.toPx(), cy - 7.dp.toPx(), cx + 18.dp.toPx(), cy)
                cubicTo(cx + 18.dp.toPx(), cy + 7.dp.toPx(), cx + 10.dp.toPx(), cy + 7.dp.toPx(), cx, cy)
            }
            drawPath(knotPath, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Central vertical interlocking diamond loop
            val centerDiamond = Path().apply {
                moveTo(cx, cy - 6.5.dp.toPx())
                lineTo(cx + 4.dp.toPx(), cy)
                lineTo(cx, cy + 6.5.dp.toPx())
                lineTo(cx - 4.dp.toPx(), cy)
                close()
            }
            drawPath(centerDiamond, color = tint, style = Stroke(width = strokeW))

            // Flanking lines
            val lineOffset = 26.dp.toPx()
            val lineLen = 42.dp.toPx()

            drawLine(
                color = tint,
                start = Offset(cx - lineOffset - lineLen, cy),
                end = Offset(cx - lineOffset, cy),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(cx + lineOffset, cy),
                end = Offset(cx + lineOffset + lineLen, cy),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Outer terminal accent dots
            drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(cx - lineOffset - lineLen - 4.dp.toPx(), cy))
            drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(cx + lineOffset + lineLen + 4.dp.toPx(), cy))
        }
    }
}
