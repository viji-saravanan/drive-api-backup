---
doc_id: drive-backup-app-testing-plan
status: active
last_updated: 2026-07-08
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

### Fake Integration Tests

Use fake local file tree, fake Drive API, fake auth, and fake email adapter.

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

Fake integration tests should simulate hostile service behavior: duplicate responses, missing metadata, partial upload completion, stale Drive IDs, repeated transient errors, and permanent denial after retries.

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

- Google sign-in flow with test account or fake auth where possible;
- folder picker result handling;
- persisted URI permission;
- revoked URI permission;
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
- Google account removed from device.
- notification permission denied or revoked.
- app killed while a file is uploading;
- folder permission revoked after successful setup;
- Drive folder access removed after successful setup;
- account removed from device after successful setup;
- low storage condition where practical;
- repeated cancellation and retry.

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
- Fake integration tests pass.
- Work scheduling tests pass.
- Negative and edge-case tests for changed areas pass.
- At least one full manual sync test passes on a real/emulated device.
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
