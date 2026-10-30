# Command reference

All commands read the jar path from `R8_ASSISTANT_JAR`, as set up in
[references/setup.md](setup.md). Every command prints its usage with `--help`.
When this page and the usage differ, follow the usage:

```shell
java -jar "$R8_ASSISTANT_JAR" --help
java -jar "$R8_ASSISTANT_JAR" instrument --help
```
The commands exit with 0 on success, 1 when the command fails, and 2 for usage
errors. On failure, a command prints a line that starts with `Error:`.

## instrument

Adds reflection logging to an APK, then aligns the APK and signs it.

```shell
java -jar "$R8_ASSISTANT_JAR" instrument \
  --input app/build/outputs/apk/debug/app-debug.apk \
  --output app/build/r8-assistant/app-debug-instrumented.apk
```

- `--input` or `-i`: the APK to instrument. It must be debuggable, or signed
  only with the debug key.
- `--output` or `-o`: the instrumented APK to write.
- `--sdk`: the Android SDK directory. Without it, `instrument` uses
  `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `sdk.dir` in
  `local.properties` in the current directory.
- `--build-tools`: the build-tools version to use, such as `36.0.0`. Without
  it, `instrument` uses the highest installed version that has `aapt2`,
  `zipalign` and `apksigner`.
- `--ks`, `--ks-pass`, `--ks-key-alias` and `--key-pass`: the keystore, its
  password, the key alias and the key password for signing. Passwords take
  the forms `pass:`, `env:` and `file:`, as in `apksigner`. Without them,
  `instrument` signs with `debug.keystore` in `~/.android` or in
  `ANDROID_USER_HOME`, with the password `android` and the alias
  `androiddebugkey`.
- `--verbose` or `-v`: print the build-tools commands that `instrument` runs.

Pin the build tools when the highest installed version fails:

```shell
java -jar "$R8_ASSISTANT_JAR" instrument \
  --input app/build/outputs/apk/debug/app-debug.apk \
  --output app/build/r8-assistant/app-debug-instrumented.apk \
  --build-tools 36.0.0 \
  --verbose
```

## suggest

Writes keep rules for the reflection in the logs.

```shell
java -jar "$R8_ASSISTANT_JAR" suggest \
  --log app/build/r8-assistant/debug-settings.json \
  --log app/build/r8-assistant/debug-sync.json \
  --rules app/proguard-rules.pro \
  --output app/build/r8-assistant/suggested.pro
```

- `--log`: a reflection log. Repeat it for several journeys.
- `--rules`: an existing rule file. Repeat it for several files. The output
  leaves out what these files already keep.
- `--output`: the file for the rules. Without it, `suggest` prints the rules.

## diff

Compares the logs with existing rules and classifies each rule.

```shell
java -jar "$R8_ASSISTANT_JAR" diff \
  --log app/build/r8-assistant/debug-settings.json \
  --log app/build/r8-assistant/debug-sync.json \
  --rules app/proguard-rules.pro \
  --output app/build/r8-assistant/proposed.pro
```

- `--log`: a reflection log. Repeat it for several journeys.
- `--rules`: a rule file to compare. Repeat it for several files.
- `--output`: the file for the proposed rules. Without it, `diff` prints the
  proposed rules after the summary.

## query

Filters the events of a log.

```shell
java -jar "$R8_ASSISTANT_JAR" query \
  --log app/build/r8-assistant/debug-settings.json \
  --class com.example.app.settings.DarkTheme \
  --member load \
  --event CLASS_GET_DECLARED_METHOD \
  --stacks
```

- `--log`: a reflection log to read. Repeat it for several logs.
- `--class`: only events on matching classes. Takes a keep rule class pattern
  with `*`, `**` and `?` wildcards. Repeat it for several patterns.
- `--member`: only events on matching member names, with `*` and `?`
  wildcards. Repeat it for several patterns.
- `--event`: only events of a matching type or API name, such as
  `CLASS_FOR_NAME` or `Class.forName`, with `*` and `?` wildcards. Repeat it
  for several patterns.
- `--stacks`: print the call stack of each event.
