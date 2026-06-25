// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.CliParserBase;
import com.android.tools.r8.utils.internal.CliParserBase.OptionInfo;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class CliParserUtils {

  private static final int HELP_WIDTH = 25;

  public static List<ParseFlagInfo> getFlagInfos(CliParserBase<?> parser) {
    int idealWidth = 100;
    int descriptionWidth = idealWidth - HELP_WIDTH;

    List<ParseFlagInfo> flags = new ArrayList<>();
    for (OptionInfo info : parser.getOptionInfo()) {
      List<String> helpLines = StringUtils.wrapToWidth(info.description, descriptionWidth);
      List<String> alternatives;
      if (info.shorthand != null) {
        assert info.suffixLabel == null : "shorthands and prefixes cannot be combined.";
        alternatives = ImmutableList.of(commandString(info.shorthand, null, info.paramLabels));
      } else {
        alternatives = ImmutableList.of();
      }
      flags.add(
          new ParseFlagInfoImpl(
              null,
              commandString(info.name, info.suffixLabel, info.paramLabels),
              alternatives,
              helpLines));
    }
    return flags;
  }

  public static List<ParseFlagInfo> getFlagInfos(CliParser<?> parser) {
    return getFlagInfos(parser.baseParser());
  }

  /** Returns a string like {@code --output <file>} */
  private static String commandString(String name, String suffixLabel, List<String> paramLabels) {
    var sb = new StringBuilder(name);
    if (suffixLabel != null) {
      sb.append(suffixLabel);
    }
    for (var label : paramLabels) {
      sb.append(' ').append(label);
    }
    return sb.toString();
  }

  public static String getUsageMessage(CliParserBase<?> parser) {
    StringBuilder builder =
        new StringBuilder(parser.getUsageHeader()).append(System.lineSeparator());

    new ParseFlagPrinter()
        .setHelpColumn(HELP_WIDTH)
        .addFlags(getFlagInfos(parser))
        .appendLinesToBuilder(builder);
    return builder.toString();
  }

  public static String getUsageMessage(CliParser<?> parser) {
    return getUsageMessage(parser.baseParser());
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

  public static DiagnosticsLevel parseDiagnosticsLevel(
      String level, Consumer<Diagnostic> errorHandler, Origin origin) {
    switch (level) {
      case "error":
        return DiagnosticsLevel.ERROR;
      case "warning":
        return DiagnosticsLevel.WARNING;
      case "info":
        return DiagnosticsLevel.INFO;
      case "none":
        return DiagnosticsLevel.NONE;
      default:
        errorHandler.accept(
            new StringDiagnostic(
                "Invalid diagnostics level '"
                    + level
                    + "'. Valid levels are 'error', 'warning', 'info' and 'none'.",
                origin));
        return null;
    }
  }

  /**
   * @param diagnosticType either an empty string or a {@code :<class>} string.
   * @param from the diagnostics level mapped from (see {@link #parseDiagnosticsLevel})
   * @param to the diagnostics level mapped to (see {@link #parseDiagnosticsLevel})
   * @param handler receives {@code diagnosticType} stripped of {@code :} and the two levels if
   *     parsable.
   */
  public static void parseDiagnosticsMapping(
      String diagnosticType,
      String from,
      String to,
      Consumer<DiagnosticsMapping> handler,
      Consumer<Diagnostic> errorHandler,
      Origin origin) {
    String diagnosticsClassName = "";
    if (!diagnosticType.isEmpty()) {
      if (diagnosticType.length() == 1 || diagnosticType.charAt(0) != ':') {
        errorHandler.accept(
            new StringDiagnostic(
                "Invalid diagnostics type specification --map-diagnostics" + diagnosticType + ".",
                origin));
        return;
      }
      diagnosticsClassName = diagnosticType.substring(1);
    }
    DiagnosticsLevel fromLevel = parseDiagnosticsLevel(from, errorHandler, origin);
    DiagnosticsLevel toLevel = parseDiagnosticsLevel(to, errorHandler, origin);
    if (fromLevel != null && toLevel != null) {
      handler.accept(new DiagnosticsMapping(diagnosticsClassName, fromLevel, toLevel));
    }
    // parseDiagnosticsLevel reports its own errors, so no reporting necessary.
  }

  public static class DiagnosticsMapping {
    public final String diagnosticType;
    public final DiagnosticsLevel from;
    public final DiagnosticsLevel to;

    public DiagnosticsMapping(String diagnosticType, DiagnosticsLevel from, DiagnosticsLevel to) {
      this.diagnosticType = diagnosticType;
      this.from = from;
      this.to = to;
    }
  }
}
