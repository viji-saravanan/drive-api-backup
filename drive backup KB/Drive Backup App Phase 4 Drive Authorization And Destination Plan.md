---
doc_id: drive-backup-app-phase-4-drive-authorization-destination-plan
status: active
last_updated: 2026-07-18
context_role: implementation-plan
read_when:
  - The agent touches Google Drive authorization, the configured shared folder, or destination health.
  - The user sees Drive consent, authorization, account mismatch, or destination-access behavior.
do_not_read_when:
  - The task only concerns local scanning, scheduling, email, or visual styling.
depends_on:
  - drive-backup-app-phase-4-session-persistence-plan
  - drive-backup-app-security-privacy-access
  - drive-backup-app-testing-plan
---

# Phase 4 Drive Authorization And Destination Plan

**Goal:** Authorize the currently approved Google account for Drive, verify that
the configured shared destination is a writable folder, and expose a precise
repairable health state without persisting Google tokens or exposing private
configuration.

**Architecture:** Sign-in remains the local app gate. A separate Google Play
services `AuthorizationClient` request is bound to the approved account and
requests Drive data access only when Drive is probed or connected. The returned
short-lived access token remains in memory for one request and is passed to a
small Drive REST adapter. Every later worker repeats silent authorization; if
Google requires user interaction, work stops with `NeedsAuthorization` instead
of launching consent from the background.

**Tech stack:** Kotlin, Coroutines, Google Play services Auth 21.6.0,
`AuthorizationClient`, Activity Result APIs, `HttpURLConnection`, structured
JSON parsing, Compose, JUnit, Android instrumentation, real Google Drive
acceptance on a physical phone.

## Scope Decision

The configured destination is an existing manually-created folder shared with
the signed-in user. `drive.file` alone cannot reliably access that fixed folder:
Google limits it to files created/opened by the app or explicitly shared with
the app through Google Picker or an equivalent app picker.

The personal fixed-destination build therefore requests:

```text
https://www.googleapis.com/auth/drive
```

This is a restricted scope. Backup/sync is an officially qualifying use case,
but broader public distribution can require OAuth verification. Before a
general public release, reassess an app-created destination anchor or Google
Picker plus `drive.file`; do not silently weaken or broaden scope.

Runtime least privilege remains strict even with the broad Google grant:

- only the configured destination ID is queried or used as a parent;
- no Drive listing, search, sharing, delete, trash, or ownership API is exposed
  by this slice;
- no offline server access or authorization code is requested;
- no access token, refresh token, result intent, or response body is persisted
  or logged;
- sign-out hides Drive state but does not claim to revoke Google's server-side
  grant; an explicit disconnect/revoke action is a later user-facing decision.

## Account Binding

- Build the authorization request with the exact approved normalized account.
- Never use account-selection prompt mode during Drive authorization.
- Bind the request with `setAccount` to the approved normalized address. If the
  authorization result exposes an account email, verify it; Google may omit
  account metadata, so absence alone is not treated as a mismatch.
- Any mismatch is terminal and fail closed. Discard the token, expose no
  destination state, and require account repair.
- A local cached app session is not Drive authorization. Every Drive operation
  obtains a current token or returns `NeedsAuthorization`.

## State Contract

| State | Meaning | User action |
|---|---|---|
| `Inactive` | No approved local account is active. | Sign in |
| `Checking` | Silent authorization or destination check is running. | Wait or leave app |
| `NeedsAuthorization` | Scope is absent, expired, revoked, or needs Google UI. | Connect Drive |
| `AwaitingAuthorization` | Google consent/account-owned resolution is open. | Complete or cancel Google UI |
| `ConfigurationRequired` | Destination ID or Google authorization setup is absent/invalid. | Project owner repairs build/cloud setup |
| `AccountMismatch` | Authorization did not return the approved account. | Repair account; never continue |
| `DestinationMissingOrInaccessible` | Drive returns not found or intentionally hides an inaccessible folder. | Restore sharing or destination |
| `DestinationNotFolder` | Configured ID resolves to a non-folder file. | Repair configuration |
| `DestinationTrashed` | Folder exists but is trashed. | Restore or replace folder |
| `DestinationReadOnly` | User can see but cannot add children. | Grant Editor access |
| `DestinationQuotaExceeded` | The destination owner or shared container has no usable capacity. | Free storage, then refresh |
| `TemporaryFailure` | Network, timeout, 429, or 5xx prevents a current answer. | Retry; do not erase setup |
| `Ready` | Current account can read the folder metadata and add children. | Continue setup or refresh |

Cancellation restores `NeedsAuthorization` or the last known non-authoritative
state. It never reports `Ready`. Late callbacks are matched to one request ID
and cannot overwrite a newer account, sign-out, retry, or destination result.

## Destination Probe Contract

Use `files.get` on only the configured ID with `supportsAllDrives=true` and an
explicit fields mask containing only:

```text
id,mimeType,trashed,capabilities(canAddChildren,canListChildren)
```

Classify:

- `200`: parse strictly; require folder MIME type, not trashed,
  `canListChildren=true`, and `canAddChildren=true`;
- `401`: current authorization is unusable; return `NeedsAuthorization`;
- `403`: distinguish rate/quota/transient reasons when safely available;
  otherwise return inaccessible/read-only without exposing server text;
- `404`: missing or inaccessible, because Drive can intentionally hide access;
- `429` and `5xx`: temporary and retryable;
- malformed success JSON, unexpected redirects, oversized bodies, and all other
  status codes: fail closed with a typed non-sensitive result.

Never include the folder ID, access token, account address, response body, or
Drive error message in UI, logs, exceptions, test names, commits, or PR output.

## Task 1: Configuration And Pure State

- [x] Validate non-empty destination configuration without exposing its value.
- [x] Add typed request, result, health, and UI states.
- [x] Write RED tests for every transition, duplicate request, cancellation,
  stale callback, account change, sign-out, retry, and configuration failure.
- [x] Commit configuration/domain state independently.

## Task 2: Google Authorization Adapter

- [x] Add the pinned official Play services Auth dependency.
- [x] Build an account-bound request for exactly the restricted Drive scope.
- [x] Map already-authorized, resolution-required, cancelled, unavailable,
  malformed, missing-scope, missing-token, and account-mismatch outcomes.
- [x] Launch the returned `PendingIntent` through Activity Result and correlate
  the callback across activity recreation.
- [x] Distinguish an AndroidX-synthesized `SendIntentException` result from an
  ordinary user cancellation.
- [x] Prove tokens and result intents are never persisted or logged.
- [x] Commit authorization independently.

## Task 3: Destination Health Adapter

- [x] Add a bounded HTTPS transport with connect/read timeouts, response-size
  limit, no redirects, and cancellation-safe cleanup.
- [x] Parse structured JSON and classify every status/metadata row above.
- [x] Write RED tests for 200 variants, 401, 403 reasons, 404, 429, 5xx,
  malformed/oversized response, timeout, cancellation, and thrown transport.
- [x] Ensure one failed check cannot change local folder or Downloads state.
- [x] Commit destination health independently.

## Task 4: Protected UI And Composition

- [x] Add a compact Drive section visible only for an approved local account.
- [x] Expose Connect, Retry/Refresh, and precise non-sensitive state copy.
- [x] Disable duplicate actions while a request or Google resolution is active.
- [x] Deactivate on sign-out/account change and ignore retired callbacks.
- [x] Add Compose and real `MainActivity` tests for all controls and lifecycle
  boundaries without rendering IDs, scopes, tokens, or server payloads.
- [x] Commit presentation/composition independently.

## Task 5: Physical Live Acceptance

- [x] Explicit Connect traverses Google Play services resolution for the
  currently approved account.
- [ ] Cancel/back leaves `NeedsAuthorization` and exposes no destination claim.
- [x] Grant reaches `Ready` against the real configured shared folder.
- [x] Force-stop/relaunch silently restores current health without a chooser or
  consent screen when the grant remains valid.
- [ ] Remove Editor access: destination becomes inaccessible/read-only while all
  local mappings and Downloads configuration remain intact.
- [ ] Restore Editor access and recover with Retry.
- [ ] Revoke Google Drive grant and prove no request reaches Drive with a stale
  token; interactive repair succeeds.
- [ ] Exercise offline, airplane mode, and interrupted consent as temporary or
  authorization states, never success.
- [ ] Run both flavor unit/APK/Android-test APK/lint checks and physical
  instrumented tests on Android user 0.
- [x] Record only redacted state/count evidence and push sequential commits.

## Current Evidence

On 2026-07-18, both-flavor unit, app APK, Android-test APK, and lint tasks
passed. A focused 19-test instrumentation set passed on the Samsung Android
user-0 target.
The public debug APK was then installed in place, preserving the real approved
session. Explicit Drive connection reached the configured folder's real
writable/listable `Ready` state. A force-stop and cold start returned directly
to that state without account or consent UI. Persistent app-data and app-process
log scans found no common token markers.

The unchecked live rows above are deliberately not inferred from deterministic
tests. Editor ACL changes, Drive grant revocation, offline mode, and user
cancellation remain required before this plan's full exit gate can be claimed.

## Exit Gate

- The approved account explicitly grants Drive access and the app proves the
  configured shared destination is a writable, listable, non-trashed folder.
- Ordinary relaunch performs a silent check when Google can satisfy it; no
  automatic consent UI opens.
- Denial, cancellation, account mismatch, revoked grant, removed sharing,
  network failure, and malformed responses never appear ready.
- No Google token or private account/folder identifier is persisted, logged,
  committed, or rendered.
- Only after this gate passes may the first controlled Drive create/upload probe
  and per-user/per-device destination creation become active.
