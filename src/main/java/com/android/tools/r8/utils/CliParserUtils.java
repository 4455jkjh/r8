// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.CliParser.OptionInfo;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class CliParserUtils {

  private static final int HELP_WIDTH = 25;

  public static List<ParseFlagInfo> getFlagInfos(CliParser<?> parser) {
    int idealWidth = 100;
    int descriptionWidth = idealWidth - HELP_WIDTH;

    List<ParseFlagInfo> flags = new ArrayList<>();
    for (OptionInfo info : parser.getOptionInfo()) {
      List<String> helpLines = StringUtils.wrapToWidth(info.description, descriptionWidth);
      List<String> alternatives =
          info.shorthand != null
              ? ImmutableList.of(commandString(info.shorthand, info.paramLabels))
              : ImmutableList.of();
      flags.add(
          new ParseFlagInfoImpl(
              null, commandString(info.name, info.paramLabels), alternatives, helpLines));
    }
    return flags;
  }

  /** Returns a string like {@code --output <file>} */
  private static String commandString(String name, List<String> paramLabels) {
    var sb = new StringBuilder(name);
    for (var label : paramLabels) {
      sb.append(' ').append(label);
    }
    return sb.toString();
  }

  public static String getUsageMessage(CliParser<?> parser) {
    StringBuilder builder =
        new StringBuilder(parser.getUsageHeader()).append(System.lineSeparator());

    new ParseFlagPrinter()
        .setHelpColumn(HELP_WIDTH)
        .addFlags(getFlagInfos(parser))
        .appendLinesToBuilder(builder);
    return builder.toString();
  }

  public static void parsePositiveInt(
      String arg, IntConsumer handler, Consumer<String> errorConsumer) {
    try {
      int parsedArg = Integer.parseInt(arg);
      if (parsedArg < 1) {
        errorConsumer.accept(arg + " is not a positive integer");
      } else {
        handler.accept(parsedArg);
      }
    } catch (NumberFormatException e) {
      errorConsumer.accept(arg + " is not an integer");
    }
  }
}
