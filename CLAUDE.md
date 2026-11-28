# Contacts

## Repo overrides

<!-- agent-kit:rules begin. Written by /repo-rules from agent-kit/conventions/agent-rules.md; edit it there, not here. Put repo-specific exceptions under "## Repo overrides" outside this block. -->
## Agent rules

These rules apply to every agent working in this repo. A rule under
**Repo overrides** elsewhere in this file wins over this block.

### Code

Follow @CODE-PRINCIPLES.md. Project-specific rules (stack, layout, gates)
live in this file, outside this block.

### Commits

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
`<type>(<scope>): <subject>`.

- **type**: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`,
  `chore`, `revert`.
- **scope**: the module or feature touched, lower-case. Leave it out when the
  change has no single home.
- **subject**: imperative, lower-case, no trailing period, 72 characters at
  most.
- **body**: why, not what. Rationale, design decisions and gotchas go here,
  where they travel with the code.
- **breaking**: `!` after the scope and a `BREAKING CHANGE: <what>` footer.
- One logical change per commit, with its gate green.

### Branches

- Never commit to `main` directly. Start each piece of work on a new
  branch cut from an up-to-date `main`, named `<type>/<slug>`
  (`feat/offline-sync`, `fix/empty-queue`).
- To land, rebase first, re-run the full gate on the rebased branch, then
  merge with a merge commit:

  ```sh
  git rebase main
  # full gate, green
  git switch main && git merge --no-ff <branch> && git branch -d <branch>
  ```

- No squash merges, no fast-forward merges. Never push or force-push unless
  asked.

### Scratch

Logs, drafts, commit-message files and throwaway scripts go in
`$(agent-store --scratch)`: a directory per worktree, outside the repo.
Never in the repo (no `.scratch/`) and never in `/tmp`. It is swept daily,
so anything worth keeping belongs in a commit, a plan or this file.

### Tasks

Tasks live in the `agent-store` store, outside the repo, so they can't be
committed by mistake. Never put a plan, task list or handoff in the repo.

- A track of work is one **meta task** `<track>-meta`, plus one **task** per
  unit of work, `<track>-<NN>-<slug>` (`sync-03-conflict-ui`).
- `agent-store <name>` prints a plan's path, `agent-store --list --name-prefix
  <track>-` lists a track, and `agent-store <name> --append` adds stdin to the
  end of a plan.
- Read the meta task first. It is an index: open a task only when its
  "Open it for:" line says it holds what you need.
- **Starting a task:** set its Status to `in progress` and point the meta's
  **Active task** at it.
- **Finishing a task:** tick its acceptance criteria, add a Log line with
  the commit shas, set Status to `done (<date>)`. In the meta, update its
  Tasks entry with a one-line result and "Open it for:", copy leftovers into
  **Follow-ups** tagged `(<NN>)`, and move **Active task** on.
- A decision that crosses tasks goes in the meta's **Cross-task decisions**.
  A decision local to one task stays in that task.
- Durable facts (rationale, gotchas, API shape) go in commit bodies or this
  file, not only in a plan. When the track is finished, archive every task
  and then the meta: `agent-store --archive <name>`.

Meta task (`<track>-meta`):

```markdown
# Meta: <track title>

**Context:** <why this track exists, in a paragraph>

Index only. Each task lives in its own plan (`agent-store <name>`).
Only decisions that cross tasks are kept here.

**Active task:** <NN-slug, or none and why>

## References
- <design plan, fixture, external repo, doc> - <path or plan name>

## Cross-task decisions
- <decision> - <why> (<who decided, date> if the user did)

## Tasks
1. `<track>-01-<slug>` - **todo | in progress | done (<date>) | deferred (<why>)**.
   <one-line result once done>
   Open it for: <what only that plan holds>

## Not planned yet
<work the track will need later; add a task when it starts>

## Follow-ups
Open items left by finished tasks. Each names its source task.
- (<NN>) <item>
```

Task (`<track>-<NN>-<slug>`):

```markdown
# Task <NN>: <title>
Meta: `<track>-meta` · Status: **todo | in progress | done (<date>)** · Commits: <shas>

## Why
<what made this task necessary: the finding, bug or decision behind it>

## Goal
<the outcome, not steps>

## Decided
- <decision local to this task> - <why>

## Open (decide first)
- <question to settle before coding; move it to Decided once answered>

## Acceptance criteria
- [ ] <a concrete check: a test, a command and its output, an observed behaviour>

## Non-goals
<what this task leaves to which other task>

## Log
- <date>: <sha> <subject>; <what was learned>
```
<!-- agent-kit:rules end -->

## Tests

- Unit tests are plain JVM: no Robolectric, no mock libraries, no new
  test dependencies. `unitTests.isReturnDefaultValues = false`, so a test
  that reaches an android.jar method throws "Method ... not mocked" instead
  of passing on 0/null/false. Android compile-time constants
  (`ContactsContract.CommonDataKinds.*.TYPE_*`) are fine.
- Rules live in pure functions over plain values (Kotlin types, app and
  commons models). Border code (cursors, provider, files, clock, caller
  identity) only reads into plain row data classes and calls the rule;
  time and identity enter as parameters.
- Where a shell's ordering must be tested, it goes through one interface
  at a real border with a hand-written fake (`MergeStore` +
  `FakeMergeStore`, tested via `DedupeWriter`). No interface that only a
  test wants.
- No build runs on the Pi: the gate is `agent-ci` on a work branch
  (fork-ci.yml: unit tests, detekt, lint, assembleFossDebug).

## Screens

- A screen's decisions go through a loop in `screens/<screen>/`:
  `<Screen>State` (data class), `<Screen>Action` (sealed: user events and
  load results), `<Screen>Effect` (sealed: loads, writes, UI) and a pure
  `update(state, action): Next<State, Effect>` in `<Screen>Update.kt`
  (`screens/Next.kt`). No `android.*` imports there. Config values, ids,
  time and permission results enter as Action payloads.
- The Activity is the shell: it maps view events to Actions, renders
  state and runs effects. `dispatch` runs on the main thread only. One
  single-worker executor per screen runs each dispatch's effects in list
  order, posting UI effects to the main thread from it, so a `Finish`
  after a write runs after the write. Its idle thread times out and it is
  never shut down, so a chain still in flight (delete, then delete the
  linked raws) completes after `onDestroy`. Results come back as
  `runOnUiThread { dispatch(...) }`.
- A screen whose effects are all `Ui` runs them inline in `dispatch`, in
  order, with no executor: an async hop would let a lifecycle callback see
  state the effect was about to change. The executor comes with the
  screen's first `Load` or `Write` effect.
- Actions and effects are grouped in nested sealed interfaces
  (`Load`/`Delete`/…, `Ui`/`Load`/`Write`) so each `when` stays under
  detekt's CyclomaticComplexMethod limit.
- Screens on the loop: `screens/viewcontact/` + `ViewContactActivity`
  (`ViewContactUpdateTest`); `screens/main/` + `MainActivity`, start page,
  import intent and search session so far (`MainUpdateTest`);
  `screens/editcontact/` + `EditContactActivity`, opening (launch intent ->
  permission, target contact, load, prefill decision;
  `EditContactOpenTest`) and leaving (back and toolbar up: the unsaved-changes
  prompt and its throttle; `EditContactUpdateTest`). The list rules the shared
  fragments apply are pure helpers
  (`helpers/TabContents.kt`, `helpers/ContactSearch.kt`). The selection
  bar, delete targets and link mode of the shared `ContactsAdapter` are
  pure helpers too (`helpers/ContactSelection.kt`, `ContactSelectionTest`).
- The edit form is read into a plain `FormSnapshot` (`helpers/EditForm.kt`) on save and back; the blank guard, building the Contact and label text -> type id (`helpers/EditFormTypes.kt`) are pure (`EditFormTest`, `EditFormTypesTest`). Intent prefill is pure too (`helpers/EditPrefill.kt`, `EditPrefillTest`). Save is not on the loop yet.
