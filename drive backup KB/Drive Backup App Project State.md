---
doc_id: drive-backup-app-project-state
status: active
last_updated: 2026-07-18
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

Phase 1 foundation, Phase 2 authentication, the public-source workflow, and the
earlier Phase 3 local-folder slices are integrated on `main` through PRs #1-#4.
The completed Phase 3 branch is the base of
`feature/phase-4-downloads-drive-setup`. Draft PR #6 contains the first two
Phase 4 boundaries: durable approved-session reuse with explicit account
switching, and exact top-level Downloads access with a read-only scanner. Their
implementation plans, source claims, and physical-device evidence are committed
with the code. Whole-branch review remains intentionally deferred before merge.

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
- approved cached-session restoration across normal relaunch, process death,
  force-stop, reboot, and in-place upgrade without an automatic Google chooser;
- explicit `Change account` and sign-out paths that remain the only normal
  chooser/session-replacement entry points;
- private build configuration loaded outside Git;
- internal and public debug flavors that coexist on one device;
- zero-secret GitHub source verification for unit, build, Android-test APK, and lint tasks;
- fresh-laptop setup and repeatable test runbook.

Implemented Phase 3 slices:

- Room schema 1 for unique folder mappings and one durable picker-operation slot;
- backup/transfer exclusion for the Room database and grant-dependent state;
- read-only `ACTION_OPEN_DOCUMENT_TREE` contract with request-token correlation;
- exact persisted-read-grant acquisition, legacy write-grant reduction, orphan
  cleanup, cancellation propagation, and crash recovery;
- approved-only folder observation, add, exact-duplicate rejection, and repair;
- provider-derived root display names with existing-row backfill and safe generic
  fallback labels when metadata is unavailable;
- confirmed removal that revokes the grant before deleting the mapping, remains
  retryable on grant or Room failure, and never deletes phone or Drive content;
- generation-owned removal presentation that prevents a cancelled older
  operation from clearing progress or publishing an outcome for a newer one;
- compact Compose folder rows with named remove confirmation, progress, notices,
  and disabled competing mutations;
- process-scope auth regression coverage proving Home, picker round trips, and
  activity recreation do not repeatedly invoke Google sign-in.
- sign-out compensation that serializes and retries pending picker cleanup
  before local auth state is cleared, without blocking sign-out on one cleanup
  failure;
- an exact-token DataStore cleanup intent that survives process recreation when
  Room cannot mark pending picker work, is excluded from backup and transfer,
  and cannot retire a newer picker request;
- per-launch picker callback identity that discards a retired pre-sign-out
  result without consuming a post-sign-in replacement, including activity
  recreation;
- typed live root health validation from exact persisted grants and one
  read-only root query, including provider authentication, explicit missing
  roots, temporary failures, cancellation, and terminal-state enforcement;
- durable idempotent enable/disable state with typed repository failures,
  per-mapping generation ownership, independent concurrent updates, and no
  health-validator or grant side effects;
- cold iterative read-only metadata scanning with cycle detection, saturating
  aggregate progress, exact complete/partial/failed decisions, cancellation
  propagation, and no filename or raw-identifier output;
- resolver-owned cursor and cancellation-signal lifetimes with Samsung-tested
  null, malformed, loading, provider-failure, cancellation, retry, and
  no-mutation behavior in both flavors;
- a disabled, variant-safe test-only provider manifest plus physical Samsung
  fault-path coverage without source-content mutations or test-process startup
  crashes;
- repository scan admission that revalidates current access and permits manual
  scans for disabled-but-readable mappings without admitting degraded roots;
- independent generation-owned health and scan jobs with monotonic progress,
  exact cancellation, stale-event suppression, and two-mapping isolation;
- protected folder controls for enablement, typed health, stable progress,
  scan/cancel, repair, and confirmed removal without rendering storage IDs.

Implemented Phase 4 slices:

- cached approved-account session reuse with fail-closed malformed-state and
  policy revalidation behavior;
- explicit account switching without coupling ordinary app relaunch to Google
  provider UI;
- a dedicated API 30+ exact primary-storage Downloads source using explicit
  package-specific all-files-access settings, isolated from SAF mappings;
- API 24-29 fallback to the existing read-only system tree picker;
- typed Downloads configuration/access health, including unused grant, denied,
  unavailable, disabled, and ready states;
- read-only iterative exact-root traversal with no production create, write,
  rename, move, or delete API;
- cycle, symlink, root-escape, unreadable-entry, overflow, cancellation, retry,
  partial-result, and stale-event defenses;
- protected visible controls for grant/repair, enable/disable, scan/cancel,
  remove, unused-grant review, and reconfiguration;
- physical Samsung acceptance for force-stop persistence, visible scan,
  denial/back, external revocation, repair, disable/enable, remove/reconfigure,
  and unchanged-source evidence.

Not yet implemented after these Phase 4 slices:

- Google Drive authorization or destination access;
- destination-folder verification and per-user/per-device folder creation;
- any selected-folder sync behavior;
- WorkManager scheduling, upload progress, email delivery, or recovery flow;
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

- Integrated development baseline is `main`. New implementation work must use a
  dedicated branch and PR; do not push directly to `main`.
- Git account switcher profiles are verified for `callmearya` and
  `viji-saravanan`; both commit with their GitHub-provided `noreply` identity.
- Current workflow intentionally splits commits between Arya personal and Viji.
  Exact equality is unnecessary, but verify the intended identity before each
  commit and push.
- Tracked source and reachable patch content contain no configured account,
  OAuth, secret, or Drive identifier. Two pre-cleanup GitHub-generated merge
  commits on `main` still contain one collaborator's personal commit email in
  commit metadata. It is not an app secret or tracked-source value. Removing it
  requires a coordinated rewrite of `main` and every descendant collaborator
  branch, so it is tracked separately from Phase 3 rather than silently
  force-rewritten during a feature push.
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
  Stack integration uses merge commits rather than squashing so the default
  branch retains each phase's contributor metadata and review boundary.
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
- The ordinary-user journey audit is tracked in [[Drive Backup App User Journey
  Gap Audit]]. UX-01, UX-02, and UX-04 are closed with physical-device evidence:
  ordinary relaunch keeps the approved session, account changes are explicit,
  and exact Downloads has denial, revocation, repair, and removal behavior.
- The same audit records the remaining user-facing gaps around mapping
  identification, access-versus-backup health, preflight,
  cancellation, partial results, scheduling explanations, recovery, and device
  constraints. These are phase-owned gates, not reasons to change Phase 3 code
  without a scoped implementation task.
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

Observed on 2026-07-11, with the ADB connection reverified on 2026-07-13:

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

Additional Phase 3 lifecycle evidence on 2026-07-15:

- the review-discovered sign-out, recreation, re-sign-in, replacement-picker,
  and late-old-result sequence was witnessed failing before the fix;
- the focused 7-test state/composition suite and related 58-test repository,
  state, composition, and auth UI suite passed directly on Samsung user 0;
- both-flavor JVM tests, app and Android-test APK assembly, and lint passed.

Additional Phase 3 root-health evidence on 2026-07-15:

- the validator's compile and terminal-`Checking` cases were witnessed RED
  before implementation;
- 29 validator and 44 repository tests passed directly on Samsung user 0;
- both-flavor JVM tests, app assembly, Android-test compilation, and
  Android-test APK assembly passed;
- the provider manifest check caught and fixed a class-name packaging defect;
- this zero-mapping observation was the pre-fixture baseline and is superseded
  by the redacted core live evidence recorded below.

Additional Phase 3 enablement evidence on 2026-07-15:

- the missing enablement contract and presentation state were witnessed RED
  before implementation;
- the focused ViewModel suite passed on the JVM, while all 47 repository tests
  passed directly on Samsung user 0;
- all 29 validator tests and all 4 app-composition tests passed again after the
  test provider was made disabled-by-default to prevent pre-instrumentation
  classloader failure;
- both-flavor JVM tests, app assembly, and Android-test APK assembly passed;
- disabled-but-ready manual scan remains a Task 6 gate and is not yet claimed.

Additional Phase 3 scanner evidence on 2026-07-15:

- missing scanner contracts and missing hostile-provider controls were
  witnessed RED before implementation;
- all 14 pure iterative-scanner tests passed for both flavors;
- all 7 resolver-facing scanner tests passed directly on Samsung user 0 for
  both internal and public flavors, and all 29 validator tests passed again;
- the complete two-flavor JVM, app APK, and Android-test APK matrix passed;
- whole-Downloads was confirmed as a separate explicit all-files-access source
  rather than a false SAF claim; that source was later completed in Phase 4.

Additional Phase 3 orchestration and core live evidence on 2026-07-15:

- the scan-admission and ViewModel contracts were witnessed RED before
  implementation; focused orchestration tests pass for both flavors;
- all 51 Room repository tests and all 11 protected-folder Compose tests passed
  directly on Samsung Android user 0 with no failures;
- the canonical 179-task two-flavor unit, app APK, Android-test APK, and lint
  matrix passed from one `--no-configuration-cache` invocation;
- `adb install --no-streaming -r` preserved app data, and direct
  `am instrument --user 0` reported 2 named mappings, 2 persisted tree grants,
  0 write grants, and no pending picker operation;
- real scans completed for a small nested tree and a dedicated 1,502-file tree;
  cancelling the larger scan changed only that mapping, and retry completed;
- the dedicated 1,502-file before/after content sentinel was byte-for-byte
  unchanged;
- this evidence is superseded by the completed 2026-07-16 recovery matrix below.

Phase 3 closure evidence on 2026-07-16:

- the review-discovered pending-picker restart defect is fixed with an
  exact-token durable cleanup intent while Room remains schema 1; both flavor
  host suites, focused file-backed Room recreation tests, and focused Samsung
  instrumentation pass;
- ViewModel tests now cover complete, partial, failed, terminal-less, throwing,
  non-cooperative late-event, cancel-versus-complete, and concurrent
  failed/healthy scan behavior; no production defect was exposed;
- a controllable `ActivityResultRegistry` test launches a real dynamic registry
  key, recreates `MainActivity`, dispatches through the restored request code,
  and proves exactly one completion with the original request token;
- the canonical 179-task two-flavor unit, app APK, Android-test APK, and lint
  matrix passes with 115 JVM tests per flavor; the complete Android
  instrumentation package passes 160 tests per flavor on Samsung user 0;
- the final redacted live probe reports 3 named mappings, 3 persisted tree read
  grants, 0 write grants, and no pending picker operation after in-place APK
  replacement without clearing app data;
- a real 1,502-file scan completed across 5 folders with 0 unreadable entries;
  complete content manifests remained identical after scan, grant revocation,
  same-tree repair, confirmed removal, re-add, move/repair, and restoration;
- explicit grant revocation degraded only the selected mapping, a healthy
  mapping remained scannable, and selecting the same tree restored the mapping
  without releasing the replacement grant;
- live picker cancellation created no mapping, exact duplicate selection was
  rejected, confirmed removal released app access without deleting files, and
  re-adding restored the expected 3-mapping baseline;
- a second configured approved identity viewed the installation-scoped mappings
  and completed a real 2-file scan with 0 unreadable entries;
- moving the dedicated tree produced a typed temporary-unavailable state after
  revalidation; repair followed the moved tree, and the original name and
  mapping were restored without content change;
- the recent-apps preview rendered protected content blank, and the reviewed
  app-process log contained zero email, content-URI, OAuth-client, JWT-shaped,
  UUID-shaped, or live-label matches.

Phase 4 session and Downloads evidence on 2026-07-18:

- the canonical two-flavor unit, app APK, Android-test APK, and lint matrix
  passes after the session and Downloads implementation;
- the unlocked physical-device Downloads Compose suite passes 5/5, and the
  auth-gate plus app-composition regression suite passes 16/16;
- the public app survives force-stop and in-place replacement, opens directly
  to the approved surface without a Google chooser, and retains its Downloads
  configuration;
- a visible real Downloads scan reaches `Scan complete`; a separate live probe
  cancels, retries, and preserves an identical before/after aggregate metadata
  sentinel;
- real OS revocation shows `Access required` and no Scan action before any read;
  backing out remains blocked, while restored permission returns `Ready`;
- live disable/enable, confirmed remove, unused-grant classification, and
  reconfiguration complete without modifying source content;
- no evidence contains an account address, device serial, path, filename,
  OAuth identifier, or token.

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

Keep the completed session-persistence and exact-Downloads slices on draft PR
#6, then implement Phase 4 Google Drive authorization and destination health on
the same branch using separate sequential commits. Do not begin uploads until
the signed-in account can authorize Drive and prove access to the configured
shared destination without exposing its identifier.

Next sequence:

- implement Drive authorization as a separate consent boundary from sign-in;
- prove shared-destination access and fail-closed revocation on the live account;
- create and verify the per-user/per-device destination contract before upload;
- keep the deferred whole-branch review as a pre-merge gate;
- keep configured APKs and raw live evidence out of public CI and Git;

## Next Notes

- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Foundation Decisions]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Context Packets]]
- [[Drive Backup App User Journey Gap Audit]]
- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
- [[Drive Backup App Phase 3 Local Folder Access Implementation Plan]]
- [[Drive Backup App Phase 4 Session Persistence Implementation Plan]]
- [[Drive Backup App Phase 4 Downloads Access Implementation Plan]]
