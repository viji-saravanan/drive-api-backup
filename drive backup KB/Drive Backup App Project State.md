---
doc_id: drive-backup-app-project-state
status: active
last_updated: 2026-07-13
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
- typed, fail-closed cloud configuration with invalid-address and duplicate checks;
- approved-account session manager with persistence-before-unlock behavior;
- Preferences DataStore storage with malformed and partial-state recovery;
- durable provider-cleanup marker retried after cancellation or process restart;
- explicit cloud-backup and device-transfer exclusion for cached auth metadata;
- Credential Manager authorized-account and explicit-chooser flows;
- stable credential/error parsing and provider-state clearing;
- lifecycle-safe request dispatch with one-shot consumption and duplicate suppression;
- application-scoped manual dependency container;
- Compose auth gate, approved surface, progress, warnings, retry, and sign-out states;
- foreground reauthentication after an approved app session backgrounds;
- private build configuration loaded outside Git;
- internal and public debug flavors that coexist on one device;
- zero-secret GitHub source verification for unit, build, Android-test APK, and lint tasks;
- fresh-laptop setup and repeatable test runbook.

Not yet implemented:

- Google Drive authorization or folder access;
- any selected-folder sync behavior;
- release signing or APK publication.

## Confirmed Cloud Setup State

Personal identifiers and cloud resource IDs are private configuration even when
they are not authentication secrets. They must not appear in tracked files or
Git history. Real values are held in ignored local configuration. Source CI
deliberately receives none of those values.

Allowed Google account roles:

- two project-owner identities;
- two primary-user identities;
- no other approved identities.

Drive destination:

- Parent folder: `My Drive > Viji > BACKUP`
- Upload folder: `My Drive > Viji > BACKUP > Viji Phone Uploads`
- Parent and upload folder IDs are private build/deployment configuration.
- Upload folder owner is the project-owner Google account.

Manual Drive access tests passed for both primary-user identities and the
alternate owner identity. Each tested account could open the upload folder,
create a test folder, upload a test file, and delete its own test file.

Separate Android OAuth debug clients exist for
`com.aryasubramani.vijibackup.internal` and
`com.aryasubramani.vijibackup`. A Web application OAuth client is configured
separately for Credential Manager. Their IDs are private configuration and are
intentionally omitted here. The configured internal mapping produced a working
live Google chooser and approved-account result on the physical baseline.

Email notification defaults:

- Sender: project-owner account, configured in the server-side relay.
- Recipients: project owner and primary user, configured in the server-side relay.
- Preferred method: Google Apps Script `MailApp` relay owned by Arya.

## Current Gaps And Boundaries

- Active development branch is `feature/phase-2-auth-allowlist`.
- PR #2 is intentionally draft and must not merge before PR #1.
- Git account switcher profiles are verified for `callmearya` and
  `viji-saravanan`; both commit with their GitHub-provided `noreply` identity.
- Current workflow intentionally splits commits between Arya personal and Viji.
  Never commit from Arya work.
- Client-side email allowlisting is a convenience gate in an untampered build,
  not an authoritative authorization boundary. A public APK exposes compiled
  identifiers and can be modified. Phase 4 and every future sync operation must
  enforce current Google authorization and Drive destination ACL access.
- Email-address allowlisting retains a subject-reassignment residual. Move to
  opaque Google subjects or a trusted verifier before claiming strong public
  authorization.
- The source/review repository became public on 2026-07-13 after the completed
  tracked-source and reachable-history privacy checks. Treat every branch, pull
  request, review, and workflow log as public. Internal APKs and privately
  configured artifacts still require a separate private release surface.
- Existing sequential commit authorship is preserved on the public branches.
  Repository contributor/profile totals will fully reflect phase commits only
  after those branches are merged into the default branch without squashing.
- GitHub source CI intentionally receives no private values and must never upload
  privately configured debug APKs as artifacts.
- The project owner currently keeps an ignored, mode-`0600` `.env` as a
  temporary local vault for the three downloaded OAuth credential bundles and
  related private configuration. It is not loaded by Gradle, not available to
  contributors or CI, and not considered a disaster-recovery copy.
- `validatePublicReleasePrivacy` blocks `publicRelease` whenever the email
  allowlist or Drive folder ID is populated. A configured `publicDebug` is a
  private test artifact and must not be distributed.
- No signing/release setup exists.
- Manual live cases still needing explicit recorded evidence before release are:
  public-flavor Google sign-in, account removal on the physical device,
  unavailable Play services, airplane mode, and rotation while provider UI is
  open. Their fail-closed logic has automated coverage where possible, but
  automation is not a substitute for those release checks.
- The physical test phone has about 1.5 GB free and is 99% used. Never delete
  user data automatically; treat low storage as an explicit test and operational risk.
- Lint reports seven version-currency warnings. API 36.1, AGP 9.2.1, Gradle
  9.4.1, and the tested dependency set remain pinned for Phase 2. Android 17/API
  37 behavior adoption requires its own upgrade and device matrix instead of
  being folded into an auth review.

## Physical Device Baseline

Connected wired-ADB target:

- Samsung Galaxy A23 (`SM-A236E`);
- Android 14 / API 34;
- One UI 6.1;
- security patch `2026-05-05`;
- Google Play services `26.24.34`;
- device serial and account addresses intentionally excluded from evidence.

Observed on 2026-07-11 and reverified on 2026-07-12:

- internal flavor: 28 instrumented tests, 0 failures, 0 errors;
- public flavor: 28 instrumented tests, 0 failures, 0 errors;
- each flavor: 59 JVM unit tests, 0 failures, 0 errors;
- both package IDs install and launch side by side;
- DataStore partial/corrupt state, application-container wiring, Compose gate,
  Credential Manager mapping, and flavor identity pass on the Samsung;
- redacted live internal matrix: A1-A4 approved, B1 blocked under a temporary
  local allowlist, cancellation recovered, A1 restart reauthenticated, and
  sign-out cleared the local session;
- the user independently reported the configured live flow working;
- launched A1 process logs contained zero fatal, raw-email, JWT-shaped, or
  OAuth-client-ID-shaped matches;
- the missing-Web-client path returns `ConfigurationRequired` before credential UI;
- Gradle emits a non-fatal `androidx.test.services` app-ops warning before
  connected tests; accept it only when every test starts, finishes, and reports
  zero failures.

## Current Passing Checks

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

./gradlew \
  :app:connectedInternalDebugAndroidTest \
  :app:connectedPublicDebugAndroidTest
```

The same source workload passes with every private build property forced blank,
proving a clean checkout reaches the intended fail-closed setup state.

## Immediate Goal

Complete Phase 2 review and stacked-PR integration without claiming the local
allowlist is public-release security.

Next sequence:

- keep PR #2 stacked on PR #1 until the foundation base is integrated;
- preserve the source repository as private and keep private APKs out of CI artifacts;
- capture the remaining release-only manual auth cases before distributing an APK;
- begin Phase 3 local folder selection from the integrated Phase 2 base;
- require current Google/Drive authorization at every future protected sync boundary.

## Next Notes

- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Foundation Decisions]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Context Packets]]
- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
