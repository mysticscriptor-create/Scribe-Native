# Phase 0 — Resolved Dependency, Renderer Access & Gradle Task Preflight

- **Master Plan Source:** [`implementation_plan.md`](./implementation_plan.md)
- **Master Plan Version / Status:** Final implementation-readiness plan (frozen)
- **Master Plan SHA-256:** `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`
- **Execution Status:** **COMPLETE (`PASS`)** — Recorded from CI Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724) on `main` @ `fba4f6b670cb4349c89c4aba02b94f4c586a7fa9` (see [`phase-0-handoff-report.md`](./phase-0-handoff-report.md))
- **Authoritative Master Sections:**
- `Sora 0.24.6 Dependency Decision & Measure/Preload Reality`
- `Phase 0 — Resolved Dependency, Renderer Access & Gradle Task Preflight` (`0.1`–`0.5`)
- `Testing Strategy & Extended Validation Matrix (A–F)` (Gradle task-name requirement)

---

## Scope

Perform mandatory read-only environment, dependency, binary AAR bytecode, source-provenance, and Gradle task verification before writing or modifying any production or test source files.

## Prerequisites & Dependencies

- **Original Baseline / Subsequent Preflight Head:** Plan drafted against baseline `main` @ `dfe1d135428d4c49f8390d2b01cf4246416b3857`; diagnostic workflow `.github/workflows/phase0-preflight.yml` executed on `main` @ `fba4f6b670cb4349c89c4aba02b94f4c586a7fa9` (with JDK 21, Android SDK, and Gradle `9.7.1` on GitHub-hosted `ubuntu-24.04`).
- **Blocks:**
  - Steps **`0.1`, `0.3`, and `0.4`** block **Phase 1** start (and therefore Phases 1–3). -> **Gate now `OPEN`** (`0.1`, `0.3`, `0.4` `PASS`).
  - Step **`0.2`** blocks **Phase 4** start (`ScribeEditorRenderer` and `ScribeDrawCursorTaskAccessor`). -> **Gate now `OPEN`** (`0.2` `PASS`).

## Explicit Non-Goals (Preserved & Confirmed)

- Do **not** modify any source files, Gradle configuration files, or tests during Phase 0. *(Confirmed: zero application code, Gradle config, build, compilation, lint, or test executions occurred.)*
- Do **not** upgrade `io.github.rosemoe:editor` from `0.24.6` (master plan retains `0.24.6` unchanged; `0.24.7` upgrade is deferred to [Phase 6](./phase-6.md)). *(Confirmed: `io.github.rosemoe:editor:0.24.6` retained.)*
- Do **not** execute builds/edits before recording the verified Phase 0 outputs required by `0.5`. *(Confirmed: only read-only resolution, `javap -p`, provenance, and task discovery ran.)*

## Shared Architecture Contracts Referenced

- **Sora `0.24.6` Retention Contract:** Defined in [`implementation_plan.md`](./implementation_plan.md#sora-0246-dependency-decision--measurepreload-reality).

---

## Implementation / Verification Steps (Planned vs. Verified Results)

### 0.1 Resolved Dependency Verification (`[Proposed]` -> `[Verified — Run 43687221724: PASS]`)

- **Planned Criterion:**
  - Run `./gradlew :app:dependencyInsight --dependency io.github.rosemoe:editor --configuration debugRuntimeClasspath` (and inspect release/test configurations as applicable).
  - Verify that the resolved dependency is **`io.github.rosemoe:editor:0.24.6`** with no unexpected version substitution or conflict.
- **Verified Result (`PASS` — Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724)):**
  - `debugRuntimeClasspath`, `releaseRuntimeClasspath`, and `debugUnitTestRuntimeClasspath` all resolved **`io.github.rosemoe:editor:0.24.6`** (`releaseVariantReleaseRuntimePublication`, requested AGP `9.1.0`) with zero substitutions or version conflicts.
  - `debugAndroidTestRuntimeClasspath` returned `No dependencies matching given input were found` (recorded as informational; app dependency is supplied via the tested APK's `debugRuntimeClasspath`).
  - Optional `releaseUnitTestRuntimeClasspath` and `releaseAndroidTestRuntimeClasspath` configurations do not exist on `:app` and were skipped.

### 0.2 Compiled AAR Bytecode & `DrawCursorTask` Accessibility Inspection (`[Proposed]` -> `[Verified — Run 43687221724: PASS]`)

- **Planned Criterion:**
  - Locate the resolved `editor-0.24.6.aar` in the local Gradle cache (`find ~/.gradle/caches -name '*editor-0.24.6*.aar'`), extract `classes.jar`, and run `javap -p` on `io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask` and `io.github.rosemoe.sora.widget.EditorRenderer`.
  - Confirm exclusively from the compiled AAR bytecode that:
    1. `EditorRenderer$DrawCursorTask` and its `y` field are directly accessible to package-private `io.github.rosemoe.sora.widget.ScribeDrawCursorTaskAccessor` **without reflection, `setAccessible`, or `Unsafe`**.
    2. Exact `protected` method signatures (`drawRows`, `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawLineNumber`, `drawRowBackground`, `drawSideBlockLine`, `buildMeasureCacheForLines`) match the planned `ScribeEditorRenderer` overrides.
    3. Binary API/signature parity matches the `0.24.6` source reference inspected in `0.3`.
- **Verified Result (`PASS` — Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724)):**
  - Gradle `ArtifactView` (`ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE = "aar"`) on `debugRuntimeClasspath` resolved **1** matching AAR:
    - Coordinates: `io.github.rosemoe:editor:0.24.6` (`releaseVariantReleaseRuntimePublication`)
    - AAR path on CI runner: `/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar`
    - AAR size / SHA-256: `790650` bytes / `fb76ae4db31d94d9fee7f97d9b8ec1c9659c54b5ee1ded7f9f95f7039646243e`
    - Extracted `classes.jar` SHA-256: `9aef25039b4bc9ef2e05e6c45dddfbad02cfc7c47acbf4f9a82ac6071f110318`
  - `javap -p` inspection of `/tmp/sora-editor-0.24.6-classes.jar` verified:
    - `public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask {`
    - `protected float y;`
    - All **9 distinct `protected` `EditorRenderer` method names (11 total signatures counting overloads)**: `drawRows` (with generic `List<EditorRenderer$DrawCursorTask>` and `List<CodeBlock>`), `patchHighlightedDelimiters`, `drawDiagnosticIndicators`, `drawUserGutterBackground`, `drawSideIcons`, `drawLineNumber`, `drawRowBackground` (3-arg and 4-arg overloads), `drawSideBlockLine`, and `buildMeasureCacheForLines` (4-arg and 2-arg overloads). Full verbatim signatures are recorded in [`phase-0-handoff-report.md`](./phase-0-handoff-report.md).
  - **Accessibility vs. Compilation Boundary:** `javap -p` proves bytecode-level accessibility of `EditorRenderer$DrawCursorTask` and `protected float y` for a same-package accessor in `io.github.rosemoe.sora.widget` without reflection, `setAccessible`, or `Unsafe`. **No actual compilation of `ScribeDrawCursorTaskAccessor` or `ScribeEditorRenderer` was performed during Phase 0**; compilation and runtime verification of that accessor occur in Phase 4.

### 0.3 Source-Reference Provenance Verification (`[Proposed]` -> `[Verified: PASS]`)

- **Planned Criterion:**
  - Independently verify the checked-out Sora source reference against the specified `io.github.rosemoe:editor:0.24.6` source commit (`87055c459e346cac6c619b3350f0bfba076228cc`) and version metadata.
  - Record the source artifact's provenance separately, keeping source-reference provenance strictly distinct from the compiled binary AAR / bytecode verification in `0.2`.
- **Verified Result (`PASS` — Recorded separately from `0.2`):**
  - Upstream `https://github.com/Rosemoe/sora-editor` tag `0.24.6` resolves to commit `87055c459e346cac6c619b3350f0bfba076228cc` (`chore: bump version to 0.24.6`), with `Versions.kt` reporting `version = "0.24.6"` / `versionCode = 95` and upstream `EditorRenderer.java` SHA-256 `632879cb83be474ea5e46823d1dfb302d728869773feff8d5105c883ed73d1c7` (verified via read-only Git/raw-GitHub inspection during Phase 0 preflight assessment and summarized separately in Run `43687221724` `PHASE0_SUMMARY.md`).
  - Source provenance is strictly distinct from and did not substitute for compiled AAR bytecode verification in `0.2`.

### 0.4 Exact Gradle Task-Name Discovery (`[Proposed]` -> `[Verified — Run 43687221724: PASS]`)

- **Planned Criterion:**
  - Run `./gradlew :app:tasks --all` and record exact verified Gradle task names for JVM unit tests and Android instrumentation tests/compilation.
  - **Constraint:** All Gradle JVM and instrumentation commands executed during implementation MUST use the exact verified task names recorded here.
- **Verified Result (`PASS` — Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724)):**
  - Executed `./gradlew :app:tasks --all --no-configuration-cache` and verified `FOUND` for:
    - **JVM Unit Tests:** `:app:testDebugUnitTest` (also `FOUND`: `:app:test`, `:app:compileDebugUnitTestKotlin`, `:app:compileDebugUnitTestJavaWithJavac`, `:app:compileDebugUnitTestSources`, `:app:assembleDebugUnitTest`)
    - **Android Instrumentation Tests & Compilation:** `:app:connectedDebugAndroidTest` (also `FOUND`: `:app:connectedAndroidTest`, `:app:compileDebugAndroidTestKotlin`, `:app:compileDebugAndroidTestJavaWithJavac`, `:app:compileDebugAndroidTestSources`, `:app:assembleDebugAndroidTest`, `:app:installDebugAndroidTest`, `:app:uninstallDebugAndroidTest`)

---

## Validation & Phase-Completion Gates (`0.5`)

### Original Gate Criteria (Preserved)
- **Gate Blocking Phase 1 Start:** Steps **`0.1`**, **`0.3`**, and **`0.4`** MUST complete and their verified outputs/task names MUST be recorded before modifying or creating any Phase 1–3 files.
- **Gate Blocking Phase 4 Start:** Step **`0.2`** MUST confirm non-reflective package-private access to `EditorRenderer$DrawCursorTask.y` and matching `EditorRenderer` protected signatures in the resolved `0.24.6` AAR before implementing `ScribeEditorRenderer` or `ScribeDrawCursorTaskAccessor`.
- **Acceptance Criteria:** Zero source edits made; dependency `io.github.rosemoe:editor:0.24.6` verified; source commit `87055c459e346cac6c619b3350f0bfba076228cc` provenance recorded independently; exact Gradle test task names recorded; bytecode confirmation obtained prior to Phase 4.

### Recorded Gate Verdicts (Run [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724))

- **Workflow Run:** `https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724` (`completed` / `success`)
- **Inspected Repository `HEAD`:** `fba4f6b670cb4349c89c4aba02b94f4c586a7fa9` (`main`)
- **Uploaded Diagnostic Artifact:** `scribe-phase0-preflight-fba4f6b670cb4349c89c4aba02b94f4c586a7fa9` (ID `14151170696`, `18,899` bytes, 16 files)
- **Step Verdicts:** `0.1`: **`PASS`** | `0.2`: **`PASS`** | `0.3`: **`PASS`** | `0.4`: **`PASS`**
- **Phase 0.5 Gate — Phases 1–3 (`0.1`, `0.3`, `0.4`):** **`OPEN`**
- **Phase 0.5 Gate — Phase 4 (`0.2`):** **`OPEN`**
- **Build/Test Execution Disclosure:** **Zero application builds, compilation tasks, lint checks, or test executions were performed during Phase 0.**

For the full toolchain snapshot, AAR/JAR SHA-256 hashes, verbatim `javap -p` dumps, full `:app:tasks --all` output, and Phase 1–5 agent handoff instructions, see [`phase-0-handoff-report.md`](./phase-0-handoff-report.md).
