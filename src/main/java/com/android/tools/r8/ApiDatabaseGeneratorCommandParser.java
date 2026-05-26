// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Paths;

public class ApiDatabaseGeneratorCommandParser {

  private static CliParser<ApiDatabaseGeneratorCommand.Builder> createParser() {
    String usageHeader =
        StringUtils.joinLines(
            "Usage: apidatabasegenerator [options] <input-files>", "where options are:");
    CliParser<ApiDatabaseGeneratorCommand.Builder> parser = new CliParser<>(usageHeader);
    return parser
        .option0("--help", "Print help.", builder -> builder.setPrintHelp(true), "-h")
        .option0("--version", "Print version.", builder -> builder.setPrintVersion(true))
        .option1(
            "--output",
            "<database-file>",
            "Output result in <database-file> (must be a file, not a directory). Defaults to"
                + " 'api_database.ser'.",
            (builder, arg) -> builder.setOutputPath(Paths.get(arg)))
        .positional((builder, arg) -> builder.addInputPath(Paths.get(arg)));
  }

  public static ApiDatabaseGeneratorCommand.Builder parse(String[] args, Origin origin) {
    return new ApiDatabaseGeneratorCommandParser()
        .parse(args, origin, ApiDatabaseGeneratorCommand.builder());
  }

  public static ApiDatabaseGeneratorCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new ApiDatabaseGeneratorCommandParser()
        .parse(args, origin, ApiDatabaseGeneratorCommand.builder(handler));
  }

  private ApiDatabaseGeneratorCommand.Builder parse(
      String[] args, Origin origin, ApiDatabaseGeneratorCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    createParser()
        .parse(expandedArgs, builder, error -> builder.error(new StringDiagnostic(error, origin)));
    return builder;
  }

  static String getUsageMessage() {
    return createParser().getUsageMessage();
  }
}
