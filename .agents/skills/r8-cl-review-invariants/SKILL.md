---
name: r8-cl-review-invariants
description: >-
  R8/D8 Gerrit CL review lens for wrong assumptions about R8 IR/LIR/CF/DEX and pipeline invariants (SSA, catch
  handlers, positions, types, GraphLens, Enqueuer/KeepInfo, resolution, synthetics, desugaring, class merging,
  IRConverter waves, D8 vs R8 vs R8 partial). Use for the compiler/IR invariants pass of an R8 review;
  not for style nits or generic logic/test bugs (covered by r8-cl-review-nits and r8-cl-review-correctness).
---

# R8 CL review lens: invariants (`r8-cl-review-invariants`)

Find code that is valid Java but wrong because of how the R8 IR, LIR, CF/DEX backends, lenses, AppView or
pipeline actually behave. Rules are distilled from 8,242 human review threads on the last 10,000 R8 CLs plus
~2,000 Fix/Revert/Reland subjects (`xN` = times reviewers raised it; numbers are r8-review CLs; "fix" = a
Fix/Revert CL for that bug class). Starred rules (\*) have before/after code in
[references/examples.md](references/examples.md).

Out of scope: naming, `isIdenticalTo`, imports, formatting, test-builder idioms (**nits** lens); generic
logic/null/off-by-one bugs, plain-Java concurrency, build speed, test hygiene (**correctness** lens).

## 1. Review procedure

1. **Orient.** Read the change metadata, commit message, relation chain, and existing comments (open threads must not be
   duplicated). Note whether this is a repro CL (test asserts current behavior with `TODO(b/...)`) or a fix
   CL. On re-review read the interdiff first.
2. **Classify hunks** by area and jump to the section: IR core/passes (`ir/code`, `ir/optimize`,
   `ir/conversion/passes`) → A-F; LIR/lenses (`lightir`, `graph/lens`, `*GraphLens`, `*TreeFixer`) → G;
   converter/pipeline (`IRConverter`, `PrimaryR8IRConverter`, `CodeRewriterPassCollection`, `R8.java`) → H;
   `ir/regalloc`, `dex`, `cf` → I; `shaking` → J; resolution/rebinding/access → K; `classmerging`, enum/number
   unboxing, `argumentpropagation`, inliner → L; `ir/desugar`, `synthesis`, `profile` → M; `kotlin`, `naming`,
   `retrace` → N.
3. **Read context in `src/`**, not just the hunk:
   - the invariant-establishing code relied on (`IRCode.isConsistentSSA`, `BasicBlock.consistentCatchHandlers`,
     `Instruction.instructionTypeCanThrow`, `Value.getAliasedValue`, the `GraphLens` subclass, `KeepInfo` bits);
   - callers and pipeline position: `R8.java` phase order, `CodeRewriterPassCollection` order and D8/R8 gating,
     which `MethodProcessor` (`PrimaryMethodProcessor`/`PostMethodProcessor`/`OneTimeMethodProcessor`) runs it;
   - all overrides of a changed IR/lens method (`evaluate`, `identicalNonValueParts`, `rewrittenWithLens`,
     `getNextMethodSignature`);
   - D8 reachability: D8 release/debug, D8 cf-to-cf (`isCfDesugaring()`), the D8 part of R8 partial.
4. **Construct the failing input** for each suspect: phi operand, try/catch around the rewritten instruction,
   `synchronized` method, long/double, debug mode, kept member, library override, classpath class, merged or
   repackaged class, second optimization pass with `Assume`s. No concrete input from code you read → drop it.
5. **Tests**: flag only a missing variant that is the most likely to break (try/catch for B.1, D8 cf-to-cf for
   H.3, merged/minified for G, R8 partial for H.9) as `should_fix`.
6. **Severity**: `blocking` = miscompile, VerifyError/linkage error, crash on valid input, unsound
   optimization, nondeterminism, lens double/missed application, keep-rule violation. `should_fix` = relies on
   an invariant without asserting it, reviewer-rejected conservative bail-out (e.g. on `hasCatchHandlers()`),
   wrong pipeline placement that only loses optimization, missing risky-variant test. `nit` = handling an
   impossible case, a bail-out that should be an assert.
7. **Confidence**: `high` = traced in `src/` and you can name the failing input; `medium` = rule applies but a
   caller may already guarantee it (say which caller).

### Calibration: do NOT flag (accepted author pushback)

- `isConstNumber()` without `getAliasedValue()`: const-number outputs never have `Assume` users (A.3).
- Missing handler logic for blocks ending in `Return` or mid-block `Throw` scans (C.6).
- Missing `DEBUGPOS` handling in passes that only run when optimizing (D.3).
- Workarounds for first/second-pass IR differences: code is kept as LIR between passes (G.9, CL 95422).
- Subtype fast paths assuming library classes never extend program classes (K.5).
- Transitive walks over synthetic contexts are unnecessary: contexts are flattened (M.4).
- Classpath code invisible to R8 (WAI; R8 partial handles it, CL 114243); `-if` soft-pinning only in round
  one (WAI, CL 108320).
- Special-casing library enums as final: `DexClass.isEffectivelyFinal` already treats enums as final (K.5).
- `IdentityHashMap` keyed by `DexType`/`DexMethod`/`DexField`/`DexProto`/`DexString` (canonical); only
  `DexTypeList` is not (J.9).
- Gaps the commit message explicitly defers with `TODO(b/...)` and a test.

## 2. Checklist

### A. SSA values, phis, aliases, users

- **A.1\*** Never chain on `getDefinition()` of a value that may be a phi, incl. after `getAliasedValue()`;
  use `isDefinedByInstructionSatisfying(...)`/`getDefinitionOrNull(pred)` or check `isPhi()` on that exact
  value (x11; CLs 85340, 85941, 103461, 112120, 114824, 118220, 129100, 130260).
- **A.2\*** Before removing/replacing a value check `hasPhiUsers()`, `hasDebugUsers()` and phi users of every
  `Assume` out-value (`aliasedUsers()` is instruction users only); make replaced instructions actually die
  (clear debug users with `removeDebugValue`, run `DeadCodeRemover`) (x7; CLs 72741, 72842, 97540, 108441,
  108843, 109480, 109520; fixes 79347-79353, 94442-94561).
- **A.3** `ConstNumber` outputs never have `Assume` users: `value.isConstNumber()` directly (x2; CLs 129100, 129520).
- **A.4** User walks over arguments/invoke results follow `Assume` and `CheckCast` out-values (assume→assume
  too; the second pass adds Assumes). Nullability on the value itself, identity on the alias root; "may be
  `this`" = `getAliasedValue().isThis()` or a phi that may merge `this`, class types only (x7; CLs 109140,
  123200, 126420, 127021, 128222, 132780, 134081; fixes 82742-82783).
- **A.5** Identity: compare `Value`s with `==` (`getNumber()` is a print id); IR constants are not canonical, so
  key dedup caches on `DexString`/`DexType`; equivalence proofs (`BasicBlockBehavioralSubsumption`) must not
  look through differing `Assume`s (x3; CLs 72741, 83162, 101480; fixes 80202, 101480-101505).
- **A.6** Phis: facts depend on the inflow edge (`phi.getOperand(preds.indexOf(pred))`); recursive walks need a
  seen set; rewrite the i-th operand by index, not `replaceValue`; loop phis may be used after the loop;
  trivial-phi replacements must be materializable (x3; CLs 72741, 86160, 92383; fixes 83667, 91240-91261,
  93220-93542, 137300).
- **A.7** `Assume` invariants: match by `getFirstOperand()`, never `origin()`; assert in `Assume#verifyInstruction`
  rather than work around; no Assume on null values or local-bearing values; remove unused Assume users
  explicitly (`removeOrReplaceByDebugLocalRead()` must not remove other instructions) (x3; CLs 97540, 118220,
  130280; fixes 80900, 90143).
- **A.8** Don't assert javac/kotlinc-shaped input (one debug user, ctor arity; arg 0 of a ctor `InvokeDirect` is
  the receiver); bail out on plain `ConstString` where `DexItemBasedConstString` is expected; `isConstant()` ⊋
  `ConstNumber` (`ResourceConstNumber`) so guard `asConstNumber()` (x5; CLs 108360, 109520, 115661, 120000, 124626).

### B. Catch handlers and exceptional edges

- **B.1\*** Adding/expanding possibly-throwing instructions: `addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
  instructions, options)` or `replaceCurrentInstruction`; not `it.add(...)`, not bailing out on
  `hasCatchHandlers()`; test inside try/catch (x7; CLs 84760, 114824, 116620, 117880, 122420, 122463, 122660;
  fixes 98500, 117762, 117880, 119140).
- **B.2\*** Exceptional edges are implicit uses: backward (post-RA) liveness unions handler live-ins at every
  `instructionTypeCanThrow()` instruction in a block with handlers; never hoist/share across an exceptional
  edge (`!pred.hasCatchSuccessor(block)`) (x5; CLs 79381, 135840, 136021, 136341, 137541; fixes 82262-82342).
- **B.3\*** Splitting after a throwing instruction: `splitCopyCatchHandlers(...)` + `blocks.previousUntil(split)`;
  copy only handlers whose live-ins are defined at the split; never drop handlers when
  `code.metadata().mayHaveMonitorInstruction()` (x4; CLs 95141, 97540, 113940, 135820).
- **B.4** Handler structure: a block with handlers must keep a throwing instruction (else `unlinkCatchHandler()`);
  the entry block never has handlers; handler blocks need not start with `MoveException` and may be reached
  normally (x3; CLs 72741, 81440, 136141; fix 98500).
- **B.5** New-array + puts → filled-new-array/fill-array-data keeps the `hasCatchHandlers()` bail-out (handler
  observes the partial array; `new-array` can OOM) (x4; CLs 72840, 73805, 73807, 74181).
- **B.6** Compare handlers guard-by-guard (`hasEquivalentCatchHandlers`); no shared `move-exception` across guards
  below API 29 (b/120164595); detached values (`!hasBlock()`) are ineligible around handlers; vertical merging
  can create duplicate guards (x5; CLs 88560, 88580, 88744, 102702, 138441; fixes 94441-94563, 102941).

### C. Blocks, CFG, iteration, IRCode consistency

- **C.1\*** A pass cleans up what it breaks: `removeUnreachableBlocks()` (pruning detached values from
  `AffectedValues`), `narrowingWithAssumeRemoval`, `removeRedundantBlocks()`, iterate to a fixpoint if cleanup
  re-enables blocks, then `assert code.isConsistentSSA(appView)`; skip blocks queued for removal, then one
  `code.removeBlocks(...)` (x9; CLs 88641, 88645, 112780, 121802, 136042, 137581, 137840, 138760; fixes 102928, 103002).
- **C.2** Arrays indexed by `block.getNumber()`: `assert code.assertBasicBlockNumbersReset();` and no block
  add/remove in between (x3; CLs 136021, 136141, 136220).
- **C.3** `DominatorChecker` shortcuts stay in the subgraph; linear walks need `hasUniqueSuccessorWithUniquePredecessor()`;
  dominance is block-level, order within a block by `getPrev()`/`getNext()` (x4; CLs 86121, 100170, 100660,
  117780; fixes 101142-101163).
- **C.4** Iterators stay valid: update the one actually used after a split; capture `next` before removal;
  recreate after helpers mutate the block (x4; CLs 89921, 97540, 98480, 134564).
- **C.5** Choose `If` successors with `If.targetFromCondition(...)`; `If` operands are never long (`Cmp`); late
  successor replacement must turn ≤2-successor switches into `Goto`/`If` (x4; CLs 79348, 97540, 136200, 137820).
- **C.6** Blocks with handlers end in `Goto`/`Throw` (never `Return`/`If`); `Throw` is always `block.exit()`;
  assert by-construction facts instead of bailing out (x5; CLs 97060, 97320, 135521, 135620, 136021).
- **C.7** Honor `getTraversalContinuation().shouldBreak()` after each `registerUse`; use the specific callback
  (e.g. `registerNewUnboxedEnumInstance`) (x1; CL 70821).

### D. Positions and debug info

- **D.1\*** New instructions take the originating instruction's position (never `Position.none()` on a
  throwing instruction), on every materializing instruction, via `setPosition(position, options)` (x4; CLs
  83064, 84760, 114824, 118381; fixes 71141, 102926).
- **D.2** Moving/sharing: `identicalPosition`; exact match in debug; in release only non-throwing instructions
  may adopt another position; `identicalNonValueParts` overrides agree (x5; CLs 82602, 135620, 136000, 137540, 137660).
- **D.3** Debug mode: no folding of local-bearing values, no field-load reuse; keep debug liveness in
  `canBeDeadCode`, not side effects. Optimizing passes never see debug positions (x5; CLs 87242, 87300,
  120961, 122240, 137820).
- **D.4** Local info only on the final out-value; splits recompute locals-at-entry from `DebugLocalsChange`s;
  return blocks may have debug phis; signature changes fix debug-info arity; `buildIR` must not mutate method
  state (x6; CLs 78520, 79661, 89601, 112120, 113240, 135820).
- **D.5** Debug-info encoding: apply the event before reading pc/line; only synthetic line 0 is a preamble;
  `DexParser` stays faithful (conversion is a separate pass); native-pc encoding gates on
  `allowDiscardingResidualDebugInfo(method)` (x10; CLs 73723, 74481, 74540, 78760, 124886, 124910, 124943,
  125680, 127721, 129140; fixes 74821, 128180, 130440).

### E. Types, nullability, abstract values

- **E.1** Out-value types match `evaluate`: new field-read/invoke out-values are maybe-null, facts via
  `Assume`; always-non-null replacements retype through `replaceCurrentInstruction(..., affectedValues)` (x4;
  CLs 86160, 112800, 114824, 118381; fixes 81340, 104561).
- **E.2** Run type propagation on affected values before `AssumeInserter`/assume removal; never lower a static
  type to a dynamic one (x3; CLs 81290, 81334, 82600; fixes 78286-78306, 81480, 81500, 121060).
- **E.3** `getType().isClassType()` before `toDexType`/`asClassType`/receiver resolution; `isAlwaysNull(appView)`
  ≠ `isNullType()`; nullability from `getType().nullability()`, not the abstract value (x5; CLs 97540, 116082,
  117780, 118501, 123400).
- **E.4** Sub-int SSA values are `TypeElement.getInt()`; `knownToBeBoolean()` values may not be 0/1;
  `TypeElement.join` of unrelated classes is `Object` in D8, a precise LUB in R8 (x3; CLs 90622, 108801, 127700).
- **E.5** Abstract values follow type changes (`rewrittenWithLens(appView, newType, lens, codeLens)`); number
  conversions use exact Java casts on raw bits; inside `getAbstractValue(appView, context, supplier)` recurse
  via the supplier (x4; CLs 84701, 103461, 114160, 114202).
- **E.6\*** `getStaticValue()` returns `DexValue.defaultForType` when implicit: check `hasExplicitStaticValue()`;
  never fold library static values (x3; CLs 114980, 115061, 131060).

### F. Side effects, canThrow, class init, library models

- **F.1** Missing info ⇒ may have side effects/throw (incl. new-array of missing/inaccessible types);
  `instructionTypeCanThrow()` is a conservative kind-level bound, operand reasoning goes in
  `instructionInstanceCanThrow(appView, context)` (x5; CLs 82180, 88240, 88281, 102822, 113240).
- **F.2\*** Volatile reads are barriers: side-effecting unless `AssumeInfoCollection.isSideEffectFree`, no
  value propagation, never finalized by `FieldFinalizer` (x1; CL 134660).
- **F.3** Library models list only non-throwing, state-free methods; non-final library methods may have program
  overrides; unmodeled library `<init>` may dispatch to them; pinned members are opaque (x8; CLs 81262, 86024,
  91700, 121680, 129560, 129561, 133500).
- **F.4** Class init: D8 may treat only the context class and its direct super as initialized; moving,
  staticizing or outlining keeps `<clinit>` order (`InitClass`) and receiver NPE; outline an allocation only
  with all its users (x4; CLs 70822, 93800, 97562, 111880; fixes 97562-97620, 132440-132604).
- **F.5** Don't move/drop `toString()`-calling operations across side effects (x2; CLs 113240, 117780).
- **F.6** Load/store elimination: constructor `readSet()` blocks only those fields; a throwing instruction's
  success path still yields facts (x2; CLs 92763, 134660; fixes 93569-93604).
- **F.7** Native/abstract targets have no traceable code; keep JVM error semantics and precedence (IAE before
  ICCE); never "fix" input that fails on the JVM (x3; CLs 62983, 73488, 92524).

### G. GraphLens, LIR, lens code rewriting

- **G.1\*** Pass the right `codeLens` (identity for items `LensCodeRewriter` never touches; the predecessor lens
  for code rewritten just before its own lens; never `null`); recursion stops at `this == codeLens`;
  `DexWritableCode.writeDex` gets original references (x3; CLs 77143, 77542, 86605; fixes 71241, 75680-75775, 88481).
- **G.2** `NonIdentityGraphLens` step methods (`getNextMethodSignature`, `getPreviousMethodSignature`,
  `getNextClassType`, `getNextType`) describe only their own step; use `getNextType`, not `lookupType`, inside
  lens methods (x4; CLs 80000, 80601, 88744, 89181; revert 81441).
- **G.3\*** Stateful `rewriteWithLens` goes from the stored applied lens to `appView.graphLens()`, then updates it;
  cached lenses skip `isClearCodeRewritingLens()`/`isMemberRebindingIdentityLens()` (x3; CLs 106382, 132100).
- **G.4\*** `rewrittenWithLens` on maps: fresh map, rewrite DexItems nested in values, merge colliding keys,
  deterministic order (x3; CLs 86024, 131700, 132100; fixes 132740-132940).
- **G.5** AppView side tables key by references and implement `rewrittenWithLens` + `withoutPrunedItems(PrunedItems)`;
  never mix original and residual references (`getOriginalTypes(type)`) (x7; CLs 85340, 85483, 86024, 86141,
  89460, 89484, 134140; fix 80464).
- **G.6** `LensCodeRewriter` runs before reference-inspecting passes; fix missed rewriting there, not in the
  Enqueuer; code a lens pass synthesizes is already post-lens; static `DexItemBasedValueString`s are rewritten
  with code (x5; CLs 77143, 81333, 81335, 89500, 98761; fix 113580).
- **G.7** Non-rebound references reach lens rewriters (`lookupPrototypeChangesForMethodDefinition` assumes
  rebound); after member rebinding `graphLens()` is never identity (x4; CLs 82063, 86104, 86160, 86244).
- **G.8** `LirLensCodeRewriter` rewrites invoke type/interface bit after (interface) merging; one constant-pool
  `DexMethod` may map per invoke type (x4; CLs 88542, 88744, 93800, 94447; fixes 87220, 88486).
- **G.9** LIR phases: phase-correct `MethodConversionOptions` (`forPreLirPhase`/`forLirPhase`/`forPostLirPhase`/
  `forD8`); convert to LIR only with no pending lenses; `LirConverter.rewriteLirWithLens` installs
  `ClearCodeRewritingGraphLens` (never manually); code stays LIR between passes, so guard `asCfCode()` with
  `isCfCode()` (x9; CLs 70682, 80804, 80923, 81300, 81360, 89681, 95422, 101641, 126021; fixes 122141-122385).

### H. Pass placement, IRConverter waves, MethodProcessor, D8/R8/partial

- **H.1** Shared code must not dereference `withClassHierarchy()`/`withLiveness()` unconditionally; D8 has a
  hierarchy only for desugaring; `enableWholeProgramOptimizations()` ≠ liveness (x5; CLs 53228, 87520, 92920, 97540, 116082).
- **H.2** Register passes in `CodeRewriterPassCollection`, runnable in D8 (`CodeRewriterPass<AppInfo>`); don't
  restrict to or assume the processor (`OneTimeMethodProcessor` calls hooks too) or reprocessing; derive
  contexts from the holder, not `code.context()` (x6; CLs 72740, 97540, 109140, 113240, 117900, 132100).
- **H.3\*** D8 cf-to-cf builds no IR: keep CF desugarings; "later lowering exists" =
  `isGeneratingDex() || enableWholeProgramOptimizations()`; indy-for-CF predicates need `!isCfDesugaring()`
  (x3; CLs 113240, 114960, 115780; fixes 53846, 99042-99101).
- **H.4** Results are schedule-independent under concurrent waves (x1; CL 136462; fixes 95881, 107960-108046,
  132780-133002, 138680).
- **H.5** Force-inline is mandatory (fix scheduling, don't skip `isProcessedConcurrently`); constructor inlining
  needs `hasWaves()` + `unsetFinalFlagFromInlinedFinalFields` (x3; CLs 87560, 121801, 135681; fixes 80061, 87580).
- **H.6** Synthesizing passes run only in primary processing and never after `SyntheticItems.isFinalized()` (x2; CLs 88460, 88483).
- **H.7** Feedback: after primary waves `assert feedback.noUpdatesLeft()` and use `getSimpleFeedback()`; peek
  unpublished info only for the current method; recompute inlining constraints after mutating code; don't
  force reprocessing (`TrivialFieldAccessReprocessor`); cross-wave state survives pruned methods (x8; CLs
  83807, 84562, 85340, 89940, 92304, 120840, 122082, 132302).
- **H.8** Handle every `Code` kind (`LirCode`, `DefaultInstanceInitializerCode`) or throw `Unimplemented`;
  `convertToThrowNullMethod` during/after IR, `ThrowNullCode.get()` before; `FinalizerRewriterPass` is post-RA
  non-SSA; DEX-only passes check `isGeneratingDex()`; CFG workarounds rerun at the end of `IRToDexFinalizer`
  (x6; CLs 84042, 89482, 122981, 131060, 135340, 135820).
- **H.9** Gate new passes for D8-in-R8-partial (`DEFAULT_D8_IN_R8_PARTIAL_OPTIONS`); sub-compilations don't
  redo outer work; classpath is part of the app (x7; CLs 102340, 102361, 102382, 104262, 104662, 112961, 113240).

### I. Register allocation, CF frames, DEX constraints, VM workarounds

- **I.1\*** Post-RA inserted instructions have number -1: wrap registers in `FixedRegisterValue` (x1; CL 135820).
- **I.2** Wide pairs (r, r+1) are jointly live; post-RA removal only of non-throwing side-effect-free
  instructions; a `CheckCast` without out-value retypes its input register; retries reset all live-interval
  state with a fallback (x7; CLs 97383, 97723, 98125, 99224, 135840, 136021, 137421; fixes 99087, 105702-105724,
  130980-131500).
- **I.3** `Argument`s are a prefix of the entry block (insert after them); wide args may straddle v15;
  consecutive arguments ≠ consecutive registers unless pinned (x4; CLs 86160, 97383, 97403, 97468; fix 97380).
- **I.4\*** CF stack/locals: advance by `getRequiredRegisters()`, wide types in max stack/locals, no `Swap`/`Dup`
  of wide values, balanced stack (`Pop`/`Pop2`) (x6; CLs 75473, 77143, 77860, 98341, 102360, 102640).
- **I.5** Output constraints: one label per `CfNew`; avoid branches needing new `CfFrame`s;
  `requiredCfVersionForConstClassInstructions()`; 64K via `canIncreaseBytecodeSize()`; DEX forms gate on
  computed predicates (`enableConstString20()`); assert `DexBuilder` pending state flushed (x8; CLs 67503,
  91661, 91901, 114824, 126380, 129741, 132320, 139101).
- **I.6** VM workarounds gate on `isGeneratingDex()` + API via `InternalOptions.canHave...Bug()`; every
  merging/sharing pass consults `Return.shouldOnlyMergeIdenticalReturnValues`/`canHaveIncorrectJoinForArrayOfInterfacesBug()`;
  re-check monitors per inlining step (x7; CLs 73580, 73920, 121185, 135980, 137660, 138321, 139141).

### J. AppView, Enqueuer, KeepInfo, tree shaking

- **J.1\*** Enqueuer extensions use incremental callbacks (`processNewlyInstantiatedClass`,
  `NewlyLiveCodeEnqueuerAnalysis`) and the worklist, not `buildIR` or rescans in `notifyFixpoint`;
  dispatch-dependent work iterates instantiated classes (x6; CLs 89608, 99580, 100980, 103021, 106981, 124462).
- **J.2** Analyses hold across tree-shaking rounds (`getDependentMinimumKeepInfo()`); per-round data stays in the
  extension; no `Unreachable` encoding round order (x3; CLs 64781, 108320, 126903).
- **J.3** Precise KeepInfo bit (`disallowInlining` ≠ `disallowClassInlining`; finality under `disallowOptimization`);
  joiners must disallow something; no `disallowShrinking` for traced items; keep info only for live items;
  conditional restrictions via dependent minimum keep info; compare KeepInfo by value (x9; CLs 64781, 86440,
  96423, 99580, 100381, 101100, 108540).
- **J.4** Removed methods: `PrunedItems.isRemoved(DexMethod)`; never carry removed sets across later lenses (x3;
  CLs 77785, 77921, 88720; fix 80327).
- **J.5** `getKeepInfo()` asserts `hasKeepInfo()`; incremental subtyping info asserted up to date; never keep
  pre-shrinking `SubtypingInfo`/root set alive (x4; CLs 86602, 96061, 97540, 109362).
- **J.6** Missing classes are reported lazily from tracing only (not attributes/library signatures); first-round
  matching uses `definitionFor` (x4; CLs 33029, 80800, 82940, 89484).
- **J.7** Gate desugaring synthesis on liveness; re-defer unsatisfied deferred work (x5; CLs 75621, 75623, 75660, 75685, 91406).
- **J.8** `RootSetBuilder`/`AnnotationRemover` may run without a ProguardConfiguration; keep dalvik system
  annotations when stripping; out-of-line synthesized helpers need keep info (`registerCompilerSynthesizedMethod`);
  framework anchor types must be library or pinned (x5; CLs 91560, 116840, 124362, 128102, 129400; revert 128620).
- **J.9** `DexTypeList` is not canonical; app flags don't survive a rebuilt `DexApplication`; optional extensions
  may be null (`ResourceShrinkerEnqueuerExtension`) (x3; CLs 83671, 115981, 124462).

### K. Resolution, dispatch, program/library/classpath, rebinding, access

- **K.1\*** `MethodResolutionResult` is single/multiple/failed/array-clone: `forEachMethodResolutionResult`;
  null-check `getProgramField()`; `isSingleFieldResolutionResult()` before `getResolvedField()` (x6; CLs 72780,
  72860, 73843, 80680, 85941, 86024; fix 70521).
- **K.2** Reason about dispatch (`lookupSingleTarget`), not resolution; library-semantic rewrites need exact
  receivers and no program overrides; match library methods by signature + receiver
  (`objectMembers.toString.match(...)`); handle `InvokeCustom`/lambda call sites (x9; CLs 77683, 85340, 97540,
  98360, 100283, 111460, 111561, 113240, 118501; fixes 96721-96743).
- **K.3** Classpath exists: minifier reserves classpath names; code owners may be classpath; assert
  `!isLibraryClass()` not `isProgramClass()` (x5; CLs 92802, 103920, 104840, 104900, 105460).
- **K.4** Referenced types may be library/classpath/missing (keep-annotation predicates must match);
  `contextIndependentDefinitionFor` asserts presence (x4; CLs 93940, 97540, 118340, 119160).
- **K.5** Hierarchy facts: library classes never extend program classes (walks stop at `isLibraryClass()`);
  enums are effectively final; private methods never override (`isNonPrivateVirtualMethod`) (x5; CLs 77143,
  106860, 106861, 112120, 134540).
- **K.6** Per-item facts: `DexClassAndMethod.getHolder()` over `definitionFor(holderType)`; resolve in the method
  context; per-class origin (`originatesFromDexResource`) over app-wide flags (x4; CLs 77240, 77560, 86541, 92920).
- **K.7** Preserve IAE: don't publicize items with failed resolutions; publicizing package-private overrides
  changes dispatch; push bottom-up state into all supertypes (x5; CLs 80000, 80680, 87100, 87102; fixes 87100-87124).
- **K.8** Bridges/rebinding only on program holders with accessibility; not above possibly-missing library
  classes on Dalvik; interface invoke-super targets an immediate superinterface (x6; CLs 94323, 107040, 107880,
  126420, 127021, 134000; fixes 77461, 107060, 108200, 110381).
- **K.9** Renaming/signature changes skip kept methods and library overrides, reserve pinned + library/classpath
  signatures first, rename override families consistently, never target unresolved names;
  `isLibraryMethodOverride` is UNKNOWN for non-virtual (x8; CLs 69941, 77683, 77861, 80000, 85340, 85483, 86011,
  86444; fixes 128420-128526, 134800-134920).

### L. Class merging, enum unboxing, argument propagation, optimization info

- **L.1** Rewrite `PermittedSubclasses`/`NestMembers`/`InnerClasses` in every tree fixer (x4; CLs 80821, 80823, 81083, 81215).
- **L.2** Merged signatures API-safe (`AndroidApiLevelUtils.isApiSafeForReference`, widen to nearest safe super)
  (x4; CLs 81404, 81405, 90824, 135145; fixes 136760-136862).
- **L.3** Precompute only merge-invariant facts; re-check resolution/clinit/invoke-super before each merge;
  merging adds interfaces; disallow the merge rather than copy keep info between methods (x5; CLs 86015, 86104,
  87361, 106001, 135100; fixes 85725-85801, 86940, 126881).
- **L.4\*** Enum unboxing: assignability via `EnumDataMap.representativeType`; `equals`/`compareTo` only between
  the same candidate; null=0, ordinal+1; lens covers subclasses; one-implementation abstract methods still
  `mapToDispatch`; Kotlin subclasses may lack `ACC_ENUM` (x10; CLs 77143, 77201, 77222, 77683, 77684, 78921,
  83000, 86980, 89780; fixes 110580-110940, 124541-124625).
- **L.5** Argument propagation: partial propagation drops `hasInFlow()`; join split states; array states track
  only nullability; seed with bottom; a computation-tree node per op (x7; CLs 85483, 91080, 95022, 106584,
  137840, 138681, 138760).
- **L.6** Field facts: write facts from `isWrittenIndirectly()`; fully tracked excludes reflective access; writes
  may leave initializers (x4; CLs 90181, 92364, 106400, 106401; fixes 86560, 112021, 135060).
- **L.7** Class inliner sees every receiver use; filled-new-array elements must be provable subtypes; new IR
  forms (`NewArrayFilled`) handled by every matcher (x8; CLs 50225, 69943, 72840, 72842-72846, 72920).

### M. Desugaring, synthetics, profiles

- **M.1** Synthesized helpers are themselves desugared (backports in synthesized code) (x2; CLs 73980, 98341).
- **M.2** One `DesugarDescription` for `needsDesugaring` and the rewrite; never synthesize in `scan()` (x1; CL
  73980; fixes 76400, 76402, 77220).
- **M.3\*** Globally mergeable synthetics must not reference non-public members; sharing respects distinct
  requirements and sharded merges (x4; CLs 76083, 76685, 92023, 108083).
- **M.4** `SyntheticItems` queries cover pending + committed; assert `!hasPendingSyntheticClasses()` when
  deriving from `classes()`; use `collectSyntheticsFromContext`, not name prefixes; contexts are flattened (x4;
  CLs 75087, 76083, 90821, 100620).
- **M.5** `ApplicationReaderMap` rewriting is global (prefer write time); synthesized API levels use
  `computedMinApiLevel()`; pre-desugaring `UseRegistry` overrides `registerInvokeSpecial`; `dexCodeOnInput()`
  detects DEX input (x5; CLs 73980, 74662, 97360, 112800, 123300).
- **M.6** Profiles: flags from the original context (`addMethodIfContextIsInProfile` per requesting context),
  follow lens holder changes, cover all synthetics, commit additions once (x16; CLs 75267, 75335, 76002, 86104,
  107960, 117900, 131060; fix 83461).

### N. Kotlin metadata, attributes, mapping

- **N.1** Kotlin metadata: combine `rewritten` from all components, handle every `KmAnnotationArgument` kind,
  `$default` signatures include receiver + mask, look up via the lens, survive `-dontshrink` (x7; CLs 93431,
  93438, 93483, 93606, 106940, 111980; fixes 138840, 138841).
- **N.2** `EnclosingMethod` needs consistent `InnerClasses` on ART; check `hasEnclosingMethod()`; inlining the
  enclosing method invalidates it (x3; CLs 28820, 91082, 99161).
- **N.3** Mapping: no compiler-synthesized info for `isGlobalSyntheticClass`; compose outlines via the composed
  range; last mapped range is outermost; re-dexing shifts PCs (x6; CLs 71925, 75780, 80902, 81200, 81541,
  85537; fixes 83020-83345, 139760).

## 3. Comment style

Terse, names the invariant and the concrete fix, 1-2 sentences, no praise. Verbatim from the threads:

- "This will NPE if the object value is defined by a phi." + `isDefinedByInstructionSatisfying` snippet (A.1, CL 103461)
- "This may violate the at-most-one-throwing-instruction per block with catch handlers. I would recommend
  inserting all instructions at once using the `addPossiblyThrowingInstructionsToPossiblyThrowingBlock` helper." (B.1, CL 114824)
- "Non-throwing instruction types must have a position. This should use the position of the originating instruction." (D.1, CL 114824)
- "This logic should go into the `instructionInstanceCanThrow()` method instead, and then `instructionTypeCanThrow()`
  should continue to return true in the same cases as before." (F.1, CL 82180)
- "This seems to potentially be double applying each lens multiple times to the enum unboxer. Shouldn't this be `appliedGraphLens`?" (G.3, CL 132100)
- "Can't this yield a NullPointerException in R8 since this runs after we've converted to LIR?" (G.9, CL 126021)
- "D8 cf-to-cf compilations are normally used to desugar ... Adding a `&& !isCfDesugaring()` check here addresses this." (H.3, CL 113240)
- "It looks like this code may give lock verification errors? If the current block is wrapped in a synchronized
  block then if `block` raises an exception the `monitor-exit` should be run, but it is not?" (B.3, CL 135820)

## 4. Reporting rules

- Anchor every finding on a line in the **new** version of the file that the CL adds or modifies (or the closest added line for issues caused by a deletion). Do not report on untouched code unless the CL's change makes that code wrong, and then anchor on the changed line.
- Report only `high` or `medium` confidence findings; verify each against the repository source tree before reporting. Drop anything you cannot substantiate with concrete code.
- Do not duplicate an issue already raised in an unresolved review thread.
- Stay in your lane: report only issues that belong to this lens; the other lenses cover the rest.
