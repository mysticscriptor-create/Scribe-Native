# Phase 2 — Pre-Delete Geometry, `Content.replace()` Deferral & Universal Settlement Claim

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Plan Guardrails & Non-Negotiable Architecture Rules` (Guardrail 2)
- `Consolidated File Ownership Map`
- `Phase 2 — Pre-Delete Geometry, Content.replace() Deferral & Universal Settlement Claim` (`2.1`–`2.2`)
- `Testing Strategy & Extended Validation Matrix` (`A`, `B1`, `E`)

---

## Scope

Implement pre-delete Case I / Case II surviving-content anchor geometry and active-seam baseline capture, the `!isInContentReplace` guard in `ScribeCodeEditor.afterDelete()`, explicit `AfterInsertAction.ClearTransactionAndClampOverscroll` handling for overscrolled non-empty replacements in `ScribeDeletionSnapshotCoordinator` and `ScribeCodeEditor.afterInsert()`, universal atomic `tryClaimPendingDeletionSettlement(expectedGen)` / `canStillSettleGeneration(expectedGen)` across all settlement entry points, and `pendingReplaceWatchdog` lifecycle tracking.

## Prerequisites & Dependencies

- **Hard Prerequisite ([Phase 0](./phase-0.md#validation--phase-completion-gates-05)):** Phase 0 Steps **`0.1`**, **`0.3`**, and **`0.4`** MUST be completed before modifying Phase 2 files. *(Phase `0.2` is NOT required for Phase 2.)*
- **Upstream Dependency ([Phase 1](./phase-1.md)):** Consumes `ScribeGapCollapsePolicy` eligibility/geometry inputs and integer safety model.
- **Downstream Consumer ([Phase 3](./phase-3.md)):** `ScribeGapCollapseController` relies on `canStillSettleGeneration`, `tryClaimPendingDeletionSettlement`, `claimAndClearLiveDeletionTransaction`, and generation-scoped settlement routing.

## Explicit Non-Goals

- Do **not** implement the 5-phase `ScribeGapCollapseController`, `ScribeScrollCoordinator.applyStepWithResult()`, or `ScribeScrollAnimation` completion refactors (covered in [Phase 3](./phase-3.md)).
- Do **not** implement `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor` (covered in [Phase 4](./phase-4.md), gated by Phase `0.2`).

## Shared Architecture Contracts Referenced

- **Guardrail 2 (Replace-Safe Settlement Claim & Universal Claim):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **File Ownership:** [`implementation_plan.md`](./implementation_plan.md#consolidated-file-ownership-map-source-verified--proposed)

---

## File Ownership in Phase 2

1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollDeletionPolicy.kt`: Extend `ScribeDeletionSnapshotCoordinator.AfterInsertAction` with **`data object ClearTransactionAndClampOverscroll : AfterInsertAction()`** (replacing `None` for overscrolled non-empty replacements).
2. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`: Pre-delete split capture in `beforeModification`, `!isInContentReplace` guard in `afterDelete`, `ClearTransaction` & `ClearTransactionAndClampOverscroll` cleanup + overscroll clamping in `afterInsert`, generation-scoped settlement routing in `ContentChangeEvent(ACTION_DELETE)` and `LayoutStateChangeEvent`, and tracked `pendingReplaceWatchdog` cleanup in `onDetachedFromWindow()`.
3. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`: Universal `canStillSettleGeneration(expectedGen)` and `tryClaimPendingDeletionSettlement(expectedGen)` primitives + integration with `prepareGapCollapseFrame1IfReady` / `handleDeletionSettlement`.

---

## Implementation Steps & Exact Specifications

### 2.1 Surviving-Content Anchor Geometry (`[Source-verified]` / `[Proposed]`)
- **Case I (`C2 < preLineLen(L2)`):**
  - If `C1 > 0` $\to$ `isMergedPartialRowSeam = true` (`[Fallback]` to existing `ScribeScrollDeletionPolicy`).
  - If `C1 == 0`:
    - `seamLine = startLine`
    - `seamCol = 0`
    - `preFirstLowerRowTopDocY = preLayout.getRowIndexForPosition(preContent.getCharIndex(L2, C2)) * rowHeight`
    - `seamFirstLowerRow = postLayout.getRowIndexForPosition(postContent.getCharIndex(startLine, 0))`
- **Case II (`C2 == preLineLen(L2)`):**
  - If `L2 + 1 >= preLineCount` $\to$ `hasSurvivingLower = false` (`[Fallback]`).
  - Else:
    - `seamLine = startLine + 1`
    - `seamCol = 0`
    - `preFirstLowerRowTopDocY = preLayout.getRowIndexForPosition(preContent.getCharIndex(L2 + 1, 0)) * rowHeight`
    - `seamFirstLowerRow = postLayout.getRowIndexForPosition(postContent.getCharIndex(startLine + 1, 0))`
- **Active-seam baseline initialized immediately on split start:**
  `(activeSeamLine = seamLine, activeSeamFirstLowerRow = seamFirstLowerRow, activeSeamDocY = seamFirstLowerRow * rowHeight.toFloat())`.

### 2.2 `Content.replace()` Event Ordering & Universal Settlement Claim (`[Source-verified]` / `[Proposed]`)

**Why `afterDelete()` Must Guard on `!isInContentReplace` (`[Source-verified]` `Content.java:585-587`):**
`Content.replace()` calls `dispatchBeforeReplace()` $\to$ `deleteInternal()` (which fires **`ScribeCodeEditor.afterDelete()`**) $\to$ `insertInternal()` (which fires **`ScribeCodeEditor.afterInsert()`**). Calling `prepareGapCollapseFrame1IfReady()` inside `afterDelete()` while `deletionSnapshotCoordinator.isInContentReplace == true` would prematurely collapse an intermediate deletion before its replacement text is inserted.

**Universal Atomic Claim (`UnifiedCanvasLayout`):**
- `canStillSettleGeneration(expectedGen: Long): Boolean`: early-rejection helper only:
  `isDeletionTransactionActive && deletionTransactionGeneration == expectedGen && hasPendingDeletionSettlement && settledClaimedGeneration != expectedGen && !gapCollapseController.isTransactionOwned && !(editor as ScribeCodeEditor).isInContentReplace`.
- `tryClaimPendingDeletionSettlement(expectedGen: Long): Boolean`: atomically sets `settledClaimedGeneration = expectedGen`, `hasPendingDeletionSettlement = false`, and calls `clearPendingDeletionLayoutListener()`. **Never consumed** while `height <= 0 || !isLaidOut || !editor.isLaidOut || !editor.isWordwrapReady() || editor.isLayoutBusyState || editor.layout == null` (preserving retry!).

**Exact Event Ordering Across All Three Deletion/Replace Sequences (`[Source-verified]` / `[Proposed]`):**

1. **Pure Selection Deletion (`Content.delete()` / `deleteText()` — `isInContentReplace == false`):**
   - `beforeModification` captures pre-delete selection & `GapCollapsePreSnapshot`.
   - `afterDelete`: sees `BeginTransaction` & **`!deletionSnapshotCoordinator.isInContentReplace`**, calls `pCanvas.beginPreEditDeletionSnapshot(..., markPendingSettlement = true)` (`gen = G`), then `super.afterDelete()`.
   - `super.afterDelete()` synchronously fires `ContentChangeEvent(ACTION_DELETE)`: listener captures `scheduledGen = G` and posts `if (canvas.canStillSettleGeneration(G)) canvas.handleDeletionSettlement(smooth = true, expectedGen = G)`.
   - Immediately after `super.afterDelete()` returns inside `afterDelete()` (**still `!isInContentReplace`**): calls **`prepareGapCollapseFrame1IfReady(fromGen = G)`**:
     - If layout & wordwrap ready: calls `tryClaimPendingDeletionSettlement(G)` $\to$ `true` $\to$ launches either V1 Gap Collapse (`isTransactionOwned = true`) OR legacy `DELETION_ANCHOR` / `RESET_TO_TOP` once.
     - If layout unready / async wordwrap: does NOT consume claim; leaves `hasPendingDeletionSettlement = true` (marks `forceFallbackOnDelayedSettlement = true` if `!isWordwrapReady()`), allowing `LayoutStateChangeEvent` / `OnLayoutChangeListener` / `ContentChangeEvent.post` to claim `G` once ready (first caller wins, second is a strict no-op; `isDeletionTransactionActive && !canStillSettleGeneration` never falls through to `clampCanvasScrollToMax` or `ensureCursorVisibleAboveKeyboard`).

2. **Empty Replacement (`Content.replace(..., "")` — `isInContentReplace == true`):**
   - `beforeReplace` sets `isInContentReplace = true`; `beforeModification` captures snapshot.
   - `afterDelete` (`isInContentReplace == true`): calls `pCanvas.beginPreEditDeletionSnapshot(..., markPendingSettlement = true)` (`gen = G`) and `super.afterDelete()`, **and SKIPS `prepareGapCollapseFrame1IfReady(G)` because `isInContentReplace == true`!**
   - `ContentChangeEvent(ACTION_DELETE)` (`isInContentReplace == true`): skips immediate/delayed settlement completely (neither empty-doc `resetDocumentToTopFromDeletion` nor `post`).
   - `LayoutStateChangeEvent`: ignored for pending deletion settlement while `isInContentReplace == true`.
   - `insertInternal("")` calls `ScribeCodeEditor.afterInsert()` (`insertedContent.isEmpty()`): `deletionSnapshotCoordinator.onAfterInsert()` resets `isInContentReplace = false` and returns **`SettleEmptyReplaceDelete`**. Immediately after `super.afterInsert()`: if layout & wordwrap ready, calls `prepareGapCollapseFrame1IfReady(fromGen = G)`; else posts `if (canvas.canStillSettleGeneration(G)) canvas.handleDeletionSettlement(smooth = true, expectedGen = G)` — settled **at most once** through `tryClaimPendingDeletionSettlement(G)`.

3. **Non-Empty Replacement (`Content.replace(..., "foo")` — `isInContentReplace == true`, Both Not-Overscrolled AND Overscrolled `[Source-verified]` `ScribeScrollDeletionPolicy.kt:498-512`):**
   - Baseline `onAfterInsert()` returned `None` whenever `wasReplaceWithDelete && insertedNotEmpty && !isTextEmpty && currentTotalScroll > maxCanvasScroll` (overscrolled), leaving the staged deletion snapshot pending (`isDeletionTransactionActive == true`) where a subsequent `LayoutStateChangeEvent` (`ScribeCodeEditor.kt:1453, 1463`) could misinterpret it as an unresolved selection deletion.
   - **Required Explicit `AfterInsertAction` & Lifecycle Fix:** Extend `ScribeDeletionSnapshotCoordinator.AfterInsertAction` with **`data object ClearTransactionAndClampOverscroll : AfterInsertAction()`** (and return `ClearTransaction` or `ClearTransactionAndClampOverscroll` instead of `None` for ALL non-empty replacements):
     - While `isInContentReplace == true`: `afterDelete` stages snapshot `G` and **skips `prepareGapCollapseFrame1IfReady(G)`**; `ContentChangeEvent(ACTION_DELETE)` and `LayoutStateChangeEvent` ignore pending settlement.
     - In `ScribeCodeEditor.afterInsert()` when `insertedNotEmpty == true`: `onAfterInsert()` resets `isInContentReplace = false` and returns:
       - **`ClearTransaction`** when `currentTotalScroll <= maxCanvasScroll` (or `isTextEmpty` edge).
       - **`ClearTransactionAndClampOverscroll`** when `!isTextEmpty && currentTotalScroll > maxCanvasScroll`.
     - **Before `super.afterInsert(...)`:** For BOTH `ClearTransaction` and `ClearTransactionAndClampOverscroll`, immediately call **`pCanvas.claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)`** (atomically clearing `activeDeletionSnapshot`, `isDeletionTransactionActive`, `hasPendingDeletionSettlement`, removing pending deletion layout listeners, and bumping `deletionTransactionGeneration++` **exactly once** while keeping controller `IDLE`, `tauLower = 0f`, zero split frame).
     - **After `super.afterInsert(...)`:** Because `G` was already cleared and bumped, neither delayed `LayoutStateChangeEvent` nor any posted callback can ever enter V1 gap collapse or legacy deletion settlement for that replacement. For `ClearTransactionAndClampOverscroll` (or if `layout.afterInsert` resulted in `(pCanvas.scrollD + offsetY) > pCanvas.maxCanvasScroll` while `isWordwrapReady() && !isLayoutBusyState`), call **`pCanvas.clampCanvasScrollToMax(smooth = true)`** cleanly (and if wordwrap is still computing asynchronously, `LayoutStateChangeEvent`'s normal `!pCanvas.isDeletionTransactionActive` branch executes `clampCanvasScrollToMax(smooth = true)` once layout completes — without split rendering).
   - Optional `beforeReplace` watchdog `pendingReplaceWatchdog: Runnable?` is tracked and removed inside `onDetachedFromWindow()` (`ScribeCodeEditor.kt:1816`).

---

## Validation & Acceptance Criteria

Covered in Master Plan `Testing Strategy & Extended Validation Matrix` (**Suites A, B1, E**):
1. **Suite A (`ScribeGapCollapsePolicyTest.kt`):** `ScribeDeletionSnapshotCoordinator.onAfterInsert()` returns `ClearTransaction` (non-empty non-overscrolled), `ClearTransactionAndClampOverscroll` — never `None` — (non-empty overscrolled), and `SettleEmptyReplaceDelete` (empty replace).
2. **Suite B1 (`ScribeGapCollapseControllerTest.kt`):** Universal `tryClaimPendingDeletionSettlement` (first caller wins, second is strict no-op, never consumed when layout/wordwrap unready) and `!isInContentReplace` guard in `afterDelete`.
3. **Suite E (`ScribeGapCollapseIntegrationTest.kt`, executed after Phases 3–5):** Non-empty replacement while not overscrolled; non-empty replacement while overscrolled (`ClearTransactionAndClampOverscroll`); delayed `LayoutStateChangeEvent` following either replacement asserting zero intermediate or delayed gap collapse starts; empty replacement (`""`).

## Phase-Completion Conditions

- Case I (`C1 == 0` vs `C1 > 0` fallback) and Case II (`L2 + 1 >= preLineCount` fallback vs surviving lower) geometry extraction matches Section `2.1`.
- `AfterInsertAction.ClearTransactionAndClampOverscroll` implemented; zero non-empty replace path leaves a pending deletion snapshot.
- `tryClaimPendingDeletionSettlement(expectedGen)` and `!isInContentReplace` guard enforced across all deletion/replace entry points, and `pendingReplaceWatchdog` cleaned on detach.
- Phase 2 unit assertions in Suites A & B1 pass via the Phase `0.4` Gradle task.
