---
doc_id: drive-backup-app-strategy
status: active
last_updated: 2026-07-08
context_role: product-strategy
read_when:
  - The agent needs product intent, target user, metrics, or non-goals.
do_not_read_when:
  - The task is a narrow implementation bug and requirements are already loaded.
---

# Drive Backup App Strategy

## Target Problem

Android device resets can destroy local data when important folders are not continuously backed up. The app exists to make selected-device-folder backup boring, visible, and recoverable without a paid service.

## Approach

Build a personal Android app that syncs user-selected internal-storage folders into a Google Drive folder shared by Arya. The signed-in user authorizes upload access with their own Google account. The app must keep syncing other files even when individual files fail, and it must send a completion email after every sync attempt.

## Who It Is For

- Primary user: Arya, protecting personal Android data.
- Secondary users: trusted people Arya shares the APK and Drive folder with.
- Operator: Arya, who controls the shared Drive parent folder, allowed users, GitHub releases, and release process.

## Success Metrics

- A selected folder can be backed up to Drive without manual file copying.
- A manual sync can be started and observed from the app.
- A periodic sync eventually runs under Android background constraints.
- A failed file does not interrupt syncing of other files.
- Every sync attempt leaves a local run record and attempts an email summary.
- A reset/reinstall recovery drill can find the backed-up files in Drive.

## Active Tracks

- Knowledge base and implementation plan.
- Android app foundation.
- Google sign-in and allowlist.
- Local folder selection through Android folder picker.
- Shared Google Drive destination setup.
- Sync engine and failure handling.
- Progress UI and sync history.
- Email notification adapter.
- Heavy test suite.
- APK release workflow.

## Not Working On For MVP

- Play Store distribution.
- Paid backend service.
- Enterprise device management.
- Automatic remote deletion mirroring.
- Full one-tap restore.
- Client-side encryption.
- Cross-platform desktop app.

## Product Principle

Backup correctness beats convenience. If a decision risks silent data loss, prefer visible warnings, conservative behavior, and explicit user action.

## Next Notes

- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Open Questions And Assumptions]]
