---
doc_id: drive-backup-app-security-privacy-access
status: active
last_updated: 2026-07-13
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

Arya creates the parent Drive folder and shares only the dedicated upload child folder with approved accounts as Editor. The app uploads into that child folder after the user signs in and authorizes access.

Confirmed destination:

- Parent: `My Drive > Viji > BACKUP`
- Upload child: `My Drive > Viji > BACKUP > Viji Phone Uploads`
- Upload folder ID: supplied through ignored or encrypted private configuration.
- Owner: project-owner Google account; address is not committed.

The upload child grants Editor access to both primary-user accounts and the alternate owner account. The project-owner account owns the folder. Actual addresses remain in Google Drive ACLs and private deployment configuration only.

The app should clearly state:

> Backups are uploaded to a Google Drive folder managed by Arya. Files in normal backup mode may be readable by Arya and by the signed-in user.

## OAuth Scope Preference

Prefer the narrowest Drive scope that supports the final implementation. Start by attempting a `drive.file` and explicit folder selection model. If a broader scope is required, document the reason, risk, and review steps before accepting it.

## Allowlist

MVP currently accepts an allowlist supplied at build time from ignored local
configuration. Source CI deliberately receives no allowlist. A clean checkout
has an empty allowlist and therefore denies every account.

Removing addresses from Git does not hide plaintext addresses embedded in a distributed APK. Before publishing an APK containing the production gate, replace plaintext email matching with opaque Google subject identifiers, a server-side decision, or Drive-ACL authorization. Drive permissions remain the authoritative data boundary because a modified client can bypass any bundled check.

The build currently enforces this boundary by failing `publicRelease` when an
email allowlist or Drive folder ID is configured. `publicDebug` remains useful
for private flavor/OAuth testing but is not a distributable public artifact.

Phase 2 persists the stable Google subject with normalized email metadata but
evaluates the local product gate by email because only addresses are configured.
Address reassignment is therefore a tracked identity-transfer risk. Cached
metadata never unlocks a cold process by itself. After explicit approval, the
same running process retains approval across Home, DocumentsUI, and activity
recreation; a new process starts at `ReauthenticationRequired`. Future Drive
work must still recheck current authorization before every protected operation.

For the current personal-use installation, every configured approved account is
an explicit co-administrator of local folder mappings on that installation.
Switching between approved accounts does not create separate mapping profiles.
This is acceptable only while all approved accounts are mutually trusted. Add
per-profile ownership and migration before admitting unrelated users.

Future version can support a remotely fetched allowlist only if:

- it is signed;
- it is cached safely;
- failure mode is conservative;
- the app can explain why access changed.

## Token Storage

- Do not manually persist raw ID, access, or refresh tokens. If a future adapter
  introduces app-owned secret material, use Android-recommended secure storage
  and document its lifecycle first.
- Do not log tokens.
- Do not include tokens in diagnostics export.
- Wipe local auth state on sign-out.

## Local Folder Access

The current Phase 3 source type uses Android's Storage Access Framework and takes
only the persistent read grant returned by the system folder picker. It never
requests or retains a write grant, broad storage permission, all-files access,
or a grant for a URI that is not referenced by either a completed mapping or the
durable in-flight selection record.

Android 11 and newer intentionally prevent this picker from granting the exact
Downloads root. Supporting that root would require a separate, explicit
all-files-access source type, direct-path or MediaStore traversal, a system
settings consent flow, and a larger security/test matrix. Do not silently add
`MANAGE_EXTERNAL_STORAGE`; this remains a product decision.

`Intent.EXTRA_LOCAL_ONLY` is a provider-filtering request, not proof that the
returned tree is physically stored on the phone. Treat provider authority,
document IDs, tree URIs, and persisted-grant metadata as opaque framework data.
The picker UI and the user's explicit selection are the Phase 3 trust boundary.

Folder mappings and in-flight selections live in the app-private Room database.
That database is excluded from Android backup and device transfer because a
restored URI without the matching framework grant is misleading and unsafe.
Every add, repair, and removal path must compensate across the non-atomic Room
and URI-grant operations, and must never delete or modify source content.
Removal releases and verifies the exact grant before deleting its mapping. If
release or storage deletion fails, the mapping remains visible and retryable.

Local folder labels, tree URIs, document IDs, provider authorities, and scanned
filenames must not be written to application logs, crash messages, analytics,
test names, screenshots, or repository evidence. Phase 3 diagnostics may use
opaque mapping IDs and aggregate counts. The future sync report may include the
minimal failed relative filenames requested by the product, but only in the
user-selected email report and protected in-app history.

Disable recent-task screenshots whenever protected folder content can be shown:
use `Activity.setRecentsScreenshotEnabled(false)` on API 33 and newer and
`FLAG_SECURE` on older supported versions. This protection is separate from
process-scope foreground authentication and does not change cold-process
reauthentication.

## Email Privacy

Email summaries include failed filenames because the user requested that behavior and recipients are controlled.

Email should not include file contents.

MVP notification method is a Google Apps Script `MailApp` relay owned by the project owner. Sender and recipient addresses belong in server-side Script Properties. Do not embed those addresses, SMTP passwords, Gmail app passwords, refresh tokens, or Apps Script relay secrets directly in source code or APK resources.

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
- Downloaded `client_secret_*.json` accidentally committed.
- Token leakage through logs.
- Sensitive filenames in email.
- Backups owned by unexpected Google account.
- User believes app can access blocked Android folders.
- Public APK accidentally built with internal diagnostics.
- A picker provider ignores the local-only hint.
- A stale picker callback acquires an unreferenced URI grant.
- A restored Room mapping has no corresponding framework grant.
- Folder labels, URIs, or filenames leak through logs or task snapshots.

## Next Notes

- [[Drive Backup App Source Register]]
- [[Drive Backup App GitHub And Release Workflow]]
- [[Drive Backup App Failure Matrix]]
