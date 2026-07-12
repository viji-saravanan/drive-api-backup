---
doc_id: drive-backup-app-fresh-laptop-setup-test-runbook
status: active
last_updated: 2026-07-12
context_role: setup-and-repeatable-testing
read_when:
  - A contributor or reviewer is starting on a different laptop.
  - A human or AI needs to reproduce the build and test environment.
  - Live Google sign-in must work from a newly generated debug signing key.
  - The project needs a repeatable frequent-testing loop.
do_not_read_when:
  - The task is product strategy with no local build or test impact.
---

# Drive Backup App Fresh Laptop Setup And Test Runbook

This runbook takes a contributor from a laptop with no project setup to a
repeatable Viji Backup build and test environment. It never assumes a username,
home directory, checkout directory, Android SDK path, or access to Arya's local
Git account switcher.

The repository root is always the directory containing `AGENTS.md`, `gradlew`,
and `settings.gradle.kts`. Commands below are run from that directory unless a
step says otherwise.

## 1. Choose The Required Setup Level

Use the smallest level that can prove the work.

| Level | Purpose | Private configuration | Google Cloud change | Physical phone |
|---|---|---|---|---|
| A. Source build | Compile, run unit tests, inspect code | Not required | Not required | Not required |
| B. Android integration | Run synthetic Credential Manager, DataStore, and Compose tests | Recommended | Not required | Physical phone or Play-enabled emulator |
| C. Live Google auth | Exercise the real Google account chooser and consent | Required | Usually required for the laptop's debug SHA-1 | Required or Play-enabled emulator with test account |
| D. Drive integration | Future Phase 3 Drive authorization and upload tests | Required | Drive API/authorization setup required | Required |

A clean checkout without `private.properties` must still compile. It is expected
to fail cloud configuration closed and show `Setup required`; that is not a
build failure.

## 2. Obtain Access Without Sharing Credentials

The contributor needs:

- read access to the private GitHub repository;
- permission to create a separate branch and PR;
- a Google account approved by the project owner for live auth testing;
- access to a modern Android device with current Google Play services for Level C;
- project-owner help for Google Auth Platform changes if the contributor cannot
  access the Cloud project.

Never provide or request:

- a Google password;
- a Gmail app password;
- an OAuth client secret;
- a downloaded `client_secret_*.json` file;
- a service-account JSON key;
- an access or refresh token;
- a GitHub personal access token in chat or source;
- a release signing keystore or password through the repository.

The OAuth client IDs, account allowlist, and Drive folder ID are identifiers, not
passwords, but this project still treats them as private configuration. Exchange
them through an approved private channel and keep them outside Git.

## 3. Install Git And Authenticate GitHub

1. Install Git from the operating system package manager or
   <https://git-scm.com/downloads>.
2. Install GitHub CLI from <https://cli.github.com/>.
3. Authenticate with the contributor's own GitHub account:

```bash
gh auth login
gh auth status
```

4. Configure a GitHub `noreply` commit identity for that account:

```bash
git config --global user.name "<github-user-name>"
git config --global user.email "<github-noreply-address>"
```

Do not copy Arya's account-switcher binary or assume it exists. On another
laptop, verify `gh auth status`, `git config user.name`, and
`git config user.email` before each commit and push. Use only an identity the
contributor is authorized to use.

## 4. Install Android Studio And Java

1. Install a current supported Android Studio from
   <https://developer.android.com/studio/install>.
2. Keep Android Studio on its bundled JetBrains Runtime.
3. In Android Studio, open:
   `Settings > Build, Execution, Deployment > Build Tools > Gradle`.
4. Select a Java 17 Gradle JDK. The bundled JBR 17 is acceptable.
5. For terminal builds, point `JAVA_HOME` to the same Java 17 installation.
6. Verify:

```bash
java -version
```

The project baseline is:

- Java source and target compatibility: 17;
- Android Gradle Plugin: 9.2.1;
- Gradle wrapper: 9.4.1;
- Kotlin/Compose compiler plugin: 2.3.21;
- Compose BOM: 2026.06.01.

Always use the checked-in Gradle wrapper. Do not install or invoke a separate
system Gradle.

## 5. Install The Android SDK

In Android Studio, open `Settings > Languages & Frameworks > Android SDK` and
install:

- Android SDK Platform 36.1;
- Android SDK Build-Tools 36.1.0;
- current Android SDK Platform-Tools;
- current Android SDK Command-line Tools;
- Android Emulator and a Google Play system image only if an emulator is needed.

The app currently has `minSdk 24`, `targetSdk 36`, and `compileSdk 36.1`.

The same packages can be installed from a terminal after Command-line Tools is
available:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" \
  "platforms;android-36.1" \
  "build-tools;36.1.0"

"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
```

On Windows, use `sdkmanager.bat` and `gradlew.bat`.

### Optional External SSD Layout

On a low-storage laptop, keep large Android artifacts on an external SSD. Use
stable paths that remain mounted while Android Studio or Gradle is running.

Suggested layout:

```text
<external-drive>/Android/sdk
<external-drive>/Android/user-home
<external-drive>/Android/avd
<external-drive>/Gradle
```

Set environment variables in the user's shell profile:

```bash
export ANDROID_HOME="<external-drive>/Android/sdk"
export ANDROID_USER_HOME="<external-drive>/Android/user-home"
export ANDROID_AVD_HOME="<external-drive>/Android/avd"
export GRADLE_USER_HOME="<external-drive>/Gradle"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

`ANDROID_HOME` is the current SDK variable. Do not introduce a conflicting
`ANDROID_SDK_ROOT` value.

In Android Studio, set the SDK location to the same `<external-drive>/Android/sdk`
path. In the repository's ignored `local.properties`, set:

```properties
sdk.dir=<absolute-path-to-Android-sdk>
```

Do not copy another contributor's `local.properties`; it is machine-specific.

## 6. Clone The Correct Branch

Clone the repository into any local directory:

```bash
gh repo clone viji-saravanan/drive-api-backup viji-backup
cd viji-backup
git fetch --all --prune
```

For Phase 2 test-only review, inspect the remote branch without committing:

```bash
git switch --detach origin/feature/phase-2-auth-allowlist
```

For changes, create a new branch from the Phase 2 branch:

```bash
git switch -c contributor/<github-user>/phase-2-review \
  origin/feature/phase-2-auth-allowlist
```

Rules:

- never push directly to `main`, `dev`, `setup/phase-1-foundation`, or
  `feature/phase-2-auth-allowlist`;
- a review/fix PR created before Phase 2 is integrated must target
  `feature/phase-2-auth-allowlist`;
- do not merge the stacked Phase 2 branch before its Phase 1 base is ready;
- keep commits sequential and scoped to one understandable step;
- do not rewrite or revert unrelated changes from another contributor.

Confirm the checkout before doing anything else:

```bash
git branch --show-current
git status --short --branch
git remote -v
```

## 7. Read The Minimum Project Context

A new human or AI must read these files in order:

1. `AGENTS.md`
2. `drive backup KB/Drive Backup App Index.md`
3. this runbook
4. `drive backup KB/Drive Backup App Project State.md`
5. `drive backup KB/Drive Backup App Engineering Change Discipline.md`
6. the feature packet named by the index
7. `drive backup KB/Drive Backup App Testing Plan.md`

Do not read the entire KB by default. Follow the packet map in the index so the
context window remains useful.

## 8. Prove A Clean Source Build First

Before adding private configuration, run:

```bash
./gradlew --version
./gradlew :app:assembleInternalDebug
./gradlew :app:testInternalDebugUnitTest
./gradlew :app:assemblePublicDebug
./gradlew :app:testPublicDebugUnitTest
```

Expected result:

- Gradle uses Java 17 or a compatible configured JDK;
- both APK flavors assemble;
- both unit suites pass;
- live sign-in is unavailable because private configuration is absent;
- no source file needs to be edited to make the clean build compile.

If this baseline fails, stop and fix the environment before changing app code.

### GitHub Source Verification

`.github/workflows/android-source-verification.yml` runs the two flavor unit,
assemble, Android-test APK, and lint tasks on feature/review pushes and pull
requests. It uses JDK 17, installs the pinned Android SDK packages, and pins each
third-party action to a reviewed commit SHA.

The workflow intentionally receives no OAuth IDs, account allowlist, or Drive
folder ID. It must remain a fail-closed source check and must not upload APKs or
cache outputs that contain private configuration. Use the manual workflow button
for a clean remote rerun; use a physical device for live Google acceptance.

## 9. Create Local Private Configuration

Copy the tracked template:

```bash
cp private.properties.example private.properties
chmod 600 private.properties
```

PowerShell equivalent:

```powershell
Copy-Item private.properties.example private.properties
```

Fill these keys through an approved private channel:

| Key | Required for | Value source |
|---|---|---|
| `vijiBackup.driveUploadFolderId` | Future Drive tests | Shared upload folder ID |
| `vijiBackup.allowedGoogleAccounts` | Live auth | Comma-separated approved test accounts |
| `vijiBackup.internalAndroidOAuthClientId` | Internal flavor identity | Android OAuth client for internal package and this signing SHA-1 |
| `vijiBackup.publicAndroidOAuthClientId` | Public flavor identity | Android OAuth client for public package and this signing SHA-1 |
| `vijiBackup.googleWebClientId` | Credential Manager ID token request | Web application OAuth client ID |

Never put a Web client secret in this file. The Android app does not need it.

Confirm both private files are ignored:

```bash
git check-ignore -v private.properties local.properties
git status --short
```

Do not continue if either file appears as untracked instead of ignored.

## 10. Register This Laptop's Debug Signing SHA-1

This step is mandatory for live Google auth from source builds on another
laptop unless the project deliberately supplies a shared test signing key.
Every laptop normally generates a different `~/.android/debug.keystore`, so the
Android OAuth clients configured for another machine will not match.

1. Generate the signing report:

```bash
./gradlew :app:signingReport
```

2. Record the debug SHA-1 locally. Do not add it to tracked docs.
3. In Google Auth Platform, create or ask the project owner to create two
   laptop-specific Android OAuth clients:

| Flavor | Package name | Certificate |
|---|---|---|
| Internal | `com.aryasubramani.vijibackup.internal` | This laptop's debug SHA-1 |
| Public | `com.aryasubramani.vijibackup` | This laptop's debug SHA-1 |

4. Put the returned Android client IDs in `private.properties`.
5. Reuse the project's Web application client ID for
   `vijiBackup.googleWebClientId`.
6. While the OAuth app is in testing mode, add every live test account under
   Google Auth Platform `Audience > Test users`.
7. Add only intended test accounts to the local allowlist.

The contributor may send the SHA-1 and package names to the project owner. The
contributor must not send or receive an OAuth client secret.

A prebuilt APK signed on the original build machine does not require a new
client for the tester's laptop because the APK signature does not change. A new
source build usually does.

## 11. Connect A Physical Android Device

Use a physical device for final auth and storage behavior. An emulator remains
useful for synthetic UI coverage.

1. On the phone, open `Settings > About phone > Software information`.
2. Tap `Build number` seven times and authenticate if asked.
3. Open `Developer options` and enable `USB debugging`.
4. Connect a data-capable USB cable.
5. Unlock the phone and accept the RSA debugging prompt for this laptop.
6. Verify from the SDK Platform-Tools directory:

```bash
"$ANDROID_HOME/platform-tools/adb" devices -l
```

The device state must be `device`, not `unauthorized` or `offline`.

Windows may require the phone manufacturer's USB driver. Linux may require
`plugdev` membership and udev rules. macOS normally requires no driver.

For live Google auth, confirm:

- Google Play services is installed and current;
- the intended test account is present on the device;
- the account is an OAuth test user while the app is in testing mode;
- the account is in the app's private allowlist;
- the screen can be unlocked during account selection.

Do not record the device serial in test evidence.

## 12. Repeatable Test Commands

### Fast Edit Loop

Run after each narrow logic change:

```bash
./gradlew :app:testInternalDebugUnitTest
./gradlew :app:assembleInternalDebug
```

If the touched code is flavor-independent, run the public unit suite before the
change is considered complete:

```bash
./gradlew :app:testPublicDebugUnitTest
./gradlew :app:assemblePublicDebug
```

### Android Test Compilation

Run before a device is available and after instrumentation-test edits:

```bash
./gradlew :app:assembleInternalDebugAndroidTest
./gradlew :app:assemblePublicDebugAndroidTest
```

### Full Connected Regression

With one authorized device or Play-enabled emulator connected:

```bash
./gradlew :app:connectedInternalDebugAndroidTest
./gradlew :app:connectedPublicDebugAndroidTest
```

These suites use synthetic credentials and test Compose hosts. They do not
select real Google accounts.

To run one instrumentation class:

```bash
./gradlew :app:connectedInternalDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.aryasubramani.vijibackup.auth.google.CredentialManagerGoogleSignInClientInstrumentedTest
```

### Pre-Push Regression

```bash
./gradlew \
  :app:testInternalDebugUnitTest \
  :app:testPublicDebugUnitTest \
  :app:assembleInternalDebug \
  :app:assemblePublicDebug \
  :app:assembleInternalDebugAndroidTest \
  :app:assemblePublicDebugAndroidTest \
  :app:lintInternalDebug \
  :app:lintPublicDebug
```

Then run both connected suites when Android behavior changed.

## 13. Manual Live Auth Matrix

Use the internal flavor first:

```bash
./gradlew --no-configuration-cache :app:installInternalDebug
```

Clearing app data is optional and destructive only to this debug app's local
state. Never run `pm clear` for another package:

```bash
"$ANDROID_HOME/platform-tools/adb" shell pm clear --user 0 \
  com.aryasubramani.vijibackup.internal
```

Run these cases manually:

| ID | Case | Expected result |
|---|---|---|
| AUTH-LIVE-01 | Fresh launch | Signed-out gate; protected content absent |
| AUTH-LIVE-02 | Approved account | Consent/chooser completes; access confirmed |
| AUTH-LIVE-03 | Back out of chooser | Returns to signed out; no cached approval |
| AUTH-LIVE-04 | Force-stop and restart after approval | Cached metadata triggers reauthentication; metadata alone never unlocks |
| AUTH-LIVE-05 | Sign out | Local session clears and chooser state is reset |
| AUTH-LIVE-06 | Valid Google account omitted from local allowlist | Account is blocked; protected content absent; restart is signed out |
| AUTH-LIVE-07 | Missing Web client or empty allowlist | Setup-required state; chooser never opens |
| AUTH-LIVE-08 | Internal and public installs | Both package IDs coexist and launch |
| AUTH-LIVE-09 | Background an approved session and return | Protected content relocks until current credential succeeds |
| AUTH-LIVE-10 | Remove the approved account while app is backgrounded | Return signs out or blocks; cached metadata never unlocks |
| AUTH-LIVE-11 | Disable network or make Play services unavailable | Stable recoverable error or signed-out state; no crash or unlock |
| AUTH-LIVE-12 | Rotate while Google provider UI is open | No duplicate chooser, stale callback unlock, or permanent `SigningIn` |

Use evidence labels `A1`, `A2`, and so on for approved accounts and `B1` for a
deliberately excluded account. Never write real account addresses in the KB,
commits, PRs, screenshots, logs, or test names.

For AUTH-LIVE-06, use a temporary environment override only in that shell:

```bash
export VIJI_BACKUP_ALLOWED_GOOGLE_ACCOUNTS="<approved-test-accounts-excluding-B1>"
./gradlew --no-configuration-cache :app:installInternalDebug
```

After the blocked test, restore the normal build immediately:

```bash
unset VIJI_BACKUP_ALLOWED_GOOGLE_ACCOUNTS
./gradlew --no-configuration-cache :app:installInternalDebug
```

Do not copy the temporary value into source, shell transcripts, or a PR.

For the public flavor, register the public package against this laptop's SHA-1,
then run at least one representative approved, cancellation, and blocked test.
Do not publish the debug APK. Build outputs contain private configuration.

## 14. Safe Test Evidence

Record:

- branch and commit under test;
- device model and Android/API version;
- Google Play services version when auth behavior is relevant;
- build flavor;
- test task and pass/fail count;
- redacted scenario IDs such as `A1 approved` and `B1 blocked`;
- any non-fatal tooling warning separately from test failures.

Never record:

- account addresses;
- OAuth client IDs or secrets;
- ID/access/refresh tokens;
- Drive folder IDs;
- ADB serials;
- an unredacted Google chooser screenshot or UI hierarchy dump;
- absolute contributor home paths.

Before collecting app logs, clear old logs. Scan the app process only and do not
attach raw logs until they have been reviewed for private data:

```bash
"$ANDROID_HOME/platform-tools/adb" logcat -c
"$ANDROID_HOME/platform-tools/adb" shell pidof \
  com.aryasubramani.vijibackup.internal
"$ANDROID_HOME/platform-tools/adb" logcat -d --pid=<app-pid> > app-log.txt
```

Keep `app-log.txt` outside the repository and delete it after recording redacted
counts or findings.

## 15. Privacy And Repository Checks

Before every commit and push:

```bash
git status --short --branch
git diff --check
git check-ignore -v private.properties local.properties
git grep -n -I -E 'GOCSPX-|client_secret|refresh_token|access_token'
git grep -n -I -E '/Users/|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
```

Review every match. Test identities must use the reserved `example.test`
domain. Expected documentation mentions such as `client_secret` are warnings,
not proof of a leak; inspect context instead of blindly suppressing the scan.

Also verify ignored/generated files are not staged:

```bash
git diff --cached --name-only
git ls-files private.properties local.properties app/build build .gradle .idea
```

The source/review repository remains private. A future public source repository
must be created from sanitized release commits because historical review
excerpts cannot be made reliably private after publication.

## 16. Troubleshooting

| Symptom | Check in this order |
|---|---|
| `Setup required` | Web client ID exists; allowlist is non-empty; normal config was rebuilt after any override |
| Google chooser never opens | Device has current Play services; Web client is correct; account exists on device; app is foreground and unlocked |
| Provider/configuration failure | Internal/public package matches its Android OAuth client; this laptop's debug SHA-1 is registered; Web client belongs to the same project |
| Account absent from chooser | Account is on device; account is an OAuth test user; use explicit button after authorized-account auto attempt |
| Account always blocked | Exact normalized address is present in private allowlist; no temporary environment override remains |
| Restart loops on stale account | Sign out or clear only this app's debug data; rerun DataStore tests; verify `NoCredential` clears cached metadata |
| `adb` shows `unauthorized` | Unlock phone; accept RSA prompt; revoke and re-authorize USB debugging if needed |
| No device detected on Windows | Install OEM USB driver; use a data cable; restart ADB |
| SDK XML version warning | Update Android SDK Command-line Tools so they match Android Studio |
| `appops ... No UID for androidx.test.services` | Treat as tooling noise only if instrumentation starts, all tests run, and Gradle succeeds |
| Install fails for storage | Free space manually; never delete user data through automation |
| Build appears to use old private config | Unset temporary environment/`-P` values; use `--no-configuration-cache`; rebuild and reinstall |

## 17. Handoff Prompt For Another AI

Give the other AI this instruction together with repository access:

```text
Work only inside the clone whose root contains AGENTS.md. Do not assume any
Arya-specific home path, Android SDK path, external drive name, or Git account
switcher. Read AGENTS.md, the KB Index, the Fresh Laptop Setup And Test Runbook,
Project State, Engineering Change Discipline, the relevant feature packet, and
Testing Plan. Do not read the whole KB.

First run git status, git branch --show-current, git remote -v, java -version,
./gradlew --version, the internal/public unit tests, and both debug assembles.
Do not edit until that baseline is understood. Treat private.properties and
local.properties as opaque ignored inputs: never print, quote, commit, upload,
or include their values in logs/PRs. Never use an OAuth client secret.

Base any contribution branch on origin/feature/phase-2-auth-allowlist unless
Project State says Phase 2 has moved. Never push directly to main, dev, the
Phase 1 branch, or the Phase 2 branch. Use your own authorized GitHub noreply
identity and open a PR back to the current phase branch. Keep commits sequential.

For every change, inspect callers, dependencies, resources/manifests,
persistence, tests, docs, and release impact. Add negative tests, run the narrow
suite, then run the blast-radius regression. Report exact commands and redacted
evidence. Do not claim live Google auth works on a fresh laptop until its debug
SHA-1/package OAuth clients and OAuth test users are configured.
```

## 18. Environment Ready Gate

The laptop is ready for frequent Phase 2 work only when all are true:

- repository access and contributor Git identity are verified;
- branch is based on the current Phase 2 remote branch;
- Java 17 and the Gradle wrapper work;
- SDK Platform 36.1, Build-Tools 36.1.0, and Platform-Tools are installed;
- `local.properties` points to this laptop's SDK;
- clean internal/public builds and unit tests pass;
- private configuration is ignored and never printed;
- this laptop's debug SHA-1 is registered for both package IDs before live auth;
- OAuth test users and local allowlist are intentionally configured;
- ADB reports an authorized device for connected/live tests;
- internal/public connected suites pass;
- zero-secret GitHub source verification passes or its absence is explained;
- normal configuration is restored after every temporary blocked-account test;
- `git status` contains only intended source, test, and documentation changes.

## Sources

Use current official entries in [[Drive Backup App Source Register]] for Android
Studio installation, Java/Gradle JDK selection, SDK variables, physical-device
setup, Google client authentication, Credential Manager Sign in with Google,
and Compose v2 test synchronization.

## Next Notes

- [[Drive Backup App Project State]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Phase 2 Auth Implementation Plan]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Engineering Change Discipline]]
- [[Drive Backup App Source Register]]
