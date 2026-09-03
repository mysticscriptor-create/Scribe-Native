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
 * Classic Diamond (Lozenge) manuscript divider — ─── ❖ ───
 */
object ClassicDiamondOrnament : ManuscriptOrnament {
    override val id: String = "classic_diamond"
    override val displayName: String = "Classic Lozenge"
    override val description: String = "Refined diamond lozenge flanked by horizontal hairlines"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(160.dp)
                .height(18.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val diamondRadius = 5.5.dp.toPx()
            val innerDotRadius = 1.2.dp.toPx()
            val lineGap = 12.dp.toPx()
            val lineWidth = 52.dp.toPx()
            val strokeWidth = 1.dp.toPx()

            // Left hairline rule
            drawLine(
                color = tint,
                start = Offset(cx - lineGap - lineWidth, cy),
                end = Offset(cx - lineGap, cy),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Right hairline rule
            drawLine(
                color = tint,
                start = Offset(cx + lineGap, cy),
                end = Offset(cx + lineGap + lineWidth, cy),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Center diamond
            val diamondPath = Path().apply {
                moveTo(cx, cy - diamondRadius)
                lineTo(cx + diamondRadius, cy)
                lineTo(cx, cy + diamondRadius)
                lineTo(cx - diamondRadius, cy)
                close()
            }
            drawPath(diamondPath, color = tint)

            // Outer satellite accent pips
            drawCircle(color = tint, radius = innerDotRadius, center = Offset(cx - lineGap + 3.dp.toPx(), cy))
            drawCircle(color = tint, radius = innerDotRadius, center = Offset(cx + lineGap - 3.dp.toPx(), cy))
        }
    }
}
