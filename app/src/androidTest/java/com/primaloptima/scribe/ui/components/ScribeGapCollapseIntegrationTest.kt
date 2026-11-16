package com.primaloptima.scribe.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
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
import com.primaloptima.scribe.ui.editor.scroll.ScribeGapCollapsePolicy
import com.primaloptima.scribe.ui.editor.scroll.ScribeOverlayBoundsRegistry
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
        val boundMax = (safeMax ?: canvas.maxCanvasScroll).coerceAtLeast(actual)
        val target = effectiveTargetOverride ?: if (requiresMode1) {
            (actual - tauLowerPx * 0.5f).coerceIn(safeMinPhysical.toFloat(), boundMax.toFloat())
        } else {
            actual.coerceIn(safeMinPhysical, boundMax).toFloat()
        }
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
            isProgressCoupled = requiresMode1 && scenario != CollapseScenario.E_OVERSCROLLED_CLAMP,
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
                        scenario = CollapseScenario.E_OVERSCROLLED_CLAMP,
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

                    // Simulate stale postInLifecycle(displayWindow) while aw is disabled
                    ed.postInLifecycle { aw.displayWindow() }
                }
                SystemClock.sleep(60L)
                instr.runOnMainSync {
                    assertFalse("Stale displayWindow() while disabled must be blocked", aw.isShowing)
                    assertFalse("aw.isEnabled still false prior to real draw pass", aw.isEnabled)

                    // Now draw unsplit with !cursorAnimator.isRunning -> DrawCursorTask refreshes & enables
                    drawEditorIntoBitmap(ed)
                }
                waitUntil(failureMsg = "aw.isEnabled must re-enable after unsplit !cursorAnimator.isRunning() draw") {
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
                    val targetReplaceEnd = ed.text.lineCount - 1
                    val targetReplaceStart = (targetReplaceEnd - 25).coerceAtLeast(2)
                    ed.setSelectionRegion(targetReplaceStart, 0, targetReplaceEnd, 0, false)
                    ed.text.replace(targetReplaceStart, 0, targetReplaceEnd, 0, "Short replacement")
                    assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                    ed.dispatchEvent(LayoutStateChangeEvent(ed, false))
                }
                waitUntil(failureMsg = "Overscrolled non-empty replacement must clamp within maxCanvasScroll without gap collapse") {
                    !canvas.isGlideRunning &&
                        (canvas.scrollD + ed.offsetY) <= canvas.maxCanvasScroll &&
                        canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
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
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE && !canvas.isDeletionTransactionActive
                }
            } finally {
                instr.runOnMainSync {
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "suiteE_replace_cleanup")
                }
            }
        }
    }

    @Test
    fun suiteE_scenarioEEventBSafetyReadBackAndCursorAnimatorCases1To4() {
        val host = launchCanvasHost(text = buildLines(95))
        val instr = InstrumentationRegistry.getInstrumentation()
        host.scenario.use {
            val canvas = host.canvas
            val ed = canvas.editor
            try {
                // A) Scenario E overscroll clamp + Event B safety bounds read-back
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
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(
                        canvas = canvas,
                        seamLine = 18,
                        tauLowerPx = rowH * 3f,
                        requiresMode1 = true,
                        safeMinPhysical = bounds.safeMinPhysical,
                        safeMax = bounds.safeMax,
                        effectiveTargetOverride = bounds.safeMax.toFloat(),
                        scenario = CollapseScenario.E_OVERSCROLLED_CLAMP
                    )
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
                waitUntil(failureMsg = "Scenario E must converge through Event B to IDLE") {
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

                // B) CursorAnimator Case 1: Idle at deletion start + Case-I lower insertion handle hit-testing
                instr.runOnMainSync {
                    ed.isCursorAnimationEnabled = false
                    ed.cursorAnimator.cancel()
                    ed.requestFocus()
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val tau = rowH * 3f
                    ed.setSelection(20, 0, false)
                    startCollapseTransaction(canvas, seamLine = 20, tauLowerPx = tau, requiresMode1 = false)
                    val lay = ed.layout!!
                    val curOff = lay.getCharLayoutOffset(20, 0)!!
                    val handleX = curOff[1] + ed.measureTextRegionOffset() - ed.offsetX
                    val unshiftedAnchorY = curOff[0] - ed.offsetY + rowH
                    val shiftedAnchorY = unshiftedAnchorY + tau
                    assertFalse(ed.isTouchOnHandle(handleX, unshiftedAnchorY + 6f))
                    assertTrue(ed.isTouchOnHandle(handleX, shiftedAnchorY + 6f))
                    canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "ca_case1")
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
                        preScrollY = canvas.scrollD + ed.offsetY,
                        centeredTargetScroll = 300f,
                        viewportBounds = canvas.resolveUnobstructedViewportBounds(),
                        safetyBounds = canvas.resolveSafetyFloorAndBounds(400f)
                    )
                    assertTrue("Active cursor animator at start must force fallback", plan.isFallback)
                    anim.cancel()
                }

                // D) CursorAnimator Case 3 & Case 4: Starting during Mode 1 and during Mode 2 triggers Option B
                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    startCollapseTransaction(canvas, seamLine = 20, tauLowerPx = rowH * 3f, requiresMode1 = true)
                    assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)
                    (ed.cursorAnimator as ScribeCursorAnimator).triggerGlide(0f, 0f, 36f, 80f, 160f, 36f)
                    assertFalse("Option B during Mode 1 must disable split rendering", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                }
                waitUntil(failureMsg = "Mode 1 Option-B abort must release safely to IDLE") {
                    canvas.gapCollapseController.phase == GapCollapsePhase.IDLE
                }

                instr.runOnMainSync {
                    val rowH = ed.rowHeight.toFloat().coerceAtLeast(20f)
                    val (gen4, epoch4) = startCollapseTransaction(canvas, seamLine = 20, tauLowerPx = rowH * 3f, requiresMode1 = true)
                    canvas.gapCollapseController.onMode1SettledOrHandoff(gen4, epoch4, (canvas.scrollD + ed.offsetY).toFloat())
                    assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
                    (ed.cursorAnimator as ScribeCursorAnimator).triggerGlide(0f, 0f, 36f, 80f, 160f, 36f)
                    assertFalse("Option B during Mode 2 must zero visual gap immediately", canvas.gapCollapseController.shouldApplySplitRender)
                    assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                }
                waitUntil(failureMsg = "Mode 2 Option-B abort must release safely to IDLE before unsplit render") {
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
}
