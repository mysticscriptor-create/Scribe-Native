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
 * Laurel Branch (Classical Imperial Wreath Sprig) divider.
 */
object LaurelBranchOrnament : ManuscriptOrnament {
    override val id: String = "laurel_branch"
    override val displayName: String = "Laurel Sprig"
    override val description: String = "Symmetrical classical twin laurel leaves curving gracefully"

    private fun drawLeaf(path: Path, startX: Float, startY: Float, tipX: Float, tipY: Float, bulge: Float) {
        val midX = (startX + tipX) / 2f
        val midY = (startY + tipY) / 2f
        path.moveTo(startX, startY)
        path.cubicTo(midX - bulge, midY - bulge, midX - bulge, midY + bulge, tipX, tipY)
        path.cubicTo(midX + bulge, midY + bulge, midX + bulge, midY - bulge, startX, startY)
        path.close()
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
            val strokeW = 1.1.dp.toPx()

            // Central seed pip
            drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(cx, cy))

            // Main stem curves
            val leftStem = Path().apply {
                moveTo(cx - 4.dp.toPx(), cy)
                cubicTo(cx - 20.dp.toPx(), cy - 4.dp.toPx(), cx - 45.dp.toPx(), cy + 2.dp.toPx(), cx - 65.dp.toPx(), cy)
            }
            drawPath(leftStem, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            val rightStem = Path().apply {
                moveTo(cx + 4.dp.toPx(), cy)
                cubicTo(cx + 20.dp.toPx(), cy - 4.dp.toPx(), cx + 45.dp.toPx(), cy + 2.dp.toPx(), cx + 65.dp.toPx(), cy)
            }
            drawPath(rightStem, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))

            // Left leaves
            val leavesPath = Path().apply {
                drawLeaf(this, cx - 18.dp.toPx(), cy - 2.dp.toPx(), cx - 26.dp.toPx(), cy - 7.dp.toPx(), 2.dp.toPx())
                drawLeaf(this, cx - 35.dp.toPx(), cy - 1.dp.toPx(), cx - 43.dp.toPx(), cy - 6.dp.toPx(), 2.dp.toPx())
                drawLeaf(this, cx - 26.dp.toPx(), cy + 1.dp.toPx(), cx - 34.dp.toPx(), cy + 6.dp.toPx(), 2.dp.toPx())

                // Right leaves
                drawLeaf(this, cx + 18.dp.toPx(), cy - 2.dp.toPx(), cx + 26.dp.toPx(), cy - 7.dp.toPx(), 2.dp.toPx())
                drawLeaf(this, cx + 35.dp.toPx(), cy - 1.dp.toPx(), cx + 43.dp.toPx(), cy - 6.dp.toPx(), 2.dp.toPx())
                drawLeaf(this, cx + 26.dp.toPx(), cy + 1.dp.toPx(), cx + 34.dp.toPx(), cy + 6.dp.toPx(), 2.dp.toPx())
            }
            drawPath(leavesPath, color = tint)
        }
    }
}
