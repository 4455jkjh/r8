// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

@KeepForApi
public class DesugaredMethodsListCommand {

  private final boolean help;
  private final boolean version;
  private final int minMajorApi;

  @SuppressWarnings("UnusedVariable")
  private final int minMinorApi;

  private final Reporter reporter;
  private final StringResource desugarLibrarySpecification;
  private final Collection<ProgramResourceProvider> desugarLibraryImplementation;
  private final StringConsumer outputConsumer;
  private final Collection<ClassFileResourceProvider> library;
  private final boolean androidPlatformBuild;

  DesugaredMethodsListCommand(
      int minMajorApi,
      int minMinorApi,
      Reporter reporter,
      StringResource desugarLibrarySpecification,
      Collection<ProgramResourceProvider> desugarLibraryImplementation,
      StringConsumer outputConsumer,
      Collection<ClassFileResourceProvider> library,
      boolean androidPlatformBuild) {
    this.help = false;
    this.version = false;
    this.minMajorApi = minMajorApi;
    this.minMinorApi = minMinorApi;
    this.reporter = reporter;
    this.desugarLibrarySpecification = desugarLibrarySpecification;
    this.desugarLibraryImplementation = desugarLibraryImplementation;
    this.outputConsumer = outputConsumer;
    this.library = library;
    this.androidPlatformBuild = androidPlatformBuild;
  }

  DesugaredMethodsListCommand(boolean help, boolean version) {
    this.help = help;
    this.version = version;
    this.minMajorApi = -1;
    this.minMinorApi = 0;
    this.reporter = null;
    this.desugarLibrarySpecification = null;
    this.desugarLibraryImplementation = null;
    this.outputConsumer = null;
    this.library = null;
    this.androidPlatformBuild = false;
  }

  public static DesugaredMethodsListCommand parse(String[] args) {
    return parse(args, new Reporter());
  }

  public static DesugaredMethodsListCommand parse(String[] args, Reporter reporter) {
    return new DesugaredMethodsListCommandParser().parse(args, reporter);
  }

  public int getMinApi() {
    return minMajorApi;
  }

  public boolean isAndroidPlatformBuild() {
    return androidPlatformBuild;
  }

  public StringResource getDesugarLibrarySpecification() {
    return desugarLibrarySpecification;
  }

  public Collection<ProgramResourceProvider> getDesugarLibraryImplementation() {
    return desugarLibraryImplementation;
  }

  public StringConsumer getOutputConsumer() {
    return outputConsumer;
  }

  public Collection<ClassFileResourceProvider> getLibrary() {
    return library;
  }

  public boolean isHelp() {
    return help;
  }

  public boolean isVersion() {
    return version;
  }

  public static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(DesugaredMethodsListCommandParser.createParser());
  }

  public Reporter getReporter() {
    return reporter;
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  @KeepForApi
  public static class Builder {

    private int minMajorApi = AndroidApiLevel.B.getLevel();
    private int minMinorApi = 0;
    private final Reporter reporter;
    private StringResource desugarLibrarySpecification = null;
    private Collection<ProgramResourceProvider> desugarLibraryImplementation = new ArrayList<>();
    private StringConsumer outputConsumer;
    private Collection<ClassFileResourceProvider> library = new ArrayList<>();

    private boolean help = false;
    private boolean version = false;
    private boolean androidPlatformBuild = false;

    public Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    public Builder setMinApi(int minMajorApi) {
      this.minMajorApi = minMajorApi;
      return this;
    }

    public Builder setMinApi(int minMajorApi, int minMinorApi) {
      this.minMajorApi = minMajorApi;
      this.minMinorApi = minMinorApi;
      return this;
    }

    public Builder setDesugarLibrarySpecification(StringResource desugarLibrarySpecification) {
      this.desugarLibrarySpecification = desugarLibrarySpecification;
      return this;
    }

    public Builder setOutputConsumer(StringConsumer outputConsumer) {
      this.outputConsumer = outputConsumer;
      return this;
    }

    public Builder setOutputPath(Path outputPath) {
      this.outputConsumer =
          new StringConsumer.FileConsumer(outputPath) {
            @Override
            public void accept(String string, DiagnosticsHandler handler) {
              super.accept(string, handler);
              super.accept(System.lineSeparator(), handler);
            }
          };
      return this;
    }

    public Builder addDesugarLibraryImplementation(
        ProgramResourceProvider programResourceProvider) {
      desugarLibraryImplementation.add(programResourceProvider);
      return this;
    }

    public Builder addLibrary(ClassFileResourceProvider classFileResourceProvider) {
      library.add(classFileResourceProvider);
      return this;
    }

    public Builder setHelp() {
      this.help = true;
      return this;
    }

    public Builder setVersion() {
      this.version = true;
      return this;
    }

    public Builder setAndroidPlatformBuild() {
      this.androidPlatformBuild = true;
      return this;
    }

    public DesugaredMethodsListCommand build() {
      // The min-api level defaults to 1, it's always present.
      // If desugarLibraryImplementation is empty, this generates only the backported method list.

      if (help || version) {
        return new DesugaredMethodsListCommand(help, version);
      }

      if (androidPlatformBuild && !desugarLibraryImplementation.isEmpty()) {
        reporter.error("With platform build desugared library is not allowed.");
      }

      if (desugarLibrarySpecification != null && library.isEmpty()) {
        reporter.error("With desugared library specification a library is required.");
      }

      if (!desugarLibraryImplementation.isEmpty() && desugarLibrarySpecification == null) {
        reporter.error(
            "The desugar library specification is required when desugared library implementation is"
                + " present.");
      }

      if (outputConsumer == null) {
        outputConsumer =
            new StringConsumer() {
              @Override
              public void accept(String string, DiagnosticsHandler handler) {
                System.out.println(string);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            };
      }
      return new DesugaredMethodsListCommand(
          minMajorApi,
          minMinorApi,
          reporter,
          desugarLibrarySpecification,
          desugarLibraryImplementation,
          outputConsumer,
          library,
          androidPlatformBuild);
    }
  }

  public static class DesugaredMethodsListCommandParser {

    private static CliParser<DesugaredMethodsListCommand.Builder> createParser() {
      var header = "Usage: desugaredmethods [options] where  options are:";
      var parser = new CliParser<DesugaredMethodsListCommand.Builder>(header);
      return parser
          .option1(
              "--output",
              "<file>",
              "Output result in <file>. <file> must be an existing directory or a zip file.",
              (b, arg) -> b.setOutputPath(Paths.get(arg)))
          .option1(
              "--lib",
              "<file|jdk-home>",
              "Add <file|jdk-home> as a library resource.",
              (b, arg) -> {
                try {
                  b.addLibrary(new ArchiveClassFileProvider(Paths.get(arg)));
                } catch (IOException e) {
                  b.reporter.error(new ExceptionDiagnostic(e, new PathOrigin(Paths.get(arg))));
                } catch (UncheckedIOException e) {
                  b.reporter.error(
                      new ExceptionDiagnostic(e.getCause(), new PathOrigin(Paths.get(arg))));
                }
              })
          .option1(
              "--min-api",
              "<number>",
              "Minimum Android API level compatibility (default: "
                  + AndroidApiLevel.getDefault().getLevel()
                  + ").",
              (b, arg) ->
                  CliParserUtils.parsePositiveInt(
                      arg,
                      b::setMinApi,
                      err -> b.reporter.error(new StringDiagnostic("Invalid min-api: " + err))))
          .option0("--version", "Print the version of DesugaredMethods.", Builder::setVersion)
          .option0("--help", "Print this message.", Builder::setHelp)
          .option1(
              "--desugared-lib",
              "<file>",
              "Specify desugared library configuration. <file> is a desugared library configuration"
                  + " (json).",
              (b, arg) -> b.setDesugarLibrarySpecification(StringResource.fromFile(Paths.get(arg))))
          .option0(
              "--android-platform-build",
              "Compile as a platform build where the runtime/bootclasspath is assumed to be the"
                  + " version specified by --min-api.",
              Builder::setAndroidPlatformBuild)
          .option1(
              "--desugared-lib-jar",
              "<file>",
              "Specify desugared library jar.",
              (b, arg) ->
                  b.addDesugarLibraryImplementation(
                      ArchiveProgramResourceProvider.fromArchive(Paths.get(arg))));
    }

    public DesugaredMethodsListCommand parse(String[] args, DiagnosticsHandler handler) {
      DesugaredMethodsListCommand.Builder builder = DesugaredMethodsListCommand.builder(handler);
      createParser().parse(args, builder, err -> builder.reporter.error(new StringDiagnostic(err)));
      return builder.build();
    }
  }
}
