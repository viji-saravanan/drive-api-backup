---
doc_id: drive-backup-app-source-register
status: active
last_updated: 2026-07-08
context_role: sources
read_when:
  - The agent makes platform claims about Android, Google Drive, Gmail, Apps Script, GitHub, or security.
do_not_read_when:
  - The task is a product decision that does not depend on platform behavior.
---

# Drive Backup App Source Register

This register lists source-backed platform claims. Future agents must verify current official docs before implementation when behavior is critical.

## Android Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Storage Access Framework folder selection | https://developer.android.com/training/data-storage/shared/documents-files | 2026-07-08 | App can use folder picker and persist access, but Android blocks some locations such as storage roots and sensitive Android folders on modern versions. |
| WorkManager periodic work | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work | 2026-07-08 | Periodic work is suitable for deferrable background work but timing is not exact and has a minimum interval. |
| Data transfer background work | https://developer.android.com/develop/background-work/background-tasks/data-transfer-options | 2026-07-08 | Long-running/user-visible transfers need careful background execution choice and user-visible behavior. |
| WorkManager progress observation | https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe | 2026-07-08 | Work progress can be observed and surfaced to UI. |
| Android Keystore | https://developer.android.com/privacy-and-security/keystore | 2026-07-08 | Token/secret protection should rely on Android-recommended secure storage patterns. |
| Foreground service types | https://developer.android.com/about/versions/14/changes/fgs-types-required | 2026-07-08 | Long-running visible transfer behavior must account for foreground service type requirements if foreground services are used. |
| Notification runtime permission | https://developer.android.com/develop/ui/compose/notifications/notification-permission | 2026-07-08 | Android notification permission affects user-visible progress and should be tested. |
| Android app data backup | https://developer.android.com/identity/data/backup | 2026-07-08 | App's own settings backup must be considered carefully and must not include secrets. |
| AGP 9 built-in Kotlin | https://developer.android.com/build/migrate-to-built-in-kotlin | 2026-07-08 | AGP 9 enables built-in Kotlin support, so the app should not apply the old Kotlin Android plugin while built-in Kotlin is enabled. |
| Jetpack Compose setup | https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler | 2026-07-08 | Compose requires the Compose compiler Gradle plugin with Kotlin 2.x and Compose dependencies should use the Compose BOM. |

## Google Drive Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Drive OAuth scopes | https://developers.google.com/workspace/drive/api/guides/api-specific-auth | 2026-07-08 | Prefer narrow scopes such as `drive.file` when possible; broad Drive access needs stronger justification. |
| Drive uploads | https://developers.google.com/workspace/drive/api/guides/manage-uploads | 2026-07-08 | Drive supports media uploads and resumable uploads for file content. |
| Drive folders | https://developers.google.com/workspace/drive/api/guides/folder | 2026-07-08 | Folders are Drive files with folder MIME type; files can be created in a folder using `parents`. |
| Drive sharing permissions | https://developers.google.com/workspace/drive/api/guides/manage-sharing | 2026-07-08 | Drive files and folders have permissions with user/group/domain/anyone types and roles such as writer/reader. |
| Google Picker | https://developers.google.com/workspace/drive/picker/guides/overview | 2026-07-08 | Picker can let users select Drive files/folders to authorize app access. |
| Shared drives overview | https://developers.google.com/workspace/drive/api/guides/about-shareddrives | 2026-07-08 | Shared drives are organization-owned and differ from normal shared folders. This project is personal/free, so normal shared folder behavior must be tested. |

## Email Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Gmail API scopes | https://developers.google.com/workspace/gmail/api/auth/scopes | 2026-07-08 | Gmail send scope is sensitive; avoid it unless needed. |
| Gmail sending | https://developers.google.com/workspace/gmail/api/guides/sending | 2026-07-08 | Gmail API can send email but adds OAuth/scope complexity. |
| Apps Script web apps | https://developers.google.com/apps-script/guides/web | 2026-07-08 | Apps Script can expose a web app endpoint, but execution identity and permissions must be handled carefully. |
| Apps Script MailApp | https://developers.google.com/apps-script/reference/mail/mail-app | 2026-07-08 | MailApp can send email and check remaining daily quota. |
| Apps Script quotas | https://developers.google.com/apps-script/guides/services/quotas | 2026-07-08 | Email sending has quotas; quota failures must not fail sync. |
| Gmail app passwords | https://support.google.com/mail/answer/185833 | 2026-07-08 | App passwords require account settings such as 2-Step Verification and are not preferred for this app. |

## GitHub Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Repository visibility | https://docs.github.com/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility | 2026-07-08 | Public/private visibility is repository-level. |
| Releases | https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases | 2026-07-08 | Anyone with read access to a repository can view releases. Use separate private/public release surfaces. |

## Sync And Backup Practice Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Syncthing file versioning | https://docs.syncthing.net/users/versioning.html | 2026-07-08 | Established sync tools preserve old versions as an optional per-folder safety feature. |
| Syncthing ignore patterns | https://docs.syncthing.net/users/ignoring.html | 2026-07-08 | Established sync tools support file ignore rules. |
| rclone check | https://rclone.org/commands/rclone_check/ | 2026-07-08 | Backup/sync verification commonly compares source and destination size/hash where possible. |
| restic check | https://restic.readthedocs.io/en/latest/045_working_with_repos.html | 2026-07-08 | Backup tools expose integrity checking as a first-class operation. |
| FolderSync folder-pair settings | https://foldersync.io/docs/help/folderpairsettings/ | 2026-07-08 | Android sync apps expose sync direction, folder-pair settings, and related controls. |

## Android Engineering Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Android architecture recommendations | https://developer.android.com/topic/architecture/recommendations | 2026-07-08 | Use layered architecture, repositories, dependency injection, and optional domain/use-case layer based on complexity. |
| Android data layer | https://developer.android.com/topic/architecture/data-layer | 2026-07-08 | Repository classes should expose data, centralize changes, resolve conflicts, abstract data sources, and serve as data-layer entry points. |
| Android domain layer | https://developer.android.com/topic/architecture/domain-layer | 2026-07-08 | Use cases should be single-action, main-safe, and used when reusable business logic justifies them. |
| Android build variants | https://developer.android.com/build/build-variants | 2026-07-08 | Product flavors/build variants can create internal/public APK behavior but multiply variants and source sets. |
| Android modularization | https://developer.android.com/topic/modularization | 2026-07-08 | Multi-module projects are useful but should be introduced deliberately after understanding architecture needs. |
| Android testing fundamentals | https://developer.android.com/training/testing/fundamentals | 2026-07-08 | Testing should verify correctness, functional behavior, usability, devices/emulators, user errors, and user flows before release. |
| Hilt setup | https://developer.android.com/training/dependency-injection/hilt-android | 2026-07-08 | Hilt is the preferred dependency-injection direction, but it should be added when real injected components and tests exist. |
| Room setup | https://developer.android.com/training/data-storage/room | 2026-07-08 | Room should back the sync ledger/history once schema and migration tests are introduced. |
| DataStore setup | https://developer.android.com/topic/libraries/architecture/datastore | 2026-07-08 | DataStore should back settings through the data layer, not directly from composables. |
| Android architecture samples | https://github.com/android/architecture-samples | 2026-07-08 | Google samples use Compose, single activity, ViewModels, repositories, flavors, Hilt, and broad tests. |
| Now in Android | https://github.com/android/nowinandroid | 2026-07-08 | Google reference app uses official architecture guidance, product flavors, Hilt, test doubles, Compose, and documented variant test commands. |
| Android testing samples | https://github.com/android/testing-samples | 2026-07-08 | Google testing samples show multiple Android testing techniques and UI/instrumentation testing patterns. |

## Open-Source App Research Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Round Sync | https://github.com/newhinton/Round-Sync | 2026-07-08 | Android cloud sync/file tools expose task management, schedules, SAF support, encryption, and APK distribution. |
| Google Drive backup sample | https://github.com/chromeos/android-google-drive-backup-sample | 2026-07-08 | Drive API and WorkManager can be combined, but background work constraints and device behavior must be handled explicitly. |
| RSAF | https://github.com/chenxiaolong/RSAF | 2026-07-08 | Android SAF/document-provider integrations have edge cases around delayed upload, hidden errors, conflicts, and interrupted pending uploads. |
| RCX documentation | https://x0b.github.io/docs/ | 2026-07-08 | Android cloud file tools use SAF for local storage and warn that large file operations need stable network and enough battery. |
| Neo Backup | https://github.com/NeoApplications/neo-backup | 2026-07-08 | Backup apps commonly expose schedules, custom backup sets, encryption, and explicit restore workflows. |

## Community Signal Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Folder sync app demand | https://www.reddit.com/r/androidapps/comments/1rgaeqj/looking_for_app_to_sync_local_folder_to_google/ | 2026-07-08 | Users ask specifically for scheduled local-folder-to-Google-Drive backup behavior. |
| Multi-folder paywall pain | https://www.reddit.com/r/androidapps/comments/xn9hsd/any_free_app_that_let_me_sync_on_google_drive/ | 2026-07-08 | Users want multiple folder mappings without paid gating. |
| Reliability over feature count | https://www.reddit.com/r/androidapps/comments/17vq1db/foldersync_vs_autosync/ | 2026-07-08 | Users value reliable folder-pair persistence and manual control. |
| Background work difficulty | https://www.reddit.com/r/androiddev/comments/1tgl90w/what_android_app_looked_easy_until_you_actually/ | 2026-07-08 | Developers identify file/offline sync as deceptively hard due to architecture, WorkManager, and logic. |
| WorkManager vs service discussion | https://www.reddit.com/r/androiddev/comments/1dafor3/when_should_we_use_workmanager_or_service/ | 2026-07-08 | Community discussion reinforces that background execution choices are nuanced and must be checked against official docs. |
| Backup vs sync distinction | https://www.reddit.com/r/DataHoarder/comments/pylc3z/best_alternative_to_google_backup_and_sync_backup/ | 2026-07-08 | Community backup discussions warn that a single cloud copy is not a complete backup strategy. |

## Source Maintenance Rules

- Add a source row before relying on a new platform behavior.
- Re-check sources when implementation starts, because Android and Google policies change.
- If a source claim is refuted by current docs, update the affected notes and record the correction in [[Drive Backup App Open Questions And Assumptions]].

## Next Notes

- [[Drive Backup App AI Context Guide]]
- [[Drive Backup App Architecture]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Standard Practice Assessment]]
- [[Drive Backup App Android Engineering Research]]
