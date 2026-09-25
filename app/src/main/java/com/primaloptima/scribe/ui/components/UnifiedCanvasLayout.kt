package com.primaloptima.scribe.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.EdgeEffectCompat
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * UnifiedCanvasLayout — Edge-to-Edge Document Canvas Architecture.
 *
 * Hosts a ComposeView header (Child 0) and a Sora CodeEditor (Child 1) inside a single,
 * continuous vertical document canvas. Coordinates touch dispatch, flings across the
 * boundary, keyboard inset management, and 120Hz smooth rendering without touch hijacking.
 *
 * RIGID 1-DEGREE-OF-FREEDOM INVARIANT:
 * The title section and Line 0 of the document are physically locked together.
 * - When scrollD < headerHeight:
 *     editor.offsetY is strictly clamped to 0.
 *     headerView.translationY = -scrollD
 *     editor.translationY = -scrollD
 *     Bottom of Header = headerHeight - scrollD
 *     Top of Line 0 = headerHeight - scrollD - 0 = headerHeight - scrollD.
 *     They are identical down to the sub-pixel on every single frame.
 * - When scrollD == headerHeight:
 *     The header is completely scrolled off (translationY = -headerHeight).
 *     editor.translationY = -headerHeight (editor top at 0).
 *     editor.offsetY handles subsequent text scrolling.
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

    var isImeClosing: Boolean = false
        private set

    init {
        addView(headerView)
        addView(editor)
        clipChildren = true
        clipToPadding = true
        outlineProvider = android.view.ViewOutlineProvider.BOUNDS
        clipToOutline = true

        ViewCompat.setWindowInsetsAnimationCallback(
            this,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        val rootInsets = ViewCompat.getRootWindowInsets(this@UnifiedCanvasLayout)
                        val wasVisible = rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
                        if (wasVisible) {
                            isImeClosing = true
                        }
                    }
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val hasImeAnim = runningAnimations.any {
                        (it.typeMask and WindowInsetsCompat.Type.ime()) != 0
                    }
                    if (hasImeAnim) {
                        val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                        isImeClosing = !isImeVisible
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        isImeClosing = false
                    }
                }
            }
        )
    }

    var headerHeight: Int = 0
        private set

    var horizontalPaddingDp: Float = 28f
        set(value) {
            val clamped = value.coerceIn(8f, 72f)
            if (field != clamped) {
                field = clamped
                requestLayout()
                invalidate()
            }
        }

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
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDraggingCanvas = false

    private val topEdgeEffect: EdgeEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        EdgeEffectCompat.create(context, null)
    } else {
        null
    }

    var isUserTouching: Boolean = false
        private set

    private var canvasScrollAnimator: android.animation.ValueAnimator? = null
    private var keyboardDisplacement = 0f

    fun smoothScrollCanvasBy(dy: Float, durationMs: Long = 250, onEnd: (() -> Unit)? = null) {
        if (kotlin.math.abs(dy) < 1f) {
            onEnd?.invoke()
            return
        }
        canvasScrollAnimator?.cancel()
        var lastAnimatedValue = 0f
        canvasScrollAnimator = android.animation.ValueAnimator.ofFloat(0f, dy).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                val curr = anim.animatedValue as Float
                val delta = curr - lastAnimatedValue
                lastAnimatedValue = curr
                scrollCanvasBy(delta)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    canvasScrollAnimator = null
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canvasScrollAnimator = null
                }
            })
            start()
        }
    }

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
        if (isUserTouching || isDraggingCanvas) return
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
        if (!editor.isFocused || isImeClosing) return
        val cursor = try { editor.cursor } catch (_: Throwable) { null } ?: return
        val line = cursor.leftLine
        val col = cursor.leftColumn

        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val layoutOffset = try { layout.getCharLayoutOffset(line, col) } catch (_: Throwable) { null } ?: return
        val yOffset = layoutOffset[0] // doc coordinate of line bottom
        val rowHeight = editor.rowHeight
        val headerRemaining = headerHeight - scrollD
        val screenBottom = headerRemaining + yOffset - editor.offsetY
        val screenTop = screenBottom - rowHeight

        val visibleHeight = height
        val marginPx = (4 * resources.displayMetrics.density).roundToInt()

        if (visibleHeight > 0) {
            if (screenBottom > visibleHeight - marginPx) {
                val overflow = screenBottom - (visibleHeight - marginPx)
                smoothScrollCanvasBy(overflow.toFloat(), durationMs = 150)
            } else if (screenTop < 0 && (scrollD > 0 || editor.offsetY > 0)) {
                val underflow = screenTop
                smoothScrollCanvasBy(underflow.toFloat(), durationMs = 150)
            }
        }
    }

    private fun applyTranslations() {
        // Enforce the 1-DOF invariant:
        // If the header is partially or fully visible (scrollD < headerHeight),
        // the editor MUST NOT have any positive offsetY. Clamping to 0 guarantees
        // Line 0 is never drawn behind the titles.
        if (scrollD < headerHeight && editor.offsetY > 0) {
            try {
                editor.scroller?.let { s ->
                    s.startScroll(s.currX, 0, 0, 0, 0)
                    s.abortAnimation()
                }
            } catch (_: Throwable) {}
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
        if (editor.offsetY > 0) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        } else if (scrollD > headerHeight) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        }

        val padPx = (horizontalPaddingDp * context.resources.displayMetrics.density).roundToInt()
        val contentWidth = (width - 2 * padPx).coerceAtLeast(0)

        // Measure CodeEditor with symmetric margins to fill the viewport height
        editor.measure(
            MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(width, viewportHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val viewportHeight = b - t
        val padPx = (horizontalPaddingDp * context.resources.displayMetrics.density).roundToInt()
        val contentWidth = (width - 2 * padPx).coerceAtLeast(0)

        headerView.layout(0, 0, width, headerHeight)
        editor.layout(padPx, headerHeight, padPx + contentWidth, headerHeight + viewportHeight)

        val totalContentHeight = headerHeight + (editor.layout?.layoutHeight ?: 0)
        if (totalContentHeight <= viewportHeight) {
            if (editor.offsetY > 0) {
                try {
                    editor.scroller?.let { s ->
                        s.startScroll(s.currX, 0, 0, 0, 0)
                        s.abortAnimation()
                    }
                } catch (_: Throwable) {}
            }
            if (scrollD > 0) {
                scrollD = 0
                scrollDFloat = 0f
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
        } else if (editor.offsetY > 0) {
            scrollD = headerHeight
            scrollDFloat = headerHeight.toFloat()
        }
        applyTranslations()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topEdgeEffect?.setSize(w, h)
        if (oldh > 0 && h != oldh) {
            val dh = oldh - h
            if (dh > 0) {
                // Viewport shrinking (keyboard appearing)
                if (editor.isFocused) {
                    val cursor = try { editor.cursor } catch (_: Throwable) { null }
                    if (cursor != null) {
                        val layout = try { editor.layout } catch (_: Throwable) { null }
                        val offset = layout?.getCharLayoutOffset(cursor.leftLine, cursor.leftColumn)
                        if (offset != null) {
                            val yOffset = offset[0]
                            val headerRemaining = headerHeight - scrollD
                            val cursorBottom = headerRemaining + yOffset - editor.offsetY
                            val marginPx = (16 * resources.displayMetrics.density).roundToInt()
                            val overflow = cursorBottom - (h - marginPx)
                            if (overflow > 0) {
                                val scrollStep = minOf(overflow.toFloat(), dh.toFloat())
                                keyboardDisplacement += scrollStep
                                scrollCanvasBy(scrollStep)
                            }
                        }
                    }
                }
            } else if (dh < 0) {
                // Viewport expanding (keyboard dismissing)
                val expandAmount = (-dh).toFloat()
                if (keyboardDisplacement > 0f) {
                    val returnStep = minOf(keyboardDisplacement, expandAmount)
                    keyboardDisplacement -= returnStep
                    scrollCanvasBy(-returnStep)
                }
                // When viewport expands, ensure editor.offsetY is immediately clamped to editor.scrollMaxY:
                if (editor.offsetY > editor.scrollMaxY && editor.scrollMaxY >= 0) {
                    val excess = (editor.offsetY - editor.scrollMaxY).toFloat()
                    scrollCanvasBy(-excess)
                }
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
            if (topEdgeEffect.draw(canvas)) {
                postInvalidateOnAnimation()
            }
        }
        canvas.restoreToCount(save)
    }

    /**
     * Intercepts child requests to keep rectangles (like cursor) visible.
     * Offsets the requested rectangle by the editor's visual position, scrolling scrollD
     * so that the typing cursor is strictly kept above the keyboard and shortcut bar.
     */
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        if (isImeClosing) {
            return true
        }
        if (child !== editor) {
            if (scrollD > 0) {
                if (immediate) {
                    scrollDFloat = 0f
                    scrollD = 0
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(0, headerHeight)
                } else {
                    if (!scroller.isFinished) {
                        scroller.abortAnimation()
                    }
                    lastScrollerY = scrollD
                    scroller.startScroll(0, scrollD, 0, -scrollD, 250)
                    postInvalidateOnAnimation()
                }
            }
            return true
        }

        if (child === editor && !editor.isFocused) {
            return false
        }

        if (child === editor) {
            val screenTop = (headerHeight - scrollD) + rectangle.top
            val screenBottom = (headerHeight - scrollD) + rectangle.bottom
            val visibleHeight = height
            if (screenBottom > visibleHeight) {
                val delta = (screenBottom - visibleHeight).toFloat()
                scrollCanvasBy(delta)
                return true
            } else if (screenTop < 0 && (scrollD > 0 || editor.offsetY > 0)) {
                val delta = screenTop.toFloat()
                scrollCanvasBy(delta)
                return true
            }
        }
        return false
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && (scrollD < headerHeight || editor.offsetY <= 0)) {
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
            // 1. If top edge stretch effect is active, absorb upward movement into releasing the stretch first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                val dist = EdgeEffectCompat.getDistance(topEdgeEffect)
                if (dist > 0f) {
                    val deltaDistance = -dy / height.coerceAtLeast(1)
                    val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                    postInvalidateOnAnimation()
                    if (EdgeEffectCompat.getDistance(topEdgeEffect) > 0f) {
                        return dy
                    }
                }
            }

            // 2. Consume delta into scrollD until header is fully off screen
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
                    val unconsumed = dy - consumeCanvas
                    if (unconsumed < 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                        val deltaDistance = -unconsumed / height.coerceAtLeast(1)
                        val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                        postInvalidateOnAnimation()
                    }
                    return unconsumed
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                        val deltaDistance = -dy / height.coerceAtLeast(1)
                        val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                        postInvalidateOnAnimation()
                    }
                    return dy
                }
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
                isUserTouching = true
                canvasScrollAnimator?.cancel()
                keyboardDisplacement = 0f
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                try {
                    editor.scroller?.abortAnimation()
                } catch (_: Throwable) {}
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y

                if (isDraggingCanvas) {
                    scrollCanvasBy(dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
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
                lastTouchX = ev.x
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
                    if (scrollD < headerHeight) {
                        isDraggingCanvas = true
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else if (editor.offsetY <= 0 && (totalDy < 0f || (lastTouchY - ev.y) < 0f)) {
                        isDraggingCanvas = true
                        lastTouchX = ev.x
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
                isUserTouching = true
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                try {
                    editor.scroller?.abortAnimation()
                } catch (_: Throwable) {}
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
                scrollCanvasBy(dy)
                return true
            }

            MotionEvent.ACTION_UP -> {
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
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
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                if ((scroller.isOverScrolled() || currY <= 0) && scrollD <= 0 && editor.offsetY <= 0 && topEdgeEffect.isFinished) {
                    val velocity = scroller.currVelocity.toInt()
                    if (velocity > 0) {
                        topEdgeEffect.onAbsorb(velocity)
                    }
                }
            }
            postInvalidateOnAnimation()
        }
    }
}
