# Viji Backup Project Instructions

The repository root is the directory containing this file. Never assume another contributor has the same username, home directory, or checkout path.

`KB` means knowledge base. The project knowledge base lives in `drive backup KB/`.

## Context Retrieval

Before app-related work, read:

1. `drive backup KB/Drive Backup App Index.md`
2. `drive backup KB/Drive Backup App Context Packets.md`
3. The packet-specific notes listed for the task.

Do not read the whole knowledge base by default. Use the packet map.

## Current Rule

Do not full-send the app in one pass. Work phase by phase, and keep every phase buildable, testable, and documented.

## Engineering Posture

- Have backbone. Challenge weak requirements, risky shortcuts, and vague terminology before implementation.
- Ask targeted questions when a decision materially affects safety, security, recoverability, testing, or long-term maintenance.
- Do not silently fill dangerous gaps with assumptions. Record assumptions in the knowledge base when work continues without an answer.
- Question your own implementation before finalizing: what can fail, what can be abused, what happens after reboot, what happens on bad network, and what happens when Android or Google denies access.
- Verify current official docs during implementation for Android, Google Drive, Google auth, email, GitHub release, build, and signing behavior. Do not rely on memory for platform behavior.
- Prefer boring, maintainable code over clever code. Backup software must be predictable under stress.

## Required Before Implementation

- Identify the roadmap phase being worked on.
- Load the relevant context packet.
- Load `drive backup KB/Drive Backup App Engineering Change Discipline.md` for any code, Gradle, manifest, permission, release, or test edit.
- State the acceptance gate for the phase.
- Identify the blast radius of the intended change: callers, dependencies, generated outputs, config, manifests, permissions, storage/schema, background work, UI state, tests, docs, and release behavior where relevant.
- Add or update tests in the same change, including negative and edge cases.
- State which official docs were checked when the change touches platform behavior.
- Do not push directly to `dev` or `main`.
- Do not add secrets, OAuth tokens, Drive credentials, SMTP credentials, or GitHub tokens to the repo.
- Do not commit personal email addresses, Drive folder IDs, OAuth client IDs, or machine-specific absolute paths. Use `example.test` identities in tests and the ignored private configuration contract for real values.
- Treat `.env`, `private.properties`, downloaded OAuth JSON, and any token file as
  opaque local inputs. Never print, paste, upload, attach, or source them into
  public CI. The project owner's ignored `.env` is a temporary credential vault,
  not Android build configuration and not a disaster-recovery backup.
- Use the Git account switcher before every commit and push. This project intentionally splits commit attribution between Arya personal and Viji; do not leave commits under Arya work.

## Change-Impact Discipline

Never patch one file and assume the rest of the app still works. Before editing, inspect the adjacent files and contracts that depend on the touched area. After editing, run the narrowest meaningful verification plus any broader checks required by the blast radius. If verification is blocked, record the blocker and the unverified risk instead of treating the change as complete.

## Testing Standard

No sunny-day-only testing. Each feature must cover:

- success path;
- invalid input;
- denied permission;
- revoked access;
- interrupted work;
- retryable failure;
- permanent failure;
- stale/corrupt local state where relevant;
- user cancellation where relevant;
- persistence across app restart where relevant.

If a case cannot be automated in the current phase, document it as a manual/device test in `drive backup KB/Drive Backup App Testing Plan.md`.

## Canonical Terms

Use `drive backup KB/CONTEXT.md` for project language.

## Documentation

If behavior changes, update the relevant note in `drive backup KB/` in the same branch.
