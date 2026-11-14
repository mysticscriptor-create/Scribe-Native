# Phase 0 — Resolved Dependency, Renderer Access & Gradle Task Preflight

**Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)  
**Master Plan Version / Status:** Final implementation-readiness plan  
**Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`  
**Authoritative Master Sections:**
- `Sora 0.24.6 Dependency Decision & Measure/Preload Reality`
- `Phase 0 — Resolved Dependency, Renderer Access & Gradle Task Preflight` (`0.1`–`0.5`)
- `Testing Strategy & Extended Validation Matrix (A–F)` (Gradle task-name requirement)

---

## Scope

Perform mandatory read-only environment, dependency, binary AAR bytecode, source-provenance, and Gradle task verification before writing or modifying any production or test source files.

## Prerequisites & Dependencies

- **Prerequisites:** Clean checkout of `Scribe-Native` (`main` @ `dfe1d135428d4c49f8390d2b01cf4246416b3857`) with JDK 21 and Android SDK / Gradle wrapper available.
- **Blocks:**
  - Steps **`0.1`, `0.3`, and `0.4`** block **Phase 1** start (and therefore Phases 1–3).
  - Step **`0.2`** blocks **Phase 4** start (`ScribeEditorRenderer` and `ScribeDrawCursorTaskAccessor`).

## Explicit Non-Goals

- Do **not** modify any source files, Gradle configuration files, or tests during Phase 0.
- Do **not** upgrade `io.github.rosemoe:editor` from `0.24.6` (master plan retains `0.24.6` unchanged; `0.24.7` upgrade is deferred to [Phase 6](./phase-6.md)).
- Do **not** execute builds/edits before recording the verified Phase 0 outputs required by `0.5`.

## Shared Architecture Contracts Referenced

- **Sora `0.24.6` Retention Contract:** Defined in [`implementation_plan.md`](./implementation_plan.md#sora-0246-dependency-decision--measurepreload-reality).

---

## Implementation / Verification Steps

### 0.1 Resolved Dependency Verification (`[Proposed]`)
- Run:
  ```bash
  ./gradlew :app:dependencyInsight --dependency io.github.rosemoe:editor --configuration debugRuntimeClasspath
  ```
  (and inspect release/test configurations as applicable).
- Verify that the resolved dependency is **`io.github.rosemoe:editor:0.24.6`** with no unexpected version substitution or conflict.

### 0.2 Compiled AAR Bytecode & `DrawCursorTask` Accessibility Inspection (`[Proposed]`)
- Locate the resolved `editor-0.24.6.aar` in the local Gradle cache:
  ```bash
  find ~/.gradle/caches -name '*editor-0.24.6*.aar'
  ```
- Extract `classes.jar`:
  ```bash
  unzip -p <resolved-aar-path> classes.jar > /tmp/sora-editor-0.24.6-classes.jar
  ```
- Inspect compiled bytecode via `javap`:
  ```bash
  javap -p -cp /tmp/sora-editor-0.24.6-classes.jar 'io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask'
  javap -p -cp /tmp/sora-editor-0.24.6-classes.jar 'io.github.rosemoe.sora.widget.EditorRenderer'
  ```
- Confirm exclusively from the compiled AAR bytecode that:
  1. `EditorRenderer$DrawCursorTask` and its `y` field are directly accessible to package-private `io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor` **without reflection, `setAccessible`, or `Unsafe`**.
  2. Exact `protected` method signatures (`drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawLineNumber`, `drawRowBackground`, `drawSideBlockLine`, `buildMeasureCacheForLines`) match the planned `ScribeEditorRenderer` overrides.
  3. Binary API/signature parity matches the `0.24.6` source reference inspected in `0.3`.

### 0.3 Source-Reference Provenance Verification (`[Proposed]`)
- Independently verify the checked-out Sora source reference against the specified `io.github.rosemoe:editor:0.24.6` source commit (`87055c459e346cac6c619b3350f0bfba076228cc`) and version metadata.
- Record the source artifact's provenance separately, keeping source-reference provenance strictly distinct from the compiled binary AAR / bytecode verification in `0.2`.

### 0.4 Exact Gradle Task-Name Discovery (`[Proposed]`)
- Run:
  ```bash
  ./gradlew :app:tasks --all
  ```
- Record the exact verified Gradle task names for:
  - JVM unit tests (e.g., `:app:testDebugUnitTest`),
  - Android instrumentation tests (e.g., `:app:connectedDebugAndroidTest` / compilation tasks).
- **Constraint:** All Gradle JVM and instrumentation commands executed during implementation MUST use the exact verified task names recorded here.

---

## Validation & Phase-Completion Gates (`0.5`)

- **Gate Blocking Phase 1 Start:** Steps **`0.1`**, **`0.3`**, and **`0.4`** MUST complete and their verified outputs/task names MUST be recorded before modifying or creating any Phase 1–3 files.
- **Gate Blocking Phase 4 Start:** Step **`0.2`** MUST confirm non-reflective package-private access to `EditorRenderer$DrawCursorTask.y` and matching `EditorRenderer` protected signatures in the resolved `0.24.6` AAR before implementing `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor`.
- **Acceptance Criteria:** Zero source edits made; dependency `io.github.rosemoe:editor:0.24.6` verified; source commit `87055c459e346cac6c619b3350f0bfba076228cc` provenance recorded independently; exact Gradle test task names recorded; bytecode confirmation obtained prior to Phase 4.
