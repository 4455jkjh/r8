# `r8-cl-review-correctness`: R8 Correctness Examples

On-demand before/after patterns for the starred (`*`) rules in [../SKILL.md](../SKILL.md).

## Quick Index

| Section | Topic |
| --- | --- |
| A.3 | Strict pattern recognizers (`hasExplicitStaticValue`, sentinels, exact types) |
| B.1, B.3 | Hash order reaching output, total orders for ties |
| C.1, C.2, C.4 | Worker shared state, per-thread `Timing`, nested-wait deadlocks |
| D.3 | Covering the whole family (leftovers, prefixes, `DexValue`, CF+DEX readers) |
| E.1, E.2 | External input errors vs `assert`, `reporter.error` non-abort |
| G.1 | DEX limits (`MAX_ENTRIES` `<` vs `<=`) |
| H.1 | Never emit APIs above min API |
| I.1, I.2 | `-keepclasseswithmembers` conjunction, global options in library rules |
| K.3 | Chunked `StringConsumer` |
| L.1–L.4 | Tests that don't reach the path, can't fail, lack positive evidence, or disable the feature |

---

## A. R8 Logic Slips

### A.3 Strict pattern recognizers
```java
// Bad: getStaticValue() returns DexValue.defaultForType when absent
if (valueField == null || !(valueField.getStaticValue() instanceof DexValueInt)) {
  return false;
}
// Good
if (valueField == null
    || !valueField.hasExplicitStaticValue()
    || !valueField.getStaticValue().isDexValueInt()) {
  return false;
}
```
```java
// Bad: matches subclasses of the library type
appInfo.isSubtype(field.getType(), factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)
// Good
field.getType().isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)
```
Also bail when a required constant is unknown (`null`) or a scan sentinel (e.g. `UNRECOGNIZED`) is absent.
**Say:** "`getStaticValue()` returns a default `DexValue` when no explicit value exists, so this accepts any static `int` field. Guard with `valueField.hasExplicitStaticValue()`."

---

## B. Nondeterminism

### B.1 Hash order reaching output
```java
// Bad: output field order depends on IdentityHashMap iteration
Map<DexType, DexEncodedField> fields = new IdentityHashMap<>();
clazz.appendStaticFields(fields.values());
// Good: sort at the observable point (or use classesWithDeterministicOrder / LinkedHashMap)
clazz.appendStaticFields(
    ListUtils.sort(fields.values(), Comparator.comparing(DexEncodedField::getReference)));
```
Preserve linked types through every `copy()`/constructor/getter. Do not flag membership-only sets or maps whose consumer sorts later.
**Say:** "Iterating `IdentityHashMap` here makes the emitted field order nondeterministic. Sort the values by reference before appending."

### B.3 Ties need a total order
```java
// Bad: first-wins depends on thread/hash order
contexts.putIfAbsent(syntheticKey, method);
// Good: minimum under a total order
Collections.min(candidates, Comparator.comparing(DexClass::getType))
```
For output comparators, break all ties and assert `cmp != 0 || a == b`.
**Say:** "Using `putIfAbsent` here makes the chosen synthesizing context depend on processing order. Pick a deterministic minimum (e.g. by reference) instead."

---

## C. Concurrency & Shared State

### C.1 Shared mutable state on worker threads
```java
// Bad (under ThreadUtils.processItems / TaskCollection / waves)
if (!reported.get()) { reported.set(); reporter.warning(..); }
Set<DexType> seen = Sets.newIdentityHashSet();
// Good
if (!reported.getAndSet(true)) { reporter.warning(..); } // AtomicBoolean
Set<DexType> seen = ConcurrentHashMap.newKeySet();
```
Flag only when a writer is actually reachable from parallel workers; single-threaded code is fine.
**Say:** "This runs on worker threads, so mutating `Sets.newIdentityHashSet()` here can race. Use `ConcurrentHashMap.newKeySet()` (or collect per task and merge afterward)."

### C.2 `Timing` is per thread
```java
// Bad: main-thread Timing used on worker
executor.submit(() -> { try (Timing t = timing.begin("Write map")) { ... } });
// Good
executor.submit(() -> {
  Timing threadTiming = timing.createThreadTiming("Write map", options);
  ...
  threadTiming.end();
});
```
**Say:** "This runs on a worker thread, so it should create a per-thread timing with `timing.createThreadTiming(...)` instead of using the main-thread `timing`."

### C.4 Nested waits deadlock
```java
// Bad: outer task blocks on inner tasks submitted to the same fixed pool
ThreadUtils.processItems(virtualFiles, file -> {
  List<Future<?>> inner = new ArrayList<>();
  for (DexProgramClass clazz : file.classes()) { inner.add(executorService.submit(() -> work(clazz))); }
  options.getThreadingModule().awaitFutures(inner); // deadlocks when #threads <= #virtual files
}, options.getThreadingModule(), executorService);

// Good: submit dependent tasks onto SynchronizedTaskCollection without blocking outer workers
SynchronizedTaskCollection<?> tasks = new SynchronizedTaskCollection<>(options, executorService);
for (VirtualFile file : virtualFiles) {
  tasks.submit(() -> {
    for (DexProgramClass clazz : file.classes()) { tasks.submit(() -> work(clazz)); }
  });
}
tasks.await();
```
**Say:** "Can this deadlock on a fixed thread pool when `#threads <= #virtual files`, since outer tasks block waiting on inner tasks in the same pool? Chain them via `SynchronizedTaskCollection` instead."

---

## D. Null, Empty & Missing Families

### D.3 Cover the whole family
```java
// Bad: matches com.foobar as well as com.foo
descriptor.startsWith("Lcom/foo")
// Good
descriptor.startsWith("Lcom/foo/")
```
Also check: legacy Kotlin `Intrinsics` names alongside `checkNotNullParameter`, both `support` and `androidx` variants, every `DexValue` kind in `UseCollector.registerDexValue`, and both CF and DEX readers for new `DexProgramClass` properties.
**Say:** "Without a trailing `/`, `startsWith("Lcom/foo")` will also match sibling packages like `com.foobar`."

---

## E. Error Handling & Assertions

### E.1 External input: report, don't `assert`
```java
// Bad: asserts are off in production builds
assert jsonArray.size() == 2;
// Good
if (jsonArray.size() != 2) {
  throw reporter.fatalError("Invalid desugared library specification: " + jsonArray);
}
```
**Say:** "Since assertions are disabled in production, validating external input with `assert` will be skipped. Throw `reporter.fatalError(...)` instead."

### E.2 `reporter.error` does not abort
```java
// Bad: execution continues into code that assumes a valid config
if (proguardConfigs.isEmpty()) {
  reporter.error("No keep rule files provided");
}
return new ProcessKeepRulesCommand(..);
// Good
if (proguardConfigs.isEmpty()) {
  reporter.error("No keep rule files provided");
}
reporter.failIfPendingErrors();
return new ProcessKeepRulesCommand(..);
```
**Say:** "`reporter.error(...)` does not stop execution, so this still builds an invalid command. Call `reporter.failIfPendingErrors()` or use `reporter.fatalError(...)`."

---

## G. DEX Limits

### G.1 DEX limits & widths
```java
// Bad: getMaxNumberOfFields returns MAX_ENTRIES (U16BIT_MAX + 1)
if (numberOfFieldIds <= VirtualFile.getMaxNumberOfFields(options)) { ... }
// Good
if (numberOfFieldIds < VirtualFile.getMaxNumberOfFields(options)) { ... }
```
**Say:** "`getMaxNumberOfFields` can return `MAX_ENTRIES` (`U16BIT_MAX + 1`), so `<=` allows one field too many. Should this be `<` to match the sibling checks?"

---

## H. API-Level Gating

### H.1 Never emit APIs above min API
```java
// Bad: backport template for API 33 calls an API 30 method
public static BigInteger newBigInteger(byte[] val, int off, int len) {
  Objects.checkFromIndexSize(off, len, val.length); // NoSuchMethodError below API 30
  ...
}
// Good: manual check safe at the lowest target API
if (off < 0 || len < 0 || off > val.length - len) {
  throw new IndexOutOfBoundsException();
}
```
**Say:** "`Objects.checkFromIndexSize` was added in API 30, so this backport will throw `NoSuchMethodError` on older devices. Use a manual bounds check instead."

---

## I. Keep-Rule Semantics

### I.1 `-keepclasseswithmembers` is conjunctive
```text
-keepclasseswithmembers class * { @Foo <fields>; @Bar <methods>; }
```
If no class has `@Bar` methods, the whole rule matches nothing—drop it or replace it with a never-matching rule. Clearing the member list turns it into an over-keeping `-keep class *` rule (only valid for plain `-keep`). `{ *; }` does not match memberless classes, and `-keepclasseswithmembers` never gets an implicit `<init>()`.
**Say:** "Clearing the member list on `-keepclasseswithmembers` turns an unmatchable rule into one that keeps all classes. Drop the rule (or replace it with a never-matching member rule) instead."

### I.2 Global options in library consumer rules
```java
// Bad: last-wins across rule files
processKotlinNullChecks = value;
// Good: order-independent meet
processKotlinNullChecks = processKotlinNullChecks.meet(value);
```
Also wire every new global option into `FilteredKeepRulesBuilder` and `ValidateLibraryConsumerRulesKeepRuleProcessor#handleRule` so library consumer rules reject it.
**Say:** "Assigning directly makes this option depend on rule-file order. Combine repeated values with `meet(...)` and reject it in `ValidateLibraryConsumerRulesKeepRuleProcessor`."

---

## K. Consumers

### K.3 `StringConsumer` may be chunked
```java
// Bad: keeps only the last chunk
builder.setReportConsumer((s, handler) -> report = s);
// Good
StringBuilder sb = new StringBuilder();
builder.setReportConsumer((s, handler) -> sb.append(s));
```
**Say:** "`StringConsumer#accept` can be called with multiple chunks, so overwriting `report` drops earlier output. Append to a `StringBuilder` instead."

---

## L. Tests That Cannot Fail

### L.1 Input must reach the path
```java
// Bad: identical expectations cannot detect the optimization
assertEquals(count, optimize ? 3 : 3);
// Good
assertEquals(count, optimize ? 2 : 3);
```
Also verify any testing flag is enabled, `main` reaches the path under test, and a fix CL's regression test fails without the fix.
**Say:** "Both arms expect `3`, so this assertion passes whether the optimization ran or not."

### L.2 Assertions that can't fail
```java
// Bad: vacuously passes if 0 initializers match
for (FoundMethodSubject init : clazz.allMethods(FoundMethodSubject::isInstanceInitializer)) {
  assertTrue(isUnboxed(init));
}
// Good
List<FoundMethodSubject> inits = clazz.allMethods(FoundMethodSubject::isInstanceInitializer);
assertEquals(4, inits.size());
inits.forEach(init -> assertTrue(isUnboxed(init)));
```
Also flag Java `assert` in tests, `try/catch` without `fail()` (use `assertThrows`), and `.run(...)` with no output assertion.
**Say:** "If `allMethods(...)` returns an empty list, this loop passes without checking anything. Assert the expected method count first."

### L.3 Pair absence with positive evidence
```java
// Bad: passes even if an unrelated change removes the call
assertThat(method, not(invokesMethodWithHolder(atomicReferenceFieldUpdaterType)));
// Good: also assert the replacement
assertThat(method, not(invokesMethodWithHolder(atomicReferenceFieldUpdaterType)));
assertThat(method, invokesMethodWithHolder(unsafeType));
```
**Say:** "Asserting only absence can pass for the wrong reason—also assert that the replacement call (or a negative control) is present."

### L.4 Don't disable what you test
```java
// Bad: disables the merger instead of testing that it didn't merge
.noHorizontalClassMerging()
// Good
.addHorizontallyMergedClassesInspector(HorizontallyMergedClassesInspector::assertNoClassesMerged)
```
**Say:** "This disables horizontal merging rather than checking that nothing merged. Use `addHorizontallyMergedClassesInspector(...::assertNoClassesMerged)` instead."
