---
doc_id: drive-backup-app-project-state
status: active
last_updated: 2026-07-11
context_role: current-state
read_when:
  - The agent needs to understand the current local scaffold before implementation.
  - The agent is about to start a roadmap phase.
do_not_read_when:
  - The task is a pure product decision with no local repo impact.
---

# Drive Backup App Project State

## Workspace

Project root:

```text
<repository-root>
```

Knowledge base:

```text
<repository-root>/drive backup KB
```

## Current Implementation

Phase 1 foundation work is under review in PR #1. Phase 2 authentication work is
published as draft PR #2 from `feature/phase-2-auth-allowlist`, stacked on
`setup/phase-1-foundation`.

Implemented Phase 2 slices:

- exact normalized account policy;
- approved-account session manager with fail-closed persistence behavior;
- Preferences DataStore metadata storage with corruption recovery;
- explicit cloud-backup and device-transfer exclusion for cached auth metadata;
- Credential Manager Google sign-in adapter and provider-state clearer;
- private build configuration loaded outside Git;
- internal and public debug flavors that coexist on one device.

Not yet implemented:

- launcher-level auth state and Compose presentation;
- successful live Google sign-in because the Web OAuth client ID is missing;
- Google Drive authorization or folder access;
- any selected-folder sync behavior.

## Confirmed Cloud Setup State

Personal identifiers and cloud resource IDs are private configuration even when they are not authentication secrets. They must not appear in tracked files or Git history. Real values are held in the ignored `private.properties` file for local builds and encrypted repository secrets for CI.

Allowed Google account roles:

- two project-owner identities;
- two primary-user identities;
- no other approved identities.

Drive destination:

- Parent folder: `My Drive > Viji > BACKUP`
- Upload folder: `My Drive > Viji > BACKUP > Viji Phone Uploads`
- Parent and upload folder IDs are private build/deployment configuration.
- Upload folder owner is the project-owner Google account.

Manual Drive access tests passed for both primary-user identities and the alternate owner identity. Each tested account could open the upload folder, create a test folder, upload a test file, and delete its own test file.

Separate Android OAuth debug clients exist for `com.aryasubramani.vijibackup.internal` and `com.aryasubramani.vijibackup`. Their client IDs are private build configuration and are intentionally omitted here.

The OAuth client mapping is based on the creation order followed during setup. Before implementing auth, verify this mapping in Google Cloud Console because the downloaded JSON did not include package metadata.

Email notification defaults:

- Sender: project-owner account, configured in the server-side relay.
- Recipients: project owner and primary user, configured in the server-side relay.
- Preferred method: Google Apps Script `MailApp` relay owned by Arya.

## Current Gaps

- Active development branch is `feature/phase-2-auth-allowlist`.
- PR #2 is intentionally draft and must not merge before PR #1.
- Git account switcher profiles are verified for `callmearya` and `viji-saravanan`; both commit with their GitHub-provided `noreply` identity.
- Current workflow intentionally splits commits between Arya personal and Viji. Never commit from Arya work.
- The launcher still opens the Phase 1 shell; this is a known U5 release blocker.
- Credential Manager adapter behavior beyond missing configuration needs fake-provider and live tests.
- Public APK authorization must not depend on plaintext email values recoverable from the APK. Resolve the Drive-ACL versus trusted-verifier design before public release.
- The source/review repository must remain private because immutable historical review excerpts retain old private configuration. A public repository must be created fresh from sanitized release commits.
- No CI is configured.
- No signing/release setup exists.
- The physical test phone has about 1.5 GB free and is 99% used. Never delete user data automatically; treat low storage as an explicit test and operational risk.
- Lint reports freshness warnings for target SDK, compile SDK, Core KTX, Gradle wrapper, and Compose compiler versions. These are documented in [[Drive Backup App Foundation Decisions]] and should be resolved deliberately in a future SDK/tooling update.

## Physical Device Baseline

Connected wired-ADB target:

- Samsung Galaxy A23 (`SM-A236E`);
- Android 14 / API 34;
- One UI 6.1;
- security patch `2026-05-05`;
- Google Play services `26.24.34`;
- device serial and account addresses intentionally excluded from evidence.

Observed on 2026-07-11:

- internal flavor: 6 instrumented tests, 0 failures, 0 errors;
- public flavor: 6 instrumented tests, 0 failures, 0 errors;
- both package IDs install and launch side by side;
- launched processes produced no fatal, token-shaped, or email-shaped app logs;
- the missing-Web-client test returns `ConfigurationRequired` before credential UI.

## Current Passing Checks

```bash
./gradlew :app:assembleInternalDebug
./gradlew :app:testInternalDebugUnitTest
./gradlew :app:assemblePublicDebug
./gradlew :app:testPublicDebugUnitTest
./gradlew :app:assembleInternalDebugAndroidTest
./gradlew :app:lintInternalDebug
./gradlew :app:connectedInternalDebugAndroidTest
./gradlew :app:connectedPublicDebugAndroidTest
```

## Immediate Phase 2 Goal

Finish Phase 2 without treating the draft adapters as a completed access gate.

Remaining gate:

- obtain the Web application OAuth client ID;
- add exhaustive fake-provider Credential Manager mapping tests;
- implement launcher auth state and Compose screens;
- run the internal/public live account matrix on the Samsung;
- verify restart, cancellation, account switching, sign-out, network failure, and log privacy;
- resolve the public-APK authorization/privacy boundary;
- keep PR #2 draft until those checks pass.

## Next Notes

- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Foundation Decisions]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Context Packets]]
