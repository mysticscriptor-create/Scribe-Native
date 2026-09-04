package com.primaloptima.scribe.ui.components

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs

/**
 * ScribeCodeEditor — Custom Sora CodeEditor integration for UnifiedCanvasLayout.
 *
 * 1. Continues downward fling smoothly into the canvas header when reaching offset Y <= 0.
 * 2. Overrides ensurePositionVisible to track keyboard and shortcut bar height perfectly,
 *    keeping the active cursor line just above the shortcut bar rather than jumping 2 lines above.
 * 3. Configures prose writing input options (auto-capitalization after newlines and sentences).
 * 4. Focus-aware keyboard resize adjustments ensuring cursor visibility only when document is focused.
 */
class ScribeCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CodeEditor(context, attrs, defStyleAttr) {

    private var lastMakeVisibleTime: Long = 0L
    private var isFlingActive = false

    init {
        // Prevent auto-scrolling to cursor when keyboard opens or screen resizes while header/titles are focused
        props.adjustToSelectionOnResize = false

        // Distinguish autonomous flings from active finger drags to avoid premature snapping into header
        subscribeEvent(ScrollEvent::class.java) { event, _ ->
            when (event.cause) {
                ScrollEvent.CAUSE_USER_FLING -> isFlingActive = true
                ScrollEvent.CAUSE_USER_DRAG -> isFlingActive = false
                ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE -> isFlingActive = false
                ScrollEvent.CAUSE_TEXT_SELECTING -> isFlingActive = false
            }
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.inputType = outAttrs.inputType or
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        return connection
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Focus-aware keyboard adjustment: when keyboard appears (height decreases)
        // and the document has focus, reveal the cursor above the keyboard.
        if (isFocused && h < oldh) {
            post {
                ensureSelectionVisible()
                (parent as? UnifiedCanvasLayout)?.ensureCursorVisibleAboveKeyboard()
            }
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            postDelayed({
                if (isFocused) {
                    ensureSelectionVisible()
                    (parent as? UnifiedCanvasLayout)?.ensureCursorVisibleAboveKeyboard()
                }
            }, 100)
        }
    }

    override fun ensureSelectionVisible() {
        // Only scroll to selection if this editor actually has keyboard focus
        if (!isFocused) return
        super.ensureSelectionVisible()
    }

    override fun computeScroll() {
        val scroller = scroller
        val wasFinished = scroller?.isFinished ?: true
        val prevY = scroller?.currY ?: 0

        super.computeScroll()

        // When flinging towards the top of the text (offsetY reaching 0):
        // Only transfer fling to canvas header if this is a genuine fling and user is not holding finger down
        if (!wasFinished && scroller != null && scroller.currY <= 0 && prevY > 0) {
            val parentCanvas = parent as? UnifiedCanvasLayout
            val velocity = scroller.currVelocity
            if (velocity > 0f && isFlingActive && parentCanvas?.isUserTouching != true) {
                isFlingActive = false
                scroller.abortAnimation()
                parentCanvas?.continueFlingFromEditor(velocity)
            }
        }
        if (scroller != null && scroller.isFinished) {
            isFlingActive = false
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
