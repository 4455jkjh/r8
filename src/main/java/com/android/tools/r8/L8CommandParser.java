// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;

public class L8CommandParser extends BaseCompilerCommandParser {

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
            (state, arg) -> addLibraryArgument(state.builder, arg, state.origin))
        .option1(
            "--min-api",
            "<number>",
            "Minimum Android API level compatibility (default: "
                + AndroidApiLevel.getDefault().getLevel()
                + ").",
            (state, arg) -> {
              if (state.hasDefinedApiLevel) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot set multiple " + MIN_API_FLAG + " options", state.origin);
                state.builder.error(diagnostic);
              } else {
                parsePositiveIntArgument(
                    state.builder::error,
                    MIN_API_FLAG,
                    arg,
                    state.origin,
                    state.builder::setMinApiLevel);
                state.hasDefinedApiLevel = true;
              }
            })
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
        .prefix0(
            "--force-enable-assertions",
            "[:[<class name>|<package name>...]]",
            "Forcefully enable javac generated assertion code.",
            (state, suffix) -> parseForceEnableAssertions(state.builder, suffix, state.origin),
            "--force-ea")
        .prefix0(
            "--force-disable-assertions",
            "[:[<class name>|<package name>...]]",
            "Forcefully disable javac generated assertion code. This is the default handling of"
                + " javac assertion code when generating DEX file format.",
            (state, suffix) -> parseForceDisableAssertions(state.builder, suffix, state.origin),
            "--force-da")
        .prefix0(
            "--force-passthrough-assertions",
            "[:[<class name>|<package name>...]]",
            "Don't change javac generated assertion code. This is the default handling of"
                + " javac assertion code when generating class file format.",
            (state, suffix) -> parseForcePassthroughAssertions(state.builder, suffix, state.origin),
            "--force-pa")
        .prefix0(
            "--force-assertions-handler",
            ":<handler method>[:[<class name>|<package name>...]]",
            "Change javac and kotlinc generated assertion code to invoke the method <handler"
                + " method> with each assertion error instead of throwing it. The <handler"
                + " method> is specified as a class name followed by a dot and the method name."
                + " The handler method must take a single argument of type java.lang.Throwable"
                + " and have return type void.",
            (state, suffix) -> parseForceAssertionsHandler(state.builder, suffix, state.origin),
            "--force-ah")
        .option1(
            "--thread-count",
            "<number>",
            "Use <number> of threads for compilation. If not specified the number will be based on"
                + " heuristics taking the number of cores into account.",
            (state, arg) ->
                parsePositiveIntArgument(
                    state.builder::error,
                    THREAD_COUNT_FLAG,
                    arg,
                    state.origin,
                    state.builder::setThreadCount))
        .prefix2(
            "--map-diagnostics",
            "[:<type>]",
            "<from-level>",
            "<to-level>",
            "Map diagnostics of <type> (default any) reported as <from-level> to <to-level> where"
                + " <from-level> and <to-level> are one of 'info', 'warning', or 'error' and the"
                + " optional <type> is either the simple or fully qualified Java type name of a"
                + " diagnostic. If <type> is unspecified, all diagnostics at <from-level> will be"
                + " mapped. Note that fatal compiler errors cannot be mapped.",
            (state, suffix, fromLevel, toLevel) ->
                CliParserUtils.parseDiagnosticsMapping(
                    suffix,
                    fromLevel,
                    toLevel,
                    m ->
                        state
                            .builder
                            .getReporter()
                            .addDiagnosticsLevelMapping(m.from, m.diagnosticType, m.to),
                    state.builder::error,
                    state.origin))
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
            "--version",
            "Print the version of " + toolName + ".",
            state -> state.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", state -> state.builder.setPrintHelp(true), "-h")
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
