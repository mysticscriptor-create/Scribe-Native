# Scribe Editor Regression Checklist

This checklist tracks verified invariants, risk areas, and regression scenarios across scrolling, viewport coordination, keyboard/accessory transitions, and caret positioning in Scribe.

---

## 1. 1-DOF (1 Degree of Freedom) Scroll Coordination

| Case ID | Verification Scenario | Scope / Invariant | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- |
| **1DOF-01** | Header partially visible (`scrollD < headerHeight`) | All vertical scroll belongs strictly to outer canvas (`scrollD`). Sora Editor internal offset `offsetY` MUST remain exactly 0. Content on line 0 is never drawn behind titles. | Verified | `test1DOF_ForwardScroll_OnlyOuterConsumesWhileHeaderVisible` |
| **1DOF-02** | Forward handoff boundary (`scrollD == headerHeight`) | Once header is fully hidden, subsequent upward scroll delta transitions cleanly to editor `offsetY` with 0px jump or loss. | Verified | `test1DOF_ForwardScroll_HandoffSplitAcrossBoundary` |
| **1DOF-03** | Reverse handoff (`scrollD == headerHeight`, `offsetY > 0`) | Downward scroll consumes against editor `offsetY` first. Canvas header does NOT reveal until `offsetY == 0`. | Verified | `test1DOF_ReverseScroll_OnlyInnerConsumesUntilNeutral` |
| **1DOF-04** | State reconciliation | Reconciling any out-of-order scroll state strictly enforces: if `totalScroll < headerHeight`, `scrollD = totalScroll` and `offsetY = 0`. | Verified | `test1DOF_ReconcileScrollState_HeaderPartiallyVisible_ForcesEditorToZero` |
| **1DOF-05** | Safe scroll bounds clamp | `calculateSafeScrollMaxY` strictly returns 0 while header is visible (`isHeaderFullyHidden = false`). | Verified | `test1DOF_SafeScrollMaxY_ReturnsZeroWhenHeaderIsVisible` |

---

## 2. Keyboard & Accessory Expansion / Contraction (All Caret Positions)

| Case ID | Verification Scenario | Scope / Invariant | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- |
| **EXP-01** | Mid-document caret (e.g. Line 50 of 100) | When emoji picker (>330dp) or accessory drawer expands and then contracts back to normal keyboard height, canvas smoothly restores the exact displacement down. | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **EXP-02** | Early lines / Line 2 | Restoring displacement clamps to `-totalScroll` so canvas never scrolls past document top. Remaining compensation is tracked without overflow. | Verified | `testContractionRestoration_EarlyLine_ClampsToTotalScroll` |
| **EXP-03** | Second-to-last and third-to-last lines | Caret resting on penultimate lines restores downward smoothly without clipping or getting stranded high up. | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **EXP-04** | Last line of document | Last line smoothly anchors to the shortcut bar with exact 24dp breathing margin. | Verified | `testContractionRestoration_LastLine_AnchorsToBreathingMarginWhenZeroDisplacement` |
| **EXP-05** | Mid-document without prior expansion | When editing in middle of document without prior expansion displacement, contraction produces 0f delta (canvas remains stationary). | Verified | `testContractionRestoration_MiddleOfDocument_WithoutDisplacement_RemainsStationary` |

---

## 3. Safe Bottom Boundary & Breathing Margin

| Case ID | Verification Scenario | Scope / Invariant | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- |
| **SAFE-01** | Breathing margin preservation (24dp) | Active line always settles with `CURSOR_BOTTOM_MARGIN_DP` (24dp) clearance above shortcut bar / keyboard surface. | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **SAFE-02** | Never settle behind shortcut bar | When restoring downward, if slack to breathing line is less than displacement, downward delta is clamped to `-slack`. Caret NEVER crosses below safe boundary. | Verified | `testContractionRestoration_SafeBoundaryClamping_NeverSettlesBehindShortcutBar` |
| **SAFE-03** | Deadband hysteresis (4dp) | Micro-movements within `CURSOR_DEADBAND_DP` (4dp) are suppressed to prevent sub-pixel animation jitter and scroll fighting. | Verified | `testContractionRestoration_SafeBoundaryClamping_NeverSettlesBehindShortcutBar` |

---

## 4. Pure Writer Invariant (Full Keyboard Dismissal)

| Case ID | Verification Scenario | Scope / Invariant | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- |
| **PWR-01** | Complete keyboard dismissal (`imeAboveNav -> 0dp`) | When soft keyboard dismisses to 0dp, automatic downward scroll is STRICTLY prohibited (`deltaY = 0f`). Document canvas remains rock-solid stationary at saved margin. | Verified | `testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta` |
| **PWR-02** | Dismissal from expanded state | Dismissing keyboard directly while emoji picker or accessory is open still strictly keeps canvas stationary at saved margin. | Verified | `testBottomSurfaceCoordinator_AllowsDownwardFollow_WhenTemporaryDisplacementActive` |
| **PWR-03** | Inactive bottom surface | If Compose reports bottom bar is inactive, downward follow returns 0f. | Verified | `testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta` |

---

## 5. User Interaction Precedence & Invalidation

| Case ID | Verification Scenario | Scope / Invariant | Status | Automated Test / Implementation |
| :--- | :--- | :--- | :--- | :--- |
| **USR-01** | User touch down (`ACTION_DOWN`) | Any active or pending compensation displacement is immediately invalidated (`0f`). Manual dragging takes absolute precedence. | Verified | `UnifiedCanvasLayout.dispatchTouchEvent` |
| **USR-02** | Caret line change | If user moves cursor or taps a different line while expanded, previous line compensation is invalidated (`invalidateCompensationIfLineChanged`). | Verified | `ScribeCodeEditor.SelectionChangeEvent` |
| **USR-03** | Text content edited | Editing text (`ContentChangeEvent`) invalidates pending expansion compensation. | Verified | `ScribeCodeEditor.ContentChangeEvent` |
| **USR-04** | Tap on last line | Tapping last line with keyboard already open does NOT pull canvas downward. | Verified | `testTapOnLastLineDoesNotAnchorDownward` |
