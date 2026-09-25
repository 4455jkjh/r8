// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;

public class L8CommandParser {

  static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static L8Command.Builder parse(String[] args, Origin origin) {
    return parse(args, origin, L8Command.builder());
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static L8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return parse(args, origin, L8Command.builder(handler));
  }

  private static class ParserState {
    CompilationMode compilationMode = null;
    Path outputPath = null;
    OutputMode outputMode = OutputMode.DexIndexed;
    boolean hasDefinedApiLevel = false;
    final L8Command.Builder builder;
    final Origin origin;

    public ParserState(L8Command.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static CliParser<ParserState> createParser() {
    String toolName = "l8";
    String header =
        StringUtils.joinLines(
            "Usage: " + toolName + " [options] <input-files>",
            " where <input-files> are any combination class, zip, or jar files",
            " where <input-files> are any combination of dex, class, zip, jar, or apk files",
            " and options are:");
    return new CliParser<ParserState>(header)
        .option0(
            "--debug",
            "Compile with debugging information (default).",
            state -> {
              if (state.compilationMode == CompilationMode.RELEASE) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot compile in both --debug and --release mode.", state.origin);
                state.builder.error(diagnostic);
              } else {
                state.compilationMode = CompilationMode.DEBUG;
              }
            })
        .option0(
            "--release",
            "Compile without debugging information.",
            state -> {
              if (state.compilationMode == CompilationMode.DEBUG) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot compile in both --debug and --release mode.", state.origin));
              } else {
                state.compilationMode = CompilationMode.RELEASE;
              }
            })
        .option1(
            "--output",
            "<file>",
            "Output result in <file>. <file> must be an existing directory or a zip file.",
            (state, arg) -> {
              if (state.outputPath != null) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot output both to '" + state.outputPath + "' and '" + arg + "'",
                        state.origin);
                state.builder.error(diagnostic);
              } else {
                state.outputPath = Paths.get(arg);
              }
            })
        .option1(
            "--lib",
            "<file|jdk-home>",
            "Add <file|jdk-home> as a library resource.",
            (state, arg) ->
                CompilerCommandParserUtils.addLibraryArgument(
                    state.builder.getAppBuilder(), arg, state.origin, state.builder.getReporter()))
        .apply(
            CliParserUtils.addMinApiOption(
                state -> state.hasDefinedApiLevel,
                (state, apiLevel) -> {
                  state.builder.setMinApiLevel(apiLevel);
                  state.hasDefinedApiLevel = true;
                },
                (state, error) -> state.builder.error(new StringDiagnostic(error, state.origin))))
        .option1(
            "--pg-conf",
            "<file>",
            "Proguard configuration <file>.",
            (state, arg) -> state.builder.addProguardConfigurationFiles(Paths.get(arg)))
        .option1(
            "--pg-map-output",
            "<file>",
            "Output the resulting name and line mapping to <file>.",
            (state, arg) -> state.builder.setProguardMapOutputPath(Paths.get(arg)))
        .option1(
            "--partition-map-output",
            "<file>",
            "Output the resulting mapping to <file>.",
            (state, arg) -> state.builder.setPartitionMapOutputPath(Paths.get(arg)))
        .option1(
            "--desugared-lib",
            "<file>",
            "Specify desugared library configuration. <file> is a desugared library configuration"
                + " (json).",
            (state, arg) ->
                state.builder.addDesugaredLibraryConfiguration(
                    StringResource.fromFile(Paths.get(arg))))
        .apply(
            CliParserUtils.addForceAssertionOptions(state -> state.builder, state -> state.origin))
        .apply(
            CliParserUtils.addThreadCountOption(
                (state, threadCount) -> state.builder.setThreadCount(threadCount),
                state -> state.builder.getReporter(),
                state -> state.origin))
        .apply(
            CliParserUtils.addMapDiagnosticsOption(
                state -> state.builder.getReporter(), state -> state.origin))
        .option2(
            "--art-profile",
            "<input>",
            "<output>",
            "Rewrite human readable ART profile read from <input> and write to <output>.",
            (state, arg1, arg2) -> {
              Path artProfilePath = Paths.get(arg1);
              Path rewrittenArtProfilePath = Paths.get(arg2);
              state.builder.addArtProfileForRewriting(
                  ArtProfileProviderUtils.createFromHumanReadableArtProfile(artProfilePath),
                  ArtProfileConsumerUtils.create(rewrittenArtProfilePath));
            })
        .option0(
            "--classfile",
            "Compile program to Java classfile format.",
            state -> state.outputMode = OutputMode.ClassFile)
        .option1(
            "--dumpinputtofile",
            "<file>",
            "Dump the all compiler input to <file> for easy reproduction.",
            (state, arg) -> state.builder.dumpInputToFile(Paths.get(arg)))
        .option1(
            "--dumpinputtodirectory",
            "<dir>",
            "Dump the all compiler input to <dir> for easy reproduction.",
            (state, arg) -> state.builder.dumpInputToDirectory(Paths.get(arg)))
        .apply(CliParserUtils.addVersionOption(state -> state.builder.setPrintVersion(true)))
        .apply(CliParserUtils.addHelpOption(state -> state.builder.setPrintHelp(true)))
        .positional((state, arg) -> state.builder.addProgramFiles(Paths.get(arg)));
  }

  private static L8Command.Builder parse(String[] args, Origin origin, L8Command.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    var state = new ParserState(builder, origin);
    createParser().parse(expandedArgs, state, error -> state.builder.getReporter().error(error));
    if (state.compilationMode != null) {
      builder.setMode(state.compilationMode);
    }
    if (state.outputPath == null) {
      state.outputPath = Paths.get(".");
    }
    return builder.setOutput(state.outputPath, state.outputMode);
  }
}
