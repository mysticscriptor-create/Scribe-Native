# Scribe Native — Selection-Deletion Gap Collapse: Final Architecture-Validated Plan

**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`  
**Branch / HEAD SHA:** `main` @ `dfe1d135428d4c49f8390d2b01cf4246416b3857`  
**Sora Editor Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`), `minSdk = 23`  
**Companion Documents:** `codebase_audit_findings.md`  
**Status:** Final implementation-readiness plan — **zero source edits, builds, tests, commits, or pushes executed.**

**Provenance Key Used Throughout:**
- **`[Source-verified]`**: Directly inspected in `/tmp/Scribe-Native` (`dfe1d135`) or `/tmp/sora-editor-0.24.6` (`87055c45`).
- **`[Derived]`**: Mathematical or behavioral consequence logically deduced from verified source.
- **`[Proposed]`**: New or modified Scribe architecture / behavior designed for V1.
- **`[Fallback]`**: Explicit conservative exclusion / safe delegation path to existing Scribe behavior.

---

## Plan Guardrails & Non-Negotiable Architecture Rules

1. **Preserve Sora's Native Rendering Lifecycle, Draw Order & Approach-A Line Numbers (`[Source-verified]` / `[Proposed]`):**
   - `ScribeEditorRenderer` (`: EditorRenderer`, `CodeEditor.java:4682`) calls **`super.draw(canvas)` once** per frame (`EditorRenderer.java:190-200`), driving **`drawView(canvas)` once** (`461-641`).
   - Inside `drawView(canvas)`, Pass 1 (Upper `0 .. seamFirstLowerRow - 1`, unshifted) and Pass 2 (Lower `seamFirstLowerRow .. rowCount - 1`, `clipRect` before `canvas.translate(0f, +tauLower)`) draw strictly disjoint partitions across every row-attached layer in Sora's original draw order (`drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `postDrawCurrentLines`, `drawUserGutterBackground`, `drawSideIcons`, explicit 6-case Approach-A `firstLn` & `postDrawLineNumbers`, `drawSideBlockLine`, and `DrawCursorTask`) while keeping viewport-level layers fixed.
   - **Conservative Cursor-Animation Policy (Option B):** Split rendering NEVER activates or continues while `editor.cursorAnimator.isRunning()` is true (preventing desync with Sora's un-overridable `drawView()` gutter line highlight `EditorRenderer.java:528-534` and unshifted `drawSelectionOnAnimation()` `2439-2456`). When idle at deletion start (standard selection deletion), Pass-2 `DrawCursorTask` shifts the lower-region caret & insertion handle by `+tauLower`.
   - `UnifiedCanvasLayout` / Mode-2 `Choreographer` **synchronously invoke `editor.invalidate()`** on every frame changing `tauLower` (including outer-only `scrollD < headerHeight` frames and settle-to-`0f`), scheduling redraw through standard Android traversal without claiming `invalidate()` draws inline.
2. **Dedicated Controller, Replace-Safe Settlement Claim, Sealed Event B & Idempotent Cancel (`[Source-verified]` / `[Proposed]`):**
   - **`ScribeGapCollapseController`** (`ui/editor/scroll/ScribeGapCollapseController.kt`) owns the 5-phase state machine (`GapCollapsePhase`), transaction generation/epoch tokens, `Mode2VisualState`, pointer pause/resumption, atomic cancellation/final-safety claims, active-seam baseline, and `PendingPostSafetyKeyboardEval`.
   - **`Content.replace()` (`Content.java:585-587`) & Overscroll Protection:** Because `deleteInternal()` fires `afterDelete()` **before** `insertInternal()` fires `afterInsert()`, `afterDelete()` MUST NOT call `prepareGapCollapseFrame1IfReady()` or claim settlement while `deletionSnapshotCoordinator.isInContentReplace` is `true`. Furthermore, **non-empty replacement while overscrolled (`currentTotalScroll > maxCanvasScroll`)** returns explicit **`AfterInsertAction.ClearTransactionAndClampOverscroll`** (replacing `None`), clearing the staged deletion transaction **once** before resolving post-replace overscroll clamping with zero split frame.
   - **Every** settlement entry point uses the same **`tryClaimPendingDeletionSettlement(expectedGen)`** (never consuming it while layout/wordwrap is still retryable).
   - **Event B** uses 6-variant **`SafetyRevalidationResult`** and never exposes `IDLE` before snapshot cleanup, listener removal, callback invalidation, and generation advancement complete atomically.
   - **Idempotent Dual `ACTION_DOWN`:** Cancelling a legacy non-deletion animator is strictly separated from claiming/clearing a live deletion transaction so `deletionTransactionGeneration++` advances ONLY when an actual live deletion transaction is cleared.
3. **Literal Sole Coordinator `finishAnimation()` Ownership, `applyStepWithResult()` Completion Lifecycle, Sub-1px/Coalescing Contract, Physical Velocity Read-Back & Mode 2 (`[Source-verified]` / `[Proposed]`):**
   - **ZERO production calls to `finishAnimation()` exist outside `ScribeScrollCoordinator`** (removes both `ScribeScrollAnimation.kt:107` and `ScribeScrollAnimation.kt:167`!). Only `ScribeScrollCoordinator` owns, detects termination for, and executes `finishAnimation(...)` inside `applyStepWithResult(...)`, returning `ScrollStepResult` so `ScribeCanvasGlideAnimator.advanceFrame()` preserves the exact post-settlement callback lifecycle (`isGlideRunning = false`, capture/clear `activeGlideReason` + `activeGlideOnEnd`, `onSettledCallback(completedReason)` once, and `onEnd` once iff `!isGlideRunning && !scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId`).
   - Inactive sub-1px requests (`!scrollCoordinator.isAnimationActive && abs(physicalDistance) < 1f`) execute `scrollCoordinator.syncActualScroll(currentScroll)`, invoke `onEnd?.invoke()`, and return `false` without creating an operation, calling `finishAnimation()`, or invoking `onSettledCallback`; `abs(physicalDistance) >= 1f` creates a Mode-1 op; active same-target coalescing (`!forceRetarget && abs(currTarget - clampedTarget) <= 1f`) chains `onEnd` onto `activeGlideOnEnd` instead of invoking it prematurely.
   - Mode-2 handoff velocity is derived from **observed physical scroll read-back** (`(scrollD + editor.offsetY).toFloat()`), guarded against inactive Mode-1 (`!mode1ActuallyStarted || vPhys == null`) and zero/sub-1px physical spans (`abs(physicalSpan) < 1f`) with `0f` fallback, retaining the last valid pre-boundary physical velocity on boundary-clamped final frames, and executed via the **exact closed-form critically damped solution** matching the continuous monotonicity proof (`[0f, omega]`).
   - Option-A user flings/drags/handle-auto-scroll during Mode 2 are identified via explicit `PhysicalScrollOrigin` tagging while idle `ScribeScrollCoordinator.syncActualScroll()` stays continuously synchronized.
4. **Authoritative Integer-Consistent Safety Floor, Clean Overlay Lifecycle & Incremental Seam Rules (`[Source-verified]` / `[Proposed]`):**
   - One `ScribeOverlayBoundsRegistry` (shared across `CompactEditorLayout` and `ExpandedEditorLayout`, updating ONLY when **`isVisible && coords.isAttached`**, and removing keys via `onGloballyPositioned` fallback + `DisposableEffect` on hide, composition exit, layout switch, or key replacement) feeds one `resolveSafetyFloorAndBounds(...)` defining canonical integer `safeMax: Int`, fractional `minSafeScroll: Float`, integer **`safeMinPhysical: Int = ceil(minSafeScroll).toInt().coerceIn(0, safeMax)`**, and integer **`actualPhysicalScroll: Int = scrollD + editor.offsetY`** (settled safe interval **`[safeMinPhysical, safeMax]`**). Fractional `minSafeScroll` and `roundToInt()` are never used as physical targets, and transient `S_actual` may temporarily lie outside `[safeMinPhysical, safeMax]`. Used identically across planning (all scenarios including Scenario A), Mode-1 `deletionBoundaryProvider`, Event B, and `SAFE_BOUNDARY_CORRECTION`.
   - Height-only resize strictly serializes: stop collapse $\to$ `FINAL_SAFETY_PENDING` $\to$ recompute bounds at new `h` $\to$ Event B $\to$ unified post-`IDLE` `PendingPostSafetyKeyboardEval`.
   - `EditorTextActionWindow` remains `isEnabled = false` until **after** the first completed unsplit `ScribeEditorRenderer.draw(canvas)` pass actually refreshes cursor handles (`didRefreshCursorDescriptors && !cursorAnimator.isRunning()`), blocking stale queued Sora `displayWindow()` calls.

---

## Sora `0.24.6` Dependency Decision & Measure/Preload Reality

**`[Source-verified]` (`EditorRenderer.java` `492, 1007-1018, 1365, 2489-2538`, `WordwrapLayout.java:288-293`):**
- In `0.24.6`, `buildMeasureCacheForLines(..., timestamp, true)` only updates `cache.setUpdateTimestamp(timestamp)` when `styleHash != hash || forced` (`2505, 2529`); unchanged-hash lines fall back cleanly to `GraphicsCompat.getRunAdvance` / `paint.getTextRunAdvances`.
- `drawView()` (`492`) calls `prepareLines(firstVisibleLine, lastVisibleLine)` (`1007-1013`), populating `preloadedLines`/`preloadedDirections` across `[normalFirstVisLine - 5, normalLastVisLine + 5]`; outside `±5`, Sora uses lazy `getLine()` / `getLineDirections()`.

**`[Proposed]` Decision:**
- **Retain `io.github.rosemoe:editor:0.24.6` unchanged.**
- Convert non-empty `pass2RowRange` row indices via `editor.layout.getLineNumberForRow(...)` before calling protected `buildMeasureCacheForLines(pass2FirstLine, minOf(pass2LastLine, normalFirstVisLine - 1))`.

---

## Consolidated File Ownership Map (`[Source-verified]` / `[Proposed]`)

### New Production Files (5 Focused Files — Zero Trivial Helper Split)
| New File (`[Proposed]`) | Class / Symbol | Exact Ownership & Responsibilities |
|---|---|---|
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseController.kt` | `ScribeGapCollapseController`, `GapCollapsePhase`, `Mode2VisualState`, `SafetyRevalidationResult`, `PendingPostSafetyKeyboardEval`, `PhysicalScrollOrigin` | Owns the 5-phase state machine (`IDLE`, `MODE_1_PHYSICAL_COUPLED`, `MODE_2_VISUAL_SETTLING`, `POINTER_FROZEN`, `FINAL_SAFETY_PENDING`), owning deletion generation & `collapseAnimEpoch`, Mode-2 `Choreographer` closed-form spring loop & observed physical-velocity tracker, pointer freeze/resumption, atomic cancellation claim, Event-B final-safety claim/retry epoch bookkeeping, active-seam baseline (`activeSeamLine`, `activeSeamFirstLowerRow`, `activeSeamDocY`, `tauLower`), and `PendingPostSafetyKeyboardEval`. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicy.kt` | `ScribeGapCollapsePolicy`, `CollapseScenario`, `CollapsePlan`, `UnobstructedViewportBounds`, `VisibilityConfidence`, `SeamMappingResult` | Strictly pure, stateless math and policy: `(U, L, T)` classification across Scenarios A/B/C/D/E + V1 eligibility gates, `resolveSafetyFloorAndBounds` (`safeMax: Int`, `minSafeScroll: Float`, integer `safeMinPhysical: Int`, integer `actualPhysicalScroll: Int`, `clampPhysicalSafetyTarget()`), Branch 1/2/3 sub-pixel & synchronous Scenario-A safety floor clamping, scenario-specific Mode-1 `tauLower(t)` and Mode-2 initial velocity formulas, incremental seam mapping for mid-collapse insertions, and Case I / Case II pre/post geometry calculation. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeOverlayBoundsRegistry.kt` | `ScribeOverlayBoundsRegistry`, `OverlayOcclusionRole`, `LocalOverlayBoundsRegistry` | Window-coordinate (`boundsInWindow()`) overlay tracking registry shared across `MainEditorScreen`, `CompactEditorLayout`, `ExpandedEditorLayout`, `FloatingWindowOverlay`, `EditorImagePreviewCard`, and `UnifiedCanvasLayout`; converts entries to canvas-local coordinates and enforces key-scoped removal on hide, composition disposal, layout switch, or key replacement. |
| `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeEditorRenderer.kt` | `ScribeEditorRenderer : EditorRenderer` | Single-`super.draw(canvas)` / single-`drawView(canvas)` split renderer: orchestrates Pass 1 (`pass1RowRange`, unshifted) and Pass 2 (`pass2RowRange`, `clipRect` before `translate(0, tauLower)`), overrides `drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, explicit 6-case Approach-A Pass-2 line-number & `firstLn` collision/gap handling, Pass-2 `DrawCursorTask` Y-offset shift, pre-`super.draw` Option-B `cursorAnimator.isRunning()` defensive unsplit guard, and post-`super.draw` frame sequence + handle-refresh notification (`renderedDrawPassSeq`, `didRefreshCursorDescriptors`). |
| `app/src/main/java/io/github/rosemoe/sora/widget/ScribeDrawCursorTaskAccessor.java` | `ScribeDrawCursorTaskAccessor` | Minimal package-private bridge in `io.github.rosemoe.sora.widget` to shift `EditorRenderer.DrawCursorTask.y` by `+tauLower` once before appending Pass-2 tasks into `postDrawCursor`. |

### Existing Modified Production Files (View Host, Scroll, Editor & Layout Integration)
| Existing File (`[Source-verified]`) | Integration Responsibilities (`[Proposed]`) |
|---|---|
| `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt` | View host & lifecycle integrator: hosts `ScribeGapCollapseController`; computes `UnobstructedViewportBounds` + `resolveSafetyFloorAndBounds`; separates `stopLegacyAnimators` vs `claimAndClearLiveDeletionTransaction`; wires `tryClaimPendingDeletionSettlement` & `canStillSettleGeneration`; executes atomic `finalizeCompletedGapCollapseTransaction`, `finalizeUnresolvedGapCollapseTransaction`, `handleCursorAnimatorActivatedDuringCollapse`, and `cancelGapCollapseAndDeletionTransaction`; tracks `PhysicalScrollOrigin` & syncs idle `scrollCoordinator.syncActualScroll`; exposes `dispatchTouchEvent` `try/finally`; serializes height-only `onSizeChanged` with `PendingPostSafetyKeyboardEval`; maintains `scrollDFloat = scrollD + outerScrollResidualPx`; triggers synchronous `editor.invalidate()`. |
| `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCodeEditor.kt` | Wires `onCreateRenderer()` returning `ScribeEditorRenderer`; `@UiThread renderPassRowRangeOverride` gating `getFirstVisibleRow()` / `getLastVisibleRow()`; `untranslateYOffset` for touch & handle hit-testing; `beforeModification` / `afterDelete` (`!isInContentReplace` guard!) / `afterInsert` (`ClearTransaction` & `ClearTransactionAndClampOverscroll` cleanup + overscroll clamping) split capture, incremental seam rebase, Policy A/B internal mutation routing, and post-fallback `ensureSelectionVisible()`; Option-B `ScribeCursorAnimator` start observer triggering `handleCursorAnimatorActivatedDuringCollapse()`; `ContentChangeEvent(ACTION_DELETE)` & `LayoutStateChangeEvent` generation-scoped settlement routing; `ScrollEvent.cause` + `handleAutoScrollRunnable` origin tagging; `EditorTextActionWindow` kept disabled until post-`draw()` handle refresh + cleanup in `onDetachedFromWindow()`; tracked `pendingReplaceWatchdog`. |
| `app/src/main/java/com/primaloptima/scribe/ui/components/ScribeCursorAnimator.kt` | Adds lightweight `onAnimatorStarted: (() -> Unit)?` notification hook inside `start()` / `triggerGlide()` so any cursor glide starting during Mode 1 or Mode 2 notifies `ScribeCodeEditor` / `UnifiedCanvasLayout` synchronously on UI thread before the next draw frame. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollCoordinator.kt` | **Sole owner of `finishAnimation()` & completion detection**: adds `StepTerminationReason`, `ScrollStepResult` (`operationId`, `actualTotalScroll`, `consumedDelta`, `boundaryHit`, `terminationReason`, `isOperationFinished`), `applyStepWithResult(newAnimatedScroll, operationId, isSpringSettled)` (rejecting stale `operationId`, executing target/boundary/`isSpringSettled` completion and calling coordinator's own `finishAnimation(operationId)` once), and idle-only `syncActualScroll(actualTotalScroll)`; documents all 3 meanings of `requestTarget()` returning `false`; preserves `applyStep(Float, Long?)` compatibility. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollAnimation.kt` | Removes all `finishAnimation()` calls (`107`, `167`): inactive sub-1px (`abs(physicalDistance) < 1f && !isAnimationActive`) calls `syncActualScroll(currentScroll)`, invokes `onEnd?.invoke()`, and returns `false` without creating an op or firing `onSettledCallback`; `abs(physicalDistance) >= 1f` enters `requestTarget()` / Mode-1 spring glide; active same-target coalescing (`!forceRetarget && abs(currTarget - clampedTarget) <= 1f`) chains `onEnd` onto `activeGlideOnEnd` and returns `false` without premature firing; `advanceFrame()` preserves baseline completion lifecycle (`isGlideRunning = false`, capture/clear `activeGlideReason` + `activeGlideOnEnd`, `onSettledCallback(completedReason)` once, and `onEnd` once iff `!isGlideRunning && !scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId`) driven by `ScrollStepResult.isOperationFinished` (and routes any rare created op with `< 1f` discrepancy through `applyStepWithResult(..., isSpringSettled = true)`); adds `onFrameCommitted` Event-A callback and `cancelPreservingTransaction(reason)`. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollInputController.kt` | Emits `PhysicalScrollOrigin` scope tags (`USER_CANVAS_DRAG`, `USER_CANVAS_FLING`) around manual drag `scrollCanvasBy` and `OverScroller` fling/`computeScroll()`. |
| `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollDeletionPolicy.kt` | Retains existing deletion intent (`RESET_TO_TOP`, `TAIL_ANCHOR`, `CARET_ANCHOR`); extends `ScribeDeletionSnapshotCoordinator.AfterInsertAction` with explicit **`ClearTransactionAndClampOverscroll`** (replacing `None` for overscrolled non-empty replacements); adds optional `usableBottomForSafety: Float = usableViewportHeight.toFloat()` parameter on `validateSettledDeletionSafety`. |
| `app/src/main/java/com/primaloptima/scribe/ui/screens/MainEditorScreen.kt` | Creates and provides `ScribeOverlayBoundsRegistry` via `LocalOverlayBoundsRegistry` wrapping `CompactEditorLayout` and `ExpandedEditorLayout`; registers `FindReplaceBar`, top banners, and indent indicator using `isVisible && coords.isAttached` + key-scoped `DisposableEffect` cleanup; wires Policy-B pre-mutation cancel on `applySmartPrefix` / selection `applyFormat`. |
| `app/src/main/java/com/primaloptima/scribe/ui/screens/CompactEditorLayout.kt` | Registers `shortcut_bar` (`BOTTOM_BAR`) and `word_count_pill` (`FLOATING_PILL`) via `boundsInWindow()` using `isVisible && coords.isAttached` + `DisposableEffect` cleanup. |
| `app/src/main/java/com/primaloptima/scribe/ui/screens/ExpandedEditorLayout.kt` | Registers side/bottom dock or shortcut overlay bounds via `boundsInWindow()` using `isVisible && coords.isAttached` + `DisposableEffect` cleanup. |
| `app/src/main/java/com/primaloptima/scribe/ui/components/FloatingWindowOverlay.kt` | Registers floating window card (`FLOATING_PILL`) and active modal scrims (`MODAL_BLOCKER`) using `isVisible && coords.isAttached` + `DisposableEffect` cleanup. |
| `app/src/main/java/com/primaloptima/scribe/ui/components/EditorImagePreviewCard.kt` | Registers floating preview card (`FLOATING_PILL`) using `isVisible && coords.isAttached` + `DisposableEffect` cleanup. |

### Focused Test Files (New Suites A–E + Existing Regression Suite F)
| Test File (`[Proposed]`) | Coverage Scope |
|---|---|
| `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicyTest.kt` | **Suite A:** `(U, L, T)` truth table + `!isCursorAnimationRunning` gate, canonical integer `safeMinPhysical` rounding/clamp proofs (`(100.4, 100)->101` .. `(101.3, 102)->102`), transient `S_actual > safeMax` settlement to `safeMax`, Scenario A `[safeMinPhysical, safeMax]` 3 regimes + synchronous `< 1px` floor snap, single safety floor (`TOP_BAR`/`BOTTOM_BAR` vs `FLOATING_PILL`), lower-anchor positive/zero/negative shortfall math, Mode-2 scenario-specific handoff velocity (including zero/`< 1px` span, exact `1px` span, `!mode1ActuallyStarted` / synchronous snap guards, finite `[0f, omega2]` bounds) & closed-form monotonicity, Branch 1/2/3 integer-consistent proofs, incremental seam mapping, and `ScribeDeletionSnapshotCoordinator.onAfterInsert()` `ClearTransaction` vs `ClearTransactionAndClampOverscroll` vs `SettleEmptyReplaceDelete`. |
| `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt` | **Suite B1:** 5-phase transitions, sealed `SafetyRevalidationResult` (all 6 variants, `[safeMinPhysical, safeMax]` read-back enforcement, retry preservation), atomic teardown-before-`IDLE`, `cancelPreservingTransaction`, Option-B mid-collapse cursor-animator abort across all 6 Event-B outcomes (Mode 1/2, `CORRECTION_STARTED`, `DEFERRED_UNTIL_LAYOUT_READY`, stale-epoch rejection, immediate split-render disable while `FINAL_SAFETY_PENDING`, `EditorTextActionWindow` safety), `PendingPostSafetyKeyboardEval` across all 4 Event-B exits, idempotent dual-`ACTION_DOWN` post-`IDLE` generation proof, observed physical read-back handoff velocity (normal settled, boundary clamped, zero/sub-1px/exact-1px spans & synchronous snap), Option-A `PhysicalScrollOrigin` discrimination, closed-form Mode-2 `frameTimeNanos` `0L` clock. *(Extend `ScribeScrollCoordinatorTest.kt` & `ScribeScrollAnimationTest.kt` for **Suite B2**: literal sole-coordinator `finishAnimation()` ownership, `applyStepWithResult()` completion lifecycle, `onSettledCallback`/`onEnd` ordering & replacement-op suppression, stale-op rejection, `cancelPreservingTransaction()` suppression, inactive `0..0.999 px` no-op + `1.00 px` glide start, active same-target `onEnd` coalescing, active retargeting, and zero/sub-1px handoff guards.)* |
| `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt` | **Suite C:** Outer-only (`scrollD < headerHeight`) & Mode-2 `invalidate()` verification, all 6 Approach-A `firstLn` collision/gap/continuation cases (Cases 1–6) + explicit unclipped `overrideFirstLnLine` verification in Cases 2, 3 (`pass2TopScreenY <= 0f`), and 4 vs translated Case 5, per-layer clip/translation (`patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawSideBlockLine`, `DrawCursorTask` + Case-I lower-region insertion-handle placement), zero double-shift, Option-B cursor-animator fallback, immediate split-render disable during `FINAL_SAFETY_PENDING`, and `EditorTextActionWindow` post-`IDLE` unsplit descriptor-refresh gate. |
| `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseIntegrationTest.kt` | **Suite D & E:** Dispatch `try/finally` pointer/fling tests (1)–(6), `ScribeOverlayBoundsRegistry` cleanup (hide, dispose, layout-switch, key replacement, AND attached-but-hidden `isVisible = false && coords.isAttached == true` non-re-registration), `ScribeEditorRenderer` post-`draw()` (`!cursorAnimator.isRunning()`) `EditorTextActionWindow` re-enable & refresh, serialized height resize + `scrollDFloat` invariant, `Content.replace()` lifecycle (non-overscrolled, overscrolled `ClearTransactionAndClampOverscroll`, delayed layout callbacks after either), cursor-animator tests (idle at deletion start, running at deletion start, starting in Mode 1/2), & Scenario E. |

---

## Phase 0 — Resolved Dependency, Renderer Access & Gradle Task Preflight (`[Proposed]`)

*(Mandatory read-only preflight before implementation; zero commands executed during this planning step.)*

### 0.1 Resolved Dependency Verification
- Run `./gradlew :app:dependencyInsight --dependency io.github.rosemoe:editor --configuration debugRuntimeClasspath` (and inspect release/test configurations as applicable) to verify that the resolved dependency is **`io.github.rosemoe:editor:0.24.6`** with no unexpected version substitution or conflict.

### 0.2 Compiled AAR Bytecode & `DrawCursorTask` Accessibility Inspection
- Locate the resolved `editor-0.24.6.aar` in the local Gradle cache (`find ~/.gradle/caches -name '*editor-0.24.6*.aar'`), extract `classes.jar` (`unzip -p <resolved-aar-path> classes.jar > /tmp/sora-editor-0.24.6-classes.jar`), and run:
  ```bash
  javap -p -cp /tmp/sora-editor-0.24.6-classes.jar 'io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask'
  javap -p -cp /tmp/sora-editor-0.24.6-classes.jar 'io.github.rosemoe.sora.widget.EditorRenderer'
  ```
- Confirm exclusively from the compiled AAR bytecode that `EditorRenderer$DrawCursorTask` and its `y` field are directly accessible to package-private `io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor` **without reflection, `setAccessible`, or `Unsafe`**, verify exact `protected` method signatures (`drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawLineNumber`, `drawRowBackground`, `drawSideBlockLine`, `buildMeasureCacheForLines`) against the `ScribeEditorRenderer` overrides, and confirm binary API/signature parity with the `0.24.6` source reference inspected in `0.3`.

### 0.3 Source-Reference Provenance Verification
- Independently verify the checked-out Sora source reference against the specified `io.github.rosemoe:editor:0.24.6` source commit (`87055c459e346cac6c619b3350f0bfba076228cc`) and version metadata.
- Record the source artifact's provenance separately, keeping source-reference provenance strictly distinct from the compiled binary AAR / bytecode verification in `0.2`.

### 0.4 Exact Gradle Task-Name Discovery
- Run `./gradlew :app:tasks --all` and record the exact verified Gradle task names for:
  - JVM unit tests (e.g., `:app:testDebugUnitTest`),
  - Android instrumentation tests (e.g., `:app:connectedDebugAndroidTest` / compilation tasks).

### 0.5 Phase-0 Completion Gates
- **Gate Blocking Phase 1 Start:** Steps `0.1`, `0.3`, and `0.4` MUST complete and their verified outputs/task names MUST be recorded before modifying or creating any Phase 1–3 files.
- **Gate Blocking Phase 4 Start:** Step `0.2` MUST confirm non-reflective package-private access to `EditorRenderer$DrawCursorTask.y` and matching `EditorRenderer` protected signatures in the resolved `0.24.6` AAR before implementing `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor`.

---

## Phase 1 — Pure Policy, Shared Overlay Registry Lifecycle, Single Safety Floor & Exact Equations (`ScribeGapCollapsePolicy`)

### 1.1 Deletion-Intent Precedence (`[Source-verified]` / `[Proposed]`)
`ScribeScrollDeletionPolicy.classifyDeletionIntent(...)` (`ScribeScrollDeletionPolicy.kt:126-168`) executes **first**, unchanged:
1. `RESET_TO_TOP` $\to$ existing `resetDocumentToTopFromDeletion(smooth)` (`[Fallback]`).
2. `TAIL_ANCHOR` $\to$ existing `calculateSafeDeletionDestination(TAIL_ANCHOR, ...)` (`[Fallback]`).
3. `CARET_ANCHOR` $\to$ `ScribeGapCollapsePolicy`: split gap collapse if eligible, or existing `CARET_ANCHOR` smooth fallback + normal cursor animation if ineligible.

### 1.2 Shared `ScribeOverlayBoundsRegistry` Lifecycle & `UnobstructedViewportBounds` (`[Source-verified]` / `[Derived]` / `[Proposed]`)

**Shared Window-Relative Registry (`[Proposed]`):**
- Owned in `MainEditorScreen` via `remember { ScribeOverlayBoundsRegistry() }`, exposed via `LocalOverlayBoundsRegistry` across `CompactEditorLayout`, `ExpandedEditorLayout`, `FloatingWindowOverlay`, `EditorImagePreviewCard`, and `UnifiedCanvasLayout`.
- Registrants report **`coords.boundsInWindow()`** (`Rect` in physical window px, accurately capturing `CompactEditorLayout:554` `.graphicsLayer { translationX = ... }`) paired with `OverlayOcclusionRole` (`TOP_BAR`, `BOTTOM_BAR`, `FLOATING_PILL`, `MODAL_BLOCKER`) keyed by a stable string key.
- **Explicit Registry Lifecycle Cleanup (`[Proposed]`):**
  To guarantee stale overlays never keep shrinking `usableBottomForSafety` or raising `minSafeScroll`:
  1. **Visibility & Attach Guard (`isVisible && coords.isAttached`):** Inside `.onGloballyPositioned { coords -> if (isVisible && coords.isAttached) registry.update(key, role, coords.boundsInWindow()) else registry.remove(key) }`. Both conditions MUST hold; if either is `false` (including an attached-but-hidden composable whose `onGloballyPositioned` fires after `isVisible` flips to `false`), remove `key` rather than updating bounds.
  2. **Composition Exit / Conditional Hide:** Every conditionally rendered or layout-owned overlay (`FindReplaceBar`, banners, indent indicator, `shortcut_bar`, `word_count_pill`, `ExpandedEditorLayout` panels, `FloatingWindowOverlay`, `EditorImagePreviewCard`) binds `DisposableEffect(key, isVisible) { if (!isVisible) registry.remove(key); onDispose { registry.remove(key) } }`.
  3. **Owning Layout Change & Key Replacement:** Switching between `CompactEditorLayout` and `ExpandedEditorLayout` runs `onDispose` on the exiting layout's keys automatically; `registry.update(key, ...)` replaces prior entries for the same stable key idempotently.
  4. **Canvas-local Mapping:** `yLocal = entry.windowRect.topOrBottom - canvasWinLoc[1]` (`getLocationInWindow`), with zero Compose-root assumptions.

**`UnobstructedViewportBounds` & Authoritative Single Safety Floor (`[Source-verified]` / `[Proposed]`):**
1. **`visibleEditorTop`:** `maxOf(0f, headerBottomLocal, stickyBottomLocal, maxTopBarBottomLocal)` (`headerBottomLocal = (headerHeight - scrollD).toFloat().coerceAtLeast(0f)`, `stickyBottomLocal = 0f` in V1 wordwrap).
2. **`visibleEditorBottom`:** `minOf(height.toFloat(), windowVisibleBottomLocal, minBottomBarTopLocal)`.
3. **`FLOATING_PILL` Rule:** Tested directly against handle/text rects (`!pillLocalRect.overlaps(handleRect)`); never shrinks `visibleEditorTop` / `visibleEditorBottom`.
4. **Canonical Integer Physical-Safety Model (`resolveSafetyFloorAndBounds`):**
   - Verified in `UnifiedCanvasLayout.kt:129-139`: `maxCanvasScroll` (`maxCanvasScrollPost`) is genuinely an **`Int`**, and Android View scroll offsets (`scrollD: Int` + `editor.offsetY: Int`) make physical scroll read-back integer-valued (`Int`). Define the single canonical physical-safety model:
     ```kotlin
     val safeMax: Int = maxCanvasScrollPost.coerceAtLeast(0)

     val minSafeScroll: Float =
         (headerHeight + anchorDocBottom + breathingMarginPx - usableBottomForSafety)
             .coerceAtLeast(0f)
             .coerceIn(0f, safeMax.toFloat())

     val safeMinPhysical: Int =
         kotlin.math.ceil(minSafeScroll).toInt().coerceIn(0, safeMax)

     val actualPhysicalScroll: Int = scrollD + editor.offsetY

     fun clampPhysicalSafetyTarget(requestedTarget: Float): Float =
         requestedTarget.coerceIn(safeMinPhysical.toFloat(), safeMax.toFloat())
     ```
   - **Canonical Physical-Safety Rules (`[Derived]` / `[Proposed]`):**
     - The **settled safe physical interval** is the integer interval **`[safeMinPhysical, safeMax]`** (with exact integer-valued float bounds `[safeMinPhysical.toFloat(), safeMax.toFloat()]`).
     - Observed integer read-back validation uses **`actualPhysicalScroll >= safeMinPhysical`** and **`actualPhysicalScroll <= safeMax`** (never `actualPhysicalScroll.toFloat() >= minSafeScroll` and never `roundToInt()`).
     - Fractional `minSafeScroll` and `roundToInt()` are **never** used as physical scroll targets; only **`safeMinPhysical`** (or `safeMax`) is a valid boundary physical target.
     - **Transient `S_actual` is NOT restricted to `[0, safeMax]` or `[safeMinPhysical, safeMax]`:** immediately after a deletion shrinks `maxCanvasScrollPost`, or before settlement/clamping runs, transient `actualPhysicalScroll` may legitimately lie outside `[safeMinPhysical, safeMax]` (including `S_actual < safeMinPhysical` or transient overscroll `S_actual > safeMax`). Settlement and Event B exist specifically to bring physical scroll into settled `[safeMinPhysical, safeMax]`.
     - Used identically across `ScribeGapCollapsePolicy` (**all scenarios including Scenario A**), `ScribeScrollAnimation.deletionBoundaryProvider`, Event B `revalidateDeletionSettlementSafety`, and `SAFE_BOUNDARY_CORRECTION`. Event B MUST NOT report `VALIDATED_SAFE` or `CORRECTED_AND_VALIDATED` whenever `actualPhysicalScroll < safeMinPhysical` or `actualPhysicalScroll > safeMax`; any `SAFE_BOUNDARY_CORRECTION` uses `effectiveCorrectiveTarget = clampPhysicalSafetyTarget(validation.correctiveTargetScroll)` and validates post-correction read-back `actualAfterCorrection = scrollD + editor.offsetY` against `[safeMinPhysical, safeMax]`.
5. **Reliable Visibility Gate (`VisibilityConfidence`):** Computations strictly from `preLayout.getCharLayoutOffset` + `canvasLocalY(docY)`, returning `RESOLVED` only when `isLaidOut && editor.isLaidOut && width > 0 && height > 0 && editor.isWordwrapReady() && !editor.isLayoutBusyState && preLayout != null && !isModalActive && viewportBounds.isValid`.

### 1.3 Complete `(U, L, T)` Truth Table & Unified Scenario-A Safety Enforcement (`[Derived]` / `[Proposed]` / `[Fallback]`)

| `VisibilityConfidence` | $U$ | $L$ | $T$ | Guard / Gate | `CollapseScenario` | Authoritative Target & Behaviour (`[safeMinPhysical, safeMax]`) |
|---|---|---|---|---|---|---|
| `UNRELIABLE` | any | any | any | Unlaid / `!wordwrapReady` / busy / `MODAL_BLOCKER` / `!isValid` | **`INELIGIBLE_FALLBACK`** (`[Fallback]`) | Existing `ScribeScrollDeletionPolicy`. |
| `RESOLVED` | `false` | `true` | `false`/`true` | V1 eligible | **`B_LOWER_ONLY`** (Lower-Anchor w/ Safety Exception) | `S_requestedTarget = S_pre.toFloat() - ΔG`, `S_effectiveTarget = clampPhysicalSafetyTarget(S_requestedTarget)`. Stationary Lower (`0.0 px`) when reachable; Safety Exception `ΔY_lower = -shortfall` (`shortfall = ΔG - (S_pre.toFloat() - S_actualFinal.toFloat())`) when clamped. Synchronous snap uses `tauLower(0) = maxOf(0f, ΔG + (S_actualInitial.toFloat() - S_pre.toFloat()))`; Mode 1 uses `tauLower(t) = maxOf(0f, ΔG - (S_pre.toFloat() - S_actual(t).toFloat()))`. |
| `RESOLVED` | `true` | `true` | `false`/`true` | V1 eligible | **`C_BOTH_VISIBLE`** (Lower-Anchor w/ Safety Exception) | Identical equation & behavior to `B_LOWER_ONLY`. |
| `RESOLVED` | `true` | `false` | `false`/`true` | V1 eligible | **`A_UPPER_ONLY`** | `S_requestedTarget = S_pre.toFloat()`, **`S_effectiveTarget = clampPhysicalSafetyTarget(S_requestedTarget)`** *(never leaves settled `S_actual < safeMinPhysical` or `> safeMax`)*:<br>• **A-Stationary (`S_pre >= safeMinPhysical && S_pre <= safeMax`):** `S_effectiveTarget = S_pre.toFloat()` $\implies$ skip Mode 1 (`ΔY_upper = 0.0 px`), Mode 2 closes `tauLower(0) = ΔG -> 0`.<br>• **A-MinSafeClamped `< 1px` (`S_pre < minSafeScroll && minSafeScroll - S_pre.toFloat() < 1f`):** **Never leave below `safeMinPhysical`** and do not invoke Mode 1 — synchronously snap physical scroll to **`S_settled0 = safeMinPhysical`** (`δS = +1.0 px`), `ΔY_upper = -1.0 px` immediately onto `[safeMinPhysical, safeMax]`, initialize **`tauLower(0) = ΔG`**, and settle via Mode 2 (`ΔY_lower(0) = -1.0 px`, zero overlap, `ΔY_lower(final) = -(ΔG + 1.0)`).<br>• **A-MinSafeClamped `>= 1px` (`S_pre < minSafeScroll && minSafeScroll - S_pre.toFloat() >= 1f`):** `S_effectiveTarget = safeMinPhysical.toFloat()` (`safeMinPhysical - S_pre >= 1`); Mode 1 scrolls `S_pre -> safeMinPhysical`, closing progress-coupled `tauLower(t)` with denominator `S_effectiveTarget - S_pre.toFloat()` (`ΔY_upper = -(safeMinPhysical - S_pre)`).<br>• **A-MaxClamped (`S_pre > safeMax`):** Both `S_pre` and `safeMax` are integers so `S_pre - safeMax >= 1` always; `S_effectiveTarget = safeMax.toFloat()`. Mode 1 scrolls `S_pre -> safeMax` (`ΔY_upper = +(S_pre - safeMax)`) using lower-anchor `tauLower(t) = maxOf(0f, ΔG - (S_pre.toFloat() - S_actual(t).toFloat()))`, leaving `maxOf(0f, ΔG - (S_pre.toFloat() - S_actualFinal.toFloat()))` closed by Mode 2. |
| `RESOLVED` | `false` | `false` | `true` | $\Delta G \le 3 \cdot H_{\text{usable}}$ | **`D_SELECTION_ONLY_CENTERED`** | `S_requestedTarget = S_center`, `S_effectiveTarget = clampPhysicalSafetyTarget(S_requestedTarget)`; when `abs(S_effectiveTarget - S_pre.toFloat()) >= 1f`, Mode 1 closes progress-coupled `tauLower(t) = ΔG * (1f - (S_actual(t).toFloat() - S_pre.toFloat()) / (S_effectiveTarget - S_pre.toFloat())).coerceIn(0f, ΔG)`; when `S_pre < minSafeScroll && minSafeScroll - S_pre.toFloat() < 1f`, synchronously snaps to `safeMinPhysical` and enters Mode 2 with `progressVelocity = 0f`; when `abs(S_effectiveTarget - S_pre.toFloat()) < 1f` produces no Mode-1 movement (including `S_effectiveTarget == S_pre`), Mode-2 handoff uses unchanged actual physical read-back `actualPhysicalScroll.toFloat()` and `progressVelocity = 0f`. |
| `RESOLVED` | `false` | `false` | `true` | $\Delta G > 3 \cdot H_{\text{usable}}$ | **`INELIGIBLE_FALLBACK`** (`[Fallback]`) | Existing `CARET_ANCHOR` fallback. |
| `RESOLVED` | `false` | `false` | `false` | V1 eligible | **`E_OFFSCREEN_CARET_FOLLOW`** | Cancel/retract cleanly + `ensureCursorVisibleAboveKeyboard(allowDuringDeletionRetraction = true)`. |

- **V1 Eligibility Gate (`INELIGIBLE_FALLBACK` if any fails):**
  `deletionIntent != CARET_ANCHOR || !isSelection || !isWordwrap || !isWordwrapReady || isLayoutBusy || isComposing || isCursorAnimationRunning || hasRtlOrBidiAtSeam || isMergedPartialRowSeam || !hasSurvivingUpper || !hasSurvivingLower || deltaGapPx <= 0.5f || visibilityConfidence != RESOLVED`.
  *(Here `isCursorAnimationRunning = editor.cursorAnimator.isRunning()` enforces Option B at deletion preparation.)*

### 1.4 Representable Scroll Model, Integer `safeMax` & Honest Sub-Pixel Branches (`[Source-verified]` / `[Derived]` / `[Proposed]`)

**Representable Physical Scroll (`[Source-verified]` `UnifiedCanvasLayout.kt:129, 176, 1250`):**
Android View offsets (`scrollD: Int` + `editor.offsetY: Int`) make `actualPhysicalScroll = scrollD + editor.offsetY` strictly **integer-valued (`Int`)**. Transient `S_actual` (for example immediately after a deletion lowers `maxCanvasScrollPost`, or before settlement/clamping executes) **can temporarily lie outside `[safeMinPhysical, safeMax]`** (`S_actual < safeMinPhysical` or `S_actual > safeMax`). Once settled by Mode 1 / Event B, `actualPhysicalScroll` MUST lie in integer **`[safeMinPhysical, safeMax]`**. Notice two immediate mathematical consequences:
- Because `S_pre` and `safeMax` are both integers, **`S_pre > safeMax` implies `S_pre - safeMax >= 1` always** (a `< 1 px` violation above `safeMax` is impossible).
- Therefore, **`S_pre !in minSafeScroll..safeMax && 0f < abs(S_effectiveTarget - S_pre.toFloat()) < 1f` (fractional sub-1px floor crossing) occurs ONLY when `minSafeScroll` has a fractional part just above integer `S_pre`**: `S_pre < minSafeScroll < S_pre + 1` (in which case `safeMinPhysical = ceil(minSafeScroll).toInt() = S_pre + 1`)!

Screen-position displacement after synchronous integer snap $\delta S = S_{\text{actualInitial}} - S_{\text{pre}}$ and initial $\tau_{\text{lower}}(0) \ge 0$:
$$\Delta Y_{\text{lower}}(0) = -\delta S + \tau_{\text{lower}}(0) - \Delta G$$

1. **Normal Reachable Target (`abs(S_effectiveTarget - S_pre.toFloat()) >= 1f && shortfall == 0f`):**
   - `B_LOWER_ONLY` / `C_BOTH_VISIBLE`: Upper glides $+\Delta G$ down; **Lower remains 100% stationary (`ΔY_lower = 0.0 px` on every frame)**.
2. **Physical Safety-Bound Clamp (`abs(S_effectiveTarget - S_pre.toFloat()) >= 1f && shortfall != 0f`):**
   - B/C Frame 0 ($\delta S = 0$): $\tau_{\text{lower}}(0) = \Delta G \implies \Delta Y_{\text{lower}}(0) = 0.0\text{ px}$; **`ΔY_lower(final) = -shortfall`** where `shortfall = ΔG - (S_pre.toFloat() - S_actualFinal.toFloat())` (upward for `shortfall > 0`; downward for `shortfall < 0` via Mode-1 saturation at `0f`).
3. **Sub-Pixel / Rounding-Limited Regimes (Zero False Stillness):**
   - **Branch 1 (`S_pre in safeMinPhysical..safeMax && 0f < abs(S_effectiveTarget - S_pre.toFloat()) < 1f`):**
     Physical scroll stays at integer `S_pre` ($\delta S = 0$), **`ΔY_upper = 0.0 px`**, Frame-0 Lower jump = `0.0 px`, Mode 2 closes $\tau_{\text{lower}}(0) = \Delta G \to 0$ from unchanged `actualPhysicalScroll` (**`ΔY_lower = -ΔG`**).
   - **Branch 2 (`S_pre < safeMinPhysical && 0f < minSafeScroll - S_pre.toFloat() < 1f` — the ONLY reachable sub-1px off-bound regime):**
     Synchronously snaps physical scroll to **`S_settled0 = safeMinPhysical`** via `scrollCanvasBy(S_settled0 - S_pre)`, zeros residuals, sets `scrollDFloat = scrollD.toFloat()`, calls `scrollCoordinator.syncActualScroll(S_settled0.toFloat())`, and skips Mode 1. Here $\delta S = (S_{\text{settled0}} - S_{\text{pre}}).\text{toFloat}() = +1.0\text{ px} > 0$, forcing **$\Delta Y_{\text{upper}} = -1.0\text{ px}$** onto `[safeMinPhysical, safeMax]`:
     - **In `B_LOWER_ONLY` / `C_BOTH_VISIBLE`:** Because $\delta S = +1.0\text{ px} > 0$ and $\Delta G > 0.5\text{f}$, $\tau_{\text{lower}}(0) = \max(0\text{f}, \Delta G + (S_{\text{actualInitial}} - S_{\text{pre}})) = \Delta G + \delta S > 1.5\text{f} > 0$ holds unconditionally, yielding **$\Delta Y_{\text{lower}}(0) = 0.0\text{ px}$ Frame-0 Lower jump**, after which Mode 2 closes $\tau_{\text{lower}}(0) \to 0$ (**`ΔY_lower(final) = -(ΔG + 1.0)`**).
     - **In `A_UPPER_ONLY` / `D_SELECTION_ONLY_CENTERED`:** Using Scenario A/D's no-widening bound $\tau_{\text{lower}}(0) = \Delta G \in [0, \Delta G]$: Upper moves `-1.0 px` onto `safeMinPhysical`, Lower moves $\Delta Y_{\text{lower}}(0) = -1.0\text{ px}$ cleanly with zero overlap and zero gap expansion, and Mode 2 closes $\Delta G \to 0$ ($\Delta Y_{\text{lower}}(\text{final}) = -(\Delta G + 1.0)$).
     *(Numerical example: `S_pre = 100`, `minSafeScroll = 100.4f`, `safeMax = 900`, `ΔG = 12f`: `safeMinPhysical = ceil(100.4).toInt() = 101`, `S_settled0 = 101`, `δS = +1.0 px`; B/C sets `tauLower(0) = 13f -> ΔY_lower(0) = 0.0 px`, Mode 2 settles `13f -> 0f`; A sets `tauLower(0) = 12f -> ΔY_upper = -1.0 px, ΔY_lower(0) = -1.0 px`, Mode 2 settles `12f -> 0f`; settled `actualPhysicalScroll = 101 >= safeMinPhysical`.)*
   - **Branch 3 (`abs(S_effectiveTarget - S_pre.toFloat()) >= 1f`) Mode-1 Rounding Settlement (`SPRING_SETTLED_ROUNDING_LIMITED`):**
     If integer view rounding or `SAFE_BOUNDARY_CORRECTION` boundary clamping leaves `S_actualFinal` shy of `S_effectiveTarget` (while remaining within `[safeMinPhysical, safeMax]` or routed through Event B if outside), Mode 2 closes any residual $\tau_{\text{lower}}$ computed from observed integer read-back `S_actualFinal.toFloat()`.

### 1.5 Derived Scenario Mode-1 $\tau_{\text{lower}}(t)$, Physical Velocity Read-Back & Exact Closed-Form Mode 2 (`[Derived]` / `[Proposed]`)

1. **Lower-Anchor Formula (`B_LOWER_ONLY`, `C_BOTH_VISIBLE`, `A-MaxClamped`):**
   $$\tau_{\text{lower}}(t) = \max\!\big(0\text{f},\ \Delta G - (S_{\text{pre}}.\text{toFloat}() - S_{\text{actual}}(t).\text{toFloat}())\big)$$
   where `S_actual(t)` is observed integer physical read-back `(scrollD + editor.offsetY)`. Verified across all 3 shortfall signs ($\text{shortfall} = \Delta G - (S_{\text{pre}}.\text{toFloat}() - S_{\text{actualFinal}}.\text{toFloat}())$):
   - **Positive (`shortfall > 0`):** $S_{\text{pre}} = 500, \Delta G = 300, \text{minSafeScroll} = 349.4\text{f} \implies \text{safeMinPhysical} = 350, \text{safeMax} = 900 \implies S_{\text{effectiveTarget}} = 350\text{f}, S_{\text{actualFinal}} = 350, \text{shortfall} = +150\text{ px}$. $\tau_{\text{lower}}(t)$ shrinks $300 \to +150\text{ px}$ (`ΔY_lower = 0.0 px` on Mode 1); $+150\text{ px}$ settles via Mode 2 ($\Delta Y_{\text{lower}}(\text{final}) = -150\text{ px}$).
   - **Zero (`shortfall == 0`):** $S_{\text{pre}} = 500, \Delta G = 300, \text{safeMinPhysical} = 100 \implies S_{\text{effectiveTarget}} = 200\text{f}, S_{\text{actualFinal}} = 200, \text{shortfall} = 0\text{ px}$. $\tau_{\text{lower}}(\text{end}) = 0.0\text{ px}$ $\to$ straight to Event B.
   - **Negative (`shortfall < 0`):** Transient post-delete overscroll $S_{\text{pre}} = 950, \Delta G = 100, \text{safeMax} = 700 \implies S_{\text{effectiveTarget}} = 700\text{f}, S_{\text{actualFinal}} = 700, \text{shortfall} = -150\text{ px}$. Phase 1a ($950 \to 850$): $\tau_{\text{lower}}$ shrinks $100 \to 0\text{ px}$; Phase 1b ($850 \to 700$): $\tau_{\text{lower}}$ **saturates at `0f`**, Upper & Lower shift $+150\text{ px}$ down together ($\Delta Y_{\text{lower}}(\text{final}) = +150\text{ px} = -\text{shortfall}$).

2. **Progress-Coupled Formula (`A-MinSafeClamped` with `minSafeScroll - S_pre.toFloat() >= 1f`, `D_SELECTION_ONLY_CENTERED` with `abs(S_effectiveTarget - S_pre.toFloat()) >= 1f`):**
   $$\tau_{\text{lower}}(t) = \Delta G \cdot \left(1 - \frac{S_{\text{actual}}(t).\text{toFloat}() - S_{\text{pre}}.\text{toFloat}()}{S_{\text{effectiveTarget}} - S_{\text{pre}}.\text{toFloat}()}\right).\text{coerceIn}(0\text{f}, \Delta G)$$
   *(Never evaluated when `abs(S_effectiveTarget - S_pre.toFloat()) < 1f` or `!mode1ActuallyStarted`.)*

3. **Mode-2 Handoff Velocity Derived From Actual Physical Scroll Read-Back (`[Proposed]`):**
   Never substitute unconsumed spring `glideVelocity` when View/coordinator clamping occurs:
   - During Mode 1 (`MODE_1_PHYSICAL_COUPLED`), `ScribeGapCollapseController` tracks `prevActualScroll`, `prevFrameTimeNs`, and **`lastValidPreBoundaryPhysVelPxPerSec: Float?`**.
   - On each unclamped Mode-1 frame where `observedDelta = stepResult.actualTotalScroll - prevActualScroll` is non-zero (`!stepResult.boundaryHit && abs(observedDelta) >= 0.5f && dtSec > 0f`): update smoothed physical velocity $v_{\text{phys}} = \text{ema}(\text{observedDelta} / \text{dtSec})$ ($\alpha = 0.65\text{f}$ over consecutive steps to reject single-frame 1px integer quantization jitter).
   - If the final boundary frame consumes less than requested (`stepResult.boundaryHit` or `abs(observedDelta) < 0.5f`), retain `lastValidPreBoundaryPhysVelPxPerSec` directly.
   - **Zero/Sub-1px Span & Inactive Mode-1 Handoff Guard (No Division by Zero / No NaN):**
     Convert $v_{\text{phys}}$ per scenario law before calculating Mode-2 `progressVelocity`:
     - **Progress-coupled (`A-MinSafeClamped`, `D`):**
       ```kotlin
       val physicalSpan =
           S_effectiveTarget - S_pre.toFloat()

       val tauDotHandoff =
           if (
               !mode1ActuallyStarted ||
               vPhys == null ||
               kotlin.math.abs(physicalSpan) < 1f
           ) {
               0f
           } else {
               -(deltaGapPx / physicalSpan) * vPhys
           }
       ```
     - **Lower-anchor (`B`, `C`, `A-MaxClamped`):**
       ```kotlin
       val tauDotHandoff =
           if (!mode1ActuallyStarted || vPhys == null) 0f else vPhys
       ```
     - **Safe monotonic clamp & fallback (all scenarios):**
       ```kotlin
       val progressVelocity =
           if (tauResumePx <= 0.5f || tauDotHandoff >= 0f) {
               0f
           } else {
               (-tauDotHandoff / tauResumePx)
                   .coerceIn(0f, omega2)
           }
       ```
     Synchronous safety snaps, inactive sub-1px paths, and zero-span Scenario-D transitions therefore always start Mode 2 with `progressVelocity = 0f`, guaranteeing finite values in `[0f, omega2]` and zero division by zero or `NaN` propagation.

4. **Exact Closed-Form Mode-2 `Choreographer` Algorithm Matching Monotonicity Proof (`[Derived]` / `[Proposed]`):**
   Rather than discrete Euler integration (which can overshoot at 32 ms frame hitches), `Choreographer.FrameCallback` integrates accumulated **`elapsedTimeSec`** using the **exact analytical critically damped equation** ($\omega = 13.5\text{ rad/s}, \zeta = 1.0, v_0 \in [0, \omega]$):
   ```kotlin
   // Tick 1 (lastFrameTimeNs == 0L): dt = (1f / refreshRateHz).coerceIn(0.001f, 0.032f)
   // Tick k > 1: dt = ((frameTimeNanos - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
   lastFrameTimeNs = frameTimeNanos
   elapsedTimeSec += dt
   val wt = state.omega * elapsedTimeSec
   val residualFraction = kotlin.math.exp(-wt) * (1f + (state.omega - state.progressVelocity) * elapsedTimeSec)
   val nextTau = (state.tauAtResumePx * residualFraction).coerceIn(0f, state.tauAtResumePx)
   ```
   Since $\frac{d}{dt}[e^{-\omega t}(1 + (\omega - v_0)t)] = -e^{-\omega t}[v_0 + \omega(\omega - v_0)t] \le 0$ for all $t \ge 0$ iff $v_0 \in [0, \omega]$, every tick is **provably strictly non-increasing with zero overshoot** regardless of discrete `dt`. Settle when `nextTau <= 0.5f`: snap `tauLower = 0f`, call `editor.invalidate()`, and transition `FINAL_SAFETY_PENDING` $\to$ Event B!

---

## Phase 2 — Pre-Delete Geometry, `Content.replace()` Deferral & Universal Settlement Claim

### 2.1 Surviving-Content Anchor Geometry (`[Source-verified]` / `[Proposed]`)
- **Case I (`C2 < preLineLen(L2)`):** `C1 > 0` $\to$ `isMergedPartialRowSeam = true` (`[Fallback]`). If `C1 == 0`: `seamLine = startLine`, `seamCol = 0`, `preFirstLowerRowTopDocY = preLayout.getRowIndexForPosition(preContent.getCharIndex(L2, C2)) * rowHeight`, `seamFirstLowerRow = postLayout.getRowIndexForPosition(postContent.getCharIndex(startLine, 0))`.
- **Case II (`C2 == preLineLen(L2)`):** if `L2 + 1 >= preLineCount` $\to$ `hasSurvivingLower = false` (`[Fallback]`). Else `seamLine = startLine + 1`, `seamCol = 0`, `preFirstLowerRowTopDocY = preLayout.getRowIndexForPosition(preContent.getCharIndex(L2 + 1, 0)) * rowHeight`, `seamFirstLowerRow = postLayout.getRowIndexForPosition(postContent.getCharIndex(startLine + 1, 0))`.
- Active-seam baseline initialized immediately on split start: `(activeSeamLine = seamLine, activeSeamFirstLowerRow = seamFirstLowerRow, activeSeamDocY = seamFirstLowerRow * rowHeight.toFloat())`.

### 2.2 `Content.replace()` Event Ordering & Universal Settlement Claim (`[Source-verified]` / `[Proposed]`)

**Why `afterDelete()` Must Guard on `!isInContentReplace` (`[Source-verified]` `Content.java:585-587`):**
`Content.replace()` calls `dispatchBeforeReplace()` $\to$ `deleteInternal()` (which fires **`ScribeCodeEditor.afterDelete()`**) $\to$ `insertInternal()` (which fires **`ScribeCodeEditor.afterInsert()`**). Calling `prepareGapCollapseFrame1IfReady()` inside `afterDelete()` while `deletionSnapshotCoordinator.isInContentReplace == true` would prematurely collapse an intermediate deletion before its replacement text is inserted!

**Universal Atomic Claim (`UnifiedCanvasLayout`):**
- `canStillSettleGeneration(expectedGen: Long): Boolean`: early-rejection helper only (`isDeletionTransactionActive && deletionTransactionGeneration == expectedGen && hasPendingDeletionSettlement && settledClaimedGeneration != expectedGen && !gapCollapseController.isTransactionOwned && !(editor as ScribeCodeEditor).isInContentReplace`).
- `tryClaimPendingDeletionSettlement(expectedGen: Long): Boolean`: atomically sets `settledClaimedGeneration = expectedGen`, `hasPendingDeletionSettlement = false`, and `clearPendingDeletionLayoutListener()`. Never consumed while `height <= 0 || !isLaidOut || !editor.isLaidOut || !editor.isWordwrapReady() || editor.isLayoutBusyState || editor.layout == null` (preserving retry!).

**Exact Event Ordering Across All Three Deletion/Replace Sequences (`[Source-verified]` / `[Proposed]`):**

1. **Pure Selection Deletion (`Content.delete()` / `deleteText()` — `isInContentReplace == false`):**
   - `beforeModification` captures pre-delete selection & `GapCollapsePreSnapshot`.
   - `afterDelete`: sees `BeginTransaction` & **`!deletionSnapshotCoordinator.isInContentReplace`**, calls `pCanvas.beginPreEditDeletionSnapshot(..., markPendingSettlement = true)` (`gen = G`), then `super.afterDelete()`.
   - `super.afterDelete()` synchronously fires `ContentChangeEvent(ACTION_DELETE)`: listener captures `scheduledGen = G` and posts `if (canvas.canStillSettleGeneration(G)) canvas.handleDeletionSettlement(smooth = true, expectedGen = G)`.
   - Immediately after `super.afterDelete()` returns inside `afterDelete()` (**still `!isInContentReplace`**): calls **`prepareGapCollapseFrame1IfReady(fromGen = G)`**:
     - If layout & wordwrap ready: calls `tryClaimPendingDeletionSettlement(G)` $\to$ `true` $\to$ launches either V1 Gap Collapse (`isTransactionOwned = true`) OR legacy `DELETION_ANCHOR` / `RESET_TO_TOP` once!
     - If layout unready / async wordwrap: does NOT consume claim; leaves `hasPendingDeletionSettlement = true` (marks `forceFallbackOnDelayedSettlement = true` if `!isWordwrapReady()`), allowing `LayoutStateChangeEvent` / `OnLayoutChangeListener` / `ContentChangeEvent.post` to claim `G` once ready (first caller wins, second is a strict no-op; `isDeletionTransactionActive && !canStillSettleGeneration` never falls through to `clampCanvasScrollToMax` or `ensureCursorVisibleAboveKeyboard`).

2. **Empty Replacement (`Content.replace(..., "")` — `isInContentReplace == true`):**
   - `beforeReplace` sets `isInContentReplace = true`; `beforeModification` captures snapshot.
   - `afterDelete` (`isInContentReplace == true`): calls `pCanvas.beginPreEditDeletionSnapshot(..., markPendingSettlement = true)` (`gen = G`) and `super.afterDelete()`, **and SKIPS `prepareGapCollapseFrame1IfReady(G)` because `isInContentReplace == true`!**
   - `ContentChangeEvent(ACTION_DELETE)` (`isInContentReplace == true`): skips immediate/delayed settlement completely (neither empty-doc `resetDocumentToTopFromDeletion` nor `post`).
   - `LayoutStateChangeEvent`: ignored for pending deletion settlement while `isInContentReplace == true`.
   - `insertInternal("")` calls `ScribeCodeEditor.afterInsert()` (`insertedContent.isEmpty()`): `deletionSnapshotCoordinator.onAfterInsert()` resets `isInContentReplace = false` and returns **`SettleEmptyReplaceDelete`**. Immediately after `super.afterInsert()`: if layout & wordwrap ready, calls `prepareGapCollapseFrame1IfReady(fromGen = G)`; else posts `if (canvas.canStillSettleGeneration(G)) canvas.handleDeletionSettlement(smooth = true, expectedGen = G)` — settled **at most once** through `tryClaimPendingDeletionSettlement(G)`.

3. **Non-Empty Replacement (`Content.replace(..., "foo")` — `isInContentReplace == true`, Both Not-Overscrolled AND Overscrolled `[Source-verified]` `ScribeScrollDeletionPolicy.kt:498-512`):**
   - Baseline `onAfterInsert()` returned `None` whenever `wasReplaceWithDelete && insertedNotEmpty && !isTextEmpty && currentTotalScroll > maxCanvasScroll` (overscrolled), leaving the staged deletion snapshot pending (`isDeletionTransactionActive == true`) where a subsequent `LayoutStateChangeEvent` (`ScribeCodeEditor.kt:1453, 1463`) could misinterpret it as an unresolved selection deletion!
   - **Required Explicit `AfterInsertAction` & Lifecycle Fix:** Extend `ScribeDeletionSnapshotCoordinator.AfterInsertAction` with **`data object ClearTransactionAndClampOverscroll : AfterInsertAction()`** (and return `ClearTransaction` or `ClearTransactionAndClampOverscroll` instead of `None` for ALL non-empty replacements):
     - While `isInContentReplace == true`: `afterDelete` stages snapshot `G` and **skips `prepareGapCollapseFrame1IfReady(G)`**; `ContentChangeEvent(ACTION_DELETE)` and `LayoutStateChangeEvent` ignore pending settlement.
     - In `ScribeCodeEditor.afterInsert()` when `insertedNotEmpty == true`: `onAfterInsert()` resets `isInContentReplace = false` and returns:
       - **`ClearTransaction`** when `currentTotalScroll <= maxCanvasScroll` (or `isTextEmpty` edge).
       - **`ClearTransactionAndClampOverscroll`** when `!isTextEmpty && currentTotalScroll > maxCanvasScroll`.
     - **Before `super.afterInsert(...)`:** For BOTH `ClearTransaction` and `ClearTransactionAndClampOverscroll`, immediately call **`pCanvas.claimAndClearLiveDeletionTransaction(preserveRetractionDebt = false)`** (atomically clearing `activeDeletionSnapshot`, `isDeletionTransactionActive`, `hasPendingDeletionSettlement`, removing pending deletion layout listeners, and bumping `deletionTransactionGeneration++` **exactly once** while keeping controller `IDLE`, `tauLower = 0f`, zero split frame).
     - **After `super.afterInsert(...)`:** Because `G` was already cleared and bumped, neither delayed `LayoutStateChangeEvent` nor any posted callback can ever enter V1 gap collapse or legacy deletion settlement for that replacement. For `ClearTransactionAndClampOverscroll` (or if `layout.afterInsert` resulted in `(pCanvas.scrollD + offsetY) > pCanvas.maxCanvasScroll` while `isWordwrapReady() && !isLayoutBusyState`), call **`pCanvas.clampCanvasScrollToMax(smooth = true)`** cleanly (and if wordwrap is still computing asynchronously, `LayoutStateChangeEvent`'s normal `!pCanvas.isDeletionTransactionActive` branch executes `clampCanvasScrollToMax(smooth = true)` once layout completes — without split rendering).
   - Optional `beforeReplace` watchdog `pendingReplaceWatchdog: Runnable?` is tracked and removed inside `onDetachedFromWindow()` (`1816`).

---

## Phase 3 — State Machine, Atomic Cleanup, Literal Sole Coordinator Ownership & Physical Scroll Sync

### 3.1 `GapCollapsePhase`, `Mode2VisualState` & Sealed Event-B Completion (`ScribeGapCollapseController`) (`[Proposed]`)

```kotlin
enum class GapCollapsePhase {
    IDLE,
    MODE_1_PHYSICAL_COUPLED,
    MODE_2_VISUAL_SETTLING,
    POINTER_FROZEN,
    FINAL_SAFETY_PENDING
}

enum class PhysicalScrollOrigin {
    NONE,
    USER_CANVAS_DRAG,
    USER_CANVAS_FLING,
    USER_EDITOR_DRAG_OR_FLING,
    USER_HANDLE_AUTOSCROLL,
    GAP_COLLAPSE_MODE_1,
    EVENT_B_SAFETY_CORRECTION,
    EXTERNAL_PROGRAMMATIC
}

data class Mode2VisualState(
    val tauAtResumePx: Float,
    val elapsedTimeSec: Float = 0f,
    val progressVelocity: Float,  // Clamped in [0f, omega]
    val omega: Float = 13.5f,
    val lastFrameTimeNs: Long = 0L // 0L sentinel -> dt = 1f / refreshHz on frame 1
)

sealed interface SafetyRevalidationResult {
    data object VALIDATED_SAFE : SafetyRevalidationResult
    data object CORRECTION_STARTED : SafetyRevalidationResult
    data object CORRECTED_AND_VALIDATED : SafetyRevalidationResult
    data class DEFERRED_UNTIL_LAYOUT_READY(val reason: String) : SafetyRevalidationResult
    data class UNRESOLVED(val reason: String) : SafetyRevalidationResult
    data object STALE_TRANSACTION : SafetyRevalidationResult
}

data class PendingPostSafetyKeyboardEval(
    val owningGen: Long,
    val expectedSafetyEpoch: Long,
    val targetHeightPx: Int,
    val isHeightShrink: Boolean,
    val isAuthorizedTransition: Boolean,
    val shouldFollowExpansion: Boolean,
    val isKeyboardDismissing: Boolean,
    val isDestinationSurfaceActive: Boolean,
    val dockTransition: ScribeDockTransition
)
```

### 3.2 Atomic Event-B Finalization, Post-`IDLE` Dual-`ACTION_DOWN` Safety & `cancelPreservingTransaction` (`[Source-verified]` / `[Proposed]`)

**Why `ScribeCanvasGlideAnimator.cancel()` Must Not Be Used Preserving a Transaction (`[Source-verified]` `ScribeScrollAnimation.kt:185-202`):**
Baseline `cancel()` copies `val onEnd = activeGlideOnEnd`, nulls `activeGlideOnEnd`, calls `scrollCoordinator.cancelAnimation()`, and **still invokes `onEnd?.invoke()`** + `onCancelledCallback`. Add **`fun cancelPreservingTransaction(reason: String? = null)`** on `ScribeCanvasGlideAnimator` that sets `activeGlideOnEnd = null` *before* local capture and skips both `onEnd` and `onCancelledCallback`.

**Separated Cancellation Responsibilities (Solving Post-`IDLE` Dual `ACTION_DOWN`):**
Split cleanup into three distinct primitives on `UnifiedCanvasLayout`:
1. **`stopLegacyAnimators(preserveRetractionDebt: Boolean)`:** Cancels `canvasAnimator` (safe for non-deletion animators), `cancelRestoration()`, resets `outerScrollResidualPx = 0f` / `editorScrollResidualPx = 0f` / `scrollDFloat = scrollD.toFloat()`, ends keyboard transaction, stops input/editor flings. **Does NOT touch `activeDeletionSnapshot` or advance `deletionTransactionGeneration`.**
2. **`claimAndClearLiveDeletionTransaction(preserveRetractionDebt: Boolean): Boolean`:** Checks `val hasLiveTx = isDeletionTransactionActive || hasPendingDeletionSettlement || isDeletionTailAnchored || isDeletionResetRetractionActive`. If **`!hasLiveTx`**, returns **`false` immediately without clearing anything or incrementing `deletionTransactionGeneration++`!** If `hasLiveTx`, removes listeners, clears snapshot & settlement flags, captures retraction debt if requested, advances **`deletionTransactionGeneration++` exactly once**, and returns `true`.
3. **`commitGapCollapseIdle()`:** Transitions controller to `IDLE` (`tauLower = 0f`, `owningDeletionGen = -1L`, active seam cleared) and schedules post-draw `EditorTextActionWindow` refresh gate.

**Dual `ACTION_DOWN` Trace (`UnifiedCanvasLayout.onUserTouchDown` $\to$ `ScribeCodeEditor.onTouchEvent(ACTION_DOWN)`):**
- On `ACTION_DOWN` #1 (during `MODE_1`, `MODE_2`, or `FINAL_SAFETY_PENDING`): `tryClaimCancellation(G)` succeeds $\to$ locks controller, cancels `SAFE_BOUNDARY_CORRECTION`/Mode-1 via `cancelPreservingTransaction`, stops Choreographer, calls `claimAndClearLiveDeletionTransaction` (`true`, bumps `G -> G + 1` once), calls `commitGapCollapseIdle()` (`IDLE`), stops legacy animators.
- On `ACTION_DOWN` #2 arriving *after* `IDLE` is committed: enters legacy cancellation branch $\to$ calls `stopLegacyAnimators` (safe no-op or stops unrelated legacy glide) and calls `claimAndClearLiveDeletionTransaction` $\to$ sees `hasLiveTx == false` $\to$ **returns `false` (zero second clear, zero second `deletionTransactionGeneration++`, zero old `onEnd`)!**

**Atomic Teardown-Before-`IDLE` for Event B (`finalizeCompletedGapCollapseTransaction` & `finalizeUnresolvedGapCollapseTransaction`):**
1. Validate `(capturedGen, capturedCollapseEpoch)` and call `controller.beginTeardownLockAndConsumeEval(capturedGen, capturedCollapseEpoch)` (prevents re-entry & returns `pendingEval`).
2. Clean Mode-2 Choreographer, safety-retry listeners, `cancelPreservingTransaction`, and `claimAndClearLiveDeletionTransaction(false)` (advances `G -> G + 1` once).
3. **ONLY THEN** call `commitGapCollapseIdle()` (`IDLE` is never exposed with live transaction state!).
4. Synchronously call `editor.invalidate()` and finally invoke `dispatchPendingPostSafetyKeyboardEval(pendingEval)` if non-null.

### 3.3 Literal Sole Coordinator Ownership of `finishAnimation()`, `applyStepWithResult()` Lifecycle & Callback Coalescing (`[Source-verified]` / `[Proposed]`)

**Audit of All `finishAnimation()` Call Sites in Baseline (`[Source-verified]`):**
- `ScribeScrollAnimation.kt:107` (`advanceFrame`) — **Removed.**
- **`ScribeScrollAnimation.kt:167` (`startAuthoritativeAnimation` when `distance < 1f`) — Removed!**
- `ScribeScrollCoordinator.kt:181` (`applyStep`) & `187` (`fun finishAnimation`) — **Retained as sole owner.**

**1. Coordinator Result & Completion Contract (`ScribeScrollCoordinator.applyStepWithResult`):**
`ScrollStepResult` MUST expose at least:
- `operationId: Long`
- `actualTotalScroll: Float`
- `consumedDelta: Float`
- `boundaryHit: Boolean`
- `terminationReason: StepTerminationReason`
- `isOperationFinished: Boolean`

`applyStepWithResult(newAnimatedScroll: Float, operationId: Long? = null, isSpringSettled: Boolean = false): ScrollStepResult` MUST:
1. Reject stale operation IDs (`operationId != null && operationId != activeOperationId`, or `!isAnimationActive`) without finishing or mutating another operation (returning `isOperationFinished = false` with current `actualTotalScroll`).
2. Apply the physical scroll step and compute actual consumption/read-back (`actualTotalScroll`, `consumedDelta`, `boundaryHit`).
3. Determine completion solely inside the coordinator from the existing target-reached condition (`abs(totalScroll - target) < 0.5f`), boundary termination (`hitBoundary`), or `isSpringSettled`.
4. Call the coordinator's own `finishAnimation(operationId)` **exactly once** when the operation terminates.
5. Return `ScrollStepResult` identifying the actual `terminationReason` and `isOperationFinished`.

Do not delegate completion detection or `finishAnimation()` calls to `ScribeCanvasGlideAnimator`.

**2. Explicit `requestTarget()` Return Contract (`ScribeScrollCoordinator`):**
`requestTarget(...) == false` has two distinct coordinator-side meanings (plus `startAuthoritativeAnimation`'s active-glide coalescing branch below) and MUST NEVER be treated as a single ambiguous signal that the target was reached:
1. **Inactive sub-1px no-op (`!isAnimationActive && abs(clampedTarget - totalScroll) < 1f`):** Coordinator is idle and `totalScroll` is already within `< 1f` of `clampedTarget`; no active operation is created (`activeTargetTotalScroll = clampedTarget`).
2. **Same-reason active-target coalescing (`isAnimationActive && activeReason == reason && !forceRetarget && abs(currTarget - clampedTarget) <= 2f`):** Coordinator is **already executing an active animation** toward that target; no new operation is created and the active animation is still running (caller MUST NOT invoke `onEnd` immediately!).
3. **True (`shouldStart == true`):** A fresh `activeOperationId++` was created and `isAnimationActive = true`.

**3. Animator Callback Delivery After Normal Mode-1 Completion (`ScribeCanvasGlideAnimator.advanceFrame()`):**
`advanceFrame()` submits `requestedStep` (`glideTarget` if `isSettled && distToTarget < 1f`, else `glidePosition`) and `isSpringSettled` to `val result = scrollCoordinator.applyStepWithResult(requestedStep, opId, isSpringSettled)`.

When `result.operationId == opId && result.isOperationFinished`:
1. Set `isGlideRunning = false`.
2. Capture `activeGlideReason` and `activeGlideOnEnd` into local variables.
3. Clear `activeGlideReason = null` and `activeGlideOnEnd = null`.
4. Invoke `onSettledCallback?.invoke(completedReason)` **exactly once**.
5. After that callback returns, invoke the captured `onEnd` **exactly once only if all three conditions remain true**:
   ```kotlin
   !isGlideRunning &&
   !scrollCoordinator.isAnimationActive &&
   opId == scrollCoordinator.activeOperationId
   ```
   *(Preserves existing behavior when `onSettledCallback` starts a replacement safety-correction animation: the old operation's `onEnd` must not prematurely start Mode 2 or another completion path.)*

If `result.isOperationFinished` is `false` (and `opId == scrollCoordinator.activeOperationId && scrollCoordinator.isAnimationActive`), continue scheduling the next animation frame normally (`postOnAnimationAction(glideFrameRunnable)`); if a stale/cancelled op ID was observed, stop the runnable (`isGlideRunning = false`) without firing `onSettledCallback` or `onEnd`. Never invoke `onSettledCallback` or `onEnd` a second time for the same operation if `applyStepWithResult()` already reported completion through a boundary or target-reached result.

**4. Unified `startAuthoritativeAnimation()` Sub-1px, Coalescing & Cancellation Semantics:**
In `ScribeCanvasGlideAnimator.startAuthoritativeAnimation(currentScroll, targetTotalScroll, reason, maxCanvasScroll, forceRetarget, onEnd)`, let `clampedTarget = targetTotalScroll.coerceIn(0f, maxCanvasScroll.toFloat())` and `physicalDistance = clampedTarget - currentScroll`:
1. **Active Glide Coalescing vs Retargeting (`if (isGlideRunning && scrollCoordinator.isAnimationActive)`):**
   - **Same-target coalescing (`!forceRetarget && abs(glideTarget - clampedTarget) <= 1f`):** Do **NOT** create a new op and do **NOT** invoke `onEnd` immediately. Chain `onEnd` after the existing `activeGlideOnEnd` and `return false`.
   - **Actual retarget (`forceRetarget || abs(glideTarget - clampedTarget) > 1f`):** Update `glideTarget = clampedTarget`, `activeGlideReason = reason`, preserve `glideVelocity`, chain `onEnd` after `activeGlideOnEnd`, call `scrollCoordinator.retargetActiveOperation(clampedTarget, reason, maxCanvasScroll)`, and `return true`.
2. **Fresh Inactive Sub-1px No-Op (`if (!scrollCoordinator.isAnimationActive && abs(physicalDistance) < 1f)`):**
   - Preserve the distinct immediate-completion path unchanged across `0 px`, `0.03 px`, `0.05 px`, `0.50 px`, and `0.999 px`:
     ```kotlin
     scrollCoordinator.syncActualScroll(currentScroll)
     onEnd?.invoke()
     return false
     ```
     This creates no operation, does **not** call `finishAnimation()`, and does **not** invoke the normal active-glide `onSettledCallback`.
   - When `abs(physicalDistance) >= 1f` (including **exact `1.00 px`** — MUST NOT use `<= 1f`), proceed directly to `scrollCoordinator.requestTarget(...)`.
3. **Coordinator `requestTarget(...)` & Rare Created-Op `< 1f` Discrepancy:**
   - If `requestTarget(...)` returns `false`: if `scrollCoordinator.isAnimationActive`, chain `onEnd` onto `activeGlideOnEnd` (never fire early!) and `return false`; otherwise (`!isAnimationActive`), call `scrollCoordinator.syncActualScroll(currentScroll)`, invoke `onEnd?.invoke()` once, and `return false`.
   - If `requestTarget(...)` returns `true` (`opId = scrollCoordinator.activeOperationId`) yet `abs(target - currentScroll) < 1f`:
     - Complete it through **`val result = scrollCoordinator.applyStepWithResult(target, opId, isSpringSettled = true)`** (never call `finishAnimation()` directly in the animator).
     - Deliver its `onEnd?.invoke()` **exactly once** after `result.isOperationFinished` confirms successful coordinator completion (`!scrollCoordinator.isAnimationActive && opId == scrollCoordinator.activeOperationId`), and `return false`.
4. **Cancellation Semantics:**
   - Standard `cancel()` and `cancelPreservingTransaction()` retain their separately specified cancellation semantics (`cancelPreservingTransaction()` discards `activeGlideOnEnd` and suppresses both `onEnd` and `onCancelledCallback`). Neither cancellation path delivers a normal successful-settlement `onSettledCallback`.
- **Zero `finishAnimation()` calls remain in `ScribeScrollAnimation.kt`.**

### 3.4 Pointer Dispatch `try/finally`, Option-A Fling & `PhysicalScrollOrigin` Sync (`[Source-verified]` / `[Proposed]`)
- `UnifiedCanvasLayout.dispatchTouchEvent` wraps both `inputController.dispatchTouchEvent(ev)` short-circuit (`ScribeScrollInputController.kt:101, 130`) and `super.dispatchTouchEvent(ev)` in `try { ... } finally { if (ACTION_UP || ACTION_CANCEL) onPointerGestureEndedIfNeeded(ev) }`.
- Explicit `PhysicalScrollOrigin` scopes tag `USER_CANVAS_DRAG`, `USER_CANVAS_FLING`, `USER_EDITOR_DRAG_OR_FLING` (`ScrollEvent.CAUSE_USER_DRAG`/`CAUSE_USER_FLING`), and `USER_HANDLE_AUTOSCROLL` (contrasted against `CAUSE_MAKE_POSITION_VISIBLE` / `EXTERNAL_PROGRAMMATIC`).
- `notifyViewportScrollChanged()` continuously calls `scrollCoordinator.syncActualScroll((scrollD + editor.offsetY).toFloat())` whenever `!scrollCoordinator.isAnimationActive && !canvasAnimator.isGlideRunning`. During Mode 2, authorized origins update `lastMode2KnownPhysicalScroll`; `EXTERNAL_PROGRAMMATIC` drift $> 0.5\text{ px}$ aborts Mode 2 into Event B (`syncActualScroll` first).

---

## Phase 4 — Single-Lifecycle `ScribeEditorRenderer`, Per-Layer Strategy & `EditorTextActionWindow` Refresh

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
In Sora (`EditorRenderer.java:503-579, 917-944, 1350-1359`), `firstLn` (`MutableInt?`, non-null iff `isFirstLineNumberAlwaysVisible && isWordwrap`) is populated in `drawRows` line `1355` when `row == firstVis`, while subsequent leading rows (`rowInf.isLeadingRow`) append `(line, row)` to `lineNumbersOut`. In V1 (`lineNumberNotPinned == true`), let `bottom = editor.getRowBottom(0)`, `rowH = editor.rowHeight`, `gutterLeft = maxOf(0f, offsetX)`, `dividerLeftX = (offsetX + lineNumberWidth + sideIconWidth + editor.dividerMarginLeft).coerceAtLeast(gutterLeft)`, `pass2TopScreenY = editor.getRowTop(pass2RowRange.first) - editor.getOffsetY() + tauLower`, and when `pass2LineNumbers.isNotEmpty()` let `row2_0 = IntPair.getSecond(pass2LineNumbers.get(0))` with translated top:
$$\text{translatedTop2} = \text{editor.getRowTop}(row2\_0) - \text{editor.getOffsetY}() + \tau_{\text{lower}}$$
Baseline Y helpers matching Sora (`555, 558`):
- $y_{\text{pinned}} = (\text{rowBottom}(0) + \text{rowTop}(0))/2\text{f} - (\text{descent} - \text{ascent})/2\text{f} - \text{ascent}$
- $y_{\text{pushed}, P2} = \text{translatedTop2} - \text{rowH}/2\text{f} - (\text{descent} - \text{ascent})/2\text{f} - \text{ascent}$ (algebraically equals Sora `558` when $\tau_{\text{lower}} = 0$)

| Case (`[Source-verified]` / `[Proposed]`) | Native `firstLn` | Consulted Buffer & Top Calc | `overrideFirstLnLine` & Y Position | Clip, Translation & Placement vs Divider (`drawDivider` X) |
|---|---|---|---|---|
| **1. Both partitions visible w/ Pass-1 leading rows** (`!P1.isEmpty && !P2.isEmpty && postDrawLineNumbers.isNotEmpty`) | **Retained** (`pass1FirstLn = firstLn`, `pass2FirstLn = null`) | `postDrawLineNumbers` ONLY (`row1 = get(0).second`); Sora `553` tests unshifted `rowTop(row1) - offsetY`. | Not needed (`-1`). | Native Sora (`548-578`) draws `firstLn` unshifted inside `clipRect(0, stuckLineBottom, editor.width, editor.height)` after `drawDivider`; `pass2LineNumbers` draw inside `drawSideIcons` under Pass-2 clip `[pass2ClipTop, h]` + `translate(0, +tauLower)`. |
| **2. Pass 1 visible, NO Pass-1 leading row** (`!P1.isEmpty && postDrawLineNumbers.isEmpty`) | **Suppressed** (`captured = firstLn.value`, then set `firstLn.value = -1`) | `pass2LineNumbers`; compute `translatedTop2 = rowTop(row2_0) - offsetY + tauLower`. | **Required** (`overrideFirstLnLine = captured`): `y_pinned` if `pass2LineNumbers.isEmpty \|\| translatedTop2 > bottom`, else `y_pushed_P2` (**Case 4**). | Drawn in `drawSideIcons` **unshifted** at Sora's gutter X (`LEFT: offsetX`, `RIGHT: offsetX + lineNumberWidth`, `CENTER: offsetX + (lineNumberWidth + dividerMarginLeft)/2f`), strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` (never slicing pinned/pushed `firstLn` at `pass1ClipBottom` or `pass2ClipTop`). |
| **3. Pass 1 empty & Pass 2 visible** (`P1.isEmpty && !P2.isEmpty`) | **Suppressed** (`firstLn.value = -1`; `pass2FirstLn = MutableInt(-1)`) | `pass2LineNumbers` & `pass2TopScreenY = rowTop(P2.first) - offsetY + tauLower`. | **Required** (`pass2FirstLn.value`):<br>• `pass2TopScreenY <= 0f`: `y_pinned` or `y_pushed_P2` (**Case 4**).<br>• `pass2TopScreenY > 0f`: (**Case 5**) draw inside Pass-2 canvas at `rowCenterY(P2.first) - offsetY`. | `pass2TopScreenY <= 0f`: unshifted in `drawSideIcons`, strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` *(so `pass2TopScreenY <= 0f` never clips `firstLn` against `pass2ClipTop`)*.<br>`pass2TopScreenY > 0f`: inside Pass-2 `clipRect(gutterLeft, pass2ClipTop, dividerLeftX, h)` + `translate(0, +tauLower)` ($C^0$-continuous at `pass2TopScreenY = 0f`). |
| **4. Pass-2 first leading row colliding w/ `firstLn`** (`(!P1.isEmpty && postDrawLineNumbers.isEmpty)` OR `(P1.isEmpty && pass2TopScreenY <= 0f)` AND `translatedTop2 <= bottom`) | **Suppressed** | `pass2LineNumbers.get(0)` using `translatedTop2`. | **Required** at `y_pushed_P2`. | Unshifted in `drawSideIcons`, strictly left of `dividerLeftX`, using Sora's full-height viewport clip `clipRect(0f, stuckLineBottom, editor.width.toFloat(), editor.height.toFloat())` (`y_pushed_P2` sits cleanly above `translatedTop2` with zero overlap against Pass-2 numbers below `pass2ClipTop`, and full-height viewport clip prevents cutting off the pushed glyph when `pass2ClipTop` is near `stuckLineBottom`). |
| **5. Pass 2 visible w/ blank visual gap above it** | Per Cases 1/2 if `!P1.isEmpty` (`translatedTop2 > bottom`); **Suppressed** if `P1.isEmpty` (`pass2TopScreenY > 0f`) | If `!P1.isEmpty`: Case 1/2 keeps `firstLn` unpushed at `y_pinned`. If `P1.isEmpty`: `pass2TopScreenY > 0f`. | Drawn inside Pass 2 (`P1.isEmpty`) so label stays attached to `P2.first` instead of floating in empty gap `[0, pass2TopScreenY]`. | Pass-2 `clipRect(gutterLeft, pass2ClipTop, dividerLeftX, h)` + `translate(0, +tauLower)`, strictly left of `dividerLeftX` (note `P2.first` is excluded from `pass2LineNumbers` by `drawRows:1355`, so drawing `pass2FirstLn.value` preserves its line number too). |
| **6. First visible row is a wrapped continuation row** (`!layout.getRowAt(firstVis).isLeadingRow`) | If `!P1.isEmpty`: Case 1 (retained) or Case 2 (suppressed). If `P1.isEmpty`: Suppressed. | `drawRows:1355` always writes `line_top` into `requiredFirstLn` even when `!isLeadingRow`, routing straight to Case 1, Case 2 (+ Case 4), Case 3 (+ Case 4), or Case 5. | Follows the corresponding Case 1 / 2 / 3 / 4 / 5 formula identically with `line_top`. | Same exact Pass-1/unshifted full-height viewport clip or Pass-2/translated clip and divider-left X alignment. |

*(Because `drawLineNumberBackground` runs at `525`, `drawSideIcons` at `543`, and `drawDivider` at `546` strictly to the right at `dividerLeftX >= offsetX + lineNumberWidth`, all single `super.draw(canvas)` / `drawView()` invariants remain intact without a second renderer.)*

### 4.3 Option B — Conservative V1 Cursor-Animation Policy (`[Source-verified]` / `[Proposed]` / `[Fallback]`)
In Sora `0.24.6` (`EditorRenderer.java:528-534, 609-615, 1195-1198, 2439-2456`), `cursorAnimator.isRunning()` bypasses `postDrawCursor` `DrawCursorTask`s (which carry Pass-2 `+tauLower`) and instead invokes `drawSelectionOnAnimation(canvas)` using `animatedX()`/`animatedY()` directly AND paints gutter highlight inside `drawView()` (`528-534`). Therefore V1 adopts **Option B**:
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
   - **Text-Action-Window Safety During Asynchronous Completion:** Keep `EditorTextActionWindow.isEnabled = false` throughout `FINAL_SAFETY_PENDING`. Never re-enable it merely because split rendering has been disabled; re-enable ONLY through the Section 4.4 post-draw cursor-descriptor refresh gate after the transaction reaches `IDLE`, an unsplit renderer pass has completed, `cursorAnimator.isRunning() == false`, and the saved generation and existing selection/focus/handle/layout guards still pass.

### 4.4 `ScribeCodeEditor` Row-Override Reset & Deferred `EditorTextActionWindow` Re-Enable Timing (`[Source-verified]` / `[Proposed]`)
- `@UiThread var renderPassRowRangeOverride: IntRange? = null` clears in `try/finally` around every pass AND in `ScribeEditorRenderer.draw(canvas)` `finally`.
- **Preventing Stale `EditorTextActionWindow` Display Before Handle Refresh (`[Source-verified]` `EditorTextActionWindow.java:214, 241, 296, 331`, `EditorRenderer.java:592-598`):**
  1. Entering split collapse (`shouldApplySplitRender` `false -> true`), capture `savedTextActionWindowEnabled = aw.isEnabled` and set `aw.isEnabled = false` (`dismiss()` immediately).
  2. Keep **`aw.isEnabled = false` throughout split collapse AND after transitioning to `IDLE`** until the first completed unsplit render pass actually executes `DrawCursorTask`s!
  3. Inside `ScribeEditorRenderer.drawView(canvas)`: record **`didRefreshCursorDescriptorsOnLastDraw = !controller.shouldApplySplitRender && !editor.cursorAnimator.isRunning()`** *(because `EditorRenderer.java:592-598` skips `postDrawCursor` `DrawCursorTask`s and runs `drawSelectionOnAnimation(canvas)` when `cursorAnimator.isRunning()`, leaving selection handles unrefreshed!)*.
  4. Inside `ScribeEditorRenderer.draw(canvas)` after `super.draw(canvas)` finishes: notify `editor.onAfterRendererDrawPass(++renderedDrawPassSeq, didRefreshCursorDescriptorsOnLastDraw)`.
  5. On `IDLE` commit (`commitGapCollapseIdle`): if `savedTextActionWindowEnabled == true`, arm `pendingTextActionRefreshTargetSeq = renderer.renderedDrawPassSeq + 1L`, `pendingTextActionRefreshGen = deletionTransactionGeneration`, keep `aw.isEnabled = false` (blocking any queued Sora `postInLifecycle(displayWindow)` / `postDisplay` runnables), and synchronously call `editor.invalidate()`.
  6. Only when `onAfterRendererDrawPass(seq, didRefreshCursorDescriptors)` observes `!controller.shouldApplySplitRender && seq >= pendingTextActionRefreshTargetSeq && didRefreshCursorDescriptors`: clear the seq gate and post tracked `pendingTextActionRefreshRunnable` via `editor.postOnAnimation(...)`.
  7. Inside `pendingTextActionRefreshRunnable` (next UI frame outside `onDraw`): validate `isAttachedToWindow && savedTextActionWindowEnabled && cursor.isSelected && hasFocus && visibility == View.VISIBLE && deletionTransactionGeneration == pendingTextActionRefreshGen && !controller.shouldApplySplitRender && !eventHandler.hasAnyHeldHandle() && !eventHandler.isDragSelecting && !isLayoutBusyState && !editor.cursorAnimator.isRunning()`; **only now** restore `aw.isEnabled = true` and call `aw.displayWindow()`! (And remove `pendingTextActionRefreshRunnable` + clear pending tokens on detach / new deletion / cancel.)

---

## Phase 5 — Touch Mapping, Incremental Insertion Rebasing, Internal Mutation Audit & Serialized Resize

### 5.1 Touch & Handle Coordinate Proof (`[Source-verified]` / `[Proposed]`)
- `untranslateYOffset(yOffset)` subtracts `tauLower` at most once for `yOffset >= seamDocY + tauLower`, clamping `[seamDocY, seamDocY + tauLower)` to `seamDocY` inside `getPointPosition` / `getPointVisualPosition`.
- `isTouchOnHandle` adds `+controller.getTranslationForPosition(line, col)`.

### 5.2 Incremental Active-Seam Baseline & Exact `(seamLine, 0)` Rebase Rules (`[Source-verified]` / `[Proposed]`)
1. Before content edit (`beforeModification`) while `controller.isResumableCollapse`: capture `(preEditSeamLine = controller.activeSeamLine, preEditSeamDocY = controller.activeSeamDocY, preEditTau = controller.tauLower)`.
2. After `super.afterInsert` (`isWordwrapReady() && !isLayoutBusyState`, else Row-4 fallback):
   - Map `(preEditSeamLine, 0)` post-insert (`newSeamLine`, `newSeamCol`), measure `postSeamDocY`, compute $\Delta Y_{\text{insert}} = \text{postSeamDocY} - \text{preEditSeamDocY}$:
     - `-0.5f <= deltaYInsert <= 0.5f && newSeamCol == 0`: Preserve phase & `tauLower = preEditTau`, update active-seam baseline.
     - `0.5f < deltaYInsert <= preEditTau && newSeamCol == 0`: Rebase `residual = (preEditTau - deltaYInsert).coerceAtLeast(0f)` and update baseline (`collapseAnimEpoch++`, `G` preserved).
     - `deltaYInsert > preEditTau`, `< -0.5f`, `newSeamCol != 0`, or async layout: Row-4 `[Fallback]` (`cancelGapCollapseAndDeletionTransaction`, `syncActualScroll()`, then **explicitly call `ensureSelectionVisible()` + `clampCanvasScrollToMax(smooth = true)`**).

### 5.3 Internal Content Mutation Audit & V1 Policies (`[Source-verified]` / `[Proposed]`)
- **Policy A (Seam-Aware):** `enforceAutoIndentFloor()` (`isEnforcingIndentFloor = true`), `commitText()`, single-line `handleProseNewline()` (`text.insert`), `pasteText()`, single-line `applyFormat` (`insert`) / `applyLinePrefix` (`insert`).
- **Policy B (Pre-Mutation Cancel):** `applyFormattedTextSilently()` (`1040`), `updateImageDescription()` (`2088`), `removeImageTag()` (`2102`), `setText()` (`1269`), multi-line `applySmartPrefix` (`4377`), and selection `applyFormat` (`replace`).

### 5.4 Complete 8-Row V1 Mid-Collapse Operation Policy (`[Proposed]` / `[Fallback]`)
- **1a.** Insert strictly below active seam: Preserve phase, `tauLower`, `G`, `E`, baseline unchanged.
- **1b.** Insert at/above seam with `-0.5f <= deltaYInsert <= 0.5f`, `newSeamCol == 0`: Preserve phase, `tauLower`, `G`, `E`; update baseline.
- **2.** Small insert at/above seam (`0.5f < deltaYInsert <= preEditTau`, `newSeamCol == 0`): Rebase `tauLower = preEditTau - deltaYInsert` to Mode 2 (`or POINTER_FROZEN`).
- **3.** Small Undo / Redo-insert matching Row 2 geometry: Identical rebase.
- **4.** Large Undo/insert, `< -0.5f`, `newSeamCol != 0`, or async layout: `cancelGapCollapseAndDeletionTransaction` + `syncActualScroll` + `ensureSelectionVisible()` + `clampCanvasScrollToMax()`.
- **5.** Second selection deletion / Undo-delete: Interrupt active collapse, `syncActualScroll`, begin Deletion `#2` (`G + 1`) as `INELIGIBLE_FALLBACK`.
- **6.** Non-empty replacement & Policy-B batch mutations: Pre-mutation cancel + `releaseDeferralKeepingStartOrigin()`.
- **7.** Empty replacement (`replace(..., "")`): Interrupt active collapse; if idle, handled per Section 2.2 Sequence 2 (`afterDelete` skips collapse while `isInContentReplace == true`, `afterInsert` settles once via `tryClaimPendingDeletionSettlement`).

### 5.5 Deterministic Serialized Height-Resize & Async Continuation + `scrollDFloat` Invariants (`[Source-verified]` / `[Proposed]`)

1. **Width Change (`w != oldw`) / `onDetachedFromWindow()`:**
   Remove `pendingReplaceWatchdog` & `pendingTextActionRefreshRunnable`; clear overlay/text-action tokens; `cancelGapCollapseAndDeletionTransaction(preserveRetractionDebt = false, reason = ...)`; `syncActualScroll((scrollD + editor.offsetY).toFloat())`.
2. **Height-Only Resize (`w == oldw && abs(h - oldh) > 2`) During `controller.isTransactionOwned == true`:**
   1. Stop Mode-2 Choreographer; `canvasAnimator.cancelPreservingTransaction("height_resize_serialize")`; `cancelRestoration()`; `tauLower = 0f` (`editor.invalidate()`).
   2. `transitionToFinalSafetyForFallback(G)` or `restartFinalSafetyClaim(G)` (`collapseAnimEpoch -> safetyEpoch`).
   3. Read `curActual = (scrollD + editor.offsetY).toFloat()`; `scrollCoordinator.syncActualScroll(curActual)`; recompute bounds at new `h`; store `PendingPostSafetyKeyboardEval(owningGen = G, expectedSafetyEpoch = safetyEpoch, targetHeightPx = h, ...)`.
   4. Execute Event B (`completeDeletionVisualTransaction(G, safetyEpoch)`).
   5. Consume `PendingPostSafetyKeyboardEval` **exclusively** inside `beginTeardownLockAndConsumeEval` within **`finalizeCompletedGapCollapseTransaction`** and **`finalizeUnresolvedGapCollapseTransaction`** across all 4 completion paths (`VALIDATED_SAFE`/`CORRECTED_AND_VALIDATED`, `CORRECTION_STARTED -> onEnd -> fresh epoch`, `DEFERRED_UNTIL_LAYOUT_READY -> retry`, and `UNRESOLVED`).
   6. Execute `dispatchPendingPostSafetyKeyboardEval(eval)` only after `IDLE` + `G -> G + 1`, guarded by `isAttachedToWindow && height == eval.targetHeightPx && phase == IDLE && !isTransactionOwned`.
3. **`scrollDFloat` Invariant (`UnifiedCanvasLayout.kt:179, 1250, 2151`):**
   Keep `scrollDFloat = scrollD.toFloat() + outerScrollResidualPx` strictly separate from `S_actual`; whenever residuals zero, set `scrollDFloat = scrollD.toFloat()` (never `S_actual`). Replace `scrollCoordinator.totalScroll = currentScroll` (`858`) with idle-only `scrollCoordinator.syncActualScroll(currentScroll)`.

---

## Phase 6 — Deferred V2 Scope (`[Proposed]`)

- **Deferred V2:** Multi-seam / multi-region renderer splitting; merged partial-line horizontal splitting (`C1 > 0 && C2 < preLineLen(L2)`); optional `0.24.7` upgrade.

---

## Testing Strategy & Extended Validation Matrix (A–F) (`[Proposed]`)

*(Read-only planning phase — zero tests executed yet. All Gradle JVM and instrumentation commands executed during implementation MUST use the exact verified task names recorded in **Phase 0 (`0.4`)**.)*

- **A. Pure Policy, Overlay Floor, `onAfterInsert()`, Canonical `safeMinPhysical` & Sub-Pixel Proofs (`ScribeGapCollapsePolicyTest.kt`):**
  - `(U, L, T)` truth table + `!isCursorAnimationRunning` gate; Scenario A (`S_pre < minSafeScroll` `< 1px` snap vs `>= 1px` Mode 1, `S_pre in safeMinPhysical..safeMax`, `S_pre > safeMax`).
  - Canonical integer `safeMinPhysical` & transient overscroll assertions (explicitly distinguishing requested target `S_requestedTarget` / `S_effectiveTarget` from observed integer physical read-back `actualPhysicalScroll = scrollD + editor.offsetY`, never reporting safe if `actualPhysicalScroll !in safeMinPhysical..safeMax`):
    - `(minSafeScroll = 100.4f, S_pre = 100) -> 101`
    - `(minSafeScroll = 100.9f, S_pre = 100) -> 101`
    - `(minSafeScroll = 101.0f, S_pre = 100) -> 101`
    - `(minSafeScroll = 101.3f, S_pre = 100) -> 102`
    - `(minSafeScroll = 101.3f, S_pre = 101) -> 102`
    - `(minSafeScroll = 101.3f, S_pre = 102) -> 102`
    - Transient `S_actual > safeMax` settled to `safeMax` via Mode 1 / Event B (`SAFE_BOUNDARY_CORRECTION`).
  - Single safety floor (`TOP_BAR` + `BOTTOM_BAR` vs `FLOATING_PILL`).
  - Lower-anchor Mode-1 $\tau_{\text{lower}}(t)$ + positive/zero/negative shortfall proofs; Scenario-specific handoff velocity + closed-form Mode-2 monotonicity across arbitrary discrete ticks; integer-consistent Branch 1/2/3 tests; zero/sub-1px handoff guard tests:
    - Scenario D with `S_effectiveTarget == S_pre`.
    - Scenario D with a nonzero physical span `< 1px`.
    - Scenario D with a Mode-1 span of exactly `1px`.
    - Scenario D with a normal Mode-1 span.
    - Synchronous safety snap followed by Mode 2.
    - All resulting `progressVelocity` values finite and within `[0f, omega2]`.
  - `ScribeDeletionSnapshotCoordinator.onAfterInsert()`: non-empty non-overscrolled (`ClearTransaction`), non-empty overscrolled (`ClearTransactionAndClampOverscroll` — never `None`), empty replace (`SettleEmptyReplaceDelete`).
- **B. Controller, Coordinator, `applyStepWithResult()` Lifecycle, Sub-1px/Coalescing Contract, Physical Velocity, Dual `ACTION_DOWN`, `Content.replace()`, Cursor Animator & Mutation Suites (`ScribeGapCollapseControllerTest.kt`, `ScribeScrollCoordinatorTest.kt`, `ScribeScrollAnimationTest.kt`):**
  - **Suite B1 (Controller):** All 6 `SafetyRevalidationResult` variants (strictly checking integer `actualPhysicalScroll in safeMinPhysical..safeMax`, `effectiveCorrectiveTarget = clampPhysicalSafetyTarget(...)`, and `actualAfterCorrection`) + retry preservation; atomic teardown-before-`IDLE`; `cancelPreservingTransaction()`; Option-B mid-collapse cursor-animator abort across all async Event-B outcomes (Mode 1 `VALIDATED_SAFE`, Mode 2 `CORRECTED_AND_VALIDATED`, `CORRECTION_STARTED` keeping `FINAL_SAFETY_PENDING` & ownership of `G` until correction settles + fresh validation succeeds, `DEFERRED_UNTIL_LAYOUT_READY` keeping ownership with single valid retry, obsolete correction/retry callback rejected against newer generation, split rendering immediately disabled while `FINAL_SAFETY_PENDING`); `PendingPostSafetyKeyboardEval` across all 4 exits. Observed physical read-back handoff velocity tests (normal settled Mode 1 & physically clamped boundary handoff, ignoring unconsumed spring velocity); `PhysicalScrollOrigin` vs `CAUSE_MAKE_POSITION_VISIBLE`. Dual `ACTION_DOWN` after `IDLE` asserting snapshot clear & generation bump occur **exactly once**. Universal `tryClaimPendingDeletionSettlement` & `!isInContentReplace` guard in `afterDelete`; incremental seam + internal mutations.
  - **Suite B2 (Coordinator & Animator Completion / Callback Lifecycle / Sub-1px / Coalescing / Zero-Span Handoff):** Literal zero-other-caller `finishAnimation()` verification (`ScribeScrollAnimation` has 0 calls), all 3 meanings of `requestTarget() == false`, and explicit lifecycle + sub-1px / coalescing / retarget tests:
    1. Normal Mode-1 completion invokes `onSettledCallback` once and then `onEnd` once.
    2. Target-reached and boundary-hit completion both deliver the same callback lifecycle exactly once (never double-invoked).
    3. A settled callback (`onSettledCallback`) starting a replacement operation prevents the previous operation's `onEnd` from firing.
    4. A stale operation result cannot finish, mutate, or invoke callbacks for a newer operation.
    5. `cancelPreservingTransaction()` suppresses both `onEnd` and `onSettledCallback` / `onCancelledCallback`.
    6. `0 px`, `0.03 px`, `0.05 px`, `0.50 px`, and `0.999 px` fresh inactive sub-1px requests invoke `syncActualScroll` and `onEnd` only (no active op, no `finishAnimation()`, no `onSettledCallback`, Mode 2 reads back actual physical scroll and starts with `progressVelocity = 0f`).
    7. `1.00 px` inactive (`abs(physicalDistance) == 1.0f`): MUST NOT take sub-1px short-circuit; creates Mode-1 op via `requestTarget()`, runs glide, settles once via `applyStepWithResult(..., isSpringSettled = true)`.
    8. Active same-target coalescing (`isGlideRunning && isAnimationActive && !forceRetarget && abs(currTarget - clampedTarget) <= 1f`): returns `false` without creating new op, does NOT fire `onEnd` prematurely, chains `onEnd` onto `activeGlideOnEnd` after active op finishes.
    9. Active retarget (`forceRetarget || abs(currTarget - clampedTarget) > 1f`): calls `retargetActiveOperation`, preserves velocity, chains `onEnd`, settles once via `applyStepWithResult(..., isSpringSettled = true)`.
    10. Scenario D zero/sub-1px/exact-1px/normal Mode-1 spans and synchronous safety snap handoff produce finite `progressVelocity in 0f..omega2`.
- **C. Renderer Two-Pass Layer, Approach-A `firstLn` (Cases 1–6) & Cursor-Animator Option-B Suite (`ScribeGapCollapseRendererTest.kt`):**
  - Synchronous `editor.invalidate()` on outer-only Mode-1 and Mode-2 ticks.
  - All 6 Approach-A `firstLn` cases explicitly verified, including:
    1. Unclipped `overrideFirstLnLine` rendering when `P1.isEmpty && pass2TopScreenY <= 0f` (both `y_pinned` and `y_pushed_P2`) using full-height viewport clip `[stuckLineBottom, editor.height]` left of `dividerLeftX`.
    2. Unclipped `y_pushed_P2` rendering when `pass2LineNumbers.get(0)` collides with `firstLn` (`translatedTop2 <= bottom`) in Case 4.
    3. Zero clipping regression for Case 1 (`!P1.isEmpty && postDrawLineNumbers.isNotEmpty`) and Case 2 (`!P1.isEmpty && postDrawLineNumbers.isEmpty`).
    4. Translated Pass-2 `pass2FirstLn` in Case 5 (`P1.isEmpty && pass2TopScreenY > 0f`) clipped to `[pass2ClipTop, h]` left of `dividerLeftX` + wrapped continuation row Case 6.
  - Per-layer clip/translation (`patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawSideBlockLine`, `DrawCursorTask` + Case-I lower-region insertion-handle placement), zero double-shift, Option-B cursor-animator unsplit safety, immediate split-render disable (`shouldApplySplitRender == false`) during `FINAL_SAFETY_PENDING`, and `EditorTextActionWindow` remaining disabled until the post-`IDLE` unsplit cursor-descriptor refresh gate succeeds.
- **D. Pointer Dispatch, Registry Lifecycle, `EditorTextActionWindow` Timing & Serialized Resize (`ScribeGapCollapseIntegrationTest.kt`):**
  - Pointer/fling tests (1)–(6) via `try/finally`.
  - `ScribeOverlayBoundsRegistry` removal on hide, composition exit, layout switch, key replacement, AND proof that an attached-but-hidden composable (`isVisible = false && coords.isAttached == true`) removes its key rather than re-registering stale bounds when `onGloballyPositioned` fires.
  - `EditorTextActionWindow` stays `isEnabled = false` through `IDLE` until unsplit `!cursorAnimator.isRunning()` `DrawCursorTask` completion, blocking stale `postInLifecycle(displayWindow)` calls.
  - Serialized height resize (`dh > 0`, `dh < 0`) + `scrollDFloat` invariant.
- **E. `Content.replace()`, Cursor-Animator & Scenario E End-to-End Tests (`ScribeGapCollapseIntegrationTest.kt`):**
  - Non-empty replacement while **not overscrolled**; non-empty replacement while **overscrolled** (`ClearTransactionAndClampOverscroll`); delayed `LayoutStateChangeEvent` following either replacement asserting zero intermediate or delayed gap collapse starts; empty replacement (`""`); Scenario E; Event-B verification comparing requested corrective targets against observed integer read-back `scrollD + editor.offsetY in safeMinPhysical..safeMax`.
  - Cursor animator: (1) idle at deletion start + Case-I lower-region insertion-handle placement, (2) already running at deletion start (`INELIGIBLE_FALLBACK`), (3) starting during Mode 1, (4) starting during Mode 2 (zero visual gap, transaction safely released before resuming unsplit rendering).
- **F. Existing Regression Suites (`*Test.kt`).**
