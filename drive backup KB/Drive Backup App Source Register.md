---
doc_id: drive-backup-app-source-register
status: active
last_updated: 2026-07-18
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
| Storage Access Framework folder selection | https://developer.android.com/training/data-storage/shared/documents-files | 2026-07-18 | App can use a folder picker and persist access; Android 11+ blocks storage roots, the Downloads root, and sensitive Android folders. Moving or deleting the selected tree can invalidate access. |
| Android 11 storage restrictions | https://developer.android.com/about/versions/11/privacy/storage | 2026-07-15 | `ACTION_OPEN_DOCUMENT_TREE` cannot grant internal-storage roots, reliable volume roots, or the Downloads root; DocumentsUI shows the Downloads action disabled. |
| All-files access | https://developer.android.com/training/data-storage/manage-all-files | 2026-07-18 | Backup/restore is a documented potential use case for `MANAGE_EXTERNAL_STORAGE`; the app must declare it, send the user to system settings, and check `Environment.isExternalStorageManager()`. The broad grant does not relax SAF restrictions or justify source mutation. |
| Package-specific all-files settings | https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION | 2026-07-18 | API 30+ can open the requesting app's all-files-access settings page; intent resolution can vary, so the app retains a global-settings fallback and refreshes actual permission state on return. |
| All-files grant check | https://developer.android.com/reference/android/os/Environment#isExternalStorageManager() | 2026-07-18 | API 30+ must classify the current OS grant from `Environment.isExternalStorageManager()` immediately before protected access rather than trusting an Activity result. |
| Google Play all-files policy | https://support.google.com/googleplay/android-developer/answer/10467955 | 2026-07-15 | Backup/restore apps can be eligible for all-files access when automatic multi-directory access is core functionality, but Play distribution requires declaration, review, prominent disclosure, and narrow disclosed use. |
| OpenDocumentTree activity contract | https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocumentTree | 2026-07-13 | The Activity Result contract opens `ACTION_OPEN_DOCUMENT_TREE`; additional business state and request correlation must be retained separately. |
| Activity Result process behavior | https://developer.android.com/training/basics/intents/result | 2026-07-13 | Register callbacks consistently and store business state separately so process recreation cannot attach a result to the wrong operation. |
| Local-only intent hint | https://developer.android.com/reference/android/content/Intent#EXTRA_LOCAL_ONLY | 2026-07-13 | `EXTRA_LOCAL_ONLY` requests local data but is a provider hint, not proof of physical storage location. |
| Persisted URI permissions | https://developer.android.com/reference/android/content/ContentResolver | 2026-07-13 | Persisted grants can be enumerated, taken, and released by exact URI and read/write mode. A grant does not prove the tree still resolves. |
| DocumentsContract traversal | https://developer.android.com/reference/android/provider/DocumentsContract | 2026-07-13 | Build tree-backed document and child URIs through the contract; document IDs remain provider identifiers, not filesystem paths. |
| DocumentsProvider behavior | https://developer.android.com/reference/android/provider/DocumentsProvider | 2026-07-13 | Providers can return loading/error extras, null metadata, repeated documents under multiple parents, authentication failures, and cancellation-sensitive queries. |
| AOSP DocumentsProvider query bridge | https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsProvider.java | 2026-07-15 | The framework query bridge catches a provider `FileNotFoundException` and returns a null cursor, so clients cannot safely classify that ambiguous path as a confirmed missing tree. |
| Authentication-required provider state | https://developer.android.com/reference/android/app/AuthenticationRequiredException | 2026-07-13 | Provider authentication is a specialized `SecurityException` and must be classified before generic permission denial. |
| DocumentFile traversal overhead | https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile | 2026-07-13 | Direct `DocumentsContract` queries avoid the substantial overhead documented for `DocumentFile` traversal. |
| Provider query cancellation | https://developer.android.com/reference/android/os/CancellationSignal | 2026-07-13 | Each blocking provider query can receive a cancellation signal connected to coroutine cancellation. |
| Recent-apps screenshot control | https://developer.android.com/reference/android/app/Activity#setRecentsScreenshotEnabled(boolean) | 2026-07-13 | API 33+ can disable activity screenshots used by the Overview/Recents representation. |
| Secure activity window | https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE | 2026-07-13 | `FLAG_SECURE` prevents screenshots and non-secure display output for protected content on older supported APIs. |
| WorkManager periodic work | https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work | 2026-07-08 | Periodic work is suitable for deferrable background work but timing is not exact and has a minimum interval. |
| Data transfer background work | https://developer.android.com/develop/background-work/background-tasks/data-transfer-options | 2026-07-08 | Long-running/user-visible transfers need careful background execution choice and user-visible behavior. |
| WorkManager progress observation | https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe | 2026-07-08 | Work progress can be observed and surfaced to UI. |
| Android Keystore | https://developer.android.com/privacy-and-security/keystore | 2026-07-08 | Token/secret protection should rely on Android-recommended secure storage patterns. |
| Foreground service types | https://developer.android.com/about/versions/14/changes/fgs-types-required | 2026-07-08 | Long-running visible transfer behavior must account for foreground service type requirements if foreground services are used. |
| Notification runtime permission | https://developer.android.com/develop/ui/compose/notifications/notification-permission | 2026-07-08 | Android notification permission affects user-visible progress and should be tested. |
| Android app data backup | https://developer.android.com/identity/data/backup | 2026-07-08 | App's own settings backup must be considered carefully and must not include secrets. |
| Android Studio installation | https://developer.android.com/studio/install | 2026-07-11 | A current supported Android Studio can build against a physical device without installing an emulator on a resource-constrained laptop. |
| Java versions in Android builds | https://developer.android.com/build/jdks | 2026-07-11 | Keep the Android Studio Gradle JDK and terminal `JAVA_HOME` aligned; this project uses Java 17 compatibility. |
| Android SDK environment variables | https://developer.android.com/tools/variables | 2026-07-11 | `ANDROID_HOME` identifies the SDK; SDK user state and AVD locations can be moved deliberately without copying another contributor's absolute paths. |
| Physical Android device setup | https://developer.android.com/studio/run/device | 2026-07-11 | Enable USB debugging, accept the laptop RSA key, verify `adb devices`, and use real-device testing before release. |
| AGP 9.2 compatibility | https://developer.android.com/build/releases/agp-9-2-0-release-notes | 2026-07-12 | AGP 9.2 supports through API 37 and documents Gradle 9.4.1 and JDK 17 as its baseline; Phase 2 remains on this tested tool pairing. |
| Android 17 SDK and behavior changes | https://developer.android.com/about/versions/17/setup-sdk | 2026-07-12 | API 37 adoption changes the compile/target baseline and requires an explicit upgrade and compatibility test pass instead of an incidental auth-phase bump. |
| AGP 9 built-in Kotlin | https://developer.android.com/build/migrate-to-built-in-kotlin | 2026-07-08 | AGP 9 enables built-in Kotlin support, so the app should not apply the old Kotlin Android plugin while built-in Kotlin is enabled. |
| Jetpack Compose setup | https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler | 2026-07-08 | Compose requires the Compose compiler Gradle plugin with Kotlin 2.x and Compose dependencies should use the Compose BOM. |
| Compose v2 test migration | https://developer.android.com/develop/ui/compose/testing/migrate-v2 | 2026-07-11 | Compose v2 test rules use `StandardTestDispatcher`; explicitly synchronize queued composition work and use an explicit Android host when required. |
| Credential Manager Sign in with Google overview | https://developer.android.com/identity/sign-in/credential-manager-siwg | 2026-07-11 | Use Credential Manager for Android authentication; Google Drive data access is a separate authorization action. |
| Credential Manager Sign in with Google implementation | https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation | 2026-07-11 | Implement an authorized-account auto-select path plus a persistent explicit chooser; both use a Web application OAuth client ID. |
| Credential Manager sign-out | https://developer.android.com/reference/androidx/credentials/CredentialManager#clearCredentialState(androidx.credentials.ClearCredentialStateRequest) | 2026-07-10 | Explicit app sign-out should clear local app state and notify credential providers with `clearCredentialState`. |
| Google ID token credential | https://developers.google.com/identity/android-credential-manager/android/reference/com/google/android/libraries/identity/googleid/GoogleIdTokenCredential | 2026-07-11 | Google ID library 1.2 exposes token-parsed email and stable Google account ID; raw ID tokens must not be persisted or logged. |
| Google Identity Android release notes | https://developers.google.com/identity/android-credential-manager/releases | 2026-07-11 | Google ID library 1.2.0 distinguishes email from stable unique ID; tests must require both identity claims. |
| Google Play services client authentication | https://developers.google.com/android/guides/client-auth | 2026-07-11 | Source builds on a different laptop normally have a different debug certificate SHA-1 and need matching Android OAuth client registration. |
| Google ID token validation | https://developers.google.com/identity/gsi/web/guides/verify-google-id-token | 2026-07-10 | A relying-party server must validate token signature, audience, issuer, and expiry before treating an ID token as a server-side security identity. |
| Google Android authorization client | https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient | 2026-07-18 | Request Drive separately from authentication. Previously granted scopes can return tokens directly; otherwise the result carries a `PendingIntent`. Bind the request to the approved account and parse the returned intent through the client. |
| Android authorization guide | https://developer.android.com/identity/authorization | 2026-07-18 | Authorization is separate from authentication; access tokens are short-lived, must be used securely, and can be reacquired after prior consent without storing refresh tokens on-device. |
| Google authorization request account binding | https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.Builder | 2026-07-18 | `setAccount` binds authorization to one on-device account and `setRequestedScopes` declares the exact OAuth scopes. The app does not use select-account prompt mode for Drive. |
| Google authorization result | https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult | 2026-07-18 | A result exposes granted scopes, a short-lived access token, an optional resolution, and account metadata. Validate scope, token, and account; never persist or log the token. |
| Android IntentSender Activity Result | https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.StartIntentSenderForResult | 2026-07-18 | A `SendIntentException` is returned as a synthesized canceled result carrying a specific action and extra; classify it separately from ordinary user cancellation. |
| Google Play services Auth release | https://developers.google.com/android/guides/releases | 2026-07-18 | `com.google.android.gms:play-services-auth:21.6.0` is the current official release used for the authorization boundary. |

## Google Drive Sources

| Topic | Source | Checked | Claim Used In Plan |
|---|---|---:|---|
| Drive OAuth scopes | https://developers.google.com/workspace/drive/api/guides/api-specific-auth | 2026-07-18 | `drive.file` covers files created/opened by the app or shared with it through Picker. The existing fixed manually-created folder therefore uses restricted `drive` scope for the personal build; backup/sync qualifies, but public distribution can require verification. |
| Drive uploads | https://developers.google.com/workspace/drive/api/guides/manage-uploads | 2026-07-08 | Drive supports media uploads and resumable uploads for file content. |
| Drive folders | https://developers.google.com/workspace/drive/api/guides/folder | 2026-07-08 | Folders are Drive files with folder MIME type; files can be created in a folder using `parents`. |
| Drive files.get | https://developers.google.com/workspace/drive/api/reference/rest/v3/files/get | 2026-07-18 | Probe only the configured file ID with an explicit fields mask and `supportsAllDrives=true`; classify HTTP errors without exposing response text. |
| Drive partial responses | https://developers.google.com/workspace/drive/api/guides/fields-parameter | 2026-07-18 | Request a minimal explicit `fields` mask instead of accepting the default or complete resource representation. |
| Drive error handling | https://developers.google.com/workspace/drive/api/guides/handle-errors | 2026-07-18 | Distinguish authorization, file-permission, quota, and retryable 403 reasons; 429 and 5xx remain retryable and provider text stays out of UI/logs. |
| Drive file capabilities | https://developers.google.com/workspace/drive/api/reference/rest/v3/files | 2026-07-18 | Folder metadata exposes `trashed`, MIME type, `canListChildren`, and `canAddChildren`, which distinguish a usable upload destination from view-only or invalid state. |
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
| Repository visibility | https://docs.github.com/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility | 2026-07-13 | Public/private visibility is repository-level; making a repository public exposes its code, activity, Actions history, and logs. |
| Releases | https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases | 2026-07-08 | Anyone with read access to a repository can view releases. Use separate private/public release surfaces. |
| Actions secure use | https://docs.github.com/en/actions/reference/security/secure-use | 2026-07-13 | Secret log redaction is not guaranteed for transformed values; write access and privileged workflows can expose configured secrets, so keep PR CI zero-secret and gate any future environment secrets. |
| Checkout action | https://github.com/actions/checkout | 2026-07-12 | Source CI uses the current v6 release pinned to an exact reviewed commit instead of a mutable branch. |
| Setup Java action | https://github.com/actions/setup-java | 2026-07-12 | Source CI installs Temurin JDK 17 with the current v5 release pinned to an exact reviewed commit. |
| Gradle setup action | https://github.com/gradle/actions/blob/main/docs/setup-gradle.md | 2026-07-12 | The official Gradle action configures wrapper execution and caching; CI pins the current v6 release to an exact reviewed commit. |

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
| Room setup | https://developer.android.com/training/data-storage/room | 2026-07-13 | Room backs folder mappings now and later relational sync state through DAOs, transactions, constraints, and exported schemas. |
| Room 2.8.4 release and Gradle plugin | https://developer.android.com/jetpack/androidx/releases/room | 2026-07-13 | Room 2.8.4 is the current stable release, supports this app's API 24 minimum and AGP 9.2 toolchain, uses KSP for Kotlin, and requires schema-directory configuration with its Gradle plugin. |
| Room database testing | https://developer.android.com/training/data-storage/room/testing-db | 2026-07-13 | DAO behavior should be tested against a real Room database on Android. |
| Room migration testing | https://developer.android.com/training/data-storage/room/migrating-db-versions | 2026-07-13 | Export schemas from version 1 and test every future migration; never use destructive migration for user mappings. |
| DataStore setup | https://developer.android.com/topic/libraries/architecture/datastore | 2026-07-13 | DataStore is for small state and lacks partial updates/referential integrity; complex relational mappings belong in Room. |
| KSP2 setup | https://kotlinlang.org/docs/ksp-kapt-migration.html | 2026-07-13 | KSP 2.3.9 is the current documented plugin and KSP2 is required for the project's Kotlin 2.3/AGP 9 toolchain. |
| UI Automator cross-app testing | https://developer.android.com/training/testing/other-components/ui-automator | 2026-07-13 | External system picker UI requires UI Automator or explicit manual physical-device interaction, not app-only Espresso assertions. |
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
