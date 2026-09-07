// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.ClassConflictResolver;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.StringConsumer.WriterConsumer;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.tracereferences.internal.TraceReferencesNativesPrinter;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

class TraceReferencesCommandParser {

  static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  private static class ParserState {
    final TraceReferencesCommand.Builder builder;
    final Origin origin;
    Command command = null;
    Path output = null;
    boolean allowObfuscation = false;

    ParserState(TraceReferencesCommand.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static void setCommand(ParserState state, Command command) {
    if (state.command != null) {
      state.builder.error(new StringDiagnostic("Multiple commands specified", state.origin));
    } else {
      state.command = command;
    }
  }

  private static CliParser<ParserState> createParser() {
    String usageHeader =
        StringUtils.joinLines(
            "Usage: tracereferences <command> [<options>] [@<argfile>]",
            " Where <command> is one of:");
    CliParser<ParserState> parser = new CliParser<>(usageHeader);
    return parser
        .option0(
            "--check",
            "Run emitting only diagnostics messages.",
            state -> setCommand(state, Command.CHECK))
        .option0(
            "--keep-rules",
            "Traced references will be output in the keep-rules format.",
            state -> setCommand(state, Command.KEEP_RULES))
        // TODO(b/481400921): Remove experimental.
        .option0("--natives", "EXPERIMENTAL.", state -> setCommand(state, Command.NATIVES))
        .addHelpText(
            StringUtils.joinLines(
                " and each <argfile> is a file containing additional options (one per line)",
                " and options are:"))
        .option1(
            "--lib",
            "<file|jdk-home>",
            "Add <file|jdk-home> runtime library.",
            (state, arg) -> addLibraryArgument(state.builder, state.origin, arg))
        .option1(
            "--source",
            "<file>",
            "Add <file> as a source for tracing references.",
            (state, arg) -> state.builder.addSourceFiles(Paths.get(arg)))
        .option1(
            "--target",
            "<file>",
            "Add <file> as a target for tracing references. When target is not specified all"
                + " references from source outside of library are treated as a missing references.",
            (state, arg) -> state.builder.addTargetFiles(Paths.get(arg)))
        .option1(
            "--output",
            "<file>",
            "Output result in <outfile>. If not passed the result will go to standard out.",
            (state, arg) -> {
              if (state.output != null) {
                state.builder.error(
                    new StringDiagnostic("Option '--output' passed multiple times.", state.origin));
              } else {
                state.output = Paths.get(arg);
              }
            })
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
        .option0(
            "--resolve-trivial-conflicts",
            "Resolve trivial duplicate class conflicts.",
            state -> state.builder.setClassConflictResolver(new TrivialClassConflictResolver()))
        .option0(
            "--version",
            "Print the version of tracereferences.",
            state -> state.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", state -> state.builder.setPrintHelp(true), "-h")
        .addHelpText(" and --keep-rules specific options are:")
        .option0(
            "--allowobfuscation",
            "Output keep rules with the allowobfuscation modifier (defaults to rules without the"
                + " modifier).",
            state -> state.allowObfuscation = true);
  }

  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(String[] args, Origin origin) {
    return parse(args, origin, TraceReferencesCommand.builder());
  }

  /**
   * Parse the tracereferences command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return tracereferences command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return parse(args, origin, TraceReferencesCommand.builder(handler));
  }

  private enum Command {
    CHECK,
    KEEP_RULES,
    NATIVES
  }

  @SuppressWarnings("DefaultCharset")
  private static TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, TraceReferencesCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    if (expandedArgs.length == 0) {
      builder.error(new StringDiagnostic("Missing command"));
      return builder;
    }
    ParserState state = new ParserState(builder, origin);
    createParser()
        .parse(expandedArgs, state, error -> builder.error(new StringDiagnostic(error, origin)));

    if (builder.isPrintHelp() || builder.isPrintVersion()) {
      return builder;
    }

    if (state.command == null) {
      builder.error(
          new StringDiagnostic(
              "Missing command, specify one of '--check' or '--keep-rules'", origin));
      return builder;
    }

    if (state.command == Command.CHECK && state.output != null) {
      builder.error(
          new StringDiagnostic("Using '--output' requires command '--keep-rules'", origin));
      return builder;
    }

    if (state.command != Command.KEEP_RULES && state.allowObfuscation) {
      builder.error(
          new StringDiagnostic(
              "Using '--allowobfuscation' requires command '--keep-rules'", origin));
      return builder;
    }

    switch (state.command) {
      case CHECK:
        builder.setConsumer(
            new TraceReferencesCheckConsumer(TraceReferencesConsumer.emptyConsumer()));
        break;
      case KEEP_RULES:
        builder.setConsumer(
            new TraceReferencesCheckConsumer(
                TraceReferencesKeepRules.builder()
                    .setAllowObfuscation(state.allowObfuscation)
                    .setOutputConsumer(
                        state.output != null
                            ? new FileConsumer(state.output)
                            : new WriterConsumer(null, new PrintWriter(System.out)))
                    .build()));
        break;
      case NATIVES:
        // TODO(b/481400921): Remove experimental.
        System.out.println("Command --natives is still EXPERIMENTAL!!!");
        builder
            .setConsumer(TraceReferencesConsumer.emptyConsumer())
            .setNativeReferencesConsumer(
                TraceReferencesNativesPrinter.builder()
                    .setOutputConsumer((string, handler) -> System.out.println(string))
                    .build());
        break;
      default:
        throw new Unreachable();
    }
    return builder;
  }

  /**
   * This method must match the lookup in {@link
   * com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    return false;
  }

  static void addLibraryArgument(
      TraceReferencesCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }

  private static class TrivialClassConflictResolver implements ClassConflictResolver {

    @Override
    public Origin resolveDuplicateClass(
        ClassReference reference, Collection<Origin> origins, DiagnosticsHandler handler) {
      byte[] previousClassBytes = null;
      for (Origin origin : origins) {
        byte[] classBytes = readClassBytes(origin);
        if (previousClassBytes == null) {
          previousClassBytes = classBytes;
        } else if (!Arrays.equals(previousClassBytes, classBytes)) {
          // Not a trivial conflict, do not resolve.
          return null;
        }
      }
      // All duplicate classes are identical.
      return origins.iterator().next();
    }

    private static byte[] readClassBytes(Origin origin) {
      if (origin instanceof ArchiveEntryOrigin) {
        ArchiveEntryOrigin archiveEntryOrigin = (ArchiveEntryOrigin) origin;
        Origin archiveOrigin = archiveEntryOrigin.parent();
        if (archiveOrigin instanceof PathOrigin) {
          PathOrigin archivePathOrigin = (PathOrigin) archiveOrigin;
          Path archivePath = archivePathOrigin.getPath();
          try {
            return ZipUtils.readSingleEntry(archivePath, archiveEntryOrigin.getEntryName());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
      throw new RuntimeException("Unhandled origin: " + origin);
    }
  }
}
