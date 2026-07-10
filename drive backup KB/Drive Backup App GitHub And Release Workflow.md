---
doc_id: drive-backup-app-github-release-workflow
status: active
last_updated: 2026-07-10
context_role: release-process
read_when:
  - The agent works on GitHub setup, branches, commits, APKs, releases, or account switching.
do_not_read_when:
  - The task is only sync algorithm design.
---

# Drive Backup App GitHub And Release Workflow

## Repository Model

Use two visibility surfaces:

- Private source repository for development and internal APKs.
- Public release repository or public distribution repository for APKs meant for others.

Do not assume a private repository can expose only one release asset publicly while keeping other release assets private. Release visibility follows repository read access.

## Branch Rules

- No direct push to `dev`.
- No direct push to `main`.
- Every work item uses a branch.
- Branch naming:
  - `docs/<topic>`
  - `feature/<topic>`
  - `fix/<topic>`
  - `release/<version>`

## Commit Rules

Commit sequentially:

1. documentation or plan update;
2. scaffold or configuration;
3. implementation slice;
4. tests;
5. release metadata.

Each commit should be buildable where practical. Do not mix unrelated features.

## Commit Attribution Split

The repository intentionally uses both Arya personal and Viji GitHub identities. Do not accidentally commit as Arya work.

Use the local switcher before every commit:

```bash
git-account-switch arya-personal
git-account-switch viji
```

Recommended split for each phase:

- Arya personal: planning, KB, architecture notes, source-register updates, and review/audit commits.
- Viji: app scaffold, Android implementation slices, UI/resource changes, test commits, and release APK preparation.

When a phase has multiple commits, alternate in logical order so both identities are represented. Before every commit, run:

```bash
git config user.name
git config user.email
gh auth status
```

Record the account used in the final response.

## Account Switching Checklist

Before every commit and push:

- Confirm active GitHub account in the GitHub account switcher app.
- Confirm `git config user.name`.
- Confirm `git config user.email`.
- Confirm remote URL.
- Confirm branch name.
- Confirm no secrets in diff.
- Confirm commit email uses the selected account's GitHub `noreply` address.

Use Arya personal and Viji intentionally according to the commit attribution split above. Never commit or push from Arya work unless the user explicitly changes this rule.

## APK Channels

Use two build flavors or release channels:

- `internal`: private diagnostics, test toggles, verbose troubleshooting, private release only.
- `public`: user-safe diagnostics, no private toggles, public release candidate.

Both channels must be signed. Neither channel may contain Google tokens, service account keys, SMTP passwords, GitHub tokens, or personal secrets.

Tracked source, tests, and KB notes must also omit personal email addresses, Drive folder IDs, OAuth client IDs, and contributor-specific filesystem paths. Supply real cloud configuration through the ignored `private.properties` contract locally and encrypted environment variables in CI. Notification recipients remain server-side.

## Release Checklist

Before publishing an APK:

- Update version name and version code.
- Build signed APK.
- Run release test gate from [[Drive Backup App Testing Plan]].
- Verify app flavor.
- Verify allowlist behavior.
- Verify Drive folder setup.
- Verify completion email.
- Create release notes with known limitations.
- Upload internal APK to private release surface.
- Upload public APK only to public release surface when approved.

## Rollback

Keep the last known good APK available. If a release breaks sync or auth, publish a rollback note and link the previous APK.

## Next Notes

- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Source Register]]
- [[Drive Backup App Implementation Roadmap]]
