# Reports

Present the report in your reply, and save a copy as
`app/build/r8-assistant/report.md`. Quote evidence from the logs, the `diff`
output and the retraced logcat. Never claim more than the evidence shows.

## Crash fix report

Write these sections:

1. **Crash**: the retraced exception and its top app frames.
2. **Cause**: what R8 removed or renamed, with the evidence: the log event and
   its call site, or the retraced stack trace when the log has no event.
3. **Fix**: the added rules with their provenance comments, and the file that
   owns them.
4. **Verification**: for each journey, the result of the release replay.
5. **Device**: the APK that is installed on the device at the end.

Example:

```markdown
## Release crash fix: dark theme in settings

**Crash** (retraced): java.lang.ClassNotFoundException:
com.example.app.settings.DarkTheme at ThemeLoader.load(ThemeLoader.java:42).

**Cause**: R8 removed DarkTheme, which ThemeLoader.load loads with
Class.forName. Log event CLASS_FOR_NAME com.example.app.settings.DarkTheme
from ThemeLoader.load(ThemeLoader.java:42), journey settings.

**Fix** in app/proguard-rules.pro:

    # r8-assistant: Class.forName in ThemeLoader.load (journey settings).
    -keep class com.example.app.settings.DarkTheme {
      <init>();
    }

**Verification**: journey settings. The release replay passed every action, and
the retraced logcat has no failures.

**Device**: the release build app-release.apk is installed.
```

## Narrowing report

Write these sections:

1. **Summary**: the number of rules narrowed, deleted and left unchanged, and
   the DEX size before and after, in bytes and as a percentage.
2. **Diff**: the change to each rule file, as a unified diff.
3. **Evidence per rule**: a table with the rule, its evidence level
   (observed, not observed or uncovered), the evidence, and the change.
4. **Deleted rules**: for each rule deleted as not observed, the journey that
   ran its code and the code that the journey reached.
5. **Uncovered rules**: each rule left unchanged because no journey reached
   its code, and the flow that would cover it.
6. **Coverage**: each journey with its layer (journey file, instrumented test,
   exploration or manual mode) and the screens that it reached.
7. **Verification**: for each journey, the result of the release replay.
8. **Risks**: flows that no journey covered, reflection that the log leaves
   out, and broad rules from dependencies that stayed unchanged.
9. **Device**: the APK that is installed on the device at the end.

State that the changes are not committed, and that the user reviews them
before committing.

Example of the evidence table:

| Rule | Level | Evidence | Change |
| --- | --- | --- | --- |
| `-keep class com.example.app.model.** { *; }` | Observed | `CLASS_GET_DECLARED_FIELDS` on `SyncResponse` from Gson, journey sync | Narrowed to the fields of `SyncResponse` |
| `-keep class com.example.app.legacy.** { *; }` | Not observed | Journeys settings and sync ran `LegacyImporter.run`, no events | Deleted |
| `-keep class com.example.app.share.** { *; }` | Uncovered | No journey opened the share screen | Unchanged |
