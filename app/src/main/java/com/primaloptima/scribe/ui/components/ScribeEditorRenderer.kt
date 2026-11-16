package com.primaloptima.scribe.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import androidx.collection.MutableIntList
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.util.LongArrayList
import io.github.rosemoe.sora.util.MutableInt
import io.github.rosemoe.sora.widget.EditorRenderer
import io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.floor

/**
 * Single-lifecycle Sora 0.24.6 renderer supporting Phase 4 Selection-Deletion Gap Collapse.
 *
 * Preserves exactly one native {@code super.draw(canvas)} / {@code super.drawView(canvas)}
 * lifecycle and partitions visible rows into strictly disjoint Pass 1 (upper unshifted)
 * and Pass 2 (lower translated by +tauLower) regions across all draw-order layers.
 */
open class ScribeEditorRenderer(
    private val scribeEditor: ScribeCodeEditor
) : EditorRenderer(scribeEditor) {

    var renderedDrawPassSeq: Long = 0L
        private set

    var didRefreshCursorDescriptorsOnLastDraw: Boolean = false
        private set

    // Observational state for Suite C static/instrumentation verification
    internal var lastSplitFrameWasActive: Boolean = false
        private set
    internal var lastPass1RowRange: IntRange = IntRange.EMPTY
        private set
    internal var lastPass2RowRange: IntRange = IntRange.EMPTY
        private set
    internal var lastOverrideFirstLnLine: Int = -1
        private set
    internal var lastOverrideFirstLnY: Float = Float.NaN
        private set
    internal var lastPass2FirstLnDrawnInPass2: Int = -1
        private set
    internal var lastPass1ClipBottom: Float = 0f
        private set
    internal var lastPass2ClipTop: Float = 0f
        private set
    internal var lastTauLower: Float = 0f
        private set

    // Per-frame split state (populated in drawView and reset in finally)
    private var isSplitFrameActive: Boolean = false
    private var frameTauLower: Float = 0f
    private var frameSeamLine: Int = -1
    private var frameSeamFirstLowerRow: Int = -1
    private var framePass1RowRange: IntRange = IntRange.EMPTY
    private var framePass2RowRange: IntRange = IntRange.EMPTY
    private var framePass1ClipBottom: Float = 0f
    private var framePass2ClipTop: Float = 0f
    private var frameStuckLineBottom: Float = 0f

    private val framePass2LineNumbers = LongArrayList()
    private val framePass2CurrentLines = MutableIntList()
    private val framePass2FirstLn = MutableInt(-1)
    private var frameOverrideFirstLnLine: Int = -1
    private var isWarmingPass2Cache: Boolean = false

    override fun draw(canvas: Canvas) {
        val controller = scribeEditor.parentCanvas?.gapCollapseController
        if (controller != null &&
            controller.shouldApplySplitRender &&
            scribeEditor.cursorAnimator.isRunning
        ) {
            scribeEditor.parentCanvas?.handleCursorAnimatorActivatedDuringCollapse()
        }
        try {
            super.draw(canvas)
        } finally {
            scribeEditor.renderPassRowRangeOverride = null
        }
        scribeEditor.onAfterRendererDrawPass(
            ++renderedDrawPassSeq,
            didRefreshCursorDescriptorsOnLastDraw
        )
    }

    override fun drawView(canvas: Canvas) {
        didRefreshCursorDescriptorsOnLastDraw = false
        val controller = scribeEditor.parentCanvas?.gapCollapseController
        if (controller != null &&
            controller.shouldApplySplitRender &&
            scribeEditor.cursorAnimator.isRunning
        ) {
            scribeEditor.parentCanvas?.handleCursorAnimatorActivatedDuringCollapse()
        }
        prepareSplitFrameState()
        if (isSplitFrameActive) {
            scribeEditor.onGapCollapseSplitEntered()
        }
        try {
            super.drawView(canvas)
            didRefreshCursorDescriptorsOnLastDraw =
                !isSplitFrameActive && !scribeEditor.cursorAnimator.isRunning
        } finally {
            clearSplitFrameState()
            scribeEditor.renderPassRowRangeOverride = null
        }
    }

    private fun prepareSplitFrameState() {
        clearSplitFrameState()
        lastSplitFrameWasActive = false
        lastPass1RowRange = IntRange.EMPTY
        lastPass2RowRange = IntRange.EMPTY
        lastOverrideFirstLnLine = -1
        lastOverrideFirstLnY = Float.NaN
        lastPass2FirstLnDrawnInPass2 = -1
        lastPass1ClipBottom = 0f
        lastPass2ClipTop = 0f
        lastTauLower = 0f

        val canvasLayout = scribeEditor.parentCanvas ?: return
        val controller = canvasLayout.gapCollapseController
        if (!controller.shouldApplySplitRender || scribeEditor.cursorAnimator.isRunning) {
            return
        }
        val layout = scribeEditor.layout ?: return
        val rowH = scribeEditor.rowHeight.toFloat()
        val totalRows = layout.rowCount
        val seamRow = controller.activeSeamFirstLowerRow
        val seamLine = controller.activeSeamLine
        val tau = controller.tauLower
        if (rowH <= 0f || totalRows <= 0 || seamRow < 0 || seamLine < 0 || tau <= 0.5f) {
            return
        }
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()
        if (w <= 0f || h <= 0f) {
            return
        }

        scribeEditor.renderPassRowRangeOverride = null
        val normalFirstVis = scribeEditor.getFirstVisibleRow()
        val normalLastVis = scribeEditor.getLastVisibleRow()
        val offsetY = scribeEditor.offsetY.toFloat()
        val stuckBottom = getStuckLineBottom(stuckCodeBlocks)
        val seamScreenYView = scribeEditor.getRowTop(seamRow).toFloat() - offsetY
        val p1ClipBottom = minOf(h, seamScreenYView)
        val p2ClipTop = maxOf(stuckBottom, seamScreenYView + tau)

        val p1End = minOf(seamRow - 1, normalLastVis)
        val p1Range = if (p1ClipBottom > stuckBottom && normalFirstVis <= p1End && p1End >= 0) {
            normalFirstVis..p1End
        } else {
            IntRange.EMPTY
        }

        val shiftedFirstVis = maxOf(0, floor((offsetY - tau) / rowH).toInt())
        val shiftedLastVis = minOf(totalRows - 1, floor((offsetY + h - tau) / rowH).toInt())
        val p2Start = maxOf(seamRow, shiftedFirstVis)
        val p2End = minOf(totalRows - 1, shiftedLastVis)
        val p2Range = if (p2ClipTop < h && p2Start <= p2End) {
            p2Start..p2End
        } else {
            IntRange.EMPTY
        }

        isSplitFrameActive = true
        frameTauLower = tau
        frameSeamLine = seamLine
        frameSeamFirstLowerRow = seamRow
        framePass1RowRange = p1Range
        framePass2RowRange = p2Range
        framePass1ClipBottom = p1ClipBottom
        framePass2ClipTop = p2ClipTop
        frameStuckLineBottom = stuckBottom

        lastSplitFrameWasActive = true
        lastPass1RowRange = p1Range
        lastPass2RowRange = p2Range
        lastPass1ClipBottom = p1ClipBottom
        lastPass2ClipTop = p2ClipTop
        lastTauLower = tau
    }

    private fun clearSplitFrameState() {
        isSplitFrameActive = false
        frameTauLower = 0f
        frameSeamLine = -1
        frameSeamFirstLowerRow = -1
        framePass1RowRange = IntRange.EMPTY
        framePass2RowRange = IntRange.EMPTY
        framePass1ClipBottom = 0f
        framePass2ClipTop = 0f
        frameStuckLineBottom = 0f
        framePass2LineNumbers.clear()
        framePass2CurrentLines.clear()
        framePass2FirstLn.value = -1
        frameOverrideFirstLnLine = -1
    }

    override fun buildMeasureCacheForLines(
        startLine: Int,
        endLine: Int,
        timestamp: Long,
        useCachedContent: Boolean
    ) {
        super.buildMeasureCacheForLines(startLine, endLine, timestamp, useCachedContent)
        if (!isWarmingPass2Cache &&
            useCachedContent &&
            isSplitFrameActive &&
            !framePass2RowRange.isEmpty()
        ) {
            val layout = scribeEditor.layout ?: return
            val pass2FirstLine = layout.getLineNumberForRow(framePass2RowRange.first)
            val pass2LastLine = layout.getLineNumberForRow(framePass2RowRange.last)
            val rangesToWarm = listOf(
                maxOf(pass2FirstLine, 0)..minOf(pass2LastLine, startLine - 1),
                maxOf(pass2FirstLine, endLine + 1)..pass2LastLine
            ).filter { it.first <= it.last }
            if (rangesToWarm.isNotEmpty()) {
                isWarmingPass2Cache = true
                try {
                    for (range in rangesToWarm) {
                        buildMeasureCacheForLines(range.first, range.last)
                    }
                } finally {
                    isWarmingPass2Cache = false
                }
            }
        }
    }

    override fun drawRows(
        canvas: Canvas,
        offset: Float,
        postDrawLineNumbers: LongArrayList,
        postDrawCursor: MutableList<DrawCursorTask>,
        postDrawCurrentLines: MutableIntList,
        requiredFirstLn: MutableInt?,
        stuckLines: MutableList<CodeBlock>?
    ) {
        if (!isSplitFrameActive) {
            super.drawRows(
                canvas,
                offset,
                postDrawLineNumbers,
                postDrawCursor,
                postDrawCurrentLines,
                requiredFirstLn,
                stuckLines
            )
            return
        }

        frameStuckLineBottom = getStuckLineBottom(stuckLines)
        frameOverrideFirstLnLine = -1
        framePass2LineNumbers.clear()
        framePass2CurrentLines.clear()
        framePass2FirstLn.value = -1

        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()

        val pass1FirstLn: MutableInt?
        val pass2FirstLn: MutableInt?
        if (!framePass1RowRange.isEmpty()) {
            pass1FirstLn = requiredFirstLn
            pass2FirstLn = null
        } else {
            requiredFirstLn?.value = -1
            pass1FirstLn = null
            pass2FirstLn = if (requiredFirstLn != null) framePass2FirstLn else null
        }

        if (!framePass1RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            scribeEditor.renderPassRowRangeOverride = framePass1RowRange
            try {
                super.drawRows(
                    canvas,
                    offset,
                    postDrawLineNumbers,
                    postDrawCursor,
                    postDrawCurrentLines,
                    pass1FirstLn,
                    stuckLines
                )
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }

            if (requiredFirstLn != null &&
                requiredFirstLn.value != -1 &&
                postDrawLineNumbers.size() == 0 &&
                !framePass2RowRange.isEmpty()
            ) {
                frameOverrideFirstLnLine = requiredFirstLn.value
                requiredFirstLn.value = -1
            }
        }

        if (!framePass2RowRange.isEmpty()) {
            val pass2CursorTasks = ArrayList<DrawCursorTask>(3)
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            scribeEditor.renderPassRowRangeOverride = framePass2RowRange
            try {
                super.drawRows(
                    canvas,
                    offset,
                    framePass2LineNumbers,
                    pass2CursorTasks,
                    framePass2CurrentLines,
                    pass2FirstLn,
                    stuckLines
                )
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
            for (i in 0 until pass2CursorTasks.size) {
                val task = pass2CursorTasks[i]
                ScribeDrawCursorTaskAccessor.addOffsetY(task, frameTauLower)
                postDrawCursor.add(task)
            }
        }
    }

    override fun patchHighlightedDelimiters(canvas: Canvas, textOffset: Float) {
        if (!isSplitFrameActive) {
            super.patchHighlightedDelimiters(canvas, textOffset)
            return
        }
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()
        if (!framePass1RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            scribeEditor.renderPassRowRangeOverride = framePass1RowRange
            try {
                super.patchHighlightedDelimiters(canvas, textOffset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
        if (!framePass2RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            scribeEditor.renderPassRowRangeOverride = framePass2RowRange
            try {
                super.patchHighlightedDelimiters(canvas, textOffset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
    }

    override fun drawDiagnosticIndicators(canvas: Canvas, offset: Float) {
        if (!isSplitFrameActive) {
            super.drawDiagnosticIndicators(canvas, offset)
            return
        }
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()
        if (!framePass1RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            scribeEditor.renderPassRowRangeOverride = framePass1RowRange
            try {
                super.drawDiagnosticIndicators(canvas, offset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
        if (!framePass2RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            scribeEditor.renderPassRowRangeOverride = framePass2RowRange
            try {
                super.drawDiagnosticIndicators(canvas, offset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
    }

    override fun drawUserGutterBackground(canvas: Canvas, right: Int) {
        if (!isSplitFrameActive) {
            super.drawUserGutterBackground(canvas, right)
            return
        }
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()
        if (!framePass1RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            scribeEditor.renderPassRowRangeOverride = framePass1RowRange
            try {
                super.drawUserGutterBackground(canvas, right)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
        if (!framePass2RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            scribeEditor.renderPassRowRangeOverride = framePass2RowRange
            try {
                val currentLineBgColor =
                    scribeEditor.colorScheme.getColor(EditorColorScheme.CURRENT_LINE)
                for (i in 0 until framePass2CurrentLines.size) {
                    val row = framePass2CurrentLines.get(i)
                    super.drawRowBackground(canvas, currentLineBgColor, row, right)
                }
                super.drawUserGutterBackground(canvas, right)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }
    }

    override fun drawSideIcons(canvas: Canvas, offset: Float) {
        if (!isSplitFrameActive) {
            super.drawSideIcons(canvas, offset)
            return
        }
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()

        if (!framePass1RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            scribeEditor.renderPassRowRangeOverride = framePass1RowRange
            try {
                super.drawSideIcons(canvas, offset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }

        if (!framePass2RowRange.isEmpty()) {
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            scribeEditor.renderPassRowRangeOverride = framePass2RowRange
            try {
                super.drawSideIcons(canvas, offset)
            } finally {
                scribeEditor.renderPassRowRangeOverride = null
                canvas.restore()
            }
        }

        val lineNumberNotPinned = scribeEditor.isLineNumberEnabled &&
            (scribeEditor.isWordwrap || !scribeEditor.isLineNumberPinned)
        if (!lineNumberNotPinned) {
            return
        }

        val lineNumberWidth = scribeEditor.measureLineNumber()
        val offsetX = offset - lineNumberWidth
        val sideIconWidth = if (hasSideHintIcons()) scribeEditor.rowHeight.toFloat() else 0f
        val gutterLeft = maxOf(0f, offsetX)
        val dividerLeftX =
            (offsetX + lineNumberWidth + sideIconWidth + scribeEditor.dividerMarginLeft)
                .coerceAtLeast(gutterLeft)

        val cursor = scribeEditor.cursor
        val currentLineNumber = if (cursor.isSelected) -1 else cursor.leftLine
        val colorScheme = scribeEditor.colorScheme
        val lineNumberColor = colorScheme.getColor(EditorColorScheme.LINE_NUMBER)
        val currentLineNumberColor = colorScheme.getColor(EditorColorScheme.LINE_NUMBER_CURRENT)
        val metrics = lineNumberMetrics
        val rowH = scribeEditor.rowHeight.toFloat()
        val bottom = scribeEditor.getRowBottom(0).toFloat()

        val hasPass2Leading = framePass2LineNumbers.size() > 0
        val translatedTop2 = if (hasPass2Leading) {
            val row2_0 = IntPair.getSecond(framePass2LineNumbers.get(0))
            scribeEditor.getRowTop(row2_0).toFloat() - scribeEditor.offsetY.toFloat() + frameTauLower
        } else {
            Float.POSITIVE_INFINITY
        }

        // Case 2 (+ Case 4): !P1.isEmpty && postDrawLineNumbers.isEmpty
        if (!framePass1RowRange.isEmpty() && frameOverrideFirstLnLine != -1) {
            val y = if (!hasPass2Leading || translatedTop2 > bottom) {
                computeYPinned(metrics)
            } else {
                computeYPushedP2(translatedTop2, rowH, metrics)
            }
            lastOverrideFirstLnLine = frameOverrideFirstLnLine
            lastOverrideFirstLnY = y
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, h)
            drawUnshiftedFirstLn(
                canvas = canvas,
                line = frameOverrideFirstLnLine,
                offsetX = offsetX,
                lineNumberWidth = lineNumberWidth,
                y = y,
                currentLineNumber = currentLineNumber,
                lineNumberColor = lineNumberColor,
                currentLineNumberColor = currentLineNumberColor
            )
            canvas.restore()
        }

        val isCase3Or5 = framePass1RowRange.isEmpty() &&
            !framePass2RowRange.isEmpty() &&
            framePass2FirstLn.value != -1
        val pass2TopScreenY = if (!framePass2RowRange.isEmpty()) {
            scribeEditor.getRowTop(framePass2RowRange.first).toFloat() -
                scribeEditor.offsetY.toFloat() +
                frameTauLower
        } else {
            Float.NEGATIVE_INFINITY
        }

        // Case 3 top (+ Case 4): P1.isEmpty && !P2.isEmpty && pass2TopScreenY <= 0f
        if (isCase3Or5 && pass2TopScreenY <= 0f) {
            val p2FirstLine = framePass2FirstLn.value
            val y = if (!hasPass2Leading || translatedTop2 > bottom) {
                computeYPinned(metrics)
            } else {
                computeYPushedP2(translatedTop2, rowH, metrics)
            }
            lastOverrideFirstLnLine = p2FirstLine
            lastOverrideFirstLnY = y
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, h)
            drawUnshiftedFirstLn(
                canvas = canvas,
                line = p2FirstLine,
                offsetX = offsetX,
                lineNumberWidth = lineNumberWidth,
                y = y,
                currentLineNumber = currentLineNumber,
                lineNumberColor = lineNumberColor,
                currentLineNumberColor = currentLineNumberColor
            )
            canvas.restore()
        }

        // Case 5 (P1.isEmpty && pass2TopScreenY > 0f) + all Pass-2 leading row line numbers
        val drawCase5InPass2 = isCase3Or5 && pass2TopScreenY > 0f
        if (!framePass2RowRange.isEmpty() && (hasPass2Leading || drawCase5InPass2)) {
            canvas.save()
            canvas.clipRect(gutterLeft, framePass2ClipTop, dividerLeftX, h)
            canvas.translate(0f, frameTauLower)
            if (drawCase5InPass2) {
                val p2FirstLine = framePass2FirstLn.value
                lastPass2FirstLnDrawnInPass2 = p2FirstLine
                val lnColor = if (p2FirstLine == currentLineNumber) {
                    currentLineNumberColor
                } else {
                    lineNumberColor
                }
                super.drawLineNumber(
                    canvas,
                    p2FirstLine,
                    framePass2RowRange.first,
                    offsetX,
                    lineNumberWidth,
                    lnColor
                )
            }
            for (i in 0 until framePass2LineNumbers.size()) {
                val packed = framePass2LineNumbers.get(i)
                val ln = IntPair.getFirst(packed)
                val rw = IntPair.getSecond(packed)
                val lnColor = if (ln == currentLineNumber) {
                    currentLineNumberColor
                } else {
                    lineNumberColor
                }
                super.drawLineNumber(canvas, ln, rw, offsetX, lineNumberWidth, lnColor)
            }
            canvas.restore()
        }
    }

    override fun drawSideBlockLine(canvas: Canvas) {
        if (!isSplitFrameActive) {
            super.drawSideBlockLine(canvas)
            return
        }
        if (!scribeEditor.props.drawSideBlockLine) return
        val blocks = scribeEditor.styles?.blocks
        if (blocks == null || blocks.isEmpty()) return
        val current = scribeEditor.currentCursorBlock
        if (current !in blocks.indices) return
        val block = blocks[current]
        val w = scribeEditor.width.toFloat()
        val h = scribeEditor.height.toFloat()
        if (!framePass1RowRange.isEmpty() && block.startLine < frameSeamLine) {
            canvas.save()
            canvas.clipRect(0f, frameStuckLineBottom, w, framePass1ClipBottom)
            try {
                super.drawSideBlockLine(canvas)
            } finally {
                canvas.restore()
            }
        }
        if (!framePass2RowRange.isEmpty() && block.endLine >= frameSeamLine) {
            canvas.save()
            canvas.clipRect(0f, framePass2ClipTop, w, h)
            canvas.translate(0f, frameTauLower)
            try {
                super.drawSideBlockLine(canvas)
            } finally {
                canvas.restore()
            }
        }
    }

    private fun computeYPinned(metrics: Paint.FontMetricsInt): Float {
        return (scribeEditor.getRowBottom(0) + scribeEditor.getRowTop(0)) / 2f -
            (metrics.descent - metrics.ascent) / 2f -
            metrics.ascent
    }

    private fun computeYPushedP2(
        translatedTop2: Float,
        rowH: Float,
        metrics: Paint.FontMetricsInt
    ): Float {
        return translatedTop2 - rowH / 2f -
            (metrics.descent - metrics.ascent) / 2f -
            metrics.ascent
    }

    private fun drawUnshiftedFirstLn(
        canvas: Canvas,
        line: Int,
        offsetX: Float,
        lineNumberWidth: Float,
        y: Float,
        currentLineNumber: Int,
        lineNumberColor: Int,
        currentLineNumberColor: Int
    ) {
        if (lineNumberWidth + offsetX <= 0f) return
        val align = scribeEditor.lineNumberAlign
        paintOther.textAlign = align
        paintOther.color = if (line == currentLineNumber) {
            currentLineNumberColor
        } else {
            lineNumberColor
        }
        val text = (line + 1).toString()
        when (align) {
            Paint.Align.LEFT -> canvas.drawText(text, offsetX, y, paintOther)
            Paint.Align.RIGHT -> canvas.drawText(text, offsetX + lineNumberWidth, y, paintOther)
            Paint.Align.CENTER -> canvas.drawText(
                text,
                offsetX + (lineNumberWidth + scribeEditor.dividerMarginLeft) / 2f,
                y,
                paintOther
            )
        }
    }
}
