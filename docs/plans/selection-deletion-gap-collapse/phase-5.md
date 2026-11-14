# Phase 5 — Touch Mapping, Incremental Insertion Rebasing, Internal Mutation Audit & Serialized Resize

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Plan Guardrails & Non-Negotiable Architecture Rules` (Guardrail 4)
- `Consolidated File Ownership Map`
- `Phase 5 — Touch Mapping, Incremental Insertion Rebasing, Internal Mutation Audit & Serialized Resize` (`5.1`–`5.5`)
- `Testing Strategy & Extended Validation Matrix` (`B1`, `D`, `E`, `F`)

---

## Scope

Implement touch/handle Y untranslate mapping (`untranslateYOffset`, `isTouchOnHandle`), incremental active-seam baseline rebasing (`ScribeGapCollapsePolicy` seam mapping + `ScribeCodeEditor` `beforeModification` / `afterInsert`), Policy A / Policy B internal content mutation routing across `ScribeCodeEditor` and `MainEditorScreen`, the complete 8-row mid-collapse operation matrix, width/detach cleanup, serialized height-only `onSizeChanged` with `PendingPostSafetyKeyboardEval`, `scrollDFloat` invariant separation (replacing `scrollCoordinator.totalScroll = currentScroll` at `UnifiedCanvasLayout.kt:858`), and full V1 integration + regression validation (**Suites B1 mutation/seam cases, D, E, F**).

## Prerequisites & Dependencies

- **Upstream Dependencies:** Phases **0**, **1**, **2**, **3**, and **4** must be implemented so `ScribeGapCollapseController`, `ScribeGapCollapsePolicy`, `ScribeOverlayBoundsRegistry`, `ScribeEditorRenderer`, `ScribeScrollCoordinator`, `ScribeCanvasGlideAnimator`, and `UnifiedCanvasLayout` / `ScribeCodeEditor` hooks exist.

## Explicit Non-Goals

- Do **not** implement V2 deferred features listed in [Phase 6](./phase-6.md).
- Do **not** couple `scrollDFloat` to `S_actual` or mutate `scrollCoordinator.totalScroll` directly outside `ScribeScrollCoordinator`.

## Shared Architecture Contracts Referenced

- **Guardrail 4 (Serialized Height Resize & Integer Safety Floor):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Guardrail 2 & 3 (Atomic Cleanup, `syncActualScroll`, `Content.replace()` Lifecycle):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)

---

## File Ownership in Phase 5

1. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`: `untranslateYOffset` for `getPointPosition` / `getPointVisualPosition` & handle hit-testing (`isTouchOnHandle`); `beforeModification` / `afterInsert` incremental seam rebase; Policy A / Policy B internal mutation routing; post-fallback `ensureSelectionVisible()` + `clampCanvasScrollToMax(smooth = true)`; detach cleanup.
2. `app/src/main/java/com/primaloptima/scribe/ui/screens/MainEditorScreen.kt`: Wires Policy-B pre-mutation cancel on `applySmartPrefix` / selection `applyFormat`.
3. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`: Width change / `onDetachedFromWindow()` cleanup; serialized height-only `onSizeChanged` $\to$ `FINAL_SAFETY_PENDING` $\to$ Event B $\to$ `PendingPostSafetyKeyboardEval`; `scrollDFloat = scrollD.toFloat() + outerScrollResidualPx` invariant & replacing `scrollCoordinator.totalScroll = currentScroll` (`858`) with idle-only `scrollCoordinator.syncActualScroll(currentScroll)`.
4. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicy.kt`: Pure `SeamMappingResult` incremental seam mapping helpers (if not already populated in Phase 1).

### Test Files
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt` (Suite B1 incremental seam + internal mutation cases)
- `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseIntegrationTest.kt` (**Suites D & E**)
- Existing `*Test.kt` (**Suite F**)

---

## Implementation Steps & Exact Specifications

### 5.1 Touch & Handle Coordinate Proof (`[Source-verified]` / `[Proposed]`)
- `untranslateYOffset(yOffset)` subtracts `tauLower` at most once for `yOffset >= seamDocY + tauLower`, clamping `[seamDocY, seamDocY + tauLower)` to `seamDocY` inside `getPointPosition` / `getPointVisualPosition`.
- `isTouchOnHandle` adds `+controller.getTranslationForPosition(line, col)`.

### 5.2 Incremental Active-Seam Baseline & Exact `(seamLine, 0)` Rebase Rules (`[Source-verified]` / `[Proposed]`)
1. Before content edit (`beforeModification`) while `controller.isResumableCollapse`: capture `(preEditSeamLine = controller.activeSeamLine, preEditSeamDocY = controller.activeSeamDocY, preEditTau = controller.tauLower)`.
2. After `super.afterInsert` (`isWordwrapReady() && !isLayoutBusyState`, else Row-4 fallback):
   - Map `(preEditSeamLine, 0)` post-insert (`newSeamLine`, `newSeamCol`), measure `postSeamDocY`, compute $\Delta Y_{\text{insert}} = \text{postSeamDocY} - \text{preEditSeamDocY}$:
     - `-0.5f <= deltaYInsert <= 0.5f && newSeamCol == 0`: Preserve phase & `tauLower = preEditTau`, update active-seam baseline.
     - `0.5f < deltaYInsert <= preEditTau && newSeamCol == 0`: Rebase `residual = (preEditTau - deltaYInsert).coerceAtLeast(0f)` and update baseline (`collapseAnimEpoch++`, `G` preserved).
     - `deltaYInsert > preEditTau`, `< -0.5f`, `newSeamCol != 0`, or async layout: Row-4 `[Fallback]` (`cancelGapCollapseAndDeletionTransaction`, `syncActualScroll()`, then **explicitly call `ensureSelectionVisible()` + `clampCanvasScrollToMax(smooth = true)`**).

### 5.3 Internal Content Mutation Audit & V1 Policies (`[Source-verified]` / `[Proposed]`)
- **Policy A (Seam-Aware):** `enforceAutoIndentFloor()` (`isEnforcingIndentFloor = true`), `commitText()`, single-line `handleProseNewline()` (`text.insert`), `pasteText()`, single-line `applyFormat` (`insert`) / `applyLinePrefix` (`insert`).
- **Policy B (Pre-Mutation Cancel):** `applyFormattedTextSilently()` (`1040`), `updateImageDescription()` (`2088`), `removeImageTag()` (`2102`), `setText()` (`1269`), multi-line `applySmartPrefix` (`4377`), and selection `applyFormat` (`replace`).

### 5.4 Complete 8-Row V1 Mid-Collapse Operation Policy (`[Proposed]` / `[Fallback]`)
- **1a.** Insert strictly below active seam: Preserve phase, `tauLower`, `G`, `E`, baseline unchanged.
- **1b.** Insert at/above seam with `-0.5f <= deltaYInsert <= 0.5f`, `newSeamCol == 0`: Preserve phase, `tauLower`, `G`, `E`; update baseline.
- **2.** Small insert at/above seam (`0.5f < deltaYInsert <= preEditTau`, `newSeamCol == 0`): Rebase `tauLower = preEditTau - deltaYInsert` to Mode 2 (`or POINTER_FROZEN`).
- **3.** Small Undo / Redo-insert matching Row 2 geometry: Identical rebase.
- **4.** Large Undo/insert, `< -0.5f`, `newSeamCol != 0`, or async layout: `cancelGapCollapseAndDeletionTransaction` + `syncActualScroll` + `ensureSelectionVisible()` + `clampCanvasScrollToMax()`.
- **5.** Second selection deletion / Undo-delete: Interrupt active collapse, `syncActualScroll`, begin Deletion `#2` (`G + 1`) as `INELIGIBLE_FALLBACK`.
- **6.** Non-empty replacement & Policy-B batch mutations: Pre-mutation cancel + `releaseDeferralKeepingStartOrigin()`.
- **7.** Empty replacement (`replace(..., "")`): Interrupt active collapse; if idle, handled per [Phase 2 (`2.2` Sequence 2)](./phase-2.md#22-contentreplace-event-ordering--universal-settlement-claim-source-verified--proposed) (`afterDelete` skips collapse while `isInContentReplace == true`, `afterInsert` settles once via `tryClaimPendingDeletionSettlement`).

### 5.5 Deterministic Serialized Height-Resize & Async Continuation + `scrollDFloat` Invariants (`[Source-verified]` / `[Proposed]`)

1. **Width Change (`w != oldw`) / `onDetachedFromWindow()`:**
   Remove `pendingReplaceWatchdog` & `pendingTextActionRefreshRunnable`; clear overlay/text-action tokens; `cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = ...)`; `syncActualScroll((scrollD + editor.offsetY).toFloat())`.
2. **Height-Only Resize (`w == oldw && abs(h - oldh) > 2`) During `controller.isTransactionOwned == true`:**
   1. Stop Mode-2 Choreographer; `canvasAnimator.cancelPreservingTransaction("height_resize_serialize")`; `cancelRestoration()`; `tauLower = 0f` (`editor.invalidate()`).
   2. `transitionToFinalSafetyForFallback(G)` or `restartFinalSafetyClaim(G)` (`collapseAnimEpoch -> safetyEpoch`).
   3. Read `curActual = (scrollD + editor.offsetY).toFloat()`; `scrollCoordinator.syncActualScroll(curActual)`; recompute bounds at new `h`; store `PendingPostSafetyKeyboardEval(owningGen = G, expectedSafetyEpoch = safetyEpoch, targetHeightPx = h, ...)`.
   4. Execute Event B (`completeDeletionVisualTransaction(G, safetyEpoch)`).
   5. Consume `PendingPostSafetyKeyboardEval` **exclusively** inside `beginTeardownLockAndConsumeEval` within **`finalizeCompletedGapCollapseTransaction`** and **`finalizeUnresolvedGapCollapseTransaction`** across all 4 completion paths (`VALIDATED_SAFE`/`CORRECTED_AND_VALIDATED`, `CORRECTION_STARTED -> onEnd -> fresh epoch`, `DEFERRED_UNTIL_LAYOUT_READY -> retry`, and `UNRESOLVED`).
   6. Execute `dispatchPendingPostSafetyKeyboardEval(eval)` only after `IDLE` + `G -> G + 1`, guarded by `isAttachedToWindow && height == eval.targetHeightPx && phase == IDLE && !isTransactionOwned`.
3. **`scrollDFloat` Invariant (`UnifiedCanvasLayout.kt:179, 1250, 2151`):**
   Keep `scrollDFloat = scrollD.toFloat() + outerScrollResidualPx` strictly separate from `S_actual`; whenever residuals zero, set `scrollDFloat = scrollD.toFloat()` (never `S_actual`). Replace `scrollCoordinator.totalScroll = currentScroll` (`858`) with idle-only `scrollCoordinator.syncActualScroll(currentScroll)`.

---

## Validation & Acceptance Criteria (Suites B1 Mutation/Seam, D, E, F)

Execute using the exact JVM and instrumentation Gradle tasks recorded in Phase `0.4`:

1. **Suite B1 (`ScribeGapCollapseControllerTest.kt`):** Incremental seam + internal mutation tests.
2. **Suite D (`ScribeGapCollapseIntegrationTest.kt`):**
   - Pointer/fling tests (1)–(6) via `try/finally`.
   - `ScribeOverlayBoundsRegistry` removal on hide, composition exit, layout switch, key replacement, AND proof that an attached-but-hidden composable (`isVisible = false && coords.isAttached == true`) removes its key rather than re-registering stale bounds when `onGloballyPositioned` fires.
   - `EditorTextActionWindow` stays `isEnabled = false` through `IDLE` until unsplit `!cursorAnimator.isRunning()` `DrawCursorTask` completion, blocking stale `postInLifecycle(displayWindow)` calls.
   - Serialized height resize (`dh > 0`, `dh < 0`) + `scrollDFloat` invariant.
3. **Suite E (`ScribeGapCollapseIntegrationTest.kt`):**
   - Non-empty replacement while **not overscrolled**; non-empty replacement while **overscrolled** (`ClearTransactionAndClampOverscroll`); delayed `LayoutStateChangeEvent` following either replacement asserting zero intermediate or delayed gap collapse starts; empty replacement (`""`); Scenario E; Event-B verification comparing requested corrective targets against observed integer read-back `scrollD + editor.offsetY in safeMinPhysical..safeMax`.
   - Cursor animator: (1) idle at deletion start + Case-I lower-region insertion-handle placement, (2) already running at deletion start (`INELIGIBLE_FALLBACK`), (3) starting during Mode 1, (4) starting during Mode 2 (zero visual gap, transaction safely released before resuming unsplit rendering).
4. **Suite F (`*Test.kt`):** All existing regression test suites pass with zero regressions.

## Phase-Completion Conditions

- Touch untranslate & handle hit-testing work accurately across active seams.
- Incremental seam rebasing and all 8 mid-collapse operation rows + Policy A/B internal mutations behave per specification.
- Serialized height-only resize and `scrollDFloat` separation preserve `PendingPostSafetyKeyboardEval` invariants across all 4 Event-B exits.
- Full Validation Matrix **Suites A, B1, B2, C, D, E, and F** pass via the exact Phase `0.4` Gradle tasks.
