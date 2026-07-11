---
doc_id: drive-backup-app-open-questions-assumptions
status: active
last_updated: 2026-07-10
context_role: assumptions
read_when:
  - The agent needs unresolved decisions, risks, or assumptions.
do_not_read_when:
  - The task is purely mechanical and all relevant behavior is already specified.
---

# Drive Backup App Open Questions And Assumptions

## Decided

- Use APK distribution, not Play Store for MVP.
- Use private/internal APK and public APK surfaces.
- Users sign in with their own Google account.
- Arya shares the Drive parent folder with approved users.
- Failed filenames are included in email summaries.
- Folder targets are user-selectable internal-storage folders.
- MVP uses normal readable Drive files.
- Client-side encryption is future-only.
- Do not store Arya's Drive credentials on user devices.
- Do not auto-delete remote files in MVP.
- Drive upload folder is `My Drive > Viji > BACKUP > Viji Phone Uploads`, owned by the project owner's Google account.
- Four Google accounts are approved: two owner identities and two primary-user identities. Actual addresses live only in ignored or encrypted private configuration.
- Completion email should go to the owner and primary user. Actual recipients must be configured in the server-side mail relay, not in the APK.
- MVP email method is Google Apps Script `MailApp` relay owned by Arya.
- Samsung Galaxy A23 (`SM-A236E`) is the primary physical test device. It runs Android 14 / One UI 6.1; device serial and account addresses remain outside evidence.

## Assumptions

- Users are trusted people selected by Arya.
- Users are comfortable with Arya having access to normal-mode backups in the shared Drive folder.
- Users have enough Google Drive quota or the shared-folder quota behavior is acceptable after testing.
- Android folder picker access is acceptable even if some special folders cannot be selected.
- Exact periodic sync timing is not required.
- Email quota is low enough risk for personal usage.

## Open Questions

| Question | Why It Matters | When To Resolve |
|---|---|---|
| Does upload into Arya's shared My Drive folder count against Arya's storage or the uploading user's storage? | Quota behavior affects user expectations and failure handling. | Phase 4 Drive destination spike |
| Can `drive.file` plus folder selection support the desired shared folder workflow end to end? | Determines OAuth scope risk and consent complexity. | Phase 4 Drive destination spike |
| What is the first supported Android version? | Affects permissions, WorkManager behavior, and testing matrix. | Android project setup |
| Should the public APK gate users with opaque Google subject identifiers, a server-side policy, or the authoritative Drive ACL? | Plain email allowlists injected into an APK can still be extracted even when absent from Git. | Before public release |
| Should MVP preserve old remote versions before overwrite? Recommended answer: yes, keep last 3 versions or 30 days, whichever is smaller. | Protects against corrupt or unwanted local changes being uploaded over the only good backup; costs Drive storage. | Before changed-file upload implementation |
| Should MVP include basic exclusions only, or full include/exclude pattern rules? Recommended answer: basic exclusions only. | Full pattern rules are useful but can delay the core backup engine. | Before settings implementation |

## Risk Register

| Risk | Mitigation |
|---|---|
| App silently misses folders Android blocks | Use folder picker only and show unsupported-folder explanation. |
| Shared folder setup works manually but fails through API | Early Drive spike before sync engine. |
| User thinks backups are private from Arya | Clear setup copy and normal/private mode language. |
| Public APK leaks internal diagnostics | Separate build flavor and release checklist. |
| Future AI skips failure handling | Index and context guide require loading failure matrix for implementation. |
| Backup silently becomes stale | Health dashboard, stale warning, and recovery drill reminder. |
| Primary phone is 99% full | Never delete user data automatically. Surface low-storage preflight and per-file failures, and require free-space recovery before large sync stress tests. |

## Next Notes

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Source Register]]
