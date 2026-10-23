---
name: r8-cl-review-nits
description: >-
  R8/D8 Gerrit CL review lens for nits and API usage: style, naming, comments, imports, dead code, simplifications, R8 helper APIs over hand-rolled code, Java/Guava/fastutil misuse, inefficient-but-correct code, test-builder/CodeInspector idioms, test structure and commit hygiene (mined from 8,242 R8 review threads).
  Use for the nits and API-usage pass of an R8 review; not for correctness/soundness or IR/pipeline invariants (covered by r8-cl-review-correctness and r8-cl-review-invariants).
---

# R8 CL review: nits & API-usage lens

Rules cite frequency in human threads (`xN`) and CLs (`r8-review.googlesource.com/c/r8/+/<CL>`). Before/after code and full helper lists: [references/examples.md](references/examples.md).

## 1. Review procedure

### 1.1 Read

1. Read the change metadata, commit message, relation chain, and existing comments; never duplicate open threads. On re-review, review only the interdiff plus `/COMMIT_MSG`.
2. Classify each file:
   - `src/main` -> F-L; `src/test` -> M-P; `tools/`, `*.kts`, `infra/` -> Q; A-E apply everywhere.
   - `src/keepanno`, `src/resourceshrinker`, `src/library_desugar` cannot use R8 utils (`library_desugar`: no Guava/fastutil either); local helpers are fine there.
   - Release-branch cherry-pick -> A.5 only. Repro+fix stack -> A.6.
3. Walk the diff hunk by hunk; check only added/modified lines. An untouched line counts only if the CL made it stale; anchor on the changed line.

### 1.2 Verify in the repository before reporting

- **Helper suggestions**: `rg -n '<name>\(' src/` and read the signature; never suggest a helper you have not found. Generic utils: `src/utils/java/com/android/tools/r8/utils/internal/` (`ListUtils`, `SetUtils`, `MapUtils`, `IterableUtils`, `OptionalBool`, `collections.WorkList`, `exceptions.Unreachable`).
- **"Unused" (A.3)**: `rg -w <symbol>` over all of `src/` (testbase, keepanno); check overrides and reflection.
- **"Cannot fail" (F.2)**: read callers and what establishes the guarantee; if non-local, suggest an `assert` instead of deletion.
- **Names/visibility (D, F.5)**: compare with siblings, superclass, overrides (an override cannot rename or narrow).
- **Performance (J)**: hot paths only: per instruction/method/class or inside fixpoints (`CodeRewriterPass#rewriteCode`, `Enqueuer#trace*`, `UseRegistry#register*`, LIR scans, `ThreadUtils.processItems` lambdas, rule matching). Cold code: at most a nit.
- **Comments (B)**: read the code to prove the comment stale.
- **Tests (M-P)**: read `@Parameters`, the base class and the suggested builder/matcher (`TestBase`, `TestShrinkerBuilder`, `R8TestBuilder`, `TestParameters`, `codeinspector/Matchers`, `CodeMatchers`).
- **Public API (L)**: check `@KeepForApi` on the class or outer class.
- **Passes (I.5, K.3)**: read the creation site (`CodeRewriterPassCollection`, `R8`, `PrimaryR8IRConverter`).

### 1.3 Severity and confidence

- **blocking** (must not land): behavior-changing debug code (`&& false`, `if (true)`, `System.out.println`/`Math.random()` in `src/main`); silently dropped coverage (narrowed/commented `@Parameters`, `@Ignore`); new public surface on `@KeepForApi`; hand-edited generated files or missing copyright header; quadratic scan on a whole-app hot path.
- **should_fix**: misused R8 API with real cost/drift risk; code the CL made unused; lying comments; TODO without bug; bad `Bug:` footer; misleading names; weak test assertions; `TAG=`/`CONV=`.
- **nit**: readability/convention only; prefix `Nit:`.
- **Confidence**: `high` = verified and mechanical fix; `medium` = clear convention, fix needs judgment. Nothing lower.
- **Volume**: report a repeated pattern once with "(here and below)"; at most ~15 findings, by severity.

### 1.4 Do NOT flag (reviewer-accepted pushback)

- `==` on definitions without `equals` (`DexEncodedMethod`/`Field`, `DexClass`, `ProgramMethod`) or on SSA `Value`s (106400). `isIdenticalTo` applies only to `DexReference`, `DexString` and `DexProto`.
- `X x = insn.asX(); if (x == null) continue;` (accepted idiom, 70822).
- `testForD8(parameters.getBackend()).setMinApi(parameters)` when the chain needs `D8TestBuilder`-only methods (`testForD8(parameters)` returns `TestCompilerBuilder`).
- `.release()` on D8, whose default is debug (122960).
- `addDefaultRuntimeLibrary` after `addLibraryClasses` (which replaces the default library).
- `if (System.currentTimeMillis() > 0)` and `System.out.println` in test *input* programs.
- Untouched legacy code: `public final` IR fields, old formatting, old names.
- Perf rewrites in cold code; new `DexClass` helpers for trivial filter variants (104660); `Objects.hash` outside hot `hashCode()`.
- Copyright year on moved files; cleanups on cherry-picks; structural test edits in the *fix* CL of a stack.
- Anything whose main impact is wrong output, crash, nondeterminism, or a broken IR/lens/keep-info invariant or pass ordering (other lenses).

## 2. Checklist

Default severity: `{B}` blocking-eligible, `{S}` should_fix, `{N}` nit; adjust per 1.3.

### A. Diff hygiene and dead code

- **A.1 Debug leftovers** (x105; 81721, 116081): `System.out.println`, `.inspect(i -> System.out.println(..))`, `.disassemble()`, `allowStdoutMessages()`, `System.currentTimeMillis();` breakpoint anchors, `&& false`, `if (true)`, `throw new RuntimeException("XXX")`, scratch files. {B}
  - **`.addDontObfuscate()` in tests** (often left in from debugging, to make names readable in `.inspect()`/`.disassemble()` output). Check whether the test needs it: the output or assertions depend on original names (`getClass().getName()`, stack traces, `toString()`), reflection or a JNI/resource lookup by string name, a keep-rule or naming test, or a check that bypasses the mapping (comparing raw `getFinalName()` values). Lookups such as `clazz(A.class)` and `uniqueMethodWithOriginalName(...)` go through the mapping and do not need it. If none of these apply, flag it on the added line: `Is .addDontObfuscate() needed? It looks like a debugging leftover; CodeInspector maps names, so this can likely be removed.` {N} (should_fix if it hides the behavior the test claims to cover, e.g. a minification/repackaging test).
- **A.2 Commented-out code** (x28; 104681, 95748): delete commented statements, `// @Ignore`, and builder or assume calls. Unfinished work becomes `TODO(b/...)`. A narrowed `@Parameters` is {B}. {S}
- **A.3 Code the CL made unused** (x44; 124342, 134360): delete dead `InternalOptions`/`TestingOptions` flags (accessor too, no `return false;` stub), overloads, enum values, factory fields, parameters, orphaned helpers. {S}
- **A.4 Unrelated hunks** (x53; 122221, 103240): revert churn on untouched lines, import reorders, incidental default flips, rebase leftovers. Big renames and unrelated fixes get their own CL. {S}
- **A.5 Moves and cherry-picks stay minimal** (x6; 106583, 83141): moves are git renames with no behavior change. Release-branch CLs carry no cleanups, and only test helpers that exist on that branch. {S}
- **A.6 Repro+fix stack** (134680, 135060): the repro CL holds all test code and asserts current behavior under `// TODO(b/...): Should be ...`. The fix CL only flips expectations. {N}

### B. Comments, TODOs and javadoc

- **B.1 Comments the change invalidates** (x130; 137140, 110641): javadoc, `@return`, inline comments, assert messages describing old behavior; resolved TODOs; aspirational claims (make them `TODO(b/...)`). {S}
- **B.2 Copy-pasted text** (x33; 132740, 71720): text naming the wrong class, flag or phase, e.g. `"...initial keep info"` in final-export code, or `// two outlines` above `assertEquals(1, ...)`. {S}
- **B.3 TODO format** (x61; 70404, 117801): `TODO(b/<id>): <what is wrong>`. Never `TODO(user)`, `TODO(b/)` or bare `TODO:`. Disabled checks and workarounds (`setEnableDex2OatVerification(false)`) need a cause plus a bug. Known-wrong expectations get `// TODO(b/N): Should be "A".` {S}
- **B.4 Explain the why** (x85; 76083, 130140): one line for remote-guard early returns, unsynchronized caches, swallowed exceptions, fixpoints, `DupX1`/`Swap` tricks, deliberately missing `@KeepForApi`. Every `canHave...Bug()` needs a description plus `b/` link. {N}
- **B.6 Javadoc and bytecode docs** (x26; 70407, 103301): use `/** */` and `{@code x}`, describe `enableX` as "turn on/off X", write DEX/CF in caps. Hand-built `CfInstruction` lists and ASM dumps need a pseudo-Java comment; prefer `transformer(X.class)` to dumps. {N}

### C. Commit message (`/COMMIT_MSG`)

- **C.1 Subject and body match the diff** (x81; 72028, 138322): subject <=72 chars saying what and where ("Fix IllegalAccessError from synthetic bridge insertion", not "Fix b/426351560"). Proofread; update after splitting; no squash leftovers, extra `Change-Id:` or `[WIP]`; mention behavior changes the title omits. {S}
- **C.2 Bug footer** (x55; 121980, 108780): `Bug: b/<id>`, not `Bug: 291923475`, for the original issue. Use `Fixes: b/<id>` to auto-close, and list every bug. Fixes that may be cherry-picked need one. {S}
- **C.3 `RELNOTES:`** (x15; 126903, 110240): required for user-visible changes (keep-rule semantics, flags, defaults, API/CLI). For keep-rule changes, list the affected rules and the opt-out. {S}
- **C.4 No tool metadata** (x13; 135343, 115761): no `TAG=agy`, `CONV=...`, AI boilerplate, or local `PRESUBMIT.py` edits. {S}

### D. Naming

- **D.1 Typos** (x69; 84300, 71232): `orignalOffset`, `setaddVersionAsPreamble`; critical in API names, keep-rule modifiers and system properties (`...stsartup...` can never be enabled). {S}
- **D.2 Name the full behavior** (x74; 110641, 117780): name side effects (`getAndClear...`), `compute...` for non-trivial getters; booleans match their expression (`art15OrOlder = v.isOlderThanOrEqual(V15_0_0)`); plurals for collections; positive predicates (`isGeneratingDex()`, not `!isGeneratingClassFiles()`). {S}
- **D.3 R8 vocabulary and sibling names** (x84; 86520, 94961): `clazz`, `method`, `code`, `invoke`, `factory`, `field` vs `fieldReference`, `isInterface` (not `itf`), `Unsupported<X>Diagnostic`. Mirror siblings (`StartEnqueuerAnalysis`). Consistent casing (`worklist` vs `workList` fails InconsistentCapitalization). Unused lambda params: `unused`. {N}
- **D.4 Established terms** (x20; 75496, 70362): `rewrittenWithLens` takes a lens. "Live" means Enqueuer liveness. `argumentIndex` includes the receiver, `parameterIndex` indexes the proto. `previous...` is the previous lens, `original...` only the identity lens. Name skipped checks (`isObjectCloneWithoutHolderCheck`). {S}
- **D.5 Rename what derives** (x24; 82140, 81580): after a rename, also rename factories, locals, parameters and rewriter ids, plus variables whose meaning changed (`leftLattice` -> `leftAbstractValue`). The old concept becomes `Legacy...`. {N}
- **D.7 Enums, not flags** (x14; 104681, 88980): mode `boolean` parameters become enums, `int` API levels become `AndroidApiLevel`, and test `boolean debug` becomes `CompilationMode`. Positional boolean constructors become named factories. {N}

### E. Formatting, imports, headers, suppressions

- **E.1 Format touched lines only** (x60; 97020, 106540): `tools/fmt_diff.py` (100 cols Java/`.kts`, 80 Python). No tabs, trailing spaces, double blank lines; comments end with a period. Never reformat untouched code. {N}
- **E.3 Imports** (x18; 123700, 117340): no inline FQNs unless simple names truly clash (ASM vs LIR `Opcodes`), and no wildcard imports. {N}
- **E.4 Copyright header** (x28; 113761, 134880): every new file (`.kts` and keep-rule `.txt` included) has exactly one R8 header with the current year. Moved files keep their year. Adapted code needs a source URL and license. {B if missing, else N}
- **E.5 Suppressions** (x9; 86024, 139340): drop `@SuppressWarnings("ReferenceEquality")` once `isIdenticalTo` is used, and stale `UnusedVariable`/`HidingField`. Never `@SuppressWarnings("")` or a global disable. {N}

### F. Structure and simplification

- **F.1 Merge branches, return early** (x44; 94447, 127021): `if (a || b)` over duplicate bodies, `&&` over nested ifs, `else if` for exclusive checks; hoist shared statements; swap branches to drop a negation/empty arm; cheap early returns first. {N}
- **F.2 Checks that cannot fail** (x37; 99842, 113463): `isProgramClass()` inside `appInfo().classes()`, `x != null &&` before `x == CONST`, `isEmpty()` return before a loop, null checks on non-null definitions, `withLiveness()` on `AppView<AppInfoWithLiveness>`, guards the callee repeats. {S}
- **F.3 Deduplicate and hoist** (x39; 132320, 108640): extract repeated blocks and compound conditions into helpers or named predicates (one inline copy was found inverted). Hoist repeated chains (`DexType holder = invoke.getMethod().getHolderType();`). Overloads delegate. {N}
- **F.5 Visibility and finality** (x35; 85941, 113780): new fields `private final` + getter (legacy `public final` IR fields are no precedent); stateless helpers `private static`; util classes get a `private` constructor; add `@Override`. {N}
- **F.6 Encapsulation** (x10; 115261, 122460): don't reach into another object's collections or downcast it; add a method or narrow mutator on the owner. Use `appView.options()`, not `IRCode.options()`. {N}
- **F.7 No sentinels** (x18; 89363, 102242): never let `null` mean base or all; use `FeatureSplit.BASE` or `Predicates.alwaysTrue()`. Use `Foo.empty()` with `isEmpty()`, and `isX()` instead of `this == NONE_X`. Rewrite helpers return their input unchanged. {N}
- **F.8 Polymorphism; pure predicates** (x12; 84620, 136080): kind logic in overrides, not `isX() ? ... :` at call sites; abstract discriminators. Never mutate IR in `is...`/`has...` (`normalizeArgumentsForCommutativeBinop()` in `isBinopWithLit8OrLit16`). {N}
- **F.10 Concise Java** (x12; 112420, 64781): drop dead stores, `tmp = ...; return tmp;`, `x != null ? (String) x : null`, uncaptured `BooleanBox` and super-only overrides. `if (!set.add(x)) return;`, `Optional.ofNullable(v)`, `<>`. {N}
- **F.11 Magic numbers** (x14; 79421, 124801): use named constants or options (`ALL_BITS_SET`, `rewriteArrayOptions().maxSizeForFilledNewArrayOfInts`) and `'0'`, not 48. Question unexplained bailouts (`blocks.size() <= 2500`). {N}
- **F.12 Minimal signatures** (x13; 116721, 85340): don't pass what's derivable from another argument or from `appView`, and pass `appView` instead of lens + factory + options. Return results, not `Box` out-parameters. Normalize (`getAliasedValue()`) inside the helper. {N}

### G. Dex items and `DexItemFactory`

- **G.1 `isIdenticalTo`** (x29; 91265, 135268): compare `DexType`/`DexString`/`DexMethod`/`DexField`/`DexProto` with `isIdenticalTo`/`isNotIdenticalTo`, never `==`/`equals` (asserts and streams too). Possibly-null side: call on the constant (`factory.objectType.isNotIdenticalTo(clazz.getSuperType())`) or `DexType.identical(a, b)`. {S}
- **G.2 No `DexString` -> `String`** (x9; 131060, 134900): not `getName().toString().equals("x")`, `toSourceString().startsWith` or `toDescriptorString().startsWith("Lj$/")`. Use `isIdenticalTo(factory.xName)`, `isEqualTo(String)`, `startsWith`, `iterator()`, `getDescriptor().startsWith(factory.jDollarDescriptorPrefix)`. Pass `DexReference`s, not strings, to tracing. {S}
- **G.3 Factory constants and member groups** (x19; 73763, 133260): reuse factory fields and member groups (`classMethods.getDeclaredField`, `StringBuildingMethods`, `JavaUtilConcurrentTimeUnitMembers#isConversionMethod`) and match with `isIdenticalTo`, not name + arity. Use `method.match(proto, name)`. Never call `createString("...")` per instruction, and don't cache factory results. {S}
- **G.5 Builders** (x9; 100921, 77683): `createInstanceInitializer(holder, params...)`, `field.withHolder(holder, factory)`, `createFreshMethodNameWithoutHolder` (no `$collision$` loops), `X.EMPTY_ARRAY` (never `new X[0]`). {N}
- **G.6 No Reference API or reflection in `src/main`** (x3; 100921, 71510): no `Reference.*` or `Foo.class.getMethod`. Build items with the factory and reuse AppView holders (`protoShrinker()`'s `ProtoReferences`). {S}
- **G.7 `DescriptorUtils` and rendering** (x12; 77961, 114024): `getInternalNameFromJavaType`, `javaTypeToDescriptor`, `getSimpleClassNameFromDescriptor` instead of `replace('.', '/')`. In messages use `getTypeName()`, `MethodReferenceUtils.toSourceString` and `toProguardMapSource()`, never implicit `toString()`. Use `SyntheticNaming.COMPANION_CLASS_SUFFIX`, not `"$-CC"`. {S}

### H. Definitions, AppView and resolution

- **H.1 Typed accessors** (x16; 77543, 123660): `asProgramClassOrNull(appView.definitionFor(t))`, `asProgramMethod()`, `asDexValueInt()`/`asConstNumber()` instead of casts, `getHolderType()` (not `getReference().holder`), `asProgramMethod(clazz)` instead of `new ProgramMethod`. {N}
- **H.2 Member pools** (x9; 107640, 89484): `staticFields(DexEncodedField::hasExplicitStaticValue)`, `methods(DexEncodedMethod::hasCode)`, `forEachProgramInstanceInitializer`, `forEachProgramMethodMatching`, `hasMethodsOrFields()`; indexed `lookupMethod(proto, name)` instead of scanning. {N}
- **H.3 Pass definitions; don't re-resolve** (x12; 85180, 126903): take `ProgramMethod`/`ProgramField`, not a reference plus `definitionFor`. Call `lookupDispatchTarget`/`lookupSingleVirtualTarget` on the result you already have, not `InvokeMethod#lookupSingleTarget`. Use `getResolutionPair()`, and `resolveMethod(m, isInterface)` instead of an `OnInterface`/`OnClass` ternary. {S}
- **H.4 Precise types** (x13; 77683, 80923): `AppView<?>` only in shared D8/R8 code; R8-only code takes `AppView<? extends AppInfoWithClassHierarchy>`/`<AppInfoWithLiveness>`. Use `ReferenceTypeElement`, read-only `MethodConversionOptions`, and `Consumer<? super T>`. {N}
- **H.5 Reuse APIs; no near-duplicates** (x18; 136742, 92805): `LazyCfCode` already answers `isCfCode()`/`asCfCode()`; use `getR8PartialR8SubCompilationOptions()`, `ParameterRemovalUtils.canRemoveUnusedParameter`, `getKeepInfo(x).isMinificationAllowed(o)`, `DefaultUseRegistry` (no empty overrides), `DexMethodSignatureSet`. {S}
- **H.7 Hierarchy traversals** (x7; 103920, 124362): use `traverseSuperClasses(..)` with `TraversalContinuation.breakIf(..)` instead of chained `isSubtype`, plus `traverseTransitiveSubclasses`, `forEachImmediateSuperClassMatching` and `shouldBreak()`. Cache per type and stop at non-program classes. {S}
- **H.8 Lens helpers** (x8; 86160, 82480): `asNonIdentityLens()` instead of `isIdentityLens()` + cast, `find`/`findPrevious`, `CustomLensCodeRewriter` passed to the constructor, `EMPTY_FIELD_MAP`/`EMPTY_TYPE_MAP`, a single `RewrittenPrototypeDescription.create`. {N}
- **H.9 Placement** (x10; 100380, 117800): library modeling in a `StatelessLibraryMethodModelCollection` (`LibraryMemberOptimizer`); niche reflection in its own Enqueuer analysis (`EnqueuerMockitoAnalysis`); feature code out of `R8.java` into a self-gating `run(..)`; results on `AppView`, not `InternalOptions`. {N}

### I. IR and passes

- **I.1 Invoke/value helpers** (x13; 113061, 129621): `getFirstArgument()`, `getReceiver()`, `getFirstNonReceiverArgumentIndex()` instead of `inValues().get(i)` or `isInvokeStatic() ? 0 : 1`; `isDefinedByInstructionSatisfying`, `hasSingleUniqueUser()`. {N}
- **I.2 Composite predicates** (x18; 135620, 137840): `hasUniquePredecessor()`, `hasUniqueSuccessorWithUniquePredecessor()`, `getUniqueNormalSuccessor()`, `position.isSome()`, `isEqualsOrNotEquals()`, `isZeroTest()`, Guava `equivalent()` (not `doEquivalent`). {N}
- **I.3 Typed iteration** (x9; 85941, 109140): `code.<StaticPut>instructions(Instruction::isStaticPut)`, `it.<InvokeVirtual>nextUntil(..)`, `block.entry()`/`getNext()` when not mutating, `LinearFlowInstructionListIterator`. {N}
- **I.4 Create and replace IR** (x14; 86160, 134605): `InvokeStatic.builder()...setFreshOutValue(code, t).setSingleArgument(v).setPosition(p).build()`, `replaceCurrentInstruction(code.createIntConstant(..))`, `new AffectedValues()` + `replaceUsers(v, affected)` (not a `Set<Value>`). {S}
- **I.5 `CodeRewriterPass`** (x15; 79348, 116681): one pass per rewriting in `CodeRewriterPassCollection`, O(1) gate plus cheap filters (`code.metadata().mayHaveIf()`); non-null `getRewriterId()`; inherited `appView`, no shadowing; `hasChanged().isPossiblyTrue()`, not `isTrue()`. {S}
- **I.7 Desugaring/CF generation** (x13; 76400, 127580): override `compute` with one branch per kind returning `DesugarDescription.nothing()`. Rewrite types with `asTypeInstruction().withType(..)` and regenerate backports with `GenerateBackportMethods`. Gate sizes with `getEstimatedSizeForInliningIfLessThanOrEquals`. {N}

### J. Collections, Java/Guava/fastutil and compile-time cost

- **J.1 Identity collections** (x13; 124462, 131700): key `DexType`/`DexMethod`/`DexString`/`DexProgramClass`/`Value` with `IdentityHashMap`, `SetUtils.newIdentityHashSet(..)` or `WorkList.newIdentityWorkList()`; non-linked `ProgramMethodSet.create()` when order is irrelevant. {S}
- **J.2 fastutil primitives** (x12; 109581, 126280): `IntOpenHashSet`, `Reference2IntOpenHashMap`, `Int2ReferenceMap` instead of boxed collections. `defaultReturnValue(0)` + `getInt` instead of `getOrDefault(k, 0)`. Typed lambda parameters. No explicit default load factor. {S}
- **J.3 Hoist, cache, memoize** (x28; 134660, 107640): hoist loop-invariant allocations; compute `getAbstractValue`/resolution once; `static final Pattern`; `SupplierUtils.nonThreadSafeMemoize`; defensive copies only under `AssertionUtils.assertionsEnabled()`. {S}
- **J.4 Hot-path allocation** (x23; 115340, 124886): allocate lists on demand; no streams or per-call lambdas; `ListUtils.mapOrElse` to copy on change; `ListUtils.unmodifiableForTesting` in getters; `ThreadUtils.processItems` over classes/methods, not coarse groups. {S}
- **J.5 `hashCode`/`compareTo`** (x7; 127000, 133006): no `Objects.hash` in a hot `hashCode()`; use `31 * h + ...`, `ObjectUtils.hashLLL` or `field.hashCode()`. Use `Integer.compare`, not subtraction. {N}
- **J.6 `IRCode.blocks` is a `LinkedList`** (x6; 135481, 136021): no `get(i)` or `toArray`. Use `ListIterator`/`descendingIterator()`, and `code.removeBlocks(..)` instead of `blocks.removeAll` (which walks every block even for an empty argument). {S}
- **J.7 Quadratic scans** (x10; 97540, 89484): no `List.contains`/`values().contains` in loops (use a set or `WorkList.addIfNotSeen`), no linear member searches, no `classes()` scans for a known type (`definitionFor`). {B on whole-app paths, else S}
- **J.8 Cheap bailouts, lazy strings, hot defaults** (x14; 92462, 102200): check `code.metadata()` before fixpoints or IR building. Build messages only on failure (`NopWhyAreYouNotInliningReporter`). Don't make widely used overloads costlier; pass `AbstractValueSupplier.shallow()` only where needed. {S}
- **J.10 Java/Guava idioms** (x13; 114824, 86444): no `List.of`/`Set.of`/`Map.of` in compiler code; use `ImmutableList.of` (`ImmutableSet.of` is ordered). `IterableUtils.any`/`all`/`none` instead of `BooleanBox` accumulators; use the `compute`/`merge` result; `values().removeIf`. {N}
- **J.11 R8 utils** (x10; 124180, 98340): `ListUtils.map`/`filter`/`sorted`, `MapUtils.removeIf`, `ArrayUtils.initialize`, `ComparatorUtils.listComparator()`, `BitUtils.isAligned(4, x)`, and `WorkList` (`addIfNotSeen(Iterable)`, `removeSeen()`) instead of a `Deque` plus a seen set. No Guava duplicates. {N}

### K. Options, Timing, assertions, exceptions, diagnostics

- **K.1 Options** (x24; 107220, 120981): flags in feature options (`InlinerOptions`), WIP flags in `TestingOptions`; `getTestingOptions()` (not `options.testing`); derive enablement (`isOptimizing()`) instead of `enableX = true`; `SystemPropertyUtils.parseSystemPropertyOrDefault`, not `System.getProperty(..) != null`. {S}
- **K.2 VM-bug and version predicates** (x6; 79822, 92534): `canHave<Symptom>Bug()` built on `canHaveBugPresentUntilExclusive`. Tests use those or `parameters.canUse...()`, not `getApiLevel().getLevel() > 35`. Compare with `isNewerThan`/`isDalvik()`, not equality chains. {S}
- **K.3 Timing** (x12; 85483, 100921): wrap new passes with `timing.time("X", () -> ...)` or try-with-resources (`Timing` is `AutoCloseable`). Balance begin/end. Forward the caller's `timing` (not `Timing.empty()`) and `threadTiming`. Order parameters `(..., ExecutorService, Timing)`. {S}
- **K.4 Assertions** (x26; 74580, 136021): call expensive checks as `assert verifyX(..);` with `verifyX` returning `true` (`assert code.assertBasicBlockNumbersReset();`). Assert impossible states rather than guarding them with `if`. Write `assert a; assert b;`, not `a && b`. {S}
- **K.5 Exceptions** (x14; 75210, 113240): `Unreachable("why")` for broken invariants and `Unimplemented` only for pending work, both from `com.android.tools.r8.utils.internal.exceptions` (not keepanno). Not `assert false; return x;`. Use `UncheckedIOException` and `ExceptionUtils.unwrapExecutionException`. {S}
- **K.6 Diagnostics and CLI** (x33; 74446, 136042): a message ending in `:` includes the value; real `Origin` (`CompilationError(msg, origin)`); internal anomalies are `assert`s, not warnings (break `-warningsaserrors`); register new parser flags in `getFlags()` (`ParseFlagInfoImpl`). {S}

### L. Public API (`@KeepForApi`)

- **L.1 No accidental public surface** (x15; 121501, 133006): new `public`/`protected` members on `*Command`, `*Command.Builder`, `Origin` and other `@KeepForApi` classes, or new types in `com.android.tools.r8.references`, become package-private or internal utils. Tests read the values they configured. {B}
- **L.2 No speculative API; annotate; no leaks** (x19; 123640, 110122): add API only with a concrete consumer and extend existing hooks. Every reachable type (nested `Builder` too) is `@KeepForApi`. Keep internals (`DexProgramClass`, `ClassNameMapper`) out of API and diagnostics; store `Origin` + `FieldReference`. {B}
- **L.4 Shape** (x19; 85720, 70372): `setX`/`getX` vs `addX`, `enableX()` or `setX(boolean)` (never no-arg `setX()`), static `builder()` with non-public constructor, typed parameters (`ClassReference`, `Path`). {S}
- **L.5 Javadoc, API tests, compatibility** (x29; 79842, 113500): `/** */` with true/false semantics; a public-API-only test in `CompilerApiTestCollection` (retrace: `RetraceApiTestCollection`); keep declared `throws`. {S}

### M. Tests: builders and keep rules

- **M.1 `testForR8(parameters)`/`testForD8(parameters)`** (x44; 128580, 112301): not `testForR8(parameters.getBackend())...setMinApi(parameters)`, `.applyIf(isDexRuntime(), b -> b.setMinApi(..))` or a bare `Backend.DEX`. The overload also applies partial-compilation modes. Use `addInnerClasses(getClass())`, not class lists. Exception: see 1.4. {S}
- **M.2 Keep-rule helpers** (x26; 108540, 91765): `addKeepMainRule`, `addKeepClassAndMembersRules`, `addKeepClassAndDefaultConstructor`, `addKeepEnumsRule`, `addKeepAttributeInnerClassesAndEnclosingMethod`, `addKeepRuntimeVisibleAnnotations`, `addDontWarn(Class...)` instead of raw strings (full list: examples.md). {S}
- **M.4 `applyIf`** (x13; 70933, 100381): use `applyIf(c, ifTrue, ifFalse)` instead of two complementary calls, or the flattened `applyIf(c1, a, c2, b, else)`. Standalone assertions use a plain `if`. Write `isDexRuntime()`, not `!isCfRuntime()`. {N}
- **M.5 Drop defaults** (x24; 122341, 104380): `.setIntermediate(false)`, unneeded `addDontOptimize`/`addDontShrink` (`addDontObfuscate`: see A.1), `allowDiagnosticInfoMessages(false)`, `setMode(RELEASE)` on R8. Prefer `.debug()`/`.release()` over `setMode`. {N}
- **M.6 Library setup** (x10; 77543, 86011): after `addLibraryClasses`, add `addDefaultRuntimeLibrary(parameters)`. Use `ToolHelper.getMostRecentAndroidJar()`, and `addLibraryProvider(JdkClassFileProvider.fromSystemJdk())` instead of stub jars. {S}
- **M.7 Annotations and helpers pin behavior** (x14; 74620, 76222): `.enableInliningAnnotations()` + `@NeverInline` (not `enableInlining = false`), `@NoVerticalClassMerging`, `@NoHorizontalClassMerging`, `@NoUnusedInterfaceRemoval`. Prefer `testForR8` over `testForR8Compat`. {S}

### N. Tests: parameters

- **N.1 Narrowest parameters** (x16; 79747, 132960): use `withDefaultDexRuntime()`, `withDexRuntimesAndAllApiLevels()` or `withApiLevel(B)` when the output doesn't vary. No `withAllApiLevelsAlsoForCf()` in DEX-only tests. Parsers and CLIs use `withNoneRuntime()`. Drop unreachable branches. {N}
- **N.2 ...but cover what ships** (x20; 116428, 107040): repros run `withAllRuntimesAndApiLevels()` (CF included). Desugaring uses `testForDesugaring(parameters)`. R8 tests add `.withPartialCompilation()`. Include all relevant `LibraryDesugaringSpecification`s. {S}
- **N.3 `TestParameters` helpers** (x14; 92700, 113340): `assumeDexRuntime()`, `assumeJvmTestParameters()`, `assumeR8TestParameters()`, `assumeNoPartialCompilation()` instead of `assumeTrue(..)`; `canUse*()` predicates on `TestParameters`. {N}
- **N.4 Declarations** (x15; 138962, 76860): `@Parameter(0) public TestParameters parameters;` instead of a constructor, `getTestParameters()`, `BooleanUtils.values()`, and valid `@Parameters(name = "{0}, minify:{1}")` indices. {N}

### O. Tests: CodeInspector, run results, diagnostics

- **O.1 Matchers** (x32; 108080, 134860): `assertThat(s, isPresent())`, `isAbsent()` (not `not(isPresent())`), `isPresentIf(b)`/`isAbsentIf(b)`, `notIf(m, c)`. Never `assertTrue(s.isPresent())` or `assertEquals(cond, m.isPresent())`. {S}
- **O.2 `CodeMatchers`** (x7; 94323, 133400): `invokesMethodWithName`, `invokesMethodWithHolderAndName`, `invokesMethodWithHolder(X.class)` instead of `streamInstructions()` scans. When N sites change, count them. {N}
- **O.3 Lookups and names** (x13; 95000, 73904): `uniqueMethodWithOriginalName`, `mainMethod()`, `getFinalName()` (never hardcoded minified names), `binaryName(Foo.class)` (not `"Outer$Main"`), `.compile().inspect(..)`, and `SyntheticItemsTestUtils` for synthetics. {S}
- **O.4 Run results** (x8; 138640, 139101): always assert output (`assertFailureWithErrorThatThrows(VerifyError.class)`, `assertSuccessWithEmptyOutput()`); `.inspect` after the output check; `assertEquals(expected, actual)` order; assert the count when looping over `allMethods()`. {S}
- **O.5 Merging** (x8; 74880, 134540): `addHorizontallyMergedClassesInspector`/`addVerticallyMergedClassesInspector` (`assertMergedInto`, `assertNoClassesMerged`), not presence checks. {S}
- **O.6 Diagnostics** (x11; 78064, 117463): `compileWithExpectedDiagnostics(d -> d.assertOnlyInfos().assertInfosMatch(diagnosticType(X.class)))`. `assert*Match` already checks the count, so no `assert*Count`. No `getInfos().get(i)` or stdout matching. {S}
- **O.7 JUnit** (x14; 124080, 137500): JUnit asserts, never Java `assert`, in tests and testbase. `assertThrows(X.class, ..)` instead of `@Test(expected)` or try/fail. `throws Exception`. `Box`/`BooleanBox` instead of `AtomicReference`/`boolean[]`. {S}

### P. Tests: structure and infrastructure

- **P.1 Minimal repros** (x16; 134604, 132960): remove options, rules and code the trigger doesn't need. Turn options that matter into parameters. {N}
- **P.2 Separate scenarios and the JVM reference** (x17; 138940, 75145): `@Test testJvm()` with `assumeJvmTestParameters(); testForJvm(parameters)`, not inside `if (isCfRuntime())`. The opposite configuration gets its own `@Test`. New syntax gets `ProguardConfigurationParserTest` coverage. {N}
- **P.4 Reuse infrastructure** (x27; 124942, 135266): existing `ToolHelper`/`TestBase` helpers; shared helpers in a `*TestBase`, not static imports from other tests; `memoizeFunction`/`memoizeBiFunction` instead of static caches. {N}
- **P.6 Files** (x15; 125721, 84642): `getResourceAsReadOnlyFile`/`getSourceFileForTestClassFromResources`, never `Paths.get("src/test/java/...")`. `Paths.get(ToolHelper.THIRD_PARTY_DIR, "a")`. A fresh `temp.newFolder()` per artifact. `CompilerDump.fromArchive`. {N}
- **P.7 Tests and flags** (x11; 93940, 80783): every new option or fix needs a witnessing test. No constant `boolean useLir = true;` locals, and no `testing.forceX` that existing configurations already cover. Turn flipped defaults into `assertTrue(options.flag)`. {S}

### Q. Tools, build and generated code

- **Q.1 Generated code** (x9; 113981, 88201): regenerate (`GenerateBackportMethods`, `GenerateVarHandleMethods`, `KeepItemAnnotationGenerator`) and commit the output. Fix the generator, never the output. Commit LUCI configs regenerated with `main.star`. {B}
- **Q.2 Python tools** (x38; 112420, 139220): argparse `type=int`, `store_true` without `None` default; remove unused options; `main()` returns the exit code; `subprocess.run(cmd, check=True)`; helpers in `utils.py`; binaries as `.sha1` only. {N}
- **Q.3 Gradle/build** (x28; 120522, 119280): one source of truth (`libs.versions.toml`, synced with `create_local_maven_with_dependencies.py`); actions must not capture `Project`; remove dead dependencies with their `DependenciesPlugin.kt` entry. {S}

## 3. Comment style

Terse R8 reviewer voice: 1-2 sentences, often just the replacement in backticks; `Nit:` prefix for nits; no praise, restating or hedging; give the reason only when not obvious (cost, coverage, API surface). Real (lightly adapted) comments:

- ``Nit: `isIdenticalTo` -- and remove `@SuppressWarnings` if possible`` (G.1/E.5, CL 135268)
- ``Nit: `if (factory.objectType.isNotIdenticalTo(clazz.getSuperType())) {` `` (G.1, CL 131060)
- ``Remove `toString()` and use `DexString#startsWith` `` (G.2, CL 134900)
- ``Nit: Use `testForR8(parameters)` and remove `.setMinApi(parameters)` `` (M.1, CL 139684)
- `` `getTestingOptions()` `` (K.1, CL 139684) / `` `getR8PartialR8SubCompilationOptions()` `` (H.5, CL 136742)
- ``Nit: `!fallthrough.hasUniquePredecessor() || exit.getPosition().isSome()` `` (I.2, CL 135620)
- ``Please add a bug: `// TODO(b/449961100): Should not fail with VerifyError when only public members are kept.` `` (B.3)
- ``Use `Bug: b/291923475`; without `b/` the bug is not linked.`` (C.2)

## 4. Input / Output Contract

## 4. Reporting rules

- Anchor every finding on a line in the **new** version of the file that the CL adds or modifies (or the closest added line for issues caused by a deletion). Do not report on untouched code unless the CL's change makes that code wrong, and then anchor on the changed line.
- Report only `high` or `medium` confidence findings; verify each against the repository source tree before reporting. Drop anything you cannot substantiate with concrete code.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only issues that belong to this lens; the other lenses cover the rest.
