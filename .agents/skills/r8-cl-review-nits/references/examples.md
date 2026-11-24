# `r8-cl-review-nits`: R8 API & Test Idiom Examples

On-demand before/after idioms for the starred (`*`) rules in [../SKILL.md](../SKILL.md).

## Quick Index

| Section | Topic |
| --- | --- |
| G.1–G.4 | `isIdenticalTo`, `DexString`, `DexItemFactory`, `DescriptorUtils` |
| H.1, H.2, H.4, H.5 | Typed accessors, member pools, resolution, API reuse, hierarchy traversal |
| I.1, I.3, I.4 | IR predicates, IR builders, `AffectedValues`, `CodeRewriterPass` |
| J.1, J.3, J.5 | Identity collections, fastutil, `IRCode.blocks`, R8 utils, Guava |
| K.1, K.2 | `TestingOptions`, VM-bug predicates, `Timing`, `assert`, `Unreachable` |
| L.1 | `@KeepForApi` surface & internal leaks |
| M.1–M.3 | `testForR8(parameters)`, keep-rule helpers, `applyIf`, libraries, test annotations |
| N.1, O.1, P.1 | `TestParameters`, `CodeInspector`/`CodeMatchers`, merging, diagnostics, JVM test & memoization |

---

## G. Dex Items & `DexItemFactory`

### G.1 `isIdenticalTo`
```java
// Bad
!valueType.equals(linkedListType)
clazz.getSuperType() != factory.objectType
// Good
valueType.isNotIdenticalTo(linkedListType)
factory.objectType.isNotIdenticalTo(clazz.getSuperType()) // or DexType.identical(a, b)
```
**Say:** "Nit: Use `isNotIdenticalTo` here (call on `factory.objectType` since `getSuperType()` can be null)."

### G.2 No `DexString` -> `String`
```java
// Bad
if (method.getName().toString().equals("getNumber"))
if (type.toDescriptorString().startsWith("Lj$/"))
// Good
if (method.getName().isIdenticalTo(references.getNumberName))
if (type.getDescriptor().startsWith(factory.jDollarDescriptorPrefix))
```
**Say:** "Remove `toDescriptorString()` and compare with `getDescriptor().startsWith(factory.jDollarDescriptorPrefix)` to avoid allocating a String."

### G.3 Factory constants & member groups
```java
// Bad
itemFactory.createMethod(classType, proto, "getDeclaredField")
if (method.getName() == hasNextName && method.getProto() == hasNextProto)
// Good
itemFactory.classMethods.getDeclaredField
if (method.match(hasNextProto, hasNextName))
```
**Say:** "Nit: Reuse `itemFactory.classMethods.getDeclaredField` instead of recreating the method reference."

### G.4 Builders & `DescriptorUtils`
```java
// Bad
factory.createMethod(enumMapType, factory.createProto(factory.voidType, factory.classType), factory.constructorMethodName)
new DexEncodedField[0]
String internal = typeName.replace('.', '/');
"Missing class " + type
// Good
factory.createInstanceInitializer(enumMapType, factory.classType)
DexEncodedField.EMPTY_ARRAY
String internal = DescriptorUtils.getInternalNameFromJavaType(typeName);
"Missing class " + type.getTypeName()
```
**Say:** "Nit: Use `factory.createInstanceInitializer(...)` (and `DexEncodedField.EMPTY_ARRAY`) instead of constructing this manually."

---

## H. Definitions, `AppView` & Resolution

### H.1 Typed accessors & member pools
```java
// Bad
DexClass clazz = appView.definitionFor(type);
if (clazz == null || !clazz.isProgramClass()) return;
DexProgramClass programClass = (DexProgramClass) clazz;
for (DexEncodedField f : clazz.fields()) { if (f.isStatic() && f.hasExplicitStaticValue()) ... }
// Good
DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
if (clazz == null) return;
for (DexEncodedField f : clazz.staticFields(DexEncodedField::hasExplicitStaticValue)) { ... }
```
Also: `getHolderType()`, `method.asProgramMethod(clazz)`, `forEachProgramInstanceInitializer`, `forEachProgramMethodMatching`, `lookupMethod(proto, name)`.
**Say:** "Nit: Simplify with `asProgramClassOrNull(appView.definitionFor(type))`."

### H.2 Pass definitions; don't re-resolve
```java
// Bad
processField(field.getReference()); // later: clazz.lookupField(fieldRef)
appView.isInterface(holder) ? appInfo.resolveMethodOnInterface(holder, m) : appInfo.resolveMethodOnClass(holder, m)
// Good
processField(field);                // takes ProgramField
appInfo.resolveMethod(m, invoke.getInterfaceBit())
```
**Say:** "Nit: Pass the `ProgramField` directly here so we don't have to re-resolve it from the reference."

### H.4 Reuse existing APIs

| Avoid | Prefer |
| --- | --- |
| `options.partialSubCompilationConfiguration.asR8()...` | `options.getR8PartialR8SubCompilationOptions()` |
| `code instanceof LazyCfCode ? ((LazyCfCode) code).asCfCode() : ...` | `code.isCfCode()` / `code.asCfCode()` |
| `!keepInfo.isPinned(..) && options.isMinifying()` | `appView.getKeepInfo(clazz).isMinificationAllowed(options)` |
| `UseRegistry` subclass with empty overrides | `DefaultUseRegistry` / `DefaultUseRegistryWithResult` |
| `Set<Wrapper<DexMethod>>` by signature | `DexMethodSignatureSet` |
| `!lens.isIdentityLens()` + cast | `NonIdentityGraphLens nonIdentity = lens.asNonIdentityLens()` |

**Say:** "Nit: Use `code.isCfCode()` / `code.asCfCode()` directly—`LazyCfCode` already overrides both."

### H.5 Hierarchy traversals
```java
// Bad
appInfo.isSubtype(t, viewType) || appInfo.isSubtype(t, preferenceType)
// Good
appInfo.traverseSuperClasses(
    clazz,
    (supertype, superclass, subclass) ->
        TraversalContinuation.breakIf(
            supertype.isIdenticalTo(viewType) || supertype.isIdenticalTo(preferenceType)))
    .isBreak()
```
**Say:** "Nit: Combine these `isSubtype` calls into a single `traverseSuperClasses(...)` walk with `TraversalContinuation.breakIf(...)`."

---

## I. IR & Passes

### I.1 Invoke, value & composite predicates
```java
// Bad
invoke.inValues().get(0);
fallthrough.getPredecessors().size() != 1 || !exit.getPosition().isNone()
theIf.getType() != IfType.EQ && theIf.getType() != IfType.NE
// Good
invoke.getReceiver(); // or invoke.getFirstArgument()
!fallthrough.hasUniquePredecessor() || exit.getPosition().isSome()
!theIf.getType().isEqualsOrNotEquals()
```
Also: `hasUniqueNormalPredecessor()` when exceptional predecessors should be ignored.
**Say:** "Nit: Simplify to `!fallthrough.hasUniquePredecessor() || exit.getPosition().isSome()`."

### I.3 Create & replace IR
```java
// Bad
Value out = new Value(code.valueNumberGenerator.next(), type, null);
InvokeStatic invoke = new InvokeStatic(method, out, ImmutableList.of(value));
Set<Value> affected = Sets.newIdentityHashSet();
// Good
InvokeStatic invoke =
    InvokeStatic.builder()
        .setMethod(method)
        .setFreshOutValue(code, type)
        .setSingleArgument(value)
        .setPosition(position)
        .build();
AffectedValues affected = new AffectedValues();
```
**Say:** "Nit: Use `InvokeStatic.builder()...setFreshOutValue(code, type)` and `new AffectedValues()` instead of wiring the value number and set manually."

### I.4 `CodeRewriterPass`
```java
// Bad
changed |= pass.run(code, timing).hasChanged().isTrue(); // misses UNKNOWN
// Good
changed |= pass.run(code, timing).hasChanged().isPossiblyTrue();
```
Also: gate in `shouldRewriteCode` with `code.metadata().mayHave...()`, don't shadow inherited `appView`, and omit trailing `assert code.isConsistentSSA(appView)` (the base pass runs it).
**Say:** "Use `hasChanged().isPossiblyTrue()` here; `isTrue()` ignores `UNKNOWN` results."

---

## J. Collections & R8 Utils

### J.1 Identity collections & fastutil
```java
// Bad
Map<DexMethod, Integer> sizes = new IdentityHashMap<>();
return sizes.getOrDefault(method, 0);
Set<DexType> seen = new HashSet<>();
// Good
Reference2IntMap<DexMethod> sizes = new Reference2IntOpenHashMap<>();
sizes.defaultReturnValue(0);
return sizes.getInt(method);
Set<DexType> seen = SetUtils.newIdentityHashSet();
```
**Say:** "Nit: Use `Reference2IntOpenHashMap` with `defaultReturnValue(0)` to avoid boxing `Integer`s on this hot path."

### J.3 `IRCode.blocks` is a `LinkedList`
```java
// Bad
for (int i = 0; i < code.blocks.size(); i++) { BasicBlock b = code.blocks.get(i); }
code.blocks.removeAll(blocksToRemove);
// Good
ListIterator<BasicBlock> it = code.listIterator();
code.removeBlocks(blocksToRemove);
```
**Say:** "Use `code.removeBlocks(blocksToRemove)` instead of `code.blocks.removeAll(...)`, which scans the entire `LinkedList` even when `blocksToRemove` is empty."

### J.5 R8 utils & Guava idioms

| Avoid | Prefer |
| --- | --- |
| `List.of(a, b)` / `Set.of(...)` in compiler code | `ImmutableList.of(a, b)` / `ImmutableSet.of(...)` |
| `Deque` + `Set` seen loop | `WorkList.newIdentityWorkList(..)` (`addIfNotSeen`) |
| Stream map/filter to list | `ListUtils.map` / `ListUtils.filter` / `ListUtils.mapOrElse` |
| `BooleanBox` search accumulator | `IterableUtils.any` / `all` / `none` |
| `assert offset == ((offset + 3) & ~3)` | `assert BitUtils.isAligned(4, offset)` |

**Say:** "Nit: Use `ImmutableList.of(...)` instead of `List.of(...)` in compiler code."

---

## K. Options, Timing, Assertions & Exceptions

### K.1 Options & VM-bug predicates
```java
// Bad
options.testing.enableFoo = true;
boolean enableFoo = System.getProperty("com.android.tools.r8.enableFoo") != null;
public boolean hasBug() { return getMinApiLevel().getLevel() < 24; }
// Good
options.getTestingOptions().enableFoo = true;
boolean enableFoo = SystemPropertyUtils.parseSystemPropertyOrDefault("com.android.tools.r8.enableFoo", false);
// Art before N fails verification of ...: b/<id>.
public boolean canHaveFooBug() { return canHaveBugPresentUntilExclusive(AndroidApiLevel.N); }
```
**Say:** "Nit: Use `SystemPropertyUtils.parseSystemPropertyOrDefault(...)` so setting the property to `false` works as expected."

### K.2 Timing, assertions & exceptions
```java
// Bad
converter.removeDeadCodeAndFinalizeIR(code, feedback, Timing.empty());
code.forEachPosition(p -> verifyPosition(p)); // runs in production
default DesugarDescription compute(...) { assert false; return null; }
// Good
converter.removeDeadCodeAndFinalizeIR(code, feedback, timing);
assert verifyPositions(code); // returns true
default DesugarDescription compute(...) { throw new Unreachable("compute must be overridden"); }
```
**Say:** "Wrap this verification in `assert verifyPositions(code);` so it doesn't run in production builds."

---

## L. Public API (`@KeepForApi`)

### L.1 No accidental surface or internal leaks
```java
// Bad (on @KeepForApi R8Command or public Diagnostic)
public int getMinApiLevelMinor() { ... }
private final DexProgramClass dexProgramClass;
// Good
UncheckedApiLevel getUncheckedMinApiLevel() { ... } // package-private
private final Origin origin;
```
**Say:** "This adds a public method on a `@KeepForApi` class; make it package-private unless external consumers need it."

---

## M–P. Tests

### M.1 `testForR8(parameters)` / `testForD8(parameters)`
```java
// Bad
testForR8(parameters.getBackend()).addInnerClasses(FooTest.class).setMinApi(parameters)
// Good
testForR8(parameters).addInnerClasses(getClass())
```
Exception: `testForD8(parameters.getBackend()).setMinApi(parameters)` is valid when chaining `D8TestBuilder`-only methods.
**Say:** "Nit: Use `testForR8(parameters)` and drop `.setMinApi(parameters)` so partial-compilation configurations apply too."

### M.2 Keep-rule helpers

| Raw rule | Helper |
| --- | --- |
| `"-keep class Main { public static void main(...); }"` | `addKeepMainRule(Main.class)` |
| `"-keep class A { *; }"` | `addKeepClassAndMembersRules(A.class)` |
| `"-keep class A { <init>(); }"` | `addKeepClassAndDefaultConstructor(A.class)` |
| `"-keep,allowobfuscation class A"` | `addKeepClassRulesWithAllowObfuscation(A.class)` |
| `"-keepclassmembers enum * { ... }"` | `addKeepEnumsRule()` |
| `"-keepattributes *Annotation*,InnerClasses,EnclosingMethod"` | `addKeepAttributeInnerClassesAndEnclosingMethod().addKeepRuntimeVisibleAnnotations()` |
| `"-dontwarn " + X.class.getTypeName()` | `addDontWarn(X.class)` |
| `"-keeppackagenames"` | `addKeepPackageNamesRule(..)` |

**Say:** "Nit: Use `.addKeepClassAndMembersRules(A.class)` instead of a raw `-keep` rule string."

### M.3 `applyIf`, libraries & test annotations
```java
// Bad
.applyIf(isExcluded, b -> b.addR8ExcludedClasses(R.string.class))
.applyIf(!isExcluded, b -> b.addR8IncludedClasses(R.string.class))
.addOptionsModification(o -> o.inlinerOptions().enableInlining = false)
// Good
.applyIf(isExcluded, b -> b.addR8ExcludedClasses(R.string.class), b -> b.addR8IncludedClasses(R.string.class))
.enableInliningAnnotations() // + @NeverInline on the method
```
Also: add `.addDefaultRuntimeLibrary(parameters)` after `.addLibraryClasses(...)`.
**Say:** "Nit: Combine these into a single 3-argument `applyIf(isExcluded, ..., ...)` call."

### N.1 `TestParameters` & declarations
```java
// Bad
assumeTrue(parameters.isDexRuntime());
assumeTrue(parameters.getApiLevel().getLevel() >= 26);
// Good
parameters.assumeDexRuntime();
assumeTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
```
Use `@Parameter(0) public TestParameters parameters;` and `BooleanUtils.values()`.
**Say:** "Nit: Use `parameters.canUseDefaultAndStaticInterfaceMethods()` instead of hardcoding API level 26."

### O.1 `CodeInspector`, `CodeMatchers`, merging & diagnostics
```java
// Bad
assertTrue(inspector.clazz(A.class).isPresent());
assertThat(method, not(isPresent()));
assertThat(inspector.clazz(B.class), isAbsent()); // checking merge
// Good
assertThat(inspector.clazz(A.class), isPresent());
assertThat(method, isAbsent());
.addHorizontallyMergedClassesInspector(i -> i.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
```
Also: `invokesMethodWithName`/`invokesMethodWithHolderAndName`, `uniqueMethodWithOriginalName`, `binaryName(Foo.class)`, `SyntheticItemsTestUtils`, and `compileWithExpectedDiagnostics(...)` (without redundant `assertInfosCount`).
**Say:** "Nit: Use `assertThat(method, isAbsent())` instead of `not(isPresent())`."

### P.1 Separate JVM reference & test reuse
```java
// Bad
if (parameters.isCfRuntime()) { testForJvm(parameters)...; return; }
private static final Map<Backend, R8TestCompileResult> cache = new HashMap<>();
// Good
@Test public void testJvm() throws Exception { parameters.assumeJvmTestParameters(); testForJvm(parameters)...; }
private static final BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> compiled = memoizeBiFunction(MyTest::compile);
```
**Say:** "Nit: Move the JVM reference run into its own `@Test testJvm()` using `parameters.assumeJvmTestParameters()`."
