# Phase 4 Handoff Report — Single-Lifecycle `ScribeEditorRenderer`, Per-Layer Strategy & `EditorTextActionWindow` Refresh

- **Baseline Starting Commit:** `7caaae2a7fa4eb01f811df45fe3868600817d517` (`fix(ci): pass emulator Gradle command on a single line for android-emulator-runner`)
- **Authoritative Master Plan:** `docs/plans/selection-deletion-gap-collapse/implementation_plan.md` (SHA-256 `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`, unmodified)
- **Phase Specification:** `docs/plans/selection-deletion-gap-collapse/phase-4.md`
- **Hard Prerequisite Satisfied:** Phase 0 Step `0.2` (`editor-0.24.6.aar` `javap -p` bytecode verification) confirmed direct non-reflective same-package access to `EditorRenderer$DrawCursorTask.y` and all 9 protected `EditorRenderer` method names (11 signatures).
- **Build & Test Execution Status:** **Implementation completed; execution-based validation pending.** Per strict Phase 4 resource instructions, zero Gradle builds, JVM tests, lint checks, or instrumentation runs were executed locally. All changes were verified statically.

---

## 1. Files Created & Modified in Phase 4

### Production Files
1. **Created:** `app/src/main/java/io/github/rosemoe/sora/widget/ScribeDrawCursorTaskAccessor.java`
   - Package-private-equivalent Java bridge residing in `io.github.rosemoe.sora.widget` exposing `addOffsetY(EditorRenderer.DrawCursorTask, float)` and `getY(EditorRenderer.DrawCursorTask)` via direct access to `task.y`.
   - Zero reflection, zero `setAccessible`, zero `Unsafe`.
2. **Created:** `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeEditorRenderer.kt`
   - `open class ScribeEditorRenderer(private val scribeEditor: ScribeCodeEditor) : EditorRenderer(scribeEditor)`.
   - Single native `super.draw(canvas)` and `super.drawView(canvas)` lifecycle (no second native draw lifecycle, no offscreen bitmap snapshot).
   - Pre-`super.draw(canvas)` and pre-`super.drawView(canvas)` Option-B defensive guards checking `controller.shouldApplySplitRender && scribeEditor.cursorAnimator.isRunning` -> `handleCursorAnimatorActivatedDuringCollapse()`.
   - Strictly disjoint Pass 1 (`[normalFirstVis, minOf(seamRow - 1, normalLastVis)]`) and Pass 2 (`[maxOf(seamRow, shiftedFirstVis), minOf(totalRows - 1, shiftedLastVis)]`) row partitions across all 10 draw layers:
     - `buildMeasureCacheForLines` Pass-2 line-range measure cache warming (`isWarmingPass2Cache` guard, converting Pass-2 rows via `layout.getLineNumberForRow(...)`, calling 2-arg `buildMeasureCacheForLines(pass2FirstLine, warmEnd)` with `useCachedContent = false`).
     - `drawRows` Pass 1 (`postDrawLineNumbers`, `postDrawCursor`, `postDrawCurrentLines`, `pass1FirstLn`) unshifted under `clipRect(0f, stuckBottom, w, pass1ClipBottom)`, and Pass 2 (`framePass2LineNumbers`, `pass2CursorTasks`, `framePass2CurrentLines`, `pass2FirstLn`) under `clipRect(0f, pass2ClipTop, w, h)` **before** `canvas.translate(0f, frameTauLower)`, shifting `pass2CursorTasks` once via `ScribeDrawCursorTaskAccessor.addOffsetY(task, frameTauLower)`.
     - `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground` (painting `framePass2CurrentLines` via `super.drawRowBackground` + `super.drawUserGutterBackground`), `drawSideIcons` (Pass 1 + Pass 2 + Approach-A 6-case `firstLn` + `framePass2LineNumbers`), and `drawSideBlockLine` (`block.startLine < frameSeamLine` in Pass 1; `block.endLine >= frameSeamLine` in Pass 2).
   - Full implementation of all 6 Approach-A `firstLn` collision, gap, and wrapped continuation cases (`Cases 1–6`), using `clipRect(0f, frameStuckLineBottom, w, h)` for unshifted `overrideFirstLnLine` (`y_pinned` / `y_pushed_P2` strictly left of `dividerLeftX`) and `clipRect(gutterLeft, framePass2ClipTop, dividerLeftX, h)` + `translate(0f, frameTauLower)` for `framePass2LineNumbers` and Case-5 `framePass2FirstLn`.
   - Tracks `renderedDrawPassSeq` and `didRefreshCursorDescriptorsOnLastDraw = !isSplitFrameActive && !scribeEditor.cursorAnimator.isRunning`, resetting `renderPassRowRangeOverride = null` and per-frame state in `finally`.
3. **Modified:** `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCursorAnimator.kt`
   - Added `var onAnimatorStarted: (() -> Unit)? = null` invoked synchronously on UI thread at the end of `triggerGlide()` and `start()`.
   - Attached an internal `AnimatorListenerAdapter` calling `editor.postInvalidateOnAnimation()` on animation end so deferred `EditorTextActionWindow` refresh catches the next unsplit draw pass reliably.
4. **Modified:** `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`
   - Overrode `onCreateRenderer(): EditorRenderer` returning `ScribeEditorRenderer(this)` and exposed computed `scribeEditorRenderer` (avoiding `CodeEditor.<init>` field-clobbering).
   - Added `@get:UiThread @set:UiThread internal var renderPassRowRangeOverride: IntRange? = null` and overrode `getFirstVisibleRow()` / `getLastVisibleRow()`.
   - Wired `ScribeCursorAnimator.onAnimatorStarted` in `init`, plus Option-B backup checks in `afterInsert(...)` and `setSelection(int, int, boolean, int)`.
   - Implemented the Section 4.4 `EditorTextActionWindow` suppression & post-`IDLE` unsplit cursor-descriptor refresh gate (`onGapCollapseSplitEntered`, `onGapCollapseCommitIdle`, `onAfterRendererDrawPass`, `restoreTextActionWindowIfFallbackStranded`, `cancelPendingTextActionRefresh`) and cleaned up pending refresh tokens on `onDetachedFromWindow()`.
5. **Modified:** `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`
   - Added `internal fun handleCursorAnimatorActivatedDuringCollapse()` implementing the full Option-B mid-collapse abort (`cancelPreservingTransaction("cursor_animator_active")`, `cancelRestoration()`, physical scroll sync `(scrollD + editor.offsetY).toFloat()`, `gapCollapseController.restartFinalSafetyClaim(capturedGen)`, `editor.invalidate()`, routing all 6 Event-B outcomes through `completeDeletionVisualTransaction`).
   - Wired `onGapCollapseCommitIdle(completedGen)` in `commitGapCollapseIdle()`, `onGapCollapseSplitEntered()` in `prepareGapCollapseFrame1IfReady()` when `shouldApplySplitRender` is true, `cancelPendingTextActionRefresh(clearSavedState = false)` in `beginPreEditDeletionSnapshot()`, and `restoreTextActionWindowIfFallbackStranded(fromGen)` on fallback exit.

### Unexecuted Test Files Added / Updated
1. **Updated (Suite B1 Option-B):** `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt`
   - Added `OptionBCursorAbortHarness` and `testOptionB_MidCollapseCursorAnimatorAbort_AllSixEventBOutcomesAndTextActionGate` covering Mode-1 `VALIDATED_SAFE`, Mode-2 `CORRECTION_STARTED` -> `CORRECTED_AND_VALIDATED`, `DEFERRED_UNTIL_LAYOUT_READY` -> retry, `UNRESOLVED`, `STALE_TRANSACTION`, immediate split-render disable (`shouldApplySplitRender == false`, `tauLower == 0f`) while `FINAL_SAFETY_PENDING`, and `EditorTextActionWindow` gating while `cursorAnimator` is running or in `FINAL_SAFETY_PENDING` / pre-draw `IDLE`.
2. **Created (Suite C):** `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt`
   - Instrumentation suite with `RecordingCanvas` + real `UnifiedCanvasLayout` / `ScribeCodeEditor` in `CrashActivity` covering:
     - `testRendererLifecycleDisjointnessCacheWarmAndRowOverrideReset` (single `super.draw` seq increment, strictly disjoint `P1`/`P2`, Pass-2 `buildMeasureCacheForLines` cache warming, `renderPassRowRangeOverride` cleanup).
     - `testApproachA_AllSixFirstLnCases_IncludingWrappingAndCollision` (all 6 Approach-A `firstLn` cases including wrapped continuation rows and `y_pushed_P2` collision).
     - `testPerLayerClipTranslationAndLowerInsertionHandleShiftZeroDoubleShift` (Diagnostics, `CodeBlock`, `LineSideIcon`, `LineGutterBackground`, and Case-I lower-region `insertHandleDescriptor.position` shifted by `+tauLower` once with zero double-shift).
     - `testSyncOuterOnlyInvalidation_OptionBCursorAnimator_AndTextActionWindowGate` (outer-only `scrollD < headerHeight` Mode-1/Mode-2 `invalidate()`, Option-B `cursorAnimator.triggerGlide` abort, and `EditorTextActionWindow` remaining disabled until `didRefreshCursorDescriptorsOnLastDraw == true`).

---

## 2. Static Verification Performed (Zero Builds / Tests Executed)

- Verified `implementation_plan.md` SHA-256 unchanged (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`).
- Verified zero reflection, `setAccessible`, or `Unsafe` in `ScribeDrawCursorTaskAccessor.java`, `ScribeEditorRenderer.kt`, and `ScribeCursorAnimator.kt`.
- Verified `ScribeEditorRenderer` calls `super.draw(canvas)` once and `super.drawView(canvas)` once, resets `renderPassRowRangeOverride = null` in every pass `finally` and `draw` / `drawView` `finally`, and strictly preserves Sora `0.24.6` without version upgrades or multi-seam / Phase 5–6 scope leaks.
- Traced all 11 protected `EditorRenderer` bytecode signatures from `phase-0-handoff-report.md` and upstream Sora `0.24.6` source (`87055c459e346cac6c619b3350f0bfba076228cc`).
- Ran `git diff --check` cleanly across all modified files.

---

## 3. Handoff Contract & Ready Interfaces for Phase 5

1. **`ScribeGapCollapseController` Row/Position Translation Helpers:**
   - Already expose `getTranslationForRow(rowIndex)` and `getTranslationForPosition(line, col)` for Phase 5 touch hit-testing (`isTouchOnHandle`) and `untranslateYOffset(yOffset)` inside `ScribeCodeEditor`.
2. **Single-Lifecycle `ScribeEditorRenderer` Ready:**
   - Phase 5 touch untranslation, incremental `(seamLine, 0)` insertion rebasing, Policy A/B internal mutation routing, and serialized height resize can rely cleanly on `ScribeEditorRenderer`'s single `draw` pass, `didRefreshCursorDescriptorsOnLastDraw`, and `EditorTextActionWindow` refresh gate without modifying `ScribeDrawCursorTaskAccessor`.
3. **Suite C & Suite B1 Pending Execution:**
   - Pending CI / emulator validation via `:app:testDebugUnitTest` (Suite B1 Option-B) and `:app:connectedDebugAndroidTest` (Suite C).
