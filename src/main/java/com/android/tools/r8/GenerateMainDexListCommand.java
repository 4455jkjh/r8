// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.JoiningStringConsumer;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@KeepForApi
public class GenerateMainDexListCommand extends BaseCommand {

  private final List<ProguardConfigurationRule> mainDexKeepRules;
  private final StringConsumer mainDexListConsumer;
  private final GraphConsumer mainDexKeptGraphConsumer;
  private final DexItemFactory factory;
  private final Reporter reporter;

  @KeepForApi
  public static class Builder extends BaseCommand.Builder<GenerateMainDexListCommand, Builder> {

    private final DexItemFactory factory = new DexItemFactory();
    private final List<ProguardConfigurationSource> mainDexRules = new ArrayList<>();
    private StringConsumer mainDexListConsumer = null;
    private GraphConsumer mainDexKeptGraphConsumer = null;

    private Builder() {
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }


    @Override
    GenerateMainDexListCommand.Builder self() {
      return this;
    }

    /**
     * Add proguard configuration file resources for automatic main dex list calculation.
     */
    public GenerateMainDexListCommand.Builder addMainDexRulesFiles(Path... paths) {
      guard(() -> {
        for (Path path : paths) {
          mainDexRules.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /**
     * Add proguard configuration file resources for automatic main dex list calculation.
     */
    public GenerateMainDexListCommand.Builder addMainDexRulesFiles(List<Path> paths) {
      guard(() -> {
        for (Path path : paths) {
          mainDexRules.add(new ProguardConfigurationSourceFile(path));
        }
      });
      return self();
    }

    /**
     * Add proguard configuration for automatic main dex list calculation.
     */
    public GenerateMainDexListCommand.Builder addMainDexRules(List<String> lines, Origin origin) {
      String config = String.join(System.lineSeparator(), lines);
      mainDexRules.add(new ProguardConfigurationSourceStrings(config, Paths.get("."), origin));
      return self();
    }

    /**
     * Set the output file for the main-dex list.
     *
     * If the file exists it will be overwritten.
     */
    public GenerateMainDexListCommand.Builder setMainDexListOutputPath(Path mainDexListOutputPath) {
      mainDexListConsumer = new StringConsumer.FileConsumer(mainDexListOutputPath);
      return self();
    }

    public GenerateMainDexListCommand.Builder setMainDexListConsumer(
        StringConsumer mainDexListConsumer) {
      this.mainDexListConsumer = mainDexListConsumer;
      return self();
    }

    @Override
    protected GenerateMainDexListCommand makeCommand() {
      // If printing versions ignore everything else.
      if (isPrintHelp() || isPrintVersion()) {
        return new GenerateMainDexListCommand(isPrintHelp(), isPrintVersion());
      }

      List<ProguardConfigurationRule> mainDexKeepRules =
          ProguardConfigurationParser.parseMainDex(mainDexRules, factory, getReporter());

      return new GenerateMainDexListCommand(
          factory,
          getAppBuilder().build(),
          mainDexKeepRules,
          new JoiningStringConsumer(mainDexListConsumer, "\n"),
          mainDexKeptGraphConsumer,
          getReporter());
    }

    public GenerateMainDexListCommand.Builder setMainDexKeptGraphConsumer(
        GraphConsumer graphConsumer) {
      this.mainDexKeptGraphConsumer = graphConsumer;
      return self();
    }
  }

  static String usageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  public static GenerateMainDexListCommand.Builder builder() {
    return new GenerateMainDexListCommand.Builder();
  }

  public static GenerateMainDexListCommand.Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new GenerateMainDexListCommand.Builder(diagnosticsHandler);
  }

  public static GenerateMainDexListCommand.Builder parse(String[] args) {
    GenerateMainDexListCommand.Builder builder = builder();
    createParser()
        .parse(
            args,
            builder,
            err ->
                builder.getReporter().error(new StringDiagnostic(err, CommandLineOrigin.INSTANCE)));
    return builder;
  }

  public StringConsumer getMainDexListConsumer() {
    return mainDexListConsumer;
  }

  Reporter getReporter() {
    return reporter;
  }

  private static CliParser<GenerateMainDexListCommand.Builder> createParser() {
    var header =
        StringUtils.joinLines(
            "Usage: maindex [options] <input-files>",
            " where <input-files> are JAR files",
            " and options are:");
    var parser = new CliParser<GenerateMainDexListCommand.Builder>(header);
    return parser
        .option1(
            "--lib",
            "<file>",
            "Add <file> as a library resource.",
            (b, arg) -> b.addLibraryFiles(Paths.get(arg)))
        .option1(
            "--main-dex-rules",
            "<file>",
            "Proguard keep rules for classes to place in the primary dex file.",
            (b, arg) -> b.addMainDexRulesFiles(Paths.get(arg)))
        .option1(
            "--main-dex-list",
            "<file>",
            "List of classes to place in the primary dex file.",
            (b, arg) -> b.addMainDexListFiles(Paths.get(arg)))
        .option1(
            "--main-dex-list-output",
            "<file>",
            "Output the full main-dex list in <file>.",
            (b, arg) -> b.setMainDexListOutputPath(Paths.get(arg)))
        .option0("--version", "Print the version.", b -> b.setPrintVersion(true))
        .option0("--help", "Print this message.", b -> b.setPrintHelp(true))
        .positional((b, arg) -> b.addProgramFiles(Paths.get(arg)));
  }

  private GenerateMainDexListCommand(
      DexItemFactory factory,
      AndroidApp inputApp,
      List<ProguardConfigurationRule> mainDexKeepRules,
      StringConsumer mainDexListConsumer,
      GraphConsumer mainDexKeptGraphConsumer,
      Reporter reporter) {
    super(inputApp);
    this.factory = factory;
    this.mainDexKeepRules = mainDexKeepRules;
    this.mainDexListConsumer = mainDexListConsumer;
    this.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;
    this.reporter = reporter;
  }

  private GenerateMainDexListCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    this.factory = new DexItemFactory();
    this.mainDexKeepRules = ImmutableList.of();
    this.mainDexListConsumer = null;
    this.mainDexKeptGraphConsumer = null;
    this.reporter = new Reporter();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, reporter);
    internal.programConsumer = DexIndexedConsumer.emptyConsumer();
    internal.mainDexKeepRules = mainDexKeepRules;
    internal.mainDexListConsumer = mainDexListConsumer;
    internal.mainDexKeptGraphConsumer = mainDexKeptGraphConsumer;
    internal.minimalMainDex = internal.debug;
    assert internal.retainCompileTimeAnnotations;
    internal.retainCompileTimeAnnotations = false;
    // Disable fast path in AppInfoWithClassHierarchy#isSubtype.
    internal.getTestingOptions().allowLibraryExtendsProgramInFullMode = true;
    return internal;
  }
}

