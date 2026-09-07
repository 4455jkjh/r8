// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AarArchiveResourceProvider;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerCommandParserTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public LibraryAnalyzerCommandParserTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testUsageMessage() {
    assertEquals(
        StringUtils.lines(
            "Usage: libanalyzer [options]",
            "where options are:",
            "  --aar <path>            # Path to Android Archive (AAR) that should be analyzed.",
            "  --keep-radius-output <path>",
            "                          # Path where to write keep radius result (protobuf).",
            "  --jar <path>            # Path to Java Archive (JAR) that should be analyzed.",
            "  --lib <path>            # Path to file or JDK home to use as a library resource.",
            "  --maven-coord <x:y:z>   # Set the Maven coordinate of the previous --aar/--jar.",
            "  --min-api <major|major.minor>",
            "                          # Minimum API level to use for analysis.",
            "  --output <path>         # Path where to write analysis result (protobuf).",
            "  --repo <path>           # Path to local Maven repository.",
            "  --thread-count <int>    # Number of threads to use.",
            "  --help",
            "  -h                      # Print this message.",
            "  --version               # Print the version."),
        LibraryAnalyzerCommandParser.getUsageMessage());
  }

  @Test
  public void testParseJar() throws Exception {
    Path jar = temp.newFile("test.jar").toPath();
    LibraryAnalyzerCommand command =
        LibraryAnalyzerCommandParser.parse(
                new String[] {"--jar", jar.toString()}, CommandLineOrigin.INSTANCE)
            .build();
    List<ProgramResourceProvider> providers = command.getApp().getProgramResourceProviders();
    assertEquals(1, providers.size());
    assertTrue(providers.get(0) instanceof ArchiveResourceProvider);
  }

  @Test
  public void testParseAar() throws Exception {
    Path aar = temp.newFile("test.aar").toPath();
    LibraryAnalyzerCommand command =
        LibraryAnalyzerCommandParser.parse(
                new String[] {"--aar", aar.toString()}, CommandLineOrigin.INSTANCE)
            .build();
    List<ProgramResourceProvider> providers = command.getApp().getProgramResourceProviders();
    assertEquals(1, providers.size());
    assertTrue(providers.get(0) instanceof AarArchiveResourceProvider);
  }

  @Test
  public void testParseMultipleArchives() throws Exception {
    Path jar = temp.newFile("test.jar").toPath();
    Path aar = temp.newFile("test.aar").toPath();
    LibraryAnalyzerCommand command =
        LibraryAnalyzerCommandParser.parse(
                new String[] {"--jar", jar.toString(), "--aar", aar.toString()},
                CommandLineOrigin.INSTANCE)
            .build();
    List<ProgramResourceProvider> providers = command.getApp().getProgramResourceProviders();
    assertEquals(2, providers.size());
    assertTrue(providers.get(0) instanceof ArchiveResourceProvider);
    assertTrue(providers.get(1) instanceof AarArchiveResourceProvider);
  }

  @Test
  public void testParseJarFollowedByOtherOption() throws Exception {
    Path jar = temp.newFile("test.jar").toPath();
    LibraryAnalyzerCommand command =
        LibraryAnalyzerCommandParser.parse(
                new String[] {"--jar", jar.toString(), "--min-api", "30"},
                CommandLineOrigin.INSTANCE)
            .build();
    List<ProgramResourceProvider> providers = command.getApp().getProgramResourceProviders();
    assertEquals(1, providers.size());
    assertTrue(providers.get(0) instanceof ArchiveResourceProvider);
  }

  @Test
  public void testParseMavenCoordOnFirstArchiveOnly() throws Exception {
    Path jar1 = temp.newFile("test1.jar").toPath();
    Path jar2 = temp.newFile("test2.jar").toPath();
    LibraryAnalyzerCommand command =
        LibraryAnalyzerCommandParser.parse(
                new String[] {
                  "--jar",
                  jar1.toString(),
                  "--maven-coord",
                  "group:module:1.0",
                  "--jar",
                  jar2.toString()
                },
                CommandLineOrigin.INSTANCE)
            .build();
    List<ProgramResourceProvider> providers = command.getApp().getProgramResourceProviders();
    assertEquals(2, providers.size());
  }
}
