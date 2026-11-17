# Phase 5 Renderer Regression Handoff — Run #887 Wordwrap Async Layout Race Fix

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native`
**Branch:** `main`
**Baseline HEAD Inspected:** `1fb8f7c485d228206b373fc2a0a91bed63ed07b2` (last known passing CI before failure: Run `#886` / `43917616908` on `1b9f6655c5b9d7a0b8d3ae0a8fe413c4017be24e`)
**Failing CI Run Investigated:** Run `#887` (`https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43919697989`, Job `133483354087`)
**Sora Baseline Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`)
**Master Plan SHA-256 (verified intact):** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`
**Scope Note:** Narrowly scoped renderer/layout lifecycle fix only. Does NOT implement the separate V1 selection-deletion behavioral fixes.

---

## 1. Failure Investigated (Run #887)

- **Failing Test:** `ScribeGapCollapseIntegrationTest.suiteE_contentReplaceOverscrollNonOverscrollDelayedLayoutAndEmptyReplace`
- **Exception:** `java.lang.IndexOutOfBoundsException: Index 72 out of bounds for length 72`
- **Stack Trace:**
  - `io.github.rosemoe.sora.text.Content.getLine(Content.java:256)`
  - `io.github.rosemoe.sora.widget.EditorRenderer.getLine(EditorRenderer.java:344)`
  - `io.github.rosemoe.sora.widget.EditorRenderer.prepareLine(EditorRenderer.java:335)`
  - `io.github.rosemoe.sora.widget.EditorRenderer.drawRows(EditorRenderer.java:1204)`
  - `com.primaloptima.scribe.ui.components.ScribeEditorRenderer.drawRows(ScribeEditorRenderer.kt:245)`
  - `io.github.rosemoe.sora.widget.EditorRenderer.drawView(EditorRenderer.java:515)`
  - `com.primaloptima.scribe.ui.components.ScribeEditorRenderer.drawView(ScribeEditorRenderer.kt:102)`
  - `io.github.rosemoe.sora.widget.EditorRenderer.draw(EditorRenderer.java:195)`
  - `com.primaloptima.scribe.ui.components.ScribeEditorRenderer.draw(ScribeEditorRenderer.kt:78)`
  - `io.github.rosemoe.sora.widget.CodeEditor.onDraw(CodeEditor.java:4716)`
  - `com.primaloptima.scribe.ui.components.ScribeCodeEditor.onDraw(ScribeCodeEditor.kt:2537)`

Note: `git diff 1b9f6655..1fb8f7c4` added only `phase-5-handoff-report.md` with zero code changes, confirming Run #887 was an async timing/lifecycle race present in the existing editor/renderer pipeline.

---

## 2. Confirmed Root Cause

1. `suiteE_contentReplaceOverscrollNonOverscrollDelayedLayoutAndEmptyReplace` launches with `100` lines (`0..99`, 3-digit line-number gutter cached in `EditorRenderer.cachedGutterWidth`).
2. Step 1 replaces lines `10..14` with `"Replaced paragraph line
"`, reducing the document to `97` lines (`0..96`, 2-digit gutter width).
3. During the subsequent `SystemClock.sleep(80L)`, a draw frame enters `EditorRenderer.drawView()` (`EditorRenderer.java:476–479`), detects `cachedGutterWidth != gutterWidth` (3 digits -> 2 digits), and calls `ScribeCodeEditor.createLayout(false)`. That constructs a new `WordwrapLayout` reusing `rowTable` (`0..96`), sets `layoutBusy = true`, and submits background `WordwrapAnalyzeTask(0, 96)`.
4. When `WordwrapAnalyzeTask(0, 96)` finishes `compute()` on the worker thread before Step 2 and posts its `postInLifecycle` UI completion runnable (`WordwrapLayout.java:102–118`), Step 2 executes `ed.text.replace(71, 0, 96, 0, "Short replacement")` on the main thread while scrolled to `maxCanvasScroll` (near the bottom). `Content` shrinks to `72` lines (`0..71`), and `WordwrapLayout.afterDelete`/`afterInsert` updates `rowTable` in-place to `0..71` **on the same `WordwrapLayout` instance** (`WordwrapLayout.this.editor == editor` remains `true`, and `97 -> 72` keeps 2-digit gutter width).
5. The queued `postInLifecycle` runnable then runs on the UI thread, calls `rowTable.clear(); rowTable.addAll(r2);`, and **overwrites the valid `0..71` `rowTable` with the stale `0..96` `WordwrapResult`**. Because `layoutHeight` reflects the stale 97-line `rowTable`, `scrollBy(0, 0)` does not clamp `offsetY` out of lines `72..96`.
6. On the next draw frame, `EditorRenderer.prepareLines()` clamps preloading to `content.getLineCount() - 1` (`71`), while `EditorRenderer.drawRows()` iterates `WordwrapLayoutRowItr` over the stale `rowTable`, reads `line = 72`, calls `prepareLine(72) -> getLine(72) -> Content.getLine(72)`, and throws `IndexOutOfBoundsException: Index 72 out of bounds for length 72`.
7. Companion hazard: if `afterDelete`/`afterInsert` executes while `WordwrapLayout.rowTable` is empty (`createLayout(true)` in flight), `WordwrapLayout.breakLines()` inserts only the edited line(s) into `rowTable`, leaving a partial/incomplete `rowTable`.

---

## 3. Exact Correction Implemented

### `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`
- Added `hasOutOfBoundsOrPartialRowTable(lay: WordwrapLayout)`: O(1) structural invariant check verifying that when `rowTable` is non-empty, `rowCount >= text.lineCount`, `getLineNumberForRow(0) == 0`, and `getLineNumberForRow(rowCount - 1) == text.lineCount - 1` (and handling destroyed `null` `rowTable` when `rowTableField` is available).
- Added `reconcileWordwrapLayoutAfterMutation()` invoked after `super.afterDelete(...)` and `super.afterInsert(...)`: whenever `isWordwrap` is enabled and either `hasOutOfBoundsOrPartialRowTable(lay)` is true or a Sora background layout task is active (`layoutBusyField?.getBoolean(this) ?: isLayoutBusyState`), calls `createLayout(clearWordwrapCache = tableBad)`. Destroying the old `WordwrapLayout` sets `oldLayout.editor = null`, causing any in-flight or already-queued stale `WordwrapAnalyzeTask` completion runnable to exit at `if (WordwrapLayout.this.editor != editor) return;` without overwriting `rowTable`, while preserving valid `rowTable` updates when `!tableBad`.
- Updated `createLayout(clearWordwrapCache: Boolean)` so even `EditorRenderer.drawView()`'s `createLayout(false)` refuses to carry forward an out-of-bounds or partial `rowTable`.
- Updated `isWordwrapReady()` to return `false` if `hasOutOfBoundsOrPartialRowTable(curLayout)` holds.
- Added `ensureWordwrapLayoutConsistentBeforeRender()` to detect any out-of-bounds, partial, or unexpectedly missing idle `rowTable` prior to drawing and clear/recreate the layout cleanly (`clearWordwrapCache = true`, invalidating render nodes).

### `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeEditorRenderer.kt`
- Invoked `scribeEditor.ensureWordwrapLayoutConsistentBeforeRender()` at the start of `drawView(canvas)` before `prepareSplitFrameState()` and `super.drawView(canvas)` so rendering never traverses stale `WordwrapLayout` row-to-line mappings.

### `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseIntegrationTest.kt`
- Preserved all existing assertions in `suiteE_contentReplaceOverscrollNonOverscrollDelayedLayoutAndEmptyReplace` and added post-settlement bitmap rendering (`drawEditorIntoBitmap(ed)`) plus layout row-to-line and scroll-bounds verification.
- Added `suiteE_wordwrapAsyncGutterShrinkAndSubstantialLineCountDropRendersCleanly` covering:
  1. Substantial shrinkage (`120 -> 98 -> 40` lines) crossing the 3-digit to 2-digit gutter boundary while scrolled to bottom with immediate and settled `drawEditorIntoBitmap(ed)` verification.
  2. Large document scrolled to bottom with async `createLayout(false)` in flight (`horizontalPaddingDp = 12f`), shrinking across `120 -> 9` lines (3-digit -> 1-digit), immediate + delayed + settled bitmap draws, and row-to-line/scroll-bounds assertions.
  3. Large document scrolled to bottom with async `createLayout(false)` in flight (`horizontalPaddingDp = 10f`), empty replacement (`100 -> 30` lines), immediate + settled bitmap draws, and row-to-line/scroll-bounds assertions.

---

## 4. Verification Status

- **Local Verification:** NOT RUN. Per project policy, no Gradle builds, JVM unit tests, Android instrumentation tests, or lint tasks were executed locally in this workspace.
- **Master Plan Integrity:** Verified `docs/plans/selection-deletion-gap-collapse/implementation_plan.md` SHA-256 remains `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`.
- **Remote CI Status:** Committed and pushed to `main` for GitHub Actions validation; CI results must be checked on the triggered workflow run before declaring the run green.
