// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.BaseCompilerCommandUtils.createProgramOutputConsumer;

import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.profile.startup.StartupProfileProviderUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.MapIdTemplateProvider;
import com.android.tools.r8.utils.SourceFileTemplateProvider;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class R8CommandParser extends BaseCompilerCommandParser {

  // Due to the family of flags (for assertions and diagnostics) we can't base the one/two args
  // on this setup of flags. Thus, the flag collection just encodes the descriptive content.
  static List<ParseFlagInfo> getFlags() {
    return CliParserUtils.getFlagInfos(createParser());
  }

  private static CliParser<ParserState> createParser() {
    var header =
        StringUtils.joinLines(
            "Usage: r8 [options] [@<argfile>] <input-files>",
            " where <input-files> are any combination class, zip, or jar files",
            " and each <argfile> is a file containing additional arguments (one per line)",
            " and options are:");
    return new CliParser<ParserState>(header)
        .option0(
            "--release",
            "Compile without debugging information (default).",
            state -> {
              if (state.mode == CompilationMode.DEBUG) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot compile in both --debug and --release mode.", state.origin);
                state.builder.error(diagnostic);
              }
              state.mode = CompilationMode.RELEASE;
            })
        .option0(
            "--debug",
            "Compile with debugging information.",
            state -> {
              if (state.mode == CompilationMode.RELEASE) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot compile in both --debug and --release mode.", state.origin);
                state.builder.error(diagnostic);
              }
              state.mode = CompilationMode.DEBUG;
            })
        .option0(
            "--dex",
            "Compile program to DEX file format (default).",
            state -> {
              if (state.outputMode == OutputMode.ClassFile) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot compile in both --dex and --classfile output mode.", state.origin);
                state.builder.error(diagnostic);
              }
              state.outputMode = OutputMode.DexIndexed;
            })
        .option0(
            "--classfile",
            "Compile program to Java classfile format.",
            state -> {
              if (state.outputMode == OutputMode.DexIndexed) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot compile in both --dex and --classfile output mode.", state.origin));
              }
              state.outputMode = OutputMode.ClassFile;
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
              }
              state.outputPath = Paths.get(arg);
            })
        .option1(
            "--lib",
            "<file|jdk-home>",
            "Add <file|jdk-home> as a library resource.",
            (state, arg) ->
                CompilerCommandParserUtils.addLibraryArgument(
                    state.builder.getAppBuilder(), arg, state.origin, state.builder.getReporter()))
        .option1(
            "--classpath",
            "<file>",
            "Add <file> as a classpath resource.",
            (state, arg) -> state.builder.addClasspathFiles(Paths.get(arg)))
        .option1(
            "--min-api",
            "<number>",
            "Minimum Android API level compatibility (default: "
                + AndroidApiLevel.getDefault().getMajor()
                + ").",
            (state, arg) -> {
              if (state.hasDefinedApiLevel) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic("Cannot set multiple --min-api options", state.origin);
                state.builder.error(diagnostic);
              } else {
                CliParserUtils.parsePositiveInt(
                    arg,
                    state.builder::setMinApiLevel,
                    error ->
                        state.builder.error(
                            new StringDiagnostic(
                                "Invalid argument to --min-api: " + error, state.origin)));
                state.hasDefinedApiLevel = true;
              }
            })
        .option1(
            "--api-database",
            "<file>",
            "Use <file> as the Android API database for API modeling, overriding the default"
                + " database. <file> must be a .ser file generated by ApiDatabaseGenerator.",
            (state, arg) -> {
              if (state.builder.getApiDatabasePath() != null) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot set multiple --api-database options", state.origin);
                state.builder.error(diagnostic);
              } else {
                state.builder.setApiDatabasePath(Paths.get(arg));
              }
            })
        .option0(
            "--pg-compat",
            "Compile with R8 in Proguard compatibility mode.",
            state -> state.builder.setProguardCompatibility(true))
        .option1(
            "--pg-conf",
            "<file>",
            "Proguard configuration <file>.",
            (state, arg) -> state.builder.addProguardConfigurationFiles(Paths.get(arg)))
        .option1(
            "--pg-conf-output",
            "<file>",
            "Output the collective configuration to <file>.",
            (state, arg) ->
                state.builder.setProguardConfigurationConsumer(new FileConsumer(Paths.get(arg))))
        .option1(
            "--pg-map",
            "<file>",
            "Use <file> as a mapping file for distribution and composition with output mapping"
                + " file.",
            (state, arg) -> state.builder.setProguardMapInputFile(Paths.get(arg)))
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
        .option0(
            "--no-tree-shaking",
            "Force disable tree shaking of unreachable classes.",
            state -> state.builder.setDisableTreeShaking(true))
        .option0(
            "--no-minification",
            "Force disable minification of names.",
            state -> state.builder.setDisableMinification(true))
        .option0(
            "--no-data-resources",
            "Ignore all data resources.",
            state -> state.includeDataResources = false)
        .option0(
            "--no-desugaring",
            "Force disable desugaring.",
            state -> state.builder.setDisableDesugaring(true))
        .option1(
            "--main-dex-rules",
            "<file>",
            "Proguard keep rules for classes to place in the primary dex file.",
            (state, arg) -> state.builder.addMainDexRulesFiles(Paths.get(arg)))
        .option1(
            "--main-dex-list",
            "<file>",
            "List of classes to place in the primary dex file.",
            (state, arg) -> state.builder.addMainDexListFiles(Paths.get(arg)))
        .option2(
            "--android-resources",
            "<input>",
            "<output>",
            "Add android resource input and output to be used in resource shrinking. Both input and"
                + " output must be specified.",
            (state, arg1, arg2) -> {
              Path inputPath = Paths.get(arg1);
              Path outputPath = Paths.get(arg2);
              state.builder.setAndroidResourceProvider(
                  new ArchiveProtoAndroidResourceProvider(inputPath));
              state.builder.setAndroidResourceConsumer(
                  new ArchiveProtoAndroidResourceConsumer(outputPath, inputPath));
              state.hasAndroidResources = true;
            })
        .option1(
            "--android-resources-usage-log",
            "<file>",
            "Write the resource shrinking usage log to <file>.",
            (state, arg) -> state.androidResourcesUsageLog = Paths.get(arg))
        .option2(
            "--feature",
            "<input>[:|;<res-input>]",
            "<output>[:|;<res-output>]",
            "Add feature <input> file to <output> file. Several occurrences can map to the same"
                + " output. If <res-input> and <res-output> are specified use these as resource"
                + " shrinker input and output. Separator is : on linux/mac, ; on windows. It is"
                + " possible to supply resource only features by using an empty string for <input>"
                + " and <output>, e.g. '--feature :in.ap_ :out.ap_'.",
            (state, arg1, arg2) -> state.featureSplitConfigCollector.addInputOutput(arg1, arg2))
        .option0(
            "--isolated-splits",
            "Specifies that the application is using isolated splits, i.e., if split APKs installed"
                + " for this application are loaded into their own Context objects.",
            (state) -> state.builder.setEnableIsolatedSplits(true))
        .option1(
            "--main-dex-list-output",
            "<file>",
            "Output the full main-dex list in <file>.",
            (state, arg) -> state.builder.setMainDexListOutputPath(Paths.get(arg)))
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
                CliParserUtils.parsePositiveInt(
                    arg,
                    state.builder::setThreadCount,
                    error ->
                        state.builder.error(
                            new StringDiagnostic(
                                "Invalid argument to --thread-count: " + error, state.origin))))
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
        .option1(
            "--map-id-template",
            "<template>",
            "Set the map-id to <template>. The <template> can reference the variables: %MAP_HASH,"
                + " compiler generated mapping hash.",
            (state, arg) ->
                state.builder.setMapIdProvider(
                    MapIdTemplateProvider.create(arg, state.builder.getReporter())))
        .option1(
            "--source-file-template",
            "<template>",
            "Set all source-file attributes to <template>. The <template> can reference the"
                + " variables: %MAP_ID, map id (e.g., value of --map-id-template). %MAP_HASH,"
                + " compiler generated mapping hash.",
            (state, arg) ->
                state.builder.setSourceFileProvider(
                    SourceFileTemplateProvider.create(arg, state.builder.getReporter())))
        .option0(
            "--android-platform-build",
            "Compile as a platform build where the runtime/bootclasspath is assumed to be the"
                + " version specified by --min-api.",
            state -> state.builder.setAndroidPlatformBuild(true))
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
        .option1(
            "--startup-profile",
            "<file>",
            "Startup profile <file> to use for dex layout.",
            (state, arg) -> {
              Path startupProfilePath = Paths.get(arg);
              state.builder.addStartupProfileProviders(
                  StartupProfileProviderUtils.createFromHumanReadableArtProfile(
                      startupProfilePath));
            })
        .option1(
            "--build-metadata-output",
            "<file>",
            "Output build metadata in <file>.",
            (state, arg) -> {
              if (state.buildMetadataOutputPath != null) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot output build metadata to both '"
                            + state.buildMetadataOutputPath
                            + "' and '"
                            + arg
                            + "'",
                        state.origin));
              } else {
                state.buildMetadataOutputPath = Paths.get(arg);
              }
            })
        .option1(
            "--configuration-analysis-data-output",
            "<file>",
            "Output configuration analysis data in <file>.",
            (state, arg) -> {
              if (state.configurationAnalysisDataOutputPath != null) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot output configuration analysis data to both '"
                            + state.configurationAnalysisDataOutputPath
                            + "' and '"
                            + arg
                            + "'",
                        state.origin));
              } else {
                state.configurationAnalysisDataOutputPath = Paths.get(arg);
                state.hasKeepRadiusOutput = true;
              }
            })
        .option0(
            "--optimize-multidex-for-linearalloc",
            "Optimize class distribution across DEX files for legacy multidex.",
            state -> state.builder.setOptimizeMultidexForLinearAlloc(true))
        .option1(
            "--dumpinputtofile",
            "<file>",
            "Dump all compiler input to <file> for easy reproduction.",
            (state, arg) -> state.builder.dumpInputToFile(Paths.get(arg)))
        .option1(
            "--dumpinputtodirectory",
            "<dir>",
            "Dump all compiler input to <dir> for easy reproduction.",
            (state, arg) -> state.builder.dumpInputToDirectory(Paths.get(arg)))
        .option0(
            "--version", "Print the version of r8.", state -> state.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", state -> state.builder.setPrintHelp(true))
        .positional(
            (state, arg) -> {
              if (arg.startsWith("@")) {
                state.builder.error(
                    new StringDiagnostic(
                        "Recursive @argfiles are not supported: " + arg, state.origin));
              } else {
                state.builder.addProgramFiles(Paths.get(arg));
              }
            });
  }

  static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  private static class ParserState {
    private CompilationMode mode = null;
    private OutputMode outputMode = null;
    private Path outputPath = null;
    private boolean hasKeepRadiusOutput = false;
    private boolean hasDefinedApiLevel = false;
    private boolean includeDataResources = true;
    private boolean hasAndroidResources = false;
    private Path androidResourcesUsageLog = null;
    private Path configurationAnalysisDataOutputPath = null;
    private Path buildMetadataOutputPath = null;
    private final FeatureSplitConfigCollector featureSplitConfigCollector =
        new FeatureSplitConfigCollector();

    private final R8Command.Builder builder;
    private final Origin origin;

    private ParserState(R8Command.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  /**
   * Parse the R8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static R8Command.Builder parse(String[] args, Origin origin) {
    return new R8CommandParser().parse(args, origin, R8Command.builder());
  }

  /**
   * Parse the R8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return R8 command builder with state set up according to parsed command line.
   */
  public static R8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return new R8CommandParser().parse(args, origin, R8Command.builder(handler));
  }

  private R8Command.Builder parse(String[] args, Origin origin, R8Command.Builder builder) {
    ParserState state = new ParserState(builder, origin);
    parse(args, origin, builder, state);
    if (state.mode != null) {
      builder.setMode(state.mode);
    }
    OutputMode outputMode = state.outputMode != null ? state.outputMode : OutputMode.DexIndexed;
    if (state.hasKeepRadiusOutput
        && state.outputPath == null
        && outputMode == OutputMode.DexIndexed) {
      builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    } else {
      Path outputPath = state.outputPath != null ? state.outputPath : Paths.get(".");
      builder.setOutput(outputPath, outputMode, state.includeDataResources);
    }
    builder.setEnableExperimentalMissingLibraryApiModeling(true);
    return builder;
  }

  private void parse(
      String[] args, Origin argsOrigin, R8Command.Builder builder, ParserState state) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    createParser()
        .parse(
            expandedArgs,
            state,
            error -> state.builder.error(new StringDiagnostic(error, state.origin)));
    addFeatureSplitConfigs(
        builder, state.featureSplitConfigCollector.getConfigs(), state.includeDataResources);
    if (state.configurationAnalysisDataOutputPath != null) {
      builder.setConfigurationAnalysisDataConsumer(
          new ByteArrayConsumer.FileConsumer(state.configurationAnalysisDataOutputPath));
    }
    if (state.buildMetadataOutputPath != null) {
      final Path finalBuildMetadataOutputPath = state.buildMetadataOutputPath;
      builder.setBuildMetadataConsumer(
          buildMetadata -> {
            try {
              FileUtils.writeTextFile(finalBuildMetadataOutputPath, buildMetadata.toJson());
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    }
    if (state.hasAndroidResources) {
      builder.setResourceShrinkerConfiguration(
          b -> {
            b.enableOptimizedShrinkingWithR8();
            if (state.androidResourcesUsageLog != null) {
              b.setDebugConsumer(new FileConsumer(state.androidResourcesUsageLog));
            }
            return b.build();
          });
    } else if (state.androidResourcesUsageLog != null) {
      builder.error(
          new StringDiagnostic(
              "--android-resources-usage-log requires --android-resources to be set.", argsOrigin));
    }
  }

  private void addFeatureSplitConfigs(
      R8Command.Builder builder,
      Collection<FeatureSplitConfig> featureSplitConfigs,
      boolean includeDataResources) {
    for (FeatureSplitConfig featureSplitConfig : featureSplitConfigs) {
      builder.addFeatureSplit(
          featureSplitGenerator -> {
            featureSplitGenerator.setProgramConsumer(
                featureSplitConfig.outputJar != null
                    ? createProgramOutputConsumer(
                        featureSplitConfig.outputJar, OutputMode.DexIndexed, includeDataResources)
                    : DexIndexedConsumer.emptyConsumer());
            for (Path inputPath : featureSplitConfig.inputJars) {
              featureSplitGenerator.addProgramResourceProvider(
                  ArchiveResourceProvider.fromArchive(inputPath, false));
            }
            if (featureSplitConfig.inputResources != null) {
              featureSplitGenerator.setAndroidResourceProvider(
                  new ArchiveProtoAndroidResourceProvider(
                      featureSplitConfig.inputResources,
                      new PathOrigin(featureSplitConfig.inputResources)));
            }
            if (featureSplitConfig.outputResources != null) {
              featureSplitGenerator.setAndroidResourceConsumer(
                  new ArchiveProtoAndroidResourceConsumer(
                      featureSplitConfig.outputResources, featureSplitConfig.inputResources));
            }
            return featureSplitGenerator.build();
          });
    }
  }

  // Represents a set of paths parsed from a string that may contain a ":" (";" on windows).
  // Supported examples are:
  //   pathA -> first = pathA, second = null
  //   pathA:pathB -> first = pathA, second = pathB
  //   :pathB -> first = null, second = pathB
  //   pathA: -> first = pathA, second = null
  private static class PossibleDoublePath {

    public final Path first;
    public final Path second;

    private PossibleDoublePath(Path first, Path second) {
      this.first = first;
      this.second = second;
    }

    public static PossibleDoublePath parse(String input) {
      Path first = null, second = null;
      List<String> inputSplit = StringUtils.split(input, File.pathSeparatorChar);
      if (inputSplit.isEmpty() || inputSplit.size() > 2) {
        throw new IllegalArgumentException("Feature input/output takes one or two paths.");
      }
      String firstString = inputSplit.get(0);
      if (!firstString.isEmpty()) {
        first = Paths.get(firstString);
      }
      if (inputSplit.size() == 2) {
        // "a:".split() gives just ["a"], so we should never get here if we don't have
        // a second string. ":b".split gives ["", "b"] which is handled for first above.
        assert !inputSplit.get(1).isEmpty();
        second = Paths.get(inputSplit.get(1));
      }
      return new PossibleDoublePath(first, second);
    }
  }

  private static class FeatureSplitConfig {
    private final List<Path> inputJars = new ArrayList<>();
    private Path inputResources;
    private Path outputResources;
    private Path outputJar;
  }

  private static class FeatureSplitConfigCollector {

    private final List<FeatureSplitConfig> resourceOnlySplits = new ArrayList<>();
    private final Map<Path, FeatureSplitConfig> withCodeSplits = new HashMap<>();

    public void addInputOutput(String input, String output) {
      PossibleDoublePath inputPaths = PossibleDoublePath.parse(input);
      PossibleDoublePath outputPaths = PossibleDoublePath.parse(output);
      FeatureSplitConfig featureSplitConfig;
      if (outputPaths.first != null) {
        featureSplitConfig =
            withCodeSplits.computeIfAbsent(outputPaths.first, k -> new FeatureSplitConfig());
        featureSplitConfig.outputJar = outputPaths.first;
        // We support adding resources independently of the input jars, which later --feature
        // can add, so we might have no input jars here, example:
        //  ... --feature :input_feature.ap_ out.jar:out_feature.ap_ --feature in.jar out.jar
        if (inputPaths.first != null) {
          featureSplitConfig.inputJars.add(inputPaths.first);
        }
      } else {
        featureSplitConfig = new FeatureSplitConfig();
        resourceOnlySplits.add(featureSplitConfig);
      }
      if (Objects.isNull(inputPaths.second) != Objects.isNull(outputPaths.second)) {
        throw new IllegalArgumentException(
            "Both input and output for feature resources must be provided");
      }
      featureSplitConfig.inputResources = inputPaths.second;
      featureSplitConfig.outputResources = outputPaths.second;
    }

    public Collection<FeatureSplitConfig> getConfigs() {
      ArrayList<FeatureSplitConfig> featureSplitConfigs = new ArrayList<>(resourceOnlySplits);
      featureSplitConfigs.addAll(withCodeSplits.values());
      return featureSplitConfigs;
    }
  }
}
