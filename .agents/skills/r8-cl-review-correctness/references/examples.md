<!-- disableFinding(LINE_OVER_80) -->

# `r8-cl-review-correctness` — Examples

Before/after code and real review threads for the starred (★) checklist items in
[../SKILL.md](../SKILL.md). Each entry gives the checklist id, the trigger to look for in a diff, the
bad/good shapes, the failure mode, and the CLs where reviewers raised it. Code is distilled from the review
threads; names were checked against the current R8 tree.

---

## A. Logic slips

### A.1 Boolean polarity (CLs 80822, 91080, 116580, 104581)

Trigger: a new/renamed predicate (`isXAllowed`, `hasNonIdentityX`, `canX`) guarding a call that does the
opposite thing (`disallowX`, `return`), or a renamed boolean parameter.

```java
// ❌ CL 80822 (keep-rule modifier -allowpermittedsubclassesremoval)
if (keepInfo.isPermittedSubclassesRemovalAllowed(..)) {
  joiner.disallowPermittedSubclassesRemoval();   // removes exactly what must be kept
}

// ✅
if (!keepInfo.isPermittedSubclassesRemovalAllowed(..)) {
  joiner.disallowPermittedSubclassesRemoval();
}
```

```java
// ❌ CL 91080: "has non-identity witness" built from the equality helper without negation
return originalFieldWitness != null && originalFieldWitness.isEqualToDexField(getReference());
// ✅
return originalFieldWitness != null && !originalFieldWitness.isEqualToDexField(getReference());
```

```java
// ❌ early-return guard lost its negation: the optimization now runs exactly when it must not
if (singleTarget == null || canConvertNullCheck(appView, singleTarget)) {
  return;
}
// ✅
if (singleTarget == null || !canConvertNullCheck(appView, singleTarget)) {
  return;
}
```

Review comment: "This should be negated. When removal is not allowed, we need to call
`disallowPermittedSubclassesRemoval()` on the keep info." Severity: blocking (keep-rule semantics /
miscompilation).

How to check: write out the truth table for the guard with the two most common inputs; when a boolean
parameter is renamed to its opposite (`onlyIncludeProgramClasses` → `includeNonProgramClasses`), open every
caller in `src/` and every use inside the method.

### A.2 Wrong variable after copy-paste (CLs 85537, 74664, 85340, 124462)

```java
// ❌ CL 85537: tests the outer value, not the loop variable → NPE on phi definitions
for (Value inValue : newArray.inValues()) {
  if (value.isPhi()) {
    continue;
  }
  Instruction definition = inValue.getDefinition();   // inValue may be a phi
  ...
}
// ✅
for (Value inValue : newArray.inValues()) {
  if (inValue.isPhi()) {
    continue;
  }
  ...
}
```

```java
// ❌ CL 85340: memoizing helper returns a constant instead of the computed value
cache.put(type, superIsSub);
return true;
// ✅
cache.put(type, superIsSub);
return superIsSub;

// ❌ argument array sized from the *context* method
ValueBoxingStatus[] args = new ValueBoxingStatus[context.getReference().getArity()];
// ✅ (review comment: "invoke.getInvokedMethod().getArity()")
ValueBoxingStatus[] args = new ValueBoxingStatus[invoke.getInvokedMethod().getArity()];
```

CL 74664: "This should probably have been `caller.method`" — a position mapper used `method` (the
inlinee) where the caller was intended; output mapping was silently wrong. CL 124462: option set on
`r8Builder` where `d8Builder` was meant (R8 partial).

### A.4 Early exit skips required work (CLs 108843, 111980, 82940, 90181, 99842)

```java
// ❌ CL 108843 (StringBuilder escape analysis): returns after validating the first user
for (Instruction user : invoke.outValue().aliasedUsers()) {
  if (user.isAssume()) {
    return true;            // remaining users never checked → unsound optimization
  }
  ...
}
// ✅ check every user (and phi users, which aliasedUsers() does not include)
if (invoke.outValue().hasPhiUsers()) {
  return false;
}
for (Instruction user : invoke.outValue().aliasedUsers()) {
  if (!user.isAssume() && !isAllowedUser(user)) {
    return false;
  }
}
return true;
```

Review comment: "We still need to process all the remaining users, so we cannot eagerly return true
here." Related traps:
- A reporting loop that `return`s on a skip case drops the remaining diagnostics — use `continue` (CL 82940).
- CL 111980: a lookup loop returning on the `$default` variant skipped `keepByteCode.accept(method)` for the
  main method that must always run (Kotlin inline functions broke).
- Callback contracts: an `onChangedAction` passed into a state update must be called on every
  state-changing path including `assert false; setStateToUnknown();` fallbacks (CL 90181); a wrapping
  `DataResourceConsumer`/`ProgramResourceProvider` must call the original's `finished(..)` exactly once, also
  for skipped providers (CL 99842; data resources were lost in R8 partial).

### A.6 Count over candidates, not over the counters map (CL 89721)

```java
// ❌ zero-call callees never appear in `counters`, so they stay "single caller" and get pruned
for (Entry<ProgramMethod, Integer> entry : counters.entrySet()) {
  if (entry.getValue() != 1) {
    singleCallerMethodCandidates.remove(entry.getKey());
  }
}
// ✅
for (ProgramMethod callee : callees) {
  int callsToCallee = counters.getOrDefault(callee, 0);
  if (callsToCallee != 1) {
    singleCallerMethodCandidates.remove(callee);
  }
}
```

Failure mode: reachable code deleted by the single caller inliner. Severity: blocking.

### A.11 Strict pattern recognizers (CLs 131060, 129660, 129520, 114824)

```java
// ❌ CL 131060 (LargeProtoEnumRewriter): getStaticValue() returns DexValue.defaultForType(type)
// when there is no ConstantValue, so this accepts every int field
if (valueField == null || !(valueField.getStaticValue() instanceof DexValueInt)) {
  return false;
}
// ✅
if (valueField == null
    || !valueField.hasExplicitStaticValue()
    || !valueField.getStaticValue().isDexValueInt()) {
  return false;
}
```

```java
// ❌ sentinel scan: if the UNRECOGNIZED put-static is absent (closed enum), this consumes the rest of <clinit>
while (iterator.hasNext()) {
  CfInstruction insn = iterator.next();
  if (isPutUnrecognized(insn)) {
    break;
  }
  toRemove.add(insn);
}
// ✅ bail when the sentinel is missing (unrecognizedConstant == null) and test open + closed enums
if (unrecognizedConstant == null) {
  return false;
}
```

```java
// ❌ CL 114824: matches subclasses of the library type
field.getType().isLessThanOrEqualTo(factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)
// ✅
field.getType().isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)
```

Also: bail when a required constant is unknown (`null`), not only when it differs (CL 129660 dropped
non-empty `StringBuilder` initial content); check boolean constants are 0/1.

---

## B. Nondeterminism

### B.1 Hash order reaching output (CLs 86015, 87301, 78284, 77561, 73521, 121501)

Trigger: a new `HashMap`/`HashSet`/`IdentityHashMap`/`ConcurrentHashMap`, or `appInfo().classes()`, whose
iteration (a) creates/installs members or synthetics, (b) assigns names, (c) picks a merge target or
candidate, (d) writes rules, profiles, mapping lines, META-INF services, or (e) chooses which class an error
reports.

```java
// ❌ field order in the output class depends on IdentityHashMap order
Map<DexType, DexEncodedField> localOffsetFields = new IdentityHashMap<>();
...
clazz.appendStaticFields(localOffsetFields.values());

// ✅ sort at the point where order becomes observable
clazz.appendStaticFields(
    ListUtils.sort(localOffsetFields.values(), Comparator.comparing(DexEncodedField::getReference)));
```

```java
// ❌ CL 73521-style: fresh names assigned while iterating hash-ordered classes
for (DexProgramClass clazz : appView.appInfo().classes()) { assignFreshName(clazz); }
// ✅
for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) { assignFreshName(clazz); }
```

```java
// ❌ identity-hashed keys (KeepBindingSymbol has no stable hashCode)
Map<KeepBindingSymbol, KeepBindingInfo> bindings = new HashMap<>();
// ✅
Map<KeepBindingSymbol, KeepBindingInfo> bindings = new LinkedHashMap<>();
```

Deterministic alternatives in R8: `ListUtils.sort`/`ListUtils.destructiveSort`,
`appInfo().classesWithDeterministicOrder()`, `ProgramMethodSet.createLinked()`,
`Int2ReferenceLinkedOpenHashMap`, `LinkedHashMap`/`LinkedHashSet`, `TreeMap`.

Fix CLs that shipped this class of bug: 132740 (nondeterministic rewriting of `KeepInfoCollection`), 95881
(argument propagation), 110600 (SourceFile template expansion), 83461 (synthetics added to profile), 80442
(L8), 78284 (enum emulated dispatch).

Do not flag (calibration): membership-only sets; maps whose consumers are order-insensitive (CL 115961:
`FieldAccessInfoCollection` is identity-backed by design); collections sorted later at the output point.

### B.3 Ties need a total order (CLs 97665, 112800, 95165, 120480; fix 121643)

```java
// ❌ first-wins: the synthesizing context of a shared synthetic depends on thread/hash order
contexts.putIfAbsent(syntheticKey, method);
// ❌ arbitrary element of a hash collection
Collections.singleton(appView.appInfo().classes().iterator().next())
// ✅ minimum under a total order (as Outline#getSynthesizingContext does)
Collections.min(appView.appInfo().classes(), Comparator.comparing(DexClass::getType))
```

```java
// ❌ comparator used to order output returns 0 for distinct elements (e.g. compares length only)
int compare(A a, B b) { return Integer.compare(a.size(), b.size()); }
// ✅ break ties fully and assert
int cmp = Integer.compare(a.size(), b.size());
if (cmp == 0) { cmp = a.getReference().compareTo(b.getReference()); }
assert cmp != 0 || a == b;
```

Fix CL 121643 "Fix nondeterministic sorting of annotations in ApplicationWriter" is this bug in the writer.

### B.7 Worklists enqueue once (CL 132780)

```java
// ❌ raw deque with partial seen-guards: the same phi is enqueued twice and type propagation order varies
Deque<Phi> phiWorklist = new ArrayDeque<>();
for (Value v : affectedValues) {
  phiWorklist.addAll(v.uniquePhiUsers());
}
// ✅
WorkList<Phi> phiWorklist = WorkList.newIdentityWorkList();
for (Value v : affectedValues) {
  phiWorklist.addIfNotSeen(v.uniquePhiUsers());
}
```

Review comment (CL 132780): "Should we use another `WorkList.newIdentityWorkList` here to avoid duplicates
in the phiWorklist?" — landed as "Fix nondeterminism in type propagation".

---

## C. Concurrency and shared state

### C.1 Shared mutable state on worker threads (CLs 77242, 105442, 104681, 113581, 89484)

```java
// ❌ CL 77242: check-then-set on a shared BooleanBox from parallel Kotlin metadata parsing
if (!reported.get()) {
  reported.set();
  reporter.warning(..);
}
// ✅
private final AtomicBoolean reported = new AtomicBoolean();
if (!reported.getAndSet(true)) {
  reporter.warning(..);
}
```

```java
// ❌ CL 105442 / fix 105443: set filled from ThreadUtils.processItems workers
Set<DexType> seen = Sets.newIdentityHashSet();
// ✅
Set<DexType> seen = ConcurrentHashMap.newKeySet();
```

- CL 89484: `EdgeMatcher` stashed per-call callbacks in fields and was used from parallel code — create a
  fresh instance per task.
- Fixes for this class: 124180 (`AnnotationRemover.Builder` retention), 121221 (relocator `HashMap`), 102480
  (thread-unsafe `-dontwarn` pattern collection), 95261 (`ConcurrentModificationException`).
- How to decide: find the caller chain in `src/`; flag only if a writer is reachable from
  `ThreadUtils.processItems`, `TaskCollection`, Enqueuer waves or IR conversion waves. If all writers are on
  the main thread, don't flag (CL 136142, 136783 pushback).

### C.2 Timing is per thread (CLs 109360, 109041, 121661)

```java
// ❌ CL 109360: main-thread Timing used on a worker
executor.submit(() -> {
  try (Timing t = timing.begin("Write proguard map")) { ... }
});
// ✅
executor.submit(() -> {
  Timing threadTiming = timing.createThreadTiming("Write proguard map", options);
  ...
  threadTiming.end();
});
```

Review comment: "This is running on a thread != main thread. This needs to retrieve the timing for the
given thread using `Timing threadTiming = timing.createThreadTiming(...)`." Also don't keep a `Timing` in a
long-lived shared object (e.g. a `DexApplication` field); pass it as a parameter. Fix CL 121661 "Fix usage of
non-thread-local Timing usage".

### C.4 Nested waits deadlock (CL 122481; revert 109724)

```java
// ❌ outer tasks block on inner tasks submitted to the same fixed pool
ThreadUtils.processItems(virtualFiles, file -> {
  Map<DexString, DexString> mapping = computeMapping(file);
  List<Future<?>> inner = new ArrayList<>();
  for (DexProgramClass clazz : file.classes()) {
    inner.add(executorService.submit(() -> rewriteJumboStrings(clazz, mapping)));
  }
  ThreadUtils.awaitFutures(inner);     // deadlocks when #threads <= #virtual files
}, options.getThreadingModule(), executorService);

// ✅ chain instead of blocking: submit outer tasks, and enqueue the dependent inner tasks on completion
TaskCollection<?> tasks = new TaskCollection<>(options.getThreadingModule(), executorService);
for (VirtualFile file : virtualFiles) {
  tasks.submit(() -> {
    Map<DexString, DexString> mapping = computeMapping(file);
    for (DexProgramClass clazz : file.classes()) {
      tasks.submit(() -> rewriteJumboStrings(clazz, mapping));
    }
  });
}
tasks.await();
```

Review comment: "Can this deadlock with a `newFixedThreadPool` if #threads <= #virtual files?" The earlier
attempt was reverted (109724 "Revert 'Paralleize jumbo string rewriting'"). Severity: blocking.

---

## D. Null, empty, missing variants

### D.3 Empty collections (CLs 98600, 112100, 126401; fixes 126401, 82520)

```java
// ❌ records without instance fields
DexEncodedField first = sortedInstanceFields.get(0);
// ✅
if (sortedInstanceFields.isEmpty()) {
  return generateEmptyRecordEquals(..);
}
```

- Generating annotations: an omitted `@Target` means "applicable anywhere"; emitting an empty `@Target({})`
  means the opposite (CL 112100).
- Fallback searches that may also find nothing must handle that (CL 126401, landed as "Fix
  NullPointerException when inlining outline").
- Outlines with no positions (fix 82520 "Do not fail for outlines with no positions").

### D.6 Cover the whole family (CLs 84760, 79401, 122160, 97581, 92802)

```java
// ❌ CL 84760: generalized from Boolean to all boxed types, but a leftover branch still special-cases Boolean
if (abstractValue.isSingleBoxedBoolean() || abstractValue.isSingleBoxedInteger()) { ... }
// ✅ grep for the old predicate and convert every branch to the general form
```

```java
// ❌ package filter matches com.foobar too
descriptor.startsWith("Lcom/foo")
// ✅
descriptor.startsWith("Lcom/foo/")
```

- Kotlin: model legacy `Intrinsics.checkParameterIsNotNull` etc. alongside `checkNotNullParameter` (CL 79401).
- Hard-coded library lists include support-library and androidx variants (CL 122160).
- `UseCollector.registerDexValue` must trace types, arrays, nested annotations, enums
  (`isDexValueEnum`), fields, methods and `AnnotationDefault` values (CL 97581) — missing kinds produce
  incomplete trace-references keep rules that crash at runtime.
- New `DexProgramClass` property: set it in both the CF and DEX readers, preferably via a `final` constructor
  field (CL 92802; the DEX-reader omission forced revert 92780).

---

## E. Error handling and asserts

### E.1 External input: report, don't assert (CLs 100501, 85533, 69642, 77701; fix 100182)

```java
// ❌ desugared-library spec / api-versions.xml / generator main(): asserts are off in production
assert jsonArray.size() == 2;
assert api.getLength() == 1 : "Expected exactly one <api> element";
// ✅
if (jsonArray.size() != 2) {
  throw reporter.fatalError("Invalid desugared library specification: " + jsonArray);
}
if (api.getLength() != 1) {
  throw new RuntimeException("Expected exactly one <api> element");
}
```

Also check unsupported format versions up front at read time (CL 100501, `api-versions.xml` v4). Fix CL
100182 "Don't rely on assertions for validating api-version.xml". Calibration: internal invariants may and
should use `assert` — only flag input from users, files, or the network.

### E.2 No side effects in `assert` (CLs 95141, 71826)

```java
// ❌ CL 95141 (outliner): the removal only happens with -ea
if (toRemove.contains(instruction)) {
  assert toRemove.remove(instruction);
  ...
}
// ✅
if (toRemove.remove(instruction)) {
  ...
}
// or
boolean removed = toRemove.remove(instruction);
assert removed;
```

Review comment: "Should we maybe change `toRemove.contains` to `toRemove.remove` so that we have the same
behavior without assertions enabled?" CL 71826: assert-only helpers such as `DexOutputBuffer.assertZero()`
must restore the buffer position they advance.

### E.4 `reporter.error` does not abort (CLs 110122, 135342, 85941)

```java
// ❌ CL 110122: execution continues into code that asserts valid configuration
if (proguardConfigs.isEmpty()) {
  reporter.error("No keep rule files provided");
}
return new ProcessKeepRulesCommand(..);
// ✅
if (proguardConfigs.isEmpty()) {
  reporter.error("No keep rule files provided");
}
reporter.failIfPendingErrors();
return new ProcessKeepRulesCommand(..);
// or: throw reporter.fatalError("No keep rule files provided");
```

```java
// ❌ CL 135342: reports an error, then still returns the malformed value which gets added to the config
builder.error(new StringDiagnostic("Illegal assertion scope: " + s, origin));
return parsed;
// ✅
builder.error(new StringDiagnostic("Illegal assertion scope: " + s, origin));
return null;
```

Review comment: "Add `reporter.failIfPendingErrors()`. Otherwise the assert in `ProcessKeepRules` will fail.
Alternatively change `reporter.error` to `reporter.fatalError`."

---

## F. Object contracts

### F.3 equals/hashCode and lattice equality (CLs 113463, 85340, 92240)

```java
// ❌ CL 113463: kind added to equals but not hashCode → entries collapse, deferred annotation tracing dropped
@Override public boolean equals(Object o) { .. && kind == other.kind && annotationClass == other.annotationClass; }
@Override public int hashCode() { return annotationClass.hashCode(); }
// ✅
@Override public int hashCode() { return Objects.hash(annotationClass, kind); }
```

```java
// ❌ CL 92240 (KeepAnnotationCollectionInfo): this is <=, not ==
boolean isEqualTo(Info other) {
  if (isBottom() || other.isTop()) {
    return true;
  }
  ...
}
// ✅ symmetric
boolean isEqualTo(Info other) {
  if (isBottom() || other.isBottom()) {
    return isBottom() && other.isBottom();
  }
  ...
}
```

CL 85340: "Aren't these `containsAll` always going to return false since the transitive dependencies do not
override `equals()` and `hashCode()`?" — value objects used in sets need both (or a multiset when counts
matter).

---

## G. Arithmetic and limits

### G.1 Sign and `MIN_VALUE` (CLs 120720, 134760, 79421, 133006; fix 135080)

```kotlin
// ❌ CL 120720 (test sharding): Math.abs(Int.MIN_VALUE) is negative → that file runs in no shard
if (Math.abs(path.hashCode()) % shardCount != shardNumber) exclude()
// ✅
if (Math.floorMod(path.hashCode(), shardCount) != shardNumber) exclude()
```

```java
// ❌ CL 134760 (IntLongArithmeticRewriter): 0x80000000 has bitCount 1; as a long multiplier it is negative
if (Integer.bitCount(c) == 1 && c != 1) { rewriteToShift(..); }
// ✅
if (c > 1 && Integer.bitCount(c) == 1) { rewriteToShift(..); }
```

```java
// ❌ overflowing comparator
return a.size - b.size;
// ✅
return Integer.compare(a.size, b.size);
```

Narrowing checks: `((long) i) != l`. Loop-bound formulas must be re-derived for negative steps (CL 130560);
fix CL 135080 "Fix 32-bit int subtraction overflow in NaturalIntLoopOptimizer".

### G.2 Inclusive/exclusive limits (CLs 130140, 97100, 75097; revert 131660)

```java
// ❌ CL 130140: getMaxNumberOfTypes may return MAX_ENTRIES (U16BIT_MAX + 1)
if (numberOfTypeIds <= VirtualFile.getMaxNumberOfTypes(options)) { ... }
// ✅ match the sibling checks
if (numberOfTypeIds < VirtualFile.getMaxNumberOfTypes(options)) { ... }
```

Review comment: "This may return `MAX_ENTRIES`, which is `Constants.U16BIT_MAX + 1`. Should this be `<`
instead of `<=` like the two checks above?" One extra type id crashed Android 7 dex2oat. Also: define what
a threshold of `0` means (CL 97100: call the overload so `MAX_TRANSITIVE_DEPENDENCIES = 0` behaves), and
character ranges like `'A'..'Z'` need `<=`.

---

## H. API-level gating

### H.1 Never emit APIs above min API (CLs 129741, 116721, 116682)

```java
// ❌ CL 129741: backport template for BigInteger(byte[], int, int) (API 33) uses an API 30 method
public static BigInteger newBigInteger(byte[] val, int off, int len) {
  Objects.checkFromIndexSize(off, len, val.length);   // NoSuchMethodError below API 30
  ...
}
// ✅ manual bounds check usable at the template's lowest target
if (off < 0 || len < 0 || off > val.length - len) {
  throw new IndexOutOfBoundsException();
}
```

```java
// ❌ optimization inserting Unsafe#getAndSetObject (API N) unconditionally
// ✅
if (!options.isGeneratingDex() || options.getMinApiLevel().isLessThan(AndroidApiLevel.N)) {
  return false;
}
```

Reverts in this class: 99221 (backport `ExecutorService#close`), 101301 (desugaring `AutoCloseable#close`
everywhere), 122322 (desugar `CharSequence#isEmpty`), 114023/114060/117043 (Math backports).

---

## I. Keep-rule semantics

### I.1 `-keepclasseswithmembers` is conjunctive (CLs 120245, 89080, 92532; fixes 71703, 87784, 87304)

```text
-keepclasseswithmembers class * { @Foo <fields>; @Bar <methods>; }
```

- Pruning member rules (e.g. the annotation index in CL 120245 finding no class with `@Bar` methods): for
  `-keepclasseswithmembers` an unmatchable member rule means the whole rule matches nothing. Clearing the member
  list instead turns it into `-keep class *`-like over-keeping. Drop the rule or replace it with a
  never-matching member rule; clearing is only correct for plain `-keep`.
- `{ *; }` does not match a class without members.
- The implicit default `<init>()` member is added for empty member lists only in compat mode and never for
  `-keepclasseswithmembers` (CLs 92532, 94546; fix 71703 "Don't implicitly keep default constructors for
  keepclassmembers").
- Synthetic rules derived from user rules (`-nohorizontalclassmerging`, `-neverclassinline`) must keep the
  member rules (fixes 87784, 87304).

### I.2 Global options in library consumer rules (CLs 110122, 110962, 112940)

```java
// ❌ CL 112940: last-wins when the option appears in several rule files (order-dependent)
processKotlinNullChecks = value;
// ✅ combine like -maximumremovedandroidloglevel
processKotlinNullChecks = processKotlinNullChecks.meet(value);
```

When adding a global option, also wire it into `FilteredKeepRulesBuilder` (`writeComment(parser,
positionStart)`) and `ValidateLibraryConsumerRulesKeepRuleProcessor` (`handleRule`), so library consumer rule
validation rejects it and filtering passes it through. Globals in library rules silently change every
consuming app.

---

## K. Consumers

### K.3 `StringConsumer` may be chunked (CL 125600)

```java
// ❌ keeps only the last chunk
builder.setReportConsumer((s, handler) -> report = s);
// ✅
StringBuilder sb = new StringBuilder();
builder.setReportConsumer((s, handler) -> sb.append(s));
```

Review comment: "This is assuming we don't stream the report to the consumer. In principle this should
append." Decorating consumers must forward `finished(..)` to all delegates exactly once (CLs 130821, 99842).

---

## L. Tests that do not test what they claim

### L.1 Input must reach the path (CLs 97540, 97060, 101560, 134242, 125040)

```java
// ❌ identical branches: the expectation cannot detect the optimization
assertEquals(count, optimize ? 3 : 3);
// ✅
assertEquals(count, optimize ? 2 : 3);
```

```java
// ❌ feature under test is behind a testing flag that the test never sets
testForR8(parameters).addInnerClasses(getClass()).addKeepMainRule(Main.class)
    .run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines(EXPECTED);
// ✅
testForR8(parameters).addInnerClasses(getClass()).addKeepMainRule(Main.class)
    .addOptionsModification(o -> o.getTestingOptions().enableFoo = true)
    .compile()
    .inspect(this::inspectFooApplied)
    .run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines(EXPECTED);
```

Checks: the triggering value is used (the exact value that restarts a type switch; a referenced class that is
present so a `DexItemBasedConstString` is created); `main` dispatches into the code under test; printed output
depends on the behavior (call `b.bar()` after `b.mutate()`); for a fix CL, ask whether the test fails without
the fix. Precondition proxies don't count: assert `isJumboString` instructions, not
`hasLineNumberTable()` which `PcBasedDebugInfo` also satisfies (CL 125040).

### L.2 Assertions that can't fail (CLs 86681, 131240, 90841, 138940, 92301)

```java
// ❌ passes when no initializers are found
for (FoundMethodSubject init : clazz.allMethods(FoundMethodSubject::isInstanceInitializer)) {
  assertTrue(isUnboxed(init));
}
// ✅ (CL 86681: "Add an assert that 4 initializers are found ...")
List<FoundMethodSubject> inits = clazz.allMethods(FoundMethodSubject::isInstanceInitializer);
assertEquals(4, inits.size());
inits.forEach(init -> assertTrue(isUnboxed(init)));
```

```java
// ❌ CL 138940: Java assert in a test + catch without fail()
try { runArt(app, vm); } catch (AssertionError e) { assert e.toString().contains("VerifyError"); }
// ✅
AssertionError e = assertThrows(AssertionError.class, () -> runArt(app, vm));
assertTrue(e.toString().contains("VerifyError"));
```

```java
// ❌ runs but never asserts
.run(parameters.getRuntime(), Main.class);
// ✅
.run(parameters.getRuntime(), Main.class).assertSuccessWithEmptyOutput();
```

Also: assertions inside `if (insn.isX())` need a counter checked after the loop; assert
`getClassFilesForInnerClasses(..)` has size 1 before `.iterator().next()`.

### L.3 Pair absence with positive evidence (CLs 117983, 76762, 75097, 86760, 75145)

```java
// ❌ absence only: passes if an unrelated change removes the call
assertThat(method, not(invokesMethodWithHolder(atomicReferenceFieldUpdaterType)));
// ✅ also assert the replacement mechanism
assertThat(method, not(invokesMethodWithHolder(atomicReferenceFieldUpdaterType)));
assertThat(method, invokesMethodWithHolder(unsafeType));
```

```java
// ❌
.addEnumUnboxingInspector(i -> i.assertUnboxed(OtherEnum.class))
// ✅ positive and negative case
.addEnumUnboxingInspector(i -> i.assertUnboxed(OtherEnum.class).assertNotUnboxed(MyEnum.class))
```

Keep-rule/profile tests: add an unrelated member that must be removed (`isAbsent()`), a case where the
precondition is not live, and a case where a later setter replaces an earlier one; with
`containsString(NEW)` also assert `not(containsString(OLD))`. Question unexplained
`options.testing.allowTypeErrors = true`.

### L.4 Don't disable what you test (CLs 75906, 74260)

```java
// ❌ CL 75906: the test claims "no merging of methods with UNKNOWN api level" but disables the merger
.noHorizontalClassMerging()
// ✅
.addHorizontallyMergedClassesInspector(HorizontallyMergedClassesInspector::assertNoClassesMerged)
```

Review comment: "This actually disables the merger. To ensure no merging happened this should:
`.addHorizontallyMergedClassesInspector(inspector -> inspector.assertNoClassesMerged())`". Similarly, a test
that enables `enableEnumUnboxingDebugLogs` (which changes optimizer logic) must also run without it.

---

## M. Tools and scripts

### M.1 Propagate failures (CLs 76182, 138081, 93662, 117181; fix 81240)

```python
# ❌ bots stay green
print('API level must be minor[.major]')
return
subprocess.Popen(cmd).communicate()
# ✅
print('API level must be minor[.major]')
return -1
subprocess.run(cmd, check=True)
```

Scripts that intentionally continue after a failure (e.g. `perf.py` over several apps, CL 93662) must record
the failure and `sys.exit(1)` at the end. Fix CL 81240 "Fix check-cherry-pick.py to exit with 1 on any error".

### M.2 No silent fallbacks (CLs 79620, 99702, 113485, 101661; fix 100564)

```python
# ❌ unexpected branch silently falls back; results uploaded under the wrong branch
try:
  version = parse_branch(branch)
except ValueError:
  return branch
# ✅
except ValueError:
  raise Exception('Unexpected branch: ' + branch)
```

Also flag `shutil.rmtree(path, ignore_errors=True)`, skipping missing inputs, filing results under `HEAD`
instead of the tested hash, and a testing flag that is set while the code honoring it doesn't run. Fix CL
100564 "Ensure that we don't archive to latest for branches of dev".

### M.3 Units and regexes (CLs 119182, 125160)

```python
# ❌ CL 119182: time.sleep takes seconds — this sleeps ~3.5 days ("That's a really long time to sleep?")
time.sleep(5 * 60 * 1000)
# ✅
time.sleep(5 * 60)

# ❌ CL 125160: alternation order makes "100ms" parse as 100 minutes
re.match(r"([\d\.]+)(m|s|ms)?", s)
# ✅
re.match(r"([\d\.]+)(ms|m|s)?$", s)
```
