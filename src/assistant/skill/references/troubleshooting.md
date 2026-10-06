# Troubleshooting

Each section names a symptom, its cause, and the action to take.

## A command reports a usage error

The command exits with 2 and prints a line that starts with `Error:`, such as
an unknown option or a missing value. Print the usage of the command, and fix
the command line with the options that the usage lists:

```shell
java -jar "$R8_ASSISTANT_JAR" instrument --help
```

## `instrument` refuses the APK

The APK is neither debuggable nor signed only with the debug key. Only
instrument the debug build, never the release APK. Never change the release
signing config of the project for this.

## `instrument` finds no build tools

The SDK has no build-tools version with `aapt2`, `zipalign` and `apksigner`,
or `instrument` does not find the SDK. Run the commands from the project root,
so that `instrument` reads `sdk.dir` from `local.properties`, or set
`ANDROID_HOME`. Install build tools as described in
[references/setup.md](setup.md).

## The project signs debug builds with its own keystore

`instrument` signs with the default debug keystore, so the instrumented APK
does not install over the debug build, and instrumented tests do not run
against it. Pass the keystore of the project:

```shell
java -jar "$R8_ASSISTANT_JAR" instrument \
  --input app/build/outputs/apk/debug/app-debug.apk \
  --output app/build/r8-assistant/app-debug-instrumented.apk \
  --ks app/debug.keystore \
  --ks-pass env:DEBUG_KEYSTORE_PASSWORD \
  --ks-key-alias debug
```

## `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed app has another signature, for example a build from an app store
or a build signed with another key. Ask the user before you uninstall it,
because uninstalling deletes the data of the app:

```shell
adb uninstall com.example.app
```

## `INSTALL_FAILED_VERSION_DOWNGRADE`

The installed app has a higher version code. For a debuggable installed app,
install with `adb install -r -d`. Otherwise ask the user before you uninstall
the app.

## No log file on the device

- The installed APK is not the instrumented one. Install it again with
  `adb install -r`, and check that no Gradle task replaced it.
- The path uses another application ID. Check the ID of the installed APK, as
  described in [references/setup.md](setup.md).
- The device runs API level 15 or lower, which gets no log.
- No app code made a reflective call before the pull. Run the journey further,
  or treat the journey as one without reflection.

## Empty or short log

No app code reflected during the journey, the reflection happened inside
ignored code (see the limits in [references/capture.md](capture.md)), or the
app restarted and truncated the log. An empty log is no evidence for deleting
a rule. Mark the rules of that journey as uncovered unless another journey
covers them.

## Events from earlier steps are missing

A new app process truncated the log: the app restarted, a second process
started, or a test runner started a process per test. Split the journey at the
restart and pull the log before each restart.

## A request line has no result line

The call threw, or the process died before the call returned. Find the
exception in logcat. In a debug log, this is often expected behavior of the
app, such as probing for an optional class.

## The crash has no matching log event

The reflection runs inside `androidx.`, `kotlin.` or `kotlinx.` code, or in
code that the journey did not reach in the debug build. Write the rule from the
retraced stack trace, as described in [references/rules.md](rules.md).

## `retrace` is missing

Install the SDK command line tools, as described in
[references/setup.md](setup.md). On Windows, run `retrace.bat`.

## Several devices are connected

Ask the user which device to use, and set `ANDROID_SERIAL` for every adb
command.

## A flow needs a login or other private input

Do not guess credentials. Ask the user to sign in on the device, or switch to
manual mode as described in [references/journeys.md](journeys.md).

## The release build fails in R8

Read the R8 error. Missing class warnings come from the rules or the
dependencies of the project, not from this skill. Fix them before continuing,
and never add `-dontwarn` for classes that the app uses at runtime.
