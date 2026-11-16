# Turn logs into rules

Run the commands on the logs of all journeys, and act on the output with the
evidence levels and conventions in this file.

## Suggest rules

```shell
java -jar "$R8_ASSISTANT_JAR" suggest \
  --log app/build/r8-assistant/debug-settings.json \
  --output app/build/r8-assistant/suggested.pro
```

Pass `--log` once per journey to combine logs, and pass the rule files of the
project with `--rules` to leave out what they already keep:

```shell
java -jar "$R8_ASSISTANT_JAR" suggest \
  --log app/build/r8-assistant/debug-settings.json \
  --log app/build/r8-assistant/debug-sync.json \
  --rules app/proguard-rules.pro \
  --output app/build/r8-assistant/suggested.pro
```

For a crash, copy only the rules that cover the classes and members in the
retraced stack trace. Do not paste the whole output into a rule file.

## Query events

```shell
java -jar "$R8_ASSISTANT_JAR" query \
  --log app/build/r8-assistant/debug-settings.json \
  --class com.example.app.settings.DarkTheme \
  --stacks
```

Filter with `--class`, `--member` and `--event`, and add `--stacks` to print
the call stack of each event. The stack ties an event to the code that made
the call, which is the evidence to quote in the report.

```shell
java -jar "$R8_ASSISTANT_JAR" query \
  --log app/build/r8-assistant/debug-sync.json \
  --event CLASS_GET_DECLARED_FIELDS
```

## Compare logs with the rules

```shell
java -jar "$R8_ASSISTANT_JAR" diff \
  --log app/build/r8-assistant/debug-settings.json \
  --log app/build/r8-assistant/debug-sync.json \
  --rules app/proguard-rules.pro \
  --output app/build/r8-assistant/proposed.pro
```

Pass the log of every journey and every rule file that the project owns.
`diff` reports, for each rule, the logged events that the rule covers and the
rules that no event uses.

## Evidence levels

Classify every rule that you change:

- **Observed**: the logs have events for the classes or members of the rule.
  Keep the rule, narrowed to the logged classes and members.
- **Not observed**: the logs have no events for the classes of the rule, and a
  test or journey ran the code that uses them. Narrow or delete the rule; the
  release replay decides.
- **Undecided or conditional**: `diff` marks a rule `# Undecided, kept as is`
  or `# Conditional, kept as is` when the logs alone do not decide its
  selector, as listed in [references/cli.md](cli.md). Leave rules with these
  verdicts unchanged.
- **Uncovered**: no test or journey ran the code that uses the classes, or the
  log leaves out the reflection that reaches them, as listed in
  [references/capture.md](capture.md). Leave the rule unchanged and list it in
  the report.

"Not observed" is weak evidence. A deletion stays only when the release replay
passes for the flows that ran the code of the rule.

## Rule conventions

`suggest` and `diff` emit class, member and `-keepattributes` rules from the
logs. Apply these conventions where the logs alone do not decide:

- Name exact classes. Never use package-wide wildcards.
- Keep only the members that the logs show, and the constructors of classes
  that the app creates by reflection. In R8 full mode, `-keep class` without
  members does not keep the default constructor, so list `<init>` explicitly.
- Add `allowobfuscation` only when no code looks up the name as a string.
  Gson and Moshi read field names, and `Class.forName`, `ClassLoader.loadClass`
  and `ResourceBundle.getBundle` read class names.
- For a `SERVICE_LOADER_LOAD` event whose result lists no providers, keep the
  provider classes listed in `META-INF/services` with their no-argument
  constructors.
- For generic supertype, method or field events (`CLASS_GET_GENERIC_*`,
  `METHOD_GET_GENERIC_*`, `FIELD_GET_GENERIC_TYPE`), the log records
  `<raw type><...>` without its type arguments. When a library such as
  Retrofit, Gson or Moshi reflects on those type arguments (for example
  `ResponseType` in `Call<ResponseType>`), keep the type-argument classes too.
- Match an anonymous class, such as `SyncRepository$1`, by its supertype with
  `class * extends` or `class * implements`. The number in its name changes
  when the code changes.

## Place and document each rule

Put each rule in the rule file that owns the code: the `proguard-rules.pro` of
the app module for app code, and the consumer rules file of a library module
for code in that module.

To find the owning file of an existing rule, search `configuration.txt`, as
described in [references/setup.md](setup.md).

Write a provenance comment on the line before each rule, with the call site
and the test or journey:

```proguard
# r8-assistant: Class.forName in ThemeLoader.load (journey settings).
-keep class com.example.app.settings.DarkTheme {
  <init>();
}

# r8-assistant: Gson reads the fields of SyncResponse (journey sync).
-keepclassmembers class com.example.app.sync.SyncResponse {
  <fields>;
  <init>();
}
```

For the official guidance on keep rules, see
[Add keep rules](https://developer.android.com/topic/performance/app-optimization/add-keep-rules).
