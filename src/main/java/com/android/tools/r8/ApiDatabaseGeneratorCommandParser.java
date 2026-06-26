// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Paths;

public class ApiDatabaseGeneratorCommandParser {

  private static class ParserState {
    final ApiDatabaseGeneratorCommand.Builder builder;
    final Origin origin;

    ParserState(ApiDatabaseGeneratorCommand.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static CliParser<ParserState> createParser() {
    String usageHeader =
        StringUtils.joinLines(
            "Usage: apidatabasegenerator [options] <input-files>",
            "where <input-files> are Android API XML files (e.g., api-versions.xml) to merge,",
            "and options are:");
    CliParser<ParserState> parser = new CliParser<>(usageHeader);
    return parser
        .option0("--help", "Print help.", state -> state.builder.setPrintHelp(true), "-h")
        .option0("--version", "Print version.", state -> state.builder.setPrintVersion(true))
        .option1(
            "--output",
            "<database-file>",
            "Output result in <database-file> (must be a file, not a directory). Defaults to"
                + " 'api_database.ser'.",
            (state, arg) -> state.builder.setOutputPath(Paths.get(arg)))
        .prefix2(
            "--map-diagnostics",
            "[:<type>]",
            "<from-level>",
            "<to-level>",
            "Map diagnostics of <type> (default any) reported as <from-level> to <to-level> where"
                + " <from-level> and <to-level> are one of 'none', 'info', 'warning', or 'error',"
                + " and the optional <type> is either the simple or fully qualified Java type name"
                + " of a diagnostic. If <type> is unspecified, all diagnostics at <from-level> will"
                + " be mapped. Note that fatal compiler errors cannot be mapped.",
            (state, suffix, fromLevel, toLevel) ->
                CliParserUtils.parseDiagnosticsMapping(
                    suffix,
                    fromLevel,
                    toLevel,
                    m -> state.builder.addDiagnosticsLevelMapping(m.from, m.diagnosticType, m.to),
                    state.builder::error,
                    state.origin))
        .positional((state, arg) -> state.builder.addInputPath(Paths.get(arg)));
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
    ParserState state = new ParserState(builder, origin);
    createParser()
        .parse(expandedArgs, state, error -> builder.error(new StringDiagnostic(error, origin)));
    return builder;
  }

  static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }
}
