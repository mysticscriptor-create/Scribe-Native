package com.primaloptima.scribe.ui.components

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.style.CursorAnimator

/**
 * ScribeCursorAnimator — Custom Sora CodeEditor CursorAnimator.
 *
 * Implements smooth, continuous, physics-based cursor gliding for:
 * 1. Pressing Enter to create a new line (in both Indent ON and Indent OFF modes).
 * 2. Pressing Backspace / Delete to delete or merge a line (in both modes).
 * 3. Tapping on different lines in the document (matching Sora's signature glide feel).
 * 4. Mid-flight interruption: if the cursor is already in motion and a new navigation
 *    or edit occurs, it smoothly glides from its current intermediate position without jumping.
 *
 * Crash Guard:
 * ValueAnimator instances are only created via ValueAnimator.ofFloat() with explicit ranges.
 * If start() is called when no movement has occurred (e.g., initial document load),
 * it returns safely without invoking start() on an uninitialized ValueAnimator.
 */
class ScribeCursorAnimator(private val editor: CodeEditor) : CursorAnimator, ValueAnimator.AnimatorUpdateListener {

    private val glideInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val glideDurationMs = 140L

    private var animatorX: ValueAnimator? = null
    private var animatorY: ValueAnimator? = null
    private var animatorHeight: ValueAnimator? = null

    private var isAnimationReady: Boolean = false

    private var currentVisualX = 0f
    private var currentVisualY = 0f
    private var currentVisualHeight = 0f

    private var isGlideArmed = false
    private var armedStartX = 0f
    private var armedStartY = 0f
    private var armedStartHeight = 0f
    private var armedTargetLine: Int? = null
    private var armedTargetCol: Int? = null
    private var armedFallbackTargetY: Float? = null

    private var lastAnimateTimestamp: Long = 0L

    /**
     * Arms an intentional cursor glide BEFORE modifying document text or selection.
     * This preserves the true origin coordinates against multi-pass selection events
     * (such as auto-indent floor enforcement or IME sync).
     */
    fun armGlide(
        startX: Float,
        startY: Float,
        startHeight: Float,
        targetLine: Int? = null,
        targetCol: Int? = null,
        fallbackTargetY: Float? = null
    ) {
        isGlideArmed = true
        armedStartX = startX
        armedStartY = startY
        armedStartHeight = startHeight
        armedTargetLine = targetLine
        armedTargetCol = targetCol
        armedFallbackTargetY = fallbackTargetY
    }

    /**
     * Directly triggers or completes a glide transition to target coordinates.
     */
    fun triggerGlide(
        fromX: Float,
        fromY: Float,
        fromHeight: Float,
        toX: Float,
        toY: Float,
        toHeight: Float
    ) {
        if (!editor.isCursorAnimationEnabled) return

        if (kotlin.math.abs(fromX - toX) < 1f && kotlin.math.abs(fromY - toY) < 1f) {
            cancel()
            currentVisualX = toX
            currentVisualY = toY
            currentVisualHeight = toHeight
            return
        }

        cancel()
        currentVisualX = fromX
        currentVisualY = fromY
        currentVisualHeight = fromHeight

        val ax = ValueAnimator.ofFloat(fromX, toX).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
            addUpdateListener(this@ScribeCursorAnimator)
        }
        val ay = ValueAnimator.ofFloat(fromY, toY).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
        }
        val ah = ValueAnimator.ofFloat(fromHeight, toHeight).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
        }

        animatorX = ax
        animatorY = ay
        animatorHeight = ah

        lastAnimateTimestamp = System.currentTimeMillis()
        isAnimationReady = false
        ax.start()
        ay.start()
        ah.start()
    }

    override fun markStartPos() {
        if (isGlideArmed) {
            // Retain the pre-edit armed start position
            return
        }
        if (isRunning) {
            // Mid-flight retarget: seamlessly continue from current visual position
            armedStartX = animatedX()
            armedStartY = animatedY()
            armedStartHeight = animatedLineHeight()
            cancel()
            currentVisualX = armedStartX
            currentVisualY = armedStartY
            currentVisualHeight = armedStartHeight
            return
        }
        val cur = try { editor.cursor } catch (_: Throwable) { null } ?: return
        val lay = editor.layout ?: return
        val line = cur.leftLine
        val col = cur.leftColumn
        val offset = try { lay.getCharLayoutOffset(line, col) } catch (_: Throwable) { null } ?: return
        currentVisualX = editor.measureTextRegionOffset() + offset[1]
        currentVisualY = offset[0]
        currentVisualHeight = editor.rowHeight.toFloat()
    }

    override fun markEndPos() {
        if (!editor.isCursorAnimationEnabled) return

        val fromX = if (isGlideArmed) armedStartX else currentVisualX
        val fromY = if (isGlideArmed) armedStartY else currentVisualY
        val fromH = if (isGlideArmed) armedStartHeight else currentVisualHeight
        val fallbackY = armedFallbackTargetY
        isGlideArmed = false

        val cur = try { editor.cursor } catch (_: Throwable) { null } ?: return
        val lay = editor.layout ?: return
        val targetLine = armedTargetLine ?: cur.leftLine
        val targetCol = armedTargetCol ?: cur.leftColumn
        armedTargetLine = null
        armedTargetCol = null
        armedFallbackTargetY = null

        val offset = try { lay.getCharLayoutOffset(targetLine, targetCol) } catch (_: Throwable) { null }

        val toX: Float
        val toY: Float
        val toH = editor.rowHeight.toFloat()

        if (offset != null) {
            toX = editor.measureTextRegionOffset() + offset[1]
            toY = offset[0]
        } else {
            // Layout re-measurement fallback while WordwrapLayout background thread settles
            toY = fallbackY ?: (fromY + if (targetLine > cur.leftLine) toH else -toH)
            val indentAdvance = if (targetCol > 0) editor.textPaint.measureText(" ".repeat(targetCol)) else 0f
            toX = editor.measureTextRegionOffset() + indentAdvance
        }

        if (kotlin.math.abs(fromX - toX) < 1f && kotlin.math.abs(fromY - toY) < 1f) {
            cancel()
            currentVisualX = toX
            currentVisualY = toY
            currentVisualHeight = toH
            return
        }

        cancel()
        currentVisualX = fromX
        currentVisualY = fromY
        currentVisualHeight = fromH

        animatorX = ValueAnimator.ofFloat(fromX, toX).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
            addUpdateListener(this@ScribeCursorAnimator)
        }
        animatorY = ValueAnimator.ofFloat(fromY, toY).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
        }
        animatorHeight = ValueAnimator.ofFloat(fromH, toH).apply {
            duration = glideDurationMs
            interpolator = glideInterpolator
        }
        isAnimationReady = true
    }

    override fun start() {
        if (!editor.isCursorAnimationEnabled || !isAnimationReady) return
        isAnimationReady = false
        lastAnimateTimestamp = System.currentTimeMillis()
        animatorX?.start()
        animatorY?.start()
        animatorHeight?.start()
    }

    override fun cancel() {
        isAnimationReady = false
        animatorX?.cancel()
        animatorY?.cancel()
        animatorHeight?.cancel()
        animatorX = null
        animatorY = null
        animatorHeight = null
    }

    override fun isRunning(): Boolean =
        animatorX?.isRunning == true || animatorY?.isRunning == true || animatorHeight?.isRunning == true

    override fun animatedX(): Float =
        animatorX?.takeIf { it.isRunning }?.animatedValue as? Float ?: currentVisualX

    override fun animatedY(): Float =
        animatorY?.takeIf { it.isRunning }?.animatedValue as? Float ?: currentVisualY

    override fun animatedLineHeight(): Float =
        animatorHeight?.takeIf { it.isRunning }?.animatedValue as? Float ?: currentVisualHeight

    override fun animatedLineBottom(): Float = animatedY()

    override fun onAnimationUpdate(animation: ValueAnimator) {
        editor.postInvalidateOnAnimation()
    }
}
