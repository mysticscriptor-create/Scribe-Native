package com.primaloptima.scribe.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.primaloptima.scribe.CrashActivity
import com.primaloptima.scribe.R
import com.primaloptima.scribe.ui.editor.scroll.CollapsePlan
import com.primaloptima.scribe.ui.editor.scroll.CollapseScenario
import com.primaloptima.scribe.ui.editor.scroll.GapCollapsePhase
import com.primaloptima.scribe.ui.editor.scroll.LocalOverlayBoundsRegistry
import com.primaloptima.scribe.ui.editor.scroll.OverlayOcclusionRole
import com.primaloptima.scribe.ui.editor.scroll.RegisterOverlayBoundsEffect
import com.primaloptima.scribe.ui.editor.scroll.SafetyFloorAndBounds
import com.primaloptima.scribe.ui.editor.scroll.ScribeDockGeometry
import com.primaloptima.scribe.ui.editor.scroll.ScribeDockMode
import com.primaloptima.scribe.ui.editor.scroll.ScribeDockTransition
import com.primaloptima.scribe.ui.editor.scroll.PreDeleteHandleCaptureStatus
import com.primaloptima.scribe.ui.editor.scroll.ScribeGapCollapsePolicy
import com.primaloptima.scribe.ui.editor.scroll.SelectionDeletionDispatchOutcome
import com.primaloptima.scribe.ui.editor.scroll.ScribeOverlayBoundsRegistry
import com.primaloptima.scribe.ui.editor.scroll.VisibilityConfidence
import com.primaloptima.scribe.ui.editor.scroll.ScribeScrollDeletionPolicy
import com.primaloptima.scribe.ui.editor.scroll.reportOverlayBounds
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScribeGapCollapseIntegrationTest {

    private data class HostHandle(
        val scenario: ActivityScenario<CrashActivity>,
        val container: FrameLayout,
        val canvas: UnifiedCanvasLayout
    )

    private fun buildLines(count: Int): String =
        (1..count).joinToString("\n") { idx ->
            "Paragraph line $idx: verification body content for integrated gap collapse tests."
        }

    private fun launchCanvasHost(
        text: String = buildLines(90),
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
            container = FrameLayout(activity)
            canvas = UnifiedCanvasLayout(activity)
            canvas.headerView.setPadding(0, headerHeightPx, 0, 0)
            canvas.editor.apply {
                isFocusableInTouchMode = true
                isWordwrap = true
                isLineNumberEnabled = true
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
                    ed.isWordwrapReady() &&
                    !ed.isLayoutBusy
            }
            if (ready) break
            SystemClock.sleep(20L)
        }
        return HostHandle(scenario, container, canvas)
    }

    private fun waitUntil(timeoutMs: Long = 3_500L, failureMsg: String, condition: () -> Boolean) {
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

    private fun drawEditorIntoBitmap(editor: ScribeCodeEditor) {
        val w = editor.width.coerceAtLeast(1)
        val h = editor.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-editor.scrollX.toFloat(), -editor.scrollY.toFloat())
        editor.renderer.draw(c)
        bmp.recycle()
    }

    private fun startCollapseTransaction(
        canvas: UnifiedCanvasLayout,
        seamLine: Int,
        tauLowerPx: Float,
        requiresMode1: Boolean,
        safeMinPhysical: Int = 0,
        safeMax: Int? = null,
        effectiveTargetOverride: Float? = null,
        scenario: CollapseScenario = CollapseScenario.B_LOWER_ONLY
    ): Pair<Long, Long> {
        val ed = canvas.editor
        val lay = ed.layout!!
        val seamRow = lay.getRowIndexForPosition(ed.text.getCharIndex(seamLine, 0))
        val rowH = ed.rowHeight.toFloat().coerceAtLeast(1f)
        val actual = canvas.scrollD + ed.offsetY
        val boundMax = safeMax ?: canvas.maxCanvasScroll
        assertTrue(
            "Safety bounds must form a valid interval: [$safeMinPhysical, $boundMax]",
            safeMinPhysical <= boundMax
        )
        val rawTarget = effectiveTargetOverride ?: if (requiresMode1) {
            actual - tauLowerPx * 0.5f
        } else {
            actual.toFloat()
        }
        val target = rawTarget.coerceIn(safeMinPhysical.toFloat(), boundMax.toFloat())
        val gen = canvas.beginPreEditDeletionSnapshot(
            isSelection = true,
            selectionStartLine = (seamLine - 2).coerceAtLeast(0),
            selectionEndLine = seamLine,
            preEditDocLineCount = ed.text.lineCount + 2,
            preEditDocHeight = (canvas.headerHeight + lay.layoutHeight).toFloat(),
            preEditScrollY = actual.toFloat(),
            preEditCaretScreenY = 220f,
            wasAtTail = false,
            affectsDocumentTail = false,
            markPendingSettlement = false
        )
        val plan = CollapsePlan(
            scenario = scenario,
            requestedTargetScroll = target,
            effectiveTargetScroll = target,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = boundMax,
                minSafeScroll = safeMinPhysical.toFloat(),
                safeMinPhysical = safeMinPhysical,
                actualPhysicalScroll = actual
            ),
            deltaGapPx = tauLowerPx.coerceAtLeast(1f),
            preScrollY = actual,
            requiresMode1 = requiresMode1,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = actual,
            initialTauLowerPx = tauLowerPx,
            isProgressCoupled = when (scenario) {
                CollapseScenario.A_UPPER_ONLY -> actual < safeMinPhysical
                CollapseScenario.D_SELECTION_ONLY_CENTERED -> true
                CollapseScenario.B_LOWER_ONLY,
                CollapseScenario.C_BOTH_VISIBLE,
                CollapseScenario.INELIGIBLE_FALLBACK,
                CollapseScenario.E_OFFSCREEN_CARET_FOLLOW -> false
            },
            requiresOffscreenCaretFollow = false
        )
        val epoch = canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = seamLine,
            seamFirstLowerRow = seamRow,
            seamDocY = seamRow * rowH,
            rowHeightPx = rowH,
            initialPhysicalScrollPx = actual.toFloat()
        )
        if (requiresMode1) {
            canvas.gapCollapseController.markMode1Started(gen, epoch, actual.toFloat())
        }
        canvas.syncActiveSeamBaselineFromController()
        if (canvas.gapCollapseController.shouldApplySplitRender) {
            ed.onGapCollapseSplitEntered()
        }
        return gen to epoch
    }

    @Test
    fun suiteD_pointerFreezeDualDownDragFlingAndResumeAcrossCases1To6() {
        val host = launchCanvasHost()
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val events = mutableListOf<MotionEvent>()
            try {
                // Cases 1–4: Mode 1 -> ACTION_DOWN freeze -> dual ACTION_DOWN -> drag -> ACTION_UP -> Mode 2 -> IDLE
                instr.runOnMainSync {
                    canvas.editor.isCursorAnimationEnabled = false
                    canvas.scrollCanvasBy(420f)
                    val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen, _) = startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 22,
                        tauLowerPx = rowH * 4f,
                        requiresMode1 = true
                    )
                    assertTrue(
                        canvas.startAuthoritativeScrollAnimation(
                            targetTotalScroll = (canvas.scrollD + canvas.editor.offsetY - rowH * 2f).coerceAtLeast(0f),
                            reason = com.primaloptima.scribe.ui.editor.scroll.ScrollAnimationReason.DELETION_ANCHOR,
                            durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                            forceRetarget = true
                        )
                    )
                    assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)

                    val t0 = SystemClock.uptimeMillis()
                    val down1 = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 300f, 500f, 0).also { events.add(it) }
                    canvas.dispatchTouchEvent(down1)
                    assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                    assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
                    assertFalse(canvas.isGlideRunning)

                    // Dual ACTION_DOWN while already POINTER_FROZEN keeps transaction & tauLower
                    val down2 = MotionEvent.obtain(t0 + 10L, t0 + 10L, MotionEvent.ACTION_DOWN, 305f, 500f, 0).also { events.add(it) }
                    canvas.dispatchTouchEvent(down2)
                    assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)

                    // Drag + release -> resumes Mode 2 cleanly
                    val move1 = MotionEvent.obtain(t0, t0 + 25L, MotionEvent.ACTION_MOVE, 300f, 440f, 0).also { events.add(it) }
                    val move2 = MotionEvent.obtain(t0, t0 + 45L, MotionEvent.ACTION_MOVE, 300f, 380f, 0).also { events.add(it) }
                    val up1 = MotionEvent.obtain(t0, t0 + 60L, MotionEvent.ACTION_UP, 300f, 360f, 0).also { events.add(it) }
                    canvas.dispatchTouchEvent(move1)
                    canvas.dispatchTouchEvent(move2)
                    assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                    canvas.dispatchTouchEvent(up1)
                    assertTrue(
                        canvas.gapCollapseController.phase == GapCollapsePhase.MODE_2_VISUAL_SETTLING ||
                            canvas.gapCollapseController.phase == GapCollapsePhase.FINAL_SAFETY_PENDING ||
                            canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                    )
                }
                waitUntil(failureMsg = "Mode 2 must converge cleanly to IDLE after pointer resume") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }

                // Case 5: Zero-tau POINTER_FROZEN resumes directly to FINAL_SAFETY_PENDING -> IDLE on ACTION_UP
                instr.runOnMainSync {
                    val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen5, epoch5) = startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 22,
                        tauLowerPx = 0f,
                        requiresMode1 = false
                    )
                    assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, canvas.gapCollapseController.phase)
                    assertTrue(gen5 >= 0L && epoch5 >= 0L)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c5_prep")

                    val (genZero, _) = startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 22,
                        tauLowerPx = rowH * 2f,
                        requiresMode1 = true,
                        effectiveTargetOverride = (canvas.scrollD + canvas.editor.offsetY).toFloat()
                    )
                    assertTrue(canvas.gapCollapseController.freezeForPointer(genZero))
                    assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                    val t = SystemClock.uptimeMillis()
                    val upZero = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, 250f, 450f, 0).also { events.add(it) }
                    canvas.dispatchTouchEvent(upZero)
                }
                waitUntil(failureMsg = "Zero-tau POINTER_FROZEN release must converge to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }

                // Case 6: POINTER_FROZEN entered during MODE_2_VISUAL_SETTLING freezes & resumes to IDLE
                instr.runOnMainSync {
                    val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen6, epoch6) = startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 22,
                        tauLowerPx = rowH * 4f,
                        requiresMode1 = true
                    )
                    canvas.gapCollapseController.onMode1SettledOrHandoff(
                        expectedGen = gen6,
                        expectedCollapseEpoch = epoch6,
                        actualFinalPhysicalScrollPx = (canvas.scrollD + canvas.editor.offsetY).toFloat()
                    )
                    assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
                    val t = SystemClock.uptimeMillis()
                    val down6 = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 220f, 460f, 0).also { events.add(it) }
                    val up6 = MotionEvent.obtain(t, t + 20L, MotionEvent.ACTION_UP, 220f, 460f, 0).also { events.add(it) }
                    canvas.dispatchTouchEvent(down6)
                    assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                    canvas.dispatchTouchEvent(up6)
                }
                waitUntil(failureMsg = "Resumed Mode 2 must converge to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }
            } finally {
                events.forEach { it.recycle() }
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteD_pointer_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteD_overlayBoundsRegistryLifecycleAndAttachedHiddenGuard() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instr.targetContext, CrashActivity::class.java)
        ActivityScenario.launch<CrashActivity>(intent).use { scenario ->
            val registry = ScribeOverlayBoundsRegistry()
            var isVisible by mutableStateOf(true)
            var showHost by mutableStateOf(true)
            var useCompactLayout by mutableStateOf(true)
            var overlayKey by mutableStateOf("floating_pill_v1")
            var offsetPx by mutableIntStateOf(0)
            var attachedHiddenPositionedCalls = 0

            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalOverlayBoundsRegistry provides registry) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (showHost) {
                                val activeKey = overlayKey
                                RegisterOverlayBoundsEffect(
                                    key = activeKey,
                                    isVisible = isVisible,
                                    registry = registry
                                )
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, offsetPx) }
                                        .size(180.dp, 48.dp)
                                        .onGloballyPositioned { coords ->
                                            if (!isVisible && coords.isAttached) {
                                                attachedHiddenPositionedCalls++
                                            }
                                        }
                                        .reportOverlayBounds(
                                            key = activeKey,
                                            role = OverlayOcclusionRole.FLOATING_PILL,
                                            isVisible = isVisible,
                                            registry = registry
                                        )
                                )
                            }

                            if (useCompactLayout) {
                                RegisterOverlayBoundsEffect("compact_bar", true, registry)
                                Box(
                                    modifier = Modifier
                                        .size(300.dp, 56.dp)
                                        .reportOverlayBounds("compact_bar", OverlayOcclusionRole.TOP_BAR, true, registry)
                                )
                            } else {
                                RegisterOverlayBoundsEffect("expanded_bar", true, registry)
                                Box(
                                    modifier = Modifier
                                        .size(300.dp, 64.dp)
                                        .reportOverlayBounds("expanded_bar", OverlayOcclusionRole.TOP_BAR, true, registry)
                                )
                            }
                        }
                    }
                }
            }

            waitUntil(failureMsg = "Initial visible overlay and compact bar must register") {
                registry.contains("floating_pill_v1") && registry.contains("compact_bar")
            }

            // 1. Key replacement removes old key and registers replacement key
            instr.runOnMainSync { overlayKey = "floating_pill_v2" }
            waitUntil(failureMsg = "Key replacement must remove old key and register new key") {
                !registry.contains("floating_pill_v1") && registry.contains("floating_pill_v2")
            }

            // 2. Layout branch switch removes compact key and registers expanded key
            instr.runOnMainSync { useCompactLayout = false }
            waitUntil(failureMsg = "Layout switch must remove old branch key") {
                !registry.contains("compact_bar") && registry.contains("expanded_bar")
            }

            // 3. Attached-but-hidden composable removes key and NEVER re-registers on onGloballyPositioned
            instr.runOnMainSync { isVisible = false }
            waitUntil(failureMsg = "Hiding overlay must remove its key immediately") {
                !registry.contains("floating_pill_v2")
            }
            val prevCalls = attachedHiddenPositionedCalls
            instr.runOnMainSync { offsetPx = 24 }
            waitUntil(failureMsg = "Attached-but-hidden relayout must fire onGloballyPositioned") {
                attachedHiddenPositionedCalls > prevCalls
            }
            instr.runOnMainSync {
                assertFalse(
                    "Attached-but-hidden composable must NOT re-register stale bounds onGloballyPositioned",
                    registry.contains("floating_pill_v2")
                )
            }

            // 4. Re-show then composition exit removes key via DisposableEffect
            instr.runOnMainSync { isVisible = true }
            waitUntil(failureMsg = "Re-shown overlay must re-register") {
                registry.contains("floating_pill_v2")
            }
            instr.runOnMainSync { showHost = false }
            waitUntil(failureMsg = "Composition exit must dispose overlay bounds key") {
                !registry.contains("floating_pill_v2")
            }
        }
    }

    @Test
    fun suiteD_textActionWindowStaysDisabledThroughIdleUntilUnsplitDrawCompletes() {
        val host = launchCanvasHost()
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            lateinit var aw: EditorTextActionWindow
            var preDrawSeq = 0L
            try {
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    aw = ed.getComponent(EditorTextActionWindow::class.java)
                    aw.isEnabled = true
                    ed.setSelectionRegion(14, 2, 14, 14, false)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(canvas, seamLine = 15, tauLowerPx = rowH * 3f, requiresMode1 = false)
                    assertFalse("TextActionWindow must be disabled on split entry", aw.isEnabled)

                    // Reach IDLE: aw.isEnabled MUST stay false prior to unsplit draw pass
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "idle_gate")
                    assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                    assertFalse("aw.isEnabled must remain false in IDLE before unsplit draw", aw.isEnabled)
                    assertFalse("aw must not show prior to unsplit draw", aw.isShowing)

                    val renderer = ed.scribeEditorRenderer!!
                    preDrawSeq = renderer.renderedDrawPassSeq

                    // Invoke explicit stale displayWindow() on UI thread BEFORE returning control to Android draw traversal
                    aw.displayWindow()
                    assertFalse("aw.isEnabled must remain false when stale displayWindow() runs while disabled", aw.isEnabled)
                    assertFalse("Stale displayWindow() while disabled must be blocked", aw.isShowing)

                    // Trigger genuine eligible unsplit draw with !cursorAnimator.isRunning
                    drawEditorIntoBitmap(ed)
                    assertTrue(
                        "renderedDrawPassSeq must advance after eligible unsplit draw",
                        renderer.renderedDrawPassSeq > preDrawSeq
                    )
                    assertTrue(
                        "Cursor descriptors must refresh on eligible unsplit draw",
                        renderer.didRefreshCursorDescriptorsOnLastDraw
                    )
                }
                waitUntil(failureMsg = "aw.isEnabled must re-enable after unsplit !cursorAnimator.isRunning() draw") {
                    val renderer = ed.scribeEditorRenderer
                    renderer != null &&
                        renderer.renderedDrawPassSeq > preDrawSeq &&
                        renderer.didRefreshCursorDescriptorsOnLastDraw &&
                        aw.isEnabled
                }
                instr.runOnMainSync { aw.dismiss() }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteD_aw_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteD_serializedHeightResizeAndScrollDFloatInvariant() {
        val host = launchCanvasHost(text = buildLines(130))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            try {
                // Verify scrollDFloat separation and dh > 0 shrink serialization
                instr.runOnMainSync {
                    canvas.editor.isCursorAnimationEnabled = false
                    canvas.scrollCanvasBy(480f)
                    assertTrue(canvas.scrollD > 0)
                    assertTrue(canvas.editor.offsetY > 0)
                    assertEquals(
                        canvas.scrollD.toFloat() + canvas.outerScrollResidualPxForTest,
                        canvas.outerScrollDFloatForTest,
                        0.001f
                    )
                    assertTrue(
                        kotlin.math.abs(canvas.outerScrollDFloatForTest - (canvas.scrollD + canvas.editor.offsetY)) > 1f
                    )

                    val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(canvas, seamLine = 25, tauLowerPx = rowH * 4f, requiresMode1 = true)
                    canvas.isKeyboardVisible = true
                    canvas.measure(
                        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY)
                    )
                    canvas.layout(0, 0, 720, 640)
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                    assertEquals(
                        canvas.scrollD.toFloat() + canvas.outerScrollResidualPxForTest,
                        canvas.outerScrollDFloatForTest,
                        0.001f
                    )
                }
                waitUntil(failureMsg = "Serialized dh > 0 resize must finish Event B to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }

                // dh < 0 (viewport expanding while dock remains active -> contraction follow serialized cleanly)
                instr.runOnMainSync {
                    canvas.applyDockGeometry(
                        ScribeDockGeometry(
                            dockMode = ScribeDockMode.IME,
                            activeTransition = ScribeDockTransition.DRAWER_TO_IME_HANDOFF,
                            transitionGeneration = 1L,
                            standardKeyboardBaselinePx = 220f,
                            currentImeObstructionPx = 220f,
                            currentDrawerHeightPx = 0f,
                            shortcutBarHeightPx = 48f,
                            isShortcutBarOccupied = true,
                            physicalDockClearanceAppliedPx = 0f,
                            virtualTailPx = 300f
                        )
                    )
                    val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(canvas, seamLine = 25, tauLowerPx = rowH * 3f, requiresMode1 = false)
                    canvas.measure(
                        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(880, View.MeasureSpec.EXACTLY)
                    )
                    canvas.layout(0, 0, 720, 880)
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                }
                waitUntil(failureMsg = "Serialized dh < 0 resize must finish Event B to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteD_resize_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_contentReplaceOverscrollNonOverscrollDelayedLayoutAndEmptyReplace() {
        val host = launchCanvasHost(text = buildLines(100))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. Non-empty replacement while NOT overscrolled + delayed LayoutStateChangeEvent
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = false
                    canvas.scrollCanvasBy(320f)
                    val prePhysical = canvas.scrollD + ed.offsetY
                    ed.setSelectionRegion(10, 0, 14, 0, false)
                    ed.text.replace(10, 0, 14, 0, "Replaced paragraph line\n")
                    assertFalse("Split collapse must NOT start on non-empty replace", canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse(ed.isInContentReplace)
                    assertEquals("Physical scroll origin preserved when not overscrolled", prePhysical, canvas.scrollD + ed.offsetY)
                    // Simulate delayed LayoutStateChangeEvent(isLayoutBusy = false)
                    ed.dispatchEvent(LayoutStateChangeEvent(ed, false))
                }
                SystemClock.sleep(80L)
                instr.runOnMainSync {
                    assertFalse("Delayed LayoutStateChangeEvent must not trigger gap collapse", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                }

                // 2. Non-empty replacement while OVERSCROLLED (ClearTransactionAndClampOverscroll) + delayed LayoutStateChangeEvent
                instr.runOnMainSync {
                    canvas.scrollCanvasBy(canvas.maxCanvasScroll.toFloat() - (canvas.scrollD + ed.offsetY))
                    val preReplaceScroll = canvas.scrollD + ed.offsetY
                    val targetReplaceEnd = ed.text.lineCount - 1
                    val targetReplaceStart = (targetReplaceEnd - 25).coerceAtLeast(2)
                    ed.setSelectionRegion(targetReplaceStart, 0, targetReplaceEnd, 0, false)
                    ed.text.replace(targetReplaceStart, 0, targetReplaceEnd, 0, "Short replacement")
                    assertTrue(
                        "Shrinking document by 25 lines must create overscroll ($preReplaceScroll > ${canvas.maxCanvasScroll})",
                        preReplaceScroll > canvas.maxCanvasScroll
                    )
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                    assertFalse(canvas.isDeletionTransactionActive)
                    assertFalse(canvas.hasPendingDeletionSettlement)
                    ed.dispatchEvent(LayoutStateChangeEvent(ed, false))
                }
                waitUntil(failureMsg = "Overscrolled non-empty replacement must clamp within maxCanvasScroll without gap collapse") {
                    !canvas.isGlideRunning &&
                        (canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.gapCollapseController.shouldApplySplitRender &&
                        !canvas.isDeletionTransactionActive &&
                        ed.isWordwrapReady() &&
                        !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                    val lay = ed.layout!!
                    assertTrue(lay.rowCount >= ed.text.lineCount)
                    assertEquals(0, lay.getLineNumberForRow(0))
                    assertEquals(ed.text.lineCount - 1, lay.getLineNumberForRow(lay.rowCount - 1))
                    assertTrue((canvas.scrollD + ed.offsetY) in 0..canvas.maxCanvasScroll)
                }

                // 3. Empty replacement ("") while IDLE settles cleanly via SettleEmptyReplaceDelete
                instr.runOnMainSync {
                    ed.setText(buildLines(90))
                }
                waitUntil(failureMsg = "Reset wordwrap layout ready") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    canvas.scrollCanvasBy(360f)
                    val preScroll = canvas.scrollD + ed.offsetY
                    ed.setSelectionRegion(12, 0, 20, 0, false)
                    ed.text.replace(12, 0, 20, 0, "")
                    assertTrue(preScroll >= 300)
                }
                waitUntil(failureMsg = "Empty replace from IDLE must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive &&
                        ed.isWordwrapReady() &&
                        !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                    val lay = ed.layout!!
                    assertTrue(lay.rowCount >= ed.text.lineCount)
                    assertEquals(0, lay.getLineNumberForRow(0))
                    assertEquals(ed.text.lineCount - 1, lay.getLineNumberForRow(lay.rowCount - 1))
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_replace_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_wordwrapAsyncGutterShrinkAndSubstantialLineCountDropRendersCleanly() {
        val host = launchCanvasHost(text = buildLines(120))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // Case 1: Cross 3-digit -> 2-digit gutter boundary via replace + draw (triggers EditorRenderer.createLayout(false)),
                // then immediately shrink substantially near the bottom while async wordwrap is computing.
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = false
                    ed.text.replace(0, 0, 21, 0, "Header line\n") // 120 - 21 + 1 = 100 lines
                    ed.text.replace(0, 0, 2, 0, "Trimmed") // 100 -> 98 lines (3-digit -> 2-digit gutter change)
                    drawEditorIntoBitmap(ed) // triggers EditorRenderer.drawView cachedGutterWidth != gutterWidth -> createLayout(false)
                    canvas.scrollCanvasBy((canvas.maxCanvasScroll - (canvas.scrollD + ed.offsetY)).toFloat())
                    val endLine = ed.text.lineCount - 1
                    ed.setSelectionRegion(39, 0, endLine, 0, false)
                    ed.text.replace(39, 0, endLine, 0, "Short tail")
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Substantial shrink across digit boundary must settle wordwrap and clamp scroll cleanly") {
                    !canvas.isGlideRunning &&
                        (canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive &&
                        ed.isWordwrapReady() &&
                        !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                    val lay = ed.layout!!
                    assertEquals(40, ed.text.lineCount)
                    assertTrue(lay.rowCount >= ed.text.lineCount)
                    assertEquals(0, lay.getLineNumberForRow(0))
                    assertEquals(ed.text.lineCount - 1, lay.getLineNumberForRow(lay.rowCount - 1))
                    for (r in ed.getFirstVisibleRow()..ed.getLastVisibleRow()) {
                        val ln = lay.getLineNumberForRow(r)
                        assertTrue("Visible row $r mapped to out-of-bounds line $ln", ln in 0 until ed.text.lineCount)
                    }
                    assertTrue((canvas.scrollD + ed.offsetY) in 0..canvas.maxCanvasScroll)
                }

                // Case 2: Large doc scrolled to bottom, explicit async relayout in flight, shrink across 3-digit -> 1-digit (120 -> 9 lines)
                instr.runOnMainSync {
                    ed.setText(buildLines(120))
                }
                waitUntil(failureMsg = "120-line document wordwrap ready") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    canvas.scrollCanvasBy((canvas.maxCanvasScroll - (canvas.scrollD + ed.offsetY)).toFloat())
                    ed.horizontalPaddingDp = 12f // triggers createLayout(false) -> async WordwrapAnalyzeTask(0..119) while scrolled to bottom
                    val endLine = ed.text.lineCount - 1
                    ed.setSelectionRegion(8, 0, endLine, 0, false)
                    ed.text.replace(8, 0, endLine, 0, "Final surviving line")
                    drawEditorIntoBitmap(ed)
                }
                SystemClock.sleep(50L)
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "120->9 line shrink during async wordwrap must clamp and settle cleanly") {
                    !canvas.isGlideRunning &&
                        (canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive &&
                        ed.isWordwrapReady() &&
                        !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                    val lay = ed.layout!!
                    assertEquals(9, ed.text.lineCount)
                    assertTrue(lay.rowCount >= ed.text.lineCount)
                    assertEquals(0, lay.getLineNumberForRow(0))
                    assertEquals(ed.text.lineCount - 1, lay.getLineNumberForRow(lay.rowCount - 1))
                    assertTrue((canvas.scrollD + ed.offsetY) in 0..canvas.maxCanvasScroll)
                }

                // Case 3: Large doc scrolled to bottom, async relayout in flight, empty-replacement deletion (100 -> 30 lines)
                instr.runOnMainSync {
                    ed.setText(buildLines(100))
                }
                waitUntil(failureMsg = "100-line document wordwrap ready") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    canvas.scrollCanvasBy((canvas.maxCanvasScroll - (canvas.scrollD + ed.offsetY)).toFloat())
                    ed.horizontalPaddingDp = 10f // triggers createLayout(false) -> async WordwrapAnalyzeTask(0..99) while scrolled to bottom
                    ed.setSelectionRegion(29, 0, 99, 0, false)
                    ed.text.replace(29, 0, 99, 0, "")
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "100->30 empty replace during async wordwrap must settle cleanly") {
                    !canvas.isGlideRunning &&
                        (canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive &&
                        ed.isWordwrapReady() &&
                        !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                    val lay = ed.layout!!
                    assertEquals(30, ed.text.lineCount)
                    assertTrue(lay.rowCount >= ed.text.lineCount)
                    assertEquals(0, lay.getLineNumberForRow(0))
                    assertEquals(ed.text.lineCount - 1, lay.getLineNumberForRow(lay.rowCount - 1))
                    assertTrue((canvas.scrollD + ed.offsetY) in 0..canvas.maxCanvasScroll)
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_async_shrink_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_offscreenCaretFollowScenarioEAndEnsureCursorVisible() {
        val host = launchCanvasHost(text = buildLines(95))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            var preScroll = 0
            try {
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    val aw = ed.getComponent(EditorTextActionWindow::class.java)
                    aw.isEnabled = true

                    // Select lines 2..6 (leaving surviving upper 0..1 and lower 7..94), then scroll far down
                    // below the selection while staying well clear of the bottom tail (CARET_ANCHOR intent).
                    ed.setSelectionRegion(2, 0, 6, 0, false)
                    val targetScroll = canvas.maxCanvasScroll * 0.55f
                    canvas.scrollCanvasBy(targetScroll - (canvas.scrollD + ed.offsetY))
                    preScroll = canvas.scrollD + ed.offsetY

                    val lay = ed.layout!!
                    val viewportBounds = canvas.resolveUnobstructedViewportBounds()
                    val selGeom = canvas.getSelectionGeometry(ed.cursor, lay)!!
                    val preSelBottomScreen = canvas.headerHeight - preScroll + selGeom.bottomDocY
                    assertTrue(
                        "Selection and seam rows must be strictly offscreen above visibleEditorTop ($preSelBottomScreen <= ${viewportBounds.visibleEditorTop})",
                        preSelBottomScreen + ed.rowHeight <= viewportBounds.visibleEditorTop
                    )

                    val policyPlan = ScribeGapCollapsePolicy.planCollapse(
                        deletionIntent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
                        isSelection = true,
                        isWordwrap = true,
                        isWordwrapReady = true,
                        isLayoutBusy = false,
                        isComposing = false,
                        isCursorAnimationRunning = false,
                        hasRtlOrBidiAtSeam = false,
                        isMergedPartialRowSeam = false,
                        hasSurvivingUpper = true,
                        hasSurvivingLower = true,
                        deltaGapPx = (selGeom.bottomDocY - selGeom.topDocY).coerceAtLeast(ed.rowHeight.toFloat()),
                        visibilityConfidence = VisibilityConfidence.RESOLVED,
                        isUpperVisible = false,
                        isLowerVisible = false,
                        isTouchedOnScreen = false,
                        preScrollY = preScroll,
                        centeredTargetScroll = 0f,
                        viewportBounds = viewportBounds,
                        safetyBounds = canvas.resolveSafetyFloorAndBounds(selGeom.bottomDocY, viewportBounds = viewportBounds)
                    )
                    assertEquals(CollapseScenario.E_OFFSCREEN_CARET_FOLLOW, policyPlan.scenario)
                    assertTrue(policyPlan.requiresOffscreenCaretFollow)
                    assertFalse(policyPlan.requiresMode1)
                    assertFalse(policyPlan.isProgressCoupled)
                    assertEquals(0f, policyPlan.initialTauLowerPx, 0.001f)

                    // Trigger real offscreen selection deletion on the host -> UnifiedCanvasLayout Scenario E branch
                    ed.deleteText()

                    assertFalse("Scenario E must not enter split rendering", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                    assertFalse(canvas.gapCollapseController.isTransactionOwned)
                    assertFalse("Scenario E must claim and clear deletion transaction", canvas.isDeletionTransactionActive)
                    assertFalse(canvas.hasPendingDeletionSettlement)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_SCENARIO_E_CARET_FOLLOW,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                    assertEquals(CollapseScenario.E_OFFSCREEN_CARET_FOLLOW, canvas.lastSelectionDeletionDiagnostic?.scenario)
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                    assertTrue("TextActionWindow must remain enabled (not stranded) in Scenario E", aw.isEnabled)
                }

                waitUntil(failureMsg = "Scenario E must scroll offscreen caret back into view and remain IDLE") {
                    !canvas.isGlideRunning &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive &&
                        (canvas.scrollD + ed.offsetY) < preScroll
                }

                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val cur = ed.cursor
                    val finalPhysical = canvas.scrollD + ed.offsetY
                    val caretGeom = canvas.getCharGeometry(lay, cur.leftLine, cur.leftColumn, ed.rowHeight.toFloat())
                    val finalCaretScreenBottom = canvas.headerHeight + caretGeom.bottomDocY - finalPhysical
                    assertTrue("Final scroll ($finalPhysical) must move up from preScroll ($preScroll)", finalPhysical < preScroll)
                    assertTrue(
                        "Caret screen bottom ($finalCaretScreenBottom) must be brought inside viewport [0, ${canvas.height}]",
                        finalCaretScreenBottom >= 0f && finalCaretScreenBottom <= canvas.height.toFloat()
                    )
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_scenarioE_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_aMaxClampedEventBSafetyReadBackAndCursorAnimatorCases1To4() {
        val host = launchCanvasHost(text = buildLines(95))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // A) A-MaxClamped (CollapseScenario.A_UPPER_ONLY with S_pre > safeMax) + Event B safety bounds read-back
                var expectedSafeBounds: SafetyFloorAndBounds? = null
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = false
                    canvas.scrollCanvasBy(500f)
                    val lay = ed.layout!!
                    ed.setSelection(18, 0, false)
                    val caretBottom = canvas.getCharGeometry(lay, 18, 0, ed.rowHeight.toFloat()).bottomDocY
                    val tightMax = 380
                    val bounds = canvas.resolveSafetyFloorAndBounds(
                        anchorDocBottom = caretBottom,
                        maxCanvasScrollPost = tightMax
                    )
                    expectedSafeBounds = bounds
                    assertTrue(bounds.safeMinPhysical <= bounds.safeMax)
                    assertTrue(
                        "A-MaxClamped fixture must start overscrolled (${bounds.actualPhysicalScroll} > ${bounds.safeMax})",
                        bounds.actualPhysicalScroll > bounds.safeMax
                    )
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 18,
                        tauLowerPx = rowH * 3f,
                        requiresMode1 = true,
                        safeMinPhysical = bounds.safeMinPhysical,
                        safeMax = bounds.safeMax,
                        effectiveTargetOverride = bounds.safeMax.toFloat(),
                        scenario = CollapseScenario.A_UPPER_ONLY
                    )
                    val activePlan = canvas.gapCollapseController.currentPlan!!
                    assertEquals(CollapseScenario.A_UPPER_ONLY, activePlan.scenario)
                    assertFalse(activePlan.isProgressCoupled)
                    assertEquals(bounds.safeMax.toFloat(), activePlan.effectiveTargetScroll, 0.001f)
                    val settled = canvas.gapCollapseController.onMode1SettledOrHandoff(
                        expectedGen = canvas.gapCollapseController.owningDeletionGen,
                        expectedCollapseEpoch = canvas.gapCollapseController.collapseAnimEpoch,
                        actualFinalPhysicalScrollPx = bounds.safeMax.toFloat()
                    )
                    assertTrue(
                        settled == GapCollapsePhase.MODE_2_VISUAL_SETTLING ||
                            settled == GapCollapsePhase.FINAL_SAFETY_PENDING ||
                            settled == GapCollapsePhase.IDLE
                    )
                }
                waitUntil(failureMsg = "A-MaxClamped must converge through Event B to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }
                instr.runOnMainSync {
                    val observedPhysical = canvas.scrollD + ed.offsetY
                    val bounds = canvas.resolveSafetyFloorAndBounds(
                        anchorDocBottom = canvas.getCharGeometry(ed.layout!!, 18, 0, ed.rowHeight.toFloat()).bottomDocY
                    )
                    assertTrue(
                        "Observed integer scroll ($observedPhysical) must lie in [${bounds.safeMinPhysical}, ${bounds.safeMax}]",
                        observedPhysical in bounds.safeMinPhysical..bounds.safeMax
                    )
                    assertTrue(expectedSafeBounds != null)
                }

                // B) CursorAnimator Case 1: Idle at deletion start + Case-I lower insertion handle hit-testing via actual descriptor
                instr.runOnMainSync {
                    // Position viewport near top of document so unshifted and +tauLower shifted caret on line 3 stay inside viewport
                    canvas.scrollCanvasBy(canvas.headerHeight.toFloat() - (canvas.scrollD + ed.offsetY))
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    ed.setSoftKeyboardEnabled(false)
                    assertFalse("Soft keyboard must remain disabled for deterministic handle descriptor check", ed.isSoftKeyboardEnabled)
                    assertTrue("Editor must retain focus for handle check", ed.isFocused)
                    assertTrue("Editor must remain editable for handle check", ed.isEditable)
                    assertFalse("Editor must not be layoutBusy for handle check", ed.isLayoutBusy)

                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val tau = rowH * 3f
                    ed.setSelection(3, 0, false)
                    ed.cursorAnimator.cancel()
                    ed.eventHandler.notifyLater()
                    assertTrue("Insert handle timer must be armed before baseline draw", ed.eventHandler.shouldDrawInsertHandle())

                    drawEditorIntoBitmap(ed)
                    val unsplitDesc = RectF(ed.insertHandleDescriptor.position)
                    assertFalse("Unsplit insertion handle descriptor must be non-empty", unsplitDesc.isEmpty)

                    startCollapseTransaction(canvas, seamLine = 3, tauLowerPx = tau, requiresMode1 = false)
                    drawEditorIntoBitmap(ed)
                    val splitDesc = RectF(ed.insertHandleDescriptor.position)
                    assertFalse("Split Pass-2 insertion handle descriptor must be non-empty", splitDesc.isEmpty)
                    assertEquals("Pass-2 insertion handle top must shift by +tauLower ONCE", unsplitDesc.top + tau, splitDesc.top, 0.5f)
                    assertEquals("Pass-2 insertion handle bottom must shift by +tauLower ONCE", unsplitDesc.bottom + tau, splitDesc.bottom, 0.5f)
                    assertTrue(
                        "Shifted insertion handle ($splitDesc) must lie inside editor viewport (0..${ed.width}, 0..${ed.height})",
                        splitDesc.centerX() in 0f..ed.width.toFloat() &&
                            splitDesc.top >= 0f &&
                            splitDesc.bottom <= ed.height.toFloat()
                    )

                    assertTrue(
                        "Hit testing at actual shifted descriptor must return true",
                        ed.isTouchOnHandle(splitDesc.centerX(), splitDesc.top + 6f)
                    )
                    assertFalse(
                        "Hit testing at old unshifted descriptor must return false",
                        ed.isTouchOnHandle(unsplitDesc.centerX(), unsplitDesc.top + 6f)
                    )

                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "ca_case1")
                    ed.setSoftKeyboardEnabled(true)
                }

                // C) CursorAnimator Case 2: Already running at deletion start -> INELIGIBLE_FALLBACK
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = true
                    val anim = ed.cursorAnimator as ScribeCursorAnimator
                    anim.triggerGlide(0f, 0f, 36f, 120f, 240f, 36f)
                    assertTrue(anim.isRunning())
                    val plan = ScribeGapCollapsePolicy.planCollapse(
                        deletionIntent = ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR,
                        isSelection = true,
                        isWordwrap = true,
                        isWordwrapReady = true,
                        isLayoutBusy = false,
                        isComposing = false,
                        isCursorAnimationRunning = anim.isRunning(),
                        hasRtlOrBidiAtSeam = false,
                        isMergedPartialRowSeam = false,
                        hasSurvivingUpper = true,
                        hasSurvivingLower = true,
                        deltaGapPx = 120f,
                        visibilityConfidence = VisibilityConfidence.RESOLVED,
                        isUpperVisible = true,
                        isLowerVisible = true,
                        isTouchedOnScreen = true,
                        preScrollY = canvas.scrollD + ed.offsetY,
                        centeredTargetScroll = 300f,
                        viewportBounds = canvas.resolveUnobstructedViewportBounds(),
                        safetyBounds = canvas.resolveSafetyFloorAndBounds(400f)
                    )
                    assertTrue("Active cursor animator at start must force fallback", plan.isFallback)
                    assertEquals(CollapseScenario.INELIGIBLE_FALLBACK, plan.scenario)
                    anim.cancel()
                }

                // D) CursorAnimator Case 3 & Case 4: Starting during Mode 1 and during Mode 2 cancels cursorAnimator
                // without resetting tauLower or relinquishing gap-collapse controller ownership
                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen3, _) = startCollapseTransaction(canvas, seamLine = 20, tauLowerPx = rowH * 3f, requiresMode1 = true)
                    assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)
                    val tauBefore = canvas.gapCollapseController.tauLower
                    (ed.cursorAnimator as ScribeCursorAnimator).triggerGlide(0f, 0f, 36f, 80f, 160f, 36f)
                    assertFalse("Mid-collapse cursorAnimator in Mode 1 must be cancelled", ed.cursorAnimator.isRunning)
                    assertTrue("Mode 1 collapse must retain ownership", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(gen3, canvas.gapCollapseController.owningDeletionGen)
                    assertTrue("Mode 1 split rendering must stay active", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(tauBefore, canvas.gapCollapseController.tauLower, 0.001f)
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "case3_cleanup")
                }

                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen4, epoch4) = startCollapseTransaction(canvas, seamLine = 20, tauLowerPx = rowH * 3f, requiresMode1 = true)
                    canvas.gapCollapseController.onMode1SettledOrHandoff(gen4, epoch4, (canvas.scrollD + ed.offsetY).toFloat())
                    assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
                    val tauBeforeMode2 = canvas.gapCollapseController.tauLower
                    (ed.cursorAnimator as ScribeCursorAnimator).triggerGlide(0f, 0f, 36f, 80f, 160f, 36f)
                    assertFalse("Mid-collapse cursorAnimator in Mode 2 must be cancelled", ed.cursorAnimator.isRunning)
                    assertTrue("Mode 2 collapse must retain ownership", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(gen4, canvas.gapCollapseController.owningDeletionGen)
                    assertTrue("Mode 2 split rendering must stay active", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(tauBeforeMode2, canvas.gapCollapseController.tauLower, 0.001f)
                }
                waitUntil(failureMsg = "Mode 2 collapse must settle normally to IDLE after cancelling cursorAnimator") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_final_cleanup")
                }
            }
        }
    }

    private fun buildIndentedLines(count: Int): String =
        (1..count).joinToString("\n") { idx ->
            "    Paragraph line $idx: verification body content for integrated gap collapse tests."
        }

    private fun installDefaultOverlays(
        canvas: UnifiedCanvasLayout,
        topBarHeightPx: Float = 80f,
        bottomBarHeightPx: Float = 120f
    ): ScribeOverlayBoundsRegistry {
        val registry = ScribeOverlayBoundsRegistry()
        val loc = IntArray(2)
        canvas.getLocationInWindow(loc)
        val left = loc[0].toFloat()
        val top = loc[1].toFloat()
        val right = left + canvas.width.toFloat()
        val bottom = top + canvas.height.toFloat()
        if (topBarHeightPx > 0f) {
            registry.update(
                "test_top_bar",
                OverlayOcclusionRole.TOP_BAR,
                androidx.compose.ui.geometry.Rect(left, top, right, top + topBarHeightPx)
            )
        }
        if (bottomBarHeightPx > 0f) {
            registry.update(
                "test_bottom_bar",
                OverlayOcclusionRole.BOTTOM_BAR,
                androidx.compose.ui.geometry.Rect(left, bottom - bottomBarHeightPx, right, bottom)
            )
        }
        registry.update(
            "top_pill_search",
            OverlayOcclusionRole.FLOATING_PILL,
            androidx.compose.ui.geometry.Rect(left + 16f, top + 12f, left + 96f, top + 60f)
        )
        registry.update(
            "top_pill_menu",
            OverlayOcclusionRole.FLOATING_PILL,
            androidx.compose.ui.geometry.Rect(right - 96f, top + 12f, right - 16f, top + 60f)
        )
        canvas.overlayBoundsRegistry = registry
        return registry
    }

    @Test
    fun suiteE_realSelectionDeletions_ScenariosC_A_B_AndAnimatorIndentRace() {
        val host = launchCanvasHost(text = buildIndentedLines(105))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. Default / Scenario C via real ed.cutText() with cursor animation & indent floor active
                var cPreScroll = 0
                var cDeltaGap = 0f
                var cPreLowerLineText = ""
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.firstLineIndentSpaces = 4
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * 24f) - (canvas.scrollD + ed.offsetY))
                    cPreScroll = canvas.scrollD + ed.offsetY

                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val firstVisRow = kotlin.math.ceil(((cPreScroll - canvas.headerHeight) + bounds.visibleEditorTop) / rowH).toInt()
                    val lastVisRow = kotlin.math.floor(((cPreScroll - canvas.headerHeight) + bounds.visibleEditorBottom) / rowH).toInt()
                    val startLine = (lay.getLineNumberForRow(firstVisRow + 2) + 1).coerceIn(4, 70)
                    var endLine = (startLine + 1).coerceAtMost(85)
                    while (endLine + 1 < 85) {
                        val candidateRow = lay.getRowIndexForPosition(ed.text.getCharIndex(endLine + 1, 0))
                        if (candidateRow > lastVisRow - 3) break
                        endLine++
                    }
                    cPreLowerLineText = ed.text.getLineString(endLine)
                    val startRow = lay.getRowIndexForPosition(ed.text.getCharIndex(startLine, 0))
                    val endRow = lay.getRowIndexForPosition(ed.text.getCharIndex(endLine, 0))
                    assertTrue("Scenario C fixture handles must stay inside unobstructed viewport ($startRow..$endRow in $firstVisRow..$lastVisRow)", endRow > startRow && endRow <= lastVisRow - 2)
                    cDeltaGap = (endRow - startRow) * rowH

                    ed.setSelectionRegion(startLine, 0, endLine, 0, false)
                    ed.cutText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("Real cutText() must enter gap collapse transaction", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.C_BOTH_VISIBLE, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse("Deletion-induced cursor animation must NOT abort collapse", ed.cursorAnimator.isRunning)
                    assertEquals(4, ed.cursor.leftColumn)
                    assertEquals(cPreLowerLineText, ed.text.getLineString(canvas.gapCollapseController.activeSeamLine))
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Scenario C real cutText() must settle cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    val finalScroll = canvas.scrollD + ed.offsetY
                    val expectedTarget = maxOf(0f, cPreScroll - cDeltaGap)
                    assertTrue(
                        "Scenario C must scroll up by deltaGap so lower content stays stationary (pre=$cPreScroll, final=$finalScroll, expected=$expectedTarget)",
                        kotlin.math.abs(finalScroll - expectedTarget) <= ed.rowHeight.toFloat()
                    )
                    drawEditorIntoBitmap(ed)
                }

                // 2. Scenario A (upper handle visible, lower handle offscreen below) via KeyEvent.KEYCODE_DEL
                var aPreScroll = 0
                instr.runOnMainSync {
                    ed.setText(buildIndentedLines(105))
                }
                waitUntil(failureMsg = "Document reload ready for Scenario A") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * 20f) - (canvas.scrollD + ed.offsetY))
                    aPreScroll = canvas.scrollD + ed.offsetY

                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val firstVisRow = kotlin.math.ceil(((aPreScroll - canvas.headerHeight) + bounds.visibleEditorTop) / rowH).toInt()
                    val lastVisRow = kotlin.math.floor(((aPreScroll - canvas.headerHeight) + bounds.visibleEditorBottom) / rowH).toInt()
                    val l1 = (lay.getLineNumberForRow(firstVisRow + 2) + 1).coerceIn(2, 60)
                    val l2 = (lay.getLineNumberForRow((lastVisRow + 8).coerceAtMost(lay.rowCount - 1))).coerceAtLeast(l1 + 8)
                    ed.setSelectionRegion(l1, 0, l2, 0, false)

                    val now = SystemClock.uptimeMillis()
                    ed.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0))
                    ed.dispatchKeyEvent(KeyEvent(now, now + 10L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0))

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("KeyEvent KEYCODE_DEL must trigger gap collapse", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.A_UPPER_ONLY, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse(ed.cursorAnimator.isRunning)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Scenario A must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(aPreScroll, canvas.scrollD + ed.offsetY)
                    drawEditorIntoBitmap(ed)
                }

                // 3. Scenario B Case II (lower handle at (L2, C2) visible while L2+1 is near/below bottom edge) via deleteSurroundingText
                instr.runOnMainSync {
                    ed.setText(buildIndentedLines(105))
                }
                waitUntil(failureMsg = "Document reload ready for Scenario B Case II") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val l2 = 42
                    val lay = ed.layout!!
                    val l2Len = ed.text.getColumnCount(l2)
                    val l2Row = lay.getRowIndexForPosition(ed.text.getCharIndex(l2, l2Len))
                    val targetRowBottomLocal = bounds.visibleEditorBottom - 10f
                    val targetScroll = canvas.headerHeight + ((l2Row + 1) * rowH) - targetRowBottomLocal
                    canvas.scrollCanvasBy(targetScroll - (canvas.scrollD + ed.offsetY))
                    val l1 = (l2 - 14).coerceAtLeast(4)
                    ed.setSelectionRegion(l1, 2, l2, l2Len, false)

                    val ic = ed.onCreateInputConnection(EditorInfo())!!
                    ic.deleteSurroundingText(1, 0)

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("InputConnection deleteSurroundingText must enter collapse", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.B_LOWER_ONLY, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse(ed.cursorAnimator.isRunning)
                    assertEquals(4, ed.cursor.leftColumn)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Scenario B Case II must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    drawEditorIntoBitmap(ed)
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_real_cab_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_realSelectionDeletions_ScenariosD_And_E_WithOverlays() {
        val host = launchCanvasHost(text = buildIndentedLines(115))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. Scenario D (neither handle visible, selected text visible in middle of screen)
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.firstLineIndentSpaces = 4
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 90f, bottomBarHeightPx = 120f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * 30f) - (canvas.scrollD + ed.offsetY))

                    val lay = ed.layout!!
                    val bPre = canvas.resolveUnobstructedViewportBounds()
                    val firstVisRow = ((canvas.scrollD + ed.offsetY - canvas.headerHeight) / rowH).toInt()
                    val lastVisRow = (((canvas.scrollD + ed.offsetY - canvas.headerHeight) + bPre.visibleEditorBottom) / rowH).toInt()
                    val l1 = lay.getLineNumberForRow((firstVisRow - 4).coerceAtLeast(1))
                    val l2 = lay.getLineNumberForRow((lastVisRow + 4).coerceAtMost(lay.rowCount - 2))
                    ed.setSelectionRegion(l1, 0, l2, 0, false)
                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("Scenario D must enter gap collapse", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse(ed.cursorAnimator.isRunning)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Scenario D must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val cur = ed.cursor
                    val caretGeom = canvas.getCharGeometry(lay, cur.leftLine, cur.leftColumn, ed.rowHeight.toFloat())
                    val screenBottom = canvas.headerHeight + caretGeom.bottomDocY - (canvas.scrollD + ed.offsetY)
                    assertTrue(
                        "Scenario D resulting caret ($screenBottom) must be inside unobstructed viewport [${bounds.visibleEditorTop}, ${bounds.visibleEditorBottom}]",
                        screenBottom >= bounds.visibleEditorTop && screenBottom <= bounds.visibleEditorBottom
                    )
                    drawEditorIntoBitmap(ed)
                }

                // 2. Scenario E (offscreen below BOTTOM_BAR, then offscreen behind TOP_BAR)
                var preBelowScroll = 0
                instr.runOnMainSync {
                    ed.setText(buildIndentedLines(115))
                }
                waitUntil(failureMsg = "Document reload ready for Scenario E") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * 4f) - (canvas.scrollD + ed.offsetY))
                    preBelowScroll = canvas.scrollD + ed.offsetY
                    ed.setSelectionRegion(62, 0, 66, 0, false)
                    ed.deleteText()
                    assertFalse("Scenario E below viewport must bypass split rendering", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                }
                waitUntil(failureMsg = "Scenario E below viewport must scroll caret up into unobstructed view") {
                    !canvas.isGlideRunning &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        (canvas.scrollD + ed.offsetY) > preBelowScroll
                }
                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val cur = ed.cursor
                    val caretGeom = canvas.getCharGeometry(lay, cur.leftLine, cur.leftColumn, ed.rowHeight.toFloat())
                    val screenBottom = canvas.headerHeight + caretGeom.bottomDocY - (canvas.scrollD + ed.offsetY)
                    assertTrue(
                        "Scenario E offscreen-below caret ($screenBottom) must land above BOTTOM_BAR (${bounds.visibleEditorBottom})",
                        screenBottom <= bounds.visibleEditorBottom
                    )

                    // Subcase: offscreen behind TOP_BAR
                    canvas.scrollCanvasBy((canvas.maxCanvasScroll * 0.55f) - (canvas.scrollD + ed.offsetY))
                    ed.setSelectionRegion(4, 0, 8, 0, false)
                    ed.deleteText()
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                }
                waitUntil(failureMsg = "Scenario E behind TOP_BAR must bring caret below visibleEditorTop") {
                    !canvas.isGlideRunning &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val cur = ed.cursor
                    val caretGeom = canvas.getCharGeometry(lay, cur.leftLine, cur.leftColumn, ed.rowHeight.toFloat())
                    val screenTop = canvas.headerHeight + caretGeom.topDocY - (canvas.scrollD + ed.offsetY)
                    assertTrue(
                        "Scenario E top-occluded caret ($screenTop) must be below visibleEditorTop (${bounds.visibleEditorTop})",
                        screenTop >= bounds.visibleEditorTop
                    )
                    drawEditorIntoBitmap(ed)
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_real_de_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_v1ShortfallAtZeroScrollAndPreExistingRunningAnimatorFallback() {
        val host = launchCanvasHost(text = buildIndentedLines(95))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. V1 Shortfall > 0 at preScrollY == 0 (Scenario C clamped at 0 -> Mode 2 closes remaining tau)
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.firstLineIndentSpaces = 4
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 0f, bottomBarHeightPx = 100f)
                    canvas.scrollCanvasBy(-(canvas.scrollD + ed.offsetY).toFloat())
                    assertEquals(0, canvas.scrollD + ed.offsetY)

                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val lastVisRow = kotlin.math.floor((bounds.visibleEditorBottom - canvas.headerHeight) / rowH).toInt()
                    var endLine = 2
                    while (endLine + 1 < 12) {
                        val candidateRow = lay.getRowIndexForPosition(ed.text.getCharIndex(endLine + 1, 0))
                        if (candidateRow > lastVisRow - 2) break
                        endLine++
                    }
                    ed.setSelectionRegion(1, 0, endLine, 0, false)
                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("V1 shortfall at preScrollY=0 must still enter gap collapse", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.C_BOTH_VISIBLE, plan?.scenario)
                    assertEquals(0f, plan?.effectiveTargetScroll ?: -1f, 0.001f)
                    assertTrue((plan?.initialTauLowerPx ?: 0f) > 0f)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "V1 shortfall at scroll=0 must finish Mode 2 to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(0, canvas.scrollD + ed.offsetY)
                    assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                    drawEditorIntoBitmap(ed)
                }

                // 2. Unrelated running cursor animator before deletion is cancelled so gap collapse acquires sole ownership
                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row22 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(22, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row22 - 2).coerceAtLeast(0)) - (canvas.scrollD + ed.offsetY))
                    ed.setSelectionRegion(22, 0, 26, 0, false)
                    val anim = ed.cursorAnimator as ScribeCursorAnimator
                    anim.triggerGlide(0f, 0f, rowH, 160f, 360f, rowH)
                    assertTrue("Animator must genuinely be running BEFORE deleteText()", anim.isRunning)

                    ed.deleteText()

                    assertFalse("Pre-existing running cursor animator must be cancelled for gap collapse", anim.isRunning)
                    assertTrue("Gap collapse must take sole ownership after cancelling unrelated cursor animator", canvas.gapCollapseController.isTransactionOwned)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                    assertTrue(canvas.lastSelectionDeletionDiagnostic?.wasCursorAnimatorCancelled == true)
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Gap collapse after pre-existing animator cancellation must converge cleanly to IDLE") {
                    !canvas.isGlideRunning &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_shortfall_anim_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_secondLastParagraphDeletion_KeepsBottomParagraphStationaryAtTailProximity() {
        val lines = buildList {
            for (i in 0 until 45) {
                if (i == 43) {
                    add("    Second-last paragraph with enough wrapped prose to span multiple rows before deletion happens cleanly.")
                } else if (i == 44) {
                    add("    Last line.")
                } else {
                    add("    Body paragraph $i with ordinary prose.")
                }
            }
        }.joinToString("\n")
        val host = launchCanvasHost(text = lines)
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                var preScroll = 0
                var preLastScreenTop = 0f
                var preUpperScreenTop = 0f
                var deltaGapPx = 0f
                val secondLastLine = 43
                val lastLine = 44

                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.firstLineIndentSpaces = 4
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 100f)

                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val lastRow = lay.getRowIndexForPosition(ed.text.getCharIndex(lastLine, 0))
                    val secondLastStartRow = lay.getRowIndexForPosition(ed.text.getCharIndex(secondLastLine, 0))
                    val upperRefRow = lay.getRowIndexForPosition(ed.text.getCharIndex(secondLastLine - 1, 0))
                    assertTrue("Final paragraph must wrap to 1 row for this regression test", lastRow == lay.rowCount - 1)

                    // Scroll so final paragraph sits comfortably inside lower unobstructed viewport (wasAtTail == true
                    // AND both selection handles are unobstructed inside viewport -> Scenario C).
                    val targetLastBottomLocal = bounds.visibleEditorBottom - rowH * 3f
                    val desiredScroll = (canvas.headerHeight + (lastRow + 1) * rowH - targetLastBottomLocal).coerceAtLeast(0f)
                    canvas.scrollCanvasBy(desiredScroll - (canvas.scrollD + ed.offsetY))
                    preScroll = canvas.scrollD + ed.offsetY
                    val docH = canvas.headerHeight + lay.layoutHeight.toFloat()
                    assertTrue(
                        "Test fixture must satisfy tail proximity (wasAtTail == true)",
                        (preScroll + canvas.height.toFloat()) >= (docH - canvas.breathingMarginPx - 4f)
                    )
                    preLastScreenTop = canvas.headerHeight - preScroll + (lastRow * rowH)
                    preUpperScreenTop = canvas.headerHeight - preScroll + (upperRefRow * rowH)
                    deltaGapPx = (lastRow - secondLastStartRow) * rowH
                    assertTrue("Deleted second-last paragraph must have positive gap height", deltaGapPx >= rowH)

                    // Delete second-last paragraph including trailing newline: (43, 0)..(44, 0)
                    ed.setSelectionRegion(secondLastLine, 0, lastLine, 0, false)
                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue(
                        "Second-last paragraph deletion MUST enter gap collapse (not TAIL_ANCHOR fallback)",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    assertEquals(CollapseScenario.C_BOTH_VISIBLE, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(secondLastLine, canvas.gapCollapseController.activeSeamLine)

                    // Verify Frame-1 visual screen top of final paragraph stays stationary (no upward jump)
                    val frame1Scroll = (canvas.scrollD + ed.offsetY).toFloat()
                    val frame1LastVisualTop = canvas.headerHeight - frame1Scroll +
                        canvas.gapCollapseController.activeSeamDocY +
                        canvas.gapCollapseController.tauLower
                    assertEquals(
                        "Frame 1 visual position of final paragraph must not jump upward",
                        preLastScreenTop,
                        frame1LastVisualTop,
                        1.5f
                    )
                    drawEditorIntoBitmap(ed)
                }

                waitUntil(failureMsg = "Second-last paragraph deletion must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }

                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val finalScroll = (canvas.scrollD + ed.offsetY).toFloat()
                    val survivingLastRow = lay.getRowIndexForPosition(ed.text.getCharIndex(secondLastLine, 0))
                    val survivingUpperRow = lay.getRowIndexForPosition(ed.text.getCharIndex(secondLastLine - 1, 0))
                    val finalLastScreenTop = canvas.headerHeight - finalScroll + (survivingLastRow * rowH)
                    val finalUpperScreenTop = canvas.headerHeight - finalScroll + (survivingUpperRow * rowH)

                    assertEquals(
                        "Settled screen position of final paragraph must stay stationary",
                        preLastScreenTop,
                        finalLastScreenTop,
                        2.0f
                    )
                    assertEquals(
                        "Settled upper paragraph must have glided downward by deltaGapPx",
                        preUpperScreenTop + deltaGapPx,
                        finalUpperScreenTop,
                        2.0f
                    )
                    assertEquals(4, ed.cursor.leftColumn)
                    drawEditorIntoBitmap(ed)
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_second_last_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_unfocusedHandleSuppressionRoutesVisibleSelectionToScenarioD() {
        val host = launchCanvasHost(text = buildIndentedLines(95))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 100f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row24 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(24, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row24 - 2).coerceAtLeast(0)) - (canvas.scrollD + ed.offsetY))

                    // Select visible region then clear focus before deletion -> handles are not drawn by Sora!
                    ed.setSelectionRegion(24, 0, 26, 0, false)
                    ed.clearFocus()
                    assertFalse(ed.isFocused)

                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("Visible selection with suppressed handles must still collapse via Scenario D", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, plan?.scenario)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Suppressed-handle Scenario D collapse must settle to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_unfocused_cleanup")
                }
            }
        }
    }


    @Test
    fun suiteE_realWrappedLineDeletionLeavingEmptyLineTriggersDeferredIndentFloorInsideCollapse() {
        val lines = buildList {
            for (i in 0 until 45) {
                if (i == 15) {
                    add("    " + "Wrapped line content slated for full line deletion without newline ".repeat(6).trimEnd())
                } else {
                    add("    Body paragraph $i with ordinary prose.")
                }
            }
        }.joinToString("\n")
        val host = launchCanvasHost(text = lines)
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.firstLineIndentSpaces = 4
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 100f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row15 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(15, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row15 - 2).coerceAtLeast(0)) - (canvas.scrollD + ed.offsetY))

                    // Delete all characters on line 15 without its trailing newline -> Case II, line 15 becomes "",
                    // deferred enforceAutoIndentFloor restores "    " at (15, 4) while caret line 15 stays stationary in Pass 2.
                    val len15 = ed.text.getColumnCount(15)
                    ed.setSelectionRegion(15, 0, 15, len15, false)
                    ed.deleteText()

                    assertEquals("    ", ed.text.getLineString(15))
                    assertEquals(4, ed.cursor.leftColumn)
                    assertTrue("Gap collapse must enter and stay active after deferred indent floor", canvas.gapCollapseController.isTransactionOwned)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(15, canvas.gapCollapseController.activeSeamLine)
                    assertFalse("Deferred indent floor inside collapse must not trigger cursor animator", ed.cursorAnimator.isRunning)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Wrapped Case II empty-line indent floor collapse must converge to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_empty_floor_cleanup")
                }
            }
        }
    }


    @Test
    fun suiteE_partialMidParagraphSelectionDeletion_c1Gt0_c2LtLen_KeepsLowerContentStationary() {
        val text = (0 until 70).joinToString("\n") { idx ->
            if (idx == 22) {
                "Alpha prefix words before deleted span that goes across multiple paragraphs cleanly."
            } else if (idx == 24) {
                "Removed middle portion then surviving lower suffix words that wrap nicely onto the next row of the paragraph."
            } else {
                "Paragraph $idx: ordinary body prose with enough words to fill lines cleanly."
            }
        }
        val host = launchCanvasHost(text = text)
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                var preScroll = 0
                var preNextLineScreenTop = 0f
                var preNextLineRow = -1
                var deltaGapPx = 0f
                val l1 = 22
                val c1 = 13 // non-blank prefix "Alpha prefix "
                val l2 = 24
                val c2 = 23 // mid-line before "then surviving lower suffix..."

                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)
                    val lay = ed.layout!!
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row22 = lay.getRowIndexForPosition(ed.text.getCharIndex(l1, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row22 - 2).coerceAtLeast(0)) - (canvas.scrollD + ed.offsetY))
                    preScroll = canvas.scrollD + ed.offsetY

                    preNextLineRow = lay.getRowIndexForPosition(ed.text.getCharIndex(l2 + 1, 0))
                    preNextLineScreenTop = canvas.headerHeight - preScroll + (preNextLineRow * rowH)

                    ed.setSelectionRegion(l1, c1, l2, c2, false)
                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue("c1 > 0 && c2 < len deletion must enter gap collapse", canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(CollapseScenario.C_BOTH_VISIBLE, plan?.scenario)
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    assertFalse("Deletion-induced cursor animation must NOT abort c1>0&&c2<len collapse", ed.cursorAnimator.isRunning)
                    deltaGapPx = plan?.deltaGapPx ?: 0f
                    assertTrue(deltaGapPx > 0f)

                    val postNextRow = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(l1 + 1, 0))
                    val frame1NextLineVisualTop = canvas.headerHeight -
                        (canvas.scrollD + ed.offsetY).toFloat() +
                        (postNextRow * rowH) +
                        canvas.gapCollapseController.tauLower
                    assertEquals(
                        "Frame 1 visual position of lower paragraph after merged seam must stay stationary",
                        preNextLineScreenTop,
                        frame1NextLineVisualTop,
                        2.0f
                    )
                    drawEditorIntoBitmap(ed)
                }

                waitUntil(failureMsg = "Partial mid-paragraph c1>0&&c2<len collapse must settle to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }

                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val finalScroll = (canvas.scrollD + ed.offsetY).toFloat()
                    val postNextRow = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(l1 + 1, 0))
                    val settledNextLineScreenTop = canvas.headerHeight - finalScroll + (postNextRow * rowH)
                    assertEquals(
                        "Settled lower paragraph screen position must remain stationary",
                        preNextLineScreenTop,
                        settledNextLineScreenTop,
                        2.0f
                    )
                    drawEditorIntoBitmap(ed)
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_c1gt0_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_startOfDocumentAndTailAnchoredSelectionDeletions_EnterGapCollapse() {
        val host = launchCanvasHost(text = buildIndentedLines(60))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. Start of document (0, 0) deletion with surviving lower content
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 100f)
                    canvas.scrollCanvasBy(-(canvas.scrollD + ed.offsetY).toFloat())

                    val lay = ed.layout!!
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val lastVisRow = kotlin.math.floor((bounds.visibleEditorBottom - canvas.headerHeight) / rowH).toInt()
                    var endLine = 2
                    while (endLine + 1 < 12) {
                        val candidateRow = lay.getRowIndexForPosition(ed.text.getCharIndex(endLine + 1, 0))
                        if (candidateRow > lastVisRow - 2) break
                        endLine++
                    }
                    ed.setSelectionRegion(0, 0, endLine, 0, false)
                    ed.deleteText()

                    assertTrue(
                        "Selection starting at (0, 0) with surviving lower must enter gap collapse",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "(0, 0) start deletion must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }

                // 2. Tail-anchored deletion near document bottom with surviving lower paragraph
                instr.runOnMainSync {
                    val lay = ed.layout!!
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val totalRows = lay.rowCount
                    val lastLine = ed.text.lineCount - 1
                    val endLine = (lastLine - 2).coerceAtLeast(10)
                    val startLine = (endLine - 3).coerceAtLeast(5)
                    val desiredScroll = (canvas.headerHeight + totalRows * rowH - canvas.height.toFloat() + canvas.breathingMarginPx).coerceAtLeast(0f)
                    canvas.scrollCanvasBy(desiredScroll - (canvas.scrollD + ed.offsetY))

                    ed.setSelectionRegion(startLine, 0, endLine, 0, false)
                    ed.deleteText()

                    assertTrue(
                        "Selection deletion near document tail with surviving lower must enter gap collapse",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Tail-anchored gap collapse must converge cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_start_tail_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_cutTextWithVisualFirstLineIndent_CopiesIndentedTextAndRunsGapCollapse() {
        val host = launchCanvasHost(text = buildLines(80))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                var expectedClip = ""
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.setVisualFirstLineIndent(4)
                    ed.isCursorAnimationEnabled = true
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)

                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val lay = ed.layout!!
                    val row20 = lay.getRowIndexForPosition(ed.text.getCharIndex(20, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row20 - 2).coerceAtLeast(0)) - (canvas.scrollD + ed.offsetY))

                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val lastVisRow = kotlin.math.floor(((canvas.scrollD + ed.offsetY) - canvas.headerHeight + bounds.visibleEditorBottom) / rowH).toInt()
                    var endLine = 21
                    while (endLine + 1 < 35) {
                        val candidateRow = lay.getRowIndexForPosition(ed.text.getCharIndex(endLine + 1, 0))
                        if (candidateRow > lastVisRow - 2) break
                        endLine++
                    }
                    expectedClip = (20 until endLine).joinToString("\n", postfix = "\n") { l ->
                        "    " + ed.text.getLineString(l)
                    }
                    ed.setSelectionRegion(20, 0, endLine, 0, false)
                    ed.cutText()

                    assertTrue(
                        "cutText() with visualFirstLineIndent must enter gap collapse directly",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    assertEquals(CollapseScenario.C_BOTH_VISIBLE, canvas.gapCollapseController.currentPlan?.scenario)
                    val cm = ed.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val actualClip = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    assertEquals("cutText() must copy 4-space visually indented text", expectedClip, actualClip)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "cutText() with visual indent gap collapse must settle cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    (ed.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_cut_visual_indent_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_largeSelectionExceeding3xViewport_RoutesToScenarioD() {
        val host = launchCanvasHost(text = buildIndentedLines(180))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row80 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(80, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * row80) - (canvas.scrollD + ed.offsetY))

                    // Huge selection (~100 lines, >3x viewport height) spanning above & below viewport
                    ed.setSelectionRegion(20, 0, 140, 0, false)
                    ed.deleteText()

                    val plan = canvas.gapCollapseController.currentPlan
                    assertTrue(
                        "Large >3x viewport visible selection must collapse via Scenario D",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    assertEquals(CollapseScenario.D_SELECTION_ONLY_CENTERED, plan?.scenario)
                    assertTrue(plan?.isProgressCoupled == true)
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Large Scenario D collapse must settle cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_large_d_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_isolationDispatchAsyncLayoutCompositionAndUnsupportedContrast() {
        val host = launchCanvasHost(text = buildLines(140))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // Case A: Async wordwrap relayout in flight -> never falls back to handleDeletionSettlement()
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)

                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row40 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(40, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row40 - 2)) - (canvas.scrollD + ed.offsetY))

                    ed.horizontalPaddingDp = 12f
                    ed.setSelectionRegion(40, 0, 44, 0, false)
                    ed.deleteText()

                    assertEquals(0, canvas.legacySettlementInvocationCount)
                    val outcome = canvas.lastSelectionDeletionDiagnostic?.outcome
                    assertTrue(
                        "Async relayout deletion must either defer as PENDING_LAYOUT or start GAP_COLLAPSE_STARTED (got $outcome)",
                        outcome == SelectionDeletionDispatchOutcome.GAP_COLLAPSE_PENDING_LAYOUT ||
                            outcome == SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED
                    )
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "Async-relayout selection deletion must finish exclusively via gap collapse") {
                    ed.isWordwrapReady() &&
                        !ed.isLayoutBusy &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                }

                // Case B: Active composing region does NOT stall or veto selection-deletion gap collapse
                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val row25 = ed.layout!!.getRowIndexForPosition(ed.text.getCharIndex(25, 0))
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * (row25 - 2)) - (canvas.scrollD + ed.offsetY))

                    val ic = ed.onCreateInputConnection(EditorInfo())!!
                    ed.setSelectionRegion(25, 0, 29, 0, false)
                    val compStart = ed.text.getCharIndex(30, 0)
                    assertTrue(ic.setComposingRegion(compStart, compStart + 4))
                    assertTrue(ed.hasComposingText())

                    ed.deleteText()

                    assertTrue(
                        "Selection deletion must enter gap collapse immediately despite active composing region",
                        canvas.gapCollapseController.isTransactionOwned
                    )
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                }
                waitUntil(failureMsg = "Gap collapse with active composing region must settle cleanly to IDLE") {
                    canvas.lastSelectionDeletionDiagnostic?.outcome == SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                }

                // Case C: Unsupported EOF selection deletion (!hasSurvivingLower) vs legitimate non-selection deletion
                instr.runOnMainSync {
                    canvas.scrollCanvasBy(canvas.maxCanvasScroll.toFloat())
                    val lastLine = ed.text.lineCount - 1
                    val lastCol = ed.text.getColumnCount(lastLine)
                    ed.setSelectionRegion(lastLine - 10, 0, lastLine, lastCol, false)
                    ed.deleteText()

                    assertFalse(canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_UNSUPPORTED_GEOMETRY,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                    assertEquals("end_of_document_no_surviving_lower", canvas.lastSelectionDeletionDiagnostic?.reason)
                    assertEquals(PreDeleteHandleCaptureStatus.CAPTURED, canvas.lastSelectionDeletionDiagnostic?.upperHandleStatus)
                    assertTrue((canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll)
                    assertEquals(0, canvas.legacySettlementInvocationCount)

                    ed.setSelection(12, 4)
                    ed.deleteText()
                }
                waitUntil(failureMsg = "Non-selection deletion must retain ordinary handleDeletionSettlement()") {
                    canvas.legacySettlementInvocationCount == 1 &&
                        !canvas.isDeletionTransactionActive &&
                        !canvas.isGlideRunning
                }
                instr.runOnMainSync {
                    assertEquals(
                        SelectionDeletionDispatchOutcome.NOT_A_SELECTION_DELETION,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                    assertFalse(canvas.lastLegacySettlementWasSelection)
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_iso_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteF_stage1RealBottomSelectionGboardBackspaceAndShortfall() {
        val host = launchCanvasHost(text = buildLines(105))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // 1. Bottom-of-viewport paragraph selection (lower handle within 24dp breathing margin above bottom bar)
                //    with floating pills registered must stay visually stationary without a 24dp post-collapse jump.
                var preLowerScreenY = 0f
                var targetLowerLine = 0
                var targetStartLine = 0
                var targetLowerText = ""
                instr.runOnMainSync {
                    ed.requestFocus()
                    ed.isCursorAnimationEnabled = false
                    installDefaultOverlays(canvas, topBarHeightPx = 80f, bottomBarHeightPx = 120f)
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    canvas.scrollCanvasBy((canvas.headerHeight + rowH * 25f) - (canvas.scrollD + ed.offsetY))
                    val preScroll = canvas.scrollD + ed.offsetY
                    val bounds = canvas.resolveUnobstructedViewportBounds()
                    val lay = ed.layout!!
                    targetLowerLine = -1
                    for (candidate in 10 until (ed.text.lineCount - 5)) {
                        val topDoc = canvas.getCharGeometry(lay, candidate, 0, rowH).topDocY
                        val screenBottom = canvas.headerHeight + topDoc + rowH - preScroll
                        if (screenBottom <= bounds.visibleEditorBottom - 4f) {
                            targetLowerLine = candidate
                        } else {
                            break
                        }
                    }
                    assertTrue("Expected a visible bottom endLine (got $targetLowerLine)", targetLowerLine > 15)
                    targetStartLine = (targetLowerLine - 4).coerceAtLeast(1)
                    targetLowerText = ed.text.getLineString(targetLowerLine)
                    val preLowerDoc = canvas.getCharGeometry(lay, targetLowerLine, 0, rowH).topDocY
                    preLowerScreenY = canvas.headerHeight + preLowerDoc - preScroll

                    ed.setSelectionRegion(targetStartLine, 0, targetLowerLine, 0, false)
                    ed.deleteText()

                    assertTrue(canvas.gapCollapseController.isTransactionOwned)
                    val diag = canvas.lastSelectionDeletionDiagnostic!!
                    assertEquals(SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED, diag.outcome)
                    assertTrue(
                        "Expected Scenario B or C (got ${diag.scenario})",
                        diag.scenario == CollapseScenario.B_LOWER_ONLY || diag.scenario == CollapseScenario.C_BOTH_VISIBLE
                    )
                }
                waitUntil(failureMsg = "Bottom-of-viewport collapse must settle cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                    assertEquals(targetLowerText, ed.text.getLineString(targetStartLine))
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val finalScroll = (canvas.scrollD + ed.offsetY).toFloat()
                    val finalLowerDoc = canvas.getCharGeometry(ed.layout!!, targetStartLine, 0, rowH).topDocY
                    val finalLowerScreenY = canvas.headerHeight + finalLowerDoc - finalScroll
                    assertTrue(
                        "Lower surviving line must stay stationary without post-collapse 24dp jump " +
                            "(pre=$preLowerScreenY, final=$finalLowerScreenY)",
                        kotlin.math.abs(finalLowerScreenY - preLowerScreenY) <= 2f
                    )
                }

                // 2. Real Gboard Backspace ic.deleteSurroundingText(1, 0) while composing text is active
                instr.runOnMainSync {
                    ed.setText(buildLines(90))
                }
                waitUntil(failureMsg = "Document reload ready for Gboard deleteSurroundingText") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                var gboardKeepUpper = ""
                var gboardKeepLower = ""
                instr.runOnMainSync {
                    ed.requestFocus()
                    canvas.scrollCanvasBy((canvas.headerHeight + ed.rowHeight * 12f) - (canvas.scrollD + ed.offsetY))
                    gboardKeepUpper = ed.text.getLineString(11)
                    gboardKeepLower = ed.text.getLineString(16)
                    val ic = ed.onCreateInputConnection(EditorInfo())!!
                    ed.setSelectionRegion(12, 0, 16, 0, false)
                    val compStart = ed.text.getCharIndex(17, 0)
                    assertTrue(ic.setComposingRegion(compStart, compStart + 5))
                    assertTrue(ed.hasComposingText())

                    assertTrue(ic.deleteSurroundingText(1, 0))
                    assertEquals(gboardKeepUpper, ed.text.getLineString(11))
                    assertEquals(gboardKeepLower, ed.text.getLineString(12))
                    assertTrue(canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                }
                waitUntil(failureMsg = "Gboard backspace selection collapse must settle cleanly to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }

                // 3. Top-of-document partial shortfall (0 < S_pre < deltaGapPx) closes smoothly to scroll == 0
                instr.runOnMainSync {
                    ed.setText(buildLines(90))
                }
                waitUntil(failureMsg = "Document reload ready for top-shortfall test") {
                    ed.layout != null && ed.isWordwrapReady() && !ed.isLayoutBusy
                }
                instr.runOnMainSync {
                    ed.requestFocus()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val smallPreScroll = (rowH * 2.5f)
                    canvas.scrollCanvasBy(smallPreScroll - (canvas.scrollD + ed.offsetY))
                    val startRow = 3
                    val endRow = 10
                    assertTrue((endRow - startRow) * rowH > (canvas.scrollD + ed.offsetY))
                    ed.setSelectionRegion(startRow, 0, endRow, 0, false)
                    ed.deleteText()

                    assertTrue(canvas.gapCollapseController.isTransactionOwned)
                    assertEquals(
                        SelectionDeletionDispatchOutcome.GAP_COLLAPSE_STARTED,
                        canvas.lastSelectionDeletionDiagnostic?.outcome
                    )
                }
                waitUntil(failureMsg = "Top shortfall collapse must settle smoothly at scroll 0") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE &&
                        !canvas.isGlideRunning &&
                        !canvas.isDeletionTransactionActive
                }
                instr.runOnMainSync {
                    assertEquals(0, canvas.scrollD + ed.offsetY)
                    assertEquals(0, canvas.legacySettlementInvocationCount)
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteF_cleanup")
                }
            }
        }
    }
}
