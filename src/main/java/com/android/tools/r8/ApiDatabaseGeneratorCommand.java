// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@KeepForApi
public final class ApiDatabaseGeneratorCommand {

  private final List<Path> inputPaths;
  private final Path outputPath;
  private final boolean printHelp;
  private final boolean printVersion;

  private ApiDatabaseGeneratorCommand(List<Path> inputPaths, Path outputPath) {
    this.inputPaths = inputPaths;
    this.outputPath = outputPath;
    this.printHelp = false;
    this.printVersion = false;
  }

  private ApiDatabaseGeneratorCommand(boolean printHelp, boolean printVersion) {
    this.inputPaths = Collections.emptyList();
    this.outputPath = null;
    this.printHelp = printHelp;
    this.printVersion = printVersion;
  }

  public List<Path> getInputPaths() {
    return inputPaths;
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

    public ApiDatabaseGeneratorCommand build() {
      if (printHelp || printVersion) {
        return new ApiDatabaseGeneratorCommand(printHelp, printVersion);
      }
      if (inputPaths.isEmpty()) {
        error(new StringDiagnostic("At least one input path must be specified"));
      }
      if (outputPath == null) {
        outputPath = Paths.get(".", "api_database.ser");
      }
      return new ApiDatabaseGeneratorCommand(inputPaths, outputPath);
    }

    public void error(Diagnostic diagnostic) {
      reporter.error(diagnostic);
    }
  }
}
