# Scribe Native Scroll Architecture Refactor Map

**Commit Reference:** `d0d39b5` ("fix(viewport): eliminate unnecessary scroll-and-return and coordinate fluid shortcut bar entry")  
**Current Milestone:** Phase 5 Complete — Extract Scroll Input and Fling Handling  
**Status:** Input, drag, fling, and momentum coordination extracted into `ScribeScrollInputController.kt`. **Zero behavioral changes, zero formula modifications, no builds/tests run.**

---

## 1. Executive Summary & Extraction Status

Following the Phase 1 inventory, Phase 2 policy/geometry separation, and Phase 3 coordinator extraction, **Phase 4** has separated the scroll animation implementations from `ScribeScrollPolicy.kt` and `UnifiedCanvasLayout.kt` into: **`ScribeScrollAnimation.kt`**.

As discovered during Phase 1 inspection, the baseline contained two distinct animation systems:
1. **Testable Animation Coordinator (`AnimationDriver` & `ProductionScrollAnimationCoordinator`):**
   - Originally in `ScribeScrollPolicy.kt`.
   - Used by unit tests (`CursorAndCanvasGeometryTest.kt`) with `TestAnimationDriver`.
   - Coordinates `AuthoritativeScrollCoordinator` with a driver factory, providing monotonic operation ID tracking and race-free retargeting.
2. **Active Canvas Animation & Spring Glide (`ScribeCanvasGlideAnimator`):**
   - Originally inline in `UnifiedCanvasLayout.kt` (`isGlideRunning`, `glidePosition`, `glideVelocity`, `glideTarget`, `glideFrameRunnable`, `activeCanvasAnimator`).
   - Drives active document scrolling using a critically damped second-order spring ($\omega = 13.5\text{ rad/s}, \zeta = 1.0$) scheduled on `postOnAnimation(glideFrameRunnable)`.
   - Integrates with `ScribeScrollCoordinator` via `applyStep`, `finishAnimation`, and `retargetActiveOperation`.

Per Phase 4 mission requirements, **both mechanisms have been extracted without conflation or merging**:
- `AnimationDriver` and `ProductionScrollAnimationCoordinator` have been moved to `ScribeScrollAnimation.kt`.
- The cohesive canvas animation physics, frame scheduling, retargeting, and settling handling have been moved into `ScribeCanvasGlideAnimator` in `ScribeScrollAnimation.kt`.
- `UnifiedCanvasLayout.kt` delegates animation state and lifecycle to `canvasAnimator: ScribeCanvasGlideAnimator`, while keeping View-boundary layout measurement and drawing at the ViewGroup level.
- All animation constants, durations, spring formulas, monotonic operation IDs, and cancellation/retargeting semantics are 100% preserved.

### Current Extraction Progress Matrix

| Module / Component | Target Destination File | Status | Notes |
| :--- | :--- | :--- | :--- |
| Pure Geometry & Target Models | `ScribeScrollGeometry.kt` | **Extracted (Phase 2)** | `SelectionGeometry`, `SoraCharGeometry`, `SafeTargetValidationResult`, `CursorTargetResolution` |
| Deletion Anchoring & Tail Calculations | `ScribeScrollDeletionPolicy.kt` | **Extracted (Phase 2)** | `DeletionSnapshot`, `classifyDeletionIntent`, `calculateSafeDeletionDestination`, `calculateDeletionAnchorDelta` |
| Contraction Restoration Calculations | `ScribeScrollRestorationPolicy.kt` | **Extracted (Phase 2)** | `ContractionRestorationRequest/Result`, `resolveContractionRestorationDelta`, `validateFinalTarget`, `calculateLastLineFollowDelta` |
| Bottom Surface Coordinates & Insets | `ScribeBottomSurfaceCoordinator.kt` | **Preserved (Phase 2)** | Stateless bottom-surface calculations; imports aligned |
| General Viewport & Cursor Policy | `ScribeScrollPolicy.kt` | **Refactored (Phase 2-4)** | Central policy retaining stateless viewport thresholds, caret visibility formulas, and safe bounds |
| Authoritative Scroll Coordination State | `ScribeScrollCoordinator.kt` | **Extracted (Phase 3)** | `ScribeScrollCoordinator`, `ScrollAnimationReason`, monotonic `activeOperationId`, causal compensation |
| Scroll Diagnostics Helper | `ScribeScrollDiagnostics.kt` | **Extracted (Phase 3)** | `ScribeScrollDiagnostics` (`isDebugDiagnosticsEnabled`, `logDiagnostic`, `onScrollDiagnosticLog`) |
| Scroll Animation Driver & Canvas Spring | `ScribeScrollAnimation.kt` | **Extracted (Phase 4)** | `AnimationDriver`, `ProductionScrollAnimationCoordinator`, `FastOutSlowInInterpolator`, `ScribeCanvasGlideAnimator` |
| Scroll Input & Fling Handling | `ScribeScrollInputController.kt` | **Extracted (Phase 5)** | `ScribeScrollInputController` (`OverScroller`, `VelocityTracker`, touch-slop arbitration), `ScribeEditorScrollInputHelper` |

---

## 2. Scroll Responsibilities Inventory & Current Status

| Responsibility | Baseline Location | Refactored Component | Classification & Notes |
| :--- | :--- | :--- | :--- |
| **1. Unified Canvas/Editor Scrolling & Delta Allocation** | `UnifiedCanvasLayout.scrollByTotal` | `UnifiedCanvasLayout.kt` (delegates to `ScribeScrollCoordinator`) | UI Integration & State Coordination |
| **2. Document Scroll Position & Header Displacement** | `UnifiedCanvasLayout`, `ScribeScrollPolicy.reconcileScrollState` | `UnifiedCanvasLayout`, `ScribeScrollPolicy` | Pure Policy + View Layout |
| **3. Caret & Selection Geometry** | `UnifiedCanvasLayout.getCharGeometry` | `ScribeScrollGeometry.kt` | Pure Geometry |
| **4. Caret Visibility Calculations** | `ScribeScrollPolicy.resolveCursorScrollDelta`, `resolveTargetCoordinates` | `ScribeScrollPolicy.kt` | Pure Policy |
| **5. Predictive Keyboard Scrolling** | `UnifiedCanvasLayout.predictivelyGlideCursorAboveKeyboard` | `UnifiedCanvasLayout` (via `ScribeCanvasGlideAnimator`) | State Coordination & Animation |
| **6. Keyboard Viewport Transactions** | `ScribeScrollPolicy.KeyboardTransaction`, `UnifiedCanvasLayout` | `UnifiedCanvasLayout` (`activeKeyboardTransaction`) | Policy Models + State Coordination |
| **7. Deletion and Last-Line Anchoring** | `ScribeScrollDeletionPolicy.kt` | `ScribeScrollDeletionPolicy.kt` | Pure Policy |
| **8. Contraction Restoration** | `ScribeScrollRestorationPolicy.kt` | `ScribeScrollRestorationPolicy.kt`, `ScribeScrollCoordinator` | Pure Policy + State Coordination |
| **9. Manual Drag and Fling** | `UnifiedCanvasLayout.kt` (touch handlers, `OverScroller`) | `ScribeScrollInputController.kt` (delegated from `UnifiedCanvasLayout`) | Input & Touch Integration |
| **10. Sora Editor Scrolling Integration** | `ScribeCodeEditor.kt`, `UnifiedCanvasLayout.kt` | Sora `CodeEditor` (`offsetY`, `scroller`) | UI Integration (Custom View) |
| **11. Automatic Scroll Animation** | `ScribeScrollPolicy.kt`, `UnifiedCanvasLayout.kt` | **`ScribeScrollAnimation.kt`** (`ScribeCanvasGlideAnimator`, `ProductionScrollAnimationCoordinator`) | Animation & Physics Engine |
| **12. Viewport Measurement & Layout Reconciliation** | `UnifiedCanvasLayout.onMeasure/onLayout`, `ScribeScrollPolicy.reconcileScrollState` | `UnifiedCanvasLayout`, `ScribeScrollPolicy` | Pure Policy + View Layout |
| **13. Keyboard/IME State & Inset Integration** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (Compose WindowInsets state) | UI Integration & Helper Policies |
| **14. Standard Keyboard Height Persistence** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt`, `ScribeDataStore` | `MainEditorScreen.lastKeyboardHeightDp`, `ScribeDataStore` | Policy + Persistence |
| **15. Shortcut-Bar Layout Reservation** | `MainEditorScreen.kt`, `UnifiedCanvasLayout.kt` | `MainEditorScreen`, `UnifiedCanvasLayout.estimatedKeyboardHeightPx` | UI Layout Integration |
| **16. Accessory Drawer State, Sizing, Dragging & Transitions** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (local `remember` state) | UI State & Gesture |
| **17. Drawer-to-Keyboard Handoff** | `MainEditorScreen.kt`, `ScribeBottomSurfaceCoordinator.kt` | `MainEditorScreen` (`isAwaitingKeyboardOpen`, `isBarTransitionLatched`) | State Coordination |
| **18. Saved Viewport/Session Restoration** | `EditorSessionRestorationCoordinator.kt`, `UnifiedCanvasLayout.kt` | `EditorSessionRestorationCoordinator`, `EditorViewModel` | Navigation & Lifecycle Integration |
| **19. Scroll Diagnostics** | `ScribeScrollDiagnostics.kt` | `ScribeScrollDiagnostics` (`isDebugDiagnosticsEnabled`, `onScrollDiagnosticLog`) | Diagnostics Helper |

---

## 3. Deep-Dive Inspection of `ScribeScrollAnimation.kt`

The newly extracted `ScribeScrollAnimation.kt` contains two cleanly isolated animation facilities:

### A. Testable Driver Abstractions
- **`interface AnimationDriver`**:
  Abstracts frame-based animation drivers (`ValueAnimator`, test drivers).
  Signatures: `start(from, to, durationMs, onUpdate, onCancel, onEnd)`, `cancel()`, `isRunning`.
- **`class ProductionScrollAnimationCoordinator`**:
  Coordinating controller bridging `AuthoritativeScrollCoordinator` with an `AnimationDriver`.
  Guarantees race-free monotonic `activeOperationId` tracking, automatic duration scaling based on distance, step forwarding, cancellation sequencing, and in-flight retargeting without stale callback interference.

### B. Production Canvas Spring Glide Animator (`ScribeCanvasGlideAnimator`)
- **Physics Engine**:
  - Critically damped second-order spring: $\omega = 13.5\text{ rad/s}, \zeta = 1.0$.
  - $dt$ calculation clamped to $[0.001\text{s}, 0.032\text{s}]$.
  - $accel = -\omega^2 \cdot displacement - 2\omega \cdot velocity$.
  - Hard boundary invariant: deletion anchor glide is strictly clamped above the $24\text{dp}$ breathing line ($minSafeScroll$) without overshoot.
- **State Encapsulation**:
  - `activeCanvasAnimator: ValueAnimator?`
  - `isGlideRunning: Boolean`
  - `glidePosition: Float`, `glideVelocity: Float`, `glideTarget: Float`
  - `activeGlideReason: ScrollAnimationReason?`
  - `activeGlideOpId: Long`
- **Execution & Lifecycle Methods**:
  - `startAuthoritativeAnimation(...)`: Retargets in-flight glides continuously or requests a fresh target from `ScribeScrollCoordinator`, cancels previous frames/animators, clamps target, schedules `glideFrameRunnable` via `postOnAnimation`.
  - `cancel(reason)`: Unregisters `glideFrameRunnable`, resets running state, cancels animator, and informs `scrollCoordinator.cancelAnimation`.
  - `onSettledCallback`: Dispatches post-animation events (`CONTRACTION_RESTORATION` safety revalidation, `SAFE_BOUNDARY_CORRECTION` rebase, `PREDICTIVE_GLIDE` flag cleanup).

---

## 4. Caller Migration Summary

1. **`UnifiedCanvasLayout.kt`:**
   - Replaced inline mutable animation fields (`isGlideRunning`, `glidePosition`, `glideVelocity`, `glideTarget`, `glideLastTimeNs`, `activeGlideReason`, `activeGlideOnEnd`, `activeGlideOpId`, `activeCanvasAnimator`, `glideFrameRunnable`) with a single `canvasAnimator: ScribeCanvasGlideAnimator` instance.
   - Kept delegating public/internal getters (`isGlideRunning`, `glideTarget`, `glidePosition`, `glideVelocity`, `activeGlideReason`) ensuring existing callers continue to resolve smoothly.
   - Simplified `cancelCanvasAnimation()` and `cancelRestoration()` to call `canvasAnimator.cancel(...)`.
   - `startAuthoritativeScrollAnimation()` delegates to `canvasAnimator.startAuthoritativeAnimation(...)`.
   - Viewport measurement and drawing logic remains exclusively at the View boundary.

2. **`ScribeScrollPolicy.kt`:**
   - Kept pure and stateless: removed redundant package-level typealiases to eliminate Kotlin redeclaration conflicts.
   - Delegates scoped diagnostics directly to `ScribeScrollDiagnostics`.
   - Delegates geometry and restoration models cleanly to their dedicated policy/geometry files.

3. **`CursorAndCanvasGeometryTest.kt`:**
   - Updated test callers to resolve `AnimationDriver` and `ProductionScrollAnimationCoordinator` directly from `ScribeScrollAnimation.kt` without intermediate shims.

---

## 5. Scroll Subsystem Dedicated Package Relocation

All 8 extracted scroll subsystem files have been consolidated into their dedicated architectural domain:
- **Package:** `com.primaloptima.scribe.ui.editor.scroll`
- **Location:** `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/`

### Relocated Components (8 files):
1. `ScribeScrollPolicy.kt`: Stateless core rules, bounds, and viewport calculations.
2. `ScribeScrollGeometry.kt`: Reusable caret, selection, and character geometry models.
3. `ScribeScrollDeletionPolicy.kt`: Pure deletion anchor classification and destination mathematics.
4. `ScribeScrollRestorationPolicy.kt`: Contraction restoration compensation calculations.
5. `ScribeScrollCoordinator.kt`: Authoritative mutable scroll state coordination engine.
6. `ScribeScrollAnimation.kt`: Spring glide physics engine and testable animation driver bridges.
7. `ScribeScrollDiagnostics.kt`: Scoped scroll diagnostic logger.
8. `ScribeBottomSurfaceCoordinator.kt`: Stateless bottom-surface and keyboard inset calculations.

### Caller Import Updates:
- `UnifiedCanvasLayout.kt`: Added `import com.primaloptima.scribe.ui.editor.scroll.*`
- `ScribeCodeEditor.kt`: Added `import com.primaloptima.scribe.ui.editor.scroll.*`
- `MainEditorScreen.kt`: Updated imports and inline FQCNs to `com.primaloptima.scribe.ui.editor.scroll.*`
- `EditorSessionRestorationCoordinator.kt`: Updated import to `com.primaloptima.scribe.ui.editor.scroll.ScribeScrollPolicy`
- `CursorAndCanvasGeometryTest.kt`: Added `import com.primaloptima.scribe.ui.editor.scroll.*`
- `EditorViewportRestorationTest.kt`: Updated import to `com.primaloptima.scribe.ui.editor.scroll.ScribeScrollPolicy`

Zero legacy forwarding shims or duplicate typealiases remain in `com.primaloptima.scribe.ui.components`.
Integration containers (`UnifiedCanvasLayout`, `ScribeCodeEditor`, `MainEditorScreen`, `EditorSessionRestorationCoordinator`) remain in their original packages, cleanly consuming the scroll subsystem.

---

## 6. Phase 5 — Extract Scroll Input and Fling Handling

The document scrolling input, gesture arbitration, and fling handling have been extracted from `UnifiedCanvasLayout.kt` and `ScribeCodeEditor.kt` into:
- **`ScribeScrollInputController.kt`** (`com.primaloptima.scribe.ui.editor.scroll`)

### A. UnifiedCanvasLayout Delegation
1. **Encapsulated State**:
   - `OverScroller` instance and `lastScrollerY`.
   - `VelocityTracker` lifecycle (obtain, movement tracking, velocity computation, and recycling).
   - Touch coordinates and states (`initialDownX/Y`, `lastTouchX/Y`, `isDraggingCanvas`, `isTouchOnHandleActive`, `isMultiTouchLatched`).
   - Touch slop and fling velocities (`touchSlop`, `minFlingVelocity`, `maxFlingVelocity`).
2. **Gesture and Event Arbitration**:
   - `dispatchTouchEvent(ev)`: Tracks velocity, detects handle drags, absorbs movement during active canvas drag, and triggers flings on finger lift.
   - `onInterceptTouchEvent(ev)`: Implements the central 3-tier interception invariant (header visibility, top-of-editor downward pull, short document bounds) without disturbing handle or multi-touch gestures.
   - `onTouchEvent(ev)`: Consumes intercepted drags and drives `scrollCanvasBy(dy)`.
   - `computeScroll()`: Drives momentum scrolling and absorbs overscroll into `topEdgeEffect` on API 31+.
   - `continueFlingFromEditor(velocity)`: Seamlessly accepts downward flings when editor reaches offset Y <= 0.
   - `settleOrCancelFling()` / `stopFling()` / `abortAnimation()`: Halts active fling momentum.

### B. ScribeCodeEditor Helpers (`ScribeEditorScrollInputHelper`)
1. **Handle Auto-Scroll Calculation**:
   - Extracted `calculateHandleAutoScrollVelocity(touchY, viewHeight, headerOffset, density, canScrollUp)` calculating continuous auto-scroll when selection handles are dragged into top/bottom hot zones.
2. **Editor-to-Canvas Fling Handoff**:
   - Extracted `shouldHandoffFlingToCanvas(wasFinished, currY, prevY, velocity, isFlingActive, isUserTouching)` isolating the condition for transferring momentum from Sora Editor to canvas header upon reaching document top.
3. **Preserved Editing and Sora Hooks**:
   - All text selection, typing, formatting, paste, zoom, image clicks, and Sora View overrides remain intact in `ScribeCodeEditor`.

---

## 7. Next Steps
- Proceed with Phase 6 as directed.
