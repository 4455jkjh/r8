---
name: r8-cl-review-correctness
description: >-
  Correctness lens for R8/D8 Gerrit CL review: R8 logic slips, nondeterminism,
  concurrency, null/missing cases, error handling, DEX limits/encoding,
  API-level gating, keep-rule and mapping/retrace semantics, tests that cannot
  fail, and R8 build/script bugs. Not for style/API nits or IR/LIR/lens/KeepInfo
  invariants (covered by r8-cl-review-nits and r8-cl-review-invariants).
---

# R8 CL Review: Correctness

Find code that computes the wrong result, crashes, races, produces
nondeterministic output, violates API/keep-rule/retrace semantics, or tests
that pass without exercising the change. Starred rules (`*`) have before/after
examples in [references/examples.md](references/examples.md).

Out of scope: idioms, helpers, naming, and CL hygiene (`r8-cl-review-nits`);
IR/SSA/LIR/CF/DEX, phis, catch handlers, graph lenses, AppView phases, KeepInfo
lifecycle, and D8/R8/R8-partial modes (`r8-cl-review-invariants`).

## 1. Review Procedure

### 1.1 Scope & Re-review

1. Read the commit message and diff to understand the claim (fix, optimization,
   feature, refactor, test-only, tools). For a fix, locate the regression test
   (possibly in an earlier repro CL) and verify it would fail without the fix.
2. Walk the diff hunk by hunk:
   - new collection -> does iteration order reach output (B), and do worker
     threads mutate it (C)?
   - lookup/`definitionFor`/`getStaticValue` -> can it be null, absent, or
     implicit default (A.3, D)?
   - encoding/limits/API emission -> DEX widths (G), min API (H)?
   - tests -> does input reach the new path and can every assertion fail (L)?
3. On re-review, review only the interdiff, code it invalidates, and `/COMMIT_MSG`.
   Never duplicate open threads.

### 1.2 Verification

- **When repository files are readable**: read the full changed file in `src/`
  or `d8_r8/`, plus callers, siblings (CF vs DEX reader, D8 vs R8 entry point,
  `copy()` paths), threading entry points (`ThreadUtils.processItems`,
  `TaskCollection`, Enqueuer/IRConverter waves), and downstream consumers
  (`ApplicationWriter`, `ProguardMapSupplier`) to confirm whether races or
  ordering actually matter. If a check requires a repo-wide scan you cannot run,
  mark `[Needs parent grep: <symbol>]` in internal evidence when running as a
  subagent—or drop the claim if running standalone without a parent orchestrator.
- **When running diff-only (no repository tools)**: flag only concrete bugs
  directly evident from the changed lines; drop claims that require unseen
  callers or threading context.
- For every candidate, name the concrete triggering input/state and observable
  failure (wrong DEX, crash, nondeterministic output, `NoSuchMethodError` on
  API X, silent test pass). If you cannot, drop it.

### 1.3 Severity & Confidence

- **blocking**: miscompilation, crash/`VerifyError` on valid input,
  nondeterministic output, race/deadlock, emitting an API above min API,
  keep-rule semantics dropping needed code, wrong mapping/retrace, or a fix CL
  whose regression test cannot fail.
- **should_fix**: bug on a rarer path (edge input, error path, Windows, R8
  partial), `assert` validating external input, resource/daemon leaks, unsound
  disabled-by-default code, test missing a branch/negative control the CL
  introduces, or a script reporting success on failure.
- **Confidence**: `high` = triggering path verified in code; `medium` = concrete
  bug but reachability is unconfirmed (phrase as a short question). Drop lower.
- Do not emit `nit`-severity findings from this lens.

### 1.4 Do NOT Flag

- Sorting/`Linked*` when the consumer is order-insensitive or sorts later, or
  `IdentityHashMap`/`HashSet` used only for membership.
- Races in provably single-threaded code (serial test caches, Gradle helpers).
  Conversely, never suggest downgrading a concurrent collection without tracing
  all writers.
- `assert` for internal compiler invariants (standard R8 style; flag `assert`
  only on external/user input or when the `assert` expression has side effects).
- `null` intentionally meaning "unknown" unless a caller conflates it with empty.
- Preserving runtime behavior on invalid-but-loadable input instead of throwing
  a fatal compile error.
- Missing tests on pure refactors/NFC CLs, release-branch cherry-picks without
  cleanups, or generated file bodies (`BackportedMethods`, `*CfMethods`—review
  the generator/template and whether it was regenerated).

## 2. Checklist

### A. R8 Logic Slips

- **A.1 Change flags**: combine with `hasChanged |= rewrite(..)` (plain `=`
  drops earlier `true`); `CodeRewriterPass` must report IR changes in
  `CodeRewriterResult`; distinguish `OptionalBool.isTrue()` vs
  `isPossiblyTrue()`; mutators return `true` only when state changed.
- **A.2 Arity & callbacks**: compute call arity from `invoke.getInvokedMethod()`,
  not the enclosing method; ensure `onChangedAction`/`finished(..)` callbacks
  run on every exit path.
- **A.3\* Strict pattern recognizers**: bail when a required constant is unknown
  (`null`), not only when it differs; check booleans are 0/1; use
  `isIdenticalTo` (not `appInfo.isSubtype(...)`) for exact library types; guard
  `DexEncodedField#getStaticValue()` with `hasExplicitStaticValue()` (absent
  values return the type default); stop sentinel scans (e.g. `UNRECOGNIZED` in
  `LargeProtoEnumRewriter`) when the sentinel is missing.
- **A.4 Preserve exceptional & null semantics**: don't fold `0 / x` or `0 % x`
  without `x != 0`; don't replace `a.equals(b)` with `Objects.equals` if NPE on
  null receiver is observable; don't turn reflective `null` args into
  `new Class[0]`; `ServiceLoader` only instantiates public services.

### B. Nondeterminism (Blocking When It Reaches Output)

- **B.1\* Hash order reaching output**: iterating `HashMap`/`HashSet`/
  `IdentityHashMap`/`ConcurrentHashMap` or `appInfo().classes()` to synthesize
  members, assign names, pick merge targets, run an order-dependent fixpoint,
  write rules/profiles/mappings, or pick an error class makes output
  nondeterministic. Sort (`ListUtils.sort`), use `classesWithDeterministicOrder()`,
  or use linked collections (`LinkedHashMap`, `ProgramMethodSet.createLinked()`,
  `Int2ReferenceLinkedOpenHashMap`)—and preserve the linked type through every
  `copy()`/constructor/getter.
- **B.2 IR edits in map order**: if iterating `Value`/instruction-keyed maps
  drives rewrites or allocates SSA values, use `Linked*` or sort by
  `Value.getNumber()` (`DeterminismChecker` flags SSA numbering drift).
- **B.3\* Ties need a total order**: never pick first-wins (`putIfAbsent`,
  `iterator().next()`, first seen concurrently) or sort by a partial key; use
  `Collections.min(.., comparator)` and ensure output comparators break all ties
  (`assert cmp != 0 || a == b`).
- **B.4 Stable hashing**: hashes feeding synthetic naming/sharing must use
  `HasherWrapper.murmur3128Hasher()` over sorted entries, never `Object.hashCode`
  or identity hashes.
- **B.5 Equivalence contracts**: Guava `Equivalence` / hash keys
  (`BasicBlockInstructionsEquivalence`, etc.) must be symmetric and transitive
  with consistent `hash()`, or deduplication depends on block order.
- **B.6 Worklists that re-enqueue**: raw `Deque` worklists without full seen
  guards can re-enqueue items and vary fixpoint/propagation order (use
  `WorkList.newIdentityWorkList()` / `addIfNotSeen`).

### C. Concurrency & Shared State

- **C.1\* Shared mutable state on worker threads**: only flag code reachable
  from `ThreadUtils.processItems`, `TaskCollection`, Enqueuer, or IRConverter
  waves. Catch non-atomic check-then-set (`BooleanBox` -> `AtomicBoolean`),
  concurrent writes to `Sets.newIdentityHashSet()` (`ConcurrentHashMap.newKeySet()`),
  and per-call fields stashed on shared matcher/visitor instances.
- **C.2\* `Timing` is per thread**: on worker threads use
  `timing.createThreadTiming(name, options)` instead of sharing the main-thread
  `Timing`; never store `Timing` in long-lived shared objects.
- **C.3 Submit through the threading module**: use `TaskCollection` with
  `options.getThreadingModule()` and the entry point's executor (no raw
  `executorService.submit` or ad-hoc pools; single-threaded mode must not fork).
  Stream results with `TaskCollection#stream` instead of buffering all output.
- **C.4\* Nested waits deadlock**: an outer `processItems` task awaiting inner
  tasks on the same fixed pool deadlocks when `#threads <= #outer tasks`; chain
  work with `SynchronizedTaskCollection` instead.
- **C.5 Inline-submit semantics**: inline execution catches `Throwable` into
  `Futures.immediateFailedFuture(t)`; run only nested tasks inline and don't
  detect executors by class name.
- **C.6 No mutable static compilation state**: static caches leak across
  Gradle-daemon compilations and race between concurrent builds; use instance fields.
- **C.7 Await & signal in `finally`**: await spawned work (e.g. proguard-map
  writer), call `options.signalFinishedToConsumers()` /
  `signalFinishedToProviders(reporter)` in `finally`, and propagate worker
  exceptions to the main thread.

### D. Null, Empty & Missing Families

- **D.1 Null definitions & array base types**: `definitionFor` of optional or
  missing library types (`kotlin.Metadata`) can be null (`asProgramClassOrNull`);
  superclass walks stop at null `getSuperType()`; multi-dimensional arrays need
  `getBaseType().isPrimitiveType()` (`isPrimitiveArrayType()` is depth 1 only).
- **D.2 Unknown ≠ empty & no null holes**: keep `null` ("unknown") distinct
  from empty (watch `getOrDefault(.., emptySet())`); every analysis path
  (no-`Return` methods, bottom results, partial arrays) must produce an explicit
  state (e.g. `NOT_UNBOXABLE`), not null holes. For annotations, an omitted
  `@Target` means "anywhere", unlike `@Target({})`.
- **D.3\* Cover the whole family**: when generalizing a feature, check for
  leftover special-cases; include legacy Kotlin `Intrinsics` names alongside
  `checkNotNullParameter`, both `support` and `androidx` variants, trailing `/`
  on package prefixes, every `DexValue` kind in `UseCollector.registerDexValue`,
  and both CF and DEX readers for new class properties.
- **D.4 Nullable R8 inputs & unanchored replacements**: handle null
  `MethodParameters` names, `ProgramResource.getClassDescriptors()`,
  `Path#getFileName`, and `visitAnnotation` results; avoid unanchored
  `String.replace` on identifiers/rules; handle swapped/mirrored `If` operands.

### E. Error Handling & Assertions

- **E.1\* External input: report, don't `assert`**: parsers of desugared-library
  JSON, `api-versions.xml`, mapping files, CLI options, and generator `main()`s
  must `throw reporter.fatalError(..)` or explicit exceptions (and check format
  versions at read time), since asserts are off in production.
- **E.2\* `reporter.error` does not abort**: call `reporter.failIfPendingErrors()`
  or use `reporter.fatalError(..)` before continuing into code that assumes
  valid input, and return `null` rather than a malformed value.
- **E.3 Fail loudly & side-effect-free asserts**: replace trailing
  `else { assert isX(); .. }` with explicit checks + `CompilationError` /
  `Unreachable`; never put side effects inside `assert` (e.g.
  `assert set.remove(x)` disappears without `-ea`); assert-only helpers like
  `DexOutputBuffer.assertZero()` must restore buffer positions; don't pass
  `ConsumerUtils.emptyConsumer()` to overflow callbacks that must never
  overflow; don't emit `reporter().warning` for internal heuristics (use
  `assert`, which won't break `-warningsaserrors`).
- **E.4 Diagnostic & parser safety**: guard against recursion in shared
  diagnostics (`ExceptionDiagnostic`), throw position-aware `ParseException` in
  `ProguardMapReader`, and keep error messages bounded.

### F. Object & Transaction Contracts

- **F.1 Lattice equality**: lattice `isEqualTo` must be symmetric, not `<=`;
  identity `equals` requires enforced singletons.
- **F.2 Transactions & derived counts**: new state in `IndexedItemTransaction`
  or `VirtualFile` must update `abort`/`commit`/`isEmpty()` and every
  `getNumberOfX()` counter; finalizing getters must reset pending state.

### G. DEX Limits, Shifts, Encoding & Platform

- **G.1\* DEX limits & widths**: match sibling `<`/`<=` on `MAX_ENTRIES`
  (`U16BIT_MAX + 1`, returned by `VirtualFile.getMaxNumberOfFields`); define
  threshold `0`; `writeShort`/`getShort` > 32767 reads negative; large mapping
  offsets need `long` (`TextPosition.getOffsetAsInt()`).
- **G.2 Shift & bit-width semantics**: fold shifts per JLS 15.19 (mask 5/6 bits
  and handle negative distances) or bail; power-of-two rewrites require `c > 0`
  (`Integer.bitCount(0x80000000) == 1`); narrowing checks use `((long) i) != l`.
- **G.3 Descriptors & platform portability**: validate descriptors before
  slicing (`DescriptorUtils.isValidClassDescriptor`) and handle primitives; use
  `File.pathSeparatorChar`/`os.pathsep`, `'/'` in zip entries,
  `StringUtils.unixLines` in emitted mappings, and `Reader` over hardcoded charsets.

### H. API-Level Gating, Backports & Desugared Library

- **H.1\* Never emit APIs above min API**: backport templates may only call APIs
  available at their lowest target level (e.g., no `Objects.checkFromIndexSize`
  below API 30); inserted platform calls must check `options.getMinApiLevel()`
  and DEX output.
- **H.2 Full `AndroidApiLevel` comparisons & exact cutoffs**: use
  `isLessThan`/`isGreaterThanOrEqualTo`, never `getMajor()` (drops minor levels);
  include full levels in cache keys; reject invalid levels rather than mapping
  to `MAIN`. Verify exact gating levels (`LibraryValidator`, API database,
  inclusive `api_level_below_or_equal`/`api_level_greater_or_equal`) and bump
  desugared-library spec `identifier` on content changes.
- **H.3 Backports & VM workarounds**: backports must match JDK spec on sign,
  overflow, and edge cases; gate VM-bug workarounds through the latest/main
  level until fixed and test both sides of the cutoff.

### I. Keep-Rule, ProGuard Config & Keep-Annotation Semantics

- **I.1\* `-keepclasseswithmembers` is conjunctive**: an unmatchable member rule
  makes the whole rule match nothing (drop it or replace with a never-matching
  rule—never clear the member list). `{ *; }` does not match memberless classes,
  and `-keepclasseswithmembers` never gets an implicit `<init>()`.
- **I.2\* Global options in library consumer rules**: wire every new global
  option into `FilteredKeepRulesBuilder` and
  `ValidateLibraryConsumerRulesKeepRuleProcessor#handleRule` so library rules
  reject it; combine repeated options with `meet`, not last-wins.
- **I.3 Match-all, embedded rules & defaults**: match-all detection must inspect
  every positive pattern (`MatchAllTypes`, `MatchClassTypes`, `MixedClassNameList`);
  keep one embedded-rule ingestion path (`amendWithRulesAndProvidersForInjarsAndMetaInf`);
  `META-INF/com.android.tools/` replaces `META-INF/proguard/`; derived defaults
  must not override explicit settings, and implicit `-keepattributes` widen globally.
- **I.4 Keep annotations & enum annotations**: `LOOKUP` keeps existence while
  `NAME` keeps the name; `CLASS_INSTANTIATE` is not reflection; `disallow={..}`
  implies `allowshrinking`; `@KeepEdge` is unconditional; sentinels must not
  collide with user values. Enums used in annotations must not allow obfuscation
  (`Enum.valueOf`).

### J. Mapping File & Retrace

- **J.1 Names & missing positions**: residual references come from
  `getFinalName()`, originals via residual lookup. Position 0/none needs an
  explicit range covering 0 or the ambiguous set (minimal inline ranges `:0`).
- **J.2 Merging & testing maps**: throw on mixed metadata/map versions
  (preambles must match except `pg_map_id`/`pg_map_hash`). Test position,
  outline, and composition changes with retraced stack traces
  (`inspectStackTrace`), not just `getOriginalSignature()`.

### K. Entry Points, Options, Consumers & Hot-Path Complexity

- **K.1 Options & CLI booleans**: check copy-pasted `getSystemPropertyOrDefault`
  keys match the field; branch on parsed boolean values (`--no-quiet`), not
  non-null presence; disable unsupported modes in nested commands
  (`R8PartialCompilationConfiguration.disabledConfiguration()`).
- **K.2 Dumps**: record new behavior settings as dump properties; keep
  reflectively called builder methods (`CompileDumpCompatR8`) or fall back
  cleanly (throw if the class exists without the method).
- **K.3\* Consumers & leaks**: `StringConsumer.accept` may be chunked (append,
  don't overwrite); decorators forward `finished()` to all delegates once;
  `DesugarGraphConsumer` handles unknown origins; close captured `ZipFile`s /
  streams and null out large buffers on long-lived objects.
- **K.4 Whole-app O(N²) scans**: flag per-invoke hierarchy walks,
  `appInfo().classes()` scans inside loops, `List.contains` in whole-app loops,
  or `code.blocks.get(i)` on the `LinkedList` (blocking on whole-app hot paths).

### L. Tests That Cannot Fail

- **L.1\* Input must reach the path**: verify the feature flag is enabled, the
  triggering value is actually used, `main` reaches the code under test, output
  depends on the behavior, and a regression test would fail without the fix.
- **L.2\* Assertions that can't fail**: assert the count before iterating
  `allMethods()` or asserting inside `if (insn.isX())`; use JUnit asserts and
  `assertThrows` (never Java `assert` or `try/catch` without `fail()` in tests);
  assert output on every `.run(...)` call.
- **L.3\* Pair absence with positive evidence**: pair `not(invokesMethodWithHolder(X))`
  with the expected replacement, `assertUnboxed(A)` with `assertNotUnboxed(B)`,
  `containsString(NEW)` with `not(containsString(OLD))`, and keep tests with a
  control member asserted `isAbsent()`.
- **L.4\* Don't disable what you test**: use
  `addHorizontallyMergedClassesInspector(...::assertNoClassesMerged)` instead of
  `noHorizontalClassMerging()`; tests enabling logic-changing debug flags
  (`enableEnumUnboxingDebugLogs`) must also run without them.
- **L.5 Pin preconditions & specific failures**: use `@NeverInline` /
  transformers so javac/R8 doesn't optimize away the scenario; assert exact
  preconditions (`isJumboString`, not `hasLineNumberTable()`); use
  `assertFailureWithErrorThatThrows(X.class)` and `diagnosticMessage(...)`;
  validate hand-written expected output with `testForJvm`.
- **L.6 Relevant variants & parameter wiring**: flag a missing variant only when
  the CL explicitly branches on it (e.g., an API cutoff tested on one side, or a
  numeric edge case the change handles). Ensure `parameters` are actually used
  (`run(parameters.getRuntime(), ..)`, `setMinApi(parameters)`,
  `enableApiLevelsForCf()` before `getApiLevel()` on CF, `withMaximumApiLevel()`
  for R8 partial, open-ended `isNewerThanOrEqual` version guards, no silent
  `if (!exists) return;`).
- **L.7 Shared test state**: restore `System.setIn`/`setProperty` in `finally`;
  thread-safe `ToolHelper` caches (`TestBase.memoizeFunction`), never sharing a
  mutable compile result; fresh `DexItemFactory` when reading outputs; no
  wall-clock assertions; generator tests compare against checked-in files.

### M. Tools, Scripts & Build

- **M.1 Propagate failures & no silent fallbacks**: `subprocess.run(cmd, check=True)`,
  non-zero exit on error (not bare `return`), no `rmtree(ignore_errors=True)`,
  no fallback branch names or filing benchmark results under `HEAD` instead of
  the tested hash.
- **M.2 Configs, deps & Gradle**: check copy-pasted bot/dep entries (`linux-jdk22`
  with `--runtimes=jdk21`) and regenerate `.cfg` after `.star` edits; keep
  `DependenciesPlugin.kt`, `tools/create_local_maven_with_dependencies.py`, and
  `.sha1` in sync; wire new test modules for `r8lib`; keep Gradle tasks
  configuration-cache safe (no `providers.exec` at config time) with `@Input`
  declarations; no hardcoded local home paths or writes outside temp dirs.

## 3. Writing Clean Review Comments

Write 1–3 short plain-English sentences: state the failing case/consequence and
the concrete fix (or ask a brief question if reachability is unconfirmed).

- **Separate verification from prose.** Keep traced call paths, `src/` file
  paths, cross-file line numbers, and symbol inventories in internal `Evidence:`
  only—never paste `file://` links, repo paths, call chains, CL numbers, or rule
  IDs (`A.3`, `C.1`) into the Gerrit comment. Leave any optional `reference`
  output field empty rather than putting internal rule codes in it.
- **Keep code mentions light.** Backtick only the 1–3 symbols needed to
  understand or fix the bug. Skip praise and restating the diff.

Examples of clean comments:
- "This looks inverted: when removal is not allowed, we should call `disallowPermittedSubclassesRemoval()` on the keep info."
- "This can return `MAX_ENTRIES` (`U16BIT_MAX + 1`). Should this be `<` instead of `<=` to match the checks above?"
- "We still need to check the remaining users, so returning `true` here exits too early."
- "Can this deadlock on a fixed thread pool when `#threads <= #virtual files`?"
- "This runs on a worker thread, so it should use `timing.createThreadTiming(...)` instead of the main-thread `timing`."
- "This disables horizontal merging rather than verifying it didn't happen. Use `.addHorizontallyMergedClassesInspector(inspector -> inspector.assertNoClassesMerged())` instead."

## 4. Reporting Rules

- Anchor every finding on a line added or modified by the CL (or the closest
  added line for issues caused by a deletion).
- Report only `high` or `medium` confidence findings; drop anything you cannot
  substantiate.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only correctness issues; the other lenses cover
  API/style nits and compiler invariants.
