---
doc_id: drive-backup-app-context-packets
status: active
last_updated: 2026-07-12
context_role: machine-friendly-context-manifest
read_when:
  - A future AI agent needs exact note sets for a feature area.
  - Context has compacted and the agent must resume without reloading the whole vault.
do_not_read_when:
  - The task is a broad human overview; use [[Drive Backup App Index]] instead.
---

# Drive Backup App Context Packets

This is the machine-friendly context manifest. Load [[Drive Backup App Index]] first, then use this note to choose exact packets.

For any implementation or file edit, also load [[Drive Backup App Engineering Change Discipline]]. Packet-specific notes explain what to build; the change-discipline note explains how to prove the edit did not break adjacent contracts.

## Global Implementation Exit Checks

Apply these to every implementation packet:

- The touched files' callers, dependencies, configuration, manifest/resource impact, persistence impact, tests, and docs have been checked where relevant.
- The final response states the blast radius checked.
- Verification is run, or blocked verification is explicitly recorded with residual risk.

## Packet: Product Scope

Trigger phrases:

- requirements;
- what should the app do;
- MVP;
- acceptance criteria;
- missed requirement.

Required notes:

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Strategy]]
- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Open Questions And Assumptions]]

Exit checks:

- MVP vs future behavior is explicit.
- User-facing behavior is stated.
- Non-goals are not accidentally added to MVP.

## Packet: Current Project State

Trigger phrases:

- project state;
- scaffold;
- setup;
- rename;
- map project;
- where do we start;
- Android project setup.

Required notes:

- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
- [[Drive Backup App Project State]]
- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Engineering Change Discipline]]
- [[Drive Backup App Foundation Decisions]]

Exit checks:

- Work happens inside the current clone's repository root; never assume a contributor-specific absolute path.
- The roadmap phase is named before edits begin.
- The build/test command for the phase is known.
- The change does not skip ahead into feature implementation.

## Packet: Fresh Laptop And Repeatable Testing

Trigger phrases:

- another laptop;
- new contributor;
- zero setup;
- reproduce environment;
- install Android Studio;
- external SSD;
- connect phone;
- live auth setup;
- frequent testing.

Required notes:

- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
- [[Drive Backup App Project State]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Exit checks:

- No contributor-specific absolute path or local account switcher is assumed.
- Java, Gradle, SDK, `local.properties`, and optional external-drive setup are explicit.
- Clean build-only setup is separated from private-configured and live-auth setup.
- A fresh debug signing SHA-1 has matching internal/public Android OAuth clients.
- OAuth test users, local allowlist, and device authorization are deliberate.
- Private configuration is ignored and never printed or committed.
- Baseline, fast-loop, pre-push, connected, and live test commands are named.
- Source CI is zero-secret and does not publish configured APK artifacts.
- The contributor works on a separate branch and targets the current phase branch.

## Packet: Android Engineering Method

Trigger phrases:

- Android architecture;
- engineering method;
- clean project structure;
- best engineering;
- GitHub repos;
- Reddit research;
- Hilt;
- Compose;
- Room;
- DataStore;
- module structure;
- testing style.

Required notes:

- [[Drive Backup App Android Engineering Research]]
- [[Drive Backup App Foundation Decisions]]
- [[Drive Backup App Project State]]
- [[Drive Backup App Architecture]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Engineering Change Discipline]]

Must verify:

- Official Android architecture, build, dependency injection, storage, WorkManager, and testing docs before implementing platform-specific choices.

Exit checks:

- The chosen structure is justified by app complexity, not fashion.
- Tests use fakes/test doubles for core behavior.
- Source sets, flavors, and canonical Gradle tasks are named.
- The implementation avoids premature multi-module complexity unless a boundary has proven stable.

## Packet: Standard Practice Gap Assessment

Trigger phrases:

- standard practice;
- other apps;
- backup best practices;
- missing features;
- parity with sync apps;
- should we implement.

Required notes:

- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Exit checks:

- No-regret practices are captured as requirements or tests.
- Real trade-offs become open questions.
- Sources are recorded for external app/platform claims.

## Packet: Auth And Allowlist

Trigger phrases:

- Google sign-in;
- allowed accounts;
- user gated;
- allowlist;
- OAuth.

Required notes:

- [[Drive Backup App Phase 2 Auth Implementation Plan]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Testing Plan]]

Must verify:

- Google OAuth consent and Drive scope docs before implementation.
- Credential Manager Sign in with Google docs and Web client ID requirements before implementation.

Exit checks:

- Arya's token is not stored on user devices.
- Unapproved account cannot sync.
- Sign-out clears local auth state.

## Packet: Local Folder Access

Trigger phrases:

- internal storage;
- folder picker;
- files on device;
- Android folders;
- permission revoked.

Required notes:

- [[Drive Backup App Architecture]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Must verify:

- Android Storage Access Framework docs.

Exit checks:

- User can only choose folders Android permits.
- Revoked folder permission pauses that folder only.
- App does not claim access to blocked folders.

## Packet: Drive Destination

Trigger phrases:

- shared Drive folder;
- Arya folder;
- Drive upload target;
- folder ID;
- Drive ownership;
- quota.

Required notes:

- [[Drive Backup App Architecture]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Open Questions And Assumptions]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Testing Plan]]

Must verify:

- Drive scopes.
- Drive Picker or folder selection behavior.
- Shared folder ownership/quota behavior.

Exit checks:

- User signs in with their own account.
- Shared folder access is proven before upload.
- Per-user/per-device folder is created.
- Ownership/quota spike is recorded.

## Packet: Sync Engine

Trigger phrases:

- sync logic;
- upload files;
- ledger;
- retry;
- conflict;
- delete behavior;
- changed file.

Required notes:

- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Testing Plan]]

Optional notes:

- [[Drive Backup App Source Register]] if Drive API behavior is involved.

Exit checks:

- One failed file does not stop the rest.
- Remote files are not auto-deleted in MVP.
- Sync result can be Success, Partial Success, Failed, or Cancelled.
- Per-file results are durable.

## Packet: Settings And User Controls

Trigger phrases:

- settings;
- frequency;
- folders;
- mobile data;
- upload on data;
- cancel sync;
- charging;
- battery;
- manual override.

Required notes:

- [[Drive Backup App Settings Model]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]

Must verify:

- Android background execution and network constraint docs before implementation.

Exit checks:

- Frequency choices are explicit.
- Mobile data is opt-in and recorded in sync history.
- Cancellation preserves completed results and retry state.
- Folder settings cannot delete Drive files accidentally.
- Tests cover settings persistence and effective sync behavior.

## Packet: Background And Progress

Trigger phrases:

- periodic sync;
- manual sync;
- WorkManager;
- progress;
- background upload;
- cancellation.

Required notes:

- [[Drive Backup App Architecture]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Must verify:

- WorkManager periodic work docs.
- Android data transfer/background execution docs.

Exit checks:

- Manual sync starts promptly.
- Periodic timing is not promised as exact.
- Progress survives UI recreation.
- Cancellation records a terminal state.

## Packet: Email Notifications

Trigger phrases:

- email;
- SMTP;
- Apps Script;
- MailApp;
- failed filenames;
- completion summary.

Required notes:

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Must verify:

- Apps Script MailApp quotas or chosen email provider docs.

Exit checks:

- Email includes failed filenames and reasons.
- Email failure does not fail sync.
- Email credentials are not in APK or repo.

## Packet: Encryption Or Privacy Mode

Trigger phrases:

- encryption;
- private backup;
- readable from laptop;
- sensitive folder;
- recovery key.

Required notes:

- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Open Questions And Assumptions]]

Exit checks:

- MVP remains normal readable Drive backup.
- Private encrypted mode is future-only unless explicitly promoted.
- If promoted, restore tooling and key loss behavior are specified before coding.

## Packet: Testing

Trigger phrases:

- tests;
- verify;
- QA;
- edge cases;
- failure scenarios;
- release gate.

Required notes:

- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Product Requirements]]

Optional notes:

- Feature-specific packet being tested.

Exit checks:

- Unit, fake integration, WorkManager, instrumented, and manual coverage are considered.
- The tested behavior is tied to an acceptance criterion or failure row.

## Packet: GitHub And APK Release

Trigger phrases:

- GitHub;
- branch;
- commit;
- release;
- APK;
- internal build;
- public build;
- account switcher.

Required notes:

- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]

Exit checks:

- No direct push to `dev` or `main`.
- GitHub account is verified before push.
- Private/internal and public APK visibility are separated.
- No secrets are in release artifacts.

## Packet: Roadmap

Trigger phrases:

- next step;
- sequence;
- milestone;
- plan phases;
- where to start.

Required notes:

- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Open Questions And Assumptions]]

Exit checks:

- Current phase has a clear exit gate.
- Platform spikes happen before dependent implementation.

## Global Final Check For Any Implementation Work

Before ending an implementation turn, a future agent must answer:

- Did the change preserve the failure isolation invariant?
- Did the change add or update relevant tests?
- Did the tests cover negative, edge, interruption, cancellation, and persistence behavior where relevant?
- Did the change avoid secrets in code, docs, logs, and APKs?
- Did the change update docs if behavior changed?
- Did the change rely on a platform claim that needs source verification?
- Which official docs were checked for platform behavior touched by this change?

## Next Notes

- [[Drive Backup App Index]]
- [[Drive Backup App AI Context Guide]]
- [[Drive Backup App Source Register]]
