# Scribe Native — Selection-Deletion Gap Collapse: Phase Execution Index

**Document Role:** Controlled index of phase-specific execution extracts from the frozen master implementation plan.  
**Authoritative Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan (frozen; zero source edits, builds, tests, commits, or pushes executed during planning)  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`  
**Baseline Repository Commit (`main`):** `dfe1d135428d4c49f8390d2b01cf4246416b3857`  
**Sora Editor Dependency:** `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`), `minSdk = 23`  
**Companion Documents Cited by Master Plan:** `codebase_audit_findings.md`

---

## Authority & Controlled-Extract Rules

1. [`implementation_plan.md`](./implementation_plan.md) is the **sole authoritative specification**.
2. Each `phase-*.md` document is a controlled extract for execution ordering and traceability. If any question arises, defer to [`implementation_plan.md`](./implementation_plan.md).
3. All provenance tags (`[Source-verified]`, `[Derived]`, `[Proposed]`, `[Fallback]`) preserve the exact meanings defined in [`implementation_plan.md`](./implementation_plan.md#scribe-native--selection-deletion-gap-collapse-final-architecture-validated-plan).

---

## Phases in Execution Order

| Order | Phase Document | Master Plan Section(s) | Primary Scope | Blocking Gate / Prerequisites |
|---|---|---|---|---|
| **0** | [`phase-0.md`](./phase-0.md) | `Phase 0` (`0.1`–`0.5`), `Sora 0.24.6 Dependency Decision` | Read-only preflight: resolved dependency check (`0.1`), compiled AAR bytecode & `DrawCursorTask` inspection (`0.2`), independent Sora `0.24.6` source provenance (`0.3`), exact Gradle task discovery (`0.4`), and Phase-0 gates (`0.5`). | None (must execute first before code changes). |
| **1** | [`phase-1.md`](./phase-1.md) | `Phase 1` (`1.1`–`1.5`), `Guardrail 4`, `File Ownership Map`, `Testing Strategy (A)` | Pure policy (`ScribeGapCollapsePolicy.kt`), shared overlay bounds registry (`ScribeOverlayBoundsRegistry.kt` + 5 Compose UI registrants), canonical integer safety floor (`[safeMinPhysical, safeMax]`), `(U, L, T)` scenarios, sub-pixel Branches 1–3, Mode-1 $\tau_{\text{lower}}(t)$, observed physical-read-back handoff velocity, closed-form Mode-2 spring math, and JVM Suite A. | **Phase 0 Gate (`0.1`, `0.3`, `0.4`)** completed and verified outputs/task names recorded. |
| **2** | [`phase-2.md`](./phase-2.md) | `Phase 2` (`2.1`–`2.2`), `Guardrail 2`, `File Ownership Map`, `Testing Strategy (A, B1, E)` | Surviving-content Case I / Case II anchor geometry, active-seam baseline initialization, `Content.replace()` event-ordering fix (`!isInContentReplace` guard in `afterDelete`), `AfterInsertAction.ClearTransactionAndClampOverscroll`, universal `tryClaimPendingDeletionSettlement(expectedGen)`, and `pendingReplaceWatchdog` cleanup. | **Phase 0 Gate (`0.1`, `0.3`, `0.4`)** + Phase 1 safety/policy foundations. |
| **3** | [`phase-3.md`](./phase-3.md) | `Phase 3` (`3.1`–`3.4`), `Guardrails 2 & 3`, `File Ownership Map`, `Testing Strategy (B1, B2, D)` | `ScribeGapCollapseController` 5-phase state machine, Mode-2 `Choreographer` loop, sealed Event-B `SafetyRevalidationResult`, atomic teardown-before-`IDLE`, idempotent dual-`ACTION_DOWN` primitives, `cancelPreservingTransaction`, **literal sole coordinator `finishAnimation()` ownership** via `applyStepWithResult`, sub-1px/coalescing/callback lifecycle, pointer dispatch `try/finally`, `PhysicalScrollOrigin`, and JVM Suites B1 & B2. | **Phase 0 Gate (`0.1`, `0.3`, `0.4`)** + Phases 1–2 contracts. |
| **4** | [`phase-4.md`](./phase-4.md) | `Phase 4` (`4.1`–`4.4`), `Guardrails 1 & 4`, `Sora 0.24.6 Dependency Decision`, `File Ownership Map`, `Testing Strategy (B1, C, D, E)` | Single-`super.draw(canvas)` / `drawView(canvas)` `ScribeEditorRenderer`, `ScribeDrawCursorTaskAccessor`, 10-layer Pass 1/Pass 2 split map, Pass-2 `buildMeasureCacheForLines`, explicit 6-case Approach-A `firstLn` rules, Option-B conservative cursor-animation policy (`ScribeCursorAnimator.onAnimatorStarted` + async Event-B fallback), `renderPassRowRangeOverride` reset, deferred `EditorTextActionWindow` refresh gate, and Suite C (`androidTest`). | **Phase 0 Gate (`0.2`)** confirming non-reflective `EditorRenderer$DrawCursorTask.y` access and matching `EditorRenderer` protected signatures in `editor-0.24.6.aar`, plus Phases 1–3 controller/renderer interfaces. |
| **5** | [`phase-5.md`](./phase-5.md) | `Phase 5` (`5.1`–`5.5`), `Guardrail 4`, `File Ownership Map`, `Testing Strategy (B1, D, E, F)` | Touch `untranslateYOffset` & `isTouchOnHandle`, incremental `(seamLine, 0)` rebase rules, Policy A/B internal mutation routing, 8-row mid-collapse operation matrix, width/detach cleanup, serialized height-only `onSizeChanged` with `PendingPostSafetyKeyboardEval`, `scrollDFloat` invariant separation, and Suites D, E, F. | Phases 1–4 implemented (`ScribeGapCollapseController`, `ScribeGapCollapsePolicy`, `ScribeEditorRenderer`, and `UnifiedCanvasLayout`/`ScribeCodeEditor` integration). |
| **6** | [`phase-6.md`](./phase-6.md) / [`phase-6-default-gap-collapse-plan.md`](./phase-6-default-gap-collapse-plan.md) | `Phase 6` | **Default Gap Collapse Promotion & Category A/B/C Remediation** (partial-line seams, Case II caret-line anchoring, `(0,0)` / `TAIL_ANCHOR` / `RESET_TO_TOP` collapse, uncapped Scenario D, `FINAL_SAFETY_PENDING` layout wakeup, `afterDelete` indent floor ordering, `cutText` async guard; `0.24.6` retained). | User-authorized Phase 6 expansion over baseline V1 (`38de5ab`). |

---

## Authoritative Shared Architecture Contracts (Defined in `implementation_plan.md`)

All phase documents reference these contracts rather than redefining them:

1. **Guardrail 1 — Sora Native Rendering Lifecycle, Draw Order, Approach-A Line Numbers, Option-B Cursor-Animation Policy & Synchronous `editor.invalidate()`**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules) (Rule 1) and detailed in Phase 4 (`4.1`–`4.3`).
2. **Guardrail 2 — Dedicated Controller, `Content.replace()` Protection, Universal Settlement Claim, Sealed Event B & Idempotent Dual `ACTION_DOWN` Cancel**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules) (Rule 2) and detailed in Phases 2 & 3 (`2.2`, `3.1`–`3.2`).
3. **Guardrail 3 — Literal Sole Coordinator `finishAnimation()` Ownership, `applyStepWithResult()` Completion Lifecycle, Sub-1px/Coalescing Contract, Physical Velocity Read-Back, Closed-Form Mode 2 & `PhysicalScrollOrigin`**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules) (Rule 3) and detailed in Phases 1 & 3 (`1.5`, `3.3`–`3.4`).
4. **Guardrail 4 — Authoritative Integer-Consistent Safety Floor (`[safeMinPhysical, safeMax]`), Clean `ScribeOverlayBoundsRegistry` Lifecycle, Serialized Height Resize & Deferred `EditorTextActionWindow` Gate**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#plan-guardrails--non-negotiable-architecture-rules) (Rule 4) and detailed in Phases 1, 4 & 5 (`1.2`, `4.4`, `5.5`).
5. **Sora `0.24.6` Dependency Retention & Measure/Preload Contract**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#sora-0246-dependency-decision--measurepreload-reality).
6. **Consolidated File Ownership Map (5 New Production Files, 12 Modified Production Files, 4 Focused Test Files + Existing Regression)**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#consolidated-file-ownership-map-source-verified--proposed).
7. **Testing Strategy & Extended Validation Matrix (A–F)**  
   Defined in [`implementation_plan.md`](./implementation_plan.md#testing-strategy--extended-validation-matrix-af-proposed).

---

## Phase-to-Testing-Matrix Summary

| Validation Suite | Target File(s) | Mapped Phase(s) |
|---|---|---|
| **Suite A** | `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapsePolicyTest.kt` | Primary: **Phase 1**; `onAfterInsert()` assertions also validate **Phase 2**. |
| **Suite B1** | `app/src/test/java/com/primaloptima/scribe/ui/editor/scroll/ScribeGapCollapseControllerTest.kt` | Primary: **Phase 3**; also exercises **Phase 2** (`tryClaimPendingDeletionSettlement`, `!isInContentReplace`), **Phase 4** (Option-B mid-collapse abort), and **Phase 5** (incremental seam & internal mutations). |
| **Suite B2** | `ScribeScrollCoordinatorTest.kt`, `ScribeScrollAnimationTest.kt` | **Phase 3** (sole `finishAnimation()` ownership, `applyStepWithResult`, `requestTarget`, sub-1px/coalescing/retarget, `cancelPreservingTransaction`, Scenario-D zero/sub-1px/exact-1px handoff guards). |
| **Suite C** | `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseRendererTest.kt` | **Phase 4** (two-pass layers, 6-case `firstLn`, `invalidate()`, Option-B safety, `FINAL_SAFETY_PENDING` split-render disable, `EditorTextActionWindow` refresh gate). |
| **Suites D & E** | `app/src/androidTest/java/com/primaloptima/scribe/ui/components/ScribeGapCollapseIntegrationTest.kt` | Cross-phase integration centered in **Phase 5** (pointer dispatch `try/finally`, `ScribeOverlayBoundsRegistry` cleanup from Phase 1, `EditorTextActionWindow` timing from Phase 4, serialized height resize + `scrollDFloat` from Phase 5, `Content.replace()` from Phase 2, Option-B cursor-animator end-to-end from Phase 4, Scenario E). |
| **Suite F** | Existing `*Test.kt` regression suites | **Phase 5** final V1 regression gate. |
