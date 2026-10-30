// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuggestCommandTest extends AssistantCommandTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private Path writeAppLog() throws IOException {
    return writeFile(
        "app.jsonl",
        request(
            1,
            "CLASS_FOR_NAME",
            "com.example.Plugins.load(Plugins.java:12)",
            "com.example.plugins.CsvPlugin",
            "true",
            LOADER),
        returned(1, "com.example.plugins.CsvPlugin"),
        request(
            2,
            "CLASS_GET_DECLARED_CONSTRUCTOR",
            "com.example.Plugins.load(Plugins.java:13)",
            "void",
            "com.example.plugins.CsvPlugin",
            "<init>"),
        returned(2, "<init>()"),
        // A probe for an optional class, which threw.
        request(
            3,
            "CLASS_FOR_NAME",
            "com.example.Plugins.probe(Plugins.java:30)",
            "com.example.plugins.XmlPlugin",
            "false",
            LOADER),
        request(
            4,
            "CLASS_GET_DECLARED_FIELD",
            "com.example.json.Serializer.read(Serializer.java:40)",
            "java.lang.String",
            "com.example.model.User",
            "name"),
        returned(4, "java.lang.String name"),
        request(
            5,
            "CLASS_GET_DECLARED_FIELDS",
            "com.example.json.Serializer.write(Serializer.java:55)",
            "com.example.model.Address"),
        returnedMembers(5, "int zip", "java.lang.String city"),
        request(
            6,
            "CLASS_GET_METHOD",
            "com.example.Events.dispatch(Events.java:70)",
            "void",
            "com.example.ui.MainActivity",
            "onUserLoaded",
            "com.example.model.User"),
        returned(6, "void onUserLoaded(com.example.model.User)"),
        request(
            7,
            "ATOMIC_FIELD_UPDATER_NEW_UPDATER",
            "com.example.sync.SyncState.<clinit>(SyncState.java:10)",
            "int",
            "com.example.sync.SyncState",
            "state"),
        returned(
            7,
            "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl"),
        request(
            8,
            "PROXY_NEW_PROXY_INSTANCE",
            "com.example.net.Api.create(Api.java:25)",
            LOADER,
            "com.example.net.Api$Handler",
            "com.example.net.UserService"),
        returned(8, "$Proxy1"),
        request(
            9,
            "SERVICE_LOADER_LOAD",
            "com.example.Plugins.services(Plugins.java:50)",
            "com.example.spi.Codec",
            LOADER),
        returned(9, "java.util.ServiceLoader"),
        request(
            10,
            "CLASS_GET_NAME",
            "com.example.log.Tags.of(Tags.java:8)",
            "com.example.ui.MainActivity",
            "NAME"),
        returned(10, "com.example.ui.MainActivity"),
        request(
            11,
            "CLASS_GET_NAME",
            "com.example.log.Tags.simple(Tags.java:12)",
            "com.example.ui.MainActivity$Header",
            "SIMPLE_NAME"),
        returned(11, "Header"),
        // Classes of the platform need no rules.
        request(
            12,
            "CLASS_FOR_NAME",
            "com.example.json.Json.init(Json.java:5)",
            "org.json.JSONObject",
            "true",
            LOADER),
        returned(12, "org.json.JSONObject"),
        // A failed lookup, which the logger writes without the member type.
        request(
            13,
            "CLASS_GET_DECLARED_FIELD",
            "com.example.json.Serializer.read(Serializer.java:41)",
            null,
            "com.example.model.User",
            "nickname"),
        // The same request again.
        request(
            14,
            "CLASS_GET_DECLARED_FIELD",
            "com.example.json.Serializer.read(Serializer.java:40)",
            "java.lang.String",
            "com.example.model.User",
            "name"),
        returned(14, "java.lang.String name"));
  }

  @Test
  public void testUsage() {
    SuggestCommand command = new SuggestCommand();
    assertThat(
        command.getUsage(),
        containsString("Usage: java -jar r8-assistant.jar suggest --log <file>... [options]"));
    assertThat(command.getDescription(), not(containsString("\n")));
  }

  @Test
  public void testSuggest() throws Exception {
    Path log = writeAppLog();
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertEquals(
        StringUtils.unixLines(
            "# Keep rules suggested by the R8 Assistant",
            "# Logs: " + log,
            "# Outcomes: known (after-call results present)",
            "# Events: 14 (2 threw), requirements: 12",
            "# Only reflection in the logged runs is covered.",
            "",
            "# Class.getDeclaredFields com.example.model.Address <-"
                + " com.example.json.Serializer.write(Serializer.java:55)",
            "-keepclassmembers class com.example.model.Address {",
            "    <fields>;",
            "}",
            "",
            "# Class.getDeclaredField com.example.model.User.name <-"
                + " com.example.json.Serializer.read(Serializer.java:40)",
            "-keepclassmembers class com.example.model.User {",
            "    java.lang.String name;",
            "}",
            "",
            "# Proxy.newProxyInstance com.example.net.UserService <-"
                + " com.example.net.Api.create(Api.java:25)",
            "-keep interface com.example.net.UserService {",
            "    <methods>;",
            "}",
            "",
            "# Class.forName com.example.plugins.CsvPlugin <-"
                + " com.example.Plugins.load(Plugins.java:12)",
            "# Class.getDeclaredConstructor com.example.plugins.CsvPlugin.<init>() <-"
                + " com.example.Plugins.load(Plugins.java:13)",
            "-keep class com.example.plugins.CsvPlugin {",
            "    <init>();",
            "}",
            "",
            "# ServiceLoader.load com.example.spi.Codec <-"
                + " com.example.Plugins.services(Plugins.java:50)",
            "-keep class com.example.spi.Codec {",
            "    <methods>;",
            "}",
            "",
            "# AtomicIntegerFieldUpdater.newUpdater com.example.sync.SyncState.state <-"
                + " com.example.sync.SyncState.<clinit>(SyncState.java:10)",
            "-keepclassmembers class com.example.sync.SyncState {",
            "    volatile int state;",
            "}",
            "",
            "# Class.getName com.example.ui.MainActivity <- com.example.log.Tags.of(Tags.java:8)",
            "-keepnames class com.example.ui.MainActivity",
            "",
            "# Class.getMethod com.example.ui.MainActivity.onUserLoaded(com.example.model.User) <-"
                + " com.example.Events.dispatch(Events.java:70)",
            "-keepclassmembers class com.example.ui.MainActivity {",
            "    void onUserLoaded(com.example.model.User);",
            "}",
            "",
            "# Class.getSimpleName com.example.ui.MainActivity$Header <-"
                + " com.example.log.Tags.simple(Tags.java:12)",
            "-keepnames class com.example.ui.MainActivity$Header",
            "",
            "# Needed for getSimpleName(), getCanonicalName() and the nesting flags of",
            "# nested classes:",
            "# Class.getSimpleName com.example.ui.MainActivity$Header <-"
                + " com.example.log.Tags.simple(Tags.java:12)",
            "-keepattributes InnerClasses,EnclosingMethod",
            "",
            "# Optional probes: these requests threw in the logged run, so no rules are",
            "# suggested for them:",
            "#   Class.forName com.example.plugins.XmlPlugin <-"
                + " com.example.Plugins.probe(Plugins.java:30)",
            "#   Class.getDeclaredField com.example.model.User.nickname <-"
                + " com.example.json.Serializer.read(Serializer.java:41)"),
        result.stdout);
  }

  @Test
  public void testExistingRules() throws Exception {
    Path log = writeAppLog();
    Path rules =
        writeFile(
            "rules.pro",
            "-keep class com.example.plugins.** { *; }",
            "-keepclassmembers class com.example.model.** { <fields>; }",
            "-keepnames class com.example.ui.**",
            "-keepclassmembers class com.example.ui.MainActivity { public <methods>; }",
            "-keep class * implements com.example.spi.Codec",
            "-keepattributes InnerClasses,EnclosingMethod");
    ProcessResult result = run(new SuggestCommand(), "--log", log, "--rules", rules);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    // The rules keep the plugins, the model fields and the names and methods of the UI classes.
    // The rule selecting classes by their interface may keep the proxy and service interfaces,
    // but the logs do not show the class hierarchy, so rules for them are suggested with a note.
    String possiblyKept =
        "# Possibly kept by "
            + rules
            + ":5: the class hierarchy (implements com.example.spi.Codec) is not in the log";
    assertEquals(
        StringUtils.unixLines(
            "# Keep rules suggested by the R8 Assistant",
            "# Logs: " + log,
            "# Outcomes: known (after-call results present)",
            "# Events: 14 (2 threw), requirements: 12",
            "# Existing rules: " + rules + " (cover 7 of the requirements)",
            "# Only reflection in the logged runs is covered.",
            "",
            "# Proxy.newProxyInstance com.example.net.UserService <-"
                + " com.example.net.Api.create(Api.java:25)",
            possiblyKept,
            "-keep interface com.example.net.UserService {",
            "    <methods>;",
            "}",
            "",
            "# ServiceLoader.load com.example.spi.Codec <-"
                + " com.example.Plugins.services(Plugins.java:50)",
            possiblyKept,
            "-keep class com.example.spi.Codec {",
            "    <methods>;",
            "}",
            "",
            "# AtomicIntegerFieldUpdater.newUpdater com.example.sync.SyncState.state <-"
                + " com.example.sync.SyncState.<clinit>(SyncState.java:10)",
            "-keepclassmembers class com.example.sync.SyncState {",
            "    volatile int state;",
            "}",
            "",
            "# Optional probes: these requests threw in the logged run, so no rules are",
            "# suggested for them:",
            "#   Class.forName com.example.plugins.XmlPlugin <-"
                + " com.example.Plugins.probe(Plugins.java:30)",
            "#   Class.getDeclaredField com.example.model.User.nickname <-"
                + " com.example.json.Serializer.read(Serializer.java:41)"),
        result.stdout);
  }

  @Test
  public void testGenericSupertypesBundlesAndClassLoader() throws Exception {
    Path log =
        writeFile(
            "extended.jsonl",
            // ClassLoader.loadClass -> keeps the loaded class.
            request(
                1,
                "CLASS_LOADER_LOAD_CLASS",
                "androidx.appcompat.app.AppCompatDelegateImpl.createView(AppCompatDelegateImpl.java:80)",
                "com.example.ui.CustomInflater",
                LOADER),
            returned(1, "com.example.ui.CustomInflater"),
            // ResourceBundle.getBundle -> keeps <base>, <base>_* { <init>(); } for bundle classes,
            // and nothing for PropertyResourceBundle.
            request(
                2,
                "RESOURCE_BUNDLE_GET_BUNDLE",
                "com.example.i18n.I18n.load(I18n.java:15)",
                "com.example.i18n.Messages",
                "de",
                LOADER),
            returned(2, "com.example.i18n.Messages_de"),
            request(
                3,
                "RESOURCE_BUNDLE_GET_BUNDLE",
                "com.example.i18n.I18n.loadProps(I18n.java:20)",
                "com.example.i18n.Strings",
                "en",
                LOADER),
            returned(3, "java.util.PropertyResourceBundle"),
            // Class.getGenericSuperclass with an app raw type -> keeps raw type, * extends raw
            // type, and Signature attribute.
            request(
                4,
                "CLASS_GET_GENERIC_SUPERCLASS",
                "com.example.json.TypeTokens.read(TypeTokens.java:10)",
                "com.example.json.TypeTokens$1"),
            returned(4, "com.example.json.TypeToken<...>"),
            // Class.getGenericInterfaces with an app raw type and a non-parameterized interface.
            request(
                5,
                "CLASS_GET_GENERIC_INTERFACES",
                "com.example.repo.Repos.register(Repos.java:30)",
                "com.example.repo.UserRepo"),
            returnedTypes(5, "com.example.repo.Repository<...>", "java.io.Serializable"),
            // Class.getGenericSuperclass with an unresolved raw type -> keeps anonymous class by
            // name with notes.
            request(
                6,
                "CLASS_GET_GENERIC_SUPERCLASS",
                "com.example.json.TypeTokens.broken(TypeTokens.java:25)",
                "com.example.json.Broken$1"),
            returned(6, "libcore.reflect.ParameterizedTypeImpl"));
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertEquals(
        StringUtils.unixLines(
            "# Keep rules suggested by the R8 Assistant",
            "# Logs: " + log,
            "# Outcomes: known (after-call results present)",
            "# Events: 6 (0 threw), requirements: 8",
            "# Only reflection in the logged runs is covered.",
            "",
            "# ResourceBundle.getBundle com.example.i18n.Messages (de) <-"
                + " com.example.i18n.I18n.load(I18n.java:15)",
            "-keep class com.example.i18n.Messages, com.example.i18n.Messages_* {",
            "    <init>();",
            "}",
            "",
            "# Class.getGenericSuperclass com.example.json.Broken$1 <-"
                + " com.example.json.TypeTokens.broken(TypeTokens.java:25)",
            "# The raw type of a parameterized supertype of com.example.json.Broken$1 could not be"
                + " resolved at runtime, so the class is kept by its name.",
            "# com.example.json.Broken$1 is an anonymous class: its name can change when the code"
                + " changes, and the rule no longer matches it.",
            "-keep,allowobfuscation,allowshrinking class com.example.json.Broken$1",
            "",
            "# Class.getGenericSuperclass com.example.json.TypeTokens$1 <-"
                + " com.example.json.TypeTokens.read(TypeTokens.java:10)",
            "-keep,allowobfuscation,allowshrinking class com.example.json.TypeToken",
            "",
            "# Class.getGenericSuperclass com.example.json.TypeTokens$1 <-"
                + " com.example.json.TypeTokens.read(TypeTokens.java:10)",
            "-keep,allowobfuscation,allowshrinking class * extends com.example.json.TypeToken",
            "",
            "# Class.getGenericInterfaces com.example.repo.UserRepo <-"
                + " com.example.repo.Repos.register(Repos.java:30)",
            "-keep,allowobfuscation,allowshrinking class com.example.repo.Repository",
            "",
            "# Class.getGenericInterfaces com.example.repo.UserRepo <-"
                + " com.example.repo.Repos.register(Repos.java:30)",
            "-keep,allowobfuscation,allowshrinking class * implements"
                + " com.example.repo.Repository",
            "",
            "# ClassLoader.loadClass com.example.ui.CustomInflater <-"
                + " androidx.appcompat.app.AppCompatDelegateImpl.createView(AppCompatDelegateImpl.java:80)",
            "-keep class com.example.ui.CustomInflater",
            "",
            "# Needed for the parameterized types returned by getGenericSuperclass() and",
            "# getGenericInterfaces():",
            "# Class.getGenericInterfaces com.example.repo.UserRepo <-"
                + " com.example.repo.Repos.register(Repos.java:30)",
            "# Class.getGenericSuperclass com.example.json.Broken$1 <-"
                + " com.example.json.TypeTokens.broken(TypeTokens.java:25)",
            "# Class.getGenericSuperclass com.example.json.TypeTokens$1 <-"
                + " com.example.json.TypeTokens.read(TypeTokens.java:10)",
            "-keepattributes Signature"),
        result.stdout);
  }

  @Test
  public void testOutputFile() throws Exception {
    Path log = writeAppLog();
    ProcessResult toStdout = run(new SuggestCommand(), "--log", log);
    Path output = temp.getRoot().toPath().resolve("out").resolve("rules.pro");
    ProcessResult result = run(new SuggestCommand(), "--log", log, "--output", output);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals(StringUtils.unixLines("Wrote 10 rules to " + output + "."), result.stdout);
    assertEquals(toStdout.stdout, readFile(output));
  }

  @Test
  public void testLogWithoutOutcomes() throws Exception {
    // Without after-call lines, every request is treated as successful, except member lookups
    // without a member type, which only failed lookups have.
    Path log =
        writeFile(
            "old.jsonl",
            request(
                -1,
                "CLASS_FOR_NAME",
                "com.example.Plugins.probe(Plugins.java:30)",
                "com.example.plugins.XmlPlugin",
                "false",
                LOADER),
            request(
                -1,
                "CLASS_GET_DECLARED_METHOD",
                "com.example.Events.find(Events.java:9)",
                null,
                "com.example.ui.MainActivity",
                "onMissing"));
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertThat(
        result.stdout,
        containsString(
            "# Outcomes: unknown (no after-call results; all requests treated as successful)\n"));
    assertThat(result.stdout, containsString("-keep class com.example.plugins.XmlPlugin\n"));
    assertThat(result.stdout, not(containsString("***")));
    assertThat(result.stdout, not(containsString("-keepclassmembers")));
    assertThat(
        result.stdout,
        containsString(
            "#   Class.getDeclaredMethod com.example.ui.MainActivity.onMissing() <-"
                + " com.example.Events.find(Events.java:9)\n"));
  }

  @Test
  public void testEmptyBulkLookupNotVacuouslyCovered() throws Exception {
    Path log =
        writeFile(
            "empty_bulk.jsonl",
            request(
                1,
                "CLASS_GET_DECLARED_FIELDS",
                "com.example.json.Serializer.write(Serializer.java:55)",
                "com.example.model.Empty"),
            returnedMembers(1));
    Path rules = writeFile("rules.pro", "-keep class com.example.other.Other");
    ProcessResult result = run(new SuggestCommand(), "--log", log, "--rules", rules);
    assertEquals(result.toString(), 0, result.exitCode);
    assertThat(result.stdout, containsString("cover 0 of the requirements"));
    assertThat(
        result.stdout,
        containsString(
            StringUtils.unixLines(
                "-keepclassmembers class com.example.model.Empty {", "    <fields>;", "}")));
  }

  @Test
  public void testAllLoggedEventTypesAreSupported() throws Exception {
    List<String> lines = new ArrayList<>();
    String caller = "com.example.Main.main(Main.java:1)";
    for (ReflectiveEventType type : ReflectiveEventType.values()) {
      String[] args;
      switch (type) {
        case CLASS_NEW_INSTANCE:
        case CLASS_GET_DECLARED_METHODS:
        case CLASS_GET_DECLARED_FIELDS:
        case CLASS_GET_DECLARED_CONSTRUCTORS:
        case CLASS_GET_METHODS:
        case CLASS_GET_FIELDS:
        case CLASS_GET_CONSTRUCTORS:
        case CLASS_GET_COMPONENT_TYPE:
        case CLASS_GET_PACKAGE:
        case CLASS_GET_SUPERCLASS:
        case CLASS_GET_GENERIC_SUPERCLASS:
        case CLASS_GET_GENERIC_INTERFACES:
          args = new String[] {"com.example.A"};
          break;
        case CLASS_IS_ASSIGNABLE_FROM:
        case CLASS_AS_SUBCLASS:
        case CLASS_IS_INSTANCE:
        case CLASS_CAST:
          args = new String[] {"com.example.A", "com.example.B"};
          break;
        case CLASS_GET_DECLARED_METHOD:
        case CLASS_GET_METHOD:
          args = new String[] {"void", "com.example.A", "m"};
          break;
        case CLASS_GET_DECLARED_FIELD:
        case CLASS_GET_FIELD:
        case ATOMIC_FIELD_UPDATER_NEW_UPDATER:
          args = new String[] {"int", "com.example.A", "f"};
          break;
        case CLASS_GET_DECLARED_CONSTRUCTOR:
        case CLASS_GET_CONSTRUCTOR:
          args = new String[] {"void", "com.example.A", "<init>"};
          break;
        case CLASS_GET_NAME:
          args = new String[] {"com.example.A", "NAME"};
          break;
        case CLASS_FOR_NAME:
          args = new String[] {"com.example.A", "true", LOADER};
          break;
        case CLASS_FLAG:
          args = new String[] {"com.example.A", "ENUM"};
          break;
        case SERVICE_LOADER_LOAD:
        case CLASS_LOADER_LOAD_CLASS:
          args = new String[] {"com.example.A", LOADER};
          break;
        case PROXY_NEW_PROXY_INSTANCE:
          args = new String[] {LOADER, "com.example.Handler", "com.example.A"};
          break;
        case RESOURCE_BUNDLE_GET_BUNDLE:
          args = new String[] {"com.example.Messages", "en", LOADER};
          break;
        default:
          throw new AssertionError("Unhandled ReflectiveEventType: " + type);
      }
      lines.add(request(-1, type.name(), caller, args));
    }
    lines.add(request(-1, "SOME_FUTURE_EVENT", caller));
    Path log = writeFile("all.jsonl", lines.toArray(new String[0]));
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals(
        StringUtils.unixLines(
            "Warning: "
                + log
                + ": skipped 1 line that could not be read (malformed, or events of a type this"
                + " version does not know)"),
        result.stderr);
  }

  @Test
  public void testEmptyLog() throws Exception {
    Path log = writeFile("empty.jsonl");
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 1, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals(
        StringUtils.unixLines(
            "Error: no reflection events in "
                + log
                + ". Run the instrumented app through the relevant journeys first."),
        result.stderr);
  }

  @Test
  public void testMalformedLines() throws Exception {
    Path log =
        writeFile(
            "malformed.jsonl",
            "not json",
            request(
                1,
                "CLASS_FOR_NAME",
                "com.example.Main.main(Main.java:1)",
                "com.example.A",
                "true",
                LOADER),
            returned(1, "com.example.A"),
            returned(7, "com.example.B"));
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertThat(result.stdout, containsString("-keep class com.example.A\n"));
    assertEquals(
        StringUtils.unixLines(
            "Warning: "
                + log
                + ": skipped 1 line that could not be read (malformed, or events of a type this"
                + " version does not know)"),
        result.stderr);
  }

  @Test
  public void testMissingFiles() throws Exception {
    Path missing = temp.getRoot().toPath().resolve("missing.jsonl");
    ProcessResult result = run(new SuggestCommand(), "--log", missing);
    assertEquals(result.toString(), 2, result.exitCode);
    assertEquals(
        StringUtils.unixLines("Error: the file given to --log does not exist: " + missing),
        result.stderr);
    Path log = writeAppLog();
    result = run(new SuggestCommand(), "--log", log, "--rules", missing);
    assertEquals(result.toString(), 2, result.exitCode);
    assertEquals(
        StringUtils.unixLines("Error: the file given to --rules does not exist: " + missing),
        result.stderr);
  }

  @Test
  public void testInvalidRules() throws Exception {
    Path log = writeAppLog();
    Path rules = writeFile("rules.pro", "-keep class {");
    ProcessResult result = run(new SuggestCommand(), "--log", log, "--rules", rules);
    assertEquals(result.toString(), 2, result.exitCode);
    assertEquals("", result.stdout);
    assertThat(result.stderr, containsString("Error: invalid keep rules:\n"));
  }

  @Test
  public void testSuperclassResultProbeMatchingAndNullClassName() throws Exception {
    String caller = "com.example.Main.main(Main.java:1)";
    Path log =
        writeFile(
            "super_and_probe.jsonl",
            // Class.getSuperclass keeps both the reflected class and its returned superclass.
            request(1, "CLASS_GET_SUPERCLASS", caller, "com.example.Sub"),
            returned(1, "com.example.Base"),
            // A failed field lookup (logged with null type) for a field that another lookup finds
            // is not listed as an optional probe.
            request(2, "CLASS_GET_DECLARED_FIELD", caller, null, "com.example.Sub", "count"),
            request(3, "CLASS_GET_DECLARED_FIELD", caller, "int", "com.example.Sub", "count"),
            returned(3, "int count"),
            // A ClassLoader.loadClass call with a null class name is ignored without crashing.
            request(4, "CLASS_LOADER_LOAD_CLASS", caller, null, LOADER));
    ProcessResult result = run(new SuggestCommand(), "--log", log);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertThat(result.stdout, containsString("-keep class com.example.Base\n"));
    assertThat(
        result.stdout,
        containsString(
            StringUtils.unixLines("-keep class com.example.Sub {", "    int count;", "}")));
    assertThat(
        result.stdout,
        containsString(
            StringUtils.unixLines(
                "# Optional probes: these requests threw in the logged run, so no rules are",
                "# suggested for them:",
                "#   ClassLoader.loadClass ? <- " + caller)));
    assertThat(
        result.stdout,
        not(containsString("#   Class.getDeclaredField com.example.Sub.count <- " + caller)));
  }

  @Test
  public void testUsageErrors() {
    AssistantUsageException e =
        assertThrows(AssistantUsageException.class, () -> run(new SuggestCommand()));
    assertEquals("Missing reflection log: pass --log <file>.", e.getMessage());
    e =
        assertThrows(
            AssistantUsageException.class,
            () -> run(new SuggestCommand(), "--log", "a.jsonl", "--unknown"));
    assertThat(e.getMessage(), containsString("--unknown"));
    e = assertThrows(AssistantUsageException.class, () -> run(new SuggestCommand(), "--log"));
    assertThat(e.getMessage(), containsString("--log"));
    e = assertThrows(AssistantUsageException.class, () -> run(new SuggestCommand(), "--log", ""));
    assertEquals("Invalid path for --log: path is empty", e.getMessage());
  }
}
