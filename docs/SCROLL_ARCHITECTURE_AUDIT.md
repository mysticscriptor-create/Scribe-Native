# SCROLL_ARCHITECTURE_AUDIT: Scribe Native Viewport & Scrolling Architecture

---

## 1. Current Repository HEAD

- **Inspected Commit SHA**: `6585cca6f2944b685cc4f4213da44fa9cc737ab3`
- **Branch**: `main`
- **Starting Point**: `a3b1dedcf2c6784aa849ed1abe06454d88a012f7`
- **Reference Commits Examined**:
  - `d0d39b5`: Original behavioral reference (Pure Writer bottom space, stationary dismissal, fluid shortcut bar entry)
  - `475177f`: Unnecessary cursor movement elimination and coordinated IME entrance
  - `c7fe7fa`: Mid-flight predictive glide stabilization and tap entrance
  - `6f75981`: Expanded-state scrolling and keyboard-entrance jump fixes
  - `00b8d58`: Replacement of expansion compensation ledger with viewport-driven scrolling
  - `0b3a48c`: Compensation invalidation preservation and viewport settlement flow
  - `a3b1ded`: Viewport adjustment settlement completion in glide frame loop
  - `6585cca`: Elimination of double-subtraction in viewport evaluation, baseline capture ordering correction, and committed caret resolution

---

## 2. Architecture Overview

Scribe implements a hybrid Compose-AndroidView architecture designed to provide a rich text-editing canvas with an edge-to-edge window, a collapsible note header, a code/prose editor (Rosemoe's Sora Editor), a contextual shortcut accessory bar, and expandable drawers.

```mermaid
graph TD
    subgraph Compose["Compose Hierarchy (MainEditorScreen.kt)"]
        SWI["Scaffold & WindowInsets<br/>(ime, imeAnimationTarget, navigationBars)"]
        BWC["BoxWithConstraints<br/>(maxHeight = containerHeightPx)"]
        BSS["ScribeBottomSurfaceCoordinator<br/>(PresentationState Reducer & Geometry)"]
        CUST_LAY["Custom Modifier.layout()<br/>adjustedHeight = constraints.maxHeight - topPaddingPx - bottomPaddingPx"]
        AV["AndroidView(UnifiedCanvasLayout)"]
        DHC["ScribeDrawerHeightController<br/>(Compact / Expanded / Full Dp)"]
    end

    subgraph Canvas["UnifiedCanvasLayout (ViewGroup)"]
        UCL["UnifiedCanvasLayout<br/>height = adjustedHeight<br/>scrollD (Outer) + editor.offsetY (Inner)"]
        KTC["KeyboardTransactionCoordinator<br/>(Predictive Glide & Entrance Epochs)"]
        BTC["BottomSurfaceTransitionCoordinator<br/>(Transitions & Dismissal Preservation)"]
        GLIDE["Spring Physics Engine<br/>glideFrameRunnable (omega=13.5 rad/s)"]
    end

    subgraph Policy["Scroll Policy & State (ScribeScrollPolicy.kt)"]
        ASC["AuthoritativeScrollCoordinator<br/>displayedTotalScroll = persistent + temporary"]
        RVA["resolveViewportAdjustment()<br/>(Stateless Viewport Sizing)"]
        RCS["resolveCursorSafety()<br/>(Breathing Margin: 24dp, Deadband: 4dp)"]
        CMC["calculateMaxCanvasScroll()<br/>(1-DOF Logical Scroll Boundary)"]
    end

    subgraph Sora["Editor Subsystem (ScribeCodeEditor.kt)"]
        SCE["ScribeCodeEditor : CodeEditor<br/>onTouchEvent -> super -> ensurePositionVisible"]
        ESC["EditorScroller (Hardware Scroll Engine)"]
    end

    SWI --> BWC
    BWC --> CUST_LAY
    BSS --> CUST_LAY
    CUST_LAY --> AV
    AV --> UCL
    DHC --> BSS

    UCL --> KTC
    UCL --> BTC
    UCL --> GLIDE
    UCL --> ASC
    UCL --> SCE

    ASC --> Policy
    GLIDE --> ASC
    SCE --> ESC
    SCE --> UCL
```

### The 1-DOF Canvas Coordinate Model
The document layout does not embed Sora in an Android `ScrollView` (which would disrupt Sora's tile recycling and hardware line rendering). Instead, `UnifiedCanvasLayout` implements a **Single Degree of Freedom (1-DOF)** compound coordinate system:
1. **Outer Scroll (`scrollD`)**: Translates `headerView` (ComposeView containing note title, tags, date) and `editor` (Sora view) vertically between `0` and `headerHeight`.
2. **Inner Scroll (`editor.offsetY`)**: Sora's internal hardware line offset managed by its native `EditorScroller`.
3. **Strict Invariant**:
   $$\text{Total Logical Scroll} = \text{scrollD} + \text{editor.offsetY}$$
   If $\text{scrollD} < \text{headerHeight}$, then $\text{editor.offsetY} \equiv 0$. The editor's text content never begins scrolling internally until the note header has completely translated off-screen.

### The Dual-Value Viewport-Driven Scroll Model
Introduced in commit `00b8d58`, the scrolling policy separates long-term document position from temporary UI contractions:
$$\text{displayedTotalScroll} = \text{persistentScrollPosition} + \text{temporaryViewportAdjustment}$$
- `persistentScrollPosition`: The actual canonical scroll position within the document. Updated by user drags, flings, and intentional cursor visibility requirements.
- `temporaryViewportAdjustment`: Transient upward displacement applied *only* when a bottom surface (accessory drawer or oversized IME picker) expands beyond standard baseline. When the surface contracts back to normal, this term smoothly returns to `0f`, restoring the original viewport without leaving residual scroll offset.

---

## 3. Mutable Scrolling State Ownership Table

| State Variable | Owning Class & File | Purpose | Other Modifying Components | Duplicate / Overlapping State |
| :--- | :--- | :--- | :--- | :--- |
| `scrollD` / `scrollDFloat` | `UnifiedCanvasLayout.kt` (lines 751-754) | Current outer header translation ($0 \le \text{scrollD} \le \text{headerHeight}$). | `scrollCanvasBy()`, `jumpScrollTo()`, `onMeasure()` reconciliation. | Overlaps with `scrollCoordinator.totalScroll`. |
| `editor.offsetY` | `CodeEditor.java` / `ScribeCodeEditor.kt` | Internal Sora text scroll offset. | `dispatchScrollToEditor()`, Sora's `EditorScroller`, `jumpScrollTo()`. | Component of `displayedTotalScroll`. |
| `persistentScrollPosition` | `AuthoritativeScrollCoordinator` (`ScribeScrollPolicy.kt:1495`) | Authoritative canonical document scroll offset. | `recordUserScrollDelta()`, `onViewportAdjustmentSettled()`, `evaluateViewportAdjustment()`. | Represents the persistent portion of `scrollD + editor.offsetY`. |
| `temporaryViewportAdjustment` | `AuthoritativeScrollCoordinator` (`ScribeScrollPolicy.kt:1496`) | Temporary upward displacement for expanded surfaces. | `applyStep()`, `evaluateViewportAdjustment()`, `onViewportAdjustmentSettled()`. | Overlaps with `transitionCoordinator.compensation.remainingDisplacement`. |
| `genericRetainedScroll` | `UnifiedCanvasLayout.kt` (line 71) | Preserves stationary document offset when keyboard collapses. | `completeTerminalDismissal()`, `checkAndClearPreservedDismissalScroll()`. | Overlaps with `transitionCoordinator.preservedDismissalScroll`. |
| `preservedDismissalScroll` (getter/setter) | `UnifiedCanvasLayout.kt` (lines 82-86) | Computed property alias for dismissal preservation. | Delegates to `transitionCoordinator.preservedScroll ?: genericRetainedScroll`. | **Duplicate**: Exposes two redundant storage locations for the same preserved scroll value. |
| `isKeyboardDismissing` | `UnifiedCanvasLayout.kt` (lines 88-99) | Synchronous frame-0 flag during IME collapse. | Derived from `transitionCoordinator.isTerminalDismissalActive`; setter calls `startTerminalDismissal()`. | Overlaps with `MainEditorScreen.presentationState == KEYBOARD_DISMISSAL`. |
| `liveImeHeightPx` | `UnifiedCanvasLayout.kt` (lines 137-145) | Current physical IME height above navigation bar. | Set by `MainEditorScreen.kt` in `AndroidView.update` and `snapshotFlow`. | Overlaps with Compose `WindowInsets.ime`. |
| `liveDrawerHeightPx` | `UnifiedCanvasLayout.kt` (lines 150-158) | Current physical accessory drawer obstruction. | Set by `MainEditorScreen.kt` from `drawerHeightController`. | Mirrors `drawerHeightController.currentDisplayedHeightDp`. |
| `targetEditorViewportHeightPx` | `UnifiedCanvasLayout.kt` (line 165) | Authoritative expected final viewport height. | Updated by `MainEditorScreen.kt` via `calculateTargetEditorViewportPx()`. | Overlaps with predictive viewport calculation inside `predictivelyGlideCursorAboveKeyboard`. |
| `standardKeyboardHeightPx` | `UnifiedCanvasLayout.kt` (lines 119-122) | Validated baseline standard keyboard height in pixels. | Set by `MainEditorScreen.kt` from `lastKeyboardHeightDp`. | Mirrors `lastKeyboardHeightDp` in Compose. |
| `persistentBottomSpacePx` | `UnifiedCanvasLayout.kt` (lines 188-195) | Virtual trailing blank space added to editor body. | Computed by `ScribeScrollPolicy.calculateVirtualTrailingExtentPx()`. | Pushed to `editor.persistentBottomSpacePx`. |
| `presentationState` | `MainEditorScreen.kt` (line 392) | High-level lifecycle state machine. | Reduced by `ScribeBottomSurfaceCoordinator.reduce()` on IME/drawer events. | Redundant with `transitionCoordinator.currentTransition.type`. |
| `currentTransition` | `BottomSurfaceTransitionCoordinator` (`ScribeBottomSurfaceCoordinator.kt:187`) | Tracks current active transition type, generation, phase, and geometry. | Updated by `startTransition()`, `startTerminalDismissal()`, `onSurfacePhysicallySettled()`. | **Duplicate**: Maintains independent `TransitionType` and `ExpansionCompensation` ledger alongside `AuthoritativeScrollCoordinator`. |
| `currentTransaction` | `KeyboardTransactionCoordinator` (`ScribeScrollPolicy.kt:715`) | Tracks active keyboard entrance transaction. | Started by `beginKeyboardTransaction()`, reconciled by `reconcileActiveTransactionTarget()`. | Controls predictive glide ownership. |
| `activeOperationId` | `AuthoritativeScrollCoordinator` (`ScribeScrollPolicy.kt:1552`) | Monotonically increasing ID preventing stale animation callbacks. | Incremented on every `requestTarget()`. | Synchronized with `UnifiedCanvasLayout.activeGlideOpId`. |
| `glidePosition` / `glideVelocity` / `glideTarget` | `UnifiedCanvasLayout.kt` (lines 885-887) | Internal state of the critically damped spring physics loop. | Updated frame-by-frame by `glideFrameRunnable`. | Authoritative animation runtime state. |

---

## 4. Automatic Scrolling Entry-Point Map

| Entry Point | Triggering Event | Deciding Component | Decision Logic | Performing Component | State Updated |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Editor Tap (Keyboard Hidden)** | User taps text in editor. | `ScribeCodeEditor` & `UnifiedCanvasLayout` | Detects tap; `showSoftInput()` calls `notifyKeyboardEntranceStarting()`. | `predictivelyGlideCursorAboveKeyboard()` via `startAuthoritativeScrollAnimation()` | `isKeyboardTransactionActive = true`, `targetPredictedScrollTotal`, `scrollCoordinator`. |
| **2. Editor Tap (Keyboard Settled)** | User taps another line while keyboard is visible. | `ScribeCodeEditor` & `UnifiedCanvasLayout` | Post-tap `ensureCursorVisibleAboveKeyboard()` queries `resolveCursorSafety()`. | `startAuthoritativeScrollAnimation()` (reason: `CARET_VISIBILITY`) | If unsafe: updates `scrollCoordinator.persistentScrollPosition`. If safe: 0 movement. |
| **3. Selection Change** | Cursor moved via cursor drag, typing, or selection handle. | `UnifiedCanvasLayout.notifySelectionChangedWithProvenance()` | Evaluates `resolveViewportAdjustment()`; if surface expanded, calculates temporary adjustment. | `startAuthoritativeScrollAnimation()` (reason: `VIEWPORT_ADJUSTMENT`) | Updates `scrollCoordinator.caretLine`, `caretCol`, `temporaryViewportAdjustment`. |
| **4. Predictive Cursor Movement** | Soft keyboard entrance starting (`showSoftInput`). | `UnifiedCanvasLayout.predictivelyGlideCursorAboveKeyboard()` | Evaluates cursor doc position against `futureViewportHeight - 24dp`. If unsafe, computes target total. | `startAuthoritativeScrollAnimation()` (reason: `PREDICTIVE_GLIDE`) | `isPredictiveGlideRunning = true`, `activeKeyboardTransaction.targetScroll`. |
| **5. IME Viewport Resizing** | `onSizeChanged()` in `UnifiedCanvasLayout` ($dh > 0$). | `UnifiedCanvasLayout.onSizeChanged()` | If transaction active: records layout settled. If settled & caret occluded: requests visibility. | Calls `reconcileActiveTransactionSettledViewport()` or `resolveCursorSafety()` | Reconciles predictive overshoot or adjusts canvas scroll. |
| **6. Keyboard Expansion** | User opens emoji, sticker, or clipboard picker. | `UnifiedCanvasLayout.evaluateViewportAdjustment()` | `isBottomSurfaceExpanded()` detects $\text{liveIme} > \text{baseline} + 4\text{dp}$. `resolveViewportAdjustment()` computes temporary upward displacement. | `startAuthoritativeScrollAnimation()` (reason: `VIEWPORT_ADJUSTMENT`) | `scrollCoordinator.temporaryViewportAdjustment`, `transitionCoordinator.compensation`. |
| **7. Drawer Opening / Expansion** | User clicks bottom accessory icon or drags drawer up. | `MainEditorScreen` -> `notifyDrawerExpansionStarting()` | `drawerHeightController` triggers expansion. `evaluateViewportAdjustment()` calculates required temporary adjustment. | `startAuthoritativeScrollAnimation()` (reason: `VIEWPORT_ADJUSTMENT`) | `isExpansionTransitionActive = true`, `temporaryViewportAdjustment`. |
| **8. Drawer Dragging & Contraction** | User drags drawer handle downward. | `drawerHeightController.onDragDirectionChanged` | Drawer contracts to compact; `isSurfaceExpanded` becomes `false`. `resolveViewportAdjustment()` returns target temp = 0. | Spring physics glides `temporaryViewportAdjustment` back to 0. | `temporaryViewportAdjustment` decreases; folded into persistent upon completion. |
| **9. Terminal Keyboard Dismissal** | Soft keyboard starts collapsing to 0. | `MainEditorScreen` (Frame 0 guard) & `UnifiedCanvasLayout.isKeyboardDismissing` | Target IME $\le 10\text{px}$. Synchronously latches `preservedDismissalScroll = scrollD + editor.offsetY`. Cancels upward safety glides. | Zero scroll movement; document stays completely stationary. | `isKeyboardDismissing = true`, `transitionCoordinator.preservedScroll`. |
| **10. Drawer Dismissal** | User closes accessory drawer. | `MainEditorScreen` & `UnifiedCanvasLayout.isBottomSurfaceActive` | Latches `preservedDismissalScroll`; folds any remaining temporary adjustment into persistent scroll. | Stationary document preservation; clears temporary adjustment. | `isBottomSurfaceActive = false`, `temporaryViewportAdjustment = 0f`. |
| **11. Contraction Restoration** | Bottom surface returns from expanded to baseline. | `UnifiedCanvasLayout.evaluateViewportAdjustment()` | `resolveViewportAdjustment()` evaluates target temporary adjustment against contracted height. | Spring glide reduces `displayedTotalScroll` to `persistentScrollPosition`. | `temporaryViewportAdjustment` decremented; `totalConsumedDisplacement` updated. |
| **12. Typing & Content Insertion** | User types characters or inserts newlines. | `UnifiedCanvasLayout.onEditorContentOrSelectionChanged()` | Checks if caret moved below safe boundary in active viewport. | `ensureCursorVisibleAboveKeyboard()` or `evaluateViewportAdjustment()` | Adjusts persistent scroll if caret reaches bottom breathing margin. |
| **13. Deletion & Tail Anchoring** | User deletes characters on the last line or shrinks text. | `UnifiedCanvasLayout.onEditorContentOrSelectionChanged()` | If document shrinks below current scroll, `calculateSafeDeletionDestination()` clamps excess scroll. | `startAuthoritativeScrollAnimation()` (reason: `DELETION_ANCHOR`, duration: 140ms) | Clamps `scrollD` and `editor.offsetY` down to new `maxCanvasScroll`. |
| **14. Programmatic Cursor Visibility** | Navigation, find-and-replace, or session restore. | `ScribeCodeEditor.ensurePositionVisible()` | Directly invokes `parentCanvas.ensureCursorVisibleAboveKeyboard(force = true)`. | Authoritative scroll animation. | Updates canonical canvas position. |

---

## 5. Animation Ownership Map

### 1. Spring Physics Engine (`glideFrameRunnable` in `UnifiedCanvasLayout.kt`)
- **Starter**: `startAuthoritativeScrollAnimation(targetTotalScroll, reason, durationMs, forceRetarget, onEnd)`
- **Updater**: `glideFrameRunnable` scheduled via `postOnAnimation(this)`. Uses a second-order critically damped spring:
  $$\omega = 13.5\text{ rad/s}, \quad \zeta = 1.0 \implies \text{Zero overshoot, smooth asymptotic settling within } \sim 280\text{ms}$$
- **Displacer**: Calls `scrollCoordinator.applyStep(glidePosition, opId)`, which invokes `scrollCanvasBy(stepDelta)` to allocate between `scrollD` and `editor.offsetY`.
- **Retargeting**: When `startAuthoritativeScrollAnimation()` is called while an animation is already running:
  - If target difference $\le 1\text{px}$, ignores the call.
  - Otherwise, updates `glideTarget = clampedTarget`, updates `activeGlideReason = reason`, chains `activeGlideOnEnd`, and notifies `scrollCoordinator.retargetActiveOperation()`. **The current velocity (`glideVelocity`) is preserved**, avoiding visual stutters or sudden deceleration.
- **Stale Callback Prevention**: `AuthoritativeScrollCoordinator` maintains a monotonically increasing `activeOperationId`. Any `glideFrameRunnable` execution whose `opId != scrollCoordinator.activeOperationId` exits immediately without mutating state.

### 2. Manual Touch Fling Scroller (`scroller: OverScroller` in `UnifiedCanvasLayout.kt`)
- **Starter**: `UnifiedCanvasLayout.dispatchTouchEvent` on `ACTION_UP` when canvas dragging velocity exceeds `minFlingVelocity`.
- **Updater**: Overridden `computeScroll()` calls `scroller.computeScrollOffset()` on animation ticks, invokes `scrollCanvasBy(dy)`, and records the delta into `scrollCoordinator.recordUserScrollDelta()`.
- **Cancellation**: Aborted immediately on `ACTION_DOWN` via `scroller.abortAnimation()` and `editor.stopFling()`.

### 3. Sora Internal Scroller (`editor.scroller: EditorScroller`)
- **Starter**: Native text scrolling in Sora Editor.
- **Coordination**: When hosted in `UnifiedCanvasLayout`, vertical scrolling is routed through `dispatchScrollToEditor(dy)`. `UnifiedCanvasLayout` manages vertical motion to preserve 1-DOF invariants. Sora's scroller is directly used only for horizontal scrolling (`offsetX`).

### 4. Compose Accessory Animations (`MainEditorScreen.kt`)
- `shortcutBarProgress`: Driven by `animateFloatAsState(targetValue, tween(180ms))`. Only modulates alpha/translation of the floating shortcut bar; does not directly move the canvas document.
- `handoffAnim`: Used during drawer-to-keyboard handoff if explicit progress interpolation is required.

---

## 6. Viewport Geometry Ownership Map

```
+-------------------------------------------------------------------+  y = 0 (Window / Screen Top)
| Top Insets (Status Bar / Cutout) = docTopInset.roundToPx()        |
+-------------------------------------------------------------------+  y = docTopInset
|                                                                   |
|                                                                   |
|   USABLE WRITING VIEWPORT                                         |
|   Measured Height of UnifiedCanvasLayout = h                      |
|                                                                   |
|   Usable for Text Layout & Cursor Safety:                         |
|   usableViewportHeight = h                                        |
|                                                                   |
|   Safe Bottom Boundary for Caret:                                 |
|   safeBottomBoundary = h - CURSOR_BOTTOM_MARGIN_DP (24dp)         |
|                                                                   |
| - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - |  y = h - 24dp (Safe Boundary)
|   Deadband Tolerance = 4dp                                        |
+-------------------------------------------------------------------+  y = h (UnifiedCanvasLayout Bottom)
| Fixed Shortcut Bar Clearance = 36dp                               |
+-------------------------------------------------------------------+
| Physical Bottom Surface (IME or Accessory Drawer)                |
| liveImeHeightPx / liveDrawerHeightPx                              |
+-------------------------------------------------------------------+
| Navigation Bar Inset = navInsets.getBottom(density)              |
+-------------------------------------------------------------------+  y = Window Bottom (maxHeight)
```

### Definitions & Measurement Sites:
1. **Compose Container Height (`containerHeightPx`)**:
   `with(density) { maxHeight.roundToPx() }` inside `BoxWithConstraints`. Because edge-to-edge is enabled with `contentWindowInsets = WindowInsets(0, 0, 0, 0)`, this represents the full display height of the window.
2. **Top Padding (`topPaddingPx`)**:
   `with(density) { docTopInset.roundToPx() }`. Represents status bar and top display cutouts.
3. **Bottom Geometry (`bottomGeometry.editorBottomPaddingDp`)**:
   Calculated by `ScribeBottomSurfaceCoordinator.calculateEditorBottomGeometry()`:
   $$\text{totalBottomPadding} = \text{navBarBottomDp} + \text{effectiveBottomSurfaceDp} + \text{shortcutBarClearanceDp}$$
   Where $\text{effectiveBottomSurfaceDp} = \max(0, \text{imeBottomDp} - \text{navBarBottomDp})$ for keyboard, or $\text{displayedDrawerHeightDp}$ for accessory drawer.
4. **Actual Measured Layout Height (`h`)**:
   In `MainEditorScreen.kt` custom `.layout`:
   $$\text{adjustedHeight} = \text{constraints.maxHeight} - \text{topPaddingPx} - \text{bottomPaddingPx}$$
   `UnifiedCanvasLayout` is measured with exactly `minHeight = adjustedHeight, maxHeight = adjustedHeight`. Thus, `UnifiedCanvasLayout.height` ($h$) **already excludes all bottom obstructions**.
5. **Usable Writing Viewport (`usableViewportHeight`)**:
   In `UnifiedCanvasLayout.evaluateViewportAdjustment()` and `resolveCursorSafety()`, `usableViewportHeight \equiv h`.
   *(Note: The previous defect in commit `a3b1ded` subtracted `effectiveBottomObstructionPx` again from $h$, which constituted the double-subtraction bug).*
6. **Predictive Target Viewport Height (`targetEditorViewportHeightPx`)**:
   Calculated by `calculateTargetEditorViewportPx(containerHeightPx, topPaddingPx, targetBottomPaddingPx)` and passed to `layout.updateTargetEditorViewport()`. Enables predictive glides to aim for the exact final layout height before the OS finishes resizing the window.
7. **Virtual Bottom Clearance (`persistentBottomSpacePx`)**:
   Virtual height appended to Sora's document extent:
   - When keyboard is hidden: $\text{standardKeyboardHeightPx} + \text{shortcutBarHeightPx} + \text{breathingMarginPx}$.
   - When keyboard is open: $\text{breathingMarginPx}$ ($24\text{dp}$).
   Guarantees that on keyboard dismissal, the document text does not get clipped or forced downward, maintaining a stationary scroll position.

---

## 7. Keyboard and Drawer Lifecycle Explanations

### A. Tap Editor While Keyboard is Hidden
1. **Initial Touch**: User touches line $N$. `UnifiedCanvasLayout.dispatchTouchEvent(ACTION_DOWN)` records `lastTouchDownY` and `lastTouchDownTime`.
2. **Tap Resolution**: On `ACTION_UP`, `ScribeCodeEditor.onTouchEvent` detects an un-moved short touch ($<450\text{ms}$).
3. **Keyboard Request**: Sora's internal logic calls `showSoftInput()`.
4. **Predictive Entrance**: Overridden `showSoftInput()` calls `UnifiedCanvasLayout.notifyKeyboardEntranceStarting()`.
   - `beginKeyboardTransaction()` locks in `preEntranceMaxCanvasScroll = naturalMaxCanvasScroll` and starts `transactionCoordinator`.
   - `predictivelyGlideCursorAboveKeyboard()` calculates future viewport:
     $$\text{futureViewportHeight} = \text{height} - \text{estimatedKeyboardHeightPx}$$
   - Evaluates cursor screen position against `futureViewportHeight - 24dp`.
   - If the cursor is already safe in the future viewport, zero scroll is initiated. If unsafe, starts `startAuthoritativeScrollAnimation()` with `PREDICTIVE_GLIDE`.
5. **State Transition**: `MainEditorScreen` receives `onKeyboardEntranceStarted`, transitioning `presentationState` from `HIDDEN` to `KEYBOARD_ENTRANCE`. Shortcut bar reservation is latched.

### B. Tap Another Line While Keyboard is Already Visible
1. **Initial Touch**: User taps line $M$. `UnifiedCanvasLayout` notes `isUserTouching = true` and `isKeyboardVisible = true`.
2. **Caret Movement**: Sora processes the touch and moves the cursor to line $M$.
3. **Selection Callback**: Sora fires `SelectionChangeEvent` -> `UnifiedCanvasLayout.notifySelectionChangedWithProvenance()`.
   - Updates `scrollCoordinator` caret line and column.
   - Calls `evaluateViewportAdjustment()`. Because `isSurfaceExpanded == false`, `resolveViewportAdjustment()` returns `shouldScroll = false`.
4. **Visibility Verification**: Sora invokes `ensurePositionVisible(line, col)` -> calls `parentCanvas.ensureCursorVisibleAboveKeyboard()`.
   - `resolveCursorSafety()` evaluates line $M$'s doc bottom against $\text{height} - 24\text{dp}$.
   - If line $M$ is above the safe boundary, returns `isSafe = true, neededUpwardDisplacement = 0f`. Zero movement.
   - If line $M$ is below the safe boundary (e.g. occluded near shortcut bar), computes exact overflow and smoothly glides the canvas upward by that exact delta.
5. **Post-Tap Completion**: `ACTION_UP` in `ScribeCodeEditor` verifies visibility using committed coordinates (`targetLine = null`), ensuring no competing pre-tap coordinates override the animation.

### C. Normal Keyboard Entrance and Settlement
1. **Entrance**: As the OS animates IME insets upward, Compose `WindowInsets.ime` increases.
2. **Intermediate Measurement**: Custom `.layout` measures `UnifiedCanvasLayout` with decreasing `adjustedHeight`. `UnifiedCanvasLayout.onSizeChanged()` runs. Because `isKeyboardTransactionActive` is true, intermediate resize frames do not spawn competing scroll animations.
3. **Settlement**: When `curImePx == targetImePx`:
   - `MainEditorScreen` snapshotFlow observes `isPhysicallySettled = true`.
   - Evaluates `wasKeyboardAlreadySettled = (presentationState == KEYBOARD_SETTLED)` (which evaluates to `false`).
   - Transitions `presentationState` to `KEYBOARD_SETTLED`.
   - `canUpdateSavedStandardBaseline()` validates the measured height and updates `lastKeyboardHeightDp` and `dataStore`.
   - Calls `unifiedCanvasRef.notifyImeSettledAtTarget()`.
   - `notifyImeSettledAtTarget()` reconciles any slight difference between predicted and actual settled viewport, terminates the keyboard transaction, and settles the document.

### D. Oversized Keyboard Expansion and Contraction (e.g., Emoji / Picker Panel)
1. **Expansion**: User switches to emoji panel. Physical IME height increases by $>10\text{dp}$.
2. **Detection**:
   - `MainEditorScreen` observes new height, but `canUpdateSavedStandardBaseline()` rejects updating the standard baseline because `wasKeyboardAlreadySettled == true`.
   - `UnifiedCanvasLayout.evaluateViewportAdjustment()` checks `isBottomSurfaceExpanded()`. Since $\text{liveImeHeightPx} > \text{standardKeyboardBaselinePx} + 4\text{dp}$, `isSurfaceExpanded` evaluates to `true`.
3. **Temporary Adjustment**: `resolveViewportAdjustment()` calculates:
   $$\text{targetTemporaryAdjustment} = \text{safety.neededUpwardDisplacement}$$
   Authoritative spring physics animates total scroll to `persistentScrollPosition + targetTemporaryAdjustment` (reason: `VIEWPORT_ADJUSTMENT`).
4. **Contraction**: User switches back to QWERTY keyboard. IME height contracts back to standard baseline.
5. **Restoration**: `isBottomSurfaceExpanded()` becomes `false`. `resolveViewportAdjustment()` calculates `targetTemporaryAdjustment = 0f`. Spring physics smoothly glides the document back down to `persistentScrollPosition`.

### E. Compact Drawer Opening
1. **Trigger**: User taps an accessory icon (e.g. Outline or Shortcuts).
2. **State Transition**: `activeAccessoryDrawer = OUTLINE`, `drawerState = COMPACT`. `presentationState` transitions to `ACCESSORY_DRAWER_OPEN`.
3. **Geometry**: `drawerHeightController` animates displayed height to `compactHeightDp` ($180\text{dp}$).
4. **Viewport Adjustment**: `isBottomSurfaceExpanded()` evaluates to `false` (compact height $\le$ compact baseline). `resolveViewportAdjustment()` calculates zero temporary displacement. The document does not scroll artificially; only the physical layout height is updated.

### F. Compact → Expanded → Compact Drawer Transition
1. **Expansion**: User drags drawer handle upward to `EXPANDED` ($350\text{dp}$).
2. **Adjustment**: `isBottomSurfaceExpanded()` becomes `true`. `resolveViewportAdjustment()` computes required temporary displacement to keep the active caret visible above the expanded drawer. Canvas animates upward.
3. **Contraction**: User drags drawer back down to `COMPACT`.
4. **Restoration**: `isBottomSurfaceExpanded()` becomes `false`. `resolveViewportAdjustment()` sets target temporary displacement to `0f`. Canvas glides back downward to `persistentScrollPosition`.

### G. Compact → Full → Expanded Drawer Transition
1. **To Full**: User flings drawer to top of screen (`FULL`).
2. **Continuous Adjustment**: Viewport shrinks further. `evaluateViewportAdjustment()` retargets in-flight spring animation to the new required temporary displacement.
3. **To Expanded**: User drags drawer back down to `EXPANDED`.
4. **Proportional Release**: Viewport expands. `evaluateViewportAdjustment()` re-evaluates required displacement; the spring smoothly decelerates and glides to the lower temporary displacement without jump.

### H. Drawer Drag Reversal
1. **Mid-Drag Direction Change**: User begins dragging drawer down, then reverses upward before settlement.
2. **Listener**: `drawerHeightController.onDragDirectionChanged` detects reversal.
3. **In-Flight Retargeting**: Calls `notifyDrawerExpansionStarting()` with new target obstruction. `startAuthoritativeScrollAnimation()` seamlessly updates `glideTarget` while preserving `glideVelocity`, ensuring physical continuity without animation resets.

### I. Drawer-to-Keyboard Handoff
1. **Trigger**: User taps the editor body while the accessory drawer is open.
2. **Initiation**: `returnFromDrawerToKeyboard()` captures `handoffStartingObstructionDp = currentDrawerH` and transitions `presentationState` to `DRAWER_TO_KEYBOARD_HANDOFF`.
3. **Spacer Bridging**: `transitionalSpacerDp` is set to `currentDrawerH`, holding the bottom obstruction constant while the drawer closes and soft input opens.
4. **Handoff Completion**: In `snapshotFlow`, `canCompleteHandoff()` monitors IME progress. When IME reaches target, transitions to `KEYBOARD_SETTLED`, resets `transitionalSpacerDp = 0.dp`, and calls `notifyHandoffCompleted()`.

### J. Complete Keyboard Dismissal
1. **Frame-0 Guard**: In `MainEditorScreen.kt` custom `.layout`, when `targetImePx <= 10px` while `curImePx > 0`, immediately triggers `unifiedCanvasRef.notifyTerminalDismissalStarting()`.
2. **Preservation**: `startTerminalDismissal(current)` captures:
   $$\text{preservedDismissalScroll} = \text{scrollD} + \text{editor.offsetY}$$
   `isKeyboardDismissing` is set to `true`.
3. **Animation Cancellation**: All active upward safety animations and restorations are immediately cancelled.
4. **Stationary Invariant**: As the keyboard animates down and the layout height expands back to full screen, `maxCanvasScroll` anchors to `maxOf(naturalMax, preservedDismissalScroll)`. The document remains at the exact pixel position where the user was typing, with zero downward bounce or drift.

---

## 8. Original-Versus-Current Architecture Comparison

| Architectural Aspect | Original Behavioral Reference (`d0d39b5`) | Current Implementation (`6585cca`) | Architectural Status & Rationale |
| :--- | :--- | :--- | :--- |
| **Expansion Tracking** | **Expansion Compensation Ledger**: Recorded cumulative $\Delta y$ into a ledger per transition generation; tracked anchor lines/columns. | **Stateless Viewport-Driven Adjustment**: Computes `temporaryViewportAdjustment` directly from current geometry and `isSurfaceExpanded`. | **Replaced & Modernized**: Ledger-based compensation suffered from anchor invalidation whenever users edited text, added lines, or selected text while expanded. Viewport-driven model is stateless and self-healing. |
| **Scroll Value Decomposition** | Single scalar scroll offset with ad-hoc compensations added and subtracted. | Dual-value model: `displayedTotalScroll = persistentScrollPosition + temporaryViewportAdjustment`. | **Substantially Redesigned**: Ensures user-initiated scrolling mutates persistent scroll without losing or corrupting temporary expansion adjustments. |
| **Keyboard Dismissal** | Stationary Pure Writer invariant with `preservedDismissalScroll` and virtual bottom space. | Retained `preservedDismissalScroll` and `persistentBottomSpacePx` with Frame-0 synchronous dismissal guard. | **Preserved & Hardened**: The core behavioral mechanism from `d0d39b5` is maintained, with synchronous frame-0 detection added to prevent 1-frame race conditions. |
| **1-DOF Compound Scroll** | Compound scrolling between `headerView` (`scrollD`) and Sora (`editor.offsetY`). | Identical compound scrolling governed by `ScribeScrollPolicy.allocateScrollDelta()`. | **Preserved**: Essential invariant that prevents Sora lines from rendering behind the Compose header. |
| **Spring Animation Engine** | Android `ValueAnimator` with cubic interpolators. | Second-order critically damped spring physics loop (`glideFrameRunnable`, $\omega = 13.5\text{ rad/s}, \zeta = 1.0$). | **Replaced**: Eliminates duration-based time resets on continuous retargeting; enables smooth in-flight velocity preservation. |
| **Caret Visibility Decision** | Ad-hoc threshold checks distributed across layout and editor. | Unified `resolveCursorSafety()` with explicit 24dp breathing margin, 4dp deadband, and interaction origins. | **Unified & Preserved**: Canonical decision function prevents divergent target calculations. |
| **Keyboard Entrance** | Synchronous jump or conflicting resize scrolls. | Two-phase `KeyboardTransactionCoordinator` with predictive glide and settled reconciliation. | **Redesigned**: Eliminates entrance stutter and keyboard-open document bouncing. |

---

## 9. Confirmed Duplication and Conflicting Responsibilities

### 1. [CONFIRMED DUPLICATE OWNERSHIP] Vestigial Expansion Compensation Ledger vs. Viewport-Driven Scroll Model
- **Files**: `UnifiedCanvasLayout.kt` (lines 816-842, 995-1029), `ScribeScrollPolicy.kt` (lines 1724-1760), `ScribeBottomSurfaceCoordinator.kt` (lines 350-425).
- **Explanation**: Commit `00b8d58` introduced the dual-value viewport-driven scrolling model (`resolveViewportAdjustment`), which computes temporary displacement statelessly. However, `UnifiedCanvasLayout` still maintains the old `transitionCoordinator.recordExpansionDisplacement()`, `transitionCoordinator.consumeAppliedMovement()`, `contractionRestoredBudgetPx`, and `ExpansionCompensation` ledger.
- **Affected Interactions**: Drawer expansions and contractions, keyboard expansions. Both ledgers are updated simultaneously on every physics step, creating redundant state and potential confusion during maintenance.

### 2. [CONFIRMED DUPLICATE OWNERSHIP] Dual Preserved Dismissal Scroll Properties
- **Files**: `UnifiedCanvasLayout.kt` (lines 71, 82-86), `ScribeBottomSurfaceCoordinator.kt` (lines 198, 280-290).
- **Explanation**: `UnifiedCanvasLayout` maintains both `genericRetainedScroll: Int?` and `transitionCoordinator.preservedScroll: Int?`. In lines 82-86, the getter reads `transitionCoordinator.preservedDismissalScroll ?: genericRetainedScroll`, while the setter writes *only* to `genericRetainedScroll`.
- **Affected Interactions**: Terminal keyboard dismissal, drawer dismissal, and subsequent manual scrolling clearance checks.

### 3. [CONFIRMED COMPETING MOVEMENT PATHS] Tap ACTION_UP vs. Sora ensurePositionVisible
- **Files**: `ScribeCodeEditor.kt` (lines 1550-1560, 1883-1909).
- **Explanation**: When the user taps the editor while the keyboard is active:
  1. Sora's `super.onTouchEvent(event)` detects tap and internally executes `ensurePositionVisible()`, which immediately calls `parentCanvas.ensureCursorVisibleAboveKeyboard(force = true)`.
  2. Then, `ScribeCodeEditor.onTouchEvent` continues to lines 1550-1560 and calls `parentCanvas.ensureCursorVisibleAboveKeyboard(force = false)` again in the same gesture.
- **Resolution in 6585cca**: In `6585cca`, line 1554 was updated to pass `targetLine = null, targetCol = null`, and `startAuthoritativeScrollAnimation` detects `|currTarget - clampedTarget| <= 1px` to return immediately. However, the redundant double-dispatch still exists in the source code path.

### 4. [CONFIRMED STALE-STATE RISK] Decoupled persistentScrollPosition During Direct Canvas Manipulations
- **Files**: `UnifiedCanvasLayout.kt` (lines 2493-2502), `ScribeScrollPolicy.kt` (lines 1501-1512).
- **Explanation**: `UnifiedCanvasLayout.onMeasure()` reconciles `scrollD` and `scrollDFloat` directly via `ScribeScrollPolicy.reconcileScrollState()`. If a layout pass changes `scrollD` or `editor.offsetY` outside of `scrollCanvasBy()`, `scrollCoordinator.persistentScrollPosition` is not updated, creating a potential divergence between `displayedTotalScroll` and the coordinator's ledger.
- **Affected Interactions**: Window size changes, device rotation, split-screen entering/exiting.

---

## 10. Analysis of the Excessive Upward Tap-Scrolling Issue

### Root Cause Analysis
Prior to commit `6585cca`, when a user tapped anywhere in the editor to position the caret, the document would scroll excessively upward, leaving the caret far higher than the shortcut bar instead of resting 24dp above it.

This defect was caused by a combination of two errors:

1. **The Double-Subtraction Defect in `evaluateViewportAdjustment()`**:
   In `MainEditorScreen.kt`, Compose measures `UnifiedCanvasLayout` using:
   $$\text{adjustedHeight} = \text{constraints.maxHeight} - \text{topPaddingPx} - \text{bottomPaddingPx}$$
   Where $\text{bottomPaddingPx}$ already includes the keyboard, drawer, and shortcut bar. Consequently, `UnifiedCanvasLayout.height` ($h$) **already represents the available writing viewport**.
   In `UnifiedCanvasLayout.kt`:
   ```kotlin
   val effectiveBottomObstructionPx = when {
       isDrawerActive -> liveDrawerHeightPx + shortcutBarHeightPx
       isKeyboardVisible -> liveImeHeightPx + shortcutBarHeightPx
       ...
   }
   val usableWritingHeight = (h - effectiveBottomObstructionPx).coerceAtLeast(0f)
   ```
   `evaluateViewportAdjustment()` subtracted the bottom obstruction a **second time**. On a device with $h = 1000\text{px}$ and an $800\text{px}$ keyboard + $100\text{px}$ bar:
   $$\text{usableWritingHeight} = 1000 - 900 = 100\text{px}!$$
   The safe boundary was calculated as $100\text{px} - 24\text{dp} \approx 40\text{px}$ from the top of the editor. Any caret lower than 40px was evaluated as occluded by hundreds of pixels.

2. **Inverted Settlement Flag Causing False Expansion Classification**:
   In `MainEditorScreen.kt`, `wasKeyboardAlreadySettled` was evaluated after `presentationState` had already been changed to `KEYBOARD_SETTLED`. As a result, `wasKeyboardAlreadySettled` was always `true`. This caused `canUpdateSavedStandardBaseline()` to reject standard keyboard heights that differed from the default 270dp fallback. Because the stored baseline remained stuck at 270dp while the physical keyboard was e.g. 300dp, `isBottomSurfaceExpanded()` continuously evaluated to `true`, treating every standard keyboard interaction as an expanded surface and triggering `evaluateViewportAdjustment()`.

### How Commit `6585cca` Resolved This:
- `usableWritingHeight` was changed to `h.coerceAtLeast(0f)` directly (no second subtraction).
- `wasKeyboardAlreadySettled` is now captured *prior* to state reduction, allowing standard keyboards to establish their baseline.
- `ScribeCodeEditor` post-tap visibility now resolves against the committed cursor rather than pre-tap touch estimates.

---

## 11. Prioritized List of Architectural Risks & Recurring Regressions

### Risk 1 (High): Tapping the Last Paragraph Downward/Upward Jump
- **Source**: `UnifiedCanvasLayout.kt` (`predictivelyGlideCursorAboveKeyboard`, lines 2262-2269) & `ScribeScrollPolicy.kt` (`resolveCursorSafety`, lines 188-199).
- **Mechanism**: When tapping near the bottom of a document, if `screenTop < -deadbandPx`, `resolveCursorSafety()` calculates `neededUpwardDisplacement = screenTop` (negative, meaning downward). If an entrance glide or visibility call permits downward correction, the canvas scrolls down. Then, when the keyboard actually resizes the viewport, the caret is suddenly occluded below the new bottom boundary, forcing an immediate upward correction.
- **Why It Recurs**: Viewport reduction and cursor visibility operate asynchronously. Predictive calculations that attempt to resolve negative top overflow before the keyboard arrives fight with bottom obstruction resolution after arrival.

### Risk 2 (High): Tapping Safely Visible Caret Near Maximum Scroll Boundary
- **Source**: `UnifiedCanvasLayout.kt` (lines 2492-2502) & `ScribeCodeEditor.kt` (lines 1566-1568).
- **Mechanism**: When the document is scrolled near the end, `scrollD < headerHeight` while `offsetY > 0` can briefly occur due to touch slop. Line 1566 of `ScribeCodeEditor` enforces the 1-DOF invariant via `jumpScrollTo(offsetX, 0)`. This sudden jump bypasses smooth scrolling and produces an abrupt visual bounce when tapping near the top/bottom boundary.

### Risk 3 (Medium): Stale Pre-Tap Caret Geometry in Entrance Glides
- **Source**: `ScribeCodeEditor.kt` (`pendingTapTargetState`, line 1519) and `UnifiedCanvasLayout.kt` (`notifyKeyboardEntranceStarting`, line 1182).
- **Mechanism**: When tapping to open the keyboard, `pendingTapTargetState` is resolved using `getPointVisualPositionOnScreen(event.x, event.y)` *before* Sora lays out or confirms the caret. If word-wrap, formatting, or inline image hints cause the actual caret to land on a different line, predictive glide aims for the pre-tap estimate until `reconcileActiveTransactionTarget()` catches it.

### Risk 4 (Medium): Parallel Coexistence of Legacy Ledger and Viewport-Driven Scrolling
- **Source**: `UnifiedCanvasLayout.kt` (`scrollCoordinator.onCompensationConsumed`, lines 821-824).
- **Mechanism**: Even though viewport adjustment is now stateless (`resolveViewportAdjustment`), legacy ledger code still updates `transitionCoordinator.compensation`. If any component reads from `transitionCoordinator.compensation.remainingDisplacement`, it may receive stale or misaligned data.

### Risk 5 (Low): Multi-Window and Split-Screen Viewport Baseline Drift
- **Source**: `MainEditorScreen.kt` (`canUpdateSavedStandardBaseline`, line 1526).
- **Mechanism**: In split-screen mode, `isInMultiWindowMode == true` correctly blocks updating the persistent standard keyboard baseline. However, if the user starts the app in split-screen mode and never opens it full-screen, `lastKeyboardHeightDp` remains at the default `DEFAULT_KEYBOARD_DP` (270dp), which may not match actual split-screen geometry.

---

## 12. Recommended Smallest Safe Next Step

Do **not** perform a large-scale refactor or introduce a third scrolling coordinator. The architecture is currently stabilized around the dual-value viewport model (`persistentScrollPosition + temporaryViewportAdjustment`) and spring physics engine.

### Smallest Safe Next Step:
1. **Remove Vestigial Ledger Mutations**: Cleanly deprecate and remove the obsolete `transitionCoordinator.recordExpansionDisplacement()` and `consumeAppliedMovement()` callbacks inside `UnifiedCanvasLayout.kt`, leaving `AuthoritativeScrollCoordinator` as the sole owner of `temporaryViewportAdjustment`.
2. **Consolidate Preserved Dismissal Scroll**: Eliminate `genericRetainedScroll` in `UnifiedCanvasLayout.kt` and standardize on `transitionCoordinator.preservedDismissalScroll` as the single authoritative storage for stationary dismissal positions.
3. **Eliminate Redundant Post-Tap Dispatch**: In `ScribeCodeEditor.onTouchEvent(ACTION_UP)`, remove the redundant call to `pCanvas.ensureCursorVisibleAboveKeyboard()`, relying on Sora's standard `ensurePositionVisible()` dispatch to avoid double-evaluating cursor safety on every tap.
