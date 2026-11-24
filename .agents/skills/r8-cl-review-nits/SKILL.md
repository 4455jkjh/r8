---
name: r8-cl-review-nits
description: >-
  R8/D8 Gerrit CL review lens for R8 APIs, helpers, test builders, dead code,
  and CL hygiene. Use for the nits and API-usage pass of an R8 review; not for
  correctness bugs or IR/pipeline invariants (covered by r8-cl-review-correctness
  and r8-cl-review-invariants).
---

# R8 CL Review: Nits & API Usage

Flag missed R8 helpers, non-idiomatic test builders/matchers, dead or debugging
code, `@KeepForApi` leaks, and CL hygiene issues. Starred rules (`*`) have
concise before/after idioms in [references/examples.md](references/examples.md).

Out of scope: logic bugs, nondeterminism, concurrency, O(N²) complexity bugs,
and test soundness (`r8-cl-review-correctness`); IR/SSA/LIR/lens/KeepInfo
invariants (`r8-cl-review-invariants`); formatting and `.disassemble()` /
`allowStdoutMessages()` (enforced by `PRESUBMIT.py`); and unused imports, inline
FQNs, or `@Override` in Error-Prone-enabled modules (`main`, `utils`, `tools`,
`keepradius`, `libanalyzer`—still flag inline FQNs and missing `@Override` in
`src/test`, `src/keepanno`, `src/assistant`, and `src/library_desugar`).

## 1. Review Procedure

### 1.1 Scope & Re-review

1. Read the diff, commit message, and any existing open threads provided; never
   duplicate open threads. On re-review, check only the interdiff plus `/COMMIT_MSG`.
2. Route by file:
   - `src/main` -> F–L (plus Q.1 for generated files); `src/test` -> M–P;
     `src/keepanno` -> Q.1 for generated files; `tools/`, `d8_r8/`, `infra/`,
     `*.kts`, `*.kt`, `*.toml` -> Q; A–E apply everywhere.
   - `src/keepanno`, `src/assistant`, `src/keepradius`, and `src/library_desugar`
     do not depend on R8 internal utils (`library_desugar` has no Guava/fastutil
     either); local helpers are fine there.
   - Release-branch cherry-picks -> A.5 only. Repro+fix stacks -> A.6.
3. Review added/modified lines only. Flag an untouched line only if the CL made
   it stale, anchoring on the changed line.

### 1.2 Verification

- **When repository files are readable**: read imported/adjacent classes in
  `src/` to confirm every suggested helper signature, override constraint (an
  override cannot rename or narrow visibility), `@KeepForApi` annotation, and
  test base class. Generic utils live under
  `src/utils/java/com/android/tools/r8/utils/internal/`. If verifying an
  unused-symbol claim (A.3) needs a repo-wide scan you cannot run, mark
  `[Needs parent grep: <symbol>]` in internal evidence when running as a
  subagent—or drop the claim if running standalone without a parent orchestrator.
- **When running diff-only (no repository tools)**: report only issues directly
  evident from the changed lines and commit message; never guess whether a
  helper exists or a symbol is unused.

### 1.3 Severity, Confidence & Volume

- **blocking**: silently dropped test coverage (narrowed/commented
  `@Parameters`, `@Ignore`), accidental public API surface or internal leaks on
  `@KeepForApi` classes, hand-edited generated files, or leftover
  `System.out.println` / `&& false` / `if (true)` in `src/main`.
- **should_fix**: misused R8 API with real drift/cost risk, dead code created
  by the CL, stale/lying comments, `TODO` without a bug, bad `Bug:` footer,
  weak test assertions, or AI tool metadata in `/COMMIT_MSG`.
- **nit**: R8 helper or test-builder idiom improvement; prefix with `Nit: `.
- **Confidence**: `high` = verified mechanical improvement; `medium` = clear
  convention needing author judgment. Drop anything lower.
- **Volume**: report a repeated pattern once (noting other lines if helpful);
  keep to at most ~6–8 highest-signal findings.

### 1.4 Do NOT Flag

- `==` on definitions without `equals` (`DexEncodedMethod`/`Field`, `DexClass`)
  or on SSA `Value`s. `isIdenticalTo` applies only to `DexReference`
  (`DexType`/`DexMethod`/`DexField`), `DexString`, and `DexProto`. Note that
  `ProgramMethod` wrappers are not canonicalized (compare with
  `isStructurallyEqualTo` or `getDefinition() ==`).
- `X x = insn.asX(); if (x == null) continue;` (accepted idiom).
- `testForD8(parameters.getBackend()).setMinApi(parameters)` when chaining
  `D8TestBuilder`-only methods (`testForD8(parameters)` returns `TestCompilerBuilder`).
- `.release()` on D8 (whose default is debug) or `addDefaultRuntimeLibrary`
  after `addLibraryClasses` (which replaces the default library).
- `if (System.currentTimeMillis() > 0)` and `System.out.println` in test *input* classes.
- Untouched legacy code (`public final` IR fields, old names/formatting), cold
  micro-optimizations, `Objects.hash` outside hot `hashCode()`, copyright years
  on moved files, cleanups on cherry-picks, or structural test changes in the
  *fix* CL of a repro+fix stack.

## 2. Checklist

### A. Diff Hygiene & Dead Code

- **A.1 Debug leftovers**: `System.out.println`, `.inspect(i -> System.out.println(..))`,
  `System.currentTimeMillis();` breakpoint anchors, `&& false`, `if (true)`,
  `throw new RuntimeException("XXX")`, scratch files.
  - **`.addDontObfuscate()` in tests**: frequently left over from debugging.
    Keep it only if output or assertions rely on original names (`getName()`,
    stack traces, `toString()`), string-based reflection/JNI/resource lookups,
    a naming/keep-rule test, or raw `getFinalName()` checks. `clazz(A.class)`
    and `uniqueMethodWithOriginalName(...)` use the mapping and don't need it.
    Flag as `nit` (`should_fix` if it hides behavior the test claims to verify).
- **A.2 Commented-out code**: delete commented statements, `// @Ignore`, and
  commented builder/assume calls; track deferred work with `TODO(b/...)`. A
  narrowed `@Parameters` is `blocking`.
- **A.3 Dead code introduced by the CL**: remove unused `InternalOptions` /
  `TestingOptions` flags (and their accessors—no `return false;` stubs),
  overloads, enum values, factory fields, parameters, and orphaned helpers.
- **A.4 Minimal diffs**: drop unrelated churn on untouched lines, untouched
  reformatting, import reorders, and incidental default flips.
- **A.5 Moves & cherry-picks**: keep moves behavior-preserving; release-branch
  cherry-picks carry no unrelated cleanups.
- **A.6 Repro+fix stack**: put all test setup in the repro CL asserting current
  behavior under `// TODO(b/...): Should be ...`; the fix CL only updates expectations.

### B. Comments & TODOs

- **B.1 Stale or copy-pasted comments**: update javadoc, inline comments,
  assert messages, or copy-pasted text that contradicts the new code (e.g.
  wrong class/phase name or resolved TODOs).
- **B.2 TODO format**: use `TODO(b/<id>): <what is wrong>`, never `TODO(user)`,
  `TODO(b/)`, or bare `TODO:`. Disabled checks (`setEnableDex2OatVerification(false)`)
  and known-wrong test expectations need a cause and a bug ID.
- **B.3 Explain non-obvious invariants**: one short comment for remote-guard
  early returns, unsynchronized caches, swallowed exceptions, or `DupX1`/`Swap`
  tricks. Every `canHave...Bug()` predicate needs a short description + `b/` link.
- **B.4 Bytecode test inputs**: prefer `transformer(X.class)` over hand-built
  ASM dumps; if hand-building `CfInstruction` lists, add a brief pseudo-Java comment.

### C. Commit Message (`/COMMIT_MSG`)

- **C.1 Subject & body**: subject ≤72 chars stating what and where (not just
  `"Fix b/12345"`). Mention behavior changes omitted from the title; no squash
  leftovers, duplicate `Change-Id:`, or `[WIP]`.
- **C.2 Bug footer**: use `Bug: b/<id>` (with `b/` so it links) or
  `Fixes: b/<id>` to auto-close; list all relevant bugs.
- **C.3 `RELNOTES:`**: required for user-visible changes (keep-rule semantics,
  flags, defaults, public API/CLI). For keep-rule changes, state affected rules
  and any opt-out.
- **C.4 No tool metadata**: no AI/automation metadata trailers or local
  `PRESUBMIT.py` edits.

### D. Naming & Types

- **D.1 Typos**: especially in public APIs, keep-rule modifiers, and system
  properties (a typo in a property key silently prevents enabling it).
- **D.2 R8 vocabulary**: prefer `clazz`, `method`, `code`, `invoke`, `factory`,
  `isInterface` (not `itf`), `unused` for unused lambda params; match established
  terms (`rewrittenWithLens` takes a lens; `argumentIndex` includes receiver vs
  `parameterIndex` indexes proto; `previous...` vs `original...`).
- **D.3 Enums over boolean flags**: use enums for mode flags, `AndroidApiLevel`
  instead of `int`, `CompilationMode` instead of `boolean debug` in tests, and
  named factories over positional boolean constructors.

### E. Headers, Imports & Suppressions

- **E.1 Headers, attribution & non-Error-Prone imports**: new `.txt` keep-rule,
  `.kt`, and `.proto` files (not covered by `PRESUBMIT.py`'s copyright check)
  need the R8 copyright header; adapted external code needs a source URL and
  license. Outside Error-Prone-enabled modules (`src/test`, `src/keepanno`,
  `src/assistant`, `src/library_desugar`), avoid inline FQNs (unless simple
  names clash) and add `@Override` on overrides.
- **E.2 Stale suppressions**: remove `@SuppressWarnings("ReferenceEquality")`
  once `isIdenticalTo` is used, and drop stale `UnusedVariable`/`HidingField`.
  Never use `@SuppressWarnings("")`.

### F. Structure & Encapsulation

- **F.1 Redundant checks**: flag guards that cannot fail given surrounding types
  (e.g., `isProgramClass()` inside `appInfo().classes()`, `withLiveness()` on
  `AppView<AppInfoWithLiveness>`, null checks on guaranteed non-null definitions).
- **F.2 Encapsulation & signatures**: pass `appView` instead of separate lens +
  factory + options; return values instead of `Box` out-parameters; avoid
  mutating IR inside `is...`/`has...` predicates; question unexplained magic
  bailouts (e.g. `blocks.size() <= 2500`).
- **F.3 No null sentinels**: use `FeatureSplit.BASE`, `Predicates.alwaysTrue()`,
  or `Foo.empty()`/`isEmpty()` instead of overloading `null` to mean base/all/empty.

### G. Dex Items & `DexItemFactory`

- **G.1\* `isIdenticalTo`**: compare `DexType`, `DexString`, `DexMethod`,
  `DexField`, and `DexProto` with `isIdenticalTo`/`isNotIdenticalTo`, not
  `==`/`equals`. When one side may be null, call on the constant
  (`factory.objectType.isNotIdenticalTo(superType)`) or `DexType.identical(a, b)`.
- **G.2\* No `DexString` -> `String`**: use `isIdenticalTo(factory.xName)`,
  `isEqualTo(String)`, `startsWith`, or `factory.jDollarDescriptorPrefix`
  instead of `.toString().equals(...)` or `.toDescriptorString().startsWith(...)`.
- **G.3\* Factory constants & member groups**: reuse `DexItemFactory` fields
  and member groups (`classMethods`, `StringBuildingMethods`, etc.) and
  `method.match(proto, name)` instead of name + arity. Never call
  `factory.createString("...")` inside per-instruction loops.
- **G.4\* Builders & `DescriptorUtils`**: use `createInstanceInitializer`,
  `field.withHolder(holder, factory)`, `createFreshMethodNameWithoutHolder`,
  `X.EMPTY_ARRAY` (not `new X[0]`), and `DescriptorUtils` instead of
  `replace('.', '/')`. Render types in messages with `getTypeName()` or
  `MethodReferenceUtils.toSourceString`, never implicit `toString()`.
- **G.5 No Reference API or reflection in `src/main`**: use `DexItemFactory`
  and `AppView` reference holders instead of `Reference.*` or `Foo.class.getMethod`.

### H. Definitions, `AppView` & Resolution

- **H.1\* Typed accessors & member pools**: use `asProgramClassOrNull(appView.definitionFor(t))`,
  `asProgramMethod(clazz)`, `getHolderType()`, filtered pools
  (`staticFields(DexEncodedField::hasExplicitStaticValue)`,
  `forEachProgramInstanceInitializer`), and indexed `lookupMethod(proto, name)`.
- **H.2\* Pass definitions; don't re-resolve**: pass `ProgramMethod`/`ProgramField`
  rather than a reference plus `definitionFor`. Reuse an existing
  `MethodResolutionResult` (`lookupDispatchTarget`, `getResolutionPair`), and
  use `resolveMethod(m, isInterface)` over an `OnInterface`/`OnClass` ternary.
- **H.3 Precise `AppView` types**: use `AppView<?>` only in shared D8/R8 code;
  R8-only code should take `AppView<? extends AppInfoWithClassHierarchy>` or
  `AppView<AppInfoWithLiveness>`.
- **H.4\* Reuse existing APIs**: `code.isCfCode()`/`asCfCode()` (already
  handled by `LazyCfCode`), `getR8PartialR8SubCompilationOptions()`,
  `ParameterRemovalUtils.canRemoveUnusedParameter`,
  `getKeepInfo(x).isMinificationAllowed(o)`, `DefaultUseRegistry`,
  `DexMethodSignatureSet`, and `asNonIdentityLens()`.
- **H.5\* Hierarchy traversals**: use `traverseSuperClasses(..)` with
  `TraversalContinuation.breakIf(..)` instead of chained `isSubtype` calls;
  cache per type and stop at non-program classes.

### I. IR & Passes

- **I.1\* Invoke, value & composite predicates**: prefer `getFirstArgument()`,
  `getReceiver()`, `getFirstNonReceiverArgumentIndex()`,
  `isDefinedByInstructionSatisfying`, `hasUniquePredecessor()`,
  `hasUniqueSuccessorWithUniquePredecessor()`, `position.isSome()`, and
  `isEqualsOrNotEquals()`.
- **I.2 Typed iteration**: use `code.<StaticPut>instructions(Instruction::isStaticPut)`
  or `block.entry()`/`getNext()` when not mutating.
- **I.3\* Create & replace IR**: use instruction builders with
  `setFreshOutValue`, `replaceCurrentInstruction`, and `new AffectedValues()`
  (not a raw `Set<Value>`).
- **I.4\* `CodeRewriterPass`**: register in `CodeRewriterPassCollection`, gate
  in `shouldRewriteCode` with O(1) checks (`code.metadata().mayHaveIf()`),
  don't shadow inherited `appView`, and check `hasChanged().isPossiblyTrue()`
  (not `isTrue()`).

### J. Collections & Hot-Path Idioms

- **J.1\* Identity collections & fastutil**: key `DexType`/`DexMethod`/`DexString`/
  `DexProgramClass`/`Value` with `IdentityHashMap`, `SetUtils.newIdentityHashSet`,
  or `WorkList.newIdentityWorkList()`. Use fastutil primitives (`IntOpenHashSet`,
  `Reference2IntOpenHashMap` + `defaultReturnValue(0)`/`getInt`) over boxed maps/sets.
- **J.2 Hot-path allocation & caching**: on hot paths (per instruction/method/class
  or fixpoints), hoist loop-invariant allocations and `static final Pattern`,
  avoid streams/per-call lambdas, use `ListUtils.mapOrElse`, and keep defensive
  copies under `AssertionUtils.assertionsEnabled()`. Avoid `Objects.hash` in hot
  `hashCode()`.
- **J.3\* `IRCode.blocks` is a `LinkedList`**: never call `blocks.get(i)` or
  `blocks.removeAll(...)`; use `ListIterator`/`descendingIterator()` and
  `code.removeBlocks(...)`.
- **J.4 Cheap bailouts & lazy messages**: check `code.metadata()` before
  fixpoints or IR building; build diagnostic strings only on failure.
- **J.5\* R8 utils & Guava idioms**: avoid `List.of`/`Set.of`/`Map.of` in
  compiler code (use `ImmutableList.of`/`ImmutableSet.of`). Prefer `ListUtils`,
  `MapUtils`, `IterableUtils.any`/`all`/`none`, `BitUtils.isAligned`, and
  `WorkList` over hand-rolled loops or `Deque` + `Set`.

### K. Options, Timing, Assertions & Exceptions

- **K.1\* Options & VM-bug predicates**: put WIP flags in `TestingOptions` and
  access via `getTestingOptions()` (not `options.testing`). Use
  `SystemPropertyUtils.parseSystemPropertyOrDefault` instead of
  `System.getProperty(..) != null`. Write `canHave<Symptom>Bug()` using
  `canHaveBugPresentUntilExclusive`, not raw API-level ints.
- **K.2\* Timing, assertions & exceptions**: wrap new passes in `timing.time`
  or try-with-resources and forward the caller's `timing` (never `Timing.empty()`).
  Gate expensive verifiers behind `assert verifyX(..);` returning `true`. Throw
  `Unreachable("why")` from `com.android.tools.r8.utils.internal.exceptions`
  (not `assert false; return null;` or `Unimplemented` for impossible states).
- **K.3 Diagnostics & CLI**: include the offending value after a trailing `:`,
  pass a real `Origin`, and register new CLI flags in `getFlags()`.

### L. Public API (`@KeepForApi`)

- **L.1\* No accidental surface or internal leaks**: make new helpers on
  `*Command`/`*Command.Builder`/`Origin` package-private or internal. Every
  reachable public type (including nested `Builder`) needs `@KeepForApi`; never
  expose `DexProgramClass` or `ClassNameMapper`—use `Origin` and `*Reference`.
- **L.2 Shape & compatibility**: use `enableX()` or `setX(boolean)` (never
  no-arg `setX()`), static `builder()` with non-public constructor, javadoc with
  true/false semantics, API tests in `CompilerApiTestCollection` /
  `RetraceApiTestCollection`, and preserve declared `throws`.

### M. Tests: Builders & Keep Rules

- **M.1\* `testForR8(parameters)` / `testForD8(parameters)`**: avoid
  `testForR8(parameters.getBackend()).setMinApi(parameters)` (silently skips
  R8-partial modes; see 1.4 for the D8 exception). Use `addInnerClasses(getClass())`.
- **M.2\* Keep-rule helpers**: prefer `addKeepMainRule`,
  `addKeepClassAndMembersRules`, `addKeepClassAndDefaultConstructor`,
  `addKeepEnumsRule`, `addKeepAttributeInnerClassesAndEnclosingMethod`,
  `addKeepRuntimeVisibleAnnotations`, `addKeepPackageNamesRule`, and
  `addDontWarn(Class...)` over raw rule strings.
- **M.3\* `applyIf`, defaults, libraries & annotations**: combine complementary
  `applyIf(c, ...)` / `applyIf(!c, ...)` into 3-arg `applyIf(c, t, f)`. Drop
  redundant defaults (`.setIntermediate(false)`, `setMode(RELEASE)` on R8).
  Add `addDefaultRuntimeLibrary(parameters)` after `addLibraryClasses`. Pin
  behavior with `.enableInliningAnnotations()` + `@NeverInline` (and
  `@NoVerticalClassMerging`/`@NoHorizontalClassMerging`) instead of disabling
  whole optimizations globally.

### N. Tests: Parameters

- **N.1\* Parameters & helpers**: use the narrowest runtime/API matrix needed
  (`withDefaultDexRuntime()`, `withNoneRuntime()` for parsers/CLIs), while
  keeping `withAllRuntimesAndApiLevels()` + `.withPartialCompilation()` on R8
  behavior/repro tests, `testForDesugaring(parameters)` on desugaring tests (so
  cf-to-cf is covered), and all relevant `LibraryDesugaringSpecification`s. Use
  `parameters.assumeDexRuntime()`, `assumeJvmTestParameters()`, named
  `canUse*()` helpers, `@Parameter(0)` fields, and `BooleanUtils.values()`.

### O. Tests: `CodeInspector`, Results & Diagnostics

- **O.1\* Matchers, lookups & diagnostics**: use `assertThat(s, isPresent())`,
  `isAbsent()`, `isPresentIf(b)`, `CodeMatchers` (`invokesMethodWithName`,
  `invokesMethodWithHolderAndName`), `uniqueMethodWithOriginalName`,
  `binaryName(Foo.class)`, `SyntheticItemsTestUtils`,
  `addHorizontallyMergedClassesInspector`/`addVerticallyMergedClassesInspector`,
  and `compileWithExpectedDiagnostics` (no extra `assert*Count` alongside
  `assert*Match`).
- **O.2 JUnit idioms**: use `assertThrows(X.class, ..)` instead of
  `@Test(expected)` or try/fail, and `Box`/`BooleanBox` over
  `AtomicReference`/`boolean[]`.

### P. Tests: Structure & Infrastructure

- **P.1\* JVM reference & reuse**: put the JVM reference in its own
  `@Test testJvm()` with `assumeJvmTestParameters()`, not inside
  `if (isCfRuntime())`.
  Use `TestBase.memoizeFunction`/`memoizeBiFunction` over static caches,
  `getSourceFileForTestClassFromResources` / `ToolHelper.getProjectRoot()` /
  `TestDeps` over hardcoded paths, and shared `*TestBase` classes over static
  imports from sibling tests.

### Q. Generated Code & Build

- **Q.1 Generated code**: never hand-edit `BackportedMethods`,
  `VarHandleDesugaringMethods`, keepanno generated files, or LUCI `.cfg`
  outputs—fix the generator/template (`.star`) and commit regenerated output.
- **Q.2 Build & deps**: keep `libs.versions.toml`, `DependenciesPlugin.kt`, and
  `tools/create_local_maven_with_dependencies.py` in sync; remove dead
  dependencies from `DependenciesPlugin.kt` too.

## 3. Writing Clean Review Comments

Write for a teammate reading inline on Gerrit—brief, plain English, and easy to scan:

- **1–2 short sentences.** Name the replacement and give a one-phrase reason
  only when not obvious (cost, coverage, API surface). Prefix `nit`-severity
  suggestions with `Nit: ` (omit the prefix for `should_fix` and `blocking`).
- **Never emit `file://` links, `src/` paths, rule IDs, or CL numbers.** Keep
  any verification paths in internal `Evidence:` only; if a structured output
  format has an optional `reference` field, leave it empty rather than filling
  it with checklist IDs.
- **No bare-symbol dumps.** Always include a brief verb/context alongside the
  suggested API, and skip praise or restating the diff.

Examples of clean comments:
- "Nit: Use `isIdenticalTo` here (and drop `@SuppressWarnings("ReferenceEquality")` if nothing else needs it)."
- "Nit: Remove `toString()` and compare with `DexString#startsWith` directly to avoid allocating a string per check."
- "Nit: Use `testForR8(parameters)` and drop `.setMinApi(parameters)` so partial-compilation configs apply too."
- "Nit: Use `options.getTestingOptions()` instead of accessing `options.testing` directly."
- "Nit: Is `.addDontObfuscate()` needed here? `CodeInspector` maps names, so this looks like a debugging leftover."
- "Please add a bug to the TODO, e.g. `// TODO(b/123456): ...`."
- "Use `Bug: b/291923475` so Gerrit links the bug."

## 4. Reporting Rules

- Anchor every finding on a line added or modified by the CL (or the closest
  added line for issues caused by a deletion).
- Report only `high` or `medium` confidence findings; drop anything you cannot
  substantiate.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only nits/API-usage issues; the other lenses cover
  correctness and compiler invariants.
