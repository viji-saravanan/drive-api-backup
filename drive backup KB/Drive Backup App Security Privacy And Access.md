---
doc_id: drive-backup-app-security-privacy-access
status: active
last_updated: 2026-07-08
context_role: security-privacy
read_when:
  - The agent touches auth, Drive sharing, tokens, email, encryption, or privacy.
do_not_read_when:
  - The task is only UI copy unrelated to security.
---

# Drive Backup App Security Privacy And Access

## Access Model

The app must use the signed-in user's Google account. It must not place Arya's Google refresh token, OAuth token, service account key, or Drive credentials on another user's device.

Arya controls access by:

- sharing the Drive parent folder with approved user accounts;
- maintaining the app allowlist;
- choosing who receives APKs;
- choosing email recipients.

## Shared Folder Model

Arya creates the parent Drive folder and shares it with the user as Editor. The app uploads into this folder after the user signs in and authorizes access.

The app should clearly state:

> Backups are uploaded to a Google Drive folder managed by Arya. Files in normal backup mode may be readable by Arya and by the signed-in user.

## OAuth Scope Preference

Prefer the narrowest Drive scope that supports the final implementation. Start by attempting a `drive.file` and explicit folder selection model. If a broader scope is required, document the reason, risk, and review steps before accepting it.

## Allowlist

MVP can use a bundled allowlist for approved Google account emails.

Future version can support a remotely fetched allowlist only if:

- it is signed;
- it is cached safely;
- failure mode is conservative;
- the app can explain why access changed.

## Token Storage

- Store tokens using Android-recommended secure storage.
- Do not log tokens.
- Do not include tokens in diagnostics export.
- Wipe local auth state on sign-out.

## Email Privacy

Email summaries include failed filenames because the user requested that behavior and recipients are controlled.

Email should not include file contents.

Default email fields:

- app version;
- device name;
- signed-in email;
- destination folder label;
- sync start/end time;
- status;
- counts;
- failed filenames;
- failure reasons;
- next action.

## Encryption Decision

MVP uses normal readable Drive backup.

Reason:

- Arya wants laptop/browser access to Drive contents.
- Normal files are easier to inspect and restore.
- Client-side encrypted files would require app or decrypt tool plus recovery key.

Future per-folder private mode can encrypt selected sensitive folders before upload.

If private mode is added:

- file names may also need protection, not just file contents;
- lost recovery key means lost backup;
- restore tooling must exist before release;
- tests must cover encryption, decryption, wrong key, and migration.

## Threats To Track

- APK tampering.
- Wrong Google account signing in.
- Shared folder permission accidentally widened.
- Leaked email adapter secret.
- Token leakage through logs.
- Sensitive filenames in email.
- Backups owned by unexpected Google account.
- User believes app can access blocked Android folders.
- Public APK accidentally built with internal diagnostics.

## Next Notes

- [[Drive Backup App Source Register]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Failure Matrix]]

