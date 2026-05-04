// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.internal.StringUtils;

@KeepForApi
public class ApiDatabaseGenerator {

  public static void run(ApiDatabaseGeneratorCommand command) {
    System.out.println("Not implemented yet");
  }

  private static void run(String[] args) throws CompilationFailedException {
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      System.out.println(ApiDatabaseGeneratorCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("ApiDatabaseGenerator " + Version.getVersionString());
      return;
    }
    run(command);
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines(
              "Invalid invocation.", ApiDatabaseGeneratorCommandParser.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
