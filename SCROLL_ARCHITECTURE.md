# Scribe Native Scroll Architecture Documentation

## 1. Overview & Architectural Boundaries

Scribe Native uses a hybrid Jetpack Compose + Android View/Sora Editor architecture. The scroll subsystem provides a seamless, unified vertical document canvas that integrates:
1. An edge-to-edge Compose header (title, tags, metadata).
2. A customized Sora `CodeEditor` rendering formatted Markdown text.
3. Fluid soft-keyboard (IME) insets and persistent height estimations.
4. An expandable, draggable multi-state accessory drawer (Snippets, Templates).
5. Monotonic single-owner scroll animation and compensation tracking.
6. A rigid 1-Degree-of-Freedom (1-DOF) scroll invariant between header and text.

The subsystem is organized under the architectural package:
`com.primaloptima.scribe.ui.editor.scroll`

---

## 2. File & Package Structure

### Production Source Tree

| File | Package | Primary Role |
| :--- | :--- | :--- |
| **`ScribeScrollGeometry.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Pure data classes for character and selection handle geometry (`SoraCharGeometry`, `SelectionHandleGeometry`) and geometric extraction utilities. |
| **`ScribeScrollPolicy.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Pure mathematical policies: 1-DOF delta allocation, caret visibility breathing margins & deadbands, viewport centering, predictive keyboard glide heuristics, scroll permission arbitration, and transaction models (`KeyboardTransaction`, `ZoomTransaction`, `PasteTransaction`). |
| **`ScribeScrollDeletionPolicy.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Pure mathematical rules governing text deletion scrolling: `DeletionSnapshot` capture, intent classification (`classifyDeletionIntent`), safe destination calculation, and tail anchoring. |
| **`ScribeScrollRestorationPolicy.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Pure policy for viewport contraction restoration: enforces the Pure Writer stationary invariant, calculates upward corrective deltas when caret is occluded, and validates safe targets. Note: `resolveContractionRestorationDelta()` internally calls `ScribeScrollDiagnostics.log()` when diagnostics are enabled; this existing diagnostic side effect is intentionally preserved. |
| **`ScribeScrollCoordinator.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Authoritative single-owner scroll state coordinator: owns monotonic `activeOperationId`, target updates, in-flight retargeting, and causal expansion compensation tracking (`temporaryDisplacement`). |
| **`ScribeScrollAnimation.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Production animation execution: houses `FastOutSlowInInterpolator` and `ScribeCanvasGlideAnimator` (critically damped second-order spring glide engine, $\omega=13.5	ext{ rad/s}, \zeta=1.0$). |
| **`ScribeScrollInputController.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Input and gesture execution: encapsulates `OverScroller`, `VelocityTracker`, touch slop arbitration, drag gestures, and momentum flings via the `Host` interface. |
| **`ScribeBottomSurfaceCoordinator.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Stateless pure policy for keyboard and drawer sizing: standard keyboard bounds validation, expanded/full drawer height clamping, and downward follow permissions. |
| **`ScribeBottomSurfaceState.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Jetpack Compose state container (`rememberScribeBottomSurfaceState`): manages active drawer state, live dragging, height animations, IME observation, debounced DataStore baseline persistence, and exposes `estimatedBottomSurfaceHeightDp`. |
| **`ScribeScrollDiagnostics.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Internal scoped diagnostic logger guarded by `isDebugDiagnosticsEnabled`. |

### UI & Host Adapters

| File | Package | Primary Role |
| :--- | :--- | :--- |
| **`UnifiedCanvasLayout.kt`** | `com.primaloptima.scribe.ui.components` | Custom Android `FrameLayout` hosting Child 0 (`ComposeView` header) and Child 1 (`ScribeCodeEditor`). Serves as the Android integration and orchestration boundary: owns View-side scroll state (`scrollD`), direct immediate correction paths, measurement (`onMeasure`), layout (`onLayout`), translations (`applyTranslations`), and touch dispatching. |
| **`ScribeCodeEditor.kt`** | `com.primaloptima.scribe.ui.components` | Sora `CodeEditor` subclass. Delegates vertical scroll permissions and canvas forward scroll arbitration to `ScribeScrollPolicy`. |
| **`MainEditorScreen.kt`** | `com.primaloptima.scribe.ui.screens` | Top-level Compose editor screen. Owns shortcut bar, accessory drawer UI, and binds `bottomSurfaceState` estimated height to `UnifiedCanvasLayout` via `AndroidView`. |

### Navigation Integration

| File | Package | Primary Role |
| :--- | :--- | :--- |
| **`EditorSessionRestorationCoordinator.kt`** | `com.primaloptima.scribe.navigation` | Coordinates viewport and cursor save/restore across Navigation 3 backstack transitions, sheet overlays, and process recreation. |

### Test Source Tree

| File | Package | Primary Role |
| :--- | :--- | :--- |
| **`ScribeScrollTestSupport.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Test support harnesses: `ContractionSimulationController`, `AnimationDriver`, `TestAnimationDriver`, and `ProductionScrollAnimationCoordinator`. |
| **`CursorAndCanvasGeometryTest.kt`** | `com.primaloptima.scribe.ui.components` | Tests for character/selection geometry and 1-DOF allocation invariants (32 tests). |
| **`ScribeScrollPolicyTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for breathing margins, deadband, centering, predictive glide, transactions, and tap heuristics (31 tests). |
| **`ScribeScrollDeletionPolicyTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for deletion intent classification, tail follow, paragraph deletions, and boundary clamping (16 tests). |
| **`ScribeScrollRestorationPolicyTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for contraction restoration delta, safe target validation, and simulated multi-step contraction cycles (24 tests). |
| **`ScribeScrollCoordinatorTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for authoritative state ownership, in-flight retargeting, monotonic operation IDs, and compensation invalidation (8 tests). |
| **`ScribeScrollAnimationTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for animation coordinator steps, driver cancellations, in-flight retargeting, and boundary clamps (9 tests). Exercises the test-only `ProductionScrollAnimationCoordinator` harness; does not directly test the production `ScribeCanvasGlideAnimator`. |
| **`ScribeBottomSurfacePolicyTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for stateless bottom surface policy: keyboard baseline bounds, dismiss states, drawer collapses, and settling predicates (13 tests). |
| **`ScribeBottomSurfaceStateTest.kt`** | `com.primaloptima.scribe.ui.editor.scroll` | Tests for Compose state holder: drawer animations, drag tracking, settled detection, and `estimatedBottomSurfaceHeightDp` (17 tests). |
| **`EditorViewportRestorationTest.kt`** | `com.primaloptima.scribe.navigation` | Tests for Navigation 3 lifecycle and session viewport restoration across screen pops and sheet dismissals. |

---

## 3. Mutable State Ownership & Boundaries

```
[MainEditorScreen (Compose)]
       │
       ├── owns: ScribeBottomSurfaceState (active drawer, dragging, live height, IME baseline)
       │
       └── renders: AndroidView -> [UnifiedCanvasLayout (Android View)]
                                           │
                                           ├── owns: scrollD (header translation)
                                           ├── owns: activeKeyboardTransaction, activeDeletionSnapshot
                                           ├── holds: ScribeScrollInputController (touch slop, velocity, flings)
                                           ├── holds: ScribeCanvasGlideAnimator (spring physics, frame callback)
                                           │
                                           ├── holds: ScribeScrollCoordinator (authoritative state)
                                           │            ├── totalScroll = scrollD + editor.offsetY
                                           │            ├── activeOperationId (monotonic Long)
                                           │            ├── activeTargetTotalScroll
                                           │            └── temporaryDisplacement (causal compensation)
                                           │
                                           └── hosts: [ScribeCodeEditor (Sora CodeEditor)]
                                                        └── owns: editor.offsetY (text scroll offset)
```

1. **Header Displacement (`scrollD`):** Owned by `UnifiedCanvasLayout`. Represents the physical pixel translation of the Compose header View (`[0, headerHeight]`).
2. **Editor Scroll Offset (`offsetY`):** Owned by Sora's `CodeEditor`. Represents vertical document scrolling inside the editor once the header has fully scrolled off.
3. **Total Unified Scroll (`totalScroll`):** Maintained on `ScribeScrollCoordinator` as `scrollD + editor.offsetY`.
4. **Authoritative Operation ID (`activeOperationId`):** Monotonically increasing `Long` on `ScribeScrollCoordinator`. Guarantees that stale animation frames, late callbacks, or cancelled drivers cannot apply movement after a retarget or user touch.
5. **Causal Compensation (`temporaryDisplacement`):** Owned by `ScribeScrollCoordinator`. Tracks scroll displacement caused by keyboard/drawer expansion to enable stationary Pure Writer restoration on contraction.
6. **Live Bottom Surface State:** Owned by `ScribeBottomSurfaceState` inside Compose runtime.

---

## 4. Subsystem Interactions & Lifecycles

### A. Screen to Bottom Surface State Connection
- In `MainEditorScreen.kt`, `bottomSurfaceState = rememberScribeBottomSurfaceState(initialKeyboardHeightDp)`.
- It observes `WindowInsets.ime` and `WindowInsets.navigationBars`.
- When the keyboard settles (350ms debounce), it persists the calibrated standard baseline to `ScribeDataStore`.
- The screen supplies `bottomSurfaceState.estimatedBottomSurfaceHeightDp.value` to `calculateEstimatedKeyboardHeightPx` in `AndroidView.factory` and `AndroidView.update`.
- This ensures that even when the keyboard and drawer are closed, `persistentBottomSpacePx` retains the calibrated compact keyboard reservation (e.g. 280dp), preventing layout collapsing and invalid restoration calculations.
- Live drag gestures on the drawer update `bottomSurfaceState.onDrawerDragDelta` and `onDrawerDragEndWithVelocity`.
- Tapping outside the drawer invokes `bottomSurfaceState.closeDrawerAndRestoreKeyboard`.

### B. Canvas to Scroll Coordination Connection
- `UnifiedCanvasLayout` delegates authoritative glide animations and continuous retargeting to `ScribeScrollCoordinator` and `ScribeCanvasGlideAnimator`. Certain immediate cursor visibility corrections and direct boundary clamping continue to be executed directly by `UnifiedCanvasLayout`.
- Before starting an animation, `UnifiedCanvasLayout` calls `scrollCoordinator.requestTarget(target, reason, forceRetarget, maxCanvasScroll)`.
- If accepted, `ScribeScrollCoordinator` increments `activeOperationId` and sets `activeTargetTotalScroll`.
- `ScribeCanvasGlideAnimator` executes the spring differential equation on each `postOnAnimation` tick, invoking `scrollCoordinator.applyStep(position, opId)`.
- `scrollCoordinator.applyStep` splits the delta into canvas `scrollD` and editor `offsetY` via `ScribeScrollPolicy.allocateScrollDelta`, calling `onScrollBy`.
- When user touches down (`ACTION_DOWN`), `Host.onUserTouchDown` invokes `scrollCoordinator.invalidateCompensation("User touch down")` and cancels active animations.

### C. Sora Editor Integration
- `ScribeCodeEditor` overrides `canScrollVertically(direction)`:
  - Calls `ScribeScrollPolicy.canScrollVertically(direction, scrollD, offsetY, maxCanvasScroll)`.
  - For positive directions (downward scroll into document), verifies `(scrollD + offsetY) < maxCanvasScroll`.
  - For negative directions (upward scroll toward header), verifies `scrollD > 0 || offsetY > 0`.
  - For neutral `direction == 0`, returns `null`, prompting `ScribeCodeEditor` to fall back to `super.canScrollVertically(direction)`.
- On touch events, `ScribeCodeEditor` evaluates `ScribeScrollPolicy.shouldForwardScrollToCanvas(scrollD, headerHeight, offsetY, deltaY)`:
  - If the header is visible (`scrollD < headerHeight`), forward touch to canvas.
  - If the header is hidden but user pulls downward and `offsetY == 0`, forward touch to reveal the header.
  - Otherwise, allow Sora Editor to consume the vertical scroll.
- Caret and selection geometries query Sora line metrics via `editor.getLayout()` and convert character positions to document coordinates via `ScribeScrollGeometry`.

### D. Policy and Geometry Invariants
- **Rigid 1-DOF Invariant:** Enforced by `ScribeScrollPolicy.allocateScrollDelta` and `UnifiedCanvasLayout.applyTranslations`.
  - When `scrollD < headerHeight`: `editor.offsetY` is strictly 0. Both header and editor translate by `-scrollD`.
  - When `scrollD == headerHeight`: Header translation is `-headerHeight` (fully hidden). Additional scroll increases `editor.offsetY`.
- **Caret Visibility Breathing Margin & Deadband:** Enforced by `ScribeScrollPolicy.resolveCursorScrollDelta`:
  - Maintains `CURSOR_BOTTOM_MARGIN_DP = 24dp` breathing clearance above the bottom surface.
  - Applies `CURSOR_DEADBAND_DP = 4dp` deadband to prevent microscopic jitter.
- **Direction-Specific Cursor Correction Dispatch:** Enforced in `UnifiedCanvasLayout.requestChildRectangleOnScreen`:
  - Positive delta (bottom overflow): honors deletion transaction suppression, triggers authoritative spring glide for keyboard transactions / active animations, smooth-scrolls, or immediate-scrolls.
  - Negative delta (top underflow): directly scrolls canvas immediately unless an authoritative glide is already in progress.

### E. Animation and Input Execution
- **Input Execution (`ScribeScrollInputController`):**
  - Intercepts touch gestures in `dispatchTouchEvent`, `onInterceptTouchEvent`, and `onTouchEvent`.
  - Detects touch slop before claiming scroll ownership.
  - Manages momentum flings across the canvas/editor boundary via `OverScroller`.
  - Automatically cancels flings when user touches down or initiates selection handle drags.
- **Spring Animation (`ScribeCanvasGlideAnimator`):**
  - Simulates a critically damped second-order harmonic oscillator:
    $$\ddot{x} + 2\zeta\omega\dot{x} + \omega^2(x - x_{	ext{target}}) = 0$$
    with $\omega = 13.5	ext{ rad/s}$ and $\zeta = 1.0$.
  - Produces an asymptotic ~300ms glide with zero overshoot.
  - Retargets in-flight seamlessly without resetting position or velocity.
  - Hard boundary invariant: deletion glides are clamped to `deletionBoundaryProvider` to guarantee the caret never crosses below the 24dp breathing margin.

### F. Navigation and Viewport Restoration
- `EditorSessionRestorationCoordinator` captures viewport state (`totalScroll`, cursor position) during screen exits or sheet presentations.
- Upon returning to the editor, it reconciles the restored viewport using `UnifiedCanvasLayout.reconcileScrollState(totalScroll)`.
- `ScribeScrollPolicy.calculateImmediateScrollSplit` partitions `totalScroll` into exact `scrollD` and `offsetY` values, restoring the exact document view without visual jump.

---

## 5. Summary of Architecture Health

The Scribe Native scroll subsystem is modularized into dedicated single-responsibility components:
- Pure mathematical policies and geometries contain zero mutable state and zero Android framework dependencies.
- Central document scroll state is coordinated by `ScribeScrollCoordinator`, with monotonic operation IDs protecting against stale callbacks.
- `UnifiedCanvasLayout` acts as the Android integration/orchestration boundary, maintaining View-side measurement, touch dispatch, and immediate corrections while delegating smooth glide animations to the scroll subsystem.
- Test-only abstractions (`ProductionScrollAnimationCoordinator`, `AnimationDriver`, `TestAnimationDriver`, `ContractionSimulationController`) are completely segregated into test source (`ScribeScrollTestSupport.kt`).
- Note: While code architecture is cleanly separated, actual device runtime behavior and hardware interaction remain unverified and require separate validation.
