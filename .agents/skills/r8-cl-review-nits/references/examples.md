<!-- disableFinding(LINE_OVER_80) -->

# r8-cl-review-nits: examples

Before/after snippets for checklist items in [../SKILL.md](../SKILL.md), keyed by checklist id. CL numbers
are `r8-review.googlesource.com/c/r8/+/<CL>`. Snippets are condensed from real review threads. Every
API named here was checked against the R8 source tree (Oct 2026). Before you suggest any helper in a
finding, still `rg` it in `src/`.

---

## A. Diff hygiene and dead code

### A.1 Debug leftovers (81721, 116081, 85022)

```java
// Before: a disabled branch and a timing probe left in src/main.
if (!appView.enableWholeProgramOptimizations() && false) {
  long start = System.nanoTime();
  ...
}
// After
if (!appView.enableWholeProgramOptimizations()) {
  ...
}
```

```java
// Before: a breakpoint anchor (CL 116081).
if (clazz.getTypeName().equals("kotlin.ranges.CharRange")) {
  System.currentTimeMillis();
}
// After: delete it.
```

```java
// Before (test)
testForR8(parameters)
    ...
    .compile()
    .inspect(inspector -> System.out.println(inspector.clazz(Main.class)))
    .disassemble()
    .run(parameters.getRuntime(), Main.class);
// After
testForR8(parameters)
    ...
    .run(parameters.getRuntime(), Main.class)
    .assertSuccessWithOutputLines(EXPECTED);
```

```java
// Before (test): obfuscation was turned off while debugging the inspect() output.
testForR8(parameters)
    .addInnerClasses(getClass())
    .addKeepMainRule(Main.class)
    .addDontObfuscate()
    .compile()
    .inspect(inspector -> assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"), isAbsent()))
    .run(parameters.getRuntime(), Main.class)
    .assertSuccessWithOutputLines("Hello");
// After: CodeInspector resolves A/foo through the mapping and the output has no class
// names, so .addDontObfuscate() is not needed.
testForR8(parameters)
    .addInnerClasses(getClass())
    .addKeepMainRule(Main.class)
    .compile()
    ...
```

Keep `.addDontObfuscate()` when the expected output contains class or member names
(`getClass().getName()`, stack traces), the test looks things up by string name (reflection, JNI,
resources), or the test compares raw final names. If unsure, ask instead of asserting:
`Is .addDontObfuscate() needed here?`

Severity is **blocking** when the leftover changes behavior or output in `src/main` (`&& false`,
`if (true)`, `println`). It is **should_fix** in tests. Do not flag `System.out.println` or
`if (System.currentTimeMillis() > 0)` inside test *input* classes, where they are the program under
test.

### A.2 Commented-out code (104681, 95748)

```java
// Before
// instructions.add(new CfStackInstruction(Opcode.Pop));
instructions.add(new CfReturnVoid());

// Before (blocking: silently drops coverage)
return getTestParameters()
    // .withAllRuntimesAndApiLevels()
    .withDexRuntime(Version.V4_4_4)
    .build();
// After
return getTestParameters().withAllRuntimesAndApiLevels().build();
```

To keep the idea, write `// TODO(b/<id>): <what and why>`, never the dead code itself.

### A.3 Code the CL made unused (124342, 134360, 76860)

```java
// Before: `factory` is already a field.
private boolean analyze(Instruction instruction, int offset, DexItemFactory factory)
// After
private boolean analyze(Instruction instruction, int offset)
```

```java
// Before: the flag no longer has readers, but its accessor is stubbed.
public boolean enableFooOptimization = false;
public boolean isFooOptimizationEnabled() { return false; }
// After: delete both, plus every test addOptionsModification that sets it.
```

To verify, run `rg -w isFooOptimizationEnabled src/` (test and testbase dirs included). Check
overrides and reflective use (`@UsedByReflection`, keepanno) before you call it dead.

### A.6 Repro + fix stack (134680, 135060)

```java
// Repro CL: the test is complete and asserts the CURRENT behavior.
.run(parameters.getRuntime(), Main.class)
// TODO(b/412345678): Should succeed with "A", "B".
.assertFailureWithErrorThatThrows(VerifyError.class);

// Fix CL: the only change in the test file is the expectation.
.run(parameters.getRuntime(), Main.class)
.assertSuccessWithOutputLines("A", "B");
```

If the *fix* CL restructures the test (new helpers, parameters or classes), suggest moving that into the
repro CL. Do not flag structural edits that the fix CL needs to *exercise* the fix (1.4).

---

## B. Comments, TODOs and javadoc

### B.1 Comments the change invalidates (137140, 110641, 92580)

```java
// Before: describes a plan that was never implemented.
// Uses new-instance analysis for non-final fields.
// After
// TODO(b/296030319): Analyze new-instance instructions for non-final fields.
```

```java
// Before: the method now also returns synthetic methods.
/** Returns the program methods of this class. */
// After
/** Returns the program methods of this class, including synthesized methods. */
```

Also look for assert messages and `@return` lines that name the old type or parameter, and TODOs that
the diff resolves.

### B.2 Copy-pasted text (132740, 71720)

```java
// Before
// finalKeepInfoCollection export
reporter.error("Could not export initial keep info collection");
// After
reporter.error("Could not export final keep info collection");
```

```java
// Before
// Verify that we have two outlines.
assertEquals(1, outlines.size());
// After
// Verify that we have one outline.
assertEquals(1, outlines.size());
```

### B.3 TODO format (70404, 117801, 114824)

| Before | After |
| --- | --- |
| `// TODO: Consider running in second pass.` | `// TODO(b/453628974): Consider running in second pass.` |
| `// TODO(b/449961100):` | `// TODO(b/449961100): Should not fail with VerifyError when only public members are kept.` |
| `// TODO(b/): The expected result should be ...` | `// TODO(b/<id>): Should be "A".` |
| `// TODO(sgjesse): remove` | `// TODO(b/<id>): Remove when ...` |

A disabled check needs a cause and a bug:

```java
// Before
.setEnableDex2OatVerification(false)
// After
// TODO(b/412345678): dex2oat fails verification of the synthesized lambda on API 21.
.setEnableDex2OatVerification(false)
```

### B.4 Explain the why (76083, 130140, 126903)

```java
} catch (Throwable t) {
  // If this is hit, null is left in the cache.
}
```

```java
// Keeping an R class field does not imply that the resource is used; only reads are traced.
if (enqueuer.isRClass(field.getHolder())) {
  return;
}
```

```java
// Use a hash function that is stable across JVM runs (no Object.hashCode).
```

Every new `canHave...Bug()` in `InternalOptions` needs a one-line symptom plus a `b/` link.

### B.6 Javadoc and bytecode docs (70407, 103301)

```java
// Before: inverted description.
// Flag to disable backport methods.
public boolean enableBackportMethods = true;
// After
// Flag to turn on/off backport methods.
public boolean enableBackportMethods = true;
```

For a hand-built `List<CfInstruction>`, add a pseudo-Java comment (`// return a == null ? b : a;`).
For an ASM `dump()`, suggest `transformer(X.class).setXxx(..).transform()` when the shape can be
reached by transforming a Java class.

---

## C. Commit message

### C.1 Subject and body (72028, 138322, 114824)

| Before | After |
| --- | --- |
| `Fix b/426351560` | `Fix IllegalAccessError from synthetic bridge insertion` |
| `Changes` / `WIP` / `[WIP] Foo` | `Use identity maps in ClassToFeatureSplitMap` |
| `Reland "Reland "Foo"` (unbalanced) | `Reland "Reland "Foo""` |

Also check the body: after a split, the body must not describe changes that moved to another CL. A squash
must leave no extra `Change-Id:` lines and no `This reverts commit ...` text unless it really is a revert.

### C.2 Bug footer (121980, 108780)

```
Before:  Bug: 291923475
After:   Bug: b/291923475

Before:  Bug: b/1, b/2           (fine)
Auto-close:  Fixes: b/291923475
```

### C.4 Tool metadata (135343, 115761)

Flag `TAG=agy`, `CONV=<uuid>`, "Generated with ..." boilerplate, and edits to the author's local
`PRESUBMIT.py`/`.gitignore`.

---

## D. Naming

### D.2 Name the full behavior (110641, 117780, 115340)

```java
// Before: the name contradicts the expression.
boolean art16OrNewer = version.isOlderThanOrEqual(Version.V15_0_0);
// After
boolean art15OrOlder = version.isOlderThanOrEqual(Version.V15_0_0);
```

```java
// Before
public void map(Consumer<T> consumer)                    // runs consumer 0..1 times
int getEstimatedSizeInBytesOfItems(...) { count++; ... } // counts items
// After
public void ifPresent(Consumer<T> consumer)
int getEstimatedNumberOfItems(...)
```

```java
// Before: a negated compound condition inline.
if (singleTarget == null
    || (!info.isConvertCheckNotNull() && !kotlinNullCheckLedgibleForRemoval(...))) {
// After
if (singleTarget == null || !canConvertNullCheck(appView, singleTarget)) {
```

### D.3 R8 vocabulary (86520, 94961, 124462)

```java
// Before
IRCode irCode = programMethod.buildIR(appView);
private final boolean itf;
Instruction[] createMaterializingInstruction(..)   // returns an array
.setProguardMapConsumer(consumer != null)          // takes a boolean
// After
IRCode code = method.buildIR(appView);
private final boolean isInterface;
Instruction[] createMaterializingInstructions(..)
.setHasProguardMapConsumer(consumer != null)
```

Lambda parameters that are not used: `map.compute(method, (unusedKey, other) -> ...)`.

### D.4 Established terms (75496, 70362)

```java
// Before: the name claims an Object.clone check, but the holder is never checked.
public boolean isArrayClone(DexProto proto, DexString name)
// After
public boolean isObjectCloneWithoutHolderCheck(DexProto proto, DexString name)
```

```java
// Before: "original" in R8 means the identity-lens (input) signature.
DexMethod originalContext = getPreviousMethodSignature(context);
// After
DexMethod previousContext = getPreviousMethodSignature(context);
```

### D.5 Rename what derives (82140, 129440)

```java
// Before (after DefiniteBitsNumberValue was renamed to DefiniteBitsIntNumberValue)
DefiniteBitsIntNumberValue definiteBitsNumberValue = createDefiniteBitsNumberValue(..);
// After
DefiniteBitsIntNumberValue definiteBitsIntNumberValue = createDefiniteBitsIntNumberValue(..);
```

### D.7 Enums, not flags (104681, 88980, 70681)

```java
// Before
createInitialClassToFeatureSplitMap(options.getFeatureSplitConfiguration(), true);
new KeepRuleExtractorOptions(true, false, true, true);
testMethodRule(MethodReference method, boolean hot, boolean startup, boolean postStartup);
// After
createInitialClassToFeatureSplitMap(options.getFeatureSplitConfiguration(), Mode.R8);
KeepRuleExtractorOptions.builder().setIncludeObfuscation(true)...build();
testMethodRule(MethodReference method, ArtProfileMethodRuleInfo info);
```

(The `Mode` and builder names are illustrative; suggest the enum or builder shape and let the author
name it.)

---

## E. Formatting, imports, headers

### E.1 Format touched lines only (97020, 106540, 112500)

```java
// Before: formatter artifacts.
builder = ProguardConfiguration.builder(factory, reporter);
;
builder.setForceProguardCompatibility(forceProguardCompatibility);
if (!next.isInvokeStatic()) continue;
// Int case
// After
builder =
    ProguardConfiguration.builder(factory, reporter)
        .setForceProguardCompatibility(forceProguardCompatibility);
if (!next.isInvokeStatic()) {
  continue;
}
// Int case.
```

Gotcha: running `tools/fmt_diff.py` on a branch whose upstream is not its parent can reformat unrelated
files. If the diff contains formatting-only hunks in files the CL otherwise does not touch, flag them
under A.4.

### E.3 Imports (123700, 117340)

```java
// Before
for (com.android.tools.r8.ir.code.Value inValue : invoke.inValues()) {
// After
import com.android.tools.r8.ir.code.Value;
...
for (Value inValue : invoke.inValues()) {
```

An inline qualified name is acceptable only when two simple names really clash in one file (ASM
`org.objectweb.asm.Opcodes` vs LIR `LirOpcodes` users).

### E.4 Copyright header (113761, 134880)

```java
// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
```

Use the current year for new files. Moved files keep their year. Use exactly one header, `#`-commented
in `.py`/`.txt`/`.kts`-style files as the siblings do.

### E.5 Suppressions (86024, 135268)

```java
// Before
@SuppressWarnings("ReferenceEquality")
boolean isString(DexEncodedField field) {
  return field.getType() == dexItemFactory.stringType;
}
// After
boolean isString(DexEncodedField field) {
  return field.getType().isIdenticalTo(dexItemFactory.stringType);
}
```

---

## F. Structure and simplification

### F.1 Merge branches, return early (94447, 127021, 73860)

```java
// Before
if (a) { x.setRegister(r); return; }
if (b) { x.setRegister(r); return; }
// After
if (a || b) { x.setRegister(r); return; }
```

```java
// Before: four arms for two independent facts.
if (isInstance && isPut) {...} else if (isInstance) {...} else if (isPut) {...} else {...}
// After
if (isInstance) { parameters.add(holderType); }
if (isPut) { parameters.add(field.getType()); }
```

```java
// Before: duplicated call in both arms (CL 127021).
if (cond) { rewriteLirWithLens(...); } else { rewriteLirWithLens(...); assert ...; }
// After
rewriteLirWithLens(...);
if (!cond) { assert ...; }
```

### F.2 Checks that cannot fail (99842, 113463)

```java
// Before
return state != null && state == Block.EXCEPTIONAL_MONITOR_EXIT;
// After
return state == Block.EXCEPTIONAL_MONITOR_EXIT;
```

```java
// Before: synthesizeClass always produces a pending class.
synthesizeClass(...);
if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
  appView.rebuildAppInfo();
}
// After
synthesizeClass(...);
appView.rebuildAppInfo();
```

```java
// Before
for (DexProgramClass clazz : appView.appInfo().classes()) {
  if (!clazz.isProgramClass()) continue;   // classes() only yields program classes
```

Calibration: if the guarantee is non-local (another pass, a caller three frames up), suggest replacing the
check with an `assert` rather than deleting it.

### F.3 Deduplicate and hoist (108640, 132320)

```java
// Before
if (invoke.getMethod().getHolderType().isNotIdenticalTo(a)
    && invoke.getMethod().getHolderType().isNotIdenticalTo(b)) {
// After
DexType holder = invoke.getMethod().getHolderType();
if (holder.isNotIdenticalTo(a) && holder.isNotIdenticalTo(b)) {
```

### F.5 Visibility and finality (85941, 113780, 124462)

```java
// Before
public Set<DexMethod> copyOfMethods;
private static class ResolvedUpdater {
  public final boolean isNullable;
// After
private final Set<DexMethod> copyOfMethods;   // with a getter if read outside
private static class ResolvedUpdater {
  final boolean isNullable;
```

### F.7 No sentinels (89363, 102242, 138940)

```java
// Before: null means "the base".
FeatureSplit split = isInBase ? null : featureSplit;
// After
FeatureSplit split = isInBase ? FeatureSplit.BASE : featureSplit;
```

```java
// Before (test): null VM on CF.
StackTrace.extractFromArt(stderr, parameters.isDexRuntime() ? vm : null);
// After
if (parameters.isDexRuntime()) {
  StackTrace.extractFromArt(stderr, parameters.asDexRuntime().getVm());
}
```

```java
// Before
if (other == UNPROCESSED_CANDIDATE) { return this; }
// After
if (other.isUnprocessedCandidate()) { return this; }
```

```java
// Rewrite helpers return their input when there is nothing to do, not null.
return isRewrittenType(type) ? vivifiedTypeFor(type, appView) : type;
```

### F.12 Minimal signatures (116721, 112120)

```java
// Before: every caller normalizes.
timeUnitFromValue(value.getAliasedValue());
// After: normalize inside the helper.
private TimeUnit timeUnitFromValue(Value value) {
  Value root = value.getAliasedValue();
  ...
}
```

---

## G. Dex items and `DexItemFactory`

### G.1 `isIdenticalTo` (91265, 135268, 131060, 114824)

```java
// Before
!guards1.get(i).equals(guards2.get(i))
!valueType.equals(linkedListType)
resolved.getReference().holder.equals(arrayListType)
clazz.getSuperType() != factory.objectType          // super type may be null
// After
guards1.get(i).isNotIdenticalTo(guards2.get(i))
valueType.isNotIdenticalTo(linkedListType)
resolved.getHolderType().isIdenticalTo(arrayListType)
factory.objectType.isNotIdenticalTo(clazz.getSuperType())
```

If both sides may be null, use `DexType.identical(a, b)`. Scope: `DexReference` subclasses (`DexType`,
`DexMethod`, `DexField`), `DexString` and `DexProto`. Do not flag `==` on `DexEncodedMethod`,
`DexClass`, `ProgramMethod` or SSA `Value`s (1.4).

### G.2 No `DexString` -> `String` (131060, 134900, 81840)

```java
// Before
if (method.getName().toString().equals("getNumber"))
if (type.toDescriptorString().startsWith("Lj$/"))
if (name.toSourceString().startsWith("access$"))
// After
if (method.getName().isIdenticalTo(references.getNumberName))
if (type.getDescriptor().startsWith(factory.jDollarDescriptorPrefix))
if (name.startsWith(factory.createString("access$")))   // hoist the DexString to a field
```

`DexString` already has `startsWith`, `endsWith`, `contains`, `isEqualTo(String)` and `iterator()`.

### G.3 Factory constants and member groups (73763, 133260, 114824, 97540)

```java
// Before
getDeclaredField = itemFactory.createMethod(classType, proto, "getDeclaredField");
if (target.getName().toString().startsWith("to") && target.getArity() == 1) // TimeUnit conversion
if (method.getName() == hasNextName && method.getProto() == hasNextProto)
// After
getDeclaredField = itemFactory.classMethods.getDeclaredField;
if (!timeUnitMembers.isConversionMethod(target)) { return; }
if (method.match(hasNextProto, hasNextName))
```

Calling `factory.createString("...")` inside a per-instruction loop interns the string on every call.
Hoist it to a field or add it to `DexItemFactory`.

### G.5 Builders (100921, 131060, 77683)

```java
// Before
factory.createMethod(enumMapType, factory.createProto(factory.voidType, factory.classType),
    factory.constructorMethodName)
factory.createField(holder, field.getType(), field.getName())
new DexEncodedField[0]
// After
factory.createInstanceInitializer(enumMapType, factory.classType)
field.withHolder(holder, factory)
DexEncodedField.EMPTY_ARRAY
```

For fresh names use `factory.createFreshMethodNameWithoutHolder(..)`, not a hand-written
`$collision$N` loop.

### G.7 `DescriptorUtils` and rendering (77961, 114024)

```java
// Before
String post = postPrefix.replace('.', '/');
"... for `" + holder.toSourceString() + "`"
"Missing class " + type                         // implicit toString()
// After
String post = DescriptorUtils.getInternalNameFromJavaType(postPrefix);
"... for `" + holder.getTypeName() + "`"
"Missing class " + type.getTypeName()
```

Methods in messages: `MethodReferenceUtils.toSourceString(method.asMethodReference())`, or
`method.toSourceString()` for `DexMethod`. Mapping output: `toProguardMapSource()`.

---

## H. Definitions, AppView and resolution

### H.1 Typed accessors (77543, 131060, 123660)

```java
// Before
DexClass clazz = appView.definitionFor(type);
if (clazz == null || !clazz.isProgramClass()) return;
DexProgramClass programClass = (DexProgramClass) clazz;
// After
DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
if (clazz == null) {
  return;
}
```

More accessors: `getAnnotationType()` (instead of `annotation.annotation.type`), `code.getBlocks()`,
`qualifiedName()`, `asDexValueInt().getValue()`, `asConstNumber()`, and `method.asProgramMethod(clazz)`
(not `new ProgramMethod(clazz, method)`).

### H.2 Member pools (107640, 89484, 126903)

```java
// Before
for (DexEncodedField field : clazz.fields()) {
  if (field.isStatic() && field.getStaticValue() != null) { ... }   // getStaticValue() is never null
}
// After
for (DexEncodedField field : clazz.staticFields(DexEncodedField::hasExplicitStaticValue)) { ... }
```

Full list of `DexClass`/`DexProgramClass` helpers worth suggesting: `staticFields(Predicate)`,
`instanceFields()`, `methods(Predicate)`, `virtualMethods(Predicate)`, `directMethods(Predicate)`,
`forEachProgramField`, `forEachProgramStaticField`, `forEachProgramMethod`,
`forEachProgramMethodMatching`, `forEachProgramInstanceInitializer`, `forEachProgramMember`,
`hasMethodsOrFields()`, `lookupMethod(proto, name)`, `lookupUniqueStaticFieldWithName`.

### H.3 Pass definitions; don't re-resolve (85180, 126903, 62983)

```java
// Before
populateRClassValues(DexField field, SingleProgramFieldResolutionResult result) { ... }
processField(field.getReference());  ...  rClass.lookupField(fieldReference);
appView.isInterface(holder) ? appInfo.resolveMethodOnInterface(holder, m)
                            : appInfo.resolveMethodOnClass(holder, m)
// After
populateRClassValues(ProgramField field) { ... }
processField(field);  ...  DexProgramClass rClass = field.getHolder();
appInfo.resolveMethod(m, invoke.getInterfaceBit())
```

When a `MethodResolutionResult` is already in hand, call `lookupDispatchTarget(..)` on it or use
`getResolutionPair()`, instead of a second `InvokeMethod#lookupSingleTarget`.

### H.5 Reuse APIs (136742, 92805, 139684)

| Before | After |
| --- | --- |
| `options.partialSubCompilationConfiguration.asR8().…` | `options.getR8PartialR8SubCompilationOptions()` |
| `code instanceof LazyCfCode ? ((LazyCfCode) code).asCfCode() : …` | `code.isCfCode()` / `code.asCfCode()` (`LazyCfCode` overrides both) |
| local "can remove parameter" logic | `ParameterRemovalUtils.canRemoveUnusedParameter(..)` |
| `!keepInfo.isPinned(..) && options.isMinifying()` | `appView.getKeepInfo(clazz).isMinificationAllowed(options)` |
| `UseRegistry` subclass with 20 empty overrides | extend `DefaultUseRegistry` / `DefaultUseRegistryWithResult` |
| `Set<Wrapper<DexMethod>>` keyed by signature | `DexMethodSignatureSet` |

### H.7 Hierarchy traversals (103920, 124362)

```java
// Before
appInfo.isSubtype(t, viewType) || appInfo.isSubtype(t, preferenceType)
    || appInfo.isSubtype(t, transitionType)
// After: one walk with early exit.
appInfo
    .traverseSuperClasses(
        clazz,
        (supertype, superclass, subclass) ->
            TraversalContinuation.breakIf(
                supertype.isIdenticalTo(viewType)
                    || supertype.isIdenticalTo(preferenceType)
                    || supertype.isIdenticalTo(transitionType)))
    .isBreak()
```

### H.8 Lens helpers (86160, 82480)

```java
// Before
if (!lens.isIdentityLens()) { NonIdentityGraphLens nonIdentity = (NonIdentityGraphLens) lens; ...
// After
NonIdentityGraphLens nonIdentity = lens.asNonIdentityLens();
if (nonIdentity != null) { ...
```

Empty maps: `EMPTY_FIELD_MAP`/`EMPTY_TYPE_MAP` (not `new IdentityHashMap<>()`). A custom IR rewrite for a
lens goes in a `CustomLensCodeRewriter` passed to the lens constructor. Build a single
`RewrittenPrototypeDescription.create(..)`, not a chain of `with*`.

---

## I. IR and passes

### I.1 Invoke/value helpers (113061, 129621)

```java
// Before
invoke.arguments().get(0).getAliasedValue().getDefinition().isConstNumber()
Value receiver = invoke.inValues().get(0);
int first = invoke.isInvokeStatic() ? 0 : 1;
// After
invoke.getFirstArgument().isConstNumber()     // or isDefinedByInstructionSatisfying(Instruction::isConstNumber)
Value receiver = invoke.getReceiver();
int first = invoke.getFirstNonReceiverArgumentIndex();
```

More: `phi.getOperand(i)`, `value.getConstInt()`, `value.hasSingleUniqueUser()`,
`newInstance.getUniqueConstructorInvoke(factory)`, `proto.toShorty()`.

### I.2 Composite predicates (135620, 137840, 80804)

```java
// Before
fallthrough.getPredecessors().size() != 1 || !exit.getPosition().isNone()
theIf.getType() != IfType.EQ && theIf.getType() != IfType.NE
position instanceof SourcePosition || position instanceof SyntheticPosition
// After
!fallthrough.hasUniquePredecessor() || exit.getPosition().isSome()
!theIf.getType().isEqualsOrNotEquals()
position.isSourcePosition() || position.isSyntheticPosition()
```

More: `hasUniqueSuccessorWithUniquePredecessor()`, `getUniqueNormalSuccessor()`, `isZeroTest()`,
`isPrimitiveArrayType()`, `identicalAfterRegisterAllocation(..)`. There is **no**
`hasUniqueNormalPredecessor`; never suggest it.

### I.3 Typed iteration (85941, 109140)

```java
// Before
for (Instruction instruction : code.instructions()) {
  if (instruction.isStaticPut()) { StaticPut put = instruction.asStaticPut(); ... }
}
// After
for (StaticPut put : code.<StaticPut>instructions(Instruction::isStaticPut)) { ... }

// Non-mutating block walk:
for (Instruction i = block.entry(); i != null; i = i.getNext()) { ... }
```

### I.4 Create and replace IR (86160, 134605, 81290)

```java
// Before
Value out = new Value(code.valueNumberGenerator.next(), type, null);
InvokeStatic invoke = new InvokeStatic(method, out, ImmutableList.of(value));
invoke.setPosition(position);
// After
InvokeStatic invoke =
    InvokeStatic.builder()
        .setMethod(method)
        .setFreshOutValue(code, type)
        .setSingleArgument(value)
        .setPosition(position)
        .build();
```

```java
// Before
ConstNumber zero = code.createNumberConstant(0, type);
iterator.add(zero); current.outValue().replaceUsers(zero.outValue()); iterator.remove(); ...
// After
iterator.replaceCurrentInstruction(code.createNumberConstant(0, type));
```

```java
// Before
Set<Value> affectedValues = Sets.newIdentityHashSet();
// After
AffectedValues affectedValues = new AffectedValues();
value.replaceUsers(newValue, affectedValues);
...
affectedValues.narrowingWithAssumeRemoval(appView, code);
```

### I.5 `CodeRewriterPass` (79348, 116681, 135680)

```java
// Before
changed |= pass.run(code, timing).hasChanged().isTrue();     // drops UNKNOWN
// After
changed |= pass.run(code, timing).hasChanged().isPossiblyTrue();
```

Shape of a new pass:

```java
public class FooRewriter extends CodeRewriterPass<AppInfo> {
  @Override
  protected String getRewriterId() {
    return "FooRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.enableFoo && code.metadata().mayHaveIf();   // O(1) gate
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) { ... }
}
```

Register it in `CodeRewriterPassCollection`. Do not re-declare an `appView` field that shadows the
inherited one, and do not end with `assert code.isConsistentSSA(appView);`, since the base class handles
that.

### I.7 Desugaring (76400, 127580)

```java
// Before: a separate needsDesugaring + desugar pair.
// After
@Override
public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
  if (instruction.isInvokeDynamic()) {
    return needsDesugaring(instruction) ? computeInvokeDynamic(..) : DesugarDescription.nothing();
  }
  return DesugarDescription.nothing();
}
```

To change a type operand, use `instruction.asTypeInstruction().withType(newType)`. Regenerate backports
with `GenerateBackportMethods` rather than editing `BackportedMethods.java`.

---

## J. Collections and compile-time cost

### J.1 Identity collections (124462, 131700, 89220)

```java
// Before
Map<DexString, Origin> references = new HashMap<>();
Set<DexType> seen = new HashSet<>();
// After
Map<DexString, Origin> references = new IdentityHashMap<>();
Set<DexType> seen = SetUtils.newIdentityHashSet();
Set<DexType> copy = SetUtils.newIdentityHashSet(annotationsToRetain);                // copy
Set<DexType> sized = SetUtils.newIdentityHashSet(live.size() + pruned.size());       // presized
WorkList<DexType> worklist = WorkList.newIdentityWorkList(roots);
```

### J.2 fastutil primitives (109581, 126280, 106583)

```java
// Before
Map<DexMethod, Integer> sizes = new IdentityHashMap<>();
return sizes.getOrDefault(method, 0);
Set<Integer> inlinedIds = new HashSet<>();
new Int2ReferenceOpenHashMap<>(map, Hash.DEFAULT_LOAD_FACTOR)
// After
Reference2IntMap<DexMethod> sizes = new Reference2IntOpenHashMap<>();
sizes.defaultReturnValue(0);
return sizes.getInt(method);
IntSet inlinedIds = new IntOpenHashSet();
new Int2ReferenceOpenHashMap<>(map)
```

### J.3 Hoist, cache, memoize (134660, 71621)

```java
// Before: recomputed on every query.
public boolean isAmbiguous() { return computeIsAmbiguous(); }
// After
private OptionalBool isAmbiguous = OptionalBool.unknown();

public boolean isAmbiguous() {
  if (isAmbiguous.isUnknown()) {
    isAmbiguous = OptionalBool.of(computeIsAmbiguous());
  }
  return isAmbiguous.isTrue();
}
```

Or `Supplier<Foo> foo = SupplierUtils.nonThreadSafeMemoize(this::computeFoo);`. Defensive copies that
exist only for checking go under `if (AssertionUtils.assertionsEnabled())`.

### J.4 Hot-path allocation (115340, 124886)

```java
// Before
new AwaitableFutures(a, b).await();
List<DexType> result = new ArrayList<>(types.size());
for (DexType type : types) result.add(lens.lookupType(type));
// After
AwaitableFutures.awaitAll(a, b);
List<DexType> result = ListUtils.mapOrElse(types, lens::lookupType, types);   // copies only on change
```

```java
// Before: one task per feature split.
ThreadUtils.processItems(featureSplits, this::process, ...);
// After: one task per class.
ThreadUtils.processItems(appView.appInfo().classes(), this::process, ...);
```

### J.5 `hashCode` (127000, 133006)

```java
// Before (hot: used as a map key per invoke)
return Objects.hash(method, invokeType, hasOutValue, proto);
// After
return ObjectUtils.hashLLL(method, invokeType, proto) * 31 + Boolean.hashCode(hasOutValue);
```

Only flag this on a hot key; elsewhere `Objects.hash` is accepted (1.4).

### J.6 `IRCode.blocks` is a `LinkedList` (135481, 136021)

```java
// Before
for (int i = 0; i < code.blocks.size(); i++) { BasicBlock block = code.blocks.get(i); ... }  // O(n^2)
code.blocks.removeAll(blocksToRemove);   // walks every block, even for an empty set
// After
ListIterator<BasicBlock> it = code.listIterator();
while (it.hasNext()) { BasicBlock block = it.next(); ... }
code.removeBlocks(blocksToRemove);
```

### J.7 Quadratic scans (97540, 89484)

```java
// Before
for (DexEncodedMethod method : methods) {
  if (!processed.contains(method)) {      // processed is a List
// After
Set<DexEncodedMethod> processed = Sets.newIdentityHashSet();
...
if (processed.add(method)) {
```

```java
// Before
for (DexProgramClass clazz : appView.appInfo().classes()) {
  if (clazz.getType().isIdenticalTo(target)) { ... }
}
// After
DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(target));
```

### J.8 Lazy strings (124300, 102200)

```java
// Before: builds a string for every traced value.
traceMappedValue(value, field.getReference().toString());
// After: pass the reference; render only when reporting.
traceMappedValue(value, field.getReference());
```

### J.10 Java/Guava idioms (114824, 86444, 77543)

```java
// Before
List<DexType> types = List.of(a, b);     // java.util.List.of: not in r8lib's supported API range
BooleanBox found = new BooleanBox();
method.getParameters().forEach(p -> { if (shouldConsider(p)) found.set(); });
return found.get();
// After
List<DexType> types = ImmutableList.of(a, b);
return Iterables.any(method.getParameters(), this::shouldConsider);
```

### J.11 R8 utils (124180, 82480)

| Hand-rolled | R8 helper |
| --- | --- |
| `assert offset == ((offset + 0x03) & ~0x03);` | `assert BitUtils.isAligned(4, offset);` |
| `Deque` + `Set` seen-set loop | `WorkList.newIdentityWorkList(..)` with `addIfNotSeen`, `hasNext`, `next` |
| stream map/collect to list | `ListUtils.map(list, fn)` |
| filter loop | `ListUtils.filter(list, predicate)` |
| `map.entrySet().removeIf(e -> ...)` | `MapUtils.removeIf(map, (k, v) -> ...)` |
| lexicographic list comparator | `ComparatorUtils.listComparator()` |
| `Iterables.all/none` with custom loop | `IterableUtils.all(..)` / `IterableUtils.none(..)` |

---

## K. Options, Timing, assertions, exceptions, diagnostics

### K.1 Options (107220, 120981, 139684, 126903)

```java
// Before
options.testing.enableFoo = true;
public Boolean enableFoo = null;
boolean enableFoo = System.getProperty("com.android.tools.r8.enableFoo") != null;
// After
options.getTestingOptions().enableFoo = true;
public boolean enableFoo = false;
boolean enableFoo =
    SystemPropertyUtils.parseSystemPropertyOrDefault("com.android.tools.r8.enableFoo", false);
```

Derived enablement: `return isEnabled() && appView.enableWholeProgramOptimizations();` instead of a new
`enableFooInD8 = false` flag.

### K.2 VM-bug predicates (79822, 92534)

```java
// Before (InternalOptions)
public boolean hasFilledNewArrayBug() { return getMinApiLevel().getLevel() < 24; }
// After
// Art before N fails verification of filled-new-array with sub types: b/<id>.
public boolean canHaveSubTypesInFilledNewArrayBug() {
  return canHaveBugPresentUntilExclusive(AndroidApiLevel.N);
}
```

```java
// Before (test)
if (version == Version.V5_1_1 || version == Version.V6_0_1)
// After
if (!version.isNewerThan(Version.V6_0_1))
```

### K.3 Timing (85483, 100921, 75144)

```java
// Before
timing.begin("Create app info");
AppInfoWithLiveness info = new AppInfoWithLiveness(...);
// (missing timing.end())
converter.removeDeadCodeAndFinalizeIR(code, feedback, Timing.empty());
// After
AppInfoWithLiveness info = timing.time("Create app info", () -> new AppInfoWithLiveness(...));
converter.removeDeadCodeAndFinalizeIR(code, feedback, timing);
```

`Timing` is `AutoCloseable`, so `try (Timing t = timing.begin("X")) { ... }` also works (check the
local idiom).

### K.4 Assertions (74580, 136021, 120242)

```java
// Before: verification runs in production.
code.forEachPosition(p -> verifyOriginalMethodInPosition(p, original, method));
// After
assert verifyOriginalMethodsInPositions(code, original, method);

private static boolean verifyOriginalMethodsInPositions(...) {
  code.forEachPosition(p -> { assert ...; });
  return true;
}
```

```java
// Golden-data check says how to fix it (CL 120242).
assertArrayEquals("Did you forget to run GenerateSunMiscUnsafe.main?", generated, bytes);
```

### K.5 Exceptions (75210, 113240)

```java
// Before
default DesugarDescription compute(...) {
  assert false;
  return null;
}
throw new Unimplemented();          // for a state that can never happen
// After
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
...
default DesugarDescription compute(...) {
  throw new Unreachable("compute must be overridden");
}
```

Use `throw new UnsupportedOperationException("APPEND is not supported for RandomAccessFile channels")`
with a message, never bare (73422).

### K.6 Diagnostics (74446, 92024)

```java
// Before
new StringDiagnostic("Recursive @argfiles are not supported: ", origin)
throw new CompilationError("Invalid bootstrap arg " + arg);
// After
new StringDiagnostic("Recursive @argfiles are not supported: " + arg, origin)
throw new CompilationError("Invalid bootstrap arg " + arg, context.getOrigin());
```

---

## L. Public API (`@KeepForApi`)

### L.1 No accidental public surface (121501, 133006)

```java
// Before: on R8Command (kept API).
public int getMinApiLevelMinor() { ... }
// After: package-private, or moved to an internal util.
UncheckedApiLevel getUncheckedMinApiLevel() { ... }
```

Tests should assert on the value they configured instead of reading it back through a new public getter.

### L.2 No leaks (110122, 91265)

```java
// Before: a public Diagnostic holding compiler internals.
private final DexProgramClass dexProgramClass;
private final DexEncodedField dexEncodedField;
// After
private final Origin origin;
private final FieldReference field;
```

### L.4 Shape (85720, 70372)

```java
// Before
new LegacyResourceShrinker.Builder()
builder.setEnableFoo();            // no-arg setter
// After
LegacyResourceShrinker.builder()  // Builder constructor non-public
builder.setEnableFoo(true);        // or enableFoo()
```

### L.5 Compatibility (76500, 113500)

```java
// Keep a declared throws on an existing API default method.
@SuppressWarnings("RedundantThrows")
default void finished(DiagnosticsHandler handler) throws IOException {}

// Before
provider.getProgramResources(resource -> ...);
// After
ProgramResourceProviderUtils.forEachProgramResourceCompat(provider, resource -> ...);
```

---

## M. Tests: builders and keep rules

### M.1 `testForR8(parameters)` (128580, 139684, 112301)

```java
// Before
testForR8(parameters.getBackend())
    .addInnerClasses(FooTest.class)
    .addKeepMainRule(Main.class)
    .setMinApi(parameters)
    .run(parameters.getRuntime(), Main.class)
// After
testForR8(parameters)
    .addInnerClasses(getClass())
    .addKeepMainRule(Main.class)
    .run(parameters.getRuntime(), Main.class)
```

`testForR8(parameters)` also applies `setMinApi` and the R8-partial configurations, so the old form
silently skips partial-compilation coverage. Exception: a D8 chain that needs `D8TestBuilder`-only methods
may keep `testForD8(parameters.getBackend()).setMinApi(parameters)`, because `testForD8(parameters)`
returns `TestCompilerBuilder`.

### M.2 Keep-rule helpers (108540, 91765, 134860)

| Raw rule | Helper on `TestShrinkerBuilder`/`R8TestBuilder` |
| --- | --- |
| `"-keep class Main { public static void main(...); }"` | `addKeepMainRule(Main.class)` |
| `"-keep class A { *; }"` | `addKeepClassAndMembersRules(A.class)` |
| `"-keep class A { <init>(); }"` | `addKeepClassAndDefaultConstructor(A.class)` |
| `"-keep,allowobfuscation class A"` | `addKeepClassRulesWithAllowObfuscation(A.class)` |
| `"-keepclassmembers enum * { values(); valueOf(...); }"` | `addKeepEnumsRule()` |
| `"-keepattributes PermittedSubclasses"` | `addKeepPermittedSubclasses(..)` |
| `"-keepattributes *Annotation*,InnerClasses,EnclosingMethod"` | `addKeepAttributeInnerClassesAndEnclosingMethod().addKeepRuntimeVisibleAnnotations()` |
| `"-dontwarn " + X.class.getTypeName()` | `addDontWarn(X.class)` |
| `"-keeppackagenames"` | `addKeepPackageNamesRule(..)` |
| attribute literal `"Signature"` | `ProguardKeepAttributes.SIGNATURE` |

### M.4 `applyIf` (70933, 100381)

```java
// Before
.applyIf(isExcluded, b -> b.addR8ExcludedClasses(R.string.class))
.applyIf(!isExcluded, b -> b.addR8IncludedClasses(R.string.class))
// After
.applyIf(
    isExcluded,
    b -> b.addR8ExcludedClasses(R.string.class),
    b -> b.addR8IncludedClasses(R.string.class))
```

```java
.applyIf(spec == JDK8, r -> r.assertFailure(), r -> r.assertSuccess())
```

Outside a builder chain, use a plain `if` instead of wrapping one statement in `applyIf`.

### M.5 Drop defaults (122341, 104380)

Remove these unless the test needs them: `.setIntermediate(false)`, `.setMode(CompilationMode.RELEASE)` on
R8, `.allowDiagnosticInfoMessages(false)`, and `.addDontObfuscate()`/`.addDontOptimize()`/`.addDontShrink()`
when the assertions do not depend on them. Prefer `.debug()`/`.release()` over `setMode(..)`.

### M.6 Library setup (77543, 86011)

```java
testForR8(parameters)
    .addLibraryClasses(LibraryClass.class)
    .addDefaultRuntimeLibrary(parameters)       // addLibraryClasses replaces the default library
    .addProgramClasses(Main.class)
```

Instead of a checked-in stub jar, use `.addLibraryProvider(JdkClassFileProvider.fromSystemJdk())` or
`ToolHelper.getMostRecentAndroidJar()`.

### M.7 Test annotations (74620, 76222)

```java
// Before
.addOptionsModification(o -> o.inlinerOptions().enableInlining = false)
// After
.enableInliningAnnotations()
...
static class A {
  @NeverInline
  static void foo() { ... }
}
```

Also `@NoVerticalClassMerging`/`.enableNoVerticalClassMergingAnnotations()`, `@NoHorizontalClassMerging`,
and `@NoUnusedInterfaceRemoval`.

---

## N. Tests: parameters

### N.1 / N.2 Narrow but sufficient (79747, 132960, 116428)

```java
// Before: output does not depend on the API level.
getTestParameters().withDexRuntimes().withAllApiLevels().build()
.withDexRuntimesStartingFromIncluding(Version.V5_1_1).withAllApiLevels()
// After
getTestParameters().withDefaultDexRuntime().build()
.withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B)
```

```java
// A repro or desugaring test must cover CF too.
getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build()
// R8 behavior tests: also partial compilation.
getTestParameters().withAllRuntimesAndApiLevels().withPartialCompilation().build()
```

Parser and CLI tests use `withNoneRuntime()`.

### N.3 `TestParameters` helpers (92700, 113340)

```java
// Before
assumeTrue(parameters.isDexRuntime());
assumeTrue(parameters.getRuntime().isCf());
assumeTrue(parameters.getApiLevel().getLevel() >= 26);
// After
parameters.assumeDexRuntime();
parameters.assumeJvmTestParameters();
assumeTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
```

### N.4 Declarations (138962, 76860, 133500)

```java
// Before
private final TestParameters parameters;
public FooTest(TestParameters parameters) { this.parameters = parameters; }
// After
@Parameter(0)
public TestParameters parameters;

@Parameter(1)
public boolean minify;

@Parameters(name = "{0}, minify: {1}")
public static List<Object[]> data() {
  return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build(),
      BooleanUtils.values());
}
```

---

## O. Tests: CodeInspector, run results, diagnostics

### O.1 Matchers (108080, 91765, 134860)

```java
// Before
assertTrue(inspector.clazz(A.class).isPresent());
assertFalse(method.isPresent());
assertThat(method, not(isPresent()));
assertEquals(parameters.isCfRuntime(), method.isPresent());
// After
assertThat(inspector.clazz(A.class), isPresent());
assertThat(method, isAbsent());
assertThat(method, isAbsent());
assertThat(method, isPresentIf(parameters.isCfRuntime()));
```

Conditional negation: `assertThat(method, notIf(isRenamed(), keep))`.

### O.2 `CodeMatchers` (94323, 133400)

```java
// Before
assertTrue(
    main.streamInstructions()
        .noneMatch(i -> i.isInvoke() && i.getMethod().getName().toString().equals("requireNonNull")));
// After
assertThat(main, not(invokesMethodWithName("requireNonNull")));
assertThat(main, invokesMethodWithHolderAndName(typeName(Objects.class), "requireNonNull"));
```

### O.3 Lookups and names (95000, 125600)

```java
// Before
inspector.clazz("a.a").method("void", "b", ImmutableList.of());
String name = "com.example.FooTest$ExceptionB";
// After
inspector.clazz(Foo.class).uniqueMethodWithOriginalName("bar");
String name = binaryName(ExceptionB.class);
```

Synthetics: `SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 0)` and similar, not a
hard-coded `$$ExternalSynthetic` name.

### O.4 Run results (138640, 139101)

```java
// Before: inspects but never checks behavior.
.run(parameters.getRuntime(), Main.class)
.inspect(this::inspect);
// After
.run(parameters.getRuntime(), Main.class)
.assertSuccessWithOutputLines(EXPECTED)
.inspect(this::inspect);
// Expected failures are typed.
.assertFailureWithErrorThatThrows(VerifyError.class);
```

### O.5 Merging (74880, 134540)

```java
// Before
assertThat(inspector.clazz(B.class), isAbsent());   // "B was merged"
// After
.addHorizontallyMergedClassesInspector(
    inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
```

### O.6 Diagnostics (78064, 117463)

```java
// Before
.compile();
assertEquals(1, diagnostics.getInfos().size());
assertTrue(diagnostics.getInfos().get(0).getDiagnosticMessage().contains("Foo"));
// After
.compileWithExpectedDiagnostics(
    diagnostics ->
        diagnostics
            .assertOnlyInfos()
            .assertInfosMatch(
                allOf(diagnosticType(FooDiagnostic.class), diagnosticMessage(containsString("Foo")))))
```

`assertInfosMatch(a, b, c)` already checks the count; drop the extra `assertInfosCount`.

### O.7 JUnit (124080, 137500)

```java
// Before
@Test(expected = CompilationFailedException.class)
public void test() throws Exception { ...; }
try { compile(); fail(); } catch (CompilationFailedException e) { }
assert result.size() == 2;                        // Java assert: disabled without -ea
AtomicReference<String> seen = new AtomicReference<>();
// After
assertThrows(CompilationFailedException.class, () -> compile());
assertEquals(2, result.size());
Box<String> seen = new Box<>();
```

---

## P. Tests: structure and infrastructure

### P.2 Separate the JVM reference (138940, 75145)

```java
// Before
@Test
public void test() throws Exception {
  if (parameters.isCfRuntime()) {
    testForJvm(parameters).addTestClasspath().run(parameters.getRuntime(), Main.class)...;
    return;
  }
  testForR8(parameters)...;
}
// After
@Test
public void testJvm() throws Exception {
  parameters.assumeJvmTestParameters();
  testForJvm(parameters)
      .addTestClasspath()
      .run(parameters.getRuntime(), Main.class)
      .assertSuccessWithOutputLines(EXPECTED);
}

@Test
public void testR8() throws Exception {
  testForR8(parameters)...;
}
```

### P.4 Reuse infrastructure (124942, 136921, 90181)

```java
// Before: a hand-rolled static cache.
private static final Map<Backend, R8TestCompileResult> cache = new HashMap<>();
// After
private static final BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> compiled =
    memoizeBiFunction(MyTest::compile);
```

Helpers shared by several tests go into a `*TestBase` (`class ServiceLoaderConstClassFromCalleeTest
extends ServiceLoaderTestBase`), not static imports from a sibling test.

### P.6 Files (125721, 84642)

```java
// Before
Path source = Paths.get("src/test/java/com/android/tools/r8/foo/Foo.java");
Path jar = Paths.get("third_party/foo/foo.jar");
// After
Path source = getSourceFileForTestClassFromResources(Foo.class);   // or getResourceAsReadOnlyFile
Path jar = Paths.get(ToolHelper.THIRD_PARTY_DIR, "foo", "foo.jar");
```

---

## Q. Tools, build and generated code

### Q.1 Generated code (113981, 88201)

If the diff edits `BackportedMethods.java`, `VarHandleDesugaringMethods.java` or keepanno generated
annotations/AST by hand, it is **blocking**. Ask for the generator change (`GenerateBackportMethods`,
`GenerateVarHandleMethods`, `KeepItemAnnotationGenerator`) plus the regenerated output. The generator
tests compare output byte-for-byte and fail presubmit otherwise.

### Q.2 Python tools (112420, 139220)

```python
# Before
parser.add_argument('--count', default=None)
parser.add_argument('--verbose', action='store_true', default=None)
os.system('java -jar ' + jar)
if x == None:
# After
parser.add_argument('--count', type=int)
parser.add_argument('--verbose', action='store_true')
subprocess.run(['java', '-jar', jar], check=True)
if x is None:
```

Use existing helpers from `tools/utils.py` (`utils.TempDir()`, `utils.getAndroidBuildTools(..)`) and
do not re-implement them. `main()` returns an exit code and the script ends with
`sys.exit(main())`.
