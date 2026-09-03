package com.primaloptima.scribe.ui.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import androidx.compose.ui.platform.ComposeView
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * UnifiedCanvasLayout — Edge-to-Edge Document Canvas Architecture.
 *
 * Hosts a ComposeView header (Child 0) and a Sora CodeEditor (Child 1) inside a single,
 * continuous vertical document canvas. Coordinates touch dispatch, flings across the
 * boundary, keyboard inset management, and 120Hz smooth rendering without touch hijacking.
 */
class UnifiedCanvasLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    val headerView: ComposeView = ComposeView(context).apply {
        id = View.generateViewId()
    }

    val editor: ScribeCodeEditor = ScribeCodeEditor(context).apply {
        id = View.generateViewId()
    }

    init {
        addView(headerView)
        addView(editor)
        clipChildren = false
        clipToPadding = false
    }

    var headerHeight: Int = 0
        private set

    var scrollD: Int = 0
        private set

    private var scrollDFloat: Float = 0f

    var onScrollDelta: ((dy: Float) -> Unit)? = null
    var onUnifiedScrollChanged: ((scrollD: Int, maxHeaderHeight: Int) -> Unit)? = null

    private val scroller = OverScroller(context)
    private var lastScrollerY = 0

    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var initialDownX = 0f
    private var initialDownY = 0f
    private var lastTouchY = 0f
    private var isDraggingCanvas = false

    fun resetScroll() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        try {
            editor.scroller?.abortAnimation()
        } catch (_: Throwable) {}
        scrollDFloat = 0f
        scrollD = 0
        isDraggingCanvas = false
        applyTranslations()
        onUnifiedScrollChanged?.invoke(0, headerHeight)
        try {
            val scroller = editor.scroller
            if (scroller != null) {
                scroller.startScroll(scroller.currX, 0, 0, 0, 0)
                scroller.abortAnimation()
                editor.invalidate()
            }
        } catch (_: Throwable) {}
    }

    /**
     * Continues a downward fling from the editor into the canvas header.
     * Called by ScribeCodeEditor when flinging towards the top and offsetY hits 0.
     */
    fun continueFlingFromEditor(velocity: Float) {
        if (scrollD <= 0) return
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        val vy = -abs(velocity).toInt()
        lastScrollerY = scrollD
        scroller.fling(0, scrollD, 0, vy, 0, 0, 0, headerHeight, 0, 0)
        postInvalidateOnAnimation()
    }

    /**
     * When user types or focuses the editor, ensures the cursor is never occluded
     * by the keyboard or shortcut bar by scrolling header off or adjusting editor scroll
     * so that the active cursor line stays just above the shortcut bar.
     */
    fun ensureCursorVisibleAboveKeyboard() {
        val cursor = try { editor.cursor } catch (_: Throwable) { null } ?: return
        val line = cursor.leftLine
        val col = cursor.leftColumn
        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val layoutOffset = try { layout.getCharLayoutOffset(line, col) } catch (_: Throwable) { null } ?: return
        val yOffset = layoutOffset[0] // doc coordinate of line bottom
        val screenBottom = (headerHeight - scrollD) + yOffset - editor.offsetY
        val visibleHeight = height
        val marginPx = (4 * resources.displayMetrics.density).roundToInt()

        if (visibleHeight > 0 && screenBottom > visibleHeight - marginPx) {
            val overflow = screenBottom - (visibleHeight - marginPx)
            if (scrollD < headerHeight) {
                val canConsume = (headerHeight - scrollD).toFloat()
                val consume = minOf(overflow.toFloat(), canConsume)
                scrollDFloat += consume
                scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                val remaining = overflow - consume.toInt()
                if (remaining > 0) {
                    dispatchScrollToEditor(remaining.toFloat())
                }
            } else {
                dispatchScrollToEditor(overflow.toFloat())
            }
        }
    }

    private fun applyTranslations() {
        if (editor.offsetY > 0 && scrollD < headerHeight) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        }
        val d = scrollD.toFloat()
        headerView.translationY = -d
        editor.translationY = -d
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val viewportHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Measure header with EXACTLY width and UNSPECIFIED height
        headerView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val measuredH = headerView.measuredHeight
        if (measuredH > 0) {
            headerHeight = measuredH
        }

        // Maintain continuous canvas invariant on resume/relayout:
        // If the editor is scrolled down into text, the header must be scrolled off.
        if (editor.offsetY > 0) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        } else if (scrollD > headerHeight) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        }

        // Measure CodeEditor to fill the full viewport height
        editor.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(width, viewportHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val viewportHeight = b - t

        headerView.layout(0, 0, width, headerHeight)
        editor.layout(0, headerHeight, width, headerHeight + viewportHeight)

        if (editor.offsetY > 0) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        }
        applyTranslations()
    }

    /**
     * Intercepts child requests to keep rectangles (like cursor) visible.
     * Offsets the requested rectangle by the editor's visual position, scrolling scrollD
     * so that the typing cursor is strictly kept above the keyboard and shortcut bar.
     */
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        if (child === editor) {
            val screenTop = (headerHeight - scrollD) + rectangle.top
            val screenBottom = (headerHeight - scrollD) + rectangle.bottom
            val visibleHeight = height

            if (screenBottom > visibleHeight) {
                val delta = screenBottom - visibleHeight
                val canScroll = (headerHeight - scrollD).toFloat()
                val consume = minOf(delta.toFloat(), canScroll)
                if (consume > 0f) {
                    scrollDFloat += consume
                    scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return true
                }
            } else if (screenTop < 0 && scrollD > 0) {
                val delta = -screenTop.toFloat()
                val consume = minOf(delta, scrollDFloat)
                if (consume > 0f) {
                    scrollDFloat -= consume
                    scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return true
                }
            }
        }
        return super.requestChildRectangleOnScreen(child, rectangle, immediate)
    }

    /**
     * Disallow intercept override: when the editor is at the top of the document (offsetY <= 0)
     * and the title is scrolled off, we MUST NOT allow the editor to lock out downward drags.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && editor.offsetY <= 0 && scrollD > 0) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * Consumes scroll delta vertically.
     * @param dy > 0 means finger moving up (content scrolls down, document canvas moves into view).
     *           dy < 0 means finger moving down (content scrolls up, pulling header back into view).
     * @return unconsumed delta.
     */
    fun scrollCanvasBy(dy: Float): Float {
        onScrollDelta?.invoke(dy)

        if (dy > 0f) {
            // Scrolling down into document (finger moving up)
            if (scrollDFloat < headerHeight) {
                val canConsume = headerHeight - scrollDFloat
                val consume = minOf(dy, canConsume)
                scrollDFloat += consume
                scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                val unconsumed = dy - consume
                if (unconsumed > 0f) {
                    dispatchScrollToEditor(unconsumed)
                }
                return unconsumed
            } else {
                dispatchScrollToEditor(dy)
                return dy
            }
        } else if (dy < 0f) {
            // Scrolling up towards top of document (finger moving down)
            val editorY = editor.offsetY
            if (editorY > 0) {
                val canConsumeEditor = -editorY.toFloat()
                val consumeEditor = maxOf(dy, canConsumeEditor)
                dispatchScrollToEditor(consumeEditor)
                val unconsumed = dy - consumeEditor
                if (unconsumed < 0f && scrollDFloat > 0f) {
                    val canConsumeCanvas = -scrollDFloat
                    val consumeCanvas = maxOf(unconsumed, canConsumeCanvas)
                    scrollDFloat += consumeCanvas
                    scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return unconsumed - consumeCanvas
                }
                return unconsumed
            } else {
                // Editor is at top, pull header down
                if (scrollDFloat > 0f) {
                    val canConsumeCanvas = -scrollDFloat
                    val consumeCanvas = maxOf(dy, canConsumeCanvas)
                    scrollDFloat += consumeCanvas
                    scrollD = scrollDFloat.roundToInt().coerceIn(0, headerHeight)
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return dy - consumeCanvas
                }
                return dy
            }
        }
        return 0f
    }

    /**
     * Natively scrolls Sora CodeEditor via its internal EditorScroller.
     * This avoids calling View.scrollBy() which desynchronizes the View hardware canvas,
     * ensuring lines render smoothly at 120Hz without blank screens during fast scrolling.
     */
    private fun dispatchScrollToEditor(dy: Float) {
        try {
            val scroller = editor.scroller ?: return
            val currY = scroller.currY
            val targetY = (currY + dy).roundToInt().coerceIn(0, editor.scrollMaxY)
            if (targetY != currY) {
                scroller.startScroll(scroller.currX, currY, 0, targetY - currY, 0)
                scroller.abortAnimation()
                editor.invalidate()
            }
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                try {
                    editor.scroller?.abortAnimation()
                } catch (_: Throwable) {}
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchY = ev.y
                isDraggingCanvas = false
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.y
                lastTouchY = ev.y

                if (isDraggingCanvas) {
                    scrollCanvasBy(dy)
                    return true
                }

                // If user is dragging downwards (dy < 0) and editor is at top (offsetY <= 0) and header is hidden (scrollD > 0)
                if (dy < 0f && editor.offsetY <= 0 && scrollD > 0) {
                    isDraggingCanvas = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val cancelEvent = MotionEvent.obtain(ev).apply {
                        action = MotionEvent.ACTION_CANCEL
                    }
                    editor.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()

                    scrollCanvasBy(dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingCanvas) {
                    isDraggingCanvas = false
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (abs(vy) > minFlingVelocity) {
                        val scrollerVy = -vy.toInt()
                        lastScrollerY = scrollD + editor.offsetY
                        val maxScroll = headerHeight + editor.scrollMaxY
                        scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                        postInvalidateOnAnimation()
                    }
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDraggingCanvas) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchY = ev.y
                isDraggingCanvas = false
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val totalDx = ev.x - initialDownX
                val totalDy = initialDownY - ev.y

                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                    if (totalDy > 0f && scrollD < headerHeight) {
                        // Dragging up and header is visible: intercept canvas drag!
                        isDraggingCanvas = true
                        lastTouchY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else if (totalDy < 0f && editor.offsetY <= 0 && scrollD > 0) {
                        // Dragging down and editor is at top: intercept to pull header down!
                        isDraggingCanvas = true
                        lastTouchY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingCanvas = false
            }
        }
        return isDraggingCanvas
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                try {
                    editor.scroller?.abortAnimation()
                } catch (_: Throwable) {}
                lastTouchY = ev.y
                initialDownY = ev.y
                initialDownX = ev.x
                isDraggingCanvas = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.y
                lastTouchY = ev.y
                scrollCanvasBy(dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val vy = velocityTracker?.yVelocity ?: 0f
                if (abs(vy) > minFlingVelocity) {
                    val scrollerVy = -vy.toInt()
                    lastScrollerY = scrollD + editor.offsetY
                    val maxScroll = headerHeight + editor.scrollMaxY
                    scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                    postInvalidateOnAnimation()
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val currY = scroller.currY
            val dy = (currY - lastScrollerY).toFloat()
            lastScrollerY = currY

            scrollCanvasBy(dy)

            postInvalidateOnAnimation()
        }
    }
}
