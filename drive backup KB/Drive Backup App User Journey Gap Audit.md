---
doc_id: drive-backup-app-user-journey-gap-audit
status: active
last_updated: 2026-07-15
context_role: user-experience-acceptance
read_when:
  - A phase is being closed or a new user-facing workflow is being started.
  - The agent needs to check ordinary-user behavior rather than only implementation behavior.
do_not_read_when:
  - The task is an isolated internal refactor with no user-visible behavior.
---

# Drive Backup App User Journey Gap Audit

## Purpose

This audit checks whether `Viji Backup` behaves as a normal user would expect,
separately from visual design quality. It exists because a behavior can satisfy
an implementation contract and still be a poor user experience.

The audit must be rerun at every phase boundary. Findings are recorded as
observed, specified-but-not-implemented, implemented-but-not-live-proven, or
closed with evidence. A passing unit test does not close a user-journey item
when the behavior depends on Android, Google, Drive, a real phone, or a real
network.

## Current High-Priority Findings

| ID | User journey gap | Current evidence | Expected behavior | Owner and gate |
|---|---|---|---|---|
| UX-01 | The Google account chooser appears again after the user closes and reopens the app. | Current Phase 2 deliberately loads a cached account as `ReauthenticationRequired` and starts an authorized-account Credential Manager request on a cold process. The user reports a chooser on every reopen. | A user remains signed in across normal relaunch, process death, and phone restart. Google reauthentication is requested only after explicit sign-out, account removal, revoked access, or a security-required failure. | Phase 2 follow-up; mandatory before Phase 4 Drive setup. Test on Samsung after swipe-away, force-stop, reboot, and upgrade. |
| UX-02 | The account used for backup is not yet a durable, obvious user decision. | The app can show the saved email during reauthentication, but the chooser is being used as a repeated entry point. | Show the current signed-in account and provide an explicit `Change account` action. Do not use incidental app relaunch as account selection. | Phase 2/4. Test approved-account switch, blocked account, account removed from device, and sign-out. |
| UX-03 | Folder rows can be difficult to identify when folders have the same name or metadata is unavailable. | Rows use provider display names and fall back to `Folder 1`, `Folder 2`, and so on. The user already reported difficulty identifying which folder to remove. | Every mapping must have a stable user-recognizable label. Same-name folders need a safe disambiguator or an explicit user label. Never expose raw storage IDs or full sensitive paths. | Phase 3 follow-up; must close before sync setup is considered user-ready. Test duplicate names, missing metadata, reorder, restart, repair, and removal. |
| UX-04 | Android rejects the top-level Downloads root through the normal folder picker. | This is a platform boundary, not an implementation success. The current picker correctly refuses to claim access. | Explain the restriction in plain language, offer the approved dedicated Downloads flow, show its access health, and provide a clear denial/revocation repair path. | Phase 4 first milestone. Live Samsung evidence required for grant, denial, revocation, and repair. |
| UX-05 | `Folder Access: Ready` can be mistaken for “backed up.” | Phase 3 currently proves persisted read access and a metadata scan, not a Drive backup. | Separate `Access ready`, `Backup not configured`, `Never backed up`, `Last backup`, `Partial success`, and `Needs attention`. | Phase 4/5/6. Add state-contract tests and live first-run verification. |
| UX-06 | A user can reach a long-running operation without one consolidated readiness answer. | Setup preflight is required by the PRD but is not implemented yet. | Before first sync and before reconnecting Drive, show account, local access, Drive destination, network, battery, notification, storage, and email readiness with a repair action for each blocker. | Phase 4/6. A blocked preflight must explain why sync cannot start and must not silently schedule a doomed worker. |
| UX-07 | Cancellation behavior is not yet defined from a sync user's perspective. | Phase 3 scan cancellation exists; actual upload cancellation is future work. | After Cancel, show `Cancelling`, then a terminal `Cancelled` result. Preserve completed files, retain incomplete files for retry, and never report cancellation as total failure. | Phase 5/6. Test cancellation before a file, during a file, between files, after the last file, after process death, and on retry. |
| UX-08 | Per-file failures could be technically isolated but still hidden from the user. | Failure isolation and email filenames are requirements, but sync and completeness-report surfaces are not implemented. | Continue all eligible files, show counts and retryable failures, list failed filenames and reasons in history and email, and distinguish failed from skipped and cancelled. | Phase 5/7. Test unreadable, changing, deleted, oversized, quota-blocked, and verification-mismatch files in one run. |
| UX-09 | Periodic sync timing can be misunderstood as exact. | Android controls WorkManager execution timing; the product explicitly rejects exact scheduling. | Show the selected frequency, the next eligible window or last attempt, and why a run was deferred. Never promise an exact clock time. | Phase 6. Test Doze, battery saver, reboot, metered network, offline period, and delayed execution. |
| UX-10 | Network and battery choices need to be understandable at the moment they matter. | Settings are specified but not implemented. | Clearly state when mobile data may be used, show a one-time override consequence, explain charging/low-battery deferral, and preserve the effective settings in history. | Phase 6. Test Wi-Fi, metered network, mobile data allowed/blocked, file-size limit, low battery, charging-only, and manual override. |
| UX-11 | A Drive sharing or destination failure can be confused with a local-folder failure. | Drive destination setup and health are future work. | Identify the failing boundary as Google account, Drive sharing, destination folder, quota, or local folder. Preserve local mappings and give the smallest repair action. | Phase 4/5. Test deleted destination, revoked Editor access, 401, 403, 429, 5xx, and quota exhaustion. |
| UX-12 | Email delivery is part of the requested result but must not become a hidden dependency. | Email is future work; the product requires email after every attempt and says email failure must not fail sync. | Show whether the sync succeeded independently of whether the summary email succeeded. Include failed filenames only in configured recipients' messages and record retry state. | Phase 7. Test relay unavailable, quota exceeded, malformed recipient configuration, partial-success summary, and privacy-safe content. |
| UX-13 | Backup existence is not the same as recovery readiness. | The PRD requires laptop-readable Drive contents and a recovery drill, but the recovery surface is future work. | Show last verified backup, remind the user to inspect Drive from a laptop, and explain that removing the app or resetting the phone does not delete Drive files. | Phase 8. Test after app data loss, reinstall, device reset, and changed Google account. |
| UX-14 | Low storage, notification denial, battery restrictions, and app updates can make a user believe backup is active when it is not. | These cases are listed in the failure/testing plans but are not yet part of a unified health experience. | Surface each condition with its effect, severity, and repair action. Never silently claim protection when no successful backup is recent. | Phase 6/8/9. Live Samsung and upgrade evidence required. |

## Phase Entry And Exit Gates

### Before Phase 4 implementation

- Resolve UX-01, or explicitly block Drive setup until the session policy is
  changed and tested.
- Define the current-account and `Change account` behavior from UX-02.
- Define the exact top-level Downloads consent, denial, revocation, and repair
  journey from UX-04.
- Decide the user-visible distinction between folder access health and backup
  health from UX-05.

### Before Phase 5/6 sync implementation

- Implement the preflight contract from UX-06.
- Freeze terminal sync states and cancellation semantics from UX-07.
- Freeze per-file outcome vocabulary and completeness-report fields from UX-08.
- Define the deferred-work explanation and effective-settings history from
  UX-09 and UX-10.

### Before public release

- Complete live Drive, email, recovery, low-storage, notification, update, and
  account-removal journeys.
- Run the ordinary-user matrix below on the Samsung baseline and at least one
  second Android environment.
- Confirm that every failed or deferred state has an in-app explanation and a
  recovery action, or is explicitly documented as non-recoverable without
  administrator action.

## Ordinary-User Acceptance Matrix

| Journey | Action sequence | Required observation |
|---|---|---|
| Returning user | Sign in once, leave app, swipe it away, reopen | No Google chooser; same approved account and mappings are available. |
| Security boundary | Explicitly sign out, reopen, sign in with blocked account | App remains blocked and does not expose protected content or sync controls. |
| Account change | Use explicit `Change account`, choose another approved account | Account changes only after the user requests it; mappings and policy are shown according to the documented co-administrator rule. |
| Folder selection | Add two same-named folders, close/reopen, remove the second | User can identify the intended mapping without URI or storage-ID knowledge. |
| Downloads | Attempt restricted root, follow dedicated flow, deny it, later grant it | The app explains each state and never represents denied access as protected. |
| First backup | Complete setup with one healthy folder and one blocked dependency | Preflight identifies the blocker before upload and gives a repair path. |
| Partial result | Include readable, unreadable, changing, and skipped files | Remaining eligible files complete; report, history, and email agree on each outcome. |
| Cancellation | Cancel during a large upload, close app, reopen, retry | Completed work remains complete; incomplete work is retryable; the result is `Cancelled`. |
| Deferred schedule | Enable Wi-Fi-only periodic sync, use mobile data, then reconnect Wi-Fi | App explains deferral and later runs when constraints permit; no false success is shown. |
| Recovery | Confirm a backup, remove app data or use a replacement phone, open Drive on laptop | Drive files remain readable and the recovery instructions identify what can be restored or reconfigured. |

## Explicit Non-Goals For This Audit

This audit does not require visual redesign, private encryption, full pattern
exclusions, multi-destination backup, or one-tap restore. Those remain separate
product decisions. It does require clear user-facing state and recovery action
where those future features affect current expectations.

## Evidence Rules

- A code path is not user-journey evidence when Android, Google, Drive, email,
  scheduling, or process lifetime controls the result.
- A deterministic hostile test is supporting evidence for failure isolation; it
  does not replace the named live acceptance case.
- Evidence must redact account addresses, URIs, device serials, filenames that
  reveal private content, tokens, and client secrets.
- Every closed finding must link to the test, live observation, or decision that
  closed it.

## Next Notes

- [[Drive Backup App Index]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Project State]]
