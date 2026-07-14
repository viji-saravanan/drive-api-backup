---
doc_id: drive-backup-app-github-release-workflow
status: active
last_updated: 2026-07-14
context_role: release-process
read_when:
  - The agent works on GitHub setup, branches, commits, APKs, releases, or account switching.
do_not_read_when:
  - The task is only sync algorithm design.
---

# Drive Backup App GitHub And Release Workflow

## Repository Model

The source and review repository is public. Use separate visibility surfaces
for source collaboration and private release artifacts:

- Public source repository for tracked source, development branches, pull
  requests, reviews, and zero-secret source verification.
- Private artifact surface for internal APKs, configured test APKs, signing
  inputs, and any release evidence that contains private identifiers.
- Public releases in the source repository only for an approved public APK that
  has passed the complete release gate.

Repository visibility applies to its releases, workflow history, and logs. Do
not assume one asset in a public repository can remain private. Never upload an
internal or privately configured artifact to the public source repository.

## Branch Rules

- No direct push to `dev`.
- No direct push to `main`.
- Every work item uses a branch.
- Stacked phase branches target the immediately preceding phase branch until
  that base merges; retarget only after the base is integrated.
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

## Pull Request Rules

- Open one PR for each work branch with a concrete title and complete description.
- Confirm the PR base matches the current stacked phase, not `dev` by default.
- After each push, add a redacted PR progress comment listing commits, exact test
  commands/results, manual evidence, unresolved risks, and reviewer attention areas.
- Never paste account addresses, cloud identifiers, device serials, raw logs, or
  private screenshots into a PR.
- Keep the PR draft while required release evidence or its base PR remains incomplete.

### Merging Stacked Pull Requests

When the user authorizes integration, merge a phase stack from its oldest base
to its newest head:

1. Verify every PR is mergeable, its available checks pass, current review
   feedback is resolved or explicitly dispositioned, and the local worktree is
   clean.
2. Mark the oldest PR ready, obtain the required independent approval, and use
   a merge commit. Do not squash or rebase away sequential attribution.
3. After the base merges, retarget only the next direct child to the integrated
   branch. Recheck its diff, checks, approval, and mergeability before merging.
4. Retarget sibling PRs independently after their shared base is integrated;
   never collapse one sibling's scope into another merely to shorten the stack.
5. Do not delete an ancestor branch until every descendant PR has been
   retargeted. After the final merge, update local `main` by fast-forward and
   verify PR states, checks, commit ancestry, contributor metadata, and privacy.

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

## Generated Gradle Wrapper Integrity

Treat `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, and
`gradle-wrapper.properties` as one generated change surface. Never hand-edit one
launcher to answer a review finding.

When the pinned Gradle version changes, or wrapper integrity is questioned:

1. Run the wrapper task with the exact pinned version and distribution type,
   then run it again through the refreshed wrapper.
2. Verify a third identical invocation is up-to-date and produces no new diff.
3. Compare the wrapper JAR SHA-256 with Gradle's official checksum for that
   exact version.
4. Run `sh -n gradlew`, `./gradlew --version`, and the full two-flavor source
   verification gate.
5. Commit all generated wrapper files together in an isolated build commit.

If generated output differs from a review assumption, preserve the generated
output and record the command, Gradle revision, checksum match, and test result.

## APK Channels

Use two build flavors or release channels:

- `internal`: private diagnostics, test toggles, verbose troubleshooting, private release only.
- `public`: user-safe diagnostics, no private toggles, public release candidate.

Both channels must be signed. Neither channel may contain Google tokens, service account keys, SMTP passwords, GitHub tokens, or personal secrets.

Tracked source, tests, and KB notes must also omit personal email addresses,
Drive folder IDs, OAuth client IDs, and contributor-specific filesystem paths.
Supply real local configuration through ignored `private.properties`.

The source-verification workflow is intentionally zero-secret and uploads no
APK. A future private release workflow may read protected environment values,
but it must disable caches/artifacts that could retain configured outputs unless
the storage surface is explicitly approved. Notification recipients remain server-side.

The project owner's ignored `.env` may contain temporary vault copies of
downloaded OAuth credential bundles. It is not a CI input. No workflow may read,
source, encode, cache, upload, or inspect that file. GitHub environment secrets,
if introduced later, must be least-privilege and manually gated; log masking is
not treated as a guarantee against workflow exfiltration.

## Release Checklist

Before publishing an APK:

- Update version name and version code.
- Build signed APK.
- Run release test gate from [[Drive Backup App Testing Plan]].
- Verify app flavor.
- Verify allowlist behavior.
- Verify the public APK does not rely on a recoverable client-side allowlist as
  authoritative data authorization; current Drive grant and destination ACL
  checks must fail closed.
- Confirm `validatePublicReleasePrivacy` passes for the intended public model;
  never bypass the task or distribute a privately configured `publicDebug` APK.
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
