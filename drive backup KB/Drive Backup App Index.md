---
doc_id: drive-backup-app-index
status: active
last_updated: 2026-07-12
context_role: entrypoint
read_when:
  - Any future AI session starts work on the Android Drive backup app.
  - The agent needs to choose which notes to load without reading the whole vault.
do_not_read_when:
  - Never skip this note for app-related work.
---

# Drive Backup App Index

This is the entrypoint for the Android Drive backup app knowledge base.

The product is a personal Android backup app that periodically syncs user-selected internal-storage folders into a Google Drive folder owned by Arya and shared with the signed-in user. The app must support manual sync, progress visibility, per-file fallbacks, completion email summaries, user account gating, and a no-paywall distribution path through APKs.

## Current Product Decisions

- Distribution uses APK files, with a private/internal build visible only to Arya and a public build visible to others.
- App display name is `Viji Backup`.
- Base application ID is `com.aryasubramani.vijibackup`.
- Users sign in with their own Google account.
- Arya owns the shared Drive parent folder and shares it with approved users as Editor.
- The app uploads into the shared parent folder using the signed-in user account.
- Sync target is any internal-storage folder that Android allows the user to select through the folder picker.
- Users can configure folder mappings, sync frequency, network/mobile-data behavior, battery/charging behavior, manual sync, cancellation, and email recipients.
- Email summaries include failed filenames and failure reasons.
- MVP uses normal readable Drive files, not client-side encryption.
- Private encrypted per-folder backups are a future option only.
- Remote files are never auto-deleted in MVP.

## Minimal Context Packets

Load only the packet that matches the task. Do not read all notes by default.

| Task | Load These Notes |
|---|---|
| Product scope or requirements | [[Drive Backup App Product Requirements]], [[Drive Backup App Strategy]], [[Drive Backup App Open Questions And Assumptions]] |
| Current repo state or setup | [[Drive Backup App Fresh Laptop Setup And Test Runbook]], [[Drive Backup App Project State]], [[Drive Backup App Implementation Roadmap]], [[Drive Backup App GitHub And Release Workflow]], [[Drive Backup App Testing Plan]] |
| Fresh laptop, external SDK, ADB, or repeatable local testing | [[Drive Backup App Fresh Laptop Setup And Test Runbook]], [[Drive Backup App Project State]], [[Drive Backup App Testing Plan]], [[Drive Backup App GitHub And Release Workflow]] |
| Android engineering method | [[Drive Backup App Foundation Decisions]], [[Drive Backup App Android Engineering Research]], [[Drive Backup App Architecture]], [[Drive Backup App Testing Plan]], [[Drive Backup App Source Register]] |
| Any implementation or file edit | [[Drive Backup App Engineering Change Discipline]], then the packet for the touched feature |
| Google sign-in, allowlist, or sign-out | [[Drive Backup App Phase 2 Auth Implementation Plan]], [[Drive Backup App Security Privacy And Access]], [[Drive Backup App Testing Plan]], [[Drive Backup App Source Register]] |
| Standard-practice gap assessment | [[Drive Backup App Standard Practice Assessment]], [[Drive Backup App Product Requirements]], [[Drive Backup App Open Questions And Assumptions]], [[Drive Backup App Source Register]] |
| Architecture or implementation planning | [[Drive Backup App Architecture]], [[Drive Backup App Sync Semantics]], [[Drive Backup App Failure Matrix]], [[Drive Backup App Security Privacy And Access]] |
| Android storage, background sync, or permissions | [[Drive Backup App Architecture]], [[Drive Backup App Sync Semantics]], [[Drive Backup App Source Register]] |
| Settings, frequency, mobile data, or cancellation | [[Drive Backup App Settings Model]], [[Drive Backup App Product Requirements]], [[Drive Backup App Sync Semantics]], [[Drive Backup App Failure Matrix]], [[Drive Backup App Testing Plan]] |
| Google Drive shared folder behavior | [[Drive Backup App Security Privacy And Access]], [[Drive Backup App Architecture]], [[Drive Backup App Source Register]] |
| Email notification design | [[Drive Backup App Product Requirements]], [[Drive Backup App Failure Matrix]], [[Drive Backup App Security Privacy And Access]], [[Drive Backup App Source Register]] |
| Encryption discussion | [[Drive Backup App Security Privacy And Access]], [[Drive Backup App Product Requirements]] |
| Testing | [[Drive Backup App Fresh Laptop Setup And Test Runbook]], [[Drive Backup App Testing Plan]], [[Drive Backup App Failure Matrix]], [[Drive Backup App Product Requirements]] |
| GitHub, APK release, or account switching | [[Drive Backup App GitHub And Release Workflow]], [[Drive Backup App Source Register]] |
| Roadmap or sequencing | [[Drive Backup App Implementation Roadmap]], [[Drive Backup App Product Requirements]], [[Drive Backup App Testing Plan]] |
| Terminology | [[Drive Backup App Glossary]] |
| Context retrieval or anti-hallucination rules | [[Drive Backup App AI Context Guide]], [[Drive Backup App Context Packets]], [[Drive Backup App Source Register]] |

## Retrieval Rule

Future agents should start here, classify the task, load the smallest packet above, then follow each loaded note's "Next Notes" section only when the task needs it.

Do not infer Android, Google Drive, Gmail, Apps Script, or GitHub behavior from memory when [[Drive Backup App Source Register]] has a relevant source. Load the relevant source row first, then verify current docs if implementation depends on the exact behavior.

## Notes

- [[CONTEXT]]
- [[Drive Backup App AI Context Guide]]
- [[Drive Backup App Context Packets]]
- [[Drive Backup App Engineering Change Discipline]]
- [[Drive Backup App Foundation Decisions]]
- [[Drive Backup App Fresh Laptop Setup And Test Runbook]]
- [[Drive Backup App Project State]]
- [[Drive Backup App Phase 2 Auth Implementation Plan]]
- [[Drive Backup App Android Engineering Research]]
- [[Drive Backup App Strategy]]
- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Product Requirements]]
- [[Drive Backup App Architecture]]
- [[Drive Backup App Settings Model]]
- [[Drive Backup App Sync Semantics]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Security Privacy And Access]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Implementation Roadmap]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Open Questions And Assumptions]]
- [[Drive Backup App Glossary]]
