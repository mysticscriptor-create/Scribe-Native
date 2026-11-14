# Phase 1 — Pure Policy, Shared Overlay Registry Lifecycle, Single Safety Floor & Exact Equations (`ScribeGapCollapsePolicy`)

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Plan Guardrails & Non-Negotiable Architecture Rules` (Guardrails 3 & 4)
- `Consolidated File Ownership Map`
- `Phase 1 — Pure Policy, Shared Overlay Registry Lifecycle, Single Safety Floor & Exact Equations (ScribeGapCollapsePolicy)` (`1.1`–`1.5`)
- `Testing Strategy & Extended Validation Matrix` (`A`)

---

## Scope

Implement stateless gap-collapse math and classification policy (`ScribeGapCollapsePolicy.kt`), the shared window-relative overlay occlusion registry (`ScribeOverlayBoundsRegistry.kt`) plus its Compose UI registrants, the single canonical integer physical-safety floor model (`resolveSafetyFloorAndBounds`), scenario-specific Mode-1 $\tau_{\text{lower}}(t)$ and Mode-2 velocity/closed-form equations, `usableBottomForSafety` support on `validateSettledDeletionSafety`, and pure JVM unit tests (**Suite A**).

## Prerequisites & Dependencies

- **Hard Prerequisite ([Phase 0](./phase-0.md#validation--phase-completion-gates-05)):** Phase 0 Steps **`0.1`**, **`0.3`**, and **`0.4`** MUST be completed and verified outputs/task names recorded before creating or modifying any Phase 1 file. *(Phase `0.2` is NOT required to start Phase 1.)*
- **Depended On By:** Phases 2, 3, 4, and 5 consume `ScribeGapCollapsePolicy`, `ScribeOverlayBoundsRegistry`, `UnobstructedViewportBounds`, `VisibilityConfidence`, `CollapseScenario`, `CollapsePlan`, and `resolveSafetyFloorAndBounds`.

## Explicit Non-Goals

- Do **not** implement `ScribeGapCollapseController` state-machine transitions, `Choreographer` loop, or `ScribeScrollCoordinator` / `ScribeScrollAnimation` refactors in this phase (deferred to [Phase 3](./phase-3.md)).
- Do **not** implement `Content.replace()` / `afterDelete` / `afterInsert` event-ordering changes (deferred to [Phase 2](./phase-2.md)) — *note: Suite A unit-test coverage of `ScribeDeletionSnapshotCoordinator.onAfterInsert()` expects the Phase 2 `ClearTransactionAndClampOverscroll` update to be complete before that specific test case passes*.
- Do **not** implement `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor` (deferred to [Phase 4](./phase-4.md), gated by Phase `0.2`).

## Shared Architecture Contracts Referenced

- **Guardrail 3 (Mode-2 Handoff Velocity Read-Back & Closed-Form Monotonicity):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **Guardrail 4 (Canonical Integer Safety Floor & Overlay Lifecycle):** [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules)
- **File Ownership:** [`implementation_plan.md`](./implementation_plan.md#consolidated-file-ownership-map-source-verified--proposed)

---

## File Ownership in Phase 1

### New Files
1. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicy.kt`  
   Symbols: `ScribeGapCollapsePolicy`, `CollapseScenario`, `CollapsePlan`, `UnobstructedViewportBounds`, `VisibilityConfidence`, `SeamMappingResult`.  
   Strictly pure, stateless math and policy (zero Android View lifecycle state).
2. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeOverlayBoundsRegistry.kt`  
   Symbols: `ScribeOverlayBoundsRegistry`, `OverlayOcclusionRole`, `LocalOverlayBoundsRegistry`.

### Modified Files (Phase 1 Scope)
1. `app/src/main/java/com/primaloptima/scribe/ui/screens/MainEditorScreen.kt`: Creates and provides `ScribeOverlayBoundsRegistry` via `LocalOverlayBoundsRegistry` wrapping `CompactEditorLayout` and `ExpandedEditorLayout`; registers `FindReplaceBar`, top banners, and indent indicator using `isVisible && coords.isAttached` + key-scoped `DisposableEffect` cleanup. *(Policy-B pre-mutation cancel wiring in `MainEditorScreen.kt` is covered in [Phase 5](./phase-5.md).)*
2. `app/src/main/java/com/primaloptima/scribe/ui/screens/CompactEditorLayout.kt`: Registers `shortcut_bar` (`BOTTOM_BAR`) and `word_count_pill` (`FLOATING_PILL`) via `boundsInWindow()` using `isVisible && coords.isAttached` + `DisposableEffect` cleanup.
3. `app/src/main/java/com/primaloptima/scribe/ui/screens/ExpandedEditorLayout.kt`: Registers side/bottom dock or shortcut overlay bounds via `boundsInWindow()` using `isVisible && coords.isAttached` + `DisposableEffect` cleanup.
4. `app/src/main/java/com/primaloptima/scribe/ui/components/FloatingWindowOverlay.kt`: Registers floating window card (`FLOATING_PILL`) and active modal scrims (`MODAL_BLOCKER`) using `isVisible && coords.isAttached` + `DisposableEffect` cleanup.
5. `app/src/main/java/com/primaloptima/scribe/ui/components/EditorImagePreviewCard.kt`: Registers floating preview card (`FLOATING_PILL`) using `isVisible && coords.isAttached` + `DisposableEffect` cleanup.
6. `app/src/main/java/com/primaloptima/scribe/ui/editor/scroll/ScribeScrollDeletionPolicy.kt`: Adds optional `usableBottomForSafety: Float = usableViewportHeight.toFloat()` parameter on `validateSettledDeletionSafety` (retaining existing `RESET_TO_TOP`, `TAIL_ANCHOR`, `CARET_ANCHOR` classification).
7. `app/src/main/java/com/primaloptima/scribe/ui/components/UnifiedCanvasLayout.kt`: Computes `UnobstructedViewportBounds` + `resolveSafetyFloorAndBounds` consuming `LocalOverlayBoundsRegistry`.

### Test File
- `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicyTest.kt` (**Suite A**)

---

## Implementation Steps & Exact Specifications

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
   Verified in `UnifiedCanvasLayout.kt:129-139`: `maxCanvasScroll` (`maxCanvasScrollPost`) is an `Int`, and Android View scroll offsets (`scrollD: Int` + `editor.offsetY: Int`) make physical scroll read-back integer-valued (`Int`). Define:
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
Android View offsets (`scrollD: Int` + `editor.offsetY: Int`) make `actualPhysicalScroll = scrollD + editor.offsetY` strictly **integer-valued (`Int`)**. Transient `S_actual` (for example immediately after a deletion lowers `maxCanvasScrollPost`, or before settlement/clamping executes) **can temporarily lie outside `[safeMinPhysical, safeMax]`** (`S_actual < safeMinPhysical` or `S_actual > safeMax`). Once settled by Mode 1 / Event B, `actualPhysicalScroll` MUST lie in integer **`[safeMinPhysical, safeMax]`**. Consequences:
- Because `S_pre` and `safeMax` are both integers, **`S_pre > safeMax` implies `S_pre - safeMax >= 1` always** (a `< 1 px` violation above `safeMax` is impossible).
- Therefore, **`S_pre !in minSafeScroll..safeMax && 0f < abs(S_effectiveTarget - S_pre.toFloat()) < 1f` (fractional sub-1px floor crossing) occurs ONLY when `minSafeScroll` has a fractional part just above integer `S_pre`**: `S_pre < minSafeScroll < S_pre + 1` (in which case `safeMinPhysical = ceil(minSafeScroll).toInt() = S_pre + 1`).

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
     *(Numerical reference: `S_pre = 100`, `minSafeScroll = 100.4f`, `safeMax = 900`, `ΔG = 12f`: `safeMinPhysical = ceil(100.4).toInt() = 101`, `S_settled0 = 101`, `δS = +1.0 px`; B/C sets `tauLower(0) = 13f -> ΔY_lower(0) = 0.0 px`, Mode 2 settles `13f -> 0f`; A sets `tauLower(0) = 12f -> ΔY_upper = -1.0 px, ΔY_lower(0) = -1.0 px`, Mode 2 settles `12f -> 0f`; settled `actualPhysicalScroll = 101 >= safeMinPhysical`.)*
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
   - Track `prevActualScroll`, `prevFrameTimeNs`, and `lastValidPreBoundaryPhysVelPxPerSec: Float?` during Mode 1.
   - On each unclamped Mode-1 frame where `observedDelta = stepResult.actualTotalScroll - prevActualScroll` is non-zero (`!stepResult.boundaryHit && abs(observedDelta) >= 0.5f && dtSec > 0f`): update smoothed physical velocity $v_{\text{phys}} = \text{ema}(\text{observedDelta} / \text{dtSec})$ ($\alpha = 0.65\text{f}$ over consecutive steps to reject single-frame 1px integer quantization jitter).
   - If the final boundary frame consumes less than requested (`stepResult.boundaryHit` or `abs(observedDelta) < 0.5f`), retain `lastValidPreBoundaryPhysVelPxPerSec` directly.
   - **Zero/Sub-1px Span & Inactive Mode-1 Handoff Guard (No Division by Zero / No NaN):**
     - Progress-coupled (`A-MinSafeClamped`, `D`):
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
     - Lower-anchor (`B`, `C`, `A-MaxClamped`):
       ```kotlin
       val tauDotHandoff =
           if (!mode1ActuallyStarted || vPhys == null) 0f else vPhys
       ```
     - Safe monotonic clamp & fallback (all scenarios):
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

4. **Exact Closed-Form Mode-2 Equation (`[Derived]` / `[Proposed]`):**
   Accumulates `elapsedTimeSec` using critically damped parameters ($\omega = 13.5\text{ rad/s}, \zeta = 1.0, v_0 \in [0, \omega]$):
   ```kotlin
   // Tick 1 (lastFrameTimeNs == 0L): dt = (1f / refreshRateHz).coerceIn(0.001f, 0.032f)
   // Tick k > 1: dt = ((frameTimeNanos - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
   lastFrameTimeNs = frameTimeNanos
   elapsedTimeSec += dt
   val wt = state.omega * elapsedTimeSec
   val residualFraction = kotlin.math.exp(-wt) * (1f + (state.omega - state.progressVelocity) * elapsedTimeSec)
   val nextTau = (state.tauAtResumePx * residualFraction).coerceIn(0f, state.tauAtResumePx)
   ```
   Since $\frac{d}{dt}[e^{-\omega t}(1 + (\omega - v_0)t)] = -e^{-\omega t}[v_0 + \omega(\omega - v_0)t] \le 0$ for all $t \ge 0$ iff $v_0 \in [0, \omega]$, every tick is **provably strictly non-increasing with zero overshoot** regardless of discrete `dt`. Settle when `nextTau <= 0.5f`.

---

## Validation & Acceptance Criteria (Suite A — `ScribeGapCollapsePolicyTest.kt`)

Execute via the exact JVM unit-test Gradle task recorded in Phase `0.4`:
1. `(U, L, T)` truth table + `!isCursorAnimationRunning` gate; Scenario A (`S_pre < minSafeScroll` `< 1px` snap vs `>= 1px` Mode 1, `S_pre in safeMinPhysical..safeMax`, `S_pre > safeMax`).
2. Canonical integer `safeMinPhysical` & transient overscroll assertions (distinguishing requested target `S_requestedTarget` / `S_effectiveTarget` from observed integer read-back `actualPhysicalScroll = scrollD + editor.offsetY`, never reporting safe if `actualPhysicalScroll !in safeMinPhysical..safeMax`):
   - `(minSafeScroll = 100.4f, S_pre = 100) -> 101`
   - `(minSafeScroll = 100.9f, S_pre = 100) -> 101`
   - `(minSafeScroll = 101.0f, S_pre = 100) -> 101`
   - `(minSafeScroll = 101.3f, S_pre = 100) -> 102`
   - `(minSafeScroll = 101.3f, S_pre = 101) -> 102`
   - `(minSafeScroll = 101.3f, S_pre = 102) -> 102`
   - Transient `S_actual > safeMax` settled to `safeMax` via Mode 1 / Event B (`SAFE_BOUNDARY_CORRECTION`).
3. Single safety floor (`TOP_BAR` + `BOTTOM_BAR` vs `FLOATING_PILL`).
4. Lower-anchor Mode-1 $\tau_{\text{lower}}(t)$ + positive/zero/negative shortfall proofs; Scenario-specific handoff velocity + closed-form Mode-2 monotonicity across arbitrary discrete ticks; integer-consistent Branch 1/2/3 tests; zero/sub-1px handoff guard tests:
   - Scenario D with `S_effectiveTarget == S_pre`.
   - Scenario D with a nonzero physical span `< 1px`.
   - Scenario D with a Mode-1 span of exactly `1px`.
   - Scenario D with a normal Mode-1 span.
   - Synchronous safety snap followed by Mode 2.
   - All resulting `progressVelocity` values finite and within `[0f, omega2]`.
5. `ScribeDeletionSnapshotCoordinator.onAfterInsert()` tests (`ClearTransaction`, `ClearTransactionAndClampOverscroll` — never `None` for overscrolled non-empty replace, `SettleEmptyReplaceDelete`) — *note: requires [Phase 2](./phase-2.md) `AfterInsertAction.ClearTransactionAndClampOverscroll` change to pass*.

## Phase-Completion Conditions

- `ScribeGapCollapsePolicy.kt` and `ScribeOverlayBoundsRegistry.kt` compile cleanly with zero Android-View state inside `ScribeGapCollapsePolicy`.
- All 5 overlay screens/components register and clean up bounds strictly under `isVisible && coords.isAttached` + `DisposableEffect`.
- Suite A policy tests pass using the Phase `0.4` Gradle task.
