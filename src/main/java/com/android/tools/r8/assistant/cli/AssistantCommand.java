// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import java.io.PrintStream;
import java.util.List;

/**
 * A subcommand of the R8 Assistant command line, {@code java -jar r8-assistant.jar <command>
 * [options]}.
 *
 * <p>Parsing is separate from running: {@link #parseArguments} only validates the arguments and
 * records them, without touching the file system or running anything, so that command lines (for
 * example the ones in the skill documentation) can be checked without running them. {@link #run} is
 * only called after {@link #parseArguments} succeeded. To add a command, implement this class and
 * register it in {@link AssistantCommands}.
 */
public abstract class AssistantCommand {

  /** The name of the command on the command line, for example {@code instrument}. */
  public abstract String getName();

  /** A one line description of the command, listed in the top-level usage. */
  public abstract String getDescription();

  /** The full usage of the command, printed for {@code <command> --help}. */
  public abstract String getUsage();

  /**
   * Parses and validates the arguments that follow the command name. Must not have side effects.
   *
   * @throws AssistantUsageException if the arguments are invalid. The message is shown to the user.
   */
  public abstract void parseArguments(List<String> args) throws AssistantUsageException;

  /**
   * Runs the command after {@link #parseArguments} succeeded.
   *
   * @return the exit code of the tool.
   */
  public abstract int run(PrintStream out, PrintStream err) throws Exception;
}
