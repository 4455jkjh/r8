// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.bisect;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BisectOptions {

  public static final String BUILD_GOOD_FLAG = "--good";
  public static final String BUILD_BAD_FLAG = "--bad";
  public static final String RESULT_GOOD_FLAG = "--result-good";
  public static final String RESULT_BAD_FLAG = "--result-bad";
  public static final String STATE_FLAG = "--state";
  public static final String OUTPUT_FLAG = "--output";
  public static final String COMMAND_FLAG = "--command";

  public final Path goodBuild;
  public final Path badBuild;
  public final Path stateFile;
  public final Path command;
  public final Path output;
  public final Result result;

  public enum Result {
    UNKNOWN,
    GOOD,
    BAD
  }

  private BisectOptions(
      Path goodBuild, Path badBuild, Path stateFile, Path command, Path output, Result result) {
    this.goodBuild = goodBuild;
    this.badBuild = badBuild;
    this.stateFile = stateFile;
    this.command = command;
    this.output = output;
    this.result = result;
  }

  private static class ParserState {
    boolean printHelp = false;
    Path goodBuild = null;
    Path badBuild = null;
    Path stateFile = null;
    Path command = null;
    Path output = null;
    Result result = Result.UNKNOWN;
  }

  private static CliParser<ParserState> createParser() {
    String header = StringUtils.joinLines("Usage: bisect [options]", "where options are:");
    var parser = new CliParser<ParserState>(header);
    return parser
        .option1(BUILD_BAD_FLAG, "<apk>", "Known bad APK.", (b, arg) -> b.badBuild = Paths.get(arg))
        .option1(
            COMMAND_FLAG,
            "<file>",
            "Command to run after each bisection.",
            (b, arg) -> b.command = Paths.get(arg))
        .option1(
            BUILD_GOOD_FLAG, "<apk>", "Known good APK.", (b, arg) -> b.goodBuild = Paths.get(arg))
        .option0("--help", "Print this message.", b -> b.printHelp = true, "-h")
        .option1(OUTPUT_FLAG, "<dir>", "Output directory.", (b, arg) -> b.output = Paths.get(arg))
        .option0(
            RESULT_BAD_FLAG,
            "Bisect again assuming previous run was bad.",
            b -> b.result = checkSingleResult(b.result, Result.BAD))
        .option0(
            RESULT_GOOD_FLAG,
            "Bisect again assuming previous run was good.",
            b -> b.result = checkSingleResult(b.result, Result.GOOD))
        .option1(
            STATE_FLAG, "<file>", "Bisection state.", (b, arg) -> b.stateFile = Paths.get(arg));
  }

  public static BisectOptions parse(String[] args) {
    var parser = createParser();
    var state = new ParserState();
    var hasError = new BooleanBox(false);
    parser.parse(
        args,
        state,
        err -> {
          System.err.println(err);
          hasError.set(true);
        });
    if (state.printHelp) {
      return null;
    }
    if (hasError.get()) {
      throw new CompilationError("Failed to parse bisect arguments");
    }
    exists(require(state.badBuild, BUILD_BAD_FLAG), BUILD_BAD_FLAG);
    exists(require(state.goodBuild, BUILD_GOOD_FLAG), BUILD_GOOD_FLAG);
    if (state.stateFile != null) {
      exists(state.stateFile, STATE_FLAG);
    }
    if (state.command != null) {
      exists(state.command, COMMAND_FLAG);
    }
    if (state.output != null) {
      directoryExists(state.output, OUTPUT_FLAG);
    }
    return new BisectOptions(
        state.goodBuild,
        state.badBuild,
        state.stateFile,
        state.command,
        state.output,
        state.result);
  }

  private static Path require(Path value, String flag) {
    if (value == null) {
      throw new CompilationError("Missing required option: " + flag);
    }
    return value;
  }

  private static Path exists(Path path, String flag) {
    if (Files.exists(path)) {
      return path;
    }
    throw new CompilationError("File " + flag + ": " + path + " does not exist");
  }

  private static Path directoryExists(Path path, String flag) {
    if (Files.exists(path) && Files.isDirectory(path)) {
      return path;
    }
    throw new CompilationError("File " + flag + ": " + path + " is not a valid directory");
  }

  private static Result checkSingleResult(Result current, Result result) {
    if (current != Result.UNKNOWN) {
      throw new CompilationError(
          "Cannot specify " + RESULT_GOOD_FLAG + " and " + RESULT_BAD_FLAG + " simultaneously");
    }
    return result;
  }

  public static String usageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }
}
