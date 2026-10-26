// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The R8 Assistant command line, {@code java -jar r8-assistant.jar <command> [options]}, which
 * dispatches to the {@link AssistantCommand} registered in {@link AssistantCommands}.
 */
public final class AssistantCommandLine {

  /** How the assistant is invoked, as shown in the usage. */
  public static final String INVOCATION = "java -jar r8-assistant.jar";

  public static final int EXIT_SUCCESS = 0;

  /** The exit code when a command fails. */
  public static final int EXIT_FAILURE = 1;

  /** The exit code when the command line is invalid. */
  public static final int EXIT_USAGE_ERROR = 2;

  private static final String HELP = "--help";
  private static final String HELP_SHORTHAND = "-h";
  private static final String VERSION = "--version";

  // The column and the width of the descriptions in the usage, as for the options of commands.
  private static final int DESCRIPTION_COLUMN = 25;
  private static final int DESCRIPTION_WIDTH = 75;

  private AssistantCommandLine() {}

  /** Runs the command line and returns the exit code. Does not throw and does not exit. */
  public static int run(String[] args, PrintStream out, PrintStream err) {
    return run(args, out, err, AssistantCommands.createAll());
  }

  /** Runs the command line with the given commands and returns the exit code. */
  public static int run(
      String[] args, PrintStream out, PrintStream err, List<AssistantCommand> commands) {
    List<String> arguments = Arrays.asList(args);
    AssistantCommand command;
    try {
      command = parse(arguments, commands);
    } catch (AssistantUsageException e) {
      err.println("Error: " + e.getMessage());
      err.println("Run '" + getHelpCommandLine(arguments, commands) + "' for usage.");
      return EXIT_USAGE_ERROR;
    }
    if (command == null) {
      out.print(getHelpOrVersion(arguments, commands));
      return EXIT_SUCCESS;
    }
    try {
      return command.run(out, err);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      err.println("Error: " + command.getName() + " was interrupted.");
      return EXIT_FAILURE;
    } catch (RuntimeException e) {
      err.println("Error: " + command.getName() + " failed with an internal error:");
      e.printStackTrace(err);
      return EXIT_FAILURE;
    } catch (Exception e) {
      err.println("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      return EXIT_FAILURE;
    }
  }

  /**
   * Parses a command line, for example one from the documentation, without running it.
   *
   * @return the command with its arguments parsed, or null if the command line only prints the
   *     usage or the version.
   * @throws AssistantUsageException if the command line is invalid.
   */
  public static AssistantCommand parse(List<String> args) throws AssistantUsageException {
    return parse(args, AssistantCommands.createAll());
  }

  private static AssistantCommand parse(List<String> args, List<AssistantCommand> commands)
      throws AssistantUsageException {
    if (args.isEmpty()) {
      return null;
    }
    String name = args.get(0);
    if (isHelp(name) || name.equals(VERSION)) {
      if (args.size() > 1) {
        throw new AssistantUsageException("Unexpected argument after " + name + ": " + args.get(1));
      }
      return null;
    }
    if (name.startsWith("-")) {
      throw new AssistantUsageException("Unknown option: " + name);
    }
    AssistantCommand command = AssistantCommands.find(commands, name);
    if (command == null) {
      throw new AssistantUsageException(getUnknownCommandMessage(name, commands));
    }
    List<String> commandArgs = args.subList(1, args.size());
    if (commandArgs.stream().anyMatch(AssistantCommandLine::isHelp)) {
      return null;
    }
    command.parseArguments(commandArgs);
    return command;
  }

  private static boolean isHelp(String arg) {
    return arg.equals(HELP) || arg.equals(HELP_SHORTHAND);
  }

  private static String getUnknownCommandMessage(String name, List<AssistantCommand> commands) {
    String message =
        "Unknown command: "
            + name
            + ". Available commands: "
            + commands.stream().map(AssistantCommand::getName).collect(Collectors.joining(", "))
            + ".";
    if (name.endsWith(".apk") && AssistantCommands.find(commands, InstrumentCommand.NAME) != null) {
      message +=
          " To instrument an APK, run: "
              + INVOCATION
              + " "
              + InstrumentCommand.NAME
              + " --input <apk> --output <apk>";
    }
    return message;
  }

  private static String getHelpCommandLine(List<String> args, List<AssistantCommand> commands) {
    if (!args.isEmpty() && AssistantCommands.find(commands, args.get(0)) != null) {
      return INVOCATION + " " + args.get(0) + " " + HELP;
    }
    return INVOCATION + " " + HELP;
  }

  private static String getHelpOrVersion(List<String> args, List<AssistantCommand> commands) {
    if (args.isEmpty() || isHelp(args.get(0))) {
      return getUsage(commands);
    }
    if (args.get(0).equals(VERSION)) {
      return "R8 Assistant " + Version.getVersionString() + StringUtils.LINE_SEPARATOR;
    }
    return AssistantCommands.find(commands, args.get(0)).getUsage();
  }

  /** Returns the top-level usage, which lists the commands. */
  public static String getUsage(List<AssistantCommand> commands) {
    List<ParseFlagInfo> commandInfos = new ArrayList<>();
    for (AssistantCommand command : commands) {
      commandInfos.add(
          ParseFlagInfoImpl.flag(
              command.getName(),
              ImmutableList.of(),
              StringUtils.wrapToWidth(command.getDescription(), DESCRIPTION_WIDTH)));
    }
    List<ParseFlagInfo> optionInfos =
        ImmutableList.of(
            new ParseFlagInfoImpl(
                HELP,
                HELP,
                ImmutableList.of(HELP_SHORTHAND),
                ImmutableList.of("Print this message.")),
            ParseFlagInfoImpl.flag0(VERSION, "Print the version of R8 Assistant."));
    StringBuilder builder = new StringBuilder();
    builder
        .append("Usage: ")
        .append(INVOCATION)
        .append(" <command> [options]")
        .append(StringUtils.LINE_SEPARATOR)
        .append(" where <command> is one of:")
        .append(StringUtils.LINE_SEPARATOR);
    appendFlags(commandInfos, builder);
    builder.append(" and the options are:").append(StringUtils.LINE_SEPARATOR);
    appendFlags(optionInfos, builder);
    builder
        .append(" Run '")
        .append(INVOCATION)
        .append(" <command> ")
        .append(HELP)
        .append("' for the options of a command.")
        .append(StringUtils.LINE_SEPARATOR);
    return builder.toString();
  }

  private static void appendFlags(List<ParseFlagInfo> flags, StringBuilder builder) {
    new ParseFlagPrinter()
        .setHelpColumn(DESCRIPTION_COLUMN)
        .addFlags(flags)
        .appendLinesToBuilder(builder);
  }
}
