// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.CompilerCommandParserUtils;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathBasedMavenOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.collections.Pair;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@KeepForApi
public class LibraryAnalyzerCommandParser {

  public static String getUsageMessage() {
    return CliParserUtils.getUsageMessage(createParser());
  }

  public static LibraryAnalyzerCommand.Builder parse(String[] args, Origin origin) {
    Reporter reporter = new Reporter();
    LibraryAnalyzerCommand.Builder builder = LibraryAnalyzerCommand.builder();
    String[] expandedArgs = FlagFile.expandFlagFiles(args, reporter::error);
    ParserState parserState = new ParserState(builder, origin, reporter);
    createParser()
        .parse(
            expandedArgs,
            parserState,
            error -> reporter.error(new StringDiagnostic(error, origin)));
    parserState.flushPendingPath();
    return builder;
  }

  private enum PathType {
    Jar,
    Aar
  }

  private static class ParserState {
    private Pair<Path, PathType> pendingPath = null;
    private final LibraryAnalyzerCommand.Builder builder;
    private final Origin origin;
    private final Reporter reporter;

    private ParserState(LibraryAnalyzerCommand.Builder builder, Origin origin, Reporter reporter) {
      this.builder = builder;
      this.origin = origin;
      this.reporter = reporter;
    }

    void addPendingPathWithOrigin(Origin origin) {
      if (pendingPath.getSecond() == PathType.Jar) {
        if (origin != null) {
          builder.addJarPath(pendingPath.getFirst(), origin);
        } else {
          builder.addJarPath(pendingPath.getFirst());
        }
      } else {
        assert pendingPath.getSecond() == PathType.Aar;
        if (origin != null) {
          builder.addAarPath(pendingPath.getFirst(), origin);
        } else {
          builder.addAarPath(pendingPath.getFirst());
        }
      }
    }

    void flushPendingPath() {
      if (pendingPath != null) {
        addPendingPathWithOrigin(null);
      }
    }
  }

  private static CliParser<ParserState> createParser() {
    var header = StringUtils.joinLines("Usage: libanalyzer [options]", "where options are:");
    return new CliParser<ParserState>(header)
        .option1(
            "--aar",
            "<path>",
            "Path to Android Archive (AAR) that should be analyzed.",
            (state, arg) -> {
              Path aarPath = Paths.get(arg);
              if (!FileUtils.isAarFile(aarPath)) {
                throw new IllegalArgumentException("Expected AAR, got: " + arg);
              }
              state.flushPendingPath();
              state.pendingPath = Pair.create(aarPath, PathType.Aar);
            })
        .option1(
            "--keep-radius-output",
            "<path>",
            "Path where to write keep radius result (protobuf).",
            (state, arg) -> state.builder.setKeepRadiusOutputPath(Paths.get(arg)))
        .option1(
            "--jar",
            "<path>",
            "Path to Java Archive (JAR) that should be analyzed.",
            (state, arg) -> {
              Path jarPath = Paths.get(arg);
              if (!FileUtils.isJarFile(jarPath)) {
                throw new IllegalArgumentException("Expected JAR, got: " + arg);
              }
              state.flushPendingPath();
              state.pendingPath = Pair.create(jarPath, PathType.Jar);
            })
        .option1(
            "--lib",
            "<path>",
            "Path to file or JDK home to use as a library resource.",
            (state, arg) ->
                CompilerCommandParserUtils.addLibraryArgument(
                    state.builder.getAppBuilder(), arg, state.origin, state.reporter))
        .option1(
            "--maven-coord",
            "<x:y:z>",
            "Set the Maven coordinate of the previous --aar/--jar.",
            (state, arg) -> {
              if (state.pendingPath == null) {
                state.reporter.error(
                    new StringDiagnostic(
                        "No preceding --jar or --aar to modify (or multiple --maven-coord in a"
                            + " row)",
                        state.origin));
              } else {
                PathBasedMavenOrigin mavenOrigin =
                    parseMavenCoord(
                        arg,
                        state.pendingPath.getFirst(),
                        error ->
                            state.reporter.error(
                                new StringDiagnostic(
                                    "Invalid argument to --maven-coord: " + error, state.origin)));
                state.addPendingPathWithOrigin(mavenOrigin);
                state.pendingPath = null;
              }
            })
        .option1(
            "--min-api",
            "<major|major.minor>",
            "Minimum API level to use for analysis.",
            (state, arg) -> state.builder.setMinApiLevel(AndroidApiLevel.parseAndroidApiLevel(arg)))
        .option1(
            "--output",
            "<path>",
            "Path where to write analysis result (protobuf).",
            (state, arg) ->
                state.builder.setOutputConsumer(new ByteArrayConsumer.FileConsumer(Paths.get(arg))))
        .option1(
            "--repo",
            "<path>",
            "Path to local Maven repository.",
            (state, arg) -> {
              Path repoPath = Paths.get(arg);
              if (!Files.isDirectory(repoPath)) {
                throw new IllegalArgumentException(
                    "Invalid parameter for --repo. Expected directory, got: " + arg);
              }
              try (Stream<Path> paths = Files.walk(repoPath)) {
                paths
                    .filter(path -> FileUtils.isAarFile(path) || FileUtils.isJarFile(path))
                    .forEach(
                        path -> {
                          if (FileUtils.isAarFile(path)) {
                            state.builder.addAarPath(
                                path, getMavenOriginFromArchive(repoPath, path));
                          } else {
                            state.builder.addJarPath(
                                path, getMavenOriginFromArchive(repoPath, path));
                          }
                        });
              } catch (IOException e) {
                throw new UncheckedIOException("Failed to walk repository path: " + arg, e);
              }
            })
        .option1(
            "--thread-count",
            "<int>",
            "Number of threads to use.",
            (state, arg) ->
                CliParserUtils.parsePositiveInt(
                    arg,
                    state.builder::setThreadCount,
                    error ->
                        state.reporter.error(
                            new StringDiagnostic(
                                "Invalid argument to --threads: " + error, state.origin))))
        .option0("--help", "Print this message.", state -> state.builder.setPrintHelp(true), "-h")
        .option0("--version", "Print the version.", state -> state.builder.setPrintVersion(true));
  }

  private static Origin getMavenOriginFromArchive(Path repoPath, Path path) {
    String directoryName = repoPath.relativize(path).getParent().toString();
    int lastSeparator = directoryName.lastIndexOf(File.separatorChar);
    int prevSeparator = directoryName.lastIndexOf(File.separatorChar, lastSeparator - 1);
    String group =
        StringUtils.replaceAll(directoryName.substring(0, prevSeparator), File.separator, ".");
    String module = directoryName.substring(prevSeparator + 1, lastSeparator);
    String version = directoryName.substring(lastSeparator + 1);
    return new PathBasedMavenOrigin(path, group, module, version);
  }

  private static PathBasedMavenOrigin parseMavenCoord(
      String mavenCoord, Path path, Consumer<String> errorConsumer) {
    List<String> coordinates = StringUtils.split(mavenCoord, ':');
    if (coordinates.size() != 3) {
      errorConsumer.accept("Cannot read Maven coordinate: " + mavenCoord);
      return null;
    } else {
      return new PathBasedMavenOrigin(
          path, coordinates.get(0), coordinates.get(1), coordinates.get(2));
    }
  }
}
