---
doc_id: drive-backup-app-phase-4-downloads-access-plan
status: active
last_updated: 2026-07-16
context_role: implementation-plan
read_when:
  - The agent touches exact top-level Downloads access, all-files permission, or direct-path scanning.
  - The user reports that Android disables the Downloads root in the folder picker.
do_not_read_when:
  - The task concerns an ordinary user-selected SAF folder or only Google Drive.
depends_on:
  - drive-backup-app-phase-4-session-persistence-plan
  - drive-backup-app-phase-3-local-folder-access-plan
  - drive-backup-app-security-privacy-access
---

# Phase 4 Downloads Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` or `superpowers:executing-plans` and
> complete each production change with a red-green test cycle.

**Goal:** Let the user configure, health-check, recursively enumerate, cancel,
repair, disable, and remove the exact primary-storage `Downloads` source without
changing any source file.

**Architecture:** Android 11+ uses one dedicated `downloadsaccess` feature and
the package-specific all-files-access settings flow. It is a singleton source
with its own Preferences DataStore, permission gateway, direct read-only
scanner, ViewModel, and UI; it never enters the SAF/Room repository. Android 10
and earlier continue to use the existing system tree picker, where the exact
Downloads root is not platform-blocked.

**Tech stack:** Kotlin, Coroutines, Preferences DataStore, Android
`MANAGE_EXTERNAL_STORAGE`, `Environment`, `Settings`, Compose, JUnit, Android
instrumentation, physical Samsung acceptance.

## Decision And Alternatives

Use a parallel direct-path singleton.

- A unified typed source migration would immediately touch Room schema 1, the
  large compensation repository, every Phase 3 mapping test, and future upload
  contracts. That is unnecessary for proving one fixed system source.
- `MediaStore.Downloads` does not guarantee complete recursive coverage of
  arbitrary and unindexed files, so it cannot satisfy exact whole-Downloads.
- `MANAGE_EXTERNAL_STORAGE` is justified because backup/restore is an Android-
  documented broad-access use case and Android 11+ explicitly blocks the exact
  Downloads root in `ACTION_OPEN_DOCUMENT_TREE`.

The permission technically grants read and write access across shared storage.
The app therefore enforces read-only behavior structurally: production
interfaces expose only health checks, metadata reads, and input streams; no
write, rename, move, create, or delete operation exists.

## Fixed Product Decisions

- Scope is the primary shared-storage `Downloads` directory only. Removable SD
  card and OTG roots remain separate future sources.
- API 30+ uses the dedicated special-access path. API 24-29 uses the existing
  SAF tree selection because the root restriction begins at API 30.
- The source recursively includes hidden files and folders when readable.
- Symbolic links are not followed. Escapes outside the normalized Downloads root
  are rejected.
- One unreadable entry increments a failure count and does not stop siblings.
- The source configuration is installation-scoped and shared by the mutually
  trusted approved co-administrator accounts, like Phase 3 mappings.
- Sign-out hides the source but does not delete its configuration or alter the
  Android permission.
- Disable stops future scans/sync while retaining configuration.
- Remove clears local source configuration and scan state but never removes or
  modifies phone files. If Android broad access remains granted, the app shows
  `Access granted but unused` with a direct system-settings revoke action.
- Revocation never removes the source silently. It becomes `Needs access` and
  offers repair.

## State Contract

Access health is independent from scan state and future backup health.

| State | Meaning | Allowed action |
|---|---|---|
| `NotConfigured` | Downloads is not a backup source and no unused broad grant exists. | Add Downloads |
| `PermissionGrantedButUnused` | Android broad access exists but local source configuration was removed. | Configure or revoke access |
| `NeedsPermission` | Source remains configured but Android access is absent. | Repair access or remove |
| `StorageUnavailable` | Permission exists but primary shared storage/root is unavailable. | Retry after storage returns |
| `Disabled` | Source is configured but excluded from automatic backup. | Enable or remove |
| `Ready` | Configured, enabled, granted, mounted, readable exact root. | Scan, disable, remove |

Scan state is one of `Idle`, `Scanning(progress)`, `Cancelling`, or terminal
`Completed`, `Partial`, `Failed`, `Cancelled`. A generation token prevents late
events from an old scan overwriting disable, revoke, remove, sign-out, or retry.

## Task 1: Permission, Configuration, And Health

**Create:**

- `app/src/main/java/com/aryasubramani/vijibackup/downloadsaccess/domain/DownloadsAccess.kt`
- `app/src/main/java/com/aryasubramani/vijibackup/downloadsaccess/data/DownloadsSourceStore.kt`
- `app/src/main/java/com/aryasubramani/vijibackup/downloadsaccess/data/DataStoreDownloadsSourceStore.kt`
- `app/src/main/java/com/aryasubramani/vijibackup/downloadsaccess/platform/AndroidDownloadsAccessGateway.kt`
- matching unit and Android instrumented tests under the same package paths.

**Modify:** `AndroidManifest.xml`, both backup-rule XML files, `AppContainer.kt`.

- [ ] Write failing tests for every state-table transition, API 29/30 boundary,
  package-specific settings intent, unresolved-intent fallback, storage
  unavailability, permission revocation, persistence failure, and cancellation.
- [ ] Implement the manifest permission, app-private configuration store,
  gateway, root provider, and repository with no file mutation API.
- [ ] Prove DataStore recreation and exclusion from backup/device transfer.
- [ ] Run focused tests and both-flavor unit/build/lint checks.
- [ ] Commit permission/configuration independently.

## Task 2: Settings Result And Protected UI

**Create:**

- `downloadsaccess/presentation/DownloadsAccessViewModel.kt`
- `downloadsaccess/presentation/DownloadsAccessScreen.kt`
- their unit and Compose instrumented tests.

**Modify:** `MainActivity.kt`, `VijiBackupApp.kt`, `strings.xml`, composition tests.

- [ ] Write failing tests for add, deny/back, grant, external revoke, repair,
  disable/enable, remove, unused grant, duplicate settings launch, activity
  recreation, sign-out hiding, and approved-account switching.
- [ ] Emit one-shot settings requests from the ViewModel and use an Activity
  Result launcher plus `onStart` refresh; never trust the settings result code.
- [ ] Render Downloads as a distinct source and keep `Access ready` separate
  from `Never backed up`.
- [ ] Run both-flavor unit and Compose suites on Android user 0.
- [ ] Commit UI/orchestration independently.

## Task 3: Read-Only Recursive Scanner

**Create:**

- `downloadsaccess/domain/DownloadsScanner.kt`
- `downloadsaccess/platform/IterativeDownloadsScanner.kt`
- focused JVM/instrumented scanner and ViewModel race tests.

- [ ] Write failing tests for empty/deep/wide trees, hidden entries, symlinks,
  loops, root escape, unreadable root/child, disappearing/changing entries,
  cancellation at every boundary, overflow-safe counts, and late callbacks.
- [ ] Implement an iterative non-following traversal on an injected IO
  dispatcher. Recheck broad access and root containment immediately before open.
- [ ] Continue siblings after per-entry failures and emit only aggregate,
  non-sensitive progress.
- [ ] Prove before/after source sentinels are identical.
- [ ] Commit scanner/orchestration independently.

## Task 4: Physical Samsung Acceptance

- [ ] Deny/back: no source, no scan, no ready claim.
- [ ] Grant: exact primary Downloads becomes `Ready`; SAF mappings are unchanged.
- [ ] Scan real Downloads: aggregate completion only; no filenames or paths in
  logs/evidence.
- [ ] Cancel a sufficiently large scan and retry successfully.
- [ ] Revoke externally, return, and prove `Needs access` before any scan.
- [ ] Repair, disable/enable, remove, and reconfigure.
- [ ] Force-stop/relaunch and in-place upgrade preserve configuration and classify
  the current OS grant correctly.
- [ ] Compare complete content sentinels before/after every case; mismatch count
  must be zero.
- [ ] Repeat automated coverage for both flavors and record package-specific
  grants separately.

## Exit Gate

- The physical Samsung enumerates the exact primary top-level Downloads tree
  only after explicit special-access consent.
- Denial and revocation never appear protected.
- No app path can mutate Downloads content.
- One unreadable entry and cancellation remain isolated and retryable.
- Existing SAF mapping behavior and grants remain unchanged.
- Only after this gate passes may Phase 4 Drive authorization become the active
  implementation slice.

