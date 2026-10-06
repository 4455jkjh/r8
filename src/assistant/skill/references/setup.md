# Setup

Run every command from the project root. The examples use the app module
`app`, the application ID `com.example.app` and the state directory
`app/build/r8-assistant/`. Replace them with the values of the project.

## Download the jar

The skill pins version {{R8_ASSISTANT_VERSION}} of the R8 assistant jar,
`com.android.tools:r8-assistant` on Google Maven. The download step checks the
SHA-256 of the jar and refuses a jar that does not match. When
`R8_ASSISTANT_JAR` is already set, for example to a local build of the jar,
the step uses it as is.

```shell
r8_assistant_sha256() {
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "$1" | cut -d ' ' -f 1
  else
    shasum -a 256 "$1" | cut -d ' ' -f 1
  fi
}

r8_assistant_fetch() {
  local version="{{R8_ASSISTANT_VERSION}}"
  local expected="{{R8_ASSISTANT_SHA256}}"
  if [ -n "${R8_ASSISTANT_JAR:-}" ]; then
    echo "Using R8_ASSISTANT_JAR=$R8_ASSISTANT_JAR"
    return 0
  fi
  case "$version" in
    "" | *[!0-9A-Za-z.-]*)
      echo "This copy of the skill has no released jar version." >&2
      echo "Set R8_ASSISTANT_JAR to an r8-assistant jar." >&2
      return 1
      ;;
  esac
  case "$expected" in
    "" | *[!0-9a-fA-F]*)
      echo "This copy of the skill has no released jar SHA-256." >&2
      echo "Set R8_ASSISTANT_JAR to an r8-assistant jar." >&2
      return 1
      ;;
  esac
  local repo="${R8_ASSISTANT_MAVEN_REPO:-https://dl.google.com/android/maven2}"
  local dir="$HOME/.android/cache/r8-assistant/$version"
  local jar="$dir/r8-assistant-$version.jar"
  local url="$repo/com/android/tools/r8-assistant/$version"
  url="$url/r8-assistant-$version.jar"
  if [ ! -f "$jar" ] || [ "$(r8_assistant_sha256 "$jar")" != "$expected" ]; then
    mkdir -p "$dir" || return 1
    if ! curl -fsSL -o "$jar.part" "$url"; then
      rm -f "$jar.part"
      echo "Download failed: $url" >&2
      return 1
    fi
    if [ "$(r8_assistant_sha256 "$jar.part")" != "$expected" ]; then
      rm -f "$jar.part"
      echo "SHA-256 mismatch for $url. Refusing to use it." >&2
      return 1
    fi
    mv -f "$jar.part" "$jar" || return 1
  fi
  export R8_ASSISTANT_JAR="$jar"
  echo "R8_ASSISTANT_JAR=$R8_ASSISTANT_JAR"
}

r8_assistant_fetch
```

The step prints the path of the jar. If your shell does not keep variables
between commands, set `R8_ASSISTANT_JAR` to that path at the start of every
command that runs the jar, or run the step again. It reuses the verified jar.

On Windows, run the same step in PowerShell:

```powershell
$version = "{{R8_ASSISTANT_VERSION}}"
$expected = "{{R8_ASSISTANT_SHA256}}"
if ($env:R8_ASSISTANT_JAR) {
  Write-Output "Using R8_ASSISTANT_JAR=$env:R8_ASSISTANT_JAR"
} else {
  if ($version -notmatch '^[0-9A-Za-z.-]+$' -or $expected -notmatch '^[0-9a-fA-F]{64}$') {
    throw "This copy of the skill has no released jar version. Set R8_ASSISTANT_JAR to an r8-assistant jar."
  }
  $repo = $env:R8_ASSISTANT_MAVEN_REPO
  if (-not $repo) { $repo = "https://dl.google.com/android/maven2" }
  $dir = Join-Path $HOME ".android/cache/r8-assistant/$version"
  $jar = Join-Path $dir "r8-assistant-$version.jar"
  $url = "$repo/com/android/tools/r8-assistant/$version/r8-assistant-$version.jar"
  if (-not ((Test-Path $jar) -and ((Get-FileHash -Algorithm SHA256 $jar).Hash -eq $expected))) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$jar.part"
    if ((Get-FileHash -Algorithm SHA256 "$jar.part").Hash -ne $expected) {
      Remove-Item "$jar.part"
      throw "SHA-256 mismatch for $url. Refusing to use it."
    }
    Move-Item -Force "$jar.part" $jar
  }
  $env:R8_ASSISTANT_JAR = $jar
  Write-Output "R8_ASSISTANT_JAR=$jar"
}
```

When the step fails:

- **Download failed**, for example with HTTP 404: the version is not on Google
  Maven, or a proxy blocks the download. Tell the user, and ask for a jar in
  `R8_ASSISTANT_JAR` or a Maven mirror in `R8_ASSISTANT_MAVEN_REPO`.
- **SHA-256 mismatch**: stop and tell the user. Never run a jar that failed
  the check.
- **No released jar version**: this copy of the skill comes from source and not
  from a release. Ask the user for a jar in `R8_ASSISTANT_JAR`.

## Check the tools

```shell
java -version
java -jar "$R8_ASSISTANT_JAR" --version
adb version
adb devices -l
```

- **Java**: the jar needs Java 11 or higher. With an older Java, the
  `--version` line fails with `UnsupportedClassVersionError`.
- **Android SDK**: `instrument` finds the SDK through `ANDROID_HOME`, then
  `ANDROID_SDK_ROOT`, then `sdk.dir` in `local.properties` in the current
  directory. The examples use `ANDROID_HOME`; set it to the SDK directory if
  it is not set.
- **Build tools**: `instrument` uses the highest installed build-tools version
  that has `aapt2`, `zipalign` and `apksigner`.
- **Retrace**: `retrace` comes with the SDK command line tools, in
  `cmdline-tools/latest/bin`.

Find the SDK and list the installed tools:

```shell
sdk="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ -z "$sdk" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
echo "$sdk"
ls "$sdk/build-tools" "$sdk/cmdline-tools/latest/bin"
```

Install missing packages with the Android CLI:

```shell
android sdk install "build-tools;36.0.0" "cmdline-tools;latest"
```

Without the Android CLI, use `sdkmanager` from an existing command line tools
installation:

```shell
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "build-tools;36.0.0" "cmdline-tools;latest"
```

## Pick the device

Use the device in `ANDROID_SERIAL`, or the only connected device. If several
devices are connected and `ANDROID_SERIAL` is not set, ask the user which one
to use, then set `ANDROID_SERIAL` for every adb command.

If no device is connected, list the virtual devices and ask the user which one
to start:

```shell
android emulator list
android emulator start medium_phone
```

Without the Android CLI, use `emulator -list-avds` and `emulator -avd` from
the `emulator` directory of the SDK.

Check the API level of the device. It must be at least the `minSdk` of the
app. The examples use the log path for API level 29 and higher.
[references/capture.md](capture.md) lists the path for lower API levels.

```shell
adb shell getprop ro.build.version.sdk
```

## Find the project facts

- **App module**: the module that applies the `com.android.application`
  plugin, directly or through a version catalog alias such as
  `libs.plugins.android.application`. Ask when there are several.
- **Release build type**: the build type with `isMinifyEnabled = true`, or
  `minifyEnabled true` in Groovy. If no build type runs R8, stop. If several
  build types run R8, such as `release` and `benchmark`, ask which one to use.
- **Product flavors**: with flavors the variant name combines the flavor and
  the build type, such as `freeRelease`. The Gradle tasks become
  `:app:assembleFreeDebug` and `:app:assembleFreeRelease`, and the outputs go
  to `app/build/outputs/apk/free/release/` and
  `app/build/outputs/mapping/freeRelease/`. Ask which flavor to use.
- **Rule files**: the `proguardFiles` entries of `defaultConfig` and of the
  release build type, and the `consumerProguardFiles` entries of library
  modules in the project. After a release build,
  `app/build/outputs/mapping/release/configuration.txt` contains every rule
  that R8 read. Each section starts with the comment
  `# The proguard configuration file for the following section is` followed
  by the path of its file. Files in the Gradle cache or in
  `build/intermediates` come from dependencies or from the Android Gradle
  plugin. Leave them unchanged.
- **Application IDs**: debug builds often add an `applicationIdSuffix`, so the
  debug and release application IDs differ. Apps with different IDs are
  separate installs that do not share data.
- **Debug keystore**: this skill signs release builds with the debug key when
  needed so that they install over a debug build with the same application ID.
  The key is `~/.android/debug.keystore`, or `debug.keystore` in
  `ANDROID_USER_HOME`. The first debug build creates it.

Read the application ID of each APK with `aapt2` from a build-tools version
that the `ls` output lists. The first line starts with
`package: name='com.example.app'`.

```shell
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk | head -n 1
```

## State directory

Keep all files of a run in the state directory of the app module:

```text
app/build/r8-assistant/
  app-debug-instrumented.apk    instrumented debug build
  debug-settings.json           debug log of the journey settings
  suggested.pro                 output of suggest
  proposed.pro                  proposed rules from diff
  logcat-settings.txt           logcat of a release replay
  retraced-settings.txt         retraced logcat
  journeys/settings.xml         journey files that you wrote
```

`./gradlew clean` deletes this directory, so do not run it during a run. At the
end, offer to copy the journey files that you wrote into the project, as
described in [references/journeys.md](journeys.md).

## Windows

Use PowerShell, `.\gradlew.bat` and `retrace.bat`. Quote every `-P` argument,
because PowerShell splits unquoted arguments that contain dots. Use
`Select-String` in place of `grep`. Run the jar with `$env:R8_ASSISTANT_JAR`,
on a single line:

```powershell
java -jar "$env:R8_ASSISTANT_JAR" instrument --input app/build/outputs/apk/debug/app-debug.apk --output app/build/r8-assistant/app-debug-instrumented.apk
```
