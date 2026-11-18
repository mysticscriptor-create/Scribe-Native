# Phase 6 — Deferred V2 Scope

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Sora 0.24.6 Dependency Decision & Measure/Preload Reality`
- `Phase 6 — Deferred V2 Scope`

---

## Scope

Documents capabilities explicitly deferred out of V1 into V2. **None of these items are part of V1 implementation.**

## Prerequisites & Dependencies

- V1 completion (Phases 0–5 and Validation Matrix A–F) should be completed and stable before considering any V2 scope expansion.

## Explicit Non-Goals (For V1)

Every item below is a **non-goal for V1** and MUST NOT be implemented during Phases 0–5:

1. **Multi-seam / multi-region renderer splitting** (`[Proposed]` V2).
2. **Merged partial-line horizontal splitting (`C1 > 0 && C2 < preLineLen(L2)`)** (`[Proposed]` V2 — V1 strictly routes `C1 > 0` Case-I seams to `INELIGIBLE_FALLBACK` per [Phase 2 (`2.1`)](./phase-2.md#21-surviving-content-anchor-geometry-source-verified--proposed)).
3. **Optional `io.github.rosemoe:editor:0.24.7` upgrade** (`[Proposed]` V2 — V1 strictly retains `io.github.rosemoe:editor:0.24.6` unchanged per [Phase 0](./phase-0.md)).

## Shared Architecture Contracts Referenced

- **Sora `0.24.6` Dependency Retention:** [`implementation_plan.md`](./implementation_plan.md#sora-0246-dependency-decision--measurepreload-reality)
- **V1 Eligibility Gate (`!isMergedPartialRowSeam`):** [`implementation_plan.md`](./implementation_plan.md#13-complete-u-l-t-truth-table--unified-scenario-a-safety-enforcement-derived--proposed--fallback)

---

## Validation & Completion Conditions

- No V1 validation suite tests V2 deferred items.
- V1 completion requires verifying that `C1 > 0 && C2 < preLineLen(L2)` cleanly delegates to `[Fallback]` (`INELIGIBLE_FALLBACK`) and `io.github.rosemoe:editor` remains pinned at `0.24.6`.


---

## User-Authorized Phase 6 Remediation Plan

See [`phase-6-default-gap-collapse-plan.md`](./phase-6-default-gap-collapse-plan.md) for the authorized Category A/B/C remediation plan promoting selection-deletion gap collapse to default behavior while retaining `io.github.rosemoe:editor:0.24.6` (`87055c459e346cac6c619b3350f0bfba076228cc`).
