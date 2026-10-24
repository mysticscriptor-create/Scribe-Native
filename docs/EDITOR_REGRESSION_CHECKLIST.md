# Scribe Editor Regression Checklist

This checklist tracks verified invariants, risk areas, and regression scenarios across scrolling, viewport coordination, keyboard/accessory transitions, and caret positioning in Scribe.

### Verification Tiers
- **Tier 1: Automated Unit / Policy**: Pure mathematical invariants, coordinate ordering, 1-DOF allocation, and `AuthoritativeScrollCoordinator` lifecycle tested via deterministic JUnit 4 suites in `CursorAndCanvasGeometryTest.kt`.
- **Tier 2: Component Architecture Integration**: Single-owner coordination in `UnifiedCanvasLayout.kt` managing `activeCanvasAnimator`, Compose layout measurement frames, Sora Editor touch events, and debounced frame coalescing.
- **Tier 3: Physical Device Interactive QA**: Manual test procedures executed on physical hardware against live keyboard IMEs (e.g. Gboard) and 60-120Hz display refresh rates.

---

## 1. 1-DOF (1 Degree of Freedom) Scroll Coordination

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1DOF-01** | Header partially visible (`scrollD < headerHeight`) | All vertical scroll belongs strictly to outer canvas (`scrollD`). Sora Editor internal offset `offsetY` MUST remain exactly 0. Content on line 0 is never drawn behind titles. | Tier 1 (Unit) | Verified | `test1DOF_ForwardScroll_OnlyOuterConsumesWhileHeaderVisible` |
| **1DOF-02** | Forward handoff boundary (`scrollD == headerHeight`) | Once header is fully hidden, subsequent upward scroll delta transitions cleanly to editor `offsetY` with 0px jump or loss. | Tier 1 (Unit) | Verified | `test1DOF_ForwardScroll_HandoffSplitAcrossBoundary` |
| **1DOF-03** | Reverse handoff (`scrollD == headerHeight`, `offsetY > 0`) | Downward scroll consumes against editor `offsetY` first. Canvas header does NOT reveal until `offsetY == 0`. | Tier 1 (Unit) | Verified | `test1DOF_ReverseScroll_OnlyInnerConsumesUntilNeutral` |
| **1DOF-04** | State reconciliation | Reconciling any out-of-order scroll state strictly enforces: if `totalScroll < headerHeight`, `scrollD = totalScroll` and `offsetY = 0`. | Tier 1 (Unit) | Verified | `test1DOF_ReconcileScrollState_HeaderPartiallyVisible_ForcesEditorToZero` |
| **1DOF-05** | Safe scroll bounds clamp | `calculateSafeScrollMaxY` strictly returns 0 while header is visible (`isHeaderFullyHidden = false`). | Tier 1 (Unit) | Verified | `test1DOF_SafeScrollMaxY_ReturnsZeroWhenHeaderIsVisible` |

---

## 2. Keyboard & Accessory Expansion / Contraction (All Caret Positions)

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **EXP-01** | Mid-document caret (e.g. Line 50 of 100) | When emoji picker (>330dp) or accessory drawer expands and then contracts back to normal keyboard height, canvas smoothly restores the exact displacement down. | Tier 1 (Unit) | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **EXP-02** | Early lines / Line 2 | Restoring displacement clamps to `-totalScroll` so canvas never scrolls past document top. Remaining compensation is tracked without overflow. | Tier 1 (Unit) | Verified | `testContractionRestoration_EarlyLine_ClampsToTotalScroll` |
| **EXP-03** | Second-to-last and third-to-last lines | Caret resting on penultimate lines restores downward smoothly without clipping or getting stranded high up. | Tier 1 (Unit) | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **EXP-04** | Last line of document | Last line smoothly anchors to the shortcut bar with exact 24dp breathing margin. | Tier 1 (Unit) | Verified | `testContractionRestoration_LastLine_AnchorsToBreathingMarginWhenZeroDisplacement` |
| **EXP-05** | Mid-document without prior expansion | When editing in middle of document without prior expansion displacement, contraction produces 0f delta (canvas remains stationary). | Tier 1 (Unit) | Verified | `testContractionRestoration_MiddleOfDocument_WithoutDisplacement_RemainsStationary` |

---

## 3. Safe Bottom Boundary & Breathing Margin

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SAFE-01** | Breathing margin preservation (24dp) | Active line always settles with `CURSOR_BOTTOM_MARGIN_DP` (24dp) clearance above shortcut bar / keyboard surface. | Tier 1 (Unit) | Verified | `testContractionRestoration_MiddleOfDocument_RestoresTemporaryDisplacement` |
| **SAFE-02** | Strict safe-boundary enforcement | `slack < 0f` is strictly unsafe; caret NEVER settles even 1px below breathing boundary. Calculated corrective upward delta restores clearance. | Tier 1 (Unit) | Verified | `testAuthoritativeCoordinator_StrictSafeBoundary_SlackNegativeIsAlwaysUnsafe` |
| **SAFE-03** | Deadband hysteresis (4dp) | Micro-movements within `CURSOR_DEADBAND_DP` (4dp) above breathing line are suppressed to prevent sub-pixel animation jitter. | Tier 1 (Unit) | Verified | `testContractionRestoration_SafeBoundaryClamping_NeverSettlesBehindShortcutBar` |

---

## 4. Pure Writer Invariant (Full Keyboard Dismissal)

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PWR-01** | Complete keyboard dismissal (`imeAboveNav -> 0dp`) | When soft keyboard dismisses to 0dp, automatic downward scroll is STRICTLY prohibited (`deltaY = 0f`). Document canvas remains rock-solid stationary at saved margin. | Tier 1 (Unit) | Verified | `testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta` |
| **PWR-02** | Dismissal from expanded state | Dismissing keyboard directly while emoji picker or accessory is open still strictly keeps canvas stationary at saved margin. | Tier 1 (Unit) | Verified | `testBottomSurfaceCoordinator_AllowsDownwardFollow_WhenTemporaryDisplacementActive` |
| **PWR-03** | Inactive bottom surface | If Compose reports bottom bar is inactive, downward follow returns 0f. | Tier 1 (Unit) | Verified | `testContractionRestoration_FullKeyboardDismissal_StrictlyReturnsZeroDelta` |

---

## 5. User Interaction Precedence & Invalidation

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test / Implementation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **USR-01** | User touch down (`ACTION_DOWN`) | Any active or pending compensation displacement is immediately invalidated (`0f`). Manual dragging takes absolute precedence. | Tier 1 & Tier 2 | Verified | `testAuthoritativeCoordinator_UserTouch_InvalidatesAllCompensation` & `UnifiedCanvasLayout.dispatchTouchEvent` |
| **USR-02** | Caret line/column change | If user moves cursor or taps a different line or column while expanded, compensation is invalidated (`invalidateCompensationIfCaretChanged`). | Tier 1 & Tier 2 | Verified | `testAuthoritativeCoordinator_CaretChange_InvalidatesCompensation` & `ScribeCodeEditor.SelectionChangeEvent` |
| **USR-03** | Text content edited | Editing text (`ContentChangeEvent`) invalidates pending expansion compensation. | Tier 1 & Tier 2 | Verified | `ScribeCodeEditor.ContentChangeEvent` |
| **USR-04** | Tap on last line | Tapping last line with keyboard already open does NOT pull canvas downward. | Tier 1 (Unit) | Verified | `testTapOnLastLineDoesNotAnchorDownward` |

---

## 6. Contraction Animation & Authoritative Retargeting

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ANIM-01** | Repeated intermediate viewport changes | During 60-120fps drawer collapse, intermediate layout frames coalesce through debounced 48ms handler. Does NOT launch competing animations or overshoot. | Tier 1 & Tier 2 | Verified | `testAuthoritativeCoordinator_CoalescesIntermediateCloseTargets` & `UnifiedCanvasLayout.onSizeChanged` |
| **ANIM-02** | Retargeting active animation in flight | When new target arrives while animation is in flight, smoothly retargets from current actual scroll without velocity restart or applying cumulative deltas. | Tier 1 (Unit) | Verified | `testAuthoritativeCoordinator_SmoothRetargetingInFlight_NoVelocityReset` |
| **ANIM-03** | Interrupted/cancelled restoration accounting | When animation is interrupted or cancelled, compensation bookkeeping reconciles with the EXACT movement applied tick-by-tick. | Tier 1 (Unit) | Verified | `testContractionSimulation_AnimationCancellation_ReconcilesActualAppliedMovement` |
| **ANIM-04** | Causal expansion tracking | Upward movement adds to compensation ONLY during active expansion transition via caret visibility/glide. Touch and edits invalidate cleanly. | Tier 1 (Unit) | Verified | `testAuthoritativeCoordinator_CausalExpansionTracking` |
| **ANIM-05** | Repeated expand/collapse cycles | Rapid expand/collapse cycles cannot accumulate displacement or restore the same movement more than once. Remaining displacement strictly resets to 0f. | Tier 1 (Unit) | Verified | `testContractionSimulation_RepeatedExpandCollapseCycles_NoAccumulatedDisplacement` |

---

## 7. Caret Coordinate & Selection Geometry Verification

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **GEOM-01** | Verified Sora Editor return ordering | Pinned Sora Editor dependency contract verified: `getCharLayoutOffset(line, column)` returns `[bottomDocY, leftDocX]`. Never confuse vertical Y with horizontal X. | Tier 1 (Unit) | Verified | `testContraction_VerifiedCoordinateOrdering_SelectionBounds` |
| **GEOM-02** | Strongly typed `SoraCharGeometry` | Coordinate abstraction guarantees `bottomDocY`, `topDocY`, and `leftDocX` are explicitly named and cannot be transposed. | Tier 2 (Integration) | Verified | `UnifiedCanvasLayout.SoraCharGeometry` & `getCharGeometry()` |
| **GEOM-03** | Multi-line selection bounding | Selection geometry takes `minOf(leftTop, rightTop)` and `maxOf(leftBottom, rightBottom)` so full selection height is safely respected during contraction. | Tier 1 (Unit) | Verified | `testContraction_VerifiedCoordinateOrdering_SelectionBounds` |
| **GEOM-04** | Corrective upward scroll if initially unsafe | If caret is initially behind safe boundary (`slack < 0f`), policy calculates corrective upward delta rather than doing nothing. | Tier 1 (Unit) | Verified | `testContraction_CaretInitiallyBeyondSafeBoundary_CalculatesCorrectiveUpward` |
| **GEOM-05** | Final target safety validation | Proposed target scroll is validated before animation; if unsafe, target is clamped so final caret position strictly respects the 24dp breathing margin. | Tier 1 (Unit) | Verified | `testContraction_TargetValidation_ClampsUnsafeTarget` |

---

## 8. Transition-Specific Expansion & Contraction Semantics

| Case ID | Verification Scenario | Scope / Invariant | Verification Tier | Status | Automated Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TRN-01** | Expanded keyboard -> normal keyboard | Emoji keyboard (>330dp) returns to standard height: restores expansion displacement subject to 24dp breathing margin. | Tier 1 (Unit) | Verified | `testContraction_KeyboardExpansion_FollowedByNormalHeightRestoration` |
| **TRN-02** | Expanded accessory -> normal accessory | Full accessory drawer collapses to compact accessory bar: restores expansion displacement subject to safe boundary. | Tier 1 (Unit) | Verified | `testContraction_AccessoryDrawerExpansion_FollowedByNormalHeightRestoration` |
| **TRN-03** | Selection change during expanded state | Extending or moving selection while expanded invalidates compensation; subsequent collapse remains stationary. | Tier 1 (Unit) | Verified | `testContraction_SelectionChangeDuringTransition_InvalidatesStaleCompensation` |
| **TRN-04** | Manual touch/drag during expansion | Touching or dragging canvas cancels active restoration and zeroes compensation. | Tier 1 (Unit) | Verified | `testContraction_ManualScrollDuringExpansion_InvalidatesCompensation` |
| **TRN-05** | Partial restoration retains only safe compensation | When downward restoration is clamped by available slack, remaining displacement is rebased against settled slack so no stale displacement lingers. | Tier 1 (Unit) | Verified | `testContraction_PartialRestoration_RetainsOnlySafelyAppliableCompensation` |
| **TRN-06** | 1-DOF invariant throughout restoration | Downward restoration consumes against inner editor `offsetY` down to 0 before outer canvas `scrollD` reveals the header. Zero content drawn behind header. | Tier 1 (Unit) | Verified | `testContraction_Preserves1DOFInvariant_DuringRestorationSteps` |

---

## 9. Physical Device Interactive QA Matrix

*Note: These scenarios represent manual QA protocols for execution on physical hardware with live IMEs. Automated unit and lifecycle integration tests pass in CI; on-device QA verifies visual fluidness, real tactile response, and device-specific IME edge cases.*

| Scenario ID | Test Procedure | Expected Outcome | Verification Tier | QA Status |
| :--- | :--- | :--- | :--- | :--- |
| **DEV-01** | Expand accessory drawer to FULL, then tap drag handle or collapse to COMPACT. | Document settles smoothly; active line has >= 24dp breathing clearance above shortcut bar. Zero overshoot. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-02** | Switch to Gboard Emoji palette (>330dp), then switch back to ABC keyboard. | Canvas smoothly restores temporary upward displacement, settling caret >= 24dp above keyboard. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-03** | Repeat DEV-01 and DEV-02 with caret on: (a) last line, (b) second-to-last line, (c) third-to-last line, (d) middle line. | All positions settle safely with >= 24dp breathing clearance. Last line anchors to breathing margin. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-04** | Rapidly toggle accessory drawer 5 times in quick succession. | No animation fighting, no cumulative downward creep, document settles cleanly. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-05** | In expanded drawer state, manually scroll document or move caret, then collapse. | Document does NOT restore old displacement; stays at user's new intended position. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-06** | Complete keyboard dismissal (back button or swipe down). | Pure Writer invariant: document remains 100% stationary at saved margin. Zero downward scroll. | Tier 3 (Physical Device) | Manual QA Protocol |
| **DEV-07** | Partial header visibility scroll handoff. | 1-DOF invariant: editor offsetY remains 0 until header is 100% hidden. Reverse scroll neutralizes editor first. | Tier 3 (Physical Device) | Manual QA Protocol |
