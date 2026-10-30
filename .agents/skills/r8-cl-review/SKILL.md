---
name: r8-cl-review
description: >-
  Orchestrates multi-perspective code reviews on R8/D8 Gerrit changes by
  running three parallel R8 review lens subagents (`r8-cl-review-nits`,
  `r8-cl-review-correctness`, and `r8-cl-review-invariants`) alongside the
  baseline `gerrit_reviewer` swarm, then verifying, deduplicating, and
  publishing the combined findings.
---

# R8 Multi-Lens Review Orchestrator

When reviewing an R8 Gerrit change (`project: r8`), act as the **Parent
Orchestrator**. Do NOT perform the deep code review directly in the root
session, and do NOT load all three individual lens skills into your own context.
Delegate review analysis in parallel to independent subagents (one per R8 lens,
plus `gerrit_reviewer` when available), then verify and synthesize their
findings.

## Step A: Gather Change Context & Fast-Exit Gate

1. Follow the host prompt's **Step 1** instructions to inspect:
   - Change metadata (`<cl_number>`, `<patchset>`, `<gerrit_host>`, subject,
     branch, owner username, and modified file list).
   - Commit message body and relation chain.
   - Line-numbered diff (on re-review when a previously reviewed patchset
     `<base_ps>` already has prior review output from you, also inspect the
     incremental diff `<base_ps>..<patchset>`).
   - Existing review comments and findings.
2. **Fast-exit gate**: If the diff (or incremental re-review diff) contains zero
   reviewable text modifications (e.g., pure rebase with identical diff and
   unchanged `/COMMIT_MSG`, or binary-only changes), skip Step B and Step C and
   proceed directly to the host prompt's no-issues completion step.
3. **Stage Diff & Commit Message for Subagents**: Write the line-numbered diff
   to `/tmp/r8_review_<cl_number>_ps<patchset>.patch` and the raw commit message
   body to `/tmp/r8_review_<cl_number>_ps<patchset>_commit_msg.txt` so every
   subagent can read the exact target patchset without redundant VCS queries.

## Step B: Define Lens Reviewer & Fan Out Subagents in Parallel

1. **Define Read-Only Lens Subagent**: Call `define_subagent` once to register
   `r8_lens_reviewer`:
   - `Name`: `"r8_lens_reviewer"`
   - `Description`: `"Read-only R8/D8 compiler review subagent executing one assigned R8 review lens skill."`
   - `EnableWriteTools`: `false`
   - `EnableSubagentTools`: `false`
   - `EnableMCPTools`: `false`
   - `SystemPrompt`:
     ```text
     You are an independent, strictly read-only R8/D8 compiler review lens subagent.
     Perform one focused review pass using ONLY the single R8 review lens assigned
     in your prompt, and send your verified findings back to the parent orchestrator
     via `send_message`.

     Core Rules:
     1. Read your assigned lens `SKILL.md` in full in bounded slices (<=250 lines
        with `StartLine`/`EndLine`), and consult its sibling `references/examples.md`
        when calibrating starred rules, before/after idioms, or R8 helper APIs.
        Apply its procedure, directory scope rules, severity/confidence thresholds,
        and "Do NOT flag" list faithfully. Stay strictly within your assigned lens.
     2. Read the staged line-numbered diff file and commit message file passed in
        your prompt, plus the modified workspace files and referenced R8 classes,
        superclasses, callers, and helpers via file-reading tools in bounded slices
        (<=250 lines). Explicit Java imports map directly to paths under
        `src/main/java/`, `src/test/java/`, `src/test/testbase/java/`,
        `src/keepanno/java/`, and `src/utils/java/com/android/tools/r8/utils/internal/`.
     3. Verify every helper signature, nullability/invariant claim, and override
        constraint against actual code you read in `src/`. Drop any claim you cannot
        ground in the repository (if an unused-symbol check requires scanning
        unimported callers across `src/`, note that explicitly for parent grep
        verification).
     4. Return verified findings via `send_message` as a concise bulleted list—each
        with its 1-indexed new-file line range (`file:start-end`, or raw
        commit-message body line `commit_msg:line`), severity, brief `src/`
        citation, and comment text—or `"No findings"` if clean.
     ```

2. **Parallel Subagent Fan-Out**: Follow the host prompt's subagent resiliency
   heartbeat instructions, then in a **single turn** launch all review subagents
   concurrently via `invoke_subagent`:
   1. **Baseline Multi-Perspective Swarm** (when `gerrit_reviewer` is listed in
      your available subagents):
      - `TypeName`: `"gerrit_reviewer"`
      - `Role`: `"General Gerrit Reviewer Swarm"`
      - `Prompt`: Instruct it to run in **Subagent Mode** on CL `<cl_number>`
        patchset `<patchset>` on `<gerrit_host>` (`project: r8`), passing the
        author username, modified file list, and staged diff path
        `/tmp/r8_review_<cl_number>_ps<patchset>.patch`, and strictly requiring
        it NOT to post drafts, replies, findings, or publish anything directly
        to Gerrit—returning all verified findings via `send_message`.
   2. **R8 Nits & API Lens**:
      - `TypeName`: `"r8_lens_reviewer"`
      - `Role`: `"R8 Nits and API Reviewer"`
      - `Prompt`: Pass `<cl_number>`, `<patchset>`, branch, subject, modified
        files, staged diff and commit-message paths, any existing open thread
        locations to skip, and instruct it to execute
        `.agents/skills/r8-cl-review-nits/SKILL.md` (with
        `.agents/skills/r8-cl-review-nits/references/examples.md`).
   3. **R8 Correctness Lens**:
      - `TypeName`: `"r8_lens_reviewer"`
      - `Role`: `"R8 Correctness Reviewer"`
      - `Prompt`: Same change context, executing
        `.agents/skills/r8-cl-review-correctness/SKILL.md` (with
        `.agents/skills/r8-cl-review-correctness/references/examples.md`).
   4. **R8 Compiler & IR Invariants Lens**:
      - `TypeName`: `"r8_lens_reviewer"`
      - `Role`: `"R8 IR Invariants Reviewer"`
      - `Prompt`: Same change context, executing
        `.agents/skills/r8-cl-review-invariants/SKILL.md` (with
        `.agents/skills/r8-cl-review-invariants/references/examples.md`).

   *Large diffs (>4,000 changed lines)*: Include file-priority guidance in the 3
   `r8_lens_reviewer` prompts (`src/main` first, then `tools/` and `*.kts`, then
   `src/test`; skip golden/expectation blobs; sample representative files in
   mechanical bulk updates).

3. **Wait & Clean Up**: Stop calling tools to wait reactively until all spawned
   subagents report back (handling heartbeat wakeups per the host prompt), then
   clean up any remaining subagents and timers before finalizing output.

## Step C: Verify, Deduplicate & Calibrate Findings

Synthesize all candidate findings from `gerrit_reviewer` and the 3
`r8_lens_reviewer` passes:

1. **Ground-Truth Verification**:
   - For every `blocking` and `should_fix` candidate (and any nit claiming an
     unused symbol, Error Prone suppression, IR block structure, or existing R8
     helper method), verify the claim against the workspace at `<patchset>`
     using bounded file reads and targeted `grep_search` scoped under `src/` or
     `tools/`.
   - Drop any claim contradicted by surrounding guards, callers, subclass
     overrides, or the "Do NOT flag" calibration rules of the R8 lens skills
     (e.g., valid `==` identity checks on `DexClass`/`ProgramMethod`/SSA
     `Value`, intentional `testForD8(parameters.getBackend()).setMinApi(...)`
     chains needing `D8TestBuilder` methods, or
     `@SuppressWarnings("ReferenceEquality")` protecting instruction identity
     assertions).
2. **Retain Substantive R8 Codebase Nits**:
   - Verified R8 idiom, helper-API (`ListUtils`, `SetUtils`, `OptionalBool`,
     `WorkList`), test-builder (`testForR8(parameters)`, named API-level
     helpers, `CodeInspector` matchers, leftover `.addDontObfuscate()`),
     TODO/Bug-tag, and dead-code findings from `r8-cl-review-nits` are
     substantive R8 engineering standards—retain high-confidence ones (prefixed
     `Nit: `) as non-blocking nits while dropping whitespace or subjective
     naming trivia.
3. **Cross-Pass Deduplication & Volume Cap**:
   - Merge overlapping findings across subagents within ±3 lines in the same
     file into **one** finding keeping the highest verified severity and the
     most specific R8 API/invariant explanation, presenting a single voice
     without citing subagent names, lens labels, or internal verification notes.
   - Keep **all** verified `blocking` defects, at most **6** `should_fix`
     issues, and at most **5** high-confidence `nit` items, prioritizing highest
     impact and precision.
4. **Gerrit `/COMMIT_MSG` +6 Offset & Delivery**:
   - Gerrit's `/COMMIT_MSG` view prepends 6 header lines (`Parent`, `Author`,
     `AuthorDate`, `Commit`, `CommitDate`, blank line) before line 1 of the
     commit message body, so anchor any commit-message finding at body line `N`
     to line `N + 6` on `file: "/COMMIT_MSG"` (line 7 is the subject line).
   - Follow the host prompt's Steps 3–5 instructions to deliver the final
     review.
