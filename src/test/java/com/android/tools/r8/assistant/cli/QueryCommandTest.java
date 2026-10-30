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
public class QueryCommandTest extends AssistantCommandTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private Path writeQueryLog() throws IOException {
    return writeFile(
        "query.jsonl",
        request(
            1,
            "CLASS_FOR_NAME",
            "app//com.example.Plugins.load(Plugins.java:12)",
            "com.example.plugins.CsvPlugin",
            "true",
            LOADER),
        returned(1, "com.example.plugins.CsvPlugin"),
        request(
            2,
            "CLASS_FOR_NAME",
            "com.example.Plugins.probe(Plugins.java:30)",
            "com.example.plugins.XmlPlugin",
            "false",
            LOADER),
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
        returnedMembers(4, "int zip", "java.lang.String city"));
  }

  @Test
  public void testUsage() {
    QueryCommand command = new QueryCommand();
    assertThat(
        command.getUsage(),
        containsString("Usage: java -jar r8-assistant.jar query --log <file>... [options]"));
    assertThat(command.getDescription(), not(containsString("\n")));
  }

  @Test
  public void testClassMemberAndEventFilters() throws Exception {
    Path log = writeQueryLog();

    // Filter by class pattern with negation and --stacks.
    ProcessResult byClass =
        run(
            new QueryCommand(),
            "--log",
            log,
            "--class",
            "!com.example.plugins.XmlPlugin,com.example.plugins.*",
            "--stacks");
    assertEquals(byClass.toString(), 0, byClass.exitCode);
    assertEquals(
        StringUtils.unixLines(
            "[returned] Class.forName com.example.plugins.CsvPlugin (CLASS_FOR_NAME)",
            "  caller: com.example.Plugins.load(Plugins.java:12)",
            "  result: com.example.plugins.CsvPlugin",
            "  log: " + log,
            "  stack:",
            "    com.example.Plugins.load(Plugins.java:12)",
            "1 of 4 events match."),
        byClass.stdout);

    // Filter by member name matching a bulk lookup's returned members.
    ProcessResult byBulkMember = run(new QueryCommand(), "--log", log, "--member", "city");
    assertEquals(byBulkMember.toString(), 0, byBulkMember.exitCode);
    assertEquals(
        StringUtils.unixLines(
            "[returned] Class.getDeclaredFields com.example.model.Address"
                + " (CLASS_GET_DECLARED_FIELDS)",
            "  caller: com.example.json.Serializer.write(Serializer.java:55)",
            "  returned members:",
            "    int zip",
            "    java.lang.String city",
            "  log: " + log,
            "1 of 4 events match."),
        byBulkMember.stdout);

    // Filter by event API glob and class pattern (AND across different filter kinds).
    ProcessResult combined =
        run(
            new QueryCommand(),
            "--log",
            log,
            "--class",
            "com.example.model.**",
            "--event",
            "Class.getDeclaredField");
    assertEquals(combined.toString(), 0, combined.exitCode);
    assertEquals(
        StringUtils.unixLines(
            "[returned] Class.getDeclaredField com.example.model.User.name"
                + " (CLASS_GET_DECLARED_FIELD)",
            "  caller: com.example.json.Serializer.read(Serializer.java:40)",
            "  result: java.lang.String name",
            "  log: " + log,
            "1 of 4 events match."),
        combined.stdout);

    // Filter that matches an event that threw.
    ProcessResult threw =
        run(new QueryCommand(), "--log", log, "--class", "com.example.plugins.XmlPlugin");
    assertEquals(threw.toString(), 0, threw.exitCode);
    assertEquals(
        StringUtils.unixLines(
            "[threw] Class.forName com.example.plugins.XmlPlugin (CLASS_FOR_NAME)",
            "  caller: com.example.Plugins.probe(Plugins.java:30)",
            "  log: " + log,
            "1 of 4 events match."),
        threw.stdout);
  }

  @Test
  public void testMissingFileAndUsageErrors() throws Exception {
    Path missing = temp.getRoot().toPath().resolve("missing.jsonl");
    ProcessResult result = run(new QueryCommand(), "--log", missing);
    assertEquals(result.toString(), 2, result.exitCode);
    assertEquals(
        StringUtils.unixLines("Error: the file given to --log does not exist: " + missing),
        result.stderr);

    AssistantUsageException e =
        assertThrows(AssistantUsageException.class, () -> run(new QueryCommand()));
    assertEquals("Missing reflection log: pass --log <file>.", e.getMessage());
    e =
        assertThrows(
            AssistantUsageException.class,
            () -> run(new QueryCommand(), "--log", "a.jsonl", "--class", "{invalid"));
    assertEquals("Invalid class pattern for --class: {invalid", e.getMessage());
  }
}
