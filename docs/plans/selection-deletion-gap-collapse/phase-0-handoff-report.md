# Phase 0 Handoff Report & Preflight Evidence — Scribe Native

- **Authoritative Master Plan:** [`implementation_plan.md`](./implementation_plan.md) (**frozen**, SHA-256: `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`)
- **Authoritative Phase Document:** [`phase-0.md`](./phase-0.md)
- **Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`
- **Branch / Inspected `HEAD`:** `main` @ `fba4f6b670cb4349c89c4aba02b94f4c586a7fa9`
- **Diagnostic Workflow:** `.github/workflows/phase0-preflight.yml`
- **Diagnostic Workflow Run:** [`43687221724`](https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724) (`completed` / **`success`**)
- **Workflow Artifact:** `scribe-phase0-preflight-fba4f6b670cb4349c89c4aba02b94f4c586a7fa9` (Artifact ID `14151170696`, `18,899` bytes, 16 files)
- **Target Dependency:** `io.github.rosemoe:editor:0.24.6` (Sora source reference commit `87055c459e346cac6c619b3350f0bfba076228cc`)

> **Execution Scope Disclosure:** **No application builds, compilation tasks, lint checks, or test executions were performed during Phase 0.** All findings below come strictly from read-only Gradle dependency resolution, compiled AAR bytecode inspection (`javap -p`), read-only Gradle `:app:tasks --all` discovery, and separately recorded upstream Git source-reference provenance. Nothing is claimed as verified beyond this supplied evidence.

---

## 1. Executive Gate Evaluation (`0.5`)

| Step | Check | Evidence Verdict | Gate Impact | Notes & Scope Boundary |
|---|---|---|---|---|
| **0.1** | Resolved Dependency Verification | **PASS** | Gates Phases 1–3 | `debugRuntimeClasspath`, `releaseRuntimeClasspath`, and `debugUnitTestRuntimeClasspath` all resolve `io.github.rosemoe:editor:0.24.6` (`releaseVariantReleaseRuntimePublication`, AGP `9.1.0`) with zero substitutions or conflicts. `debugAndroidTestRuntimeClasspath` reports no direct `editor` match (informational; app dependency provided via tested APK `debugRuntimeClasspath`). Absent optional configs (`releaseUnitTestRuntimeClasspath`, `releaseAndroidTestRuntimeClasspath`) were skipped. |
| **0.2** | Compiled AAR Bytecode Inspection (`javap -p`) | **PASS** | Gates Phase 4 | Gradle `ArtifactView` (`ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE = "aar"`) on `debugRuntimeClasspath` resolved **1** raw AAR (`io.github.rosemoe:editor:0.24.6`). `javap -p` confirmed `public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask`, `protected float y;`, and exact `protected` signatures for **9 distinct method names (11 total signatures counting overloads)**. Confirms **bytecode-level accessibility** for a same-package accessor without reflection, `setAccessible`, or `Unsafe`; **no compilation of `ScribeDrawCursorTaskAccessor` was performed during Phase 0**. |
| **0.3** | Sora Source-Reference Provenance | **PASS** | Gates Phases 1–3 | Upstream `Rosemoe/sora-editor` tag `0.24.6` resolves to `87055c459e346cac6c619b3350f0bfba076228cc` (`chore: bump version to 0.24.6`, `Versions.kt`: `version = "0.24.6"`, `versionCode = 95`, `EditorRenderer.java` SHA-256 `632879cb83be474ea5e46823d1dfb302d728869773feff8d5105c883ed73d1c7`). Kept strictly separate from `0.2` compiled bytecode evidence; did not substitute for `0.2`. |
| **0.4** | Exact Gradle Task Discovery | **PASS** | Gates Phases 1–3 | `./gradlew :app:tasks --all --no-configuration-cache` verified `FOUND` for `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:compileDebugAndroidTestKotlin`, `:app:compileDebugAndroidTestJavaWithJavac`, `:app:compileDebugUnitTestKotlin`, and `:app:compileDebugUnitTestJavaWithJavac`. |

### Final Phase-0 Gate Verdicts (`0.5`)
- **Phases 1–3 Gate (`0.1`, `0.3`, `0.4`):** **`OPEN`**
- **Phase 4 Gate (`0.2`):** **`OPEN`**

---

## 2. Verified Toolchain, Artifacts & Method Signatures

### Toolchain & Environment Snapshot (CI Runner `ubuntu-24.04`)
- **OS / Kernel:** `Linux runnervm8axu0 7.0.0-1017-azure #17~24.04.1-Ubuntu SMP PREEMPT Thu Sep 24 18:46:57 UTC 2026 x86_64 GNU/Linux`
- **JDK / `javap`:** Eclipse Temurin `21.0.13+9-LTS` (`javap 21.0.13`)
- **Gradle Wrapper:** Gradle `9.7.1` (embedded Kotlin `2.4.0`, Groovy `4.0.32`)
- **AGP / Target Profile Observed in `dependencyInsight`:** AGP `9.1.0`, `org.gradle.jvm.environment = android`, `org.jetbrains.kotlin.platform.type = androidJvm`
- **Gradle Execution Mode Used in Preflight:** `--no-configuration-cache` (read-only tasks only)

### Resolved Sora AAR & Hashes (`0.2` — Compiled Binary Evidence)
- **Coordinates:** `io.github.rosemoe:editor:0.24.6`
- **Published Variant:** `releaseVariantReleaseRuntimePublication` (`artifactType=aar`, `org.gradle.category=library`, `org.gradle.dependency.bundling=external`, `org.gradle.libraryelements=aar`, `org.gradle.status=release`, `org.gradle.usage=java-runtime`)
- **Resolved Gradle Cache Path (on CI runner):**
  `/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar`
- **Resolved AAR Size / SHA-256:** `790650` bytes / `fb76ae4db31d94d9fee7f97d9b8ec1c9659c54b5ee1ded7f9f95f7039646243e`
- **Extracted `classes.jar` SHA-256:** `9aef25039b4bc9ef2e05e6c45dddfbad02cfc7c47acbf4f9a82ac6071f110318`

### Verified `javap -p` Accessibility & Method Signatures (`0.2`)
- **Nested Task Class & Field:**
  - `public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask {`
  - `protected float y;`
  - **Bytecode Accessibility vs. Compilation Note:** This establishes from compiled bytecode that a class compiled in package `io.github.rosemoe.sora.widget` (`ScribeDrawCursorTaskAccessor`) can access `EditorRenderer$DrawCursorTask.y` directly without reflection, `setAccessible`, or `Unsafe`. **Actual compilation of `ScribeDrawCursorTaskAccessor` was NOT performed in Phase 0** and will occur for the first time in Phase 4.
- **9 Distinct `protected` `EditorRenderer` Method Names / 11 Total Signatures (Counting Overloads):**
  1. `drawRows`: `protected void drawRows(android.graphics.Canvas, float, io.github.rosemoe.sora.util.LongArrayList, java.util.List<io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask>, androidx.collection.MutableIntList, io.github.rosemoe.sora.util.MutableInt, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>);`
  2. `patchHighlightedDelimiters`: `protected void patchHighlightedDelimiters(android.graphics.Canvas, float);`
  3. `drawDiagnosticIndicators`: `protected void drawDiagnosticIndicators(android.graphics.Canvas, float);`
  4. `drawUserGutterBackground`: `protected void drawUserGutterBackground(android.graphics.Canvas, int);`
  5. `drawSideIcons`: `protected void drawSideIcons(android.graphics.Canvas, float);`
  6. `drawLineNumber`: `protected void drawLineNumber(android.graphics.Canvas, int, int, float, float, int);`
  7. `drawRowBackground` (2 overloads):
     - `protected void drawRowBackground(android.graphics.Canvas, int, int);` *(3-arg)*
     - `protected void drawRowBackground(android.graphics.Canvas, int, int, int);` *(4-arg)*
  8. `drawSideBlockLine`: `protected void drawSideBlockLine(android.graphics.Canvas);`
  9. `buildMeasureCacheForLines` (2 overloads):
     - `protected void buildMeasureCacheForLines(int, int, long, boolean);` *(4-arg)*
     - `protected void buildMeasureCacheForLines(int, int);` *(2-arg)*

### Separate Upstream Source Provenance (`0.3` — Distinct from `0.2`)
- **Upstream Repository:** `https://github.com/Rosemoe/sora-editor`
- **Tag `0.24.6` Commit:** `87055c459e346cac6c619b3350f0bfba076228cc` (`chore: bump version to 0.24.6`)
- **Version Metadata (`Versions.kt`):** `version = "0.24.6"`, `versionCode = 95`
- **Upstream `EditorRenderer.java` SHA-256:** `632879cb83be474ea5e46823d1dfb302d728869773feff8d5105c883ed73d1c7`
- **Method Separation Note:** Upstream source provenance (`0.3`) was verified via read-only Git/raw-GitHub inspection during Phase 0 preflight and summarized separately in `PHASE0_SUMMARY.md`; it was never substituted for compiled AAR bytecode verification (`0.2`).

### Exact Verified Gradle Task Names (`0.4`)
- **JVM Unit Tests & Compilation:**
  - `:app:testDebugUnitTest`
  - `:app:test`
  - `:app:compileDebugUnitTestKotlin`
  - `:app:compileDebugUnitTestJavaWithJavac`
  - `:app:compileDebugUnitTestSources`
  - `:app:assembleDebugUnitTest`
- **Android Instrumentation Tests & Compilation:**
  - `:app:connectedDebugAndroidTest`
  - `:app:connectedAndroidTest`
  - `:app:compileDebugAndroidTestKotlin`
  - `:app:compileDebugAndroidTestJavaWithJavac`
  - `:app:compileDebugAndroidTestSources`
  - `:app:assembleDebugAndroidTest`
  - `:app:installDebugAndroidTest`
  - `:app:uninstallDebugAndroidTest`

---

## 3. Instructions for Subsequent Implementation Agents (Phases 1–5)

1. **Implementation Authority & Frozen Master Plan:**
   - Follow [`implementation_plan.md`](./implementation_plan.md) as the sole implementation specification (frozen SHA-256 `c667de445f39ca769ce0bca4176b29ad902da29b98e260d48dd6adb8f1dc6806`) alongside the phase extract assigned to you ([`phase-1.md`](./phase-1.md)–[`phase-5.md`](./phase-5.md)).
   - Do **not** modify `implementation_plan.md` or upgrade `io.github.rosemoe:editor` from `0.24.6` (deferred V2 scope in [`phase-6.md`](./phase-6.md)).
2. **Verified Gradle Commands (Required by Phase `0.4`):**
   - Execute JVM unit tests (Suites A, B1, B2, F unit suites) using `:app:testDebugUnitTest` (or targeted `--tests` filters on `:app:testDebugUnitTest`).
   - Compile Android instrumentation sources using `:app:compileDebugAndroidTestKotlin` / `:app:compileDebugAndroidTestJavaWithJavac` / `:app:assembleDebugAndroidTest`, and run Suites C, D, E on a device or emulator using `:app:connectedDebugAndroidTest`.
3. **Phase 4 Accessor & Renderer Requirement (`0.2`):**
   - Implement `ScribeDrawCursorTaskAccessor` in package `io.github.rosemoe.sora.widget` using direct package access to `EditorRenderer$DrawCursorTask.y` (no reflection, `setAccessible`, or `Unsafe`).
   - Verify actual compilation of `ScribeDrawCursorTaskAccessor` and `ScribeEditorRenderer` (matching the 9 method names / 11 signatures recorded above) during Phase 4 builds and Suite C tests, since Phase 0 only inspected compiled dependency bytecode.
4. **Environment Note:**
   - Ephemeral agent containers without JDK 21 / Android SDK / emulator cannot execute Gradle or instrumentation locally; `.github/workflows/build.yml` runs on GitHub-hosted `ubuntu-24.04` with Temurin JDK 21, and `connectedDebugAndroidTest` requires an attached Android device or emulator environment.

---

## 4. Verbatim Extracted Diagnostic Files (Run `43687221724`)


### File 1: `PHASE0_SUMMARY.md`
````markdown
# Phase 0 Preflight Diagnostic Summary

- **Repository:** `https://github.com/mysticscriptor-create/Scribe-Native.git`
- **Commit SHA:** `fba4f6b670cb4349c89c4aba02b94f4c586a7fa9`
- **Workflow Run:** `https://github.com/mysticscriptor-create/Scribe-Native/actions/runs/43687221724`

## Step Statuses
- **0.1 — Resolved Dependency Verification:** **PASS**
- **0.2 — Compiled AAR Bytecode Inspection (`editor-0.24.6.aar` + `javap -p`):** **PASS**
- **0.3 — Sora Source-Reference Provenance (`87055c459e346cac6c619b3350f0bfba076228cc`):** **PASS** *(recorded separately; does not substitute for 0.2)*
- **0.4 — Exact Gradle Task Discovery (`:app:tasks --all`):** **PASS**

## Independent Phase Gates (0.5)
- **Phases 1–3 Gate (`0.1`, `0.3`, `0.4`):** **OPEN**
- **Phase 4 Gate (`0.2`):** **OPEN**

## 0.1 Dependency Insight Summary
- `debugRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `releaseRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `debugUnitTestRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `debugAndroidTestRuntimeClasspath` [INFORMATIONAL]: No independent match (app dependency provided via tested APK debugRuntimeClasspath)
- [SKIPPED OPTIONAL]: Configuration not present on :app, skipping: releaseUnitTestRuntimeClasspath
- [SKIPPED OPTIONAL]: Configuration not present on :app, skipping: releaseAndroidTestRuntimeClasspath

## 0.2 Resolved AAR Metadata & SHA-256
```text
> Task :app:phase0ResolveSoraAar
MATCHING_ARTIFACT_COUNT=1
CANDIDATE[0]=io.github.rosemoe:editor:0.24.6 | variant=io.github.rosemoe:editor:0.24.6 variant releaseVariantReleaseRuntimePublication | attrs={artifactType=aar, org.gradle.category=library, org.gradle.dependency.bundling=external, org.gradle.libraryelements=aar, org.gradle.status=release, org.gradle.usage=java-runtime} | file=/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar
RESOLVED_GROUP=io.github.rosemoe
RESOLVED_MODULE=editor
RESOLVED_VERSION=0.24.6
RESOLVED_COORDINATES=io.github.rosemoe:editor:0.24.6
RESOLVED_ARTIFACT_TYPE=aar
RESOLVED_VARIANT=io.github.rosemoe:editor:0.24.6 variant releaseVariantReleaseRuntimePublication
RESOLVED_VARIANT_ATTRIBUTES={artifactType=aar, org.gradle.category=library, org.gradle.dependency.bundling=external, org.gradle.libraryelements=aar, org.gradle.status=release, org.gradle.usage=java-runtime}
RESOLVED_AAR_PATH=/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar
RESOLVED_AAR_SIZE_BYTES=790650
RESOLVED_AAR_SHA256=fb76ae4db31d94d9fee7f97d9b8ec1c9659c54b5ee1ded7f9f95f7039646243e
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step02-1794725443520.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
CLASSES_JAR_PATH=/tmp/sora-editor-0.24.6-classes.jar
CLASSES_JAR_SHA256=9aef25039b4bc9ef2e05e6c45dddfbad02cfc7c47acbf4f9a82ac6071f110318
```

## 0.2 Compiled `javap -p` Accessibility & Signature Verification
- DrawCursorTask class: `public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask {`
- DrawCursorTask.y field: `protected float y;` (direct same-package access in io.github.rosemoe.sora.widget without reflection, setAccessible, or Unsafe confirmed)
- `drawRows`: `protected void drawRows(android.graphics.Canvas, float, io.github.rosemoe.sora.util.LongArrayList, java.util.List<io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask>, androidx.collection.MutableIntList, io.github.rosemoe.sora.util.MutableInt, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>);`
- `patchHighlightedDelimiters`: `protected void patchHighlightedDelimiters(android.graphics.Canvas, float);`
- `drawDiagnosticIndicators`: `protected void drawDiagnosticIndicators(android.graphics.Canvas, float);`
- `drawUserGutterBackground`: `protected void drawUserGutterBackground(android.graphics.Canvas, int);`
- `drawSideIcons`: `protected void drawSideIcons(android.graphics.Canvas, float);`
- `drawLineNumber`: `protected void drawLineNumber(android.graphics.Canvas, int, int, float, float, int);`
- `drawRowBackground(3-arg)`: `protected void drawRowBackground(android.graphics.Canvas, int, int);`
- `drawRowBackground(4-arg)`: `protected void drawRowBackground(android.graphics.Canvas, int, int, int);`
- `drawSideBlockLine`: `protected void drawSideBlockLine(android.graphics.Canvas);`
- `buildMeasureCacheForLines(4-arg)`: `protected void buildMeasureCacheForLines(int, int, long, boolean);`
- `buildMeasureCacheForLines(2-arg)`: `protected void buildMeasureCacheForLines(int, int);`

STEP_0_2_STATUS=PASS

## 0.3 Source Provenance Summary
- Upstream repository: `https://github.com/Rosemoe/sora-editor`
- Tag `0.24.6` commit: `87055c459e346cac6c619b3350f0bfba076228cc` (`chore: bump version to 0.24.6`, `Versions.kt`: `version = "0.24.6"`, `versionCode = 95`)

## 0.4 Discovered Unit & Instrumentation Tasks
```text
assembleAndroidTest - Assembles all the Test applications.
assembleUnitTest - Assembles all the unit test applications.
compileDebugAndroidTestSources
compileDebugUnitTestSources
installDebugAndroidTest - Installs the android (on device) tests for the Debug build.
uninstallDebugAndroidTest - Uninstalls the android (on device) tests for the Debug build.
connectedAndroidTest - Installs and runs instrumentation tests for all flavors on connected devices.
connectedDebugAndroidTest - Installs and runs the tests for debug on connected devices.
deviceAndroidTest - Installs and runs instrumentation tests using all Device Providers.
testDebugUnitTest - Run unit tests for the debug build.
analyzeDebugAndroidTestDependencies
analyzeDebugUnitTestDependencies
assembleDebugAndroidTest - Assembles main output for variant debugAndroidTest
assembleDebugUnitTest - Assembles main output for variant debugUnitTest
checkDebugAndroidTestAarMetadata
checkDebugAndroidTestDuplicateClasses
compileDebugAndroidTestJavaWithJavac
compileDebugAndroidTestKotlin
compileDebugAndroidTestNavigationResources
compileDebugUnitTestJavaWithJavac
compileDebugUnitTestKotlin
compressDebugAndroidTestAssets
createDebugAndroidTestApkListingFileRedirect
desugarDebugAndroidTestFileDependencies
dexBuilderDebugAndroidTest
generateDebugAndroidTestAssets
generateDebugAndroidTestBuildConfig
generateDebugAndroidTestLintModel
generateDebugAndroidTestResources
generateDebugAndroidTestRFile
generateDebugAndroidTestSources
generateDebugUnitTestAssets
generateDebugUnitTestLintModel
generateDebugUnitTestResources
generateDebugUnitTestSources
javaPreCompileDebugAndroidTest
javaPreCompileDebugUnitTest
kspDebugAndroidTestKotlin
kspDebugUnitTestKotlin
lintAnalyzeDebugAndroidTest - Run lint analysis on the debugAndroidTest component
lintAnalyzeDebugUnitTest - Run lint analysis on the debugUnitTest component
mapDebugAndroidTestSourceSetPaths
mergeDebugAndroidTestAssets
mergeDebugAndroidTestGeneratedProguardFiles
mergeDebugAndroidTestGlobalSynthetics
mergeDebugAndroidTestJavaResource
mergeDebugAndroidTestJniLibFolders
mergeDebugAndroidTestNativeLibs
mergeDebugAndroidTestResources
mergeExtDexDebugAndroidTest
mergeLibDexDebugAndroidTest
mergeProjectDexDebugAndroidTest
packageDebugAndroidTest
packageDebugAndroidTestResources
parseDebugAndroidTestLocalResources
preDebugAndroidTestBuild
preDebugUnitTestBuild
processDebugAndroidTestJavaRes
processDebugAndroidTestManifest
processDebugAndroidTestNavigationResources
processDebugAndroidTestResources
processDebugUnitTestJavaRes
signingConfigWriterDebugAndroidTest
stripDebugAndroidTestDebugSymbols
validateSigningDebugAndroidTest
writeDebugAndroidTestSigningConfigVersions

Required task presence check:
  - testDebugUnitTest: FOUND
  - connectedDebugAndroidTest: FOUND
  - compileDebugAndroidTestKotlin: FOUND
  - compileDebugAndroidTestJavaWithJavac: FOUND
  - compileDebugUnitTestKotlin: FOUND
  - compileDebugUnitTestJavaWithJavac: FOUND
```

## Environment & Tooling Snapshot
```text
=== Timestamp (UTC) ===
2026-11-15T06:50:20Z
=== Repository HEAD ===
fba4f6b670cb4349c89c4aba02b94f4c586a7fa9
=== OS / Kernel ===
Linux runnervm8axu0 7.0.0-1017-azure #17~24.04.1-Ubuntu SMP PREEMPT Thu Sep 24 18:46:57 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
=== Java / javap ===
openjdk version "21.0.13" 2026-10-20 LTS
OpenJDK Runtime Environment Temurin-21.0.13+9 (build 21.0.13+9-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.13+9 (build 21.0.13+9-LTS, mixed mode, sharing)
/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.13-9/x64/bin/javap
21.0.13
=== Gradle Wrapper ===

Welcome to Gradle 9.7.1!

Here are the highlights of this release:
 - Isolated Projects graduates to incubating
 - Broader Configuration Cache compatibility
 - Resilient Sync helps you fix broken builds
 - More source locations in problem reports

For more details see https://docs.gradle.org/9.7.1/release-notes.html


------------------------------------------------------------
Gradle 9.7.1
------------------------------------------------------------

Build time:    2026-08-19 14:16:09 UTC
Revision:      92f0512e7f06d84621afba191f75e265363890cf

Kotlin:        2.4.0
Groovy:        4.0.32
Ant:           Apache Ant(TM) version 1.10.17 compiled on April 6 2026
Launcher JVM:  21.0.13 (Eclipse Adoptium 21.0.13+9-LTS)
Daemon JVM:    /usr/lib/jvm/temurin-21-jdk-amd64 (no Daemon JVM specified, using current Java home)
OS:            Linux 7.0.0-1017-azure amd64
```
````

### File 2: `00-environment.txt`
```text
=== Timestamp (UTC) ===
2026-11-15T06:50:20Z
=== Repository HEAD ===
fba4f6b670cb4349c89c4aba02b94f4c586a7fa9
=== OS / Kernel ===
Linux runnervm8axu0 7.0.0-1017-azure #17~24.04.1-Ubuntu SMP PREEMPT Thu Sep 24 18:46:57 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
=== Java / javap ===
openjdk version "21.0.13" 2026-10-20 LTS
OpenJDK Runtime Environment Temurin-21.0.13+9 (build 21.0.13+9-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.13+9 (build 21.0.13+9-LTS, mixed mode, sharing)
/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.13-9/x64/bin/javap
21.0.13
=== Gradle Wrapper ===

Welcome to Gradle 9.7.1!

Here are the highlights of this release:
 - Isolated Projects graduates to incubating
 - Broader Configuration Cache compatibility
 - Resilient Sync helps you fix broken builds
 - More source locations in problem reports

For more details see https://docs.gradle.org/9.7.1/release-notes.html


------------------------------------------------------------
Gradle 9.7.1
------------------------------------------------------------

Build time:    2026-08-19 14:16:09 UTC
Revision:      92f0512e7f06d84621afba191f75e265363890cf

Kotlin:        2.4.0
Groovy:        4.0.32
Ant:           Apache Ant(TM) version 1.10.17 compiled on April 6 2026
Launcher JVM:  21.0.13 (Eclipse Adoptium 21.0.13+9-LTS)
Daemon JVM:    /usr/lib/jvm/temurin-21-jdk-amd64 (no Daemon JVM specified, using current Java home)
OS:            Linux 7.0.0-1017-azure amd64
```

### File 3: `01-configurations.txt`
```text
Starting a Gradle Daemon (subsequent builds will be faster)

> Task :app:phase0ListConfigurations
RESOLVABLE_CONFIG=_agp_internal_debugAndroidTest_kspClasspath
RESOLVABLE_CONFIG=_agp_internal_debugUnitTest_kspClasspath
RESOLVABLE_CONFIG=_agp_internal_debug_kspClasspath
RESOLVABLE_CONFIG=_agp_internal_release_kspClasspath
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-device-provider-ddmlib
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-driver-instrumentation
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-additional-test-output
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-apk-installer
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-coverage
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-device-info
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-emulator-control
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-host-logcat
RESOLVABLE_CONFIG=_internal-unified-test-platform-android-test-plugin-result-listener-gradle
RESOLVABLE_CONFIG=_internal-unified-test-platform-core
RESOLVABLE_CONFIG=_internal-unified-test-platform-gradle-work-action
RESOLVABLE_CONFIG=_internal-unified-test-platform-launcher
RESOLVABLE_CONFIG=androidJacocoAnt
RESOLVABLE_CONFIG=androidJdkImage
RESOLVABLE_CONFIG=androidTestUtil
RESOLVABLE_CONFIG=composeMappingProducerClasspath
RESOLVABLE_CONFIG=coreLibraryDesugaring
RESOLVABLE_CONFIG=debugAndroidTestAnnotationProcessorClasspath
RESOLVABLE_CONFIG=debugAndroidTestCompileClasspath
RESOLVABLE_CONFIG=debugAndroidTestImplementationDependenciesMetadata
RESOLVABLE_CONFIG=debugAndroidTestLintChecksClasspath
RESOLVABLE_CONFIG=debugAndroidTestRuntimeClasspath
RESOLVABLE_CONFIG=debugAnnotationProcessorClasspath
RESOLVABLE_CONFIG=debugCompileClasspath
RESOLVABLE_CONFIG=debugImplementationDependenciesMetadata
RESOLVABLE_CONFIG=debugLintChecksClasspath
RESOLVABLE_CONFIG=debugReverseMetadataValues
RESOLVABLE_CONFIG=debugRuntimeClasspath
RESOLVABLE_CONFIG=debugUnitTestAnnotationProcessorClasspath
RESOLVABLE_CONFIG=debugUnitTestCompileClasspath
RESOLVABLE_CONFIG=debugUnitTestImplementationDependenciesMetadata
RESOLVABLE_CONFIG=debugUnitTestLintChecksClasspath
RESOLVABLE_CONFIG=debugUnitTestRuntimeClasspath
RESOLVABLE_CONFIG=kotlin-extension
RESOLVABLE_CONFIG=kotlinAbiValidationCompatClasspath
RESOLVABLE_CONFIG=kotlinBuildToolsApiClasspath
RESOLVABLE_CONFIG=kotlinCompilerClasspath
RESOLVABLE_CONFIG=kotlinCompilerPluginClasspath
RESOLVABLE_CONFIG=kotlinCompilerPluginClasspathDebug
RESOLVABLE_CONFIG=kotlinCompilerPluginClasspathDebugAndroidTest
RESOLVABLE_CONFIG=kotlinCompilerPluginClasspathDebugUnitTest
RESOLVABLE_CONFIG=kotlinCompilerPluginClasspathRelease
RESOLVABLE_CONFIG=kspDebugAndroidTestKotlinProcessorClasspath
RESOLVABLE_CONFIG=kspDebugKotlinProcessorClasspath
RESOLVABLE_CONFIG=kspDebugUnitTestKotlinProcessorClasspath
RESOLVABLE_CONFIG=kspPluginClasspath
RESOLVABLE_CONFIG=kspPluginClasspathNonEmbeddable
RESOLVABLE_CONFIG=kspReleaseKotlinProcessorClasspath
RESOLVABLE_CONFIG=lintChecks
RESOLVABLE_CONFIG=lintPublish
RESOLVABLE_CONFIG=releaseAnnotationProcessorClasspath
RESOLVABLE_CONFIG=releaseCompileClasspath
RESOLVABLE_CONFIG=releaseImplementationDependenciesMetadata
RESOLVABLE_CONFIG=releaseLintChecksClasspath
RESOLVABLE_CONFIG=releaseReverseMetadataValues
RESOLVABLE_CONFIG=releaseRuntimeClasspath
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step01-1794725428479.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 14s
1 actionable task: 1 executed
```

### File 4: `01-skipped-configurations.txt`
```text
Configuration not present on :app, skipping: releaseUnitTestRuntimeClasspath
Configuration not present on :app, skipping: releaseAndroidTestRuntimeClasspath
```

### File 5: `01-summary.txt`
```text
- `debugRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `releaseRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `debugUnitTestRuntimeClasspath` [REQUIRED - PASS]: ['io.github.rosemoe:editor:0.24.6', 'io.github.rosemoe:editor:0.24.6']
- `debugAndroidTestRuntimeClasspath` [INFORMATIONAL]: No independent match (app dependency provided via tested APK debugRuntimeClasspath)
- [SKIPPED OPTIONAL]: Configuration not present on :app, skipping: releaseUnitTestRuntimeClasspath
- [SKIPPED OPTIONAL]: Configuration not present on :app, skipping: releaseAndroidTestRuntimeClasspath
```

### File 6: `01-dependencyInsight-debugRuntimeClasspath.txt`
```text
> Task :app:dependencyInsight
io.github.rosemoe:editor:0.24.6
  Variant releaseVariantReleaseRuntimePublication:
    | Attribute Name                                  | Provided     | Requested    |
    |-------------------------------------------------|--------------|--------------|
    | org.gradle.dependency.bundling                  | external     |              |
    | org.gradle.libraryelements                      | aar          |              |
    | org.gradle.status                               | release      |              |
    | org.gradle.category                             | library      | library      |
    | org.gradle.usage                                | java-runtime | java-runtime |
    | com.android.build.api.attributes.AgpVersionAttr |              | 9.1.0        |
    | com.android.build.api.attributes.BuildTypeAttr  |              | debug        |
    | org.gradle.jvm.environment                      |              | android      |
    | org.jetbrains.kotlin.platform.type              |              | androidJvm   |

io.github.rosemoe:editor:0.24.6
\--- debugRuntimeClasspath

A web-based, searchable dependency report is available by adding the --scan option.
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step01-1794725437212.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
```

### File 7: `01-dependencyInsight-releaseRuntimeClasspath.txt`
```text
> Task :app:dependencyInsight
io.github.rosemoe:editor:0.24.6
  Variant releaseVariantReleaseRuntimePublication:
    | Attribute Name                                  | Provided     | Requested    |
    |-------------------------------------------------|--------------|--------------|
    | org.gradle.dependency.bundling                  | external     |              |
    | org.gradle.libraryelements                      | aar          |              |
    | org.gradle.status                               | release      |              |
    | org.gradle.category                             | library      | library      |
    | org.gradle.usage                                | java-runtime | java-runtime |
    | com.android.build.api.attributes.AgpVersionAttr |              | 9.1.0        |
    | com.android.build.api.attributes.BuildTypeAttr  |              | release      |
    | org.gradle.jvm.environment                      |              | android      |
    | org.jetbrains.kotlin.platform.type              |              | androidJvm   |

io.github.rosemoe:editor:0.24.6
\--- releaseRuntimeClasspath

A web-based, searchable dependency report is available by adding the --scan option.
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step01-1794725438868.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
```

### File 8: `01-dependencyInsight-debugUnitTestRuntimeClasspath.txt`
```text
> Task :app:dependencyInsight
io.github.rosemoe:editor:0.24.6
  Variant releaseVariantReleaseRuntimePublication:
    | Attribute Name                                  | Provided     | Requested    |
    |-------------------------------------------------|--------------|--------------|
    | org.gradle.dependency.bundling                  | external     |              |
    | org.gradle.libraryelements                      | aar          |              |
    | org.gradle.status                               | release      |              |
    | org.gradle.category                             | library      | library      |
    | org.gradle.usage                                | java-runtime | java-runtime |
    | com.android.build.api.attributes.AgpVersionAttr |              | 9.1.0        |
    | com.android.build.api.attributes.BuildTypeAttr  |              | debug        |
    | org.gradle.jvm.environment                      |              | android      |
    | org.jetbrains.kotlin.platform.type              |              | androidJvm   |

io.github.rosemoe:editor:0.24.6
\--- project ':app'
     \--- project ':app' (*)

(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.

A web-based, searchable dependency report is available by adding the --scan option.
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step01-1794725440422.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
```

### File 9: `01-dependencyInsight-debugAndroidTestRuntimeClasspath.txt`
```text
> Task :app:dependencyInsight
No dependencies matching given input were found in configuration ':app:debugAndroidTestRuntimeClasspath'
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step01-1794725441782.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
```

### File 10: `02-resolved-aar.txt`
```text
> Task :app:phase0ResolveSoraAar
MATCHING_ARTIFACT_COUNT=1
CANDIDATE[0]=io.github.rosemoe:editor:0.24.6 | variant=io.github.rosemoe:editor:0.24.6 variant releaseVariantReleaseRuntimePublication | attrs={artifactType=aar, org.gradle.category=library, org.gradle.dependency.bundling=external, org.gradle.libraryelements=aar, org.gradle.status=release, org.gradle.usage=java-runtime} | file=/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar
RESOLVED_GROUP=io.github.rosemoe
RESOLVED_MODULE=editor
RESOLVED_VERSION=0.24.6
RESOLVED_COORDINATES=io.github.rosemoe:editor:0.24.6
RESOLVED_ARTIFACT_TYPE=aar
RESOLVED_VARIANT=io.github.rosemoe:editor:0.24.6 variant releaseVariantReleaseRuntimePublication
RESOLVED_VARIANT_ATTRIBUTES={artifactType=aar, org.gradle.category=library, org.gradle.dependency.bundling=external, org.gradle.libraryelements=aar, org.gradle.status=release, org.gradle.usage=java-runtime}
RESOLVED_AAR_PATH=/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar
RESOLVED_AAR_SIZE_BYTES=790650
RESOLVED_AAR_SHA256=fb76ae4db31d94d9fee7f97d9b8ec1c9659c54b5ee1ded7f9f95f7039646243e
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step02-1794725443520.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
CLASSES_JAR_PATH=/tmp/sora-editor-0.24.6-classes.jar
CLASSES_JAR_SHA256=9aef25039b4bc9ef2e05e6c45dddfbad02cfc7c47acbf4f9a82ac6071f110318
```

### File 11: `02-gradle-cache-find.txt`
```text
/home/runner/.gradle/caches/modules-2/files-2.1/io.github.rosemoe/editor/0.24.6/2db6470dda44ac970c3ec8a8a33d387026a744cd/editor-0.24.6.aar
```

### File 12: `02-javap-DrawCursorTask.txt`
```text
Compiled from "EditorRenderer.java"
public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask {
  private static final io.github.rosemoe.sora.widget.style.SelectionHandleStyle$HandleDescriptor TMP_DESC;
  protected float x;
  protected float y;
  protected int handleType;
  protected io.github.rosemoe.sora.widget.style.SelectionHandleStyle$HandleDescriptor descriptor;
  protected boolean isBidiIndicatorRequired;
  protected boolean isRightToLeft;
  final io.github.rosemoe.sora.widget.EditorRenderer this$0;
  public io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask(io.github.rosemoe.sora.widget.EditorRenderer, float, float, int, io.github.rosemoe.sora.widget.style.SelectionHandleStyle$HandleDescriptor);
  public void setBidiIndicatorRequired(boolean);
  public void setRightToLeft(boolean);
  private int getActualHandleType();
  private boolean drawSelForLeftRight();
  private boolean drawSelForInsert();
  private boolean isSelForLongSelect();
  protected void execute(android.graphics.Canvas);
  static {};
}
```

### File 13: `02-javap-EditorRenderer.txt`
```text
Compiled from "EditorRenderer.java"
public class io.github.rosemoe.sora.widget.EditorRenderer {
  private static final int[] PRESSED_DRAWABLE_STATE;
  private static final int[] DEFAULT_DRAWABLE_STATE;
  private static final java.lang.String LOG_TAG;
  private static final int[] sDiagnosticsColorMapping;
  protected final io.github.rosemoe.sora.graphics.BufferedDrawPoints bufferedDrawPoints;
  protected final io.github.rosemoe.sora.graphics.Paint paintGeneral;
  protected final io.github.rosemoe.sora.graphics.Paint paintOther;
  protected final android.graphics.Rect viewRect;
  private final android.graphics.RectF tmpRect;
  private final android.graphics.Path tmpPath;
  protected final io.github.rosemoe.sora.graphics.Paint paintGraph;
  private final android.graphics.RectF verticalScrollBarRect;
  private final android.graphics.RectF horizontalScrollBarRect;
  private final io.github.rosemoe.sora.util.LongArrayList postDrawLineNumbers;
  private final androidx.collection.MutableIntList postDrawCurrentLines;
  private final io.github.rosemoe.sora.util.LongArrayList matchedPositions;
  private final androidx.collection.MutableLongLongMap highlightPositions;
  private final android.util.SparseArray<io.github.rosemoe.sora.text.ContentLine> preloadedLines;
  private final android.util.SparseArray<io.github.rosemoe.sora.text.bidi.Directions> preloadedDirections;
  private final io.github.rosemoe.sora.widget.CodeEditor editor;
  private final java.util.List<io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion> collectedDiagnostics;
  protected java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock> lastStuckLines;
  android.graphics.Paint$FontMetricsInt metricsText;
  private android.graphics.drawable.Drawable horizontalScrollbarThumbDrawable;
  private android.graphics.drawable.Drawable horizontalScrollbarTrackDrawable;
  private android.graphics.drawable.Drawable verticalScrollbarThumbDrawable;
  private android.graphics.drawable.Drawable verticalScrollbarTrackDrawable;
  private final android.graphics.drawable.Drawable lineBreakGraph;
  private final android.graphics.drawable.Drawable softwrapLeftGraph;
  private final android.graphics.drawable.Drawable softwrapRightGraph;
  private volatile long displayTimestamp;
  private android.graphics.Paint$FontMetricsInt metricsLineNumber;
  private android.graphics.Paint$FontMetricsInt metricsGraph;
  private int cachedGutterWidth;
  private io.github.rosemoe.sora.text.Cursor cursor;
  protected io.github.rosemoe.sora.text.ContentLine lineBuf;
  protected io.github.rosemoe.sora.text.Content content;
  private volatile boolean renderingFlag;
  protected boolean forcedRecreateLayout;
  private final io.github.rosemoe.sora.widget.minimap.MinimapRenderer minimapRenderer;
  private final io.github.rosemoe.sora.lang.styling.line.LineStyles coordinateLine;
  public io.github.rosemoe.sora.widget.EditorRenderer(io.github.rosemoe.sora.widget.CodeEditor);
  public void onEditorFullTextUpdate();
  public void draw(android.graphics.Canvas);
  public void onSizeChanged(int, int);
  io.github.rosemoe.sora.graphics.Paint getPaint();
  io.github.rosemoe.sora.graphics.Paint getPaintOther();
  io.github.rosemoe.sora.graphics.Paint getPaintGraph();
  void setCachedLineNumberWidth(int);
  public android.graphics.RectF getVerticalScrollBarRect();
  public android.graphics.RectF getHorizontalScrollBarRect();
  public void setHorizontalScrollbarThumbDrawable(android.graphics.drawable.Drawable);
  public android.graphics.drawable.Drawable getHorizontalScrollbarThumbDrawable();
  public void setHorizontalScrollbarTrackDrawable(android.graphics.drawable.Drawable);
  public android.graphics.drawable.Drawable getHorizontalScrollbarTrackDrawable();
  public void setVerticalScrollbarThumbDrawable(android.graphics.drawable.Drawable);
  public android.graphics.drawable.Drawable getVerticalScrollbarThumbDrawable();
  public void setVerticalScrollbarTrackDrawable(android.graphics.drawable.Drawable);
  public android.graphics.drawable.Drawable getVerticalScrollbarTrackDrawable();
  public void setTextSizePxDirect(float);
  public void setTypefaceText(android.graphics.Typeface);
  public void setTypefaceLineNumber(android.graphics.Typeface);
  public void setTextScaleX(float);
  public void setLetterSpacing(float);
  protected void onTextStyleUpdate();
  protected void updateTimestamp();
  protected void prepareLine(int);
  protected io.github.rosemoe.sora.text.ContentLine getLine(int);
  protected io.github.rosemoe.sora.text.bidi.Directions getLineDirections(int);
  io.github.rosemoe.sora.text.ContentLine getLineDirect(int);
  int getColumnCount(int);
  public void updateLineDisplayList(android.graphics.RenderNode, int, io.github.rosemoe.sora.lang.styling.Spans$Reader);
  public io.github.rosemoe.sora.graphics.TextRow createTextRow(int);
  private void applySelectedTextRange(io.github.rosemoe.sora.graphics.TextRow, int, io.github.rosemoe.sora.text.ContentLine);
  protected float drawSingleTextLine(android.graphics.Canvas, int, float, float, io.github.rosemoe.sora.lang.styling.Spans$Reader, boolean);
  public boolean hasSideHintIcons();
  public void drawView(android.graphics.Canvas);
  protected void drawUserGutterBackground(android.graphics.Canvas, int);
  protected void drawStuckLineNumbers(android.graphics.Canvas, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>, float, float, int);
  protected float getStuckLineBottom(java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>);
  protected void drawStuckLines(android.graphics.Canvas, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>, float);
  protected void drawHardwrapMarker(android.graphics.Canvas, float);
  protected void drawSideIcons(android.graphics.Canvas, float);
  protected void drawFormatTip(android.graphics.Canvas);
  protected void drawColor(android.graphics.Canvas, int, android.graphics.RectF);
  protected void drawColorRound(android.graphics.Canvas, int, android.graphics.RectF);
  protected void drawColor(android.graphics.Canvas, int, android.graphics.Rect);
  protected void drawRowBackground(android.graphics.Canvas, int, int);
  protected void drawRowBackground(android.graphics.Canvas, int, int, int);
  protected void drawLineNumber(android.graphics.Canvas, int, int, float, float, int);
  protected void drawLineNumberBackground(android.graphics.Canvas, float, float, int);
  protected void drawDivider(android.graphics.Canvas, float, int);
  private void prepareLines(int, int);
  private void releasePreloadedData();
  protected java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock> getStuckCodeBlocks();
  protected io.github.rosemoe.sora.lang.styling.line.LineStyles getLineStyles(int);
  protected <T extends io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle> T getLineStyle(int, java.lang.Class<T>);
  protected io.github.rosemoe.sora.lang.styling.color.ResolvableColor getUserBackgroundForLine(int);
  protected io.github.rosemoe.sora.lang.styling.color.ResolvableColor getUserGutterBackgroundForLine(int);
  protected void drawAnimatedCurrentLineBackground(android.graphics.Canvas, int);
  public io.github.rosemoe.sora.graphics.TextRowParams createTextRowParams();
  protected void drawRows(android.graphics.Canvas, float, io.github.rosemoe.sora.util.LongArrayList, java.util.List<io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask>, androidx.collection.MutableIntList, io.github.rosemoe.sora.util.MutableInt, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>);
  private long getBidiIndicatorAttrs(int, int);
  private void applyBidiIndicatorAttrs(io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask, int, int);
  private void drawBidiSelectionIndicator(android.graphics.Canvas, float, float, float, boolean);
  protected void drawDiagnosticIndicator(android.graphics.Canvas, io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle, int, float, float);
  protected void drawDiagnosticIndicators(android.graphics.Canvas, float);
  private void drawWhitespaces(android.graphics.Canvas, io.github.rosemoe.sora.graphics.TextRow, android.text.GetChars, int, int, int, int, boolean, float, float, int, int);
  public float getMiniGraphWidth();
  protected void drawMiniGraph(android.graphics.Canvas, float, int, android.graphics.drawable.Drawable);
  protected int getRowTopForBackground(int);
  protected int getRowBottomForBackground(int);
  protected void drawRowRegionBackground(android.graphics.Canvas, int, io.github.rosemoe.sora.graphics.TextRow, int, int, int, int, int, int);
  protected void drawRowBackgroundRectWithBorder(android.graphics.Canvas, android.graphics.RectF, int, int);
  protected void drawRowBackgroundRect(android.graphics.Canvas, android.graphics.RectF);
  protected void drawRowBackgroundRect(android.graphics.Canvas, android.graphics.RectF, io.github.rosemoe.sora.graphics.Paint);
  private boolean isInside(int, int, int, boolean);
  public long getTimestamp();
  public android.graphics.Paint$FontMetricsInt getLineNumberMetrics();
  public android.graphics.Paint$FontMetricsInt getTextMetrics();
  protected void drawEdgeEffect(android.graphics.Canvas);
  protected void drawBlockLines(android.graphics.Canvas, float);
  protected void drawSideBlockLine(android.graphics.Canvas);
  protected void drawScrollBarsAndMinimap(android.graphics.Canvas);
  protected void drawScrollBarTrackVertical(android.graphics.Canvas);
  private void getVerticalScrollBarRect(android.graphics.RectF, float);
  protected void drawScrollBarVertical(android.graphics.Canvas);
  protected void drawLineInfoPanel(android.graphics.Canvas, float, float, float);
  protected void drawScrollBarTrackHorizontal(android.graphics.Canvas);
  protected void patchSnippetRegions(android.graphics.Canvas, float);
  protected void patchHighlightedDelimiters(android.graphics.Canvas, float);
  protected boolean isInvalidTextBounds(int, int);
  protected void patchTextRegionWithColor(android.graphics.Canvas, float, int, int, int, int, int);
  protected void patchTextBackgroundRegions(android.graphics.Canvas, float, int, int, int, float, int);
  protected void patchTextRegions(android.graphics.Canvas, float, int, int, io.github.rosemoe.sora.graphics.TextRow$DrawTextConsumer, io.github.rosemoe.sora.graphics.TextRow$BackgroundRegionConsumer);
  protected void drawSelectionOnAnimation(android.graphics.Canvas);
  protected void drawScrollBarHorizontal(android.graphics.Canvas);
  protected void buildMeasureCacheForLines(int, int, long, boolean);
  protected void buildMeasureCacheForLines(int, int);
  protected float getRowWidth(int);
  private boolean lambda$patchTextBackgroundRegions$7(float, int, android.graphics.Canvas, float, int, float, float);
  private void lambda$patchTextRegionWithColor$6(int, android.graphics.Canvas, int, int, android.graphics.Canvas, android.text.GetChars, int, int, int, int, boolean, float, float, io.github.rosemoe.sora.graphics.TextRowParams, io.github.rosemoe.sora.lang.styling.Span);
  private boolean lambda$drawRowRegionBackground$5(float, int, android.graphics.Canvas, int, int, float, float);
  private boolean lambda$drawDiagnosticIndicators$4(android.graphics.Canvas, io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle, int, float, float, float);
  private boolean lambda$drawRows$3(float, android.graphics.Canvas, float, float);
  private void lambda$drawRows$2(int, io.github.rosemoe.sora.graphics.TextRow, int, int, int, int, android.graphics.Canvas, android.text.GetChars, int, int, int, int, boolean, float, float, io.github.rosemoe.sora.graphics.TextRowParams, io.github.rosemoe.sora.lang.styling.Span);
  private kotlin.Unit lambda$drawRows$1(android.graphics.Canvas, int, io.github.rosemoe.sora.graphics.TextRow, io.github.rosemoe.sora.widget.layout.Row, java.lang.Long, java.lang.Long);
  private void lambda$prepareLines$0(int, io.github.rosemoe.sora.text.ContentLine, io.github.rosemoe.sora.text.bidi.Directions);
  static {};
}
```

### File 14: `02-bytecode-verification.txt`
```text
- DrawCursorTask class: `public class io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask {`
- DrawCursorTask.y field: `protected float y;` (direct same-package access in io.github.rosemoe.sora.widget without reflection, setAccessible, or Unsafe confirmed)
- `drawRows`: `protected void drawRows(android.graphics.Canvas, float, io.github.rosemoe.sora.util.LongArrayList, java.util.List<io.github.rosemoe.sora.widget.EditorRenderer$DrawCursorTask>, androidx.collection.MutableIntList, io.github.rosemoe.sora.util.MutableInt, java.util.List<io.github.rosemoe.sora.lang.styling.CodeBlock>);`
- `patchHighlightedDelimiters`: `protected void patchHighlightedDelimiters(android.graphics.Canvas, float);`
- `drawDiagnosticIndicators`: `protected void drawDiagnosticIndicators(android.graphics.Canvas, float);`
- `drawUserGutterBackground`: `protected void drawUserGutterBackground(android.graphics.Canvas, int);`
- `drawSideIcons`: `protected void drawSideIcons(android.graphics.Canvas, float);`
- `drawLineNumber`: `protected void drawLineNumber(android.graphics.Canvas, int, int, float, float, int);`
- `drawRowBackground(3-arg)`: `protected void drawRowBackground(android.graphics.Canvas, int, int);`
- `drawRowBackground(4-arg)`: `protected void drawRowBackground(android.graphics.Canvas, int, int, int);`
- `drawSideBlockLine`: `protected void drawSideBlockLine(android.graphics.Canvas);`
- `buildMeasureCacheForLines(4-arg)`: `protected void buildMeasureCacheForLines(int, int, long, boolean);`
- `buildMeasureCacheForLines(2-arg)`: `protected void buildMeasureCacheForLines(int, int);`

STEP_0_2_STATUS=PASS
```

### File 15: `04-discovered-test-tasks.txt`
```text
assembleAndroidTest - Assembles all the Test applications.
assembleUnitTest - Assembles all the unit test applications.
compileDebugAndroidTestSources
compileDebugUnitTestSources
installDebugAndroidTest - Installs the android (on device) tests for the Debug build.
uninstallDebugAndroidTest - Uninstalls the android (on device) tests for the Debug build.
connectedAndroidTest - Installs and runs instrumentation tests for all flavors on connected devices.
connectedDebugAndroidTest - Installs and runs the tests for debug on connected devices.
deviceAndroidTest - Installs and runs instrumentation tests using all Device Providers.
testDebugUnitTest - Run unit tests for the debug build.
analyzeDebugAndroidTestDependencies
analyzeDebugUnitTestDependencies
assembleDebugAndroidTest - Assembles main output for variant debugAndroidTest
assembleDebugUnitTest - Assembles main output for variant debugUnitTest
checkDebugAndroidTestAarMetadata
checkDebugAndroidTestDuplicateClasses
compileDebugAndroidTestJavaWithJavac
compileDebugAndroidTestKotlin
compileDebugAndroidTestNavigationResources
compileDebugUnitTestJavaWithJavac
compileDebugUnitTestKotlin
compressDebugAndroidTestAssets
createDebugAndroidTestApkListingFileRedirect
desugarDebugAndroidTestFileDependencies
dexBuilderDebugAndroidTest
generateDebugAndroidTestAssets
generateDebugAndroidTestBuildConfig
generateDebugAndroidTestLintModel
generateDebugAndroidTestResources
generateDebugAndroidTestRFile
generateDebugAndroidTestSources
generateDebugUnitTestAssets
generateDebugUnitTestLintModel
generateDebugUnitTestResources
generateDebugUnitTestSources
javaPreCompileDebugAndroidTest
javaPreCompileDebugUnitTest
kspDebugAndroidTestKotlin
kspDebugUnitTestKotlin
lintAnalyzeDebugAndroidTest - Run lint analysis on the debugAndroidTest component
lintAnalyzeDebugUnitTest - Run lint analysis on the debugUnitTest component
mapDebugAndroidTestSourceSetPaths
mergeDebugAndroidTestAssets
mergeDebugAndroidTestGeneratedProguardFiles
mergeDebugAndroidTestGlobalSynthetics
mergeDebugAndroidTestJavaResource
mergeDebugAndroidTestJniLibFolders
mergeDebugAndroidTestNativeLibs
mergeDebugAndroidTestResources
mergeExtDexDebugAndroidTest
mergeLibDexDebugAndroidTest
mergeProjectDexDebugAndroidTest
packageDebugAndroidTest
packageDebugAndroidTestResources
parseDebugAndroidTestLocalResources
preDebugAndroidTestBuild
preDebugUnitTestBuild
processDebugAndroidTestJavaRes
processDebugAndroidTestManifest
processDebugAndroidTestNavigationResources
processDebugAndroidTestResources
processDebugUnitTestJavaRes
signingConfigWriterDebugAndroidTest
stripDebugAndroidTestDebugSymbols
validateSigningDebugAndroidTest
writeDebugAndroidTestSigningConfigVersions

Required task presence check:
  - testDebugUnitTest: FOUND
  - connectedDebugAndroidTest: FOUND
  - compileDebugAndroidTestKotlin: FOUND
  - compileDebugAndroidTestJavaWithJavac: FOUND
  - compileDebugUnitTestKotlin: FOUND
  - compileDebugUnitTestJavaWithJavac: FOUND
```

### File 16: `04-app-tasks-all.txt`
```text
> Task :app:tasks

------------------------------------------------------------
Tasks runnable from project ':app'
------------------------------------------------------------

Android tasks
-------------
signingReport - Displays the signing info for the base and test modules

Build tasks
-----------
assemble - Assemble main outputs for all the variants.
assembleAndroidTest - Assembles all the Test applications.
assembleUnitTest - Assembles all the unit test applications.
build - Assembles and tests this project.
buildDependents - Assembles and tests this project and all projects that depend on it.
buildNeeded - Assembles and tests this project and all projects it depends on.
bundle - Assemble bundles for all the variants.
clean - Deletes the build directory.
compileDebugAndroidTestSources
compileDebugSources
compileDebugUnitTestSources
compileReleaseSources

Help tasks
----------
artifactTransforms - Displays the Artifact Transforms that can be executed in project ':app'.
buildEnvironment - Displays all buildscript dependencies declared in project ':app'.
dependencies - Displays all dependencies declared in project ':app'.
dependencyInsight - Displays the insight into a specific dependency in project ':app'.
help - Displays a help message.
javaToolchains - Displays the detected java toolchains.
kotlinDslAccessorsReport - Prints the Kotlin code for accessing the currently available project extensions.
outgoingVariants - Displays the outgoing variants of project ':app'.
projects - Displays the sub-projects of project ':app'.
properties - Displays the properties of project ':app'.
resolvableConfigurations - Displays the configurations that can be resolved in project ':app'.
tasks - Displays the tasks runnable from project ':app'.

Install tasks
-------------
installDebug - Installs the Debug build.
installDebugAndroidTest - Installs the android (on device) tests for the Debug build.
installRelease - Installs the Release build.
uninstallAll - Uninstall all applications.
uninstallDebug - Uninstalls the Debug build.
uninstallDebugAndroidTest - Uninstalls the android (on device) tests for the Debug build.
uninstallRelease - Uninstalls the Release build.

Verification tasks
------------------
check - Runs all checks.
checkJetifier - Checks whether Jetifier is needed for the current project
connectedAndroidTest - Installs and runs instrumentation tests for all flavors on connected devices.
connectedCheck - Runs all device checks on currently connected devices.
connectedDebugAndroidTest - Installs and runs the tests for debug on connected devices.
deviceAndroidTest - Installs and runs instrumentation tests using all Device Providers.
deviceCheck - Runs all device checks using Device Providers and Test Servers.
lint - Runs lint on the default variant.
lintDebug - Print text output from the corresponding lint report task
lintFix - Runs lint on the default variant and applies any safe suggestions to the source code.
lintRelease - Print text output from the corresponding lint report task
test - Run unit tests for all variants.
testDebugUnitTest - Run unit tests for the debug build.
updateLintBaseline - Updates the lint baseline using the default variant.

Other tasks
-----------
aggregatedTestResultsCollectionDebug
aggregatedTestResultsCollectionRelease
analyzeDebugAndroidTestDependencies
analyzeDebugDependencies
analyzeDebugUnitTestDependencies
analyzeReleaseDependencies
assembleDebug - Assembles main output for variant debug
assembleDebugAndroidTest - Assembles main output for variant debugAndroidTest
assembleDebugUnitTest - Assembles main output for variant debugUnitTest
assembleRelease - Assembles main output for variant release
buildDebugPreBundle
buildReleasePreBundle
bundleDebug - Assembles bundle for variant debug
bundleDebugClassesToCompileJar
bundleDebugClassesToRuntimeJar
bundleDebugResources
bundleRelease - Assembles bundle for variant release
bundleReleaseClassesToCompileJar
bundleReleaseClassesToRuntimeJar
bundleReleaseResources
checkDebugAarMetadata
checkDebugAndroidTestAarMetadata
checkDebugAndroidTestDuplicateClasses
checkDebugDuplicateClasses
checkDebugManifest
checkReleaseAarMetadata
checkReleaseDuplicateClasses
checkReleaseManifest
collectDebugAggregatedCoverage
collectDebugCoverage
collectReleaseAggregatedCoverage
collectReleaseCoverage
collectReleaseDependencies
compileDebugAndroidTestJavaWithJavac
compileDebugAndroidTestKotlin
compileDebugAndroidTestNavigationResources
compileDebugArtProfile
compileDebugJavaWithJavac
compileDebugKotlin
compileDebugNavigationResources
compileDebugUnitTestJavaWithJavac
compileDebugUnitTestKotlin
compileLint
compileLintChecks
compileReleaseArtProfile
compileReleaseJavaWithJavac
compileReleaseKotlin
compileReleaseNavigationResources
compressDebugAndroidTestAssets
compressDebugAssets
compressReleaseAssets
configureReleaseDependencies
consumeConfigAttr
convertLinkedResourcesToBinaryRelease
convertShrunkResourcesToBinaryRelease
createAggregatedCoverageReport
createAggregatedTestReport
createCoverageReport
createDebugAndroidTestApkListingFileRedirect
createDebugApkListingFileRedirect
createDebugApksFromBundleListingFileRedirect
createDebugBundleListingFileRedirect
createDebugCompatibleScreenManifests
createMockableJar
createReleaseApkListingFileRedirect
createReleaseApksFromBundleListingFileRedirect
createReleaseBundleListingFileRedirect
createReleaseCompatibleScreenManifests
createTestReport
desugarDebugAndroidTestFileDependencies
desugarDebugFileDependencies
dexBuilderDebug
dexBuilderDebugAndroidTest
expandReleaseArtProfileWildcards
extractApksForDebug
extractApksForRelease
extractApksFromBundleForDebug
extractApksFromBundleForRelease
extractDebugNativeDebugMetadata
extractDebugNativeSymbolTables
extractDeepLinksDebug
extractDeepLinksRelease
extractProguardFiles
extractReleaseNativeDebugMetadata
extractReleaseNativeSymbolTables
extractReleaseVersionControlInfo
generateDebugAndroidTestAssets
generateDebugAndroidTestBuildConfig
generateDebugAndroidTestLintModel
generateDebugAndroidTestResources
generateDebugAndroidTestRFile
generateDebugAndroidTestSources
generateDebugAssets
generateDebugBuildConfig
generateDebugFeatureMetadata
generateDebugFeatureTransitiveDeps
generateDebugLintModel
generateDebugLintReportModel
generateDebugManifestClass
generateDebugResources
generateDebugRFile
generateDebugSources
generateDebugUnitTestAssets
generateDebugUnitTestLintModel
generateDebugUnitTestResources
generateDebugUnitTestSources
generateReleaseAssets
generateReleaseBuildConfig
generateReleaseFeatureMetadata
generateReleaseFeatureTransitiveDeps
generateReleaseLintModel
generateReleaseLintReportModel
generateReleaseManifestClass
generateReleaseResources
generateReleaseRFile
generateReleaseSources
javaPreCompileDebug
javaPreCompileDebugAndroidTest
javaPreCompileDebugUnitTest
javaPreCompileRelease
kspDebugAndroidTestKotlin
kspDebugKotlin
kspDebugUnitTestKotlin
kspReleaseKotlin
lintAnalyzeDebug - Run lint analysis on the debug component
lintAnalyzeDebugAndroidTest - Run lint analysis on the debugAndroidTest component
lintAnalyzeDebugUnitTest - Run lint analysis on the debugUnitTest component
lintAnalyzeRelease - Run lint analysis on the release component
lintFixDebug - Fix lint on the debug variant
lintFixRelease - Fix lint on the release variant
lintReportDebug - Run lint on the debug variant
lintReportRelease - Run lint on the release variant
makeApkFromBundleForDebug
makeApkFromBundleForRelease
mapDebugAndroidTestSourceSetPaths
mapDebugSourceSetPaths
mapReleaseSourceSetPaths
mergeDebugAndroidTestAssets
mergeDebugAndroidTestGeneratedProguardFiles
mergeDebugAndroidTestGlobalSynthetics
mergeDebugAndroidTestJavaResource
mergeDebugAndroidTestJniLibFolders
mergeDebugAndroidTestNativeLibs
mergeDebugAndroidTestResources
mergeDebugArtProfile
mergeDebugAssets
mergeDebugGeneratedProguardFiles
mergeDebugGlobalSynthetics
mergeDebugJavaResource
mergeDebugJniLibFolders
mergeDebugNativeDebugMetadata
mergeDebugNativeLibs
mergeDebugResources
mergeDebugStartupProfile
mergeExtDexDebug
mergeExtDexDebugAndroidTest
mergeLibDexDebug
mergeLibDexDebugAndroidTest
mergeProjectDexDebug
mergeProjectDexDebugAndroidTest
mergeReleaseArtProfile
mergeReleaseAssets
mergeReleaseComposeMapping
mergeReleaseGeneratedProguardFiles
mergeReleaseJavaResource
mergeReleaseJniLibFolders
mergeReleaseNativeDebugMetadata
mergeReleaseNativeLibs
mergeReleaseResources
mergeReleaseStartupProfile
minifyReleaseWithR8
optimizeReleaseResources
packageDebug
packageDebugAndroidTest
packageDebugAndroidTestResources
packageDebugBundle
packageDebugResources
packageDebugUniversalApk
packageRelease
packageReleaseBundle
packageReleaseResources
packageReleaseUniversalApk
parseDebugAndroidTestLocalResources
parseDebugIntegrityConfig
parseDebugLocalResources
parseReleaseIntegrityConfig
parseReleaseLocalResources
preBuild
preDebugAndroidTestBuild
preDebugBuild
preDebugUnitTestBuild
prepareLintJarForPublish
preReleaseBuild
processApplicationManifestDebugForBundle
processApplicationManifestReleaseForBundle
processDebugAndroidTestJavaRes
processDebugAndroidTestManifest
processDebugAndroidTestNavigationResources
processDebugAndroidTestResources
processDebugJavaRes
processDebugMainManifest
processDebugManifest
processDebugManifestForPackage
processDebugNavigationResources
processDebugResources
processDebugUnitTestJavaRes
processManifestDebugForFeature
processManifestReleaseForFeature
processReleaseJavaRes
processReleaseMainManifest
processReleaseManifest
processReleaseManifestForPackage
processReleaseNavigationResources
processReleaseResources
produceDebugBundleIdeListingFile
produceReleaseBundleIdeListingFile
produceReleaseComposeMapping
reportReleaseComposeMappingErrors
resolveConfigAttr
sdkReleaseDependencyData
signDebugBundle
signingConfigWriterDebug
signingConfigWriterDebugAndroidTest
signingConfigWriterRelease
signReleaseBundle
stripDebugAndroidTestDebugSymbols
stripDebugDebugSymbols
stripReleaseDebugSymbols
testResultsCollectionDebug
testResultsCollectionRelease
updateLintBaselineDebug - Update the lint baseline using the debug variant
updateLintBaselineRelease - Update the lint baseline using the release variant
validateSigningDebug
validateSigningDebugAndroidTest
validateSigningRelease
writeDebugAndroidTestSigningConfigVersions
writeDebugApplicationId
writeDebugAppMetadata
writeDebugModuleMetadata
writeDebugSigningConfigVersions
writeReleaseApplicationId
writeReleaseAppMetadata
writeReleaseModuleMetadata
writeReleaseSigningConfigVersions
zipApksForDebug
zipApksForRelease

Rules
-----
Pattern: clean<TaskName>: Cleans the output files of a task.
Pattern: build<ConfigurationName>: Assembles the artifacts of a configuration.
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/step04-1794725445817.json

[Incubating] Problems report is available at: file:///home/runner/work/Scribe-Native/Scribe-Native/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 2s
1 actionable task: 1 executed
```
