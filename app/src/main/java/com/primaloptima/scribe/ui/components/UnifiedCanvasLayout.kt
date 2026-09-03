package com.primaloptima.scribe.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.OverScroller
import androidx.compose.ui.platform.ComposeView
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs

/**
 * UnifiedCanvasLayout — Edge-to-Edge Document Canvas Architecture.
 *
 * Hosts a ComposeView header (Child 0) and a Sora CodeEditor (Child 1) inside a single,
 * continuous vertical document canvas. Coordinates bidirectional touch interception and
 * momentum flings between the document header and editor body.
 */
class UnifiedCanvasLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    val headerView: ComposeView = ComposeView(context).apply {
        id = View.generateViewId()
    }

    val editor: CodeEditor = CodeEditor(context).apply {
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
    private var lastDispatchY = 0f
    private var isDraggingCanvas = false

    init {
        // Listen to Sora's scroll events to handle upward fling reaching top of editor
        try {
            editor.subscribeEvent(ScrollEvent::class.java) { _, _ ->
                if (editor.offsetY <= 0 && scrollD > 0 && !scroller.isFinished) {
                    // Handled during upward fling
                }
            }
        } catch (_: Throwable) {}
    }

    fun resetScroll() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        scrollD = 0
        isDraggingCanvas = false
        applyTranslations()
        onUnifiedScrollChanged?.invoke(0, headerHeight)
        try {
            editor.scrollTo(0, 0)
        } catch (_: Throwable) {}
    }

    private fun applyTranslations() {
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
        headerHeight = headerView.measuredHeight

        // Clamp scrollD in case header height changed (e.g. secondary title shown/hidden)
        if (scrollD > headerHeight) {
            scrollD = headerHeight
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

        applyTranslations()
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
            // Scrolling down into document
            if (scrollD < headerHeight) {
                val canConsume = (headerHeight - scrollD).toFloat()
                val consume = minOf(dy, canConsume)
                scrollD += consume.toInt()
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
            // Scrolling up towards top of document
            if (editor.offsetY > 0) {
                // Editor has scroll; let editor absorb first
                val currentOffset = editor.offsetY.toFloat()
                val canConsumeEditor = -currentOffset
                val consumeEditor = maxOf(dy, canConsumeEditor)
                dispatchScrollToEditor(consumeEditor)
                val unconsumed = dy - consumeEditor
                if (unconsumed < 0f && scrollD > 0) {
                    val canConsumeCanvas = -scrollD.toFloat()
                    val consumeCanvas = maxOf(unconsumed, canConsumeCanvas)
                    scrollD += consumeCanvas.toInt()
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return unconsumed - consumeCanvas
                }
                return unconsumed
            } else {
                // Editor is at top, pull header down
                if (scrollD > 0) {
                    val canConsume = -scrollD.toFloat()
                    val consume = maxOf(dy, canConsume)
                    scrollD += consume.toInt()
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    return dy - consume
                }
                return dy
            }
        }
        return 0f
    }

    private fun dispatchScrollToEditor(dy: Float) {
        try {
            editor.scrollBy(0, dy.toInt())
        } catch (_: Throwable) {
            try {
                val scroller = getEditorScroller()
                scroller?.startScroll(0, editor.offsetY, 0, dy.toInt(), 0)
                editor.postInvalidateOnAnimation()
            } catch (_: Throwable) {}
        }
    }

    private fun getEditorScroller(): OverScroller? {
        return try {
            val method = editor.javaClass.getMethod("getScroller")
            method.invoke(editor) as? OverScroller
        } catch (_: Throwable) {
            try {
                val field = editor.javaClass.getDeclaredField("scroller")
                field.isAccessible = true
                field.get(editor) as? OverScroller
            } catch (_: Throwable) {
                null
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDispatchY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastDispatchY - ev.y
                lastDispatchY = ev.y

                // If user is pulling DOWN inside editor and editor reaches the top (offsetY == 0),
                // smoothly transfer drag gesture to the UnifiedCanvasLayout to pull header down!
                if (dy < 0f && editor.offsetY <= 0 && scrollD > 0 && !isDraggingCanvas) {
                    isDraggingCanvas = true
                    lastTouchY = ev.y

                    // Cancel child touch
                    val cancelEvent = MotionEvent.obtain(ev).apply {
                        action = MotionEvent.ACTION_CANCEL
                    }
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()

                    parent?.requestDisallowInterceptTouchEvent(true)
                    return onTouchEvent(ev)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    isDraggingCanvas = true
                    lastTouchY = ev.y
                    initialDownY = ev.y
                    initialDownX = ev.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                isDraggingCanvas = false
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchY = ev.y
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val totalDx = ev.x - initialDownX
                val totalDy = initialDownY - ev.y

                if (!isDraggingCanvas) {
                    if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                        if (totalDy > 0f) {
                            // Dragging up (scrolling down)
                            if (scrollD < headerHeight) {
                                isDraggingCanvas = true
                                lastTouchY = ev.y
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                        } else {
                            // Dragging down (scrolling up)
                            if (editor.offsetY <= 0 && scrollD > 0) {
                                isDraggingCanvas = true
                                lastTouchY = ev.y
                                parent?.requestDisallowInterceptTouchEvent(true)
                                return true
                            }
                        }
                    }
                }
                return isDraggingCanvas
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
                    // vy > 0 means dragging finger down (content scrolls up, decreasing scrollD)
                    // vy < 0 means dragging finger up (content scrolls down, increasing scrollD)
                    val scrollerVy = -vy.toInt()
                    lastScrollerY = scrollD
                    scroller.fling(0, scrollD, 0, scrollerVy, 0, 0, 0, headerHeight, 0, 0)
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
            val unconsumed = scrollCanvasBy(dy)

            if (scrollD >= headerHeight && unconsumed > 0f) {
                // Header reached top limit during fling; pass remaining momentum to editor
                val remainingVelocity = scroller.currVelocity
                scroller.abortAnimation()
                if (remainingVelocity > minFlingVelocity) {
                    try {
                        val editorScroller = getEditorScroller()
                        editorScroller?.fling(
                            0, editor.offsetY,
                            0, remainingVelocity.toInt(),
                            0, 0,
                            0, Int.MAX_VALUE,
                            0, 0
                        )
                        editor.postInvalidateOnAnimation()
                    } catch (_: Throwable) {}
                }
            } else {
                postInvalidateOnAnimation()
            }
        }
    }
}
