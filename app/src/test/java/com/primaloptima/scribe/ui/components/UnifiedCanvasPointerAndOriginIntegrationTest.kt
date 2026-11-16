package com.primaloptima.scribe.ui.components

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import com.primaloptima.scribe.R
import com.primaloptima.scribe.ui.editor.scroll.CollapsePlan
import com.primaloptima.scribe.ui.editor.scroll.CollapseScenario
import com.primaloptima.scribe.ui.editor.scroll.GapCollapsePhase
import com.primaloptima.scribe.ui.editor.scroll.PhysicalScrollOrigin
import com.primaloptima.scribe.ui.editor.scroll.SafetyFloorAndBounds
import com.primaloptima.scribe.ui.editor.scroll.ScribeScrollDeletionPolicy
import com.primaloptima.scribe.ui.editor.scroll.ScribeScrollInputController
import com.primaloptima.scribe.ui.editor.scroll.PendingPostSafetyKeyboardEval
import com.primaloptima.scribe.ui.editor.scroll.ScrollAnimationReason
import com.primaloptima.scribe.ui.editor.scroll.ScribeDockTransition
import com.primaloptima.scribe.ui.editor.scroll.ScribeGapCollapsePolicy
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.util.IntPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w800dp-h1200dp-mdpi")
class UnifiedCanvasPointerAndOriginIntegrationTest {

    private val activityControllers = mutableListOf<ActivityController<ComponentActivity>>()

    @After
    fun tearDown() {
        activityControllers.forEach { controller ->
            try {
                controller.pause().stop().destroy()
            } catch (_: Throwable) {}
        }
        activityControllers.clear()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createThemedActivity(): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        activityControllers.add(controller)
        controller.create()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Scribe)
        controller.start().resume().visible()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun createLaidOutCanvas(headerHeightPx: Int = 200, lineCount: Int = 140): UnifiedCanvasLayout {
        val activity = createThemedActivity()
        val canvas = UnifiedCanvasLayout(activity)
        canvas.headerView.setPadding(0, headerHeightPx, 0, 0)
        canvas.editor.isWordwrap = false
        val body = (1..lineCount).joinToString("\n") { idx ->
            "Line $idx: prose paragraph content for scroll and gap collapse integration verification."
        }
        canvas.editor.setText(body)
        activity.setContentView(canvas)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
        canvas.measure(widthSpec, heightSpec)
        canvas.layout(0, 0, 800, 1200)
        return canvas
    }

    private fun makeTestCollapsePlan(
        preScrollY: Int,
        effectiveTargetScroll: Float,
        requiresMode1: Boolean,
        initialTauLowerPx: Float,
        safeMinPhysical: Int = 100,
        safeMax: Int = 2500
    ): CollapsePlan {
        return CollapsePlan(
            scenario = CollapseScenario.B_LOWER_ONLY,
            requestedTargetScroll = effectiveTargetScroll,
            effectiveTargetScroll = effectiveTargetScroll,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = safeMax,
                minSafeScroll = safeMinPhysical.toFloat(),
                safeMinPhysical = safeMinPhysical,
                actualPhysicalScroll = preScrollY
            ),
            deltaGapPx = initialTauLowerPx.coerceAtLeast(10f),
            preScrollY = preScrollY,
            requiresMode1 = requiresMode1,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = preScrollY,
            initialTauLowerPx = initialTauLowerPx,
            isProgressCoupled = requiresMode1,
            requiresOffscreenCaretFollow = false
        )
    }

    private fun startDeletionSnapshot(canvas: UnifiedCanvasLayout): Long {
        val lineCount = canvas.editor.text.lineCount
        val docH = (canvas.headerHeight + (canvas.editor.layout?.layoutHeight ?: 0)).toFloat()
        val currentScroll = (canvas.scrollD + canvas.editor.offsetY).toFloat()
        return canvas.beginPreEditDeletionSnapshot(
            isSelection = true,
            selectionStartLine = 10,
            selectionEndLine = 14,
            preEditDocLineCount = lineCount,
            preEditDocHeight = docH,
            preEditScrollY = currentScroll,
            preEditCaretScreenY = 300f,
            wasAtTail = false,
            affectsDocumentTail = false,
            preEditLastNonBlankLine = lineCount - 1,
            markPendingSettlement = false
        )
    }

    @Test
    fun testRealHostDispatchTouchEvent_FreezesMode1AnimatorPreservingTransactionAndResumesToMode2() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200)
        // Isolate Phase 3 pointer freeze/resume from unrelated tap-navigation cursor gliding:
        // a stationary tap (ACTION_DOWN/ACTION_UP without drag slop) triggers Sora onSingleTapUp ->
        // setSelection(CAUSE_TAP) -> ScribeCursorAnimator.start(), which exercises Phase 4 Option B.
        canvas.editor.isCursorAnimationEnabled = false
        canvas.scrollCanvasBy(500f)
        assertEquals(200, canvas.scrollD)
        assertEquals(300, canvas.editor.offsetY)

        val gen = startDeletionSnapshot(canvas)
        val plan = makeTestCollapsePlan(
            preScrollY = 500,
            effectiveTargetScroll = 350f,
            requiresMode1 = true,
            initialTauLowerPx = 300f,
            safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
        )
        val collapseEpoch = canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = canvas.editor.rowHeight.toFloat(),
            initialPhysicalScrollPx = 500f
        )
        canvas.gapCollapseController.markMode1Started(gen, collapseEpoch, 500f)
        val started = canvas.startAuthoritativeScrollAnimation(
            targetTotalScroll = 350f,
            reason = ScrollAnimationReason.DELETION_ANCHOR,
            durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
            forceRetarget = true
        )
        assertTrue("Mode-1 glide must start on host", started)
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)
        assertTrue(canvas.isGlideRunning)
        assertTrue(canvas.scrollCoordinator.isAnimationActive)

        // Dispatch real ACTION_DOWN through UnifiedCanvasLayout -> ScribeCodeEditor
        val downTime = SystemClock.uptimeMillis()
        val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
        try {
            canvas.dispatchTouchEvent(downEv)
        } finally {
            downEv.recycle()
        }

        // Must enter POINTER_FROZEN, stop Mode-1 animator cleanly, and NOT cancel collapse on dual ACTION_DOWN
        assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
        assertEquals(gen, canvas.currentDeletionTransactionGeneration)
        assertTrue(canvas.isDeletionTransactionActive)
        assertFalse("Mode-1 glide must stop immediately on pointer freeze", canvas.isGlideRunning)
        assertFalse("Coordinator operation must stop immediately on pointer freeze", canvas.scrollCoordinator.isAnimationActive)
        assertTrue(canvas.gapCollapseController.shouldApplySplitRender)

        // Dispatch ACTION_UP -> try/finally resumes POINTER_FROZEN into MODE_2_VISUAL_SETTLING without Mode-1 animator
        val upEv = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, 200f, 500f, 0)
        try {
            canvas.dispatchTouchEvent(upEv)
        } finally {
            upEv.recycle()
        }

        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
        assertFalse("No stale Mode-1 glide may run concurrently with Mode 2", canvas.isGlideRunning)
        assertFalse("No stale coordinator animation may run concurrently with Mode 2", canvas.scrollCoordinator.isAnimationActive)

        canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
        assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
    }

    @Test
    fun testRealHostDualActionDownInFinalSafetyPending_CancelsAndBumpsGenerationOnce() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200)
        canvas.scrollCanvasBy(400f)

        val gen = startDeletionSnapshot(canvas)
        canvas.gapCollapseController.transitionToFinalSafetyForFallback(gen)
        assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, canvas.gapCollapseController.phase)
        assertTrue(canvas.isDeletionTransactionActive)

        val downTime = SystemClock.uptimeMillis()
        val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
        try {
            canvas.dispatchTouchEvent(downEv)
        } finally {
            downEv.recycle()
        }

        assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
        assertFalse(canvas.isDeletionTransactionActive)
        assertEquals(
            "Dual ACTION_DOWN (UnifiedCanvasLayout + ScribeCodeEditor) must bump generation exactly once",
            gen + 1L,
            canvas.currentDeletionTransactionGeneration
        )
    }

    @Test
    fun testRealHostUntaggedScrollCanvasBy_AbortsMode2WhileAuthorizedCanvasDragTracks() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200)
        canvas.scrollCanvasBy(400f)
        val gen = startDeletionSnapshot(canvas)
        val plan = makeTestCollapsePlan(
            preScrollY = 400,
            effectiveTargetScroll = 400f,
            requiresMode1 = false,
            initialTauLowerPx = 80f,
            safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
        )
        canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = canvas.editor.rowHeight.toFloat(),
            initialPhysicalScrollPx = 400f
        )
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)

        // Authorized user drag keeps Mode 2 alive and updates known physical scroll
        canvas.withPhysicalScrollOrigin(PhysicalScrollOrigin.USER_CANVAS_DRAG) {
            canvas.scrollCanvasBy(20f)
        }
        assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
        assertEquals(420f, canvas.gapCollapseController.lastMode2KnownPhysicalScroll, 0.5f)

        // Untagged programmatic scrollCanvasBy > 0.5px must classify as EXTERNAL_PROGRAMMATIC and abort Mode 2
        canvas.scrollCanvasBy(25f)
        assertNotEquals(
            "Untagged external scrollCanvasBy must abort Mode 2 into Event B",
            GapCollapsePhase.MODE_2_VISUAL_SETTLING,
            canvas.gapCollapseController.phase
        )
        assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
        assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
    }

    @Test
    fun testRealHostScribeCodeEditorScrollBy_UntaggedProgrammaticDirectAndForwardedPathsAbortMode2() {
        // Case A: Direct inner-editor scrollBy when scrollD == headerHeight and offsetY > 0
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200)
            canvas.scrollCanvasBy(400f)
            assertEquals(200, canvas.scrollD)
            assertEquals(200, canvas.editor.offsetY)

            val gen = startDeletionSnapshot(canvas)
            val plan = makeTestCollapsePlan(
                preScrollY = 400,
                effectiveTargetScroll = 400f,
                requiresMode1 = false,
                initialTauLowerPx = 80f,
                safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
            )
            canvas.gapCollapseController.beginCollapse(
                owningGen = gen,
                plan = plan,
                seamLine = 10,
                seamFirstLowerRow = 10,
                seamDocY = 400f,
                rowHeightPx = canvas.editor.rowHeight.toFloat(),
                initialPhysicalScrollPx = 400f
            )
            assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)

            canvas.editor.scrollBy(0, 25)
            assertEquals(225, canvas.editor.offsetY)
            assertNotEquals(
                "Untagged programmatic ScribeCodeEditor.scrollBy must abort Mode 2",
                GapCollapsePhase.MODE_2_VISUAL_SETTLING,
                canvas.gapCollapseController.phase
            )
            assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
        }

        // Case B: Forwarded scrollBy when scrollD < headerHeight (scrolls canvas header via scrollCanvasBy)
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200)
            canvas.scrollCanvasBy(150f)
            assertEquals(150, canvas.scrollD)
            assertEquals(0, canvas.editor.offsetY)

            val gen = startDeletionSnapshot(canvas)
            val plan = makeTestCollapsePlan(
                preScrollY = 150,
                effectiveTargetScroll = 150f,
                requiresMode1 = false,
                initialTauLowerPx = 80f,
                safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
            )
            canvas.gapCollapseController.beginCollapse(
                owningGen = gen,
                plan = plan,
                seamLine = 2,
                seamFirstLowerRow = 2,
                seamDocY = 80f,
                rowHeightPx = canvas.editor.rowHeight.toFloat(),
                initialPhysicalScrollPx = 150f
            )
            assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)

            canvas.editor.scrollBy(0, -25)
            assertEquals(125, canvas.scrollD)
            assertNotEquals(
                "Untagged programmatic ScribeCodeEditor.scrollBy forwarding to scrollCanvasBy must abort Mode 2",
                GapCollapsePhase.MODE_2_VISUAL_SETTLING,
                canvas.gapCollapseController.phase
            )
            assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
        }
    }

    @Test
    fun testScribeScrollInputController_DistinguishesProgrammaticStartScrollFromUserFling() {
        val activity = createThemedActivity()
        val observedOrigins = mutableListOf<PhysicalScrollOrigin>()
        var simulatedScrollD = 200

        val host = object : ScribeScrollInputController.Host {
            override val scrollD: Int get() = simulatedScrollD
            override val headerHeight: Int get() = 200
            override val editorOffsetY: Int get() = 0
            override val maxCanvasScroll: Int get() = 1000
            override var isUserTouching: Boolean = false
            override val isAnyHandleActive: Boolean = false
            override val isMultiTouchActive: Boolean = false
            override fun isTouchOnHandle(editorX: Float, editorY: Float): Boolean = false
            override fun editorLeft(): Int = 0
            override fun editorIsHandleDragging(): Boolean = false
            override fun scrollCanvasBy(dy: Float) {
                simulatedScrollD = (simulatedScrollD + dy.toInt()).coerceIn(0, maxCanvasScroll)
            }
            override fun postInvalidateOnAnimation() {}
            override fun stopEditorFling() {}
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {}
            override fun onTopEdgeEffectRelease() {}
            override fun onTopEdgeEffectAbsorb(velocity: Int) {}
            override val isTopEdgeEffectFinished: Boolean = true
            override fun onUserTouchDown(touchY: Float) {}
            override fun runWithScrollOrigin(origin: PhysicalScrollOrigin, block: () -> Unit) {
                observedOrigins.add(origin)
                block()
            }
        }

        val controller = ScribeScrollInputController(activity, host)

        // Programmatic startScroll must emit EXTERNAL_PROGRAMMATIC, never USER_CANVAS_FLING
        controller.startScroll(0, simulatedScrollD, 0, -100, 250)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(32))
        assertTrue(controller.computeScroll())
        assertTrue("startScroll computeScroll must emit origin", observedOrigins.isNotEmpty())
        assertEquals(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC, observedOrigins.last())

        observedOrigins.clear()
        controller.abortAnimation()

        // Editor fling handoff must emit USER_CANVAS_FLING
        controller.continueFlingFromEditor(3000f)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(32))
        assertTrue(controller.computeScroll())
        assertTrue("fling computeScroll must emit origin", observedOrigins.isNotEmpty())
        assertEquals(PhysicalScrollOrigin.USER_CANVAS_FLING, observedOrigins.last())
    }

    @Test
    fun testRealHostPointerFreeze_FullDownMoveUpDragMaintainsFrozenPhaseAndResumesToMode2() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200)
        canvas.scrollCanvasBy(150f)
        assertEquals(150, canvas.scrollD)

        val gen = startDeletionSnapshot(canvas)
        val plan = makeTestCollapsePlan(
            preScrollY = 150,
            effectiveTargetScroll = 50f,
            requiresMode1 = true,
            initialTauLowerPx = 200f,
            safeMinPhysical = 0,
            safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
        )
        val collapseEpoch = canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = 2,
            seamFirstLowerRow = 2,
            seamDocY = 80f,
            rowHeightPx = canvas.editor.rowHeight.toFloat(),
            initialPhysicalScrollPx = 150f
        )
        canvas.gapCollapseController.markMode1Started(gen, collapseEpoch, 150f)
        assertTrue(
            canvas.startAuthoritativeScrollAnimation(
                targetTotalScroll = 50f,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                forceRetarget = true
            )
        )

        val downTime = SystemClock.uptimeMillis()
        val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
        val moveEv1 = MotionEvent.obtain(downTime, downTime + 16L, MotionEvent.ACTION_MOVE, 200f, 540f, 0)
        val moveEv2 = MotionEvent.obtain(downTime, downTime + 32L, MotionEvent.ACTION_MOVE, 200f, 590f, 0)
        val upEv = MotionEvent.obtain(downTime, downTime + 48L, MotionEvent.ACTION_UP, 200f, 590f, 0)
        try {
            canvas.dispatchTouchEvent(downEv)
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertFalse(canvas.isGlideRunning)

            canvas.dispatchTouchEvent(moveEv1)
            canvas.dispatchTouchEvent(moveEv2)
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.currentDeletionTransactionGeneration)

            canvas.dispatchTouchEvent(upEv)
            assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
            val expectedPhysical = (canvas.scrollD + canvas.editor.offsetY).toFloat()
            assertEquals(expectedPhysical, canvas.gapCollapseController.lastMode2KnownPhysicalScroll, 0.5f)
            assertFalse(canvas.isGlideRunning)
            assertFalse(canvas.scrollCoordinator.isAnimationActive)
        } finally {
            downEv.recycle()
            moveEv1.recycle()
            moveEv2.recycle()
            upEv.recycle()
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
        }
    }

    private fun assertHostZeroTauPointerFreezeSettlesOnGestureEnd(endAction: Int) {
        val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 200)
        canvas.scrollCanvasBy(850f)
        assertEquals(200, canvas.scrollD)
        assertEquals(650, canvas.editor.offsetY)

        val gen = startDeletionSnapshot(canvas)
        val overscrollPlan = CollapsePlan(
            scenario = CollapseScenario.B_LOWER_ONLY,
            requestedTargetScroll = 750f,
            effectiveTargetScroll = 700f,
            safetyBounds = SafetyFloorAndBounds(
                safeMax = 700,
                minSafeScroll = 300f,
                safeMinPhysical = 300,
                actualPhysicalScroll = 850
            ),
            deltaGapPx = 100f,
            preScrollY = 850,
            requiresMode1 = true,
            requiresSynchronousSnap = false,
            synchronousSnapTarget = 850,
            initialTauLowerPx = 100f,
            isProgressCoupled = false,
            requiresOffscreenCaretFollow = false
        )
        val collapseEpoch = canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = overscrollPlan,
            seamLine = 10,
            seamFirstLowerRow = 10,
            seamDocY = 400f,
            rowHeightPx = canvas.editor.rowHeight.toFloat(),
            initialPhysicalScrollPx = 850f
        )
        canvas.gapCollapseController.markMode1Started(gen, collapseEpoch, 850f)
        assertTrue(
            canvas.startAuthoritativeScrollAnimation(
                targetTotalScroll = 700f,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                forceRetarget = true
            )
        )
        // Advance Mode-1 physical retraction past the gap delta (850 -> 740) so tauLower genuinely saturates at 0f
        canvas.withPhysicalScrollOrigin(PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1) {
            canvas.scrollCanvasBy(-110f)
        }
        val physicalBeforeFreeze = (canvas.scrollD + canvas.editor.offsetY).toFloat()
        assertEquals(740f, physicalBeforeFreeze, 1f)
        canvas.gapCollapseController.onMode1FrameCommitted(
            expectedGen = gen,
            expectedCollapseEpoch = collapseEpoch,
            actualPhysicalScrollPx = physicalBeforeFreeze,
            boundaryHit = false,
            frameTimeNanos = 1_016_000_000L
        )
        assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)
        assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
        assertTrue(canvas.isGlideRunning)

        val downTime = SystemClock.uptimeMillis()
        val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
        val endEv = MotionEvent.obtain(downTime, downTime + 40L, endAction, 200f, 500f, 0)
        try {
            canvas.dispatchTouchEvent(downEv)
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertTrue(canvas.gapCollapseController.isPointerFrozen)
            assertFalse(canvas.gapCollapseController.isResumableCollapse)
            assertFalse("Mode-1 glide must stop on zero-tau pointer freeze", canvas.isGlideRunning)
            assertFalse("Scroll coordinator must stop on zero-tau pointer freeze", canvas.scrollCoordinator.isAnimationActive)
            assertEquals(gen, canvas.currentDeletionTransactionGeneration)
            assertTrue(canvas.isDeletionTransactionActive)

            canvas.dispatchTouchEvent(endEv)
            assertFalse("Zero-tau POINTER_FROZEN must never remain stranded after pointer end", canvas.gapCollapseController.isPointerFrozen)
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(canvas.isDeletionTransactionActive)
            assertEquals(gen + 1L, canvas.currentDeletionTransactionGeneration)
        } finally {
            downEv.recycle()
            endEv.recycle()
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
        }
    }

    @Test
    fun testRealHostPointerFreeze_ZeroTauMode1_ActionDownFreezesAndActionUpSettlesToFinalSafety() {
        assertHostZeroTauPointerFreezeSettlesOnGestureEnd(MotionEvent.ACTION_UP)
    }

    @Test
    fun testRealHostPointerFreeze_ZeroTauMode1_ActionDownFreezesAndActionCancelSettlesToFinalSafety() {
        assertHostZeroTauPointerFreezeSettlesOnGestureEnd(MotionEvent.ACTION_CANCEL)
    }

    @Test
    fun testRealHostPointerFrozen_CursorAnimatorActivationTriggersOptionBAndFinalizesViaEventB() {
        // Case 1: Stationary tap (ACTION_DOWN -> ACTION_UP) while pointer-frozen with cursor animation enabled
        // triggers Sora setSelection(CAUSE_TAP) -> ScribeCursorAnimator.start() -> Phase 4 Option-B Event-B (VALIDATED_SAFE).
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200)
            canvas.scrollCanvasBy(500f)
            assertTrue(canvas.editor.isCursorAnimationEnabled)

            val gen = startDeletionSnapshot(canvas)
            val plan = makeTestCollapsePlan(
                preScrollY = 500,
                effectiveTargetScroll = 350f,
                requiresMode1 = true,
                initialTauLowerPx = 300f,
                safeMax = canvas.maxCanvasScroll.coerceAtLeast(1000)
            )
            val collapseEpoch = canvas.gapCollapseController.beginCollapse(
                owningGen = gen,
                plan = plan,
                seamLine = 10,
                seamFirstLowerRow = 10,
                seamDocY = 400f,
                rowHeightPx = canvas.editor.rowHeight.toFloat(),
                initialPhysicalScrollPx = 500f
            )
            canvas.gapCollapseController.markMode1Started(gen, collapseEpoch, 500f)
            assertTrue(
                canvas.startAuthoritativeScrollAnimation(
                    targetTotalScroll = 350f,
                    reason = ScrollAnimationReason.DELETION_ANCHOR,
                    durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                    forceRetarget = true
                )
            )

            val downTime = SystemClock.uptimeMillis()
            val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
            val upEv = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, 200f, 500f, 0)
            try {
                canvas.dispatchTouchEvent(downEv)
                assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                assertTrue(canvas.gapCollapseController.isTransactionOwned)
                assertTrue(canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(300f, canvas.gapCollapseController.tauLower, 0.01f)

                // Stationary ACTION_UP triggers Sora onSingleTapUp -> setSelection -> ScribeCursorAnimator.start()
                canvas.dispatchTouchEvent(upEv)
                assertFalse("Split rendering must disable immediately on Option-B fallback", canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                assertFalse(canvas.gapCollapseController.isTransactionOwned)
                assertFalse(canvas.isDeletionTransactionActive)
                assertEquals(gen + 1L, canvas.currentDeletionTransactionGeneration)
                assertFalse("No Mode-1/Mode-2 animation may run after Option-B fallback", canvas.isGlideRunning)
                assertFalse(canvas.scrollCoordinator.isAnimationActive)
            } finally {
                downEv.recycle()
                upEv.recycle()
                (canvas.editor.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
            }
        }

        // Case 2: Unsafe physical scroll (actual < dynamic safeMinPhysical for lower caret) during POINTER_FROZEN ->
        // ScribeCursorAnimator.triggerGlide() keeps FINAL_SAFETY_PENDING across async Event-B CORRECTION_STARTED ->
        // converges via CORRECTED_AND_VALIDATED -> IDLE with physical scroll corrected inside [safeMinPhysical, safeMax].
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 200)
            canvas.editor.isCursorAnimationEnabled = false
            canvas.editor.setSelection(80, 0, false)
            (canvas.editor.cursorAnimator as? ScribeCursorAnimator)?.cancel()
            val afterSetScroll = (canvas.scrollD + canvas.editor.offsetY).toFloat()
            canvas.scrollCanvasBy(250f - afterSetScroll)
            assertEquals(250, canvas.scrollD + canvas.editor.offsetY)
            canvas.editor.isCursorAnimationEnabled = true

            val rowH = canvas.editor.rowHeight.toFloat()
            val caretGeom = canvas.getCharGeometry(canvas.editor.layout!!, 80, 0, rowH)
            val expectedBounds = canvas.resolveSafetyFloorAndBounds(anchorDocBottom = caretGeom.bottomDocY)
            assertTrue(
                "Expected physical scroll (250) strictly below dynamic safeMinPhysical (${expectedBounds.safeMinPhysical})",
                250 + 50 < expectedBounds.safeMinPhysical
            )
            assertTrue(
                "Expected non-contradictory safety bounds (${expectedBounds.safeMinPhysical} <= ${expectedBounds.safeMax})",
                expectedBounds.safeMinPhysical <= expectedBounds.safeMax
            )

            val gen = startDeletionSnapshot(canvas)
            val plan = makeTestCollapsePlan(
                preScrollY = 250,
                effectiveTargetScroll = 150f,
                requiresMode1 = true,
                initialTauLowerPx = 220f,
                safeMinPhysical = expectedBounds.safeMinPhysical,
                safeMax = expectedBounds.safeMax
            )
            val collapseEpoch = canvas.gapCollapseController.beginCollapse(
                owningGen = gen,
                plan = plan,
                seamLine = 10,
                seamFirstLowerRow = 10,
                seamDocY = 400f,
                rowHeightPx = rowH,
                initialPhysicalScrollPx = 250f
            )
            canvas.gapCollapseController.markMode1Started(gen, collapseEpoch, 250f)
            assertTrue(
                canvas.startAuthoritativeScrollAnimation(
                    targetTotalScroll = 150f,
                    reason = ScrollAnimationReason.DELETION_ANCHOR,
                    durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                    forceRetarget = true
                )
            )

            val downTime = SystemClock.uptimeMillis()
            val downEv = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 200f, 500f, 0)
            val cancelEv = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_CANCEL, 200f, 500f, 0)
            try {
                canvas.dispatchTouchEvent(downEv)
                assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
                assertTrue(canvas.gapCollapseController.isTransactionOwned)
                assertTrue(canvas.gapCollapseController.shouldApplySplitRender)

                // Invoke real ScribeCursorAnimator while POINTER_FROZEN & physically below dynamic safeMinPhysical
                val animator = canvas.editor.cursorAnimator as ScribeCursorAnimator
                animator.triggerGlide(0f, 0f, 40f, 120f, 160f, 40f)

                // Option B must disable split rendering immediately and hold FINAL_SAFETY_PENDING during CORRECTION_STARTED
                assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
                assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
                assertEquals(GapCollapsePhase.FINAL_SAFETY_PENDING, canvas.gapCollapseController.phase)
                assertTrue(canvas.gapCollapseController.isTransactionOwned)
                assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
                assertTrue("Event-B SAFE_BOUNDARY_CORRECTION must run asynchronously", canvas.isGlideRunning)

                // Drive Choreographer frames until spring safety correction finishes
                var steps = 0
                while (canvas.gapCollapseController.phase == GapCollapsePhase.FINAL_SAFETY_PENDING && steps < 120) {
                    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(32))
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    steps++
                }

                // Event B must converge via CORRECTED_AND_VALIDATED to IDLE cleanly
                assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
                assertFalse(canvas.gapCollapseController.isTransactionOwned)
                assertFalse(canvas.isDeletionTransactionActive)
                assertEquals(gen + 1L, canvas.currentDeletionTransactionGeneration)
                val settledPhysical = canvas.scrollD + canvas.editor.offsetY
                assertTrue(
                    "Physical scroll ($settledPhysical) must be corrected inside [${expectedBounds.safeMinPhysical}, ${expectedBounds.safeMax}]",
                    settledPhysical in expectedBounds.safeMinPhysical..expectedBounds.safeMax
                )
                assertFalse("No Mode-1 or Mode-2 animation may remain active after Event B settles", canvas.isGlideRunning)
                assertFalse(canvas.scrollCoordinator.isAnimationActive)

                canvas.dispatchTouchEvent(cancelEv)
                assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            } finally {
                downEv.recycle()
                cancelEv.recycle()
                (canvas.editor.cursorAnimator as? ScribeCursorAnimator)?.cancel()
                canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
            }
        }
    }

    private fun awaitWordwrapLayoutReady(editor: ScribeCodeEditor, timeoutMs: Long = 5000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (editor.layout != null && editor.isWordwrapReady() && !editor.isLayoutBusy) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                if (editor.layout != null && editor.isWordwrapReady() && !editor.isLayoutBusy) {
                    var readyDispatched = false
                    editor.doOnLayoutReady { readyDispatched = true }
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    if (readyDispatched && editor.layout != null && editor.isWordwrapReady() && !editor.isLayoutBusy) {
                        return
                    }
                }
            }
            Thread.sleep(5L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Wordwrap layout must be ready and not busy before entering split collapse (layout=${editor.layout != null}, wordwrapReady=${editor.isWordwrapReady()}, layoutBusy=${editor.isLayoutBusy})",
            editor.layout != null && editor.isWordwrapReady() && !editor.isLayoutBusy
        )
    }

    private fun enterSplitCollapseAt(
        canvas: UnifiedCanvasLayout,
        seamLine: Int,
        tauLowerPx: Float,
        requiresMode1: Boolean = false,
        freezeToPointer: Boolean = false
    ): Pair<Long, Long> {
        canvas.editor.isWordwrap = true
        awaitWordwrapLayoutReady(canvas.editor)
        val lay = canvas.editor.layout!!
        val seamCharIdx = canvas.editor.text.getCharIndex(seamLine, 0)
        val seamRow = lay.getRowIndexForPosition(seamCharIdx)
        val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(1f)
        val seamDocY = seamRow * rowH
        val preScrollY = (canvas.scrollD + canvas.editor.offsetY)
        val gen = canvas.beginPreEditDeletionSnapshot(
            isSelection = true,
            selectionStartLine = (seamLine - 2).coerceAtLeast(0),
            selectionEndLine = seamLine,
            preEditDocLineCount = canvas.editor.text.lineCount,
            preEditDocHeight = (canvas.headerHeight + lay.layoutHeight).toFloat(),
            preEditScrollY = preScrollY.toFloat(),
            preEditCaretScreenY = 300f,
            wasAtTail = false,
            affectsDocumentTail = false,
            preEditLastNonBlankLine = canvas.editor.text.lineCount - 1,
            markPendingSettlement = false
        )
        val plan = makeTestCollapsePlan(
            preScrollY = preScrollY,
            effectiveTargetScroll = if (requiresMode1) (preScrollY - 120).coerceAtLeast(0).toFloat() else preScrollY.toFloat(),
            requiresMode1 = requiresMode1,
            initialTauLowerPx = tauLowerPx,
            safeMinPhysical = 0,
            safeMax = canvas.maxCanvasScroll.coerceAtLeast(2500)
        )
        val epoch = canvas.gapCollapseController.beginCollapse(
            owningGen = gen,
            plan = plan,
            seamLine = seamLine,
            seamFirstLowerRow = seamRow,
            seamDocY = seamDocY,
            rowHeightPx = rowH,
            initialPhysicalScrollPx = preScrollY.toFloat()
        )
        if (requiresMode1) {
            canvas.gapCollapseController.markMode1Started(gen, epoch, preScrollY.toFloat())
        }
        if (freezeToPointer) {
            assertTrue(canvas.gapCollapseController.freezeForPointer(gen))
        }
        canvas.syncActiveSeamBaselineFromController()
        return gen to canvas.gapCollapseController.collapseAnimEpoch
    }

    @Test
    fun testTouchUntranslateYOffsetAndHandleHitTestingAcrossActiveSplitSeam() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 80)
        val editor = canvas.editor
        editor.isCursorAnimationEnabled = false
        canvas.scrollCanvasBy(350f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val rowH = editor.rowHeight.toFloat().coerceAtLeast(20f)
        val tau = rowH * 4f
        val seamLine = 20
        enterSplitCollapseAt(canvas, seamLine = seamLine, tauLowerPx = tau, requiresMode1 = false)
        assertTrue(canvas.gapCollapseController.shouldApplySplitRender)

        val seamDocY = canvas.gapCollapseController.activeSeamDocY
        // Upper region unchanged
        assertEquals(seamDocY - 15f, editor.untranslateYOffset(seamDocY - 15f), 0.001f)
        // Collapsed gap band [seamDocY, seamDocY + tau) clamps to seamDocY
        assertEquals(seamDocY, editor.untranslateYOffset(seamDocY), 0.001f)
        assertEquals(seamDocY, editor.untranslateYOffset(seamDocY + tau * 0.5f), 0.001f)
        // Lower region subtracts tauLower at most once
        assertEquals(seamDocY + rowH, editor.untranslateYOffset(seamDocY + tau + rowH), 0.001f)

        // getPointPosition & getPointVisualPosition map lower screen Y back to logical line below seam
        val testX = editor.measureTextRegionOffset() + 40f
        val targetLine = seamLine + 2
        val targetDocOffset = editor.layout.getCharLayoutOffset(targetLine, 0)!!
        val visualTranslatedDocY = targetDocOffset[0] + tau - (rowH * 0.5f)
        val packedPos = editor.getPointPosition(testX, visualTranslatedDocY)
        val mappedLine = IntPair.getFirst(packedPos)
        val visualPos = editor.getPointVisualPosition(testX, visualTranslatedDocY)
        assertEquals("getPointPosition must untranslate lower region once", targetLine, mappedLine)
        assertEquals("getPointVisualPosition must agree with untranslated row", targetLine, visualPos.line)

        // Handle hit testing adds +tauLower for handles on or below activeSeamLine
        editor.setSelectionRegion(seamLine, 0, seamLine + 1, 4, false)
        val lay = editor.layout
        val leftOff = lay.getCharLayoutOffset(seamLine, 0)!!
        val handleX = leftOff[1] + editor.measureTextRegionOffset() - editor.offsetX
        val unshiftedAnchorY = leftOff[0] - editor.offsetY + rowH
        val shiftedAnchorY = unshiftedAnchorY + tau
        assertFalse(
            "Unshifted Y must NOT match handle during split render",
            editor.isTouchOnHandle(handleX, unshiftedAnchorY + 6f)
        )
        assertTrue(
            "Shifted Y (+tauLower) must hit selection handle during split render",
            editor.isTouchOnHandle(handleX, shiftedAnchorY + 6f)
        )

        canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "test_cleanup")
        assertEquals(seamDocY + tau, editor.untranslateYOffset(seamDocY + tau), 0.001f)
    }

    @Test
    fun testMidCollapseEightRowOperationMatrixAndPolicyABMutations() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 90)
        val editor = canvas.editor
        editor.isCursorAnimationEnabled = false
        canvas.scrollCanvasBy(400f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val rowH = editor.rowHeight.toFloat().coerceAtLeast(20f)

        // Row 1a: Insert strictly below active seam -> preserve phase, tauLower, G, E, baseline
        run {
            val (gen, epoch) = enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, requiresMode1 = true)
            val preBaseline = canvas.gapCollapseController.activeSeamDocY
            editor.text.insert(30, 0, "tail line insert\n")
            assertEquals(GapCollapsePhase.MODE_1_PHYSICAL_COUPLED, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(preBaseline, canvas.gapCollapseController.activeSeamDocY, 0.001f)
            assertEquals(rowH * 4f, canvas.gapCollapseController.tauLower, 0.001f)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r1a")
        }

        // Row 1b: Insert above/at seam without row shift (deltaY == 0) -> preserve phase, tau, G, E, update baseline
        run {
            val (gen, epoch) = enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.text.insert(15, 2, "abc")
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(20, canvas.gapCollapseController.activeSeamLine)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r1b")
        }

        // Row 2: Small newline insert above seam (0.5f < deltaY <= tau) -> rebase residual tau into POINTER_FROZEN / Mode 2
        run {
            val (gen, epoch) = enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, requiresMode1 = true)
            editor.text.insert(10, 0, "\n")
            assertEquals(GapCollapsePhase.MODE_2_VISUAL_SETTLING, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch + 1L, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(21, canvas.gapCollapseController.activeSeamLine)
            assertEquals(rowH * 3f, canvas.gapCollapseController.tauLower, 1.5f)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r2")
        }

        // Row 3: Undo/Redo small insert above seam -> identical rebase
        run {
            editor.text.insert(5, 0, "\n")
            editor.undo()
            val (gen, _) = enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            assertTrue(editor.canRedo())
            editor.redo()
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(21, canvas.gapCollapseController.activeSeamLine)
            assertEquals(rowH * 3f, canvas.gapCollapseController.tauLower, 1.5f)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r3")
        }

        // Row 4: Inline insertion at (seamLine, 0) (newSeamCol != 0) or deltaY > tau -> fallback
        run {
            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 2f, freezeToPointer = true)
            editor.text.insert(20, 0, "X")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(canvas.gapCollapseController.shouldApplySplitRender)

            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 1.5f, freezeToPointer = true)
            editor.text.insert(10, 0, "\n\n\n")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
        }

        // Row 5: Second deletion interrupts collapse and marks Deletion #2 INELIGIBLE_FALLBACK
        run {
            val (gen1, _) = enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.setSelectionRegion(8, 0, 10, 0, false)
            editor.deleteText()
            assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
            assertNotEquals(gen1, canvas.currentDeletionTransactionGeneration)
            assertTrue(canvas.forceFallbackOnDelayedSettlement || canvas.gapCollapseController.phase != GapCollapsePhase.POINTER_FROZEN)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Second deletion must never launch gap collapse", canvas.gapCollapseController.shouldApplySplitRender)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r5")
        }

        // Row 6: Content.replace + Policy B (updateImageDescription, removeImageTag, setText) cancel pre-mutation
        run {
            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            val prePhysical = canvas.scrollD + editor.offsetY
            editor.text.replace(5, 0, 5, 4, "Repl")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertEquals(prePhysical, canvas.scrollD + editor.offsetY)

            // Policy B: updateImageDescription / removeImageTag / applyFormattedTextSilently / setText cancel collapse pre-mutation
            canvas.editor.text.insert(4, 0, "![alt](vault-img://img1)\n")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.updateImageDescription("vault-img://img1", "updated-alt")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)

            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.removeImageTag("vault-img://img1")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)

            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.applyFormattedTextSilently(
                newText = editor.text.toString(),
                oldIndent = 0,
                newIndent = 0,
                savedLine = 4,
                savedCol = 0,
                adjustedCol = 0,
                savedScrollX = 0,
                savedScrollY = editor.offsetY,
                savedCanvasScrollD = canvas.scrollD
            )
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)

            enterSplitCollapseAt(canvas, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.setText("Fresh document line 1\nFresh document line 2\nFresh document line 3")
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(editor.isInContentReplace)
        }

        // Row 7: Empty replacement ("") interrupts active collapse without triggering a nested collapse
        run {
            val canvas2 = createLaidOutCanvas(headerHeightPx = 200, lineCount = 80)
            canvas2.editor.isCursorAnimationEnabled = false
            canvas2.scrollCanvasBy(350f)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            enterSplitCollapseAt(canvas2, seamLine = 20, tauLowerPx = rowH * 4f, freezeToPointer = true)
            canvas2.editor.text.replace(6, 0, 9, 0, "")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse("Empty replace interrupting active collapse must settle as fallback without split render", canvas2.gapCollapseController.shouldApplySplitRender)
            canvas2.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "r7")
        }

        // Policy A: handleProseNewline() with blank line indent normalization keeps collapse intact
        run {
            val canvas3 = createLaidOutCanvas(headerHeightPx = 200, lineCount = 80)
            canvas3.editor.isCursorAnimationEnabled = false
            canvas3.editor.firstLineIndentSpaces = 4
            canvas3.editor.text.insert(8, 0, "\n")
            canvas3.editor.text.replace(8, 0, 8, canvas3.editor.text.getColumnCount(8), "")
            canvas3.scrollCanvasBy(350f)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val rowH3 = canvas3.editor.rowHeight.toFloat().coerceAtLeast(20f)
            enterSplitCollapseAt(canvas3, seamLine = 25, tauLowerPx = rowH3 * 4f, freezeToPointer = true)
            canvas3.editor.setSelection(8, 0)
            canvas3.editor.handleProseNewline()
            assertEquals(
                "Policy A handleProseNewline must rebase instead of false Policy-B cancel",
                GapCollapsePhase.POINTER_FROZEN,
                canvas3.gapCollapseController.phase
            )
            assertEquals(26, canvas3.gapCollapseController.activeSeamLine)
            canvas3.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "pa_cleanup")
        }
    }

    @Test
    fun testPolicyAAutoIndentFloorAndCommitTextSeamReconciliation() {
        // Case 1: enforceAutoIndentFloor() during active collapse (above seam, below seam, and on seam line)
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 90)
            val editor = canvas.editor
            editor.isCursorAnimationEnabled = false
            // Create under-indented whitespace lines so text.replace triggers BOTH ACTION_DELETE and ACTION_INSERT
            editor.text.insert(8, 0, "  \n")
            editor.text.insert(31, 0, "  \n")
            canvas.scrollCanvasBy(350f)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val rowH = editor.rowHeight.toFloat().coerceAtLeast(20f)

            // 1a: Above seam (line 8 < 25) -> PreservePhase, no ACTION_DELETE transaction corruption
            val (gen, epoch) = enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            val preSeamDocY = canvas.gapCollapseController.activeSeamDocY
            editor.setSelection(8, 0)
            editor.firstLineIndentSpaces = 4
            editor.enforceAutoIndentFloor()
            assertEquals("    ", editor.text.getLineString(8))
            assertEquals(4, editor.cursor.leftColumn)
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(25, canvas.gapCollapseController.activeSeamLine)
            assertEquals(preSeamDocY, canvas.gapCollapseController.activeSeamDocY, 0.001f)
            assertEquals(rowH * 4f, canvas.gapCollapseController.tauLower, 0.001f)
            assertEquals(gen, canvas.currentDeletionTransactionGeneration)
            assertFalse(canvas.hasPendingDeletionSettlement)

            // 1b: Below seam (line 31 > 25) -> Row 1a preserves collapse intact
            editor.firstLineIndentSpaces = 0
            editor.setSelection(31, 0)
            editor.firstLineIndentSpaces = 4
            editor.enforceAutoIndentFloor()
            assertEquals("    ", editor.text.getLineString(31))
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(rowH * 4f, canvas.gapCollapseController.tauLower, 0.001f)

            // 1c: On the seam line itself (line 25 == seamLine) -> Row 4 conservative fallback
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "1b_cleanup")
            editor.firstLineIndentSpaces = 0
            editor.text.replace(25, 0, 25, editor.text.getColumnCount(25), "  ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.setSelection(25, 0)
            editor.firstLineIndentSpaces = 4
            editor.enforceAutoIndentFloor()
            assertEquals("    ", editor.text.getLineString(25))
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
            assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
            assertEquals((canvas.scrollD + editor.offsetY).toFloat(), canvas.scrollCoordinator.totalScroll, 0.5f)
        }

        // Case 2: commitText() with blank-line indentation normalization during active collapse
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 90)
            val editor = canvas.editor
            editor.isCursorAnimationEnabled = false
            editor.text.insert(8, 0, "  \n")
            canvas.scrollCanvasBy(350f)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val rowH = editor.rowHeight.toFloat().coerceAtLeast(20f)

            // 2a: Single-character commitText on under-indented line above seam preserves phase cleanly
            val (gen1, epoch1) = enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            val preDocY1 = canvas.gapCollapseController.activeSeamDocY
            editor.setSelection(8, 0)
            editor.firstLineIndentSpaces = 4
            editor.commitText("H", false, false)
            assertEquals("    H", editor.text.getLineString(8))
            assertEquals(5, editor.cursor.leftColumn)
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen1, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch1, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(25, canvas.gapCollapseController.activeSeamLine)
            assertEquals(preDocY1, canvas.gapCollapseController.activeSeamDocY, 0.001f)
            assertEquals(rowH * 4f, canvas.gapCollapseController.tauLower, 0.001f)
            assertEquals(gen1, canvas.currentDeletionTransactionGeneration)
            assertFalse(canvas.hasPendingDeletionSettlement)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c2a_cleanup")

            // 2b: Multi-line commitText on under-indented line normalizes seam before super.commitText and rebases tauLower
            editor.firstLineIndentSpaces = 0
            editor.text.replace(8, 0, 8, editor.text.getColumnCount(8), "  ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val (gen2, epoch2) = enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            val preDocY2 = canvas.gapCollapseController.activeSeamDocY
            editor.setSelection(8, 0)
            editor.firstLineIndentSpaces = 4
            editor.commitText("Alpha\nBeta", false, false)
            assertEquals("        Alpha", editor.text.getLineString(8))
            assertEquals("    Beta", editor.text.getLineString(9))
            assertEquals(GapCollapsePhase.POINTER_FROZEN, canvas.gapCollapseController.phase)
            assertEquals(gen2, canvas.gapCollapseController.owningDeletionGen)
            assertEquals(epoch2 + 1L, canvas.gapCollapseController.collapseAnimEpoch)
            assertEquals(26, canvas.gapCollapseController.activeSeamLine)
            assertEquals(preDocY2 + rowH, canvas.gapCollapseController.activeSeamDocY, 1.5f)
            assertEquals(rowH * 3f, canvas.gapCollapseController.tauLower, 1.5f)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "c2b_cleanup")
        }

        // Case 3: Unavailable / busy layout during internal indent mutation triggers conservative fallback
        run {
            val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 90)
            val editor = canvas.editor
            editor.isCursorAnimationEnabled = false
            editor.text.insert(8, 0, "  \n")
            canvas.scrollCanvasBy(350f)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val rowH = editor.rowHeight.toFloat().coerceAtLeast(20f)

            // 3a: enforceAutoIndentFloor() while layout is busy
            enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.setSelection(8, 0)
            editor.firstLineIndentSpaces = 4
            try {
                editor.dispatchEvent(LayoutStateChangeEvent(editor, true))
                assertTrue(editor.isLayoutBusy)
                editor.enforceAutoIndentFloor()
            } finally {
                editor.dispatchEvent(LayoutStateChangeEvent(editor, false))
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
            assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
            assertFalse(canvas.gapCollapseController.isTransactionOwned)
            assertEquals((canvas.scrollD + editor.offsetY).toFloat(), canvas.scrollCoordinator.totalScroll, 0.5f)

            // 3b: commitText() with indent normalization while layout is busy
            editor.firstLineIndentSpaces = 0
            editor.text.replace(8, 0, 8, editor.text.getColumnCount(8), "  ")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
            editor.setSelection(8, 0)
            editor.firstLineIndentSpaces = 4
            try {
                editor.dispatchEvent(LayoutStateChangeEvent(editor, true))
                assertTrue(editor.isLayoutBusy)
                editor.commitText("X", false, false)
            } finally {
                editor.dispatchEvent(LayoutStateChangeEvent(editor, false))
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals("    X", editor.text.getLineString(8))
            assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
            assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
            assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
            assertFalse(canvas.gapCollapseController.isTransactionOwned)
            assertEquals((canvas.scrollD + editor.offsetY).toFloat(), canvas.scrollCoordinator.totalScroll, 0.5f)
        }
    }

    @Test
    fun testWidthChangeDetachCleanupSerializedHeightResizeAndScrollDFloatInvariant() {
        val canvas = createLaidOutCanvas(headerHeightPx = 200, lineCount = 120)
        canvas.editor.isCursorAnimationEnabled = false
        canvas.scrollCanvasBy(500f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        // scrollDFloat invariant: strictly scrollD.toFloat() + outerScrollResidualPx (never S_actual)
        assertEquals(200, canvas.scrollD)
        assertEquals(300, canvas.editor.offsetY)
        assertEquals(
            canvas.scrollD.toFloat() + canvas.outerScrollResidualPxForTest,
            canvas.outerScrollDFloatForTest,
            0.001f
        )
        assertNotEquals(
            (canvas.scrollD + canvas.editor.offsetY).toFloat(),
            canvas.outerScrollDFloatForTest
        )

        val rowH = canvas.editor.rowHeight.toFloat().coerceAtLeast(20f)
        // Width change cancels active collapse and syncs scrollCoordinator
        enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, requiresMode1 = true)
        assertTrue(canvas.gapCollapseController.isTransactionOwned)
        canvas.measure(
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
        )
        canvas.layout(0, 0, 640, 1200)
        assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
        assertFalse(canvas.gapCollapseController.isTransactionOwned)
        assertEquals((canvas.scrollD + canvas.editor.offsetY).toFloat(), canvas.scrollCoordinator.totalScroll, 0.5f)

        // Height-only shrink during active collapse serializes through FINAL_SAFETY_PENDING -> Event B -> Eval
        canvas.isKeyboardVisible = true
        val (genH, _) = enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 4f, freezeToPointer = true)
        canvas.measure(
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        )
        canvas.layout(0, 0, 640, 900)
        // Even if Event B completed synchronously on laid-out host, split rendering is immediately disabled
        // and transaction transitions cleanly without stale Mode-1/Mode-2 overlap.
        assertFalse(canvas.gapCollapseController.shouldApplySplitRender)
        assertEquals(0f, canvas.gapCollapseController.tauLower, 0.001f)
        assertTrue(
            canvas.gapCollapseController.phase == GapCollapsePhase.IDLE ||
                canvas.gapCollapseController.phase == GapCollapsePhase.FINAL_SAFETY_PENDING
        )
        assertEquals(canvas.scrollD.toFloat() + canvas.outerScrollResidualPxForTest, canvas.outerScrollDFloatForTest, 0.001f)
        if (canvas.gapCollapseController.isTransactionOwned) {
            assertEquals(genH, canvas.gapCollapseController.owningDeletionGen)
            canvas.cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "h_cleanup")
        }

        // Detach cleanup cancels collapse and syncs physical scroll
        enterSplitCollapseAt(canvas, seamLine = 25, tauLowerPx = rowH * 3f, freezeToPointer = true)
        (canvas.parent as? android.view.ViewGroup)?.removeView(canvas)
        assertEquals(GapCollapsePhase.IDLE, canvas.gapCollapseController.phase)
        assertEquals((canvas.scrollD + canvas.editor.offsetY).toFloat(), canvas.scrollCoordinator.totalScroll, 0.5f)
    }
}
