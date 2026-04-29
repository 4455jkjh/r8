// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class ApiDatabaseGeneratorCommandParser {

  private static final String LOWER_CASE_NAME = "apidatabasegenerator";
  private static final String OUTPUT_FLAG = "--output";

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: " + LOWER_CASE_NAME + " [options] <input-files>", " where options are:");

  public static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(
            ParseFlagInfoImpl.flag1(
                OUTPUT_FLAG,
                "<database-file>",
                "Output result in <database-file> (must be a file, not a directory). Defaults to"
                    + " 'api_database.ser'."))
        .add(ParseFlagInfoImpl.getVersion(LOWER_CASE_NAME))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(builder, USAGE_MESSAGE);
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER = ImmutableSet.of(OUTPUT_FLAG);

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
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_ONE_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
      }
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals(OUTPUT_FLAG)) {
        builder.setOutputPath(Paths.get(nextArg));
      } else if (arg.startsWith("--")) {
        builder.error(new StringDiagnostic("Unknown option: " + arg, origin));
      } else {
        builder.addInputPath(Paths.get(arg));
      }
    }
    return builder;
  }
}
