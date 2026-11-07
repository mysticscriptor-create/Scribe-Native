package com.primaloptima.scribe.ui.editor.scroll

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs

/**
 * Controller for unified canvas touch input, manual drag gestures, and fling momentum.
 *
 * Encapsulates the OverScroller, VelocityTracker, touch-slop arbitration,
 * and vertical gesture interception rules that coordinate scrolling between
 * the outer canvas and the inner Sora editor.
 */
class ScribeScrollInputController(
    context: Context,
    private val host: Host
) {

    interface Host {
        val scrollD: Int
        val headerHeight: Int
        val editorOffsetY: Int
        val maxCanvasScroll: Int
        var isUserTouching: Boolean
        val isAnyHandleActive: Boolean
        val isMultiTouchActive: Boolean
        fun isTouchOnHandle(editorX: Float, editorY: Float): Boolean
        fun editorLeft(): Int
        fun editorIsHandleDragging(): Boolean
        fun scrollCanvasBy(dy: Float)
        fun postInvalidateOnAnimation()
        fun stopEditorFling()
        fun requestDisallowInterceptTouchEvent(disallow: Boolean)
        fun onTopEdgeEffectRelease()
        fun onTopEdgeEffectAbsorb(velocity: Int)
        val isTopEdgeEffectFinished: Boolean
        fun onUserTouchDown(touchY: Float)
    }

    val scroller: OverScroller = OverScroller(context)
    var lastScrollerY: Int = 0
    private var velocityTracker: VelocityTracker? = null

    val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    val minFlingVelocity: Int = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    val maxFlingVelocity: Int = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    var initialDownX: Float = 0f
    var initialDownY: Float = 0f
    var lastTouchX: Float = 0f
    var lastTouchY: Float = 0f

    var isDraggingCanvas: Boolean = false
    var isTouchOnHandleActive: Boolean = false
    var isMultiTouchLatched: Boolean = false

    /**
     * Handles touch dispatch movement tracking, handle drag detection, and drag consumption.
     * Returns true if the event was consumed by active canvas drag or completed drag fling,
     * or null if dispatch should proceed normally through super.dispatchTouchEvent.
     */
    fun dispatchTouchEvent(ev: MotionEvent): Boolean? {
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouchLatched = false
                host.isUserTouching = true
                host.onUserTouchDown(ev.y)
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - host.editorLeft()
                val editorY = ev.y - (host.headerHeight - host.scrollD)
                isTouchOnHandleActive = host.editorIsHandleDragging() || host.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouchLatched = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    isMultiTouchLatched = true
                }
                if (!isTouchOnHandleActive && host.editorIsHandleDragging()) {
                    isTouchOnHandleActive = true
                }
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                if (isDraggingCanvas) {
                    host.scrollCanvasBy(dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isMultiTouchLatched = false
                isTouchOnHandleActive = false
                host.isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !host.isTopEdgeEffectFinished) {
                    host.onTopEdgeEffectRelease()
                    host.postInvalidateOnAnimation()
                }
                val wasDragging = isDraggingCanvas
                if (wasDragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (abs(vy) > minFlingVelocity) {
                        val scrollerVy = -vy.toInt()
                        lastScrollerY = host.scrollD + host.editorOffsetY
                        val maxScroll = host.maxCanvasScroll
                        if (maxScroll > 0) {
                            scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                        }
                        host.postInvalidateOnAnimation()
                    }
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                if (wasDragging) {
                    return true
                }
            }
        }
        return null
    }

    /**
     * Intercepts vertical drag gestures when the outer canvas owns scrolling:
     * 1. If header is visible (scrollD < headerHeight), canvas intercepts all vertical dragging.
     * 2. If editor is at top (editorOffsetY <= 0) and user drags down, canvas intercepts.
     * 3. If maxCanvasScroll <= 0, canvas intercepts to prevent scrolling short documents.
     */
    fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (host.isAnyHandleActive || host.isMultiTouchActive || ev.pointerCount > 1) return false
        if (isDraggingCanvas) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouchLatched = false
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - host.editorLeft()
                val editorY = ev.y - (host.headerHeight - host.scrollD)
                isTouchOnHandleActive = host.editorIsHandleDragging() || host.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouchLatched = true
                isDraggingCanvas = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (host.isAnyHandleActive || host.isMultiTouchActive || ev.pointerCount > 1) return false
                velocityTracker?.addMovement(ev)
                val totalDx = ev.x - initialDownX
                val totalDy = initialDownY - ev.y
                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                    if (host.scrollD < host.headerHeight || host.editorOffsetY <= 0 || host.maxCanvasScroll <= 0) {
                        isDraggingCanvas = true
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        host.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingCanvas = false
                isTouchOnHandleActive = false
                isMultiTouchLatched = false
            }
        }
        return isDraggingCanvas
    }

    /**
     * Handles intercepted touch events directly on the canvas layout.
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.isUserTouching = true
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                host.stopEditorFling()
                lastTouchX = ev.x
                lastTouchY = ev.y
                initialDownY = ev.y
                initialDownX = ev.x
                isDraggingCanvas = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                host.scrollCanvasBy(dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                host.isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !host.isTopEdgeEffectFinished) {
                    host.onTopEdgeEffectRelease()
                    host.postInvalidateOnAnimation()
                }
                velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val vy = velocityTracker?.yVelocity ?: 0f
                if (abs(vy) > minFlingVelocity) {
                    val scrollerVy = -vy.toInt()
                    lastScrollerY = host.scrollD + host.editorOffsetY
                    val maxScroll = host.maxCanvasScroll
                    if (maxScroll > 0) {
                        scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                    }
                    host.postInvalidateOnAnimation()
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                host.isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !host.isTopEdgeEffectFinished) {
                    host.onTopEdgeEffectRelease()
                    host.postInvalidateOnAnimation()
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return false
    }

    /**
     * Advances scroller momentum and allocates deltas to the canvas.
     */
    fun computeScroll(): Boolean {
        if (scroller.computeScrollOffset()) {
            val currY = scroller.currY
            val dy = (currY - lastScrollerY).toFloat()
            lastScrollerY = currY
            host.scrollCanvasBy(dy)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if ((scroller.isOverScrolled() || currY <= 0) && host.scrollD <= 0 && host.editorOffsetY <= 0 && host.isTopEdgeEffectFinished) {
                    val velocity = scroller.currVelocity.toInt()
                    if (velocity > 0) {
                        host.onTopEdgeEffectAbsorb(velocity)
                    }
                }
            }
            host.postInvalidateOnAnimation()
            return true
        }
        return false
    }

    /**
     * Continues a downward fling from the editor into the canvas header.
     * Called when editor flings towards the top and offsetY hits 0.
     */
    fun continueFlingFromEditor(velocity: Float) {
        if (host.isUserTouching || isDraggingCanvas) return
        if (host.scrollD <= 0) return
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        val vy = -abs(velocity).toInt()
        lastScrollerY = host.scrollD
        scroller.fling(0, host.scrollD, 0, vy, 0, 0, 0, host.headerHeight, 0, 0)
        host.postInvalidateOnAnimation()
    }

    /**
     * Halts any active fling animation on the canvas scroller and editor.
     */
    fun settleOrCancelFling() {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        host.stopEditorFling()
    }

    /**
     * Stops any in-flight scroller momentum.
     */
    fun stopFling() {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
    }

    /**
     * Aborts scroller animation.
     */
    fun abortAnimation() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
    }

    /**
     * Starts a programmatic scroller animation.
     */
    fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        lastScrollerY = startY
        scroller.startScroll(startX, startY, dx, dy, duration)
        host.postInvalidateOnAnimation()
    }
}

/**
 * Editor-specific scroll input and fling calculation helpers.
 */
object ScribeEditorScrollInputHelper {

    /**
     * Calculates the edge-triggered selection auto-scroll velocity based on touch position,
     * header offset, visible bottom, and display density.
     */
    fun calculateHandleAutoScrollVelocity(
        touchY: Float,
        viewHeight: Int,
        headerOffset: Int,
        density: Float,
        canScrollUp: Boolean
    ): Float {
        val visibleBottom = viewHeight - headerOffset
        val hotZone = 40f * density
        return when {
            touchY > visibleBottom - hotZone -> {
                val ratio = ((touchY - (visibleBottom - hotZone)) / hotZone).coerceIn(0.1f, 2.0f)
                (ratio * 14f * density)
            }
            touchY < hotZone && canScrollUp -> {
                val ratio = ((hotZone - touchY) / hotZone).coerceIn(0.1f, 2.0f)
                -(ratio * 14f * density)
            }
            else -> 0f
        }
    }

    /**
     * Determines whether a downward fling reaching the top of the editor document (offsetY <= 0)
     * should transfer momentum to the canvas header.
     */
    fun shouldHandoffFlingToCanvas(
        wasFinished: Boolean,
        currY: Int,
        prevY: Int,
        velocity: Float,
        isFlingActive: Boolean,
        isUserTouching: Boolean
    ): Boolean {
        return !wasFinished && currY <= 0 && prevY > 0 && velocity > 0f && isFlingActive && !isUserTouching
    }
}
