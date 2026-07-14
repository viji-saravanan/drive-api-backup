---
doc_id: drive-backup-app-failure-matrix
status: active
last_updated: 2026-07-13
context_role: failure-behavior
read_when:
  - The agent implements folder access, sync, Drive upload, email, retries, or tests.
do_not_read_when:
  - The task is only product wording.
---

# Drive Backup App Failure Matrix

Core invariant: one bad file must not stop the rest of the sync.

| Scenario                                           | Expected Behavior                                                                 | Manual Intervention                        | Required Test                    |
| -------------------------------------------------- | --------------------------------------------------------------------------------- | ------------------------------------------ | -------------------------------- |
| Local file unreadable                              | Mark file failed, continue other files                                            | User may inspect permission or delete file | Unit and fake integration        |
| Local file changes during upload                   | Retry once after metadata refresh, then defer if still changing                   | None unless repeated                       | Fake integration                 |
| Local file deleted before upload                   | Mark missing locally, skip upload                                                 | None                                       | Unit                             |
| Folder picker cancelled                            | Clear matching request; do not persist mapping or grant                            | None                                       | Contract and live Samsung        |
| User tries to select internal-storage or Downloads root | Explain Android's system restriction; allow an eligible subfolder; never fake success | Choose subfolder or approve a future dedicated broad-access source | Live Samsung and official contract |
| Stale or mismatched picker callback                | Ignore before taking a persistent grant                                            | User starts a current request              | Contract and recreation          |
| Duplicate picker launch                            | Keep the singleton request; do not open another picker                             | None                                       | Room/contract                    |
| Picker returns invalid or non-tree URI             | Reject selection; do not persist a grant                                           | User selects again                         | Contract/provider                |
| Picker returns exact duplicate URI                 | Reject duplicate without changing existing mapping/grant                           | None                                       | Room and live Samsung            |
| Persistable read flag missing                      | Reject selection and preserve prior state                                          | User retries with supported provider       | Provider contract                |
| Write grant unexpectedly retained                  | Release write mode, retain/revalidate read mode, fail closed if reduction fails     | None unless provider remains unsafe        | Instrumented and live Samsung    |
| Room write fails after grant acquisition           | Mark cleanup pending and release only an unreferenced grant                         | Retry add/repair                           | Crash/compensation test          |
| Process dies between URI grant and Room commit     | Reconcile pending URI and exact grant; finalize or abandon deterministically        | None                                       | Process crash-point test         |
| Selected folder permission revoked                 | Mark only that mapping `PermissionMissing`; continue other mappings                 | User repairs folder access                 | Instrumented and live Samsung    |
| Selected folder moved or deleted                   | Mark only that mapping `TreeMissing`; never delete mapping or other content         | User repairs or removes mapping            | Dedicated live test tree         |
| Provider authentication required                  | Mark provider-auth state; do not misreport app permission denial                    | User authenticates provider or repairs     | Provider contract               |
| Folder provider temporarily unavailable            | Preserve mapping, report temporary state, allow retry                               | None unless persistent                     | Provider contract and device     |
| Folder query returns null cursor                   | Treat scan as failed/partial per progress oracle, never as empty                    | Retry                                      | Controllable provider            |
| Folder query reports loading or error extras       | Preserve partial counts and mark scan partial                                      | Retry after provider settles               | Controllable provider            |
| Folder row has null/invalid metadata               | Record a Phase 3 issue, continue siblings, never fabricate a filesystem path        | None unless repeated                       | Controllable provider            |
| Folder display-name query fails                    | Keep valid mapping with generic label; retry metadata backfill on later reconciliation | None unless provider remains unavailable | Provider and repository          |
| Provider exposes repeated/cyclic document IDs      | Stop revisiting that identity, report partial result, continue unrelated branches   | None                                       | Controllable provider            |
| Scan cancelled during blocking provider query      | Cancel active signal, close cursor, keep mapping, reject stale progress             | User can scan again                        | Contract/provider/live Samsung   |
| One mapping fails while another is healthy         | Keep jobs/results isolated; healthy mapping still completes                         | Repair only failed mapping                 | Contract and live Samsung        |
| Repair selects the same URI                        | Revalidate without releasing the mapping's existing read grant                      | None                                       | Compensation and live Samsung    |
| Repair replacement fails                           | Keep original URI and grant; clean only unreferenced replacement                    | User retries repair                        | Compensation test               |
| Remove grant release fails                         | Keep mapping visible and unchanged; report retryable removal-access failure          | User retries                               | Repository and UI                |
| Room delete fails after removal grant is released  | Keep mapping visible without grant; retry idempotent release then delete             | User retries                               | Forced SQLite failure/retry      |
| Cancelled removal completes after a newer removal  | Discard stale outcome and cleanup by operation generation; preserve newer progress   | None                                       | ViewModel coroutine-race test    |
| Home or folder picker backgrounds approved app     | Retain approval in the same process; do not launch another Google chooser            | None                                       | Lifecycle instrumentation        |
| App signs out with pending folder operation        | Mark operation abandoning before auth clear; retry idempotent grant cleanup          | Sign in again only for new work            | Cross-feature crash test         |
| Android blocks folder selection                    | Explain unsupported folder, do not add mapping                                    | User picks different folder                | Manual/instrumented              |
| Setup preflight fails                              | Block automatic sync and show repair action                                       | User repairs blocking item                 | Unit/instrumented                |
| Network lost mid-upload                            | Pause run, preserve retry state, resume later                                     | None                                       | Fake integration and device      |
| Metered network when Wi-Fi required                | Defer sync                                                                        | None                                       | Work constraints test            |
| Mobile data enabled by user                        | Upload according to mobile data settings and any file-size limit                  | User may disable mobile data               | Unit and Work constraints test   |
| Mobile data disabled but manual override confirmed | Run that manual sync only with override recorded in history                       | None                                       | Unit/instrumented                |
| Mobile data file exceeds user limit                | Defer or skip file with visible reason, continue other files                      | User raises limit or uses Wi-Fi            | Unit and fake integration        |
| Battery low                                        | Defer periodic sync unless manual override is allowed                             | None                                       | Work constraints test            |
| Google auth expired                                | Refresh token if possible                                                         | Sign in again if refresh fails             | Fake auth test                   |
| User removed from allowlist                        | Stop sync, show gated status, do not upload                                       | Arya re-adds account                       | Unit/instrumented                |
| Shared folder missing                              | Stop destination sync, preserve local mappings                                    | Arya/user reconnects folder                | Fake Drive                       |
| Shared folder permission removed                   | Stop destination sync, show permission repair                                     | Arya restores sharing                      | Fake Drive                       |
| Drive 401 or 403                                   | Try auth repair path, then pause Drive sync                                       | User signs in or Arya fixes sharing        | Fake Drive                       |
| Drive 429                                          | Retry with backoff                                                                | None                                       | Fake Drive                       |
| Drive 5xx                                          | Retry with backoff                                                                | None unless persistent                     | Fake Drive                       |
| Drive quota exceeded                               | Stop new uploads, preserve pending queue, email/report quota status               | User frees storage or changes destination  | Fake Drive                       |
| Resumable upload session expired                   | Start new upload session                                                          | None                                       | Fake Drive                       |
| Upload verification mismatch                       | Mark file failed, preserve retry state, continue other files                      | None unless persistent                     | Fake Drive                       |
| Duplicate Drive file at destination                | Reconcile by stored ID/metadata; preserve both if uncertain                       | User may resolve conflict                  | Fake Drive                       |
| Local database corrupted                           | Enter recovery mode and rebuild where possible from Drive metadata                | User may reconnect folders                 | Recovery test                    |
| App killed mid-sync                                | Worker resumes or records interrupted state                                       | None                                       | Device/instrumented              |
| User cancels sync                                  | Stop new uploads, preserve completed results and retry state, write Cancelled run | User can retry later                       | WorkManager and fake integration |
| Notification permission denied                     | Keep in-app progress; explain limited background visibility                       | User grants notification permission        | Instrumented                     |
| Device reboot                                      | Scheduled work and permissions survive where Android allows                       | User repairs lost permissions              | Device                           |
| Email adapter unavailable                          | Record email failure, queue retry, do not fail sync                               | None unless persistent                     | Fake email                       |
| Email quota exceeded                               | Record quota status and retry next day/window                                     | None                                       | Fake email                       |
| APK installed over old version                     | Migrate local state or fail safe with backup prompt                               | User exports diagnostics                   | Upgrade test                     |
| Version archive exceeds retention or quota         | Apply retention cleanup if enabled; otherwise stop new archive writes and report  | User changes retention or frees storage    | Fake Drive                       |

## Error Classification

- Retryable: network loss, Drive 429, Drive 5xx, expired upload session.
- Repairable by user: folder permission revoked, sign-in revoked, Drive sharing removed.
- Permanent per-file: unreadable file, blocked path, unsupported local access.
- Product-risk: local database corruption, migration failure, duplicate/conflict ambiguity.

## Reporting Rules

- Sync history records all failures.
- Email includes failed filenames and reasons.
- UI separates "retrying automatically" from "needs manual action".
- Diagnostics never include OAuth tokens, email credentials, or file contents.

## Next Notes

- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Standard Practice Assessment]]
