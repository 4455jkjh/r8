---
name: r8-cl-review-correctness
description: >-
  Correctness lens for R8/D8 Gerrit CL review: logic slips, null/missing cases, error handling, nondeterminism/thread-safety,
  overflow/encoding, API-level gating, keep-rule and mapping/retrace semantics, tests that cannot fail, tools/script bugs.
  Use for correctness review of an R8 CL; not for style/idiom nits or IR/LIR/lens/keep-info invariants (covered by r8-cl-review-nits and r8-cl-review-invariants).
---

# R8 CL Review — Correctness Lens (`r8-cl-review-correctness`)

Find code that computes the wrong thing, crashes, races, varies between runs, or tests that pass without
testing the change. Rules come from 8,242 human review threads in the last 10,000 R8 CLs (xN = times raised)
plus ~2,000 Fix/Revert/Reland CLs. Before/after code for starred rules (★): [references/examples.md](references/examples.md).
Out of scope: idioms/helpers/naming/formatting/CL hygiene (`nits`); IR/SSA/LIR/CF/DEX, phi/alias/catch
handlers, graph lenses, AppView phases, keep-info lifecycle, D8/R8/R8-partial modes (`invariants`).

---

## 1. Review procedure

1. **Read the claim** (commit message, change metadata, relation chain): fix / optimization / feature / NFC
   refactor / test-only / tools. For a fix, find the regression test and ask "would it fail without the
   fix?" (L.1); in a stack the repro CL may hold it.
2. **Walk the diff hunk by hunk**, opening the whole changed file in `src/`. Ask per hunk:
   - changed condition → evaluate both polarities by hand, re-check each `!` (A.1);
   - variable in a loop/copy-pasted block → the intended one of the similar names (A.2)?
   - new collection → who iterates it, does order reach output (B), which threads touch it (C)?
   - lookup/`get`/`definitionFor`/`getFirst` → can it be null/empty/absent (D)?
   - new branch/early `return`/`break` → what later work is skipped (A.4)?
   - arithmetic/shift/limit/`<=` → boundaries, sign, `MIN_VALUE`, width (G);
   - platform API emitted into app code → min API (H).
3. **Open context** (`rg` in `src/`): callers (relying on old return/nullability/side effects);
   siblings (other `switch` arms, CF vs DEX reader, D8 vs R8 entry point, `equals`/`hashCode`, builder vs
   value class, `copy()` paths); threading context (reached from `ThreadUtils.processItems`,
   `TaskCollection`, `Enqueuer` or `IRConverter` waves? if not, don't flag races); the consumer
   (`ApplicationWriter`, `LineNumberOptimizer`/`ProguardMapSupplier`, rule output) which decides whether
   order matters; pass order in `R8.java`/`IRConverter` for "guaranteed by an earlier pass" claims.
4. **Tests** (L): input reaches the new path, each assertion can fail, expectations independently right,
   both outcomes asserted. **Scripts/build** (M): exit codes, fallbacks, units, parsing, all bot configs.
5. **Re-review** (interdiff): only what changed plus code it invalidates; skip open threads.
6. **Verify**: name the concrete triggering input/state and the observable failure (wrong DEX, crash,
   nondeterministic output, NoSuchMethodError on API X, silent test pass). If you can't, drop it or keep it
   `medium` phrased as a question.

### Severity
- **blocking**: miscompilation, crash/VerifyError on valid input, nondeterministic output, race/deadlock,
  API above min API, keep-rule semantics dropping needed code, wrong mapping/retrace, fix CL whose test can't fail.
- **should_fix**: bug on a rarer path (edge input, error path, Windows, R8 partial), `assert` on external
  input, leaks, unsound disabled-by-default code, test missing the new branch/negative control, script
  reporting success on failure.
- **nit**: rare here (doc contradicting code, unexplained magic threshold).

### Confidence
- **high**: triggering path traced in `src/` (caller → changed code → consumer).
- **medium**: concrete bug, reachability unconfirmed; phrase as a question ("Can `x` be null here when …?").

### Calibration — do NOT flag (author pushback in threads)
- Sorting/`Linked*` when the consumer is order-insensitive or already sorts later (CL 115961: identity-backed
  `FieldAccessInfoCollection` must not imply order); `IdentityHashMap`/`HashSet` used only for membership.
- Races in provably single-threaded code: serial test caches (CL 136783), Gradle helpers (CL 136142: "This
  should be single threaded so let's also get rid of the AtomicLong"). Conversely never suggest downgrading a
  concurrent collection without tracing every writer (CLs 113581, 80401).
- `assert` for internal invariants — R8 style; only external/user input needs reported errors (E.1).
- `null` deliberately meaning "unknown"/feature detection (CL 107400) — flag only if a caller conflates it.
- Fatal errors for invalid-but-loadable input: reviewers prefer preserving runtime behavior (CL 72300).
- Missing tests on pure refactors/NFC CLs; generated files (`BackportedMethods`, `*CfMethods`) — review
  the template and whether it was regenerated, not the generated text.
- Idiom preferences or IR/lens/keep-info invariants (other lenses); speculative "consider" without a failure.

---

## 2. Checklist

### A. Logic slips (most frequent compiler bugs)
- **A.1★ Boolean polarity** — re-check every `!` after renames/extractions: `hasX()` ⇒ `x != null`; early-return
  guards need `!canX(..)`; renamed boolean params flip every use; `is*RemovalAllowed` guards
  `disallow*` (`if (!isPermittedSubclassesRemovalAllowed(..)) joiner.disallowPermittedSubclassesRemoval()`).
  (x7; CLs 91080, 116580, 104581, 80822)
- **A.2★ Wrong variable after copy-paste** — loop var vs outer (`inValue.isPhi()` not `value.isPhi()`),
  `caller.method` vs `method`, `r8Builder` vs `d8Builder`, arity from `invoke.getInvokedMethod()` not the
  context, memoizer returns the computed value not `true`. (x9; CLs 85537, 74664, 85340, 124462)
- **A.3 Change flags** — `hasChanged |= rewrite(..)` in every case (plain `=` drops earlier `true`); a
  `CodeRewriterPass` returns whether IR changed in its `CodeRewriterResult`; `OptionalBool`
  `isTrue()` vs `isPossiblyTrue()` deliberate; mutators return true only when something changed.
  (x6; CLs 81220, 134401, 130620, 135680)
- **A.4★ Early exit skips required work** — `return` inside a per-item loop where `continue` is meant;
  returning `true` after checking one user of all (StringBuilder escape, CL 108843); mandatory work after a
  lookup loop (CL 111980); `onChangedAction`/`finished(..)` callbacks on every path. (x6; CLs 82940, 90181, 99842, 100530)
- **A.5 Refactor/branch split changes behavior** — diff each new branch against every old path: lost side
  effects (`getAndClearSourceFile`), dropped guards (`mode.isInitialTreeShaking()`), filters leaking to other
  callers, conditional adds made unconditional (CliParser ports). (x5; CLs 100802, 126903, 124461, 131301)
- **A.6 Count over candidates, not over the counters map** — zero-occurrence entries are absent from
  `counters`; iterate candidates with `getOrDefault(c, 0)`. (CL 89721; deleted reachable code)
- **A.7 Self-inclusive / tautological checks** — checking overlap after adding the item to the collection
  (`intervals.overlaps(intervals)`), identical ternary arms (`optimize ? 3 : 3`), placeholder conditions
  (`containsKey("")`). (x3; CLs 97464, 97540, 93605)
- **A.8 Placeholder arguments** — `ImmutableMap.of()`/`null` at one call site where real data exists
  silently disables the feature there. (CL 100601)
- **A.9 Ordering assumptions** — "first" element / break-on-first-failure must match the collection's real
  order (`ImmutableDisjointIntRangeMap` iterates by minified line); assert sortedness of lists like
  `SUPPORTED_DEX2OAT_VMS`. (x2; CLs 134140, 73421)
- **A.10 Graph algorithms** — iterative DFS re-pushes the node before successors; don't mutate state
  (`clearInFlow()`) while building the flow graph; a doc claiming "maximum matching" must not be greedy.
  (x3; CLs 78262, 91900, 120480)
- **A.11 Pattern recognizers must be strict** — bail when a required constant is unknown (null), not only
  when different; booleans are 0/1; `isIdenticalTo` not `isLessThanOrEqualTo` for library types;
  `DexEncodedField#getStaticValue()` returns a type default when absent — guard with
  `hasExplicitStaticValue()`; sentinel scans (`UNRECOGNIZED` in `LargeProtoEnumRewriter`) must stop when the
  sentinel is missing. (x5; CLs 129660, 129520, 131060, 114824)
- **A.12 Preserve exceptional/null semantics** — no folding `0 / x`, `0 % x` without `x != 0`;
  `Objects.equals` vs `a.equals(b)`; don't turn reflective `null` args into `new Class[0]`; only public
  `ServiceLoader` services. (x4; CLs 79421, 81040, 96760, 132720; reverts 101380, 101403)

### B. Nondeterminism (blocking when it reaches output)
- **B.1★ Hash order reaching output** — iterating `HashMap`/`HashSet`/`IdentityHashMap`/`ConcurrentHashMap`/
  `appInfo().classes()` to synthesize members, assign names, merge, run an order-dependent fixpoint, write
  rules/profiles/mapping, or pick the reported class: sort (`ListUtils.sort`, `ListUtils.destructiveSort`),
  use `appInfo().classesWithDeterministicOrder()`, `LinkedHashMap`, `ProgramMethodSet.createLinked()`,
  `Int2ReferenceLinkedOpenHashMap`. Identity-hashed keys (`KeepBindingSymbol`) need `Linked*`.
  (x15; CLs 86015, 87301, 78284, 77561, 73521, 121501; fixes 132740 KeepInfoCollection, 95881, 110600, 83461)
- **B.2 IR edits in map order** — if iteration over `Value`/instruction-keyed maps drives rewrites or creates
  values, use `Linked*` or sort by `Value.getNumber()`; `DeterminismChecker` flags SSA numbering drift.
  (x3; CLs 79348, 81800, 83162; fix 124720 positions from inlining)
- **B.3★ Ties need a total order** — no first-wins (`putIfAbsent`, `iterator().next()`, first seen
  concurrently) or partial keys; pick `Collections.min(.., comparator)` like `Outline#getSynthesizingContext`;
  comparators used for output must not return 0 for distinct items (`assert cmp != 0`).
  (x4; CLs 97665, 112800, 95165; fix 121643 ApplicationWriter annotations)
- **B.4 Linked copy of an unordered source** — `new LinkedHashSet<>(x)` only freezes `x`'s order; every
  `copy()`/constructor/getter must keep the linked type. (x2; CLs 94121, 130821)
- **B.5 Stable hashing** — hashes feeding synthetic naming/sharing use `HasherWrapper.murmur3128Hasher()` over
  sorted entries, never `Object.hashCode`/identity hashes. (x2; CLs 95540, 95645; fix 95540 D8 synthetics)
- **B.6 Dedup equivalences must be equivalences** — Guava `Equivalence`/hash keys (e.g.
  `BasicBlockInstructionsEquivalence`, `identicalArrayValuesAfterRegisterAllocation`) must be symmetric and
  transitive with consistent hash, or results depend on block order. (CL 135980)
- **B.7★ Worklists enqueue once** — use `WorkList.newIdentityWorkList(..)`/`addIfNotSeen` instead of a raw
  `Deque` with partial `seen` guards. (CL 132780; fix 132780 type propagation)
- **B.8 Races that change output** — non-concurrent maps written from waves silently vary output.
  (fixes 96260, 107960 horizontal merging → ART profile, 138680)
- **B.9 Tool/report output** — sort trace-references/report output; place markers explicitly (don't assume
  "ours" is first). (x2; CLs 118121, 70661)

### C. Concurrency and shared state
- **C.1★ Shared mutable state on workers** — code under `ThreadUtils.processItems`/`TaskCollection`/Enqueuer:
  non-atomic read-modify-write (`BooleanBox.getAndSet()` → `AtomicBoolean`), `Sets.newIdentityHashSet()` filled
  concurrently (→ `ConcurrentHashMap.newKeySet()`), per-call fields stashed in shared objects (`EdgeMatcher`). (x10; CLs 77242, 105442, 104681, 113581, 89484; fixes 124180 AnnotationRemover.Builder, 121221, 102480)
- **C.2★ Timing is per thread** — on a worker use `timing.createThreadTiming(name, options)`; never store
  `Timing` in long-lived shared objects. (x4; CLs 109360, 109041, 121661, 122083; fix 121661)
- **C.3 Submit through the threading module** — no raw `executorService.submit` or ad-hoc pools; use
  `TaskCollection` with `options.getThreadingModule()` and the entry point's executor (single-threaded mode
  must not fork). Stream results with `TaskCollection#stream`, don't buffer all output. (x3; CLs 121881, 109360, 101042)
- **C.4★ Nested waits deadlock** — an outer `processItems` task blocking on sub-tasks in the same fixed pool
  deadlocks when #threads ≤ #outer tasks; chain tasks instead. (CL 122481; revert 109724)
- **C.5 Inline-submit semantics** — inline execution catches `Throwable` → `Futures.immediateFailedFuture(t)`;
  run only nested tasks inline; don't detect executors by class name. (x2; CLs 122461, 109682)
- **C.6 No mutable static compilation state** — static caches (`static Map<CfCode, ..>`, executor info) leak
  across Gradle-daemon compilations and race between concurrent ones; use instance fields. (x3; CLs 129741, 122461, 77861)
- **C.7 Locking** — double-checked locking re-tests inside `synchronized` with a `volatile` field; racy lazy
  init in generated code (`TypeSwitchMethods`) needs a comment why it is benign; no recursive
  `computeIfAbsent` on a `ConcurrentHashMap`. (x4; CLs 101901, 101945, 91901, 77861)
- **C.8 Await and signal in `finally`** — await spawned work (proguard-map writer); entry points call
  `options.signalFinishedToConsumers()`/`signalFinishedToProviders(reporter)` in a `finally`; propagate
  worker exceptions to the main thread. (x2; CLs 109360, 100921; fix 122982)
- **C.9 Cancellation** — search the whole cause chain for `CancelCompilationException` (`ExceptionUtils`).
  (x2; CLs 77642, 77600)
- **C.10 Test-infra caches** — `ToolHelper` caches shared by parallel tests are thread-safe, never share one
  mutable compile result; use `TestBase.memoizeFunction`. (x3; CLs 136464, 136783, 136142)

### D. Null, empty, absent, missing variants
- **D.1 Null definitions** — `definitionFor` of optional/library-absent types (`kotlin.Metadata`) can be null:
  `asProgramClassOrNull(appView.definitionFor(t))`; superclass walks stop at null `getSuperType()`; arrays need
  `getBaseType().isPrimitiveType()` (`isPrimitiveArrayType()` is depth 1). (x4; CLs 108540, 72010, 81405; fixes 94900, 125380, 139583)
- **D.2 Null-check the value that can be null** — guard the lookup result, not a derived never-null value; a
  disjunct like `!x.hasUses() || ..` must not let the body dereference the absent thing. (x3; CLs 70542, 89363, 97380)
- **D.3★ Empty collections** — check before `get(0)`/`getFirst()`/`iterator().next()` (records with no fields,
  outlines without positions); an omitted annotation element may mean "any" (`@Target`). (x3; CLs 98600, 112100, 126401; fixes 126401, 82520)
- **D.4 Unknown ≠ empty** — keep `null`("can't answer") distinct from empty ("nothing"), especially through
  `getOrDefault(.., emptySet())`. (CL 119420; keep rules silently matched nothing)
- **D.5 Every analysis path yields a state** — no-`Return` methods, bottom results, partially filled arrays get an
  explicit state (e.g. `ValueBoxingStatus.NOT_UNBOXABLE`), never null holes. (CL 85340)
- **D.6★ Cover the whole family** — when generalizing, grep leftovers (`isSingleBoxedBoolean() || ..`); legacy
  Kotlin `Intrinsics` names alongside `checkNotNullParameter`; support + androidx variants; package prefixes end
  in `/`; every `DexValue` kind in `UseCollector.registerDexValue`; both CF and DEX readers set new class props.
  (x8; CLs 84760, 79401, 122160, 97581, 92802)
- **D.7 Nullable inputs** — MethodParameters names, `ProgramResource.getClassDescriptors()`, `Path#getFileName`,
  `visitAnnotation` results. (CL 107300; fixes 79240, 92716, 94250, 97861, 74501)
- **D.8 Cover all operand forms** — swapped/mirrored `If` operands, trivial operands (divisor 1), every
  operator; unanchored `String.replace` on names/rules corrupts identifiers. (x4; CLs 129301, 113060, 81840, 77961)

### E. Error handling, asserts, diagnostics
- **E.1★ External input: report, don't `assert`** — parsers of desugared-library JSON, `api-versions.xml`,
  mapping files, CLI and generator `main()`s: `throw reporter.fatalError(..)`/explicit exceptions; check format
  versions at read time. (x7; CLs 100501, 85533, 69642, 77701, 123660; fix 100182)
- **E.2★ No side effects in `assert`** — `assert set.remove(x)` disappears in production; do the mutation and
  assert the result. Assert-only helpers restore buffer positions (`DexOutputBuffer.assertZero()`).
  (x2; CLs 95141, 71826)
- **E.3 Fail loudly on unexpected shapes** — trailing `else { assert isX(); .. }` → `else if (isX()) .. else
  throw new CompilationError(..)`; impossible overrides throw `Unreachable`, not `return null`/`assert false`.
  (x6; CLs 103301, 102081, 91488, 106583)
- **E.4★ `reporter.error` does not abort** — call `reporter.failIfPendingErrors()` or use `fatalError` before
  code that assumes valid input; return `null` not the malformed value. (x3; CLs 110122, 135342, 85941)
- **E.5 Asserts must be exact and possible** — `==` for exact sizes; asserts a degenerate impl can't pass
  (`orderChanged != blocks.equals(..)`); but not tighter than valid input (`>=` bounds, `DexMethod.match`
  for any-subclass holders). (x9; CLs 107640, 135521, 83141, 106583)
- **E.6 Turn implicit preconditions into asserts** — "guaranteed by normalization" comments, id not already
  registered before `put`, lattice disjointness (`verifyFieldSetsDisjoint`), single-element indexing.
  (x9; CLs 109800, 110461, 115940, 118340)
- **E.7 No-op overflow callbacks** — don't pass `ConsumerUtils.emptyConsumer()` where overflow must never
  happen; throw. (CL 132320)
- **E.8 No user warnings for internal heuristics** — `assert false : ".."`, not `reporter().warning(..)`; no
  unconditional stdout. (x2; CLs 136042, 104941)
- **E.9 Diagnostics** — recursive guards in shared diagnostics (`ExceptionDiagnostic`); parsers throw their
  position-aware type (`ParseException` in `ProguardMapReader`); bounded messages. (CLs 119600, 77028; fix 139440)

### F. Collections and object contracts
- **F.1 Mutation during iteration** — no `put`/`remove` on a map/predecessor list being iterated; collect and
  apply after. (x3; CLs 88940, 135341, 135084; fix 95261 ConcurrentModificationException)
- **F.2 Silent overwrite** — `map.put` in a loop with repeatable keys: merge or assert no different existing
  entry (`FieldMapBacking.replaceFields`). (x2; CLs 90824, 75149; fix 78701 mapping clobbering)
- **F.3★ equals/hashCode** — same discriminators in both (CL 113463); value objects in sets/`containsAll`
  override both; identity `equals` needs enforced singletons; lattice `isEqualTo` is not `<=` (CL 92240).
  (x4; CLs 85340, 87480, 113463, 92240)
- **F.4 Immutable/unmodifiable traps** — `Collections.unmodifiableList` is a view, not a copy
  (`ImmutableList.copyOf`); guard `removeIf`/`clear`/caller adds on possible `ImmutableList.of()`/`emptyMap()`;
  `List.of(..).contains(null)` throws. (x4; CLs 70407, 92364, 77543, 74500)
- **F.5 Wrong key type** — `Collection.remove(k)` with a mismatched type is a silent no-op. (CL 135268)
- **F.6 Transactions/derived counts** — new state in `IndexedItemTransaction`/`VirtualFile` updates
  abort/commit/`isEmpty()` and every `getNumberOfX()`; `isEmpty()` derived from state; finalizing getters reset
  pending state. (x4; CLs 80205, 120341, 73101, 109581)

### G. Arithmetic, width, encoding, platform
- **G.1★ Sign and `MIN_VALUE`** — `Math.floorMod(h, n)` not `Math.abs(h) % n`; power of two needs `c > 0`
  (`bitCount(0x80000000) == 1`, sign-extends for long); narrowing check `((long) i) != l`; `Integer.compare`
  not `a - b`; negative steps in loop bounds. (x5; CLs 120720, 134760, 79421, 130560, 133006; fix 135080)
- **G.2★ Inclusive/exclusive limits** — when limits change or are reused (`MAX_ENTRIES = U16BIT_MAX + 1`,
  `VirtualFile.getMaxNumberOfTypes`) match sibling `<`/`<=`; define threshold 0; ranges `'A'..'Z'` need `<=`.
  (x3; CLs 130140, 97100, 75097; revert 131660)
- **G.3 Shift semantics** — fold shifts per JLS 15.19 (mask 5/6 bits; negative distances) or bail.
  (x2; CLs 92377, 92500; fixes 92377/92502/92530)
- **G.4 Float/double bits** — `Float.floatToRawIntBits`/`IntUtils.encodeFloat`/`LongUtils.encodeDouble`; record
  `equals` uses `Float.compare`/`Double.compare`. (x3; CLs 114542, 114560, 133740)
- **G.5 Width** — `writeShort`/`getShort` > 32767 reads negative; offsets into huge mapping text are `long`
  (`TextPosition.getOffsetAsInt()`); int counters in stats can overflow. (x2; CLs 77980, 78044; fixes 105541, 108181, 134161)
- **G.6 Descriptors** — validate before slicing (`DescriptorUtils.isValidClassDescriptor`); handle primitives
  in type-name helpers. (x2; CLs 107460, 80181; fix 110124 `isRClassDescriptor` SIOOBE)
- **G.7 Platform** — `File.pathSeparatorChar`/`os.pathsep` for classpaths, `'/'` in zip entries, unix lines in
  emitted mapping (`StringUtils.unixLines`), `Reader` not hard-coded charsets, no re-unescaping DOM values.
  (x6; CLs 87360, 77340, 70681, 107180; fixes 106241, 102080)

### H. API-level gating, backports, desugared library
- **H.1★ Never emit APIs above min API** — backport templates use only APIs at their lowest target (no
  `Objects.checkFromIndexSize`); inserted platform calls check `options.getMinApiLevel()` and DEX output.
  (x3; CLs 129741, 116721, 116682; reverts 99221, 101301, 122322)
- **H.2 Full `AndroidApiLevel` comparisons** — `isLessThan`/`isGreaterThanOrEqualTo`, never `getMajor()` (minor
  levels); full levels in cache keys; invalid levels rejected, not mapped to `MAIN`. (x3; CLs 136783, 136464, 116427)
- **H.3 Exact gating level** — `LibraryValidator` markers and API database entries at the exact level (e.g.
  `BAKLAVA_1`); spec `api_level_below_or_equal`/`api_level_greater_or_equal` are inclusive; bump spec
  `identifier` on content change. (x6; CLs 124942, 92534, 71940, 70986; fixes 104883, 71701)
- **H.4 Backports follow the JDK spec** — delegate to documented equivalents; test sign, overflow boundaries
  and divergences; regenerate `BackportedMethods`. (x6; CLs 126413, 113589, 113900; reverts 114023, 114060, 117043)
- **H.5 VM-bug workarounds** — gate on the latest/main level until fixed; test both sides of the cutoff.
  (x2; CLs 82247, 138520)

### I. Keep-rule / ProGuard config / keep-annotation semantics
- **I.1★ `-keepclasseswithmembers` is conjunctive** — an unmatchable member rule makes the rule match nothing
  (drop/never-match, don't clear); `{ *; }` doesn't match memberless classes; no implicit `<init>()` there.
  (x5; CLs 120245, 89080, 92532; fixes 71703, 87784, 87304)
- **I.2★ Global options in library consumer rules** — validation rejects all globals; new globals wired into
  `FilteredKeepRulesBuilder` and `ValidateLibraryConsumerRulesKeepRuleProcessor#handleRule`; repeated options
  combine with `meet` (like `-maximumremovedandroidloglevel`), not last-wins. (x5; CLs 110122, 110962, 112940)
- **I.3 Match-all detection inspects matchers** — `MatchAllTypes`/`MatchClassTypes`, every positive pattern of
  a `MixedClassNameList`. (x3; CLs 87322, 118001, 117640; fix 87322)
- **I.4 Embedded rules** — single ingestion path `R8Command.amendWithRulesAndProvidersForInjarsAndMetaInf`;
  `META-INF/com.android.tools/` replaces `META-INF/proguard/`. (x2; CLs 96861, 97702; revert 97941)
- **I.5 Edge semantics** — `-keep class * extends X` with missing `X`; implicit `-keepattributes` from
  options widen globally; no-op flags in `IGNORED_FLAG_OPTIONS`; derived defaults must not override explicit
  settings (`canMatchRuntimeInvisibleAnnotationsWithWildcards`). (x4; CLs 74502, 70564, 75330; fixes 120460, 119403)
- **I.6 Keep annotations** — `KeepConstraint.LOOKUP` keeps existence, `NAME` keeps the name;
  `CLASS_INSTANTIATE` is not reflection; `disallow={..}` implies allowshrinking; `@KeepEdge` for unconditional;
  sentinels must not collide with user values. (x5; CLs 94661, 95301, 74621, 106540)
- **I.7 Enums used in annotations** — generated rules must not `allowobfuscation` (runtime `Enum.valueOf`).
  (x2; CLs 97622, 97701)

### J. Mapping file and retrace
- **J.1 Final vs original names** — residual references from `getFinalName()`; originals via residual lookup.
  (x2; CLs 81540, 81462)
- **J.2 Conservative without positions** — position 0/none uses an explicit range covering 0 or the ambiguous
  set; minimal inline ranges `:0`. (x4; CLs 76700, 68411, 75705; fixes 83020, 82520)
- **J.3 Merging maps/partitions** — throw on mixed metadata/map versions; preambles agree except
  `pg_map_id`/`pg_map_hash`. (CL 119622; fix 109462)
- **J.4 Positions/outlines/composition** — changes need retraced-stack-trace tests (`inspectStackTrace`), not
  `getOriginalSignature()` (CLs 86604, 70520; reverts 136740, 137681, 84980, 74840, 75341; fix 139760).

### K. Entry points, options, resources
- **K.1 System-property keys match the field** — copy-pasted `getSystemPropertyOrDefault` keys wire the wrong
  option. (x2; CLs 118240, 117800)
- **K.2 Boolean parsing** — branch on the parsed value (`--no-quiet`), not non-null/presence; dump values as
  fallback. (x2; CLs 124264, 116420)
- **K.3 Consumers/providers** — `StringConsumer.accept` may be chunked (append); decorators forward
  `finished()` to all delegates exactly once; `DesugarGraphConsumer` reports edges with unknown origins.
  (x5; CLs 125600, 130821, 99842, 76763)
- **K.4 Leaks and memory** — close captured `ZipFile`s/streams; null out large buffers (mapping) on long-lived
  objects; don't build one giant string; static caches leak in daemons. (x3; CLs 78661, 87183; fixes 103681, 125560, 125364, 135140)
- **K.5 Nested commands** — disable unsupported modes up front
  (`R8PartialCompilationConfiguration.disabledConfiguration()`). (CL 104941)
- **K.6 Dumps** — record behavior settings as dump properties; keep reflectively called builder methods
  (`CompileDumpCompatR8`) or fall back; throw if the class exists but the method doesn't. (x3; CLs 72900, 71926, 70936)
- **K.7 Hot-path cost** — per-invoke hierarchy walks/`appInfo().classes()` scans, `code.blocks.get(i)` on the
  `LinkedList`, unguarded `removeAll`, O(N²) list membership. (CLs 133500, 131060, 135620, 135481)

### L. Tests that do not test what they claim
- **L.1★ Input must reach the path** — feature flag actually set (`addOptionsModification`), triggering value
  used, main dispatches to the code, output depends on the behavior; regression test fails without the fix.
  (x19; CLs 97540, 97060, 101560, 134242, 125040, 132180)
- **L.2★ Assertions that can't fail** — `forEach`/`if (insn.isX())` over zero matches; assert the count
  first (`assertEquals(N, clazz.allMethods().size())`); `anyMatch` vs exact counts; Java `assert`/try-catch
  without `fail()` → JUnit/`assertThrows`; `run(..)` without `assertSuccessWith*`. (x22; CLs 86681, 131240, 90841, 138940, 92301)
- **L.3★ Pair absence with positive evidence** — `not(invokesMethodWithHolder(X))` plus the replacement;
  `assertUnboxed(A).assertNotUnboxed(B)`; `containsString(NEW)` plus `not(containsString(OLD))`; keep tests need
  a control that is removed (`isAbsent()`). (x24; CLs 117983, 76762, 75097, 86760, 75145)
- **L.4★ Don't disable what you test** — `noHorizontalClassMerging()` in a no-merge test →
  `addHorizontallyMergedClassesInspector(HorizontallyMergedClassesInspector::assertNoClassesMerged)`; debug
  options that change logic (`enableEnumUnboxingDebugLogs`) also run without. (x2; CLs 75906, 74260)
- **L.5 Pin the scenario** — `@NeverInline`/`@NoParameterTypeStrengthening`, transformers for shapes javac
  won't emit; assert the precondition directly (`isJumboString`, not `hasLineNumberTable()`). (x7; CLs 83141, 126400, 113604, 125040)
- **L.6 Specific failures/diagnostics** — `assertFailureWithErrorThatThrows(X.class)`;
  `compileWithExpectedDiagnostics(d -> d.assertErrorsMatch(diagnosticMessage(..)))`; fail closed on output.
  (x8; CLs 108200, 33029, 91061, 125860)
- **L.7 Reference expectations** — validate hand-written expected output with `testForJvm`; TODO tests assert
  the current wrong shape with a bug. (x5; CLs 70930, 70820, 73281)
- **L.8 Coverage of variants** — each new branch, D8 debug/release, R8 full/compat, both sides of min-API
  cutoffs, boundary numerics (`Long.MIN_VALUE`, -0.0, NaN), non-degenerate maps, all operand kinds.
  (x30; CLs 70245, 92745, 115300, 73282, 138441)
- **L.9 Parameters actually used** — `run(parameters.getRuntime(), ..)` not `ToolHelper.runJava`/
  `TestRuntime.getDefaultCfRuntime()`; `setMinApi(parameters)`; `getApiLevel()` null on CF without
  `enableApiLevelsForCf()`; R8 partial needs `withMaximumApiLevel()`; open-ended version guards
  (`isNewerThanOrEqual`, not `isInRangeInclusive`); no `if (!exists) return;`. (x12; CLs 138940, 86022, 101060, 133820, 40521)
- **L.10 Global/shared state** — restore `System.setIn/setProperty` in `finally`; fresh `DexItemFactory` when
  reading output (`extractMarkers()`); `MultiTestRunResult` callbacks run per runtime; no wall-clock values;
  generator tests compare with the checked-in file. (x10; CLs 127340, 71289, 137500, 122760; fixes 105281, 121660)
- **L.11 Line separators** — `StringUtils.unixLines`/`joinLines` consistently on both sides. (x4; CLs 76585, 75901, 124420)

### M. Tools, scripts, build
- **M.1★ Propagate failures** — `subprocess.run(cmd, check=True)`/`check_call`; error paths `return -1`/
  `sys.exit(1)`, not bare `return`; continue-on-error scripts exit non-zero at the end. (x9; CLs 76182, 138081, 93662, 117181; fix 81240)
- **M.2★ No silent fallbacks** — no `rmtree(ignore_errors=True)`, no fallback branch names, no skipped missing
  inputs; results filed under the tested hash, not `HEAD`. (x12; CLs 79620, 99702, 113485, 101661; fix 100564)
- **M.3 Units, regexes, bounds** — `time.sleep` takes seconds (`5*60*1000` = 3.5 days); longest-first anchored
  alternations (`(ms|m|s)?$`); pending `pop()` in size checks. (x4; CLs 119182, 125160, 122680, 86321)
- **M.4 Strict argument parsing** — reject unknown options/positionals, both `-g=dir`/`-g dir`, conflicting
  options, delimiters inside joined values. (x5; CLs 128080, 134982, 94267, 83760)
- **M.5 Copy-pasted configs** — every value per bot/dep entry (`linux-jdk22` with `--runtimes=jdk21`);
  regenerate cfg after `.star` edits; Python names only fail at runtime. (x5; CLs 91582, 91941, 76801; fixes 93560, 93322)
- **M.6 Dependencies** — update `DependenciesPlugin.kt` + `tools/create_local_maven_with_dependencies.py` +
  `.sha1`; every Gradle setup; new test modules wired for r8lib (`tools/test.py --no-r8lib`). (x14; CLs 95480, 85026, 82246, 89900; fixes 118740, 118800)
- **M.7 Gradle hygiene** — configuration-cache safe (no `providers.exec` at config time), outputs declared
  `@Input`, ported scripts keep flags/defaults/classpath order; toolchain bumps explained. (x10; CLs 84300, 120541, 129500, 119000; fixes 129160, 118600)
- **M.8 Bootstrap/portability** — no hard-coded local developer home paths; guard `jdk.GetJdkHome()` on
  fresh checkouts; tests write only to temp dirs. (x6; CLs 112420, 127362, 127542, 77380)

---

## 3. Comment style

1–2 sentences: the failing input or consequence, then the fix; a question when reachability is uncertain. Real examples:

- "This should be negated. When removal is not allowed, we need to call `disallowPermittedSubclassesRemoval()` on the keep info." (CL 80822)
- "This may return `MAX_ENTRIES`, which is `Constants.U16BIT_MAX + 1`. Should this be `<` instead of `<=` like the two checks above?" (CL 130140)
- "The `counters` map will only contain counters for the callees we've seen calls to. I think we need to iterate `callees` here." (CL 89721)
- "We still need to process all the remaining users, so we cannot eagerly return true here." (CL 108843)
- "Can this deadlock with a `newFixedThreadPool` if #threads <= #virtual files?" (CL 122481)
- "This is running on a thread != main thread. This needs to retrieve the timing for the given thread using `Timing threadTiming = timing.createThreadTiming(...)`." (CL 109360)
- "Should we maybe change `toRemove.contains` to `toRemove.remove` so that we have the same behavior without assertions enabled?" (CL 95141)
- "This actually disables the merger. To ensure no merging happened use `.addHorizontallyMergedClassesInspector(inspector -> inspector.assertNoClassesMerged())`." (CL 75906)

No praise, restating, or rule ids in `message`.

---

## 4. Reporting rules

- Anchor every finding on a line in the **new** version of the file that the CL adds or modifies (or the closest added line for issues caused by a deletion). Do not report on untouched code unless the CL's change makes that code wrong, and then anchor on the changed line.
- Report only `high` or `medium` confidence findings; verify each against the repository source tree before reporting. Drop anything you cannot substantiate with concrete code.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only issues that belong to this lens; the other lenses cover the rest.
