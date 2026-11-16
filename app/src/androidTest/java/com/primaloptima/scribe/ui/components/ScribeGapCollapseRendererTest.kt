package com.primaloptima.scribe.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.primaloptima.scribe.CrashActivity
import com.primaloptima.scribe.R
import com.primaloptima.scribe.ui.editor.scroll.CollapsePlan
import com.primaloptima.scribe.ui.editor.scroll.CollapseScenario
import com.primaloptima.scribe.ui.editor.scroll.GapCollapsePhase
import com.primaloptima.scribe.ui.editor.scroll.SafetyFloorAndBounds
import com.primaloptima.scribe.ui.editor.scroll.ScribeGapCollapseController
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.lang.brackets.BracketsProvider
import io.github.rosemoe.sora.lang.brackets.PairedBracket
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.line.LineGutterBackground
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.style.CursorAnimator
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScribeGapCollapseRendererTest {

    private data class HostHandle(
        val scenario: ActivityScenario<CrashActivity>,
        val container: FrameLayout,
        val canvas: UnifiedCanvasLayout
    )

    private data class RecordedTextOp(
        val text: String,
        val localX: Float,
        val localY: Float,
        val screenX: Float,
        val screenY: Float,
        val translationY: Float,
        val color: Int,
        val clipScreen: RectF
    )

    private data class RecordedLineOp(
        val startX: Float,
        val startY: Float,
        val stopX: Float,
        val stopY: Float,
        val screenX: Float,
        val screenStartY: Float,
        val screenStopY: Float,
        val translationY: Float,
        val color: Int,
        val clipScreen: RectF
    )

    private data class RecordedRectOp(
        val screenRect: RectF,
        val translationY: Float,
        val color: Int,
        val style: Paint.Style,
        val clipScreen: RectF
    )

    private data class RecordedPathOp(
        val screenBounds: RectF,
        val translationY: Float,
        val color: Int,
        val style: Paint.Style,
        val clipScreen: RectF
    )

    private class RecordingCanvas(
        width: Int,
        height: Int
    ) : Canvas(Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)) {

        private data class CanvasState(
            val count: Int,
            val tx: Float,
            val ty: Float,
            val clip: RectF
        )

        private val stack = ArrayDeque<CanvasState>()
        private var currentTx = 0f
        private var currentTy = 0f
        private var currentClip = RectF(0f, 0f, width.toFloat(), height.toFloat())
        var maxStackDepth = 0
            private set

        val textOps = mutableListOf<RecordedTextOp>()
        val lineOps = mutableListOf<RecordedLineOp>()
        val rectOps = mutableListOf<RecordedRectOp>()
        val pathOps = mutableListOf<RecordedPathOp>()

        override fun save(): Int {
            val count = super.save()
            stack.addLast(CanvasState(count, currentTx, currentTy, RectF(currentClip)))
            if (stack.size > maxStackDepth) maxStackDepth = stack.size
            return count
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
            val count = super.saveLayer(bounds, paint)
            stack.addLast(CanvasState(count, currentTx, currentTy, RectF(currentClip)))
            if (bounds != null) {
                intersectClipScreen(bounds.left, bounds.top, bounds.right, bounds.bottom)
            }
            if (stack.size > maxStackDepth) maxStackDepth = stack.size
            return count
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int {
            val count = super.saveLayer(left, top, right, bottom, paint)
            stack.addLast(CanvasState(count, currentTx, currentTy, RectF(currentClip)))
            intersectClipScreen(left, top, right, bottom)
            if (stack.size > maxStackDepth) maxStackDepth = stack.size
            return count
        }

        override fun restore() {
            super.restore()
            val popped = stack.removeLastOrNull() ?: return
            currentTx = popped.tx
            currentTy = popped.ty
            currentClip = popped.clip
        }

        override fun restoreToCount(saveCount: Int) {
            super.restoreToCount(saveCount)
            while (stack.isNotEmpty() && stack.last().count >= saveCount) {
                val popped = stack.removeLast()
                currentTx = popped.tx
                currentTy = popped.ty
                currentClip = popped.clip
            }
        }

        override fun translate(dx: Float, dy: Float) {
            super.translate(dx, dy)
            currentTx += dx
            currentTy += dy
        }

        private fun intersectClipScreen(left: Float, top: Float, right: Float, bottom: Float) {
            val sLeft = left + currentTx
            val sTop = top + currentTy
            val sRight = right + currentTx
            val sBottom = bottom + currentTy
            if (!currentClip.intersect(sLeft, sTop, sRight, sBottom)) {
                currentClip.setEmpty()
            }
        }

        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
            intersectClipScreen(left, top, right, bottom)
            return super.clipRect(left, top, right, bottom)
        }

        override fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean {
            intersectClipScreen(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            return super.clipRect(left, top, right, bottom)
        }

        override fun clipRect(rect: RectF): Boolean {
            intersectClipScreen(rect.left, rect.top, rect.right, rect.bottom)
            return super.clipRect(rect)
        }

        override fun clipRect(rect: Rect): Boolean {
            intersectClipScreen(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
            return super.clipRect(rect)
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            textOps.add(
                RecordedTextOp(
                    text = text,
                    localX = x,
                    localY = y,
                    screenX = x + currentTx,
                    screenY = y + currentTy,
                    translationY = currentTy,
                    color = paint.color,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawText(text, x, y, paint)
        }

        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
            textOps.add(
                RecordedTextOp(
                    text = String(text, index, count),
                    localX = x,
                    localY = y,
                    screenX = x + currentTx,
                    screenY = y + currentTy,
                    translationY = currentTy,
                    color = paint.color,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawText(text, index, count, x, y, paint)
        }

        override fun drawTextRun(
            text: CharArray,
            index: Int,
            count: Int,
            contextIndex: Int,
            contextCount: Int,
            x: Float,
            y: Float,
            isRtl: Boolean,
            paint: Paint
        ) {
            textOps.add(
                RecordedTextOp(
                    text = String(text, index, count),
                    localX = x,
                    localY = y,
                    screenX = x + currentTx,
                    screenY = y + currentTy,
                    translationY = currentTy,
                    color = paint.color,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint)
        }

        override fun drawTextRun(
            text: CharSequence,
            start: Int,
            end: Int,
            contextStart: Int,
            contextEnd: Int,
            x: Float,
            y: Float,
            isRtl: Boolean,
            paint: Paint
        ) {
            textOps.add(
                RecordedTextOp(
                    text = text.subSequence(start, end).toString(),
                    localX = x,
                    localY = y,
                    screenX = x + currentTx,
                    screenY = y + currentTy,
                    translationY = currentTy,
                    color = paint.color,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            lineOps.add(
                RecordedLineOp(
                    startX = startX,
                    startY = startY,
                    stopX = stopX,
                    stopY = stopY,
                    screenX = startX + currentTx,
                    screenStartY = startY + currentTy,
                    screenStopY = stopY + currentTy,
                    translationY = currentTy,
                    color = paint.color,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawLine(startX, startY, stopX, stopY, paint)
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            rectOps.add(
                RecordedRectOp(
                    screenRect = RectF(left + currentTx, top + currentTy, right + currentTx, bottom + currentTy),
                    translationY = currentTy,
                    color = paint.color,
                    style = paint.style,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawRect(left, top, right, bottom, paint)
        }

        override fun drawRect(r: RectF, paint: Paint) {
            drawRect(r.left, r.top, r.right, r.bottom, paint)
        }

        override fun drawRect(r: Rect, paint: Paint) {
            drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)
        }

        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
            rectOps.add(
                RecordedRectOp(
                    screenRect = RectF(rect.left + currentTx, rect.top + currentTy, rect.right + currentTx, rect.bottom + currentTy),
                    translationY = currentTy,
                    color = paint.color,
                    style = paint.style,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawRoundRect(rect, rx, ry, paint)
        }

        override fun drawRoundRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            rx: Float,
            ry: Float,
            paint: Paint
        ) {
            rectOps.add(
                RecordedRectOp(
                    screenRect = RectF(left + currentTx, top + currentTy, right + currentTx, bottom + currentTy),
                    translationY = currentTy,
                    color = paint.color,
                    style = paint.style,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawRoundRect(left, top, right, bottom, rx, ry, paint)
        }

        override fun drawColor(color: Int) {
            rectOps.add(
                RecordedRectOp(
                    screenRect = RectF(currentClip),
                    translationY = currentTy,
                    color = color,
                    style = Paint.Style.FILL,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawColor(color)
        }

        override fun drawPath(path: Path, paint: Paint) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            bounds.offset(currentTx, currentTy)
            pathOps.add(
                RecordedPathOp(
                    screenBounds = bounds,
                    translationY = currentTy,
                    color = paint.color,
                    style = paint.style,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawPath(path, paint)
        }

        fun assertBalancedAndZeroTranslation() {
            assertTrue("Canvas save/restore stack must balance after draw (remaining=${stack.size})", stack.isEmpty())
            assertEquals("Canvas translationX must return to 0 after draw", 0f, currentTx, 0.01f)
            assertEquals("Canvas translationY must return to 0 after draw", 0f, currentTy, 0.01f)
        }
    }

    private fun launchHost(
        text: String,
        wordwrap: Boolean = true,
        lineNumbers: Boolean = true,
        firstLnAlwaysVisible: Boolean = true,
        pinnedLineNumbers: Boolean = false,
        headerHeightPx: Int = 160,
        viewportWidthPx: Int = 720,
        viewportHeightPx: Int = 960
    ): HostHandle {
        val instr = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instr.targetContext, CrashActivity::class.java)
        val scenario = ActivityScenario.launch<CrashActivity>(intent)
        lateinit var container: FrameLayout
        lateinit var canvas: UnifiedCanvasLayout
        scenario.onActivity { activity ->
            activity.setTheme(R.style.Theme_Scribe)
            container = FrameLayout(activity).apply {
                isFocusable = false
                isFocusableInTouchMode = false
            }
            canvas = UnifiedCanvasLayout(activity)
            canvas.headerView.setPadding(0, headerHeightPx, 0, 0)
            canvas.editor.apply {
                isFocusableInTouchMode = true
                isWordwrap = wordwrap
                isLineNumberEnabled = lineNumbers
                isFirstLineNumberAlwaysVisible = firstLnAlwaysVisible
                isLineNumberPinned = pinnedLineNumbers
                setTextSize(16f)
                setText(text)
            }
            container.addView(canvas, FrameLayout.LayoutParams(viewportWidthPx, viewportHeightPx))
            activity.setContentView(container)
            val wSpec = View.MeasureSpec.makeMeasureSpec(viewportWidthPx, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(viewportHeightPx, View.MeasureSpec.EXACTLY)
            container.measure(wSpec, hSpec)
            container.layout(0, 0, viewportWidthPx, viewportHeightPx)
            canvas.editor.requestFocus()
        }

        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instr.runOnMainSync {
                val ed = canvas.editor
                ready = ed.isAttachedToWindow &&
                    ed.windowToken != null &&
                    ed.isLaidOut &&
                    ed.width == viewportWidthPx &&
                    ed.height == viewportHeightPx &&
                    ed.layout != null &&
                    (!ed.isWordwrap || (ed.isWordwrapReady() && !ed.isLayoutBusy))
            }
            if (ready) break
            SystemClock.sleep(20L)
        }
        return HostHandle(scenario, container, canvas)
    }

    private fun waitUntil(timeoutMs: Long = 2_500L, failureMsg: String, condition: () -> Boolean) {
        val instr = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var met = false
            instr.runOnMainSync {
                met = condition()
            }
            if (met) return
            SystemClock.sleep(16L)
        }
        var finalMet = false
        instr.runOnMainSync {
            finalMet = condition()
        }
        assertTrue(failureMsg, finalMet)
    }

    private fun scrollCanvasTo(canvas: UnifiedCanvasLayout, targetTotalScrollPx: Float) {
        val currentTotal = (canvas.scrollD + canvas.editor.offsetY).toFloat()
        canvas.scrollCanvasBy(targetTotalScrollPx - currentTotal)
    }

    private fun makePlan(
        preScrollY: Int,
        effectiveTargetScroll: Float,
        requiresMode1: Boolean,
        initialTauPx: Float
    ): CollapsePlan {
        return CollapsePlan(
            scenario = CollapseScenario.B_LOWER_ONLY,
            requestedTargetScroll = effectiveTargetScroll,
            effectiveTargetScroll = effectiveTargetScroll,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 10_000,
                minSafeScroll = 0f,
                safeMinPhysical = 0,
                actualPhysicalScroll = preScrollY
            ),
            deltaGapPx = initialTauPx.coerceAtLeast(1f),
            preScrollY = preScrollY,
            requiresMode1 = requiresMode1,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = preScrollY,
            initialTauLowerPx = initialTauPx,
            isProgressCoupled = requiresMode1,
            requiresOffscreenCaretFollow = false
        )
    }

    private fun beginSplitCollapse(
        canvas: UnifiedCanvasLayout,
        gen: Long,
        seamLine: Int,
        seamFirstLowerRow: Int,
        tauPx: Float,
        requiresMode1: Boolean = false
    ): Long {
        val currentActual = canvas.scrollD + canvas.editor.offsetY
        val plan = makePlan(
            preScrollY = currentActual,
            effectiveTargetScroll = if (requiresMode1) (currentActual - tauPx).coerceAtLeast(0f) else currentActual.toFloat(),
            requiresMode1 = requiresMode1,
            initialTauPx = tauPx
        )
        canvas.beginPreEditDeletionSnapshot(
            isSelection = true,
            selectionStartLine = (seamLine - 1).coerceAtLeast(0),
            selectionEndLine = seamLine,
            preEditDocLineCount = canvas.editor.text.lineCount + 2,
            preEditDocHeight = (canvas.headerHeight + (canvas.editor.layout?.layoutHeight ?: 0)).toFloat(),
            preEditScrollY = currentActual.toFloat(),
            preEditCaretScreenY = 200f,
            wasAtTail = false,
            affectsDocumentTail = false,
            markPendingSettlement = true
        )
        val epoch = canvas.gapCollapseController.beginCollapse(
            owningGen = canvas.currentDeletionTransactionGeneration,
            plan = plan,
            seamLine = seamLine,
            seamFirstLowerRow = seamFirstLowerRow,
            seamDocY = seamFirstLowerRow * canvas.editor.rowHeight.toFloat(),
            rowHeightPx = canvas.editor.rowHeight.toFloat(),
            initialPhysicalScrollPx = currentActual.toFloat()
        )
        if (requiresMode1) {
            canvas.gapCollapseController.markMode1Started(
                expectedGen = canvas.currentDeletionTransactionGeneration,
                expectedCollapseEpoch = epoch,
                actualStartPhysicalScrollPx = currentActual.toFloat()
            )
        }
        if (canvas.gapCollapseController.shouldApplySplitRender) {
            canvas.editor.onGapCollapseSplitEntered()
        }
        return epoch
    }

    private fun drawEditorInViewSpace(editor: ScribeCodeEditor): RecordingCanvas {
        val w = editor.width.coerceAtLeast(1)
        val h = editor.height.coerceAtLeast(1)
        val rec = RecordingCanvas(w, h)
        // CodeEditor.onDraw(canvas) calls renderer.draw(canvas) directly in view coordinates;
        // EditorRenderer itself subtracts offsetX / offsetY internally.
        editor.renderer.draw(rec)
        rec.assertBalancedAndZeroTranslation()
        return rec
    }

    private fun dividerLeftX(editor: ScribeCodeEditor): Float {
        val renderer = editor.renderer as ScribeEditorRenderer
        val lineNumberWidth = editor.measureLineNumber()
        val offsetX = -editor.offsetX.toFloat()
        val sideIconWidth = if (renderer.hasSideHintIcons()) editor.rowHeight.toFloat() else 0f
        val gutterLeft = maxOf(0f, offsetX)
        return (offsetX + lineNumberWidth + sideIconWidth + editor.dividerMarginLeft).coerceAtLeast(gutterLeft)
    }

    private fun installTestBracketsLanguage(editor: ScribeCodeEditor, provider: BracketsProvider) {
        val manager = object : AnalyzeManager {
            private var receiver: StyleReceiver? = null
            override fun setReceiver(receiver: StyleReceiver?) {
                this.receiver = receiver
                receiver?.updateBracketProvider(this, provider)
            }
            override fun reset(content: ContentReference, extraArguments: Bundle) {}
            override fun insert(start: CharPosition, end: CharPosition, insertedContent: CharSequence) {}
            override fun delete(start: CharPosition, end: CharPosition, deletedContent: CharSequence) {}
            override fun rerun() {
                receiver?.updateBracketProvider(this, provider)
            }
            override fun destroy() {
                receiver = null
            }
        }
        val lang = object : EmptyLanguage() {
            override fun getAnalyzeManager(): AnalyzeManager = manager
        }
        editor.setEditorLanguage(lang)
    }

    @Test
    fun testRendererLifecycleDisjointnessCacheWarmAndRowOverrideReset() {
        val lines = (1..120).joinToString("\n") { idx -> "Line $idx with steady prose content." }
        val host = launchHost(text = lines, wordwrap = true)
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                assertTrue(ed.renderer is ScribeEditorRenderer)
                val renderer = ed.renderer as ScribeEditorRenderer

                val rowH = ed.rowHeight.coerceAtLeast(20)
                scrollCanvasTo(canvas, canvas.headerHeight + rowH * 30f)
                val normalFirstVis = ed.getFirstVisibleRow()
                val seamRow = normalFirstVis + 3
                val seamLine = ed.layout.getLineNumberForRow(seamRow)
                // Big tau (> 8 rows) exposes Pass-2 rows strictly above normalFirstVis to test cache warming
                val tau = rowH * 8f
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 1L,
                    seamLine = seamLine,
                    seamFirstLowerRow = seamRow,
                    tauPx = tau
                )

                val seqBefore = renderer.renderedDrawPassSeq
                assertNull(ed.renderPassRowRangeOverride)
                drawEditorInViewSpace(ed)
                assertEquals("Single super.draw lifecycle must increment renderedDrawPassSeq by 1", seqBefore + 1L, renderer.renderedDrawPassSeq)
                assertNull("renderPassRowRangeOverride must reset to null in finally", ed.renderPassRowRangeOverride)
                assertTrue(renderer.lastSplitFrameWasActive)

                val p1 = renderer.lastPass1RowRange
                val p2 = renderer.lastPass2RowRange
                assertFalse(p1.isEmpty())
                assertFalse(p2.isEmpty())
                assertTrue("Pass 1 and Pass 2 must be strictly disjoint around seamRow", p1.last < seamRow && p2.first >= seamRow)
                assertTrue("Pass 2 must warm source lines outside normal visible window", ed.layout.getLineNumberForRow(p2.first) < ed.layout.getLineNumberForRow(normalFirstVis))
                assertNotNull(
                    "Pass-2 source line measure cache must be warmed",
                    ed.renderContext.cache.queryMeasureCache(ed.layout.getLineNumberForRow(p2.first))
                )
            }
        }
    }

    @Test
    fun testApproachA_AllSixFirstLnCases_IncludingWrappingAndCollision() {
        val longLine0 = (1..24).joinToString(" ") { "longwrappedtoken$it" }
        val shortLines = (2..60).joinToString("\n") { "S$it short" }
        val doc = "$longLine0\n$shortLines"
        val host = launchHost(
            text = doc,
            wordwrap = true,
            lineNumbers = true,
            firstLnAlwaysVisible = true,
            pinnedLineNumbers = false
        )
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                val renderer = ed.renderer as ScribeEditorRenderer
                val lay = ed.layout
                val line0Rows = lay.getRowCountForLine(0)
                assertTrue("Expected wrapped line 0 (>= 5 rows) at 720px width, got $line0Rows", line0Rows >= 5)
                val rowH = ed.rowHeight.toFloat()
                val h = ed.height.toFloat()
                val divLeft = dividerLeftX(ed)

                // --- Case 1: !P1.isEmpty && !P2.isEmpty && postDrawLineNumbers.isNotEmpty ---
                // Also verifies Repair 3.2: Pass-1 postDrawLineNumbers with native [stuckLineBottom, h] clip
                // never paint across [pass1ClipBottom, pass2ClipTop] or overlap Pass-2 line numbers.
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())
                val seamLineC1 = 3
                val seamRowC1 = lay.getRowIndexForPosition(ed.text.getCharIndex(seamLineC1, 0))
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 1L,
                    seamLine = seamLineC1,
                    seamFirstLowerRow = seamRowC1,
                    tauPx = rowH * 2f
                )
                val rec1 = drawEditorInViewSpace(ed)
                assertEquals(-1, renderer.lastOverrideFirstLnLine)
                assertEquals(-1, renderer.lastPass2FirstLnDrawnInPass2)
                val p1Nums = rec1.textOps.filter { it.screenX < divLeft && it.text in setOf("1", "2", "3") }
                val p2Nums = rec1.textOps.filter { it.screenX < divLeft && it.text == "4" }
                assertEquals("Pass 1 must draw line numbers 1, 2, 3 exactly once each", 3, p1Nums.size)
                assertEquals("Pass 2 must draw line number 4 exactly once", 1, p2Nums.size)
                val p1BottomsMax = p1Nums.maxOf { it.screenY }
                assertTrue(
                    "All Pass-1 postDrawLineNumbers baselines ($p1BottomsMax) must stay strictly above pass1ClipBottom (${renderer.lastPass1ClipBottom}) and pass2ClipTop (${renderer.lastPass2ClipTop})",
                    p1Nums.all { it.translationY == 0f && it.screenY <= renderer.lastPass1ClipBottom + 0.5f && it.screenY < renderer.lastPass2ClipTop }
                )
                val op4 = p2Nums.single()
                assertEquals(rowH * 2f, op4.translationY, 0.01f)
                assertTrue("Pass 2 line 4 (${op4.screenY}) must sit strictly below Pass 1 line 3", op4.screenY > p1BottomsMax)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c1")

                // --- Case 6 + Case 2: firstVis is continuation row of line 0 (!isLeadingRow), P1 has NO leading rows ---
                assertFalse("Row 1 must be wrapped continuation row", lay.getRowAt(1).isLeadingRow)
                scrollCanvasTo(canvas, canvas.headerHeight + rowH * 1f)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 2L,
                    seamLine = 1,
                    seamFirstLowerRow = line0Rows,
                    tauPx = rowH * 2f
                )
                val rec2 = drawEditorInViewSpace(ed)
                assertEquals("Case 2 (+Case 6) must populate overrideFirstLnLine=0", 0, renderer.lastOverrideFirstLnLine)
                val case2FirstOps = rec2.textOps.filter { it.text == "1" && it.screenX < divLeft }
                assertEquals("Must draw '1' once unshifted", 1, case2FirstOps.size)
                val op2 = case2FirstOps.single()
                assertEquals(0f, op2.translationY, 0.01f)
                assertEquals(0f, op2.clipScreen.top, 0.5f)
                assertEquals(h, op2.clipScreen.bottom, 0.5f)
                assertTrue(op2.screenX < divLeft)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c2")

                // --- Case 4 via Case 2 (collision: !P1.isEmpty && postDrawLineNumbers.isEmpty && translatedTop2 <= bottom) ---
                val collOffsetY2 = (line0Rows - 1) * rowH + rowH * 0.4f
                val tauColl2 = rowH * 0.25f
                scrollCanvasTo(canvas, canvas.headerHeight + collOffsetY2)
                assertFalse("Row ${ed.getFirstVisibleRow()} must be continuation row of line 0", lay.getRowAt(ed.getFirstVisibleRow()).isLeadingRow)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 3L,
                    seamLine = 1,
                    seamFirstLowerRow = line0Rows,
                    tauPx = tauColl2
                )
                val rec4a = drawEditorInViewSpace(ed)
                assertEquals(0, renderer.lastOverrideFirstLnLine)
                val op4a = rec4a.textOps.single { it.text == "1" && it.screenX < divLeft }
                val op4aLine2 = rec4a.textOps.single { it.text == "2" && it.screenX < divLeft }
                assertEquals(0f, op4a.translationY, 0.01f)
                assertEquals(h, op4a.clipScreen.bottom, 0.5f)
                assertTrue("Case 4 y_pushed_P2 (${op4a.screenY}) must sit strictly above Pass-2 row2_0 (${op4aLine2.screenY})", op4a.screenY < op4aLine2.screenY)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c4a")

                // --- Case 3 top (+ Case 6): P1.isEmpty && !P2.isEmpty && pass2TopScreenY <= 0f ---
                scrollCanvasTo(canvas, canvas.headerHeight + rowH * 2.4f)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 4L,
                    seamLine = 0,
                    seamFirstLowerRow = 1,
                    tauPx = rowH * 0.8f
                )
                val rec3 = drawEditorInViewSpace(ed)
                assertTrue("Case 3 top expects P1.isEmpty", renderer.lastPass1RowRange.isEmpty())
                assertEquals("Case 3 top expects overrideFirstLnLine=0", 0, renderer.lastOverrideFirstLnLine)
                val op3 = rec3.textOps.single { it.text == "1" && it.screenX < divLeft }
                assertEquals(0f, op3.translationY, 0.01f)
                assertEquals(0f, op3.clipScreen.top, 0.5f)
                assertEquals(h, op3.clipScreen.bottom, 0.5f)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c3")

                // --- Case 3 top WITH Case 4 collision (P1.isEmpty && pass2TopScreenY <= 0f && translatedTop2 <= bottom) ---
                val tauColl3 = rowH * 0.6f
                scrollCanvasTo(canvas, canvas.headerHeight + (line0Rows - 1) * rowH + rowH * 0.35f)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 5L,
                    seamLine = 0,
                    seamFirstLowerRow = ed.getFirstVisibleRow(),
                    tauPx = tauColl3
                )
                val rec4b = drawEditorInViewSpace(ed)
                assertTrue(renderer.lastPass1RowRange.isEmpty())
                assertEquals(0, renderer.lastOverrideFirstLnLine)
                val op4b1 = rec4b.textOps.single { it.text == "1" && it.screenX < divLeft }
                val op4b2 = rec4b.textOps.single { it.text == "2" && it.screenX < divLeft }
                assertEquals(0f, op4b1.translationY, 0.01f)
                assertEquals(h, op4b1.clipScreen.bottom, 0.5f)
                assertTrue("Case 4 under P1.isEmpty must push '1' above '2'", op4b1.screenY < op4b2.screenY)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c4b")

                // --- Case 5 (P1.isEmpty && pass2TopScreenY > 0f, blank visual gap above P2) ---
                val tauGap = rowH * 1.5f
                scrollCanvasTo(canvas, canvas.headerHeight + rowH * 1f)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 6L,
                    seamLine = 0,
                    seamFirstLowerRow = 1,
                    tauPx = tauGap
                )
                val rec5 = drawEditorInViewSpace(ed)
                assertTrue(renderer.lastPass1RowRange.isEmpty())
                assertEquals(-1, renderer.lastOverrideFirstLnLine)
                assertEquals("Case 5 must draw pass2FirstLn (line 0) inside Pass 2", 0, renderer.lastPass2FirstLnDrawnInPass2)
                val op5 = rec5.textOps.single { it.text == "1" && it.screenX < divLeft }
                assertEquals("Case 5 must translate line 1 number by +tauLower", tauGap, op5.translationY, 0.01f)
                assertEquals("Case 5 must clip to pass2ClipTop", renderer.lastPass2ClipTop, op5.clipScreen.top, 0.5f)
                assertEquals(h, op5.clipScreen.bottom, 0.5f)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c5")
            }
        }
    }

    @Test
    fun testPerLayerClipTranslationAndLowerInsertionHandleShiftZeroDoubleShift() {
        val doc = (0..40).joinToString("\n") { i -> "Line $i {} sample text for layers and handle" }
        val host = launchHost(text = doc, wordwrap = true)
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                val renderer = ed.renderer as ScribeEditorRenderer
                val rowH = ed.rowHeight.toFloat()
                val h = ed.height.toFloat()
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())

                // Configure distinct, easily identifiable colors for every layer
                val colorWholeBg = 0xFF101216.toInt()
                val colorLineNumBg = 0xFF181B22.toInt()
                val colorDivider = 0xFF3A4150.toInt()
                val colorCurrentLine = 0xFF282E3A.toInt()
                val colorSelection = 0x664477CC
                val colorDelimBg = 0x55FF8800
                val colorDelimUnderline = 0xFFFF00FF.toInt()
                val colorSideBlock = 0xFF00CCAA.toInt()
                val colorIconP1 = 0xFF336699.toInt()
                val colorIconP2 = 0xFF993366.toInt()
                val colorGutterP1 = 0x4400AAFF
                val colorGutterP2 = 0x4400FF00

                ed.colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, colorWholeBg)
                ed.colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colorLineNumBg)
                ed.colorScheme.setColor(EditorColorScheme.LINE_DIVIDER, colorDivider)
                ed.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, colorCurrentLine)
                ed.colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colorSelection)
                ed.colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, colorDelimBg)
                ed.colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, colorDelimUnderline)
                ed.colorScheme.setColor(EditorColorScheme.SIDE_BLOCK_LINE, colorSideBlock)

                ed.isBlockLineEnabled = true
                ed.props.drawSideBlockLine = true
                ed.isHighlightCurrentLine = true
                ed.isHighlightBracketPair = true
                ed.props.highlightMatchingDelimiters = true

                // Open '{' at line 2 col 7 (P1) paired with '}' at line 12 col 9 (P2)
                val openCol = ed.text.getLine(2).indexOf('{')
                val closeCol = ed.text.getLine(12).indexOf('}')
                val openIdx = ed.text.getCharIndex(2, openCol)
                val closeIdx = ed.text.getCharIndex(12, closeCol)
                installTestBracketsLanguage(
                    ed,
                    BracketsProvider { _, _ -> PairedBracket(openIdx, 1, closeIdx, 1) }
                )

                val styles = Styles().apply {
                    addCodeBlock(CodeBlock().apply {
                        startLine = 1
                        startColumn = 0
                        endLine = 14
                        endColumn = 5
                        toBottomOfEndLine = true
                    })
                    addLineStyle(LineSideIcon(2, ColorDrawable(colorIconP1)))
                    addLineStyle(LineSideIcon(12, ColorDrawable(colorIconP2)))
                    addLineStyle(LineGutterBackground(2, ConstColor(colorGutterP1)))
                    addLineStyle(LineGutterBackground(12, ConstColor(colorGutterP2)))
                    finishBuilding()
                }
                ed.styles = styles

                val diag = DiagnosticsContainer().apply {
                    val idxP1 = ed.text.getCharIndex(2, 0)
                    val idxP2 = ed.text.getCharIndex(12, 0)
                    addDiagnostic(DiagnosticRegion(idxP1, idxP1 + 4, DiagnosticRegion.SEVERITY_WARNING, 0L, DiagnosticDetail("W", "Warn")))
                    addDiagnostic(DiagnosticRegion(idxP2, idxP2 + 4, DiagnosticRegion.SEVERITY_ERROR, 0L, DiagnosticDetail("E", "Err")))
                }
                ed.diagnosticIndicatorStyle = DiagnosticIndicatorStyle.WAVY_LINE
                ed.diagnostics = diag

                val seamLine = 6
                val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(seamLine, 0))
                val tau = rowH * 2.5f
                val divLeft = dividerLeftX(ed)

                // A) Selection spanning P1 (line 2) -> P2 (line 12): verify selection background & P1/P2 text partition
                ed.requestFocus()
                ed.setSelectionRegion(2, 2, 12, 6, false)
                val unsplitSel = drawEditorInViewSpace(ed)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 1L,
                    seamLine = seamLine,
                    seamFirstLowerRow = seamRow,
                    tauPx = tau
                )
                val splitSel = drawEditorInViewSpace(ed)
                val p1Clip = renderer.lastPass1ClipBottom
                val p2Clip = renderer.lastPass2ClipTop

                val selRectsP1 = splitSel.rectOps.filter { it.color == colorSelection && it.clipScreen.bottom <= p1Clip + 0.5f }
                val selRectsP2 = splitSel.rectOps.filter { it.color == colorSelection && it.clipScreen.top >= p2Clip - 0.5f }
                assertTrue("Selection in Pass 1 must draw unshifted rects clipped to pass1ClipBottom", selRectsP1.isNotEmpty() && selRectsP1.all { it.translationY == 0f })
                assertTrue("Selection in Pass 2 must draw +tauLower shifted rects clipped to pass2ClipTop", selRectsP2.isNotEmpty() && selRectsP2.all { it.translationY == tau })
                assertTrue("Unshifted baseline had selection rects", unsplitSel.rectOps.any { it.color == colorSelection })
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "layers_sel")

                // B) P1 Caret test: caret inside P1 (line 2) handle stays unshifted under split frame
                ed.setSelection(2, 4, false)
                drawEditorInViewSpace(ed)
                val unsplitP1Handle = RectF(ed.insertHandleDescriptor.position)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 2L,
                    seamLine = seamLine,
                    seamFirstLowerRow = seamRow,
                    tauPx = tau
                )
                drawEditorInViewSpace(ed)
                val splitP1Handle = RectF(ed.insertHandleDescriptor.position)
                assertEquals("P1 caret handle top must NOT shift", unsplitP1Handle.top, splitP1Handle.top, 0.5f)
                assertEquals("P1 caret handle bottom must NOT shift", unsplitP1Handle.bottom, splitP1Handle.bottom, 0.5f)
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "layers_p1_caret")

                // C) P2 Caret on '}' (line 12): exercises ALL 10 layers + Case-I lower handle shift ONCE
                ed.setSelection(12, closeCol + 1, false)
                val unsplit = drawEditorInViewSpace(ed)
                val unsplitDesc = RectF(ed.insertHandleDescriptor.position)
                assertFalse("Unsplit baseline handle descriptor must be populated", unsplitDesc.isEmpty)

                val p1RowTop = ed.getRowTop(ed.layout.getRowIndexForPosition(ed.text.getCharIndex(2, 0))) - ed.offsetY.toFloat()
                val p2RowTop = ed.getRowTop(ed.layout.getRowIndexForPosition(ed.text.getCharIndex(12, 0))) - ed.offsetY.toFloat()

                beginSplitCollapse(
                    canvas = canvas,
                    gen = 3L,
                    seamLine = seamLine,
                    seamFirstLowerRow = seamRow,
                    tauPx = tau
                )
                val rec = drawEditorInViewSpace(ed)
                val p1Bottom = renderer.lastPass1ClipBottom
                val p2Top = renderer.lastPass2ClipTop

                // Layer 1: WHOLE_BACKGROUND fixed unshifted
                assertTrue(
                    "Layer 1 WHOLE_BACKGROUND must draw unshifted across viewport",
                    rec.rectOps.any { it.color == colorWholeBg && it.translationY == 0f && it.clipScreen.height() >= h - 1f }
                )

                // Layer 3: CURRENT_LINE on line 12 (P2) translated +tau & clipped to pass2ClipTop; no line drawn twice
                val curLineRects = rec.rectOps.filter { it.color == colorCurrentLine && it.clipScreen.top >= p2Top - 0.5f }
                assertTrue("Layer 3 CURRENT_LINE in P2 must shift by +tauLower", curLineRects.isNotEmpty() && curLineRects.all { it.translationY == tau })
                val p1Line2Ops = rec.textOps.filter { it.text.contains("Line 2 ") }
                val p2Line12Ops = rec.textOps.filter { it.text.contains("Line 12 ") }
                assertTrue("Line 2 text must be in P1 unshifted", p1Line2Ops.isNotEmpty() && p1Line2Ops.all { it.clipScreen.bottom <= p1Bottom + 0.5f && kotlin.math.abs(it.translationY - p1RowTop) <= 1f })
                assertTrue("Line 12 text must be in P2 shifted +tauLower", p2Line12Ops.isNotEmpty() && p2Line12Ops.all { it.clipScreen.top >= p2Top - 0.5f && kotlin.math.abs(it.translationY - (p2RowTop + tau)) <= 1f })
                assertEquals("No line-number label may be drawn twice", 1, rec.textOps.count { it.text == "3" && it.screenX < divLeft })
                assertEquals("No Pass-2 line-number label may be drawn twice", 1, rec.textOps.count { it.text == "13" && it.screenX < divLeft })

                // Layer 4: patchHighlightedDelimiters (P1 '{' vs P2 '}')
                val unsplitDelimUnderlines = unsplit.lineOps.filter { it.color == colorDelimUnderline }
                assertTrue("Unsplit baseline must render delimiter underlines", unsplitDelimUnderlines.size >= 2)
                val delimP1 = rec.rectOps.filter { it.color == colorDelimBg && it.clipScreen.bottom <= p1Bottom + 0.5f } +
                    rec.lineOps.filter { it.color == colorDelimUnderline && it.clipScreen.bottom <= p1Bottom + 0.5f }
                val delimP2 = rec.rectOps.filter { it.color == colorDelimBg && it.clipScreen.top >= p2Top - 0.5f } +
                    rec.lineOps.filter { it.color == colorDelimUnderline && it.clipScreen.top >= p2Top - 0.5f }
                assertTrue("Layer 4 P1 delimiter must draw in Pass 1 unshifted", delimP1.isNotEmpty())
                assertTrue("Layer 4 P2 delimiter must draw in Pass 2 shifted", delimP2.isNotEmpty())
                val delimLineP1 = rec.lineOps.first { it.color == colorDelimUnderline && it.clipScreen.bottom <= p1Bottom + 0.5f }
                val delimLineP2 = rec.lineOps.first { it.color == colorDelimUnderline && it.clipScreen.top >= p2Top - 0.5f }
                val unsplitDelimP1 = unsplitDelimUnderlines.minBy { it.screenStartY }
                val unsplitDelimP2 = unsplitDelimUnderlines.maxBy { it.screenStartY }
                assertEquals("P1 delimiter Y must be unshifted", unsplitDelimP1.screenStartY, delimLineP1.screenStartY, 1f)
                assertEquals("P2 delimiter Y must shift +tauLower", unsplitDelimP2.screenStartY + tau, delimLineP2.screenStartY, 1f)

                // Layer 5: drawDiagnosticIndicators (WAVY_LINE paths in P1 vs P2)
                val unsplitDiags = unsplit.pathOps.filter { it.style == Paint.Style.STROKE }
                assertTrue("Unsplit baseline must draw diagnostic wavy paths", unsplitDiags.size >= 2)
                val diagP1 = rec.pathOps.filter { it.style == Paint.Style.STROKE && it.clipScreen.bottom <= p1Bottom + 0.5f }
                val diagP2 = rec.pathOps.filter { it.style == Paint.Style.STROKE && it.clipScreen.top >= p2Top - 0.5f }
                assertTrue("Layer 5 P1 WAVY_LINE must draw in Pass 1", diagP1.isNotEmpty())
                assertTrue("Layer 5 P2 WAVY_LINE must draw in Pass 2", diagP2.isNotEmpty())
                assertEquals(
                    "P1 WAVY_LINE must remain unshifted",
                    unsplitDiags.minOf { it.screenBounds.top },
                    diagP1.minOf { it.screenBounds.top },
                    1.5f
                )
                assertEquals(
                    "P2 WAVY_LINE must shift by +tauLower",
                    unsplitDiags.maxOf { it.screenBounds.top } + tau,
                    diagP2.maxOf { it.screenBounds.top },
                    1.5f
                )

                // Layer 6 & 7: Gutter fixed bg, UserGutterBackground P1/P2, SideIcons P1/P2, LINE_DIVIDER unshifted, P1/P2 line numbers
                assertTrue("Layer 6 LINE_NUMBER_BACKGROUND fixed unshifted", rec.rectOps.any { it.color == colorLineNumBg && it.translationY == 0f })
                assertTrue("Layer 6 LineGutterBackground P1 unshifted", rec.rectOps.any { it.color == colorGutterP1 && it.translationY == 0f && it.clipScreen.bottom <= p1Bottom + 0.5f })
                assertTrue("Layer 6 LineGutterBackground P2 shifted", rec.rectOps.any { it.color == colorGutterP2 && it.translationY == tau && it.clipScreen.top >= p2Top - 0.5f })
                assertTrue("Layer 7 LineSideIcon P1 unshifted", rec.rectOps.any { it.color == colorIconP1 && it.translationY == 0f && it.clipScreen.bottom <= p1Bottom + 0.5f })
                assertTrue("Layer 7 LineSideIcon P2 shifted", rec.rectOps.any { it.color == colorIconP2 && it.translationY == tau && it.clipScreen.top >= p2Top - 0.5f })
                assertTrue("Layer 7 LINE_DIVIDER stays unshifted across full height", rec.rectOps.any { it.color == colorDivider && it.translationY == 0f } || rec.lineOps.any { it.color == colorDivider && it.translationY == 0f })

                // Layer 8: drawSideBlockLine split into P1 unshifted & P2 shifted
                val sideBlockP1 = rec.lineOps.filter { it.color == colorSideBlock && it.translationY == 0f && it.clipScreen.bottom <= p1Bottom + 0.5f }
                val sideBlockP2 = rec.lineOps.filter { it.color == colorSideBlock && it.translationY == tau && it.clipScreen.top >= p2Top - 0.5f }
                assertTrue("Layer 8 SIDE_BLOCK_LINE must draw unshifted segment in Pass 1", sideBlockP1.isNotEmpty())
                assertTrue("Layer 8 SIDE_BLOCK_LINE must draw +tauLower segment in Pass 2", sideBlockP2.isNotEmpty())

                // Layer 9: Lower-region DrawCursorTask + insertHandleDescriptor shifted ONCE (zero double shift)
                val splitDesc = RectF(ed.insertHandleDescriptor.position)
                assertEquals("Layer 9 lower-region handle top must shift by +tauLower ONCE", unsplitDesc.top + tau, splitDesc.top, 0.5f)
                assertEquals("Layer 9 lower-region handle bottom must shift by +tauLower ONCE", unsplitDesc.bottom + tau, splitDesc.bottom, 0.5f)

                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "layers")
            }
        }
    }

    @Test
    fun testSyncOuterOnlyInvalidation_OptionBCursorAnimator_AndTextActionWindowGate() {
        val doc = (1..80).joinToString("\n") { "Paragraph $it with enough length to scroll and select cleanly." }
        val host = launchHost(text = doc, wordwrap = true, headerHeightPx = 300)
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            var invalidateCalls = 0
            lateinit var realAnimator: ScribeCursorAnimator
            lateinit var aw: EditorTextActionWindow
            var simulateAnimatorRunning = false

            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                aw = ed.getComponent(EditorTextActionWindow::class.java)
                assertTrue("EditorTextActionWindow starts enabled", aw.isEnabled)
                realAnimator = ed.cursorAnimator as ScribeCursorAnimator
                // Delegate wrapper ensures isRunning == true can be held deterministically even when
                // CI emulator runs with disable-animations: true (animator_duration_scale == 0).
                val deterministicAnimator = object : CursorAnimator by realAnimator {
                    override fun isRunning(): Boolean = simulateAnimatorRunning || realAnimator.isRunning()
                }
                ed.setCursorAnimator(deterministicAnimator)

                // Verify synchronous invalidate() during outer-only (scrollD < headerHeight) Mode-1 & Mode-2 ticks
                scrollCanvasTo(canvas, 100f)
                val testController = ScribeGapCollapseController(
                    onInvalidateEditor = { invalidateCalls++ }
                )
                val rowH = ed.rowHeight.toFloat()
                val planM1 = makePlan(preScrollY = 100, effectiveTargetScroll = 0f, requiresMode1 = true, initialTauPx = 80f)
                val ep1 = testController.beginCollapse(1L, planM1, 10, 10, rowH * 10f, rowH, 100f)
                testController.markMode1Started(1L, ep1, 100f)
                val beforeM1Tick = invalidateCalls
                testController.onMode1FrameCommitted(1L, ep1, actualPhysicalScrollPx = 80f, boundaryHit = false, frameTimeNanos = 1_016_000_000L)
                assertEquals("Outer-only Mode-1 tick must synchronously invoke invalidate", beforeM1Tick + 1, invalidateCalls)

                val planM2 = makePlan(preScrollY = 100, effectiveTargetScroll = 100f, requiresMode1 = false, initialTauPx = 80f)
                val ep2 = testController.beginCollapse(2L, planM2, 10, 10, rowH * 10f, rowH, 100f)
                val beforeM2Tick = invalidateCalls
                testController.onMode2Frame(2L, ep2, 2_000_000_000L)
                testController.onMode2Frame(2L, ep2, 2_016_000_000L)
                assertEquals("Outer-only Mode-2 tick must synchronously invoke invalidate", beforeM2Tick + 1, invalidateCalls)

                // Real canvas Option-B test: enter Mode 2 with remaining selection, verify aw.isEnabled == false
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())
                ed.requestFocus()
                ed.setSelectionRegion(10, 2, 10, 12, false)
                assertTrue(ed.cursor.isSelected)
                val renderer = ed.renderer as ScribeEditorRenderer
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 10L,
                    seamLine = 10,
                    seamFirstLowerRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(10, 0)),
                    tauPx = 90f
                )
                assertFalse("EditorTextActionWindow must disable upon split collapse entry", aw.isEnabled)
                drawEditorInViewSpace(ed)
                assertTrue(renderer.lastSplitFrameWasActive)
                assertFalse(aw.isEnabled)

                // Start cursorAnimator mid-collapse -> real onAnimatorStarted invokes handleCursorAnimatorActivatedDuringCollapse()
                simulateAnimatorRunning = true
                realAnimator.triggerGlide(0f, 0f, 120f, 40f, 20f, 20f)
                assertFalse("Split rendering must disable immediately when cursorAnimator starts", canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertFalse("aw.isEnabled must stay false prior to post-IDLE unsplit draw", aw.isEnabled)

                // Draw while cursorAnimator is STILL running -> didRefreshCursorDescriptorsOnLastDraw == false
                drawEditorInViewSpace(ed)
                assertFalse(renderer.lastSplitFrameWasActive)
                assertFalse("Draw while cursorAnimator running skips DrawCursorTasks", renderer.didRefreshCursorDescriptorsOnLastDraw)
                assertFalse("aw.isEnabled must stay false when DrawCursorTasks were skipped", aw.isEnabled)

                // Finish cursorAnimator, draw next unsplit frame -> didRefreshCursorDescriptorsOnLastDraw == true,
                // posting pendingTextActionRefreshRunnable on next animation frame
                simulateAnimatorRunning = false
                realAnimator.cancel()
                assertFalse(ed.cursorAnimator.isRunning)
                drawEditorInViewSpace(ed)
                assertTrue(renderer.didRefreshCursorDescriptorsOnLastDraw)
            }

            waitUntil(failureMsg = "EditorTextActionWindow must re-enable after post-IDLE unsplit cursor-descriptor refresh") {
                aw.isEnabled && aw.isShowing
            }
            instr.runOnMainSync {
                aw.dismiss()
                host.canvas.editor.setCursorAnimator(realAnimator)
                assertNull(host.canvas.editor.renderPassRowRangeOverride)
            }
        }
    }

    @Test
    fun testOptionBEventB_SafetyCorrectionAndDeferredLayoutRetryOnRealHost() {
        val shortDoc = (1..24).joinToString("\n") { "Short doc line $it for Event-B safety bounds." }
        val host = launchHost(text = shortDoc, wordwrap = true, headerHeightPx = 300)
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            lateinit var aw: EditorTextActionWindow

            // Subcase 1: Unsafe physical scroll triggers Event B safety correction -> CORRECTED_AND_VALIDATED
            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                aw = ed.getComponent(EditorTextActionWindow::class.java)
                val realAnimator = ed.cursorAnimator as ScribeCursorAnimator
                val rowH = ed.rowHeight.toFloat()

                // Scroll past safeMax (24 short lines ~ 24 * rowH, much smaller than 960px viewport -> maxSafe == 0)
                val maxScroll = canvas.maxCanvasScroll.toFloat()
                scrollCanvasTo(canvas, maxScroll)
                val preCorrectionScroll = (canvas.scrollD + ed.offsetY).toFloat()

                ed.requestFocus()
                ed.setSelectionRegion(8, 1, 8, 8, false)
                val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(8, 0))
                val plan = makePlan(
                    preScrollY = preCorrectionScroll.toInt(),
                    effectiveTargetScroll = preCorrectionScroll,
                    requiresMode1 = false,
                    initialTauPx = rowH * 2f
                ).let { base ->
                    base.copy(
                        safetyBounds = canvas.resolveSafetyFloorAndBounds(
                            anchorDocBottom = (8 + 1) * rowH
                        )
                    )
                }
                canvas.beginPreEditDeletionSnapshot(
                    isSelection = true,
                    selectionStartLine = 7,
                    selectionEndLine = 8,
                    preEditDocLineCount = ed.text.lineCount + 2,
                    preEditDocHeight = (canvas.headerHeight + ed.layout.layoutHeight).toFloat(),
                    preEditScrollY = preCorrectionScroll,
                    preEditCaretScreenY = 200f,
                    wasAtTail = false,
                    affectsDocumentTail = false,
                    markPendingSettlement = true
                )
                canvas.gapCollapseController.beginCollapse(
                    owningGen = canvas.currentDeletionTransactionGeneration,
                    plan = plan,
                    seamLine = 8,
                    seamFirstLowerRow = seamRow,
                    seamDocY = seamRow * rowH,
                    rowHeightPx = rowH,
                    initialPhysicalScrollPx = preCorrectionScroll
                )
                ed.onGapCollapseSplitEntered()
                assertFalse(aw.isEnabled)

                // Now push physical scroll beyond safeMax so Event B requires corrective scroll back into safety bounds
                val safeNow = canvas.resolveSafetyFloorAndBounds(anchorDocBottom = (8 + 1) * rowH)
                if (safeNow.safeMax > 10) {
                    scrollCanvasTo(canvas, safeNow.safeMax.toFloat() + 60f)
                } else {
                    scrollCanvasTo(canvas, maxOf(120f, safeNow.safeMinPhysical.toFloat() + 120f))
                }

                // Trigger real CursorAnimator Option-B interruption
                realAnimator.triggerGlide(0f, 0f, 120f, 40f, 20f, 20f)
                assertFalse("Split rendering must disable immediately on Event B", canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertFalse("aw.isEnabled must stay false until post-IDLE unsplit draw pass", aw.isEnabled)
            }

            waitUntil(failureMsg = "Event-B safety correction must converge to IDLE") {
                host.canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                    !host.canvas.gapCollapseController.isTransactionOwned
            }
            instr.runOnMainSync {
                val ed = host.canvas.editor
                drawEditorInViewSpace(ed)
            }
            waitUntil(failureMsg = "EditorTextActionWindow must restore after Event-B IDLE + unsplit draw") {
                aw.isEnabled
            }

            // Subcase 2: DEFERRED_UNTIL_LAYOUT_READY (height == 0) keeps FINAL_SAFETY_PENDING -> layout becomes ready -> retries to IDLE
            instr.runOnMainSync {
                val canvas = host.canvas
                val ed = canvas.editor
                val realAnimator = ed.cursorAnimator as ScribeCursorAnimator
                val rowH = ed.rowHeight.toFloat()
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())
                ed.requestFocus()
                ed.setSelectionRegion(6, 1, 6, 8, false)
                val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(6, 0))
                beginSplitCollapse(canvas, gen = 50L, seamLine = 6, seamFirstLowerRow = seamRow, tauPx = rowH * 2f)
                assertFalse(aw.isEnabled)

                // Force canvas height = 0 so completeDeletionVisualTransaction hits DEFERRED_UNTIL_LAYOUT_READY
                val zeroSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
                val zeroHSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY)
                canvas.measure(zeroSpec, zeroHSpec)
                canvas.layout(0, 0, 720, 0)

                realAnimator.triggerGlide(0f, 0f, 120f, 40f, 20f, 20f)
                assertEquals(
                    "DEFERRED_UNTIL_LAYOUT_READY must keep FINAL_SAFETY_PENDING",
                    GapCollapsePhase.FINAL_SAFETY_PENDING,
                    canvas.gapCollapseController.phase
                )
                assertTrue("Controller must remain transaction-owned while deferred", canvas.gapCollapseController.isTransactionOwned)
                assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertFalse("aw.isEnabled must remain false while deferred", aw.isEnabled)

                // Restore valid layout -> OnLayoutChangeListener schedules retry -> completes cleanly to IDLE
                val wSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
                val hSpec = View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY)
                canvas.measure(wSpec, hSpec)
                canvas.layout(0, 0, 720, 960)
            }

            waitUntil(failureMsg = "Deferred Event B must retry on layout ready and settle to IDLE") {
                host.canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                    !host.canvas.gapCollapseController.isTransactionOwned
            }
            instr.runOnMainSync {
                drawEditorInViewSpace(host.canvas.editor)
            }
            waitUntil(failureMsg = "EditorTextActionWindow must restore after deferred Event-B settles") {
                aw.isEnabled
            }
        }
    }

    @Test
    fun testEditorTextActionWindow_AllSevenLifecycleCases() {
        val doc = (1..60).joinToString("\n") { "Paragraph $it for EditorTextActionWindow lifecycle verification." }
        val host = launchHost(text = doc, wordwrap = true)
        host.scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()

            // Case 1: isLayoutBusy active during refresh attempt -> recovers on LayoutStateChangeEvent(false) + fresh unsplit draw
            run {
                lateinit var aw: EditorTextActionWindow
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    aw.isEnabled = true
                    ed.requestFocus()
                    ed.setSelectionRegion(8, 2, 8, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(8, 0))
                    beginSplitCollapse(canvas, gen = 1L, seamLine = 8, seamFirstLowerRow = seamRow, tauPx = 80f)
                    assertFalse(aw.isEnabled)

                    // Mark layout busy BEFORE committing IDLE + drawing unsplit frame
                    ed.dispatchEvent(LayoutStateChangeEvent(ed, true))
                    assertTrue(ed.isLayoutBusy)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "l_busy")
                    drawEditorInViewSpace(ed)
                }
                // Let pending runnable run and observe isLayoutBusy == true
                SystemClock.sleep(60L)
                instr.runOnMainSync {
                    val ed = host.canvas.editor
                    assertFalse("aw.isEnabled must remain false while isLayoutBusy", aw.isEnabled)
                    ed.dispatchEvent(LayoutStateChangeEvent(ed, false))
                    assertFalse(ed.isLayoutBusy)
                    // Fresh unsplit draw after layout busy clears satisfies targetSeq re-arming
                    drawEditorInViewSpace(ed)
                }
                waitUntil(failureMsg = "Case 1: aw.isEnabled must recover after isLayoutBusy clears + fresh unsplit draw") {
                    aw.isEnabled
                }
                instr.runOnMainSync { aw.dismiss() }
            }

            // Case 2: cursorAnimator.isRunning active during first refresh attempt -> stays disabled; later unsplit !isRunning draw restores
            run {
                lateinit var aw: EditorTextActionWindow
                lateinit var realAnimator: ScribeCursorAnimator
                var forceRunning = false
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    realAnimator = ed.cursorAnimator as ScribeCursorAnimator
                    ed.setCursorAnimator(object : CursorAnimator by realAnimator {
                        override fun isRunning(): Boolean = forceRunning || realAnimator.isRunning()
                    })
                    ed.setSelectionRegion(10, 2, 10, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(10, 0))
                    beginSplitCollapse(canvas, gen = 2L, seamLine = 10, seamFirstLowerRow = seamRow, tauPx = 80f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "anim_busy")
                    // Draw occurs with !isRunning (or drawReady becomes true), then runnable sees isRunning == true
                    forceRunning = true
                    drawEditorInViewSpace(ed)
                }
                SystemClock.sleep(60L)
                instr.runOnMainSync {
                    val ed = host.canvas.editor
                    assertFalse("Case 2: aw.isEnabled must remain false while cursorAnimator.isRunning", aw.isEnabled)
                    forceRunning = false
                    drawEditorInViewSpace(ed)
                }
                waitUntil(failureMsg = "Case 2: aw.isEnabled must restore after !cursorAnimator.isRunning unsplit draw") {
                    aw.isEnabled
                }
                instr.runOnMainSync {
                    aw.dismiss()
                    host.canvas.editor.setCursorAnimator(realAnimator)
                }
            }

            // Case 3a & 3b: Temporary !hasFocus() and visibility != View.VISIBLE -> recovers when focus/visibility returns without new deletion
            run {
                lateinit var aw: EditorTextActionWindow
                lateinit var focusSink: View
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    focusSink = View(host.container.context).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }
                    host.container.addView(focusSink, FrameLayout.LayoutParams(10, 10))
                    ed.setSelectionRegion(12, 2, 12, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(12, 0))
                    beginSplitCollapse(canvas, gen = 3L, seamLine = 12, seamFirstLowerRow = seamRow, tauPx = 80f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "focus_gate")
                    focusSink.requestFocus()
                    assertFalse(ed.hasFocus())
                    drawEditorInViewSpace(ed)
                }
                SystemClock.sleep(60L)
                instr.runOnMainSync {
                    assertFalse("Case 3a: must not enable/show while !hasFocus()", aw.isEnabled || aw.isShowing)
                    host.canvas.editor.requestFocus()
                    host.container.removeView(focusSink)
                    assertTrue(host.canvas.editor.hasFocus())
                }
                waitUntil(failureMsg = "Case 3a: must restore without new deletion when focus returns") {
                    aw.isEnabled && aw.isShowing
                }
                instr.runOnMainSync { aw.dismiss() }

                // 3b: Temporary visibility == View.INVISIBLE
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    ed.setSelectionRegion(14, 2, 14, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(14, 0))
                    beginSplitCollapse(canvas, gen = 4L, seamLine = 14, seamFirstLowerRow = seamRow, tauPx = 80f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "vis_gate")
                    drawEditorInViewSpace(ed)
                    ed.visibility = View.INVISIBLE
                }
                SystemClock.sleep(60L)
                instr.runOnMainSync {
                    assertFalse("Case 3b: must not enable/show while INVISIBLE", aw.isEnabled || aw.isShowing)
                    host.canvas.editor.visibility = View.VISIBLE
                }
                waitUntil(failureMsg = "Case 3b: must restore without new deletion when visibility returns") {
                    aw.isEnabled
                }
                instr.runOnMainSync { aw.dismiss() }
            }

            // Case 4 & Case 5: Detach before pending runnable runs -> stale runnable never runs, aw.isEnabled restored safely without displayWindow; reattach preserves state cleanly
            run {
                lateinit var aw: EditorTextActionWindow
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    ed.requestFocus()
                    ed.setSelectionRegion(16, 2, 16, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(16, 0))
                    beginSplitCollapse(canvas, gen = 5L, seamLine = 16, seamFirstLowerRow = seamRow, tauPx = 80f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "detach")
                    drawEditorInViewSpace(ed)
                    // Detach BEFORE posted animation runnable executes
                    host.container.removeView(canvas)
                    assertTrue("Case 4: detach must restore original aw.isEnabled == true immediately", aw.isEnabled)
                    assertFalse("Case 4: detach must NOT call displayWindow/show popup while detached", aw.isShowing)
                }
                SystemClock.sleep(80L)
                instr.runOnMainSync {
                    assertFalse("Case 4: stale runnable must not show popup after detach", aw.isShowing)
                    assertTrue("Case 4: aw.isEnabled remains true", aw.isEnabled)
                    // Reattach after detach
                    host.container.addView(host.canvas, FrameLayout.LayoutParams(720, 960))
                    val wSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
                    val hSpec = View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY)
                    host.container.measure(wSpec, hSpec)
                    host.container.layout(0, 0, 720, 960)
                }
                waitUntil(failureMsg = "Case 5: reattach preserves window attached state cleanly") {
                    host.canvas.editor.isAttachedToWindow && aw.isEnabled
                }
            }

            // Case 6: New deletion transaction invalidates previous generation's pending refresh
            run {
                lateinit var aw: EditorTextActionWindow
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    ed.requestFocus()
                    ed.setSelectionRegion(18, 2, 18, 12, false)
                    val seamRow1 = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(18, 0))
                    beginSplitCollapse(canvas, gen = 6L, seamLine = 18, seamFirstLowerRow = seamRow1, tauPx = 80f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "gen1_done")
                    drawEditorInViewSpace(ed)
                    // Immediately start a newer deletion transaction BEFORE gen1's runnable fires
                    beginSplitCollapse(canvas, gen = 7L, seamLine = 18, seamFirstLowerRow = seamRow1, tauPx = 60f)
                    assertFalse("Case 6: aw.isEnabled must stay false when new transaction begins", aw.isEnabled)
                }
                SystemClock.sleep(80L)
                instr.runOnMainSync {
                    assertFalse("Case 6: old generation runnable must NOT re-enable aw during newer transaction", aw.isEnabled || aw.isShowing)
                    host.canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "gen2_done")
                    drawEditorInViewSpace(host.canvas.editor)
                }
                waitUntil(failureMsg = "Case 6: aw.isEnabled restores once newer transaction finishes + draws unsplit") {
                    aw.isEnabled
                }
                instr.runOnMainSync { aw.dismiss() }
            }

            // Case 7: Transaction starting with aw.isEnabled == false keeps aw.isEnabled == false after IDLE + unsplit draw
            run {
                lateinit var aw: EditorTextActionWindow
                instr.runOnMainSync {
                    val canvas = host.canvas
                    val ed = canvas.editor
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    aw.isEnabled = false
                    ed.requestFocus()
                    ed.setSelectionRegion(20, 2, 20, 12, false)
                    val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(20, 0))
                    beginSplitCollapse(canvas, gen = 8L, seamLine = 20, seamFirstLowerRow = seamRow, tauPx = 80f)
                    assertFalse(aw.isEnabled)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "orig_disabled")
                    drawEditorInViewSpace(ed)
                }
                SystemClock.sleep(80L)
                instr.runOnMainSync {
                    assertFalse("Case 7: originally disabled aw.isEnabled must stay false after IDLE + unsplit draw", aw.isEnabled)
                    assertFalse("Case 7: originally disabled aw must never show", aw.isShowing)
                    aw.isEnabled = true
                }
            }
        }
    }
}
