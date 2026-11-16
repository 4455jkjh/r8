# Capture reflection logs

Capture reflection logs from the instrumented debug build by running the
instrumented tests in `src/androidTest` or by running UI journeys, as
described in [references/journeys.md](journeys.md). The examples use the
application ID `com.example.app` and a device with API level 29 or higher.

## Build and instrument the debug APK

```shell
./gradlew :app:assembleDebug
mkdir -p app/build/r8-assistant
java -jar "$R8_ASSISTANT_JAR" instrument \
  --input app/build/outputs/apk/debug/app-debug.apk \
  --output app/build/r8-assistant/app-debug-instrumented.apk
```

With product flavors, the APK path contains the flavor, for example
`app/build/outputs/apk/free/debug/app-free-debug.apk`.

`instrument` refuses an APK that is neither debuggable nor signed only with the
debug key. It aligns the instrumented APK and signs it with the debug keystore.

## Install the instrumented APK

```shell
adb install -r app/build/r8-assistant/app-debug-instrumented.apk
```

Always install with `adb install -r`. `./gradlew installDebug`,
`connectedAndroidTest` and `android run` install the APK without
instrumentation, and `connectedAndroidTest` also uninstalls the app, which
deletes its data. If the install fails, see
[references/troubleshooting.md](troubleshooting.md).

## Log path

The app writes the log to a path that depends on the API level of the device:

- API level 29 and higher:
  `/sdcard/Android/media/com.example.app/additional_test_output/reflection_log.json`
- API levels 16 to 28:
  `/sdcard/Android/data/com.example.app/files/test_data/reflection_log.json`
- On API level 15 and lower, the app writes no log.

The path uses the application ID of the installed APK, including any
`applicationIdSuffix` of the debug build.

## Record a test run or journey

Stop the app and delete any old log on the device:

```shell
log=/sdcard/Android/media/com.example.app/additional_test_output/reflection_log.json
adb shell am force-stop com.example.app
adb shell rm -f "$log"
```

Run the instrumented tests with `am instrument` or run a UI journey, as
described in [references/journeys.md](journeys.md). For a UI journey, find
the launcher activity of the installed app (from `AndroidManifest.xml` on API
levels 16 to 23) and start it:

```shell
adb shell cmd package resolve-activity --brief com.example.app
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.example.app/com.example.app.MainActivity
```

Then pull the log into the state directory and delete the copy on the device:

```shell
log=/sdcard/Android/media/com.example.app/additional_test_output/reflection_log.json
adb pull "$log" app/build/r8-assistant/debug-settings.json
adb shell rm -f "$log"
```

Repeat for each separate test run or journey, with one file per run, such as
`debug-sync.json`.

If the instrumented debug build crashes in the same way as the release build,
R8 is not the cause. Report the crash and stop.

## Log format

The log has one JSON object per line.

Before each distinct reflective call, the app writes a request line with the
event type, an ID, the top five frames of the call stack, and the arguments.
After the call returns, it writes the result for that ID:

```json
{"event": "CLASS_FOR_NAME", "id": 3, "stack": ["com.example.app.settings.ThemeLoader.load(ThemeLoader.java:42)", "com.example.app.settings.SettingsFragment.onViewCreated(SettingsFragment.kt:31)"], "args": ["com.example.app.settings.DarkTheme", "true", "null"]}
{"returned": 3, "result": "com.example.app.settings.DarkTheme"}
```

The event types group into eight categories:

- **Class lookup and instantiation**: `CLASS_FOR_NAME`, `CLASS_NEW_INSTANCE`,
  `CLASS_LOADER_LOAD_CLASS`, `UNSAFE_ALLOCATE_INSTANCE`.
- **Methods, fields and constructors**: `CLASS_GET_METHOD`,
  `CLASS_GET_DECLARED_METHOD`, `CLASS_GET_METHODS`,
  `CLASS_GET_DECLARED_METHODS`, `CLASS_GET_FIELD`, `CLASS_GET_DECLARED_FIELD`,
  `CLASS_GET_FIELDS`, `CLASS_GET_DECLARED_FIELDS`, `CLASS_GET_CONSTRUCTOR`,
  `CLASS_GET_DECLARED_CONSTRUCTOR`, `CLASS_GET_CONSTRUCTORS`,
  `CLASS_GET_DECLARED_CONSTRUCTORS`.
- **Annotations on classes and members**: `CLASS_GET_ANNOTATION`,
  `CLASS_GET_ANNOTATIONS`, `CLASS_IS_ANNOTATION_PRESENT`,
  `FIELD_GET_ANNOTATION`, `FIELD_GET_ANNOTATIONS`,
  `FIELD_IS_ANNOTATION_PRESENT`, `METHOD_GET_ANNOTATION`,
  `METHOD_GET_ANNOTATIONS`, `METHOD_IS_ANNOTATION_PRESENT`,
  `CONSTRUCTOR_GET_ANNOTATION`, `CONSTRUCTOR_GET_ANNOTATIONS`,
  `CONSTRUCTOR_IS_ANNOTATION_PRESENT` (covering both `getAnnotation(s)` and
  `getDeclaredAnnotation(s)`).
- **Generic supertypes and member signatures**: `CLASS_GET_GENERIC_SUPERCLASS`,
  `CLASS_GET_GENERIC_INTERFACES`, `METHOD_GET_GENERIC_PARAMETER_TYPES`,
  `METHOD_GET_GENERIC_RETURN_TYPE`, `METHOD_GET_GENERIC_EXCEPTION_TYPES`,
  `FIELD_GET_GENERIC_TYPE`.
- **Nested and enclosing classes**: `CLASS_GET_DECLARED_CLASSES`,
  `CLASS_GET_CLASSES`, `CLASS_GET_ENCLOSING_CLASS`, `CLASS_GET_DECLARING_CLASS`.
- **Enums**: `ENUM_VALUE_OF`, `CLASS_GET_ENUM_CONSTANTS`.
- **Class metadata and type checks**: `CLASS_GET_NAME`, `CLASS_GET_PACKAGE`,
  `CLASS_GET_SUPERCLASS`, `CLASS_GET_COMPONENT_TYPE`,
  `CLASS_IS_ASSIGNABLE_FROM`, `CLASS_IS_INSTANCE`, `CLASS_CAST`,
  `CLASS_AS_SUBCLASS`, `CLASS_FLAG`.
- **Concurrency, proxies, services and bundles**:
  `ATOMIC_FIELD_UPDATER_NEW_UPDATER`, `PROXY_NEW_PROXY_INSTANCE`,
  `SERVICE_LOADER_LOAD` and `RESOURCE_BUNDLE_GET_BUNDLE`.

Results are class names, member signatures or booleans, and an object shows
as the name of its class. Four kinds of results need a note:

- `RESOURCE_BUNDLE_GET_BUNDLE` returns the class of the bundle, such as
  `com.example.app.i18n.Messages_en`, or `java.util.PropertyResourceBundle`
  when the bundle is a properties file.
- `CLASS_GET_GENERIC_SUPERCLASS`, `CLASS_GET_GENERIC_INTERFACES`,
  `METHOD_GET_GENERIC_PARAMETER_TYPES`, `METHOD_GET_GENERIC_RETURN_TYPE`,
  `METHOD_GET_GENERIC_EXCEPTION_TYPES` and `FIELD_GET_GENERIC_TYPE` return the
  name of each plain class, and `<raw type><...>` without type arguments (such
  as `com.google.gson.reflect.TypeToken<...>`) for each parameterized type, or
  the runtime class name (`libcore.reflect.ParameterizedTypeImpl` on ART) when
  `getRawType` throws.
- Annotation queries return the annotation type names (plus `class:<type>` and
  `interface:<type>` entries for `Class`-valued annotation elements), or a
  boolean for `*_IS_ANNOTATION_PRESENT`.
- `ENUM_VALUE_OF` and `CLASS_GET_ENUM_CONSTANTS` return the enum constant
  name(s), and `SERVICE_LOADER_LOAD` returns the loaded provider class names.

## Limits of the log

- **Distinct events only**: the app writes each distinct combination of event,
  stack and arguments once per process, and each distinct result once per
  event. The log shows which reflection happened, not how often.
- **Failures**: a request line without a result line means that the call
  threw, or that the process died before the call returned. The log records no
  exception type; take it from logcat.
- **Ignored code**: the log leaves out reflection on `java.`, `javax.` and
  `android.` classes. It also leaves out internal reflection within the same
  library group (such as an `androidx.foo.*` class reflecting on
  `androidx.foo.*`, or `kotlin.` and `kotlinx.` code reflecting on `kotlin.`
  and `kotlinx.`).
- **Library reflection**: reflection that AndroidX and Kotlin code does on app
  classes, third-party classes, or another library group (such as Room
  instantiating `WorkDatabase_Impl`) is in the log. The five logged frames are
  often all library frames.
- **Platform code**: only code in the APK is instrumented. Reflection inside
  the Android platform, such as the creation of activities from the manifest,
  of custom views by `LayoutInflater`, and of security provider services by
  `java.security.Provider`, has no events.
- **JNI calls**: native code that looks up classes, methods or fields through
  JNI (`FindClass`, `GetMethodID`, `GetFieldID`) does not go through Java
  bytecode and has no events.
- **Class loaders**: `loadClass` calls through a variable of a subclass type,
  such as `DexClassLoader`, and `super.loadClass` calls have no events.
- **Module bundles**: the `ResourceBundle.getBundle` overloads that take a
  `java.lang.Module` have no events.
- **ServiceLoader**: `SERVICE_LOADER_LOAD` iterates the loader to record the
  loaded provider classes; a provider whose instantiation throws during loading
  has no entry in the result.
- **One process at a time**: each new app process truncates the log, so app
  restarts, a second app process, and per-test processes of a test runner
  overwrite earlier events. Pull the log before the app restarts.
- **Crashes**: when the app crashes or is killed, the log ends there. Treat
  the journey as failed and run it again after the cause is fixed.
- **Empty logs**: an empty log means that the journey made no reflective call
  that the log records, or that the installed APK is not the instrumented one.
  See [references/troubleshooting.md](troubleshooting.md).

## Privacy

The log contains class names, member names, stack frames and the string
arguments of reflective calls, but no field values.

Keep the logs in the state directory, delete each log on the device after the
pull, and send the logs nowhere.
