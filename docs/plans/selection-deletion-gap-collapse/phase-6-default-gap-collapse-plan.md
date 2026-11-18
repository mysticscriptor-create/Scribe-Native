# Phase 6 — Default Gap Collapse Promotion & Category A/B/C Remediation Plan

**Baseline HEAD (`main`):** `38de5ab332ff102efff822f786ea7a0db951b021`  
**Preserved Reference Plan:** [`implementation_plan.md`](./implementation_plan.md) (`SHA-256: c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`)  
**Pinned Editor Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`, unchanged)  
**Authorization:** User-authorized remediation of Category A runtime bugs inside V1, Category B rules that forced selection deletions into fallback instead of default gap collapse, and Category C narrow-viewport test fixture wrapping defects.

---

## 1. Objectives

Promote selection-deletion gap collapse from a narrow special-case to the **default selection-deletion path** matching `intended_behavior`:

1. **Default / Scenario C (Both Handles Visible) & Scenario B (Lower Handle Only):**
   - Surviving lower content stays visually stationary (`0.0px` Frame-1 and Mode-1 drift when physically possible).
   - Surviving upper content glides smoothly downward to meet the lower boundary.
   - No Frame-1 snap followed by a secondary scroll.
   - Caret stays logically valid and visually consistent with the stationary lower boundary (Case II blank/indent caret line remains in Pass 2 when upper prefix is blank).
2. **Scenario A (Upper Handle Only Visible):**
   - Upper surviving content and upper selection boundary stay stationary; lower surviving content glides upward to close the gap.
3. **Scenario D (Selected Text Visible, Neither Handle Visible):**
   - Viewport-centered convergence of upper and lower surviving content around the usable viewport center, regardless of selection height (`> 3x` viewport height supported).
4. **Scenario E (Neither Handles Nor Selected Text Visible):**
   - Bypass split collapse; perform ordinary smooth caret-follow scroll (`ensureCursorVisibleAboveKeyboard`).
5. **General Eligibility:**
   - Support partial-line / merged-row seams (`c1 > 0 && c2 < preLineLen(L2)`), `(0, 0)` start selections with surviving lower content, and `TAIL_ANCHOR` / `RESET_TO_TOP` deletion intents whenever non-empty lower content survives.

---

## 2. Workstreams & Technical Design

### Category B — Promoting Gap Collapse to Default Behavior

1. **Partial-Line / Merged-Row Seams (`ScribeGapCollapsePolicy.kt`, `ScribeGapCollapseController.kt`, `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt`):**
   - Capture `preUpperPrefixBlank = (c1 == 0 || line(l1)[0 until c1].isBlank())`, `preRowLowerSuffixStart = rowIndexAt(l2, c2)`, `preRowLowerEndOfL2 = rowIndexAt(l2, preLineLenL2)`, and `preRowNextLine = rowIndexAt(l2 + 1, 0)` in `classifyPreDeleteSeam`.
   - Remove the `c1 > 0 && c2 < preLineLenL2` `INELIGIBLE_FALLBACK` gate (`isMergedPartialRowSeam = false` for supported single-seam merges).
   - In `resolvePostDeleteSeamGeometry`:
     - If `c1 == 0`: `seamLine = postStartLine`, `seamCol = 0`, `seamFirstLowerRow = row(postStartLine, 0)`, `preFirstLowerRowTopDocY = preRowLowerSuffixStart * rowH`.
     - Else if `preUpperPrefixBlank && postCaretRow == row(postStartLine, 0)`: `postStartLine` carries no visible upper text (`""` or `"    "` indent floor + `l2[c2..)`), so `seamLine = postStartLine`, `seamCol = 0`, `seamFirstLowerRow = postCaretRow`, `preFirstLowerRowTopDocY = preRowLowerSuffixStart * rowH`.
     - Else if `postLastRowOfStartLine > postCaretRow`: split strictly between `postCaretRow` (Pass 1 upper + caret) and `postCaretRow + 1` (Pass 2 lower wrapped tail + `l2+1..`), setting `seamLine = postStartLine`, `seamFirstLowerRow = postCaretRow + 1`, `seamCol = maxOf(postRowStartCol(seamFirstLowerRow), postCaretCol + 1)`, and `preFirstLowerRowTopDocY = (preRowNextLine - (postLastRowOfStartLine + 1 - seamFirstLowerRow)) * rowH` (or EOF equivalent using `preRowLowerEndOfL2 + 1`) so `l2+1..end` has zero drift.
     - Else (`postLastRowOfStartLine == postCaretRow`) and `postStartLine + 1 < postLineCount`: `seamLine = postStartLine + 1`, `seamCol = 0`, `seamFirstLowerRow = row(postStartLine + 1, 0)`, `preFirstLowerRowTopDocY = preRowNextLine * rowH`.
   - Track `activeSeamCol` in `ScribeGapCollapseController` so `getTranslationForPosition(line, col)` evaluates `(line > activeSeamLine || (line == activeSeamLine && col >= activeSeamCol))` accurately for handle hit testing and supports incremental rebase when `activeSeamCol >= 0`.

2. **Case II (`c2 == preLineLen(L2)`) Caret-Line Anchoring (`ScribeGapCollapsePolicy.kt`, `UnifiedCanvasLayout.kt`):**
   - Distinguish real upper text (`!preUpperPrefixBlank`) from blank/indent-only upper prefix (`preUpperPrefixBlank`).
   - When `preUpperPrefixBlank && !anchorUpper` (Scenarios B, C, D): put the empty/indent caret line (`postStartLine`) into Pass 2 (`seamLine = postStartLine`, `seamFirstLowerRow = row(postStartLine, 0)`, `preFirstLowerRowTopDocY = (preRowNextLine - startLineRowSpan) * rowH`). The caret and blank line remain stationary right above `l2 + 1` while `0..postStartLine - 1` glide down.
   - When `!preUpperPrefixBlank` OR `anchorUpper` (Scenario A): keep `seamLine = postStartLine + 1` and `preFirstLowerRowTopDocY = preRowNextLine * rowH` so upper surviving content + caret at `postStartLine` stay stationary at the upper boundary.

3. **Document Boundary & Intent Eligibility (`ScribeGapCollapsePolicy.kt`, `UnifiedCanvasLayout.kt`):**
   - Remove `!hasSurvivingUpper` rejection so `(0, 0)..(L2, C2)` selections with surviving lower content smoothly collapse (header + top boundary glide down to stationary lower in Scenarios B/C, or lower glides up to `(0, 0)` in Scenario A).
   - Allow `TAIL_ANCHOR` and `RESET_TO_TOP` deletion intents to enter Gap Collapse whenever `isSelection && !isTextEmpty && hasSurvivingLower && deltaGapPx > MIN_GAP_COLLAPSE_PX`; propagate `isDeletionTailAnchored` and include `newDocHeight` in `anchorDocBottom` when `TAIL_ANCHOR` so `planCollapse` and Event B enforce the exact same `[safeMinPhysical, safeMax]` floor.
   - Remove the `MAX_SCENARIO_D_GAP_VIEWPORT_MULTIPLIER` (`3f`) cap so large selections (`> 3x` viewport height) converge via Scenario D.

### Category A — Runtime Bugs Inside V1

1. **`FINAL_SAFETY_PENDING` / `LayoutStateChangeEvent` Deadlock (`UnifiedCanvasLayout.kt`, `ScribeCodeEditor.kt`):**
   - Wire `ScribeCodeEditor`'s `LayoutStateChangeEvent` completion (`!isLayoutBusy && isWordwrapReady() && !isInContentReplace`) to `UnifiedCanvasLayout.onEditorLayoutStateReadyForGapCollapse()` when `gapCollapseController.isTransactionOwned`.
   - Guard `deferSafetyRevalidationUntilLayoutReady`'s `View.OnLayoutChangeListener` with `!editorBusy` so an in-progress async wordwrap pass does not consume the single retry prematurely.
   - If async wordwrap completes during an active split phase (`MODE_1`, `MODE_2`, `POINTER_FROZEN`), rebase `activeSeamFirstLowerRow` / `activeSeamDocY` cleanly from `(activeSeamLine, activeSeamCol)`.
2. **`afterDelete()` / `afterInsert()` vs Deferred `enforceAutoIndentFloor` Ordering (`ScribeCodeEditor.kt`):**
   - Execute `applyDeferredAutoIndentFloorBeforeGapCollapseIfNeeded()` (inserting the `"    "` floor and updating `layout` silently with `suppressCursorAnimator = true, makeSelectionVisible = false`) **before** calling `pCanvas.prepareGapCollapseFrame1IfReady(...)` in both `afterDelete()` and `afterInsert()` (`SettleEmptyReplaceDelete`).
3. **`cutText()` + `visualFirstLineIndentSpaces > 0` Async Overwrite Race (`ScribeCodeEditor.kt`, `MainEditorScreen.kt`):**
   - Override `ScribeCodeEditor.cutText()` to copy the selected text synchronously (applying visual indent prefix if active) before calling `deleteText()`, avoiding async `onRequestCommitIndentForCopy` + `applyFormattedTextSilently()` mid-collapse.
   - Guard `MainEditorScreen`'s `indentJob` to wait for any active collapse/deletion transaction and re-read `documentVersion` before applying formatted text.
4. **`MainEditorScreen.kt` `ContentChangeEvent` Guards:**
   - Skip `resetDocumentToTop` during `isDeletionReplaceInProgress` or `isDeletionTransactionActive`, and skip `ensureCursorVisibleAboveKeyboard` during `isDeletionReplaceInProgress`, `isAutoIndentFloorInProgress`, or `gapCollapseController.isTransactionOwned`.

### Category C — Instrumentation Test Fixture & Regression Updates

1. Fix the narrow-width (`720px`) wrapping assumptions in `ScribeGapCollapseIntegrationTest.kt` (compute scroll targets from `lay.getRowIndexForPosition(...)` and use concise 1-row final line where asserted).
2. Update `ScribeGapCollapsePolicyTest.kt` and `ScribeGapCollapseIntegrationTest.kt` assertions reflecting Category B behavior (partial-line seams, Case II caret-line anchoring in Scenarios B/C vs Scenario A, `(0, 0)` start, `TAIL_ANCHOR`/`RESET_TO_TOP`, uncapped Scenario D).
3. Add regression tests proving Category A & B fixes end-to-end on real attached editors and in unit tests.
