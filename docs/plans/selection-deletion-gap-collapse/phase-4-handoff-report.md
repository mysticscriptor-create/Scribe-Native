# Phase 4 Handoff Report — Renderer, Option B & TextActionWindow Integration (Hardened & Repaired)

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native`
**Branch:** `main`
**Current Base Commit Remediated From:** `b514051166889c2bd19d4e6eeeb25eba82f1627c`
**Latest CI Run Remediated:** `43827526440` (Run `#871`: `:app:testDebugUnitTest` `432 tests completed, 1 failed` at `UnifiedCanvasPointerAndOriginIntegrationTest.kt:186`)
**Prior Repaired Commit / Run:** `a503f9be3141d1c35520ca583f0da32d20277f41` / `43819532310` (`:app:compileDebugUnitTestKotlin` failure)
**Previous Phase 4 / CI Baseline:** `7caaae2a7fa4eb01f811df45fe3868600817d517`
**Sora Baseline Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`)
**Master Plan SHA-256 (verified intact):** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`

---

## 1. Phase 4 Deliverables (Implemented — Execution Pending CI)

### Production Files Implemented / Hardened
1. **Created:** `app/src/main/java/io/github/rosemoe/sora/widget/ScribeDrawCursorTaskAccessor.java`
   - Package-private bridge helper in `io.github.rosemoe.sora.widget` exposing `addOffsetY(EditorRenderer.DrawCursorTask, float)` and `getY(EditorRenderer.DrawCursorTask)` via direct access to `task.y`.
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
   - **Pass-1 `postDrawLineNumbers` clipping audit:** Inspected upstream Sora `0.24.6` `EditorRenderer.java` (`548–578`, `1350–1359`). Because `drawRows` Pass 1 runs under `renderPassRowRangeOverride = framePass1RowRange` (`rw <= seamRow - 1`), every row in `postDrawLineNumbers` has `rowBottom(rw) - offsetY <= rowTop(seamRow) - offsetY == seamScreenYView <= framePass1ClipBottom < framePass2ClipTop`. Upstream `548` `clipRect(0, stuckLineBottom, width, height)` also carries Case-1 `firstLn` (`549`) which intentionally must not be clipped by `pass1ClipBottom` across Cases 1–4.
   - Tracks `renderedDrawPassSeq` and `didRefreshCursorDescriptorsOnLastDraw = !isSplitFrameActive && !scribeEditor.cursorAnimator.isRunning`, resetting `renderPassRowRangeOverride = null` and per-frame state in `finally`.
3. **Modified:** `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCursorAnimator.kt`
   - Added `var onAnimatorStarted: (() -> Unit)? = null` invoked synchronously on UI thread at the end of `triggerGlide()` and `start()`.
   - Attached an internal `AnimatorListenerAdapter` calling `editor.postInvalidateOnAnimation()` on animation end so deferred `EditorTextActionWindow` refresh catches the next unsplit draw pass reliably.
4. **Modified & Hardened (Repair 2):** `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`
   - Overrode `onCreateRenderer(): EditorRenderer` returning `ScribeEditorRenderer(this)` and exposed computed `scribeEditorRenderer`.
   - Added `@get:UiThread @set:UiThread internal var renderPassRowRangeOverride: IntRange? = null` and overrode `getFirstVisibleRow()` / `getLastVisibleRow()`.
   - Wired `ScribeCursorAnimator.onAnimatorStarted` in `init`, plus Option-B backup checks in `afterInsert(...)` and `setSelection(int, int, boolean, int)`.
   - Hardened the Section 4.4 `EditorTextActionWindow` refresh gate (`pendingTextActionRefreshDrawReady`, `pendingTextActionRefreshBlockedByLayoutOrAnimator`, `schedulePendingTextActionRefreshIfNeeded()`, `reevaluatePendingTextActionRefreshIfNeeded()`):
     - Transient blockers (`isLayoutBusy`, `cursorAnimator.isRunning`, `hasAnyHeldHandle`, `isDragSelecting`, `!hasFocus()`, `visibility != View.VISIBLE`) no longer strand `EditorTextActionWindow` disabled.
     - Event-driven re-evaluation wired into `HandleStateChangeEvent` (`!isHeld`), `SelectionChangeEvent`, `LayoutStateChangeEvent` (`!isLayoutBusy`), `createLayout()` `finally`, `onTouchEvent` (`ACTION_UP`/`ACTION_CANCEL`), `onFocusChanged`, `onWindowFocusChanged`, `onVisibilityChanged`, `onWindowVisibilityChanged`, and `onAttachedToWindow`.
     - Blocking inside `isLayoutBusy` or `cursorAnimator.isRunning` resets `pendingTextActionRefreshDrawReady = false` and arms `pendingTextActionRefreshTargetSeq = renderedDrawPassSeq + 1L` so restoration still waits for a fresh unsplit `!isRunning` draw pass.
     - Hardened `onDetachedFromWindow()` to capture `wasOriginallyEnabled = savedTextActionWindowEnabled`, cancel pending callbacks/tokens (`clearSavedState = true`), and restore `aw.isEnabled = true` safely **without** calling `aw.displayWindow()` while detached (preserving `false` if originally disabled).
     - Hardened `restoreTextActionWindowIfFallbackStranded(expectedGen)` to re-arm whenever `pendingTextActionRefreshGen != expectedGen` or no refresh is armed while `savedTextActionWindowEnabled == true`.
5. **Modified & Hardened (Repair 2 & Option-B):** `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`
   - Implements `internal fun handleCursorAnimatorActivatedDuringCollapse()` routing all 6 Event-B `SafetyRevalidationResult` outcomes through `completeDeletionVisualTransaction` and common finalizers (`finalizeCompletedGapCollapseTransaction` / `finalizeUnresolvedGapCollapseTransaction`) without duplicating teardown logic.
   - Updated `requiresOffscreenCaretFollow` (`1517`), `plan.isFallback` (`1527–1533`), and `claimAndClearLiveDeletionTransaction` (`793`) so post-increment `deletionTransactionGeneration` is passed to `restoreTextActionWindowIfFallbackStranded(...)` whenever non-collapse / fallback transactions finish cleanly in `GapCollapsePhase.IDLE` (`!isTransactionOwned`).

### Test Files Fixed & Strengthened (Unexecuted Locally — Pending CI Run)
1. **Fixed & Verified Statically (Repair 1, Suite B1 Option-B):** `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt`
   - Fixed the confirmed CI `:app:compileDebugUnitTestKotlin` compilation error at line 1056 (`expectedSafetyEpoch = 999L` -> `capturedSafetyEpoch = 999L`).
   - Audited all `finishCorrectionAndReenter`, `runEventB`, and controller harness call sites in JVM tests.
2. **Hardened & Extended (Repairs 2.4, 3, 4, 5, Suite C):** `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt`
   - Fixed `launchHost()` to size `UnifiedCanvasLayout` via explicit `FrameLayout.LayoutParams(viewportWidthPx, viewportHeightPx)` (`720x960`) and await `ed.windowToken != null` so emulator screen size does not expand editor width or break wrapping bounds.
   - Fixed `drawEditorInViewSpace()` to invoke `editor.renderer.draw(rec)` directly in view space (matching `CodeEditor.onDraw` without spurious `rec.translate(-offsetX, -offsetY)`).
   - Fixed CI `disable-animations: true` (`animator_duration_scale == 0`) determinism in `testSyncOuterOnlyInvalidation_OptionBCursorAnimator_AndTextActionWindowGate` by wrapping `realAnimator` with a delegating `CursorAnimator` (`override fun isRunning() = simulateAnimatorRunning || realAnimator.isRunning()`) while still triggering real `realAnimator.triggerGlide(...)` -> `onAnimatorStarted`.
   - Extended `RecordingCanvas` to capture `drawRect`, `drawRoundRect`, `drawColor`, `drawPath`, `drawText`, `drawTextRun` (`CharArray`/`CharSequence`), `drawLine`, `saveLayer`, and balanced `save`/`restore` depth (`assertBalancedAndZeroTranslation()`).
   - Strengthened `testApproachA_AllSixFirstLnCases_IncludingWrappingAndCollision` with Case-1 screen-space boundary assertions proving Pass-1 `postDrawLineNumbers` never paint across `[pass1ClipBottom, pass2ClipTop]` or overlap Pass-2.
   - Strengthened `testPerLayerClipTranslationAndLowerInsertionHandleShiftZeroDoubleShift` across all 10 layers (`WHOLE_BACKGROUND`, `buildMeasureCacheForLines`, `drawRows` selection + `CURRENT_LINE` + text + no duplicate rows, `patchHighlightedDelimiters` with `BracketsProvider` `{`/`}`, `drawDiagnosticIndicators` `WAVY_LINE`, `LINE_NUMBER_BACKGROUND` + `LineGutterBackground` + `LineSideIcon` + `LINE_DIVIDER` + Pass 1/2 line numbers, `drawSideBlockLine`, P1 unshifted caret + Case-I P2 `insertHandleDescriptor.position` shifted ONCE with zero double-shift).
   - Added `testOptionBEventB_SafetyCorrectionAndDeferredLayoutRetryOnRealHost` exercising Event-B `CORRECTION_STARTED -> CORRECTED_AND_VALIDATED` (using a 60-line doc with lower selection at line 50 and scroll at `headerHeight`, verifying physical scroll dynamically below `safeMinPhysical` enters `FINAL_SAFETY_PENDING` with `isGlideRunning` and settles inside `[safeMinPhysical, safeMax]`) and `DEFERRED_UNTIL_LAYOUT_READY -> OnLayoutChangeListener retry` on the real `UnifiedCanvasLayout` host.
   - Added `testEditorTextActionWindow_AllSevenLifecycleCases` covering all 7 `EditorTextActionWindow` lifecycle cases (layout-busy block/clear, animator-running block/clear, `!hasFocus()` and `View.INVISIBLE` temporary blocks + recovery without new deletion, detach cleanup without popup display, reattach state preservation, new-transaction generation invalidation, and preserving pre-existing `isEnabled == false`).
3. **Remediated & Extended (CI #871 Fix, Suite B2 Option-B):** `app/src/test/java/com/primaloptima/scribe/ui/components/UnifiedCanvasPointerAndOriginIntegrationTest.kt`
   - Confirmed CI #871 (`43827526440`) failure root cause: in `testRealHostDispatchTouchEvent_FreezesMode1AnimatorPreservingTransactionAndResumesToMode2`, stationary `ACTION_DOWN -> ACTION_UP` without drag slop reaches Sora `onSingleTapUp` -> `setSelection(CAUSE_TAP)` -> `ScribeCursorAnimator.start()` -> `onAnimatorStarted` -> `handleCursorAnimatorActivatedDuringCollapse()`. Because `isTransactionOwned` includes `POINTER_FROZEN` per `phase-4.md` §4.3 (`shouldApplySplitRender == true`), Phase 4 Option B legitimately immediately evaluates Event B (`POINTER_FROZEN -> FINAL_SAFETY_PENDING -> VALIDATED_SAFE -> IDLE`) before `onPointerGestureEndedIfNeeded`.
   - Isolated Phase 3's pointer-freeze/Mode-2-resume test from unrelated tap cursor gliding via `canvas.editor.isCursorAnimationEnabled = false` without weakening production Option-B safety.
   - Added `testRealHostPointerFrozen_CursorAnimatorActivationTriggersOptionBAndFinalizesViaEventB()` validating both Case 1 (stationary `ACTION_DOWN -> ACTION_UP` tap under `POINTER_FROZEN` triggers real `CAUSE_TAP` -> `ScribeCursorAnimator.start()` -> Option-B `VALIDATED_SAFE -> IDLE`) and Case 2 (unsafe physical scroll `< dynamic safeMinPhysical` under `POINTER_FROZEN` -> `ScribeCursorAnimator.triggerGlide()` holds `FINAL_SAFETY_PENDING` through async `CORRECTION_STARTED` and converges via `CORRECTED_AND_VALIDATED -> IDLE` inside `[safeMinPhysical, safeMax]`).

### CI Workflow Hardening
- **Modified:** `.github/workflows/build.yml`
  - Added `Upload JVM unit test reports` (`if: always()`, `actions/upload-artifact@v4`, `scribe-unitTest-reports-${{ github.sha }}`, uploading `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/`, 14-day retention, `if-no-files-found: warn`) so unit test failure details remain directly downloadable when `testDebugUnitTest` fails.

---

## 2. Static Verification Performed (Zero Local Builds / Tests Executed)

- Verified `implementation_plan.md` SHA-256 unchanged (`c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`) and `phase-4.md` unmodified; verified `.github/workflows/build.yml` YAML syntax cleanly.
- Verified zero reflection, `setAccessible`, or `Unsafe` in `ScribeDrawCursorTaskAccessor.java`, `ScribeEditorRenderer.kt`, `ScribeCursorAnimator.kt`, and `ScribeGapCollapseRendererTest.kt`.
- Verified zero `finishAnimation(` calls outside `ScribeScrollCoordinator.kt`.
- Verified zero premature Phase 5/6 symbols (`untranslateYOffset`, multi-seam splitting, `C1 > 0` horizontal split, Sora `0.24.7` upgrade).
- Verified all Sora `0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`) signatures used by production and test code (`BracketsProvider`, `PairedBracket`, `AnalyzeManager`, `EditorStyleDelegate`, `EditorTextActionWindow`, `EditorRenderer`, `LayoutStateChangeEvent`).
- Ran `git diff --check` cleanly across all modified files.
- **No Gradle build, compilation, lint, or test task was executed locally.** Full compilation and test execution (`:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:connectedDebugAndroidTest`) will run in GitHub Actions after push.

---

## 3. Handoff Contract & Ready Interfaces for Phase 5

1. **`ScribeGapCollapseController` Row/Position Translation Helpers:** Already expose `getTranslationForRow(rowIndex)` and `getTranslationForPosition(line, col)` for Phase 5 touch hit-testing (`isTouchOnHandle`) and `untranslateYOffset(yOffset)`.
2. **Single-Lifecycle `ScribeEditorRenderer` & Hardened `EditorTextActionWindow` Gate:** Ready for Phase 5 touch untranslation, `(seamLine, 0)` incremental insertion rebasing, Policy A/B internal mutation routing, and height-resize serialization.
3. **CI Verification Pending:** Confirm the triggered GitHub Actions workflow on `main` completes green across `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:connectedDebugAndroidTest` before beginning Phase 5.
