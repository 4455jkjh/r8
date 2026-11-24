---
name: r8-cl-review-invariants
description: >-
  R8/D8 Gerrit CL review lens for R8 compiler, IR/LIR/CF/DEX, and pipeline
  invariants (SSA, catch handlers, positions, types, GraphLens, Enqueuer,
  KeepInfo, resolution, synthetics, desugaring, class merging, IRConverter
  waves, D8 vs R8 vs R8 partial). Not for style nits or generic logic/test bugs
  (covered by r8-cl-review-nits and r8-cl-review-correctness).
---

# R8 CL Review: Compiler & IR Invariants

Find code that is valid Java but breaks R8's IR, LIR, CF/DEX backend,
`GraphLens`, `AppView`, or pipeline invariants. Starred rules (`*`) have
before/after examples in [references/examples.md](references/examples.md).

Out of scope: naming, `isIdenticalTo`, imports, formatting, test-builder idioms
(`r8-cl-review-nits`); generic logic/null/off-by-one bugs, plain-Java
concurrency, build speed, and test hygiene (`r8-cl-review-correctness`).

## 1. Review Procedure

### 1.1 Scope & Re-review

1. Read the commit message and any provided open threads (never duplicate them).
   Note whether this is a repro CL (asserts current behavior with `TODO(b/...)`)
   or a fix CL. On re-review, review only the interdiff, code it invalidates,
   and `/COMMIT_MSG`.
2. Jump to the relevant checklist sections by area:

   | Area / Package | Sections |
   | --- | --- |
   | IR core & passes (`ir/code`, `ir/optimize`, `ir/conversion/passes`) | A–F |
   | LIR & lenses (`lightir`, `graph/lens`, `*GraphLens`, `*TreeFixer`) | G |
   | Converter & pipeline (`IRConverter`, `PrimaryR8IRConverter`, `CodeRewriterPassCollection`, `R8`) | H |
   | Regalloc & backends (`ir/regalloc`, `dex`, `cf`) | I |
   | Tree shaking & KeepInfo (`shaking`) | J |
   | Resolution, dispatch, rebinding, access | K |
   | Class merging, enum/number unboxing, arg propagation, inliner | L |
   | Desugaring, synthetics, profiles (`ir/desugar`, `synthesis`, `profile`) | M |
   | Kotlin metadata, attributes, mapping (`kotlin`, `naming`, `retrace`) | N |

### 1.2 Verification

- **When repository files are readable**: read the surrounding code in `src/`—
  the invariant-establishing helper (`isConsistentSSA`, `consistentCatchHandlers`,
  `instructionTypeCanThrow`, `getAliasedValue`, the `GraphLens` subclass,
  `KeepInfo`), pipeline placement (`R8.java`, `CodeRewriterPassCollection`,
  `MethodProcessor` kind), overrides of changed IR/lens methods, and D8/R8-partial
  reachability. If a check needs a repo-wide scan you cannot run, mark
  `[Needs parent grep: <symbol>]` in internal evidence when running as a
  subagent—or drop the claim if running standalone without a parent orchestrator.
- **When running diff-only (no repository tools)**: flag only invariant
  violations directly visible in the changed lines; drop claims that depend on
  unseen callers or pipeline ordering.
- Construct a concrete failing scenario (phi operand, try/catch, `synchronized`,
  long/double, debug mode, kept/library/classpath/merged class, second pass with
  `Assume`s). No concrete failing scenario -> drop it.

### 1.3 Severity & Confidence

- **blocking**: miscompilation, `VerifyError`/linkage error, crash on valid
  input, unsound optimization, lens double-application or missed rewriting, or
  keep-rule violation.
- **should_fix**: relies on an unasserted invariant that can break under
  refactoring, conservative bail-out that misses real code (e.g. bailing out on
  `hasCatchHandlers()`), wrong pipeline placement that loses optimization, or a
  missing test for a risky compiler mode directly affected by the change
  (try/catch for B.1, D8 cf-to-cf for H.3, merged/minified for G, R8 partial for H.8).
- **Confidence**: `high` = traced in code with a concrete failing input;
  `medium` = rule applies and reachability looks plausible, though a caller may
  guard it (trace callers before reporting; if a caller already guarantees it,
  drop it).
- Do not emit `nit`-severity findings from this lens.

### 1.4 Do NOT Flag

- `isConstNumber()` without `getAliasedValue()`: `ConstNumber` outputs never get
  `Assume` users.
- Missing handler logic for blocks ending in `Return` or mid-block `Throw`
  scans (blocks with handlers only end in `Goto`/`Throw`, and `Throw` is always
  `block.exit()`).
- Missing `DEBUGPOS` handling in passes that only run when optimizing.
- Workarounds for first- vs second-pass IR differences: code stays in LIR between
  passes.
- Subtype fast paths assuming library classes never extend program classes, or
  not special-casing library enums (`DexClass.isEffectivelyFinal` already treats
  enums as final).
- Transitive walks over synthetic contexts (contexts are already flattened).
- Classpath code being invisible to R8 (expected; R8 partial handles it) or
  `-if` soft-pinning only running in round one (expected).
- `IdentityHashMap` keyed by `DexType`/`DexMethod`/`DexField`/`DexProto`/
  `DexString` (all canonical; only `DexTypeList` is not).
- Gaps explicitly deferred in the commit message with `TODO(b/...)` and a test,
  or cleanups on release-branch cherry-picks.

## 2. Checklist

### A. SSA Values, Phis, Aliases & Users

- **A.1\* Phi has no `getDefinition()`**: never chain on `getDefinition()` of
  a value that may be a phi—including after `getAliasedValue()`. Use
  `isDefinedByInstructionSatisfying(...)`, `getDefinitionOrNull(pred)`, or guard
  with `isPhi()` on that exact value.
- **A.2\* Check every user kind before removal/replacement**: `aliasedUsers()`
  returns instruction users only. Check `hasPhiUsers()`, `hasDebugUsers()`, and
  phi users of any `Assume` out-value. Clear debug users (`removeDebugValue`)
  and run `DeadCodeRemover` so replaced instructions actually die.
- **A.3 Follow `Assume` & `CheckCast` in user walks**: user walks over arguments
  or invoke results must traverse `Assume` (including chained `Assume`s from the
  second pass) and `CheckCast` out-values. Check nullability on the value itself,
  identity on the alias root; "may be `this`" means `getAliasedValue().isThis()`
  or a phi that may merge `this` (class types only).
- **A.4 Value & constant identity**: compare `Value`s with `==` (`getNumber()`
  is only a print ID). IR constants are not canonical—key dedup caches on
  `DexString`/`DexType`. Equivalence proofs (`BasicBlockBehavioralSubsumption`)
  must not look through differing `Assume`s.
- **A.5 Phis**:
  - Inflow facts depend on the specific edge (`phi.getOperand(preds.indexOf(pred))`);
    rewrite the i-th operand by index rather than `replaceValue`.
  - Recursive phi walks need a seen set; loop phis may remain live after the
    loop; trivial-phi replacements must be materializable.
- **A.6 `Assume` invariants**: match `Assume` by `getFirstOperand()`, never
  `origin()`; enforce invariants in `Assume#verifyInstruction`; never attach
  `Assume` to null or local-bearing values; remove unused `Assume` users
  explicitly (`removeOrReplaceByDebugLocalRead()` must not remove other
  instructions).
- **A.7 Don't assume javac/kotlinc shapes**: don't assume a single debug user or
  fixed constructor arity (arg 0 of a constructor `InvokeDirect` is the receiver);
  bail out on plain `ConstString` when expecting `DexItemBasedConstString`;
  `isConstant()` also matches `ResourceConstNumber`, so check `isConstNumber()`
  before `asConstNumber()`.

### B. Catch Handlers & Exceptional Edges

- **B.1\* Adding/expanding possibly-throwing instructions**: a block with catch
  handlers may contain at most one throwing instruction. Use
  `addPossiblyThrowingInstructionsToPossiblyThrowingBlock(instructions, options)`
  or `replaceCurrentInstruction`—never `it.add(...)` of a second throwing
  instruction, and do not bail out on `hasCatchHandlers()`. Test inside try/catch.
- **B.2\* Exceptional edges are implicit uses**: in backward (post-RA) liveness,
  union handler live-ins at every `instructionTypeCanThrow()` instruction in a
  covered block. Never hoist or share instructions across an exceptional edge
  (`!pred.hasCatchSuccessor(block)`).
- **B.3\* Splitting after a throwing instruction**: use
  `splitCopyCatchHandlers(...)` + `blocks.previousUntil(split)`. Copy only
  handlers whose live-ins are already defined at the split, and never drop
  handlers when `code.metadata().mayHaveMonitorInstruction()` (skips `monitor-exit`).
- **B.4 Handler structure**: a block with handlers must keep a throwing
  instruction (otherwise call `unlinkCatchHandler()`); the entry block never has
  handlers; handler blocks need not start with `MoveException` and may also be
  reached via normal edges.
- **B.5 New-array + puts -> filled-new-array/fill-array-data**: keep the
  `hasCatchHandlers()` bail-out because a handler can observe the partially
  initialized array (and `new-array` can OOM).
- **B.6 Guard equivalence & API limits**: compare handlers guard-by-guard
  (`hasEquivalentCatchHandlers`); never share `move-exception` across guards
  below API 29; detached values (`!hasBlock()`) are ineligible around handlers;
  vertical class merging can introduce duplicate guards.

### C. Blocks, CFG, Iteration & `IRCode` Consistency

- **C.1\* Clean up the CFG you break**: after unlinking edges, call
  `removeUnreachableBlocks()` (pruning detached values from `AffectedValues`),
  `narrowingWithAssumeRemoval`, and `removeRedundantBlocks()`; iterate to a
  fixpoint if cleanup re-enables blocks; end with `assert code.isConsistentSSA(appView)`
  outside `CodeRewriterPass` (`CodeRewriterPass#run` already asserts it on
  `HAS_CHANGED`). When queueing `blocksToRemove`, skip queued blocks during
  iteration and call `code.removeBlocks(...)` once.
- **C.2 Block-number indexing**: arrays indexed by `block.getNumber()` require
  `assert code.assertBasicBlockNumbersReset();` with no block additions or
  removals in between.
- **C.3 Dominance & linear walks**: `DominatorChecker` shortcuts must stay in
  the subgraph; linear multi-block walks require
  `hasUniqueSuccessorWithUniquePredecessor()`; dominance is block-level (order
  within a block via `getPrev()`/`getNext()`).
- **C.4 R8 iterator invalidation**: after a block split or helper that mutates
  blocks, update the exact `BasicBlockIterator` / `InstructionListIterator`
  actually in use (or recreate it).
- **C.5 `If` & switches**: pick `If` successors with `If.targetFromCondition(...)`;
  `If` never compares `long` operands directly (`Cmp` is required); late
  successor replacement must turn ≤2-successor switches into `Goto`/`If`.
- **C.6 Assert by-construction handler invariants**: blocks with handlers only
  end in `Goto`/`Throw` and `Throw` is always `block.exit()`; assert these
  instead of adding dead bail-out branches.

### D. Positions & Debug Info

- **D.1\* Positions on new instructions**: new throwing or materializing
  instructions must take the originating instruction's position via
  `setPosition(position, options)`—never `Position.none()`.
- **D.2 Moving/sharing positions**: check `identicalPosition`; debug mode
  requires exact match, while release mode only lets non-throwing instructions
  adopt another position; `identicalNonValueParts` overrides must agree.
- **D.3 Debug mode preservation**: never fold local-bearing values or reuse
  field loads in debug mode; track debug liveness via `canBeDeadCode`, not
  side effects.
- **D.4 Locals & IR building**: attach local info only to the final out-value;
  splits must recompute locals-at-entry across `DebugLocalsChange`s; return
  blocks may have debug phis; signature changes must update debug-info arity;
  `buildIR` must not mutate method state.
- **D.5 Debug-info encoding**: apply each debug event before reading pc/line;
  only synthetic line 0 is a preamble; keep `DexParser` faithful (convert in a
  separate pass); gate native-pc encoding on `allowDiscardingResidualDebugInfo(method)`.

### E. Types, Nullability & Abstract Values

- **E.1 Out-value types match `evaluate`**: new field-read/invoke out-values
  start maybe-null (attach non-null facts via `Assume`); always-non-null
  replacements must retype via `replaceCurrentInstruction(..., affectedValues)`.
- **E.2 Type propagation order**: run type propagation on affected values before
  `AssumeInserter` or assume removal; never lower a static type to a dynamic one.
- **E.3 Class types & nullability**: check `getType().isClassType()` before
  `toDexType`/`asClassType`/receiver resolution; `isAlwaysNull(appView)` is not
  the same as `isNullType()`; read nullability from `getType().nullability()`,
  not the abstract value.
- **E.4 Sub-int & joins**: sub-int SSA values have `TypeElement.getInt()`;
  `knownToBeBoolean()` values are not guaranteed to be strictly 0/1 at runtime;
  `TypeElement.join` of unrelated classes is `Object` in D8 but a precise LUB in R8.
- **E.5\* Abstract values & static defaults**:
  - `DexEncodedField#getStaticValue()` returns `DexValue.defaultForType` when
    implicit: guard with `hasExplicitStaticValue()`, and never fold static
    values of `ClassKind.LIBRARY` classes (device values may differ).
  - Rewrite abstract values on type changes (`rewrittenWithLens(appView, newType, lens, codeLens)`);
    use exact Java casts on raw bits for numeric conversions; inside
    `getAbstractValue(appView, context, supplier)`, recurse through `supplier`.

### F. Side Effects, `canThrow`, Class Init & Library Models

- **F.1 Conservative `canThrow`**: missing type/target info means an instruction
  may throw or have side effects (including `new-array` of missing/inaccessible
  types). Keep `instructionTypeCanThrow()` as a conservative kind-level bound;
  put operand-specific reasoning in `instructionInstanceCanThrow(appView, context)`.
- **F.2\* Volatile reads are memory barriers**: volatile reads are side-effecting
  unless `AssumeInfoCollection.isSideEffectFree`, must not participate in value
  propagation, and must never be finalized by `FieldFinalizer` (`final volatile`
  is illegal).
- **F.3 Library models & pinned members**: library models may list only
  non-throwing, state-free methods; non-final library methods can have program
  overrides (and unmodeled library `<init>` may dispatch to them); pinned
  members are opaque.
- **F.4 Class initialization & outlining**: D8 may treat only the context class
  and its direct superclass as initialized. Moving, staticizing, or outlining
  must preserve `<clinit>` order (`InitClass`) and receiver NPEs; outline an
  allocation only together with all of its users. Do not move or drop
  `toString()`-calling operations across side effects.
- **F.5 Load/store elimination**: constructor `readSet()` blocks only the read
  fields; a throwing instruction's normal successor still observes its facts.
- **F.6 Native/abstract & JVM errors**: native/abstract targets have no
  traceable code; preserve JVM error semantics and precedence (`IAE` before
  `ICCE`), and never "fix" input that fails on the JVM.

### G. `GraphLens`, LIR & Lens Code Rewriting

- **G.1\* Pass the right `codeLens` & stop at it**: pass the lens the item is
  already rewritten to (identity for items `LensCodeRewriter` never touches;
  predecessor lens for code rewritten right before its own lens; never `null`).
  Recursive lens methods stop at `this == codeLens`. `DexWritableCode.writeDex`
  expects original references.
- **G.2 `NonIdentityGraphLens` step methods**: `getNextMethodSignature`,
  `getPreviousMethodSignature`, `getNextClassType`, and `getNextType` describe
  only their own single step; call `getNextType` (not `lookupType`) inside lens
  methods.
- **G.3\* Stateful lens rewriting**: rewrite from the stored `appliedGraphLens`
  to `appView.graphLens()`, then update `appliedGraphLens`. Cached lenses must
  skip `isClearCodeRewritingLens()` and `isMemberRebindingIdentityLens()`.
- **G.4\* `rewrittenWithLens` on maps**: build a fresh map (never mutate in
  place), rewrite `DexItem`s nested inside values even when the key is unchanged,
  and merge colliding keys.
- **G.5 AppView side tables**: key by references and implement both
  `rewrittenWithLens` and `withoutPrunedItems(PrunedItems)`; never mix original
  and residual references (`getOriginalTypes(type)`).
- **G.6 `LensCodeRewriter` boundaries**: `LensCodeRewriter` runs before
  reference-inspecting passes (fix missed rewriting there, not in Enqueuer);
  code synthesized by a lens pass is already post-lens; static
  `DexItemBasedValueString`s are rewritten with code.
- **G.7 Non-rebound refs & LIR lens rewrites**: non-rebound references reach
  lens rewriters (`lookupPrototypeChangesForMethodDefinition` assumes rebound);
  after member rebinding, `graphLens()` is never identity. `LirLensCodeRewriter`
  must update invoke type and interface bit after (interface) merging; one
  constant-pool `DexMethod` can map differently per invoke type.
- **G.8 LIR phases**:
  - Use phase-correct `MethodConversionOptions` (`forPreLirPhase`, `forLirPhase`,
    `forPostLirPhase`, `forD8`).
  - Convert to LIR only when no pending lenses exist; let
    `LirConverter.rewriteLirWithLens` install `ClearCodeRewritingGraphLens`
    (never manually).
  - Because code stays in LIR between passes, guard `asCfCode()` with `isCfCode()`.

### H. Pass Placement, Waves, `MethodProcessor` & Modes

- **H.1 Shared D8/R8 `AppView` access**: never call `withClassHierarchy()` or
  `withLiveness()` unconditionally in shared code; D8 has a hierarchy only when
  desugaring, and `enableWholeProgramOptimizations()` does not imply liveness.
- **H.2 `CodeRewriterPass` registration & context**: register passes in
  `CodeRewriterPassCollection` (`CodeRewriterPass<AppInfo>` when D8-runnable);
  don't assume a specific `MethodProcessor` (`OneTimeMethodProcessor` also calls
  hooks) or reprocessing; derive contexts from the holder rather than `code.context()`.
- **H.3\* D8 cf-to-cf builds no IR**: keep CF desugarings for D8 cf-to-cf. Check
  "a later lowering exists" with `isGeneratingDex() || enableWholeProgramOptimizations()`;
  predicates allowing invoke-custom in CF output need `&& !isCfDesugaring()`.
- **H.4 Force-inline & constructors**: force-inline is mandatory (fix scheduling,
  don't skip `isProcessedConcurrently`); constructor inlining needs `hasWaves()`
  and `unsetFinalFlagFromInlinedFinalFields`.
- **H.5 Synthesis window**: passes that synthesize classes/methods may run only
  in primary processing and never after `SyntheticItems.isFinalized()`.
- **H.6 Waves & feedback**:
  - Results must be schedule-independent across concurrent waves; peek
    unpublished feedback only for the current method.
  - After primary waves, `assert feedback.noUpdatesLeft()` and use
    `getSimpleFeedback()`; recompute inlining constraints after mutating code;
    don't force reprocessing (`TrivialFieldAccessReprocessor`); cross-wave state
    must survive pruned methods.
- **H.7 `Code` kinds & post-RA passes**: handle every `Code` kind (`LirCode`,
  `DefaultInstanceInitializerCode`) or throw `Unimplemented`; use
  `convertToThrowNullMethod` during/after IR and `ThrowNullCode.get()` before;
  `FinalizerRewriterPass` is post-RA non-SSA; DEX-only passes check
  `isGeneratingDex()`; CFG workarounds rerun at the end of `IRToDexFinalizer`.
- **H.8 D8-in-R8-partial**: gate new passes in `DEFAULT_D8_IN_R8_PARTIAL_OPTIONS`;
  sub-compilations must not redo outer work, and classpath is part of the app.

### I. Register Allocation, CF Frames, DEX Constraints & VM Workarounds

- **I.1\* Post-RA inserted instructions**: newly inserted post-RA instructions
  have number `-1`, which breaks split-interval lookup—wrap physical registers
  in `FixedRegisterValue`.
- **I.2 Post-RA liveness & retries**: treat wide pairs `(r, r+1)` as jointly
  live; remove only non-throwing, side-effect-free instructions post-RA; a
  `CheckCast` without out-value retypes its input register; allocator retries
  must reset all live-interval state with a fallback.
- **I.3 Arguments & registers**: `Argument`s are a prefix of the entry block
  (insert after them); wide args may straddle v15; consecutive arguments do not
  imply consecutive registers unless pinned.
- **I.4\* CF stack & wide locals**: advance local indices by
  `getRequiredRegisters()`, count wide types in max stack/locals, never emit
  `Swap`/`Dup` on long/double (use a fresh local), and balance unused return
  values with `Pop`/`Pop2`.
- **I.5 Output constraints**: one label per `CfNew`; avoid branches requiring
  new `CfFrame`s; respect `requiredCfVersionForConstClassInstructions()`; check
  64K limits via `canIncreaseBytecodeSize()`; gate DEX instruction forms on
  computed predicates (`enableConstString20()`); assert `DexBuilder` pending
  state is flushed.
- **I.6 VM workarounds**: gate on `isGeneratingDex()` + `InternalOptions.canHave...Bug()`;
  merging/sharing passes must consult `Return.shouldOnlyMergeIdenticalReturnValues`
  and `canHaveIncorrectJoinForArrayOfInterfacesBug()`; re-check monitors on each
  inlining step.

### J. `AppView`, Enqueuer, `KeepInfo` & Tree Shaking

- **J.1\* Incremental Enqueuer extensions**: use callbacks
  (`processNewlyInstantiatedClass`, `NewlyLiveCodeEnqueuerAnalysis`) and the
  worklist, never `buildIR` or full rescans in `notifyFixpoint`. Dispatch-dependent
  work iterates instantiated classes, not all live classes.
- **J.2 Tree-shaking rounds & rebuilds**: preserve analyses across rounds via
  `getDependentMinimumKeepInfo()`; keep per-round state in the extension; never
  hardcode round order with `Unreachable`; app flags don't survive a rebuilt
  `DexApplication`.
- **J.3 Precise `KeepInfo`**:
  - Pick the exact bit (`disallowInlining` ≠ `disallowClassInlining`; finality
    under `disallowOptimization`); joiners must disallow something; never set
    `disallowShrinking` just for traced items.
  - Keep info applies only to live items; express conditional restrictions via
    dependent minimum keep info; compare `KeepInfo` by value.
- **J.4 Pruned items & lifetime**: check removed methods via
  `PrunedItems.isRemoved(DexMethod)` and never carry removed sets across later
  lenses. `getKeepInfo()` asserts `hasKeepInfo()`; keep incremental subtyping
  info up to date and never retain pre-shrinking `ImmediateAppSubtypingInfo` /
  `ImmediateProgramSubtypingInfo` or root sets.
- **J.5 Missing classes, desugaring & root set**: report missing classes lazily
  from tracing only (not attributes/library signatures); gate desugaring
  synthesis on liveness and re-defer unsatisfied work. `RootSetBuilder` /
  `AnnotationRemover` may run without a `ProguardConfiguration`; preserve dalvik
  system annotations when stripping; give out-of-line synthesized helpers keep
  info (`registerCompilerSynthesizedMethod`); framework anchor types must be
  library or pinned.

### K. Resolution, Dispatch, Classpath, Rebinding & Access

- **K.1\* Multi-outcome resolution**: `MethodResolutionResult` can be single,
  multiple, failed, or array-clone—use `forEachMethodResolutionResult`,
  null-check `getProgramField()`, and check `isSingleFieldResolutionResult()`
  before `getResolvedField()`.
- **K.2 Dispatch vs resolution**: reason about dispatch (`lookupSingleTarget`),
  not just resolution. Library-semantic rewrites need exact receivers and no
  program overrides; match library methods by signature + receiver
  (`objectMembers.toString.match(...)`); handle `InvokeCustom`/lambda call sites.
- **K.3 Classpath & missing types**: minifier reserves classpath names; code
  owners may be classpath (assert `!isLibraryClass()`, not `isProgramClass()`).
  Referenced types may be library, classpath, or missing
  (`contextIndependentDefinitionFor` asserts presence).
- **K.4 Hierarchy & per-item context**: private methods never override
  (`isNonPrivateVirtualMethod`); prefer `DexClassAndMethod.getHolder()` over
  `definitionFor(holderType)`; resolve in the method's context; check per-class
  origin (`originatesFromDexResource`) over app-wide flags.
- **K.5 Access, bridges & rebinding**: don't publicize items with failed
  resolutions (preserves `IAE`); publicizing package-private overrides changes
  dispatch; push bottom-up state into all supertypes. Emit bridges/rebinding
  only on accessible program holders, never above possibly-missing library
  classes on Dalvik; interface `invoke-super` must target an immediate
  superinterface.
- **K.6 Renaming & signature changes**: skip kept methods and library overrides,
  reserve pinned + library/classpath signatures first, rename override families
  consistently, and never target unresolved names (`isLibraryMethodOverride` is
  `UNKNOWN` for non-virtual methods).

### L. Class Merging, Enum Unboxing, Argument Propagation & Inlining

- **L.1 Tree fixer attributes**: every tree fixer must rewrite
  `PermittedSubclasses`, `NestMembers`, and `InnerClasses`.
- **L.2 API-safe merged signatures**: check `AndroidApiLevelUtils.isApiSafeForReference`
  and widen merged parameter/return types to the nearest API-safe supertype.
- **L.3 Merge preconditions**: precompute only merge-invariant facts; re-check
  resolution, `<clinit>`, and `invoke-super` before each merge; merging adds
  interfaces; disallow the merge rather than copying keep info between methods.
- **L.4\* Enum unboxing**:
  - Representation: `null` -> `0`, ordinal `i` -> `i + 1`; subclasses are
    assignable to the root only, never to siblings (`enumDataMap.isAssignableTo(from, to)`
    or `representativeType(from).isIdenticalTo(to)`).
  - `equals`/`compareTo` unbox only for the same candidate; lens covers
    subclasses; single-implementation abstract methods still use `mapToDispatch`;
    Kotlin enum subclasses may lack `ACC_ENUM`.
- **L.5 Argument propagation & field facts**: partial propagation drops
  `hasInFlow()`; join split states; array states track nullability only; seed
  with bottom; one computation-tree node per op. Read indirect writes via
  `isWrittenIndirectly()`; "fully tracked" excludes reflective access; writes
  may occur outside initializers.
- **L.6 Class inliner & `NewArrayFilled`**: class inlining must account for
  every escaping receiver use; `NewArrayFilled` elements must be provable
  subtypes of the array component type, and new IR forms must be handled by
  every relevant matcher.

### M. Desugaring, Synthetics & Profiles

- **M.1 Desugaring & `DesugarDescription`**: synthesized helpers are themselves
  desugared (e.g. backports inside synthesized code). Compute one
  `DesugarDescription` for both `needsDesugaring` and rewriting; never
  synthesize inside `scan()`.
- **M.2 `SyntheticItems` queries & `ApplicationReaderMap`**: queries must cover
  pending + committed synthetics; assert `!hasPendingSyntheticClasses()` when
  deriving from `classes()`; use `collectSyntheticsFromContext`, not name
  prefixes. Prefer write-time rewriting over global `ApplicationReaderMap`; use
  `computedMinApiLevel()` for synthesized API levels; pre-desugaring
  `UseRegistry` overrides `registerInvokeSpecial`; detect DEX input via
  `dexCodeOnInput()`.
- **M.3\* Globally mergeable synthetics**: never reference non-public members
  from a globally mergeable synthetic kind (causes `IllegalAccessError` when
  merged across packages). Synthetic equivalence must respect distinct
  requirements (extension SDKs, sharded merges).
- **M.4 ART profiles**: copy flags from the original context
  (`addMethodIfContextIsInProfile` per requesting context), follow lens holder
  changes, cover all synthetics, and commit profile additions once.

### N. Kotlin Metadata, Attributes & Mapping

- **N.1 Kotlin metadata**: combine `rewritten` flags across all components,
  handle every `KmAnnotationArgument` kind, include receiver + mask in
  `$default` signatures, look up via the lens, and survive `-dontshrink`.
- **N.2 `EnclosingMethod` & `InnerClasses`**: ART requires consistent
  `InnerClasses` whenever `EnclosingMethod` is present; check
  `hasEnclosingMethod()`; inlining the enclosing method invalidates it.
- **N.3 Mapping invariants**: omit compiler-synthesized info for
  `isGlobalSyntheticClass`; compose outlines via the composed range; last mapped
  range is outermost; re-dexing shifts PCs.

## 3. Writing Clean Review Comments

Write 1–3 short plain-English sentences: state what concrete input or mode
breaks, what fails (NPE, `VerifyError`, miscompile), and the concrete fix.

- **Keep internal traces out of comments.** Never put `file://` links, `src/`
  paths, call-graph chains, checklist IDs (`A.1`, `G.3`), or CL numbers in the
  comment—keep those in internal `Evidence:` only. Leave any optional
  `reference` output field empty rather than emitting rule codes.
- **Minimal code references.** Name only the 1–3 symbols or helpers the author
  needs to call or fix; explain the compiler invariant in plain words instead of
  compressed shorthand. Skip praise and restating the diff.

Examples of clean comments:
- "This will NPE if the object value comes from a phi. Use `isDefinedByInstructionSatisfying(...)` instead of chaining `getDefinition()`."
- "Adding a second throwing instruction here can violate the at-most-one-throwing-instruction invariant for blocks with catch handlers. Insert them together with `addPossiblyThrowingInstructionsToPossiblyThrowingBlock(...)`."
- "New throwing instructions shouldn't use `Position.none()`, as it breaks stack traces and retracing. Copy the originating instruction's position via `setPosition(position, options)`."
- "This looks like it may double-apply lenses to the enum unboxer. Should it rewrite from `appliedGraphLens` instead?"
- "Can this NPE in R8 since the pass runs after conversion to LIR (so `asCfCode()` may fail)?"
- "D8 cf-to-cf compilations desugar ahead of later dexing, so we can't emit invoke-custom there. Guard this with `&& !isCfDesugaring()`."
- "If this block is inside a `synchronized` block and throws, dropping the catch handler skips `monitor-exit` and can cause lock verification errors."

## 4. Reporting Rules

- Anchor every finding on a line added or modified by the CL (or the closest
  added line for issues caused by a deletion).
- Report only `high` or `medium` confidence findings; drop anything you cannot
  substantiate.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only compiler/IR/pipeline invariant issues; the
  other lenses cover API/style nits and generic correctness.
