# Scribe Native Scroll Architecture Refactor Map

**Commit Reference Baseline:** `d0d39b5` ("fix(viewport): eliminate unnecessary scroll-and-return and coordinate fluid shortcut bar entry")  
**Starting HEAD:** `f262f7b`  
**Milestone:** Phases 1–5 Complete + Post-Phase 5 Correction Pass Finished  
**Verification Scope:** Exhaustive static source code inspection and repository-wide symbol reference verification. (Local Gradle/tests intentionally not executed per bounded instructions; remote verification deferred to GitHub Actions CI).

---

## 1. Executive Summary & Current Architecture State

Across Phases 1 through 5 and the subsequent **Correction Pass**, Scribe's monolithic scroll subsystem has been systematically refactored into focused, decoupled components under the dedicated architectural package:
`com.primaloptima.scribe.ui.editor.scroll`

### Architectural Health & Correction Pass Achievements
1. **Full Restoration of `ACTION_DOWN` Touch Lifecycle:**
   - Corrected the Phase 5 regression in `ScribeScrollInputController.dispatchTouchEvent` where initial touch-down actions were inadvertently dropped.
   - Restored `Host.onUserTouchDown(touchY: Float)`, reinstating timestamp/coordinate recording, `UnifiedCanvasLayout.onUserTouchDown` invocation (crucial for `MainEditorScreen` drawer dismissal and keyboard restoration), active canvas animation cancellation, compensation invalidation, and editor fling cessation.
2. **Stateless Policy Purity & Compatibility Layer Removal:**
   - Purged all dead forwarding functions, duplicate delegation wrappers, and package-level typealiases from `ScribeScrollPolicy.kt`.
   - Relocated the mutable test-support harness `ContractionSimulationController` into the dedicated unit test package (`app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/`).
   - Removed the mutable diagnostic configuration facade from `ScribeScrollRestorationPolicy.kt` while preserving 100% of internal diagnostic logging via `ScribeScrollDiagnostics.log`.
3. **Type Safety & Coordinator Consolidation:**
   - Standardized `ProductionScrollAnimationCoordinator` in `ScribeScrollAnimation.kt` to reference `ScribeScrollCoordinator` directly.
   - Fully migrated all references to `AuthoritativeScrollCoordinator` and removed the legacy backward-compatibility typealias.
   - Cleaned up unused imports in `UnifiedCanvasLayout.kt` (`VelocityTracker`, `ViewConfiguration`, `PathInterpolator`) while retaining `OverScroller`.

---

## 2. Scroll Subsystem Inventory & Location Matrix

### Production Subsystem Files (9 Files in `com.primaloptima.scribe.ui.editor.scroll`)
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

### Test Support Files (1 File in `com.primaloptima.scribe.ui.editor.scroll`)
| Component / File | Primary Class | Location |
| :--- | :--- | :--- |
| **`ContractionSimulationController.kt`** | `class ContractionSimulationController` | `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/` |

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
| **13. Keyboard/IME State & Inset Integration** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (Compose `WindowInsets`) | UI integration consuming stateless coordinator policies. |
| **14. Standard Keyboard Height Persistence** | `MainEditorScreen.kt`, `ScribeDataStore` | `MainEditorScreen`, `ScribeDataStore` | DataStore persistence governed by `ScribeBottomSurfaceCoordinator` rules. |
| **15. Shortcut-Bar Layout Reservation** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.kt` | `MainEditorScreen`, `UnifiedCanvasLayout` | UI layout reservation via `estimatedKeyboardHeightPx`. |
| **16. Accessory Drawer State, Sizing, Dragging & Transitions** | `MainEditorScreen.kt` | `MainEditorScreen` (local `remember` state) | UI state management (Candidate for `ScribeBottomSurfaceState`). |
| **17. Drawer-to-Keyboard Handoff** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.onUserTouchDown` | `MainEditorScreen` + `ScribeScrollInputController` | Restored touch-down lifecycle triggers drawer collapse & keyboard reopening. |
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
         |                          | (Host)                   |
         |                          v                          v
         |              [ScribeScrollInputController]   [ScribeEditorScrollInputHelper]
         |                          |
         |                          v
         |              [ScribeCanvasGlideAnimator]
         |                          |
         |                          v
         +----------------> [ScribeScrollCoordinator]
                                    |
          +-------------------------+-------------------------+
          |                         |                         |
          v                         v                         v
[ScribeScrollPolicy]   [ScribeScrollDeletionPolicy]   [ScribeScrollRestorationPolicy]
          |                         |                         |
          +-------------------------+-------------------------+
                                    |
                                    v
                         [ScribeScrollGeometry]
                                    ^
                                    |
                        [ScribeScrollDiagnostics]
                                    ^
                                    |
                    [ScribeBottomSurfaceCoordinator]
```

---

## 5. Proposal for ScribeBottomSurfaceState.kt (Deferred Work)

In Phase 1, extraction boundaries identified a potential state container: **`ScribeBottomSurfaceState.kt`**.

### Current Implementation State in `MainEditorScreen.kt`
Keyboard, accessory drawer, and transition state currently live directly inside `MainEditorScreen.kt` as local Compose state:
- `isKeyboardVisible: Boolean` (WindowInsets observation)
- `activeAccessoryDrawer: AccessoryDrawerType?`
- `drawerState: AccessoryDrawerState` (`COMPACT`, `EXPANDED`, `DISMISSED`)
- `drawerHeightDpAnim: Animatable<Float, ...>`
- `isAwaitingKeyboardOpen: Boolean`
- `isBarTransitionLatched: Boolean`
- `lastKeyboardHeightDp: Float`
- `compactHeightDp: State<Float>`
- `isWritingBarSearchActive: Boolean`
- `previewItem: Any?`

### Deferred Boundary Definition
- **Target File:** `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeBottomSurfaceState.kt`
- **Rationale for Deferral:** Extracting Compose `remember`, coroutine scopes, and `Animatable` lifecycles from `MainEditorScreen.kt` belongs strictly to the bottom-surface coordinator refactoring phase (Phases 6/7). Prematurely moving Compose UI state during the document scroll correction pass would violate scope and risk Compose recomposition regressions.
- **Contract:** `ScribeBottomSurfaceCoordinator.kt` remains pure and stateless, while `ScribeBottomSurfaceState.kt` will eventually encapsulate the mutable transition state machine.

---

## 6. Observed Defects and Questionable Implementations Backlog

### A. Phase 5 Regression (Resolved by Correction Pass)
- **Defect:** `ScribeScrollInputController.dispatchTouchEvent` omitted `lastTouchDownY/Time`, `cancelCanvasAnimation()`, `invalidateCompensation()`, `stopEditorFling()`, and `onUserTouchDown?.invoke()` on `ACTION_DOWN`.
- **Resolution:** Re-established complete contract in `ScribeScrollInputController` and implemented `Host.onUserTouchDown(touchY: Float)` in `UnifiedCanvasLayout`.

### B. Pre-Existing Baseline Questionable Implementations (Recorded for Later Phases)
1. **Side-Effect in Pure Policy (`ScribeScrollRestorationPolicy`):**
   - `resolveContractionRestorationDelta` internally invokes `ScribeScrollDiagnostics.log(...)`. While the configuration facade was removed, the calculation function still emits diagnostic logs as a side-effect. Decoupling this into the coordinator layer should be considered in later passes.
2. **Touch Heuristic Coupling in UnifiedCanvasLayout:**
   - Lines 1060–1065 in `UnifiedCanvasLayout.kt` check `if (now - lastTouchDownTime < 500L)` to suppress or bias cursor visibility glides. This hardcoded time threshold should eventually be codified as a named policy constant in `ScribeScrollPolicy`.
3. **Dual Animation Systems (`ScribeCanvasGlideAnimator` vs `ProductionScrollAnimationCoordinator`):**
   - Production canvas scrolling uses the critically damped spring glide (`ScribeCanvasGlideAnimator`), while `ProductionScrollAnimationCoordinator` is only exercised in test suites with `AnimationDriver`. Unifying the production spring into an `AnimationDriver` implementation remains an optimization opportunity for Phase 8.
4. **Hardcoded Breathing Margins in Multiple Locations:**
   - While `CURSOR_BOTTOM_MARGIN_DP` (24dp) is centralized in `ScribeScrollPolicy`, certain layout bounds calculations in `UnifiedCanvasLayout` compute `24f * density` independently.

---

## 7. Current Milestone Completion Status

- [x] **Phase 1 — Inventory & Extraction Map:** Complete.
- [x] **Phase 2 — Separate Pure Policies & Geometry:** Complete (compatibility shims purged).
- [x] **Phase 3 — Extract Scroll Coordination State:** Complete (`AuthoritativeScrollCoordinator` alias eliminated).
- [x] **Phase 4 — Extract Scroll Animation Code:** Complete.
- [x] **Phase 5 — Extract Scroll Input and Fling Handling:** Complete (`ACTION_DOWN` lifecycle fully restored).
- [x] **Correction Pass:** Complete across all 4 steps.

**Next Milestone:** Proceed to **Phase 6** as directed by the user.
