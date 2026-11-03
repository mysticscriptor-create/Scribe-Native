package com.primaloptima.scribe.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.EdgeEffect
import android.widget.OverScroller
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

    var preservedDismissalScroll: Int? = null

    var isKeyboardDismissing: Boolean = false
        set(value) {
            field = value
            if (value) {
                if (preservedDismissalScroll == null) {
                    preservedDismissalScroll = scrollD + editor.offsetY
                }
            } else {
                checkAndClearPreservedDismissalScroll()
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
        get() = 24f * resources.displayMetrics.density

    /**
     * Stable standard keyboard baseline in pixels, derived from the validated stored value or default (280dp).
     * Represents the keyboard itself—does NOT include shortcut bar or breathing margin.
     */
    var standardKeyboardHeightPx: Float = ScribeBottomSurfaceCoordinator.DEFAULT_KEYBOARD_DP * context.resources.displayMetrics.density
        set(value) {
            field = value.coerceAtLeast(0f)
            updatePersistentBottomClearance()
        }

    /**
     * Fixed shortcut-bar slot height in pixels, currently 36dp when applicable (or 0f when no shortcuts).
     */
    var shortcutBarHeightPx: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            updatePersistentBottomClearance()
        }

    /**
     * Current physical IME measurement in pixels above navigation bar.
     */
    var liveImeHeightPx: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    /**
     * Actual displayed drawer obstruction in pixels, controlled by the drawer-height controller.
     */
    var liveDrawerHeightPx: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    /**
     * Authoritative expected final viewport height in pixels from target IME/navigation geometry.
     * When supplied, predictive scrolling aligns to this exact target rather than blindly subtracting
     * a full-screen keyboard estimate from a constrained split-screen or multi-window height.
     */
    var targetEditorViewportHeightPx: Float? = null

    /**
     * Backward-compatible property representing standard keyboard + shortcut bar height.
     */
    var estimatedKeyboardHeightPx: Float
        get() = standardKeyboardHeightPx + shortcutBarHeightPx
        set(value) {
            standardKeyboardHeightPx = (value - shortcutBarHeightPx).coerceAtLeast(0f)
        }

    /**
     * Authoritative retained bottom scroll clearance in pixels.
     * Captures the complete three-part geometry from settled standard keyboard.
     * When the physical bottom surface disappears upon dismissal, this clearance preserves the document's
     * complete previously available scroll range without clamping or creating visible blank space.
     */
    var retainedBottomScrollClearancePx: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            updatePersistentBottomClearance()
        }

    val persistentBottomSpacePx: Float
        get() = ScribeScrollPolicy.calculateVirtualTrailingExtentPx(
            isPhysicalBottomSurfaceActive = isKeyboardVisible || isBottomSurfaceActive,
            standardKeyboardHeightPx = standardKeyboardHeightPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMarginPx,
            retainedClearancePx = retainedBottomScrollClearancePx
        )

    private fun updatePersistentBottomClearance() {
        editor.persistentBottomSpacePx = persistentBottomSpacePx.roundToInt()
    }

    var retainedScrollExtentPx: Float
        get() = retainedBottomScrollClearancePx
        set(value) {
            retainedBottomScrollClearancePx = value
        }

    /**
     * Authoritative update of the three-part retained bottom clearance:
     * actual IME obstruction above navigation baseline + actual shortcut bar height + breathing margin.
     */
    fun updateRetainedBottomScrollClearance(
        actualObstructionAboveNavPx: Float,
        shortcutBarHeightPx: Float
    ) {
        val breathingMargin = breathingMarginPx
        retainedBottomScrollClearancePx = ScribeBottomSurfaceCoordinator.calculateRetainedBottomScrollClearancePx(
            actualObstructionAboveNavPx = actualObstructionAboveNavPx,
            shortcutBarHeightPx = shortcutBarHeightPx,
            breathingMarginPx = breathingMargin
        )
    }

    /**
     * Ends an active expansion transition lifecycle upon IME or drawer settlement.
     */
    fun endExpansionTransition() {
        isExpansionTransitionActive = false
    }

    /**
     * Cancels any active viewport-owned safety animation when keyboard dismissal starts.
     */
    fun cancelViewportSafetyAnimation() {
        if (isGlideRunning && (activeGlideReason == ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY ||
                    activeGlideReason == ScribeScrollPolicy.ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION ||
                    activeGlideReason == ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE)) {
            removeCallbacks(glideFrameRunnable)
            isGlideRunning = false
            activeGlideReason = null
            activeGlideOnEnd = null
            val oldAnimator = activeCanvasAnimator
            activeCanvasAnimator = null
            oldAnimator?.cancel()
            scrollCoordinator.cancelAnimation("IME dismissal started")
        }
    }

    val naturalMaxCanvasScroll: Int
        get() {
            val layoutH = editor.layout?.layoutHeight ?: 0
            val breathingMargin = breathingMarginPx.roundToInt()
            val bottomSpace = if (isKeyboardVisible || isBottomSurfaceActive) {
                breathingMargin
            } else {
                persistentBottomSpacePx.roundToInt()
            }
            return ScribeScrollPolicy.calculateMaxCanvasScroll(
                headerHeight = headerHeight,
                layoutHeight = layoutH,
                viewportHeight = height,
                bottomSpace = bottomSpace,
                isTextEmpty = editor.text.isEmpty()
            )
        }

    private var preEntranceMaxCanvasScroll: Int = 0

    val maxCanvasScroll: Int
        get() {
            val naturalMax = naturalMaxCanvasScroll
            return when {
                isKeyboardTransactionActive -> maxOf(preEntranceMaxCanvasScroll, naturalMax)
                else -> preservedDismissalScroll?.let { maxOf(naturalMax, it) } ?: naturalMax
            }
        }

    fun checkAndClearPreservedDismissalScroll() {
        val preserved = preservedDismissalScroll ?: return
        if (!isKeyboardDismissing && !isKeyboardVisible && !isBottomSurfaceActive) {
            if (naturalMaxCanvasScroll >= preserved) {
                preservedDismissalScroll = null
            }
        }
    }

    val maxHeaderScrollD: Int
        get() = ScribeScrollPolicy.calculateMaxHeaderScroll(headerHeight, maxCanvasScroll)

    var isKeyboardVisible: Boolean = false
        set(value) {
            field = value
            editor.isKeyboardVisible = value
            updatePersistentBottomClearance()
            if (!value) {
                checkAndClearPreservedDismissalScroll()
            }
        }

    var isBottomSurfaceActive: Boolean = false
        set(value) {
            val wasActive = field
            field = value
            editor.isBottomSurfaceActive = value
            updatePersistentBottomClearance()
            if (wasActive && !value) {
                if (preservedDismissalScroll == null) {
                    preservedDismissalScroll = scrollD + editor.offsetY
                }
                cancelViewportSafetyAnimation()
                checkAndClearPreservedDismissalScroll()
            }
        }

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

    var onScrollDelta: ((dy: Float) -> Unit)? = null
    var onUnifiedScrollChanged: ((scrollD: Int, maxHeaderHeight: Int) -> Unit)? = null
    var onViewportScrollChanged: ((scrollD: Int, editorOffsetY: Int, totalLogicalScroll: Int) -> Unit)? = null
    var onUserTouchDown: (() -> Unit)? = null
    var onKeyboardEntranceStarted: (() -> Unit)? = null

    fun notifyViewportScrollChanged() {
        onViewportScrollChanged?.invoke(scrollD, editor.offsetY, scrollD + editor.offsetY)
    }

    private val scroller = OverScroller(context)
    private var lastScrollerY = 0

    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var initialDownX = 0f
    private var initialDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDraggingCanvas = false
    private var isTouchOnHandleActive = false

    private var isMultiTouchLatched = false

    val isAnyHandleActive: Boolean
        get() = editor.isHandleDragging || isTouchOnHandleActive

    val isMultiTouchActive: Boolean
        get() = isMultiTouchLatched || (editor as? ScribeCodeEditor)?.isZoomGestureActiveOnly == true || editor.isPinchScaling

    private val topEdgeEffect: EdgeEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        EdgeEffectCompat.create(context, null)
    } else {
        null
    }

    var isUserTouching: Boolean = false
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
    internal val scrollCoordinator = ScribeScrollPolicy.AuthoritativeScrollCoordinator().apply {
        onScrollBy = { delta ->
            scrollCanvasBy(delta)
        }
    }

    /**
     * Transition-aware compensation tracking:
     * Records upward canvas displacement caused by an expanded bottom surface (e.g. emoji keyboard,
     * accessory drawer) so that it can be safely and smoothly restored when the surface contracts.
     */
    /**
     * Tracks the previous physical bottom obstruction to accurately confirm real contractions.
     */
    private var previousBottomObstructionPx: Float = 0f

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
     * Authoritative single active animation tracking. Monotonic operation IDs
     * are owned by scrollCoordinator to eliminate cancellation/retargeting races.
     */
    private var activeCanvasAnimator: android.animation.ValueAnimator? = null
    private var isGlideRunning = false
    private var glidePosition = 0f
    private var glideVelocity = 0f
    private var glideTarget = 0f
    private var glideLastTimeNs = 0L
    private var activeGlideReason: ScribeScrollPolicy.ScrollAnimationReason? = null
    private var activeGlideOnEnd: (() -> Unit)? = null
    private var activeGlideOpId: Long = 0L

    private val glideFrameRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isGlideRunning) return
            val opId = activeGlideOpId
            if (opId != scrollCoordinator.activeOperationId || !scrollCoordinator.isAnimationActive) {
                isGlideRunning = false
                return
            }

            val nowNs = System.nanoTime()
            val dtSec = ((nowNs - glideLastTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            glideLastTimeNs = nowNs

            // Critically damped second-order spring:
            // Natural frequency omega = 13.5 rad/s gives ~300ms ultra-smooth gentle glide
            // Damping ratio zeta = 1.0 (critically damped: zero overshoot, perfectly smooth asymptotic settling)
            val omega = 13.5f
            val displacement = glidePosition - glideTarget
            val accel = -omega * omega * displacement - 2f * omega * glideVelocity
            glideVelocity += accel * dtSec
            glidePosition += glideVelocity * dtSec

            // Hard boundary invariant: deletion glide must never cross the 24dp breathing margin
            if (activeGlideReason == ScribeScrollPolicy.ScrollAnimationReason.DELETION_ANCHOR) {
                val minSafeScroll = if (isDeletionTailAnchored) {
                    (headerHeight + (editor.layout?.layoutHeight ?: 0) + breathingMarginPx - height).coerceAtLeast(0f)
                } else {
                    val cur = try { editor.cursor } catch (_: Throwable) { null }
                    val lay = editor.layout
                    val caretDocY = if (cur != null && lay != null) {
                        getCharGeometry(lay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
                    } else {
                        0f
                    }
                    (headerHeight + caretDocY + breathingMarginPx - height).coerceAtLeast(0f)
                }
                if (glidePosition < minSafeScroll) {
                    glidePosition = minSafeScroll
                    if (glideVelocity < 0f) glideVelocity = 0f
                }
            }

            scrollCoordinator.applyStep(glidePosition, opId)

            val distToTarget = kotlin.math.abs(glidePosition - glideTarget)
            val isSettled = distToTarget < 0.5f && kotlin.math.abs(glideVelocity) < 15f
            val isStoppedByCoord = !scrollCoordinator.isAnimationActive

            if (isSettled || isStoppedByCoord) {
                isGlideRunning = false
                if (scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId) {
                    if (distToTarget < 1f) {
                        scrollCoordinator.applyStep(glideTarget, opId)
                    }
                    scrollCoordinator.finishAnimation(opId)
                }
                val reason = activeGlideReason
                val onEnd = activeGlideOnEnd
                activeGlideOnEnd = null
                activeGlideReason = null

                if (reason == ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE) {
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                } else if (reason == ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION) {
                    revalidateFinalTargetSafety()
                } else if (reason == ScribeScrollPolicy.ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION) {
                    rebaseCompensationAfterSafeCorrection()
                }
                onEnd?.invoke()
            } else {
                postOnAnimation(this)
            }
        }
    }
    val isRestorationActive: Boolean
        get() = scrollCoordinator.isAnimationActive && scrollCoordinator.activeReason == ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION
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
        if (reason != null && ScribeScrollPolicy.isDebugDiagnosticsEnabled) {
            ScribeScrollPolicy.logDiagnostic("Compensation", "Invalidated: $reason")
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
            removeCallbacks(glideFrameRunnable)
            isGlideRunning = false
            activeGlideReason = null
            activeGlideOnEnd = null
            val oldAnimator = activeCanvasAnimator
            activeCanvasAnimator = null
            oldAnimator?.cancel()
            scrollCoordinator.cancelAnimation("Restoration cancelled")
        }
    }



    // ── Coordinated Keyboard Viewport Transaction ─────────────────────────────
    var lastTouchDownY: Float? = null
        private set
    var lastTouchDownTime: Long = 0L
        private set
    var isPredictiveGlideRunning: Boolean = false
        private set
    var targetPredictedScrollTotal: Float = -1f
        private set
    private val transactionCoordinator = ScribeScrollPolicy.KeyboardTransactionCoordinator()
    private var activeKeyboardTransaction: ScribeScrollPolicy.KeyboardTransaction? = null
    private var keyboardTransactionId = 0L
    var keyboardTransactionEpoch: Long = 0L
        private set
    var isImePhysicallySettled: Boolean = false
        internal set
    internal var hasShrunkDuringTransaction: Boolean = false

    var onKeyboardEntranceFailed: (() -> Unit)? = null

    private val imeAppearanceWatchdogRunnable = Runnable {
        if (isKeyboardTransactionActive && !isImePhysicallySettled && liveImeHeightPx <= 0f) {
            cancelKeyboardTransaction("IME appearance timeout")
        }
    }

    private fun startImeAppearanceWatchdog() {
        removeCallbacks(imeAppearanceWatchdogRunnable)
        postDelayed(imeAppearanceWatchdogRunnable, 1500L)
    }

    private fun cancelImeAppearanceWatchdog() {
        removeCallbacks(imeAppearanceWatchdogRunnable)
    }

    val isKeyboardTransactionActive: Boolean
        get() = transactionCoordinator.isActive

    fun beginKeyboardTransaction(
        targetScroll: Float = -1f,
        targetLine: Int? = null,
        targetCol: Int? = null,
        initialScreenY: Float? = null,
        isUnresolvedTap: Boolean = false
    ): Long {
        keyboardTransactionId++
        keyboardTransactionEpoch++
        val currentScroll = (scrollDFloat + editor.offsetY)
        preEntranceMaxCanvasScroll = naturalMaxCanvasScroll
        hasShrunkDuringTransaction = false
        val tx = transactionCoordinator.startTransaction(
            transactionId = keyboardTransactionId,
            preTransactionScroll = currentScroll,
            targetScroll = targetScroll,
            targetLine = targetLine,
            targetCol = targetCol,
            initialScreenY = initialScreenY,
            isUnresolvedTap = isUnresolvedTap
        )
        activeKeyboardTransaction = tx
        return tx.transactionId
    }

    fun notifyKeyboardEntranceStarting(
        targetLine: Int? = null,
        targetCol: Int? = null,
        isUnresolvedTap: Boolean = false
    ) {
        if (isKeyboardTransactionActive) return
        isImePhysicallySettled = false
        startImeAppearanceWatchdog()
        predictivelyGlideCursorAboveKeyboard(
            targetLine = targetLine,
            targetCol = targetCol,
            isUnresolvedTap = isUnresolvedTap
        )
    }

    /**
     * Authoritative update of target editor viewport from Compose layout coordination.
     * Reconciles in-flight predictive glide or active keyboard transactions immediately.
     */
    fun updateTargetEditorViewport(targetViewportPx: Float) {
        val safeTarget = targetViewportPx.coerceAtLeast(0f)
        val prevTarget = targetEditorViewportHeightPx
        if (prevTarget != null && kotlin.math.abs(safeTarget - prevTarget) < 1f) {
            return
        }
        targetEditorViewportHeightPx = safeTarget
        if (!hasWindowFocus()) {
            return
        }
        if (isKeyboardTransactionActive || (isPredictiveGlideRunning && targetPredictedScrollTotal > 0f)) {
            val cursor = try { editor.cursor } catch (_: Throwable) { null } ?: return
            val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
            val line = activeKeyboardTransaction?.targetLine ?: cursor.leftLine
            val col = activeKeyboardTransaction?.targetCol ?: cursor.leftColumn
            val geom = getCharGeometry(layout, line, col, editor.rowHeight.toFloat())
            val density = resources.displayMetrics.density
            val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
            val currentScroll = (scrollD + editor.offsetY).toFloat()
            val targetLineBottom = (safeTarget - marginPx).toFloat()
            val plannedTarget = if (targetPredictedScrollTotal > 0f) targetPredictedScrollTotal else currentScroll
            val finalScreenTop = (headerHeight + geom.topDocY - plannedTarget)
            val finalScreenBottom = (headerHeight + geom.bottomDocY - plannedTarget)

            if (finalScreenTop >= -deadbandPx && finalScreenBottom <= (targetLineBottom + deadbandPx)) {
                // In-flight predictive glide is heading towards a safe target; do NOT cancel in-flight glide!
                return
            }

            val overflow = if (finalScreenBottom > targetLineBottom + deadbandPx) {
                finalScreenBottom - targetLineBottom
            } else if (finalScreenTop < -deadbandPx) {
                finalScreenTop
            } else 0f

            val maxScrollFuture = ScribeScrollPolicy.calculateMaxCanvasScroll(
                headerHeight = headerHeight,
                layoutHeight = layout.layoutHeight,
                viewportHeight = safeTarget.toInt(),
                bottomSpace = marginPx,
                isTextEmpty = editor.text.isEmpty()
            ).toFloat()

            val newTarget = (plannedTarget + overflow).coerceIn(0f, maxScrollFuture)
            activeKeyboardTransaction = activeKeyboardTransaction?.copy(targetScroll = newTarget)
            if (kotlin.math.abs(newTarget - plannedTarget) > ScribeScrollPolicy.DRAG_THRESHOLD_DP ||
                (!isPredictiveGlideRunning && kotlin.math.abs(newTarget - currentScroll) > ScribeScrollPolicy.DRAG_THRESHOLD_DP)) {
                targetPredictedScrollTotal = newTarget
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = newTarget,
                    reason = ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE,
                    durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS
                ) {
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                    checkKeyboardTransactionSettled()
                }
            } else {
                if (!isPredictiveGlideRunning) {
                    targetPredictedScrollTotal = currentScroll
                }
            }
        }
    }

    fun clearTargetEditorViewport() {
        targetEditorViewportHeightPx = null
    }

    fun endKeyboardTransaction() {
        cancelImeAppearanceWatchdog()
        transactionCoordinator.finish()
        activeKeyboardTransaction = null
        preEntranceMaxCanvasScroll = 0
        hasShrunkDuringTransaction = false
        targetEditorViewportHeightPx = null
    }

    fun cancelKeyboardTransaction(reason: String = "Unknown") {
        cancelImeAppearanceWatchdog()
        keyboardTransactionEpoch++
        val wasActive = isKeyboardTransactionActive
        transactionCoordinator.cancel(reason)
        endKeyboardTransaction()
        isExpansionTransitionActive = false
        if (wasActive) {
            onKeyboardEntranceFailed?.invoke()
        }
        isPredictiveGlideRunning = false
        targetPredictedScrollTotal = -1f

        // Fully cancel in-flight animation machinery ONLY if owned by keyboard entrance
        if (activeGlideReason == ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE) {
            removeCallbacks(glideFrameRunnable)
            val oldAnimator = activeCanvasAnimator
            activeCanvasAnimator = null
            oldAnimator?.cancel()
            isGlideRunning = false
            activeGlideReason = null
            activeGlideOnEnd = null
            scrollCoordinator.cancelAnimation("Keyboard transaction cancelled: $reason")
        }
    }

    fun notifyImeSettledAtTarget() {
        cancelImeAppearanceWatchdog()
        isImePhysicallySettled = true
        if (isKeyboardTransactionActive) {
            var updated = transactionCoordinator.onImeInsetSettled()
            if (hasShrunkDuringTransaction && updated?.isLayoutMeasurementSettled != true) {
                updated = transactionCoordinator.onLayoutMeasurementSettled()
            }
            activeKeyboardTransaction = updated
            if (updated?.isFullySettled == true) {
                reconcileActiveTransactionSettledViewport()
            }
        }
        checkKeyboardTransactionSettled()
    }

    fun checkKeyboardTransactionSettled() {
        if (isKeyboardTransactionActive &&
            transactionCoordinator.isFullySettled &&
            !isGlideRunning &&
            !isExpansionTransitionActive) {
            endKeyboardTransaction()
        }
    }

    fun reconcileActiveTransactionTarget(committedLine: Int, committedCol: Int) {
        val transaction = activeKeyboardTransaction ?: return
        val updatedTx = transactionCoordinator.reconcileTarget(committedLine, committedCol)
        activeKeyboardTransaction = updatedTx
        val edLayout = editor.layout ?: return
        val density = resources.displayMetrics.density
        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
        val currentScroll = (scrollDFloat + editor.offsetY)
        val geom = getCharGeometry(edLayout, committedLine, committedCol, editor.rowHeight.toFloat())

        val futureViewportHeight = if (isKeyboardVisible || isBottomSurfaceActive) {
            height.toFloat()
        } else if (targetEditorViewportHeightPx != null && targetEditorViewportHeightPx!! > 0f) {
            targetEditorViewportHeightPx!!
        } else {
            val availableH = height.toFloat()
            val maxObstruction = if (availableH > 0f) minOf(estimatedKeyboardHeightPx, availableH * 0.55f) else estimatedKeyboardHeightPx
            (availableH - maxObstruction).coerceAtLeast(editor.rowHeight * 3f)
        }
        val targetLineBottom = futureViewportHeight - marginPx

        val plannedTarget = if (targetPredictedScrollTotal > 0f) {
            targetPredictedScrollTotal
        } else if (transaction.targetScroll >= 0f) {
            transaction.targetScroll
        } else {
            currentScroll
        }

        val finalScreenTop = (headerHeight + geom.topDocY - plannedTarget)
        val finalScreenBottom = (headerHeight + geom.bottomDocY - plannedTarget)

        if (finalScreenTop >= -deadbandPx && finalScreenBottom <= (targetLineBottom + deadbandPx)) {
            activeKeyboardTransaction = activeKeyboardTransaction?.copy(
                targetLine = committedLine,
                targetCol = committedCol
            )
            return
        }

        if (finalScreenTop < -deadbandPx) {
            val correctedTarget = (plannedTarget + finalScreenTop).coerceAtLeast(0f)
            activeKeyboardTransaction = activeKeyboardTransaction?.copy(
                targetLine = committedLine,
                targetCol = committedCol,
                targetScroll = correctedTarget
            )
            targetPredictedScrollTotal = correctedTarget
            startAuthoritativeScrollAnimation(
                targetTotalScroll = correctedTarget,
                reason = ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE,
                durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS,
                forceRetarget = true
            ) {
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
                checkKeyboardTransactionSettled()
            }
            return
        }

        if (finalScreenBottom > targetLineBottom + deadbandPx) {
            val overflow = finalScreenBottom - targetLineBottom
            val correctedTarget = (plannedTarget + overflow).coerceIn(0f, maxCanvasScroll.toFloat())
            activeKeyboardTransaction = activeKeyboardTransaction?.copy(
                targetLine = committedLine,
                targetCol = committedCol,
                targetScroll = correctedTarget
            )
            targetPredictedScrollTotal = correctedTarget
            startAuthoritativeScrollAnimation(
                targetTotalScroll = correctedTarget,
                reason = ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE,
                durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS,
                forceRetarget = true
            ) {
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
                checkKeyboardTransactionSettled()
            }
        }
    }

    fun reconcileActiveTransactionSettledViewport() {
        val transaction = activeKeyboardTransaction ?: return
        val line = transaction.targetLine ?: editor.cursor?.leftLine ?: return
        val col = transaction.targetCol ?: editor.cursor?.leftColumn ?: 0
        val edLayout = editor.layout ?: return
        val cursor = try { editor.cursor } catch (_: Throwable) { null }
        val selGeom = if (cursor?.isSelected == true) getSelectionGeometry(cursor, edLayout) else null
        val geom = getCharGeometry(edLayout, line, col, editor.rowHeight.toFloat())
        val caretTop = selGeom?.topDocY ?: geom.topDocY
        val caretBottom = selGeom?.bottomDocY ?: geom.bottomDocY

        val currentScroll = (scrollDFloat + editor.offsetY)
        val plannedTarget = if (targetPredictedScrollTotal >= 0f) targetPredictedScrollTotal else transaction.targetScroll

        val reconciliationResult = ScribeScrollPolicy.evaluateSettledReconciliation(
            currentScrollY = currentScroll,
            plannedTargetScrollY = plannedTarget,
            caretDocTop = caretTop,
            caretDocBottom = caretBottom,
            headerHeight = headerHeight.toFloat(),
            settledViewportHeight = height.toFloat(),
            density = resources.displayMetrics.density,
            maxCanvasScroll = maxCanvasScroll.toFloat()
        )

        when (reconciliationResult) {
            is ScribeScrollPolicy.SettledReconciliationResult.CancelOvershoot -> {
                cancelViewportSafetyAnimation()
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
                checkKeyboardTransactionSettled()
            }
            is ScribeScrollPolicy.SettledReconciliationResult.Retarget -> {
                val target = reconciliationResult.targetScrollY.toFloat()
                targetPredictedScrollTotal = target
                activeKeyboardTransaction = transaction.copy(targetScroll = target)
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = target,
                    reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
                    durationMs = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS,
                    forceRetarget = true
                ) {
                    checkKeyboardTransactionSettled()
                }
            }
            is ScribeScrollPolicy.SettledReconciliationResult.NoOp -> {
                checkKeyboardTransactionSettled()
            }
        }
    }

    // ── Coordinated Deletion Viewport Transaction ─────────────────────────────
    private var activeDeletionSnapshot: ScribeScrollPolicy.DeletionSnapshot? = null
    private var deletionTransactionGeneration: Long = 0L
    private var isDeletionTailAnchored: Boolean = false

    val isDeletionTransactionActive: Boolean
        get() = activeDeletionSnapshot != null

    fun beginPreEditDeletionSnapshot(
        isSelection: Boolean,
        selectionStartLine: Int,
        selectionEndLine: Int,
        preEditDocLineCount: Int,
        preEditDocHeight: Float,
        preEditScrollY: Float,
        preEditCaretScreenY: Float,
        wasAtTail: Boolean,
        affectsDocumentTail: Boolean
    ) {
        deletionTransactionGeneration++
        activeDeletionSnapshot = ScribeScrollPolicy.DeletionSnapshot(
            transactionEpoch = deletionTransactionGeneration,
            isSelection = isSelection,
            selectionStartLine = selectionStartLine,
            selectionEndLine = selectionEndLine,
            preEditDocLineCount = preEditDocLineCount,
            preEditDocHeight = preEditDocHeight,
            preEditScrollY = preEditScrollY,
            preEditCaretScreenY = preEditCaretScreenY,
            wasAtTail = wasAtTail,
            affectsDocumentTail = affectsDocumentTail
        )
    }

    fun notifyContentDeletionStarted() {
        if (activeDeletionSnapshot != null) return // Already captured accurately before mutation
        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val lay = try { editor.layout } catch (_: Throwable) { null }
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
        val affectsDocumentTail = endLine >= (editor.text.lineCount - 1)
        beginPreEditDeletionSnapshot(
            isSelection = cur?.isSelected == true,
            selectionStartLine = cur?.leftLine ?: 0,
            selectionEndLine = cur?.rightLine ?: 0,
            preEditDocLineCount = editor.text.lineCount,
            preEditDocHeight = docH,
            preEditScrollY = currentScroll,
            preEditCaretScreenY = caretScreenY,
            wasAtTail = wasAtTail,
            affectsDocumentTail = affectsDocumentTail
        )
    }

    fun notifyLastLineDeletionStarted() {
        notifyContentDeletionStarted()
    }

    fun handleDeletionSettlement(smooth: Boolean = true) {
        val snapshot = activeDeletionSnapshot ?: return
        val currentGen = deletionTransactionGeneration

        val currentScroll = (scrollD + editor.offsetY).toFloat()
        val curLay = editor.layout
        val newDocHeight = curLay?.layoutHeight?.toFloat() ?: 0f
        val newDocLineCount = editor.text.lineCount
        val usableHeight = height.toFloat()
        val maxBound = maxCanvasScroll.toFloat()
        val breathingMargin = breathingMarginPx

        val intent = ScribeScrollPolicy.classifyDeletionIntent(
            snapshot = snapshot,
            newDocLineCount = newDocLineCount,
            newDocHeight = headerHeight + newDocHeight,
            usableViewportHeight = usableHeight
        )

        val cur = try { editor.cursor } catch (_: Throwable) { null }
        val newCaretDocY = if (cur != null && curLay != null) {
            getCharGeometry(curLay, cur.leftLine, cur.leftColumn, editor.rowHeight.toFloat()).bottomDocY
        } else {
            0f
        }

        val target = ScribeScrollPolicy.calculateSafeDeletionDestination(
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

        isDeletionTailAnchored = (intent == ScribeScrollPolicy.DeletionAnchorIntent.TAIL_ANCHOR)

        if (intent == ScribeScrollPolicy.DeletionAnchorIntent.RESET_TO_TOP) {
            activeDeletionSnapshot = null
            isDeletionTailAnchored = false
            resetDocumentToTop(smooth = smooth)
        } else if (kotlin.math.abs(target - currentScroll) >= 1f) {
            startAuthoritativeScrollAnimation(
                targetTotalScroll = target,
                reason = ScribeScrollPolicy.ScrollAnimationReason.DELETION_ANCHOR,
                durationMs = ScribeScrollPolicy.DURATION_DELETION_ANCHOR_MS,
                forceRetarget = true,
                onEnd = {
                    if (deletionTransactionGeneration == currentGen) {
                        activeDeletionSnapshot = null
                        isDeletionTailAnchored = false
                    }
                }
            )
        } else {
            activeDeletionSnapshot = null
            isDeletionTailAnchored = false
        }
    }

    companion object {
        val FastOutSlowInInterpolator: android.view.animation.Interpolator =
            PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }

    fun startAuthoritativeScrollAnimation(
        targetTotalScroll: Float,
        reason: ScribeScrollPolicy.ScrollAnimationReason,
        durationMs: Long = 280L,
        interpolator: android.view.animation.Interpolator = FastOutSlowInInterpolator,
        forceRetarget: Boolean = false,
        onEnd: (() -> Unit)? = null
    ) {
        val currentScroll = (scrollDFloat + editor.offsetY)
        scrollCoordinator.totalScroll = currentScroll
        val maxBound = maxCanvasScroll
        val clampedTarget = targetTotalScroll.coerceIn(0f, maxBound.toFloat())

        // Continuous retargeting: if a glide is already running, seamlessly retarget in-flight!
        if (isGlideRunning && scrollCoordinator.isAnimationActive) {
            val currTarget = glideTarget
            if (!forceRetarget && kotlin.math.abs(currTarget - clampedTarget) <= 1f) {
                onEnd?.invoke()
                return
            }
            glideTarget = clampedTarget
            activeGlideReason = reason
            if (onEnd != null) {
                val prevOnEnd = activeGlideOnEnd
                activeGlideOnEnd = {
                    prevOnEnd?.invoke()
                    onEnd.invoke()
                }
            }
            scrollCoordinator.retargetActiveOperation(clampedTarget, reason, maxBound)
            return
        }

        val shouldStart = scrollCoordinator.requestTarget(clampedTarget, reason, forceRetarget, maxBound)
        if (!shouldStart) {
            onEnd?.invoke()
            return
        }

        val opId = scrollCoordinator.activeOperationId
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        removeCallbacks(glideFrameRunnable)

        val target = scrollCoordinator.activeTargetTotalScroll ?: clampedTarget
        val distance = kotlin.math.abs(target - currentScroll)
        if (distance < 1f) {
            scrollCoordinator.finishAnimation(opId)
            onEnd?.invoke()
            return
        }

        isGlideRunning = true
        glidePosition = currentScroll
        glideVelocity = 0f
        glideTarget = target
        glideLastTimeNs = System.nanoTime()
        activeGlideReason = reason
        activeGlideOnEnd = onEnd
        activeGlideOpId = opId

        postOnAnimation(glideFrameRunnable)
    }

    fun smoothScrollCanvasBy(
        dy: Float,
        reason: ScribeScrollPolicy.ScrollAnimationReason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
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

    /**
     * Clean, strongly typed abstraction for character and layout geometry from Sora Editor.
     * Eliminates confusion between vertical doc Y and horizontal doc X.
     */
    data class SoraCharGeometry(
        val bottomDocY: Float,
        val topDocY: Float,
        val leftDocX: Float
    )

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
    ): ScribeScrollPolicy.SelectionGeometry? {
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

            return ScribeScrollPolicy.SelectionGeometry(
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

        return ScribeScrollPolicy.SelectionGeometry(
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
            reason = ScribeScrollPolicy.ScrollAnimationReason.CONTRACTION_RESTORATION,
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
        val validation = ScribeScrollPolicy.validateFinalTarget(
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
                    reason = ScribeScrollPolicy.ScrollAnimationReason.SAFE_BOUNDARY_CORRECTION,
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

    fun cancelCanvasAnimation() {
        cancelRestoration()
        removeCallbacks(glideFrameRunnable)
        isGlideRunning = false
        activeGlideReason = null
        activeGlideOnEnd = null
        val oldAnimator = activeCanvasAnimator
        activeCanvasAnimator = null
        oldAnimator?.cancel()
        scrollCoordinator.cancelAnimation("cancelCanvasAnimation")
        isPredictiveGlideRunning = false
        targetPredictedScrollTotal = -1f
        endKeyboardTransaction()
        activeDeletionSnapshot = null
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
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
            handleDeletionSettlement(smooth = smooth)
            return
        }
        val deltaY = ScribeScrollPolicy.calculateDeletionAnchorDelta(
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
                    reason = ScribeScrollPolicy.ScrollAnimationReason.DELETION_ANCHOR,
                    durationMs = ScribeScrollPolicy.DURATION_DELETION_ANCHOR_MS,
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
        cancelCanvasAnimation()
        try {
            editor.scroller?.abortAnimation()
        } catch (_: Throwable) {}
        val totalScroll = scrollD + editor.offsetY
        if (totalScroll > 0) {
            if (smooth) {
                smoothScrollCanvasBy(
                    dy = -totalScroll.toFloat(),
                    reason = ScribeScrollPolicy.ScrollAnimationReason.RESET_TOP,
                    durationMs = ScribeScrollPolicy.DURATION_RESET_TOP_MS,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                scrollCanvasBy(-totalScroll.toFloat())
            }
        }
    }

    /**
     * Directly restores the canvas header scroll offset without animation.
     */
    fun setCanvasScrollD(targetScrollD: Int) {
        cancelCanvasAnimation()
        val clamped = targetScrollD.coerceIn(0, headerHeight)
        scrollD = clamped
        scrollDFloat = clamped.toFloat()
        applyTranslations()
        onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
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
                val lineDocY = geom.topDocY
                val cursorCanvasY = headerHeight + lineDocY
                val viewportHeight = height.toFloat()
                val targetTotalScroll = cursorCanvasY - (viewportHeight / 2f)
                val currentScroll = (scrollDFloat + editor.offsetY)
                val distance = kotlin.math.abs(targetTotalScroll - currentScroll)
                val maxSmoothDistance = 1.5f * viewportHeight

                if (smooth && distance <= maxSmoothDistance && distance > 1f) {
                    val maxBound = maxCanvasScroll.toFloat()
                    val clampedTarget = targetTotalScroll.coerceIn(0f, maxBound)
                    startAuthoritativeScrollAnimation(
                        targetTotalScroll = clampedTarget,
                        reason = ScribeScrollPolicy.ScrollAnimationReason.PROGRAMMATIC,
                        durationMs = 320L
                    )
                } else {
                    if (targetTotalScroll <= 0f) {
                        restoreCanvasAndEditorScroll(0, 0)
                    } else {
                        val targetScrollD = targetTotalScroll.toInt().coerceIn(0, headerHeight)
                        val remainingScroll = (targetTotalScroll.toInt() - targetScrollD).coerceAtLeast(0)
                        restoreCanvasAndEditorScroll(targetScrollD, remainingScroll)
                    }
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
                scrollD = reconciled.scrollD
                scrollDFloat = reconciled.scrollD.toFloat()
                applyTranslations()
                try {
                    editor.jumpScrollTo(editor.offsetX, reconciled.editorOffsetY)
                } catch (_: Throwable) {}
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                notifyViewportScrollChanged()
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
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        editor.stopFling()
    }

    fun resetScroll() {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        editor.stopFling()
        scrollDFloat = 0f
        scrollD = 0
        isDraggingCanvas = false
        applyTranslations()
        onUnifiedScrollChanged?.invoke(0, headerHeight)
        notifyViewportScrollChanged()
        try {
            editor.jumpScrollTo(editor.offsetX, 0)
        } catch (_: Throwable) {}
    }

    /**
     * Continues a downward fling from the editor into the canvas header.
     * Called by ScribeCodeEditor when flinging towards the top and offsetY hits 0.
     */
    fun continueFlingFromEditor(velocity: Float) {
        if (isUserTouching || isDraggingCanvas) return
        if (scrollD <= 0) return
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        val vy = -abs(velocity).toInt()
        lastScrollerY = scrollD
        scroller.fling(0, scrollD, 0, vy, 0, 0, 0, headerHeight, 0, 0)
        postInvalidateOnAnimation()
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
    fun predictivelyGlideCursorAboveKeyboard(
        targetLine: Int? = null,
        targetCol: Int? = null,
        isUnresolvedTap: Boolean = false
    ) {
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

        val currentScroll = (scrollD + editor.offsetY).toFloat()

        if (isUnresolvedTap) {
            beginKeyboardTransaction(
                targetScroll = currentScroll,
                targetLine = null,
                targetCol = null,
                initialScreenY = null,
                isUnresolvedTap = true
            )
            targetPredictedScrollTotal = currentScroll
            onKeyboardEntranceStarted?.invoke()
            return
        }

        val density = resources.displayMetrics.density
        val cursor = try { editor.cursor } catch (_: Throwable) { null }
        val line = targetLine ?: cursor?.leftLine ?: return
        val col = targetCol ?: cursor?.leftColumn ?: 0
        val layout = try { editor.layout } catch (_: Throwable) { null } ?: return
        val geom = getCharGeometry(layout, line, col, editor.rowHeight.toFloat())

        val selGeom = if (cursor?.isSelected == true) getSelectionGeometry(cursor, layout) else null
        val yOffset = selGeom?.bottomDocY ?: geom.bottomDocY
        val yOffsetTop = selGeom?.topDocY ?: geom.topDocY

        val headerRemaining = headerHeight - scrollD
        val screenBottom = headerRemaining + yOffset - editor.offsetY
        val screenTop = headerRemaining + yOffsetTop - editor.offsetY

        val futureViewportHeight = if (isKeyboardOrDrawerActive && height > 0) {
            height.toFloat()
        } else if (targetEditorViewportHeightPx != null && targetEditorViewportHeightPx!! > 0f) {
            targetEditorViewportHeightPx!!
        } else {
            val availableH = height.toFloat()
            val maxObstruction = if (availableH > 0f) minOf(estimatedKeyboardHeightPx, availableH * 0.55f) else estimatedKeyboardHeightPx
            (availableH - maxObstruction).coerceAtLeast(editor.rowHeight * 3f)
        }

        val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
        val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
        val targetLineBottom = futureViewportHeight - marginPx

        // Authoritative future max canvas scroll matching post-resize onSizeChanged exactly
        val maxScrollFuture = ScribeScrollPolicy.calculateMaxCanvasScroll(
            headerHeight = headerHeight,
            layoutHeight = layout.layoutHeight,
            viewportHeight = futureViewportHeight.toInt(),
            bottomSpace = marginPx,
            isTextEmpty = editor.text.isEmpty()
        ).toFloat()

        // Two-sided safety check in predicted future viewport
        val now = System.currentTimeMillis()
        val recentTouchY = if (now - lastTouchDownTime < 500L) lastTouchDownY else null

        val isCaretSafeInFuture = screenTop >= -deadbandPx && screenBottom <= (targetLineBottom + deadbandPx)
        val isTitleSafe = (scrollD == 0 && recentTouchY != null && recentTouchY <= futureViewportHeight && recentTouchY >= 0f)
        if (isCaretSafeInFuture || isTitleSafe) {
            // Tapped / current position is already safe in predicted final viewport!
            // Suppress glide completely — zero document movement!
            beginKeyboardTransaction(
                targetScroll = currentScroll,
                targetLine = line,
                targetCol = col,
                initialScreenY = screenTop
            )
            targetPredictedScrollTotal = currentScroll
            onKeyboardEntranceStarted?.invoke()
            return
        }

        val overflow = if (screenBottom > targetLineBottom + deadbandPx) {
            screenBottom - targetLineBottom
        } else if (screenTop < -deadbandPx) {
            screenTop
        } else {
            0f
        }
        val desiredTarget = (currentScroll + overflow).coerceIn(0f, maxScrollFuture)
        val deltaY = kotlin.math.abs(desiredTarget - currentScroll)

        // Initiate keyboard transaction and notify Compose to reserve shortcut bar geometry
        beginKeyboardTransaction(
            targetScroll = desiredTarget,
            targetLine = line,
            targetCol = col,
            initialScreenY = screenTop
        )
        onKeyboardEntranceStarted?.invoke()

        if (deltaY > ScribeScrollPolicy.DRAG_THRESHOLD_DP) {
            isExpansionTransitionActive = true
            resetExpansionTransitionRunnable?.let { removeCallbacks(it) }
            val rReset = Runnable {
                isExpansionTransitionActive = false
                checkKeyboardTransactionSettled()
            }
            resetExpansionTransitionRunnable = rReset
            postDelayed(rReset, 600L)

            isPredictiveGlideRunning = true
            targetPredictedScrollTotal = desiredTarget
            startAuthoritativeScrollAnimation(
                targetTotalScroll = desiredTarget,
                reason = ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE,
                durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS
            ) {
                isPredictiveGlideRunning = false
                targetPredictedScrollTotal = -1f
                checkKeyboardTransactionSettled()
            }
        } else {
            // Already safe, no scroll needed; settle transaction once keyboard finishes opening
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
        origin: ScribeScrollPolicy.CursorCorrectionOrigin = ScribeScrollPolicy.CursorCorrectionOrigin.PROGRAMMATIC_NAVIGATION
    ) {
        if (activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
            activeEditingTarget == EditingTarget.SECONDARY_TITLE
        ) {
            return
        }
        if ((!editor.isFocused && !isBottomSurfaceActive) || isKeyboardDismissing || suppressCursorVisibilityScroll || isRestoringViewport) return
        if (!force && editor.isPasteTransactionActive) return
        val now = System.currentTimeMillis()
        if (!force && now - lastEnsureCursorTime < 16L) return

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
        val selGeom = if (cursor.isSelected) getSelectionGeometry(cursor, layout) else null
        val yOffset = selGeom?.bottomDocY ?: (layoutOffset?.let { getCharGeometry(layout, line, col, rowHeight.toFloat()).bottomDocY } ?: run {
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
        })
        val yOffsetBottom = yOffset
        val yOffsetTop = selGeom?.topDocY ?: (layoutOffset?.let { getCharGeometry(layout, line, col, rowHeight.toFloat()).topDocY } ?: (yOffsetBottom - rowHeight))
        val currentScroll = (scrollD + editor.offsetY).toFloat()

        // If a keyboard transaction or predictive glide is currently in flight:
        if (isKeyboardTransactionActive || (isPredictiveGlideRunning && targetPredictedScrollTotal > 0f)) {
            val density = resources.displayMetrics.density
            val marginPx = (ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density).roundToInt()
            val deadbandPx = (ScribeScrollPolicy.CURSOR_DEADBAND_DP * density).roundToInt()
            val targetViewportH = if (isKeyboardVisible || isBottomSurfaceActive) {
                height.toFloat()
            } else if (targetEditorViewportHeightPx != null && targetEditorViewportHeightPx!! > 0f) {
                targetEditorViewportHeightPx!!
            } else {
                val availableH = height.toFloat()
                val maxObstruction = if (availableH > 0f) minOf(estimatedKeyboardHeightPx, availableH * 0.55f) else estimatedKeyboardHeightPx
                (availableH - maxObstruction).coerceAtLeast(editor.rowHeight * 3f)
            }
            val targetLineBottom = (targetViewportH - marginPx).toFloat()
            val plannedTarget = if (targetPredictedScrollTotal > 0f) {
                targetPredictedScrollTotal
            } else if (activeKeyboardTransaction?.targetScroll ?: -1f >= 0f) {
                activeKeyboardTransaction!!.targetScroll
            } else {
                currentScroll
            }
            val finalScreenTop = (headerHeight + yOffsetTop - plannedTarget)
            val finalScreenBottom = (headerHeight + yOffsetBottom - plannedTarget)
            if (finalScreenTop >= -deadbandPx && finalScreenBottom <= targetLineBottom + deadbandPx) {
                // Caret is already safe against the predicted viewport — apply NO correction!
                return
            }

            // Caret is unsafe at top or bottom against predicted viewport: apply minimum correction
            val correction = if (finalScreenBottom > targetLineBottom + deadbandPx) {
                finalScreenBottom - targetLineBottom
            } else if (finalScreenTop < -deadbandPx) {
                finalScreenTop
            } else {
                0f
            }
            val maxBound = maxCanvasScroll.toFloat()
            val correctedTarget = (plannedTarget + correction).coerceIn(0f, maxBound)
            if (kotlin.math.abs(correctedTarget - currentScroll) > 1f) {
                activeKeyboardTransaction = activeKeyboardTransaction?.copy(targetScroll = correctedTarget)
                targetPredictedScrollTotal = correctedTarget
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = correctedTarget,
                    reason = ScribeScrollPolicy.ScrollAnimationReason.PREDICTIVE_GLIDE,
                    durationMs = ScribeScrollPolicy.DURATION_PREDICTIVE_GLIDE_MS
                ) {
                    isPredictiveGlideRunning = false
                    targetPredictedScrollTotal = -1f
                    checkKeyboardTransactionSettled()
                }
            }
            // Strict Invariant: NEVER fall through to generic resolveCursorSafety while keyboard transaction is active!
            return
        }

        val decision = ScribeScrollPolicy.resolveCursorSafety(
            rectTop = yOffsetTop,
            rectBottom = yOffsetBottom,
            headerHeight = headerHeight.toFloat(),
            currentTotalScroll = currentScroll,
            usableViewportHeight = height.toFloat(),
            density = resources.displayMetrics.density,
            maxCanvasScroll = maxCanvasScroll.toFloat(),
            origin = origin
        )

        if (!decision.isSafe) {
            lastEnsureCursorTime = now
            val duration = customDurationMs
            if (decision.neededUpwardDisplacement > 0f && isExpansionTransitionActive) {
                recordExpansionDisplacement(decision.neededUpwardDisplacement, line, col, cursor.isSelected)
            }
            if (smooth) {
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = decision.targetTotalScroll,
                    reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
                    durationMs = duration,
                    interpolator = FastOutSlowInInterpolator
                )
            } else {
                val beforeScroll = (scrollD + editor.offsetY).toFloat()
                scrollCanvasBy(decision.neededUpwardDisplacement)
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

        // Maintain continuous canvas invariant on resume/relayout:
        if (headerHeight > 0 && editor.isWordwrapReady() && editor.layout != null) {
            val reconciled = ScribeScrollPolicy.reconcileScrollState(
                headerHeight = headerHeight,
                scrollD = scrollD,
                editorOffsetY = editor.offsetY,
                maxCanvasScroll = maxCanvasScroll
            )
            scrollD = reconciled.scrollD
            scrollDFloat = reconciled.scrollD.toFloat()
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

        // Only enforce viewport height invariant when layout & wordwrap are fully ready
        if (editor.text.isEmpty()) {
            if (editor.offsetY > 0) {
                try {
                    editor.jumpScrollTo(editor.offsetX, 0)
                } catch (_: Throwable) {}
            }
            if (scrollD > 0) {
                scrollD = 0
                scrollDFloat = 0f
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                notifyViewportScrollChanged()
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
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
                notifyViewportScrollChanged()
            }
            if (reconciled.editorOffsetY != editor.offsetY) {
                try {
                    editor.jumpScrollTo(editor.offsetX, reconciled.editorOffsetY)
                    notifyViewportScrollChanged()
                } catch (_: Throwable) {}
            }
        }
        applyTranslations()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            cancelViewportSafetyAnimation()
            if (isKeyboardTransactionActive && !isImePhysicallySettled) {
                cancelKeyboardTransaction("Window focus lost during unconfirmed transaction")
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topEdgeEffect?.setSize(w, h)
        if (oldw > 0 && kotlin.math.abs(w - oldw) > 2) {
            post { if (isAttachedToWindow) updatePersistentBottomClearance() }
        }
        if (oldh > 0 && kotlin.math.abs(h - oldh) > 2) {
            val dh = oldh - h
            val currentObstruction = (liveImeHeightPx + liveDrawerHeightPx + (if (isKeyboardVisible || isBottomSurfaceActive) shortcutBarHeightPx else 0f)).coerceAtLeast(0f)
            val isScribeOwnedBottomSurface = hasWindowFocus() && (
                isKeyboardTransactionActive ||
                isBottomSurfaceActive ||
                (editor.isFocused && (isKeyboardVisible || liveImeHeightPx > 0f))
            )

            if (dh > 0) {
                if (isScribeOwnedBottomSurface) {
                    // Viewport shrinking (Scribe keyboard appearing or accessory drawer opening)
                    previousBottomObstructionPx = currentObstruction
                    isExpansionTransitionActive = true
                    isKeyboardDismissing = false

                    val curC = try { editor.cursor } catch (_: Throwable) { null }
                    if (curC != null) {
                        expansionCaretLine = curC.leftLine
                        expansionCaretCol = curC.leftColumn
                        expansionHadSelection = curC.isSelected
                    }

                    if (isKeyboardTransactionActive) {
                        hasShrunkDuringTransaction = true
                        if (isImePhysicallySettled) {
                            val updated = transactionCoordinator.onLayoutMeasurementSettled()
                            activeKeyboardTransaction = updated
                            if (updated?.isFullySettled == true) {
                                reconcileActiveTransactionSettledViewport()
                            }
                            checkKeyboardTransactionSettled()
                        }
                    } else {
                        val isTitleTargetActive = activeEditingTarget == EditingTarget.PRIMARY_TITLE ||
                            activeEditingTarget == EditingTarget.SECONDARY_TITLE
                        if (!isTitleTargetActive && (editor.isFocused || isBottomSurfaceActive || isKeyboardVisible)) {
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
                                    val caretDocTop = selGeom?.topDocY ?: run {
                                        val targetLine = if (isAllSelected) cur.rightLine else cur.leftLine
                                        val targetCol = if (isAllSelected) cur.rightColumn else cur.leftColumn
                                        getCharGeometry(lay, targetLine, targetCol, editor.rowHeight.toFloat()).topDocY
                                    }
                                    val caretDocBottom = selGeom?.bottomDocY ?: run {
                                        val targetLine = if (isAllSelected) cur.rightLine else cur.leftLine
                                        val targetCol = if (isAllSelected) cur.rightColumn else cur.leftColumn
                                        getCharGeometry(lay, targetLine, targetCol, editor.rowHeight.toFloat()).bottomDocY
                                    }

                                    val currentScroll = (scrollD + editor.offsetY).toFloat()
                                    val decision = ScribeScrollPolicy.resolveCursorSafety(
                                        rectTop = caretDocTop,
                                        rectBottom = caretDocBottom,
                                        headerHeight = headerHeight.toFloat(),
                                        currentTotalScroll = currentScroll,
                                        usableViewportHeight = h.toFloat(),
                                        density = resources.displayMetrics.density,
                                        maxCanvasScroll = maxCanvasScroll.toFloat(),
                                        allowDownwardForTopUnderflow = false
                                    )

                                    if (!decision.isSafe && decision.neededUpwardDisplacement > (ScribeScrollPolicy.CURSOR_DEADBAND_DP * resources.displayMetrics.density)) {
                                        startAuthoritativeScrollAnimation(
                                            targetTotalScroll = decision.targetTotalScroll,
                                            reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
                                            durationMs = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS
                                        )
                                    }
                                    // Strict Invariant: If isCurrentlySafe, do ABSOLUTELY NOTHING. Safe cursor never moves!
                                }
                            }
                        }
                    }
                } else {
                    // Ordinary window resize OR foreign split-screen IME resize:
                    // MUST NOT trigger expansion transition, caret-safety scroll, or predictive animations.
                    // Preserve the document's total scroll position; safely clamp to new max if content overflows.
                    val currentScroll = (scrollD + editor.offsetY)
                    val maxScroll = maxCanvasScroll
                    if (currentScroll > maxScroll) {
                        clampCanvasScrollToMax(smooth = false)
                    }
                }
            } else if (dh < 0) {
                // Viewport expanding (accessory drawer contracting, emoji keyboard closing, or keyboard dismissing)
                isExpansionTransitionActive = false
                resetExpansionTransitionRunnable?.let { removeCallbacks(it) }

                val density = resources.displayMetrics.density
                val curObstruction = currentObstruction
                val wasLargerObstructionActive = previousBottomObstructionPx > (curObstruction + 2f)
                val isConfirmedContraction = wasLargerObstructionActive && (isBottomSurfaceActive || isKeyboardVisible) && hasWindowFocus()
                val hasRecordedDisplacement = temporaryExpansionDisplacement > 0f
                val isTransactionPermitted = !isKeyboardDismissing && !isKeyboardTransactionActive

                // Strict Invariant: Downward restoration is ONLY permitted for a confirmed contraction
                // of an active bottom surface where real temporary upward displacement was recorded!
                // Generic viewport expansion, keyboard dismissal, and last-line state NEVER independently authorize restoration.
                if (!isTransactionPermitted || !isConfirmedContraction || !hasRecordedDisplacement) {
                    if (isKeyboardDismissing || (!isKeyboardVisible && !isBottomSurfaceActive)) {
                        // Pure Writer Stationary Invariant:
                        // When keyboard is dismissed or closing, the document MUST remain
                        // 100% completely stationary at the saved margin.
                        if (preservedDismissalScroll == null) {
                            preservedDismissalScroll = scrollD + editor.offsetY
                        }
                    }
                    cancelRestoration()
                    cancelViewportSafetyAnimation()
                    invalidateCompensation("Not a confirmed contraction with recorded displacement")
                    previousBottomObstructionPx = curObstruction
                    return
                }

                // Downward Following Invariant:
                // Only confirmed contraction of an active bottom surface with recorded temporary displacement
                pendingRestorationRunnable?.let { removeCallbacks(it) }
                val rRestoration = Runnable {
                    pendingRestorationRunnable = null
                    val cur = try { editor.cursor } catch (_: Throwable) { null }
                    val lay = editor.layout ?: return@Runnable
                    val geom = getSelectionGeometry(cur, lay) ?: return@Runnable
                    val caretDocBottom = geom.bottomDocY
                    val currentScroll = (scrollD + editor.offsetY).toFloat()
                    val lineCount = try { editor.text.lineCount } catch (_: Throwable) { 1 }
                    val isAtLastLine = try {
                        (cur != null && cur.leftLine >= (lineCount - 1)) || (lineCount <= 1)
                    } catch (_: Throwable) { false }
                    val req = ScribeScrollPolicy.ContractionRestorationRequest(
                        caretScreenBottom = headerHeight + caretDocBottom - currentScroll,
                        caretScreenTop = headerHeight + geom.topDocY - currentScroll,
                        viewportHeight = h,
                        density = density,
                        totalScroll = currentScroll,
                        temporaryDisplacement = temporaryExpansionDisplacement,
                        isAtLastLine = isAtLastLine,
                        isKeyboardDismissing = isKeyboardDismissing,
                        isBottomSurfaceActive = isBottomSurfaceActive || isKeyboardVisible
                    )
                    val result = ScribeScrollPolicy.resolveContractionRestorationDelta(req)
                    if (result.shouldScroll && result.deltaY != 0f) {
                        val target = (currentScroll + result.deltaY).coerceIn(0f, maxCanvasScroll.toFloat())
                        startOrRetargetContractionRestoration(target)
                        temporaryExpansionDisplacement = result.remainingCompensation
                    }
                }
                pendingRestorationRunnable = rRestoration
                post(rRestoration)
                previousBottomObstructionPx = curObstruction
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
        if (isKeyboardDismissing || suppressCursorVisibilityScroll || isRestoringViewport) {
            return true
        }
        if (child !== editor) {
            if (scrollD > 0) {
                if (immediate) {
                    scrollDFloat = 0f
                    scrollD = 0
                    applyTranslations()
                    onUnifiedScrollChanged?.invoke(0, headerHeight)
                } else {
                    if (!scroller.isFinished) {
                        scroller.abortAnimation()
                    }
                    lastScrollerY = scrollD
                    scroller.startScroll(0, scrollD, 0, -scrollD, 250)
                    postInvalidateOnAnimation()
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
            if (isDeletionTransactionActive) {
                return true
            }
            if (isKeyboardTransactionActive) {
                return true
            }

            val currentScroll = (scrollD + editor.offsetY).toFloat()
            val decision = ScribeScrollPolicy.resolveCursorSafety(
                rectTop = rectangle.top.toFloat(),
                rectBottom = rectangle.bottom.toFloat(),
                headerHeight = headerHeight.toFloat(),
                currentTotalScroll = currentScroll,
                usableViewportHeight = height.toFloat(),
                density = resources.displayMetrics.density,
                maxCanvasScroll = maxCanvasScroll.toFloat(),
                allowDownwardForTopUnderflow = true
            )

            if (decision.isSafe) {
                // Invariant: Already safe in viewport — zero movement!
                return true
            }

            if (!immediate) {
                startAuthoritativeScrollAnimation(
                    targetTotalScroll = decision.targetTotalScroll,
                    reason = ScribeScrollPolicy.ScrollAnimationReason.CARET_VISIBILITY,
                    durationMs = ScribeScrollPolicy.DURATION_ENSURE_CURSOR_MS
                )
            } else {
                scrollCanvasBy(decision.neededUpwardDisplacement)
            }
            return true
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
        onScrollDelta?.invoke(dy)

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

        // 2. Authoritative 1-DOF scroll allocation via ScribeScrollPolicy
        val allocation = ScribeScrollPolicy.allocateScrollDelta(
            deltaY = dy,
            headerHeight = headerHeight,
            scrollD = scrollDFloat,
            editorOffsetY = editor.offsetY,
            maxCanvasScroll = maxCanvasScroll
        )

        if (dy > 0f) {
            // Forward scroll: outer canvas moves towards headerHeight first
            if (allocation.outerDelta != 0f) {
                scrollDFloat += allocation.outerDelta
                val maxD = minOf(headerHeight, maxCanvasScroll)
                scrollD = scrollDFloat.roundToInt().coerceIn(0, maxD)
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
            if (allocation.innerDelta != 0f) {
                dispatchScrollToEditor(allocation.innerDelta)
            }
        } else {
            // Reverse scroll: inner editor returns towards 0 FIRST, then outer canvas pulls header down
            if (allocation.innerDelta != 0f) {
                dispatchScrollToEditor(allocation.innerDelta)
            }
            if (allocation.outerDelta != 0f) {
                scrollDFloat += allocation.outerDelta
                val maxD = minOf(headerHeight, maxCanvasScroll)
                scrollD = scrollDFloat.roundToInt().coerceIn(0, maxD)
                applyTranslations()
                onUnifiedScrollChanged?.invoke(scrollD, headerHeight)
            }
        }

        if (allocation.outerDelta != 0f || allocation.innerDelta != 0f) {
            if (isUserTouching && isDraggingCanvas && !isTouchOnHandleActive && !isAnyHandleActive) {
                if (abs(allocation.outerDelta) > 1f || abs(allocation.innerDelta) > 1f) {
                    preservedDismissalScroll = null
                }
            }
            notifyViewportScrollChanged()
        }

        // 3. Top edge stretch when overscrolling at the top
        if (dy < 0f && allocation.unconsumedDelta < 0f && scrollD <= 0 && editor.offsetY <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                val deltaDistance = -allocation.unconsumedDelta / height.coerceAtLeast(1)
                val displacement = (lastTouchX / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                EdgeEffectCompat.onPullDistance(topEdgeEffect, deltaDistance, displacement)
                postInvalidateOnAnimation()
            }
        }

        return allocation.unconsumedDelta
    }

    /**
     * Natively scrolls Sora CodeEditor via its internal EditorScroller.
     * This avoids calling View.scrollBy() which desynchronizes the View hardware canvas,
     * ensuring lines render smoothly at 120Hz without blank screens during fast scrolling.
     */
    private fun dispatchScrollToEditor(dy: Float) {
        // Safe dispatch: allocateScrollDelta is the authoritative arbiter of innerDelta
        try {
            val scroller = editor.scroller ?: return
            val currY = editor.offsetY
            val currX = editor.offsetX
            val targetY = (currY + dy).roundToInt()
            val maxAllowedY = editor.safeScrollMaxY
            val clampedTargetY = targetY.coerceIn(0, maxAllowedY)
            if (clampedTargetY != currY) {
                scroller.forceFinished(true)
                scroller.startScroll(currX, currY, 0, clampedTargetY - currY, 0)
                scroller.abortAnimation()
                try {
                    editor.renderContext.invalidateRenderNodes()
                } catch (_: Throwable) {}
                editor.invalidate()
                notifyViewportScrollChanged()
            }
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouchLatched = false
                isUserTouching = true
                lastTouchDownY = ev.y
                lastTouchDownTime = System.currentTimeMillis()
                onUserTouchDown?.invoke()
                cancelCanvasAnimation()
                invalidateCompensation()
                editor.stopFling()
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - editor.left
                val editorY = ev.y - (headerHeight - scrollD)
                isTouchOnHandleActive = editor.isHandleDragging || editor.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouchLatched = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    isMultiTouchLatched = true
                }
                if (!isTouchOnHandleActive && editor.isHandleDragging) {
                    isTouchOnHandleActive = true
                }
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y

                if (isDraggingCanvas) {
                    scrollCanvasBy(dy)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isMultiTouchLatched = false
                isTouchOnHandleActive = false
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
                val wasDragging = isDraggingCanvas
                if (wasDragging && ev.actionMasked == MotionEvent.ACTION_UP) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (abs(vy) > minFlingVelocity) {
                        val scrollerVy = -vy.toInt()
                        lastScrollerY = scrollD + editor.offsetY
                        val maxScroll = maxCanvasScroll
                        if (maxScroll > 0) {
                            scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                        }
                        postInvalidateOnAnimation()
                    }
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                if (wasDragging) {
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isAnyHandleActive || isMultiTouchActive || ev.pointerCount > 1) return false
        if (isDraggingCanvas) return true

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouchLatched = false
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isDraggingCanvas = false
                val editorX = ev.x - editor.left
                val editorY = ev.y - (headerHeight - scrollD)
                isTouchOnHandleActive = editor.isHandleDragging || editor.isTouchOnHandle(editorX, editorY)
                velocityTracker?.clear() ?: run { velocityTracker = VelocityTracker.obtain() }
                velocityTracker?.addMovement(ev)
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouchLatched = true
                isDraggingCanvas = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAnyHandleActive || isMultiTouchActive || ev.pointerCount > 1) return false
                velocityTracker?.addMovement(ev)
                val totalDx = ev.x - initialDownX
                val totalDy = initialDownY - ev.y

                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                    // Central touch interception invariant:
                    // All vertical drag gestures across the document (header, editor body, and trailing space)
                    // are owned by UnifiedCanvasLayout to provide one uninterrupted, continuous scroll gesture.
                    isDraggingCanvas = true
                    lastTouchX = ev.x
                    lastTouchY = ev.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingCanvas = false
                isTouchOnHandleActive = false
                isMultiTouchLatched = false
            }
        }
        return isDraggingCanvas
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                editor.stopFling()
                lastTouchX = ev.x
                lastTouchY = ev.y
                initialDownY = ev.y
                initialDownX = ev.x
                isDraggingCanvas = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                scrollCanvasBy(dy)
                return true
            }

            MotionEvent.ACTION_UP -> {
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
                velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val vy = velocityTracker?.yVelocity ?: 0f
                if (abs(vy) > minFlingVelocity) {
                    val scrollerVy = -vy.toInt()
                    lastScrollerY = scrollD + editor.offsetY
                    val maxScroll = maxCanvasScroll
                    if (maxScroll > 0) {
                        scroller.fling(0, lastScrollerY, 0, scrollerVy, 0, 0, 0, maxScroll, 0, 0)
                    }
                    postInvalidateOnAnimation()
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null && !topEdgeEffect.isFinished) {
                    topEdgeEffect.onRelease()
                    postInvalidateOnAnimation()
                }
                isDraggingCanvas = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val currY = scroller.currY
            val dy = (currY - lastScrollerY).toFloat()
            lastScrollerY = currY
            scrollCanvasBy(dy)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topEdgeEffect != null) {
                if ((scroller.isOverScrolled() || currY <= 0) && scrollD <= 0 && editor.offsetY <= 0 && topEdgeEffect.isFinished) {
                    val velocity = scroller.currVelocity.toInt()
                    if (velocity > 0) {
                        topEdgeEffect.onAbsorb(velocity)
                    }
                }
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelKeyboardTransaction("UnifiedCanvas detached from window")
        cancelCanvasAnimation()
        resetDeletionActiveRunnable?.let { removeCallbacks(it) }
        resetDeletionActiveRunnable = null
    }
}
