# Release builds and verification

Every result of this skill rests on a real R8 release build of the app. The
examples use the build type `release`, the journey `settings` and the
application ID `com.example.app`.

## Build the release APK

Sign the release APK with the debug key when the project configures no release
signing key, or so that it installs over a debug build with the same
application ID:

```shell
./gradlew :app:assembleRelease \
  -Pandroid.injected.signing.store.file="$HOME/.android/debug.keystore" \
  -Pandroid.injected.signing.store.password=android \
  -Pandroid.injected.signing.key.alias=androiddebugkey \
  -Pandroid.injected.signing.key.password=android
```

The properties override the signing config for this build only, and leave
the project files unchanged. If `ANDROID_USER_HOME` is set, the keystore is
`debug.keystore` in that directory. The build writes:

- `app/build/outputs/apk/release/app-release.apk`: the release APK.
- `app/build/outputs/mapping/release/mapping.txt`: the mapping file for
  retrace.
- `app/build/outputs/mapping/release/configuration.txt`: every rule that R8
  read, with the file that each rule came from.

With product flavors, build `:app:assembleFreeRelease`. The outputs are
`app/build/outputs/apk/free/release/app-free-release.apk` and
`app/build/outputs/mapping/freeRelease/mapping.txt`. For another minified
build type, such as `benchmark`, use the task and output paths of that build
type.

In PowerShell, quote each `-P` argument:

```powershell
.\gradlew.bat :app:assembleRelease "-Pandroid.injected.signing.store.file=$HOME\.android\debug.keystore" "-Pandroid.injected.signing.store.password=android" "-Pandroid.injected.signing.key.alias=androiddebugkey" "-Pandroid.injected.signing.key.password=android"
```

## Measure the DEX size

```shell
unzip -l app/build/outputs/apk/release/app-release.apk 'classes*.dex' | tail -n 1
```

The last line shows the total uncompressed size of the DEX files in bytes.
Record it for the baseline and after narrowing, from builds of the same
variant. In PowerShell:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path app/build/outputs/apk/release/app-release.apk).Path)
($zip.Entries | Where-Object { $_.Name -like 'classes*.dex' } | Measure-Object -Property Length -Sum).Sum
$zip.Dispose()
```

## Reproduce a crash

Install the release APK, find its launcher activity, clear logcat, and start
the app:

```shell
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package resolve-activity --brief com.example.app
adb logcat -c
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.example.app/com.example.app.MainActivity
```

Run the journey to completion, dismissing any permission or onboarding dialogs,
then save logcat and retrace it even if the screen appears to succeed:

```shell
adb logcat -d > app/build/r8-assistant/logcat-settings.txt
"$ANDROID_HOME/cmdline-tools/latest/bin/retrace" \
  app/build/outputs/mapping/release/mapping.txt \
  app/build/r8-assistant/logcat-settings.txt \
  > app/build/r8-assistant/retraced-settings.txt
```

Search the retraced logcat for failures:

```shell
grep -n -E \
  -e "FATAL EXCEPTION|ClassNotFoundException|NoClassDefFoundError" \
  -e "NoSuchMethodException|NoSuchFieldException|InstantiationException" \
  -e "IllegalAccessException|ExceptionInInitializerError" \
  -e "MissingResourceException|NoSuchAlgorithmException" \
  -e "SerializationException|Must be volatile" \
  app/build/r8-assistant/retraced-settings.txt
```

Count matches from the app: both `FATAL EXCEPTION` blocks (`Process:
com.example.app`) and caught exceptions from the app PID when a feature fails
or falls back. Quote the retraced exception and its top app frames in the
report.

## Release replay

Install the release APK without instrumentation:

```shell
adb install -r app/build/outputs/apk/release/app-release.apk
```

For each UI journey, stop the app, clear logcat, start the launcher activity,
and run the journey with all of its "Verify" and "Check" actions. Also run
any release-compatible UI tests, as described in
[references/journeys.md](journeys.md):

```shell
adb shell am force-stop com.example.app
adb logcat -c
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.example.app/com.example.app.MainActivity
```

Then save, retrace and search logcat as in the crash reproduction. The release
replay passes only when, for every exercised flow and release test:

- Every action succeeds and every expectation holds. Data that the debug build
  shows, such as list entries or parsed fields, also shows in the release
  build.
- The app process has no `FATAL EXCEPTION` and no ANR.
- The search finds no failure in a stack trace from app code.

At the end, tell the user which APK is installed on the device. Offer to
reinstall the debug build with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.
