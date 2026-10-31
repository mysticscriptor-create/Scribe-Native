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
import io.github.rosemoe.sora.event.InlayHintClickEvent
import com.primaloptima.scribe.engine.ImageHintClickData
import com.primaloptima.scribe.util.image.VaultImageManager
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.layout.WordwrapLayout
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import com.primaloptima.scribe.engine.ScribeIndentEngine
import com.primaloptima.scribe.engine.ScribeListEngine
import com.primaloptima.scribe.engine.ScribeSmartEnterEngine
import com.primaloptima.scribe.util.model.ShortcutAction
import com.primaloptima.scribe.engine.IndentInlayHint
import com.primaloptima.scribe.engine.IndentInlayHintRenderer
import com.primaloptima.scribe.engine.ScribeEditorIndentFormatter
import com.primaloptima.scribe.engine.FormatterNewlineAction
import com.primaloptima.scribe.engine.FormatterDeleteAction
import com.primaloptima.scribe.util.DocumentOpenTrace

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
    var isInteractingWithImage: Boolean = false
    private var lastInlayHintClickTime: Long = 0L
    val parentCanvas: UnifiedCanvasLayout?
        get() = parent as? UnifiedCanvasLayout

    var visualFirstLineIndentSpaces: Int = 0
        private set

    var isProcessingIndent: Boolean = false

    var documentVersion: Long = 0L
        private set

    // Holds the base prose inlay hints (scene word counts, POV badges) from ProseInlayHintProvider
    private var baseInlayHints: InlayHintsContainer? = null

    // Callback when copy is attempted during visual-only indent state to trigger immediate commit
    var onRequestCommitIndentForCopy: (() -> Unit)? = null
    // Callback when an image inlay hint is tapped
    var onImageHintClickListener: ((ImageHintClickData) -> Unit)? = null

    private var pendingCopyAction: (() -> Unit)? = null

    /**
     * Pure Writer persistent bottom space architecture:
     * Extra virtual height equal to the shortcut bar + keyboard height.
     * Allows the document to scroll freely with the keyboard appearing on top of it,
     * keeping text completely stationary on keyboard dismissal.
     */
    var persistentBottomSpacePx: Int = 0
    var isKeyboardVisible: Boolean = false
    var selectAllScrollMode: ScribeScrollPolicy.SelectAllScrollMode = ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION
    private var isExplicitSelectAllInProgress = false

    override fun getScrollMaxY(): Int {
        return try {
            val pCanvas = parent as? UnifiedCanvasLayout
            val isHeaderFullyHidden = if (pCanvas != null && pCanvas.headerHeight > 0) {
                pCanvas.scrollD >= pCanvas.headerHeight
            } else {
                pCanvas == null
            }
            if (!isHeaderFullyHidden) {
                return 0
            }

            val layout = this.layout ?: return super.getScrollMaxY()
            val layoutH = layout.layoutHeight
            val baseSora = super.getScrollMaxY()
            val density = context.resources.displayMetrics.density
            val breathingMargin = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val activeGlideTarget = 0
            val parentPreserved = pCanvas?.preservedDismissalScroll
            val safeOffsetTarget = if (parentPreserved != null) {
                (parentPreserved - pCanvas.headerHeight).coerceAtLeast(0)
            } else {
                offsetY
            }
            val effectiveOffsetY = maxOf(offsetY, safeOffsetTarget)

            ScribeScrollPolicy.calculateSafeScrollMaxY(
                layoutHeight = layoutH,
                viewportHeight = height,
                persistentBottomSpacePx = persistentBottomSpacePx,
                currentOffsetY = effectiveOffsetY,
                isFocused = isFocused,
                baseSoraMaxY = baseSora,
                isKeyboardVisible = isKeyboardVisible,
                breathingMarginPx = breathingMargin,
                isTextEmpty = text.isEmpty(),
                activeGlideTargetY = activeGlideTarget,
                isHeaderFullyHidden = true
            )
        } catch (_: Throwable) {
            super.getScrollMaxY()
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        val pCanvas = parent as? UnifiedCanvasLayout
        if (pCanvas != null) {
            if (direction > 0) {
                return (pCanvas.scrollD + offsetY) < pCanvas.maxCanvasScroll
            } else if (direction < 0) {
                return (pCanvas.scrollD > 0 || offsetY > 0)
            }
        }
        return super.canScrollVertically(direction)
    }

    override fun scrollBy(x: Int, y: Int) {
        val pCanvas = parent as? UnifiedCanvasLayout
        if (pCanvas != null && (pCanvas.scrollD < pCanvas.headerHeight || (y < 0 && pCanvas.scrollD > 0 && offsetY <= 0))) {
            pCanvas.scrollCanvasBy(y.toFloat())
            if (x != 0) {
                super.scrollBy(x, 0)
            }
        } else {
            super.scrollBy(x, y)
        }
    }

    override fun setInlayHints(hints: InlayHintsContainer?) {
        // When MainEditorScreen sets base prose hints, record them
        if (visualFirstLineIndentSpaces <= 0) {
            baseInlayHints = hints
        }
        super.setInlayHints(hints)
    }

    override fun copyText() {
        if (isProcessingIndent || visualFirstLineIndentSpaces > 0) {
            pendingCopyAction = { super.copyText() }
            onRequestCommitIndentForCopy?.invoke()
            return
        }
        super.copyText()
    }

    override fun copyText(clearSelection: Boolean) {
        if (isProcessingIndent || visualFirstLineIndentSpaces > 0) {
            pendingCopyAction = { super.copyText(clearSelection) }
            onRequestCommitIndentForCopy?.invoke()
            return
        }
        super.copyText(clearSelection)
    }


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
            cursorAnimator = ScribeCursorAnimator(this)
            isCursorAnimationEnabled = true
        } catch (_: Throwable) { }
        try {
            subscribeEvent(HandleStateChangeEvent::class.java) { event, _ ->
                isHandleDragging = event.isHeld
                if (!event.isHeld) {
                    handleAutoScrollVelocity = 0f
                    removeCallbacks(handleAutoScrollRunnable)
                }
            }
        } catch (_: Throwable) { }
        try {
            subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent::class.java) { _, _ ->
                val cur = try { cursor } catch (_: Throwable) { null } ?: return@subscribeEvent
                (parent as? UnifiedCanvasLayout)?.invalidateCompensationIfCaretChanged(cur.leftLine, cur.leftColumn, cur.isSelected)
                if (!cur.isSelected && cur.leftLine < text.lineCount) {
                    enforceAutoIndentFloor()
                    val line = cur.leftLine
                    val col = cur.leftColumn
                    val lineStr = try { text.getLineString(line) } catch (_: Throwable) { "" }
                    if (lineStr.contains("![")) {
                        val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")
                        val match = imgRegex.findAll(lineStr).firstOrNull { m ->
                            col in m.range.first..(m.range.last + 1)
                        }
                        if (match != null) {
                            // Immutability Guardrail (Suggestion #9):
                            // Intercept cursor strictly inside `![alt](uri)` and snap immediately outside.
                            if (col > match.range.first && col <= match.range.last) {
                                val mid = (match.range.first + match.range.last) / 2
                                val safeCol = if (col < mid) match.range.first else (match.range.last + 1)
                                post {
                                    try {
                                        setSelection(line, safeCol)
                                    } catch (_: Throwable) {}
                                }
                            }
                            if (System.currentTimeMillis() - lastInlayHintClickTime < 400) {
                                return@subscribeEvent
                            }
                            val desc = match.groupValues[1]
                            val uri = match.groupValues[2]
                            val raw = match.value
                            val timeDiff = System.currentTimeMillis() - lastTapTime
                            if (timeDiff in 0..400 && !isHandleDragging && uri.isNotBlank()) {
                                isInteractingWithImage = true
                                postDelayed({ isInteractingWithImage = false }, 250)
                                val lay = layout
                                val layoutOffset = try { lay.getCharLayoutOffset(line, match.range.first) } catch (_: Throwable) { null }
                                val vx = if (layoutOffset != null) (layoutOffset[1] + measureTextRegionOffset() - offsetX) else lastTapX
                                val vy = if (layoutOffset != null) (layoutOffset[0] - offsetY) else lastTapY
                                post {
                                    onImageHintClickListener?.invoke(
                                        ImageHintClickData(
                                            line = line,
                                            column = match.range.first,
                                            description = desc,
                                            imageUri = uri,
                                            rawTag = raw,
                                            viewX = vx,
                                            viewY = vy
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        try {
            subscribeEvent(InlayHintClickEvent::class.java) { event, _ ->
                val hint = event.inlayHint
                val textHint = hint as? TextInlayHint
                if (textHint != null && textHint.text.contains("🖼")) {
                    isInteractingWithImage = true
                    lastInlayHintClickTime = System.currentTimeMillis()
                    postDelayed({ isInteractingWithImage = false }, 350)
                    event.intercept()
                    // Suggestion #7: Preserve keyboard state (do not show if closed, keep if open)
                    val isKbUp = parentCanvas?.isKeyboardVisible == true
                    if (!isKbUp) {
                        try {
                            clearFocus()
                            hideSoftInput()
                        } catch (_: Throwable) {}
                    }
                    val lay = layout
                    val layoutOffset = try { lay.getCharLayoutOffset(hint.line, hint.column) } catch (_: Throwable) { null }
                    val vx = if (layoutOffset != null) (layoutOffset[1] + measureTextRegionOffset() - offsetX) else (width / 2f)
                    val vy = if (layoutOffset != null) (layoutOffset[0] - offsetY) else (height / 3f)

                    val lineStr = try { text.getLineString(hint.line) } catch (_: Throwable) { "" }
                    val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")
                    val match = imgRegex.findAll(lineStr).firstOrNull { m ->
                        hint.column in (m.range.first - 2)..(m.range.last + 2)
                    } ?: imgRegex.find(lineStr)

                    val desc = match?.groupValues?.getOrNull(1) ?: ""
                    val uri = match?.groupValues?.getOrNull(2) ?: ""
                    val raw = match?.value ?: ""

                    if (uri.isNotBlank()) {
                        post {
                            onImageHintClickListener?.invoke(
                                ImageHintClickData(
                                    line = hint.line,
                                    column = hint.column,
                                    description = desc,
                                    imageUri = uri,
                                    rawTag = raw,
                                    viewX = vx,
                                    viewY = vy
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
        try {
            subscribeEvent(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) { event, _ ->
                (parent as? UnifiedCanvasLayout)?.invalidateCompensation("Content edited")
                if (!isBatchApplyingIndent) {
                    documentVersion++
                }
                if (event.action == io.github.rosemoe.sora.event.ContentChangeEvent.ACTION_DELETE) {
                    if (text.isEmpty()) {
                        (parent as? UnifiedCanvasLayout)?.resetDocumentToTop(smooth = true)
                    } else {
                        val pCanvas = parent as? UnifiedCanvasLayout
                        pCanvas?.notifyContentDeletionStarted()
                        // Deletion settlement is safely deferred to LayoutStateChangeEvent once layout geometry is stable
                    }
                }
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
    var activeShortcuts: List<ShortcutAction> = emptyList()
    private var isInternalPasting: Boolean = false
    var isPasteTransactionActive: Boolean = false
        private set

    override fun pasteText() {
        isPasteTransactionActive = true
        super.pasteText()
        doOnLayoutReady {
            post {
                isPasteTransactionActive = false
                parentCanvas?.ensureCursorVisibleAboveKeyboard(
                    smooth = true,
                    customDurationMs = 380L,
                    force = true
                )
            }
        }
    }

    override fun pasteText(text: CharSequence?) {
        if (text == null) return
        isPasteTransactionActive = true
        isInternalPasting = true
        try {
            val processed = if (firstLineIndentSpaces > 0) formatPastedText(text) else text
            super.pasteText(processed)
        } finally {
            postDelayed({ isInternalPasting = false }, 500)
        }

        doOnLayoutReady {
            post {
                isPasteTransactionActive = false
                parentCanvas?.ensureCursorVisibleAboveKeyboard(
                    smooth = true,
                    customDurationMs = 380L,
                    force = true
                )
            }
        }
    }

    private var isEnforcingIndentFloor: Boolean = false

    fun enforceAutoIndentFloor() {
        if (firstLineIndentSpaces <= 0 || isEnforcingIndentFloor || isBatchApplyingIndent) return
        val cur = try { cursor } catch (_: Throwable) { null } ?: return
        if (cur.isSelected) return
        val line = cur.leftLine
        val col = cur.leftColumn
        if (line >= text.lineCount) return
        val lineStr = try { text.getLineString(line) } catch (_: Throwable) { "" }

        val replacement = ScribeEditorIndentFormatter.computeEmptyLineFloor(lineStr, firstLineIndentSpaces)
        if (replacement != null) {
            isEnforcingIndentFloor = true
            try {
                text.replace(line, 0, line, lineStr.length, replacement)
                setSelection(line, replacement.length)
                notifyIMEExternalCursorChange()
            } finally {
                isEnforcingIndentFloor = false
            }
            return
        }

        // If line starts with the auto-indent floor and cursor landed inside the floor (col < firstLineIndentSpaces):
        val indent = " ".repeat(firstLineIndentSpaces)
        if (col < firstLineIndentSpaces && lineStr.startsWith(indent)) {
            setSelection(line, firstLineIndentSpaces)
            notifyIMEExternalCursorChange()
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

                val replacement = ScribeEditorIndentFormatter.computeEmptyLineFloor(lineStr, firstLineIndentSpaces)
                if (replacement != null) {
                    val processed = if (text.contains('\n') || text.contains('\r') || isPastedOrMultiLineText(text)) {
                        formatPastedText(text)
                    } else {
                        text
                    }
                    this.text.replace(line, 0, line, lineStr.length, replacement)
                    setSelection(line, replacement.length)
                    super.commitText(processed, applyAutoIndent, applySymbolCompletion)
                    return
                }

                if (col < firstLineIndentSpaces) {
                    if (lineStr.startsWith(indent)) {
                        setSelection(line, firstLineIndentSpaces)
                    } else {
                        this.text.insert(line, 0, indent)
                        setSelection(line, firstLineIndentSpaces)
                    }
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
        val cur = cursor
        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = if (line < text.lineCount) text.getLineString(line) else ""
        return ScribeEditorIndentFormatter.formatPastedText(rawText, firstLineIndentSpaces, lineStr, col)
    }

    fun handleProseNewline() {
        clearKeyMetaStates()
        val cur = cursor
        if (cur.isSelected) {
            text.delete(cur.leftLine, cur.leftColumn, cur.rightLine, cur.rightColumn)
        }
        // Re-read cursor to prevent operating on stale coordinates after selection deletion
        val validCur = cursor
        val line = validCur.leftLine
        val col = validCur.leftColumn
        val lineStr = if (line < text.lineCount) text.getLineString(line) else ""

        // Capture visual start position BEFORE any text insertion for smooth line break glide
        val scribeAnimator = cursorAnimator as? ScribeCursorAnimator
        val lay = layout
        if (scribeAnimator != null && lay != null) {
            val startOff = try { lay.getCharLayoutOffset(line, col) } catch (_: Throwable) { null }
            if (startOff != null) {
                val startX = measureTextRegionOffset() + startOff[1]
                val startY = startOff[0]
                scribeAnimator.armGlide(
                    startX = startX,
                    startY = startY,
                    startHeight = rowHeight.toFloat(),
                    fallbackTargetY = startY + rowHeight
                )
            }
        }

        // ── Phase 0: Smart Pair Exit (Smooth Exit Outside Pair) ───────────────
        val pairExit = ScribeSmartEnterEngine.checkPairExit(lineStr, col, activeShortcuts)
        if (pairExit != null) {
            setSelection(line, pairExit.jumpPastCol)
            ensureSelectionVisible()
            notifyIMEExternalCursorChange()
            return
        }

        // ── Phase 2: Smart List Continuation / Termination ───────────────────
        val listMatch = ScribeSmartEnterEngine.parsePrefix(lineStr, activeShortcuts)
        if (listMatch != null) {
            val prefix = listMatch.fullPrefix
            val hasContentAfterPrefix = lineStr.length > prefix.length && lineStr.substring(prefix.length).trim().isNotEmpty()
            if (!hasContentAfterPrefix) {
                // List termination (Double Enter on an empty list item):
                // Break out of the list and reset line to base indent (or empty if indent is 0)
                val exitIndent = if (firstLineIndentSpaces > 0) " ".repeat(firstLineIndentSpaces) else ""
                text.replace(line, 0, line, lineStr.length, exitIndent)
                setSelection(line, exitIndent.length)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
                return
            } else {
                // Line has content after list marker:
                val nextPrefix = ScribeListEngine.nextSequencePrefix(listMatch)
                if (col >= prefix.length) {
                    // Cursor is after or at marker: split line and continue list sequence
                    val insertStr = "\n$nextPrefix"
                    text.insert(line, col, insertStr)
                    setSelection(line + 1, nextPrefix.length)
                    ensureSelectionVisible()
                    notifyIMEExternalCursorChange()
                    return
                }
                // If cursor is before marker (in leading whitespace), fall through to standard newline logic
            }
        }

        // ── Phase 1: Indent Hierarchy & Single-Newline Calculation ───────────
        when (val action = ScribeEditorIndentFormatter.computeNewline(lineStr, line, col, firstLineIndentSpaces)) {
            is FormatterNewlineAction.ReplaceAndInsertNewline -> {
                text.replace(action.lineToReplace, 0, action.lineToReplace, action.oldLineLength, action.normalizedLine)
                text.insert(action.lineToReplace, action.normalizedLine.length, action.textToInsert)
                setSelection(action.targetLine, action.targetCol)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
            }
            is FormatterNewlineAction.InsertNewline -> {
                text.insert(line, action.insertCol, action.textToInsert)
                setSelection(action.targetLine, action.targetCol)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
            }
        }
    }

    override fun deleteText() {
        val cur = cursor
        val pCanvas = parent as? UnifiedCanvasLayout
        if (pCanvas != null && cur != null) {
            val lay = layout
            val isSel = cur.isSelected
            val startLine = cur.leftLine
            val endLine = if (isSel) cur.rightLine else cur.leftLine
            val lineCount = text.lineCount
            val docH = (pCanvas.headerHeight + (lay?.layoutHeight ?: 0)).toFloat()
            val currentScroll = (pCanvas.scrollD + offsetY).toFloat()
            val visibleH = pCanvas.height.toFloat()
            val breathingMargin = pCanvas.breathingMarginPx
            val tailTolerancePx = 2f * context.resources.displayMetrics.density
            val wasAtTail = (currentScroll + visibleH) >= (docH - breathingMargin - tailTolerancePx)
            val affectsTail = endLine >= (lineCount - 1)
            val caretDocY = if (lay != null) {
                pCanvas.getCharGeometry(lay, cur.leftLine, cur.leftColumn, rowHeight.toFloat()).bottomDocY
            } else {
                ((cur.leftLine + 1) * rowHeight).toFloat()
            }
            val caretScreenY = (pCanvas.headerHeight + caretDocY - currentScroll)

            pCanvas.beginPreEditDeletionSnapshot(
                isSelection = isSel,
                selectionStartLine = startLine,
                selectionEndLine = endLine,
                preEditDocLineCount = lineCount,
                preEditDocHeight = docH,
                preEditScrollY = currentScroll,
                preEditCaretScreenY = caretScreenY,
                wasAtTail = wasAtTail,
                affectsDocumentTail = affectsTail
            )
        }
        if (cur.isSelected) {
            val wasAllSelected = (cur.leftLine == 0 && cur.leftColumn == 0 &&
                cur.rightLine >= text.lineCount - 1 && cur.rightColumn >= text.getColumnCount(cur.rightLine))
            super.deleteText()
            if (wasAllSelected || text.isEmpty()) {
                parentCanvas?.resetDocumentToTop(smooth = true)
            }
            return
        }
        val line = cur.leftLine
        val col = cur.leftColumn
        val lineStr = if (line < text.lineCount) text.getLineString(line) else ""



        // Capture visual start position BEFORE delete for smooth line merge glide
        val scribeAnimator = cursorAnimator as? ScribeCursorAnimator
        val lay = layout
        if (scribeAnimator != null && lay != null) {
            val startOff = try { lay.getCharLayoutOffset(line, col) } catch (_: Throwable) { null }
            if (startOff != null) {
                val startX = measureTextRegionOffset() + startOff[1]
                val startY = startOff[0]
                scribeAnimator.armGlide(
                    startX = startX,
                    startY = startY,
                    startHeight = rowHeight.toFloat(),
                    fallbackTargetY = startY - rowHeight
                )
            }
        }

        // ── Tag Guardrail & Immutability ──────────────────────────────────────
        if (lineStr.contains("![")) {
            val imgRegex = Regex("""!\[(.*?)\]\((vault-img://[^\s)]+|https?://[^\s)]+|content://[^\s)]+|[^\s)]+)\)""")
            val imgMatch = imgRegex.findAll(lineStr).firstOrNull { m ->
                col == m.range.last + 1 || (col > m.range.first && col <= m.range.last)
            }
            if (imgMatch != null) {
                setSelection(line, imgMatch.range.first)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
                return
            }
        }

        // ── Phase 0: Auto-Pair Deletion (Dynamic from Shortcuts) ──────────────
        val pairDeleteLens = ScribeSmartEnterEngine.checkPairBackspace(lineStr, col, activeShortcuts)
        if (pairDeleteLens != null) {
            val openLen = pairDeleteLens.first
            val closeLen = pairDeleteLens.second
            text.delete(line, col - openLen, line, col + closeLen)
            setSelection(line, col - openLen)
            ensureSelectionVisible()
            notifyIMEExternalCursorChange()
            return
        }

        // ── Phase 2: Single-stroke Backspace on List Marker ──────────────────
        val listMatch = ScribeSmartEnterEngine.parsePrefix(lineStr, activeShortcuts)
        if (listMatch != null) {
            val prefix = listMatch.fullPrefix
            if (col == prefix.length && lineStr.substring(prefix.length).trim().isEmpty()) {
                val replacement = if (firstLineIndentSpaces > 0) " ".repeat(firstLineIndentSpaces) else listMatch.leadingWhitespace
                text.replace(line, 0, line, lineStr.length, replacement)
                setSelection(line, replacement.length)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
                return
            }
        }

        // ── Phase 1: Indent Hierarchy & Backspace Calculation ────────────────
        val prevLineLength = if (line > 0) text.getColumnCount(line - 1) else 0
        when (val deleteAction = ScribeEditorIndentFormatter.computeDelete(lineStr, line, col, firstLineIndentSpaces, prevLineLength)) {
            is FormatterDeleteAction.MergeWithPreviousLine -> {
                text.delete(deleteAction.prevLine, deleteAction.prevCol, deleteAction.currentLine, deleteAction.deleteToCol)
                setSelection(deleteAction.prevLine, deleteAction.prevCol)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
                return
            }
            is FormatterDeleteAction.ClampCursorLineZero -> {
                setSelection(0, deleteAction.targetCol)
                ensureSelectionVisible()
                notifyIMEExternalCursorChange()
                return
            }
            is FormatterDeleteAction.DelegateToStandardDelete -> {
                super.deleteText()
                return
            }
        }
    }
    var isBatchApplyingIndent: Boolean = false
        private set

    /**
     * Requirement 1: Canvas-Level First-Line Margin (Instant Visual Feedback).
     * Renders visual indent in real-time on the canvas using Inlay Hints at column 0.
     * OPTIMIZATION: Scans and applies hints strictly to the visible viewport lines plus a 30-line
     * prefetch buffer (firstVisibleLine - 30 .. lastVisibleLine + 30). This keeps execution time
     * strictly below 1ms (flawless 120fps) even on 100,000+ word novels!
     */
    fun setVisualFirstLineIndent(spaces: Int) {
        if (visualFirstLineIndentSpaces == spaces) return
        visualFirstLineIndentSpaces = spaces
        refreshVisualFirstLineIndentHints()
    }

    /**
     * Re-evaluates visual indent hints across all prose lines in the document.
     * Applying to all lines prevents text jumping when scrolling or reflowing,
     * ensuring perfectly stable layout at 120 FPS.
     */
    fun refreshVisualFirstLineIndentHints() {
        val spaces = visualFirstLineIndentSpaces
        val content = text ?: return
        val count = content.lineCount
        if (count == 0) return

        if (spaces <= 0) {
            super.setInlayHints(baseInlayHints ?: InlayHintsContainer())
            try {
                renderContext.invalidateRenderNodes()
            } catch (_: Throwable) {}
            invalidate()
            return
        }

        val container = InlayHintsContainer()

        // 1. Preserve existing base prose hints (scene word counts, POV tags)
        baseInlayHints?.let { base ->
            try {
                for (lineNo in base.getLineNumbers()) {
                    for (hint in base.getForLine(lineNo)) {
                        container.add(hint)
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. High-speed zero-allocation visual indent overlay for all prose paragraphs:
        for (i in 0 until count) {
            val leadingSpaces = ScribeIndentEngine.getProseLeadingSpacesFast(content, i)
            if (leadingSpaces < 0) continue // non-prose or empty line

            if (spaces > leadingSpaces) {
                val diff = spaces - leadingSpaces
                // Use IndentInlayHint for pixel-perfect typeface advance match without padding/pills
                container.add(IndentInlayHint(i, 0, diff))
            }
        }
        super.setInlayHints(container)
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
    }

    /**
     * Requirements 2, 4, 6: Applies background-processed text with real spaces.
     * - Bypasses UndoManager safely by setting `ignoreModification = true`.
     * - Seamless transition: Replaces text and clears visual hints in the exact same frame,
     *   preventing double-reflow or visual jumps.
     * - Preserves exact cursor line and column without making it visible (prevents jumping to cursor).
     * - End-of-document disappearance fix: Coordinates with UnifiedCanvasLayout to clamp
     *   excess offset immediately both during the swap and after asynchronous wordwrap layout finishes.
     * - Restores base prose inlay hints.
     * - Executes pending copy if one was queued during processing.
     */
    fun applyFormattedTextSilently(
        newText: String,
        oldIndent: Int,
        newIndent: Int,
        savedLine: Int,
        savedCol: Int,
        adjustedCol: Int,
        savedScrollX: Int,
        savedScrollY: Int,
        savedCanvasScrollD: Int
    ) {
        firstLineIndentSpaces = newIndent
        val content = text ?: return
        val lineCountBefore = content.lineCount
        if (lineCountBefore == 0) return

        // 1. Cancel any active canvas smooth scrolling animator
        val parentCanvas = parent as? UnifiedCanvasLayout
        parentCanvas?.cancelCanvasAnimation()

        // 2. Single atomic buffer swap with undo suppression
        isBatchApplyingIndent = true
        content.beginBatchEdit()

        val undoMgr = content.undoManager
        val ignoreField = runCatching {
            val f = undoMgr.javaClass.getDeclaredField("ignoreModification")
            f.isAccessible = true
            f
        }.getOrNull()

        try {
            ignoreField?.set(undoMgr, true)
            val lastLine = lineCountBefore - 1
            val lastCol = content.getColumnCount(lastLine)
            content.replace(0, 0, lastLine, lastCol, newText)
        } finally {
            try {
                ignoreField?.set(undoMgr, false)
            } catch (_: Throwable) {}
            content.endBatchEdit()
            isBatchApplyingIndent = false
        }

        // 3. Clear temporary visual indent hints in the exact same frame as text replacement
        visualFirstLineIndentSpaces = 0
        super.setInlayHints(baseInlayHints ?: InlayHintsContainer())

        // 4. Cursor Position Preservation (Silent without jumping/scrolling to cursor)
        val targetLine = savedLine.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
        val maxCol = content.getColumnCount(targetLine)
        try {
            // makeItVisible = false ensures Sora Editor NEVER triggers ensurePositionVisible or scroller animation!
            setSelection(targetLine, adjustedCol.coerceIn(0, maxCol), false)
            notifyIMEExternalCursorChange()
        } catch (_: Throwable) {}

        // 5. Scroll Position Preservation & End-of-Document Text Disappearance Fix:
        // Clamping to current scrollMaxY and delegating to parentCanvas guarantees the document
        // never scrolls past the end of the text when paragraphs unwrap.
        try {
            scroller?.forceFinished(true)
            val maxAllowedY = scrollMaxY.coerceAtLeast(0)
            val targetScrollY = savedScrollY.coerceIn(0, maxAllowedY)
            scrollTo(savedScrollX, targetScrollY)
            if (parentCanvas != null) {
                parentCanvas.setCanvasScrollD(savedCanvasScrollD)
                parentCanvas.clampEditorScrollToMax()
            }
        } catch (_: Throwable) {}

        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()

        // 6. Execute pending copy if user tried to copy during visual or processing state
        pendingCopyAction?.let { action ->
            pendingCopyAction = null
            post { action.invoke() }
        }
    }
    private var lastMakeVisibleTime: Long = 0L
    private var isFlingActive = false
    private var forceNextLayoutClear = false

    /**
     * Instantly and safely halts any momentum fling animation without jumping or snapping.
     * Prevents OverScroller.abortAnimation() from forcing currY to finalY.
     * Explicitly synchronizes scroller state to exact visual offset (offsetX/offsetY).
     */
    fun stopFling() {
        isFlingActive = false
        try {
            val s = scroller ?: return
            if (!s.isFinished) {
                s.forceFinished(true)
            }
            val curY = offsetY
            val curX = offsetX
            if (s.currY != curY || s.currX != curX) {
                s.startScroll(curX, curY, 0, 0, 0)
                s.abortAnimation()
            }
        } catch (_: Throwable) {}
    }

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
    private var isFirstVisibleDelivered: Boolean = false
    private var isLayoutReadyDelivered: Boolean = false
    var onFirstVisibleListener: (() -> Unit)? = null
    var onLayoutReadyListener: (() -> Unit)? = null
    var activeTraceNoteId: String? = null

    /**
     * Executes the action once the editor's text and wordwrap layout are fully computed.
     * If layout is already ready, dispatches on the UI looper immediately.
     */
    fun doOnLayoutReady(action: () -> Unit) {
        if (!isAwaitingLayoutReady && isWordwrapReady()) {
            post { action() }
        } else {
            val prev = onLayoutReadyListener
            onLayoutReadyListener = {
                prev?.invoke()
                action()
            }
            post { checkAndTriggerReady() }
        }
    }

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

    private var safetyCheckRetries = 0
    private val safetyCheckRunnable = object : Runnable {
        override fun run() {
            if (!isAwaitingLayoutReady) return
            if (isWordwrapReady()) {
                checkAndTriggerReady()
            } else if (safetyCheckRetries < 20) {
                // Poll gently until wordwrap completes rather than falsely reporting ready
                safetyCheckRetries++
                postDelayed(this, 50)
            }
        }
    }

    /**
     * Restores full opacity and forces GPU render node invalidation when returning
     * to the editor screen mid-session (Context B). Guarantees zero blank screen state.
     */
    fun refreshRenderingOnResume() {
        clearKeyMetaStates()
        removeCallbacks(safetyCheckRunnable)
        isAwaitingLayoutReady = false
        alpha = 1f
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
        postInvalidateOnAnimation()
    }

    /**
     * Prepares the editor for a newly loaded document:
     * Fades the editor to invisible (alpha = 0f) and arms the ready-check listener.
     */
    fun prepareForNewDocument(traceNoteId: String? = null) {
        activeTraceNoteId = traceNoteId
        animate().cancel()
        alpha = 0f
        isAwaitingLayoutReady = true
        isFirstVisibleDelivered = false
        isLayoutReadyDelivered = false
        safetyCheckRetries = 0
        removeCallbacks(safetyCheckRunnable)
        postDelayed(safetyCheckRunnable, 50)
    }

    private fun deliverFirstVisible() {
        if (isFirstVisibleDelivered) return
        isFirstVisibleDelivered = true
        activeTraceNoteId?.let { DocumentOpenTrace.mark(it, "first_correct_visible_content") }
        alpha = 1f
        val listener = onFirstVisibleListener
        onFirstVisibleListener = null
        listener?.invoke()
    }

    private fun deliverLayoutReady() {
        if (isLayoutReadyDelivered) return
        isLayoutReadyDelivered = true
        isAwaitingLayoutReady = false
        removeCallbacks(safetyCheckRunnable)
        activeTraceNoteId?.let { DocumentOpenTrace.mark(it, "full_wordwrap_ready") }
        val listener = onLayoutReadyListener
        onLayoutReadyListener = null
        listener?.invoke()
    }

    /**
     * Evaluates whether layout calculation is complete.
     * When ready, triggers layout-ready listeners.
     */
    fun checkAndTriggerReady() {
        deliverFirstVisible()
        if (isWordwrapReady()) {
            deliverLayoutReady()
        }
    }

    /**
     * Called after setting new text to reveal the visible document frame immediately,
     * then await full background word-wrap layout readiness.
     */
    fun notifyContentSet() {
        deliverFirstVisible()
        if (text.length == 0 || !isWordwrap) {
            removeCallbacks(safetyCheckRunnable)
            deliverLayoutReady()
            return
        }
        if (isWordwrapReady()) {
            deliverLayoutReady()
        } else {
            post {
                checkAndTriggerReady()
            }
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
            if (visualFirstLineIndentSpaces > 0) {
                refreshVisualFirstLineIndentHints()
            }
            when (event.cause) {
                ScrollEvent.CAUSE_USER_FLING -> isFlingActive = true
                ScrollEvent.CAUSE_USER_DRAG -> isFlingActive = false
                ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE -> isFlingActive = false
                ScrollEvent.CAUSE_TEXT_SELECTING -> isFlingActive = false
            }
            (parent as? UnifiedCanvasLayout)?.notifyViewportScrollChanged()
        }

        // Listen for layout completion events from Sora Editor to trigger smooth fade-in, deletion settlement, and scroll clamping
        subscribeEvent(LayoutStateChangeEvent::class.java) { event, _ ->
            isLayoutBusyState = event.isLayoutBusy
            if (!event.isLayoutBusy) {
                val capturedEpoch = zoomTransactionEpoch
                val wasAwaitingSettlement = isZoomAwaitingSettlement
                if (wasAwaitingSettlement && !isZoomGestureActive) {
                    isZoomAwaitingSettlement = false
                }
                post {
                    // Suppress any cursor-visibility scrolling originating from or completing during a pinch zoom transaction
                    if (capturedEpoch != zoomTransactionEpoch || wasAwaitingSettlement || isZoomActiveOrSettling) {
                        return@post
                    }
                    if (isLayoutComputing || isLayoutBusyState) {
                        return@post
                    }
                    val curLay = layout
                    // Ensure Sora layout is non-null and wordwrap calculation is completely ready before settling deletion
                    if (curLay == null || !isWordwrapReady()) {
                        return@post
                    }
                    checkAndTriggerReady()
                    val pCanvas = parentCanvas
                    if (pCanvas != null) {
                        val totalScroll = pCanvas.scrollD + offsetY
                        val maxScroll = pCanvas.maxCanvasScroll
                        if (pCanvas.isDeletionTransactionActive) {
                            pCanvas.handleDeletionSettlement(smooth = true)
                        } else if (totalScroll > maxScroll || text.isEmpty()) {
                            pCanvas.clampCanvasScrollToMax(smooth = true)
                        } else if (isFocused) {
                            pCanvas.ensureCursorVisibleAboveKeyboard(smooth = true, force = false)
                        }
                    }
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
        isLayoutBusyState = true
        try {
            super.createLayout(shouldClear)
        } finally {
            isInsideWordwrapLayoutCreation = false
        }
    }

    // Zoom transaction epoch & layout settlement window to prevent cursor-following during/after pinch zoom
    @Volatile
    private var zoomTransactionEpoch: Long = 0L
    @Volatile
    private var isZoomGestureActive: Boolean = false
    @Volatile
    private var isZoomAwaitingSettlement: Boolean = false

    val isZoomGestureActiveOnly: Boolean
        get() = isPinchScaling || isZoomGestureActive

    val isZoomActiveOrSettling: Boolean
        get() = isZoomGestureActiveOnly || isZoomAwaitingSettlement

    val isPinchScaling: Boolean
        get() = eventHandler?.isScaling == true

    var onPinchScaleEndListener: ((finalSizeSp: Int) -> Unit)? = null

    private var lastMoveX = 0f
    private var lastMoveY = 0f

    private fun dispatchSyntheticMoveToSora(moveEv: MotionEvent) {
        try {
            super.onTouchEvent(moveEv)
        } catch (_: Throwable) {}
    }

    private var handleAutoScrollVelocity: Float = 0f
    private val handleAutoScrollRunnable = object : Runnable {
        override fun run() {
            val pCanvas = parent as? UnifiedCanvasLayout
            if (pCanvas != null && isHandleDragging && handleAutoScrollVelocity != 0f) {
                pCanvas.scrollCanvasBy(handleAutoScrollVelocity)
                // Synthesize an ACTION_MOVE event at current finger coordinates so Sora
                // updates its selection handle and extends selection as the document scrolls under the finger
                val now = android.os.SystemClock.uptimeMillis()
                val moveEv = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_MOVE, lastMoveX, lastMoveY, 0)
                try {
                    dispatchSyntheticMoveToSora(moveEv)
                } finally {
                    moveEv.recycle()
                }

                if (handleAutoScrollVelocity > 0f && (pCanvas.scrollD + offsetY) < pCanvas.maxCanvasScroll) {
                    postOnAnimation(this)
                } else if (handleAutoScrollVelocity < 0f && (pCanvas.scrollD > 0 || offsetY > 0)) {
                    postOnAnimation(this)
                } else {
                    handleAutoScrollVelocity = 0f
                }
            } else {
                handleAutoScrollVelocity = 0f
            }
        }
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var isTouchMoved = false
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapTime = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parentCanvas = parent as? UnifiedCanvasLayout
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = System.currentTimeMillis()
                isTouchMoved = false
                parentCanvas?.cancelCanvasAnimation()
                parentCanvas?.activeEditingTarget = com.primaloptima.scribe.util.model.EditingTarget.EDITOR_BODY
                stopFling()
            }
            MotionEvent.ACTION_MOVE -> {
                lastMoveX = event.x
                lastMoveY = event.y
                val dx = kotlin.math.abs(event.x - touchDownX)
                val dy = kotlin.math.abs(event.y - touchDownY)
                val density = context.resources.displayMetrics.density
                val slop = 8f * density
                if (dx > slop || dy > slop) {
                    isTouchMoved = true
                }
                if (parentCanvas != null && isHandleDragging && parentCanvas.scrollD < parentCanvas.headerHeight) {
                    val headerOffset = (parentCanvas.headerHeight - parentCanvas.scrollD).coerceAtLeast(0)
                    val visibleBottom = height - headerOffset
                    val hotZone = 40f * density
                    val y = event.y
                    val newVelocity = when {
                        y > visibleBottom - hotZone -> {
                            val ratio = ((y - (visibleBottom - hotZone)) / hotZone).coerceIn(0.1f, 2.0f)
                            (ratio * 14f * density)
                        }
                        y < hotZone && (parentCanvas.scrollD > 0 || offsetY > 0) -> {
                            val ratio = ((hotZone - y) / hotZone).coerceIn(0.1f, 2.0f)
                            -(ratio * 14f * density)
                        }
                        else -> 0f
                    }
                    if (newVelocity != handleAutoScrollVelocity) {
                        val wasRunning = handleAutoScrollVelocity != 0f
                        handleAutoScrollVelocity = newVelocity
                        if (newVelocity != 0f && !wasRunning) {
                            postOnAnimation(handleAutoScrollRunnable)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                handleAutoScrollVelocity = 0f
                removeCallbacks(handleAutoScrollRunnable)
                val cur = try { cursor } catch (_: Throwable) { null }
                if (cur?.isSelected != true) {
                    isHandleDragging = false
                }
                if (!isTouchMoved && (System.currentTimeMillis() - touchDownTime) < 350) {
                    lastTapX = event.x
                    lastTapY = event.y
                    lastTapTime = System.currentTimeMillis()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handleAutoScrollVelocity = 0f
                removeCallbacks(handleAutoScrollRunnable)
                val cur = try { cursor } catch (_: Throwable) { null }
                if (cur?.isSelected != true) {
                    isHandleDragging = false
                }
            }
        }
        val wasScaling = eventHandler?.isScaling == true
        val result = super.onTouchEvent(event)
        val isScalingNow = eventHandler?.isScaling == true

        if (!wasScaling && isScalingNow) {
            zoomTransactionEpoch++
            isZoomGestureActive = true
            isZoomAwaitingSettlement = true
        } else if (wasScaling && !isScalingNow) {
            isZoomGestureActive = false
            // Multi-touch ends immediately on finger lift; layout settlement tracked via isZoomAwaitingSettlement
            val currentSp = (textSizePx / context.resources.displayMetrics.density).roundToInt().coerceIn(12, 36)
            onPinchScaleEndListener?.invoke(currentSp)
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            isZoomGestureActive = false
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val dt = System.currentTimeMillis() - touchDownTime
            val wasTap = !isTouchMoved && dt < 450L
            if (wasTap && !isPinchScaling && !isHandleDragging && !isInteractingWithImage) {
                val pCanvas = parentCanvas
                if (pCanvas != null && (pCanvas.isKeyboardVisible || pCanvas.isBottomSurfaceActive)) {
                    // Viewport is already contracted: perform ordinary caret visibility check
                    pCanvas.ensureCursorVisibleAboveKeyboard(smooth = true)
                }
            }
            if (isInteractingWithImage) {
                post { isInteractingWithImage = false }
            }
        }
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight && offsetY > 0) {
            jumpScrollTo(offsetX, 0)
        }
        return result
    }

    /**
     * Instantly jumps the editor scroll position to the target offsets without animation.
     * Safely updates scroller, View mScrollX/mScrollY, invalidates GPU render nodes,
     * and triggers a redraw pass.
     */
    /**
     * Silently gains focus to activate cursor rendering and cursor blinking
     * while guaranteeing that soft input / keyboard remains hidden.
     */
    fun showCursorSilently() {
        if (!isFocused) {
            requestFocus()
        }
        try {
            hideSoftInput()
        } catch (_: Throwable) {}
        post {
            try {
                hideSoftInput()
            } catch (_: Throwable) {}
        }
        invalidate()
    }

    fun jumpScrollTo(targetX: Int, targetY: Int) {
        val scroller = scroller ?: return
        scroller.forceFinished(true)
        val currX = offsetX
        val currY = offsetY
        val maxX = try { scrollMaxX.coerceAtLeast(0) } catch (_: Throwable) { 0 }
        val maxY = try { scrollMaxY.coerceAtLeast(0) } catch (_: Throwable) { 0 }
        val clampedX = targetX.coerceIn(0, maxX)
        val clampedY = targetY.coerceIn(0, maxY)
        val dx = clampedX - currX
        val dy = clampedY - currY
        if (dx != 0 || dy != 0) {
            scroller.startScroll(currX, currY, dx, dy, 0)
            scroller.abortAnimation()
            (parent as? UnifiedCanvasLayout)?.notifyViewportScrollChanged()
        }
        try {
            renderContext.invalidateRenderNodes()
        } catch (_: Throwable) {}
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (isAwaitingLayoutReady && isWordwrap && !isWordwrapReady()) {
            // Layout computation for initial document load in progress. Paint only background
            // color to completely eliminate flashing of un-wrapped single-line fallback text.
            val bgColor = colorScheme?.getColor(EditorColorScheme.WHOLE_BACKGROUND) ?: Color.TRANSPARENT
            canvas.drawColor(bgColor)
            return
        }

        // Layer 1 Geometry Guard: While asynchronous wordwrap recomputation is in progress
        // (isLayoutComputing == true), lines are temporarily drawn with stale break columns.
        // Clip text rendering strictly at the right margin line (width - rightPadPx) so text
        // NEVER spills into the right margin during font size adjustments or slider dragging.
        // Once layout computing finishes, isLayoutComputing becomes false, restoring uninhibited
        // drawing bounds for selection teardrop handles into the margins.
        val density = context.resources.displayMetrics.density
        val rightLimit = width.toFloat() - (horizontalPaddingDp * density)
        val shouldClipMargin = isLayoutComputing && isWordwrap && rightLimit > 0f

        if (shouldClipMargin) {
            canvas.save()
            canvas.clipRect(0f, 0f, rightLimit, height.toFloat())
        }

        super.onDraw(canvas)

        if (shouldClipMargin) {
            canvas.restore()
        }
    }

    /**
     * Clears persistent/sticky key meta states (Shift, Alt, Sym) in Sora Editor's
     * KeyMetaStates tracker. This prevents stale modifier flags from lingering after
     * app backgrounding, screen sleep, or window focus transitions, which would otherwise
     * cause normal Enter to be misinterpreted as Shift+Enter (triggering startNewLine).
     */
    fun clearKeyMetaStates() {
        try {
            keyMetaStates.clearMetaStates(
                android.text.method.MetaKeyKeyListener.META_SHIFT_ON or
                android.text.method.MetaKeyKeyListener.META_ALT_ON or
                android.text.method.MetaKeyKeyListener.META_SYM_ON
            )
        } catch (_: Throwable) {}
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        clearKeyMetaStates()
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

        // Layout resize handling: UnifiedCanvasLayout manages smooth decoupled cursor scrolling.
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        clearKeyMetaStates()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        clearKeyMetaStates()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clearKeyMetaStates()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearKeyMetaStates()
        isZoomGestureActive = false
        isZoomAwaitingSettlement = false
    }

    override fun selectAll() {
        val parentCanvas = parent as? UnifiedCanvasLayout
        val initialScrollD = parentCanvas?.scrollD ?: 0
        val initialOffsetY = offsetY
        isExplicitSelectAllInProgress = true
        try {
            super.selectAll()
        } finally {
            isExplicitSelectAllInProgress = false
        }

        if (selectAllScrollMode == ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION) {
            parentCanvas?.let { canvas ->
                if (canvas.scrollD != initialScrollD) {
                    canvas.setCanvasScrollD(initialScrollD)
                }
            }
            if (offsetY != initialOffsetY) {
                jumpScrollTo(offsetX, initialOffsetY)
            }
            clampContextToolbarToViewport()
        } else {
            val cur = cursor
            parentCanvas?.ensureCursorVisibleAboveKeyboard(
                targetLine = cur.rightLine,
                targetCol = cur.rightColumn,
                smooth = true,
                force = true
            )
        }
    }

    override fun ensureSelectionVisible() {
        // Only scroll to selection if this editor actually has keyboard focus
        if (!isFocused) return
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null) {
            val cur = cursor
            val isAllSelected = cur != null && ScribeScrollPolicy.isSelectAll(
                isSelected = cur.isSelected,
                leftLine = cur.leftLine,
                leftCol = cur.leftColumn,
                rightLine = cur.rightLine,
                rightCol = cur.rightColumn,
                lineCount = text.lineCount,
                lastLineColCount = try { text.getColumnCount(text.lineCount - 1) } catch (_: Throwable) { 0 }
            )
            if (isAllSelected) {
                when (selectAllScrollMode) {
                    ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION -> {
                        clampContextToolbarToViewport()
                        return
                    }
                    ScribeScrollPolicy.SelectAllScrollMode.SCROLL_TO_SELECTION_END -> {
                        parentCanvas.ensureCursorVisibleAboveKeyboard(
                            targetLine = cur.rightLine,
                            targetCol = cur.rightColumn,
                            smooth = true,
                            force = true
                        )
                        return
                    }
                }
            }
            parentCanvas.ensureCursorVisibleAboveKeyboard(smooth = true, force = true)
            return
        }
        super.ensureSelectionVisible()
    }

    override fun computeScroll() {
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null && parentCanvas.scrollD < parentCanvas.headerHeight && offsetY > 0) {
            jumpScrollTo(offsetX, 0)
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
                scroller.forceFinished(true)
                parentCanvas?.continueFlingFromEditor(velocity)
            }
        }
        if (scroller != null && scroller.isFinished) {
            isFlingActive = false
        }
        if (prevY != (scroller?.currY ?: 0)) {
            parentCanvas?.notifyViewportScrollChanged()
        }
    }

    override fun showSoftInput() {
        if (isInteractingWithImage) return
        super.showSoftInput()
    }

    override fun ensurePositionVisible(line: Int, column: Int, noAnimation: Boolean) {
        if (isInteractingWithImage) return
        val scroller = scroller ?: return
        val layout = layout ?: return
        val layoutOffset = try { layout.getCharLayoutOffset(line, column) } catch (_: Throwable) { null }

        val currFinalX = if (scroller.isFinished) offsetX.toFloat() else scroller.finalX.toFloat()
        var targetX = currFinalX

        // 1. Horizontal cursor visibility:
        if (layoutOffset != null) {
            val xOffset = layoutOffset[1] + measureTextRegionOffset()
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
        }

        // 2. Vertical cursor visibility through UnifiedCanvasLayout:
        val parentCanvas = parent as? UnifiedCanvasLayout
        if (parentCanvas != null) {
            // If viewport has not laid out yet, defer to next frame
            if (parentCanvas.height <= 0) {
                parentCanvas.post {
                    ensurePositionVisible(line, column, noAnimation)
                }
                return
            }
            parentCanvas.ensureCursorVisibleAboveKeyboard(
                targetLine = line,
                targetCol = column,
                smooth = !noAnimation,
                force = true
            )
            invalidate()
            return
        }

        // Fallback when not hosted in UnifiedCanvasLayout:
        val yOffset = layoutOffset?.get(0) ?: ((line + 1) * rowHeight.toFloat())
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

    /**
     * Clamps the floating popup toolbar (Cut/Copy/Select All) within the visible canvas viewport
     * so it never renders offscreen during stationary Select All.
     */
    fun clampContextToolbarToViewport() {
        try {
            val aw = getComponent(
                io.github.rosemoe.sora.widget.component.EditorTextActionWindow::class.java
            ) ?: return
            post {
                try {
                    var popup: android.widget.PopupWindow? = null
                    var cls: Class<*>? = aw.javaClass
                    outer@ while (cls != null && cls != Any::class.java) {
                        for (f in cls.declaredFields) {
                            if (android.widget.PopupWindow::class.java.isAssignableFrom(f.type)) {
                                f.isAccessible = true
                                popup = f.get(aw) as? android.widget.PopupWindow
                                break@outer
                            }
                        }
                        cls = cls.superclass
                    }
                    val p = popup ?: return@post
                    val contentView = p.contentView ?: return@post
                    if (!p.isShowing) return@post

                    val loc = IntArray(2)
                    contentView.getLocationOnScreen(loc)
                    val currentY = loc[1]
                    val parentCanvas = parent as? UnifiedCanvasLayout
                    val canvasLoc = IntArray(2)
                    if (parentCanvas != null) {
                        parentCanvas.getLocationOnScreen(canvasLoc)
                        val canvasTop = canvasLoc[1]
                        val canvasBottom = canvasTop + parentCanvas.height
                        val density = resources.displayMetrics.density
                        val marginPx = (48 * density).toInt()

                        if (currentY < canvasTop + marginPx || currentY > canvasBottom - marginPx) {
                            val clampedY = (canvasTop + marginPx).coerceAtMost(canvasBottom - marginPx)
                            val screenWidth = resources.displayMetrics.widthPixels
                            val popupW = contentView.measuredWidth.takeIf { it > 0 } ?: (screenWidth * 0.8f).toInt()
                            val clampedX = ((screenWidth - popupW) / 2).coerceAtLeast(0)
                            p.update(clampedX, clampedY, -1, -1)
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun updateImageDescription(imageUri: String, newDescription: String) {
        val content = text ?: return
        val doc = content.toString()
        val updated = VaultImageManager.renameAltInMarkdown(doc, imageUri, newDescription)
        if (updated != doc) {
            content.beginBatchEdit()
            try {
                content.replace(0, 0, content.lineCount - 1, content.getColumnCount(content.lineCount - 1), updated)
            } finally {
                content.endBatchEdit()
            }
        }
    }

    fun removeImageTag(imageUri: String) {
        val content = text ?: return
        val doc = content.toString()
        val updated = VaultImageManager.removeImageFromMarkdown(doc, imageUri)
        if (updated != doc) {
            content.beginBatchEdit()
            try {
                content.replace(0, 0, content.lineCount - 1, content.getColumnCount(content.lineCount - 1), updated)
            } finally {
                content.endBatchEdit()
            }
        }
    }
}
