---
name: r8-cl-review
description: >-
  Orchestrates multi-perspective code reviews on R8/D8 Gerrit changes by
  running three parallel R8 review lens subagents (`r8-cl-review-nits`,
  `r8-cl-review-correctness`, and `r8-cl-review-invariants`) alongside the
  baseline `gerrit_reviewer` swarm, then verifying, deduplicating, and
  publishing clean, readable findings.
---

# R8 Multi-Lens Review Orchestrator

When reviewing an R8 Gerrit change (`project: r8`), act as the **Parent
Orchestrator**. Do NOT perform the deep code review directly in the root
session, and do NOT load all three lens checklists into your own context.
Delegate review analysis in parallel to independent subagents (one per R8 lens,
plus `gerrit_reviewer` when available), then verify, deduplicate, and rewrite
the surviving findings into clean, easy-to-read Gerrit comments.

## Step A: Gather Context, Fast-Exit & Stage Files

1. **Gather context** per the host prompt's Step 1 (change metadata, commit
   message, modified file list, line-numbered diff, existing comments/findings,
   and—on re-review when you already reviewed `<base_ps>`—the incremental diff
   `<base_ps>..<patchset>`).
2. **Fast-exit gate**: If the diff (or incremental re-review diff) has zero
   reviewable text modifications (e.g., pure rebase with identical diff and
   unchanged `/COMMIT_MSG`, or binary-only changes), skip Steps B and C and
   finish via the host prompt's no-issues path.
3. **Stage line-numbered diffs for subagents**: Create a per-run directory via
   `mktemp -d /tmp/r8_review_<cl_number>_ps<patchset>_XXXXXX` and write:
   - Line-numbered full patchset diff: `<dir>/diff.patch`
   - Raw commit message body: `<dir>/commit_msg.txt`
   - On re-review, also stage a line-numbered incremental diff
     `<dir>/interdiff.patch` built from `<base_ps>..<patchset>` intersected with
     `<patchset>`'s own diff (so pure rebase hunks from upstream `main` are
     excluded), and instruct subagents to review only `<dir>/interdiff.patch`
     plus `<dir>/commit_msg.txt`.

## Step B: Define Lens Reviewer & Fan Out in Parallel

1. **Define Read-Only Lens Subagent**: Call `define_subagent` once to register
   `r8_lens_reviewer`:
   - `Name`: `"r8_lens_reviewer"`
   - `Description`: `"Read-only R8/D8 compiler review subagent executing one assigned R8 review lens skill."`
   - `EnableWriteTools`: `false`
   - `EnableSubagentTools`: `false`
   - `EnableMCPTools`: `false`
   - `SystemPrompt`:
     ```text
     You are a read-only R8/D8 compiler review lens subagent. Run one focused
     pass using ONLY the single lens skill assigned in your prompt, and send
     verified findings to the parent via `send_message`.

     Core Rules:
     1. Read your assigned lens `SKILL.md` in full (up to 800 lines per read).
        Consult its sibling `references/examples.md` on demand (using its top
        Quick Index and bounded slices) when checking a starred rule or R8 API.
        Follow its scope, severity/confidence rules, and "Do NOT flag" list.
     2. Read the staged diff and commit-message files from your prompt, plus
        modified workspace files and referenced R8 classes, superclasses, and
        helpers via file-reading tools in bounded slices (<=250 lines). Explicit
        Java imports map directly under `src/main/java/`, `src/utils/java/`,
        `src/keepanno/java/`, `src/{assistant,keepradius,libanalyzer,library_desugar,tools}/java/`,
        `src/test/java/`, `src/test/testbase/java/`, `src/test/java8/<area>/`,
        and `src/test/java{11,17,21,25}/`.
     3. Verify every helper signature, nullability/invariant claim, and override
        constraint against code you actually read. You do NOT have shell or
        repo-wide grep tools: if an unused-symbol or caller check needs a
        whole-repo scan, mark it `[Needs parent grep: <symbol>]` in `Evidence:`
        instead of guessing. Drop ungrounded claims.
     4. Return findings via `send_message` as a bulleted list (or `"No findings"`):
        - Location: 1-indexed new-file line range (`file:start-end`, or raw
          commit-message body line `commit_msg:line`)
        - Severity (`blocking`, `should_fix`, `nit`) and confidence (`high`, `medium`)
        - `Evidence:` brief `src/` path/symbol proof for parent verification ONLY
          (never copied into the Gerrit comment)
        - `Comment:` 1-3 short plain-English sentences ready for Gerrit—lead
          with what breaks or can be simplified, then the concrete fix. Never
          put `file://` links, `src/` paths, cross-file line numbers, call
          chains, rule IDs, or CL numbers in `Comment:`.
     ```

2. **Parallel Fan-Out**: Follow the host prompt's subagent heartbeat setup, then
   in a **single turn** launch all review subagents concurrently via
   `invoke_subagent`:
   - **Baseline General Reviewer** (when `gerrit_reviewer` is available):
     - `TypeName`: `"gerrit_reviewer"`, `Role`: `"General Gerrit Reviewer Swarm"`
     - Prompt: Run in **Subagent Mode** on CL `<cl_number>` patchset
       `<patchset>` on `<gerrit_host>` (`project: r8`). Pass the author,
       modified file list, staged diff (and interdiff if re-review), staged
       commit-message path, and existing open thread locations to skip. Focus on
       general logic, design, and cross-file issues; **skip running
       `crcs_reviewer`** (the 3 dedicated R8 lens subagents cover R8 CRCS
       skills directly). Respect R8 norms (`assert` is standard for internal
       invariants; `==` is valid on `DexClass`, `DexEncodedMethod`/`Field`, and
       SSA `Value`). Do NOT post drafts, comments, or findings to Gerrit; return
       verified findings via `send_message`.
   - **Three R8 Lens Subagents** (`TypeName`: `"r8_lens_reviewer"`), each given
     `<cl_number>`, `<patchset>`, branch, subject, modified files, staged diff
     (plus interdiff if re-review), staged commit-message path, and open thread
     locations to skip:

     | Role | Skill | Examples |
     | --- | --- | --- |
     | `"R8 Nits and API Reviewer"` | `.agents/skills/r8-cl-review-nits/SKILL.md` | `.agents/skills/r8-cl-review-nits/references/examples.md` |
     | `"R8 Correctness Reviewer"` | `.agents/skills/r8-cl-review-correctness/SKILL.md` | `.agents/skills/r8-cl-review-correctness/references/examples.md` |
     | `"R8 IR Invariants Reviewer"` | `.agents/skills/r8-cl-review-invariants/SKILL.md` | `.agents/skills/r8-cl-review-invariants/references/examples.md` |

   *Large diffs (>4,000 changed lines)*: Tell all subagents to prioritize
   `src/main` first, then `tools/` and `*.kts`, then `src/test`; skip golden
   blobs and sample representative files in mechanical bulk updates.

3. **Wait & Clean Up**: Stop calling tools to wait reactively until all
   subagents finish (handling heartbeat wakeups per the host prompt), then clean
   up any remaining subagents and timers.

## Step C: Verify, Deduplicate, Rewrite & Deliver

1. **Ground-Truth Verification**:
   - Verify every `blocking` and `should_fix` candidate—plus any finding marked
     `[Needs parent grep: ...]` or claiming an unused symbol, Error Prone
     suppression, IR block structure, or existing R8 helper—against the
     workspace using bounded `read_file` and targeted `grep_search` under `src/`
     or `tools/`.
   - Drop any claim contradicted by surrounding guards, callers, overrides, or
     R8 calibration norms (e.g., `==` on `DexClass`/`DexEncodedMethod`/SSA
     `Value`, `assert` for internal invariants, single-threaded code flagged for
     races, `testForD8(parameters.getBackend()).setMinApi(...)` when
     `D8TestBuilder` methods are chained, or
     `@SuppressWarnings("ReferenceEquality")` guarding instruction identity
     checks).

2. **Retain Substantive R8 Findings from `r8-cl-review-nits`**:
   - Preserve verified `blocking` and `should_fix` severities from
     `r8-cl-review-nits` (e.g., narrowed `@Parameters`, `@KeepForApi` leaks,
     dead code introduced by the CL, bugless `TODO`s) without downgrading them
     to nits.
   - Keep high-confidence `nit`-severity R8 idiom, helper-API, and test-builder
     suggestions prefixed with `Nit: `; drop cosmetic formatting or subjective
     naming trivia.

3. **Deduplicate & Cap Volume**:
   - Merge overlapping findings across subagents within ±3 lines in the same
     file into **one** finding with the highest verified severity.
   - Collapse repeated instances of the same pattern across a file or CL into
     one comment (e.g., `"(and similarly on lines 142 and 188)"`).
   - Keep **all** verified `blocking` defects, at most **4** `should_fix`
     issues, and at most **3** `nit` items (drop pure style nits if the CL
     already has `blocking` issues).

4. **Write Clean, Easy-to-Read Gerrit Comments (Overrides Default Link Style)**:
   Rewrite every surviving finding into a single human teammate voice before
   delivery:
   - **Never emit `file://` links or repo path dumps.** Override any default
     instruction to link files/symbols: do NOT put `[Foo](file:///...)`,
     `src/main/java/...` paths, cross-file line numbers, call-graph traces,
     checklist IDs (`A.1`, `G.9`), CL numbers, subagent names, or internal
     `Evidence:` text in Gerrit comments.
   - **Keep it to 1–3 short sentences.** Lead with *what can break* (or *what
     to simplify*), then state the *concrete fix* in plain English. Skip
     praise, filler, and restating what the diff already shows.
   - **Go easy on code references.** Backtick only the 1–3 symbols or helper
     calls the author actually needs to change or use. Describe surrounding
     behavior in plain words instead of chaining method signatures. Never leave
     a cryptic bare-symbol comment without saying what to replace.
   - **Match tone to certainty.** State verified bugs directly; phrase
     `medium`-confidence edge cases as a brief question ("Can `x` be null here
     when ...?"). Prefix non-blocking style/idiom suggestions with `Nit: `.

   *Calibration (Dense vs. Clean):*
   - **Too dense:** "Per A.1/H.3, `PrimaryR8IRConverter` (`src/main/.../PrimaryR8IRConverter.java:214`) runs this after `AssumeInserter`, so `value.getAliasedValue().getDefinition()` at `FooPass.java:88` (called from `Bar.java:42`) will NPE on a `Phi`."
     **Clean:** "This will NPE if `value` comes from a `Phi`, since phis don't have a defining instruction. Use `isDefinedByInstructionSatisfying(...)` or guard with `isPhi()` first."
   - **Too cryptic:** "Nit: `getTestingOptions()`"
     **Clean:** "Nit: Use `options.getTestingOptions()` instead of accessing `options.testing` directly."
   - **Over-cited:** "C.1: `ThreadUtils.processItems` in `R8.java:612` runs `Collector#visit` (`Collector.java:55`) concurrently, so `Sets.newIdentityHashSet()` races before `ApplicationWriter` (CL 113581)."
     **Clean:** "This runs on worker threads, so writing to `Sets.newIdentityHashSet()` here can race. Use `ConcurrentHashMap.newKeySet()` (or collect per task and merge afterward)."

5. **Gerrit `/COMMIT_MSG` +6 Offset & Delivery**:
   - Gerrit's `/COMMIT_MSG` view prepends 6 header lines (`Parent`, `Author`,
     `AuthorDate`, `Commit`, `CommitDate`, blank line) before body line 1, so
     anchor commit-message body line `N` to line `N + 6` on `file: "/COMMIT_MSG"`
     (line 7 is the subject).
   - Deliver the final review per the host prompt's Steps 3–5.
