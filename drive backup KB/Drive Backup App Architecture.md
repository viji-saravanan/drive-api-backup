---
doc_id: drive-backup-app-architecture
status: draft
last_updated: 2026-07-08
context_role: architecture
read_when:
  - The agent needs implementation boundaries, components, or platform constraints.
do_not_read_when:
  - The task is only product positioning or release process.
---

# Drive Backup App Architecture

## System Shape

The app is a native Android app. The app has no paid backend in MVP. It talks directly to Google services using the signed-in user's Google account and sends email through a free notification adapter.

## Major Components

| Component | Responsibility |
|---|---|
| Auth and Allowlist | Google sign-in, email extraction, allowlist gate, sign-out |
| Folder Access | Android folder picker, persisted URI permissions, permission repair |
| Drive Destination | Shared folder connection, Drive folder ID storage, destination health checks |
| Sync Planner | Compare local folder state with local ledger and Drive state |
| Upload Worker | Upload files, resume uploads, record per-file results |
| Progress Store | Durable progress and status read by UI |
| Sync History | Immutable run records and exportable diagnostics |
| Email Adapter | Build and send completion summaries |
| Settings | Folder mappings, sync constraints, email recipients, privacy mode |
| Release Diagnostics | Build version, account, permissions, Drive destination, recent errors |

## Drive Destination Model

Arya creates one shared parent folder in Google Drive and shares it with approved user accounts as Editor. The user signs into the app with their own Google account and authorizes the app to write into that shared folder.

Preferred Drive structure:

```text
Arya Shared Backup Folder/
  user@example.com/
    Device-Name-InstallId/
      Folder Mapping A/
      Folder Mapping B/
```

The app should store stable Drive file IDs for folders and files when available. Path strings alone are not enough because names can collide or move.

## Android Folder Access Model

Use Android's folder picker for local folders. The app only supports folders that Android allows the user to grant. If Android blocks a folder, the app must explain that the folder cannot be backed up directly.

The app should persist granted URI permissions and detect when a permission is lost, revoked, or no longer points to readable content.

## Background Work Model

Use WorkManager for periodic and retryable background sync. Use one-time work for manual sync and unique periodic work for scheduled sync. Manual sync should be able to run as user-initiated work with visible progress.

Do not promise exact periodic timing. WorkManager periodic execution is approximate and subject to Android power management.

## Local State Model

The local database should hold:

- account identity and allowlist status;
- selected local folder mappings;
- Drive destination folder IDs;
- file ledger entries;
- active sync run state;
- per-file sync events;
- retry state;
- email notification state.

The exact schema belongs in implementation planning, but the data must support idempotent sync, crash recovery, and diagnostics.

## Email Model

Email is a notification adapter, not part of sync correctness. A sync can succeed even if the email fails. Email failures are recorded and retried separately.

Preferred MVP option: Google Apps Script MailApp web app or another free adapter controlled by Arya. SMTP is a fallback, not the preferred design.

## Security Boundaries

- No Arya Google token on user devices.
- No Drive service account secret inside the APK.
- No OAuth refresh tokens in logs.
- No email credentials in source control.
- No private GitHub token inside APK.
- Signed APKs only.

## Known Platform Caveat

If the shared destination is a normal My Drive folder owned by Arya, files uploaded by another user may be owned by that uploading user even when placed in Arya's folder. Validate ownership and quota behavior in an early spike.

## Next Notes

- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App Source Register]]
