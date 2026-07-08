---
doc_id: drive-backup-app-foundation-decisions
status: active
last_updated: 2026-07-08
context_role: foundation-decisions
read_when:
  - The agent needs the confirmed app identity, build variants, UI stack, or Phase 1A decisions.
  - The agent is about to change Gradle, package names, source sets, or app identity.
do_not_read_when:
  - The task is only sync algorithm design.
---

# Drive Backup App Foundation Decisions

## Confirmed Identity

- Display name: `Viji Backup`.
- Base application ID: `com.aryasubramani.vijibackup`.
- Source package root: `com.aryasubramani.vijibackup`.
- Internal APK application ID: `com.aryasubramani.vijibackup.internal`.
- Public APK application ID: `com.aryasubramani.vijibackup`.

## Build Structure

- Start as a single `app` module.
- Use AGP 9 built-in Kotlin support.
- Do not apply the old `org.jetbrains.kotlin.android` plugin while AGP built-in Kotlin is enabled.
- Use one flavor dimension: `distribution`.
- Use two product flavors: `internal` and `public`.
- Keep feature code in package groups inside `app` until boundaries prove stable.

## UI Foundation

- Use Jetpack Compose.
- Use a single `ComponentActivity`.
- Keep the first screen minimal until product flows are implemented.

## Deferred Foundation Libraries

These are still the chosen direction, but they should be added in the phase where they first have real usage and tests:

- Hilt for dependency injection.
- Room for sync ledger and history.
- DataStore for settings.
- WorkManager for durable background sync.

Reason: adding unused infrastructure in Phase 1 increases build and test surface without proving behavior. Each library should enter with its own negative tests and blast-radius check.

## Current Verification Commands

```bash
./gradlew :app:assembleInternalDebug
./gradlew :app:testInternalDebugUnitTest
./gradlew :app:assemblePublicDebug
./gradlew :app:testPublicDebugUnitTest
```

Instrumented checks require an emulator or physical device:

```bash
./gradlew :app:connectedInternalDebugAndroidTest
```

## SDK Constraint

The local Android SDK currently has `android-36.1` installed. `androidx.core:core-ktx:1.19.0` requires compile SDK 37, so Phase 1 pins Core KTX to `1.18.0` until Android API 37 is installed and verified deliberately.

Current lint warnings after scaffold setup are expected platform/tool freshness warnings:

- target SDK 36 while a newer target is available;
- compile SDK 36.1 while compile SDK 37 is available;
- Core KTX 1.18.0 while 1.19.0 requires compile SDK 37;
- Gradle wrapper 9.4.1 while a newer wrapper is available;
- Compose compiler 2.3.21 while a newer stable plugin is available.

Do not suppress these warnings. Resolve them deliberately in a future SDK/tooling update phase.

## Official Sources Checked

- Android Gradle Plugin 9 built-in Kotlin migration.
- Jetpack Compose setup and Compose compiler plugin.
- Android build variants.
- Android testing fundamentals.
- Android app data backup.
- Hilt, Room, DataStore, and WorkManager setup docs for future foundation choices.

## Next Notes

- [[Drive Backup App Project State]]
- [[Drive Backup App Android Engineering Research]]
- [[Drive Backup App Architecture]]
- [[Drive Backup App Testing Plan]]
