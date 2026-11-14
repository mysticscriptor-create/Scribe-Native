# Phase 4 — Single-Lifecycle `ScribeEditorRenderer`, Per-Layer Strategy & `EditorTextActionWindow` Refresh

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Plan Guardrails & Non-Negotiable Architecture Rules` (Guardrails 1 & 4)
- `Sora 0.24.6 Dependency Decision & Measure/Preload Reality`
- `Consolidated File Ownership Map`
- `Phase 4 — Single-Lifecycle ScribeEditorRenderer, Per-Layer Strategy & EditorTextActionWindow Refresh` (`4.1`–`4.4`)
- `Testing Strategy & Extended Validation Matrix` (`B1`, `C`, `D`, `E`)

---

## Scope

Implement `ScribeEditorRenderer : EditorRenderer` and package-private `ScribeDrawCursorTaskAccessor.java` using Sora's single `super.draw(canvas)` / single `drawView(canvas)` lifecycle, two-pass strictly disjoint row partitioning across all 10 draw-order layers, Pass-2 `buildMeasureCacheForLines` line-range warming, all 6 Approach-A `firstLn` collision/gap/continuation cases, Option-B conservative cursor-animation exclusion & mid-collapse asynchronous Event-B fallback (`ScribeCursorAnimator.onAnimatorStarted`, `UnifiedCanvasLayout.handleCursorAnimatorActivatedDuringCollapse()`), `@UiThread renderPassRowRangeOverride` gating, deferred post-draw `EditorTextActionWindow` refresh gate, and **Suite C** (`ScribeGapCollapseRendererTest.kt`).

## Prerequisites & Dependencies

- **Additional Hard Prerequisite ([Phase 0 Step `0.2`](./phase-0.md#02-compiled-aar-bytecode--drawcursortask-accessibility-inspection-proposed)):** Step **`0.2`** MUST have confirmed from compiled `editor-0.24.6.aar` bytecode that `EditorRenderer$DrawCursorTask` and its `y` field are directly accessible to package-private `io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor` **without reflection, `setAccessible`, or `Unsafe`**, and that all overridden `protected` `EditorRenderer` method signatures match.
- **Upstream Dependencies:**
  - [Phase 1](./phase-1.md): `UnobstructedViewportBounds`, `[safeMinPhysical, safeMax]`, Option-B `!isCursorAnimationRunning` eligibility gate.
  - [Phase 3](./phase-3.md): `ScribeGapCollapseController` (`shouldApplySplitRender`, `tauLower`, `activeSeamLine`, `activeSeamFirstLowerRow`, `FINAL_SAFETY_PENDING`, Event-B common finalizers, `commitGapCollapseIdle`).

## Explicit Non-Goals

- Do **not** implement multi-seam/multi-region splitting or merged partial-line horizontal splitting (deferred to V2 in [Phase 6](./phase-6.md)).
- Do **not** modify `io.github.rosemoe:editor:0.24.6` or use reflection/`setAccessible`/`Unsafe` for `DrawCursorTask`.
- Do **not** implement touch coordinate un-translation, mid-collapse insertion rebasing, or height-only resize serialization (covered in [Phase 5](./phase-5.md)).

## Shared Architecture Contracts Referenced

- **Guardrail 1 (Native Rendering Lifecycle, Draw Order, Approach-A `firstLn`, Option B, Synchronous `editor.invalidate()`):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Guardrail 4 (`EditorTextActionWindow` Post-Draw Handle-Refresh Gate):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Sora `0.24.6` Measure/Preload Contract:** [`implementation_plan.md`](./implementation_plan.md#sora-0246-dependency-decision--measurepreload-reality)

---

## File Ownership in Phase 4

### New Files
1. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeEditorRenderer.kt`:  
   `ScribeEditorRenderer : EditorRenderer` — single `super.draw(canvas)` / `drawView(canvas)` split renderer, Pass 1 & Pass 2 orchestration, overrides (`drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawSideBlockLine`), explicit 6-case Approach-A `firstLn` collision/gap handling, Pass-2 `DrawCursorTask` Y shift, pre-`super.draw` Option-B `cursorAnimator.isRunning()` defensive guard, and post-`super.draw` `renderedDrawPassSeq` + `didRefreshCursorDescriptors` notification.
2. `app/src/main/java/io/github/rosemoe/sora/widget/ScribeDrawCursorTaskAccessor.java`:  
   Minimal package-private bridge in `io.github.rosemoe.sora.widget` to shift `EditorRenderer.DrawCursorTask.y` by `+tauLower` once before appending Pass-2 tasks into `postDrawCursor`.

### Modified Files (Phase 4 Scope)
1. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCursorAnimator.kt`: Adds lightweight `onAnimatorStarted: (() -> Unit)?` notification hook inside `start()` / `triggerGlide()` invoked synchronously on UI thread before the next draw frame.
2. `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`: Wires `onCreateRenderer()` returning `ScribeEditorRenderer`; `@UiThread renderPassRowRangeOverride` gating `getFirstVisibleRow()` / `getLastVisibleRow()`; Option-B `ScribeCursorAnimator` start observer (plus `afterInsert` / `setSelection` backup) triggering `handleCursorAnimatorActivatedDuringCollapse()`; `EditorTextActionWindow` disabled across split collapse, `FINAL_SAFETY_PENDING`, and post-`IDLE` until `onAfterRendererDrawPass` cursor-descriptor refresh gate succeeds; detach cleanup of `pendingTextActionRefreshRunnable`.
3. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`: Implements `handleCursorAnimatorActivatedDuringCollapse()` routing all 6 Event-B `SafetyRevalidationResult` variants through the existing common finalizers.

### Test File
- `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt` (**Suite C**)

---

## Implementation Steps & Exact Specifications

### 4.0 Sora `0.24.6` Measure/Preload Integration (`[Source-verified]` / `[Proposed]`)
- **Retain `io.github.rosemoe:editor:0.24.6` unchanged.**
- Because `drawView()` (`EditorRenderer.java:492`) calls `prepareLines(firstVisibleLine, lastVisibleLine)` across `[normalFirstVisLine - 5, normalLastVisLine + 5]`, `ScribeEditorRenderer` converts non-empty `pass2RowRange` row indices via `editor.layout.getLineNumberForRow(...)` before calling protected `buildMeasureCacheForLines(pass2FirstLine, minOf(pass2LastLine, normalFirstVisLine - 1))`.

### 4.1 Complete Per-Layer Two-Pass Implementation Map (`[Source-verified]` / `[Proposed]`)
Inside `drawView(canvas)` (view-local `[0..editor.width, 0..editor.height]` after `EditorRenderer.draw:192` translates `(+offsetX, +offsetY)`), `pass1ClipBottom = minOf(h, seamScreenY_view)`, `pass2ClipTop = maxOf(stuckLineBottom, seamScreenY_view + tauLower)`:

| Draw Order (`EditorRenderer.java`) | Layer & Sora Hook / Mechanism | Pass Strategy (`override` vs `super`) | Active Row Range | Clip & Translation Scope |
|---|---|---|---|---|
| **1 (`465`)** | **`WHOLE_BACKGROUND`** | Viewport-level: native `drawView` (`super.draw`) once. | N/A | Editor viewport `[0..w, 0..h]`, unshifted. |
| **2 (`492–493`)** | **`prepareLines` / `buildMeasureCacheForLines`** (`protected`, `2536`) | Native warms normal lines; `ScribeEditorRenderer` converts `pass2RowRange` via `layout.getLineNumberForRow(...)` and warms Pass-2 source lines outside normal window. | `normal` + converted `pass2RowRange` lines | Pre-render cache warm only; zero drawing. |
| **3 (`515`)** | **`drawRows(...)`** (`protected`, `1161`: backgrounds, selection, matched/highlight/snippet, hardwrap, text, composing, collects `postDrawLineNumbers`, `postDrawCurrentLines`, `postDrawCursor`) | **`override drawRows`**:<br>• **P1** (skipped if empty): sets `renderPassRowRangeOverride = pass1RowRange` in `try/finally`, calls `super.drawRows(..., postDrawLineNumbers, postDrawCursor, postDrawCurrentLines, pass1FirstLn, stuckLines)`.<br>• **P2** (skipped if empty): sets `renderPassRowRangeOverride = pass2RowRange` in `try/finally`, calls `super.drawRows(..., pass2LineNumbers, pass2CursorTasks, pass2CurrentLines, pass2FirstLn, stuckLines)` *(isolating Approach-A buffers)*.<br>Shifts `pass2CursorTasks` Y once by `+tauLower` via `ScribeDrawCursorTaskAccessor.addOffsetY` and appends to `postDrawCursor`. `4-case requiredFirstLn` assigns `requiredFirstLn` only to Pass 1 (or Pass 2 if Pass 1 is empty). | **P1:** `[0, seamFirstLowerRow - 1] ∩ vis`<br>**P2:** `[seamFirstLowerRow, totalRows - 1] ∩ shiftedVis` | **P1:** `clipRect(0, stuckLineBottom, w, pass1ClipBottom)`, unshifted.<br>**P2:** `clipRect(0, pass2ClipTop, w, h)` **before** `canvas.translate(0f, +tauLower)`.<br>`renderPassRowRangeOverride = null` in `finally`. |
| **4 (`516`)** | **`patchHighlightedDelimiters(...)`** (`protected`, `2290` $\to$ `patchTextRegions` `2398`) | **`override patchHighlightedDelimiters`**: skips empty passes; P1 (`pass1RowRange`) unshifted then P2 (`pass2RowRange`) translated. | P1 / P2 via `renderPassRowRangeOverride` | Same P1 clip & P2 clip-before-translate. |
| **5 (`517`)** | **`drawDiagnosticIndicators(...)`** (`protected`, `1672`) | **`override drawDiagnosticIndicators`**: skips empty passes; P1 (`pass1RowRange`) unshifted then P2 (`pass2RowRange`) translated. | P1 / P2 via `renderPassRowRangeOverride` | Same P1 clip & P2 clip-before-translate. |
| **6 (`525, 538–542, 643`)** | **Line Number Bg + P1 `postDrawCurrentLines` + `drawUserGutterBackground(...)`** (`protected`, `643`) | Gutter bg unshifted; P1 `postDrawCurrentLines` holds `< seamFirstLowerRow` natively. **`override drawUserGutterBackground`**: skips empty passes; runs P1 `super.drawUserGutterBackground`; in P2 paints `pass2CurrentLines` via `super.drawRowBackground` + `super.drawUserGutterBackground`. | P1 / P2 via `renderPassRowRangeOverride` & `pass2CurrentLines` | P1 clip unshifted; P2 `clipRect(0, pass2ClipTop, w, h)` + `translate(0, +tauLower)`. |
| **7 (`543, 546, 550–578, 815`)** | **`drawSideIcons(...)`** (`protected`, `815`) + `drawDivider` (`546`) + Approach-A `firstLn` & `postDrawLineNumbers` (`drawLineNumber` `protected`, `917`) | V1 `isWordwrap == true` guarantees `lineNumberNotPinned`. **`override drawSideIcons`**: runs P1 & P2 `super.drawSideIcons`; executes the exact 6-case Approach-A `firstLn` collision/gap/wrapped-top rules below using isolated `postDrawLineNumbers` vs `pass2LineNumbers` & `pass2FirstLn`, drawing any required unshifted `overrideFirstLnLine` + translated `pass2LineNumbers` via `super.drawLineNumber` / baseline-matched `canvas.drawText` strictly left of `dividerLeftX`. | P1 / P2 via `renderPassRowRangeOverride` & `pass2LineNumbers` | P1 clip unshifted; unshifted `overrideFirstLnLine` uses Sora full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` (X left of `dividerLeftX`); Pass-2 `pass2LineNumbers` & Case-5 `pass2FirstLn` use `clipRect(gutterLeft, pass2ClipTop, dividerLeftX, h)` + `translate(0f, +tauLower)`. |
| **8 (`581–590, 2032`)** | **`drawSideBlockLine(...)`** (`protected`, `2032`) | **`override drawSideBlockLine`**: draws P1 (`clipRect(0, stuckLineBottom, w, pass1ClipBottom)`) iff `block.startLine < seamLine`, AND P2 (`clipRect(0, pass2ClipTop, w, h)` + `translate(0, +tauLower)`) iff `block.endLine >= seamLine`. | Block bounds checked against `seamLine` | Prevents gap bleed / double draw across seam. |
| **9 (`592–598`)** | **`postDrawCursor`** (`DrawCursorTask.execute` `2599`) / `drawSelectionOnAnimation` (`2439`) | **Option B (Conservative V1 Cursor-Animation Policy):** When `cursorAnimator` is idle (standard selection deletion), P1 tasks stay unshifted in `postDrawCursor` and P2 tasks shift once via `ScribeDrawCursorTaskAccessor.addOffsetY(+tauLower)` (drawing Case-I lower-region caret & insertion handle cleanly at `HandleDescriptor.position` `+tauLower`). Split rendering never runs when `cursorAnimator.isRunning()` is true (see Section 4.3). | `postDrawCursor` (`P1 ∪ shifted P2`) | Unclipped handle margins, executed once at `593-595`. |
| **10 (`600, 634–640`)** | **`drawStuckLines` / `drawScrollBarsAndMinimap` / `drawEdgeEffect` / `drawFormatTip`** | `stuckLines` is `null` in V1 wordwrap; scrollbars, edge effects, format tip stay viewport-fixed natively. | N/A | Viewport-level unshifted. |

### 4.2 Exact 6-Case Approach-A `firstLn` Collision & Gap Rules (`[Source-verified]` / `[Derived]` / `[Proposed]`)
In Sora (`EditorRenderer.java:503-579, 917-944, 1350-1359`), `firstLn` (`MutableInt?`, non-null iff `isFirstLineNumberAlwaysVisible && isWordwrap`) is populated in `drawRows` line `1355` when `row == firstVis`, while subsequent leading rows (`rowInf.isLeadingRow`) append `(line, row)` to `lineNumbersOut`. In V1 (`lineNumberNotPinned == true`), let:
- `bottom = editor.getRowBottom(0)`
- `rowH = editor.rowHeight`
- `gutterLeft = maxOf(0f, offsetX)`
- `dividerLeftX = (offsetX + lineNumberWidth + sideIconWidth + editor.dividerMarginLeft).coerceAtLeast(gutterLeft)`
- `pass2TopScreenY = editor.getRowTop(pass2RowRange.first) - editor.getOffsetY() + tauLower`
- When `pass2LineNumbers.isNotEmpty()`, `row2_0 = IntPair.getSecond(pass2LineNumbers.get(0))` with translated top:
  $$\text{translatedTop2} = \text{editor.getRowTop}(row2\_0) - \text{editor.getOffsetY}() + \tau_{\text{lower}}$$
- Baseline Y helpers matching Sora (`555, 558`):
  - $y_{\text{pinned}} = (\text{rowBottom}(0) + \text{rowTop}(0))/2\text{f} - (\text{descent} - \text{ascent})/2\text{f} - \text{ascent}$
  - $y_{\text{pushed}, P2} = \text{translatedTop2} - \text{rowH}/2\text{f} - (\text{descent} - \text{ascent})/2\text{f} - \text{ascent}$

| Case (`[Source-verified]` / `[Proposed]`) | Native `firstLn` | Consulted Buffer & Top Calc | `overrideFirstLnLine` & Y Position | Clip, Translation & Placement vs Divider (`drawDivider` X) |
|---|---|---|---|---|
| **1. Both partitions visible w/ Pass-1 leading rows** (`!P1.isEmpty && !P2.isEmpty && postDrawLineNumbers.isNotEmpty`) | **Retained** (`pass1FirstLn = firstLn`, `pass2FirstLn = null`) | `postDrawLineNumbers` ONLY (`row1 = get(0).second`); Sora `553` tests unshifted `rowTop(row1) - offsetY`. | Not needed (`-1`). | Native Sora (`548-578`) draws `firstLn` unshifted inside `clipRect(0, stuckLineBottom, editor.width, editor.height)` after `drawDivider`; `pass2LineNumbers` draw inside `drawSideIcons` under Pass-2 clip `[pass2ClipTop, h]` + `translate(0, +tauLower)`. |
| **2. Pass 1 visible, NO Pass-1 leading row** (`!P1.isEmpty && postDrawLineNumbers.isEmpty`) | **Suppressed** (`captured = firstLn.value`, then set `firstLn.value = -1`) | `pass2LineNumbers`; compute `translatedTop2 = rowTop(row2_0) - offsetY + tauLower`. | **Required** (`overrideFirstLnLine = captured`): `y_pinned` if `pass2LineNumbers.isEmpty \|\| translatedTop2 > bottom`, else `y_pushed_P2` (**Case 4**). | Drawn in `drawSideIcons` **unshifted** at Sora's gutter X (`LEFT: offsetX`, `RIGHT: offsetX + lineNumberWidth`, `CENTER: offsetX + (lineNumberWidth + dividerMarginLeft)/2f`), strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` (never slicing pinned/pushed `firstLn` at `pass1ClipBottom` or `pass2ClipTop`). |
| **3. Pass 1 empty & Pass 2 visible** (`P1.isEmpty && !P2.isEmpty`) | **Suppressed** (`firstLn.value = -1`; `pass2FirstLn = MutableInt(-1)`) | `pass2LineNumbers` & `pass2TopScreenY = rowTop(P2.first) - offsetY + tauLower`. | **Required** (`pass2FirstLn.value`):<br>• `pass2TopScreenY <= 0f`: `y_pinned` or `y_pushed_P2` (**Case 4**).<br>• `pass2TopScreenY > 0f`: (**Case 5**) draw inside Pass-2 canvas at `rowCenterY(P2.first) - offsetY`. | `pass2TopScreenY <= 0f`: unshifted in `drawSideIcons`, strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` *(so `pass2TopScreenY <= 0f` never clips `firstLn` against `pass2ClipTop`)*.<br>`pass2TopScreenY > 0f`: inside Pass-2 `clipRect(gutterLeft, pass2ClipTop, dividerLeftX, h)` + `translate(0, +tauLower)` ($C^0$-continuous at `pass2TopScreenY = 0f`). |
| **4. Pass-2 first leading row colliding w/ `firstLn`** (`(!P1.isEmpty && postDrawLineNumbers.isEmpty)` OR `(P1.isEmpty && pass2TopScreenY <= 0f)` AND `translatedTop2 <= bottom`) | **Suppressed** | `pass2LineNumbers.get(0)` using `translatedTop2`. | **Required** at `y_pushed_P2`. | Unshifted in `drawSideIcons`, strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` (`y_pushed_P2` sits cleanly above `translatedTop2` with zero overlap against Pass-2 numbers below `pass2ClipTop`, and full-height viewport clip prevents cutting off the pushed glyph when `pass2ClipTop` is near `stuckLineBottom`). |
| **5. Pass 2 visible w/ blank visual gap above it** | Per Cases 1/2 if `!P1.isEmpty` (`translatedTop2 > bottom`); **Suppressed** if `P1.isEmpty` (`pass2TopScreenY > 0f`) | If `!P1.isEmpty`: Case 1/2 keeps `firstLn` unpushed at `y_pinned`. If `P1.isEmpty`: `pass2TopScreenY > 0f`. | Drawn inside Pass 2 (`P1.isEmpty`) so label stays attached to `P2.first` instead of floating in empty gap `[0, pass2TopScreenY]`. | Pass-2 `clipRect(gutterLeft, pass2ClipTop, dividerLeftX, h)` + `translate(0, +tauLower)`, strictly left of `dividerLeftX` (note `P2.first` is excluded from `pass2LineNumbers` by `drawRows:1355`, so drawing `pass2FirstLn.value` preserves its line number too). |
| **6. First visible row is a wrapped continuation row** (`!layout.getRowAt(firstVis).isLeadingRow`) | If `!P1.isEmpty`: Case 1 (retained) or Case 2 (suppressed). If `P1.isEmpty`: Suppressed. | `drawRows:1355` always writes `line_top` into `requiredFirstLn` even when `!isLeadingRow`, routing straight to Case 1, Case 2 (+ Case 4), Case 3 (+ Case 4), or Case 5. | Follows the corresponding Case 1 / 2 / 3 / 4 / 5 formula identically with `line_top`. | Same exact Pass-1/unshifted full-height viewport clip or Pass-2/translated clip and divider-left X alignment. |

### 4.3 Option B — Conservative V1 Cursor-Animation Policy (`[Source-verified]` / `[Proposed]` / `[Fallback]`)
1. **Already Running at Deletion Start:** V1 Eligibility Gate checks `!editor.cursorAnimator.isRunning()`. If `true`, classify as **`INELIGIBLE_FALLBACK`** and use the existing `ScribeScrollDeletionPolicy` fallback cleanly (no split rendering).
2. **Idle at Deletion Start (Standard Selection Deletion):** When `C1 == 0` / Case II collapses selection `cursor.left == preDelete cursor.left`, `fromPos == toPos` so `ScribeCursorAnimator` stays idle (`!isRunning()`); Pass-2 `DrawCursorTask`s shift by `+tauLower` via `ScribeDrawCursorTaskAccessor`, placing the lower-region caret & insertion handle (`HANDLE_TYPE_INSERT`) and `insertHandleDescriptor` at exact translated coordinates.
3. **Starts During Mode 1 or Mode 2 — Full Asynchronous Event-B Lifecycle:**
   - `ScribeCursorAnimator` exposes an `onAnimatorStarted: (() -> Unit)?` callback invoked synchronously on UI thread in `start()`/`triggerGlide()` (and backed up in `ScribeCodeEditor.afterInsert` / `setSelection` plus a pre-`super.draw` safety guard checking `if (controller.shouldApplySplitRender && editor.cursorAnimator.isRunning())` inside `ScribeEditorRenderer.draw(canvas)`).
   - **Immediate Visual Fallback (`UnifiedCanvasLayout.handleCursorAnimatorActivatedDuringCollapse()`):**
     When `ScribeCursorAnimator` starts while the deletion transaction is owned (`controller.isTransactionOwned`), execute immediately:
     1. Stop the Mode-2 `Choreographer`.
     2. Call `canvasAnimator.cancelPreservingTransaction("cursor_animator_active")`.
     3. Cancel restoration.
     4. Set `tauLower = 0f`.
     5. Immediately disable split rendering by making `controller.shouldApplySplitRender == false`.
     6. Call `editor.invalidate()` so the next draw uses Sora's ordinary unsplit rendering and native `drawSelectionOnAnimation()` path.
     7. Synchronize `scrollCoordinator` from actual physical read-back:
        ```kotlin
        scrollCoordinator.syncActualScroll(
            (scrollD + editor.offsetY).toFloat()
        )
        ```
     8. Transition the controller to `FINAL_SAFETY_PENDING`, retaining ownership of generation `G`.
     *(Disabling split rendering and committing the deletion transaction to `IDLE` are separate operations: the renderer switches to unsplit drawing immediately once `tauLower == 0f` and `controller.shouldApplySplitRender == false`, even when Event B still requires asynchronous correction or layout retry. Never keep split rendering active merely because the controller has not yet reached `IDLE`.)*
   - **Route All Event-B Results Through the Existing Common Finalizers:**
     Call `completeDeletionVisualTransaction(G, safetyEpoch)` (with `[safeMinPhysical, safeMax]` validation and `clampPhysicalSafetyTarget(validation.correctiveTargetScroll)`) and handle all 6 `SafetyRevalidationResult` variants identically:

     | Result | Required Behavior |
     |---|---|
     | `VALIDATED_SAFE` | Call `finalizeCompletedGapCollapseTransaction(G, safetyEpoch)`. |
     | `CORRECTED_AND_VALIDATED` | Call `finalizeCompletedGapCollapseTransaction(G, safetyEpoch)`. |
     | `UNRESOLVED` | Call `finalizeUnresolvedGapCollapseTransaction(G, safetyEpoch)`. |
     | `CORRECTION_STARTED` | Keep `FINAL_SAFETY_PENDING` and transaction ownership. Do not clear the snapshot or advance `G`. Wait for the safety-correction operation's completion callback, read back actual physical scroll `(scrollD + editor.offsetY)`, increment `safetyEpoch`, and re-enter Event B. |
     | `DEFERRED_UNTIL_LAYOUT_READY` | Keep `FINAL_SAFETY_PENDING` and transaction ownership. Preserve the pending transaction and register the existing single generation/epoch-scoped layout retry. Re-enter Event B only when layout and wordwrap are ready. |
     | `STALE_TRANSACTION` | Stop processing this callback. Do not clear, finalize, or mutate a newer transaction. |

     Do not duplicate Event-B cleanup code inside `handleCursorAnimatorActivatedDuringCollapse()`; the existing common finalizers remain the sole owners of snapshot/listener cleanup, callback invalidation, generation advancement (`G -> G + 1`), `PendingPostSafetyKeyboardEval` consumption, and final `IDLE` commitment.
   - **Text-Action-Window Safety During Asynchronous Completion:** Keep `EditorTextActionWindow.isEnabled = false` throughout `FINAL_SAFETY_PENDING`. Never re-enable it merely because split rendering has been disabled; re-enable ONLY through the Section `4.4` post-draw cursor-descriptor refresh gate after the transaction reaches `IDLE`, an unsplit renderer pass has completed, `cursorAnimator.isRunning() == false`, and the saved generation and existing selection/focus/handle/layout guards still pass.

### 4.4 `ScribeCodeEditor` Row-Override Reset & Deferred `EditorTextActionWindow` Re-Enable Timing (`[Source-verified]` / `[Proposed]`)
- `@UiThread var renderPassRowRangeOverride: IntRange? = null` clears in `try/finally` around every pass AND in `ScribeEditorRenderer.draw(canvas)` `finally`.
- **Preventing Stale `EditorTextActionWindow` Display Before Handle Refresh (`[Source-verified]` `EditorTextActionWindow.java:214, 241, 296, 331`, `EditorRenderer.java:592-598`):**
  1. Entering split collapse (`shouldApplySplitRender` `false -> true`), capture `savedTextActionWindowEnabled = aw.isEnabled` and set `aw.isEnabled = false` (`dismiss()` immediately).
  2. Keep **`aw.isEnabled = false` throughout split collapse AND after transitioning to `IDLE`** until the first completed unsplit render pass actually executes `DrawCursorTask`s!
  3. Inside `ScribeEditorRenderer.drawView(canvas)`: record **`didRefreshCursorDescriptorsOnLastDraw = !controller.shouldApplySplitRender && !editor.cursorAnimator.isRunning()`**.
  4. Inside `ScribeEditorRenderer.draw(canvas)` after `super.draw(canvas)` finishes: notify `editor.onAfterRendererDrawPass(++renderedDrawPassSeq, didRefreshCursorDescriptorsOnLastDraw)`.
  5. On `IDLE` commit (`commitGapCollapseIdle`): if `savedTextActionWindowEnabled == true`, arm `pendingTextActionRefreshTargetSeq = renderer.renderedDrawPassSeq + 1L`, `pendingTextActionRefreshGen = deletionTransactionGeneration`, keep `aw.isEnabled = false` (blocking any queued Sora `postInLifecycle(displayWindow)` / `postDisplay` runnables), and synchronously call `editor.invalidate()`.
  6. Only when `onAfterRendererDrawPass(seq, didRefreshCursorDescriptors)` observes `!controller.shouldApplySplitRender && seq >= pendingTextActionRefreshTargetSeq && didRefreshCursorDescriptors`: clear the seq gate and post tracked `pendingTextActionRefreshRunnable` via `editor.postOnAnimation(...)`.
  7. Inside `pendingTextActionRefreshRunnable` (next UI frame outside `onDraw`): validate `isAttachedToWindow && savedTextActionWindowEnabled && cursor.isSelected && hasFocus && visibility == View.VISIBLE && deletionTransactionGeneration == pendingTextActionRefreshGen && !controller.shouldApplySplitRender && !eventHandler.hasAnyHeldHandle() && !eventHandler.isDragSelecting && !isLayoutBusyState && !editor.cursorAnimator.isRunning()`; **only now** restore `aw.isEnabled = true` and call `aw.displayWindow()`! (And remove `pendingTextActionRefreshRunnable` + clear pending tokens on detach / new deletion / cancel.)

---

## Validation & Acceptance Criteria (Suite C + B1 Option-B Cases)

Execute via the exact instrumentation and JVM Gradle tasks recorded in Phase `0.4`:
1. **Suite B1 Option-B Cases (`ScribeGapCollapseControllerTest.kt`):** Option-B mid-collapse cursor-animator abort across all 6 Event-B outcomes (Mode 1 `VALIDATED_SAFE`, Mode 2 `CORRECTED_AND_VALIDATED`, `CORRECTION_STARTED` keeping `FINAL_SAFETY_PENDING` & ownership of `G` until correction settles + fresh validation succeeds, `DEFERRED_UNTIL_LAYOUT_READY` keeping ownership with single valid retry, obsolete correction/retry callback rejected against newer generation, split rendering immediately disabled while `FINAL_SAFETY_PENDING`, `EditorTextActionWindow` safety).
2. **Suite C (`ScribeGapCollapseRendererTest.kt`):**
   - Synchronous `editor.invalidate()` on outer-only Mode-1 and Mode-2 ticks.
   - All 6 Approach-A `firstLn` cases explicitly verified, including:
     1. Unclipped `overrideFirstLnLine` rendering when `P1.isEmpty && pass2TopScreenY <= 0f` (both `y_pinned` and `y_pushed_P2`) using full-height viewport clip `[stuckLineBottom, editor.height]` left of `dividerLeftX`.
     2. Unclipped `y_pushed_P2` rendering when `pass2LineNumbers.get(0)` collides with `firstLn` (`translatedTop2 <= bottom`) in Case 4.
     3. Zero clipping regression for Case 1 (`!P1.isEmpty && postDrawLineNumbers.isNotEmpty`) and Case 2 (`!P1.isEmpty && postDrawLineNumbers.isEmpty`).
     4. Translated Pass-2 `pass2FirstLn` in Case 5 (`P1.isEmpty && pass2TopScreenY > 0f`) clipped to `[pass2ClipTop, h]` left of `dividerLeftX` + wrapped continuation row Case 6.
   - Per-layer clip/translation (`patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawSideBlockLine`, `DrawCursorTask` + Case-I lower-region insertion-handle placement), zero double-shift, Option-B cursor-animator unsplit safety, immediate split-render disable (`shouldApplySplitRender == false`) during `FINAL_SAFETY_PENDING`, and `EditorTextActionWindow` remaining disabled until the post-`IDLE` unsplit cursor-descriptor refresh gate succeeds.

## Phase-Completion Conditions

- `ScribeEditorRenderer` calls `super.draw(canvas)` once and `drawView(canvas)` once, with zero reflection/Unsafe in `ScribeDrawCursorTaskAccessor`.
- All 6 Approach-A `firstLn` cases, Option-B fallback paths, and `EditorTextActionWindow` post-draw refresh gate pass Suite C and Suite B1 Option-B tests.
