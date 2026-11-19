# Selection Deletion Gap Collapse — Stage 1 Robustness & Synchronization Handoff Report

**Base Commit:** `a487027`  
**Scope:** Stage 1 remediation of runtime blockers, safety-floor conflicts, animation/scroll desynchronization, and UI interference preventing Scenarios A–E from executing smoothly on real Android devices. (Broad dead-code/test pruning remains deferred to Stage 3.)

---

## 1. Root Causes Confirmed & Resolved

1. **Unconditional `24dp` Bottom Breathing Margin Safety Conflict (`UnifiedCanvasLayout.kt`)**
   - `resolveSafetyFloorAndBounds` previously forced `minSafeScroll` using full `breathingMarginPx` (`24dp`) during selection-deletion collapse planning (`prepareGapCollapseFrame1IfReady`) and Event B (`completeDeletionVisualTransaction`).
   - Selecting and deleting text whose lower endpoint sat near the bottom edge triggered an immediate upward `minSafeScroll` clamp or a post-collapse `SAFE_BOUNDARY_CORRECTION` jump (~`72px` on xxhdpi).
   - **Fix:** Added `breathingMarginOverridePx` to `resolveSafetyFloorAndBounds` and passed `0f` for active selection-deletion plans in Scenarios A–D (retaining `breathingMarginPx` for Scenario E caret-follow and non-selection deletions).

2. **Two-Clock Physics / Animator Handoff Desynchronization (`ScribeGapCollapsePolicy.kt`, `ScribeGapCollapseController.kt`, `UnifiedCanvasLayout.kt`)**
   - Physical scrolling previously ran on `ScribeCanvasGlideAnimator` (`DURATION_DELETION_ANCHOR_MS`) and handed off to a closed-form spring Mode-2 Choreographer loop via velocity EMA estimation, producing visible micro-jumps, `0.5px`–`1px` rounding drift, Mode-1->Mode-2 velocity mismatches, and non-monotonic easing across Scenarios B/C (`0 < S_pre < deltaGapPx`) and sub-1px Scenario D scrolls.
   - **Fix:** Added a single-clock Choreographer-driven unified progress engine (`beginUnifiedCollapse`, `onUnifiedFrame`) using pure-Kotlin Material `evaluateFastOutSlowIn` over `UNIFIED_COLLAPSE_DURATION_MS = 240L`. On each frame:
     - `evaluateUnifiedProgressDesiredScroll` computes target physical scroll directly from progress `p`.
     - `onApplyUnifiedScrollTo` drives `scrollCanvasBy` after subtracting `outerScrollResidualPx` and `editorScrollResidualPx` under `PhysicalScrollOrigin.GAP_COLLAPSE_MODE_1`.
     - `computeUnifiedProgressTauLower` compensates exact integer scroll rounding quantization error (`actualScrollPx - desiredScrollPx`) frame-by-frame so surviving lower content remains `0.00px` stationary in Scenarios B/C until top-of-document shortfall `S_pre == 0` seamlessly finishes via decaying `tauLower` on the same curve.
     - Legacy `beginCollapse` / Mode-1 / Mode-2 APIs remain intact for existing synthetic tests pending Stage 3 cleanup.

3. **False Eligibility & Visibility Vetoes (`ScribeCodeEditor.kt`, `ScribeGapCollapsePolicy.kt`)**
   - `editor.hasComposingText()` previously stalled Frame 1 (`GAP_COLLAPSE_PENDING_COMPOSITION`) and `capturePreDeleteHandleWithStatus` rejected handles when `!isFocused`, `isInMouseMode`, or `IME_CONSUMING_INPUT`.
   - `FloatingPill` overlays vetoed handle visibility and selection spans, frequently misclassifying Scenarios B/C/D as `INELIGIBLE_FALLBACK` or Scenario E.
   - `capturePreDeleteSelectedRowSpansDoc` iterated all `TextRow` regions even when wordwrap guarantees full line-width spans.
   - Gboard Backspace on a selected range with active composing text bypassed Sora `#170` (`editor.deleteText()`) in `InputConnection.deleteSurroundingText(1, 0)`.
   - **Fix:**
     - Removed Frame-1 composing veto in `UnifiedCanvasLayout` and `!isFocused` / `isInMouseMode` / `IME_CONSUMING_INPUT` vetoes in `capturePreDeleteHandleWithStatus`.
     - Added `finishComposingText()` compatibility handling in `ScribeCodeEditor.onCreateInputConnection`'s `deleteSurroundingText(1, 0)` when `cursor.isSelected && hasComposingText()`.
     - Removed `FLOATING_PILL` occlusion vetoes from `isHandleVisibleInViewport` and `resolveSelectionVisibilityDecision`, and added an O(1) wordwrap vertical-overlap fast path in `resolveSelectionVisibilityDecision`.

4. **Concurrent UI Interference During Collapse (`MainEditorScreen.kt`, `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt`)**
   - `floatingPillsVisible` reacted to collapse-driven upward scrolls in `MainEditorScreen.kt` (`onScrollDelta`, `onUnifiedScrollChanged`, `hideOnScrollConnection`, `setOnScrollChangeListener`), popping HUD pills open mid-collapse.
   - `setInlayHints`, `setDiagnostics`, and `refreshVisualFirstLineIndentHints` could mutate layout/invalidation while collapse owned the transaction.
   - Small IME toolbar `onSizeChanged` height changes (`< 120dp`) aborted active gap collapse.
   - **Fix:** Added `UnifiedCanvasLayout.isGapCollapseOrSelectionDeletionActive` to guard `MainEditorScreen` pill updates; expanded `ScribeCodeEditor.isSelectionDeletionOrCollapseDeferringEffects` and flushed deferred hints/diagnostics on collapse teardown; guarded `onSizeChanged` height-resize abort behind `heightDeltaPx >= 120f * density || isKeyboardDismissing || dockTransition == IME_DISMISSAL`.

---

## 2. Modified & Updated Files

### Production Code (`app/src/main`)
- `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicy.kt`
- `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseController.kt`
- `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt`
- `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`
- `app/src/main/java/com/primaloptima/scribe/ui/screens/MainEditorScreen.kt`

### Tests Updated / Added (`app/src/test` & `app/src/androidTest`)
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicyTest.kt`
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt`
- `app/src/test/java/com/primaloptima/scribe/ui/components/UnifiedCanvasPointerAndOriginIntegrationTest.kt`
- `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseIntegrationTest.kt`
- `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt`

---

## 3. Verification Status

- Changes have been inspected via `git diff` across all modified production, test, and documentation files.
- **Unverified Locally:** Per project workflow constraints, Gradle compilation, JVM tests, instrumentation tests, and lint were **not** executed locally and await verification via GitHub Actions CI after push to `origin/main`.

---

## 4. Post-CI Run `44572669861` Follow-Up Fixes

1. **`UnifiedCanvasLayout.onSizeChanged` Micro-Resize Isolation (`UnifiedCanvasLayout.kt`)**
   - Guarded the entire `w == oldw && gapCollapseController.isTransactionOwned` branch so non-dismissing IME height changes `< 120dp` return early instead of falling through to `evaluateHeightShrinkCursorFollow` or `scheduleHeightExpandContractionRestoration`.
2. **Remaining Gboard `InputConnection` Selection Paths (`ScribeCodeEditor.kt`)**
   - Added `deleteSurroundingTextInCodePoints(1, 0)` forwarding to `deleteSurroundingText(1, 0)` when `cursor.isSelected && isEditable` (guarded by API `>= N` for `super`).
   - Intercepted `setComposingText(text, newCursorPosition)` when `cursor.isSelected && !hasComposingText() && isEditable && acceptsComposingText() && !props.disallowSuggestions`:
     - Empty `text` delegates to `deleteSurroundingText(1, 0)` to run clean gap collapse without a trailing empty `commitText`.
     - Single-line non-empty `text` replaces atomically via `Content.replace(...)` (inside `beforeReplace`/`afterInsert`), restores composing region via `setComposingRegion`, and calls `updateCursor()`.
3. **Instrumentation Test Remediation & Per-Frame `RecordingCanvas` Verification (`ScribeGapCollapseRendererTest.kt`, `ScribeGapCollapseIntegrationTest.kt`)**
   - Fixed stale hardcoded generation arguments (`10L` / `50L`) in `ScribeGapCollapseRendererTest` to use `canvas.currentDeletionTransactionGeneration`.
   - Updated `ScribeGapCollapseIntegrationTest` (`suiteD_serializedHeightResizeAndScrollDFloatInvariant`, `suiteE_unfocusedHandleSuppressionRoutesVisibleSelectionToScenarioD`, `suiteE_realWrappedLineDeletionLeavingEmptyLineTriggersDeferredIndentFloorInsideCollapse`, and `suiteF_stage1RealBottomSelectionGboardBackspaceAndShortfall`) for Stage 1 behavior and emulator density bounds, plus added `deleteSurroundingTextInCodePoints` and `setComposingText` selection cases to `suiteF`.
   - Added `testRealInput_PerFrameScreenYInvariants_ScenariosC_A_B_D_E` in `ScribeGapCollapseRendererTest` verifying real `RecordingCanvas` drawn text screen-Y invariants across Frame 0, Frame 1, intermediate frames, and settled `IDLE` for Scenarios C, B, A, D, and E.
