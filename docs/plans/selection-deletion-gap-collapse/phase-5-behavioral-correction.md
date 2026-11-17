# Phase 5 Behavioral Correction — V1 Selection-Deletion Gap-Collapse Fixes

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native`
**Branch:** `main`
**Verified Pre-Edit Baseline HEAD:** `4021fcdcf5982957f2368b5d42131c3335a7c897` (GitHub Actions `Build Scribe APK` run `44121228779`)
**Pinned Sora Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`)
**Authoritative Frozen Plan:** `docs/plans/selection-deletion-gap-collapse/implementation_plan.md` (SHA-256 `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`, unmodified)

---

## 1. Root-Cause Findings & Corrections Applied

### 1.1 Cursor-Animation & Auto-Indent Floor Race (`ScribeCodeEditor`, `ScribeCursorAnimator`)
- **Finding:** Inside Sora `CodeEditor.afterDelete()` and `CodeEditor.afterInsert()` (empty replacement `""`), Sora synchronously dispatches `SelectionChangeEvent` and starts `cursorAnimator` before `ScribeCodeEditor.afterDelete()` / `afterInsert()` invokes `UnifiedCanvasLayout.prepareGapCollapseFrame1IfReady()`. In addition, `CodeEditor.beforeModification()` invokes `cursorAnimator.markStartPos()`, which in `ScribeCursorAnimator` cancels any already-running animation prior to capturing `isRunning`.
- **Correction:**
  - `ScribeCodeEditor.beforeModification()` captures `animatorRunningPreMod = cursorAnimator.isRunning` *before* delegating to `super.beforeModification(content)` and stores it on `PreDeleteSeamCapture.wasCursorAnimationRunningBeforeDelete`.
  - Added `deferAutoStartForPendingCollapse`, `suspendMarkAndStart`, `discardDeferredStart()`, and `flushDeferredStartIfNeeded()` to `ScribeCursorAnimator`.
  - When a real selection deletion is eligible for pending gap-collapse evaluation, `ScribeCodeEditor.afterDelete()` sets `isDeferringSelectionDeletionEffects = true` and `deferAutoStartForPendingCollapse = true`, deferring both `SelectionChangeEvent -> enforceAutoIndentFloor()` and deletion-induced cursor animator auto-starts across `afterDelete()` (and across `afterInsert()` when `afterInsert` settles `SettleEmptyReplaceDelete` from `Content.replace(..., "")`).
  - Once `prepareGapCollapseFrame1IfReady()` resolves:
    - If collapse entered (`isTransactionOwned == true`): `discardDeferredStart()` discards the deletion-induced cursor animation so Option B is not tripped, and deferred `enforceAutoIndentFloor(suppressCursorAnimator = true, makeSelectionVisible = false)` runs cleanly with `preserveSeamColZeroOnFloor = true`.
    - If collapse did not enter: any deferred cursor animation or auto-indent floor flushes normally (`flushDeferredStartIfNeeded()` / `enforceAutoIndentFloor(suppressCursorAnimator = false, makeSelectionVisible = true)`).
  - Pre-existing unrelated cursor animations genuinely running before `beforeModification()` still trigger `INELIGIBLE_FALLBACK`.

### 1.2 Handle & Selected-Text Visibility Geometry (`ScribeGapCollapsePolicy`, `ScribeEditorRenderer`, `ScribeCodeEditor`, `UnifiedCanvasLayout`, `MainEditorScreen`)
- **Finding:** Pre-deletion handle and selection visibility previously approximated `(L1, C1)` / `(L2, C2)` using post-deletion seam rows and full-width row strips, missed Case II lower handle checks at `(L2, C2)` (versus `L2+1`), did not mirror Sora's `drawRows()` window + horizontal scroll + `visibleEditorTop`/`visibleEditorBottom` handle clipping, and `MainEditorScreen.kt` registered a single full-width `"top_floating_pills"` bounds box that covered the entire top strip across mid-row horizontal space.
- **Correction:**
  - Exposed protected Sora `EditorRenderer` metrics (`getRowTopForBackground`, `getRowBottomForBackground`, `paintGeneral.spaceWidth`, `getMiniGraphWidth`) cleanly on `ScribeEditorRenderer`.
  - In `ScribeCodeEditor.beforeModification()`, captured actual pre-deletion handle document bounds `preUpperHandleDoc` `(L1, C1)` and `preLowerHandleDoc` `(L2, C2)` using Sora's active `handleStyle` (`HandleStyleSideDrop` 22dp, `HandleStyleDrop` 20x30dp or live descriptor, custom `SelectionHandleStyle` live descriptor) and RTL orientation (`content.isRtlAt`), plus exact per-row selected background spans via `EditorRenderer.createTextRow(r).iterateBackgroundRegions(...)`.
  - Implemented `ScribeGapCollapsePolicy.computeHandleRectDoc`, `isHandleVisibleInViewport` (applying Sora `drawRows()` row-window visibility, horizontal scroll clipping, `visibleEditorTop`/`visibleEditorBottom` vertical clipping, and `isRectUnobstructedByFloatingPills`), and `isSelectionTouchedOnScreen` (per-row clipped span checks rejecting spans wholly occluded by floating pills).
  - Replaced `"top_floating_pills"` on the outer full-width `Row` in `MainEditorScreen.kt` with tight per-button `FloatingPillButton` registrations: `"top_pill_search"`, `"top_pill_checkpoint"`, and `"top_pill_menu"`.

### 1.3 Mid-Collapse Hint/Diagnostics/Indent Churn (`ScribeCodeEditor`, `UnifiedCanvasLayout`)
- **Correction:** Deferred `setInlayHints()`, `setDiagnostics()`, `refreshVisualFirstLineIndentHints()`, and `ScrollEvent`-driven indent refreshes while `parentCanvas?.gapCollapseController?.isTransactionOwned == true`, flushing on `UnifiedCanvasLayout.commitGapCollapseIdle()` via `flushDeferredHintsAndDiagnosticsAfterCollapse()` and clearing state on reset/detach.

### 1.4 Scenario E & Ordinary Caret-Follow Top Obstruction Handling (`ScribeScrollPolicy`, `UnifiedCanvasLayout`)
- **Correction:** Extended `ScribeScrollPolicy.resolveCursorScrollDelta(...)` with backwards-compatible defaulted `visibleEditorTop`, `visibleEditorBottom`, and `topBreathingMarginPx` parameters. Added `resolveTopObstructionAndBreathing(...)` in `UnifiedCanvasLayout.kt` and wired both into `ensureCursorVisibleAboveKeyboard()` (supporting `allowDuringDeletionRetraction = true` focus override + post-check overscroll clamping for Scenario E) and `requestChildRectangleOnScreen()`.

---

## 2. Tests Added

1. **JVM Unit Tests:**
   - `ScribeGapCollapsePolicyTest`: `computeHandleRectDoc` (`HandleStyleSideDrop` & `HandleStyleDrop`), `isHandleVisibleInViewport` (horizontal/vertical clipping, `drawRows()` window cutoff, tight floating pill occlusion), Case II lower-handle `(L2, C2)` vs offscreen `L2+1` (`B_LOWER_ONLY`), `isSelectionTouchedOnScreen` span clipping & pill coverage, and `wasCursorAnimationRunningBeforeDelete` -> `INELIGIBLE_FALLBACK`.
   - `ScribeScrollPolicyTest`: `resolveCursorScrollDelta` top obstruction behind `TOP_BAR`, top breathing margin, `[-deadbandPx .. 0)` behavior under top obstruction vs default backwards compatibility, and zero-scroll guard.
2. **Android Instrumentation Tests:**
   - `ScribeGapCollapseIntegrationTest`:
     - `suiteE_realSelectionDeletions_ScenariosC_A_B_AndAnimatorIndentRace` (`ed.cutText()`, `KeyEvent.KEYCODE_DEL`, and `InputConnection.deleteSurroundingText(1, 0)` with `isCursorAnimationEnabled = true`, `firstLineIndentSpaces = 4`, and populated `ScribeOverlayBoundsRegistry`, including Scenario B Case II with `c1 = 2` -> floor `4`).
     - `suiteE_realSelectionDeletions_ScenariosD_And_E_WithOverlays` (Scenario D centered collapse and Scenario E offscreen-below `BOTTOM_BAR` & top-occluded behind `TOP_BAR`).
     - `suiteE_v1ShortfallAtZeroScrollAndPreExistingRunningAnimatorFallback` (V1 shortfall `> 0` clamped at `preScrollY = 0` and pre-existing running `ScribeCursorAnimator` -> `INELIGIBLE_FALLBACK`).
