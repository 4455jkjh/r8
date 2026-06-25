// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.internal.StringUtils;

public class RelocatorCommandLine {

  /**
   * EXPERIMENTAL - The CLI is subject to change. Command-line entry to Relocator.
   *
   * <p>See {@link RelocatorCommand#getUsageMessage()} or run {@code relocator --help} for usage
   * information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines("Invalid invocation.", RelocatorCommand.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }

  public static void run(String[] args) throws CompilationFailedException {
    RelocatorCommand command =
        RelocatorCommand.Builder.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      System.out.println(RelocatorCommand.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("Relocator " + Version.getVersionString());
      return;
    }
    Relocator.run(command);
  }
}
