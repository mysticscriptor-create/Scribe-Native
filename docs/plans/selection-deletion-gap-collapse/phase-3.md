# Phase 3 — State Machine, Atomic Cleanup, Literal Sole Coordinator Ownership & Physical Scroll Sync

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Plan Guardrails & Non-Negotiable Architecture Rules` (Guardrails 2 & 3)
- `Consolidated File Ownership Map`
- `Phase 3 — State Machine, Atomic Cleanup, Literal Sole Coordinator Ownership & Physical Scroll Sync` (`3.1`–`3.4`)
- `Testing Strategy & Extended Validation Matrix` (`B`, `D`)

---

## Scope

Implement `ScribeGapCollapseController` (5-phase state machine, transaction generation/epoch tokens, closed-form Mode-2 `Choreographer` loop, observed physical-velocity tracker, pointer freeze/resumption, atomic cancellation/final-safety claims, active-seam baseline, `PendingPostSafetyKeyboardEval`), refactor `ScribeScrollCoordinator` and `ScribeCanvasGlideAnimator` (`ScribeScrollAnimation.kt`) so **`ScribeScrollCoordinator` is the literal sole caller and owner of `finishAnimation()`** via `applyStepWithResult()`, implement `cancelPreservingTransaction()`, separate `stopLegacyAnimators` vs `claimAndClearLiveDeletionTransaction` vs `commitGapCollapseIdle`, wire `dispatchTouchEvent` `try/finally` + `PhysicalScrollOrigin` tagging, and validate with **Suites B1 & B2**.

## Prerequisites & Dependencies

- **Hard Prerequisite ([Phase 0](./phase-0.md#validation--phase-completion-gates-05)):** Phase 0 Steps **`0.1`**, **`0.3`**, and **`0.4`** MUST be completed before modifying Phase 3 files. *(Phase `0.2` is NOT required for Phase 3.)*
- **Upstream Dependencies:**
  - [Phase 1](./phase-1.md): `ScribeGapCollapsePolicy`, `resolveSafetyFloorAndBounds`, `[safeMinPhysical, safeMax]`, observed physical read-back handoff velocity formulas, closed-form Mode-2 equation.
  - [Phase 2](./phase-2.md): `canStillSettleGeneration`, `tryClaimPendingDeletionSettlement`, active-seam baseline initialization.
- **Downstream Consumer ([Phase 4](./phase-4.md)):** `ScribeEditorRenderer` consumes `controller.shouldApplySplitRender`, `tauLower`, `activeSeamLine`, `activeSeamFirstLowerRow`, and Option-B `handleCursorAnimatorActivatedDuringCollapse()`.

## Explicit Non-Goals

- Do **not** implement `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor` in Phase 3 (deferred to [Phase 4](./phase-4.md), gated by Phase `0.2`).
- Do **not** implement touch `untranslateYOffset`, mid-collapse incremental insertion rebasing, or height-only resize serialization logic that belongs to [Phase 5](./phase-5.md) (though `PendingPostSafetyKeyboardEval` data model and `beginTeardownLockAndConsumeEval` slot live on `ScribeGapCollapseController` here).

## Shared Architecture Contracts Referenced

- **Guardrail 2 (Dedicated Controller, Sealed Event B, Idempotent Dual `ACTION_DOWN`):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Guardrail 3 (Literal Sole Coordinator `finishAnimation()` Ownership, `applyStepWithResult()`, Sub-1px/Coalescing, Physical Velocity Read-Back, `PhysicalScrollOrigin`):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Guardrail 1 (Synchronous `editor.invalidate()` on Mode-2 ticks):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)

---

## File Ownership in Phase 3

### New File
- `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseController.kt`:  
  Symbols: `ScribeGapCollapseController`, `GapCollapsePhase`, `Mode2VisualState`, `SafetyRevalidationResult`, `PendingPostSafetyKeyboardEval`, `PhysicalScrollOrigin`.

### Modified Files (Phase 3 Scope)
1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollCoordinator.kt`: **Sole owner of `finishAnimation()` & completion detection**; adds `StepTerminationReason`, `ScrollStepResult`, `applyStepWithResult(newAnimatedScroll, operationId, isSpringSettled)`, idle-only `syncActualScroll(actualTotalScroll)`; documents all 3 meanings of `requestTarget()` returning `false`; preserves `applyStep(Float, Long?)` compatibility.
2. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollAnimation.kt`: Removes **all** `finishAnimation()` calls (`107`, `167`); implements inactive sub-1px no-op vs `>= 1f` glide start, active same-target coalescing vs retargeting, `advanceFrame()` completion lifecycle via `ScrollStepResult.isOperationFinished`, rare created-op `< 1f` route via `applyStepWithResult(..., isSpringSettled = true)`, `onFrameCommitted` Event-A callback, and `cancelPreservingTransaction(reason)`.
3. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollInputController.kt`: Emits `PhysicalScrollOrigin` scope tags (`USER_CANVAS_DRAG`, `USER_CANVAS_FLING`) around manual drag `scrollCanvasBy` and `OverScroller` fling/`computeScroll()`.
4. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`: Hosts `ScribeGapCollapseController`; implements `stopLegacyAnimators`, `claimAndClearLiveDeletionTransaction`, `commitGapCollapseIdle`, atomic `finalizeCompletedGapCollapseTransaction` & `finalizeUnresolvedGapCollapseTransaction`, `cancelGapCollapseAndDeletionTransaction`, `PhysicalScrollOrigin` tracking, `notifyViewportScrollChanged()` idle `syncActualScroll`, `dispatchTouchEvent` `try/finally`, and synchronous `editor.invalidate()` on Mode-2 / outer-only frames.
5. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`: `ScrollEvent.cause` + `handleAutoScrollRunnable` `PhysicalScrollOrigin` tagging (`USER_EDITOR_DRAG_OR_FLING`, `USER_HANDLE_AUTOSCROLL`, contrasted against `CAUSE_MAKE_POSITION_VISIBLE` / `EXTERNAL_PROGRAMMATIC`).

### Test Files
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt` (**Suite B1**)
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollCoordinatorTest.kt` & `ScribeScrollAnimationTest.kt` (**Suite B2**)

---

## Implementation Steps & Exact Specifications

### 3.1 `GapCollapsePhase`, `Mode2VisualState` & Sealed Event-B Completion (`ScribeGapCollapseController`) (`[Proposed]`)

```kotlin
enum class GapCollapsePhase {
    IDLE,
    MODE_1_PHYSICAL_COUPLED,
    MODE_2_VISUAL_SETTLING,
    POINTER_FROZEN,
    FINAL_SAFETY_PENDING
}

enum class PhysicalScrollOrigin {
    NONE,
    USER_CANVAS_DRAG,
    USER_CANVAS_FLING,
    USER_EDITOR_DRAG_OR_FLING,
    USER_HANDLE_AUTOSCROLL,
    GAP_COLLAPSE_MODE_1,
    EVENT_B_SAFETY_CORRECTION,
    EXTERNAL_PROGRAMMATIC
}

data class Mode2VisualState(
    val tauAtResumePx: Float,
    val elapsedTimeSec: Float = 0f,
    val progressVelocity: Float,  // Clamped in [0f, omega]
    val omega: Float = 13.5f,
    val lastFrameTimeNs: Long = 0L // 0L sentinel -> dt = 1f / refreshHz on frame 1
)

sealed interface SafetyRevalidationResult {
    data object VALIDATED_SAFE : SafetyRevalidationResult
    data object CORRECTION_STARTED : SafetyRevalidationResult
    data object CORRECTED_AND_VALIDATED : SafetyRevalidationResult
    data class DEFERRED_UNTIL_LAYOUT_READY(val reason: String) : SafetyRevalidationResult
    data class UNRESOLVED(val reason: String) : SafetyRevalidationResult
    data object STALE_TRANSACTION : SafetyRevalidationResult
}

data class PendingPostSafetyKeyboardEval(
    val owningGen: Long,
    val expectedSafetyEpoch: Long,
    val targetHeightPx: Int,
    val isHeightShrink: Boolean,
    val isAuthorizedTransition: Boolean,
    val shouldFollowExpansion: Boolean,
    val isKeyboardDismissing: Boolean,
    val isDestinationSurfaceActive: Boolean,
    val dockTransition: ScribeDockTransition
)
```

- Mode-2 `Choreographer` loop integrates `elapsedTimeSec` via the exact closed-form critically damped solution defined in [Phase 1 (`1.5`)](./phase-1.md#15-derived-scenario-mode-1-tau_textlowert-physical-velocity-read-back--exact-closed-form-mode-2-derived--proposed), synchronously calls `editor.invalidate()` on every tick changing `tauLower` (including settle-to-`0f`), and transitions `FINAL_SAFETY_PENDING` $\to$ Event B when `nextTau <= 0.5f`.

### 3.2 Atomic Event-B Finalization, Post-`IDLE` Dual-`ACTION_DOWN` Safety & `cancelPreservingTransaction` (`[Source-verified]` / `[Proposed]`)

**Why `ScribeCanvasGlideAnimator.cancel()` Must Not Be Used Preserving a Transaction (`[Source-verified]` `ScribeScrollAnimation.kt:185-202`):**
Baseline `cancel()` copies `val onEnd = activeGlideOnEnd`, nulls `activeGlideOnEnd`, calls `scrollCoordinator.cancelAnimation()`, and **still invokes `onEnd?.invoke()`** + `onCancelledCallback`. Add **`fun cancelPreservingTransaction(reason: String? = null)`** on `ScribeCanvasGlideAnimator` that sets `activeGlideOnEnd = null` *before* local capture and skips both `onEnd` and `onCancelledCallback`.

**Separated Cancellation Responsibilities (Solving Post-`IDLE` Dual `ACTION_DOWN`):**
Split cleanup into three distinct primitives on `UnifiedCanvasLayout`:
1. **`stopLegacyAnimators(preserveRetractionDebt: Boolean)`:** Cancels `canvasAnimator` (safe for non-deletion animators), `cancelRestoration()`, resets `outerScrollResidualPx = 0f` / `editorScrollResidualPx = 0f` / `scrollDFloat = scrollD.toFloat()`, ends keyboard transaction, stops input/editor flings. **Does NOT touch `activeDeletionSnapshot` or advance `deletionTransactionGeneration`.**
2. **`claimAndClearLiveDeletionTransaction(preserveRetractionDebt: Boolean): Boolean`:** Checks `val hasLiveTx = isDeletionTransactionActive || hasPendingDeletionSettlement || isDeletionTailAnchored || isDeletionResetRetractionActive`. If **`!hasLiveTx`**, returns **`false` immediately without clearing anything or incrementing `deletionTransactionGeneration++`!** If `hasLiveTx`, removes listeners, clears snapshot & settlement flags, captures retraction debt if requested, advances **`deletionTransactionGeneration++` exactly once**, and returns `true`.
3. **`commitGapCollapseIdle()`:** Transitions controller to `IDLE` (`tauLower = 0f`, `owningDeletionGen = -1L`, active seam cleared) and schedules post-draw `EditorTextActionWindow` refresh gate (implemented in [Phase 4 (`4.4`)](./phase-4.md#44-scribecodeeditor-row-override-reset--deferred-editortextactionwindow-re-enable-timing-source-verified--proposed)).

**Dual `ACTION_DOWN` Trace (`UnifiedCanvasLayout.onUserTouchDown` $\to$ `ScribeCodeEditor.onTouchEvent(ACTION_DOWN)`):**
- On `ACTION_DOWN` #1 (during `MODE_1`, `MODE_2`, or `FINAL_SAFETY_PENDING`): `tryClaimCancellation(G)` succeeds $\to$ locks controller, cancels `SAFE_BOUNDARY_CORRECTION`/Mode-1 via `cancelPreservingTransaction`, stops Choreographer, calls `claimAndClearLiveDeletionTransaction` (`true`, bumps `G -> G + 1` once), calls `commitGapCollapseIdle()` (`IDLE`), stops legacy animators.
- On `ACTION_DOWN` #2 arriving *after* `IDLE` is committed: enters legacy cancellation branch $\to$ calls `stopLegacyAnimators` (safe no-op or stops unrelated legacy glide) and calls `claimAndClearLiveDeletionTransaction` $\to$ sees `hasLiveTx == false` $\to$ **returns `false` (zero second clear, zero second `deletionTransactionGeneration++`, zero old `onEnd`)!**

**Atomic Teardown-Before-`IDLE` for Event B (`finalizeCompletedGapCollapseTransaction` & `finalizeUnresolvedGapCollapseTransaction`):**
1. Validate `(capturedGen, capturedCollapseEpoch)` and call `controller.beginTeardownLockAndConsumeEval(capturedGen, capturedCollapseEpoch)` (prevents re-entry & returns `pendingEval`).
2. Clean Mode-2 Choreographer, safety-retry listeners, `cancelPreservingTransaction`, and `claimAndClearLiveDeletionTransaction(false)` (advances `G -> G + 1` once).
3. **ONLY THEN** call `commitGapCollapseIdle()` (`IDLE` is never exposed with live transaction state!).
4. Synchronously call `editor.invalidate()` and finally invoke `dispatchPendingPostSafetyKeyboardEval(pendingEval)` if non-null.

### 3.3 Literal Sole Coordinator Ownership of `finishAnimation()`, `applyStepWithResult()` Lifecycle & Callback Coalescing (`[Source-verified]` / `[Proposed]`)

**Audit of All `finishAnimation()` Call Sites in Baseline (`[Source-verified]`):**
- `ScribeScrollAnimation.kt:107` (`advanceFrame`) — **Removed.**
- **`ScribeScrollAnimation.kt:167` (`startAuthoritativeAnimation` when `distance < 1f`) — Removed!**
- `ScribeScrollCoordinator.kt:181` (`applyStep`) & `187` (`fun finishAnimation`) — **Retained as sole owner.**

**1. Coordinator Result & Completion Contract (`ScribeScrollCoordinator.applyStepWithResult`):**
`ScrollStepResult` MUST expose at least:
- `operationId: Long`
- `actualTotalScroll: Float`
- `consumedDelta: Float`
- `boundaryHit: Boolean`
- `terminationReason: StepTerminationReason`
- `isOperationFinished: Boolean`

`applyStepWithResult(newAnimatedScroll: Float, operationId: Long? = null, isSpringSettled: Boolean = false): ScrollStepResult` MUST:
1. Reject stale operation IDs (`operationId != null && operationId != activeOperationId`, or `!isAnimationActive`) without finishing or mutating another operation (returning `isOperationFinished = false` with current `actualTotalScroll`).
2. Apply the physical scroll step and compute actual consumption/read-back (`actualTotalScroll`, `consumedDelta`, `boundaryHit`).
3. Determine completion solely inside the coordinator from the existing target-reached condition (`abs(totalScroll - target) < 0.5f`), boundary termination (`hitBoundary`), or `isSpringSettled`.
4. Call the coordinator's own `finishAnimation(operationId)` **exactly once** when the operation terminates.
5. Return `ScrollStepResult` identifying the actual `terminationReason` and `isOperationFinished`.

Do **not** delegate completion detection or `finishAnimation()` calls to `ScribeCanvasGlideAnimator`.

**2. Explicit `requestTarget()` Return Contract (`ScribeScrollCoordinator`):**
`requestTarget(...) == false` has two distinct coordinator-side meanings (plus `startAuthoritativeAnimation`'s active-glide coalescing branch below) and MUST NEVER be treated as a single ambiguous signal that the target was reached:
1. **Inactive sub-1px no-op (`!isAnimationActive && abs(clampedTarget - totalScroll) < 1f`):** Coordinator is idle and `totalScroll` is already within `< 1f` of `clampedTarget`; no active operation is created (`activeTargetTotalScroll = clampedTarget`).
2. **Same-reason active-target coalescing (`isAnimationActive && activeReason == reason && !forceRetarget && abs(currTarget - clampedTarget) <= 2f`):** Coordinator is **already executing an active animation** toward that target; no new operation is created and the active animation is still running (caller MUST NOT invoke `onEnd` immediately!).
3. **True (`shouldStart == true`):** A fresh `activeOperationId++` was created and `isAnimationActive = true`.

**3. Animator Callback Delivery After Normal Mode-1 Completion (`ScribeCanvasGlideAnimator.advanceFrame()`):**
`advanceFrame()` submits `requestedStep` (`glideTarget` if `isSettled && distToTarget < 1f`, else `glidePosition`) and `isSpringSettled` to `val result = scrollCoordinator.applyStepWithResult(requestedStep, opId, isSpringSettled)`.

When `result.operationId == opId && result.isOperationFinished`:
1. Set `isGlideRunning = false`.
2. Capture `activeGlideReason` and `activeGlideOnEnd` into local variables.
3. Clear `activeGlideReason = null` and `activeGlideOnEnd = null`.
4. Invoke `onSettledCallback?.invoke(completedReason)` **exactly once**.
5. After that callback returns, invoke the captured `onEnd` **exactly once only if all three conditions remain true**:
   ```kotlin
   !isGlideRunning &&
   !scrollCoordinator.isAnimationActive &&
   opId == scrollCoordinator.activeOperationId
   ```
   *(Preserves existing behavior when `onSettledCallback` starts a replacement safety-correction animation: the old operation's `onEnd` must not prematurely start Mode 2 or another completion path.)*

If `result.isOperationFinished` is `false` (and `opId == scrollCoordinator.activeOperationId && scrollCoordinator.isAnimationActive`), continue scheduling the next animation frame normally (`postOnAnimationAction(glideFrameRunnable)`); if a stale/cancelled op ID was observed, stop the runnable (`isGlideRunning = false`) without firing `onSettledCallback` or `onEnd`. Never invoke `onSettledCallback` or `onEnd` a second time for the same operation if `applyStepWithResult()` already reported completion through a boundary or target-reached result.

**4. Unified `startAuthoritativeAnimation()` Sub-1px, Coalescing & Cancellation Semantics:**
In `ScribeCanvasGlideAnimator.startAuthoritativeAnimation(currentScroll, targetTotalScroll, reason, maxCanvasScroll, forceRetarget, onEnd)`, let `clampedTarget = targetTotalScroll.coerceIn(0f, maxCanvasScroll.toFloat())` and `physicalDistance = clampedTarget - currentScroll`:
1. **Active Glide Coalescing vs Retargeting (`if (isGlideRunning && scrollCoordinator.isAnimationActive)`):**
   - **Same-target coalescing (`!forceRetarget && abs(glideTarget - clampedTarget) <= 1f`):** Do **NOT** create a new op and do **NOT** invoke `onEnd` immediately. Chain `onEnd` after the existing `activeGlideOnEnd` and `return false`.
   - **Actual retarget (`forceRetarget || abs(glideTarget - clampedTarget) > 1f`):** Update `glideTarget = clampedTarget`, `activeGlideReason = reason`, preserve `glideVelocity`, chain `onEnd` after `activeGlideOnEnd`, call `scrollCoordinator.retargetActiveOperation(clampedTarget, reason, maxCanvasScroll)`, and `return true`.
2. **Fresh Inactive Sub-1px No-Op (`if (!scrollCoordinator.isAnimationActive && abs(physicalDistance) < 1f)`):**
   - Preserve the distinct immediate-completion path unchanged across `0 px`, `0.03 px`, `0.05 px`, `0.50 px`, and `0.999 px`:
     ```kotlin
     scrollCoordinator.syncActualScroll(currentScroll)
     onEnd?.invoke()
     return false
     ```
     This creates no operation, does **not** call `finishAnimation()`, and does **not** invoke the normal active-glide `onSettledCallback`.
   - When `abs(physicalDistance) >= 1f` (including **exact `1.00 px`** — MUST NOT use `<= 1f`), proceed directly to `scrollCoordinator.requestTarget(...)`.
3. **Coordinator `requestTarget(...)` & Rare Created-Op `< 1f` Discrepancy:**
   - If `requestTarget(...)` returns `false`: if `scrollCoordinator.isAnimationActive`, chain `onEnd` onto `activeGlideOnEnd` (never fire early!) and `return false`; otherwise (`!isAnimationActive`), call `scrollCoordinator.syncActualScroll(currentScroll)`, invoke `onEnd?.invoke()` once, and `return false`.
   - If `requestTarget(...)` returns `true` (`opId = scrollCoordinator.activeOperationId`) yet `abs(target - currentScroll) < 1f`:
     - Complete it through **`val result = scrollCoordinator.applyStepWithResult(target, opId, isSpringSettled = true)`** (never call `finishAnimation()` directly in the animator).
     - Deliver its `onEnd?.invoke()` **exactly once** after `result.isOperationFinished` confirms successful coordinator completion (`!scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId`), and `return false`.
4. **Cancellation Semantics:**
   - Standard `cancel()` and `cancelPreservingTransaction()` retain their separately specified cancellation semantics (`cancelPreservingTransaction()` discards `activeGlideOnEnd` and suppresses both `onEnd` and `onCancelledCallback`). Neither cancellation path delivers a normal successful-settlement `onSettledCallback`.
- **Zero `finishAnimation()` calls remain in `ScribeScrollAnimation.kt`.**
- Also wire `onFrameCommitted` Event-A callback on `ScribeScrollAnimation.kt` (`UnifiedCanvasLayout` updates Mode-1 $\tau_{\text{lower}}(t)$ and triggers synchronous `editor.invalidate()` on every committed physical frame, including outer-only `scrollD < headerHeight` frames).

### 3.4 Pointer Dispatch `try/finally`, Option-A Fling & `PhysicalScrollOrigin` Sync (`[Source-verified]` / `[Proposed]`)
- `UnifiedCanvasLayout.dispatchTouchEvent` wraps both `inputController.dispatchTouchEvent(ev)` short-circuit (`ScribeScrollInputController.kt:101, 130`) and `super.dispatchTouchEvent(ev)` in `try { ... } finally { if (ACTION_UP || ACTION_CANCEL) onPointerGestureEndedIfNeeded(ev) }`.
- Explicit `PhysicalScrollOrigin` scopes tag `USER_CANVAS_DRAG`, `USER_CANVAS_FLING`, `USER_EDITOR_DRAG_OR_FLING` (`ScrollEvent.CAUSE_USER_DRAG`/`CAUSE_USER_FLING`), and `USER_HANDLE_AUTOSCROLL` (contrasted against `CAUSE_MAKE_POSITION_VISIBLE` / `EXTERNAL_PROGRAMMATIC`).
- `notifyViewportScrollChanged()` continuously calls `scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())` whenever `!scrollCoordinator.isAnimationActive && !canvasAnimator.isGlideRunning`. During Mode 2, authorized origins update `lastMode2KnownPhysicalScroll`; `EXTERNAL_PROGRAMMATIC` drift $> 0.5\text{ px}$ aborts Mode 2 into Event B (`syncActualScroll` first).

---

## Validation & Acceptance Criteria (Suites B1 & B2)

Execute via the exact JVM unit-test Gradle task recorded in Phase `0.4`:

### Suite B1 (`ScribeGapCollapseControllerTest.kt`)
1. All 5 `GapCollapsePhase` transitions (`IDLE`, `MODE_1_PHYSICAL_COUPLED`, `MODE_2_VISUAL_SETTLING`, `POINTER_FROZEN`, `FINAL_SAFETY_PENDING`).
2. All 6 `SafetyRevalidationResult` variants (strictly checking integer `actualPhysicalScroll in safeMinPhysical..safeMax`, `effectiveCorrectiveTarget = clampPhysicalSafetyTarget(...)`, and `actualAfterCorrection`) + retry preservation.
3. Atomic teardown-before-`IDLE`; `cancelPreservingTransaction()`; `PendingPostSafetyKeyboardEval` across all 4 exits.
4. Observed physical read-back handoff velocity tests (normal settled Mode 1 & physically clamped boundary handoff, ignoring unconsumed spring velocity); `PhysicalScrollOrigin` vs `CAUSE_MAKE_POSITION_VISIBLE`.
5. Dual `ACTION_DOWN` after `IDLE` asserting snapshot clear & generation bump occur **exactly once**.
6. Closed-form Mode-2 `frameTimeNanos` `0L` clock handling.
*(Note: Suite B1 cases exercising Option-B mid-collapse cursor-animator abort and incremental seam / internal mutations complete once Phases 4 & 5 are wired.)*

### Suite B2 (`ScribeScrollCoordinatorTest.kt`, `ScribeScrollAnimationTest.kt`)
1. Literal zero-other-caller `finishAnimation()` verification (`ScribeScrollAnimation` has 0 calls), plus all 3 meanings of `requestTarget() == false`.
2. Normal Mode-1 completion invokes `onSettledCallback` once and then `onEnd` once.
3. Target-reached and boundary-hit completion both deliver the same callback lifecycle exactly once (never double-invoked).
4. A settled callback (`onSettledCallback`) starting a replacement operation prevents the previous operation's `onEnd` from firing.
5. A stale operation result cannot finish, mutate, or invoke callbacks for a newer operation.
6. `cancelPreservingTransaction()` suppresses both `onEnd` and `onSettledCallback` / `onCancelledCallback`.
7. `0 px`, `0.03 px`, `0.05 px`, `0.50 px`, and `0.999 px` fresh inactive sub-1px requests invoke `syncActualScroll` and `onEnd` only (no active op, no `finishAnimation()`, no `onSettledCallback`, Mode 2 reads back actual physical scroll and starts with `progressVelocity = 0f`).
8. `1.00 px` inactive (`abs(physicalDistance) == 1.0f`): MUST NOT take sub-1px short-circuit; creates Mode-1 op via `requestTarget()`, runs glide, settles once via `applyStepWithResult(..., isSpringSettled = true)`.
9. Active same-target coalescing (`isGlideRunning && isAnimationActive && !forceRetarget && abs(currTarget - clampedTarget) <= 1f`): returns `false` without creating new op, does NOT fire `onEnd` prematurely, chains `onEnd` onto `activeGlideOnEnd` after active op finishes.
10. Active retarget (`forceRetarget || abs(currTarget - clampedTarget) > 1f`): calls `retargetActiveOperation`, preserves velocity, chains `onEnd`, settles once via `applyStepWithResult(..., isSpringSettled = true)`.
11. Scenario D zero/sub-1px/exact-1px/normal Mode-1 spans and synchronous safety snap handoff produce finite `progressVelocity in 0f..omega2`.

## Phase-Completion Conditions

- Zero calls to `finishAnimation()` exist anywhere in `ScribeScrollAnimation.kt` or anywhere outside `ScribeScrollCoordinator.kt`.
- Dual `ACTION_DOWN` and Event-B teardown invariants hold with exact single-increment `deletionTransactionGeneration++`.
- Suites B1 (Phase 2–3 cases) and B2 pass using the Phase `0.4` Gradle task.
