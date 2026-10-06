---
name: r8-assistant
description: Narrows R8 and ProGuard keep rules and fixes release-only reflection crashes in Android apps, using runtime evidence from an instrumented debug build and verifying every change on a real R8 release build. Use this skill when asked to narrow, improve or shrink keep rules, reduce APK or DEX size, remove broad -keep rules or -dontobfuscate, or fix crashes that only happen in the release build, such as ClassNotFoundException, NoSuchMethodException, NoSuchFieldException or Fragment InstantiationException, or Gson, Moshi or kotlinx.serialization data that is missing in release. Don't use for static review of keep rule files without running the app (suggest the r8-analyzer skill), for keep rules that ship inside third-party libraries, for dynamic feature modules or JVM jars, or for crashes that also happen in debug builds.
keywords:
- R8
- ProGuard
- keep rules
- app size
- DEX size
- release crash
- reflection
- ClassNotFoundException
- NoSuchMethodException
- NoSuchFieldException
- Gson
- Moshi
- kotlinx.serialization
- retrace
---

# R8 assistant

This skill aligns the keep rules of an Android app with its runtime
reflection. It logs reflection in an instrumented debug build, turns the log
into keep rules, and checks each change on a real R8 release build.

It handles two tasks:

- **Fix a release-only crash**: a crash, or data that goes missing, in the
  release build but not in the debug build.
- **Narrow keep rules**: replace broad rules with the classes and members that
  the app reflects on, to reduce app size.

For a static review of keep rule files without running the app, suggest the
r8-analyzer skill. When r8-analyzer marks a rule for refinement and runtime
evidence is needed, continue here.

The skill runs version {{R8_ASSISTANT_VERSION}} of the R8 assistant command
line tool. Every command reads the path of the jar from the `R8_ASSISTANT_JAR`
environment variable.

## Scope

The skill works on:

- Android app modules that build an installable base APK, including projects
  with several modules.
- Keep rules that the project owns, including the consumer rules of library
  modules in the same project.

Leave keep rules from third-party dependencies unchanged. Name the dependency
in the report when one of its rules is broad.

For the following cases, tell the user why the skill does not apply and stop:

- Dynamic feature modules, split installs and instant apps.
- JVM jars, and libraries without an Android app module.
- Release builds that do not run R8, where `isMinifyEnabled` is false.
- Crashes that also happen in the debug build. R8 is not the cause.

## Rules for every run

- Never report success before the release replay passes. A working debug build
  or a successful R8 build says nothing about the runtime behavior of the
  release build.
- Crash fixes only add rules, so that a fix never takes away what other code
  needs. Keep a fix only after the release replay passes with it.
- Narrowing edits rules in place. Present it as a diff with an evidence report
  and never commit it, because the user owns the review of every removal.
- Never add `-dontobfuscate`, `-dontshrink`, `-dontoptimize`, or rules with
  package-wide wildcards such as `com.example.**`. They hide the cause and
  give up R8 savings for code that does not need them.
- Base every rule and every claim on a log event or a retraced stack trace, and
  quote that evidence in the report.
- Keep a rule when no journey ran the code that uses its classes. Missing log
  events are evidence only for code that a journey ran.
- Keep a rule when the log leaves out the reflection that reaches its classes,
  such as reflection inside the Android platform or by AndroidX and Kotlin code
  on `androidx.`, `kotlin.` and `kotlinx.` classes. See the limits in
  [references/capture.md](references/capture.md).
- Write files only to `build/r8-assistant/` in the app module, for example
  `app/build/r8-assistant/`. Delete the log on the device after each pull, and
  send no data off the machine.

## Ask the user

Stop, ask, and wait for the answer when:

- Several devices are connected and `ANDROID_SERIAL` is not set.
- The project has several app modules, product flavors or minified build
  types, and the request does not name one.
- You need flows to exercise, and the project has no journey files or
  instrumented tests for them.
- A step needs to uninstall the app, which deletes its data on the device.
- A flow needs a login, a payment, or data that you do not have. Offer manual
  mode, where the user drives the app.
- A flow sends messages, buys something, or changes account data.

## Step 1: set up

Follow [references/setup.md](references/setup.md) to download and verify the
jar, check the tools and the device, and find the app module, variant, rule
files and application IDs. Run every command from the project root.

## Workflow A: fix a release-only crash

1. Reproduce the crash on the release build and retrace it, as described in
   [references/release-verification.md](references/release-verification.md).
2. Capture a reflection log of the instrumented debug build for the flow that
   crashes, as described in [references/capture.md](references/capture.md).
   If the debug build fails in the same way, stop: R8 is not the cause.
3. Run `suggest` on the log, and run `query` for the classes in the retraced
   stack trace, as described in [references/rules.md](references/rules.md).
4. Add the rules that cover the crash to the rule file that owns the code,
   each with a provenance comment. When the log has no matching event, write
   the rule from the retraced stack trace.
5. Run the verification gate. If it fails, return to step 3 with the new
   evidence.
6. Report with the crash fix template in
   [references/report.md](references/report.md).

## Workflow B: narrow keep rules

1. Build the release APK with the current rules, record its DEX size, and run
   the release replay as a baseline, as described in
   [references/release-verification.md](references/release-verification.md).
   Fix release-only crashes with workflow A first.
2. Pick journeys with the layers in
   [references/journeys.md](references/journeys.md), and capture a debug log
   for every journey, as described in
   [references/capture.md](references/capture.md).
3. Run `diff` with all logs and all rule files that the project owns, as
   described in [references/rules.md](references/rules.md).
4. Edit the rules in place. Narrow observed rules to the logged classes and
   members, narrow or delete rules that are not observed, and leave uncovered
   rules unchanged.
5. Run the verification gate, then record the DEX size again.
6. Present the diff and the evidence report with the narrowing template in
   [references/report.md](references/report.md). Do not commit.

## Verification gate

Run the release replay after every rule change, as described in
[references/release-verification.md](references/release-verification.md):
build the release APK with R8, install it without instrumentation, run each
journey, and check its expectations, logcat and the retraced stack traces.
Pass only with zero failures.

## Report

Use the templates in [references/report.md](references/report.md). Include the
evidence for each rule, the journeys and their coverage, the result of the
release replay, and for narrowing the DEX size before and after. Name the APK
installed on the device at the end.

Offer to copy the journey files that you wrote into the project, as described
in [references/journeys.md](references/journeys.md).

## Commands

- `instrument` adds reflection logging to an APK that is debuggable or signed
  with the debug key.
- `suggest` writes keep rules for the reflection in one or more logs.
- `diff` compares logs with existing rules and classifies each rule.
- `query` filters log events by class, member or event type.

Every command prints its usage with `--help`. Check the usage before you use
an option that this skill does not show:

```shell
java -jar "$R8_ASSISTANT_JAR" --help
java -jar "$R8_ASSISTANT_JAR" diff --help
```

For options, outputs and exit codes, see [references/cli.md](references/cli.md).
For errors, see [references/troubleshooting.md](references/troubleshooting.md).

## Antipatterns

- Reporting a fix because the debug build works, or because R8 builds without
  warnings.
- Installing with `./gradlew installDebug`, or running `connectedAndroidTest`,
  after instrumenting. Both replace the instrumented APK, and
  `connectedAndroidTest` also uninstalls the app, which deletes its data.
- Adding `-keep class com.example.** { *; }` or `-dontobfuscate` to make a
  crash go away.
- Deleting a rule because its classes have no events in the log of a journey
  that never reached them.
- Writing a rule for a class name from an obfuscated stack trace without
  retracing it.
- Running two journeys across an app restart without pulling the log in
  between. Each new app process truncates the log.
- Running `./gradlew clean` during a run. It deletes `build/r8-assistant/`.
