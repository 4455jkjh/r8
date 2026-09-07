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
import com.android.tools.r8.utils.internal.CliParserBase.HelpInfo;
import com.android.tools.r8.utils.internal.CliParserBase.OptionInfo;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class CliParserUtils {

  /** The column that help info starts printing at. */
  private static final int DESCRIPTION_OFFSET = 25;

  /** The max width of the output text column (by best effort). */
  private static final int MAX_WIDTH = 100;

  /** The max width of the description (by best effort). */
  private static final int DESCRIPTION_WIDTH = MAX_WIDTH - DESCRIPTION_OFFSET;

  public static List<ParseFlagInfo> getFlagInfos(CliParserBase<?> parser) {
    List<ParseFlagInfo> flags = new ArrayList<>();
    for (OptionInfo info : parser.getOptionInfo()) {
      flags.add(toParseFlagInfo(info));
    }
    return flags;
  }

  private static ParseFlagInfo toParseFlagInfo(OptionInfo info) {
    List<String> helpLines = StringUtils.wrapToWidth(info.description, DESCRIPTION_WIDTH);
    List<String> alternatives;
    if (info.shorthand != null) {
      alternatives =
          ImmutableList.of(commandString(info.shorthand, info.suffixLabel, info.paramLabels));
    } else {
      alternatives = ImmutableList.of();
    }
    return new ParseFlagInfoImpl(
        null,
        commandString(info.name, info.suffixLabel, info.paramLabels),
        alternatives,
        helpLines);
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

  public static String getUsageMessage(CliParser<?> parser) {
    return getUsageMessage(parser.baseParser());
  }

  public static String getUsageMessage(CliParserBase<?> parser) {
    var builder = new StringBuilder(parser.getUsageHeader()).append(System.lineSeparator());

    List<ParseFlagInfo> currentFlags = new ArrayList<>();
    for (HelpInfo helpInfo : parser.getHelpInfo()) {
      if (helpInfo.isOption()) {
        currentFlags.add(toParseFlagInfo(helpInfo.asOption()));
      } else if (helpInfo.isHelpText()) {
        appendAndClear(currentFlags, builder);
        builder.append(helpInfo.asHelpText().text).append(System.lineSeparator());
      }
    }
    appendAndClear(currentFlags, builder);
    return builder.toString();
  }

  private static void appendAndClear(List<ParseFlagInfo> currentFlags, StringBuilder builder) {
    if (!currentFlags.isEmpty()) {
      appendFlags(currentFlags, builder);
      currentFlags.clear();
    }
  }

  private static void appendFlags(List<ParseFlagInfo> currentFlags, StringBuilder builder) {
    new ParseFlagPrinter()
        .setHelpColumn(DESCRIPTION_OFFSET)
        .addFlags(currentFlags)
        .appendLinesToBuilder(builder);
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

  public static void parseApiLevel(
      String arg, Consumer<AndroidApiLevel> handler, Consumer<String> errorConsumer) {
    try {
      handler.accept(AndroidApiLevel.parseAndroidApiLevel(arg));
    } catch (IllegalArgumentException e) {
      // Note that NumberFormatException is a subclass of IllegalArgumentException.
      String rawMessage = e.getMessage();
      String message = rawMessage == null ? "" : ", " + rawMessage;
      errorConsumer.accept("Invalid API version: " + arg + message);
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
