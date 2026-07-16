---
doc_id: drive-backup-app-phase-3-completion-execution-plan
status: active
last_updated: 2026-07-16
context_role: execution-plan
artifact_contract: superpowers-implementation-plan/v1
artifact_readiness: implementation-ready
execution: code
execution_status: complete
read_when:
  - The agent completes, reviews, or tests Phase 3 folder health, metadata scanning, enablement, or sign-out compensation.
  - The agent needs the current branch, task order, test commands, or live Samsung acceptance sequence for Phase 3 closure.
do_not_read_when:
  - The task is Google Drive authorization, upload, scheduling, email, or release packaging.
---

# Drive Backup App Phase 3 Completion Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use
> `superpowers:subagent-driven-development` or `superpowers:executing-plans`.
> Every production behavior follows a witnessed red-green-refactor cycle.

## Goal

Finish the remaining Phase 3 local-folder boundary: pending-picker sign-out
compensation, live access health, enable/disable, iterative read-only metadata
scanning, per-mapping progress and cancellation, hostile-provider coverage, and
physical Samsung restart/upgrade evidence.

The authoritative product and platform contract remains
[[Drive Backup App Phase 3 Local Folder Access Implementation Plan]]. This note
is the smaller current execution packet. When the two disagree, stop and fix
the documentation before changing code.

## Architecture

Keep Room schema 1 unchanged. Durable `FolderMapping` configuration remains in
Room; `Folder Access Health` is derived from the exact persisted read grant and
a live root query; `Metadata Scan` state lives in `FolderAccessViewModel` for the
current process only. The repository hides raw tree URIs, coordinates pending
operation compensation, and gives presentation a validated cold scan stream.

Provider I/O remains behind focused SAF adapters on an injected IO dispatcher.
The scanner uses iterative `DocumentsContract` child queries, opaque document
IDs, one fresh `CancellationSignal` per query, deterministic cursor closure,
and aggregate counters only. It never opens file content or calls create,
rename, move, delete, output-stream, or write APIs.

## Tech Stack

- Kotlin, coroutines, Flow, and `viewModelScope`
- Android `ContentResolver`, `DocumentsContract`, and `CancellationSignal`
- Room 2.8.4 schema 1 with no migration in this plan
- Jetpack Compose Material 3
- JUnit 4, kotlinx-coroutines-test, AndroidX instrumented tests, and a test-only
  controllable `DocumentsProvider`
- Samsung Galaxy A23 on Android 14/API 34, wired ADB, Android user 0

## Global Constraints

- Work from `feature/phase-3-folder-health-scan`, based on integrated `main`.
- Do not add Google Drive, upload, WorkManager, email, encryption, or release
  behavior.
- Do not add broad storage or media permissions on this completion branch.
  Exact Downloads-root support is a mandatory final-app follow-on with its own
  explicit all-files-access consent path; it is not part of the SAF source.
- Never persist health or scan state. Never expose raw URI, document ID,
  filename, account subject, token, OAuth identifier, or exception payload.
- Keep internal and public flavors behaviorally equivalent and independently
  testable.
- Use the GitHub account switcher before every commit and push. Alternate Arya
  and Viji commits at coherent boundaries; the controller owns commits.
- Automated providers support hostile-path tests but never satisfy a live
  `FOLDER-LIVE-*` gate.
- Never touch Android user 150. Direct physical instrumentation targets Android
  user 0 explicitly.

## Acceptance Gate

Phase 3 can close only after all of these are evidenced:

- every mapping independently reaches one of `Ready`, `PermissionMissing`,
  `TreeMissing`, `ProviderAuthRequired`, or `TemporarilyUnavailable` after
  `Checking`;
- enabled configuration, health, and scan state stay orthogonal;
- an approved user can add, list, enable/disable, repair, scan/cancel, and
  remove mappings, while non-approved states expose no folder metadata;
- scan counts folders, files, known bytes, unknown-size files, unreadable
  entries, and elapsed time without source mutation;
- empty, nested, deep, duplicate/repeated ID, null metadata, malformed row, null
  cursor, loading/error extra, provider exception, cancellation, and isolated
  multi-mapping behavior have deterministic coverage;
- a sign-out started with pending picker work marks/cleans that work before auth
  state is cleared, and cleanup failure stays retryable without blocking sign-out;
- force-stop/relaunch and in-place APK upgrade preserve a real mapping and allow
  a complete nested scan without reopening the picker;
- real grant release preserves the mapping and reaches `PermissionMissing`;
- one intentionally broken dedicated mapping does not block a healthy real
  mapping;
- recent-apps protection remains enabled and mutation-sentinel manifests are
  unchanged;
- both-flavor unit, build, Android-test APK, lint, and direct Samsung user-0
  suites pass; all evidence committed to Git is redacted.

---

## Task 1: Pending Picker Sign-Out Compensation

**Files:**

- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/FolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/auth/presentation/AuthViewModel.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/auth/presentation/AuthUiState.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/app/MainActivity.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepositoryInstrumentedTest.kt`
- Test: `app/src/test/java/com/aryasubramani/vijibackup/auth/presentation/AuthViewModelTest.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/app/AppCompositionInstrumentedTest.kt`

**Interfaces:**

```kotlin
enum class PendingFolderCleanupResult { Complete, RetryRequired }

interface FolderMappingRepository {
    suspend fun prepareForSignOut(): PendingFolderCleanupResult
}
```

`AuthViewModel` receives a suspend pre-sign-out callback. It enters
`SigningOut`, runs the callback, then always invokes `AuthSessionManager.signOut`.
A retry-required result maps to a non-sensitive signed-out warning. A picker
callback observed after `SigningOut` begins is discarded and its retained
activity token is cleared.

- [x] Write repository tests for requested, staged, same-URI referenced, release
  failure, cancellation, retry, and concurrent callback ordering.
- [x] Run the focused tests and confirm they fail because the interface and
  compensation path do not exist.
- [x] Implement the minimum serialized compensation and auth ordering.
- [x] Run repository, auth ViewModel, and app-composition tests to green.
- [x] Review the diff for auth lifecycle and grant-reference regressions.

Completed in `66a3114` and `8eee747`. The review found a second-order race in
which a late pre-sign-out result could consume a post-sign-in replacement.
Per-launch Activity Result registry keys now survive recreation, retired
callbacks are consumed only by their original key, and the replacement remains
untouched. The final related Samsung user-0 suite passed 58 tests.

## Task 2: Typed Live Folder Access Health

**Files:**

- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/FolderAccessHealth.kt`
- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/LocalFolderAccessValidator.kt`
- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/saf/ContentResolverLocalFolderAccessValidator.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/FolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/app/AppContainer.kt`
- Create: `app/src/androidTest/AndroidManifest.xml`
- Create: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/saf/ControllableDocumentsProvider.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/saf/ContentResolverLocalFolderAccessValidatorInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepositoryInstrumentedTest.kt`

**Interfaces:**

```kotlin
enum class FolderAccessHealth {
    Checking,
    Ready,
    PermissionMissing,
    TreeMissing,
    ProviderAuthRequired,
    TemporarilyUnavailable,
}

sealed interface ValidateFolderAccessResult {
    data class Found(val health: FolderAccessHealth) : ValidateFolderAccessResult
    data object MappingNotFound : ValidateFolderAccessResult
    data object StorageFailure : ValidateFolderAccessResult
}

fun interface LocalFolderAccessValidator {
    suspend fun validate(treeUri: String): FolderAccessHealth
}
```

The adapter checks the exact persisted read grant first, builds the root
document URI with `DocumentsContract`, and validates a single directory row.
Provider authentication is classified before generic security failure. A null
cursor or provider/malformed response is temporary; an absent root row is
`TreeMissing`; cancellation propagates. A generic `SecurityException` triggers
one fresh exact-grant check: a disappeared read grant is `PermissionMissing`,
while a still-present grant is `TemporarilyUnavailable`. A provider-reported
missing root is `TreeMissing`; it is never inferred only from exception text.
Android's standard `DocumentsProvider` query bridge converts a provider
`FileNotFoundException` into a null cursor, so that ambiguous framework path is
temporary rather than falsely claiming the tree is gone. A direct preserved
missing-root exception or a valid empty root cursor is an explicit
`TreeMissing` signal.

The Task 2 provider foundation supports controlled root queries only. It lives
in the test APK, uses a variant-safe authority, remains disabled in the
installed manifest so Android cannot initialize it before the instrumentation
classloader is ready, and records calls without raw identifiers. Tests attach
the provider directly through `ContentResolver.wrap`. Task 5 extends the same
provider with child traversal and blocking query controls.

- [x] Write failing tests for every health state, invalid tree, missing/multiple
  root row, wrong MIME/ID, missing columns, extras, null cursor, cancellation,
  and non-sensitive failure handling.
- [x] Implement the validator and repository mapping lookup.
- [x] Run focused SAF/repository tests and both-flavor compilation.
- [x] Review callers, DI bindings, API-24 compatibility, and provider-resource closure.

Completed on the active branch after witnessed compile/runtime RED cases. The
Samsung user-0 validator suite passed 29 tests and the repository suite passed
44 tests. Both flavors passed JVM tests, app assembly, Android-test compilation,
and Android-test APK assembly. `Checking` is presentation-only and is rejected
as a terminal repository result. The test-provider manifest uses a fully
qualified class name and a variant-specific authority. API 24-28 execution of
the in-process `ContentResolver.wrap` fault harness remains part of the deferred
hardware matrix; production code is min-SDK-safe and the API-26 authentication
type is isolated behind a guarded implementation.

## Task 3: Enable And Disable Configuration

**Files:**

- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/FolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessViewModel.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepositoryInstrumentedTest.kt`
- Test: `app/src/test/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessViewModelTest.kt`

**Interfaces:**

```kotlin
enum class SetFolderEnabledResult {
    Updated,
    MappingNotFound,
    StorageFailure,
}

interface FolderMappingRepository {
    suspend fun setEnabled(mappingId: String, enabled: Boolean): SetFolderEnabledResult
}
```

- [x] Write failing tests for true/false persistence, idempotence, missing row,
  database failure, inactive presentation, and stale work after disable.
- [x] Implement the DAO-backed repository call and generation-safe ViewModel action.
- [x] Prove enable/disable does not invoke health validation or grant work.
- [ ] Prove disabled-but-ready remains manually scannable when Task 6 wires scan admission.
- [x] Run focused repository and ViewModel suites.

The repository persists idempotent enablement changes under the same mutation
mutex used by add, repair, and remove. Presentation owns one generation-scoped
job per mapping, permits independent mappings to update concurrently, and
prevents cancelled or inactive work from clearing replacement progress or
publishing stale notices. Compile RED was witnessed before the contract and
implementation existed. The focused ViewModel suite passed on the JVM; all 47
repository tests, all 29 root-validator tests, and all 4 app-composition tests
passed on Samsung user 0. Both flavors passed complete JVM tests plus app and
Android-test APK assembly. The disabled-but-ready scan condition remains an
explicit Task 6 acceptance dependency rather than being inferred before a
scanner exists.

## Task 4: Iterative Read-Only Metadata Scanner

**Files:**

- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/LocalFolderScanner.kt`
- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/saf/LocalFolderDocumentSource.kt`
- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/saf/ContentResolverLocalFolderDocumentSource.kt`
- Create: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/saf/IterativeLocalFolderScanner.kt`
- Create: `app/src/test/java/com/aryasubramani/vijibackup/folderaccess/saf/IterativeLocalFolderScannerTest.kt`

**Interfaces:**

```kotlin
data class FolderScanProgress(
    val foldersVisited: Long,
    val filesDiscovered: Long,
    val knownBytes: Long,
    val filesWithUnknownSize: Long,
    val unreadableEntries: Long,
)

data class FolderScanSummary(
    val progress: FolderScanProgress,
    val elapsedTimeMillis: Long,
    val issues: Set<FolderScanIssue>,
)

enum class FolderScanIssue {
    InvalidTree,
    NullCursor,
    ProviderLoading,
    ProviderError,
    QueryFailure,
    MalformedEntry,
    RepeatedDocument,
}

sealed interface FolderScanEvent {
    data class Progress(val value: FolderScanProgress) : FolderScanEvent
    data class Complete(val summary: FolderScanSummary) : FolderScanEvent
    data class Partial(val summary: FolderScanSummary) : FolderScanEvent
    data class Failed(val summary: FolderScanSummary) : FolderScanEvent
}

fun interface LocalFolderScanner {
    fun scan(treeUri: String): Flow<FolderScanEvent>
}

internal interface LocalFolderDocumentSource {
    fun rootDocumentId(treeUri: String): String?

    suspend fun queryChildren(
        treeUri: String,
        parentDocumentId: String,
        onDocument: suspend (FolderDocumentMetadata) -> Unit,
    ): FolderDocumentQueryResult
}

internal data class FolderDocumentMetadata(
    val documentId: String,
    val isDirectory: Boolean,
    val size: Long?,
)

internal data class FolderDocumentQueryResult(
    val cursorReturned: Boolean,
    val unreadableEntries: Long,
    val issues: Set<FolderScanIssue>,
)
```

`FolderScanSummary` adds elapsed milliseconds and a set of issue codes to the
progress counters. Arithmetic is overflow-safe. The stream emits monotonic
progress and exactly one terminal event while collected; coroutine cancellation
propagates without attempting a terminal emission.

`LocalFolderDocumentSource` exposes an internal nullable root-document-ID
lookup so the scanner never parses a provider URI itself. Its query callback
streams sanitized rows instead of returning an in-memory listing. It never
returns a raw URI, filename, document ID outside the scanner package, cursor,
or exception. The production source owns every `ContentResolver` query and
cursor lifetime. The iterative scanner owns queueing, cycle detection, counters,
and the terminal decision oracle. This split permits fast hostile-path unit
tests without claiming that a fake source proves Android provider behavior.

- [x] Write failing pure scanner tests for empty, nested, deep, wide, duplicate,
  self-cycle, multi-node cycle, null metadata, unknown size, overflow, malformed
  columns/rows, null cursor, loading/error extras, query exception, cancellation
  before/during/between queries, and cursor/signal closure.
- [x] Implement an iterative queue and bounded aggregate state without retaining
  names or all rows.
- [x] Run the scanner suite and audit production source for prohibited mutations.

Compile RED was witnessed before the scanner contracts existed. Fourteen pure
scanner tests now cover cold collection, empty/nested/deep/wide trees, repeated
identities and cycles, unknown sizes, saturating counters, exact terminal
decisions, source failures, privacy, backpressure, and cancellation before or
during traversal. The 10,000-level tree completes iteratively without using the
call stack. The resolver source requests only the six documented metadata
columns, owns every cursor, creates one cancellation signal per query, and does
not expose names, raw identifiers, URIs, cursors, or exceptions outside the
scanner package.

## Task 5: Resolver-Facing Hostile Provider Tests

**Files:**

- Modify: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/saf/ControllableDocumentsProvider.kt`
- Create: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/saf/ContentResolverLocalFolderScannerInstrumentedTest.kt`

The provider is exported only by the test APK, exposes no production authority,
records every framework query and prohibited mutation callback, and can produce
empty/nested/deep trees, repeated IDs, malformed rows, null cursors, extras,
security/auth/provider failures, and a cancellable blocked query.

- [x] Write resolver-facing tests against the production scanner before adding
  provider behavior needed to pass them.
- [x] Prove cursors close on terminal paths and each query receives a fresh
  cancellation signal that is cancelled with its coroutine.
- [x] Prove zero create/delete/rename/move/open-for-write calls.
- [x] Run the provider suite directly on Samsung Android user 0 for both flavors.

Seven production-scanner provider tests passed directly on Samsung user 0 for
both internal and public flavors. They cover nested data, malformed rows and
columns, null cursors, loading/error extras, provider exceptions, repeated IDs,
active-query cancellation, retry with a fresh signal, cursor closure, privacy,
and prohibited-mutation counters. The existing 29-test root-validator suite
also passed after the provider extension, and the complete two-flavor JVM/app/
Android-test APK matrix remained green. API 24-28 execution of the API-29
`ContentResolver.wrap` harness remains in the deferred hardware matrix; the
production code itself uses APIs available at the app's API-24 minimum.

## Task 6: Per-Mapping Health And Scan Orchestration

**Files:**

- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/domain/FolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/data/RoomFolderMappingRepository.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessViewModel.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/app/MainActivity.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/app/AppContainer.kt`
- Test: `app/src/test/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessViewModelTest.kt`

**Interfaces:**

```kotlin
sealed interface BeginFolderScanResult {
    data class Ready(val events: Flow<FolderScanEvent>) : BeginFolderScanResult
    data class AccessUnavailable(
        val health: FolderAccessHealth,
    ) : BeginFolderScanResult
    data object MappingNotFound : BeginFolderScanResult
    data object StorageFailure : BeginFolderScanResult
}

interface FolderMappingRepository {
    suspend fun beginScan(mappingId: String): BeginFolderScanResult
}
```

The ViewModel owns independent health and scan jobs keyed by mapping ID. Every
job has a monotonically increasing generation. Repair, remove, disable,
deactivation, and explicit cancel invalidate the current generation before
cancelling its job. A late event may never update replacement state.

- [x] Write failing tests for initial/foreground refresh, all health states,
  Ready-only scan admission, disabled manual scan, monotonic progress, exact
  terminal mapping, cancel-vs-complete, repair/remove/disable races, mapping
  disappearance, deactivation, and two-mapping isolation.
- [x] Implement repository scan-source validation and ViewModel job ownership.
- [x] Run focused tests repeatedly under the coroutine test scheduler.
- [x] Review all repository fakes and composition bindings affected by the interface change.

## Task 7: Protected Folder Controls And Accessibility

**Files:**

- Modify: `app/src/main/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessScreen.kt`
- Modify: `app/src/main/java/com/aryasubramani/vijibackup/app/VijiBackupApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/presentation/FolderAccessScreenInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/aryasubramani/vijibackup/app/AppCompositionInstrumentedTest.kt`

Each row exposes an enabled switch, health text, stable progress area, scan or
cancel button, repair, and remove. Controls use stable test tags and content
descriptions. Disabled mappings can scan when Ready. Degraded mappings keep
repair/remove usable. No raw identifier is rendered.

- [x] Write failing Compose tests for every health/scan state and action, long
  counts, fallback names, enlarged font, disabled mapping scan, degraded repair,
  stale-state replacement, auth gating, and recent-apps policy.
- [x] Implement the smallest complete control surface and resource copy.
- [x] Run Compose and app-composition coverage across the two-flavor verification matrix.

## Task 8: Full Verification And Live Samsung Closure

**Files:**

- Modify: `app/src/androidTest/java/com/aryasubramani/vijibackup/folderaccess/LiveFolderAccessStateProbeInstrumentedTest.kt`
- Modify: `drive backup KB/Drive Backup App Project State.md`
- Modify: `drive backup KB/Drive Backup App Testing Plan.md`
- Modify: `drive backup KB/Drive Backup App Source Register.md`
- Modify: `drive backup KB/Drive Backup App Fresh Laptop Setup And Test Runbook.md`
- Modify: `drive backup KB/Drive Backup App Phase 3 Local Folder Access Implementation Plan.md`

- [x] Run both-flavor unit, build, Android-test APK, and lint tasks from a clean
  configuration-cache state.
- [x] Install app and test APKs without clearing production app data.
- [x] Run direct instrumentation with `am instrument --user 0`; do not use the
  Gradle connected runner if it probes Android user 150.
- [x] Capture before/after mutation-sentinel manifests locally for only the
  dedicated Viji Backup test tree.
- [x] Run every safe `FOLDER-LIVE-*` case: picker cancel, add, read-only grant,
  exact duplicate, edge-case scan, real read-only folder scans, cancel/isolation,
  force-stop/relaunch, grant release/repair, same-URI repair, dedicated-tree
  move/repair, broken/healthy isolation, co-admin identity, controlled remove,
  in-place upgrade, recent-apps redaction, and redacted log audit.
- [x] Never infer which existing mapping is dispensable. The operator explicitly
  identifies the dedicated mapping before any live remove or move action.
- [x] Record only counts, status codes, and pass/fail evidence in Git.
- [x] Run tracked-source and reachable-patch secret/personal-identifier scans
  and the final requirements checklist before the closure push; no configured
  values are present in content. Keep the two documented pre-cleanup merge
  metadata emails in Project State as separate history-rewrite work.
- [ ] Run the intentionally deferred whole-branch review before merge; do not
  repeat the completed live matrix unless the review changes relevant behavior.

The 2026-07-16 live closure restored the dedicated test fixture after every
controlled mutation and ended at 3 named mappings, 3 read grants, 0 write
grants, and no pending picker operation. All tracked evidence is aggregate and
redacted; raw manifests, screenshots, logs, account data, URIs, and identifiers
remain outside Git.

## Canonical Verification Commands

```bash
./gradlew \
  :app:testInternalDebugUnitTest \
  :app:testPublicDebugUnitTest \
  :app:assembleInternalDebug \
  :app:assemblePublicDebug \
  :app:assembleInternalDebugAndroidTest \
  :app:assemblePublicDebugAndroidTest \
  :app:lintInternalDebug \
  :app:lintPublicDebug
```

Direct physical execution uses the connected device selected without printing
its serial, then explicitly targets Android user 0. Package names remain:

```text
com.aryasubramani.vijibackup.internal
com.aryasubramani.vijibackup.internal.test
com.aryasubramani.vijibackup
com.aryasubramani.vijibackup.test
```

## Deferred Hardware Matrix

API 24, API 30, API 36, removable-storage ejection, physical reboot, and actual
cross-device transfer remain release-gate deferrals. They are not silently
converted into Phase 3 passes.

## Next Notes

- [[Drive Backup App Phase 3 Local Folder Access Implementation Plan]]
- [[Drive Backup App Project State]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Source Register]]
