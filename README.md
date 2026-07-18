# Viji Backup

Viji Backup is a personal Android app for selected-folder backup to a shared Google Drive folder.

The project is intentionally built in phases. Phase 1 established the Android
foundation. Phase 2 implements a fail-closed Google Credential Manager gate and
private build variants. Phase 3 implements read-only selected-folder mapping and
scanning. The active Phase 4 branch adds durable session reuse, exact Downloads
access, account-bound Google Drive authorization, and live writable-destination
health. File upload, scheduling, email delivery, restore, and release signing
are not implemented yet.

## Project Root

```text
<repository-root>
```

## Knowledge Base

Start with:

```text
drive backup KB/Drive Backup App Index.md
```

For implementation work, also read:

```text
drive backup KB/Drive Backup App Engineering Change Discipline.md
```

For a new laptop or repeatable device testing, follow:

```text
drive backup KB/Drive Backup App Fresh Laptop Setup And Test Runbook.md
```

## Public Repository

This source and review repository is public. Treat every tracked file, branch,
commit, pull request, review, and CI log as public before pushing it. Real
account addresses, Drive folder IDs, OAuth client IDs, device identifiers,
tokens, signing material, and machine-specific paths belong only in ignored
local configuration or an approved private release system.

Internal APKs and configured test APKs must never be uploaded here. A future
public APK may be published only after the documented public-release privacy,
signing, and test gates pass.

The project owner may keep an ignored `.env` as a temporary local credential
vault for downloaded OAuth bundles. The Android build does not load that file.
Never print it, source it into public CI, attach it to an issue or pull request,
or treat it as a recoverable backup. Build configuration remains in the ignored
`private.properties` contract described below.

## App Identity

- Display name: `Viji Backup`
- Base application ID: `com.aryasubramani.vijibackup`
- Internal application ID: `com.aryasubramani.vijibackup.internal`
- Public application ID: `com.aryasubramani.vijibackup`

## Local Commands

```bash
./gradlew :app:assembleInternalDebug
./gradlew :app:testInternalDebugUnitTest
./gradlew :app:assemblePublicDebug
./gradlew :app:testPublicDebugUnitTest
./gradlew :app:lintInternalDebug
./gradlew :app:lintPublicDebug
```

Instrumented tests require an emulator or device:

```bash
./gradlew :app:connectedInternalDebugAndroidTest
./gradlew :app:connectedPublicDebugAndroidTest
```

## Private Build Configuration

Tracked source contains no personal account addresses, Drive folder IDs, or OAuth client IDs. For local builds, copy `private.properties.example` to the ignored `private.properties` file and provide the approved values there. The source-verification workflow intentionally injects no private values, stores no configured APK artifact, and proves the fail-closed build state. Future private release jobs may use protected `VIJI_BACKUP_*` environment values but must not expose or cache configured artifacts.

Do not commit `private.properties`. A clean checkout intentionally builds with an empty allowlist and empty cloud identifiers, causing cloud access to fail closed.

Live Google auth from a fresh source-build laptop normally needs Android OAuth clients registered for that laptop's debug SHA-1 and both package IDs. The setup runbook documents the exact flow. Never provide an OAuth client secret to the Android app.
