package com.primaloptima.scribe.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.primaloptima.scribe.CrashActivity
import com.primaloptima.scribe.R
import com.primaloptima.scribe.ui.editor.scroll.CollapsePlan
import com.primaloptima.scribe.ui.editor.scroll.CollapseScenario
import com.primaloptima.scribe.ui.editor.scroll.SafetyFloorAndBounds
import com.primaloptima.scribe.ui.editor.scroll.ScribeGapCollapseController
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.line.LineGutterBackground
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Suite C: Instrumentation verification for Phase 4 Single-Lifecycle ScribeEditorRenderer,
 * per-layer disjoint two-pass partitioning, Pass-2 cache warming, all 6 Approach-A firstLn
 * collision/gap/continuation cases, DrawCursorTask + Case-I lower-region handle placement,
 * Option-B cursor-animator fallback + immediate split-render disable, synchronous outer-only
 * Mode-1/Mode-2 invalidation, renderPassRowRangeOverride cleanup, and EditorTextActionWindow
 * post-IDLE unsplit cursor-descriptor refresh gating.
 */
@RunWith(AndroidJUnit4::class)
class ScribeGapCollapseRendererTest {

    private data class RecordedTextOp(
        val text: String,
        val localX: Float,
        val localY: Float,
        val screenX: Float,
        val screenY: Float,
        val translationY: Float,
        val clipScreen: RectF
    )

    private data class RecordedLineOp(
        val startX: Float,
        val startY: Float,
        val stopX: Float,
        val stopY: Float,
        val screenStartY: Float,
        val screenStopY: Float,
        val translationY: Float,
        val clipScreen: RectF
    )

    private class RecordingCanvas(
        width: Int,
        height: Int
    ) : Canvas(Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)) {

        private data class CanvasState(
            val tx: Float,
            val ty: Float,
            val clip: RectF
        )

        private val stack = ArrayDeque<CanvasState>()
        private var currentTx = 0f
        private var currentTy = 0f
        private var currentClip = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val textOps = mutableListOf<RecordedTextOp>()
        val lineOps = mutableListOf<RecordedLineOp>()

        override fun save(): Int {
            val count = super.save()
            stack.addLast(CanvasState(currentTx, currentTy, RectF(currentClip)))
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
            while (stack.size >= saveCount && stack.isNotEmpty()) {
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
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawText(text, index, count, x, y, paint)
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            lineOps.add(
                RecordedLineOp(
                    startX = startX,
                    startY = startY,
                    stopX = stopX,
                    stopY = stopY,
                    screenStartY = startY + currentTy,
                    screenStopY = stopY + currentTy,
                    translationY = currentTy,
                    clipScreen = RectF(currentClip)
                )
            )
            super.drawLine(startX, startY, stopX, stopY, paint)
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
    ): Pair<ActivityScenario<CrashActivity>, UnifiedCanvasLayout> {
        val instr = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instr.targetContext, CrashActivity::class.java)
        val scenario = ActivityScenario.launch<CrashActivity>(intent)
        lateinit var canvas: UnifiedCanvasLayout
        scenario.onActivity { activity ->
            activity.setTheme(R.style.Theme_Scribe)
            canvas = UnifiedCanvasLayout(activity)
            canvas.headerView.setPadding(0, headerHeightPx, 0, 0)
            canvas.editor.apply {
                isWordwrap = wordwrap
                isLineNumberEnabled = lineNumbers
                isFirstLineNumberAlwaysVisible = firstLnAlwaysVisible
                isLineNumberPinned = pinnedLineNumbers
                setTextSize(16f)
                setText(text)
            }
            activity.setContentView(canvas)
            val wSpec = View.MeasureSpec.makeMeasureSpec(viewportWidthPx, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(viewportHeightPx, View.MeasureSpec.EXACTLY)
            canvas.measure(wSpec, hSpec)
            canvas.layout(0, 0, viewportWidthPx, viewportHeightPx)
        }

        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instr.runOnMainSync {
                val ed = canvas.editor
                ready = ed.isLaidOut &&
                    ed.width > 0 &&
                    ed.height > 0 &&
                    ed.layout != null &&
                    (!ed.isWordwrap || (ed.isWordwrapReady() && !ed.isLayoutBusy))
            }
            if (ready) break
            SystemClock.sleep(25L)
        }
        return scenario to canvas
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
            affectsDocumentTail = false
        )
        val epoch = canvas.gapCollapseController.beginCollapse(
            owningGen = canvas.currentDeletionTransactionGeneration.coerceAtLeast(gen),
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
        rec.save()
        rec.translate(-editor.offsetX.toFloat(), -editor.offsetY.toFloat())
        editor.renderer.draw(rec)
        rec.restore()
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

    @Test
    fun testRendererLifecycleDisjointnessCacheWarmAndRowOverrideReset() {
        val lines = (1..120).joinToString("\n") { idx -> "Line $idx with steady prose content." }
        val (scenario, canvas) = launchHost(text = lines, wordwrap = true)
        scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val ed = canvas.editor
                assertTrue(ed.renderer is ScribeEditorRenderer)
                val renderer = ed.renderer as ScribeEditorRenderer

                // Scroll past header into document so normalFirstVis >= 30
                val rowH = ed.rowHeight.coerceAtLeast(20)
                scrollCanvasTo(canvas, canvas.headerHeight + rowH * 30f)
                val normalFirstVis = ed.getFirstVisibleRow()
                val seamRow = normalFirstVis + 3
                val seamLine = ed.layout.getLineNumberForRow(seamRow)
                // Big tau (> 8 rows) ensures Pass 2 exposes rows strictly < normalFirstVis to test cache warming!
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
        // Line 0 is intentionally long so it wraps across 5+ visual rows at 720px width;
        // subsequent lines are short so we can place Pass 2 leading rows precisely.
        val longLine0 = (1..24).joinToString(" ") { "longwrappedtoken$it" }
        val shortLines = (2..60).joinToString("\n") { "S$it short" }
        val doc = "$longLine0\n$shortLines"
        val (scenario, canvas) = launchHost(
            text = doc,
            wordwrap = true,
            lineNumbers = true,
            firstLnAlwaysVisible = true,
            pinnedLineNumbers = false
        )
        scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val ed = canvas.editor
                val renderer = ed.renderer as ScribeEditorRenderer
                val lay = ed.layout
                val line0Rows = lay.getRowCountForLine(0)
                assertTrue("Expected wrapped line 0 (>= 5 rows), got $line0Rows", line0Rows >= 5)
                val rowH = ed.rowHeight.toFloat()
                val h = ed.height.toFloat()
                val divLeft = dividerLeftX(ed)

                // --- Case 1: !P1.isEmpty && !P2.isEmpty && postDrawLineNumbers.isNotEmpty ---
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 1L,
                    seamLine = 3,
                    seamFirstLowerRow = line0Rows + 2,
                    tauPx = rowH * 2f
                )
                val rec1 = drawEditorInViewSpace(ed)
                assertEquals(-1, renderer.lastOverrideFirstLnLine)
                assertEquals(-1, renderer.lastPass2FirstLnDrawnInPass2)
                assertTrue(rec1.textOps.any { it.text == "1" && it.translationY == 0f && it.screenX < divLeft })
                assertTrue(rec1.textOps.any { it.text == "4" && it.translationY == rowH * 2f && it.screenX < divLeft })
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
                scrollCanvasTo(canvas, canvas.headerHeight + line0Rows * rowH + rowH * 0.1f)
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 5L,
                    seamLine = 0,
                    seamFirstLowerRow = line0Rows - 1,
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
        val doc = (0..40).joinToString("\n") { i -> "Line $i { sample text for layers and handle }" }
        val (scenario, canvas) = launchHost(text = doc, wordwrap = true)
        scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            instr.runOnMainSync {
                val ed = canvas.editor
                val rowH = ed.rowHeight.toFloat()
                scrollCanvasTo(canvas, canvas.headerHeight.toFloat())

                // Populate Styles: CodeBlock crossing seam, LineSideIcon in P1 & P2, LineGutterBackground in P2
                val styles = Styles().apply {
                    addCodeBlock(CodeBlock().apply {
                        startLine = 1
                        startColumn = 0
                        endLine = 14
                        endColumn = 5
                        toBottomOfEndLine = true
                    })
                    addLineStyle(LineSideIcon(2, ColorDrawable(0xFF336699.toInt())))
                    addLineStyle(LineSideIcon(12, ColorDrawable(0xFF993366.toInt())))
                    addLineStyle(LineGutterBackground(12, ConstColor(0x4400FF00)))
                    finishBuilding()
                }
                ed.styles = styles

                // Populate Diagnostics in P1 and P2
                val diag = DiagnosticsContainer().apply {
                    val idxP1 = ed.text.getCharIndex(2, 0)
                    val idxP2 = ed.text.getCharIndex(12, 0)
                    addDiagnostic(DiagnosticRegion(idxP1, idxP1 + 4, DiagnosticRegion.SEVERITY_WARNING, 0L, DiagnosticDetail("W", "Warn")))
                    addDiagnostic(DiagnosticRegion(idxP2, idxP2 + 4, DiagnosticRegion.SEVERITY_ERROR, 0L, DiagnosticDetail("E", "Err")))
                }
                ed.diagnosticIndicatorStyle = DiagnosticIndicatorStyle.WAVY_LINE
                ed.diagnostics = diag

                // Case-I lower-region caret at line 12 (strictly below seamLine 6)
                ed.requestFocus()
                ed.setSelection(12, 4, false)
                drawEditorInViewSpace(ed)
                val unsplitDesc = RectF(ed.insertHandleDescriptor.position)
                assertFalse("Unsplit baseline handle descriptor must be populated", unsplitDesc.isEmpty)

                val tau = rowH * 2.5f
                val seamLine = 6
                val seamRow = ed.layout.getRowIndexForPosition(ed.text.getCharIndex(seamLine, 0))
                beginSplitCollapse(
                    canvas = canvas,
                    gen = 1L,
                    seamLine = seamLine,
                    seamFirstLowerRow = seamRow,
                    tauPx = tau
                )
                val rec = drawEditorInViewSpace(ed)
                val splitDesc = RectF(ed.insertHandleDescriptor.position)
                assertEquals("Lower-region handle top must shift by +tauLower ONCE", unsplitDesc.top + tau, splitDesc.top, 0.5f)
                assertEquals("Lower-region handle bottom must shift by +tauLower ONCE", unsplitDesc.bottom + tau, splitDesc.bottom, 0.5f)
                assertTrue("Carets/lines must be recorded during Pass 1 & Pass 2 split frame", rec.lineOps.isNotEmpty())
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "layers")
            }
        }
    }

    @Test
    fun testSyncOuterOnlyInvalidation_OptionBCursorAnimator_AndTextActionWindowGate() {
        val doc = (1..80).joinToString("\n") { "Paragraph $it with enough length to scroll and select cleanly." }
        val (scenario, canvas) = launchHost(text = doc, wordwrap = true, headerHeightPx = 300)
        scenario.use {
            val instr = InstrumentationRegistry.getInstrumentation()
            var invalidateCalls = 0
            lateinit var animator: ScribeCursorAnimator
            lateinit var aw: EditorTextActionWindow

            instr.runOnMainSync {
                val ed = canvas.editor
                aw = ed.getComponent(EditorTextActionWindow::class.java)
                assertTrue("EditorTextActionWindow starts enabled", aw.isEnabled)
                animator = ed.cursorAnimator as ScribeCursorAnimator

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

                // Start cursorAnimator mid-collapse -> triggers Option B handleCursorAnimatorActivatedDuringCollapse()
                animator.triggerGlide(0f, 0f, 120f, 40f, 20f, 20f)
                assertFalse("Split rendering must disable immediately when cursorAnimator starts", canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertFalse("aw.isEnabled must stay false while cursorAnimator is running", aw.isEnabled)

                // Draw while cursorAnimator is STILL running -> didRefreshCursorDescriptorsOnLastDraw == false
                drawEditorInViewSpace(ed)
                assertFalse(renderer.lastSplitFrameWasActive)
                assertFalse("Draw while cursorAnimator running skips DrawCursorTasks", renderer.didRefreshCursorDescriptorsOnLastDraw)
                assertFalse("aw.isEnabled must stay false when DrawCursorTasks were skipped", aw.isEnabled)

                // Finish cursorAnimator, draw next unsplit frame -> didRefreshCursorDescriptorsOnLastDraw == true,
                // posting pendingTextActionRefreshRunnable on next animation frame
                animator.cancel()
                assertFalse(animator.isRunning)
                drawEditorInViewSpace(ed)
                assertTrue(renderer.didRefreshCursorDescriptorsOnLastDraw)
            }

            // Wait for posted animation frame runnable to execute and re-enable + display EditorTextActionWindow
            val deadline = SystemClock.uptimeMillis() + 2_000L
            while (SystemClock.uptimeMillis() < deadline) {
                var enabledNow = false
                instr.runOnMainSync {
                    enabledNow = aw.isEnabled
                }
                if (enabledNow) break
                SystemClock.sleep(16L)
            }
            instr.runOnMainSync {
                assertTrue("EditorTextActionWindow must re-enable after post-IDLE unsplit cursor-descriptor refresh", aw.isEnabled)
                assertTrue("Selected + focused + visible window must display after handle refresh", aw.isShowing)
                aw.dismiss()
                assertNull(canvas.editor.renderPassRowRangeOverride)
            }
        }
    }
}
