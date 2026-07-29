// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.Box;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@KeepForApi
public final class ApiDatabaseGeneratorCommand {

  private final List<Path> jarPaths;
  private final List<Path> xmlPaths;
  private final Path outputPath;
  private final Reporter reporter;
  private final boolean printHelp;
  private final boolean printVersion;

  private ApiDatabaseGeneratorCommand(
      List<Path> jarPaths, List<Path> xmlPaths, Path outputPath, Reporter reporter) {
    this.jarPaths = jarPaths;
    this.xmlPaths = xmlPaths;
    this.outputPath = outputPath;
    this.reporter = reporter;
    this.printHelp = false;
    this.printVersion = false;
  }

  private ApiDatabaseGeneratorCommand(boolean printHelp, boolean printVersion) {
    this.jarPaths = Collections.emptyList();
    this.xmlPaths = Collections.emptyList();
    this.outputPath = null;
    this.reporter = new Reporter();
    this.printHelp = printHelp;
    this.printVersion = printVersion;
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return reporter;
  }

  public Reporter getReporter() {
    return reporter;
  }

  public List<Path> getJarPaths() {
    return jarPaths;
  }

  public List<Path> getXmlPaths() {
    return xmlPaths;
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public static Builder parse(String[] args, Origin origin) {
    return ApiDatabaseGeneratorCommandParser.parse(args, origin);
  }

  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return ApiDatabaseGeneratorCommandParser.parse(args, origin, handler);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  @KeepForApi
  public static class Builder {
    private final List<Path> inputPaths = new ArrayList<>();
    private Path outputPath = null;
    private boolean printHelp = false;
    private boolean printVersion = false;
    private final Reporter reporter;

    private Builder() {
      this.reporter = new Reporter();
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = new Reporter(diagnosticsHandler);
    }

    public Builder addInputPath(Path inputPath) {
      this.inputPaths.add(inputPath);
      return this;
    }

    public Builder setOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    public ApiDatabaseGeneratorCommand build() throws ApiDatabaseGeneratorException {
      if (printHelp || printVersion) {
        return new ApiDatabaseGeneratorCommand(printHelp, printVersion);
      }
      Box<ApiDatabaseGeneratorCommand> box = new Box<>(null);
      ExceptionUtils.withDiagnosticsHandler(
          reporter,
          () -> {
            List<Path> jarPaths = new ArrayList<>();
            List<Path> xmlPaths = new ArrayList<>();
            for (Path path : inputPaths) {
              String name = path.getFileName().toString().toLowerCase();
              if (name.endsWith(".jar")) {
                jarPaths.add(path);
              } else if (name.endsWith(".xml")) {
                xmlPaths.add(path);
              } else {
                error(
                    new StringDiagnostic(
                        "Unsupported input file extension: "
                            + path
                            + ". Must be either .jar or .xml"));
              }
            }
            if (jarPaths.isEmpty()) {
              error(new StringDiagnostic("At least one SDK JAR input path must be specified"));
            }
            if (xmlPaths.isEmpty()) {
              error(new StringDiagnostic("At least one API XML input path must be specified"));
            }
            if (outputPath == null) {
              outputPath = Paths.get(".", "api_database.ser");
            }
            box.set(new ApiDatabaseGeneratorCommand(jarPaths, xmlPaths, outputPath, reporter));
          },
          (message, cause, cancelled) -> new ApiDatabaseGeneratorException(message, cause));
      return box.get();
    }

    public void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }

    public Builder addDiagnosticsLevelMapping(
        DiagnosticsLevel from, String diagnosticsClassName, DiagnosticsLevel to) {
      reporter.addDiagnosticsLevelMapping(from, diagnosticsClassName, to);
      return this;
    }
  }
}
