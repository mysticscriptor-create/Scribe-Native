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
import io.github.rosemoe.sora.event.HandleStateChangeEvent
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

    var isHandleDragging: Boolean = false
        private set

    var horizontalPaddingDp: Float = 10f
        set(value) {
            val clamped = value.coerceIn(8f, 72f)
            if (field != clamped) {
                field = clamped
                try {
                    renderContext.invalidateRenderNodes()
                } catch (_: Throwable) {}
                createLayout()
                invalidate()
            }
        }

    private var isInsideWordwrapLayoutCreation: Boolean = false

    override fun measureTextRegionOffset(): Float {
        val density = context.resources.displayMetrics.density
        val leftPadPx = horizontalPaddingDp * density
        // When WordwrapLayout calculates line-wrapping width, it calculates:
        // width = editor.getWidth() - (editor.measureTextRegionOffset() + measureText("a"))
        // By adding right padding during layout construction, text wraps at (width - rightPadPx),
        // giving symmetrical left and right page margins while drawing starts at leftPadPx.
        val rightPadPx = if (isInsideWordwrapLayoutCreation) horizontalPaddingDp * density else 0f
        val totalPadPx = leftPadPx + rightPadPx
        return if (isLineNumberEnabled) {
            super.measureTextRegionOffset() + totalPadPx
        } else {
            totalPadPx
        }
    }

    init {
        props.autoIndent = false
        props.deleteEmptyLineFast = false
        props.deleteMultiSpaces = 1
        try {
            subscribeEvent(HandleStateChangeEvent::class.java) { event, _ ->
                isHandleDragging = event.isHeld
            }
        } catch (_: Throwable) { }
    }

    /**
     * Hit tests whether local (touchX, touchY) falls within the bounds of any active selection
     * handle or single-cursor insertion handle.
     */
    fun isTouchOnHandle(touchX: Float, touchY: Float): Boolean {
        if (!isAttachedToWindow) return false
        return try {
            if (touchX < 0f || touchY < 0f || touchX > width.toFloat() || touchY > height.toFloat()) {
                return false
            }
            val cur = cursor ?: return false
            val lay = layout ?: return false
            val textRegionOffset = measureTextRegionOffset()
            val scrollX = offsetX.toFloat()
            val scrollY = offsetY.toFloat()
            val rHeight = rowHeight.toFloat()
            val density = context.resources.displayMetrics.density

            fun checkHandleHit(anchorX: Float, anchorY: Float, handleType: Int): Boolean {
                val bulbOffsetX = when (handleType) {
                    -1 -> -9f * density
                    1 -> 9f * density
                    else -> 0f
                }
                val bulbCenterY = anchorY + (9f * density)
                val bulbCenterX = anchorX + bulbOffsetX
                val bulbRadius = 16f * density
                val dxBulb = touchX - bulbCenterX
                val dyBulb = touchY - bulbCenterY
                if ((dxBulb * dxBulb + dyBulb * dyBulb) <= (bulbRadius * bulbRadius)) {
                    return true
                }
                val anchorRadius = 10f * density
                val dxAnchor = touchX - anchorX
                val dyAnchor = touchY - anchorY
                if (dyAnchor >= -4f * density && (dxAnchor * dxAnchor + dyAnchor * dyAnchor) <= (anchorRadius * anchorRadius)) {
                    return true
                }
                val minX = when (handleType) {
                    -1 -> anchorX - (22f * density)
                    1 -> anchorX - (4f * density)
                    else -> anchorX - (12f * density)
                }
                val maxX = when (handleType) {
                    -1 -> anchorX + (4f * density)
                    1 -> anchorX + (22f * density)
                    else -> anchorX + (12f * density)
                }
                val minY = anchorY - (4f * density)
                val maxY = anchorY + (20f * density)
                return touchX in minX..maxX && touchY in minY..maxY
            }

            if (cur.isSelected) {
                val leftOffset = lay.getCharLayoutOffset(cur.leftLine, cur.leftColumn)
                if (leftOffset != null && leftOffset.size >= 2) {
                    val leftCharX = leftOffset[1] + textRegionOffset - scrollX
                    val leftCharY = leftOffset[0] - scrollY
                    val anchorY = leftCharY + rHeight
                    if (checkHandleHit(leftCharX, anchorY, handleType = -1)) {
                        return true
                    }
                }
                val rightOffset = lay.getCharLayoutOffset(cur.rightLine, cur.rightColumn)
                if (rightOffset != null && rightOffset.size >= 2) {
                    val rightCharX = rightOffset[1] + textRegionOffset - scrollX
                    val rightCharY = rightOffset[0] - scrollY
                    val anchorY = rightCharY + rHeight
                    if (checkHandleHit(rightCharX, anchorY, handleType = 1)) {
                        return true
                    }
                }
            } else {
                val curOffset = lay.getCharLayoutOffset(cur.leftLine, cur.leftColumn)
                if (curOffset != null && curOffset.size >= 2) {
                    val curCharX = curOffset[1] + textRegionOffset - scrollX
                    val curCharY = curOffset[0] - scrollY
                    val anchorY = curCharY + rHeight
                    if (checkHandleHit(curCharX, anchorY, handleType = 0)) {
                        return true
                    }
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    var firstLineIndentSpaces: Int = 0
    private var isInternalPasting: Boolean = false

    override fun pasteText() {
        super.pasteText()
    }

    override fun pasteText(text: CharSequence?) {
        if (text == null) return
        isInternalPasting = true
        try {
            val processed = if (firstLineIndentSpaces > 0) formatPastedText(text) else text
            super.pasteText(processed)
        } finally {
            postDelayed({ isInternalPasting = false }, 500)
        }
    }

    override fun commitText(text: CharSequence, applyAutoIndent: Boolean, applySymbolCompletion: Boolean) {
        if (text == "\n" || text == "\r\n" || text == "\r") {
            handleProseNewline()
            return
        }

        if (!isInternalPasting && firstLineIndentSpaces > 0) {
            val cur = cursor
            if (!cur.isSelected) {
                val line = cur.leftLine
                val col = cur.leftColumn
                val lineStr = if (line < this.text.lineCount) this.text.getLineString(line) else ""
                val indent = " ".repeat(firstLineIndentSpaces)

                if (lineStr.isEmpty()) {
                    // Line is completely empty: ensure it starts with indent
                    val processed = if (text.contains('\n') || text.contains('\r') || text.length > 1) {
                        formatPastedText(text)
                    } else {
                        "$indent$text"
                    }
                    this.text.insert(line, 0, processed)
                    setSelection(line, processed.length)
                    ensureSelectionVisible()
                    notifyIMEExternalCursorChange()
                    return
                }

                if (text.contains('\n') || text.contains('\r') || isPastedOrMultiLineText(text)) {
                    val processed = formatPastedText(text)
                    super.commitText(processed, applyAutoIndent, applySymbolCompletion)
                    return
                }
            } else {
                if (text.contains('\n') || text.contains('\r') || text.length > 1) {
                    val processed = formatPastedText(text)
                    super.commitText(processed, applyAutoIndent, applySymbolCompletion)
                    return
                }
            }
        }

        super.commitText(text, applyAutoIndent, applySymbolCompletion)
    }

    private fun isPastedOrMultiLineText(text: CharSequence): Boolean {
        if (text.contains('\n') || text.contains('\r')) {
            return true
        }
        val cur = cursor
        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = if (line < this.text.lineCount) this.text.getLineString(line) else ""
        val isAtLineStart = col == 0 || (col <= firstLineIndentSpaces && lineStr.trim().isEmpty())
        return isAtLineStart && text.length > 1
    }

    private fun formatPastedText(rawText: CharSequence): String {
        if (firstLineIndentSpaces <= 0) return rawText.toString()
        val indent = " ".repeat(firstLineIndentSpaces)
        val cur = cursor
        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = if (line < text.lineCount) text.getLineString(line) else ""
        val isCursorAtStart = col == 0 || (col <= firstLineIndentSpaces && lineStr.trim().isEmpty())
        val lineAlreadyHasIndent = lineStr.startsWith(indent) && col >= indent.length

        // If current line has unselected whitespace only and cursor is at start without full indent,
        // clear it before paste so leading spaces are not duplicated.
        if (!cur.isSelected && isCursorAtStart && !lineAlreadyHasIndent && lineStr.isNotEmpty() && lineStr.all { it == ' ' || it == '\t' }) {
            text.delete(line, 0, line, lineStr.length)
        }

        val splitLines = rawText.toString().lines()
        val result = StringBuilder()

        for (i in splitLines.indices) {
            val rawLine = splitLines[i]
            val trimmed = rawLine.trimStart()

            val isMarkdownNonProse = trimmed.startsWith("#") ||
                trimmed.startsWith("---") ||
                trimmed.startsWith("***") ||
                trimmed.startsWith("* * *") ||
                trimmed.startsWith("###") ||
                trimmed.startsWith("___") ||
                trimmed.startsWith(">") ||
                trimmed.startsWith("```")

            val indentedLine = when {
                isMarkdownNonProse -> trimmed
                trimmed.isEmpty() -> indent
                else -> "$indent$trimmed"
            }

            if (i == 0) {
                if (lineAlreadyHasIndent) {
                    result.append(trimmed)
                } else if (!isCursorAtStart && lineStr.trim().isNotEmpty()) {
                    result.append(rawLine)
                } else {
                    result.append(indentedLine)
                }
            } else {
                result.append("\n").append(indentedLine)
            }
        }

        return result.toString()
    }

    private fun handleProseNewline() {
        val cur = cursor
        if (cur.isSelected) {
            text.delete(cur.leftLine, cur.leftColumn, cur.rightLine, cur.rightColumn)
        }
        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = text.getLineString(line)
        val isWhitespaceOnly = lineStr.isNotEmpty() && lineStr.all { it == ' ' || it == '\t' }

        if (firstLineIndentSpaces > 0) {
            val indent = " ".repeat(firstLineIndentSpaces)
            if (lineStr.isEmpty()) {
                text.insert(line, 0, "$indent\n$indent")
            } else if (isWhitespaceOnly) {
                if (lineStr != indent) {
                    text.replace(line, 0, line, lineStr.length, "$indent\n$indent")
                } else {
                    text.insert(line, indent.length, "\n$indent")
                }
            } else {
                text.insert(line, col, "\n$indent")
            }
            ensureSelectionVisible()
            notifyIMEExternalCursorChange()
            return
        }

        // Indent is OFF (Manual mode):
        if (isWhitespaceOnly) {
            text.replace(line, 0, line, lineStr.length, "\n")
            ensureSelectionVisible()
            notifyIMEExternalCursorChange()
            return
        }

        val indentToInsert = if (lineStr.isEmpty()) {
            ""
        } else {
            var p = 0
            val maxCheck = minOf(col, lineStr.length)
            while (p < maxCheck && (lineStr[p] == ' ' || lineStr[p] == '\t')) {
                p++
            }
            if (p > 0) lineStr.substring(0, p) else ""
        }
        val insertStr = "\n$indentToInsert"
        text.insert(line, col, insertStr)
        ensureSelectionVisible()
        notifyIMEExternalCursorChange()
    }
    override fun deleteText() {
        val cur = cursor
        if (cur.isSelected) {
            super.deleteText()
            return
        }

        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = text.getLineString(line)
        val isWhitespaceOnly = lineStr.isEmpty() || lineStr.all { it == ' ' || it == '\t' }

        if (firstLineIndentSpaces > 0) {
            val indent = " ".repeat(firstLineIndentSpaces)

            if (isWhitespaceOnly) {
                // When indent is ON and on an empty/whitespace line:
                // Delete this entire empty line and place cursor at the end of the previous line
                if (line > 0) {
                    val prevLine = line - 1
                    val prevCol = text.getColumnCount(prevLine)
                    text.delete(prevLine, prevCol, line, lineStr.length)
                    setSelection(prevLine, prevCol)
                    ensureSelectionVisible()
                    notifyIMEExternalCursorChange()
                    return
                } else {
                    // On line 0 empty line: ensure it has indent spaces and cursor at indent.length
                    if (lineStr != indent) {
                        text.replace(0, 0, 0, lineStr.length, indent)
                    }
                    setSelection(0, indent.length)
                    ensureSelectionVisible()
                    notifyIMEExternalCursorChange()
                    return
                }
            } else if (col <= firstLineIndentSpaces && lineStr.startsWith(indent)) {
                // Cursor is at or before the indentation boundary of a line with text:
                // (e.g. col == firstLineIndentSpaces, right in front of the text)
                // Backspace merges this line's text with previous line!
                if (line > 0) {
                    val prevLine = line - 1
                    val prevCol = text.getColumnCount(prevLine)
                    // Delete newline and the indent of this line, merging content to prevLine
                    text.delete(prevLine, prevCol, line, firstLineIndentSpaces)
                    setSelection(prevLine, prevCol)
                    ensureSelectionVisible()
                    notifyIMEExternalCursorChange()
                    return
                }
            }
        }

        // When indent is OFF: normal delete behavior (deletes one character/space at a time)
        super.deleteText()
    }

    /**
     * Live updates all paragraphs in the document when First-Line Indent slider is changed.
     * Preserves blank lines, Markdown headings, and scene breaks.
     */
    fun applyFirstLineIndentToDocument(oldIndent: Int, newIndent: Int) {
        firstLineIndentSpaces = newIndent
        val content = text ?: return
        val count = content.lineCount
        if (count == 0) return

        content.beginBatchEdit()
        try {
            val newIndentStr = if (newIndent > 0) " ".repeat(newIndent) else ""
            for (i in 0 until count) {
                val lineStr = content.getLineString(i)
                val trimmed = lineStr.trimStart()

                // Skip Markdown headings, scene breaks, blockquotes, code blocks
                if (trimmed.startsWith("#") || trimmed.startsWith("---") ||
                    trimmed.startsWith("***") || trimmed.startsWith("* * *") ||
                    trimmed.startsWith("###") || trimmed.startsWith("___") ||
                    trimmed.startsWith(">") || trimmed.startsWith("```")) {
                    continue
                }

                // Count existing leading spaces
                var leadingSpaceCount = 0
                while (leadingSpaceCount < lineStr.length && lineStr[leadingSpaceCount] == ' ') {
                    leadingSpaceCount++
                }

                if (newIndent > 0) {
                    if (trimmed.isEmpty()) {
                        // Empty / blank line: ensure it has newIndentStr
                        if (lineStr.length != newIndent) {
                            if (lineStr.isEmpty()) {
                                content.insert(i, 0, newIndentStr)
                            } else {
                                content.replace(i, 0, i, lineStr.length, newIndentStr)
                            }
                        }
                    } else if (oldIndent == 0) {
                        // Turning indent ON
                        if (leadingSpaceCount == 0) {
                            content.insert(i, 0, newIndentStr)
                        } else if (leadingSpaceCount in 2..8) {
                            content.replace(i, 0, i, leadingSpaceCount, newIndentStr)
                        }
                    } else {
                        // Adjusting indent between non-zero values (e.g. 2 -> 4 or 4 -> 6)
                        if (leadingSpaceCount == oldIndent || leadingSpaceCount in 2..8) {
                            content.replace(i, 0, i, leadingSpaceCount, newIndentStr)
                        } else if (leadingSpaceCount == 0) {
                            content.insert(i, 0, newIndentStr)
                        }
                    }
                } else {
                    // Turning indent OFF (newIndent == 0)
                    if (trimmed.isEmpty()) {
                        if (lineStr.isNotEmpty()) {
                            content.delete(i, 0, i, lineStr.length)
                        }
                    } else if (oldIndent > 0 && leadingSpaceCount == oldIndent) {
                        content.delete(i, 0, i, oldIndent)
                    } else if (leadingSpaceCount in 2..8) {
                        content.delete(i, 0, i, leadingSpaceCount)
                    }
                }
            }
        } finally {
            content.endBatchEdit()
        }
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
    }

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
        createLayout(shouldClear)
    }

    override fun createLayout(clearWordwrapCache: Boolean) {
        val shouldClear = clearWordwrapCache || isAwaitingLayoutReady || layout == null || forceNextLayoutClear
        forceNextLayoutClear = false
        isInsideWordwrapLayoutCreation = true
        try {
            super.createLayout(shouldClear)
        } finally {
            isInsideWordwrapLayoutCreation = false
        }
    }

    val isPinchScaling: Boolean
        get() = eventHandler?.isScaling == true

    var onPinchScaleEndListener: ((finalSizeSp: Int) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val cur = try { cursor } catch (_: Throwable) { null }
            if (cur?.isSelected != true) {
                isHandleDragging = false
            }
        }
        val parentCanvas = parent as? UnifiedCanvasLayout
        val wasScaling = eventHandler?.isScaling == true
        val result = super.onTouchEvent(event)
        val isScalingNow = eventHandler?.isScaling == true

        if (wasScaling && !isScalingNow) {
            val currentSp = (textSizePx / context.resources.displayMetrics.density).roundToInt().coerceIn(12, 36)
            onPinchScaleEndListener?.invoke(currentSp)
        }
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight && offsetY > 0) {
            val strayY = offsetY
            try {
                scroller?.let { s ->
                    s.startScroll(s.currX, 0, 0, 0, 0)
                    s.abortAnimation()
                }
            } catch (_: Throwable) {}
            parentCanvas.scrollCanvasBy(strayY.toFloat())
        }
        return result
    }

    override fun scrollTo(x: Int, y: Int) {
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight) {
            if (y > 0) {
                parentCanvas.scrollCanvasBy(y.toFloat())
                super.scrollTo(x, 0)
                return
            }
        }
        super.scrollTo(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight && offsetY > 0) {
            val strayY = offsetY
            try {
                scroller?.let { s ->
                    s.startScroll(s.currX, 0, 0, 0, 0)
                    s.abortAnimation()
                }
            } catch (_: Throwable) {}
            parentCanvas.scrollCanvasBy(strayY.toFloat())
        }
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
        super.onSizeChanged(w, h, oldw, oldh)
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
        postInvalidateOnAnimation()

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
    }

    override fun ensureSelectionVisible() {
        // Only scroll to selection if this editor actually has keyboard focus
        if (!isFocused) return
        super.ensureSelectionVisible()
    }

    override fun computeScroll() {
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight && offsetY > 0) {
            val strayY = offsetY
            try {
                scroller?.let { s ->
                    s.startScroll(s.currX, 0, 0, 0, 0)
                    s.abortAnimation()
                }
            } catch (_: Throwable) {}
            parentCanvas.scrollCanvasBy(strayY.toFloat())
        }
        val scroller = scroller
        val wasFinished = scroller?.isFinished ?: true
        val prevY = scroller?.currY ?: 0
        super.computeScroll()

        // When flinging towards the top of the text (offsetY reaching 0):
        // Only transfer fling to canvas header if this is a genuine fling and user is not holding finger down
        if (!wasFinished && scroller != null && scroller.currY <= 0 && prevY > 0) {
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

        // 1. Horizontal cursor visibility:
        val currFinalX = if (scroller.isFinished) offsetX.toFloat() else scroller.finalX.toFloat()
        var targetX = currFinalX
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
        if (abs(targetX - offsetX.toFloat()) >= 1f) {
            scroller.startScroll(offsetX, offsetY, (targetX - offsetX).toInt(), 0, 0)
            scroller.abortAnimation()
        }

        // 2. Vertical cursor visibility through UnifiedCanvasLayout:
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null) {
            val headerRemaining = parentCanvas.headerHeight - parentCanvas.scrollD
            val screenYBottom = headerRemaining + yOffset - offsetY
            val screenYTop = screenYBottom - rowHeight

            val visibleHeight = parentCanvas.height
            val bottomMargin = 4f * dpUnit

            if (visibleHeight > 0) {
                if (screenYBottom > visibleHeight - bottomMargin) {
                    val deltaY = screenYBottom - (visibleHeight - bottomMargin)
                    if (isInternalPasting) {
                        parentCanvas.smoothScrollCanvasBy(deltaY, durationMs = 280)
                    } else if (kotlin.math.abs(deltaY) > rowHeight * 1.5f) {
                        parentCanvas.smoothScrollCanvasBy(deltaY, durationMs = 180)
                    } else {
                        parentCanvas.smoothScrollCanvasBy(deltaY, durationMs = 120)
                    }
                } else if (screenYTop < 0 && (parentCanvas.scrollD > 0 || offsetY > 0)) {
                    val deltaY = screenYTop
                    parentCanvas.smoothScrollCanvasBy(deltaY, durationMs = 120)
                }
            }
            invalidate()
            return
        }

        // Fallback when not hosted in UnifiedCanvasLayout:
        val currFinalY = if (scroller.isFinished) offsetY.toFloat() else scroller.finalY.toFloat()
        var targetY = currFinalY
        val effectiveHeight = height.coerceAtLeast(rowHeight)
        val topLines = if (props.stickyScroll) props.stickyScrollMaxLines else 2
        if (yOffset - rowHeight * topLines < currFinalY) {
            targetY = yOffset - rowHeight * topLines
        }
        val bottomMargin = 4f * dpUnit
        if (yOffset > effectiveHeight + currFinalY - bottomMargin) {
            targetY = yOffset - effectiveHeight + bottomMargin
        }
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
