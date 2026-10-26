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
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DiffCommandTest extends AssistantCommandTestBase {

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
        request(
            3,
            "CLASS_GET_DECLARED_FIELD",
            "com.example.json.Serializer.read(Serializer.java:40)",
            "java.lang.String",
            "com.example.model.User",
            "name"),
        returned(3, "java.lang.String name"),
        request(
            4,
            "CLASS_GET_DECLARED_FIELDS",
            "com.example.json.Serializer.write(Serializer.java:55)",
            "com.example.model.Address"),
        returnedMembers(4, "int zip", "java.lang.String city"),
        request(
            5,
            "SERVICE_LOADER_LOAD",
            "com.example.Plugins.services(Plugins.java:50)",
            "com.example.spi.Codec",
            LOADER),
        returned(5, "java.util.ServiceLoader"),
        request(
            6,
            "ATOMIC_FIELD_UPDATER_NEW_UPDATER",
            "com.example.sync.SyncState.<clinit>(SyncState.java:10)",
            "int",
            "com.example.sync.SyncState",
            "state"),
        returned(
            6,
            "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl"),
        request(
            7,
            "CLASS_GET_NAME",
            "com.example.log.Tags.simple(Tags.java:12)",
            "com.example.ui.MainActivity$Header",
            "SIMPLE_NAME"),
        returned(7, "Header"),
        request(
            8,
            "CLASS_GET_GENERIC_SUPERCLASS",
            "com.example.json.TypeTokens.read(TypeTokens.java:10)",
            "com.example.json.TypeTokens$1"),
        returned(8, "com.example.json.TypeToken<...>"));
  }

  @Test
  public void testUsage() {
    DiffCommand command = new DiffCommand();
    assertThat(
        command.getUsage(),
        containsString(
            "Usage: java -jar r8-assistant.jar diff --log <file>... --rules <file>... [options]"));
    assertThat(command.getDescription(), not(containsString("\n")));
  }

  @Test
  public void testDiffAndReport() throws Exception {
    Path log = writeAppLog();
    Path rules =
        writeFile(
            "rules.pro",
            "-keep class com.example.plugins.CsvPlugin {",
            "    void <init>();",
            "}",
            "-keepclassmembers class com.example.model.** {",
            "    <fields>;",
            "}",
            "-keep class com.example.unused.Legacy {",
            "    *;",
            "}",
            "-keep class * implements com.example.spi.Codec",
            "-if class com.example.plugins.CsvPlugin -keep class com.example.plugins.CsvHelper",
            "-assumenosideeffects class android.util.Log {",
            "    public static int d(...);",
            "}");
    Path proposed = temp.getRoot().toPath().resolve("out").resolve("proposed.pro");
    ProcessResult result =
        run(new DiffCommand(), "--log", log, "--rules", rules, "--output", proposed);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertThat(result.stdout, containsString("R8 Assistant keep rule diff\n"));
    assertThat(
        result.stdout,
        containsString(
            "Rules: 6 (1 justified, 1 narrowed, 1 not observed, 1 undecided, 1 conditional, 1 not"
                + " analyzed)\n"));
    assertThat(result.stdout, containsString("  JUSTIFIED  " + rules + ":1 -keep class"));
    assertThat(
        result.stdout,
        containsString(
            "      Class.forName com.example.plugins.CsvPlugin <-"
                + " com.example.Plugins.load(Plugins.java:12)\n"));
    assertThat(
        result.stdout, containsString("  NARROWED  " + rules + ":4 -keepclassmembers class"));
    assertThat(
        result.stdout,
        containsString(
            "      Class.getDeclaredField com.example.model.User.name <-"
                + " com.example.json.Serializer.read(Serializer.java:40)\n"));
    assertThat(result.stdout, containsString("  NOT OBSERVED  " + rules + ":7 -keep class"));
    assertThat(result.stdout, containsString("  UNDECIDED  " + rules + ":10 -keep class"));
    assertThat(result.stdout, containsString("  CONDITIONAL  " + rules + ":11 -if class"));
    assertThat(
        result.stdout, containsString("  NOT ANALYZED  " + rules + ":12 -assumenosideeffects"));
    assertThat(
        result.stdout,
        containsString("Not kept by the rules (crash risk in the release build):\n"));
    assertThat(result.stdout, containsString("  field com.example.sync.SyncState.state\n"));
    assertThat(
        result.stdout, containsString("Possibly not kept by the rules (the logs cannot show it):"));
    assertThat(
        result.stdout,
        containsString(
            "Not kept by the rules: the InnerClasses and EnclosingMethod attributes, used by"
                + " getSimpleName() and the nesting flags of nested classes.\n"));
    assertThat(
        result.stdout,
        containsString(
            "Not kept by the rules: the Signature attribute (-keepattributes Signature), used by"
                + " getGenericSuperclass() and getGenericInterfaces().\n"));
    assertThat(result.stdout, containsString("Proposed rules written to " + proposed + ".\n"));

    String proposedText = readFile(proposed);
    assertThat(proposedText, containsString("# Justified: " + rules + ":1\n"));
    assertThat(proposedText, containsString("# Narrowed: " + rules + ":4 "));
    assertThat(
        proposedText,
        containsString(
            StringUtils.unixLines(
                "-keepclassmembers class com.example.model.Address {",
                "    <fields>;",
                "}",
                "-keepclassmembers class com.example.model.User {",
                "    java.lang.String name;",
                "}")));
    assertThat(
        proposedText, containsString("# Removed, not observed in the logs: " + rules + ":7 "));
    assertThat(proposedText, not(containsString("\n-keep class com.example.unused.Legacy")));
    assertThat(proposedText, containsString("# Undecided, kept as is: " + rules + ":10 "));
    assertThat(proposedText, containsString("# Conditional, kept as is: " + rules + ":11\n"));
    assertThat(proposedText, containsString("# Not analyzed, kept as is: " + rules + ":12\n"));
    assertThat(proposedText, containsString(KeepRuleWriter.NESTING_ATTRIBUTES_RULE));
    assertThat(proposedText, containsString(KeepRuleWriter.SIGNATURE_ATTRIBUTE_RULE));

    // Re-running diff with the proposed rules must report all 10 requirements kept.
    ProcessResult rerun = run(new DiffCommand(), "--log", log, "--rules", proposed);
    assertEquals(rerun.toString(), 0, rerun.exitCode);
    assertEquals("", rerun.stderr);
    assertThat(rerun.stdout, containsString("Requirements in the logs: 10 (10 kept)\n"));
  }

  @Test
  public void testEmptyLog() throws Exception {
    Path log = writeFile("empty.jsonl");
    Path rules = writeFile("rules.pro", "-keep class com.example.A");
    ProcessResult result = run(new DiffCommand(), "--log", log, "--rules", rules);
    assertEquals(result.toString(), 1, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals(
        StringUtils.unixLines(
            "Error: no reflection events in "
                + log
                + ". Without events every rule would look unused."),
        result.stderr);
  }

  @Test
  public void testMissingAndInvalidFiles() throws Exception {
    Path log = writeAppLog();
    Path missing = temp.getRoot().toPath().resolve("missing.pro");
    ProcessResult result = run(new DiffCommand(), "--log", log, "--rules", missing);
    assertEquals(result.toString(), 2, result.exitCode);
    assertEquals(
        StringUtils.unixLines("Error: the file given to --rules does not exist: " + missing),
        result.stderr);

    Path invalidRules = writeFile("invalid.pro", "-keep class {");
    result = run(new DiffCommand(), "--log", log, "--rules", invalidRules);
    assertEquals(result.toString(), 2, result.exitCode);
    assertThat(result.stderr, containsString("Error: invalid keep rules:\n"));
  }

  @Test
  public void testResourceBundleAndPartialCoverage() throws Exception {
    Path log =
        writeFile(
            "bundle.jsonl",
            request(
                1,
                "RESOURCE_BUNDLE_GET_BUNDLE",
                "com.example.i18n.I18n.load(I18n.java:15)",
                "com.example.i18n.Messages",
                "de",
                LOADER),
            returned(1, "com.example.i18n.Messages_de"),
            request(
                2,
                "CLASS_FOR_NAME",
                "com.example.Plugins.load(Plugins.java:12)",
                "com.example.plugins.CsvPlugin",
                "true",
                LOADER),
            returned(2, "com.example.plugins.CsvPlugin"));
    Path rules =
        writeFile(
            "rules.pro",
            "-keep class com.example.i18n.Messages, com.example.i18n.Messages_* {",
            "    <init>();",
            "}",
            "-keep,allowobfuscation class com.example.plugins.CsvPlugin");
    ProcessResult result = run(new DiffCommand(), "--log", log, "--rules", rules);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertThat(
        result.stdout,
        containsString(
            "  JUSTIFIED  "
                + rules
                + ":1 -keep class com.example.i18n.Messages, com.example.i18n.Messages_*"));
    assertThat(
        result.stdout,
        containsString(
            "  NARROWED  "
                + rules
                + ":4 -keep,allowobfuscation class com.example.plugins.CsvPlugin"));
    assertThat(
        result.stdout,
        containsString(
            StringUtils.unixLines(
                "-keep class com.example.i18n.Messages, com.example.i18n.Messages_* {",
                "    <init>();",
                "}")));
  }

  @Test
  public void testNegatedMemberModifier() throws Exception {
    Path log =
        writeFile(
            "public_field.jsonl",
            request(
                1,
                "CLASS_GET_FIELD",
                "com.example.json.Serializer.read(Serializer.java:40)",
                "java.lang.String",
                "com.example.model.User",
                "name"),
            returned(1, "java.lang.String name"));
    Path rules =
        writeFile(
            "rules.pro",
            "-keepclassmembers class com.example.model.** {",
            "    !private !protected java.lang.String name;",
            "}");
    Path proposed = temp.getRoot().toPath().resolve("proposed.pro");
    ProcessResult result =
        run(new DiffCommand(), "--log", log, "--rules", rules, "--output", proposed);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals("", result.stderr);
    assertThat(result.stdout, containsString("  NARROWED  " + rules + ":1 "));
    assertThat(
        readFile(proposed),
        containsString(
            StringUtils.unixLines(
                "-keepclassmembers class com.example.model.User {",
                "    !private !protected java.lang.String name;",
                "}")));
  }

  @Test
  public void testUsageErrors() {
    AssistantUsageException e =
        assertThrows(AssistantUsageException.class, () -> run(new DiffCommand()));
    assertEquals("Missing reflection log: pass --log <file>.", e.getMessage());
    e =
        assertThrows(
            AssistantUsageException.class, () -> run(new DiffCommand(), "--log", "a.jsonl"));
    assertEquals("Missing keep rules: pass --rules <file>.", e.getMessage());
  }
}
