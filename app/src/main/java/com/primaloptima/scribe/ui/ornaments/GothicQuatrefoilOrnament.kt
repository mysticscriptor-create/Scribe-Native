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
 * Gothic Quatrefoil (Medieval Four-Lobe Manuscript Blossom) divider.
 */
object GothicQuatrefoilOrnament : ManuscriptOrnament {
    override val id: String = "quatrefoil"
    override val displayName: String = "Gothic Quatrefoil"
    override val description: String = "Four-lobed medieval manuscript clover motif with accent rules"

    @Composable
    override fun Render(tint: Color, modifier: Modifier) {
        Canvas(
            modifier = modifier
                .width(170.dp)
                .height(20.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val lobeRadius = 3.6.dp.toPx()
            val strokeW = 1.1.dp.toPx()

            // 4 lobes
            drawCircle(color = tint, radius = lobeRadius, center = Offset(cx, cy - 3.dp.toPx()), style = Stroke(width = strokeW))
            drawCircle(color = tint, radius = lobeRadius, center = Offset(cx, cy + 3.dp.toPx()), style = Stroke(width = strokeW))
            drawCircle(color = tint, radius = lobeRadius, center = Offset(cx - 3.dp.toPx(), cy), style = Stroke(width = strokeW))
            drawCircle(color = tint, radius = lobeRadius, center = Offset(cx + 3.dp.toPx(), cy), style = Stroke(width = strokeW))

            // Center core
            drawCircle(color = tint, radius = 1.4.dp.toPx(), center = Offset(cx, cy))

            // Flanking lines
            val lineOffset = 16.dp.toPx()
            val lineLen = 48.dp.toPx()

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

            // Terminal dots
            drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(cx - lineOffset - lineLen - 3.dp.toPx(), cy))
            drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(cx + lineOffset + lineLen + 3.dp.toPx(), cy))
        }
    }
}
