---
doc_id: drive-backup-app-phase-3-local-folder-access-implementation-plan
status: active
last_updated: 2026-07-13
context_role: implementation-plan
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
execution_status: implementing
read_when:
  - The agent implements, reviews, or tests Phase 3 local folder access.
  - The agent changes folder mappings, SAF URI grants, local tree scanning, or permission repair.
do_not_read_when:
  - The task is only Google Drive authorization, file upload, scheduling, email, or release packaging.
---

# Drive Backup App Phase 3 Local Folder Access Implementation Plan

## Goal Capsule

Implement a read-only local-folder boundary that lets an approved user select
folders Android permits, retain access across a real process restart, inspect
permission health, repair lost access, and scan every readable descendant
without one provider or file failure stopping unrelated mappings.

The Phase 3 exit gate is physical-device evidence that a selected folder can be
fully enumerated after force-stop and relaunch without reopening the picker.
Automated tests and synthetic providers are supporting evidence only. They are
not substitutes for the live Samsung test.

Phase 3 does not request Google Drive authorization, create Drive files, upload
local content, schedule work, or send email. Those boundaries remain in later
roadmap phases. When Drive behavior is introduced in Phase 4, its acceptance
tests must use the real signed-in account and shared Drive folder, not a fake
Drive implementation.

## Implementation Checkpoint

As of 2026-07-13, Room schema 1, durable picker correlation, read-only grant
acquisition/reconciliation, add, repair, display-name resolution/backfill,
confirmed removal, and the approved folder UI are implemented on the Phase 3
branch. Process-scope approval survives Home, DocumentsUI, and activity
recreation; a cold process still reauthenticates.

Health classification, iterative scanning, scan cancellation, enable/disable,
sign-out compensation for pending picker work, and the full restart/upgrade live
matrix remain incomplete. Phase 3 must not be described as complete yet.

## Confirmed Product Inputs

- Folder configuration belongs to the Android app installation, not to one of
  the alternate approved Google email addresses used on that installation.
- Android already isolates app data and URI grants by Android user profile.
- Every approved identity configured for one installation is an intentional
  co-administrator of its folder mappings. Before unrelated people are added to
  the allowlist, mappings must be redesigned with explicit profile ownership.
- Every new user action is protected by the Phase 2 auth gate. The approved
  process remains approved while DocumentsUI or Home is foregrounded; a cold
  process reauthenticates. Picker completion may only consume the exact durable
  request that launched that picker.
- Local access is read only. The app never renames, edits, moves, or deletes a
  source file or source folder.
- Removing a mapping only removes app configuration and releases its URI grant.
  It never deletes local or future Drive content.
- Exact duplicate mappings are rejected. Parent/child overlap policy belongs to
  Phase 5, where duplicate upload and destination semantics are available.
- The current SAF source accepts only locations the system picker grants. It
  does not request broad storage access. A dedicated full-Downloads source is a
  separate unresolved product/security decision.

## Non-Negotiable Android Limits

For Android 11 and newer, including the Samsung Android 14 baseline, the system
folder picker does not grant tree access to:

- the root of internal storage;
- the root of a reliable removable volume;
- the Downloads root;
- `Android/data` or any descendant;
- `Android/obb` or any descendant.

A subfolder inside Downloads can be selected when the provider permits it. The
exact Downloads root cannot be promised under the chosen SAF model. Adding
`MANAGE_EXTERNAL_STORAGE` would be a separate security and product decision and
must not be introduced silently in this phase.

`Intent.EXTRA_LOCAL_ONLY` narrows the picker toward local providers, but Android
documents it as a hint. It is not proof that a returned tree is primary internal
storage. Provider authorities and document IDs must remain opaque; do not parse
them into filesystem paths or hard-code one OEM provider. The enforceable
contract is therefore "a user-selected SAF tree returned by a local-only picker
request," not "cryptographically proven local storage." Live acceptance selects
known internal-storage roots. Phase 4 must obtain renewed user confirmation
before reading a provider-backed tree for upload.

## Architecture Decision Record

### Feature Ownership

Add one feature package:

```text
com.aryasubramani.vijibackup.folderaccess/
  domain/
  data/
    db/
  saf/
  presentation/
```

Folder access must not be implemented inside `auth`. Existing Phase 2 auth state
and session rules remain unchanged. The approved branch of the app composes the
folder screen; every non-approved state continues to hide all folder metadata.

One `FolderAccessViewModel` owns user-driven workflow ordering. `MainActivity`
only launches the picker, retains the launched request token across recreation, and
returns the result. The repository owns durable changes and compensation. The
ViewModel rejects add, scan, enable/disable, repair, and remove calls unless it
currently holds an approved actor supplied from `AuthUiState.Approved`.

Keep the current application-scoped manual dependency container. Do not add
Hilt, Navigation, WorkManager, a generic `core` package, or Drive dependencies
for this phase.

### Persistence Boundary

Use Room 2.8.4 with KSP 2.3.9/KSP2 for folder mappings and the durable
pending-picker record. Keep Preferences DataStore for auth metadata and future
small scalar settings. Use database file `viji_backup.db`.

Room is required because mappings need independent CRUD, a uniqueness
constraint, transactions, schema migrations, and later relationships to Drive
destinations, file ledger entries, and sync history. Android's DataStore guide
explicitly directs large or complex data, partial updates, and referential
integrity to Room.

Export and commit Room schema version 1 from the first database commit. Every
future schema version requires an explicit migration and migration test. Never
use destructive migration for user configuration.

Database and persisted SAF URI metadata remain excluded from Android cloud
backup and device transfer. A URI restored onto another device would not carry
the corresponding Android permission grant. Both backup rule files exclude the
entire `database` domain so the main database, journal, and WAL side files cannot
be transferred independently.

Toolchain integration is explicit:

- version-catalog aliases for Room 2.8.4 and KSP 2.3.9;
- root and app plugin aliases for `androidx.room` and
  `com.google.devtools.ksp`;
- Room runtime, compiler, and Android-test dependencies;
- `schemaDirectory("$projectDir/schemas")` through the Room Gradle plugin;
- both-flavor compile, schema export, and test verification.

Room coroutine/Flow APIs are already in `room-runtime`; do not add the now-empty
`room-ktx` artifact.

### Mapping Schema

`local_folder_mappings` stores only durable configuration:

| Field | Contract |
|---|---|
| `id` | App-generated UUID string primary key |
| `tree_uri` | Exact content tree URI string with a unique index |
| `source_display_name` | Nullable provider-derived root label, resolved only while approved |
| `enabled` | Whether future sync may include the mapping |

Do not persist a boolean such as `permissionValid` as truth. Permission and tree
health are recalculated from Android's current persisted grants and a live root
query. A future diagnostic snapshot may record when validation ran, but it may
never bypass live validation.

Mappings are installation-scoped. Do not add an email or Google subject foreign
key to the mapping table. The approved identities are alternate credentials for
the same trusted personal backup installation. Revisit profile scoping before
supporting unrelated people inside one Android user profile.

All approved identities can view, scan, enable/disable, repair, and remove every
mapping in that installation. This co-administrator behavior receives an
explicit A-to-B live test and is not inferred from email similarity.

### Pending Picker Schema

`pending_folder_operations` stores at most one active operation:

| Field | Contract |
|---|---|
| `slot` | Constant singleton primary key; repository never inserts another slot |
| `request_token` | App-generated UUID launch token, separate from the singleton key |
| `operation` | `ADD` or `REPAIR` |
| `target_mapping_id` | Required only for repair |
| `selected_tree_uri` | Null before result; stored before grant acquisition |
| `state` | `REQUESTED`, `SELECTION_RECEIVED`, or `ABANDONING` |
| `created_at_epoch_ms` | Used to expire abandoned work |

`begin` is a serialized Room transaction that rejects an occupied slot before
emitting a launch. The activity captures that request token with the launcher and
passes it back alongside URI and result flags. The repository ignores stale or
mismatched tokens without taking a persistent grant. Tokens are opaque and are
never derived from process time, account identity, provider data, or a resettable
in-memory counter.

### Public Interfaces

Keep the production seams narrow:

- `FolderMappingRepository`: observe mappings, begin and complete picker
  operations, cancel/expire pending work, enable/disable, remove, validate, and
  reconcile grants.
- `LocalFolderGrantManager`: acquire/release read-only grants and inspect current
  persisted grants.
- `LocalFolderMetadataReader`: query and sanitize provider-derived root labels
  without exposing provider IDs or raw URIs.
- `LocalFolderScanner`: expose a cancellable cold stream of scan events from a
  persisted tree grant.

The repository coordinates Room and Android grant compensation. Presentation
does not call `ContentResolver`, DAOs, or URI-permission APIs directly. The
application-scoped repository owns a shared `Mutex`; initialization,
reconciliation, picker completion, repair, remove, and grant cleanup are
serialized through it.

Every repository entry point waits for an idempotent initialization barrier.
Initialization loads pending state first, reconciles grants second, and then
opens the barrier. A cancelled initialization can be retried; no picker result
or user mutation can race orphan cleanup.

Inject an IO dispatcher. Every `ContentResolver` query and root metadata read
runs on that dispatcher, never on the activity or Compose main thread.

## Read-Only Picker Contract

Register the folder picker at the activity root so its callback remains alive
when protected Compose content leaves composition during an auth-state change.

Use a small `ActivityResultContract` based on `ACTION_OPEN_DOCUMENT_TREE` that:

- requests `FLAG_GRANT_READ_URI_PERMISSION`;
- requests persistable and prefix grants;
- never requests `FLAG_GRANT_WRITE_URI_PERMISSION`;
- adds `EXTRA_LOCAL_ONLY=true`;
- optionally supplies `EXTRA_INITIAL_URI` for repair on API 26 and newer;
- does not add `CATEGORY_OPENABLE`;
- returns the URI and actual result flags to the activity callback;
- maps a null URI or cancelled result to cancellation, never provider failure.

After selection, persist only the read flag. Verify that the exact URI appears
in `ContentResolver.persistedUriPermissions` with read enabled and write
disabled. If an older development build left a write grant for the same tree,
release the write capability and revalidate the read grant.

Do not add `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `WRITE_EXTERNAL_STORAGE`, or
`MANAGE_EXTERNAL_STORAGE` to the manifest.

## Auth And Picker State Flow

Approval is process-scoped. Home, DocumentsUI, and activity recreation must not
trigger another Google chooser while the same approved ViewModel/process is
alive. A new process restores cached identity only as
`ReauthenticationRequired` and must prove approval again.

```text
Approved actor
  -> serialize begin ADD or REPAIR into singleton slot with request token R
  -> launch system picker
  -> app backgrounds while current-process approval remains valid
  -> callback with R and cancellation: clear matching pending row
  -> callback with stale request token: ignore without taking a grant
  -> callback with R and URI: durably store URI as SELECTION_RECEIVED
  -> take and verify read-only persistent grant
  -> atomically add/repair mapping and clear pending row
  -> query safe root metadata; commit a nullable display label
  -> expose mapping UI and refresh labels during approved reconciliation
```

Picker completion is the bounded completion of an action approved before launch.
New actions still require a current approved actor. A cold-process recovery may
finalize a durably staged selection during repository reconciliation, but it
must not expose protected metadata until authentication is approved.

Only one picker operation may exist. Duplicate taps do not launch concurrent
pickers. The activity retains launched request token R across recreation. A process
restart restores the pending record before grant reconciliation so a received
selection is not mistaken for an orphan.

The app-level sign-out callback first serializes pending cleanup. It marks a
pending row `ABANDONING`, attempts idempotent release of any unreferenced staged
grant, and only then invokes existing auth sign-out. An `ABANDONING` row is never
considered live during startup reconciliation. Failure to release is retried on
next initialization and reported as a non-sensitive warning; sign-out itself is
not denied.

Pending operations older than 24 hours are abandoned at startup. This timeout
is cleanup, not a security credential lifetime.

## Transaction And Compensation Rules

Android URI grants and Room transactions cannot be one atomic transaction.
Every operation therefore has an explicit compensation path.

### Add

1. Confirm callback request token matches the singleton pending row.
2. Validate `content://` scheme and tree-URI shape.
3. Reject an exact URI already stored.
4. Store URI as `SELECTION_RECEIVED` before taking a persistent grant.
5. Confirm the returned flags include a persistable read grant.
6. Take the persistable read grant.
7. Confirm read is persisted and write is not persisted.
8. Query and sanitize the root display label. Provider/metadata failure yields
   a nullable label and must not fail an otherwise valid selection.
9. Insert the mapping and delete the pending row in one Room transaction.
10. If a post-grant step fails, mark pending work `ABANDONING`, then release the
   URI only after checking every durable and pending reference.

Existing mappings with verified read grants refresh missing or changed display
labels during initialization. Exact duplicates remain blocked on every API.
Parent/child overlap is deferred to Phase 5 rather than guessed from opaque
provider IDs.

### Repair

1. Preserve mapping ID, enabled state, and future Drive identity.
2. Store the replacement URI in the matching pending row before taking a grant.
3. If replacement URI exactly equals current URI, clear pending state and
   revalidate later; never release that URI on success, failure, cancellation,
   expiry, sign-out, or restart.
4. Otherwise take and verify the replacement read grant before changing Room.
5. Update the mapping and clear pending state in one Room transaction.
6. Release the old URI only after Room points to the replacement and no durable
   or pending record references the old URI.
7. If replacement persistence fails, abandon the replacement, release it only
   when fully unreferenced, and retain the original mapping and grant.

### Remove

1. Reject removal while any picker operation is pending.
2. Resolve the exact mapping or report `MappingNotFound` without touching grants.
3. Release and verify the mapping's persisted grant first.
4. If release fails, keep the mapping unchanged and report a non-sensitive,
   retryable grant failure.
5. Delete the mapping only after verified release.
6. If Room deletion fails after release, keep the mapping visible. A retry
   performs the idempotent release again and then retries deletion.
7. Give each presentation-layer removal an operation generation. Deactivation
   invalidates the active generation immediately; a late completion from an
   older generation cannot publish a notice, clear newer progress, or admit a
   competing mutation.

No remove path calls a document delete API.

### Startup Reconciliation

- Load pending operations before releasing any orphan grant.
- Load all durable mapping URIs and Android persisted grants.
- Resume `SELECTION_RECEIVED` when its matching read grant exists by committing
  the already-authorized add/repair. Resolve optional root metadata, but never
  let metadata failure block recovery.
- Abandon `SELECTION_RECEIVED` when no matching persisted grant exists.
- Retry every `ABANDONING` cleanup before accepting new picker work.
- A mapping without exact persisted read access has `PermissionMissing` health.
- A grant with no mapping or live pending reference is released as orphaned.
- A persisted write capability is reduced to read only.
- A root that no longer resolves is classified separately from missing grant.
- A provider exception does not delete the mapping.
- Corrupt Room state fails closed and never triggers local-file deletion.

### Crash-Point Matrix

Use controllable test pauses at each non-atomic boundary, terminate the test
process, restart the real repository, and verify exact Room/pending/grant state:

| Crash point | Required recovery |
|---|---|
| Selection stored before grant acquisition | No grant means abandon pending work |
| Grant acquired before add transaction | Matching pending URI and grant finalize mapping |
| Replacement mapping committed before old grant release | Keep replacement; release old orphan |
| Grant released before mapping delete | Keep mapping visible; retry idempotent release and delete |
| `ABANDONING` stored before cleanup | Retry release idempotently; never revive operation |

The test pause seam is available only to tests and never changes production
ordering. Every release check first evaluates durable mappings and pending
references, including same-URI repair.

## State Contract

Configuration, access health, and scan state are orthogonal.

`enabled` is durable configuration. It never hides current access health.

| Access health | Meaning |
|---|---|
| `Checking` | Persisted grant and root are being verified |
| `Ready` | Exact persisted read grant exists and root metadata is readable |
| `PermissionMissing` | Persisted read grant is absent |
| `TreeMissing` | Grant exists but selected root was moved or deleted |
| `ProviderAuthRequired` | Provider reports its own authentication requirement |
| `TemporarilyUnavailable` | Provider failed temporarily or returned null |

Catch `AuthenticationRequiredException` before the broader `SecurityException`.
Do not collapse provider authentication, absent app grant, missing tree, and
temporary provider failure into one repair message.

| Scan state | Meaning |
|---|---|
| `NotRun` | No scan has run in this process |
| `Running` | Progress counters are active |
| `Complete` | Every discovered directory query completed without issues |
| `Partial` | At least one directory cursor was returned, but an issue prevented completeness |
| `Failed` | No directory query returned a usable cursor |
| `Cancelled` | The owning scan job was cancelled |

Action availability:

- enable/disable and remove are always available while approved;
- repair is available for every access health except an active picker operation;
- scan requires `Ready`; a disabled but `Ready` mapping may be scanned manually;
- cancelling a scan changes only scan state;
- removing or repairing during a scan first cancels that mapping's job and
  rejects all stale progress events by scan generation.

Raw exception text, raw URIs, document IDs, and account subjects never enter
logs or durable diagnostics. Filenames do not enter logs or Phase 3 diagnostics.
Later sync history and completion email may retain the minimum relative names
required by the product, under explicit retention and privacy rules.

## Scanner Contract

Use `ContentResolver` and `DocumentsContract` directly. Do not use recursive
`DocumentFile` traversal because Android documents substantial overhead.

Traversal is iterative and requests only:

- document ID;
- display name;
- MIME type;
- document flags;
- size;
- last modified time.

Rules:

- Use a queue or stack, not call-stack recursion.
- Treat document IDs as opaque provider identifiers.
- Track visited provider/document identities to stop cycles and repeated trees.
- Keep relative names as path segments, never concatenate them into a local
  filesystem path.
- A null size or modified time is valid unknown metadata.
- A non-null empty cursor is an empty directory.
- A null cursor is a provider failure, not an empty directory.
- `EXTRA_LOADING=true` or `EXTRA_ERROR` makes the result incomplete.
- `EXTRA_INFO` is informational and does not change completeness.
- Unknown rows and unreadable entries become per-entry issues while traversal
  continues with unrelated siblings.
- The scanner processes one mapping. Its caller isolates jobs so one mapping
  failure never cancels another mapping.
- A fresh `CancellationSignal` is connected to each provider query.
- Coroutine cancellation is checked between every row and directory.
- `OperationCanceledException` maps to cancellation, not scan failure.
- Do not accumulate file contents or every entry in memory merely to show a
  summary.

The cold scan stream emits progress events and, while collection remains active,
terminates with exactly one of:

- complete summary;
- partial summary plus classified issues;
- failed result;

Normal coroutine cancellation terminates the Flow with `CancellationException`;
it does not attempt an emission to a cancelled collector. The ViewModel maps the
matching job completion to `Cancelled` and rejects later events. A cancel versus
completion race must produce exactly one UI terminal state.

Decision oracle:

- if no directory query returns a cursor, result is `Failed`;
- after any directory query returns a cursor, later null cursor, exception,
  loading/error extra, malformed row, or unreadable entry makes it `Partial`;
- result is `Complete` only when every enqueued directory query finishes and no
  entry is skipped;
- a returned empty cursor is valid progress and can complete an empty tree.

Summary fields include folders visited, files discovered, known bytes, files
with unknown size, unreadable entries, and elapsed time. Phase 3 never opens a
file for upload or computes content hashes.

All provider I/O runs on the injected IO dispatcher. Cancellation of the
collecting coroutine cancels the active `CancellationSignal`, closes every
cursor, and then propagates cancellation.

## Presentation Contract

The approved app surface becomes a compact folder-management screen:

- app bar with app identity and sign-out action;
- add-folder action;
- empty state when no mappings exist;
- one row/card per mapping with source name, enabled switch, access health, and
  independent scan state;
- scan and cancel controls;
- overflow actions for repair and remove;
- confirmation before removal;
- stable progress dimensions so counts do not shift controls;
- no raw URI or account subject on screen.

Every control has a content description and stable test tag. Repair and remove
must remain usable in degraded states. Scanning a disabled mapping manually is
allowed for diagnostics but does not re-enable future sync. Until provider
metadata resolves, use a generic localized folder label rather than deriving a
path or exposing a URI.

Protected metadata must not remain in the recent-apps snapshot. On API 33 and
newer call `setRecentsScreenshotEnabled(false)`. On API 24 through 32 apply
`FLAG_SECURE` while protected folder content is rendered. The Samsung test must
background a populated screen and verify the task preview is redacted.

## TDD Execution Slices

Each slice follows red, green, refactor. Add one externally observable behavior
test, implement the minimum production behavior, run the focused test, then run
the blast-radius suite before the next slice.

### S1. Room Foundation

- Add Room 2.8.4, KSP 2.3.9, `viji_backup.db`, variant-aware schema export, and
  database-domain backup exclusions.
- Prove mapping insert/observe/update/delete and unique URI behavior against a
  real Room database.
- Prove singleton pending slot and serialized begin behavior.
- Prove database recreation preserves records.
- Prove schema 1 is exported for both flavors.

### S2. Read-Only Picker Result

- Prove request/cancel state has no mapping side effect.
- Prove duplicate launch suppression.
- Prove the launched request token survives recreation and stale callbacks are
  ignored before a persistent grant is taken.
- Prove actual granted flags are retained for validation.
- Prove auth-first and picker-result-first event orders converge.
- Live Samsung gate: picker opens and returns a real local tree.

### S3. Grant Acquisition And Add Compensation

- Prove invalid and duplicate trees fail before mapping insertion.
- Prove read grant is required and write grant is absent.
- Prove Room failure after grant acquisition releases an unreferenced grant.
- Prove the initialization barrier serializes reconciliation and picker result.
- Live Samsung gate: add a dedicated real test tree.

### S4. Auth Lifecycle, Sign-Out, And Crash Recovery

- Prove picker/Home backgrounding retains current-process approval without a
  duplicate sign-in request.
- Prove cold-process restoration still requires reauthentication.
- Prove no new user-initiated folder action or mapping observation starts while
  non-approved.
- Prove an exact previously authorized callback may complete its opaque mapping
  transaction after an auth-state or lifecycle transition.
- Prove any approved co-administrator can use a completed mapping.
- Prove cancellation, sign-out, expiry, stale result, and process recreation
  compensate grants without reviving abandoned work.
- Execute every crash point in the compensation matrix.
- Prove ActivityResult registration survives protected-content replacement.

### S5. Health And Repair

- Prove enabled state, access health, and scan state remain orthogonal.
- Prove permission missing, tree missing, provider auth, temporary failure, and
  ready access stay distinct.
- Prove repair commits new mapping state before old grant release.
- Prove failed repair retains old state.
- Prove same-URI repair never releases the referenced grant on any terminal path.
- Live Samsung gate: release the app's real persisted grant through an
  instrumentation-only action, force-stop, preserve mapping, repair, and rescan.
- Live Samsung gate: move the dedicated test tree, observe failure, and repair.

### S6. Iterative Scanner

- Prove empty, nested, deep, duplicate-name, null-metadata, cycle, partial,
  provider-failure, cancellation, and multi-mapping isolation behavior.
- Prove cursor and cancellation resources close on every terminal path.
- Exercise production `ContentResolver` code through a controllable
  instrumentation-only `DocumentsProvider` that can return null cursors,
  extras, exceptions, cycles, and blocked queries and can record prohibited
  mutation calls. This synthetic provider is supporting evidence only.
- Prove cancel-versus-complete emits exactly one UI terminal state and no stale
  generation updates.
- Live Samsung gate: scan dedicated edge-case data and selected existing folders.

### S7. Protected UI

- Prove non-approved auth states expose no folder metadata.
- Prove add, cancel, enable/disable, scan/cancel, repair, and remove.
- Prove recreation retains durable state and restores pending-operation UI.
- Prove removing, repairing, auth gating, or disabling during a scan rejects stale
  events and does not mutate replacement state.
- Prove recent-apps snapshot protection.
- Inspect screenshots on the Samsung at normal and enlarged font scale.

### S8. Restart And Upgrade Gate

- Force-stop from ADB without clearing app data.
- Relaunch, reauthenticate, and complete a nested scan without picker access.
- Install a newer APK over the existing app and repeat.

## Verification Matrix

### Automated Contract Tests

These tests provide fast deterministic coverage of branches that cannot be
safely forced against personal files. They do not count as live acceptance.

- exact duplicate, null display-name, and stable fallback-label behavior;
- one active picker and cancellation idempotence;
- singleton request correlation, stale callbacks, and both auth/result orders;
- approved co-administrator behavior and non-approved operation denial;
- grant-before-write and repair-before-release ordering;
- same-URI grant preservation on every repair terminal path;
- Room write failure compensation;
- every documented crash point and startup recovery state;
- scan cycle defense, null metadata, null cursor, loading/error extras;
- cancellation before query, during query, between rows, and between folders;
- cancel-versus-complete terminal race;
- one mapping failure isolated from another;
- no raw URI, subject, exception, or filename in diagnostics.

### Android Integration Tests On The Physical Device

Run instrumented tests on the connected Samsung for both debug flavors using
real app-private Room files and Android framework APIs:

- schema creation and recreation;
- DAO uniqueness and transaction rollback;
- database corruption fail-closed path using only the test app sandbox;
- installed-manifest backup denial plus `bmgr` rejection when the active device
  backup transport permits that proof;
- activity recreation and protected-content composition;
- controllable test-provider behavior through the production SAF scanner;
- real app-grant release and `PermissionMissing` recovery;
- package/flavor isolation;
- no broad storage permissions in the installed manifest.

### Live Samsung And Live Data Acceptance

No fake phone, fake Drive, fake DocumentsProvider, emulator result, or mocked
URI grant can satisfy this gate.

Use the connected Samsung Galaxy A23/API 34 and the system DocumentsUI. Never
rename, move, delete, or alter an existing personal file. Destructive cases use
only a clearly named dedicated test tree created for Viji Backup.

Before running any workflow, capture a local-only mutation-sentinel manifest for
the dedicated tree: relative path, byte size, modified timestamp, and SHA-256
content digest. Capture it again after add, scan, cancellation, repair, remove,
force-stop, and upgrade. Every value must match except changes deliberately made
by the test operator. The manifest remains local and only pass/fail evidence is
published. Also audit production source and the controllable provider to prove
the app never invokes create, rename, move, delete, output-stream, or write-open
APIs.

Required live cases:

- cancel picker with zero mapping/grant mutation;
- select the dedicated test tree;
- verify persisted read grant and absence of persisted write grant;
- scan empty, zero-byte, Unicode, long-name, duplicate-name-in-different-parent,
  nested, and deep entries in the dedicated tree;
- scan real Documents, DCIM/Camera, Pictures, and a selectable WhatsApp Media
  tree without modifying them;
- prove the Downloads root and Android restricted locations are not claimable;
- reject an exact duplicate;
- cancel a large scan and immediately scan another mapping;
- force-stop and relaunch without clearing data, then scan again;
- release the real persisted read grant, force-stop, observe
  `PermissionMissing`, repair, and rescan without recreating the mapping;
- repeat a same-URI repair and prove its read grant remains present;
- move or rename only the dedicated test tree, observe degraded health, repair,
  and rescan;
- keep one intentionally broken dedicated mapping while another real mapping
  scans successfully;
- use one approved identity to configure a mapping and another approved identity
  to view and scan the installation-scoped mapping;
- remove a mapping and prove the mutation-sentinel manifest is unchanged;
- upgrade the APK in place and rescan without picking again;
- background a populated screen and verify the recent-apps preview is redacted;
- inspect app process logs for raw URI, subject, filename, exception, OAuth ID,
  token, and fatal patterns;
- record redacted counts and outcomes only in Git/PR evidence.

Live Google Drive is not invoked by Phase 3 production code. Phase 4 must use
the real account, real OAuth authorization, and real shared folder for every
Drive acceptance case. A fake Drive result must never be used to claim Drive
authorization or upload readiness.

### Deferred Physical Matrix

The following remain mandatory before broad distribution but require hardware
or user coordination not currently guaranteed:

- API 24 minimum-version device or emulator coverage;
- API 30 blocked-location behavior;
- API 36 target-version coverage;
- removable SD card ejection during scan;
- physical reboot and post-reboot scan;
- actual cross-device-transfer round trip proving Room state is not restored.

These deferrals must remain visible in Project State and release gates. They do
not get silently marked passed.

## Blast-Radius Checklist

Any Phase 3 edit must inspect and test all affected contracts:

- process-scope auth lifecycle, cold-start reauthentication, and approved-content gating;
- activity result lifecycle and process recreation;
- Room schema, DAO, transactions, backup exclusions, and migration baseline;
- URI grant acquisition, release, and orphan reconciliation;
- scanner resource closure, cancellation, and partial results;
- per-mapping isolation;
- both distribution flavors and package IDs;
- Compose accessibility, font scaling, and protected-data visibility;
- zero-secret and zero-personal-identifier source checks;
- fresh-laptop setup and CI commands.

Changing one class is never considered sufficient evidence by itself. Before a
commit, search for all callers, implementations, tests, docs, persistence rules,
and UI states that consume the changed contract.

## Branch, Commit, And Review Contract

- Branch: `feature/phase-3-local-folder-selection`.
- Base: `feature/phase-2-auth-allowlist` until the stacked PRs are integrated.
- Keep planning, persistence, SAF adapter, scanner, presentation, tests, live
  evidence, and KB closure in separate logical commits.
- Use the GitHub account switcher before every commit and every push.
- Alternate Arya personal and Viji contributions as closely as logical commit
  boundaries permit.
- Never push `.env`, `private.properties`, OAuth JSON, tokens, raw account data,
  device serials, raw SAF URIs, or live filenames.
- Open a draft stacked PR as soon as the first coherent Phase 3 slice is pushed.
- Add a comprehensive PR comment after each push with tests run, live cases run,
  known gaps, and next slice.
- Do not merge Phase 3 before its base PRs and required review are resolved.

## Exit Gate

Phase 3 is complete only when all are true:

- an approved user can add, list, disable, repair, scan, and remove a
  system-granted local folder mapping;
- non-approved states reveal no mapping metadata;
- configured approved identities behave as explicit installation co-admins;
- only read permission is persisted;
- real grant loss preserves the mapping and reaches the repair path;
- process force-stop and APK upgrade preserve a usable mapping;
- the complete nested scan runs after restart without reopening the picker;
- cancellation is responsive and one bad mapping does not stop another;
- the dedicated mutation-sentinel manifest is unchanged and production code has
  no source-file mutation path;
- protected folder metadata is absent from the recent-apps snapshot;
- automated unit, integration, build, lint, and both-flavor suites pass;
- live Samsung evidence is recorded with sensitive values redacted;
- KB, fresh-laptop instructions, source register, project state, and PR evidence
  match the implementation;
- every deferred hardware case remains explicitly tracked.

## Official Sources

- [Storage Access Framework and persisted grants](https://developer.android.com/training/data-storage/shared/documents-files)
- [OpenDocumentTree activity contract](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocumentTree)
- [Intent local-only hint](https://developer.android.com/reference/android/content/Intent#EXTRA_LOCAL_ONLY)
- [Android 11 storage restrictions](https://developer.android.com/about/versions/11/privacy/storage)
- [ContentResolver persisted URI permissions](https://developer.android.com/reference/android/content/ContentResolver)
- [DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract)
- [Document metadata contract](https://developer.android.com/reference/android/provider/DocumentsContract.Document)
- [DocumentFile performance warning](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile)
- [CancellationSignal](https://developer.android.com/reference/android/os/CancellationSignal)
- [DataStore suitability guidance](https://developer.android.com/topic/libraries/architecture/datastore)
- [Room setup and current release](https://developer.android.com/jetpack/androidx/releases/room)
- [Room database testing](https://developer.android.com/training/data-storage/room/testing-db)
- [Room migration testing](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [UI Automator for cross-app UI](https://developer.android.com/training/testing/other-components/ui-automator)
- [Activity Result state and process recreation](https://developer.android.com/training/basics/intents/result)
- [AuthenticationRequiredException](https://developer.android.com/reference/android/app/AuthenticationRequiredException)
- [KSP setup and current plugin](https://kotlinlang.org/docs/ksp-kapt-migration.html)
- [Recent-apps screenshot control](https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean))
- [Secure window behavior](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)

## Next Notes

- [[Drive Backup App Architecture]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Implementation Roadmap]]
