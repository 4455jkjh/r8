# Journeys

A journey is one user flow on the device, such as opening the settings screen
and switching the theme. Run the same journeys for the debug capture and for
both release replays, so that the debug log and the release log of a journey
are comparable.

## Pick journeys in layers

Go through the layers in order. For a crash, stop at the first layer that
reproduces it. For narrowing, combine the layers until the journeys run the
code that uses the classes of the rules you change.

1. **Journey files in the project**: XML files with the root element
   `journey`. Search the project for files that contain `<journey`.
2. **Instrumented tests**: the tests in `src/androidTest` of the app module.
3. **Your own exploration**: open the screens and features that use the
   classes of the rules, or that crash. Save each flow that you run as a
   journey file in `app/build/r8-assistant/journeys/`.
4. **Manual mode**: the user drives the app on the device while you capture.

## Journey file format

```xml
<journey name="settings">
  <description>
    Open the settings screen and switch to the dark theme.
  </description>
  <actions>
    <action>Open the navigation drawer and tap "Settings"</action>
    <action>Tap "Theme" and select "Dark"</action>
    <action>Verify that the settings screen uses the dark theme</action>
  </actions>
</journey>
```

Run a journey by performing its actions in order. Actions that start with
"Verify" or "Check" are expectations: inspect the screen without interacting
with it. The journey fails when an action is impossible, an expectation does
not hold, or the app crashes, freezes or exits.

To inspect and drive the app, use the Android CLI, such as `android layout`
and `android screen capture`, or use adb, such as
`adb shell uiautomator dump`, `adb shell input tap` and
`adb shell input text`.

## Run instrumented tests

Install the instrumented app first, as described in
[references/capture.md](capture.md). Then build and install the test APK, find
the test runner, and run the tests with `am instrument`:

```shell
./gradlew :app:assembleDebugAndroidTest
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm list instrumentation
adb shell am instrument -w com.example.app.test/androidx.test.runner.AndroidJUnitRunner
```

Do not run `connectedAndroidTest`: it replaces the instrumented APK and
uninstalls the app.

`am instrument` runs all tests in one process, so one log covers the run. To
get one log per test class, run each class on its own and pull the log after
each run:

```shell
adb shell am instrument -w -e class com.example.app.SettingsTest com.example.app.test/androidx.test.runner.AndroidJUnitRunner
```

Tests that need Android Test Orchestrator or a fresh app process per test
restart the app, and each restart truncates the log. Run those tests one at a
time.

## Manual mode

Use manual mode for logins, payments, hardware such as a camera, and flows
that you are unable to reach. Tell the user which flow to perform, wait until
they say that they are done, then pull the log. Write down the flow as a
journey file, so that the release replays repeat it.

## Record coverage

For each journey, record:

- The layer that it came from, and the path of its journey file or test class.
- The screens and features that it reached.
- The classes from the rules whose code it ran.

A rule whose classes no journey reached is uncovered. Leave it unchanged and
list it in the report.

## Keep new journey files

Write the journey files from your own exploration and from manual mode to
`app/build/r8-assistant/journeys/`.

At the end of the run, offer to copy them into the project. Put them next to
the journey files that the project already has. If it has none, ask the user
for a directory.
