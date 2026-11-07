# Scribe Native Scroll Architecture Refactor Map
**Commit Reference:** `d0d39b5` ("fix(viewport): eliminate unnecessary scroll-and-return and coordinate fluid shortcut bar entry")  
**Current Milestone:** Phase 2 Complete — Separated Pure Scroll Policies and Geometry  
**Status:** Pure calculation policies and geometry extracted. **Zero behavioral changes, zero formula modifications, no builds/tests run.**

---

## 1. Executive Summary & Extraction Status

Following the Phase 1 inventory, **Phase 2** has successfully decoupled all pure geometry models, deletion policies, and downward contraction-restoration policies from `ScribeScrollPolicy.kt` and `UnifiedCanvasLayout.kt` into dedicated, single-responsibility files in `com.primaloptima.scribe.ui.components`.

### Phase 2 Extracted & Updated Files

| File Path | Status | Extracted Responsibilities / Changes |
| :--- | :---: | :--- |
| `com/primaloptima/scribe/ui/components/ScribeScrollGeometry.kt` | **NEW** | Reusable, pure geometry models: `SelectionGeometry`, `SoraCharGeometry`, `SafeTargetValidationResult`, `CursorTargetResolution`. Free of Android framework dependencies. |
| `com/primaloptima/scribe/ui/components/ScribeScrollDeletionPolicy.kt` | **NEW** | Pure deletion calculations and models: `DURATION_DELETION_ANCHOR_MS`, `DeletionSnapshot`, `DeletionAnchorIntent`, `classifyDeletionIntent`, `calculateSafeDeletionDestination`, `calculateDeletionAnchorDelta`. |
| `com/primaloptima/scribe/ui/components/ScribeScrollRestorationPolicy.kt` | **NEW** | Pure contraction restoration and safe boundary target validation: `ContractionRestorationRequest`, `ContractionRestorationResult`, `resolveContractionRestorationDelta`, `validateFinalTarget`, `calculateLastLineFollowDelta`, and scoped diagnostics callbacks. |
| `com/primaloptima/scribe/ui/components/ScribeScrollPolicy.kt` | **UPDATED** | Retains core stateless rules: dimensions (`CURSOR_BOTTOM_MARGIN_DP`, `CURSOR_DEADBAND_DP`, `DRAG_THRESHOLD_DP`), durations, `allocateScrollDelta`, `reconcileScrollState`, `calculateMaxCanvasScroll`, `calculateMaxHeaderScroll`, `calculateSafeScrollMaxY`, `resolveCursorScrollDelta`, `resolveTargetCoordinates`, `SelectAllScrollMode`, `ZoomTransaction`, `KeyboardTransaction`. Forwards deletion/restoration/geometry to new single-source files for temporary compatibility. Stateful coordinator and simulation harness intentionally retained for Phases 4 & 5. |
| `com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt` | **UPDATED** | Removed duplicate inner `SoraCharGeometry`; migrated callers directly to `ScribeScrollDeletionPolicy`, `ScribeScrollRestorationPolicy`, and `SelectionGeometry`. |
| `com/primaloptima/scribe/ui/components/ScribeBottomSurfaceCoordinator.kt` | **PRESERVED** | Preserved exact existing stateless keyboard/bottom-surface functions (`calculateMaxStandardBaselineDp`, `isValidStandardKeyboardHeight`, `shouldUpdateSavedBaseline`, `resolveEffectiveBottomSurfaceHeight`, `isKeyboardDismissing`, `isKeyboardSettled`, `shouldFollowDownward`). |
| `com/primaloptima/scribe/ui/components/CursorAndCanvasGeometryTest.kt` | **UPDATED** | Migrated test callers directly to `ScribeScrollDeletionPolicy`, `ScribeScrollRestorationPolicy`, and `SelectionGeometry`. |

---

## 2. Inventory of the 19 Scrolling Responsibilities & Phase 2 Placement

| Responsibility | Phase 2 Location | Mutable State Owner | Classification |
| :--- | :--- | :--- | :--- |
| **1. Unified Canvas/Editor Scrolling & Delta Allocation** | `ScribeScrollPolicy.allocateScrollDelta`, `UnifiedCanvasLayout.scrollCanvasBy` | `UnifiedCanvasLayout` (`scrollD`, `scrollDFloat`), Sora `editor` (`offsetY`) | Pure Policy + UI Integration |
| **2. Document Scroll Position & Header Displacement** | `ScribeScrollPolicy.reconcileScrollState`, `UnifiedCanvasLayout.applyTranslations` | `UnifiedCanvasLayout` (`scrollD`, `headerHeight`) | Pure Policy + View Layout |
| **3. Caret and Selection Geometry** | `ScribeScrollGeometry.kt` (`SelectionGeometry`, `SoraCharGeometry`) | Sora `CodeEditor` (layout, cursor, text) | Pure Geometry Models |
| **4. Caret Visibility Calculations** | `ScribeScrollPolicy.resolveCursorScrollDelta`, `resolveTargetCoordinates` | Pure calculations (stateless) | Pure Policy |
| **5. Predictive Keyboard Scrolling** | `UnifiedCanvasLayout.predictivelyGlideCursorAboveKeyboard` | `UnifiedCanvasLayout` | State Coordination & Animation |
| **6. Keyboard Viewport Transactions** | `ScribeScrollPolicy.KeyboardTransaction`, `UnifiedCanvasLayout` | `UnifiedCanvasLayout` (`activeKeyboardTransaction`) | Policy Models + State Coordination |
| **7. Deletion and Last-Line Anchoring** | **`ScribeScrollDeletionPolicy.kt`** | `UnifiedCanvasLayout` (`activeDeletionSnapshot`) | Pure Policy (Extracted) |
| **8. Contraction Restoration** | **`ScribeScrollRestorationPolicy.kt`** | `scrollCoordinator` (`AuthoritativeScrollCoordinator`) | Pure Policy (Extracted) |
| **9. Manual Drag and Fling** | `UnifiedCanvasLayout.kt` (touch handlers, `OverScroller`) | `UnifiedCanvasLayout` (`scroller`, `velocityTracker`) | Input & Touch Integration |
| **10. Sora Editor Scrolling Integration** | `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt` | Sora `CodeEditor` (`offsetY`, `scroller`) | UI Integration (Custom View) |
| **11. Automatic Scroll Animation** | `ScribeScrollPolicy` (`AuthoritativeScrollCoordinator`, `ProductionScrollAnimationCoordinator`) | `scrollCoordinator`, `UnifiedCanvasLayout.activeCanvasAnimator` | Animation & Coordinator *(Phase 4/5)* |
| **12. Viewport Measurement & Layout Reconciliation** | `UnifiedCanvasLayout.onMeasure/onLayout`, `ScribeScrollPolicy.reconcileScrollState` | `UnifiedCanvasLayout` | Pure Policy + View Layout |
| **13. Keyboard/IME State & Inset Integration** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (Compose WindowInsets state) | UI Integration & Helper Policies |
| **14. Standard Keyboard Height Persistence** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt`, `ScribeDataStore` | `MainEditorScreen.lastKeyboardHeightDp`, `ScribeDataStore` | Policy + Persistence *(Phase 3)* |
| **15. Shortcut-Bar Layout Reservation** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.kt` | `MainEditorScreen`, `UnifiedCanvasLayout.estimatedKeyboardHeightPx` | UI Layout Integration |
| **16. Accessory Drawer State, Sizing, Dragging & Transitions** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (local `remember` state) | UI State & Gesture *(Phase 3)* |
| **17. Drawer-to-Keyboard Handoff** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (`isAwaitingKeyboardOpen`, `isBarTransitionLatched`) | State Coordination *(Phase 3)* |
| **18. Saved Viewport/Session Restoration** | `EditorSessionRestorationCoordinator.kt`, `UnifiedCanvasLayout.kt` | `EditorSessionRestorationCoordinator`, `EditorViewModel` | Navigation & Lifecycle Integration |
| **19. Scroll Diagnostics** | `ScribeScrollRestorationPolicy.kt`, `ScribeScrollPolicy.kt` | Instance and callback listeners | Diagnostics & Test Support |

---

## 3. Deep-Dive Inspection of Extracted Components

### A. `ScribeScrollGeometry.kt`
- **Location:** `com.primaloptima.scribe.ui.components`
- **Extracted Content:**
  - `SelectionGeometry`: Clean document coordinate representation of caret/selection bounds (vertical `topDocY`/`bottomDocY`, horizontal `leftDocX`/`rightDocX`, selection endpoints `anchorLine`/`focusLine`).
  - `SoraCharGeometry`: Strong typing for single character bounds extracted from Sora `Layout.getCharLayoutOffset`. Replaces private inner class in `UnifiedCanvasLayout`.
  - `SafeTargetValidationResult`: Result model for boundary clearance checks (`isSafe`, `finalCaretScreenBottom`, `targetLineBottom`, `correctiveDeltaY`).
  - `CursorTargetResolution`: Resolved target coordinate pair and scroll necessity flag.
- **Design Rule:** 100% free of Android View or Sora editor framework dependencies. Pure Kotlin data structures.

### B. `ScribeScrollDeletionPolicy.kt`
- **Location:** `com.primaloptima.scribe.ui.components`
- **Extracted Content:**
  - `DURATION_DELETION_ANCHOR_MS = 140L`
  - `DeletionSnapshot`: Complete pre-edit snapshot capturing transaction epoch, selection range, line count, document height, scroll offset, caret screen Y, and tail proximity.
  - `DeletionAnchorIntent`: Enum with `TAIL_ANCHOR`, `CARET_ANCHOR`, and `RESET_TO_TOP`.
  - `classifyDeletionIntent`: Strict classification logic ensuring tail anchoring occurs only when both pre-edit viewport was near the tail and the deletion affected document end.
  - `calculateSafeDeletionDestination`: Exact calculation satisfying monotonic non-growth, hard 24dp breathing margin, and caret screen position invariance.
  - `calculateDeletionAnchorDelta`: Bounds-respecting negative delta calculation when deleting lines or shrinking text.
- **Formulas & Thresholds Preserved:** Exact 1:1 match with commit `d0d39b5`.

### C. `ScribeScrollRestorationPolicy.kt`
- **Location:** `com.primaloptima.scribe.ui.components`
- **Extracted Content:**
  - `ContractionRestorationRequest`: Parameters model for contraction downward adjustments.
  - `ContractionRestorationResult`: Output model (`deltaY`, `shouldScroll`, `remainingCompensation`, `reason`).
  - `resolveContractionRestorationDelta`: Pure calculation enforcing the Pure Writer invariant (zero downward delta on keyboard dismiss), safe breathing margin clearance, and transition-aware compensation restoration.
  - `validateFinalTarget`: Validates candidate scroll targets against the 24dp breathing margin, computing upward corrective deltas when violated.
  - `calculateLastLineFollowDelta`: Helper calculation for downward canvas adjustment when expanding usable viewport.
  - Diagnostics callback support (`isDebugDiagnosticsEnabled`, `onScrollDiagnosticLog`, `logDiagnostic`).

---

## 4. Caller Migration Summary

All production and test callers have been migrated to the new files:

1. **`UnifiedCanvasLayout.kt`:**
   - Deletion snapshot state now typed as `ScribeScrollDeletionPolicy.DeletionSnapshot?`.
   - Calls to `classifyDeletionIntent`, `calculateSafeDeletionDestination`, `calculateDeletionAnchorDelta`, and `DURATION_DELETION_ANCHOR_MS` point to `ScribeScrollDeletionPolicy`.
   - Calls to `resolveContractionRestorationDelta`, `validateFinalTarget`, and `ContractionRestorationRequest` point to `ScribeScrollRestorationPolicy`.
   - Removed local inner `SoraCharGeometry` and updated `getSelectionGeometry()` return type to `SelectionGeometry?`.

2. **`CursorAndCanvasGeometryTest.kt`:**
   - Deletion unit tests migrated to `ScribeScrollDeletionPolicy`.
   - Contraction restoration unit tests migrated to `ScribeScrollRestorationPolicy`.
   - Selection geometry tests migrated to `SelectionGeometry`.
   - Diagnostics test updated to use `ScribeScrollRestorationPolicy` diagnostics flags.

3. **`ScribeScrollPolicy.kt` Compatibility Layer:**
   - Retains thin forwarding methods and typealiases (`typealias DeletionSnapshot = ...`, `typealias SelectionGeometry = ...`, etc.) so internal coordinator classes (`AuthoritativeScrollCoordinator`, `ContractionSimulationController`) continue operating without duplication.

---

## 5. Remaining Work Plan (Phases 3–9)

- [x] **Phase 1: Inventory & Architecture Mapping** *(Completed)*
- [x] **Phase 2: Separate Scroll Policies and Geometry** *(Completed)*
- [ ] **Phase 3: Bottom Surface State Machine Extraction**
  - Extract `ScribeBottomSurfaceState.kt` to house `AccessoryDrawerMode`, `AccessoryDrawerState`, drawer heights, dragging state, and keyboard handoff latches (`isAwaitingKeyboardOpen`, `isBarTransitionLatched`).
  - Move stateful drawer/keyboard coordination out of `MainEditorScreen.kt`.
  - Align `ScribeBottomSurfaceCoordinator.kt` with the new state model.
- [ ] **Phase 4: Authoritative Scroll Coordinator Decoupling**
  - Extract `ScribeScrollCoordinator.kt` containing `AuthoritativeScrollCoordinator`.
  - Migrate causal compensation bookkeeping (`temporaryDisplacement`) and operation IDs out of `ScribeScrollPolicy.kt`.
  - Remove global mutable diagnostics flags in favor of instance-scoped listeners.
- [ ] **Phase 5: Scroll Animation & Driver Unification**
  - Extract `ScribeScrollAnimation.kt`.
  - Connect `UnifiedCanvasLayout`'s inline `ValueAnimator` to `ProductionScrollAnimationCoordinator` and `AnimationDriver`.
  - Relocate `ContractionSimulationController` to test harness.
- [ ] **Phase 6: Touch, Gesture & Momentum Input Controller Extraction**
  - Create `ScribeScrollInputController.kt`.
  - Move touch interception (`onInterceptTouchEvent`), touch dispatch (`onTouchEvent`), dragging state, fling handling (`OverScroller`), and edge-effect overscroll out of `UnifiedCanvasLayout.kt`.
- [ ] **Phase 7: UnifiedCanvasLayout Reassembly & State Consolidation**
  - Streamline `UnifiedCanvasLayout.kt` as an edge-to-edge ViewGroup orchestrating the decoupled input, coordinator, and animation components.
  - Consolidate redundant animation flags and eliminate float-to-int sub-pixel truncation drift.
- [ ] **Phase 8: Editor & Navigation Integration Clean-Up**
  - Decouple `ScribeCodeEditor.kt` vertical scroll delegation.
  - Update `EditorSessionRestorationCoordinator.kt` and `MainEditorScreen.kt` bindings.
- [ ] **Phase 9: Comprehensive Test Suite Alignment & Regression Hardening**
  - Verify and run full test suites (`CursorAndCanvasGeometryTest.kt`, `EditorViewportRestorationTest.kt`).
  - Verify zero visual or behavioral regressions against baseline `d0d39b5`.

---
*Report End — Phase 2 Execution Complete.*
