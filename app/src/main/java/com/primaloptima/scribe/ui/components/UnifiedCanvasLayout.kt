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
import android.view.animation.PathInterpolator
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

internal val CodeEditor.safeScrollMaxY: Int
    get() = try {
        scrollMaxY.coerceAtLeast(0)
    } catch (_: Throwable) {
        0
    }

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

    var isKeyboardDismissing: Boolean = false

    var horizontalPaddingDp: Float = 28f
        set(value) {
            val clamped = value.coerceIn(8f, 72f)
            if (field != clamped) {
                field = clamped
                editor.horizontalPaddingDp = clamped
                requestLayout()
                invalidate()
            }
        }

    val breathingMarginPx: Float
        get() = 24f * resources.displayMetrics.density

    /**
     * Pure Writer persistent bottom space architecture:
     * Equal to where our smooth scroll is gliding, according to the shortcut bar height.
     * estimatedKeyboardHeightPx = (compactHeightDp + 36f) * density.
     * Plus breathing margin (24dp) so that the line rests comfortably above the shortcut bar.
     */
    val persistentBottomSpacePx: Float
        get() = estimatedKeyboardHeightPx + breathingMarginPx

    val maxCanvasScroll: Int
        get() {
            val layoutH = editor.layout?.layoutHeight ?: 0
            val breathingMargin = breathingMarginPx.roundToInt()
            // When keyboard is visible, viewport is already padded by Compose.
            // Therefore, bottomSpace is ONLY breathingMargin (24dp) so last line sits
            // comfortably above shortcut bar without double-counting keyboard height or scrolling empty space.
            val bottomSpace = if (isKeyboardVisible || isBottomSurfaceActive) {
                breathingMargin
            } else if (editor.isFocused || editor.offsetY > 0 || scrollD > 0) {
                persistentBottomSpacePx.roundToInt()
            } else {
                0
            }
            return ScribeScrollPolicy.calculateMaxCanvasScroll(
                headerHeight = headerHeight,
                layoutHeight = layoutH,
                viewportHeight = height,
                bottomSpace = bottomSpace,
                isTextEmpty = editor.text.isEmpty()
            )
        }

    val maxHeaderScrollD: Int
        get() = ScribeScrollPolicy.calculateMaxHeaderScroll(headerHeight, maxCanvasScroll)

    var estimatedKeyboardHeightPx: Float = (280f + 36f) * context.resources.displayMetrics.density
        set(value) {
            field = value
            editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
        }

    var isKeyboardVisible: Boolean = false
        set(value) {
            field = value
            editor.isKeyboardVisible = value
        }

    var isBottomSurfaceActive: Boolean = false

    init {
        addView(headerView)
        addView(editor)
        clipChildren = false
        clipToPadding = false
        editor.horizontalPaddingDp = horizontalPaddingDp
        outlineProvider = android.view.ViewOutlineProvider.BOUNDS
        clipToOutline = true
        editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
        editor.isKeyboardVisible = isKeyboardVisible

    }

    var headerHeight: Int = 0
        private set

    
    var scrollD: Int = 0
        private set

    private var scrollDFloat: Float = 0f

    var onScrollDelta: ((dy: Float) -> Unit)? = null
    var onUnifiedScrollChanged: ((scrollD: Int, maxHeaderHeight: Int) -> Unit)? = null
    var onUserTouchDown: (() -> Unit)? = null

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
    private var isTouchOnHandleActive = false

    val isAnyHandleActive: Boolean
        get() = editor.isHandleDragging || isTouchOnHandleActive

    private val topEdgeEffect: EdgeEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        EdgeEffectCompat.create(context, null)
    } else {
        null
    }

    var isUserTouching: Boolean = false
        private set

    var isPredictiveGlideRunning: Boolean = false
        private set
    var targetPredictedScrollTotal: Float = -1f
        private set

    /**
     * Set to true strictly during text deletion on the last line of the document.
     * Prevents downward anchoring scroll from triggering on taps, focus, or general edits.
     */
    var isLastLineDeletionActive: Boolean = false
        private set
    private var resetDeletionActiveRunnable: Runnable? = null

    fun notifyLastLineDeletionStarted() {
        isLastLineDeletionActive = true
        resetDeletionActiveRunnable?.let { removeCallbacks(it) }
        val r = Runnable {
            isLastLineDeletionActive = false
        }
        resetDeletionActiveRunnable = r
        postDelayed(r, 220L)
    }

    companion object {
        val FastOutSlowInInterpolator: android.view.animation.Interpolator =
            PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }

    private var canvasScrollAnimator: android.animation.ValueAnimator? = null

    fun smoothScrollCanvasBy(
        dy: Float,
        durationMs: Long = 280L,
        interpolator: android.view.animation.Interpolator = FastOutSlowInInterpolator,
        onEnd: (() -> Unit)? = null
    ) {
        if (kotlin.math.abs(dy) < 1f) {
            onEnd?.invoke()
            return
        }
        canvasScrollAnimator?.cancel()
        var lastAnimatedValue = 0f
        canvasScrollAnimator = android.animation.ValueAnimator.ofFloat(0f, dy).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { anim ->
                val curr = anim.animatedValue as Float
                val delta = curr - lastAnimatedValue
                lastAnimatedValue = curr
                scrollCanvasBy(delta)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    canvasScrollAnimator = null
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    canvasScrollAnimator = null
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                }
            })
            start()
        }
    }

    /**
     * Cancels any in-progress smooth scroll animators and scroller flings.
     */
    fun cancelCanvasAnimation() {
        canvasScrollAnimator?.cancel()
        canvasScrollAnimator = null
        isPredictiveGlideRunning = false
        targetPredictedScrollTotal = -1f
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
    }

    /**
     * Enforces that editor scroll and canvas scroll are naturally anchored after text deletion.
     * When document height shrinks (e.g. deleting lines, deleting selections, or reducing indent),
     * checks whether the bottom of the document text has drifted away from the shortcut bar,
     * and smoothly pulls the canvas down so that:
     * 1. The bottom of the document text stays comfortably anchored above the shortcut bar, OR
     * 2. The title and header are restored to the top if the remaining document fits in view.
     */
    fun anchorCanvasAfterDeletion(smooth: Boolean = true) {
        val deltaY = ScribeScrollPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = editor.layout?.layoutHeight ?: 0,
            offsetY = editor.offsetY,
            viewportHeight = height,
            density = resources.displayMetrics.density,
            maxCanvasScroll = maxCanvasScroll
        )
        if (deltaY < -1f) {
            if (smooth) {
                smoothScrollCanvasBy(
                    dy = deltaY,
                    durationMs = ScribeScrollPolicy.DURATION_DELETION_ANCHOR_MS,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                scrollCanvasBy(deltaY)
            }
        }
    }

    /**
     * Clamps excess canvas scroll back to physical content bounds.
     */
    fun clampCanvasScrollToMax(smooth: Boolean = true) {
        anchorCanvasAfterDeletion(smooth = smooth)
    }

    /**
     * Legacy adapter for backward compatibility.
     */
    fun clampEditorScrollToMax() {
        anchorCanvasAfterDeletion(smooth = false)
    }

    /**
     * Smoothly returns the document canvas to the top (scrollD = 0, offsetY = 0),
     * ensuring the header and title section are fully restored into view as one continuous document.
     */
    fun resetDocumentToTop(smooth: Boolean = true) {
        cancelCanvasAnimation()
        try {
            editor.scroller?.abortAnimation()
        } catch (_: Throwable) {}
        val totalScroll = scrollD + editor.offsetY
        if (totalScroll > 0) {
            if (smooth) {
                smoothScrollCanvasBy(-totalScroll.toFloat(), durationMs = ScribeScrollPolicy.DURATION_RESET_TOP_MS, interpolator = FastOutSlowInInterpolator)
            } else {
                scrollCanvasBy(-totalScroll.toFloat())
            }
        }
    }

    /**
     * Directly restores the canvas header scroll offset without animation.
     */
    fun setCanvasScrollD(targetScrollD: Int) {
        cancelCanvasAnimation()
        val clamped = targetScrollD.coerceIn(0, headerHeight)
        scrollD = clamped
        scrollDFloat = clamped.toFloat()
        applyTranslations()
        onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
        invalidate()
    }

    /**
     * Executes the given action once the UnifiedCanvasLayout has a valid measured height
     * and headerView has a non-zero measured height.
     */
    fun doOnCanvasReady(action: () -> Unit) {
        if (headerHeight > 0 && height > 0) {
            post { action() }
            return
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (headerHeight > 0 && (bottom - top) > 0) {
                    removeOnLayoutChangeListener(this)
                    post { action() }
                }
            }
        }
        addOnLayoutChangeListener(listener)
    }

    /**
     * Centers the given cursor line vertically within the viewport/canvas.
     * Clamps to top (header visible) or bottom (max scroll) naturally.
     */
    fun centerCursorInViewport(line: Int, column: Int) {
        doOnCanvasReady {
            val ed = editor as? ScribeCodeEditor
            ed?.doOnLayoutReady {
                val edLayout = editor.layout ?: return@doOnLayoutReady
                val layoutOffset = try { edLayout.getCharLayoutOffset(line, column) } catch (_: Throwable) { null } ?: return@doOnLayoutReady
                val lineDocY = (layoutOffset[0] - editor.rowHeight).toFloat()
                val cursorCanvasY = headerHeight + lineDocY
                val viewportHeight = height.toFloat()
                val targetTotalScroll = cursorCanvasY - (viewportHeight / 2f)
                if (targetTotalScroll <= 0f) {
                    restoreCanvasAndEditorScroll(0, 0)
                } else {
                    val targetScrollD = targetTotalScroll.toInt().coerceIn(0, headerHeight)
                    val remainingScroll = targetTotalScroll.toInt() - targetScrollD
                    val maxY = editor.safeScrollMaxY
                    val targetScrollY = remainingScroll.coerceIn(0, maxY)
                    restoreCanvasAndEditorScroll(targetScrollD, targetScrollY)
                }
            }
        }
    }

    /**
     * Synchronously restores both canvas header displacement (scrollD) and Sora editor vertical offset (offsetY).
     */
    fun restoreCanvasAndEditorScroll(targetScrollD: Int, targetScrollY: Int) {
        doOnCanvasReady {
            cancelCanvasAnimation()
            val clampedD = targetScrollD.coerceIn(0, headerHeight)
            scrollD = clampedD
            scrollDFloat = clampedD.toFloat()
            applyTranslations()
            if (targetScrollY >= 0) {
                try {
                    val maxY = editor.safeScrollMaxY
                    val clampedY = if (clampedD < headerHeight) 0 else targetScrollY.coerceIn(0, maxY)
                    val scribeEditor = editor as? ScribeCodeEditor
                    if (scribeEditor != null) {
                        scribeEditor.jumpScrollTo(editor.offsetX, clampedY)
                    } else {
                        val scroller = editor.scroller
                        if (scroller != null) {
                            scroller.forceFinished(true)
                            val currY = scroller.currY
                            val currX = scroller.currX
                            val dy = clampedY - currY
                            if (dy != 0) {
                                scroller.startScroll(currX, currY, 0, dy, 0)
                                scroller.abortAnimation()
                            }
                            try {
                                editor.renderContext.invalidateRenderNodes()
                            } catch (_: Throwable) {}
                            editor.invalidate()
                        }
                    }
                } catch (_: Throwable) {}
            }
            applyTranslations()
            onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            invalidate()
        }
    }

    fun resetScroll() {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        editor.stopFling()
        scrollDFloat = 0f
        scrollD = 0
        isDraggingCanvas = false
        applyTranslations()
        onUnifiedScrollChanged?.invoke(0, headerHeight)
        try {
            editor.jumpScrollTo(editor.offsetX, 0)
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
            scroller.forceFinished(true)
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
    /**
     * Phase 1: Tap-Triggered Predictive Pre-Calculation Engine.
     * Computes the cursor position at t = 0ms (on tap), estimates future viewport
     * height using cached lastKeyboardHeightDp + shortcutBarHeight, and immediately starts
     * a smooth parallel glide running concurrently with the keyboard entrance animation.
     */
    fun predictivelyGlideCursorAboveKeyboard(targetLine: Int? = null, targetCol: Int? = null) {
        if (isKeyboardDismissing) return
        val density = resources.displayMetrics.density
        val cursor = try { editor.cursor } catch (_: Throwable) { null }
        val line = targetLine ?: cursor?.leftLine ?: return
        val col = targetCol ?: cursor?.leftColumn ?: 0

        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val layoutOffset = try { layout.getCharLayoutOffset(line, col) } catch (_: Throwable) { null } ?: return
        val yOffset = layoutOffset[0] // Doc coordinate of line bottom

        val headerRemaining = headerHeight - scrollD
        val screenBottom = (headerRemaining + yOffset - editor.offsetY).toFloat()

        val isKeyboardOrDrawerActive = isKeyboardVisible || (height <= resources.displayMetrics.heightPixels * 0.65f)
        val futureViewportHeight = if (isKeyboardOrDrawerActive) {
            height.toFloat()
        } else {
            (height - estimatedKeyboardHeightPx).coerceAtLeast(editor.rowHeight * 3f)
        }

        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
        val targetLineBottom = futureViewportHeight - marginPx

        if (screenBottom > (targetLineBottom + deadbandPx)) {
            var deltaY = screenBottom - targetLineBottom

            // Pure Writer Persistent Bottom Space:
            // Document bounds include generous bottom clearance so that lines near the end
            // of the document can glide fluidly above the shortcut bar without restriction.
            val bottomClearance = maxOf(marginPx.toFloat(), futureViewportHeight * 0.45f)
            val totalContentHeight = (headerHeight + layout.layoutHeight + bottomClearance)
            val maxScrollFuture = (totalContentHeight - futureViewportHeight).coerceAtLeast(0f)
            val currentScroll = (scrollD + editor.offsetY).toFloat()
            val maxAllowedDelta = (maxScrollFuture - currentScroll).coerceAtLeast(0f)
            deltaY = minOf(deltaY, maxAllowedDelta)

            if (deltaY > ScribeScrollPolicy.DRAG_THRESHOLD_DP) {
                isPredictiveGlideRunning = true
                targetPredictedScrollTotal = currentScroll + deltaY
                smoothScrollCanvasBy(
                    dy = deltaY,
                    durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS,
                    interpolator = FastOutSlowInInterpolator
                ) {
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                }
            }
        }
    }

    private var lastEnsureCursorTime = 0L

    fun ensureCursorVisibleAboveKeyboard(
        targetLine: Int? = null,
        targetCol: Int? = null,
        smooth: Boolean = true,
        customDurationMs: Long = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS,
        force: Boolean = false
    ) {
        if (!editor.isFocused || isKeyboardDismissing || isPredictiveGlideRunning) return
        val now = System.currentTimeMillis()
        if (!force && now - lastEnsureCursorTime < 60L) return
        val cursor = try { editor.cursor } catch (_: Throwable) { null } ?: return

        val resolutionTarget = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = cursor.isSelected,
            leftLine = cursor.leftLine,
            leftCol = cursor.leftColumn,
            rightLine = cursor.rightLine,
            rightCol = cursor.rightColumn,
            lineCount = editor.text.lineCount,
            lastLineColCount = try { editor.text.getColumnCount(editor.text.lineCount - 1) } catch (_: Throwable) { 0 },
            mode = editor.selectAllScrollMode,
            explicitLine = targetLine,
            explicitCol = targetCol
        ) ?: return // null means stationary mode: suppress scroll completely!

        val line = resolutionTarget.line
        val col = resolutionTarget.column

        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val rowHeight = editor.rowHeight
        val layoutOffset = try { layout.getCharLayoutOffset(line, col) } catch (_: Throwable) { null }
        val yOffset = if (layoutOffset != null) {
            layoutOffset[0]
        } else {
            // Find closest preceding line with a valid layout offset to accurately project coordinates
            var anchorY = -1f
            var lineGap = 1
            for (k in (line - 1) downTo 0) {
                val colK = try { editor.text.getColumnCount(k) } catch (_: Throwable) { 0 }
                val offK = try { layout.getCharLayoutOffset(k, colK) } catch (_: Throwable) { null }
                if (offK != null) {
                    anchorY = offK[0]
                    lineGap = line - k
                    break
                }
            }
            if (anchorY >= 0f) {
                anchorY + (lineGap * rowHeight)
            } else {
                (line + 1) * rowHeight.toFloat()
            }
        }
        val headerRemaining = headerHeight - scrollD
        val screenBottom = (headerRemaining + yOffset - editor.offsetY).toFloat()
        val screenTop = screenBottom - rowHeight

        val visibleHeight = height
        val density = resources.displayMetrics.density
        val isLastLine = line >= (editor.text.lineCount - 1)
        val hasScroll = scrollD > 0 || editor.offsetY > 0

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = isLastLine,
            isDeletionActive = isLastLineDeletionActive,
            hasScrollOffset = hasScroll
        )

        if (resolution.shouldScroll) {
            lastEnsureCursorTime = now
            val duration = if (resolution.isAnchorDownward) {
                ScribeScrollPolicy.DURATION_DELETION_ANCHOR_MS
            } else {
                customDurationMs
            }
            if (smooth) {
                smoothScrollCanvasBy(resolution.deltaY, durationMs = duration, interpolator = FastOutSlowInInterpolator)
            } else {
                scrollCanvasBy(resolution.deltaY)
            }
        }
    }

    internal fun applyTranslations() {
        // Enforce the 1-DOF invariant:
        // If the header is partially or fully visible (scrollD < headerHeight),
        // the editor MUST NOT have any positive offsetY. Clamping to 0 guarantees
        // Line 0 is never drawn behind the titles.
        if (scrollD < headerHeight && editor.offsetY > 0) {
            try {
                editor.jumpScrollTo(editor.offsetX, 0)
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
        if (headerHeight > 0) {
            val reconciled = ScribeScrollPolicy.reconcileScrollState(
                headerHeight = headerHeight,
                scrollD = scrollD,
                editorOffsetY = editor.offsetY,
                maxCanvasScroll = maxCanvasScroll
            )
            scrollD = reconciled.scrollD
            scrollDFloat = reconciled.scrollD.toFloat()
        }

        // Measure CodeEditor to fill the full viewport width and height
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

        // Only enforce viewport height invariant when layout & wordwrap are fully ready
        val scribeEditor = editor as? ScribeCodeEditor
        val isReady = scribeEditor?.isWordwrapReady() ?: true
        val layoutH = editor.layout?.layoutHeight ?: 0
        if (editor.text.isEmpty()) {
            if (editor.offsetY > 0) {
                try {
                    editor.jumpScrollTo(editor.offsetX, 0)
                } catch (_: Throwable) {}
            }
            if (scrollD > 0) {
                scrollD = 0
                scrollDFloat = 0f
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
        } else {
            val reconciled = ScribeScrollPolicy.reconcileScrollState(
                headerHeight = headerHeight,
                scrollD = scrollD,
                editorOffsetY = editor.offsetY,
                maxCanvasScroll = maxCanvasScroll
            )
            if (reconciled.scrollD != scrollD) {
                scrollD = reconciled.scrollD
                scrollDFloat = reconciled.scrollD.toFloat()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
            if (reconciled.editorOffsetY != editor.offsetY) {
                try {
                    editor.jumpScrollTo(editor.offsetX, reconciled.editorOffsetY)
                } catch (_: Throwable) {}
            }
        }
        applyTranslations()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topEdgeEffect?.setSize(w, h)
        if (oldh > 0 && kotlin.math.abs(h - oldh) > 2) {
            val dh = oldh - h
            if (dh > 0) {
                // Viewport shrinking (keyboard appearing)
                if (editor.isFocused) {
                    val density = resources.displayMetrics.density
                    val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
                    val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()

                    val isCursorInSafeZone = try {
                        val cur = editor.cursor
                        val lay = editor.layout
                        if (cur != null && lay != null) {
                            val isAllSelected = ScribeScrollPolicy.isSelectAll(
                                isSelected = cur.isSelected,
                                leftLine = cur.leftLine,
                                leftCol = cur.leftColumn,
                                rightLine = cur.rightLine,
                                rightCol = cur.rightColumn,
                                lineCount = editor.text.lineCount,
                                lastLineColCount = try { editor.text.getColumnCount(editor.text.lineCount - 1) } catch (_: Throwable) { 0 }
                            )
                            if (isAllSelected && editor.selectAllScrollMode == ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION) {
                                true
                            } else {
                                val targetLine = if (isAllSelected) cur.rightLine else cur.leftLine
                                val targetCol = if (isAllSelected) cur.rightColumn else cur.leftColumn
                                val off = lay.getCharLayoutOffset(targetLine, targetCol)
                                val headerRemaining = headerHeight - scrollD
                                val screenBottom = headerRemaining + off[0] - editor.offsetY
                                screenBottom <= (h - marginPx + deadbandPx)
                            }
                        } else false
                    } catch (_: Throwable) {
                        false
                    }

                    // Phase 2: Elimination of Post-Appearance Double-Jumps
                    // If an active predictive glide is underway OR cursor is already in safe zone,
                    // suppress the secondary post { ensureCursorVisibleAboveKeyboard() }.
                    if (!isPredictiveGlideRunning && !isCursorInSafeZone) {
                        post {
                            if (!isPredictiveGlideRunning) {
                                ensureCursorVisibleAboveKeyboard(smooth = true, customDurationMs = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS)
                            }
                        }
                    }
                }
            } else if (dh < 0) {
                // Viewport expanding (accessory drawer contracting, emoji keyboard closing, or bottom surface lowering)
                val density = resources.displayMetrics.density
                val rootH = rootView?.height ?: resources.displayMetrics.heightPixels
                val hasBottomObstruction = (rootH - h) >= (140 * density).roundToInt()

                val cur = try { editor.cursor } catch (_: Throwable) { null }
                val lineCount = try { editor.text.lineCount } catch (_: Throwable) { 1 }
                val isAtLastLine = try {
                    (cur != null && cur.leftLine >= (lineCount - 1)) || (lineCount <= 1)
                } catch (_: Throwable) { false }
                val hasActiveScroll = scrollD > 0 || editor.offsetY > 0

                val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
                    isKeyboardDismissing = isKeyboardDismissing,
                    isBottomSurfaceActive = isBottomSurfaceActive,
                    isKeyboardVisible = isKeyboardVisible,
                    hasPhysicalBottomObstruction = hasBottomObstruction,
                    isAtLastLine = isAtLastLine,
                    hasActiveScroll = hasActiveScroll
                )

                // Pure Writer Invariant:
                // When keyboard is dismissed or closing, the document MUST remain
                // completely stationary at the saved margin (keyboard height + shortcut bar + breathing margin).
                // Never pull down to the bottom of the screen.
                if (!shouldFollow) {
                    return
                }

                // Downward Following Invariant:
                // Only when bottom surface remains active (e.g. accessory drawer collapsed to keyboard,
                // or taller emoji keyboard returned to standard keyboard), smoothly follow the
                // shortcut bar downward maintaining the exact 24dp breathing margin.
                val lastLine = (lineCount - 1).coerceAtLeast(0)
                val lastCol = try { editor.text.getColumnCount(lastLine) } catch (_: Throwable) { 0 }
                val lay = editor.layout
                val off = try { lay?.getCharLayoutOffset(lastLine, lastCol) } catch (_: Throwable) { null }
                val lastLineDocBottom = off?.get(0)
                    ?: (lay?.layoutHeight?.toFloat() ?: ((lastLine + 1) * editor.rowHeight.toFloat()))

                val deltaY = ScribeScrollPolicy.calculateLastLineFollowDelta(
                    lastLineDocBottom = lastLineDocBottom,
                    headerHeight = headerHeight,
                    scrollD = scrollD,
                    offsetY = editor.offsetY,
                    viewportHeight = h,
                    density = density,
                    isAtLastLine = true
                )

                if (deltaY < -1f) {
                    post {
                        smoothScrollCanvasBy(
                            dy = deltaY,
                            durationMs = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS,
                            interpolator = FastOutSlowInInterpolator
                        )
                    }
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
        if (isKeyboardDismissing) {
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

        if (child === editor) {
            val screenTop = (headerHeight - scrollD) + rectangle.top
            val screenBottom = (headerHeight - scrollD) + rectangle.bottom
            val density = resources.displayMetrics.density
            val visibleHeight = height
            val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
            val targetLineBottom = visibleHeight - marginPx
            if (screenBottom > (targetLineBottom + deadbandPx)) {
                val delta = (screenBottom - targetLineBottom).toFloat()
                scrollCanvasBy(delta)
                return true
            } else if (screenTop < -deadbandPx && (scrollD > 0 || editor.offsetY > 0)) {
                val delta = screenTop.toFloat()
                scrollCanvasBy(delta)
                return true
            }
        }
        return false
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (isAnyHandleActive) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
            return
        }
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
        }

        // 2. Authoritative 1-DOF scroll allocation via ScribeScrollPolicy
        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = dy,
            headerHeight = headerHeight,
            scrollD = scrollDFloat,
            editorOffsetY = editor.offsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        if (allocation.outerDelta != 0f) {
            scrollDFloat += allocation.outerDelta
            val maxD = minOf(headerHeight, maxCanvasScroll)
            scrollD = scrollDFloat.roundToInt().coerceIn(0, maxD)
            applyTranslations()
            onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
        }

        if (allocation.innerDelta != 0f) {
            dispatchScrollToEditor(allocation.innerDelta)
        }

        // 3. Top edge stretch when overscrolling at the top
        if (dy < 0f && allocation.unconsumedDelta < 0f && scrollD <= 0 && editor.offsetY <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                val deltaDistance = -allocation.unconsumedDelta / height.coerceAtLeast(1)
                val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                postInvalidateOnAnimation()
            }
        }

        return allocation.unconsumedDelta
    }

    /**
     * Natively scrolls Sora CodeEditor via its internal EditorScroller.
     * This avoids calling View.scrollBy() which desynchronizes the View hardware canvas,
     * ensuring lines render smoothly at 120Hz without blank screens during fast scrolling.
     */
    private fun dispatchScrollToEditor(dy: Float) {
        // Invariant A: Editor MUST NOT scroll if header is visible
        if (scrollD < headerHeight) return
        try {
            val scroller = editor.scroller ?: return
            val currY = editor.offsetY
            val currX = editor.offsetX
            val targetY = (currY + dy).roundToInt()
            val maxAllowedY = editor.safeScrollMaxY
            val clampedTargetY = targetY.coerceIn(0, maxAllowedY)
            if (clampedTargetY != currY) {
                scroller.forceFinished(true)
                scroller.startScroll(currX, currY, 0, clampedTargetY - currY, 0)
                scroller.abortAnimation()
                try {
                    editor.renderContext.invalidateRenderNodes()
                } catch (_: Throwable) {}
                editor.invalidate()
            }
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                onUserTouchDown?.invoke()
                cancelCanvasAnimation()
                editor.stopFling()
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - editor.left
                val editorY = ev.y - (headerHeight - scrollD)
                isTouchOnHandleActive = editor.isHandleDragging || editor.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTouchOnHandleActive && editor.isHandleDragging) {
                    isTouchOnHandleActive = true
                }
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y

                if (isDraggingCanvas) {
                    scrollCanvasBy(dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchOnHandleActive = false
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
                val wasDragging = isDraggingCanvas
                if (wasDragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (abs(vy) > minFlingVelocity) {
                        val scrollerVy = -vy.toInt()
                        lastScrollerY = scrollD + editor.offsetY
                        val maxScroll = maxCanvasScroll
                        if (maxScroll > 0) {
                            scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                        }
                        postInvalidateOnAnimation()
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
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isAnyHandleActive) return false
        if (isDraggingCanvas) return true

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - editor.left
                val editorY = ev.y - (headerHeight - scrollD)
                isTouchOnHandleActive = editor.isHandleDragging || editor.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAnyHandleActive) return false
                velocityTracker?.addMovement(ev)
                val totalDx = ev.x - initialDownX
                val totalDy = initialDownY - ev.y

                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                    // Central touch interception invariant:
                    // 1. If header is visible (scrollD < headerHeight), UnifiedCanvasLayout MUST intercept
                    //    all vertical dragging because the outer canvas owns all movement before handoff.
                    // 2. If editor is at top (editor.offsetY <= 0) and user drags down (totalDy < 0),
                    //    UnifiedCanvasLayout MUST intercept to pull header back into view.
                    // 3. If maxCanvasScroll <= 0, UnifiedCanvasLayout intercepts so editor cannot scroll short documents.
                    if (scrollD < headerHeight || editor.offsetY <= 0 || maxCanvasScroll <= 0) {
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
                isTouchOnHandleActive = false
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
                    scroller.forceFinished(true)
                }
                editor.stopFling()
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
                    val maxScroll = maxCanvasScroll
                    if (maxScroll > 0) {
                        scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                    }
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelCanvasAnimation()
        resetDeletionActiveRunnable?.let { removeCallbacks(it) }
        resetDeletionActiveRunnable = null
    }
}
