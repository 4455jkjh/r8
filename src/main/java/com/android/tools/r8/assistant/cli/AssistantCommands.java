// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import java.util.ArrayList;
import java.util.List;

/** The registry of the R8 Assistant subcommands. */
public final class AssistantCommands {

  private AssistantCommands() {}

  /** Returns new instances of all commands, in the order they are listed in the usage. */
  public static List<AssistantCommand> createAll() {
    List<AssistantCommand> commands = new ArrayList<>();
    commands.add(new InstrumentCommand());
    commands.add(new SuggestCommand());
    commands.add(new DiffCommand());
    commands.add(new QueryCommand());
    return commands;
  }

  static AssistantCommand find(List<AssistantCommand> commands, String name) {
    for (AssistantCommand command : commands) {
      if (command.getName().equals(name)) {
        return command;
      }
    }
    return null;
  }
}
