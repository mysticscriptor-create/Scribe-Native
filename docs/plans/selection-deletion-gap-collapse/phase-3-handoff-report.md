# Phase 3 Handoff Report — State Machine, Atomic Cleanup, Literal Sole Coordinator Ownership & Physical Scroll Sync

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`
**Branch / Final Phase 3 Target Commit:** `main` @ `dd5b2a2` (`dd5b2a2778671c6046e839a37de4a4c2a507da44`)
**Baseline Phase 3 Commit:** `af1c9031f2604320b93c15c77b8ee4563dda7e87`
**Final Successful CI Run:** [`43746877007`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43746877007) (`success` on `dd5b2a2778671c6046e839a37de4a4c2a507da44` — both `testDebugUnitTest` and `app:assembleDebug` passed)
**Phase 3 Status:** **COMPLETE** (verified by GitHub Actions CI run `43746877007` on `dd5b2a2`)
**Historical Failed CI Runs (Retained for Traceability):**
- [`43733167652`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43733167652) (`82c53c8` — `failure`: initial Phase 3 CI trigger over `af1c903` compilation errors; repaired in `1f541b3`)
- [`43734685686`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43734685686) (`1f541b3` — `failure`: compilation resolved, unit-test assertion failures fixed in `8141cdf`, followed by intermediate green run [`43737177313`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43737177313) before `0811553` expanded host pointer-freeze and scroll-origin coverage)
- [`43741475138`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43741475138) (`0811553` — `failure`: 6 failures in `:app:testDebugUnitTest` after wiring `POINTER_FROZEN` touch lifecycle, Mode-1 freeze cancellation, untagged scroll-origin detection, and Robolectric host integration tests)
- [`43743569614`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43743569614) (`aff5f1f` — `failure`: after enabling `unitTests.isIncludeAndroidResources = true`, `Theme.Scribe` fixture, and zero-tau pointer-freeze tests)
- [`43745540483`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43745540483) (`d7e4028` — `failure`) & [`43745696735`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43745696735) (`f44ca5c` — `failure`): diagnostic `Test` task `FULL` exception logging updates
- [`43746436183`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43746436183) (`e057275` — `failure`: after attaching the Robolectric integration host to a `ComponentActivity` window; uncovered the direct vertical `ScribeCodeEditor.scrollBy()` routing issue fixed in `dd5b2a2`)

**Upstream Phase 2 Commits:** `2417c64c82e32d49badfbeb4813381113c828241` (GitHub Actions `build`: `success`), `fcde60df123bfe5e056d9c90a0f29c6df8fdacea`
**Upstream Phase 0 Gate:** Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724) (`OPEN` for Phases 1–3 and Phase 4)
**Authoritative Spec:** [`phase-3.md`](./phase-3.md) & [`implementation_plan.md`](./implementation_plan.md)
**Master Plan SHA-256 (Frozen):** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`
**Phase 3 Spec SHA-256:** `cd582f182b24adb68129a701ffe797f312befb4c823a0cc2a6b1af72ff8a0b30`
**Next Phase Target:** [`phase-4.md`](./phase-4.md) — Single-Lifecycle `ScribeEditorRenderer`, Per-Layer Strategy, Option-B Cursor Animator & `EditorTextActionWindow` Refresh

> **Execution Scope Disclosure:** **No local Gradle builds, Kotlin/Java compilation, Android lint checks, or JVM/instrumentation tests were executed locally during Phase 3, its CI correction commits, or this documentation closeout.** Local verification consisted of static code/spec inspection, `finishAnimation(` call-site auditing in `app/src/main`, syntactic checks, `git diff --check`, and `implementation_plan.md` / `phase-3.md` SHA-256 verification. Authoritative build and JVM test validation (`./gradlew testDebugUnitTest app:assembleDebug`) ran in GitHub Actions run [`43746877007`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43746877007) on `dd5b2a2` and passed.

---

## 1. Executive Summary & Phase-3 Completion Status

| Phase 3 Deliverable | Status | Primary File(s) | Notes |
|---|---|---|---|
| `ScribeGapCollapseController` 5-phase state machine, epochs, `Mode2VisualState`, sealed `SafetyRevalidationResult`, `PendingPostSafetyKeyboardEval` | **Complete (CI Passed)** | `ScribeGapCollapseController.kt` | Pure JVM-testable constructor injection (`scheduleFrame`, `cancelFrame`, `refreshRateHzProvider`, `nanoTimeProvider`, callbacks). |
| Closed-form Mode-2 `Choreographer` loop (`0L` first-frame clock, monotonic clamping, synchronous `editor.invalidate()`) | **Complete (CI Passed)** | `ScribeGapCollapseController.kt`, `UnifiedCanvasLayout.kt` | Uses `ScribeGapCollapsePolicy.computeFrameDtSec` and `stepMode2ClosedForm`; disables `shouldApplySplitRender` immediately upon `FINAL_SAFETY_PENDING`. |
| Event-B sealed revalidation (`completeDeletionVisualTransaction`), single layout retry, `SAFE_BOUNDARY_CORRECTION`, atomic teardown-before-`IDLE` | **Complete (CI Passed)** | `UnifiedCanvasLayout.kt`, `ScribeGapCollapseController.kt` | Enforces integer `actualPhysicalScroll in safeMinPhysical..safeMax` and `PendingPostSafetyKeyboardEval` single consumption across all 4 exits. |
| Separated cleanup primitives, `POINTER_FROZEN` zero-tau resume & Dual `ACTION_DOWN` idempotency | **Complete (CI Passed)** | `UnifiedCanvasLayout.kt`, `ScribeGapCollapseController.kt` | `onPointerGestureEndedIfNeeded()` gates on `gapCollapseController.isPointerFrozen` so positive-tau resumes `MODE_2_VISUAL_SETTLING` and zero-tau transitions to `FINAL_SAFETY_PENDING` + invokes `completeDeletionVisualTransaction()`; second `ACTION_DOWN` after `IDLE` is a no-op (`hasLiveTx == false`). |
| `cancelPreservingTransaction()` without premature `onEnd` / `onCancelledCallback` | **Complete (CI Passed)** | `ScribeScrollAnimation.kt` | Clears `activeGlideOnEnd` and `activeGlideReason` prior to coordinator cancellation. |
| Literal sole `ScribeScrollCoordinator.finishAnimation()` ownership & `applyStepWithResult()` | **Complete (CI Passed)** | `ScribeScrollCoordinator.kt`, `ScribeScrollAnimation.kt` | Zero `finishAnimation()` calls in `ScribeScrollAnimation.kt` or anywhere outside `ScribeScrollCoordinator.kt`. |
| Unified `startAuthoritativeAnimation()` sub-1px (`< 1f`), exact `1.00 px`, active coalescing vs retarget, and `syncActualScroll()` | **Complete (CI Passed)** | `ScribeScrollAnimation.kt`, `ScribeScrollCoordinator.kt`, `UnifiedCanvasLayout.kt` | `scrollCoordinator.totalScroll = currentScroll` replaced with `scrollCoordinator.syncActualScroll(currentScroll)`. |
| Pointer `dispatchTouchEvent` `try/finally` + `PhysicalScrollOrigin` tagging & `ScribeCodeEditor.scrollBy()` `EditorScroller` routing | **Complete (CI Passed)** | `UnifiedCanvasLayout.kt`, `ScribeScrollInputController.kt`, `ScribeCodeEditor.kt` | `OverScroller.startScroll()` and untagged `scrollCanvasBy()` / `ScribeCodeEditor.scrollBy()` classify as `EXTERNAL_PROGRAMMATIC`; direct vertical `ScribeCodeEditor.scrollBy()` routes through Sora's `EditorScroller` (`forceFinished(true)`, `startScroll(..., 0)`, `abortAnimation()`, `renderContext.invalidateRenderNodes()`, `invalidate()`); drift `> 0.5f` during Mode 2 calls `syncActualScroll` and aborts Mode 2 into Event B. |
| Suites B1, B2 & Host Robolectric `ComponentActivity` Pointer/Origin Integration Tests | **Complete (CI Passed)** | `app/build.gradle.kts`, `UnifiedCanvasPointerAndOriginIntegrationTest.kt`, `ScribeGapCollapseControllerTest.kt`, `ScribeScrollCoordinatorTest.kt`, `ScribeScrollAnimationTest.kt` | Enabled `unitTests.isIncludeAndroidResources = true` and `FULL` test exception logging; attached `UnifiedCanvasLayout` to a `ComponentActivity` window (`Theme.Scribe`, `start().resume().visible()`, `@After` teardown); verified zero-tau Mode-1 progression and host-level `ACTION_UP` / `ACTION_CANCEL` coverage in run `43746877007`. |

---

## 2. File Inventory & Correction History (Cumulative `2417c64..dd5b2a2`)

### Production & Build Files
- **Created:**
  1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseController.kt` — 5-phase state machine, epochs, `Mode2VisualState`, `PendingPostSafetyKeyboardEval`, `PhysicalScrollOrigin`, `SafetyRevalidationResult`, pointer pause/resume, and `restartFinalSafetyClaim`.
- **Modified:**
  1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollCoordinator.kt` — Sole `finishAnimation()` ownership, `StepTerminationReason`, `ScrollStepResult`, `applyStepWithResult()`, and idle-only `syncActualScroll()`.
  2. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollAnimation.kt` — Removed all external `finishAnimation()` calls; sub-1px `< 1f` vs `>= 1f` contract; same-target `onEnd` coalescing; `onFrameCommitted`; `cancelPreservingTransaction()`.
  3. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollInputController.kt` — `PhysicalScrollOrigin` tagging (`USER_CANVAS_DRAG`, `USER_CANVAS_FLING`, `EXTERNAL_PROGRAMMATIC` discrimination on `OverScroller.startScroll()`).
  4. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt` — `gapCollapseController` wiring, `dispatchTouchEvent` `try/finally` (`POINTER_FROZEN` + `onPointerGestureEndedIfNeeded`), `PhysicalScrollOrigin` sync, Event-B `completeDeletionVisualTransaction` & common finalizers, `commitGapCollapseIdle()`, and `dispatchPendingPostSafetyKeyboardEval()`.
  5. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt` — `PhysicalScrollOrigin` tagging (`USER_EDITOR_DRAG_OR_FLING`, `USER_HANDLE_AUTOSCROLL`, untagged `EXTERNAL_PROGRAMMATIC`), scroll event/auto-scroll origin tracking, and final direct vertical `ScribeCodeEditor.scrollBy()` routing through Sora's `EditorScroller` (`activeScroller.forceFinished(true)`, `startScroll(currX, currY, dx, dy, 0)`, `abortAnimation()`, `renderContext.invalidateRenderNodes()`, `invalidate()`).
  6. `app/build.gradle.kts` — Enabled `unitTests.isIncludeAndroidResources = true` under `testOptions` for Robolectric merged resources and configured `tasks.withType<Test>()` with `TestExceptionFormat.FULL` (`showExceptions`, `showCauses`, `showStackTraces`).

### Test Files (Suites B1, B2 & Host Robolectric Integration)
- **Created:**
  1. `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt` (**Suite B1**, 10 `@Test` cases) — Covers all 5 phases, `0L` first-frame Mode-2 clock, observed physical velocity read-back on boundary clamp, `PhysicalScrollOrigin` discrimination (`USER_*` vs `CAUSE_MAKE_POSITION_VISIBLE` / `NONE` drift `> 0.5f`), all 6 Event-B `SafetyRevalidationResult` variants + atomic teardown-before-`IDLE`, dual `ACTION_DOWN` after `IDLE` idempotency, `cancelPreservingTransaction()`, Mode-1 pointer freeze without bumping `G`, and zero-tau Mode-1 pointer-freeze progression (`initialTauLowerPx = 100f` advancing past `750f` until `tauLower == 0f` naturally, then `ACTION_UP` -> `FINAL_SAFETY_PENDING` -> `IDLE`).
  2. `app/src/test/java/com/primaloptima/scribe/ui/components/UnifiedCanvasPointerAndOriginIntegrationTest.kt` (Host Robolectric integration suite, 8 `@Test` cases) — Uses `Robolectric.buildActivity(ComponentActivity::class.java)` with `R.style.Theme_Scribe`, `start().resume().visible()`, `qualifiers = "w800dp-h1200dp-mdpi"`, `@After` controller teardown, and attached `UnifiedCanvasLayout` via `activity.setContentView(canvas)`; verifies Mode-1 freeze/resume, dual `ACTION_DOWN` in `FINAL_SAFETY_PENDING`, untagged `scrollCanvasBy()` vs `USER_CANVAS_DRAG`, untagged direct/forwarded `ScribeCodeEditor.scrollBy()` aborting Mode 2, `ScribeScrollInputController` fling vs programmatic `startScroll`, full `DOWN -> MOVE -> UP` drag freeze/resume, and host-level zero-tau Mode-1 `ACTION_DOWN -> ACTION_UP` and `ACTION_DOWN -> ACTION_CANCEL` transitions to `FINAL_SAFETY_PENDING`.
- **Modified:**
  1. `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollCoordinatorTest.kt` (**Suite B2**, 13 `@Test` cases total) — Covers authoritative coordinator retargeting, coalescing, sub-pixel accumulation, boundary clamping, `requestTarget()` 3-meaning contract, and `applyStepWithResult()` sole completion ownership.
  2. `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollAnimationTest.kt` (**Suite B2**, 27 `@Test` cases total) — Covers physics/retraction invariants plus the 6 Suite B2 lifecycle cases (normal Mode-1 `onSettledCallback` -> `onEnd`, target-reached/boundary-hit single delivery, replacement-op `onEnd` suppression, inactive `< 1f` vs exact `1.00 px` Mode-1 creation, same-target `onEnd` coalescing & active retarget chaining, and Scenario D / synchronous-snap finite `progressVelocity` bounds).

### Chronological Phase 3 Commit & CI Correction History
1. `af1c903` — Baseline Phase 3 implementation (`ScribeGapCollapseController`, `ScribeScrollCoordinator`, `ScribeScrollAnimation`, `ScribeScrollInputController`, `UnifiedCanvasLayout`, `ScribeCodeEditor`, Suites B1/B2).
2. `28b193c` — Initial Phase 3 handoff report (`[skip ci]`).
3. `82c53c8` -> `1f541b3` -> `8141cdf` — Resolved Kotlin compilation errors and updated Event-B `UNRESOLVED` / coordinator assertions (green CI run `43737177313`).
4. `0811553` — Wired `POINTER_FROZEN` touch lifecycle, Mode-1 freeze cancellation, untagged scroll-origin detection, and added `UnifiedCanvasPointerAndOriginIntegrationTest.kt` (failed CI run `43741475138`).
5. `aff5f1f` — Enabled `unitTests.isIncludeAndroidResources = true`, applied `Theme.Scribe`, updated zero-tau Mode-1 test progression, and added host-level zero-tau `ACTION_UP` / `ACTION_CANCEL` tests (failed CI run `43743569614`).
6. `d7e4028` -> `f44ca5c` — Enabled `FULL` exception logging on Gradle `Test` tasks (failed diagnostic runs `43745540483`, `43745696735`).
7. `e057275` — Attached Robolectric integration host `UnifiedCanvasLayout` to a `ComponentActivity` window (`start().resume().visible()`, `setContentView(canvas)`, `@After` teardown) (failed CI run `43746436183`, revealing the direct vertical `ScribeCodeEditor.scrollBy()` routing issue).
8. `dd5b2a2` (`dd5b2a2778671c6046e839a37de4a4c2a507da44`) — Routed direct vertical `ScribeCodeEditor.scrollBy()` through Sora's `EditorScroller` (`forceFinished`, `startScroll(..., 0)`, `abortAnimation`, `invalidateRenderNodes`, `invalidate`). **Passed GitHub Actions CI run [`43746877007`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43746877007) (`testDebugUnitTest` + `app:assembleDebug`).**

### Unmodified Scope Constraints Verified
- `docs/plans/selection-deletion-gap-collapse/implementation_plan.md` SHA-256 unchanged (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`).
- `docs/plans/selection-deletion-gap-collapse/phase-3.md` SHA-256 unchanged (`cd582f182b24adb68129a701ffe797f312befb4c823a0cc2a6b1af72ff8a0b30`).
- Zero production source or test files modified during this closeout.
- Phase 4 (`ScribeEditorRenderer`, `ScribeDrawCursorTaskAccessor`, `EditorTextActionWindow` refresh gate) and Phase 5 (`untranslateYOffset`, incremental seam rebase, Policy A/B internal mutations, serialized `onSizeChanged` height resize) were NOT started early.

---

## 3. Handoff Contract & Dependencies for Phase 4

Starting commit for Phase 4: **`dd5b2a2778671c6046e839a37de4a4c2a507da44`** (`dd5b2a2`) + this `[skip ci]` closeout doc commit.

### 3.1 Phase 0 Gate Prerequisite Already Cleared for Phase 4
- Phase 0 Step `0.2` (GitHub Actions run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724)) already verified `editor-0.24.6.aar` `javap -p` signatures for `EditorRenderer` (9 protected methods / 11 signatures) and direct package-private access to `EditorRenderer$DrawCursorTask.y` from `io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor` (zero reflection, `setAccessible`, or `Unsafe`).

### 3.2 Consuming `ScribeGapCollapseController` in Phase 4 (`ScribeEditorRenderer`, `ScribeCursorAnimator`, `ScribeCodeEditor`)
- **`UnifiedCanvasLayout.gapCollapseController`** (public `val` on `UnifiedCanvasLayout`):
  - `shouldApplySplitRender`: `true` only during `MODE_1_PHYSICAL_COUPLED`, `MODE_2_VISUAL_SETTLING`, or `POINTER_FROZEN` when `!isTeardownLocked && activeSeamLine >= 0 && tauLower > 0.5f`. **Immediately `false` in `FINAL_SAFETY_PENDING` and `IDLE`.**
  - `tauLower: Float`, `activeSeamLine: Int`, `activeSeamFirstLowerRow: Int`, `activeSeamDocY: Float`, `rowHeightPx: Float`.
  - `getTranslationForPosition(line: Int, col: Int): Float` and `getTranslationForRow(rowIndex: Int): Float`.
- **Option-B Mid-Collapse Cursor Animator Integration (Phase 4.3):**
  - `ScribeGapCollapseController.restartFinalSafetyClaim(owningGen: Long): Long?` stops the Mode-2 `Choreographer`, sets `phase = FINAL_SAFETY_PENDING`, clears `tauLower = 0f`, increments `collapseAnimEpoch` & `safetyEpoch`, invokes `onInvalidateEditor()`, and returns the fresh `safetyEpoch` (or `null` if stale/locked).
  - `UnifiedCanvasLayout.completeDeletionVisualTransaction(capturedGen: Long, capturedSafetyEpoch: Long)` is `internal fun` and owns all 6 `SafetyRevalidationResult` paths plus `finalizeCompletedGapCollapseTransaction` / `finalizeUnresolvedGapCollapseTransaction` teardown-before-`IDLE`.
- **`EditorTextActionWindow` Deferred Refresh Hook (Phase 4.4):**
  - `UnifiedCanvasLayout.commitGapCollapseIdle()` currently resets `gapCollapseController.commitIdle()`, `clearActiveSeamBaseline()`, and `resolvedSeamGeometry = null`. Phase 4 should arm its post-draw `EditorTextActionWindow` refresh sequence gate directly inside `commitGapCollapseIdle()` (and disarm on cancel/detach/new deletion).

### 3.3 Deferred Phase 5 Hooks Already Hosted on `ScribeGapCollapseController` / `UnifiedCanvasLayout`
- `ScribeGapCollapseController` hosts `PendingPostSafetyKeyboardEval`, `storePendingPostSafetyKeyboardEval(eval)`, `transitionToFinalSafetyForFallback(owningGen)`, `restartFinalSafetyClaim(owningGen)`, and `beginTeardownLockAndConsumeEval(...)` (atomic single consumption across all 4 Event-B exits).
- `UnifiedCanvasLayout.dispatchPendingPostSafetyKeyboardEval(eval)` enforces the `isAttachedToWindow && height == eval.targetHeightPx && phase == IDLE && !isTransactionOwned` post-`IDLE` guard for Phase 5 extension.
- `UnifiedCanvasLayout.onPointerGestureEndedIfNeeded(ev)` runs inside `dispatchTouchEvent`'s `try/finally` on `ACTION_UP` / `ACTION_CANCEL` and resumes `POINTER_FROZEN` (including zero-tau freezes) via `resumeFromPointerFrozen(...)`, invoking `completeDeletionVisualTransaction(...)` whenever `FINAL_SAFETY_PENDING` is returned.

---

## 4. Final Phase 3 Verification Summary

- **Authoritative CI Validation (Passed):**
  - GitHub Actions run [`43746877007`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43746877007) on commit `dd5b2a2778671c6046e839a37de4a4c2a507da44` executed `./gradlew testDebugUnitTest app:assembleDebug --parallel --build-cache --configuration-cache` and **succeeded**.
  - Both `testDebugUnitTest` (covering Suites A, B1, B2, host Robolectric `UnifiedCanvasPointerAndOriginIntegrationTest`, and existing unit regression suites) and `app:assembleDebug` passed.
- **Static & Integrity Verification (Passed):**
  - `finishAnimation(` audit in `app/src/main` -> strictly confined to `ScribeScrollCoordinator.kt` (0 external callers).
  - `implementation_plan.md` SHA-256 verified unchanged (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`).
  - `phase-3.md` SHA-256 verified unchanged (`cd582f182b24adb68129a701ffe797f312befb4c823a0cc2a6b1af72ff8a0b30`).
- **Remaining Phase 4/5 Instrumentation Scope:**
  - Android instrumentation Suites C (`ScribeGapCollapseRendererTest.kt`) and D/E (`ScribeGapCollapseIntegrationTest.kt`) are scheduled under Phases 4 and 5 via `:app:connectedDebugAndroidTest`.
