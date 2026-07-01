// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GlobalSyntheticsGeneratorCommandParser {

  public static List<ParseFlagInfo> getFlags() {
    return CliParserUtils.getFlagInfos(createParser());
  }

  public static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  public static GlobalSyntheticsGeneratorCommand.Builder parse(String[] args, Origin origin) {
    return new GlobalSyntheticsGeneratorCommandParser()
        .parse(args, origin, GlobalSyntheticsGeneratorCommand.builder());
  }

  public static GlobalSyntheticsGeneratorCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new GlobalSyntheticsGeneratorCommandParser()
        .parse(args, origin, GlobalSyntheticsGeneratorCommand.builder(handler));
  }

  private static class ParserState {
    final GlobalSyntheticsGeneratorCommand.Builder builder;
    final Origin origin;
    Path outputPath = null;
    boolean hasDefinedApiLevel = false;

    private ParserState(GlobalSyntheticsGeneratorCommand.Builder builder, Origin origin) {
      this.builder = builder;
      this.origin = origin;
    }
  }

  private static CliParser<ParserState> createParser() {
    var toolName = "globalsyntheticsgenerator";
    int defaultApi = AndroidApiLevel.getDefault().getLevel();
    String minApiFlag = "--min-api";

    var header = "Usage: " + toolName + " [options] where options are:";
    var parser = new CliParser<ParserState>(header);
    return parser
        .option1(
            minApiFlag,
            "<number>",
            "Minimum Android API level compatibility (default: " + defaultApi + ").",
            (b, arg) -> {
              if (b.hasDefinedApiLevel) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot set multiple " + minApiFlag + " options", b.origin);
                b.builder.error(diagnostic);
              } else {
                CliParserUtils.parsePositiveInt(
                    arg,
                    i -> {
                      b.builder.setMinApiLevel(i);
                      b.hasDefinedApiLevel = true;
                    },
                    err -> {
                      StringDiagnostic diagnostic =
                          new StringDiagnostic(
                              "Invalid argument to " + minApiFlag + ": " + err, b.origin);
                      b.builder.error(diagnostic);
                    });
              }
            })
        .option1(
            "--lib",
            "<file|jdk-home>",
            "Add <file|jdk-home> as a library resource.",
            (b, arg) -> b.builder.addLibraryFiles(Paths.get(arg)))
        .option1(
            "--output",
            "<globals-file>",
            "Output result in <globals-file>.",
            (b, arg) -> {
              if (b.outputPath != null) {
                StringDiagnostic diagnostic =
                    new StringDiagnostic(
                        "Cannot output both to '" + b.outputPath + "' and '" + arg + "'", b.origin);
                b.builder.error(diagnostic);
              } else {
                b.outputPath = Paths.get(arg);
              }
            })
        .option0(
            "--classfile",
            "Generate globals for only classfile to classfile desugaring. (By default globals for"
                + " both classfile and dex desugaring are generated).",
            b -> b.builder.setClassfileDesugaringOnly(true))
        .option0(
            "--verbose-synthetic-names",
            "Enable verbose synthetic names that use the `$$ExternalSynthetic` marker.",
            b -> b.builder.setEnableVerboseSyntheticNames(true))
        .option0(
            "--version",
            "Print the version of " + toolName + ".",
            b -> b.builder.setPrintVersion(true))
        .option0("--help", "Print this message.", b -> b.builder.setPrintHelp(true));
  }

  private GlobalSyntheticsGeneratorCommand.Builder parse(
      String[] args, Origin origin, GlobalSyntheticsGeneratorCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    var state = new ParserState(builder, origin);
    createParser()
        .parse(expandedArgs, state, err -> builder.error(new StringDiagnostic(err, origin)));
    if (state.outputPath == null) {
      state.outputPath = Paths.get(".");
    }
    return builder.setGlobalSyntheticsOutput(state.outputPath);
  }
}
