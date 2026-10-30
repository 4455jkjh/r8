<!-- disableFinding(LINE_OVER_80) -->

# `r8-cl-review-invariants`: detailed examples

Before/after code for the starred (\*) checklist items in [../SKILL.md](../SKILL.md), with the review threads
and Fix/Revert CLs they come from. API names are verified against the R8 checkout (2026-10-06). Code is
abridged; `...` marks omitted context.

---

## A.1 A value may be a phi: never chain on `getDefinition()`

Phis have no defining instruction. Pattern matchers that work on straight-line test code NPE as soon as a
merge point feeds the operand. This also applies to the result of `getAliasedValue()`, which can be a phi.

```java
// ❌ CL 103461 (CheckCast.getAbstractValue): NPE when object() is defined by a phi
if (object().getAliasedValue().getDefinition().isInvokeVirtual()) {
  InvokeVirtual invoke = object().getAliasedValue().getDefinition().asInvokeVirtual();
  ...
}

// ✅
Value aliasedValue = object().getAliasedValue();
if (aliasedValue.isDefinedByInstructionSatisfying(Instruction::isInvokeVirtual)) {
  InvokeVirtual invoke = aliasedValue.getDefinition().asInvokeVirtual();
  ...
}
// or, when only one shape matters:
InvokeVirtual invoke = aliasedValue.getDefinitionOrNull(Instruction::isInvokeVirtual);
```

Calibration (A.3): `ConstNumber` outputs never get `Assume` users, so `invoke.getFirstArgument().isConstNumber()`
is correct as is (CL 129100, reviewer: "`invoke.getFirstArgument().isConstNumber()`"). Don't ask for
`getAliasedValue()` there.

Thread: "This will NPE if the object value is defined by a phi." (CL 103461). Same class of bug: CLs 85340,
85941, 112120, 114824, 118220, 130260.

---

## A.2 Check every kind of user before removing or replacing a value

`aliasedUsers()` returns instruction users only. A value can stay live through a phi user, a debug user, or a
phi user of one of its `Assume` out-values.

```java
// ❌ counts only instruction users; a phi user keeps the value live (fixes 94442-94561)
if (value.aliasedUsers().size() == 1) {
  value.replaceUsers(replacement);
  value.definition.removeOrReplaceByDebugLocalRead();
}

// ✅
if (value.hasPhiUsers() || value.hasDebugUsers()) {
  return;
}
for (Instruction user : value.aliasedUsers()) {
  if (user.isAssume() && user.outValue().hasPhiUsers()) {
    return;
  }
}
...
```

Removing an instruction is not enough if debug users keep its value alive: clear them
(`Instruction.removeDebugValue`) and let `DeadCodeRemover` run, or the "removed" code survives and the
size win disappears (CLs 108441, 109480, 109520). Fixes 79347-79353 ("removal of live array-puts in presence of
Assume instruction") came from this.

---

## B.1 Inserting several possibly-throwing instructions

Invariant: a block covered by catch handlers holds **at most one** throwing instruction, and it is the one
that the exceptional edge leaves from. `it.add(...)` of a second throwing instruction breaks that. Bailing out
on `block.hasCatchHandlers()` is the other common mistake: try blocks and synchronized methods are common, so
the optimization then misses real code. Reviewers reject both.

```java
// ❌ CL 114824 (atomic field updater extender): two throwing instructions in one block with handlers
it.add(newStaticGet);
it.add(newInvoke);

// ❌ CL 122420 first patchset: gives up whenever handlers exist
if (block.hasCatchHandlers()) {
  return;
}

// ✅ the iterator splits blocks as needed and copies the handlers
it.previous();
it.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
    code, blocks, ImmutableList.of(newStaticGet, newInvoke), appView.options());
it.next();
// (IRCodeInstructionListIterator also has the overload (instructions, options), which keeps its own blocks)

// ✅ single replacement
it.replaceCurrentInstruction(newInvoke, affectedValues);
```

The iterator must come from a `BasicBlockIterator` you own for the whole pass. Building a new block iterator
per replacement is O(n²). Tests must put the rewritten instruction inside `try { ... } catch`.

Thread: "This may violate the at-most-one-throwing-instruction per block with catch handlers. I would recommend
inserting all instructions at once using the `addPossiblyThrowingInstructionsToPossiblyThrowingBlock` helper."
(CL 114824). Related fixes: 98500 "Ensure at most one throwing instruction in block with handlers", 117762 /
117880 "Don't optimize atomic updaters with ... under handler", 119140 "Fix crash in ServiceLoaderRewriter when
catch handlers exist".

---

## B.2 Exceptional edges are implicit uses (liveness, hoisting, sharing)

An instruction that can throw may jump to the handlers **before** writing its out register. Every register
live-in to a handler is therefore live at every throwing instruction of a covered block, even if a later
instruction in that block overwrites it.

```java
// ❌ CL 136021 (DeadRegisterStoreEliminator, backward pass): kills the out register even where a handler can read it
for (Instruction insn = block.exit(); insn != null; insn = insn.getPrev()) {
  if (!block.hasCatchHandlers()) {
    live &= ~outMask(insn);
  }
  live |= inMask(insn);
}

// ✅ kill, then re-add handler live-ins at every throwing instruction
for (Instruction insn = block.exit(); insn != null; insn = insn.getPrev()) {
  live &= ~outMask(insn);
  if (block.hasCatchHandlers() && insn.instructionTypeCanThrow()) {
    live |= catchLiveOutRegisters; // union of live-in of all catch successors
  }
  live |= inMask(insn);
}
assert entryBlockLiveIn.isEmpty();
```

The same reasoning forbids moving or sharing instructions into a predecessor across an exceptional edge:

```java
// ✅ precondition for hoisting `block`'s prefix into `pred`
pred.hasUniqueSuccessor() && !pred.hasCatchSuccessor(block) && !block.hasCatchHandlers()
```

Prefer block merging over hand-moving instruction prefixes. CLs 79381, 135840, 136341, 137541. Fixes
82262-82342 "Avoid conflating register sets at move-exception".

---

## B.3 Splitting a block to insert after a throwing instruction

```java
// ❌ plain split: the new block has no handlers, and one previous() mis-positions the
// block iterator when the split creates several blocks
BasicBlock split = it.split(code, blocks);
blocks.previous();

// ✅
BasicBlock split = it.splitCopyCatchHandlers(code, blocks, appView.options());
blocks.previousUntil(split);
blocks.next();
```

Two refinements from CL 135820 (`RuntimeWorkaroundCodeRewriter`, post-RA):

- Copy only handlers whose live-in registers are already defined at the split point. A handler that reads
  `v42` must not get an edge from a block that runs before `v42` is written: VerifyError.
- Never drop handlers in a synchronized region (`code.metadata().mayHaveMonitorInstruction()`), or the
  exceptional path skips `monitor-exit` (lock verification error).
  Thread: "It looks like this code may give lock verification errors? If the current block is wrapped in a
  synchronized block then if `block` raises an exception the `monitor-exit` should be run, but it is not?"

Also in that CL: `split.setLocalsAtEntry(block.getLocalsAtEntry())` is wrong if a `DebugLocalsChange` between
block entry and the split point ends a local (D.4). CLs 95141, 97540, 113940.

---

## C.1 A pass cleans up the CFG it breaks

Passes used to depend on another pass's unconditional cleanup (e.g. `AssertionsRewriter`), and broke once that
cleanup became lazy (CL 121802, fix "inadequate trivial phi removal in branch simplifier").

```java
// ❌ unlinks an If edge, leaves dead blocks and stale types behind
block.removeSuccessor(deadTarget);
return CodeRewriterResult.HAS_CHANGED;

// ✅
AffectedValues affectedValues = new AffectedValues();
...
if (unlinkedEdges) {               // gate on the actual mutation, not a proxy (CL 72741)
  code.removeUnreachableBlocks(affectedValues);   // also drops detached values from affectedValues
  affectedValues.narrowingWithAssumeRemoval(appView, code);
  code.removeRedundantBlocks();
}
assert code.isConsistentSSA(appView);
```

Type propagation must never see values detached by `removeUnreachableBlocks` (fix 88641 "Do not apply type
propagation to values detached from IR"). Reviewer suggestion in that CL:

```java
code.removeUnreachableBlocks(value -> {
  affectedPhis.remove(value);
  affectedValues.remove(value);
});
```

When collecting `blocksToRemove`, skip those blocks while iterating and call `code.removeBlocks(blocksToRemove)`
once (CL 112780). If cleanup can re-enable already visited blocks, iterate to a fixpoint or choose an order
that composes, e.g. `code.blocks.descendingIterator()` (CLs 136042, 137581).

---

## D.1 Positions on new instructions

```java
// ❌ CL 114824: synthetic/none position on code that can throw → wrong stack traces, broken retrace
ConstNumber c = code.createIntConstant(0);
c.setPosition(Position.syntheticNone());

// ✅ take the originating instruction's position
c.setPosition(invoke.getPosition(), appView.options());
```

- For `SingleValue.createMaterializingInstructions(...)`, set the position on every returned instruction and put
  the local info only on the last out-value (D.4).
- `newArrayEmpty.getPosition()`, not the position of the constant that used to be there (CL 84760).
- Prefer `setPosition(Position, InternalOptions)` to the one-arg overload, which skips mode-specific handling
  (CL 118381).

Thread: "Non-throwing instruction types must have a position. This should use the position of the originating
instruction. Ditto for the instructions below." (CL 114824). Fixes: 71141 "not emitting a synthetic position for
moved initializers", 102926 "inadequate construction of Position in newInlineePosition".

---

## E.6 `getStaticValue()` default-value trap

```java
// ❌ CL 131060: true for every static int field, even without a ConstantValue attribute
if (valueField == null || !(valueField.getStaticValue() instanceof DexValueInt)) {
  return false;
}
int wireValue = ((DexValueInt) valueField.getStaticValue()).getValue();

// ✅
if (valueField == null
    || !valueField.hasExplicitStaticValue()
    || !valueField.getStaticValue().isDexValueInt()) {
  return false;
}
int wireValue = valueField.getStaticValue().asDexValueInt().getValue();
```

`DexEncodedField.getStaticValue()` returns `DexValue.defaultForType(getReference().type)` when `staticValue ==
null`. Thread: "This returns `DexValue.defaultForType(getReference().type)` so for fields without a static value
it would be true that `getStaticValue() instanceof DexValueInt`. We should add `||
!valueField.hasExplicitStaticValue()` before this." Separately, never fold static values of `ClassKind.LIBRARY`
classes into constants: the device value can differ (CLs 114980, 115061).

---

## F.2 Volatile reads are memory barriers

From CL 134660 (`InstanceGet`/`StaticGet`, `FieldFinalizer`, `KeepMemberInfo`):

```java
// ✅ InstanceGet/StaticGet.instructionMayHaveSideEffects
if (resolvedField.getAccessFlags().isVolatile()
    && !appView.getAssumeInfoCollection().isSideEffectFree(resolvedField)) {
  return true; // removing the read drops an acquire barrier
}
```

- Value propagation stays off for volatile fields (`isValuePropagationAllowed`).
- Constructors that read volatile fields get `setMayHaveOtherSideEffectsThanInstanceFieldAssignments`.
- `FieldFinalizer` must never finalize a volatile field: `final volatile` is illegal. Reviewer, when the
  guard was removed: "This was here to guard against introducing `final volatile` fields which are not allowed."
- `-assumenosideeffects` intentionally overrides; say so in a comment.

---

## G.1 Always pass the right `codeLens`, and stop at it

Each lens lookup takes the lens the item is **already** rewritten to. Re-walking the whole unapplied chain
re-applies lenses (wrong reference), and `null` hides the problem.

```java
// ❌ CL 77542
state.rewrittenWithLens(appView, lens, null);

// ❌ EnumUnboxingRewriter: LensCodeRewriter already rewrote up to (not including) the enum unboxing lens
DexMethod m = appView.graphLens().lookupMethod(invoke.getInvokedMethod(), ...).getReference();

// ✅ only this lens's own step
DexMethod m = enumUnboxingLens.getNextMethodSignature(invoke.getInvokedMethod());

// ✅ recursive verification stops at the code lens and maps through the previous signature
@Override
public boolean isContextFreeForMethods(GraphLens codeLens) {
  if (this == codeLens) {
    return true;
  }
  return getPrevious().isContextFreeForMethods(codeLens);
}
```

- Items `LensCodeRewriter` never touches (e.g. `DexItemBasedConstString`) use `GraphLens.getIdentityLens()`.
- Code synthesized by a pass that installs a lens (e.g. `EnumUnboxingCfCodeProvider`) is already post-lens.
- In `DexWritableCode.writeDex` pass original references, since `DexInstruction.write(..., graphLens, codeLens,
  ...)` rewrites them. Only `collectIndexedItems` pre-applies lookups (CL 86605).

Threads (CL 77542): "Please remove this, we should not introduce new variants that don't take a code lens." /
"This should use the predecessor of the enum unboxing lens as code lens, or maybe simply use
`enumUnboxingLens.getNextMethodSignature(...)`". Fixes: 71241 "double-application of lens in construction of
member rebinding lens", 75680-75775 "double-lens application to field instructions in backend", 88481 "double
lens application in illegal access detector".

---

## G.3 Stateful lens rewriting starts from the last applied lens

```java
// ❌ CL 132100 (EnumUnboxer/Outliner state): rewrites from the full lens every time
info.rewriteWithLens(appView.graphLens(), appView.codeLens());

// ✅
info.rewriteWithLens(appView.graphLens(), appliedGraphLens);
appliedGraphLens = appView.graphLens();
// with ReprocessingOptimization, make the precondition explicit:
assert outlineCollection.getAppliedGraphLens() == appView.graphLens();
```

A cached lens can be pruned from the chain (parent pointers are re-linked), so cache the nearest lens that
survives (CL 106382):

```java
appliedGraphLens =
    appView.graphLens().asNonIdentityLens()
        .find(l -> !l.isClearCodeRewritingLens() && !l.isMemberRebindingIdentityLens());
```

Thread: "This seems to potentially be double applying each lens multiple times to the enum unboxer. Shouldn't this
be `appliedGraphLens`?" (CL 132100).

---

## G.4 `rewrittenWithLens` on maps

```java
// ❌ in-place put-then-remove deletes entries when A->B and B->C; value payloads stay stale;
// collisions overwrite
for (DexType type : new ArrayList<>(map.keySet())) {
  DexType rewritten = lens.lookupType(type);
  map.put(rewritten, map.get(type));
  map.remove(type);
}

// ✅ fresh map, rewrite nested references, merge collisions, deterministic iteration
Map<DexMethod, MethodBoxingStatus> rewritten = new LinkedHashMap<>();
map.forEach((method, status) -> {
  DexMethod newMethod = lens.getRenamedMethodSignature(method, codeLens);
  MethodBoxingStatus newStatus = status.rewrittenWithLens(lens, codeLens); // even if method is unchanged
  rewritten.merge(newMethod, newStatus, MethodBoxingStatus::merge);
});
```

CLs 86024, 131700, 132100. Nondeterministic iteration during lens rewriting is a recurring Fix family:
132740-132940 "Fix nondeterministic rewriting of KeepInfoCollection", 80401 "Avoid ConcurrentHashMap in lens
rewriting of minimum keep info".

---

## H.3 D8 cf-to-cf builds no IR

D8 cf-to-cf ("desugar") output is dexed later without desugaring. It must not contain invoke-custom or other
constructs that only a later IR lowering would remove.

```java
// ❌ CL 113240 first version: indy string concat allowed in D8 cf-to-cf desugaring
public boolean canUseInvokeCustomWithIndyStringConcat(CfVersion cfVersion) {
  return isGeneratingClassFiles() && cfVersion.isGreaterThanOrEqualTo(CfVersion.V9);
}

// ✅ InternalOptions today
public boolean canUseInvokeCustomWithIndyStringConcat(CfVersion cfVersion) {
  return isGeneratingClassFiles()
      && cfVersion.isGreaterThanOrEqualTo(CfVersion.V9)
      && !isCfDesugaring();
}
```

- "Will a later lowering exist?" is `isGeneratingDex() || appView.enableWholeProgramOptimizations()`, not
  `options.intermediate` (CL 115780).
- Keep CF desugarings such as `StringConcatInstructionDesugaring` for D8 cf-to-cf (CL 114960).
- Tests need `testD8CfToCf` and, with `parameters.assumeJvmTestParameters()`, a no-desugaring variant.

Thread: "D8 cf-to-cf compilations are normally used to desugar so that subsequent dexing does not require any
desugaring. Therefore, we can't actually use invoke-custom here even when compiling to Cf >= V9. Adding a `&&
!isCfDesugaring()` check here addresses this." Fixes: 53846 "Don't do switch rewriting in cf-to-cf desugar
intermediate mode", 99042-99101 "cf-to-cf handling of record call sites".

---

## I.1 Instructions inserted after register allocation

After `LinearScanRegisterAllocator`, `DexBuilder` resolves registers with
`getRegisterForValue(value, instruction.getNumber())`. Newly inserted instructions have number `-1`, and
`LiveIntervals.getSplitCovering(-1)` asserts if the value's interval was split.

```java
// ❌ CL 135820: passes the SSA value directly
Move move = new Move(newValue, value);

// ✅ refer to the physical register
Move move = new Move(newOut, new FixedRegisterValue(value.getType(), register));
```

Related post-RA rules (I.2): treat (r, r+1) of a wide value as jointly live; remove post-RA only non-throwing,
side-effect-free instructions; `FinalizerRewriterPass` code is not SSA, so don't use phi/user logic there (H.8).

---

## I.4 CF code: wide types and the operand stack

```java
// ❌ CL 75473/98341: wide parameters take two slots
for (int i = 0; i < params.size(); i++) {
  instructions.add(new CfLoad(ValueType.fromDexType(params.get(i)), i));
}

// ✅
int local = isStatic ? 0 : 1;
for (DexType p : params) {
  instructions.add(new CfLoad(ValueType.fromDexType(p), local));
  local += p.getRequiredRegisters();
}
```

- Never emit `CfStackInstruction` `Swap`/`Dup` on a long/double. Store to
  `freshLocalProvider.getFreshLocal(...)` instead (CL 77143).
- Forwarded/backported calls whose result is unused need `Pop`/`Pop2` before a void return (CL 77860, fix
  "Fix stack height for ContentProviderClient.close backport"). Emit exactly one `CfCheckCast` per path (CL 102640).
- Include wide types in `defaultMaxStack`/`defaultMaxLocals`, and test with long/double arguments and CF
  validation.

---

## J.1 Enqueuer extensions: incremental, not fixpoint rescans

```java
// ❌ CL 124462 (ResourceShrinkerEnqueuerExtension): quadratic rescans, live ⊋ instantiated
@Override
public void notifyFixpoint(...) {
  for (DexProgramClass clazz : liveClasses) {
    ... // look for onClick overrides
  }
}

// ✅ react when a class becomes instantiated (dispatch needs an instance)
@Override
public void processNewlyInstantiatedClass(DexProgramClass clazz, ...) {
  ...
}
```

- Inspect code through `NewlyLiveCodeEnqueuerAnalysis` (or `handleReflectiveBehavior`), never `buildIR` or an
  eager `getCode().asCfCode()` (CLs 100980, 103021).
- Add worklist items rather than tracing out of band (CLs 89608, 106981, 99580).
- Keep per-round data in the extension instance, not on `AppView` (CL 126903).

Thread: "This has somewhat quadratic behavior, can we fix this? Also, shouldn't this loop be over instantiated
classes and not live classes? Should this code be moved into a newly-instantiated-class callback?" (CL 124462).

---

## K.1 Resolution has more than two outcomes

`MethodResolutionResult` can be single, multiple (duplicate definitions across program/library/classpath),
failed, or array-clone. `asFailedResolution()` may be null for "neither single nor failed".

```java
// ❌ asserts a single program field; multi results pick an arbitrary one
assert result.isSingleProgramFieldResolutionResult();
ProgramField field = result.getSingleProgramField();

// ✅
ProgramField field = result.getProgramField();
if (field == null) {
  return;
}

// ✅ methods: visit every result, preferring program results
resolutionResults.forEachMethodResolutionResult(
    result -> {
      if (result.isSingleResolution()) {
        ...
      }
    });

// ✅ fields: getResolvedField() only when isSingleFieldResolutionResult()
```

Input may also mark a method abstract and still give it code. CLs 72780, 72860, 73843, 80680, 85941, 86024. Fix
70521 "Fix library overrides in the enqueuer with multi results".

---

## L.4 Enum unboxing invariants

The unboxed representation: `null` → `0`, the constant with ordinal *i* → *i + 1*. Enum subclasses
(`MyEnum$1`) are assignable to the root only, never to siblings.

```java
// ❌ treats siblings as interchangeable (accepts MyEnum$1 flowing into MyEnum$2)
return enumDataMap.representativeType(from) == enumDataMap.representativeType(to);

// ✅ CL 77222: same type, or subclass -> root only
return from == to || enumDataMap.representativeType(from) == to;
```

- `equals`/`compareTo` are unboxable only when both operands are the same candidate (or null) (CLs 77683, 77684).
- When re-boxing (e.g. for `Objects.hash`) map `0 → null`, `i > 0 → Integer.valueOf(i - 1)` (CL 89780).
- The unboxing lens must also map enum subclasses to `int`. When `KeepInfoCollection` rewriting drops info
  for an unboxed type, assert it was `!isPinned(options)` (CLs 78921, 78965).
- Abstract enum methods with a single implementation still need `lensBuilder.mapToDispatch` (CL 83000).
- Kotlin enum subclasses may lack `ACC_ENUM`. Find subtypes from roots (`superType == factory.enumType`)
  (CL 86980).

Fix families: 110580-110940 "enum unboxing bug causing VerifyError", 124541-124625 "enum unboxing bug in presence
of subclasses", 88661-88721 "missing enum unboxing analysis of instance-of", 103483-103568 "cast to int in enum
unboxer", 138762 "missing null check after enum unboxing".

---

## M.3 Globally mergeable synthetics must not reference non-public members

A synthetic kind with global merging can be deduplicated into a holder in another package. If its code
references package-private or protected members, the merged copy throws `IllegalAccessError`.

```java
// ✅ CL 76083/76685 (API model outlines)
SyntheticKind kind =
    holder.isPublic()
        ? kinds.API_MODEL_OUTLINE
        : kinds.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING;
```

Pair a non-global kind with `SamePackageForNonGlobalMergeSynthetic`. Synthetics that look equivalent but have
different requirements must not be shared by `SyntheticItems` equivalence: e.g. outlines for different
extension SDKs that all map to `AndroidApiLevel.EXTENSION` (CL 92023), and "unique per compilation" throw-block
outlines when sharded outputs are merged (CL 108083). Related fix: 94841 "Disable synthetic sharing of synthetics
with vertically merged sources".
