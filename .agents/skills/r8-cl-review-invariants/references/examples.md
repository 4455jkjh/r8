# `r8-cl-review-invariants`: Compiler & IR Invariant Examples

On-demand before/after patterns for the starred (`*`) rules in [../SKILL.md](../SKILL.md).

## Quick Index

| Section | Topic |
| --- | --- |
| A.1, A.2 | Phi `getDefinition()` NPE, checking all user kinds before removal |
| B.1–B.3 | Multiple throwing instructions, handler liveness/hoisting, `splitCopyCatchHandlers` |
| C.1 | Cleaning up unreachable/redundant blocks & detached `AffectedValues` |
| D.1 | Positions on newly created instructions |
| E.5 | `getStaticValue()` default-value trap |
| F.2 | Volatile reads as memory barriers |
| G.1, G.3, G.4 | Passing the right `codeLens`, stateful `appliedGraphLens`, map rewriting |
| H.3 | D8 cf-to-cf builds no IR (`!isCfDesugaring()`) |
| I.1, I.4 | Post-RA `FixedRegisterValue`, CF wide locals & operand stack |
| J.1 | Incremental Enqueuer callbacks vs `notifyFixpoint` rescans |
| K.1 | Multi-outcome `MethodResolutionResult` / `FieldResolutionResult` |
| L.4 | Enum unboxing representation & subclass assignability |
| M.3 | Globally mergeable synthetics & non-public member access |

---

## A. SSA Values, Phis & Users

### A.1 Phi has no `getDefinition()`
```java
// Bad: NPE when object() or getAliasedValue() is a phi
if (object().getAliasedValue().getDefinition().isInvokeVirtual()) { ... }

// Good
Value aliasedValue = object().getAliasedValue();
if (aliasedValue.isDefinedByInstructionSatisfying(Instruction::isInvokeVirtual)) {
  InvokeVirtual invoke = aliasedValue.getDefinition().asInvokeVirtual();
  ...
}
```
Note: `ConstNumber` outputs never get `Assume` users, so `value.isConstNumber()` directly is fine.
**Say:** "This will NPE if `object()` (or its aliased value) is a phi, since phis have no `getDefinition()`. Use `isDefinedByInstructionSatisfying(...)` instead."

### A.2 Check every user kind before removing or replacing a value
`aliasedUsers()` returns instruction users only—not phi users, debug users, or phi users of `Assume` out-values.
```java
// Bad: misses phi/debug users
if (value.aliasedUsers().size() == 1) { ... }

// Good
if (value.hasPhiUsers() || value.hasDebugUsers()) {
  return;
}
for (Instruction user : value.aliasedUsers()) {
  if (user.isAssume() && user.outValue().hasPhiUsers()) {
    return;
  }
}
```
Clear debug users (`Instruction.removeDebugValue`) and run `DeadCodeRemover` so replaced instructions die.
**Say:** "`aliasedUsers()` skips phi and debug users (and phi users of `Assume` outputs), so this can remove a value that is still live. Check `hasPhiUsers()` and `hasDebugUsers()` too."

---

## B. Catch Handlers & Exceptional Edges

### B.1 Adding/expanding possibly-throwing instructions
A block with catch handlers may hold at most one throwing instruction. Don't insert a second one with `it.add(...)`, and don't bail out on `block.hasCatchHandlers()` (misses try/catch and `synchronized` code).
```java
// Bad: two throwing instructions in a covered block
it.add(newStaticGet);
it.add(newInvoke);

// Good: splits blocks and copies handlers automatically
it.previous();
it.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
    code, blocks, ImmutableList.of(newStaticGet, newInvoke), appView.options());
it.next();
```
**Say:** "Adding two throwing instructions directly can violate the at-most-one-throwing-instruction invariant in blocks with catch handlers. Insert them together with `addPossiblyThrowingInstructionsToPossiblyThrowingBlock(...)`."

### B.2 Exceptional edges are implicit uses
A throwing instruction may jump to catch handlers before writing its out register, so every handler live-in register is live at every throwing instruction in a covered block.
```java
// Good (post-RA backward liveness)
for (Instruction insn = block.exit(); insn != null; insn = insn.getPrev()) {
  live &= ~outMask(insn);
  if (block.hasCatchHandlers() && insn.instructionTypeCanThrow()) {
    live |= catchLiveOutRegisters;
  }
  live |= inMask(insn);
}
```
Similarly, never hoist instructions into a predecessor across an exceptional edge (`!pred.hasCatchSuccessor(block)`).
**Say:** "Because a throwing instruction can branch to a catch handler before writing its output, handler live-ins must be unioned at every throwing instruction here."

### B.3 Splitting after a throwing instruction
```java
// Bad: new block loses handlers, and one previous() mis-positions if multiple blocks were created
BasicBlock split = it.split(code, blocks);
blocks.previous();

// Good
BasicBlock split = it.splitCopyCatchHandlers(code, blocks, appView.options());
blocks.previousUntil(split);
blocks.next();
```
Post-RA: copy only handlers whose live-ins are already defined at the split, and never drop handlers when `code.metadata().mayHaveMonitorInstruction()` (skips `monitor-exit`).
**Say:** "Use `splitCopyCatchHandlers(...)` with `blocks.previousUntil(split)` here so the split block keeps its catch handlers and the iterator stays aligned."

---

## C. CFG Cleanup

### C.1 Clean up the CFG you break
```java
// Bad: leaves unreachable blocks and detached values behind
block.removeSuccessor(deadTarget);
return CodeRewriterResult.HAS_CHANGED;

// Good
AffectedValues affectedValues = new AffectedValues();
...
if (unlinkedEdges) {
  code.removeUnreachableBlocks(affectedValues);
  affectedValues.narrowingWithAssumeRemoval(appView, code);
  code.removeRedundantBlocks();
}
assert code.isConsistentSSA(appView); // outside CodeRewriterPass, which already asserts it
```
When queueing `blocksToRemove`, skip queued blocks during iteration and remove them once with `code.removeBlocks(blocksToRemove)`.
**Say:** "Unlinking edges here can leave unreachable blocks and stale types behind. Call `removeUnreachableBlocks(affectedValues)` followed by `narrowingWithAssumeRemoval` and `removeRedundantBlocks()`."

---

## D. Positions

### D.1 Positions on new instructions
```java
// Bad: none/synthetic position breaks stack traces and retracing
ConstNumber c = code.createIntConstant(0);
c.setPosition(Position.syntheticNone());

// Good
c.setPosition(invoke.getPosition(), appView.options());
```
For `SingleValue.createMaterializingInstructions(...)`, set the position on every returned instruction and put local info only on the final out-value.
**Say:** "Copy the originating instruction's position with `setPosition(invoke.getPosition(), appView.options())` instead of using a synthetic/none position."

---

## E. Static Defaults

### E.5 `getStaticValue()` default-value trap
```java
// Bad: getStaticValue() returns DexValue.defaultForType when no ConstantValue exists
if (valueField == null || !(valueField.getStaticValue() instanceof DexValueInt)) { return false; }

// Good
if (valueField == null
    || !valueField.hasExplicitStaticValue()
    || !valueField.getStaticValue().isDexValueInt()) {
  return false;
}
```
Never fold static values from `ClassKind.LIBRARY` classes (device values can differ).
**Say:** "`getStaticValue()` returns a default value when none is explicitly set, so guard this with `valueField.hasExplicitStaticValue()`."

---

## F. Volatile Reads

### F.2 Volatile reads are memory barriers
```java
// Good: removing a volatile read drops an acquire barrier
if (resolvedField.getAccessFlags().isVolatile()
    && !appView.getAssumeInfoCollection().isSideEffectFree(resolvedField)) {
  return true;
}
```
Also: keep value propagation off for volatile fields, and never let `FieldFinalizer` mark a volatile field `final` (`final volatile` is illegal).
**Say:** "Volatile reads act as memory barriers and cannot be removed or finalized unless explicitly marked side-effect-free via `AssumeInfoCollection`."

---

## G. `GraphLens` & LIR

### G.1 Always pass the right `codeLens`, and stop at it
```java
// Bad: null hides double-application, or re-walks already-applied lenses
state.rewrittenWithLens(appView, lens, null);

// Good: step through this lens alone, or stop recursion at this == codeLens
DexMethod m = enumUnboxingLens.getNextMethodSignature(invoke.getInvokedMethod());
```
Items `LensCodeRewriter` never rewrites use `GraphLens.getIdentityLens()`. `DexWritableCode.writeDex` expects original references (instructions rewrite during `write`).
**Say:** "Passing `null` (or the wrong `codeLens`) here can double-apply earlier lenses. Pass the lens the item is already rewritten to, or call `getNextMethodSignature(...)` directly."

### G.3 Stateful lens rewriting starts from `appliedGraphLens`
```java
// Bad: rewrites from appView.codeLens() every time
info.rewriteWithLens(appView.graphLens(), appView.codeLens());

// Good: rewrite from appliedGraphLens, then advance it to appView.graphLens()
info.rewriteWithLens(appView.graphLens(), appliedGraphLens);
appliedGraphLens = appView.graphLens();

// When lenses are pruned (updateAppliedLens), walk past pruned lenses with an identity fallback
if (prunedGraphLenses.contains(appliedGraphLens) && appliedGraphLens.isNonIdentityLens()) {
  GraphLens surviving =
      appliedGraphLens
          .asNonIdentityLens()
          .find(l -> !l.isClearCodeRewritingLens() && !l.isMemberRebindingIdentityLens());
  appliedGraphLens = surviving != null ? surviving : GraphLens.getIdentityLens();
}
```
**Say:** "This can double-apply lenses on subsequent rewrites. Rewrite from `appliedGraphLens`, then update `appliedGraphLens = appView.graphLens()`."

### G.4 `rewrittenWithLens` on maps
```java
// Bad: in-place put/remove drops entries when A->B and B->C, and overwrites collisions
// Good: fresh map, rewrite nested values, merge collisions
Map<DexMethod, MethodBoxingStatus> rewritten = new LinkedHashMap<>();
map.forEach((method, status) -> {
  DexMethod newMethod = lens.getRenamedMethodSignature(method, codeLens);
  MethodBoxingStatus newStatus = status.rewrittenWithLens(lens, codeLens);
  rewritten.merge(newMethod, newStatus, MethodBoxingStatus::merge);
});
```
**Say:** "Mutating this map in place during lens rewriting can drop chained renames (`A -> B`, `B -> C`) and overwrite collisions. Build a fresh map and merge colliding keys."

---

## H. D8 cf-to-cf

### H.3 D8 cf-to-cf builds no IR
```java
// Bad: allows invoke-custom in D8 cf-to-cf desugaring
public boolean canUseInvokeCustomWithStringConcatFactory(CfVersion cfVersion) {
  return isGeneratingClassFiles() && cfVersion.isGreaterThanOrEqualTo(CfVersion.V9);
}
// Good
public boolean canUseInvokeCustomWithStringConcatFactory(CfVersion cfVersion) {
  return isGeneratingClassFiles()
      && cfVersion.isGreaterThanOrEqualTo(CfVersion.V9)
      && !isCfDesugaring();
}
```
"A later lowering exists" is `isGeneratingDex() || appView.enableWholeProgramOptimizations()`, not `options.intermediate`.
**Say:** "D8 cf-to-cf desugars ahead of later dexing without IR, so it can't emit invoke-custom here. Add `&& !isCfDesugaring()`."

---

## I. Post-RA & CF Stack

### I.1 Post-RA inserted instructions need `FixedRegisterValue`
Newly inserted post-RA instructions have number `-1`, breaking split-interval lookups.
```java
// Bad
Move move = new Move(newValue, value);
// Good
Move move = new Move(newOut, new FixedRegisterValue(value.getType(), register));
```
**Say:** "Instructions inserted after register allocation have number `-1` and can fail interval lookup. Wrap the physical register in `FixedRegisterValue`."

### I.4 CF code: wide types & operand stack
```java
// Bad: assumes every parameter uses 1 local slot
for (int i = 0; i < params.size(); i++) {
  instructions.add(new CfLoad(ValueType.fromDexType(params.get(i)), i));
}
// Good
int local = isStatic ? 0 : 1;
for (DexType p : params) {
  instructions.add(new CfLoad(ValueType.fromDexType(p), local));
  local += p.getRequiredRegisters();
}
```
Never emit `Swap`/`Dup` on `long`/`double` (use a fresh local); balance unused return values with `Pop`/`Pop2`.
**Say:** "Wide parameters (`long`/`double`) occupy two local slots, so indexing by `i` breaks local offsets. Advance the local index by `p.getRequiredRegisters()`."

---

## J. Enqueuer

### J.1 Incremental Enqueuer callbacks
```java
// Bad: rescans all live classes on every fixpoint iteration
@Override public void notifyFixpoint(...) {
  for (DexProgramClass clazz : liveClasses) { ... }
}
// Good: react incrementally when classes become instantiated
@Override public void processNewlyInstantiatedClass(DexProgramClass clazz, ...) { ... }
```
Inspect code via `NewlyLiveCodeEnqueuerAnalysis`, never `buildIR`.
**Say:** "Scanning all `liveClasses` in `notifyFixpoint` is quadratic and includes non-instantiated classes. Move this into `processNewlyInstantiatedClass(...)`."

---

## K. Resolution

### K.1 Multi-outcome resolution
```java
// Bad: fails on multi-resolution or non-program results
assert result.isSingleProgramFieldResolutionResult();
ProgramField field = result.getSingleProgramField();
// Good
ProgramField field = result.getProgramField();
if (field == null) { return; }
```
For methods, use `forEachMethodResolutionResult(...)`; for fields, guard `getResolvedField()` with `isSingleFieldResolutionResult()`.
**Say:** "Resolution can return multiple or non-program results, so asserting `isSingleProgramFieldResolutionResult()` can fail on valid input. Use `getProgramField()` with a null check instead."

---

## L. Enum Unboxing

### L.4 Enum unboxing representation & assignability
`null` -> `0`, ordinal `i` -> `i + 1`. Subclasses (`MyEnum$1`) are assignable to the root enum only, never to siblings.
```java
// Bad: treats MyEnum$1 and MyEnum$2 as interchangeable
return enumDataMap.representativeType(from).isIdenticalTo(enumDataMap.representativeType(to));
// Good: same type or subclass -> root only (or enumDataMap.isAssignableTo(from, to))
return from.isIdenticalTo(to) || enumDataMap.representativeType(from).isIdenticalTo(to);
```
Also: `equals`/`compareTo` unbox only for the same candidate; map enum subclasses to `int` in the lens; single-implementation abstract methods still use `mapToDispatch`; Kotlin enum subclasses may lack `ACC_ENUM`.
**Say:** "Comparing `representativeType` on both sides treats sibling enum subclasses as assignable to each other. Use `enumDataMap.isAssignableTo(from, to)` (or check `from.isIdenticalTo(to) || enumDataMap.representativeType(from).isIdenticalTo(to)`) instead."

---

## M. Synthetics

### M.3 Globally mergeable synthetics
Globally mergeable synthetics can be moved into another package during merging; referencing non-public members throws `IllegalAccessError`.
```java
// Good
SyntheticKind kind =
    holder.isPublic()
        ? kinds.API_MODEL_OUTLINE
        : kinds.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING;
```
**Say:** "A globally mergeable synthetic can be deduplicated into another package and throw `IllegalAccessError` when accessing non-public members. Use a non-global synthetic kind when the holder isn't public."
