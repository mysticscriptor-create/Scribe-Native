# Phase 2 Handoff Report — Pre-Delete Geometry, `Content.replace()` Deferral & Universal Settlement Claim

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`  
**Branch / Code Commit:** `main` @ `2417c64c82e32d49badfbeb4813381113c828241`  
**Upstream Phase 1 Commits:** `b7ed51d300138ebcc735aaf57a3f259816eec2f0`, `cd4bff4f43d690dbe25792f289b46218f16ddb5f`  
**Upstream Phase 0 Gate:** Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724) (`OPEN` for Phases 1–3 and Phase 4)  
**Authoritative Spec:** [`phase-2.md`](./phase-2.md) & [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan SHA-256 (Frozen):** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Next Phase Target:** [`phase-3.md`](./phase-3.md) — `ScribeGapCollapseController`, `ScribeScrollCoordinator` sole `finishAnimation()` ownership & physical scroll sync

> **Execution Scope Disclosure:** **No Gradle builds, Kotlin/Java compilation, Android lint checks, or JVM/instrumentation tests were executed locally during Phase 2 implementation.** Verification during Phase 2 consisted of static code/spec inspection, syntactic brace/parenthesis balance checks, `git diff --check`, and `implementation_plan.md` SHA-256 verification. All added and updated tests in `ScribeGapCollapsePolicyTest.kt` and `ScribeScrollDeletionPolicyTest.kt` are **unexecuted locally** and require `:app:testDebugUnitTest` (or GitHub Actions CI on commit `2417c64`) for runtime compilation and test execution verification.

---

## 1. Executive Summary & Phase-2 Completion Status

Phase 2 implemented the pre-delete surviving-content seam geometry extraction, `Content.replace()` deletion/insertion deferral and explicit overscroll clamping action, universal generation-scoped settlement claiming across all settlement entry points, and `pendingReplaceWatchdog` lifecycle tracking—strictly respecting Phase 2 boundaries (zero Phase 3–5 controller, coordinator, renderer, touch-untranslation, or resize implementations added).

| Phase 2 Deliverable | Status | Primary File(s) | Notes |
|---|---|---|---|
| Case I / Case II pre- & post-delete geometry + RTL seam check | **Implemented** | `ScribeGapCollapsePolicy.kt`, `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt` | V1 `C1 > 0` Case-I fallback preserved; zero partial-line horizontal splitting. |
| Active-seam baseline initialization (`activeSeamLine`, `activeSeamFirstLowerRow`, `activeSeamDocY`) | **Implemented** | `UnifiedCanvasLayout.kt` | Initialized in `initializeActiveSeamBaselineIfEligible()` on ready split start; cleared in `claimAndClearLiveDeletionTransaction()`. |
| `AfterInsertAction.ClearTransactionAndClampOverscroll` | **Implemented** | `ScribeScrollDeletionPolicy.kt`, `ScribeCodeEditor.kt` | Replaces `AfterInsertAction.None` for overscrolled non-empty replacements. |
| `!isInContentReplace` guard across `afterDelete`, `ContentChangeEvent`, `LayoutStateChangeEvent` | **Implemented** | `ScribeCodeEditor.kt` | Prevents intermediate `Content.replace()` delete settlement or empty-doc reset. |
| Universal `canStillSettleGeneration(expectedGen)` & atomic `tryClaimPendingDeletionSettlement(expectedGen)` | **Implemented** | `UnifiedCanvasLayout.kt`, `ScribeCodeEditor.kt` | Never consumes pending claim when layout or wordwrap is unready. |
| `claimAndClearLiveDeletionTransaction(preserveRetractionDebt)` | **Implemented** | `UnifiedCanvasLayout.kt`, `ScribeCodeEditor.kt` | Idempotent no-op returning `false` when no live transaction exists; bumps `deletionTransactionGeneration++` once otherwise. |
| `pendingReplaceWatchdog: Runnable?` tracking & detach cleanup | **Implemented** | `ScribeCodeEditor.kt` | Armed in `beforeReplace()`; cancelled in `afterInsert()` and `onDetachedFromWindow()`. |
| Phase 2 Unit Tests (Suite A Case 5 + Case I/II geometry + Coordinator tests) | **Added/Updated (Unexecuted locally)** | `ScribeGapCollapsePolicyTest.kt`, `ScribeScrollDeletionPolicyTest.kt` | Suite B1 (`ScribeGapCollapseControllerTest.kt`) and Suite E (`androidTest`) remain owned by Phases 3 & 5. |

---

## 2. Modified Files Inventory (Commit `2417c64`)

Diff summary from `2417c64c82e32d49badfbeb4813381113c828241` (`6 files changed, 795 insertions(+), 54 deletions(-)`):

1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicy.kt` (`+248`)
2. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollDeletionPolicy.kt` (`+12 / -3`)
3. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt` (`+166 / -23`)
4. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt` (`+167 / -22`)
5. `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicyTest.kt` (`+190 / -2`)
6. `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollDeletionPolicyTest.kt` (`+12 / -4`)

Unmodified constraints confirmed:
- `docs/plans/selection-deletion-gap-collapse/implementation_plan.md` SHA-256 unchanged (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`).
- Phase 3 (`ScribeGapCollapseController`, `ScribeScrollCoordinator`, `ScribeScrollAnimation`), Phase 4 (`ScribeEditorRenderer`, `ScribeDrawCursorTaskAccessor`), and Phase 5 files were not modified.

---

## 3. Verified Implementation Details & Symbol Map

### 3.1 Pure Policy & Seam Geometry (`ScribeGapCollapsePolicy.kt`)
- **Types (`lines 79–118`):**
  - `DeletionSeamCase` (`NONE`, `CASE_I_MID_LINE_OR_WHOLE_LINE`, `CASE_II_LINE_END`)
  - `PreDeleteSeamCapture`: stores `(l1, c1, l2, c2)`, `preLineCount`, `preLineLenL2`, `preScrollY`, `seamCase`, `isMergedPartialRowSeam`, `hasSurvivingUpper`, `hasSurvivingLower`, `preFirstLowerRowTopDocY`, `preSelectionTopDocY`, `preSelectionBottomDocY`, `hasRtlOrBidiAtSeam`.
  - `ResolvedSeamGeometry`: stores `isEligibleCase`, `seamLine`, `seamCol`, `seamFirstLowerRow`, `activeSeamDocY`, `preFirstLowerRowTopDocY`, `deltaGapPx`, and fallback flags.
- **Functions (`lines 543–747`):**
  - `containsRtlOrBidi(text: CharSequence): Boolean` — scans boundary lines using `Character.getDirectionality(...)` for RTL/Arabic/Bidi embedding/isolate/override directionality characters.
  - `classifyPreDeleteSeam(...)` — implements pre-delete Section `2.1`:
    - `hasSurvivingUpper = (l1 > 0 || c1 > 0)`
    - **Case I (`c2 < preLineLenL2`):** `c1 > 0` sets `isMergedPartialRowSeam = true` (V1 fallback); `c1 == 0` captures `preFirstLowerRowTopDocY = rowIndexAt(l2, c2) * rowHeightPx`.
    - **Case II (`c2 >= preLineLenL2`):** `l2 + 1 >= preLineCount` sets `hasSurvivingLower = false` (V1 fallback); else captures `preFirstLowerRowTopDocY = rowIndexAt(l2 + 1, 0) * rowHeightPx`.
  - `resolvePostDeleteSeamGeometry(...)` — maps Case I to `(seamLine = postStartLine, seamCol = 0)` and Case II to `(seamLine = postStartLine + 1, seamCol = 0)`, resolves `seamFirstLowerRow = postRowIndexAt(seamLine, 0)`, `activeSeamDocY = seamFirstLowerRow * rowHeightPx`, and `deltaGapPx = preFirstLowerRowTopDocY - activeSeamDocY`.

### 3.2 `ScribeDeletionSnapshotCoordinator` (`ScribeScrollDeletionPolicy.kt`)
- Extended `AfterInsertAction` (`lines 406–411`) with `data object ClearTransactionAndClampOverscroll : AfterInsertAction()`.
- Updated `onAfterInsert(...)` (`lines 498–529`):
  - When `wasReplaceWithDelete == true`:
    - `!insertedNotEmpty` $\to$ `SettleEmptyReplaceDelete`
    - `insertedNotEmpty && !isTextEmpty && currentTotalScroll > maxCanvasScroll` $\to$ `ClearTransactionAndClampOverscroll`
    - Otherwise $\to$ `ClearTransaction` (never `None` for any `wasReplaceWithDelete`).
  - When `!wasReplaceWithDelete && insertedNotEmpty && hasPendingOrActiveTransaction`:
    - Returns `ClearTransactionAndClampOverscroll` if `!isTextEmpty && currentTotalScroll > maxCanvasScroll`, else `ClearTransaction`.

### 3.3 Universal Settlement Claim & Lifecycle (`UnifiedCanvasLayout.kt`)
- **State Properties (`lines 545–577`):**
  - `settledClaimedGeneration: Long` (`private set`)
  - `forceFallbackOnDelayedSettlement: Boolean` (`private set`)
  - `pendingPreDeleteSeamCapture: PreDeleteSeamCapture?` (`private set`)
  - `resolvedSeamGeometry: ResolvedSeamGeometry?` (`private set`)
  - `activeSeamLine: Int`, `activeSeamFirstLowerRow: Int`, `activeSeamDocY: Float` (`private set`)
  - `currentDeletionTransactionGeneration: Long` (read-only view of `deletionTransactionGeneration`)
  - `isGapCollapseTransactionOwned: Boolean` (`get() = false` placeholder for Phase 3 controller)
- **Claim & Cleanup Primitives:**
  - `canStillSettleGeneration(expectedGen: Long): Boolean` (`lines 585–593`): checks `isDeletionTransactionActive && deletionTransactionGeneration == expectedGen && hasPendingDeletionSettlement && settledClaimedGeneration != expectedGen && !isGapCollapseTransactionOwned && scribeEditor?.isInContentReplace != true`.
  - `tryClaimPendingDeletionSettlement(expectedGen: Long): Boolean` (`lines 595–614`): refuses to consume while `height <= 0 || !isLaidOut || !editor.isLaidOut || !editor.isWordwrapReady() || editorBusy || editor.layout == null` or `!canStillSettleGeneration(expectedGen)`; on success atomically sets `settledClaimedGeneration = expectedGen`, `hasPendingDeletionSettlement = false`, and invokes `clearPendingDeletionLayoutListener()`.
  - `claimAndClearLiveDeletionTransaction(preserveRetractionDebt: Boolean): Boolean` (`lines 663–689`): checks `hasLiveTx = isDeletionTransactionActive || hasPendingDeletionSettlement || isDeletionTailAnchored || isDeletionResetRetractionActive`; returns `false` without mutating or incrementing `deletionTransactionGeneration` when `!hasLiveTx`; otherwise clears listeners, snapshot, `pendingPreDeleteSeamCapture`, `resolvedSeamGeometry`, active-seam baseline, flags, handles retraction debt, resets `settledClaimedGeneration = -1L`, and bumps `deletionTransactionGeneration++` once.
  - `clearDeletionTransaction()` (`lines 691–693`) & `cancelCanvasAnimation(...)` (`lines 1267–1278`): routed through `claimAndClearLiveDeletionTransaction(...)`.
  - `beginPreEditDeletionSnapshot(...)` (`lines 718–757`): accepts optional `preDeleteSeamCapture: PreDeleteSeamCapture? = null`, resets prior claim/seam baseline state, advances `deletionTransactionGeneration++`, and returns the new `Long` generation.
  - `deferDeletionSettlementUntilLayoutReady(capturedGen, smooth)` (`lines 801–826`): `OnLayoutChangeListener` posts guarded by `canStillSettleGeneration(capturedGen)` -> `handleDeletionSettlement(smooth = smooth, expectedGen = capturedGen)`.
  - `prepareGapCollapseFrame1IfReady(fromGen: Long)` (`lines 927–947`): guards on `canStillSettleGeneration(fromGen)`, defers without claiming when layout/wordwrap unready (setting `forceFallbackOnDelayedSettlement = true` when wordwrap is unready), initializes active-seam baseline via `initializeActiveSeamBaselineIfEligible(curLay)`, and delegates to `handleDeletionSettlement(smooth = true, expectedGen = fromGen)`.
  - `handleDeletionSettlement(smooth: Boolean = true, expectedGen: Long = deletionTransactionGeneration)` (`lines 949–1060`): enforces `canStillSettleGeneration(expectedGen)` and claims atomically via `tryClaimPendingDeletionSettlement(currentGen)` before settling via `DELETION_ANCHOR` or `RESET_TO_TOP`.
  - `anchorCanvasAfterDeletion(smooth: Boolean = true)` (`lines 1289–1316`): guards active-transaction routing with `canStillSettleGeneration(gen)` so claimed/in-replace transactions are never re-entered or prematurely clamped.

### 3.4 Editor Lifecycle, Event Ordering & Watchdog (`ScribeCodeEditor.kt`)
- **Exposed Flags & Helpers (`lines 95–130`, `1287`, `1326`):**
  - `isInContentReplace` (`deletionSnapshotCoordinator.isInContentReplace`), `isLayoutBusy` (`isLayoutComputing || isLayoutBusyState`), `isLayoutBusyState` (`private set`), `hasRtlOrBidiAroundSeam(...)`, and `cancelPendingReplaceWatchdog()`.
- **`beforeReplace` (`lines 747–768`):** cancels prior watchdog, calls `deletionSnapshotCoordinator.onBeforeReplace()`, and schedules `pendingReplaceWatchdog` via `post(watchdog)` to recover cleanly if an exceptional path exits replace without `afterInsert()`.
- **`beforeModification` (`lines 770–856`):** captures `stagedPreDeleteSeamCapture` via `ScribeGapCollapsePolicy.classifyPreDeleteSeam(...)` alongside `PreModificationSnapshot`, inspecting lines `l1`, `l2`, and `l2 + 1` via `hasRtlOrBidiAroundSeam()`.
- **`afterDelete` (`lines 858–914`):** stages `seamCapture` onto `beginPreEditDeletionSnapshot(...)`, captures `txGen`, runs `super.afterDelete(...)`, and invokes `pCanvas.prepareGapCollapseFrame1IfReady(fromGen = txGen)` **only when `!deletionSnapshotCoordinator.isInContentReplace && pCanvas.canStillSettleGeneration(txGen)`**.
- **`ContentChangeEvent(ACTION_DELETE)` (`lines 391–422`):** gated completely by `!isInContentReplace` (so intermediate replace deletions never trigger `resetDocumentToTopFromDeletion` or settlement posts). Captures `scheduledGen = pCanvas?.currentDeletionTransactionGeneration ?: -1L` and routes posted settlement through `canvas.canStillSettleGeneration(scheduledGen)`, clamping overscroll only when `!canvas.isDeletionTransactionActive`.
- **`afterInsert` (`lines 916–979`):** cancels `pendingReplaceWatchdog`, clears `stagedPreDeleteSeamCapture`, invokes `onAfterInsert(...)`:
  - **Before `super.afterInsert(...)`:** on `ClearTransaction` or `ClearTransactionAndClampOverscroll`, calls `pCanvas?.claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)`.
  - **After `super.afterInsert(...)`:** for clearing replacements, calls `pCanvas.clampCanvasScrollToMax(smooth = true)` when `ClearTransactionAndClampOverscroll` or overscrolled while `isWordwrapReady() && !isLayoutBusy` (otherwise `LayoutStateChangeEvent`'s `!isDeletionTransactionActive` branch handles deferred clamp). For `SettleEmptyReplaceDelete`, calls `prepareGapCollapseFrame1IfReady(fromGen = gen)` immediately when layout & wordwrap are ready, else posts a `canStillSettleGeneration(gen)`-guarded `handleDeletionSettlement(smooth = true, expectedGen = gen)`.
- **`LayoutStateChangeEvent` (`lines 1562–1610`):** ignores pending deletion settlement while `isInContentReplace == true`, uses `canStillSettleGeneration(txGen)`, and never falls through to `clampCanvasScrollToMax` or `ensureCursorVisibleAboveKeyboard` while `pCanvas.isDeletionTransactionActive == true`.
- **`onDetachedFromWindow` (`lines 1955–1965`):** cancels `pendingReplaceWatchdog` and clears `stagedPreDeleteSeamCapture`.

---

## 4. Test Coverage State (Unexecuted Locally)

| Test Suite / File | Coverage Added/Updated in Phase 2 | Local Execution Status | CI / Target Task |
|---|---|---|---|
| `ScribeGapCollapsePolicyTest.kt` | **Suite A Case 5** (`testSuiteACase5_SnapshotCoordinatorOnAfterInsert_NonEmptyAndEmptyReplace`) + Case I (`C1 == 0` vs `C1 > 0`), Case II (EOF vs surviving `L2 + 1`), and RTL/Bidi seam tests (`testPreAndPostDeleteSeamGeometry_CaseI_And_CaseII_MatchesSpec`). | **Not executed locally** | `:app:testDebugUnitTest` |
| `ScribeScrollDeletionPolicyTest.kt` | Updated `testSnapshotCoordinator_ReplaceWithDelete_DistinguishesShrinkingVsEmptyReplace` to assert `ClearTransactionAndClampOverscroll` (overscrolled) and `ClearTransaction` (non-overscrolled) alongside `SettleEmptyReplaceDelete`. | **Not executed locally** | `:app:testDebugUnitTest` |
| `ScribeGapCollapseControllerTest.kt` (**Suite B1**) | Live View `tryClaimPendingDeletionSettlement` exclusivity/unready retry & `!isInContentReplace` `afterDelete` tests. | **Deferred to Phase 3** | `:app:testDebugUnitTest` |
| `ScribeGapCollapseIntegrationTest.kt` (**Suite E**) | End-to-end `Content.replace()` lifecycle tests. | **Deferred to Phase 5** | `:app:connectedDebugAndroidTest` |

---

## 5. Instructions & Integration Contract for Phase 3 Agent

1. **Prerequisites Before Editing Phase 3 Code:**
   - Read [`phase-3.md`](./phase-3.md) and [`implementation_plan.md`](./implementation_plan.md) (`Guardrails 2 & 3`, Sections `3.1`–`3.4`).
   - Verify GitHub Actions status of Phase 2 commit `2417c64c82e32d49badfbeb4813381113c828241` (`build.yml` JVM unit tests + compilation) before or alongside Phase 3 startup; resolve any compile/test failure on `2417c64` first if present.
   - Preserve `implementation_plan.md` SHA-256 (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`).

2. **Exact Phase 2 $\to$ Phase 3 Handoff Wiring in `UnifiedCanvasLayout.kt`:**
   - Replace the Phase-2 placeholder `val isGapCollapseTransactionOwned: Boolean get() = false` (`UnifiedCanvasLayout.kt:573`) with `gapCollapseController.isTransactionOwned`.
   - Keep `canStillSettleGeneration`, `tryClaimPendingDeletionSettlement`, and `claimAndClearLiveDeletionTransaction` atomic contracts intact; integrate `claimAndClearLiveDeletionTransaction` with Phase 3's `commitGapCollapseIdle` / `finalizeCompletedGapCollapseTransaction` / `finalizeUnresolvedGapCollapseTransaction` / `cancelGapCollapseAndDeletionTransaction` per `phase-3.md` Section `3.2`.
   - In `prepareGapCollapseFrame1IfReady(fromGen: Long)` (`UnifiedCanvasLayout.kt:927`): keep the readiness gate, `forceFallbackOnDelayedSettlement`, and `initializeActiveSeamBaselineIfEligible(curLay)` (`resolvedSeamGeometry`, `activeSeamLine`, `activeSeamFirstLowerRow`, `activeSeamDocY`), then replace the terminal Phase-2 delegation (`handleDeletionSettlement(smooth = true, expectedGen = fromGen)`) with Phase 3's `ScribeGapCollapsePolicy.planCollapse(...)` + Mode-1/Mode-2 controller launch (falling back to `handleDeletionSettlement` when ineligible or `forceFallbackOnDelayedSettlement == true`).
   - Consume `settledClaimedGeneration`, `activeDeletionSnapshot`, and `resolvedSeamGeometry` through the sealed `SafetyRevalidationResult` Event-B pipeline while preserving Option-A `PhysicalScrollOrigin` tagging and literal sole `ScribeScrollCoordinator.finishAnimation()` ownership.

3. **Explicit Phase 3 Boundaries Still Remaining:**
   - Do **not** implement `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor` in Phase 3 (Phase 4, gated by Phase `0.2`).
   - Do **not** implement touch `untranslateYOffset`, incremental `(seamLine, 0)` rebase rules, Policy A/B mutation routing, or serialized height-resize handlers in Phase 3 (Phase 5), except for hosting the `PendingPostSafetyKeyboardEval` slot/model on `ScribeGapCollapseController` as specified in `phase-3.md`.
