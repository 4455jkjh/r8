// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.CliParserBase;
import com.android.tools.r8.utils.internal.CliParserBase.HelpInfo;
import com.android.tools.r8.utils.internal.CliParserBase.OptionInfo;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

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

  public static void parseUncheckedApiLevel(
      String arg, Consumer<UncheckedApiLevel> handler, Consumer<String> errorConsumer) {
    try {
      handler.accept(UncheckedApiLevel.parse(arg));
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

  public static <B> Consumer<CliParser<B>> addVersionOption(Consumer<B> action) {
    return parser -> parser.option0("--version", "Print version.", action, "-v");
  }

  public static <B> Consumer<CliParser<B>> addHelpOption(Consumer<B> action) {
    return parser -> parser.option0("--help", "Print usage information.", action, "-h");
  }

  /**
   * Adds the {@code --min-api} option using {@link AndroidApiLevel#getDefault()} as the default API
   * level listed in the option description.
   */
  public static <B> Consumer<CliParser<B>> addMinApiOption(
      Predicate<B> hasDefinedApiLevel,
      BiConsumer<B, UncheckedApiLevel> action,
      BiConsumer<B, String> errorHandler) {
    return addMinApiOption(hasDefinedApiLevel, action, errorHandler, AndroidApiLevel.getDefault());
  }

  public static <B> Consumer<CliParser<B>> addMinApiOption(
      Predicate<B> hasDefinedApiLevel,
      BiConsumer<B, UncheckedApiLevel> action,
      BiConsumer<B, String> errorHandler,
      AndroidApiLevel defaultApiLevel) {
    return parser ->
        parser.option1(
            "--min-api",
            "<number>[.<number>]",
            "Minimum Android API level compatibility (default: "
                + defaultApiLevel.getNumericString()
                + ").",
            (state, arg) -> {
              if (hasDefinedApiLevel.test(state)) {
                errorHandler.accept(state, "Cannot set multiple --min-api options");
              } else {
                parseUncheckedApiLevel(
                    arg,
                    apiLevel -> action.accept(state, apiLevel),
                    error -> errorHandler.accept(state, "Invalid argument to --min-api: " + error));
              }
            });
  }

  public static <B> Consumer<CliParser<B>> addThreadCountOption(
      BiConsumer<B, Integer> action,
      Function<B, ? extends DiagnosticsHandler> getDiagnosticsHandler,
      Function<B, Origin> getOrigin) {
    return parser ->
        parser.option1(
            "--thread-count",
            "<number>",
            "Use <number> of threads. If not specified the number will be based on heuristics"
                + " taking the number of cores into account.",
            (state1, arg) ->
                parsePositiveInt(
                    arg,
                    threadCount -> action.accept(state1, threadCount),
                    error1 ->
                        getDiagnosticsHandler
                            .apply(state1)
                            .error(
                                new StringDiagnostic(
                                    "Invalid argument to --thread-count: " + error1,
                                    getOrigin.apply(state1)))));
  }

  public static <B> Consumer<CliParser<B>> addMapDiagnosticsOption(
      BiConsumer<B, DiagnosticsMapping> action,
      BiConsumer<B, Diagnostic> errorHandler,
      Function<B, Origin> getOrigin) {
    return parser ->
        parser.prefix2(
            "--map-diagnostics",
            "[:<type>]",
            "<from-level>",
            "<to-level>",
            "Map diagnostics of <type> (default any) reported as <from-level> to <to-level> where"
                + " <from-level> and <to-level> are one of 'none', 'info', 'warning', or 'error',"
                + " and the optional <type> is either the simple or fully qualified Java type name"
                + " of a diagnostic. If <type> is unspecified, all diagnostics at <from-level> will"
                + " be mapped. Note that fatal compiler errors cannot be mapped.",
            (state, suffix, fromLevel, toLevel) -> {
              Consumer<Diagnostic> errorHandler1 = error -> errorHandler.accept(state, error);
              Origin origin = getOrigin.apply(state);
              String diagnosticsClassName = "";
              if (!suffix.isEmpty()) {
                if (suffix.length() == 1 || suffix.charAt(0) != ':') {
                  errorHandler1.accept(
                      new StringDiagnostic(
                          "Invalid diagnostics type specification --map-diagnostics" + suffix + ".",
                          origin));
                  return;
                }
                diagnosticsClassName = suffix.substring(1);
              }
              DiagnosticsLevel fromLevel1 = parseDiagnosticsLevel(fromLevel, errorHandler1, origin);
              DiagnosticsLevel toLevel1 = parseDiagnosticsLevel(toLevel, errorHandler1, origin);
              if (fromLevel1 != null && toLevel1 != null) {
                if (fromLevel1 == DiagnosticsLevel.NONE) {
                  errorHandler1.accept(
                      new StringDiagnostic("Cannot map from diagnostics level 'none'.", origin));
                  return;
                }
                action.accept(
                    state, new DiagnosticsMapping(diagnosticsClassName, fromLevel1, toLevel1));
              }
            });
  }

  public static <B> Consumer<CliParser<B>> addMapDiagnosticsOption(
      Function<B, Reporter> getReporter, Function<B, Origin> getOrigin) {
    return addMapDiagnosticsOption(
        (state, mapping) ->
            getReporter
                .apply(state)
                .addDiagnosticsLevelMapping(mapping.from, mapping.diagnosticType, mapping.to),
        (state, error) -> getReporter.apply(state).error(error),
        getOrigin);
  }

  public static <T, C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      Consumer<CliParser<T>> addForceAssertionOptions(
          Function<T, B> getBuilder, Function<T, Origin> getOrigin) {
    return parser ->
        parser
            .prefix0(
                "--force-enable-assertions",
                "[:[<class name>|<package name>...]]",
                "Forcefully enable javac generated assertion code.",
                (state, suffix) -> {
                  B builder = getBuilder.apply(state);
                  String scope = parseAssertionScope(builder, suffix, getOrigin.apply(state));
                  addAssertionTransformation(
                      builder, AssertionTransformationType.ENABLE, null, scope);
                },
                "--force-ea")
            .prefix0(
                "--force-disable-assertions",
                "[:[<class name>|<package name>...]]",
                "Forcefully disable javac generated assertion code. This is the default handling of"
                    + " javac assertion code when generating DEX file format.",
                (state, suffix) -> {
                  B builder = getBuilder.apply(state);
                  String scope = parseAssertionScope(builder, suffix, getOrigin.apply(state));
                  addAssertionTransformation(
                      builder, AssertionTransformationType.DISABLE, null, scope);
                },
                "--force-da")
            .prefix0(
                "--force-passthrough-assertions",
                "[:[<class name>|<package name>...]]",
                "Don't change javac generated assertion code. This is the default handling of"
                    + " javac assertion code when generating class file format.",
                (state, suffix) -> {
                  B builder = getBuilder.apply(state);
                  String scope = parseAssertionScope(builder, suffix, getOrigin.apply(state));
                  addAssertionTransformation(
                      builder, AssertionTransformationType.PASSTHROUGH, null, scope);
                },
                "--force-pa")
            .prefix0(
                "--force-assertions-handler",
                ":<handler method>[:[<class name>|<package name>...]]",
                "Change javac and kotlinc generated assertion code to invoke the method <handler"
                    + " method> with each assertion error instead of throwing it. The <handler"
                    + " method> is specified as a class name followed by a dot and the method name."
                    + " The handler method must take a single argument of type java.lang.Throwable"
                    + " and have return type void.",
                (state, suffix) -> {
                  B builder = getBuilder.apply(state);
                  Pair<MethodReference, String> handlerAndScope =
                      parseAssertionHandler(builder, suffix, getOrigin.apply(state));
                  addAssertionTransformation(
                      builder,
                      AssertionTransformationType.HANDLER,
                      handlerAndScope.getFirst(),
                      handlerAndScope.getSecond());
                },
                "--force-ah");
  }

  private enum AssertionTransformationType {
    ENABLE,
    DISABLE,
    PASSTHROUGH,
    HANDLER
  }

  private static AssertionsConfiguration.Builder prepareBuilderForScope(
      AssertionsConfiguration.Builder builder,
      AssertionTransformationType transformation,
      MethodReference assertionHandler) {
    switch (transformation) {
      case ENABLE:
        return builder.setCompileTimeEnable();
      case DISABLE:
        return builder.setCompileTimeDisable();
      case PASSTHROUGH:
        return builder.setPassthrough();
      case HANDLER:
        return builder.setAssertionHandler(assertionHandler);
      default:
        throw new Unreachable();
    }
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void addAssertionTransformation(
          B builder,
          AssertionTransformationType transformation,
          MethodReference assertionHandler,
          String scope) {
    if (scope == null) {
      builder.addAssertionsConfiguration(
          b -> prepareBuilderForScope(b, transformation, assertionHandler).setScopeAll().build());
    } else {
      assert !scope.isEmpty();
      String packageAssertionPostfix = "...";
      if (scope.endsWith(packageAssertionPostfix)) {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopePackage(
                        scope.substring(0, scope.length() - packageAssertionPostfix.length()))
                    .build());
      } else {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopeClass(scope)
                    .build());
      }
    }
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      String parseAssertionScope(B builder, String suffix, Origin origin) {
    if (suffix.isEmpty()) {
      return null;
    }
    if (suffix.equals(":")) {
      throw builder.fatalError(new StringDiagnostic("Missing optional argument", origin));
    }
    if (!suffix.startsWith(":")) {
      builder.error(new StringDiagnostic("Illegal assertion scope: " + suffix, origin));
      return null;
    }
    String classOrPackageScope = suffix.substring(1);
    if (classOrPackageScope.contains(";")
        || classOrPackageScope.contains("[")
        || classOrPackageScope.contains("/")) {
      builder.error(
          new StringDiagnostic("Illegal assertion scope: " + classOrPackageScope, origin));
    }
    return classOrPackageScope;
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      Pair<MethodReference, String> parseAssertionHandler(B builder, String suffix, Origin origin) {
    if (suffix.isEmpty() || suffix.equals(":")) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    if (!suffix.startsWith(":")) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    String remaining = suffix.substring(1);
    int index = remaining.indexOf(':');
    if (index == 0) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    String assertionsHandlerString = index > 0 ? remaining.substring(0, index) : remaining;
    int lastDotIndex = assertionsHandlerString.lastIndexOf('.');
    if (assertionsHandlerString.length() < 3
        || lastDotIndex <= 0
        || lastDotIndex == assertionsHandlerString.length() - 1
        || !DescriptorUtils.isValidJavaType(assertionsHandlerString.substring(0, lastDotIndex))) {
      throw builder.fatalError(
          new StringDiagnostic(
              "Invalid argument <handler method>: " + assertionsHandlerString, origin));
    }
    MethodReference assertionsHandler =
        Reference.methodFromDescriptor(
            DescriptorUtils.javaTypeToDescriptor(
                assertionsHandlerString.substring(0, lastDotIndex)),
            assertionsHandlerString.substring(lastDotIndex + 1),
            "(Ljava/lang/Throwable;)V");
    String scopeSuffix = remaining.substring(assertionsHandlerString.length());
    String scope = parseAssertionScope(builder, scopeSuffix, origin);
    return Pair.create(assertionsHandler, scope);
  }
}
