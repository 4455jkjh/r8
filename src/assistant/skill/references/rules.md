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
  journey ran the code that uses them. Confirm in the code that the journey
  reached it. Narrow or delete the rule; the release replay decides.
- **Uncovered**: no journey ran the code that uses the classes, or the log
  leaves out the reflection that reaches them, as listed in
  [references/capture.md](capture.md). Leave the rule unchanged and list it in
  the report.

"Not observed" is weak evidence. A deletion stays only when the release replay
passes for the journeys that ran the code of the rule.

## Rule conventions

- Name exact classes. Never use package-wide wildcards.
- Keep only the members that the logs show, and the constructors of classes
  that the app creates by reflection. In R8 full mode, `-keep class` without
  members does not keep the default constructor, so list `<init>` explicitly.
- Add `allowobfuscation` only when no code looks up the name as a string.
  Gson and Moshi read field names, and `Class.forName`, `ClassLoader.loadClass`
  and `ResourceBundle.getBundle` read class names.
- Keep the `volatile` modifier in rules for fields of atomic field updaters.
  Without it, the release build fails with
  `IllegalArgumentException: Must be volatile type`.
- Keep the provider classes listed in `META-INF/services` for a
  `SERVICE_LOADER_LOAD` event, with their no-argument constructors.
- For a `RESOURCE_BUNDLE_GET_BUNDLE` event, keep the bundle classes of all
  locales in one rule, `-keep class <base>, <base>_* { <init>(); }`, where
  `<base>` is the base name in the first argument. The log shows only the
  device locale.
- A `RESOURCE_BUNDLE_GET_BUNDLE` result of `java.util.PropertyResourceBundle`
  means that the bundle is a properties file, which needs no class rule.
- For a `CLASS_GET_GENERIC_SUPERCLASS` or `CLASS_GET_GENERIC_INTERFACES` event
  with a parameterized type in the debug result, add
  `-keepattributes Signature` and `-keep,allowobfuscation,allowshrinking` rules
  for the class and its generic supertype. R8 keeps attributes only for classes
  that a rule matches.
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
and the journey, or with the retraced exception:

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

## Write rules from a retraced stack trace

When the log has no event for a crash, write the rule from the retraced stack
trace. Retrace first, so that every class name is an original name:

- `ClassNotFoundException` or `NoClassDefFoundError`: R8 removed or renamed
  the class. Keep the class name.
- `NoSuchMethodException`: keep the method with its parameter types.
- `NoSuchFieldException`: keep the field.
- `InstantiationException`, including `Fragment$InstantiationException`: keep
  the constructor, usually `<init>()`.
- `IllegalArgumentException: Must be volatile type`: keep the field with its
  `volatile` modifier.
- `SerializationException` from kotlinx.serialization: keep the `Companion`
  field and the `serializer` method of the class.
- `MissingResourceException` for a bundle base name: keep the bundle classes,
  as for a `RESOURCE_BUNDLE_GET_BUNDLE` event in the rule conventions.
- `ClassCastException` from `Class` to `ParameterizedType`, or a library error
  about a missing type argument: keep the generic signature, as for the
  generic events in the rule conventions.
- `ExceptionInInitializerError`: retrace the cause and apply the matching case
  in this list.
- Data that goes missing, such as empty fields after Gson or Moshi parsing:
  keep the fields and the constructor of the model class.

```proguard
# r8-assistant: Fragment$InstantiationException for SettingsFragment
# (retraced logcat, journey settings).
-keep class com.example.app.settings.SettingsFragment {
  public <init>();
}
```

For the official guidance on keep rules, see
[Add keep rules](https://developer.android.com/topic/performance/app-optimization/add-keep-rules).
