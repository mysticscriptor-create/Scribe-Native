# Scribe Native Scroll Architecture Refactor Map

**Commit Reference Baseline:** `d0d39b5` ("fix(viewport): eliminate unnecessary scroll-and-return and coordinate fluid shortcut bar entry")  
**Starting HEAD:** `59c3537`  
**Milestone:** Phase 8 Corrective Audit Applied (Awaiting CI Verification)  
**Verification Scope:** Exhaustive static source code inspection and repository-wide symbol reference verification. (Local Gradle/tests intentionally not executed per bounded instructions; remote verification deferred to GitHub Actions CI).

---

## 1. Executive Summary & Current Architecture State

Across Phases 1 through 6 and the preceding **Correction Pass**, Scribe's monolithic scroll subsystem has been systematically refactored into focused, decoupled components under the dedicated architectural package:
`com.primaloptima.scribe.ui.editor.scroll`

### Architectural Health & Phase 6 Achievements
1. **Full Extraction of Keyboard & IME Viewport State (Phase 6):**
   - Extracted all soft keyboard, IME inset measurement (`WindowInsets.ime`, `WindowInsets.navigationBars`, `isImeVisible`), DataStore baseline observation and debounced persistence, settling detection (`snapshotFlow` sampling), transition latching (`isAwaitingKeyboardOpen`, `isBarTransitionLatched`), and bottom surface sizing into `ScribeBottomSurfaceState.kt` and `rememberScribeBottomSurfaceState`.
   - `MainEditorScreen.kt` fully delegates keyboard observation, baseline calibration, and transition synchronization to `bottomSurfaceState`.
2. **Stateless Policy Preservation:**
   - Preserved `ScribeBottomSurfaceCoordinator.kt` as a 100% stateless pure policy and invariant engine consumed directly by `ScribeBottomSurfaceState`.
3. **Full Restoration of `ACTION_DOWN` Touch Lifecycle (Phase 5 Correction Pass):**
   - Corrected the Phase 5 regression in `ScribeScrollInputController.dispatchTouchEvent` where initial touch-down actions were inadvertently dropped.
   - Restored `Host.onUserTouchDown(touchY: Float)`, reinstating timestamp/coordinate recording, `UnifiedCanvasLayout.onUserTouchDown` invocation (crucial for `MainEditorScreen` drawer dismissal and keyboard restoration), active canvas animation cancellation, compensation invalidation, and editor fling cessation.
4. **Stateless Policy Purity & Compatibility Layer Removal:**
   - Purged all dead forwarding functions, duplicate delegation wrappers, and package-level typealiases from `ScribeScrollPolicy.kt`.
   - Relocated the mutable test-support harness `ContractionSimulationController` into the dedicated unit test package (`app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/`).
   - Removed the mutable diagnostic configuration facade from `ScribeScrollRestorationPolicy.kt` while preserving 100% of internal diagnostic logging via `ScribeScrollDiagnostics.log`.
5. **Type Safety & Coordinator Consolidation:**
   - Standardized `ProductionScrollAnimationCoordinator` in `ScribeScrollAnimation.kt` to reference `ScribeScrollCoordinator` directly.
   - Fully migrated all references to `AuthoritativeScrollCoordinator` and removed the legacy backward-compatibility typealias.
   - Cleaned up unused imports in `UnifiedCanvasLayout.kt` (`VelocityTracker`, `ViewConfiguration`, `PathInterpolator`) while retaining `OverScroller`.

---

## 2. Scroll Subsystem Inventory & Location Matrix

### Production Subsystem Files (10 Files in `com.primaloptima.scribe.ui.editor.scroll`)
| Component / File | Primary Class / Object | Architectural Role & Responsibilities |
| :--- | :--- | :--- |
| **`ScribeScrollPolicy.kt`** | `object ScribeScrollPolicy` | Stateless 1-DOF scroll delta allocation, caret visibility rules, deadbands, breathing margins, safe maximum bounds, and Select All coordinate arbitration. |
| **`ScribeScrollGeometry.kt`** | Models | Pure data structures: `SelectionGeometry`, `SoraCharGeometry`, `SafeTargetValidationResult`, and `CursorTargetResolution`. Zero Android View dependencies. |
| **`ScribeScrollDeletionPolicy.kt`** | `object ScribeScrollDeletionPolicy` | Pure deletion classification (`classifyDeletionIntent`), safe destination math (`calculateSafeDeletionDestination`), and downward tail follow (`calculateDeletionAnchorDelta`). |
| **`ScribeScrollRestorationPolicy.kt`** | `object ScribeScrollRestorationPolicy` | Pure contraction restoration delta calculations (`resolveContractionRestorationDelta`), safe boundary validation (`validateFinalTarget`), and last-line follow delta. |
| **`ScribeScrollCoordinator.kt`** | `class ScribeScrollCoordinator` | Authoritative single-owner scroll state, monotonic `activeOperationId`, causal expansion tracking, smooth in-flight retargeting, and compensation lifecycle. |
| **`ScribeScrollAnimation.kt`** | `ScribeCanvasGlideAnimator`, `ProductionScrollAnimationCoordinator` | Critically damped spring glide physics engine ($\omega=13.5\text{ rad/s}, \zeta=1.0$) and testable `AnimationDriver` coordinating controller. |
| **`ScribeScrollInputController.kt`** | `class ScribeScrollInputController`, `ScribeEditorScrollInputHelper` | `OverScroller`, `VelocityTracker`, touch-slop arbitration, gesture interception, momentum fling handoff, and selection-handle auto-scroll velocity. |
| **`ScribeScrollDiagnostics.kt`** | `object ScribeScrollDiagnostics` | Scoped diagnostics logger (`isDebugDiagnosticsEnabled`, `onScrollDiagnosticLog`, `log`). |
| **`ScribeBottomSurfaceCoordinator.kt`** | `object ScribeBottomSurfaceCoordinator` | Stateless keyboard baseline validation, standard keyboard clamping, and Pure Writer stationary invariant rules. |
| **`ScribeBottomSurfaceState.kt`** | `class ScribeBottomSurfaceState`, `rememberScribeBottomSurfaceState` | Compose lifecycle state holder for IME insets, debounced DataStore baseline persistence, entrance settling, transition latching, and bottom surface sizing delegation. |

### Test Support Files (2 Files in `com.primaloptima.scribe.ui.editor.scroll`)
| Component / File | Primary Class | Location |
| :--- | :--- | :--- |
| **`ContractionSimulationController.kt`** | `class ContractionSimulationController` | `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/` |
| **`ScribeBottomSurfaceStateTest.kt`** | `class ScribeBottomSurfaceStateTest` | `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/` |

---

## 3. Detailed Responsibilities Inventory (19 Baseline Areas)

| Responsibility | Baseline Location (`d0d39b5`) | Refactored Component (`HEAD`) | Classification & Verification Notes |
| :--- | :--- | :--- | :--- |
| **1. Unified Canvas/Editor Scrolling & Delta Allocation** | `UnifiedCanvasLayout.scrollCanvasBy` | `ScribeScrollPolicy.allocateScrollDelta` | Pure Policy delegated from `UnifiedCanvasLayout` View boundary. |
| **2. Document Scroll Position & Header Displacement** | `UnifiedCanvasLayout`, `ScribeScrollPolicy.reconcileScrollState` | `UnifiedCanvasLayout`, `ScribeScrollPolicy` | Pure Policy + View layout translation. |
| **3. Caret & Selection Geometry** | `UnifiedCanvasLayout.getCharGeometry` | `ScribeScrollGeometry.kt` | Pure Geometry models. |
| **4. Caret Visibility Calculations** | `ScribeScrollPolicy.resolveCursorScrollDelta` | `ScribeScrollPolicy.kt` | Pure Policy maintaining 24dp breathing margin & 4dp deadband. |
| **5. Predictive Keyboard Scrolling** | `UnifiedCanvasLayout.predictivelyGlideCursorAboveKeyboard` | `UnifiedCanvasLayout` (via `ScribeCanvasGlideAnimator`) | State coordination driving second-order spring glide. |
| **6. Keyboard Viewport Transactions** | `ScribeScrollPolicy.KeyboardTransaction`, `UnifiedCanvasLayout` | `UnifiedCanvasLayout` (`activeKeyboardTransaction`) | Transaction model in `ScribeScrollPolicy`; lifecycle owned by `UnifiedCanvasLayout`. |
| **7. Deletion and Last-Line Anchoring** | `ScribeScrollDeletionPolicy.kt` | `ScribeScrollDeletionPolicy.kt` | Pure Policy maintaining monotonic non-growth & tail follow. |
| **8. Contraction Restoration** | `ScribeScrollRestorationPolicy.kt` | `ScribeScrollRestorationPolicy.kt`, `ScribeScrollCoordinator` | Pure calculations in Policy; compensation balance in Coordinator. |
| **9. Manual Drag and Fling** | `UnifiedCanvasLayout.kt` touch handlers | `ScribeScrollInputController.kt` | Input & touch integration delegated via `Host` boundary. |
| **10. Sora Editor Scrolling Integration** | `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt` | Sora `CodeEditor` (`offsetY`, `scroller`) | Custom View overrides in editor delegating momentum handoff to `ScribeEditorScrollInputHelper`. |
| **11. Automatic Scroll Animation** | `ScribeScrollPolicy.kt`, `UnifiedCanvasLayout.kt` | `ScribeScrollAnimation.kt` (`ScribeCanvasGlideAnimator`) | Spring physics engine driving `postOnAnimation`. |
| **12. Viewport Measurement & Layout Reconciliation** | `UnifiedCanvasLayout.onMeasure/onLayout` | `UnifiedCanvasLayout`, `ScribeScrollPolicy` | Pure layout rules reconciled against 1-DOF invariant. |
| **13. Keyboard/IME State & Inset Integration** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `ScribeBottomSurfaceState.kt` (`rememberScribeBottomSurfaceState`) | Extracted Compose-aware state holder observing `WindowInsets` and managing lifecycle. |
| **14. Standard Keyboard Height Persistence** | `MainEditorScreen.kt`, `ScribeDataStore` | `ScribeBottomSurfaceState.kt`, `ScribeDataStore` | Debounced 350ms settling, baseline validation, and DataStore updates managed via `LaunchedEffect`. |
| **15. Shortcut-Bar Layout Reservation** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.kt` | `ScribeBottomSurfaceState.kt`, `MainEditorScreen` | UI layout reservation via `calculateEstimatedKeyboardHeightPx` and `resolveEffectiveBottomSurfaceHeight`. |
| **16. Accessory Drawer State, Sizing, Dragging & Transitions** | `MainEditorScreen.kt` | `ScribeBottomSurfaceState.kt`, `ScribeBottomSurfaceCoordinator.kt` | Extracted drawer state container, velocity tracking, snapping, and `Animatable` height transitions. |
| **17. Drawer-to-Keyboard Handoff** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.onUserTouchDown` | `ScribeBottomSurfaceState.kt` + `MainEditorScreen` | `closeDrawerAndRestoreKeyboard`, `startAwaitingKeyboard`, `cancelAwaitingKeyboard`, and `onKeyboardEntranceStarted` lifecycle hooks. |
| **18. Saved Viewport/Session Restoration** | `EditorSessionRestorationCoordinator.kt`, `UnifiedCanvasLayout.kt` | Navigation Layer (`EditorSessionRestorationCoordinator`) | Navigation lifecycle integration preserved in navigation package. |
| **19. Scroll Diagnostics** | `ScribeScrollDiagnostics.kt` | `ScribeScrollDiagnostics.kt` | Centralized scoped diagnostics logging helper. |

---

## 4. File Dependency Graph

```
                    [ UI / Host Integration Boundary ]
         +--------------------------+--------------------------+
         |                          |                          |
[MainEditorScreen.kt]     [UnifiedCanvasLayout.kt]     [ScribeCodeEditor.kt]
         |                          |                          |
         | (delegates)              | (Host)                   |
         v                          v                          v
[ScribeBottomSurfaceState] [ScribeScrollInputController] [ScribeEditorScrollInputHelper]
         |                          |
         |                          v
         |              [ScribeCanvasGlideAnimator]
         |                          |
         |                          v
         +----------------> [ScribeScrollCoordinator]
         |                          |
         |        +-----------------+-----------------+
         |        |                 |                 |
         |        v                 v                 v
         | [ScribeScrollPolicy] [ScribeScrollDeletionPolicy] [ScribeScrollRestorationPolicy]
         |        |                 |                 |
         |        +-----------------+-----------------+
         |                          |
         v                          v
[ScribeBottomSurfaceCoordinator] [ScribeScrollGeometry]
                                    ^
                                    |
                        [ScribeScrollDiagnostics]
```

---

## 5. ScribeBottomSurfaceState Architecture (Phase 6 Complete)

In Phase 6, soft keyboard, IME viewport integration, and transition latching were extracted from `MainEditorScreen.kt` into **`ScribeBottomSurfaceState.kt`**.

### Architecture & Responsibilities
- **Target File:** `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeBottomSurfaceState.kt`
- **Compose State Holder:** Encapsulates `isAwaitingKeyboardOpen`, `isBarTransitionLatched`, `lastKeyboardHeightDp`, `compactHeightDp`, `maxStandardKbBaselineDp`, `isKeyboardVisible`, `imeBottomDp`, `docBottomNavInset`, and `imeAboveNavDp`.
- **Stateless Engine Consumption:** Delegates effective bottom surface sizing, dismissal detection, and settling checks directly to `ScribeBottomSurfaceCoordinator`.
- **Lifecycle Coordination:**
  1. `LaunchedEffect(savedKeyboardHeightFloat)`: Observes DataStore baseline emissions and synchronizes `lastKeyboardHeightDp`.
  2. `LaunchedEffect(imeBottomDp, isKeyboardVisible, docBottomNavInset, isAccessoryDrawerActive)`: Debounces 350ms on keyboard entrance, validates against `isValidStandardKeyboardHeight`, checks `shouldUpdateSavedBaseline`, and updates DataStore baseline.
  3. `LaunchedEffect(isAwaitingKeyboardOpen)`: Samples `imeAboveNavDp` via `snapshotFlow` with 150ms entrance delay and 750ms timeout to detect resting state (<0.5dp delta across 3 consecutive checks >= 140dp), calibrating `lastKeyboardHeightDp` and unlatching flags in `finally`.
- **Transitions:** Provides `startAwaitingKeyboard(unifiedCanvas, onLatched)`, `cancelAwaitingKeyboard()`, and `onKeyboardEntranceStarted(unifiedCanvas)`.

---

## 5.1 Accessory Drawer Viewport & Transition Architecture (Phase 7 Complete, Verified in CI Workflow #806)

In Phase 7, accessory drawer state and viewport transition logic were extracted from `MainEditorScreen.kt` into **`ScribeBottomSurfaceState.kt`** and **`ScribeBottomSurfaceCoordinator.kt`** (parent commit `7388a91`, lexical-scope follow-up commit `fde0518` verified in CI workflow #806).

### Architecture & Responsibilities
- **Enums Extracted:** `AccessoryDrawerState` (COMPACT, EXPANDED, FULL) and `AccessoryDrawerMode` (SNIPPETS, TEMPLATES) located in `com.primaloptima.scribe.ui.editor.scroll`.
- **Stateless Coordinator Calculations (`ScribeBottomSurfaceCoordinator`):**
  - `calculateExpandedHeightDp(compactHeightDp, screenHeightDp)`: 48% screen height clamped between 360dp and 480dp (or compact + 60dp).
  - `calculateFullHeightDp(screenHeightDp)`: 72% screen height clamped between 480dp and 650dp.
  - `calculateDrawerTargetHeightDp(state, compact, expanded, full)`: Pure mapping from drawer state to target height.
  - `calculateNextDrawerState(currentState)`: Cyclic toggle (`COMPACT -> EXPANDED -> FULL -> COMPACT`).
  - `calculateSnapTargetState(currentH, vyDp, compact, expanded, full)`: Fling velocity thresholding (> 300 dp/s down, < -300 dp/s up) and nearest-target snap resolution.
  - `resolveEffectiveDrawerHeightDp(...)`: Live height resolution accounting for drag state, animated height, and bounds.
- **State Container Delegation (`ScribeBottomSurfaceState`):**
  - Encapsulates `activeAccessoryDrawer`, `drawerState`, `isDrawerDragging`, `dragLiveHeightDp`, `lastDrawerToggleTimestamp`, and `drawerHeightDpAnim`.
  - Animates `drawerHeightDpAnim` automatically in a dedicated `LaunchedEffect` within `rememberScribeBottomSurfaceState`.
  - Provides unified lifecycle methods: `openDrawer`, `closeDrawer`, `switchDrawer`, `toggleAccessoryDrawer`, and `closeDrawerAndRestoreKeyboard`.
  - Exposes drag hooks: `onDrawerDragStart()`, `onDrawerDragDelta(deltaPx)`, and `onDrawerDragEndWithVelocity(vyPx, scope)`.
- **UI Boundary Preservation:**
  - `MainEditorScreen.kt` delegates drawer state, drag events, and keyboard transitions cleanly while retaining visual rendering in `AccessoryDrawer` composable.

---

## 5.2 Thin UI/Platform Adapters & Final Integration (Phase 8 Initial Verified in #807; Corrective Audit Applied)

In Phase 8, the remaining integration files (`UnifiedCanvasLayout.kt`, `ScribeCodeEditor.kt`, `MainEditorScreen.kt`) were reduced to thin UI/platform adapters (committed in `9e7ca2f`, initial integration verified in CI workflow #807). Following the initial integration, a focused corrective audit was applied to resolve behavior-preservation edge cases:

### Architecture & Delegations
- **`UnifiedCanvasLayout.kt`:**
  - Retains Android View/FrameLayout lifecycle, measurement (`onMeasure`), layout (`onLayout`), translations (`applyTranslations`), and touch dispatching.
  - Delegates `centerCursorInViewport` target calculations to `ScribeScrollPolicy.calculateCenterCursorTarget`.
  - Delegates predictive glide heuristics and suppression to `ScribeScrollPolicy.calculatePredictiveGlideDecision`.
  - Replaced manual cursor bounds and overflow checks in `requestChildRectangleOnScreen` with `ScribeScrollPolicy.resolveCursorScrollDelta`, while restoring direction-specific dispatch:
    - Downward overflow (`deltaY > 0`): respects deletion transactions, initiates authoritative glide for keyboard transactions / active animations, smooth-scrolls, or immediate-scrolls.
    - Upward underflow (`deltaY < 0`): directly scrolls canvas unless an authoritative glide animation is already active.
  - Replaced hardcoded `24f * density` in `breathingMarginPx` with `ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density`.
  - Purged dead local variables in `predictivelyGlideCursorAboveKeyboard` (`headerRemaining`, `screenBottom`, `deadbandPx`, `targetLineBottom`).
- **`ScribeCodeEditor.kt`:**
  - Delegates vertical scroll permission to `ScribeScrollPolicy.canScrollVertically`, passing `scrollD` and `offsetY` distinctly and falling back to `super.canScrollVertically(direction)` when direction is 0.
  - Delegates canvas forward scroll evaluation to `ScribeScrollPolicy.shouldForwardScrollToCanvas`.
  - Retains mandatory Sora Editor overrides and text editing behavior.
- **`MainEditorScreen.kt`:**
  - Restored persistent keyboard height reservation in `factory` and `update` by delegating to `bottomSurfaceState.estimatedBottomSurfaceHeightDp.value` instead of `effectiveBottomSurfaceHeight.value`. This maintains the compact keyboard reservation baseline (e.g. 280dp) even when keyboard and drawer are closed, avoiding collapsing the canvas viewport and corrupting restoration math.
  - Removed unused lingering variables (`imeBottomDp`) and unused legacy imports (`ScribeBottomSurfaceCoordinator`, `Animatable`, `animateDpAsState`, `draggable`, `rememberDraggableState`, `imePadding`, `isImeVisible`).
- **`EditorSessionRestorationCoordinator.kt`:**
  - Retained in `navigation` package preserving session restoration role.

---

## 6. Observed Defects and Questionable Implementations Backlog

### A. Phase 5 Regression (Resolved by Correction Pass)
- **Defect:** `ScribeScrollInputController.dispatchTouchEvent` omitted `lastTouchDownY/Time`, `cancelCanvasAnimation()`, `invalidateCompensation()`, `stopEditorFling()`, and `onUserTouchDown?.invoke()` on `ACTION_DOWN`.
- **Resolution:** Re-established complete contract in `ScribeScrollInputController` and implemented `Host.onUserTouchDown(touchY: Float)` in `UnifiedCanvasLayout`.

### B. Pre-Existing Baseline Questionable Implementations (Recorded for Later Phases)
1. **Side-Effect in Pure Policy (`ScribeScrollRestorationPolicy`):**
   - `resolveContractionRestorationDelta` internally invokes `ScribeScrollDiagnostics.log(...)`. While the configuration facade was removed, the calculation function still emits diagnostic logs as a side-effect. Decoupling this into the coordinator layer should be considered in later passes.
2. **Touch Heuristic Coupling in UnifiedCanvasLayout (Resolved in Phase 8):**
   - Hardcoded 500ms threshold codified as `ScribeScrollPolicy.RECENT_TOUCH_SUPPRESSION_WINDOW_MS`, and tap suppression decision logic centralized in `ScribeScrollPolicy.calculatePredictiveGlideDecision`.
3. **Dual Animation Systems (`ScribeCanvasGlideAnimator` vs `ProductionScrollAnimationCoordinator`):**
   - Production canvas scrolling uses the critically damped spring glide (`ScribeCanvasGlideAnimator`), while `ProductionScrollAnimationCoordinator` is only exercised in test suites with `AnimationDriver`. Unifying the production spring into an `AnimationDriver` implementation remains an optimization opportunity for Phase 9 or later.
4. **Hardcoded Breathing Margins in Multiple Locations (Resolved in Phase 8):**
   - `UnifiedCanvasLayout.breathingMarginPx` now evaluates directly against `ScribeScrollPolicy.CURSOR_BOTTOM_MARGIN_DP * density`.

---

## 7. Current Milestone Completion Status

- [x] **Phase 1 — Inventory & Extraction Map:** Complete.
- [x] **Phase 2 — Separate Pure Policies & Geometry:** Complete (compatibility shims purged).
- [x] **Phase 3 — Extract Scroll Coordination State:** Complete (`AuthoritativeScrollCoordinator` alias eliminated).
- [x] **Phase 4 — Extract Scroll Animation Code:** Complete.
- [x] **Phase 5 — Extract Scroll Input and Fling Handling:** Complete (`ACTION_DOWN` lifecycle fully restored).
- [x] **Correction Pass:** Complete across all 4 steps.
- [x] **Phase 6 — Extract Keyboard and IME Viewport State:** Complete (committed in `7388a91`, verified in CI workflow #804; precision assertion calibrated).
- [x] **Phase 7 — Extract Accessory Drawer Viewport and Transition State:** Complete (committed in `9481217`, lexical-scope fix `fde0518` verified in CI workflow #806; `AccessoryDrawerState`, `AccessoryDrawerMode`, dragging, snapping, and spring height transitions extracted into `ScribeBottomSurfaceState.kt` and `ScribeBottomSurfaceCoordinator.kt`).
- [/] **Phase 8 — Finish Scroll Subsystem Integration:** Corrective Audit Applied (Initial integration `9e7ca2f` verified in CI workflow #807; corrective follow-up restoring persistent keyboard reservation, direction-specific cursor correction, and exact `canScrollVertically` semantics applied, awaiting CI verification).

**Next Milestone:** Proceed to **Phase 9 — Repository-Wide Architecture, Call-Graph, Reference, and Test Audit** as directed by the user.
