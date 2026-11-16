package com.primaloptima.scribe.ui.components

import com.primaloptima.scribe.ui.editor.scroll.*

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.OverScroller
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.EdgeEffectCompat
import com.primaloptima.scribe.util.model.EditingTarget
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs
import kotlin.math.roundToInt

internal val CodeEditor.safeScrollMaxY: Int
    get() = try {
        scrollMaxY.coerceAtLeast(0)
    } catch (_: Throwable) {
        0
    }

/**
 * UnifiedCanvasLayout — Edge-to-Edge Document Canvas Architecture.
 *
 * Hosts a ComposeView header (Child 0) and a Sora CodeEditor (Child 1) inside a single,
 * continuous vertical document canvas. Coordinates touch dispatch, flings across the
 * boundary, keyboard inset management, and 120Hz smooth rendering without touch hijacking.
 *
 * RIGID 1-DEGREE-OF-FREEDOM INVARIANT:
 * The title section and Line 0 of the document are physically locked together.
 * - When scrollD < headerHeight:
 *     editor.offsetY is strictly clamped to 0.
 *     headerView.translationY = -scrollD
 *     editor.translationY = -scrollD
 *     Bottom of Header = headerHeight - scrollD
 *     Top of Line 0 = headerHeight - scrollD - 0 = headerHeight - scrollD.
 *     They are identical down to the sub-pixel on every single frame.
 * - When scrollD == headerHeight:
 *     The header is completely scrolled off (translationY = -headerHeight).
 *     editor.translationY = -headerHeight (editor top at 0).
 *     editor.offsetY handles subsequent text scrolling.
 */
class UnifiedCanvasLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    val headerView: ComposeView = ComposeView(context).apply {
        id = View.generateViewId()
    }

    val editor: ScribeCodeEditor = ScribeCodeEditor(context).apply {
        id = View.generateViewId()
    }

    var isKeyboardDismissing: Boolean = false
        set(value) {
            field = value
            if (value) {
                endKeyboardTransaction()
            }
        }

    var horizontalPaddingDp: Float = 28f
        set(value) {
            val clamped = value.coerceIn(8f, 72f)
            if (field != clamped) {
                field = clamped
                editor.horizontalPaddingDp = clamped
                requestLayout()
                invalidate()
            }
        }

    val breathingMarginPx: Float
        get() = ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * resources.displayMetrics.density

    /**
     * Pure Writer persistent bottom space architecture:
     * Equal to where our smooth scroll is gliding, according to the shortcut bar height.
     * estimatedKeyboardHeightPx = (compactHeightDp + 36f) * density.
     * Plus breathing margin (24dp) so that the line rests comfortably above the shortcut bar.
     */
    var standardKeyboardBaselinePx: Float = 280f * context.resources.displayMetrics.density
    var physicalDockClearanceAppliedPx: Float = 0f
    var shortcutBarHeightPx: Float = 36f * context.resources.displayMetrics.density
    var hasShortcuts: Boolean = true
    var dockTransition: ScribeDockTransition = ScribeDockTransition.NONE
    var dockMode: ScribeDockMode = ScribeDockMode.HIDDEN
    var targetImeObstructionPx: Float = 0f

    var dockGeometry: ScribeDockGeometry? = null
    var overlayBoundsRegistry: ScribeOverlayBoundsRegistry? = null
    private val canvasWindowLocation = IntArray(2)
    private val canvasVisibleFrame = Rect()

    fun resolveUnobstructedViewportBounds(): UnobstructedViewportBounds {
        val w = width.toFloat().coerceAtLeast(0f)
        val h = height.toFloat().coerceAtLeast(0f)
        getLocationInWindow(canvasWindowLocation)
        val canvasWindowLeft = canvasWindowLocation[0].toFloat()
        val canvasWindowTop = canvasWindowLocation[1].toFloat()
        getWindowVisibleDisplayFrame(canvasVisibleFrame)
        val windowVisibleBottomLocal = if (canvasVisibleFrame.bottom > 0) {
            (canvasVisibleFrame.bottom - canvasWindowLocation[1]).toFloat()
        } else {
            h
        }
        val headerBottomLocal = (headerHeight - scrollD).toFloat().coerceAtLeast(0f)
        val registry = overlayBoundsRegistry
        return if (registry != null) {
            registry.resolveUnobstructedViewportBounds(
                canvasWindowTopPx = canvasWindowTop,
                canvasHeightPx = h,
                headerBottomLocal = headerBottomLocal,
                stickyBottomLocal = 0f,
                windowVisibleBottomLocal = windowVisibleBottomLocal,
                canvasWindowLeftPx = canvasWindowLeft,
                canvasWidthPx = w
            )
        } else {
            val visibleTop = maxOf(0f, headerBottomLocal)
            val visibleBottom = minOf(h, windowVisibleBottomLocal)
            val usable = (visibleBottom - visibleTop).coerceAtLeast(0f)
            UnobstructedViewportBounds(
                headerBottomLocal = headerBottomLocal,
                stickyBottomLocal = 0f,
                maxTopBarBottomLocal = 0f,
                minBottomBarTopLocal = h,
                windowVisibleBottomLocal = windowVisibleBottomLocal,
                visibleEditorTop = visibleTop,
                visibleEditorBottom = visibleBottom,
                usableHeight = usable,
                hasModalBlocker = false,
                isValid = h > 0f && visibleBottom > visibleTop,
                floatingPillLocalRects = emptyList()
            )
        }
    }

    fun resolveSafetyFloorAndBounds(
        anchorDocBottom: Float,
        maxCanvasScrollPost: Int = maxCanvasScroll,
        viewportBounds: UnobstructedViewportBounds = resolveUnobstructedViewportBounds()
    ): SafetyFloorAndBounds = ScribeGapCollapsePolicy.resolveSafetyFloorAndBounds(
        headerHeight = headerHeight,
        anchorDocBottom = anchorDocBottom,
        breathingMarginPx = breathingMarginPx,
        usableBottomForSafety = viewportBounds.visibleEditorBottom,
        maxCanvasScrollPost = maxCanvasScrollPost,
        scrollD = scrollD,
        editorOffsetY = editor.offsetY
    )

    fun evaluateVisibilityConfidence(
        viewportBounds: UnobstructedViewportBounds = resolveUnobstructedViewportBounds()
    ): VisibilityConfidence = ScribeGapCollapsePolicy.evaluateVisibilityConfidence(
        isCanvasLaidOut = isLaidOut,
        isEditorLaidOut = editor.isLaidOut,
        canvasWidthPx = width,
        canvasHeightPx = height,
        isWordwrapReady = editor.isWordwrapReady(),
        isLayoutBusy = editor.isLayoutComputing,
        hasPreLayout = editor.layout != null,
        viewportBounds = viewportBounds
    )

    fun applyDockGeometry(geometry: ScribeDockGeometry) {
        dockGeometry = geometry
        standardKeyboardBaselinePx = geometry.standardKeyboardBaselinePx
        physicalDockClearanceAppliedPx = geometry.physicalDockClearanceAppliedPx
        shortcutBarHeightPx = geometry.shortcutBarHeightPx
        hasShortcuts = geometry.isShortcutBarOccupied
        dockTransition = geometry.activeTransition
        dockMode = geometry.dockMode
        targetImeObstructionPx = geometry.targetImeObstructionPx
        editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
        checkKeyboardTransactionSettled()
    }

    val virtualTailPx: Float
        get() = ScribeScrollPolicy.calculateAuthoritativeVirtualTailPx(
            keyboardBaselinePx = standardKeyboardBaselinePx,
            shortcutBarHeightPx = if (hasShortcuts) shortcutBarHeightPx else 0f,
            breathingMarginPx = breathingMarginPx,
            physicalDockClearanceAppliedPx = physicalDockClearanceAppliedPx
        )

    val persistentBottomSpacePx: Float
        get() = virtualTailPx

    val maxCanvasScroll: Int
        get() {
            val layoutH = editor.layout?.layoutHeight ?: 0
            return ScribeScrollPolicy.calculateMaxCanvasScroll(
                headerHeight = headerHeight,
                layoutHeight = layoutH,
                viewportHeight = height,
                bottomSpace = virtualTailPx.roundToInt(),
                isTextEmpty = editor.text.isEmpty()
            )
        }

    val maxHeaderScrollD: Int
        get() = ScribeScrollPolicy.calculateMaxHeaderScroll(headerHeight, maxCanvasScroll)

    var estimatedKeyboardHeightPx: Float = (280f + 36f) * context.resources.displayMetrics.density
        set(value) {
            field = value
            standardKeyboardBaselinePx = (value - (if (hasShortcuts) shortcutBarHeightPx else 0f)).coerceAtLeast(0f)
            editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
        }

    var isKeyboardVisible: Boolean = false
        set(value) {
            field = value
            editor.isKeyboardVisible = value
        }

    var isBottomSurfaceActive: Boolean = false

    init {
        addView(headerView)
        addView(editor)
        clipChildren = false
        clipToPadding = false
        editor.horizontalPaddingDp = horizontalPaddingDp
        outlineProvider = android.view.ViewOutlineProvider.BOUNDS
        clipToOutline = true
        editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
        editor.isKeyboardVisible = isKeyboardVisible

    }

    var headerHeight: Int = 0
        private set

    
    var scrollD: Int = 0
        private set

    private var scrollDFloat: Float = 0f
    private var outerScrollResidualPx: Float = 0f
    private var editorScrollResidualPx: Float = 0f
    private var lastScrollWasBoundaryClamped: Boolean = false

    var onScrollDelta: ((dy: Float) -> Unit)? = null
    var onUnifiedScrollChanged: ((scrollD: Int, maxHeaderHeight: Int) -> Unit)? = null
    var onViewportScrollChanged: ((scrollD: Int, editorOffsetY: Int, totalLogicalScroll: Int) -> Unit)? = null
    var onUserTouchDown: (() -> Unit)? = null
    var onKeyboardEntranceStarted: (() -> Unit)? = null

    private var activePhysicalScrollOrigin: PhysicalScrollOrigin = PhysicalScrollOrigin.NONE

    internal val currentPhysicalScrollOrigin: PhysicalScrollOrigin
        get() = activePhysicalScrollOrigin

    fun withPhysicalScrollOrigin(origin: PhysicalScrollOrigin, block: () -> Unit) {
        val prev = activePhysicalScrollOrigin
        activePhysicalScrollOrigin = origin
        try {
            block()
        } finally {
            activePhysicalScrollOrigin = prev
        }
    }

    fun notifyViewportScrollChanged(origin: PhysicalScrollOrigin = activePhysicalScrollOrigin) {
        val effectiveOrigin = when {
            origin != PhysicalScrollOrigin.NONE -> origin
            activePhysicalScrollOrigin != PhysicalScrollOrigin.NONE -> activePhysicalScrollOrigin
            else -> PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC
        }
        val actualPhysical = (scrollD + editor.offsetY).toFloat()
        if (!scrollCoordinator.isAnimationActive && !canvasAnimator.isGlideRunning) {
            scrollCoordinator.syncActualScroll(actualPhysical)
        }
        gapCollapseController.onViewportPhysicalScrollChanged(actualPhysical, effectiveOrigin)
        onViewportScrollChanged?.invoke(scrollD, editor.offsetY, scrollD + editor.offsetY)
    }

    private val inputController: ScribeScrollInputController by lazy {
        ScribeScrollInputController(context, object : ScribeScrollInputController.Host {
            override val scrollD: Int get() = this@UnifiedCanvasLayout.scrollD
            override val headerHeight: Int get() = this@UnifiedCanvasLayout.headerHeight
            override val editorOffsetY: Int get() = this@UnifiedCanvasLayout.editor.offsetY
            override val maxCanvasScroll: Int get() = this@UnifiedCanvasLayout.maxCanvasScroll
            override var isUserTouching: Boolean
                get() = this@UnifiedCanvasLayout.isUserTouching
                set(value) { this@UnifiedCanvasLayout.isUserTouching = value }
            override val isAnyHandleActive: Boolean get() = this@UnifiedCanvasLayout.isAnyHandleActive
            override val isMultiTouchActive: Boolean get() = this@UnifiedCanvasLayout.isMultiTouchActive
            override fun isTouchOnHandle(editorX: Float, editorY: Float): Boolean =
                this@UnifiedCanvasLayout.editor.isTouchOnHandle(editorX, editorY)
            override fun editorLeft(): Int = this@UnifiedCanvasLayout.editor.left
            override fun editorIsHandleDragging(): Boolean = this@UnifiedCanvasLayout.editor.isHandleDragging
            override fun scrollCanvasBy(dy: Float) { this@UnifiedCanvasLayout.scrollCanvasBy(dy) }
            override fun postInvalidateOnAnimation() { this@UnifiedCanvasLayout.postInvalidateOnAnimation() }
            override fun stopEditorFling() { this@UnifiedCanvasLayout.editor.stopFling() }
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                this@UnifiedCanvasLayout.parent?.requestDisallowInterceptTouchEvent(disallow)
            }
            override fun onTopEdgeEffectRelease() {
                this@UnifiedCanvasLayout.topEdgeEffect?.onRelease()
            }
            override fun onTopEdgeEffectAbsorb(velocity: Int) {
                this@UnifiedCanvasLayout.topEdgeEffect?.onAbsorb(velocity)
            }
            override val isTopEdgeEffectFinished: Boolean
                get() = this@UnifiedCanvasLayout.topEdgeEffect?.isFinished ?: true
            override fun onUserTouchDown(touchY: Float) {
                this@UnifiedCanvasLayout.lastTouchDownY = touchY
                this@UnifiedCanvasLayout.lastTouchDownTime = System.currentTimeMillis()
                this@UnifiedCanvasLayout.onUserTouchDown?.invoke()
                this@UnifiedCanvasLayout.onPointerTouchDown(preserveRetractionDebt = true)
                this@UnifiedCanvasLayout.editor.stopFling()
            }
            override fun onManualDragStarted() {
                this@UnifiedCanvasLayout.scrollCoordinator.onManualDragConfirmed()
                this@UnifiedCanvasLayout.cancelRestoration()
                this@UnifiedCanvasLayout.scrollCoordinator.invalidateCompensation("Confirmed manual drag")
            }
            override fun runWithScrollOrigin(origin: PhysicalScrollOrigin, block: () -> Unit) {
                this@UnifiedCanvasLayout.withPhysicalScrollOrigin(origin, block)
            }
        })
    }

    val scroller: OverScroller get() = inputController.scroller
    val isDraggingCanvas: Boolean get() = inputController.isDraggingCanvas
    val lastTouchX: Float get() = inputController.lastTouchX
    val lastTouchY: Float get() = inputController.lastTouchY

    val isAnyHandleActive: Boolean
        get() = editor.isHandleDragging || inputController.isTouchOnHandleActive

    val isMultiTouchActive: Boolean
        get() = inputController.isMultiTouchLatched || (editor as? ScribeCodeEditor)?.isZoomGestureActiveOnly == true || editor.isPinchScaling

    private val topEdgeEffect: EdgeEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        EdgeEffectCompat.create(context, null)
    } else {
        null
    }

    var isUserTouching: Boolean = false
        private set

    var isPredictiveGlideRunning: Boolean = false
        private set
    var targetPredictedScrollTotal: Float = -1f
        private set

    /**
     * Set to true strictly during text deletion on the last line of the document.
     * Prevents downward anchoring scroll from triggering on taps, focus, or general edits.
     */
    var isLastLineDeletionActive: Boolean = false
        private set
    private var resetDeletionActiveRunnable: Runnable? = null

    /**
     * Authoritative single-owner scroll and animation coordinator.
     */
    internal val scrollCoordinator = ScribeScrollCoordinator().apply {
        onScrollBy = { delta ->
            val stepOrigin = when (activeReason) {
                ScrollAnimationReason.DELETION_ANCHOR ->
                    if (gapCollapseController.phase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED) {
                        PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1
                    } else {
                        PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC
                    }
                ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION ->
                    PhysicalScrollOrigin.EVENT_B_SAFETY_CORRECTION
                else -> PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC
            }
            var unconsumed = 0f
            withPhysicalScrollOrigin(stepOrigin) {
                unconsumed = scrollCanvasBy(delta)
            }
            unconsumed
        }
        wasLastStepClampedByBoundary = {
            lastScrollWasBoundaryClamped
        }
    }

    /**
     * Transition-aware compensation tracking:
     * Records upward canvas displacement caused by an expanded bottom surface (e.g. emoji keyboard,
     * accessory drawer) so that it can be safely and smoothly restored when the surface contracts.
     */
    var temporaryExpansionDisplacement: Float
        get() = scrollCoordinator.temporaryDisplacement
        internal set(value) { scrollCoordinator.temporaryDisplacement = value }
    var expansionCaretLine: Int
        get() = scrollCoordinator.caretLine
        internal set(value) { scrollCoordinator.caretLine = value }
    var expansionCaretCol: Int
        get() = scrollCoordinator.caretCol
        internal set(value) { scrollCoordinator.caretCol = value }
    var expansionHadSelection: Boolean
        get() = scrollCoordinator.isSelected
        internal set(value) { scrollCoordinator.isSelected = value }

    /**
     * Flag indicating that an expansion transition is actively in progress (e.g. keyboard entrance
     * or accessory drawer opening). Upward scroll movement during this window is recorded as
     * expansion displacement. Normal typing / cursor movement outside of this transition is NOT recorded.
     */
    var isExpansionTransitionActive: Boolean
        get() = scrollCoordinator.isExpansionTransitionActive
        internal set(value) { scrollCoordinator.isExpansionTransitionActive = value }
    private var resetExpansionTransitionRunnable: Runnable? = null

    /**
     * Authoritative canvas animation coordinator delegating to ScribeCanvasGlideAnimator.
     */
    private var mode2ChoreographerCallback: Choreographer.FrameCallback? = null

    private fun resolveDisplayRefreshRateHz(): Float {
        return try {
            display?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: 60f
        } catch (_: Throwable) {
            60f
        }
    }

    val gapCollapseController: ScribeGapCollapseController = ScribeGapCollapseController(
        scheduleFrame = { callback ->
            val cb = Choreographer.FrameCallback { frameTimeNs ->
                mode2ChoreographerCallback = null
                callback(frameTimeNs)
            }
            mode2ChoreographerCallback = cb
            Choreographer.getInstance().postFrameCallback(cb)
        },
        cancelFrame = {
            mode2ChoreographerCallback?.let { cb ->
                try {
                    Choreographer.getInstance().removeFrameCallback(cb)
                } catch (_: Throwable) {}
            }
            mode2ChoreographerCallback = null
        },
        refreshRateHzProvider = { resolveDisplayRefreshRateHz() },
        onInvalidateEditor = { editor.invalidate() },
        onMode2SettledToSafety = { owningGen, _ ->
            val se = gapCollapseController.safetyEpoch
            completeDeletionVisualTransaction(owningGen, se)
        },
        onExternalDriftAbortToSafety = { owningGen, _ ->
            scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())
            val se = gapCollapseController.safetyEpoch
            completeDeletionVisualTransaction(owningGen, se)
        },
        onFreezeMode1Animator = {
            canvasAnimator.cancelPreservingTransaction("pointer_freeze")
            scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())
        }
    )

    private val canvasAnimator: ScribeCanvasGlideAnimator = ScribeCanvasGlideAnimator(
        scrollCoordinator = scrollCoordinator,
        postOnAnimationAction = { runnable -> postOnAnimation(runnable) },
        removeCallbacksAction = { runnable -> removeCallbacks(runnable) },
        deletionBoundaryProvider = {
            if (height <= 0) {
                0f
            } else {
                val cur = try { editor.cursor } catch (_: Throwable) { null }
                val lay = editor.layout
                val caretDocY = if (cur != null && lay != null) {
                    getCharGeometry(lay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
                } else {
                    0f
                }
                val anchorDocBottom = if (isDeletionTailAnchored) {
                    maxOf((lay?.layoutHeight ?: 0).toFloat(), caretDocY)
                } else {
                    caretDocY
                }
                val viewportBounds = resolveUnobstructedViewportBounds()
                resolveSafetyFloorAndBounds(
                    anchorDocBottom = anchorDocBottom,
                    viewportBounds = viewportBounds
                ).safeMinPhysical.toFloat()
            }
        },
        onSettledCallback = { reason ->
            if (gapCollapseController.isTransactionOwned) {
                if (reason == ScrollAnimationReason.PREDICTIVE_GLIDE) {
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                }
            } else if (isDeletionTransactionActive) {
                revalidateDeletionSettlementSafety(
                    capturedGen = deletionTransactionGeneration,
                    allowCorrectiveAnimation = (reason != ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION),
                    smooth = true
                )
            } else if (reason == ScrollAnimationReason.CONTRACTION_RESTORATION) {
                revalidateFinalTargetSafety()
            } else if (reason == ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION) {
                rebaseCompensationAfterSafeCorrection()
            }
            if (reason == ScrollAnimationReason.PREDICTIVE_GLIDE) {
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
            }
        },
        isDeletionSafetyActiveProvider = {
            isDeletionTransactionActive
        },
        onFrameCommitted = { result, reason, actualPhysicalScroll ->
            if (reason == ScrollAnimationReason.DELETION_ANCHOR &&
                gapCollapseController.phase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
            ) {
                gapCollapseController.onMode1FrameCommitted(
                    expectedGen = gapCollapseController.owningDeletionGen,
                    expectedCollapseEpoch = gapCollapseController.collapseAnimEpoch,
                    actualPhysicalScrollPx = actualPhysicalScroll,
                    boundaryHit = result.boundaryHit
                )
            }
        }
    )

    val isGlideRunning: Boolean
        get() = canvasAnimator.isGlideRunning
    val glideTarget: Float
        get() = canvasAnimator.glideTarget
    val glidePosition: Float
        get() = canvasAnimator.glidePosition
    val glideVelocity: Float
        get() = canvasAnimator.glideVelocity
    val activeGlideReason: ScrollAnimationReason?
        get() = canvasAnimator.activeGlideReason
    val isRestorationActive: Boolean
        get() = scrollCoordinator.isAnimationActive && scrollCoordinator.activeReason == ScrollAnimationReason.CONTRACTION_RESTORATION
    val activeRestorationTargetScroll: Float?
        get() = if (isRestorationActive) scrollCoordinator.activeTargetTotalScroll else null
    private var pendingRestorationRunnable: Runnable? = null
    var isRestoringViewport: Boolean = false
    var suppressCursorVisibilityScroll: Boolean = false

    /**
     * Active editing target in the document (PRIMARY_TITLE, SECONDARY_TITLE, or EDITOR_BODY).
     * Synchronously updated from focus changes so viewport adjustments and IME resize
     * do not scroll or chase inactive body cursor positions when editing titles.
     */
    var activeEditingTarget: EditingTarget = EditingTarget.NONE
        set(value) {
            field = value
            if (value != EditingTarget.EDITOR_BODY) {
                endKeyboardTransaction()
            }
        }

    fun recordExpansionDisplacement(dy: Float, line: Int, col: Int = 0, isSelected: Boolean = false) {
        if (dy > 0f && isExpansionTransitionActive) {
            expansionCaretLine = line
            expansionCaretCol = col
            expansionHadSelection = isSelected
        }
    }

    fun invalidateCompensation(reason: String? = null) {
        cancelRestoration()
        scrollCoordinator.invalidateCompensation(reason)
        if (reason != null && ScribeScrollDiagnostics.isDebugDiagnosticsEnabled) {
            ScribeScrollDiagnostics.log("Compensation", "Invalidated: $reason")
        }
    }

    fun invalidateCompensationIfCaretChanged(newLine: Int, newCol: Int, isSelected: Boolean) {
        scrollCoordinator.invalidateIfCaretChanged(newLine, newCol, isSelected)
    }

    fun invalidateCompensationIfLineChanged(newLine: Int) {
        invalidateCompensationIfCaretChanged(newLine, expansionCaretCol, expansionHadSelection)
    }

    fun cancelRestoration() {
        pendingRestorationRunnable?.let { removeCallbacks(it) }
        pendingRestorationRunnable = null
        if (isRestorationActive) {
            canvasAnimator.cancel("Restoration cancelled")
        }
    }

    // ── Coordinated Keyboard Viewport Transaction ───────────────────────────
    var lastTouchDownY: Float? = null
        private set
    var lastTouchDownTime: Long = 0L
        private set

    private var activeKeyboardTransaction: ScribeScrollPolicy.KeyboardTransaction? = null
    private var keyboardTransactionId = 0L

    val isKeyboardTransactionActive: Boolean
        get() = activeKeyboardTransaction?.isActive == true

    fun beginKeyboardTransaction(targetScroll: Float = -1f): Long {
        keyboardTransactionId++
        activeKeyboardTransaction = ScribeScrollPolicy.KeyboardTransaction(
            transactionId = keyboardTransactionId,
            isActive = true,
            targetScroll = targetScroll
        )
        return keyboardTransactionId
    }

    fun notifyKeyboardEntranceStarting() {
        isKeyboardDismissing = false
        predictivelyGlideCursorAboveKeyboard()
    }

    fun endKeyboardTransaction() {
        activeKeyboardTransaction = null
    }

    fun checkKeyboardTransactionSettled() {
        if (isKeyboardTransactionActive &&
            !isGlideRunning &&
            dockTransition != ScribeDockTransition.IME_ENTRANCE &&
            dockTransition != ScribeDockTransition.DRAWER_TO_IME_HANDOFF &&
            !isExpansionTransitionActive
        ) {
            endKeyboardTransaction()
        }
    }

    // ── Coordinated Deletion Viewport Transaction ─────────────────────────────
    private var activeDeletionSnapshot: ScribeScrollDeletionPolicy.DeletionSnapshot? = null
    private var deletionTransactionGeneration: Long = 0L
    private var settledClaimedGeneration: Long = -1L
    private var isDeletionTailAnchored: Boolean = false
    var forceFallbackOnDelayedSettlement: Boolean = false
        private set
    var pendingPreDeleteSeamCapture: PreDeleteSeamCapture? = null
        private set
    var resolvedSeamGeometry: ResolvedSeamGeometry? = null
        private set
    var activeSeamLine: Int = -1
        private set
    var activeSeamFirstLowerRow: Int = -1
        private set
    var activeSeamDocY: Float = 0f
        private set
    private var isDeletionResetRetractionActive: Boolean = false
    private var deletionResetGeneration: Long = 0L
    private var isInnerRetractionDebtActive: Boolean = false
    private var isOuterRetractionDebtActive: Boolean = false
    private var pendingDeletionLayoutListener: View.OnLayoutChangeListener? = null
    private var pendingDeletionAttachListener: View.OnAttachStateChangeListener? = null

    var hasPendingDeletionSettlement: Boolean = false
        private set

    val currentDeletionTransactionGeneration: Long
        get() = deletionTransactionGeneration

    val isGapCollapseTransactionOwned: Boolean
        get() = gapCollapseController.isTransactionOwned

    val isDeletionTransactionActive: Boolean
        get() = activeDeletionSnapshot != null

    private fun clearActiveSeamBaseline() {
        activeSeamLine = -1
        activeSeamFirstLowerRow = -1
        activeSeamDocY = 0f
    }

    fun canStillSettleGeneration(expectedGen: Long): Boolean {
        val scribeEditor = editor as? ScribeCodeEditor
        return isDeletionTransactionActive &&
            deletionTransactionGeneration == expectedGen &&
            hasPendingDeletionSettlement &&
            settledClaimedGeneration != expectedGen &&
            !isGapCollapseTransactionOwned &&
            scribeEditor?.isInContentReplace != true
    }

    fun tryClaimPendingDeletionSettlement(expectedGen: Long): Boolean {
        val scribeEditor = editor as? ScribeCodeEditor
        val editorBusy = scribeEditor?.isLayoutBusy ?: editor.isLayoutComputing
        if (height <= 0 ||
            !isLaidOut ||
            !editor.isLaidOut ||
            !editor.isWordwrapReady() ||
            editorBusy ||
            editor.layout == null
        ) {
            return false
        }
        if (!canStillSettleGeneration(expectedGen)) {
            return false
        }
        settledClaimedGeneration = expectedGen
        hasPendingDeletionSettlement = false
        clearPendingDeletionLayoutListener()
        return true
    }

    private val allowInnerTransitionalRetraction: Boolean
        get() = isDeletionTransactionActive || isDeletionResetRetractionActive || isInnerRetractionDebtActive

    private val allowOuterTransitionalRetraction: Boolean
        get() = isDeletionTransactionActive || isDeletionResetRetractionActive || isOuterRetractionDebtActive

    val isDeletionRetractionInProgress: Boolean
        get() = isDeletionTransactionActive ||
            isDeletionResetRetractionActive ||
            isInnerRetractionDebtActive ||
            isOuterRetractionDebtActive

    private fun clearPendingDeletionLayoutListener() {
        pendingDeletionLayoutListener?.let { removeOnLayoutChangeListener(it) }
        pendingDeletionLayoutListener = null
        pendingDeletionAttachListener?.let { removeOnAttachStateChangeListener(it) }
        pendingDeletionAttachListener = null
    }

    private fun clearDeletionResetRetraction() {
        isDeletionResetRetractionActive = false
        deletionResetGeneration++
    }

    private fun clearAllRetractionDebt() {
        isInnerRetractionDebtActive = false
        isOuterRetractionDebtActive = false
    }

    private fun updateRetractionDebtFromCurrentPosition(armIfOverscrolled: Boolean) {
        val maxD = ScribeScrollPolicy.calculateMaxHeaderScroll(headerHeight, maxCanvasScroll)
        val safeMaxY = (maxCanvasScroll - headerHeight).coerceAtLeast(0)
        val editorOverscrolled = scrollD >= headerHeight && editor.offsetY > safeMaxY
        val outerOverscrolled = scrollD > maxD
        if (armIfOverscrolled) {
            isInnerRetractionDebtActive = editorOverscrolled
            isOuterRetractionDebtActive = outerOverscrolled
        } else {
            if (isInnerRetractionDebtActive && !editorOverscrolled) {
                isInnerRetractionDebtActive = false
            }
            if (isOuterRetractionDebtActive && !outerOverscrolled) {
                isOuterRetractionDebtActive = false
            }
        }
    }

    fun claimAndClearLiveDeletionTransaction(preserveRetractionDebt: Boolean): Boolean {
        val wasDeletionRetracting = isDeletionTransactionActive || isDeletionResetRetractionActive
        val hasLiveTx = isDeletionTransactionActive ||
            hasPendingDeletionSettlement ||
            isDeletionTailAnchored ||
            isDeletionResetRetractionActive
        if (!hasLiveTx) {
            return false
        }
        clearPendingDeletionLayoutListener()
        activeDeletionSnapshot = null
        pendingPreDeleteSeamCapture = null
        resolvedSeamGeometry = null
        hasPendingDeletionSettlement = false
        isDeletionTailAnchored = false
        forceFallbackOnDelayedSettlement = false
        clearActiveSeamBaseline()
        clearDeletionResetRetraction()
        if (preserveRetractionDebt && wasDeletionRetracting) {
            updateRetractionDebtFromCurrentPosition(armIfOverscrolled = true)
        } else {
            clearAllRetractionDebt()
        }
        settledClaimedGeneration = -1L
        deletionTransactionGeneration++
        return true
    }

    fun clearDeletionTransaction() {
        claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)
    }

    fun markPendingDeletionSettlement() {
        if (activeDeletionSnapshot != null) {
            hasPendingDeletionSettlement = true
        }
    }

    fun updateDeletionAffectsTailIfNeeded(affectsTail: Boolean) {
        if (!affectsTail) return
        activeDeletionSnapshot = activeDeletionSnapshot?.let { snap ->
            if (snap.affectsDocumentTail) snap else snap.copy(affectsDocumentTail = true)
        }
    }

    fun mergeDeletionBatchTail(batchEndLine: Int, batchLastNonBlankLine: Int? = null) {
        activeDeletionSnapshot = activeDeletionSnapshot?.let { snap ->
            ScribeScrollDeletionPolicy.mergeDeletionBatchSnapshot(
                existing = snap,
                batchEndLine = batchEndLine,
                currentLastNonBlankLine = batchLastNonBlankLine ?: snap.preEditLastNonBlankLine
            )
        }
    }

    fun beginPreEditDeletionSnapshot(
        isSelection: Boolean,
        selectionStartLine: Int,
        selectionEndLine: Int,
        preEditDocLineCount: Int,
        preEditDocHeight: Float,
        preEditScrollY: Float,
        preEditCaretScreenY: Float,
        wasAtTail: Boolean,
        affectsDocumentTail: Boolean,
        preEditLastNonBlankLine: Int = (preEditDocLineCount - 1).coerceAtLeast(0),
        markPendingSettlement: Boolean = false,
        preDeleteSeamCapture: PreDeleteSeamCapture? = null
    ): Long {
        (editor as? ScribeCodeEditor)?.cancelPendingTextActionRefresh(clearSavedState = false)
        clearPendingDeletionLayoutListener()
        clearDeletionResetRetraction()
        clearAllRetractionDebt()
        settledClaimedGeneration = -1L
        forceFallbackOnDelayedSettlement = false
        pendingPreDeleteSeamCapture = preDeleteSeamCapture
        resolvedSeamGeometry = null
        clearActiveSeamBaseline()
        deletionTransactionGeneration++
        activeDeletionSnapshot = ScribeScrollDeletionPolicy.DeletionSnapshot(
            transactionEpoch = deletionTransactionGeneration,
            isSelection = isSelection,
            selectionStartLine = selectionStartLine,
            selectionEndLine = selectionEndLine,
            preEditDocLineCount = preEditDocLineCount,
            preEditDocHeight = preEditDocHeight,
            preEditScrollY = preEditScrollY,
            preEditCaretScreenY = preEditCaretScreenY,
            wasAtTail = wasAtTail,
            affectsDocumentTail = affectsDocumentTail,
            preEditLastNonBlankLine = preEditLastNonBlankLine
        )
        if (markPendingSettlement) {
            hasPendingDeletionSettlement = true
        }
        return deletionTransactionGeneration
    }

    fun notifyContentDeletionStarted() {
        if (hasPendingDeletionSettlement) return
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val lay = try { editor.layout } catch (_: Throwable) { null }
        val content = editor.text
        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val docH = (headerHeight + (lay?.layoutHeight ?: 0)).toFloat()
        val visibleH = height.toFloat()
        val breathingMargin = breathingMarginPx
        val tailTolerancePx = 2f * resources.displayMetrics.density
        val wasAtTail = (currentScroll + visibleH) >= (docH - breathingMargin - tailTolerancePx)
        val caretDocY = if (cur != null && lay != null) {
            getCharGeometry(lay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
        } else {
            0f
        }
        val caretScreenY = (headerHeight + caretDocY - currentScroll)
        val endLine = if (cur?.isSelected == true) cur.rightLine else (cur?.leftLine ?: 0)
        val lastNonBlankLine = ScribeScrollDeletionPolicy.findLastNonBlankLine(content.lineCount) { idx ->
            content.getLine(idx)
        }
        val affectsDocumentTail = ScribeScrollDeletionPolicy.isLineRangeAffectingLogicalTail(endLine, lastNonBlankLine)
        beginPreEditDeletionSnapshot(
            isSelection = cur?.isSelected == true,
            selectionStartLine = cur?.leftLine ?: 0,
            selectionEndLine = cur?.rightLine ?: 0,
            preEditDocLineCount = content.lineCount,
            preEditDocHeight = docH,
            preEditScrollY = currentScroll,
            preEditCaretScreenY = caretScreenY,
            wasAtTail = wasAtTail,
            affectsDocumentTail = affectsDocumentTail,
            preEditLastNonBlankLine = lastNonBlankLine,
            markPendingSettlement = true
        )
    }

    fun notifyLastLineDeletionStarted() {
        notifyContentDeletionStarted()
    }

    private fun deferDeletionSettlementUntilLayoutReady(capturedGen: Long, smooth: Boolean) {
        if (pendingDeletionLayoutListener != null || !isAttachedToWindow) return
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                clearPendingDeletionLayoutListener()
            }
        }
        val layoutListener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if ((bottom - top) > 0 && editor.isLaidOut) {
                    clearPendingDeletionLayoutListener()
                    post {
                        if (canStillSettleGeneration(capturedGen)) {
                            handleDeletionSettlement(smooth = smooth, expectedGen = capturedGen)
                        }
                    }
                }
            }
        }
        pendingDeletionAttachListener = attachListener
        pendingDeletionLayoutListener = layoutListener
        addOnAttachStateChangeListener(attachListener)
        addOnLayoutChangeListener(layoutListener)
    }

    private fun revalidateDeletionSettlementSafety(
        capturedGen: Long,
        allowCorrectiveAnimation: Boolean,
        smooth: Boolean = true
    ): Boolean {
        if (!isDeletionTransactionActive || deletionTransactionGeneration != capturedGen) return false
        if (height <= 0) return false
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val lay = editor.layout ?: return false
        val geom = getSelectionGeometry(cur, lay) ?: return false
        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val viewportBounds = resolveUnobstructedViewportBounds()
        val usableBottomForSafety = viewportBounds.visibleEditorBottom
        val validation = ScribeScrollDeletionPolicy.validateSettledDeletionSafety(
            currentTotalScroll = currentScroll,
            headerHeight = headerHeight,
            caretDocBottom = geom.bottomDocY,
            layoutHeight = lay.layoutHeight.toFloat(),
            usableViewportHeight = height,
            breathingMarginPx = breathingMarginPx,
            maxCanvasScroll = maxCanvasScroll,
            isTailAnchored = isDeletionTailAnchored,
            usableBottomForSafety = usableBottomForSafety
        )
        if (!validation.isSafe && allowCorrectiveAnimation) {
            val correctiveTarget = validation.correctiveTargetScroll
            if (abs(correctiveTarget - currentScroll) >= 1f) {
                if (!smooth) {
                    scrollCanvasBy(correctiveTarget - currentScroll)
                    val afterScroll = (scrollD + editor.offsetY).toFloat()
                    val postViewportBounds = resolveUnobstructedViewportBounds()
                    val postValidation = ScribeScrollDeletionPolicy.validateSettledDeletionSafety(
                        currentTotalScroll = afterScroll,
                        headerHeight = headerHeight,
                        caretDocBottom = geom.bottomDocY,
                        layoutHeight = lay.layoutHeight.toFloat(),
                        usableViewportHeight = height,
                        breathingMarginPx = breathingMarginPx,
                        maxCanvasScroll = maxCanvasScroll,
                        isTailAnchored = isDeletionTailAnchored,
                        usableBottomForSafety = postViewportBounds.visibleEditorBottom
                    )
                    if (!postValidation.isSafe && abs(postValidation.correctiveTargetScroll - afterScroll) >= 1f) {
                        scrollCanvasBy(postValidation.correctiveTargetScroll - afterScroll)
                    }
                    return false
                }
                return startAuthoritativeScrollAnimation(
                    targetTotalScroll = correctiveTarget,
                    reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
                    durationMs = 120L,
                    forceRetarget = true,
                    onEnd = {
                        if (deletionTransactionGeneration == capturedGen &&
                            !canvasAnimator.isGlideRunning &&
                            !scrollCoordinator.isAnimationActive
                        ) {
                            clearDeletionTransaction()
                        }
                    }
                )
            }
        }
        return false
    }

    private fun initializeActiveSeamBaselineIfEligible(curLay: io.github.rosemoe.sora.widget.layout.Layout) {
        val preCapture = pendingPreDeleteSeamCapture ?: return
        val postContent = editor.text
        val cur = try { editor.cursor } catch (_: Throwable) { null } ?: return
        val postStartLine = cur.leftLine.coerceIn(0, (postContent.lineCount - 1).coerceAtLeast(0))
        val rHeight = editor.rowHeight.toFloat()
        val resolved = ScribeGapCollapsePolicy.resolvePostDeleteSeamGeometry(
            preCapture = preCapture,
            postStartLine = postStartLine,
            postLineCount = postContent.lineCount,
            rowHeightPx = rHeight,
            postRowIndexAt = { line, col ->
                try {
                    val charIdx = postContent.indexer.getCharIndex(line, col)
                    curLay.getRowIndexForPosition(charIdx)
                } catch (_: Throwable) {
                    -1
                }
            }
        )
        resolvedSeamGeometry = resolved
        if (resolved.isEligibleCase) {
            activeSeamLine = resolved.seamLine
            activeSeamFirstLowerRow = resolved.seamFirstLowerRow
            activeSeamDocY = resolved.seamFirstLowerRow * rHeight
        } else {
            clearActiveSeamBaseline()
        }
    }

    private fun deferSafetyRevalidationUntilLayoutReady(
        capturedGen: Long,
        capturedSafetyEpoch: Long
    ): SafetyRevalidationResult {
        if (!isAttachedToWindow) {
            finalizeUnresolvedGapCollapseTransaction(
                capturedGen = capturedGen,
                capturedSafetyEpoch = capturedSafetyEpoch,
                reason = "detached_before_safety_retry"
            )
            return SafetyRevalidationResult.UNRESOLVED("detached_before_safety_retry")
        }
        if (!gapCollapseController.markSafetyRetryAttempted(capturedGen, capturedSafetyEpoch)) {
            finalizeUnresolvedGapCollapseTransaction(
                capturedGen = capturedGen,
                capturedSafetyEpoch = capturedSafetyEpoch,
                reason = "safety_layout_retry_exhausted"
            )
            return SafetyRevalidationResult.UNRESOLVED("safety_layout_retry_exhausted")
        }
        clearPendingDeletionLayoutListener()
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                clearPendingDeletionLayoutListener()
            }
        }
        val layoutListener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if ((bottom - top) > 0 && editor.isLaidOut && editor.isWordwrapReady()) {
                    clearPendingDeletionLayoutListener()
                    post {
                        if (gapCollapseController.matchesSafetyEpoch(capturedGen, capturedSafetyEpoch) &&
                            deletionTransactionGeneration == capturedGen
                        ) {
                            completeDeletionVisualTransaction(capturedGen, capturedSafetyEpoch)
                        }
                    }
                }
            }
        }
        pendingDeletionAttachListener = attachListener
        pendingDeletionLayoutListener = layoutListener
        addOnAttachStateChangeListener(attachListener)
        addOnLayoutChangeListener(layoutListener)
        return SafetyRevalidationResult.DEFERRED_UNTIL_LAYOUT_READY("layout_or_bounds_not_ready")
    }

    internal fun completeDeletionVisualTransaction(
        capturedGen: Long,
        capturedSafetyEpoch: Long
    ): SafetyRevalidationResult {
        if (!gapCollapseController.matchesSafetyEpoch(capturedGen, capturedSafetyEpoch) ||
            deletionTransactionGeneration != capturedGen ||
            !isDeletionTransactionActive
        ) {
            return SafetyRevalidationResult.STALE_TRANSACTION
        }
        val lay = editor.layout
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val scribeEditor = editor as? ScribeCodeEditor
        val editorBusy = scribeEditor?.isLayoutBusy ?: editor.isLayoutComputing
        val viewportBounds = resolveUnobstructedViewportBounds()
        val confidence = evaluateVisibilityConfidence(viewportBounds)
        if (height <= 0 ||
            !isLaidOut ||
            !editor.isLaidOut ||
            lay == null ||
            cur == null ||
            !editor.isWordwrapReady() ||
            editorBusy ||
            confidence != VisibilityConfidence.RESOLVED
        ) {
            return deferSafetyRevalidationUntilLayoutReady(capturedGen, capturedSafetyEpoch)
        }

        val caretGeom = getCharGeometry(lay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat())
        val anchorDocBottom = if (isDeletionTailAnchored) {
            maxOf(lay.layoutHeight.toFloat(), caretGeom.bottomDocY)
        } else {
            caretGeom.bottomDocY
        }
        val safetyBounds = resolveSafetyFloorAndBounds(
            anchorDocBottom = anchorDocBottom,
            viewportBounds = viewportBounds
        )
        if (safetyBounds.safeMinPhysical > safetyBounds.safeMax) {
            finalizeUnresolvedGapCollapseTransaction(
                capturedGen = capturedGen,
                capturedSafetyEpoch = capturedSafetyEpoch,
                reason = "contradictory_safety_bounds"
            )
            return SafetyRevalidationResult.UNRESOLVED("contradictory_safety_bounds")
        }
        if (safetyBounds.isPhysicalScrollSafe()) {
            val wasCorrected = gapCollapseController.safetyCorrectionAttempted
            finalizeCompletedGapCollapseTransaction(capturedGen, capturedSafetyEpoch)
            return if (wasCorrected) {
                SafetyRevalidationResult.CORRECTED_AND_VALIDATED
            } else {
                SafetyRevalidationResult.VALIDATED_SAFE
            }
        }

        val currentActual = safetyBounds.actualPhysicalScroll
        val correctiveTarget = safetyBounds.clampPhysicalSafetyTarget(currentActual.toFloat())
        val correctionDelta = correctiveTarget - currentActual.toFloat()
        if (abs(correctionDelta) < 1f) {
            withPhysicalScrollOrigin(PhysicalScrollOrigin.EVENT_B_SAFETY_CORRECTION) {
                scrollCanvasBy(correctionDelta)
            }
            outerScrollResidualPx = 0f
            editorScrollResidualPx = 0f
            scrollDFloat = scrollD.toFloat()
            val afterScroll = (scrollD + editor.offsetY).toFloat()
            scrollCoordinator.syncActualScroll(afterScroll)
            val afterBounds = resolveSafetyFloorAndBounds(
                anchorDocBottom = anchorDocBottom,
                viewportBounds = resolveUnobstructedViewportBounds()
            )
            return if (afterBounds.isPhysicalScrollSafe()) {
                finalizeCompletedGapCollapseTransaction(capturedGen, capturedSafetyEpoch)
                SafetyRevalidationResult.CORRECTED_AND_VALIDATED
            } else {
                finalizeUnresolvedGapCollapseTransaction(
                    capturedGen = capturedGen,
                    capturedSafetyEpoch = capturedSafetyEpoch,
                    reason = "sub1px_correction_still_unsafe"
                )
                SafetyRevalidationResult.UNRESOLVED("sub1px_correction_still_unsafe")
            }
        }

        if (!gapCollapseController.markSafetyCorrectionAttempted(capturedGen, capturedSafetyEpoch)) {
            finalizeUnresolvedGapCollapseTransaction(
                capturedGen = capturedGen,
                capturedSafetyEpoch = capturedSafetyEpoch,
                reason = "safety_correction_did_not_converge"
            )
            return SafetyRevalidationResult.UNRESOLVED("safety_correction_did_not_converge")
        }

        canvasAnimator.cancelPreservingTransaction("event_b_prepare_correction")
        var synchronousOnEndResult: SafetyRevalidationResult? = null
        val started = startAuthoritativeScrollAnimation(
            targetTotalScroll = correctiveTarget,
            reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
            durationMs = 120L,
            forceRetarget = true,
            onEnd = {
                if (gapCollapseController.matchesSafetyEpoch(capturedGen, capturedSafetyEpoch) &&
                    deletionTransactionGeneration == capturedGen
                ) {
                    val nextSafetyEpoch = gapCollapseController.advanceSafetyEpoch(capturedGen)
                    if (nextSafetyEpoch != null) {
                        synchronousOnEndResult = completeDeletionVisualTransaction(capturedGen, nextSafetyEpoch)
                    }
                }
            }
        )
        return if (started) {
            SafetyRevalidationResult.CORRECTION_STARTED
        } else {
            synchronousOnEndResult ?: run {
                val afterBounds = resolveSafetyFloorAndBounds(
                    anchorDocBottom = anchorDocBottom,
                    viewportBounds = resolveUnobstructedViewportBounds()
                )
                if (afterBounds.isPhysicalScrollSafe()) {
                    finalizeCompletedGapCollapseTransaction(capturedGen, capturedSafetyEpoch)
                    SafetyRevalidationResult.CORRECTED_AND_VALIDATED
                } else {
                    finalizeUnresolvedGapCollapseTransaction(
                        capturedGen = capturedGen,
                        capturedSafetyEpoch = capturedSafetyEpoch,
                        reason = "safety_correction_not_started"
                    )
                    SafetyRevalidationResult.UNRESOLVED("safety_correction_not_started")
                }
            }
        }
    }

    private fun stopNonCollapseAnimatorsForPointerFreeze() {
        cancelRestoration()
        if (canvasAnimator.isGlideRunning || scrollCoordinator.isAnimationActive) {
            canvasAnimator.cancelPreservingTransaction("pointer_freeze_residual_animator")
        }
        scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())
        outerScrollResidualPx = 0f
        editorScrollResidualPx = 0f
        scrollDFloat = scrollD.toFloat()
        isPredictiveGlideRunning = false
        targetPredictedScrollTotal = -1f
        endKeyboardTransaction()
        inputController.stopFling()
        editor.stopFling()
    }

    internal fun onPointerTouchDown(preserveRetractionDebt: Boolean = true) {
        when (gapCollapseController.phase) {
            GapCollapsePhase.MODE_1_PHYSICAL_COUPLED,
            GapCollapsePhase.MODE_2_VISUAL_SETTLING -> {
                if (gapCollapseController.freezeForPointer(deletionTransactionGeneration)) {
                    stopNonCollapseAnimatorsForPointerFreeze()
                } else {
                    cancelCanvasAnimation(preserveRetractionDebt = preserveRetractionDebt)
                }
            }
            GapCollapsePhase.POINTER_FROZEN -> {
                stopNonCollapseAnimatorsForPointerFreeze()
            }
            GapCollapsePhase.FINAL_SAFETY_PENDING,
            GapCollapsePhase.IDLE -> {
                cancelCanvasAnimation(preserveRetractionDebt = preserveRetractionDebt)
            }
        }
    }

    private fun stopLegacyAnimators(preserveRetractionDebt: Boolean) {
        cancelRestoration()
        canvasAnimator.cancel("stopLegacyAnimators")
        outerScrollResidualPx = 0f
        editorScrollResidualPx = 0f
        scrollDFloat = scrollD.toFloat()
        isPredictiveGlideRunning = false
        targetPredictedScrollTotal = -1f
        endKeyboardTransaction()
        inputController.stopFling()
        editor.stopFling()
    }

    internal fun handleCursorAnimatorActivatedDuringCollapse() {
        val controller = gapCollapseController
        if (!controller.isTransactionOwned) return
        val capturedGen = controller.owningDeletionGen
        if (capturedGen < 0L ||
            capturedGen != deletionTransactionGeneration ||
            controller.isTeardownLocked
        ) {
            return
        }
        if (controller.phase == GapCollapsePhase.FINAL_SAFETY_PENDING) {
            editor.invalidate()
            return
        }
        canvasAnimator.cancelPreservingTransaction("cursor_animator_active")
        cancelRestoration()
        outerScrollResidualPx = 0f
        editorScrollResidualPx = 0f
        scrollDFloat = scrollD.toFloat()
        scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())
        val nextSafetyEpoch = controller.restartFinalSafetyClaim(capturedGen) ?: return
        editor.invalidate()
        completeDeletionVisualTransaction(capturedGen, nextSafetyEpoch)
    }

    private fun commitGapCollapseIdle() {
        val completedGen = deletionTransactionGeneration
        gapCollapseController.commitIdle()
        clearActiveSeamBaseline()
        resolvedSeamGeometry = null
        (editor as? ScribeCodeEditor)?.onGapCollapseCommitIdle(completedGen)
    }

    private fun finalizeCompletedGapCollapseTransaction(
        capturedGen: Long,
        capturedSafetyEpoch: Long
    ): Boolean {
        val claim = gapCollapseController.beginTeardownLockAndConsumeEval(
            expectedGen = capturedGen,
            expectedSafetyEpoch = capturedSafetyEpoch
        ) ?: return false
        clearPendingDeletionLayoutListener()
        canvasAnimator.cancelPreservingTransaction("event_b_finalized_complete")
        claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)
        commitGapCollapseIdle()
        editor.invalidate()
        claim.pendingEval?.let { dispatchPendingPostSafetyKeyboardEval(it) }
        return true
    }

    private fun finalizeUnresolvedGapCollapseTransaction(
        capturedGen: Long,
        capturedSafetyEpoch: Long,
        reason: String
    ): Boolean {
        val claim = gapCollapseController.beginTeardownLockAndConsumeEval(
            expectedGen = capturedGen,
            expectedSafetyEpoch = capturedSafetyEpoch
        ) ?: return false
        clearPendingDeletionLayoutListener()
        canvasAnimator.cancelPreservingTransaction("event_b_unresolved_$reason")
        claimAndClearLiveDeletionTransaction(preserveRetractionDebt = true)
        commitGapCollapseIdle()
        editor.invalidate()
        claim.pendingEval?.let { dispatchPendingPostSafetyKeyboardEval(it) }
        return true
    }

    fun cancelGapCollapseAndDeletionTransaction(
        preserveRetractionDebt: Boolean = true,
        reason: String = "cancelled"
    ) {
        val claimed = gapCollapseController.tryClaimCancellation(deletionTransactionGeneration)
        if (claimed) {
            canvasAnimator.cancelPreservingTransaction(reason)
            claimAndClearLiveDeletionTransaction(preserveRetractionDebt = preserveRetractionDebt)
            commitGapCollapseIdle()
            stopLegacyAnimators(preserveRetractionDebt = preserveRetractionDebt)
            editor.invalidate()
        } else if (!gapCollapseController.isTransactionOwned) {
            stopLegacyAnimators(preserveRetractionDebt = preserveRetractionDebt)
            claimAndClearLiveDeletionTransaction(preserveRetractionDebt = preserveRetractionDebt)
        }
    }

    internal fun dispatchPendingPostSafetyKeyboardEval(eval: PendingPostSafetyKeyboardEval) {
        if (!isAttachedToWindow ||
            height != eval.targetHeightPx ||
            gapCollapseController.phase != GapCollapsePhase.IDLE ||
            gapCollapseController.isTransactionOwned
        ) {
            return
        }
        if (eval.isHeightShrink && eval.shouldFollowExpansion) {
            ensureCursorVisibleAboveKeyboard(smooth = true, force = false)
        }
    }

    fun prepareGapCollapseFrame1IfReady(fromGen: Long) {
        if (!canStillSettleGeneration(fromGen)) return
        val snapshot = activeDeletionSnapshot ?: return
        val curLay = editor.layout
        val scribeEditor = editor as? ScribeCodeEditor
        val editorBusy = scribeEditor?.isLayoutBusy ?: editor.isLayoutComputing
        if (height <= 0 || !isLaidOut || !editor.isLaidOut || curLay == null) {
            if (!editor.isWordwrapReady()) {
                forceFallbackOnDelayedSettlement = true
            }
            deferDeletionSettlementUntilLayoutReady(fromGen, smooth = true)
            return
        }
        if (!editor.isWordwrapReady() || editorBusy) {
            forceFallbackOnDelayedSettlement = true
            return
        }
        if (!forceFallbackOnDelayedSettlement) {
            initializeActiveSeamBaselineIfEligible(curLay)
        } else {
            resolvedSeamGeometry = null
            clearActiveSeamBaseline()
        }

        val content = editor.text
        val newDocLineCount = content.lineCount
        val newDocHeight = curLay.layoutHeight.toFloat()
        val isTextEmpty = content.isEmpty()
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val rHeight = editor.rowHeight.toFloat()
        val newCaretDocY = if (cur != null) {
            getCharGeometry(curLay, cur.leftLine, cur.leftColumn, rHeight).bottomDocY
        } else {
            0f
        }
        val isPostEditCaretAtLogicalTail = if (cur != null && !isTextEmpty) {
            ScribeScrollDeletionPolicy.isCaretAtLogicalTail(
                lineCount = newDocLineCount,
                caretLine = cur.leftLine,
                caretCol = cur.leftColumn,
                lineAt = { idx -> content.getLine(idx) },
                newCaretDocY = newCaretDocY,
                layoutHeight = newDocHeight,
                rowHeight = rHeight
            )
        } else {
            true
        }
        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = newDocLineCount,
            newDocHeight = headerHeight + newDocHeight,
            usableViewportHeight = height.toFloat(),
            breathingMarginPx = breathingMarginPx,
            isTextEmpty = isTextEmpty,
            isPostEditCaretAtLogicalTail = isPostEditCaretAtLogicalTail
        )

        val preCapture = pendingPreDeleteSeamCapture
        val seam = resolvedSeamGeometry
        if (forceFallbackOnDelayedSettlement ||
            !snapshot.isSelection ||
            intent != ScribeScrollDeletionPolicy.DeletionAnchorIntent.CARET_ANCHOR ||
            preCapture == null ||
            seam == null ||
            !seam.isEligibleCase
        ) {
            handleDeletionSettlement(smooth = true, expectedGen = fromGen)
            return
        }

        val viewportBounds = resolveUnobstructedViewportBounds()
        val visibilityConfidence = evaluateVisibilityConfidence(viewportBounds)
        val preScrollY = preCapture.preScrollY
        val headerRemainingPre = (headerHeight - preScrollY).toFloat()
        val upperRowBottomDocY = seam.seamFirstLowerRow * rHeight
        val upperRowTopDocY = ((seam.seamFirstLowerRow - 1).coerceAtLeast(0)) * rHeight
        val upperTopScreen = headerRemainingPre + upperRowTopDocY
        val upperBottomScreen = headerRemainingPre + upperRowBottomDocY
        val upperRect = ComposeRect(
            left = 0f,
            top = upperTopScreen,
            right = width.coerceAtLeast(1).toFloat(),
            bottom = upperBottomScreen.coerceAtLeast(upperTopScreen + 1f)
        )
        val isUpperVisible = viewportBounds.isValid &&
            upperBottomScreen > viewportBounds.visibleEditorTop &&
            upperTopScreen < viewportBounds.visibleEditorBottom &&
            viewportBounds.isRectUnobstructedByFloatingPills(upperRect)

        val lowerTopScreen = headerRemainingPre + preCapture.preFirstLowerRowTopDocY
        val lowerBottomScreen = lowerTopScreen + rHeight
        val isLowerVisible = if (viewportBounds.isValid && preCapture.preFirstLowerRowTopDocY.isFinite()) {
            val lowerRect = ComposeRect(
                left = 0f,
                top = lowerTopScreen,
                right = width.coerceAtLeast(1).toFloat(),
                bottom = lowerBottomScreen.coerceAtLeast(lowerTopScreen + 1f)
            )
            lowerBottomScreen > viewportBounds.visibleEditorTop &&
                lowerTopScreen < viewportBounds.visibleEditorBottom &&
                viewportBounds.isRectUnobstructedByFloatingPills(lowerRect)
        } else {
            false
        }

        val selTopScreen = headerRemainingPre + preCapture.preSelectionTopDocY
        val selBottomScreen = headerRemainingPre + preCapture.preSelectionBottomDocY
        val isTouchedOnScreen = isUpperVisible || isLowerVisible || (
            viewportBounds.isValid &&
                selBottomScreen > viewportBounds.visibleEditorTop &&
                selTopScreen < viewportBounds.visibleEditorBottom
            )

        val usableMidLocal = (viewportBounds.visibleEditorTop + viewportBounds.visibleEditorBottom) * 0.5f
        val seamDocY = seam.seamFirstLowerRow * rHeight
        val centeredTargetScroll = (headerHeight.toFloat() + seamDocY - usableMidLocal).coerceAtLeast(0f)
        val anchorDocBottom = maxOf(newCaretDocY, seamDocY)
        val safetyBounds = resolveSafetyFloorAndBounds(
            anchorDocBottom = anchorDocBottom,
            viewportBounds = viewportBounds
        )

        val plan = ScribeGapCollapsePolicy.planCollapse(
            deletionIntent = intent,
            isSelection = snapshot.isSelection,
            isWordwrap = editor.isWordwrap,
            isWordwrapReady = editor.isWordwrapReady(),
            isLayoutBusy = editorBusy,
            isComposing = editor.hasComposingText(),
            isCursorAnimationRunning = editor.cursorAnimator.isRunning(),
            hasRtlOrBidiAtSeam = seam.hasRtlOrBidiAtSeam,
            isMergedPartialRowSeam = seam.isMergedPartialRowSeam,
            hasSurvivingUpper = seam.hasSurvivingUpper,
            hasSurvivingLower = seam.hasSurvivingLower,
            deltaGapPx = seam.deltaGapPx,
            visibilityConfidence = visibilityConfidence,
            isUpperVisible = isUpperVisible,
            isLowerVisible = isLowerVisible,
            isTouchedOnScreen = isTouchedOnScreen,
            preScrollY = preScrollY,
            centeredTargetScroll = centeredTargetScroll,
            viewportBounds = viewportBounds,
            safetyBounds = safetyBounds
        )

        if (plan.requiresOffscreenCaretFollow) {
            if (!tryClaimPendingDeletionSettlement(fromGen)) return
            claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)
            if (!gapCollapseController.isTransactionOwned) {
                scribeEditor?.restoreTextActionWindowIfFallbackStranded(fromGen)
            }
            ensureCursorVisibleAboveKeyboard(
                smooth = true,
                force = true,
                allowDuringDeletionRetraction = true
            )
            return
        }

        if (plan.isFallback) {
            if (!gapCollapseController.isTransactionOwned) {
                scribeEditor?.restoreTextActionWindowIfFallbackStranded(fromGen)
            }
            handleDeletionSettlement(smooth = true, expectedGen = fromGen)
            return
        }

        if (!tryClaimPendingDeletionSettlement(fromGen)) return

        if (plan.requiresSynchronousSnap) {
            val currentActual = scrollD + editor.offsetY
            val snapDelta = (plan.synchronousSnapTarget - currentActual).toFloat()
            if (snapDelta != 0f) {
                withPhysicalScrollOrigin(PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1) {
                    scrollCanvasBy(snapDelta)
                }
            }
            outerScrollResidualPx = 0f
            editorScrollResidualPx = 0f
            scrollDFloat = scrollD.toFloat()
            scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())
        }

        val initialActualScroll = (scrollD + editor.offsetY).toFloat()
        val collapseEpoch = gapCollapseController.beginCollapse(
            owningGen = fromGen,
            plan = plan,
            seamLine = activeSeamLine,
            seamFirstLowerRow = activeSeamFirstLowerRow,
            seamDocY = activeSeamDocY,
            rowHeightPx = rHeight,
            initialPhysicalScrollPx = initialActualScroll
        )
        if (gapCollapseController.shouldApplySplitRender) {
            scribeEditor?.onGapCollapseSplitEntered()
        }

        if (plan.requiresMode1) {
            canvasAnimator.cancelPreservingTransaction("begin_mode1_gap_collapse")
            gapCollapseController.markMode1Started(
                expectedGen = fromGen,
                expectedCollapseEpoch = collapseEpoch,
                actualStartPhysicalScrollPx = (scrollD + editor.offsetY).toFloat()
            )
            startAuthoritativeScrollAnimation(
                targetTotalScroll = plan.effectiveTargetScroll,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                forceRetarget = true,
                onEnd = {
                    if (gapCollapseController.matchesCollapseEpoch(fromGen, collapseEpoch) &&
                        gapCollapseController.phase == GapCollapsePhase.MODE_1_PHYSICAL_COUPLED
                    ) {
                        val finalActualScroll = (scrollD + editor.offsetY).toFloat()
                        val nextPhase = gapCollapseController.onMode1SettledOrHandoff(
                            expectedGen = fromGen,
                            expectedCollapseEpoch = collapseEpoch,
                            actualFinalPhysicalScrollPx = finalActualScroll
                        )
                        if (nextPhase == GapCollapsePhase.FINAL_SAFETY_PENDING) {
                            val se = gapCollapseController.safetyEpoch
                            completeDeletionVisualTransaction(fromGen, se)
                        }
                    }
                }
            )
        } else if (gapCollapseController.phase == GapCollapsePhase.FINAL_SAFETY_PENDING) {
            completeDeletionVisualTransaction(fromGen, gapCollapseController.safetyEpoch)
        }
    }

    fun handleDeletionSettlement(
        smooth: Boolean = true,
        expectedGen: Long = deletionTransactionGeneration
    ) {
        val snapshot = activeDeletionSnapshot ?: return
        if (!canStillSettleGeneration(expectedGen)) return
        val currentGen = expectedGen
        if (height <= 0 || !isLaidOut || !editor.isLaidOut) {
            if (!editor.isWordwrapReady()) {
                forceFallbackOnDelayedSettlement = true
            }
            deferDeletionSettlementUntilLayoutReady(currentGen, smooth)
            return
        }
        val curLay = editor.layout
        val scribeEditor = editor as? ScribeCodeEditor
        val editorBusy = scribeEditor?.isLayoutBusy ?: editor.isLayoutComputing
        if (curLay == null || !editor.isWordwrapReady() || editorBusy) {
            forceFallbackOnDelayedSettlement = true
            return
        }
        if (activeSeamLine < 0 && !forceFallbackOnDelayedSettlement) {
            initializeActiveSeamBaselineIfEligible(curLay)
        }
        if (!tryClaimPendingDeletionSettlement(currentGen)) {
            return
        }

        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val newDocHeight = curLay?.layoutHeight?.toFloat() ?: 0f
        val content = editor.text
        val newDocLineCount = content.lineCount
        val isTextEmpty = content.isEmpty()
        val usableHeight = height.toFloat()
        val maxBound = maxCanvasScroll.toFloat()
        val breathingMargin = breathingMarginPx

        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val newCaretDocY = if (cur != null && curLay != null) {
            getCharGeometry(curLay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
        } else {
            0f
        }
        val isPostEditCaretAtLogicalTail = if (cur != null && !isTextEmpty) {
            ScribeScrollDeletionPolicy.isCaretAtLogicalTail(
                lineCount = newDocLineCount,
                caretLine = cur.leftLine,
                caretCol = cur.leftColumn,
                lineAt = { idx -> content.getLine(idx) },
                newCaretDocY = newCaretDocY,
                layoutHeight = newDocHeight,
                rowHeight = editor.rowHeight.toFloat()
            )
        } else {
            true
        }

        val intent = ScribeScrollDeletionPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = newDocLineCount,
            newDocHeight = headerHeight + newDocHeight,
            usableViewportHeight = usableHeight,
            breathingMarginPx = breathingMargin,
            isTextEmpty = isTextEmpty,
            isPostEditCaretAtLogicalTail = isPostEditCaretAtLogicalTail
        )

        val target = ScribeScrollDeletionPolicy.calculateSafeDeletionDestination(
            intent = intent,
            currentScroll = currentScroll,
            headerHeight = headerHeight.toFloat(),
            newDocHeight = newDocHeight,
            newCaretDocY = newCaretDocY,
            preEditCaretScreenY = snapshot.preEditCaretScreenY,
            usableViewportHeight = usableHeight,
            breathingMarginPx = breathingMargin,
            maxCanvasScroll = maxBound
        )

        isDeletionTailAnchored = (intent == ScribeScrollDeletionPolicy.DeletionAnchorIntent.TAIL_ANCHOR)

        if (intent == ScribeScrollDeletionPolicy.DeletionAnchorIntent.RESET_TO_TOP) {
            resetDocumentToTopFromDeletion(smooth = smooth)
        } else if (abs(target - currentScroll) >= 1f) {
            if (!smooth) {
                scrollCanvasBy(target - currentScroll)
                val startedCorrection = revalidateDeletionSettlementSafety(
                    capturedGen = currentGen,
                    allowCorrectiveAnimation = true,
                    smooth = false
                )
                if (!startedCorrection && deletionTransactionGeneration == currentGen) {
                    clearDeletionTransaction()
                }
                return
            }
            startAuthoritativeScrollAnimation(
                targetTotalScroll = target,
                reason = ScrollAnimationReason.DELETION_ANCHOR,
                durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                forceRetarget = true,
                onEnd = {
                    if (deletionTransactionGeneration == currentGen &&
                        !canvasAnimator.isGlideRunning &&
                        !scrollCoordinator.isAnimationActive
                    ) {
                        clearDeletionTransaction()
                    }
                }
            )
        } else {
            val startedCorrection = revalidateDeletionSettlementSafety(
                capturedGen = currentGen,
                allowCorrectiveAnimation = true,
                smooth = smooth
            )
            if (!startedCorrection && deletionTransactionGeneration == currentGen) {
                clearDeletionTransaction()
            }
        }
    }

    companion object {
        val FastOutSlowInInterpolator: android.view.animation.Interpolator =
            com.primaloptima.scribe.ui.editor.scroll.FastOutSlowInInterpolator
    }

    fun startAuthoritativeScrollAnimation(
        targetTotalScroll: Float,
        reason: ScrollAnimationReason,
        durationMs: Long = 280L,
        interpolator: android.view.animation.Interpolator = FastOutSlowInInterpolator,
        forceRetarget: Boolean = false,
        onEnd: (() -> Unit)? = null
    ): Boolean {
        if (!canvasAnimator.isGlideRunning || !scrollCoordinator.isAnimationActive) {
            outerScrollResidualPx = 0f
            editorScrollResidualPx = 0f
            scrollDFloat = scrollD.toFloat()
        }
        val currentScroll = (scrollD + editor.offsetY).toFloat()
        scrollCoordinator.syncActualScroll(currentScroll)
        val maxBound = maxCanvasScroll
        return canvasAnimator.startAuthoritativeAnimation(
            currentScroll = currentScroll,
            targetTotalScroll = targetTotalScroll,
            reason = reason,
            maxCanvasScroll = maxBound,
            forceRetarget = forceRetarget,
            onEnd = onEnd
        )
    }

    fun smoothScrollCanvasBy(
        dy: Float,
        reason: ScrollAnimationReason = ScrollAnimationReason.CARET_VISIBILITY,
        durationMs: Long = 280L,
        interpolator: android.view.animation.Interpolator = FastOutSlowInInterpolator,
        onEnd: (() -> Unit)? = null
    ) {
        if (kotlin.math.abs(dy) < 1f) {
            onEnd?.invoke()
            return
        }
        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val target = currentScroll + dy
        startAuthoritativeScrollAnimation(
            targetTotalScroll = target,
            reason = reason,
            durationMs = durationMs,
            interpolator = interpolator,
            onEnd = onEnd
        )
    }



    internal fun getCharGeometry(
        lay: io.github.rosemoe.sora.widget.layout.Layout,
        line: Int,
        column: Int,
        rowHeight: Float
    ): SoraCharGeometry {
        val raw = try { lay.getCharLayoutOffset(line, column) } catch (_: Throwable) { null }
        // Sora Editor Layout contract:
        // Index 0: character bottom Y in document coordinates (VERTICAL)
        // Index 1: character left X in document coordinates (HORIZONTAL)
        val bottomY = raw?.get(0) ?: ((line + 1) * rowHeight)
        val topY = bottomY - rowHeight
        val leftX = raw?.get(1) ?: 0f
        return SoraCharGeometry(
            bottomDocY = bottomY,
            topDocY = topY,
            leftDocX = leftX
        )
    }

    /**
     * Extracts unified selection/caret bounds in document coordinates,
     * correctly distinguishing vertical (Y: bottom/top) and horizontal (X: left/right),
     * and accurately encompassing multi-line selections.
     */
    internal fun getSelectionGeometry(
        cur: io.github.rosemoe.sora.text.Cursor?,
        lay: io.github.rosemoe.sora.widget.layout.Layout?
    ): SelectionGeometry? {
        if (lay == null) return null
        val rowHeight = editor.rowHeight.toFloat()
        val lineCount = try { editor.text.lineCount } catch (_: Throwable) { 1 }

        if (cur != null && cur.isSelected) {
            val leftLine = cur.leftLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val leftCol = cur.leftColumn.coerceAtLeast(0)
            val rightLine = cur.rightLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
            val rightCol = cur.rightColumn.coerceAtLeast(0)

            val leftGeom = getCharGeometry(lay, leftLine, leftCol, rowHeight)
            val rightGeom = getCharGeometry(lay, rightLine, rightCol, rowHeight)

            return SelectionGeometry(
                topDocY = minOf(leftGeom.topDocY, rightGeom.topDocY),
                bottomDocY = maxOf(leftGeom.bottomDocY, rightGeom.bottomDocY),
                leftDocX = minOf(leftGeom.leftDocX, rightGeom.leftDocX),
                rightDocX = maxOf(leftGeom.leftDocX, rightGeom.leftDocX),
                isSelected = true,
                anchorLine = leftLine,
                focusLine = rightLine
            )
        }

        val line = (cur?.leftLine ?: (lineCount - 1)).coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        val col = (cur?.leftColumn ?: 0).coerceAtLeast(0)
        val geom = getCharGeometry(lay, line, col, rowHeight)

        return SelectionGeometry(
            topDocY = geom.topDocY,
            bottomDocY = geom.bottomDocY,
            leftDocX = geom.leftDocX,
            rightDocX = geom.leftDocX,
            isSelected = false,
            anchorLine = line,
            focusLine = line
        )
    }

    /**
     * Authoritative contraction restoration:
     * Restores downward displacement as a single coordinated operation.
     * Retargets smoothly from the current position if geometry changes,
     * maintaining one authoritative active animation.
     */
    private fun startOrRetargetContractionRestoration(
        targetTotalScroll: Float,
        durationMs: Long = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS
    ) {
        startAuthoritativeScrollAnimation(
            targetTotalScroll = targetTotalScroll,
            reason = ScrollAnimationReason.CONTRACTION_RESTORATION,
            durationMs = durationMs,
            interpolator = FastOutSlowInInterpolator
        )
    }

    /**
     * Final target safety revalidation after contraction restoration completes.
     * Enforces that the settled position respects the breathing margin and rebases
     * any remaining compensation against actual available slack.
     */
    fun revalidateFinalTargetSafety() {
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val lay = editor.layout ?: return
        val geom = getSelectionGeometry(cur, lay) ?: return
        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val density = resources.displayMetrics.density
        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(
            targetTotalScroll = currentScroll,
            caretDocBottom = geom.bottomDocY,
            headerHeight = headerHeight,
            viewportHeight = height,
            density = density
        )
        if (!validation.isSafe && validation.correctiveDeltaY > 0.5f) {
            val maxBound = maxCanvasScroll.toFloat()
            val target = (currentScroll + validation.correctiveDeltaY).coerceIn(0f, maxBound)
            if (target > currentScroll + 0.5f) {
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = target,
                    reason = ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
                    durationMs = 120L
                )
            } else {
                // Content is already physically clamped at max scroll; rebase with settled slack
                rebaseCompensationAfterSafeCorrection()
            }
            // DO NOT rebase compensation prematurely while animation is actively moving.
            // It will be rebased in rebaseCompensationAfterSafeCorrection() once the correction completes.
        } else {
            // Settled position is safe: rebase remaining compensation against actual available slack now.
            val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val targetLineBottom = (height - marginPx).toFloat()
            val finalCaretBottom = (headerHeight + geom.bottomDocY - currentScroll)
            val remainingSlack = targetLineBottom - finalCaretBottom
            scrollCoordinator.rebaseRemainingCompensation(remainingSlack)
        }
    }

    private fun rebaseCompensationAfterSafeCorrection() {
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val lay = editor.layout ?: return
        val geom = getSelectionGeometry(cur, lay) ?: return
        val density = resources.displayMetrics.density
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val targetLineBottom = (height - marginPx).toFloat()
        val finalCaretBottom = (headerHeight + geom.bottomDocY - (scrollD + editor.offsetY))
        val remainingSlack = targetLineBottom - finalCaretBottom
        scrollCoordinator.rebaseRemainingCompensation(remainingSlack)
    }

    fun cancelCanvasAnimation(preserveRetractionDebt: Boolean = false) {
        cancelGapCollapseAndDeletionTransaction(
            preserveRetractionDebt = preserveRetractionDebt,
            reason = "cancelCanvasAnimation"
        )
    }

    /**
     * Enforces that editor scroll and canvas scroll are naturally anchored after text deletion.
     * When document height shrinks (e.g. deleting lines, deleting selections, or reducing indent),
     * checks whether the bottom of the document text has drifted away from the shortcut bar,
     * and smoothly pulls the canvas down so that:
     * 1. The bottom of the document text stays comfortably anchored above the shortcut bar, OR
     * 2. The title and header are restored to the top if the remaining document fits in view.
     */
    fun anchorCanvasAfterDeletion(smooth: Boolean = true) {
        if (isDeletionTransactionActive) {
            val gen = deletionTransactionGeneration
            if (canStillSettleGeneration(gen)) {
                handleDeletionSettlement(smooth = smooth, expectedGen = gen)
            }
            return
        }
        val deltaY = ScribeScrollDeletionPolicy.calculateDeletionAnchorDelta(
            headerHeight = headerHeight,
            scrollD = scrollD,
            layoutHeight = editor.layout?.layoutHeight ?: 0,
            offsetY = editor.offsetY,
            viewportHeight = height,
            density = resources.displayMetrics.density,
            maxCanvasScroll = maxCanvasScroll
        )
        if (deltaY < -1f) {
            if (smooth) {
                smoothScrollCanvasBy(
                    dy = deltaY,
                    reason = ScrollAnimationReason.DELETION_ANCHOR,
                    durationMs = ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                scrollCanvasBy(deltaY)
            }
        }
    }

    /**
     * Clamps excess canvas scroll back to physical content bounds.
     */
    fun clampCanvasScrollToMax(smooth: Boolean = true) {
        anchorCanvasAfterDeletion(smooth = smooth)
    }

    /**
     * Legacy adapter for backward compatibility.
     */
    fun clampEditorScrollToMax() {
        anchorCanvasAfterDeletion(smooth = false)
    }

    /**
     * Smoothly returns the document canvas to the top (scrollD = 0, offsetY = 0),
     * ensuring the header and title section are fully restored into view as one continuous document.
     */
    fun resetDocumentToTop(smooth: Boolean = true) {
        if (smooth && isGlideRunning && activeGlideReason == ScrollAnimationReason.RESET_TOP &&
            scrollCoordinator.isAnimationActive && abs(glideTarget) <= 1f
        ) {
            return
        }
        cancelCanvasAnimation()
        try {
            editor.scroller?.abortAnimation()
        } catch (_: Throwable) {}
        val totalScroll = scrollD + editor.offsetY
        if (totalScroll > 0) {
            if (smooth) {
                smoothScrollCanvasBy(
                    dy = -totalScroll.toFloat(),
                    reason = ScrollAnimationReason.RESET_TOP,
                    durationMs = ScribeScrollPolicy.DURATION_RESET_TOP_MS,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                scrollCanvasBy(-totalScroll.toFloat())
            }
        }
    }

    /**
     * Smoothly returns the document canvas to top specifically when a deletion empties the document
     * or shrinks it within viewport + breathing margin, keeping transitional retraction bounds
     * active across both scroll layers without enabling them for unrelated non-deletion resets.
     */
    fun resetDocumentToTopFromDeletion(smooth: Boolean = true) {
        if (smooth && isGlideRunning && activeGlideReason == ScrollAnimationReason.RESET_TOP &&
            scrollCoordinator.isAnimationActive && abs(glideTarget) <= 1f
        ) {
            return
        }
        cancelCanvasAnimation()
        try {
            editor.scroller?.abortAnimation()
        } catch (_: Throwable) {}
        val totalScroll = scrollD + editor.offsetY
        if (totalScroll > 0) {
            if (smooth) {
                isDeletionResetRetractionActive = true
                val capturedResetGen = ++deletionResetGeneration
                smoothScrollCanvasBy(
                    dy = -totalScroll.toFloat(),
                    reason = ScrollAnimationReason.RESET_TOP,
                    durationMs = ScribeScrollPolicy.DURATION_RESET_TOP_MS,
                    interpolator = FastOutSlowInInterpolator,
                    onEnd = {
                        if (deletionResetGeneration == capturedResetGen &&
                            !canvasAnimator.isGlideRunning &&
                            !scrollCoordinator.isAnimationActive
                        ) {
                            isDeletionResetRetractionActive = false
                        }
                    }
                )
                if (deletionResetGeneration == capturedResetGen &&
                    !canvasAnimator.isGlideRunning &&
                    !scrollCoordinator.isAnimationActive
                ) {
                    isDeletionResetRetractionActive = false
                }
            } else {
                isDeletionResetRetractionActive = true
                try {
                    scrollCanvasBy(-totalScroll.toFloat())
                } finally {
                    clearDeletionResetRetraction()
                }
            }
        }
    }

    /**
     * Directly restores the canvas header scroll offset without animation.
     */
    fun setCanvasScrollD(targetScrollD: Int) {
        cancelCanvasAnimation()
        val clamped = targetScrollD.coerceIn(0, headerHeight)
        outerScrollResidualPx = 0f
        scrollD = clamped
        scrollDFloat = clamped.toFloat()
        applyTranslations()
        onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
        notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
        invalidate()
    }

    /**
     * Executes the given action once the UnifiedCanvasLayout has a valid measured height
     * and headerView has a non-zero measured height.
     */
    fun doOnCanvasReady(action: () -> Unit) {
        if (height > 0 && isLaidOut) {
            if (headerView.measuredHeight > 0 && headerHeight == 0) {
                headerHeight = headerView.measuredHeight
            }
            post { action() }
            return
        }
        var listener: View.OnLayoutChangeListener? = null
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                listener?.let { removeOnLayoutChangeListener(it) }
                removeOnAttachStateChangeListener(this)
            }
        }
        addOnAttachStateChangeListener(attachListener)
        listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if ((bottom - top) > 0) {
                    removeOnLayoutChangeListener(this)
                    removeOnAttachStateChangeListener(attachListener)
                    if (headerView.measuredHeight > 0 && headerHeight == 0) {
                        headerHeight = headerView.measuredHeight
                    }
                    post { action() }
                }
            }
        }
        addOnLayoutChangeListener(listener)
    }

    /**
     * Centers the given cursor line vertically within the viewport/canvas.
     * When smooth is true and the target is within a bounded distance (1.5 screen heights),
     * gently glides to the destination. For distant locations or when smooth is false,
     * immediately restores via settled layout placement.
     */
    fun centerCursorInViewport(line: Int, column: Int, smooth: Boolean = false) {
        doOnCanvasReady {
            editor.doOnLayoutReady {
                val edLayout = editor.layout ?: return@doOnLayoutReady
                val geom = getCharGeometry(edLayout, line, column, editor.rowHeight.toFloat())
                val cursorCanvasY = headerHeight + geom.topDocY
                val viewportHeight = height.toFloat()
                val currentScroll = (scrollDFloat + editor.offsetY)
                val target = ScribeScrollPolicy.calculateCenterCursorTarget(
                    cursorCanvasY = cursorCanvasY,
                    viewportHeight = viewportHeight,
                    currentScroll = currentScroll,
                    headerHeight = headerHeight,
                    maxCanvasScroll = maxCanvasScroll
                )

                if (smooth && target.isSmoothEligible) {
                    startAuthoritativeScrollAnimation(
                        targetTotalScroll = target.targetTotalScroll,
                        reason = ScrollAnimationReason.PROGRAMMATIC,
                        durationMs = 320L
                    )
                } else {
                    restoreCanvasAndEditorScroll(target.targetScrollD, target.targetScrollY)
                }
            }
        }
    }

    /**
     * Synchronously restores both canvas header displacement (scrollD) and Sora editor vertical offset (offsetY).
     * Waits for both canvas geometry and editor layout to be fully settled.
     */
    fun restoreCanvasAndEditorScroll(
        targetScrollD: Int,
        targetScrollY: Int,
        isValid: (() -> Boolean)? = null,
        onComplete: (() -> Unit)? = null,
        onResult: ((com.primaloptima.scribe.navigation.RestorationResult) -> Unit)? = null
    ) {
        isRestoringViewport = true
        suppressCursorVisibilityScroll = true
        doOnCanvasReady {
            if (isValid != null && !isValid()) {
                isRestoringViewport = false
                suppressCursorVisibilityScroll = false
                onResult?.invoke(com.primaloptima.scribe.navigation.RestorationResult.CANCELLED)
                return@doOnCanvasReady
            }
            editor.doOnLayoutReady {
                if (isValid != null && !isValid()) {
                    isRestoringViewport = false
                    suppressCursorVisibilityScroll = false
                    onResult?.invoke(com.primaloptima.scribe.navigation.RestorationResult.CANCELLED)
                    return@doOnLayoutReady
                }
                cancelCanvasAnimation()
                val totalScroll = (targetScrollD + targetScrollY).coerceAtLeast(0)
                val reconciled = ScribeScrollPolicy.reconcileScrollState(
                    headerHeight = headerHeight,
                    scrollD = 0,
                    editorOffsetY = totalScroll,
                    maxCanvasScroll = maxCanvasScroll
                )
                outerScrollResidualPx = 0f
                editorScrollResidualPx = 0f
                scrollD = reconciled.scrollD
                scrollDFloat = reconciled.scrollD.toFloat()
                applyTranslations()
                try {
                    editor.jumpScrollTo(editor.offsetX, reconciled.editorOffsetY)
                } catch (_: Throwable) {}
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
                invalidate()
                isRestoringViewport = false
                post {
                    suppressCursorVisibilityScroll = false
                }
                onComplete?.invoke()
                onResult?.invoke(com.primaloptima.scribe.navigation.RestorationResult.APPLIED)
            }
        }
    }

    /**
     * Captures the live viewport snapshot directly from current canvas and editor measurements.
     * Does NOT advance or interfere with in-progress scroller animations.
     */
    fun captureViewportSnapshot(noteId: String, sessionKey: com.primaloptima.scribe.navigation.Route.Editor): com.primaloptima.scribe.navigation.ViewportSnapshot {
        return com.primaloptima.scribe.navigation.ViewportSnapshot(
            sessionKey = sessionKey,
            noteId = noteId,
            scrollD = scrollD,
            editorOffsetY = editor.offsetY
        )
    }

    fun settleOrCancelFling() {
        inputController.settleOrCancelFling()
    }

    fun resetScroll() {
        clearDeletionTransaction()
        clearDeletionResetRetraction()
        inputController.stopFling()
        editor.stopFling()
        outerScrollResidualPx = 0f
        editorScrollResidualPx = 0f
        scrollDFloat = 0f
        scrollD = 0
        inputController.isDraggingCanvas = false
        applyTranslations()
        onUnifiedScrollChanged?.invoke(0, headerHeight)
        try {
            editor.jumpScrollTo(editor.offsetX, 0)
        } catch (_: Throwable) {}
        notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
    }

    /**
     * Continues a downward fling from the editor into the canvas header.
     * Called by ScribeCodeEditor when flinging towards the top and offsetY hits 0.
     */
    fun continueFlingFromEditor(velocity: Float) {
        inputController.continueFlingFromEditor(velocity)
    }

    /**
     * When user types or focuses the editor, ensures the cursor is never occluded
     * by the keyboard or shortcut bar by scrolling header off or adjusting editor scroll
     * so that the active cursor line stays just above the shortcut bar.
     */
    /**
     * Phase 1: Tap-Triggered Predictive Pre-Calculation Engine.
     * Computes the cursor position at t = 0ms (on tap), estimates future viewport
     * height using cached lastKeyboardHeightDp + shortcutBarHeight, and immediately starts
     * a smooth parallel glide running concurrently with the keyboard entrance animation.
     */
    fun predictivelyGlideCursorAboveKeyboard(targetLine: Int? = null, targetCol: Int? = null) {
        if (isKeyboardDismissing) return
        if (activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
            activeEditingTarget == EditingTarget.SECONDARY_TITLE
        ) {
            return
        }
        val isKeyboardOrDrawerActive = isKeyboardVisible || isBottomSurfaceActive || isKeyboardTransactionActive
        if (isKeyboardOrDrawerActive && !isKeyboardTransactionActive) {
            ensureCursorVisibleAboveKeyboard(targetLine = targetLine, targetCol = targetCol, smooth = true)
            return
        }
        val density = resources.displayMetrics.density
        val cursor = try { editor.cursor } catch (_: Throwable) { null }
        val line = targetLine ?: cursor?.leftLine ?: return
        val col = targetCol ?: cursor?.leftColumn ?: 0

        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val geom = getCharGeometry(layout, line, col, editor.rowHeight.toFloat())
        val yOffset = geom.bottomDocY

        val predictedImeObstructionPx = if (targetImeObstructionPx > 0.5f) {
            targetImeObstructionPx
        } else if (standardKeyboardBaselinePx > 0.5f) {
            standardKeyboardBaselinePx
        } else {
            280f * density
        }
        val shortcutBarPx = if (hasShortcuts) shortcutBarHeightPx else 0f
        val predictedDockClearancePx = predictedImeObstructionPx + shortcutBarPx

        val futureViewportHeight = if (isKeyboardOrDrawerActive && height > 0) {
            height.toFloat()
        } else {
            (height - predictedDockClearancePx).coerceAtLeast(editor.rowHeight * 3f)
        }

        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()

        // Authoritative future max canvas scroll matching post-resize onSizeChanged exactly
        val maxScrollFuture = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layout.layoutHeight,
            viewportHeight = futureViewportHeight.toInt(),
            bottomSpace = marginPx,
            isTextEmpty = editor.text.isEmpty()
        ).toFloat()

        val currentScroll = (scrollD + editor.offsetY).toFloat()

        // Phase 1b: Check if the user initiated this via a recent tap on the canvas.
        // If the tap location is already above the breathing margin / shortcut bar in the future viewport,
        // or if the canvas is at the top (scrollD == 0) and the tap is within the future viewport,
        // suppress any predictive glide and prevent false scroll-and-return bouncing.
        val now = System.currentTimeMillis()
        val recentTouchY = if (now - lastTouchDownTime < ScribeScrollPolicy.RECENT_TOUCH_SUPPRESSION_WINDOW_MS) {
            if (targetLine == null) lastTouchDownY else null
        } else {
            null
        }

        val decision = ScribeScrollPolicy.calculatePredictiveGlideDecision(
            currentScroll = currentScroll,
            yOffset = yOffset,
            headerHeight = headerHeight,
            futureViewportHeight = futureViewportHeight,
            recentTouchY = recentTouchY,
            scrollD = scrollD,
            density = density,
            maxScrollFuture = maxScrollFuture
        )

        if (decision.isTapSuppressed) {
            beginKeyboardTransaction(decision.desiredTarget)
            targetPredictedScrollTotal = decision.desiredTarget
            onKeyboardEntranceStarted?.invoke()
            return
        }

        beginKeyboardTransaction(decision.desiredTarget)
        onKeyboardEntranceStarted?.invoke()

        if (decision.shouldGlide) {
            isExpansionTransitionActive = true
            resetExpansionTransitionRunnable?.let { removeCallbacks(it) }
            val rReset = Runnable {
                isExpansionTransitionActive = false
                checkKeyboardTransactionSettled()
            }
            resetExpansionTransitionRunnable = rReset
            postDelayed(rReset, 600L)

            isPredictiveGlideRunning = true
            targetPredictedScrollTotal = decision.desiredTarget
            smoothScrollCanvasBy(
                dy = decision.deltaY,
                reason = ScrollAnimationReason.PREDICTIVE_GLIDE,
                durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS,
                interpolator = FastOutSlowInInterpolator
            ) {
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
                checkKeyboardTransactionSettled()
            }
        } else {
            targetPredictedScrollTotal = currentScroll
        }
    }

    private var lastEnsureCursorTime = 0L

    fun ensureCursorVisibleAboveKeyboard(
        targetLine: Int? = null,
        targetCol: Int? = null,
        smooth: Boolean = true,
        customDurationMs: Long = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS,
        force: Boolean = false,
        allowDuringDeletionRetraction: Boolean = false
    ) {
        if (activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
            activeEditingTarget == EditingTarget.SECONDARY_TITLE
        ) {
            return
        }
        if ((!editor.isFocused && !isBottomSurfaceActive) || isKeyboardDismissing || suppressCursorVisibilityScroll || isRestoringViewport || (!allowDuringDeletionRetraction && (hasPendingDeletionSettlement || isDeletionRetractionInProgress))) return
        if (!force && editor.isPasteTransactionActive) return
        val now = System.currentTimeMillis()
        if (!force && now - lastEnsureCursorTime < 16L) return

        // If a keyboard transaction or predictive glide is currently in flight:
        if (isKeyboardTransactionActive || (isPredictiveGlideRunning && targetPredictedScrollTotal > 0f)) {
            val density = resources.displayMetrics.density
            val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
            val predictedIme = if (targetImeObstructionPx > 0.5f) targetImeObstructionPx else standardKeyboardBaselinePx
            val shortcutBarPx = if (hasShortcuts) shortcutBarHeightPx else 0f
            val targetViewportH = if (isKeyboardVisible || isBottomSurfaceActive) height.toFloat() else (height - (predictedIme + shortcutBarPx)).coerceAtLeast(editor.rowHeight * 3f)
            val targetLineBottom = (targetViewportH - marginPx).toFloat()
            val cur = try { editor.cursor } catch (_: Throwable) { null }
            val lay = try { editor.layout } catch (_: Throwable) { null }
            if (cur != null && lay != null) {
                val selGeom = getSelectionGeometry(cur, lay)
                val bottomY = selGeom?.bottomDocY ?: getCharGeometry(lay, targetLine ?: cur.leftLine, targetCol ?: cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
                val plannedTarget = if (targetPredictedScrollTotal > 0f) {
                    targetPredictedScrollTotal
                } else if (activeKeyboardTransaction?.targetScroll ?: -1f >= 0f) {
                    activeKeyboardTransaction!!.targetScroll
                } else {
                    (scrollD + editor.offsetY).toFloat()
                }
                val finalScreenBottom = (headerHeight + bottomY - plannedTarget)
                if (finalScreenBottom <= targetLineBottom + deadbandPx) {
                    return
                }
            }
        }

        val cursor = try { editor.cursor } catch (_: Throwable) { null } ?: return

        val resolutionTarget = ScribeScrollPolicy.resolveTargetCoordinates(
            isSelected = cursor.isSelected,
            leftLine = cursor.leftLine,
            leftCol = cursor.leftColumn,
            rightLine = cursor.rightLine,
            rightCol = cursor.rightColumn,
            lineCount = editor.text.lineCount,
            lastLineColCount = try { editor.text.getColumnCount(editor.text.lineCount - 1) } catch (_: Throwable) { 0 },
            mode = editor.selectAllScrollMode,
            explicitLine = targetLine,
            explicitCol = targetCol
        ) ?: return // null means stationary mode: suppress scroll completely!

        val line = resolutionTarget.line
        val col = resolutionTarget.column

        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val rowHeight = editor.rowHeight
        val layoutOffset = try { layout.getCharLayoutOffset(line, col) } catch (_: Throwable) { null }
        val yOffset = if (cursor.isSelected && targetLine == null) {
            getSelectionGeometry(cursor, layout)?.bottomDocY ?: (layoutOffset?.let { getCharGeometry(layout, line, col, rowHeight.toFloat()).bottomDocY } ?: ((line + 1) * rowHeight.toFloat()))
        } else if (layoutOffset != null) {
            getCharGeometry(layout, line, col, rowHeight.toFloat()).bottomDocY
        } else {
            // Find closest preceding line with a valid layout offset to accurately project coordinates
            var anchorY = -1f
            var lineGap = 1
            for (k in (line - 1) downTo 0) {
                val colK = try { editor.text.getColumnCount(k) } catch (_: Throwable) { 0 }
                val offK = try { layout.getCharLayoutOffset(k, colK) } catch (_: Throwable) { null }
                if (offK != null) {
                    anchorY = getCharGeometry(layout, k, colK, rowHeight.toFloat()).bottomDocY
                    lineGap = line - k
                    break
                }
            }
            if (anchorY >= 0f) {
                anchorY + (lineGap * rowHeight)
            } else {
                (line + 1) * rowHeight.toFloat()
            }
        }
        val headerRemaining = headerHeight - scrollD
        val screenBottom = headerRemaining + yOffset - editor.offsetY
        val screenTop = screenBottom - rowHeight

        val visibleHeight = height
        val density = resources.displayMetrics.density
        val isLastLine = line >= (editor.text.lineCount - 1)
        val hasScroll = scrollD > 0 || editor.offsetY > 0

        val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
            screenBottom = screenBottom,
            screenTop = screenTop,
            visibleHeight = visibleHeight,
            density = density,
            isLastLine = isLastLine,
            isDeletionActive = isLastLineDeletionActive,
            hasScrollOffset = hasScroll
        )

        if (resolution.shouldScroll) {
            lastEnsureCursorTime = now
            val duration = if (resolution.isAnchorDownward) {
                ScribeScrollDeletionPolicy.DURATION_DELETION_ANCHOR_MS
            } else {
                customDurationMs
            }
            if (resolution.deltaY > 0f && isExpansionTransitionActive) {
                recordExpansionDisplacement(resolution.deltaY, line, col, cursor.isSelected)
            }
            // Authoritative positioning supersedes predictive glide state
            isPredictiveGlideRunning = false
            targetPredictedScrollTotal = -1f
            if (isKeyboardTransactionActive || (isGlideRunning && scrollCoordinator.isAnimationActive)) {
                val currentScroll = (scrollD + editor.offsetY).toFloat()
                val desiredTarget = (currentScroll + resolution.deltaY).coerceIn(0f, maxCanvasScroll.toFloat())
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = desiredTarget,
                    reason = if (isKeyboardTransactionActive) ScrollAnimationReason.PREDICTIVE_GLIDE else ScrollAnimationReason.CARET_VISIBILITY,
                    forceRetarget = true
                )
            } else if (smooth) {
                smoothScrollCanvasBy(
                    dy = resolution.deltaY,
                    reason = ScrollAnimationReason.CARET_VISIBILITY,
                    durationMs = duration,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                val beforeScroll = (scrollD + editor.offsetY).toFloat()
                scrollCanvasBy(resolution.deltaY)
                val afterScroll = (scrollD + editor.offsetY).toFloat()
                val actualMoved = afterScroll - beforeScroll
                if (isExpansionTransitionActive && actualMoved > 0f) {
                    temporaryExpansionDisplacement += actualMoved
                }
            }
        }
    }

    internal fun applyTranslations() {
        // Enforce the 1-DOF invariant:
        // If the header is partially or fully visible (scrollD < headerHeight),
        // the editor MUST NOT have any positive offsetY. Clamping to 0 guarantees
        // Line 0 is never drawn behind the titles.
        if (scrollD < headerHeight && editor.offsetY > 0) {
            editorScrollResidualPx = 0f
            try {
                editor.jumpScrollTo(editor.offsetX, 0)
            } catch (_: Throwable) {}
        }
        val d = scrollD.toFloat()
        headerView.translationY = -d
        editor.translationY = -d
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val viewportHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Measure header with EXACTLY width and UNSPECIFIED height
        headerView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val measuredH = headerView.measuredHeight
        if (measuredH > 0) {
            headerHeight = measuredH
        }

        // Maintain continuous canvas invariant on resume/relayout, deferring destructive clamping
        // during an active deletion transaction, touch retraction debt, or smooth RESET_TOP glide.
        val deferLayoutReconcile = isDeletionRetractionInProgress ||
            (isGlideRunning && activeGlideReason == ScrollAnimationReason.RESET_TOP)
        if (!deferLayoutReconcile && headerHeight > 0 && editor.isWordwrapReady() && editor.layout != null) {
            val reconciled = ScribeScrollPolicy.reconcileScrollState(
                headerHeight = headerHeight,
                scrollD = scrollD,
                editorOffsetY = editor.offsetY,
                maxCanvasScroll = maxCanvasScroll
            )
            if (reconciled.scrollD != scrollD) {
                scrollD = reconciled.scrollD
                scrollDFloat = reconciled.scrollD.toFloat()
                outerScrollResidualPx = 0f
            }
        }

        // Measure CodeEditor to fill the full viewport width and height
        editor.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(width, viewportHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val viewportHeight = b - t

        headerView.layout(0, 0, width, headerHeight)
        editor.layout(0, headerHeight, width, headerHeight + viewportHeight)

        // Only enforce viewport height invariant when layout & wordwrap are fully ready and
        // no active deletion transaction, touch retraction debt, or smooth RESET_TOP glide owns the retraction.
        val deferLayoutReconcile = isDeletionRetractionInProgress ||
            (isGlideRunning && activeGlideReason == ScrollAnimationReason.RESET_TOP)
        if (!deferLayoutReconcile) {
            if (editor.text.isEmpty()) {
                if (editor.offsetY > 0) {
                    try {
                        editor.jumpScrollTo(editor.offsetX, 0)
                        editorScrollResidualPx = 0f
                    } catch (_: Throwable) {}
                }
                if (scrollD > 0) {
                    scrollD = 0
                    scrollDFloat = 0f
                    outerScrollResidualPx = 0f
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
                }
            } else if (editor.isWordwrapReady() && editor.layout != null) {
                val reconciled = ScribeScrollPolicy.reconcileScrollState(
                    headerHeight = headerHeight,
                    scrollD = scrollD,
                    editorOffsetY = editor.offsetY,
                    maxCanvasScroll = maxCanvasScroll
                )
                if (reconciled.scrollD != scrollD) {
                    scrollD = reconciled.scrollD
                    scrollDFloat = reconciled.scrollD.toFloat()
                    outerScrollResidualPx = 0f
                    onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                    notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
                }
                if (reconciled.editorOffsetY != editor.offsetY) {
                    try {
                        editor.jumpScrollTo(editor.offsetX, reconciled.editorOffsetY)
                        editorScrollResidualPx = 0f
                        notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
                    } catch (_: Throwable) {}
                }
            }
        }
        applyTranslations()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topEdgeEffect?.setSize(w, h)
        if (oldh > 0 && kotlin.math.abs(h - oldh) > 2) {
            val dh = oldh - h
            if (dh > 0) {
                val isTitleTargetActive = activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
                    activeEditingTarget == EditingTarget.SECONDARY_TITLE
                val isAuthorizedTransition = isKeyboardTransactionActive || isBottomSurfaceActive || isKeyboardVisible
                if (isAuthorizedTransition) {
                    // Viewport shrinking during authorized keyboard entrance, accessory drawer opening, or IME expansion
                    isExpansionTransitionActive = true
                    isKeyboardDismissing = false // Opening/expanding: clear any stale dismissal flag
                    resetExpansionTransitionRunnable?.let { removeCallbacks(it) }
                    val rReset = Runnable {
                        isExpansionTransitionActive = false
                        checkKeyboardTransactionSettled()
                    }
                    resetExpansionTransitionRunnable = rReset
                    postDelayed(rReset, 600L)
                    val curC = try { editor.cursor } catch (_: Throwable) { null }
                    if (curC != null) {
                        expansionCaretLine = curC.leftLine
                        expansionCaretCol = curC.leftColumn
                        expansionHadSelection = curC.isSelected
                    }
                }
                if (!isTitleTargetActive && isAuthorizedTransition) {
                    val density = resources.displayMetrics.density
                    val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
                    val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()

                    val cur = try { editor.cursor } catch (_: Throwable) { null }
                    val lay = try { editor.layout } catch (_: Throwable) { null }
                    if (cur != null && lay != null) {
                        val isAllSelected = ScribeScrollPolicy.isSelectAll(
                            isSelected = cur.isSelected,
                            leftLine = cur.leftLine,
                            leftCol = cur.leftColumn,
                            rightLine = cur.rightLine,
                            rightCol = cur.rightColumn,
                            lineCount = editor.text.lineCount,
                            lastLineColCount = try { editor.text.getColumnCount(editor.text.lineCount - 1) } catch (_: Throwable) { 0 }
                        )
                        if (!(isAllSelected && editor.selectAllScrollMode == ScribeScrollPolicy.SelectAllScrollMode.KEEP_CURRENT_POSITION)) {
                            val selGeom = getSelectionGeometry(cur, lay)
                            val caretDocBottom = selGeom?.bottomDocY ?: run {
                                val targetLine = if (isAllSelected) cur.rightLine else cur.leftLine
                                val targetCol = if (isAllSelected) cur.rightColumn else cur.leftColumn
                                getCharGeometry(lay, targetLine, targetCol, editor.rowHeight.toFloat()).bottomDocY
                            }
                            val headerRemaining = headerHeight - scrollD
                            val screenBottom = headerRemaining + caretDocBottom - editor.offsetY
                            val targetLineBottom = (h - marginPx).toFloat()

                            val isCurrentlySafe = screenBottom <= (targetLineBottom + deadbandPx)
                            val desiredTarget = if (isKeyboardTransactionActive) {
                                activeKeyboardTransaction?.calculateRetargetedDestination(
                                    caretDocBottom = caretDocBottom,
                                    headerHeight = headerHeight.toFloat(),
                                    usableViewportHeight = h.toFloat(),
                                    breathingMarginPx = marginPx.toFloat(),
                                    maxCanvasScroll = maxCanvasScroll.toFloat()
                                ) ?: ((headerHeight + caretDocBottom - targetLineBottom).coerceAtLeast(0f)
                                    .coerceIn(0f, maxCanvasScroll.toFloat()))
                            } else {
                                ((headerHeight + caretDocBottom - targetLineBottom).coerceAtLeast(0f)
                                    .coerceIn(0f, maxCanvasScroll.toFloat()))
                            }

                            if (isGlideRunning && scrollCoordinator.isAnimationActive) {
                                val shouldRetarget = if (isKeyboardTransactionActive) {
                                    activeKeyboardTransaction?.shouldRetargetEntranceGlide(
                                        glideTarget = glideTarget,
                                        desiredTarget = desiredTarget,
                                        deadbandPx = deadbandPx.toFloat()
                                    ) ?: (desiredTarget > glideTarget + deadbandPx)
                                } else {
                                    kotlin.math.abs(glideTarget - desiredTarget) > deadbandPx
                                }
                                if (shouldRetarget) {
                                    startAuthoritativeScrollAnimation(
                                        targetTotalScroll = desiredTarget,
                                        reason = ScrollAnimationReason.PREDICTIVE_GLIDE,
                                        forceRetarget = true,
                                        onEnd = {
                                            checkKeyboardTransactionSettled()
                                        }
                                    )
                                } else {
                                    checkKeyboardTransactionSettled()
                                }
                            } else if (!isCurrentlySafe) {
                                val currentScroll = (scrollD + editor.offsetY).toFloat()
                                val scrollDelta = kotlin.math.abs(desiredTarget - currentScroll)
                                if (scrollDelta > deadbandPx) {
                                    startAuthoritativeScrollAnimation(
                                        targetTotalScroll = desiredTarget,
                                        reason = ScrollAnimationReason.PREDICTIVE_GLIDE,
                                        durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS,
                                        onEnd = {
                                            checkKeyboardTransactionSettled()
                                        }
                                    )
                                } else {
                                    checkKeyboardTransactionSettled()
                                }
                            } else {
                                // Caret is already safe! Suppress any secondary jump.
                                checkKeyboardTransactionSettled()
                            }
                        }
                    }
                }
            } else if (dh < 0) {
                // Viewport expanding (accessory drawer contracting, emoji keyboard closing, or bottom surface lowering)
                isExpansionTransitionActive = false
                resetExpansionTransitionRunnable?.let { removeCallbacks(it) }

                val isDismissing = isKeyboardDismissing || dockTransition == ScribeDockTransition.IME_DISMISSAL
                if (isDismissing) {
                    cancelRestoration()
                    invalidateCompensation("Full keyboard dismissal: discard compensation without downward restoration")
                    return
                }

                val density = resources.displayMetrics.density
                val rootH = rootView?.height ?: resources.displayMetrics.heightPixels
                val hasBottomObstruction = (rootH - h) >= (140 * density).roundToInt()
                val cur = try { editor.cursor } catch (_: Throwable) { null }
                val lineCount = try { editor.text.lineCount } catch (_: Throwable) { 1 }
                val isAtLastLine = try {
                    (cur != null && cur.leftLine >= (lineCount - 1)) || (lineCount <= 1)
                } catch (_: Throwable) { false }
                val hasActiveScroll = scrollD > 0 || editor.offsetY > 0
                val shouldFollow = ScribeBottomSurfaceCoordinator.shouldFollowDownward(
                    isKeyboardDismissing = isKeyboardDismissing,
                    isBottomSurfaceActive = isBottomSurfaceActive,
                    isKeyboardVisible = isKeyboardVisible,
                    hasPhysicalBottomObstruction = hasBottomObstruction,
                    isAtLastLine = isAtLastLine,
                    hasActiveScroll = hasActiveScroll,
                    temporaryDisplacement = temporaryExpansionDisplacement,
                    activeTransition = dockTransition
                )

                // Pure Writer Invariant:
                // When keyboard is dismissed or closing, the document MUST remain
                // completely stationary at the saved margin (keyboard height + shortcut bar + breathing margin).
                // Never pull down to the bottom of the screen.
                val isDestinationSurfaceActive = (isBottomSurfaceActive || isKeyboardVisible || hasBottomObstruction) &&
                    !isKeyboardDismissing && dockTransition != ScribeDockTransition.IME_DISMISSAL

                if (!shouldFollow) {
                    if (isKeyboardDismissing || !isDestinationSurfaceActive || dockTransition == ScribeDockTransition.IME_DISMISSAL) {
                        cancelRestoration()
                        invalidateCompensation("Keyboard dismissing or bottom surface inactive")
                    }
                    return
                }

                // Downward Following Invariant:
                // When bottom surface remains active (e.g. accessory drawer collapsed to keyboard,
                // or taller emoji keyboard returned to standard keyboard), safely restore temporary
                // displacement across ALL caret positions, while ensuring the caret settles above
                // the shortcut bar maintaining the exact 24dp breathing margin.
                pendingRestorationRunnable?.let { removeCallbacks(it) }
                val capturedGen = scrollCoordinator.interactionGeneration
                val rRestoration = Runnable {
                    if (scrollCoordinator.interactionGeneration != capturedGen) {
                        return@Runnable
                    }
                    pendingRestorationRunnable = null
                    val lay = editor.layout ?: return@Runnable
                    val geom = getSelectionGeometry(cur, lay) ?: return@Runnable
                    val caretDocBottom = geom.bottomDocY
                    val caretDocTop = geom.topDocY

                    val headerRemaining = (headerHeight - scrollD).toFloat()
                    val caretScreenBottom = headerRemaining + caretDocBottom - editor.offsetY
                    val caretScreenTop = headerRemaining + caretDocTop - editor.offsetY
                    val totalScroll = (scrollD + editor.offsetY).toFloat()

                    val result = ScribeScrollRestorationPolicy.resolveContractionRestorationDelta(
                        ScribeScrollRestorationPolicy.ContractionRestorationRequest(
                            caretScreenBottom = caretScreenBottom,
                            caretScreenTop = caretScreenTop,
                            viewportHeight = height,
                            density = density,
                            totalScroll = totalScroll,
                            temporaryDisplacement = temporaryExpansionDisplacement,
                            isAtLastLine = isAtLastLine,
                            isKeyboardDismissing = isKeyboardDismissing,
                            isBottomSurfaceActive = isDestinationSurfaceActive
                        )
                    )

                    if (result.shouldScroll) {
                        val candidateTarget = (totalScroll + result.deltaY).coerceAtLeast(0f)
                        val validation = ScribeScrollRestorationPolicy.validateFinalTarget(
                            targetTotalScroll = candidateTarget,
                            caretDocBottom = caretDocBottom,
                            headerHeight = headerHeight,
                            viewportHeight = height,
                            density = density
                        )
                        val safeTarget = if (!validation.isSafe) candidateTarget + validation.correctiveDeltaY else candidateTarget
                        startOrRetargetContractionRestoration(safeTarget)
                    } else if (isRestorationActive) {
                        cancelRestoration()
                    }
                }
                pendingRestorationRunnable = rRestoration
                post(rRestoration)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
            if (topEdgeEffect.draw(canvas)) {
                postInvalidateOnAnimation()
            }
        }
        canvas.restoreToCount(save)
    }

    /**
     * Intercepts child requests to keep rectangles (like cursor) visible.
     * Offsets the requested rectangle by the editor's visual position, scrolling scrollD
     * so that the typing cursor is strictly kept above the keyboard and shortcut bar.
     */
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        if (isKeyboardDismissing || suppressCursorVisibilityScroll || isRestoringViewport || inputController.isDraggingCanvas || dockTransition == ScribeDockTransition.IME_DISMISSAL) {
            return true
        }
        if (child !== editor) {
            if (scrollD > 0) {
                if (immediate) {
                    outerScrollResidualPx = 0f
                    scrollDFloat = 0f
                    scrollD = 0
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(0, headerHeight)
                    notifyViewportScrollChanged(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC)
                } else {
                    inputController.abortAnimation()
                    inputController.startScroll(0, scrollD, 0, -scrollD, 250)
                }
            }
            return true
        }

        if (child === editor) {
            if (activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
                activeEditingTarget == EditingTarget.SECONDARY_TITLE
            ) {
                return false
            }
            if (!isKeyboardVisible && !isBottomSurfaceActive && !isKeyboardTransactionActive) {
                // When keyboard is hidden and no drawer is active, do not allow stale cursor requests to re-anchor scroll
                return true
            }
            val screenTop = (headerHeight - scrollD) + rectangle.top
            val screenBottom = (headerHeight - scrollD) + rectangle.bottom
            val density = resources.displayMetrics.density
            val hasScrollOffset = (scrollD > 0 || editor.offsetY > 0) && (isKeyboardVisible || isBottomSurfaceActive)

            val resolution = ScribeScrollPolicy.resolveCursorScrollDelta(
                screenBottom = screenBottom.toFloat(),
                screenTop = screenTop.toFloat(),
                visibleHeight = height,
                density = density,
                hasScrollOffset = hasScrollOffset
            )

            if (resolution.shouldScroll) {
                if (resolution.deltaY > 0f) {
                    // Bottom overflow
                    if (isDeletionTransactionActive) {
                        return true
                    }
                    val currentScroll = (scrollD + editor.offsetY).toFloat()
                    val desiredTarget = (currentScroll + resolution.deltaY).coerceIn(0f, maxCanvasScroll.toFloat())

                    if (isKeyboardTransactionActive || (isGlideRunning && scrollCoordinator.isAnimationActive)) {
                        startAuthoritativeScrollAnimation(
                            targetTotalScroll = desiredTarget,
                            reason = if (isKeyboardTransactionActive) ScrollAnimationReason.PREDICTIVE_GLIDE else ScrollAnimationReason.CARET_VISIBILITY,
                            forceRetarget = true
                        )
                    } else if (!immediate) {
                        smoothScrollCanvasBy(
                            dy = resolution.deltaY,
                            reason = ScrollAnimationReason.CARET_VISIBILITY,
                            durationMs = resolution.recommendedDurationMs
                        )
                    } else {
                        scrollCanvasBy(resolution.deltaY)
                    }
                    return true
                } else if (resolution.deltaY < 0f) {
                    // Top underflow
                    val currentScroll = (scrollD + editor.offsetY).toFloat()
                    val desiredTarget = (currentScroll + resolution.deltaY).coerceAtLeast(0f)
                    if (isGlideRunning && scrollCoordinator.isAnimationActive) {
                        startAuthoritativeScrollAnimation(
                            targetTotalScroll = desiredTarget,
                            reason = ScrollAnimationReason.CARET_VISIBILITY,
                            durationMs = resolution.recommendedDurationMs
                        )
                    } else {
                        scrollCanvasBy(resolution.deltaY)
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (isAnyHandleActive || isMultiTouchActive) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
            return
        }
        if (disallowIntercept && (scrollD < headerHeight || editor.offsetY <= 0)) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * Consumes scroll delta vertically.
     * @param dy > 0 means finger moving up (content scrolls down, document canvas moves into view).
     *           dy < 0 means finger moving down (content scrolls up, pulling header back into view).
     * @return unconsumed delta.
     */
    fun scrollCanvasBy(dy: Float): Float {
        if (activePhysicalScrollOrigin == PhysicalScrollOrigin.NONE) {
            var unconsumed = 0f
            withPhysicalScrollOrigin(PhysicalScrollOrigin.EXTERNAL_PROGRAMMATIC) {
                unconsumed = scrollCanvasBy(dy)
            }
            return unconsumed
        }
        onScrollDelta?.invoke(dy)
        lastScrollWasBoundaryClamped = false

        if (dy > 0f) {
            // Scrolling down into document (finger moving up)
            // 1. If top edge stretch effect is active, absorb upward movement into releasing the stretch first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                val dist = EdgeEffectCompat.getDistance(topEdgeEffect)
                if (dist > 0f) {
                    val deltaDistance = -dy / height.coerceAtLeast(1)
                    val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                    postInvalidateOnAnimation()
                    if (EdgeEffectCompat.getDistance(topEdgeEffect) > 0f) {
                        return dy
                    }
                }
            }
        }

        val editorOffsetBefore = editor.offsetY
        val editorMaxBefore = editor.safeScrollMaxY
        val editorResidualBefore = editorScrollResidualPx
        val physicalBefore = (scrollD + editorOffsetBefore).toFloat()
        val maxBound = maxCanvasScroll
        val allowInnerRetract = allowInnerTransitionalRetraction
        val allowOuterRetract = allowOuterTransitionalRetraction
        val clampInnerLowerToNewMax = isDeletionTransactionActive || isDeletionResetRetractionActive
        val clampOuterLowerToNewMax = isDeletionTransactionActive || isDeletionResetRetractionActive

        // 2. Authoritative 1-DOF scroll allocation via ScribeScrollPolicy
        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = dy,
            headerHeight = headerHeight,
            scrollD = (scrollD.toFloat() + outerScrollResidualPx).coerceAtLeast(0f),
            editorOffsetY = editor.offsetY,
            maxCanvasScroll = maxBound
        )

        var outerHitBoundary = false
        var innerHitBoundary = false

        fun applyOuter(outerDelta: Float) {
            if (outerDelta < 0f && editor.offsetY > 0) {
                outerHitBoundary = true
                return
            }
            val step = ScribeScrollDeletionPolicy.resolveOuterScrollStep(
                currentScrollD = scrollD,
                currentResidualPx = outerScrollResidualPx,
                outerDelta = outerDelta,
                headerHeight = headerHeight,
                maxCanvasScroll = maxBound,
                isDeletionTransactionActive = allowOuterRetract,
                clampLowerToNewMax = clampOuterLowerToNewMax
            )
            val prevD = scrollD
            scrollD = step.nextIntOffset
            outerScrollResidualPx = step.nextResidualPx
            scrollDFloat = scrollD.toFloat() + outerScrollResidualPx
            outerHitBoundary = step.hitBoundary
            if (scrollD < headerHeight) {
                editorScrollResidualPx = 0f
            }
            applyTranslations()
            if (scrollD != prevD) {
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
        }

        if (dy < 0f) {
            // Reverse scroll: drain inner editor offset to 0 before retracting outer header,
            // preserving sub-pixel residual continuity across the inner -> outer layer handoff.
            var effectiveOuterDelta = allocation.outerDelta
            if (allocation.innerDelta != 0f) {
                val innerProjectedAfter = editorOffsetBefore.toFloat() + editorResidualBefore + allocation.innerDelta
                innerHitBoundary = dispatchScrollToEditor(
                    dy = allocation.innerDelta,
                    allowInnerRetraction = allowInnerRetract,
                    clampLowerToNewMax = clampInnerLowerToNewMax
                )
                val innerReachedZeroHandoff = editor.offsetY == 0 && scrollD > 0 &&
                    (!allowInnerRetract || editorMaxBefore == 0 || !clampInnerLowerToNewMax)
                if (innerReachedZeroHandoff) {
                    if (innerProjectedAfter < 0f) {
                        effectiveOuterDelta += innerProjectedAfter
                    } else if (editorScrollResidualPx > 0f && effectiveOuterDelta < 0f) {
                        val combinedOuter = effectiveOuterDelta + editorScrollResidualPx
                        if (combinedOuter <= 0f) {
                            editorScrollResidualPx = 0f
                            effectiveOuterDelta = combinedOuter
                        } else {
                            editorScrollResidualPx = combinedOuter
                            effectiveOuterDelta = 0f
                        }
                    }
                }
            } else if (editorOffsetBefore == 0 && editorScrollResidualPx > 0f && scrollD > 0 && effectiveOuterDelta < 0f) {
                val combinedOuter = effectiveOuterDelta + editorScrollResidualPx
                if (combinedOuter <= 0f) {
                    editorScrollResidualPx = 0f
                    effectiveOuterDelta = combinedOuter
                } else {
                    editorScrollResidualPx = combinedOuter
                    effectiveOuterDelta = 0f
                }
            }
            if (effectiveOuterDelta != 0f) {
                applyOuter(effectiveOuterDelta)
            }
        } else {
            // Forward scroll: collapse outer header before scrolling inner editor
            if (allocation.outerDelta != 0f) {
                applyOuter(allocation.outerDelta)
            }
            if (allocation.innerDelta != 0f) {
                innerHitBoundary = dispatchScrollToEditor(
                    dy = allocation.innerDelta,
                    allowInnerRetraction = allowInnerRetract,
                    clampLowerToNewMax = clampInnerLowerToNewMax
                )
            }
        }

        updateRetractionDebtFromCurrentPosition(armIfOverscrolled = false)

        val physicalAfter = (scrollD + editor.offsetY).toFloat()
        val actualMoved = physicalAfter - physicalBefore
        if (actualMoved != 0f) {
            notifyViewportScrollChanged()
        }

        val allocationBlocked = (dy > 0f && allocation.unconsumedDelta > 0.05f) ||
            (dy < 0f && allocation.unconsumedDelta < -0.05f)
        val innerHandoffToOuter = dy < 0f &&
            scrollD > 0 &&
            (editorMaxBefore == 0 || !(allowInnerRetract && clampInnerLowerToNewMax && editorOffsetBefore > editorMaxBefore))
        val outerHandoffToInner = dy > 0f &&
            maxBound > headerHeight &&
            allocation.innerDelta != 0f
        val effectiveOuterHitBoundary = outerHitBoundary && !outerHandoffToInner
        val effectiveInnerHitBoundary = innerHitBoundary && !innerHandoffToOuter
        lastScrollWasBoundaryClamped = effectiveOuterHitBoundary || effectiveInnerHitBoundary || allocationBlocked

        // 3. Top edge stretch when overscrolling at the top
        if (dy < 0f && allocation.unconsumedDelta < 0f && scrollD <= 0 && editor.offsetY <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                val deltaDistance = -allocation.unconsumedDelta / height.coerceAtLeast(1)
                val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                postInvalidateOnAnimation()
            }
        }

        return dy - actualMoved
    }

    /**
     * Natively scrolls Sora CodeEditor via its internal EditorScroller.
     * This avoids calling View.scrollBy() which desynchronizes the View hardware canvas,
     * ensuring lines render smoothly at 120Hz without blank screens during fast scrolling.
     */
    private fun dispatchScrollToEditor(
        dy: Float,
        allowInnerRetraction: Boolean = allowInnerTransitionalRetraction,
        clampLowerToNewMax: Boolean = isDeletionTransactionActive || isDeletionResetRetractionActive
    ): Boolean {
        // Invariant A: Editor MUST NOT scroll if header is visible
        if (scrollD < headerHeight) {
            editorScrollResidualPx = 0f
            return dy != 0f
        }
        return try {
            val scroller = editor.scroller ?: return true
            val currY = editor.offsetY
            val currX = editor.offsetX
            val maxAllowedY = editor.safeScrollMaxY
            val step = ScribeScrollDeletionPolicy.resolveInnerScrollStep(
                currentOffsetY = currY,
                currentResidualPx = editorScrollResidualPx,
                innerDelta = dy,
                maxAllowedY = maxAllowedY,
                isDeletionTransactionActive = allowInnerRetraction,
                clampLowerToNewMax = clampLowerToNewMax
            )
            val clampedTargetY = step.nextIntOffset
            editorScrollResidualPx = step.nextResidualPx
            if (clampedTargetY != currY) {
                scroller.forceFinished(true)
                scroller.startScroll(currX, currY, 0, clampedTargetY - currY, 0)
                scroller.abortAnimation()
                if (editor.offsetY != clampedTargetY) {
                    editorScrollResidualPx = 0f
                }
                try {
                    editor.renderContext.invalidateRenderNodes()
                } catch (_: Throwable) {}
                editor.invalidate()
                notifyViewportScrollChanged()
            }
            step.hitBoundary
        } catch (_: Throwable) {
            true
        }
    }

    private fun onPointerGestureEndedIfNeeded(ev: MotionEvent) {
        if (gapCollapseController.isPointerFrozen) {
            val actualScroll = (scrollD + editor.offsetY).toFloat()
            val nextPhase = gapCollapseController.resumeFromPointerFrozen(
                expectedGen = gapCollapseController.owningDeletionGen,
                actualPhysicalScrollPx = actualScroll
            )
            if (nextPhase == GapCollapsePhase.FINAL_SAFETY_PENDING) {
                completeDeletionVisualTransaction(
                    capturedGen = gapCollapseController.owningDeletionGen,
                    capturedSafetyEpoch = gapCollapseController.safetyEpoch
                )
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            val handled = inputController.dispatchTouchEvent(ev)
            handled ?: super.dispatchTouchEvent(ev)
        } finally {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                onPointerGestureEndedIfNeeded(ev)
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return inputController.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (inputController.onTouchEvent(ev)) return true
        return super.onTouchEvent(ev)
    }

    override fun computeScroll() {
        inputController.computeScroll()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = "detached")
        clearPendingDeletionLayoutListener()
        resetDeletionActiveRunnable?.let { removeCallbacks(it) }
        resetDeletionActiveRunnable = null
    }
}
