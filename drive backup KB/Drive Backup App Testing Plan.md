---
doc_id: drive-backup-app-testing-plan
status: active
last_updated: 2026-07-13
context_role: testing
read_when:
  - The agent writes, reviews, or plans tests.
  - The agent implements any sync, auth, Drive, or email feature.
do_not_read_when:
  - The task is only product strategy wording.
---

# Drive Backup App Testing Plan

Testing must prove that the app reduces data-loss risk instead of creating new silent failure modes.

## Testing Philosophy

No feature is tested properly if it only proves the happy path. Backup software must be tested against denial, interruption, corruption, quota exhaustion, cancellation, stale state, duplicate state, and recovery.

For every feature, define:

- the success path;
- at least one invalid-input case;
- at least one denied/revoked-permission case when permissions are involved;
- at least one retryable failure case;
- at least one permanent failure case;
- a persistence case after app restart or worker restart when state is stored;
- a cancellation case when work is long-running;
- a reporting case proving the UI/history/email surfaces the outcome correctly.

If the current test layer cannot cover a case, add it to the manual device matrix or recovery drill. Do not drop the case silently.

No fake phone, emulator, test double, synthetic DocumentsProvider, or fake cloud
response can be used as evidence that a physical-device, SAF, Google auth,
Google Drive, upload, or recovery acceptance gate passed. Deterministic tests
remain required for hostile branches that cannot be triggered safely against
personal data, but they are supporting evidence only. Every implemented external
boundary also needs the real physical-device/service case named by its phase.

## Test Layers

### Unit Tests

Cover pure logic:

- allowlist matching;
- folder mapping validation;
- relative path normalization;
- file identity comparison;
- sync planner decisions;
- deletion policy;
- conflict policy;
- retry classification;
- settings validation;
- effective settings snapshot creation;
- preflight result classification;
- preview estimate generation;
- completeness report generation;
- upload verification decision logic;
- email summary generation;
- privacy mode selection;
- Drive destination state classification.

Every unit-tested state machine should include invalid transition tests, not just valid transitions.

### Deterministic Integration Tests

Use controlled local trees, service test doubles, and instrumentation-only
providers to force failures that must not be inflicted on personal data.

Cover:

- first sync;
- incremental sync;
- changed file;
- deleted local file;
- unreadable file;
- duplicate remote file;
- network failure;
- mobile data disabled;
- mobile data enabled;
- mobile data file-size limit;
- dry-run preview does not mutate Drive;
- upload verification success and mismatch;
- Drive 401/403/429/5xx;
- quota exceeded;
- resumable upload restart;
- email failure;
- crash and resume from ledger.

Deterministic integration tests should simulate hostile service behavior:
duplicate responses, missing metadata, partial upload completion, stale Drive
IDs, repeated transient errors, and permanent denial after retries. Passing them
never replaces a real Drive or physical-device acceptance result.

### WorkManager Tests

Cover:

- manual one-time sync scheduling;
- unique periodic sync scheduling;
- no duplicate concurrent workers per mapping;
- retry/backoff behavior;
- cancellation;
- progress observation;
- constraints for network, mobile data policy, battery, charging, and storage.

Work tests must prove that duplicate workers do not corrupt state and that cancellation leaves completed file records intact.

### Instrumented Android Tests

Cover:

- Credential Manager adapter mapping with controlled results plus separate live
  approved-account sign-in on the physical device;
- folder picker result handling;
- persisted read-only URI permission with no persisted write permission;
- revoked URI permission;
- picker request correlation across process/activity recreation;
- Room mapping transaction and migration behavior;
- iterative DocumentsContract traversal, cancellation, and cursor closure;
- settings screen updates for frequency, mobile data, folders, charging, and email recipients;
- notification permission denied;
- preflight screen repair actions;
- backup health dashboard states;
- app restart with configured folder;
- sync progress UI;
- sync history UI;
- diagnostics export;
- upgrade migration.

Instrumented tests should cover Android-specific permission and lifecycle behavior that unit tests cannot prove.

### Manual Device Tests

Run on at least:

- Android 11;
- Android 12/13;
- Android 14/15 or current target;
- one Pixel device or emulator;
- one Samsung device if available.

The connected Samsung physical baseline is mandatory for Phase 3 and later
acceptance. An emulator can broaden API coverage but cannot replace it.

Scenarios:

- battery saver on;
- screen off during sync;
- Wi-Fi only;
- metered network;
- mobile data allowed;
- mobile data blocked by setting;
- manual mobile data override;
- airplane mode during upload;
- device reboot;
- app force stop;
- Drive folder sharing removed;
- Google account removed from device;
- notification permission denied or revoked;
- app killed while a file is uploading;
- folder permission revoked after successful setup;
- Drive folder access removed after successful setup;
- account removed from device after successful setup;
- low storage condition where practical;
- repeated cancellation and retry.

Current physical baseline is Samsung Galaxy A23 (`SM-A236E`) on Android 14 / API
34 and One UI 6.1. On 2026-07-12, both internal and public flavors completed 28
instrumented tests with zero failures or errors. This device currently has about
1.5 GB free, so it is valid low-storage evidence but is not suitable for large
sync data sets until the owner frees space manually.

Do not record device serials or account addresses. Label real approved accounts
as A1-A4 and a deliberately excluded real account as B1 in test evidence.

## Phase 2 Authentication Evidence

Automated evidence current on 2026-07-12:

- 59 JVM tests per flavor cover exact policy matching, locale normalization,
  malformed configuration, session persistence ordering, stale callbacks,
  duplicate sign-in/sign-out, cancellation, retry classification,
  current-process approval retention, cold-process reauthentication,
  restart-resumable provider cleanup, and lifecycle request dispatch;
- 28 connected tests per flavor cover Credential Manager request construction
  and result mapping, malformed credentials, cancellation and fatal propagation,
  DataStore recreation/corruption/partial-state repair, Compose text/actions and
  protected-content denial, application-container stability, and real
  `MainActivity` fail-closed composition;
- both app APKs and both Android-test APKs assemble;
- both lint tasks pass with seven recorded version-currency warnings;
- the full source workload passes with all private configuration forced blank,
  which proves the clean-checkout `ConfigurationRequired` path;
- GitHub source verification injects no private identifiers and uploads no APK artifact.

Redacted live evidence:

- A1-A4 reached approved state on the configured internal flavor;
- B1 was blocked under a temporary local allowlist and normal configuration was restored;
- backing out of Google UI recovered without cached approval;
- A1 restart required fresh reauthentication before approval;
- Home, DocumentsUI, and activity recreation retained one approved in-process
  session without launching another Google chooser;
- sign-out cleared the local session;
- the user independently reported the configured live flow working;
- an A1 process-only log scan found no fatal, raw-email, JWT-shaped, or
  OAuth-client-ID-shaped matches.

Release-only auth cases not yet claimed as complete:

- public-flavor live Google sign-in and blocked-account flow;
- physical removal of an approved Google account while the process survives;
- unavailable or disabled Google Play services;
- airplane mode during the provider flow;
- rotation while Google provider UI is active;
- TalkBack, switch access, and extreme font-scale review on a compact viewport.

Use [[Drive Backup App Fresh Laptop Setup And Test Runbook]] for exact setup,
commands, debug SHA registration, evidence privacy, and branch/PR rules.

## Phase 3 Local Folder Evidence Contract

The implementation and exact matrix are owned by
[[Drive Backup App Phase 3 Local Folder Access Implementation Plan]]. Phase 3 is
not complete until the Samsung/API 34 proves all of the following with the real
system picker and real persisted grant:

- only read permission is retained;
- selected internal-storage data scans after force-stop/relaunch without another
  picker interaction;
- a real released grant becomes `PermissionMissing` while the mapping survives;
- repair restores the same mapping and same-URI repair does not revoke it;
- cancellation stops one scan without poisoning another mapping;
- a moved/deleted dedicated test tree does not delete its mapping automatically;
- one broken mapping does not stop another real mapping;
- installation-scoped mappings remain available to another configured approved
  identity;
- recent-apps preview hides protected folder metadata;
- a host-side before/after mutation-sentinel manifest proves the app did not
  alter dedicated test content;
- process logs and public evidence contain no raw URI, live filename, subject,
  token, OAuth identifier, or exception payload.

Focused evidence current on 2026-07-13:

- 30 repository instrumentation cases pass on Android user 0, covering add,
  duplicate rejection, pending recovery, grant reduction/reconciliation,
  metadata backfill, repair, removal, cancellation, and forced SQLite
  delete-failure retry;
- 7 Compose folder-screen cases pass on the Samsung, covering real/fallback
  labels, exact-ID forwarding, named confirmation, cancel-without-side-effect,
  in-flight progress, disabled competing actions, and non-sensitive notices;
- the app-composition lifecycle case proves one explicit sign-in and one
  long-lived folder observer across Home/resume and activity recreation;
- the ViewModel coroutine-race case proves that a cancelled removal completing
  late cannot clear progress, admit a third mutation, or publish state over a
  newer removal;
- the redacted live probe confirms 2 production mappings, 2 persisted tree
  grants, 0 write grants, and no pending picker operation;
- before the first approved launch containing the metadata reader, the redacted
  probe correctly found 0 named mappings. Acceptance requires the same probe to
  find 2 after approved reconciliation; names and URIs must not be printed;
- the exact Downloads root is visibly blocked by Android DocumentsUI as the
  official Android 11+ contract requires. This is not an app defect and must not
  be bypassed silently;
- live removal remains pending. The user must identify one dispensable test
  mapping first; the other real mapping must remain untouched.

Impossible-to-induce branches use a controllable instrumentation-only
DocumentsProvider through the production scanner. This is not live acceptance.
Crash-point tests terminate and restart the test process around real Room and URI
grant boundaries. Deferred API 24/API 30/API 36, removable-storage, reboot, and
cross-device-transfer cases stay visible until actual evidence exists.

## Data Set Matrix

Create test folders with:

- zero-byte files;
- tiny text files;
- photos;
- videos;
- PDFs;
- nested folders;
- long filenames;
- unicode filenames if supported by test environment;
- duplicate names in different folders;
- large files;
- files changing during sync;
- files deleted during sync.

## Release Test Gate

Before any public APK release:

- Unit tests pass.
- Deterministic hostile-path integration tests pass.
- Work scheduling tests pass.
- Negative and edge-case tests for changed areas pass.
- At least one full manual sync test passes on a physical device using real local
  data, the real signed-in Google account, and the real shared Drive folder.
- Failure email includes failed filenames.
- Source register has been checked for any platform behavior changed by the release.
- APK is signed with the correct key.
- GitHub branch, tag, and release notes match the release channel.

## Recovery Drill

At least once before MVP release:

1. Configure app on a test device.
2. Sync a representative folder set.
3. Confirm files are visible in the shared Drive folder from a laptop browser.
4. Reset or uninstall/reinstall app.
5. Confirm backup contents are still accessible from Drive.
6. Reconnect app and verify it does not duplicate every file unnecessarily.
7. Mark recovery drill completed in the app and confirm the reminder clears.

## AI Testing Context Rule

Any future agent implementing a feature must add or update tests in the same turn. If tests are not possible, the agent must state why and update this plan or the relevant failure row.

Before marking a feature done, the agent must state which negative cases were tested and which remain manual/device-only.

Testing must follow the blast radius. If a code edit affects settings, sync semantics, background work, Drive uploads, auth, folder access, email, progress UI, history, or release behavior, tests must cover the affected downstream behavior, not only the directly edited class or file.

## Next Notes

- [[Drive Backup App Engineering Change Discipline]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Source Register]]
