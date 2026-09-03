package com.primaloptima.scribe.ui.components

import android.content.Context
import android.util.AttributeSet
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs

/**
 * ScribeCodeEditor — Custom Sora CodeEditor integration for UnifiedCanvasLayout.
 *
 * 1. Continues downward fling smoothly into the canvas header when reaching offset Y <= 0.
 * 2. Overrides ensurePositionVisible to track keyboard and shortcut bar height perfectly,
 *    keeping the active cursor line just above the shortcut bar rather than jumping 2 lines above.
 */
class ScribeCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CodeEditor(context, attrs, defStyleAttr) {

    private var lastMakeVisibleTime: Long = 0L

    override fun computeScroll() {
        val scroller = scroller
        val wasFinished = scroller?.isFinished ?: true
        val prevY = scroller?.currY ?: 0

        super.computeScroll()

        // When flinging towards the top of the text (offsetY reaching 0):
        if (!wasFinished && scroller != null && scroller.currY <= 0 && prevY > 0) {
            val velocity = scroller.currVelocity
            if (velocity > 0f) {
                scroller.abortAnimation()
                (parent as? UnifiedCanvasLayout)?.continueFlingFromEditor(velocity)
            }
        }
    }

    override fun ensurePositionVisible(line: Int, column: Int, noAnimation: Boolean) {
        val scroller = scroller ?: return
        val layout = layout ?: return
        val layoutOffset = try { layout.getCharLayoutOffset(line, column) } catch (_: Throwable) { null } ?: return
        val xOffset = layoutOffset[1] + measureTextRegionOffset()
        val yOffset = layoutOffset[0] // Bottom of current row in doc coordinates

        val currFinalY = if (scroller.isFinished) offsetY.toFloat() else scroller.finalY.toFloat()
        val currFinalX = if (scroller.isFinished) offsetX.toFloat() else scroller.finalX.toFloat()
        var targetY = currFinalY
        var targetX = currFinalX

        val parentCanvas = parent as? UnifiedCanvasLayout
        val headerRemaining = parentCanvas?.let { it.headerHeight - it.scrollD } ?: 0
        val effectiveHeight = (height - headerRemaining).coerceAtLeast(rowHeight)

        val topLines = if (props.stickyScroll) props.stickyScrollMaxLines else 2
        if (yOffset - rowHeight * topLines < currFinalY) {
            targetY = yOffset - rowHeight * topLines
        }

        // Bottom boundary:
        // Keep active typing line just above the shortcut bar (with 4dp margin for descenders)
        // instead of Sora's default + getRowHeight() * 1f which pushed the line 2 rows up.
        val bottomMargin = 4f * dpUnit
        if (yOffset > effectiveHeight + currFinalY - bottomMargin) {
            targetY = yOffset - effectiveHeight + bottomMargin
        }

        val charWidth = if (column == 0) 0f else textPaint.measureText("a")
        if (xOffset < currFinalX + (if (isLineNumberPinned) measureTextRegionOffset() else 0f)) {
            val backupX = targetX
            val scrollSlopX = width / 2f
            targetX = xOffset + (if (isLineNumberPinned) -measureTextRegionOffset() else 0f) - charWidth
            if (abs(targetX - backupX) < scrollSlopX) {
                targetX = maxOf(1f, backupX - scrollSlopX)
            }
        }
        if (xOffset + charWidth > currFinalX + width) {
            targetX = xOffset + charWidth * 0.8f - width
        }

        targetX = targetX.coerceIn(0f, scrollMaxX.toFloat())
        targetY = targetY.coerceIn(0f, scrollMaxY.toFloat())

        if (abs(targetX - offsetX.toFloat()) < 1f && abs(targetY - offsetY.toFloat()) < 1f) {
            invalidate()
            return
        }

        val now = System.currentTimeMillis()
        val animation = now - lastMakeVisibleTime >= 100
        lastMakeVisibleTime = now

        if (animation && !noAnimation) {
            scroller.forceFinished(true)
            scroller.startScroll(offsetX, offsetY, (targetX - offsetX).toInt(), (targetY - offsetY).toInt())
            if (props.awareScrollbarWhenAdjust && abs(offsetY - targetY) > dpUnit * 100) {
                eventHandler.notifyScrolled()
            }
        } else {
            scroller.startScroll(offsetX, offsetY, (targetX - offsetX).toInt(), (targetY - offsetY).toInt(), 0)
            scroller.abortAnimation()
        }

        dispatchEvent(ScrollEvent(this, offsetX, offsetY, targetX.toInt(), targetY.toInt(), ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE))
        invalidate()
    }
}
