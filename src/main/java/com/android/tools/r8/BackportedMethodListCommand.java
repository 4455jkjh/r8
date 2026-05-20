// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.BaseCompilerCommandParser.LIB_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.MIN_API_FLAG;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Immutable command structure for an invocation of the {@link BackportedMethodList} tool.
 *
 * <p>To build a BackportedMethodList command use the {@link BackportedMethodListCommand.Builder}
 * class. For example:
 *
 * <pre>
 *   BackportedMethodListCommand command = BackportedMethodListCommand.builder()
 *     .setMinApiLevel(apiLevel)
 *     .setOutputPath(Paths.get("methods-list.txt"))
 *     .build();
 * </pre>
 */
@KeepForApi
public class BackportedMethodListCommand {

  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final int minApiLevel;
  private final boolean androidPlatformBuild;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final AndroidApp app;
  private final StringConsumer backportedMethodListConsumer;

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public boolean isAndroidPlatformBuild() {
    return androidPlatformBuild;
  }

  Reporter getReporter() {
    return reporter;
  }

  public int getMinApiLevel() {
    return minApiLevel;
  }

  public DesugaredLibrarySpecification getDesugaredLibraryConfiguration() {
    return desugaredLibrarySpecification;
  }

  public StringConsumer getBackportedMethodListConsumer() {
    return backportedMethodListConsumer;
  }

  AndroidApp getInputApp() {
    return app;
  }

  private BackportedMethodListCommand(boolean printHelp, boolean printVersion) {
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = new Reporter();
    this.minApiLevel = -1;
    this.androidPlatformBuild = false;
    this.desugaredLibrarySpecification = null;
    this.app = null;
    this.backportedMethodListConsumer = null;
  }

  private BackportedMethodListCommand(
      Reporter reporter,
      int minApiLevel,
      boolean androidPlatformBuild,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      AndroidApp app,
      StringConsumer backportedMethodListConsumer) {
    this.printHelp = false;
    this.printVersion = false;
    this.reporter = reporter;
    this.minApiLevel = minApiLevel;
    this.androidPlatformBuild = androidPlatformBuild;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.app = app;
    this.backportedMethodListConsumer = backportedMethodListConsumer;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static Builder parse(String[] args) {
    Builder builder = builder();
    createParser().parse(args, builder, err -> builder.error(new StringDiagnostic(err)));
    return builder;
  }

  private static CliParser<Builder> createParser() {
    BooleanBox hasDefinedApiLevel = new BooleanBox(false);
    var parser =
        new CliParser<Builder>(
            StringUtils.lines("Usage: BackportedMethodList [options]", " Options are:"));
    parser
        .option1(
            "--output",
            "<file>",
            "Output result in <file>.",
            (b, arg) -> b.setOutputPath(Paths.get(arg)))
        .option1(
            MIN_API_FLAG,
            "<number>",
            "Minimum Android API level for the application.",
            (b, arg) -> {
              if (hasDefinedApiLevel.get()) {
                b.error(new StringDiagnostic("Cannot set multiple --min-api options"));
              } else {
                CliParserUtils.parsePositiveInt(
                    arg,
                    b::setMinApiLevel,
                    err -> b.error(new StringDiagnostic("Invalid argument to --min-api: " + err)));
                hasDefinedApiLevel.set(true);
              }
            })
        .option1(
            "--desugared-lib",
            "<file>",
            "Desugared library configuration (JSON from the configuration).",
            (b, arg) -> b.addDesugaredLibraryConfiguration(StringResource.fromFile(Paths.get(arg))))
        .option1(
            LIB_FLAG,
            "<file>",
            "The compilation SDK library (android.jar).",
            (b, arg) -> b.addLibraryFiles(Paths.get(arg)))
        .option0(
            "--android-platform-build",
            "Compilation of platform code.",
            b -> b.setAndroidPlatformBuild(true))
        .option0(
            "--version", "Print the version of BackportedMethodList.", b -> b.setPrintVersion(true))
        .option0("--help", "Print this message.", b -> b.setPrintHelp(true), "-h");
    return parser;
  }

  public static String usageMessage() {
    return createParser().getUsageMessage();
  }

  @KeepForApi
  public static class Builder {

    private final Reporter reporter;
    private int minApiLevel = AndroidApiLevel.B.getLevel();
    private List<StringResource> desugaredLibrarySpecificationResources = new ArrayList<>();
    private final AndroidApp.Builder app;
    private StringConsumer backportedMethodListConsumer;
    private boolean printHelp = false;
    private boolean printVersion = false;
    private boolean androidPlatformBuild = false;

    private Builder() {
      this(new DiagnosticsHandler() {});
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.app = AndroidApp.builder();
      this.reporter = new Reporter(diagnosticsHandler);
    }

    /**
     * Set the minimum API level for the application compiled.
     *
     * <p>The tool will only report backported methods which are not present at this API level.
     *
     * <p>The default is 1 if never set.
     */
    public Builder setMinApiLevel(int minApiLevel) {
      if (minApiLevel <= 0) {
        reporter.error(new StringDiagnostic("Invalid minApiLevel: " + minApiLevel));
      } else {
        this.minApiLevel = minApiLevel;
      }
      return this;
    }

    public int getMinApiLevel() {
      return minApiLevel;
    }

    /** Desugared library configuration */
    public Builder addDesugaredLibraryConfiguration(StringResource configuration) {
      desugaredLibrarySpecificationResources.add(configuration);
      return this;
    }

    /** Desugared library configuration */
    public Builder addDesugaredLibraryConfiguration(String configuration) {
      return addDesugaredLibraryConfiguration(
          StringResource.fromString(configuration, Origin.unknown()));
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryResourceProvider(ClassFileResourceProvider provider) {
      app.addLibraryResourceProvider(provider);
      return this;
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryFiles(Path... files) {
      addLibraryFiles(Arrays.asList(files));
      return this;
    }

    /** The compilation SDK library (android.jar) */
    public Builder addLibraryFiles(Collection<Path> files) {
      for (Path path : files) {
        app.addLibraryFile(path);
      }
      return this;
    }

    DesugaredLibrarySpecification getDesugaredLibraryConfiguration(DexItemFactory factory) {
      if (desugaredLibrarySpecificationResources.isEmpty()) {
        return DesugaredLibrarySpecification.empty();
      }
      if (desugaredLibrarySpecificationResources.size() > 1) {
        reporter.fatalError("Only one desugared library configuration is supported.");
      }
      StringResource desugaredLibrarySpecificationResource =
          desugaredLibrarySpecificationResources.get(0);
      return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
          desugaredLibrarySpecificationResource, factory, reporter, false, getMinApiLevel());
    }

    /** Output file for the backported method list */
    public Builder setOutputPath(Path outputPath) {
      backportedMethodListConsumer =
          new StringConsumer.FileConsumer(outputPath) {
            @Override
            public void accept(String string, DiagnosticsHandler handler) {
              super.accept(string, handler);
              super.accept(System.lineSeparator(), handler);
            }
          };
      return this;
    }

    /** Consumer receiving the the backported method list */
    public Builder setConsumer(StringConsumer consumer) {
      this.backportedMethodListConsumer = consumer;
      return this;
    }

    /** True if the print-help flag is enabled. */
    public boolean isPrintHelp() {
      return printHelp;
    }

    /** Set the value of the print-help flag. */
    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public Builder setAndroidPlatformBuild(boolean androidPlatformBuild) {
      this.androidPlatformBuild = androidPlatformBuild;
      return this;
    }

    /** True if the print-version flag is enabled. */
    public boolean isPrintVersion() {
      return printVersion;
    }

    /** Set the value of the print-version flag. */
    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    private void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    public BackportedMethodListCommand build() {
      AndroidApp library = app.build();
      if (!desugaredLibrarySpecificationResources.isEmpty()
          && library.getLibraryResourceProviders().isEmpty()) {
        reporter.error(
            new StringDiagnostic("With desugared library configuration a library is required"));
      }

      if (isPrintHelp() || isPrintVersion()) {
        return new BackportedMethodListCommand(isPrintHelp(), isPrintVersion());
      }

      if (backportedMethodListConsumer == null) {
        backportedMethodListConsumer =
            new StringConsumer() {
              @Override
              public void accept(String string, DiagnosticsHandler handler) {
                System.out.println(string);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            };
      }
      DexItemFactory factory = new DexItemFactory();
      return new BackportedMethodListCommand(
          reporter,
          minApiLevel,
          androidPlatformBuild,
          getDesugaredLibraryConfiguration(factory),
          library,
          backportedMethodListConsumer);
    }
  }
}
