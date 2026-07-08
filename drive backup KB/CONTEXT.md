# Drive Backup App

This context defines the core language for the personal Android folder backup app. Use these terms consistently in requirements, implementation plans, tests, and diagnostics.

## Language

**Arya Shared Backup Folder**:
The Google Drive parent folder owned by Arya and shared with approved users as Editor.
_Avoid_: Shared Drive, team drive, backup bucket

**Approved User**:
A person whose Google account is allowed to use the app.
_Avoid_: Customer, tenant, enterprise user

**Signed-In User**:
The Google account currently authorizing the app on the Android device.
_Avoid_: Drive owner, app owner

**Allowlist**:
The list of Google account emails permitted to pass the app gate after sign-in.
_Avoid_: whitelist, customer list, access table

**Folder Mapping**:
A configured relationship between one Android local folder and one Drive destination folder.
_Avoid_: sync pair, mount, binding

**Drive Destination**:
The Google Drive folder where a folder mapping uploads its backup files.
_Avoid_: remote, cloud, bucket

**Normal Backup**:
A backup mode where files are uploaded to Drive as normal readable files.
_Avoid_: plain sync, unencrypted backup

**Private Backup**:
A future backup mode where files are encrypted before upload and require a recovery key to restore.
_Avoid_: secure mode, encrypted sync

**Manual Sync**:
A user-triggered sync run started from the app.
_Avoid_: force upload, immediate backup

**Periodic Sync**:
An automatically scheduled sync run whose exact execution time is controlled by Android.
_Avoid_: exact schedule, cron

**Partial Success**:
A sync run where at least one eligible file completed and at least one file failed or was deferred.
_Avoid_: failed sync, warning

**Cancelled Sync**:
A sync run stopped by the user or interrupted before completion, with completed work preserved and incomplete work left retryable.
_Avoid_: failed sync, aborted backup

**Sync Ledger**:
The local durable record of file state, Drive file IDs, upload state, retries, and per-file outcomes.
_Avoid_: cache, database, log

**Completeness Report**:
The per-run summary of scanned folders, uploadable files, uploaded files, unchanged files, skipped files, failed files, blocked folders, and verification failures.
_Avoid_: log, email, status text

**Recovery Drill**:
A deliberate check that backed-up files can be found and opened from Drive after setup.
_Avoid_: restore test, QA pass
