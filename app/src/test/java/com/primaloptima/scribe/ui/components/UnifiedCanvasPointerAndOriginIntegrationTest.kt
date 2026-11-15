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
import com.primaloptima.scribe.ui.editor.scroll.ScrollAnimationReason
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
}
