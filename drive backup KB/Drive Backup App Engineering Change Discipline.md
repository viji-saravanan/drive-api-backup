---
doc_id: drive-backup-app-engineering-change-discipline
status: active
last_updated: 2026-07-08
context_role: implementation-quality-gate
read_when:
  - The agent will edit code, Gradle files, Android manifest, resources, tests, CI, release docs, or implementation-facing KB notes.
  - The agent is reviewing whether an implementation change is complete.
do_not_read_when:
  - The task is only a product discussion with no repo change.
---

# Drive Backup App Engineering Change Discipline

This note encodes the project rule that no implementation change is allowed to be treated as isolated without checking its surrounding contracts.

## Core Rule

Do not read one file, patch one file, and hope the rest of the project still works.

For every implementation change, the agent must prove it checked the change's blast radius before finalizing. The proof can be short, but it must be explicit.

## Before Editing

Identify the touched area and inspect the relevant neighbors:

- direct callers and callees;
- imports and dependency injection bindings;
- repository, use case, worker, ViewModel, and UI state contracts;
- Gradle version catalog, plugins, source sets, product flavors, and build variants;
- Android manifest permissions, services, receivers, foreground-service declarations, and backup rules;
- Room entities, migrations, DAOs, and persisted settings;
- WorkManager scheduling, constraints, retry, cancellation, and progress observers;
- Drive, auth, SAF, email, notification, and network adapter interfaces;
- tests that currently cover the behavior;
- KB notes, README, release workflow, or source register entries affected by the change.

Only inspect the bullets that apply to the change, but do not skip a category just because it is inconvenient.

## During Editing

Make the smallest coherent change that preserves existing contracts.

If a touched file implies another required update, do that update in the same branch. Examples:

- adding a setting also updates validation, persistence, UI state, tests, and docs;
- changing sync result semantics also updates ledger, progress UI, email summaries, history, failure matrix, and tests;
- adding a permission also updates manifest, onboarding/preflight, denial handling, tests, and source-backed docs;
- changing a Drive API behavior also updates fake Drive tests, retry classification, source register, and manual test plan;
- adding a product flavor also updates canonical Gradle tasks, release workflow, and variant-specific tests.

## Before Marking Done

Run or document the narrowest meaningful verification:

- compile/build check for code or Gradle edits;
- unit tests for pure logic;
- fake integration tests for sync, Drive, auth, folder, email, and ledger behavior;
- WorkManager tests for background, retry, progress, and cancellation behavior;
- instrumented or manual device tests for Android permissions, SAF, account, notification, and lifecycle behavior;
- markdown/link checks for KB edits.

If a check cannot run, state why and name the remaining risk. Do not silently replace a blocked test with optimism.

## Final Response Requirement

For every implementation turn, final output must include:

- files or areas changed;
- blast radius checked;
- tests/checks run;
- negative or edge cases covered;
- any blocked verification or residual risk.

## Next Notes

- [[Drive Backup App AI Context Guide]]
- [[Drive Backup App Testing Plan]]
- [[Drive Backup App Failure Matrix]]
- [[Drive Backup App Source Register]]
