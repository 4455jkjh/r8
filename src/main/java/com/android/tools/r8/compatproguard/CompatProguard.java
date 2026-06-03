// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.MapIdTemplateProvider;
import com.android.tools.r8.utils.SourceFileTemplateProvider;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Proguard + dx compatibility interface for r8.
 *
 * This should become a mostly drop-in replacement for uses of Proguard followed by dx for
 * use with the Android Platform build.
 *
 * It accepts all Proguard flags supported by r8, except -outjars.
 *
 * It accepts a few dx flags which are known to be used in the Android Platform build.
 *
 * The flag -outjars does not make sense as r8 (like Proguard + dx) produces Dex output.
 * For output use --output as for R8 proper.
 */
public class CompatProguard {
  public static class CompatProguardOptions {
    public final String output;
    CompilationMode mode;
    public final int minApi;
    public final boolean forceProguardCompatibility;
    public final boolean includeDataResources;
    public final boolean multiDex;
    public final String mainDexList;
    public final MapIdProvider mapIdProvider;
    public final SourceFileProvider sourceFileProvider;
    public final String depsFileOutput;
    public final List<String> proguardConfig;
    public boolean printHelpAndExit;

    CompatProguardOptions(
        List<String> proguardConfig,
        String output,
        CompilationMode mode,
        int minApi,
        boolean multiDex,
        boolean forceProguardCompatibility,
        boolean includeDataResources,
        String mainDexList,
        MapIdProvider mapIdProvider,
        SourceFileProvider sourceFileProvider,
        String depsFileOutput,
        boolean printHelpAndExit) {
      this.output = output;
      this.mode = mode;
      this.minApi = minApi;
      this.forceProguardCompatibility = forceProguardCompatibility;
      this.includeDataResources = includeDataResources;
      this.multiDex = multiDex;
      this.mainDexList = mainDexList;
      this.proguardConfig = proguardConfig;
      this.mapIdProvider = mapIdProvider;
      this.sourceFileProvider = sourceFileProvider;
      this.depsFileOutput = depsFileOutput;
      this.printHelpAndExit = printHelpAndExit;
    }

    private static class ParserState {
      DiagnosticsHandler handler;
      String output = null;
      CompilationMode mode = null;
      int minApi = 1;
      boolean forceProguardCompatibility = false;
      boolean includeDataResources = true;
      boolean multiDex = false;
      String mainDexList = null;
      boolean printHelpAndExit = false;
      MapIdProvider mapIdProvider = null;
      SourceFileProvider sourceFileProvider = null;
      String depsFileOutput = null;
      // Inputs like '-keep a --output tmp/ b' is interpreted as a proguard inputs, "-keep", "a",
      // and "b" and an output flag "--output tmp/".
      List<String> proguardInputs = new ArrayList<>();

      public ParserState(DiagnosticsHandler handler) {
        this.handler = handler;
      }
    }

    private static CliParser<ParserState> createParser() {
      var header =
          StringUtils.joinLines(
              "Usage: compatproguard [options] <proguard-config>*", "", "Where options are:");
      CliParser<ParserState> parser = new CliParser<>(header, true);
      return parser
          .option0("--help", "Print this message.", b -> b.printHelpAndExit = true, "-h")
          .option0(
              "--release",
              "Compile without debugging information (default).",
              b -> {
                if (b.mode == CompilationMode.DEBUG) {
                  throw new CompilationError("Cannot compile in both --debug and --release mode.");
                }
                b.mode = CompilationMode.RELEASE;
              })
          .option0(
              "--debug",
              "Compile with debugging information.",
              b -> {
                if (b.mode == CompilationMode.RELEASE) {
                  throw new CompilationError("Cannot compile in both --debug and --release mode.");
                }
                b.mode = CompilationMode.DEBUG;
              })
          .option1("--output", "<dir>", "Output result in <dir>.", (b, arg) -> b.output = arg)
          .option1(
              "--min-api",
              "<n>",
              "Specify the targeted min android api level.",
              (b, arg) -> {
                CliParserUtils.parsePositiveInt(
                    arg,
                    api -> b.minApi = api,
                    err -> {
                      throw new CompilationError("Cannot read --min-api: " + err);
                    });
              })
          .option1(
              "--main-dex-list",
              "<list>",
              "Specify main dex list for multi-dexing.",
              (b, arg) -> b.mainDexList = arg)
          .option1(
              "--map-id-template",
              "<template>",
              "Set the map-id to <template>.",
              (b, arg) -> b.mapIdProvider = MapIdTemplateProvider.create(arg, b.handler))
          .option1(
              "--source-file-template",
              "<template>",
              "Set all source-file attributes to <template>.",
              (b, arg) -> b.sourceFileProvider = SourceFileTemplateProvider.create(arg, b.handler))
          .option1(
              "--deps-file", "<file>", "Set deps file output.", (b, arg) -> b.depsFileOutput = arg)
          .option0("--minimal-main-dex", "Ignored (provided for compatibility).", b -> {})
          .option0("--multi-dex", "Ignored (provided for compatibility).", b -> b.multiDex = true)
          .option0("--no-locals", "Ignored (provided for compatibility).", b -> {})
          .option0("--core-library", "Ignored (provided for compatibility).", b -> {})
          .option0(
              "--force-proguard-compatibility",
              "Proguard compatibility mode.",
              b -> b.forceProguardCompatibility = true)
          .option0(
              "--no-data-resources",
              "Ignore all data resources.",
              b -> b.includeDataResources = false)
          .positional(
              (b, arg) -> {
                if (arg.equals("-outjars")) {
                  throw new CompilationError(
                      "Proguard argument -outjar is not supported. Use R8 compatible --output"
                          + " flag");
                } else {
                  b.proguardInputs.add(arg);
                }
              });
    }

    public static CompatProguardOptions parse(String[] args) {
      ParserState state = new ParserState(new DiagnosticsHandler() {});
      createParser()
          .parse(
              args,
              state,
              err -> {
                throw new CompilationError(err);
              });
      return new CompatProguardOptions(
          groupProguardArgs(state.proguardInputs),
          state.output,
          state.mode,
          state.minApi,
          state.multiDex,
          state.forceProguardCompatibility,
          state.includeDataResources,
          state.mainDexList,
          state.mapIdProvider,
          state.sourceFileProvider,
          state.depsFileOutput,
          state.printHelpAndExit);
    }

    /**
     * Takes a list like "-keep", "a", "b", "-fun", "c" and returns a list like "-keep a b","-fun
     * c".
     */
    private static ImmutableList<String> groupProguardArgs(List<String> inputs) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      StringBuilder currentGroup = new StringBuilder();
      for (String arg : inputs) {
        if (arg.startsWith("-") && currentGroup.length() != 0) {
          builder.add(currentGroup.toString());
          currentGroup.setLength(0);
        }
        if (currentGroup.length() != 0) {
          currentGroup.append(' ');
        }
        currentGroup.append(arg);
      }
      if (currentGroup.length() != 0) {
        builder.add(currentGroup.toString());
      }
      return builder.build();
    }

    public static String usageMessage() {
      return CliParserUtils.getUsageMessage(createParser());
    }
  }

  private static void run(String[] args) throws CompilationFailedException {
    // Run R8 passing all the options from the command line as a Proguard configuration.
    CompatProguardOptions options = CompatProguardOptions.parse(args);
    if (options.printHelpAndExit || options.output == null) {
      System.out.println();
      System.out.println(CompatProguardOptions.usageMessage());
      return;
    }
    CompatProguardCommandBuilder builder =
        new CompatProguardCommandBuilder(options.forceProguardCompatibility);
    builder
        .setOutput(Paths.get(options.output), OutputMode.DexIndexed, options.includeDataResources)
        .addProguardConfiguration(options.proguardConfig, CommandLineOrigin.INSTANCE)
        .setMinApiLevel(options.minApi)
        .setMapIdProvider(options.mapIdProvider)
        .setSourceFileProvider(options.sourceFileProvider);
    if (options.mode != null) {
      builder.setMode(options.mode);
    }
    if (options.mainDexList != null) {
      builder.addMainDexListFiles(Paths.get(options.mainDexList));
    }
    if (options.depsFileOutput != null) {
      Path target = Paths.get(options.output);
      if (!FileUtils.isArchive(target)) {
        target = target.resolve("classes.dex");
      }
      builder.setInputDependencyGraphConsumer(new DepsFileWriter(target, options.depsFileOutput));
    }
    R8.run(builder.build());
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
