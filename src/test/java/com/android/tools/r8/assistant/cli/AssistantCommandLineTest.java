// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static com.android.tools.r8.assistant.cli.AssistantCommandLine.INVOCATION;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssistantCommandLineTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static ProcessResult run(List<AssistantCommand> commands, String... args)
      throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    String encoding = StandardCharsets.UTF_8.name();
    int exitCode;
    try (PrintStream outStream = new PrintStream(out, true, encoding);
        PrintStream errStream = new PrintStream(err, true, encoding)) {
      exitCode = AssistantCommandLine.run(args, outStream, errStream, commands);
    }
    return new ProcessResult(
        exitCode, out.toString(encoding), err.toString(encoding), String.join(" ", args));
  }

  private static ProcessResult run(String... args) throws UnsupportedEncodingException {
    return run(AssistantCommands.createAll(), args);
  }

  private static void assertUsageError(ProcessResult result, String error, String helpCommand) {
    assertEquals(result.toString(), AssistantCommandLine.EXIT_USAGE_ERROR, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals(
        StringUtils.lines("Error: " + error, "Run '" + helpCommand + "' for usage."),
        result.stderr);
  }

  @Test
  public void testUsage() throws Exception {
    String usage = AssistantCommandLine.getUsage(AssistantCommands.createAll());
    assertThat(usage, containsString("Usage: java -jar r8-assistant.jar <command> [options]"));
    assertThat(
        usage,
        containsString(
            "  instrument              # Instrument a debug APK to log reflective operations at"
                + " runtime."));
    assertThat(usage, containsString("--help"));
    assertThat(usage, containsString("--version"));
    for (String args : ImmutableList.of("", "--help", "-h")) {
      ProcessResult result = args.isEmpty() ? run() : run(args);
      assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
      assertEquals(usage, result.stdout);
      assertEquals("", result.stderr);
    }
  }

  @Test
  public void testVersion() throws Exception {
    ProcessResult result = run("--version");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(StringUtils.lines("R8 Assistant " + Version.getVersionString()), result.stdout);
    assertEquals("", result.stderr);
  }

  @Test
  public void testCommandHelp() throws Exception {
    String usage = new InstrumentCommand().getUsage();
    assertThat(
        usage,
        containsString(
            "Usage: java -jar r8-assistant.jar instrument --input <apk> --output <apk> [options]"));
    for (List<String> args :
        ImmutableList.of(
            ImmutableList.of("instrument", "--help"),
            ImmutableList.of("instrument", "-h"),
            ImmutableList.of("instrument", "--input", "in.apk", "--unknown", "--help"))) {
      ProcessResult result = run(args.toArray(new String[0]));
      assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
      assertEquals(usage, result.stdout);
      assertEquals("", result.stderr);
    }
  }

  @Test
  public void testUnknownCommand() throws Exception {
    assertUsageError(
        run("foo"),
        "Unknown command: foo. Available commands: instrument, query.",
        INVOCATION + " --help");
    assertUsageError(
        run("app.apk", "--output", "out.apk"),
        "Unknown command: app.apk. Available commands: instrument, query. To instrument an APK,"
            + " run: "
            + INVOCATION
            + " instrument --input <apk> --output <apk>",
        INVOCATION + " --help");
  }

  @Test
  public void testUnknownOption() throws Exception {
    assertUsageError(run("--foo"), "Unknown option: --foo", INVOCATION + " --help");
  }

  @Test
  public void testArgumentsAfterHelpOrVersion() throws Exception {
    assertUsageError(
        run("--help", "instrument"),
        "Unexpected argument after --help: instrument",
        INVOCATION + " --help");
    assertUsageError(
        run("--version", "--bogus"),
        "Unexpected argument after --version: --bogus",
        INVOCATION + " --help");
  }

  @Test
  public void testCommandUsageErrors() throws Exception {
    String help = INVOCATION + " instrument --help";
    assertUsageError(run("instrument"), "Missing input APK: pass --input <apk>.", help);
    assertUsageError(
        run("instrument", "in.apk", "out.apk", "--min-api", "21"),
        "Unknown option: --min-api",
        help);
    assertUsageError(
        run("instrument", "in.apk", "out.apk", "extra.apk"),
        "Unexpected argument: extra.apk",
        help);
  }

  @Test
  public void testCommandFailure() throws Exception {
    ProcessResult result = run("instrument", "missing.apk", "out.apk");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals("", result.stdout);
    assertEquals(
        StringUtils.lines("Error: Input APK does not exist or is not a file: missing.apk"),
        result.stderr);
  }

  @Test
  public void testExitCodeOfCommand() throws Exception {
    FakeCommand command = new FakeCommand("fake", (out, err) -> 3);
    ProcessResult result = run(ImmutableList.of(command), "fake", "a", "b");
    assertEquals(result.toString(), 3, result.exitCode);
    assertEquals(ImmutableList.of("a", "b"), command.arguments);
    assertTrue(command.ran);
  }

  @Test
  public void testOutputOfCommand() throws Exception {
    FakeCommand command =
        new FakeCommand(
            "fake",
            (out, err) -> {
              out.println("out");
              err.println("err");
              return AssistantCommandLine.EXIT_SUCCESS;
            });
    ProcessResult result = run(ImmutableList.of(command), "fake");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(StringUtils.lines("out"), result.stdout);
    assertEquals(StringUtils.lines("err"), result.stderr);
  }

  @Test
  public void testCheckedExceptionIsReportedWithoutStackTrace() throws Exception {
    ProcessResult result =
        run(
            ImmutableList.of(
                new FakeCommand(
                    "fake",
                    (out, err) -> {
                      throw new IOException("Could not write out.apk");
                    })),
            "fake");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(StringUtils.lines("Error: Could not write out.apk"), result.stderr);

    result =
        run(
            ImmutableList.of(
                new FakeCommand(
                    "fake",
                    (out, err) -> {
                      throw new IOException();
                    })),
            "fake");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(StringUtils.lines("Error: java.io.IOException"), result.stderr);
  }

  @Test
  public void testRuntimeExceptionIsReportedWithStackTrace() throws Exception {
    ProcessResult result =
        run(
            ImmutableList.of(
                new FakeCommand(
                    "fake",
                    (out, err) -> {
                      throw new IllegalStateException("Unexpected state");
                    })),
            "fake");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertThat(
        result.stderr,
        containsString(
            StringUtils.lines(
                "Error: fake failed with an internal error:",
                "java.lang.IllegalStateException: Unexpected state")));
    assertThat(result.stderr, containsString("at "));
  }

  @Test
  public void testInterruptedCommand() throws Exception {
    ProcessResult result =
        run(
            ImmutableList.of(
                new FakeCommand(
                    "fake",
                    (out, err) -> {
                      throw new InterruptedException();
                    })),
            "fake");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_FAILURE, result.exitCode);
    assertEquals(StringUtils.lines("Error: fake was interrupted."), result.stderr);
    // Clear the interrupt status of the test thread.
    assertTrue(Thread.interrupted());
  }

  @Test
  public void testCommandIsNotRunIfParsingFails() throws Exception {
    FakeCommand command = new FakeCommand("fake", (out, err) -> 0);
    assertUsageError(
        run(ImmutableList.of(command), "fake", "--bad"),
        "Bad argument: --bad",
        INVOCATION + " fake --help");
    assertFalse(command.ran);
  }

  @Test
  public void testCommandIsNeitherParsedNorRunForHelp() throws Exception {
    FakeCommand command = new FakeCommand("fake", (out, err) -> 0);
    ProcessResult result = run(ImmutableList.of(command), "fake", "--bad", "-h");
    assertEquals(result.toString(), AssistantCommandLine.EXIT_SUCCESS, result.exitCode);
    assertEquals(command.getUsage(), result.stdout);
    assertNull(command.arguments);
    assertFalse(command.ran);
  }

  @Test
  public void testUsageListsCommandsInOrder() throws Exception {
    List<AssistantCommand> commands =
        ImmutableList.of(
            new FakeCommand("second", (out, err) -> 0), new FakeCommand("first", (out, err) -> 0));
    String usage = AssistantCommandLine.getUsage(commands);
    assertThat(usage, containsString("  second                  # The second command."));
    assertThat(usage, containsString("  first                   # The first command."));
    assertTrue(usage.indexOf("second") < usage.indexOf("first"));
    assertThat(usage, not(containsString("instrument")));
    assertUsageError(
        run(commands, "third"),
        "Unknown command: third. Available commands: second, first.",
        INVOCATION + " --help");
  }

  @Test
  public void testParseWithoutRunning() throws Exception {
    AssistantCommand command =
        AssistantCommandLine.parse(
            ImmutableList.of("instrument", "-i", "in.apk", "-o", "out.apk", "--temp-dir", "tmp"));
    assertTrue(command instanceof InstrumentCommand);
    assertEquals(Paths.get("in.apk"), ((InstrumentCommand) command).getInput());
    assertEquals(Paths.get("out.apk"), ((InstrumentCommand) command).getOutput());
    for (List<String> args :
        ImmutableList.<List<String>>of(
            ImmutableList.of(),
            ImmutableList.of("--help"),
            ImmutableList.of("--version"),
            ImmutableList.of("instrument", "--help"))) {
      assertNull(AssistantCommandLine.parse(args));
    }
    assertEquals(
        "Unknown command: foo. Available commands: instrument, query.",
        assertThrows(
                AssistantUsageException.class,
                () -> AssistantCommandLine.parse(ImmutableList.of("foo")))
            .getMessage());
    assertEquals(
        "Missing output APK: pass --output <apk>.",
        assertThrows(
                AssistantUsageException.class,
                () -> AssistantCommandLine.parse(ImmutableList.of("instrument", "in.apk")))
            .getMessage());
  }

  @Test
  public void testRegisteredCommands() {
    Set<String> names = new HashSet<>();
    for (AssistantCommand command : AssistantCommands.createAll()) {
      String name = command.getName();
      assertTrue("Duplicate command: " + name, names.add(name));
      assertFalse(name, name.startsWith("-"));
      assertFalse(name, command.getDescription().contains("\n"));
      assertTrue(
          command.getUsage(), command.getUsage().startsWith("Usage: " + INVOCATION + " " + name));
      AssistantCommand created = AssistantCommands.find(AssistantCommands.createAll(), name);
      assertEquals(command.getClass(), created.getClass());
      assertTrue("Commands must be new instances", created != command);
    }
    assertTrue(names.contains(InstrumentCommand.NAME));
    assertNull(AssistantCommands.find(AssistantCommands.createAll(), "foo"));
  }

  private static class FakeCommand extends AssistantCommand {

    interface Action {
      int run(PrintStream out, PrintStream err) throws Exception;
    }

    private final String name;
    private final Action action;
    private List<String> arguments = null;
    private boolean ran = false;

    FakeCommand(String name, Action action) {
      this.name = name;
      this.action = action;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return "The " + name + " command.";
    }

    @Override
    public String getUsage() {
      return StringUtils.lines("Usage: " + INVOCATION + " " + name + " [--bad]");
    }

    @Override
    public void parseArguments(List<String> args) throws AssistantUsageException {
      if (args.contains("--bad")) {
        throw new AssistantUsageException("Bad argument: --bad");
      }
      arguments = args;
    }

    @Override
    public int run(PrintStream out, PrintStream err) throws Exception {
      ran = true;
      return action.run(out, err);
    }
  }
}
