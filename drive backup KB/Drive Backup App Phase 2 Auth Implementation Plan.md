---
doc_id: drive-backup-app-phase-2-auth-implementation-plan
status: active
last_updated: 2026-07-10
context_role: implementation-plan
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
execution: code
read_when:
  - The agent implements, reviews, or tests Phase 2 authentication and account gating.
  - The agent changes Google sign-in, local auth-session state, the allowlist, or sign-out.
do_not_read_when:
  - The task is Drive authorization, folder selection, sync, or email with no auth impact.
---

# Drive Backup App Phase 2 Auth Implementation Plan

## Goal Capsule

Implement Google sign-in and a bundled approved-account gate for `Viji Backup`.
An approved Google account may enter the app. An unapproved account is shown a
blocked state and cannot reach later backup workflows. Sign-out must remove local
auth state and notify Credential Manager to clear provider session state.

Phase 2 authenticates the user. It does not request Google Drive access. Drive
authorization belongs to Phase 4 and must use `AuthorizationClient` with the
narrowest viable Drive scope.

## Confirmed Inputs

Approved account emails:

- `owner.primary@example.test`
- `owner.alternate@example.test`
- `primary.user@example.test`
- `alternate.user@example.test`

Existing Android OAuth client IDs remain mapped to the internal and public
Android application IDs. They are not the Web application client ID required by
Credential Manager's Sign in with Google request.

## KTD-1: Web OAuth Client Prerequisite

Credential Manager requires a Web application OAuth client ID as its server
client ID. That value has not been supplied yet.

Implementation behavior while it is missing:

- builds and automated tests remain deterministic;
- the app shows an explicit configuration-required state;
- the app never substitutes either Android OAuth client ID;
- no live Google account flow is claimed as verified.

Live sign-in verification is blocked until Arya creates the Web application
client in the same Google Cloud project and supplies its non-secret client ID.

## Architecture Decisions

### Authentication And Authorization Stay Separate

- Credential Manager plus Sign in with Google authenticates the person in Phase 2.
- Google Identity `AuthorizationClient` requests Drive access later in Phase 4.
- Sign-in does not request `drive.file` or any other Drive scope.

### Trust Boundary

The app has no relying-party backend in MVP. Google recommends validating ID
tokens on a backend before using them as a server-side security identity. This
app receives the credential directly from Credential Manager, extracts the
token-backed Google account email and stable Google account subject, then
immediately discards the raw ID token.

Consequences:

- the bundled allowlist is a safety and product gate in an untampered APK;
- it is not a tamper-resistant authorization service;
- Google Drive sharing and OAuth grants remain the authoritative data-access
  boundary;
- no token, client secret, app password, or service-account key is persisted,
  logged, committed, or added to diagnostics.

Do not introduce client-side JWT signature validation. Google's signing keys
rotate, and duplicating backend verification on-device would not make a modified
APK trustworthy.

### Session Model

- Persist only the approved account's stable subject, normalized email, and
  optional display name.
- Use Preferences DataStore behind an `AuthSessionStore` interface.
- A cached account is never treated as approved immediately after process start.
- A cached account enters `ReauthenticationRequired`; Credential Manager must
  return a current credential before the app enters `Approved`.
- A blocked account is never persisted.
- Local persistence is excluded from Android cloud backup by the app's existing
  `allowBackup=false` policy.

### Dependency Injection

Use a small manual application container for this phase. It keeps Android
adapters out of composables and enables fakes without adding Hilt configuration
before the component graph justifies it. Re-evaluate Hilt when Drive, settings,
Room, and WorkManager dependencies are introduced.

## Public State Contract

The presentation layer exposes these observable states:

| State | Meaning | Allowed user action |
|---|---|---|
| `Initializing` | Cached local state is loading. | None |
| `ConfigurationRequired` | Web OAuth client ID is absent. | None until build configuration is repaired |
| `SignedOut` | No approved active account exists. | Start explicit Google sign-in |
| `ReauthenticationRequired` | A cached approved account needs a fresh Credential Manager result. | Automatic authorized-account attempt or explicit sign-in after fallback |
| `SigningIn` | A Credential Manager flow is active. | Wait; duplicate launches are ignored |
| `Approved` | Current Google account is on the allowlist. | Enter app surface or sign out |
| `Blocked` | Current Google account is not on the allowlist. | Choose another account |
| `Error` | A recoverable or permanent auth operation failed. | Retry or return to signed-out state as classified |
| `SigningOut` | Local and provider state are being cleared. | Wait; duplicate sign-out is ignored |

Raw exception messages must never be shown. Platform failures map to stable,
user-safe error categories.

## State And Failure Rules

- Email comparison trims surrounding whitespace and lowercases with
  `Locale.ROOT`.
- Matching is exact after normalization. Aliases, substring matches, and
  lookalike addresses are rejected unless explicitly listed.
- Blank or missing email and subject claims are invalid credentials.
- User cancellation returns to a non-approved state and is not reported as a
  fatal error.
- No credential available clears stale cached approval and shows signed out.
- Unknown credential types and malformed Google credentials are rejected.
- A DataStore write failure must not result in `Approved`.
- A DataStore clear failure must not claim sign-out succeeded.
- If provider-state clearing fails after local state was cleared, local sign-out
  still succeeds and the UI reports a non-sensitive warning.
- Lifecycle interruption must not leave the UI permanently in `SigningIn`.
- Duplicate sign-in or sign-out taps must not launch concurrent operations.

## Verification Contract

### Automated Unit Proof

- Every approved email passes exact normalized matching.
- Case and surrounding whitespace do not change an approved decision.
- Locale-sensitive casing cannot change the result.
- Empty, malformed, unapproved, alias, and lookalike addresses are blocked.
- Approved credentials are persisted without raw tokens.
- Blocked credentials clear stale approved state and are not persisted.
- Persistence failures fail closed.
- Sign-out clears local state and requests provider-state clearing.
- Provider clear failure after local clear produces signed-out-with-warning.
- Invalid and duplicate state transitions do not start another operation.
- Credential Manager exception and credential-type mapping is exhaustive for
  the categories used by the app.

### Android Integration Proof

- DataStore account state survives store recreation and clears on sign-out.
- Signed-out, approved, blocked, configuration-required, loading, and error
  composables expose the expected text and actions.
- Both `internalDebug` and `publicDebug` Android-test APKs compile.

### Manual Device Proof

Run after KTD-1 is resolved:

- each of the four approved accounts can sign in;
- an unapproved test account is blocked;
- user cancellation is recoverable;
- no Google account on device is handled;
- switching from a blocked account to an approved account works;
- sign-out allows a different account to be selected;
- app restart reauthenticates cached identity before approval;
- removing the Google account from the device removes effective approval;
- airplane mode and unavailable Google Play services produce recoverable UI;
- rotation or process recreation during sign-in does not leave a stuck state;
- `adb logcat` contains no ID token or raw credential payload.

## Implementation Units

### U1. Auth Dependencies And Configuration

Goal: add stable Credential Manager, Google ID, DataStore, Lifecycle, and test
dependencies through the version catalog; add an explicit Web client ID
configuration seam that defaults to missing.

Files:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/aryasubramani/vijibackup/core/CloudConfiguration.kt`
- matching configuration tests

Verification: both debug variants compile and missing configuration is tested.

### U2. Account Policy

Goal: model Google account identity and make the allowlist decision a small,
pure, fail-closed module.

Files:

- `app/src/main/java/com/aryasubramani/vijibackup/auth/domain/`
- `app/src/test/java/com/aryasubramani/vijibackup/auth/domain/`

Execution note: one red behavior test followed by the minimum implementation,
repeated vertically.

Verification: focused account-policy tests pass for all normalization and
lookalike cases.

### U3. Durable Session Manager

Goal: persist only approved account metadata, require reauthentication after
process start, and fail closed on storage errors.

Files:

- `app/src/main/java/com/aryasubramani/vijibackup/auth/data/`
- unit fakes and manager tests
- DataStore Android integration test

Execution note: prove approved, blocked, persistence-failure, and sign-out
behaviors one at a time.

Verification: unit tests and Android-test compilation pass.

### U4. Credential Manager Adapter

Goal: support the authorized-account bottom-sheet attempt, explicit Sign in with
Google button flow, safe credential parsing, error classification, and provider
state clearing.

Files:

- `app/src/main/java/com/aryasubramani/vijibackup/auth/google/`
- adapter mapping tests

Verification: all result categories are covered without storing or logging the
ID token.

### U5. Auth State And UI

Goal: wire ViewModel state to a focused Compose auth gate and replace the Phase
1 shell.

Files:

- `app/src/main/java/com/aryasubramani/vijibackup/auth/presentation/`
- `app/src/main/java/com/aryasubramani/vijibackup/app/`
- resources and matching unit/instrumented tests

Verification: state-transition tests pass, UI tests compile, and both app
variants assemble.

### U6. Integration Documentation And Review Handoff

Goal: update current-state and retrieval notes, record verification evidence,
and produce a reviewer handoff that tells a fresh laptop/AI exactly which branch
and base to use.

Files:

- `README.md`
- `drive backup KB/Drive Backup App Project State.md`
- `drive backup KB/Drive Backup App Index.md`
- reviewer handoff document

Verification: KB links resolve, no outdated Phase 1-only status remains, and the
handoff identifies KTD-1 plus manual tests still outstanding.

## Scope Boundaries

Phase 2 does not:

- request Drive scopes or upload files;
- validate Drive folder access;
- implement folder selection, sync, WorkManager, email, or release signing;
- add a remote allowlist or backend account service;
- persist raw ID tokens, access tokens, or refresh tokens;
- claim that a local APK allowlist survives APK tampering.

## Definition Of Done

- All U1-U6 code and documentation outputs are complete.
- Approved, blocked, sign-out, missing-configuration, cancellation, stale-cache,
  persistence-failure, and provider-failure paths have evidence.
- Internal and public debug variants build and unit-test successfully.
- Android-test APKs compile; device-only tests are explicitly listed if no
  emulator or configured Web client is available.
- Secret scan and tracked-file review find no credential bundles or tokens.
- The Phase 2 branch is pushed separately and its PR targets the Phase 1 branch
  until Phase 1 merges.

## Sources

Use the current entries in [[Drive Backup App Source Register]], especially the
Credential Manager Sign in with Google implementation guide, Google ID token
credential reference, token-validation guidance, and `AuthorizationClient`
reference.

## Next Notes

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Project State]]
- [[Drive Backup App Engineering Change Discipline]]
