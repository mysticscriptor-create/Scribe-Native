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
 * Aldus Fleuron (Printer's Hedera / Renaissance Ivy Leaf) ornament.
 */
object FleuronOrnament : ManuscriptOrnament {
    override val id: String = "fleuron"
    override val displayName: String = "Aldus Fleuron"
    override val description: String = "Renaissance printer's floral leaf flourish with curved tendrils"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(180.dp)
                .height(20.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeW = 1.1.dp.toPx()

            // Central ivy leaf / heart bud
            val leafPath = Path().apply {
                moveTo(cx, cy - 6.dp.toPx())
                cubicTo(
                    cx + 5.dp.toPx(), cy - 7.dp.toPx(),
                    cx + 7.dp.toPx(), cy - 1.dp.toPx(),
                    cx, cy + 6.dp.toPx()
                )
                cubicTo(
                    cx - 7.dp.toPx(), cy - 1.dp.toPx(),
                    cx - 5.dp.toPx(), cy - 7.dp.toPx(),
                    cx, cy - 6.dp.toPx()
                )
                close()
            }
            drawPath(leafPath, color = tint)

            // Central vein pip
            drawCircle(color = tint, radius = 1.dp.toPx(), center = Offset(cx, cy))

            // Left curved tendril
            val leftTendril = Path().apply {
                moveTo(cx - 10.dp.toPx(), cy)
                cubicTo(
                    cx - 24.dp.toPx(), cy - 5.dp.toPx(),
                    cx - 40.dp.toPx(), cy + 5.dp.toPx(),
                    cx - 65.dp.toPx(), cy
                )
            }
            drawPath(
                path = leftTendril,
                color = tint,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Left end spiral / curl
            drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(cx - 66.dp.toPx(), cy - 1.dp.toPx()))

            // Right curved tendril
            val rightTendril = Path().apply {
                moveTo(cx + 10.dp.toPx(), cy)
                cubicTo(
                    cx + 24.dp.toPx(), cy - 5.dp.toPx(),
                    cx + 40.dp.toPx(), cy + 5.dp.toPx(),
                    cx + 65.dp.toPx(), cy
                )
            }
            drawPath(
                path = rightTendril,
                color = tint,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Right end spiral / curl
            drawCircle(color = tint, radius = 1.6.dp.toPx(), center = Offset(cx + 66.dp.toPx(), cy - 1.dp.toPx()))
        }
    }
}
