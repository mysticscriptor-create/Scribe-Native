package com.primaloptima.scribe.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.InputType
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.MotionEvent
import kotlin.math.roundToInt
import android.view.inputmethod.InputConnection
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.layout.WordwrapLayout
import kotlin.math.abs

/**
 * ScribeCodeEditor — Custom Sora CodeEditor integration for UnifiedCanvasLayout.
 *
 * 1. Continues downward fling smoothly into the canvas header when reaching offset Y <= 0.
 * 2. Overrides ensurePositionVisible to track keyboard and shortcut bar height perfectly,
 *    keeping the active cursor line just above the shortcut bar rather than jumping 2 lines above.
 * 3. Configures prose writing input options (auto-capitalization after newlines and sentences).
 * 4. Focus-aware keyboard resize adjustments ensuring cursor visibility only when document is focused.
 * 5. Wordwrap flash-suppression and calculation-ready fade-in animation:
 *    Suppresses drawing single-line fallback text during background wrap computation and
 *    smoothly fades in the formatted document the exact millisecond layout calculations complete.
 */
class ScribeCodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CodeEditor(context, attrs, defStyleAttr) {

    private var lastMakeVisibleTime: Long = 0L
    private var isFlingActive = false
    private var forceNextLayoutClear = false

    /**
     * Updates the editor's text typeface and base font weight.
     * Clears the wordwrap table cache to ensure new glyph advances and metrics
     * are re-measured, updates the language tokenizer with the new base weight,
     * reruns analysis, and triggers a full redraw.
     */
    fun updateTypefaceAndWeight(newTypeface: android.graphics.Typeface, weight: Int) {
        this.typefaceText = newTypeface
        setEditorLanguage(com.primaloptima.scribe.util.ScribeProseLanguage(weight))
        forceNextLayoutClear = true
        renderContext.invalidateRenderNodes()
        createLayout()
        rerunAnalysis()
        invalidate()
    }

    /**
     * Updates line spacing and guarantees immediate view invalidation,
     * resetting cached hardware-accelerated RenderNodes and redrawing the document.
     */
    override fun setLineSpacing(add: Float, mult: Float) {
        super.setLineSpacing(add, mult)
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
    }

    // ── Wordwrap Layout Ready & Flash-Suppression Engine ───────────────────────
    private var isLayoutBusyState: Boolean = false
    private var isAwaitingLayoutReady: Boolean = false
    var onLayoutReadyListener: (() -> Unit)? = null

    private val layoutBusyField = try {
        CodeEditor::class.java.getDeclaredField("layoutBusy").apply {
            isAccessible = true
        }
    } catch (_: Throwable) { null }

    private val rowTableField = try {
        WordwrapLayout::class.java.getDeclaredField("rowTable").apply {
            isAccessible = true
        }
    } catch (_: Throwable) { null }

    val isLayoutComputing: Boolean
        get() = (layoutBusyField?.getBoolean(this) ?: isLayoutBusyState)

    /**
     * Checks whether the wordwrap layout calculation is genuinely complete and ready to render.
     * Returns true when:
     * - Wordwrap is disabled (LineBreakLayout)
     * - Or when measured width > 0, background wordwrap analysis is NOT busy,
     *   and rowTable is populated (or text is empty).
     */
    fun isWordwrapReady(): Boolean {
        if (width <= 0) return false
        val curLayout = layout ?: return false
        if (!isWordwrap) return true
        if (curLayout is WordwrapLayout) {
            if (isLayoutComputing) return false
            val table = try { rowTableField?.get(curLayout) as? List<*> } catch (_: Throwable) { null }
            if (table != null && text.length > 0 && table.isEmpty()) {
                return false
            }
            return true
        }
        return true
    }

    private val safetyFadeInRunnable = Runnable {
        if (isAwaitingLayoutReady) {
            isAwaitingLayoutReady = false
            alpha = 1f
            onLayoutReadyListener?.invoke()
        }
    }

    /**
     * Prepares the editor for a newly loaded document:
     * Fades the editor to invisible (alpha = 0f) and arms the ready-check listener.
     */
    fun prepareForNewDocument() {
        animate().cancel()
        alpha = 0f
        isAwaitingLayoutReady = true
        removeCallbacks(safetyFadeInRunnable)
        postDelayed(safetyFadeInRunnable, 600)
    }

    /**
     * Evaluates whether layout calculation is complete.
     * When ready, smoothly fades in the text and triggers onLayoutReadyListener.
     */
    fun checkAndTriggerReady() {
        if (isAwaitingLayoutReady && isWordwrapReady()) {
            removeCallbacks(safetyFadeInRunnable)
            isAwaitingLayoutReady = false
            onLayoutReadyListener?.invoke()
            animate()
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Called after setting new text to check immediately if layout is already ready
     * (e.g. empty document) or to schedule the check on the UI message looper.
     */
    fun notifyContentSet() {
        if (text.length == 0 || !isWordwrap) {
            removeCallbacks(safetyFadeInRunnable)
            isAwaitingLayoutReady = false
            alpha = 1f
            onLayoutReadyListener?.invoke()
            return
        }
        post {
            checkAndTriggerReady()
        }
    }

    private val glowTopOrBottomField = try {
        io.github.rosemoe.sora.widget.EditorTouchEventHandler::class.java.getDeclaredField("glowTopOrBottom").apply {
            isAccessible = true
        }
    } catch (_: Throwable) { null }

    private val selectiveEdgeEffect by lazy {
        object : android.widget.EdgeEffect(context) {
            private fun isBottomGlow(): Boolean {
                return glowTopOrBottomField?.getBoolean(eventHandler) ?: (offsetY > 0)
            }

            override fun draw(canvas: android.graphics.Canvas): Boolean {
                if (!isBottomGlow()) {
                    finish()
                    return false
                }
                return super.draw(canvas)
            }

            override fun onPull(deltaDistance: Float, displacement: Float) {
                if (!isBottomGlow()) return
                super.onPull(deltaDistance, displacement)
            }

            override fun onPull(deltaDistance: Float) {
                if (!isBottomGlow()) return
                super.onPull(deltaDistance)
            }

            override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
                if (!isBottomGlow()) return 0f
                return super.onPullDistance(deltaDistance, displacement)
            }

            override fun onAbsorb(velocity: Int) {
                if (!isBottomGlow()) return
                super.onAbsorb(velocity)
            }
        }
    }

    override fun getVerticalEdgeEffect(): android.widget.EdgeEffect {
        return selectiveEdgeEffect
    }

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

        // Listen for layout completion events from Sora Editor to trigger smooth fade-in
        subscribeEvent(LayoutStateChangeEvent::class.java) { event, _ ->
            isLayoutBusyState = event.isLayoutBusy
            if (!event.isLayoutBusy) {
                post {
                    checkAndTriggerReady()
                }
            }
        }
    }

    /**
     * Override createLayout to preserve wordwrap cache during interactive adjustments
     * (sliders for font size, margin changes, typeface updates, pinch-to-zoom).
     *
     * When loading a brand new document (isAwaitingLayoutReady is true), cache is cleared (true).
     * Once the document is open and visible, cache is preserved (false) so that rowTable is NEVER
     * emptied while background tasks recompute breaks. This guarantees zero flashing into single-line
     * mode and zero disappearing text during interactive sliders.
     */
    override fun createLayout() {
        val shouldClear = isAwaitingLayoutReady || layout == null || forceNextLayoutClear
        forceNextLayoutClear = false
        super.createLayout(shouldClear)
    }

    val isPinchScaling: Boolean
        get() = eventHandler?.isScaling == true

    var onPinchScaleEndListener: ((finalSizeSp: Int) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wasScaling = eventHandler?.isScaling == true
        val result = super.onTouchEvent(event)
        val isScalingNow = eventHandler?.isScaling == true

        if (wasScaling && !isScalingNow) {
            val currentSp = (textSizePx / context.resources.displayMetrics.density).roundToInt().coerceIn(12, 36)
            onPinchScaleEndListener?.invoke(currentSp)
        }
        return result
    }

    override fun onDraw(canvas: Canvas) {
        if (isAwaitingLayoutReady && isWordwrap && !isWordwrapReady()) {
            // Layout computation for initial document load in progress. Paint only background
            // color to completely eliminate flashing of un-wrapped single-line fallback text.
            val bgColor = colorScheme?.getColor(EditorColorScheme.WHOLE_BACKGROUND) ?: Color.TRANSPARENT
            canvas.drawColor(bgColor)
            return
        }
        super.onDraw(canvas)
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
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas?.isImeClosing == true) {
            val savedOffsetY = offsetY
            val savedOffsetX = offsetX
            super.onSizeChanged(w, h, oldw, oldh)
            if (offsetY != savedOffsetY || offsetX != savedOffsetX) {
                try {
                    scroller?.let { s ->
                        s.startScroll(savedOffsetX, savedOffsetY, 0, 0, 0)
                        s.abortAnimation()
                    }
                } catch (_: Throwable) {}
            }
            return
        }
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0 && isAwaitingLayoutReady) {
            post {
                checkAndTriggerReady()
            }
        }

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
