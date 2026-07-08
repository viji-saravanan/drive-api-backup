---
doc_id: drive-backup-app-standard-practice-assessment
status: active
last_updated: 2026-07-08
context_role: gap-assessment
read_when:
  - The agent needs to compare this app against common backup/sync app practices.
  - The agent is deciding whether a feature is standard practice or project-specific scope creep.
do_not_read_when:
  - The task is a narrow implementation bug with requirements already loaded.
---

# Drive Backup App Standard Practice Assessment

This note compares the current plan against common backup/sync app behavior and platform guidance. It records what should be adopted, deferred, or explicitly rejected.

## Adopt In MVP

| Practice | Why It Belongs Here | Current Gap Closed |
|---|---|---|
| Setup preflight check | Backup apps should fail before a long sync if account, folder, Drive, notification, network, or email setup is broken. | Adds a visible readiness gate before first backup. |
| Backup health dashboard | Users need to know whether they are protected now, not just whether a worker was scheduled. | Adds last success age, failed files, pending retries, unprotected folders, and Drive destination health. |
| Upload verification | Mature tools verify copied data by size/hash or equivalent metadata when available. | Adds post-upload confidence and a verification-mismatch failure path. |
| Dry-run / preview before first sync | Sync tools commonly show what will happen before a destructive or large operation. | Lets users see file counts, bytes, skipped folders, and mobile-data impact before first upload. |
| Explicit cancel/pause/resume controls | Long-running transfer apps expose user control. | Reinforces cancellation as a first-class user action. |
| Notification and foreground progress | Android long transfers need user-visible progress and notification behavior. | Adds notification permission and cancel action considerations. |
| Ignore/exclude rules | Sync apps commonly support ignore patterns and filters. | Prevents backing up cache/temp files and gives future users control. |
| Backup completeness report | Backup tools need a report of what was scanned, uploaded, skipped, failed, and unprotected. | Makes email, history, and diagnostics consistent. |
| Recovery drill reminder | Backup is not proven until restore/access is tested. | Adds scheduled or user-visible reminder to validate Drive contents. |

## Adopt After MVP

| Practice | Why It Is Deferred |
|---|---|
| Full per-folder include/exclude pattern language | Useful but easy to overbuild. MVP can start with simple excludes and skipped-file visibility. |
| Per-folder retention policies | Important, but needs a storage trade-off decision first. |
| Desktop decrypt/restore tool | Only needed if private encrypted backup becomes real. |
| Multi-destination backup | Better resilience, but violates the first MVP's simplicity. |
| Cloud-to-local restore engine | Valuable, but MVP can use readable Drive files and guided restore. |

## Explicitly Reject For MVP

| Practice | Reason |
|---|---|
| Two-way sync | Increases conflict and deletion risk. This product is backup-first. |
| Automatic remote deletion mirroring | Too dangerous after accidental local deletion or reset. |
| Hidden upload with Arya's credentials | Security boundary violation. Users must sign in with their own account. |
| Exact scheduled timing | Android controls background execution timing. |

## New Requirements From This Assessment

- Add preflight before first sync and before reconnecting Drive.
- Add health dashboard with stale-backup warning.
- Verify upload completion with available metadata.
- Add dry-run preview for first sync and large manual sync.
- Add notification progress and cancel action requirements.
- Add simple exclude behavior and skipped-file reporting.
- Add backup completeness report.
- Add recovery drill reminder.

## Sources Behind The Assessment

- Syncthing supports file versioning and ignore patterns.
- rclone has a `check` command that compares source and destination.
- restic has repository checking and data verification concepts.
- FolderSync exposes folder-pair settings, sync direction, and related sync controls.
- Android foreground/data-transfer guidance requires careful user-visible long-running transfer behavior.
- Android Auto Backup exists for app data, but this app must avoid backing up secrets accidentally.

## Open Trade-Off

Version retention is the main unresolved practice.

Recommended answer: keep a limited archive of old remote versions before overwriting changed files, such as last 3 versions or 30 days, whichever is smaller. This protects against accidental overwrite with corrupt or unwanted local changes, but it uses extra Drive storage.

## Next Notes

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Open Questions And Assumptions]]
- [[Drive Backup App Source Register]]
