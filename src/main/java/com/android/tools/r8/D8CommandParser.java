// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.profile.art.ArtProfileConsumerUtils;
import com.android.tools.r8.profile.art.ArtProfileProviderUtils;
import com.android.tools.r8.profile.startup.StartupProfileProviderUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class D8CommandParser extends BaseCompilerCommandParser {

  public static List<ParseFlagInfo> getFlags() {
    return CliParserUtils.getFlagInfos(createParser());
  }

  private static final String APK_EXTENSION = ".apk";
  private static final String JAR_EXTENSION = ".jar";
  private static final String ZIP_EXTENSION = ".zip";

  private static boolean isArchive(Path path) {
    String name = StringUtils.toLowerCase(path.getFileName().toString());
    return name.endsWith(APK_EXTENSION)
        || name.endsWith(JAR_EXTENSION)
        || name.endsWith(ZIP_EXTENSION);
  }

  static class OrderedClassFileResourceProvider implements ClassFileResourceProvider {
    static class Builder {
      private final ImmutableList.Builder<ClassFileResourceProvider> builder =
          ImmutableList.builder();
      boolean empty = true;

      OrderedClassFileResourceProvider build() {
        return new OrderedClassFileResourceProvider(builder.build());
      }

      void addClassFileResourceProvider(ClassFileResourceProvider provider) {
        builder.add(provider);
        empty = false;
      }

      boolean isEmpty() {
        return empty;
      }
    }

    final List<ClassFileResourceProvider> providers;
    final Set<String> descriptors = Sets.newHashSet();

    private OrderedClassFileResourceProvider(ImmutableList<ClassFileResourceProvider> providers) {
      this.providers = providers;
      // Collect all descriptors that can be provided.
      this.providers.forEach(provider -> this.descriptors.addAll(provider.getClassDescriptors()));
    }

    static Builder builder() {
      return new Builder();
    }

    @Override
    public Set<String> getClassDescriptors() {
      return descriptors;
    }

    @Override
    public ProgramResource getProgramResource(String descriptor) {
      // Search the providers in order. Return the program resource from the first provider that can
      // provide it.
      for (ClassFileResourceProvider provider : providers) {
        if (provider.getClassDescriptors().contains(descriptor)) {
          return provider.getProgramResource(descriptor);
        }
      }
      return null;
    }
  }

  static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static D8Command.Builder parse(String[] args, Origin origin) {
    return parse(args, origin, D8Command.builder());
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static D8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return parse(args, origin, D8Command.builder(handler));
  }

  private static class ParserState {
    private Path buildMetadataOutputPath = null;
    private CompilationMode compilationMode = null;
    private Path outputPath = null;
    private Path globalsOutputPath = null;
    private OutputMode outputMode = null;
    private boolean hasDefinedApiLevel = false;
    private final OrderedClassFileResourceProvider.Builder classpathBuilder =
        OrderedClassFileResourceProvider.builder();
    private final D8Command.Builder builder;
    private final Origin origin;

    public ParserState(D8Command.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static CliParser<ParserState> createParser() {
    var header =
        StringUtils.joinLines(
            "Usage: d8 [options] [@<argfile>] <input-files>",
            " where <input-files> are any combination of dex, class, zip, jar, or apk files",
            " and each <argfile> is a file containing additional arguments (one per line)",
            " and options are:");
    return new CliParser<ParserState>(header)
        .option0(
            "--debug",
            "Compile with debugging information (default).",
            state -> {
              if (state.compilationMode == CompilationMode.RELEASE) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot compile in both --debug and --release mode.", state.origin));
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
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot output both to '" + state.outputPath + "' and '" + arg + "'",
                        state.origin));
              } else {
                state.outputPath = Paths.get(arg);
              }
            })
        .option1(
            "--globals",
            "<file>",
            "Global synthetics <file> from a previous intermediate compilation. The <file> may be"
                + " either a zip-archive of global synthetics or the global-synthetic files"
                + " directly.",
            (state, arg) -> state.builder.addGlobalSyntheticsFiles(Paths.get(arg)))
        .option1(
            "--globals-output",
            "<file>",
            "Output global synthetics in <file>. <file> must be an existing directory or a"
                + " non-existent zip archive.",
            (state, arg) -> {
              if (state.globalsOutputPath != null) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot output globals both to '"
                            + state.globalsOutputPath
                            + "' and '"
                            + arg
                            + "'",
                        state.origin);
                state.builder.error(diagnostic);
              } else {
                state.globalsOutputPath = Paths.get(arg);
              }
            })
        .option1(
            "--lib",
            "<file|jdk-home>",
            "Add <file|jdk-home> as a library resource.",
            (state, arg) -> addLibraryArgument(state.builder, arg, state.origin))
        .option1(
            "--classpath",
            "<file>",
            "Add <file> as a classpath resource.",
            (state, arg) -> {
              Path file = Paths.get(arg);
              try {
                if (!Files.exists(file)) {
                  throw new NoSuchFileException(file.toString());
                }
                if (isArchive(file)) {
                  state.classpathBuilder.addClassFileResourceProvider(
                      new ArchiveClassFileProvider(file));
                } else if (Files.isDirectory(file)) {
                  state.classpathBuilder.addClassFileResourceProvider(
                      DirectoryClassFileProvider.fromDirectory(file));
                } else {
                  state.builder.error(
                      new StringDiagnostic(
                          "Unsupported classpath file type", new PathOrigin(file)));
                }
              } catch (IOException e) {
                state.builder.error(new ExceptionDiagnostic(e, new PathOrigin(file)));
              }
            })
        .option1(
            "--min-api",
            "<number>",
            "Minimum Android API level compatibility (default: "
                + AndroidApiLevel.getDefault().getMajor()
                + ").",
            (state, arg) -> {
              if (state.hasDefinedApiLevel) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot set multiple " + MIN_API_FLAG + " options", state.origin));
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
            "--api-database",
            "<file>",
            "Use <file> as the Android API database for API modeling, overriding the default"
                + " database. <file> must be a .ser file generated by ApiDatabaseGenerator.",
            (state, arg) -> {
              if (state.builder.getApiDatabasePath() != null) {
                state.builder.error(
                    new StringDiagnostic(
                        "Cannot set multiple " + API_DATABASE_FLAG + " options", state.origin));
              } else {
                state.builder.setApiDatabasePath(Paths.get(arg));
              }
            })
        .option1(
            "--pg-map",
            "<file>",
            "Use <file> as a mapping file for distribution.",
            (state, arg) -> state.builder.setProguardMapInputFile(Paths.get(arg)))
        .option1(
            "--pg-map-output",
            "<file>",
            "Enable line optimization and output mapping to <file>.",
            (state, arg) -> state.builder.setProguardMapOutputPath(Paths.get(arg)))
        .option1(
            "--partition-map-output",
            "<file>",
            "Enable line optimization and output mapping to <file>.",
            (state, arg) -> state.builder.setPartitionMapOutputPath(Paths.get(arg)))
        .option0(
            "--intermediate",
            "Compile an intermediate result intended for later merging.",
            state -> state.builder.setIntermediate(true))
        .option0(
            "--file-per-class",
            "Produce a separate dex file per class. Synthetic classes are in their own file.",
            state -> state.outputMode = OutputMode.DexFilePerClass)
        .option0(
            "--file-per-class-file",
            "Produce a separate dex file per input .class file. Synthetic classes are with their"
                + " originating class.",
            state -> state.outputMode = OutputMode.DexFilePerClassFile)
        .option0(
            "--classfile",
            "Compile program to Java classfile format.",
            state -> state.outputMode = OutputMode.ClassFile)
        .option0(
            "--no-desugaring",
            "Force disable desugaring.",
            state -> state.builder.setDisableDesugaring(true))
        .option1(
            "--desugared-lib",
            "<file>",
            "Specify desugared library configuration. <file> is a desugared library configuration"
                + " (json).",
            (state, arg) ->
                state.builder.addDesugaredLibraryConfiguration(
                    StringResource.fromFile(Paths.get(arg))))
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
        .option1(
            "--main-dex-list-output",
            "<file>",
            "Output resulting main dex list in <file>.",
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
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot output build metadata to both '"
                            + state.buildMetadataOutputPath
                            + "' and '"
                            + arg
                            + "'",
                        state.origin);
                state.builder.error(diagnostic);
              } else {
                state.buildMetadataOutputPath = Paths.get(arg);
              }
            })
        .option0(
            "--verbose-synthetic-names",
            "Enable verbose synthetic names that use the `$$ExternalSynthetic` marker.",
            state -> state.builder.setEnableVerboseSyntheticNames(true))
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
            "--version", "Print the version of d8.", state -> state.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", state -> state.builder.setPrintHelp(true), "-h")
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

  private static D8Command.Builder parse(String[] args, Origin origin, D8Command.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    var state = new ParserState(builder, origin);
    createParser()
        .parse(
            expandedArgs,
            state,
            error -> state.builder.error(new StringDiagnostic(error, state.origin)));

    if (!state.classpathBuilder.isEmpty()) {
      builder.addClasspathResourceProvider(state.classpathBuilder.build());
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
    if (state.compilationMode != null) {
      builder.setMode(state.compilationMode);
    }
    if (state.outputMode == null) {
      state.outputMode = OutputMode.DexIndexed;
    }
    if (state.outputPath == null) {
      state.outputPath = Paths.get(".");
    }
    if (state.globalsOutputPath != null) {
      builder.setGlobalSyntheticsOutput(state.globalsOutputPath);
    }
    return builder.setOutput(state.outputPath, state.outputMode);
  }
}
